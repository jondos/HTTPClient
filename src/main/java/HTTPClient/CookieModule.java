/*
 * @(#)CookieModule.java				0.3 30/01/1998
 *
 *  This file is part of the HTTPClient package
 *  Copyright (C) 1996-1998  Ronald Tschalaer
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public
 *  License as published by the Free Software Foundation; either
 *  version 2 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public
 *  License along with this library; if not, write to the Free
 *  Software Foundation, Inc., 59 Temple Place - Suite 330, Boston,
 *  MA 02111-1307, USA
 *
 *  For questions, suggestions, bug-reports, enhancement-requests etc.
 *  I may be contacted at:
 *
 *  ronald@innovation.ch
 *  Ronald.Tschalaer@psi.ch
 *
 */

package HTTPClient;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ProtocolException;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;

/**
 * This module handles Netscape cookies (also called Version 0 cookies)
 * and Version 1 cookies. Specifically is reads the <var>Set-Cookie</var>
 * and <var>Set-Cookie2</var> response headers and sets the <var>Cookie</var>
 * and <var>Cookie2</var> headers as neccessary.
 *
 * <P>The accepting and sending of cookies is controlled by a
 * <var>CookiePolicyHandler</var>. This allows you to fine tune your privacy
 * preferences. A cookie is only added to the cookie jar if the handler
 * allows it, and a cookie from the cookie jar is only sent if the handler
 * allows it.
 * *
 * @see <a href="http://home.netscape.com/newsref/std/cookie_spec.html">Netscape's cookie spec</a>
 * @see <a href="ftp://ds.internic.net/internet-drafts/draft-ietf-http-state-man-mec-10.txt">HTTP State Management Mechanism spec</a>
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 * @author      modified by Stefan K&ouml;psell, 04/11/24
 * @since	V0.3
 */

public class CookieModule implements HTTPClientModule, GlobalConstants
{
    /** the list of known cookies */
    private static Hashtable cookie_cntxt_list = new Hashtable();

    /** the cookie policy handler */
    private static CookiePolicyHandler cookie_handler =
					    new DefaultCookiePolicyHandler();


    // Constructors

    CookieModule()
    {
    }


    // Methods

    /**
     * Invoked by the HTTPClient.
     */
    public int requestHandler(Request req, Response[] resp)
    {
	// First remove any Cookie headers we might have set for a previous
	// request

	req.setHeaders(HttpClientUtil.removeAllValues(req.getHeaders(), "Cookie"));


	// Now set any new cookie headers

	Hashtable cookie_list =
	    HttpClientUtil.getList(cookie_cntxt_list, req.getConnection().getContext());
	if (cookie_list.size() == 0)
	    return REQ_CONTINUE;	// no need to create a lot of objects

	Vector  names   = new Vector();
	Vector  lens    = new Vector();
	boolean cookie2 = false;

	synchronized(cookie_list)
	{
	    Enumeration list = cookie_list.elements();
	    Vector remove_list = null;

	    while (list.hasMoreElements())
	    {
		Cookie cookie = (Cookie) list.nextElement();

		if (cookie.hasExpired())
		{
		    if (remove_list == null)  remove_list = new Vector();
		    remove_list.addElement(cookie); /** @author modified by Stefan K&ouml;psell, 04/11/24 */
		    continue;
		}

		if (cookie.sendWith(req)  &&  (cookie_handler == null  ||
		    cookie_handler.sendCookie(cookie, req)))
		{
		    int len = cookie.getPath().length();
		    int idx;

		    // insert in correct position
		    for (idx=0; idx<lens.size(); idx++)
			if (((Integer) lens.elementAt(idx)).intValue() < len)
			    break;

		    names.insertElementAt(cookie.toExternalForm(), idx);
		    lens.insertElementAt(new Integer(len), idx);

		    if (cookie instanceof Cookie2)  cookie2 = true;
		}
	    }

	    // remove any marked cookies
	    // Note: we can't do this during the enumeration!
	    if (remove_list != null)
	    {
		for (int idx=0; idx<remove_list.size(); idx++)
		    cookie_list.remove(remove_list.elementAt(idx));
	    }
	}

	if (!names.isEmpty())
	{
	    StringBuffer value = new StringBuffer();

	    if (cookie2)
		value.append("$Version=\"1\"; ");

	    value.append((String) names.elementAt(0));
	    for (int idx=1; idx<names.size(); idx++)
	    {
		value.append("; ");
		value.append((String) names.elementAt(idx));
	    }
	    NVPair[] hdrs = req.getHeaders();
	    hdrs = HttpClientUtil.resizeArray(hdrs, hdrs.length+1);
	    hdrs[hdrs.length-1] = new NVPair("Cookie", value.toString());

	    // add Cookie2 header if necessary
	    if (!cookie2)
	    {
		int idx = HttpClientUtil.getIndex(hdrs, "Cookie2");
		if (idx == hdrs.length)
		    hdrs = HttpClientUtil.addValue(hdrs, "Cookie2", "$Version=\"1\"");
	    }

	    req.setHeaders(hdrs);

	    if (DebugMods)
		HttpClientUtil.logLine("CookM: Sending cookies '" + value + "'");
	}

	return REQ_CONTINUE;
    }


    /**
     * Invoked by the HTTPClient.
     */
    public void responsePhase1Handler(Response resp, RoRequest req)
	    throws IOException
    {
	String set_cookie  = resp.getHeader("Set-Cookie");
	String set_cookie2 = resp.getHeader("Set-Cookie2");
	if (set_cookie == null  &&  set_cookie2 == null)
	    return;

	resp.deleteHeader("Set-Cookie");
	resp.deleteHeader("Set-Cookie2");

	if (set_cookie != null)
	    handleCookie(set_cookie, false, req, resp);
	if (set_cookie2 != null)
	    handleCookie(set_cookie2, true, req, resp);
    }


    /**
     * Invoked by the HTTPClient.
     */
    public int responsePhase2Handler(Response resp, Request req)
    {
	return RSP_CONTINUE;
    }


    /**
     * Invoked by the HTTPClient.
     */
    public void responsePhase3Handler(Response resp, RoRequest req)
    {
    }


    /**
     * Invoked by the HTTPClient.
     */
    public void trailerHandler(Response resp, RoRequest req)  throws IOException
    {
	String set_cookie = resp.getTrailer("Set-Cookie");
	String set_cookie2 = resp.getHeader("Set-Cookie2");
	if (set_cookie == null  &&  set_cookie2 == null)
	    return;

	resp.deleteTrailer("Set-Cookie");
	resp.deleteTrailer("Set-Cookie2");

	if (set_cookie != null)
	    handleCookie(set_cookie, false, req, resp);
	if (set_cookie2 != null)
	    handleCookie(set_cookie2, true, req, resp);
    }


    private void handleCookie(String set_cookie, boolean cookie2, RoRequest req,
			      Response resp)
	    throws ProtocolException
    {
	Cookie[] cookies;
	if (cookie2)
	    cookies = Cookie2.parse(set_cookie, req);
	else
	    cookies = Cookie.parse(set_cookie, req);

	if (DebugMods)
	{
	    HttpClientUtil.logLine("CookM: Received and parsed " + cookies.length +
			 " cookies:");
	    for (int idx=0; idx<cookies.length; idx++)
		HttpClientUtil.logLine("CookM: Cookie " + idx + ": " +cookies[idx]);
	}

	Hashtable cookie_list =
	    HttpClientUtil.getList(cookie_cntxt_list, req.getConnection().getContext());
	synchronized(cookie_list)
	{
	    for (int idx=0; idx<cookies.length; idx++)
	    {
		Cookie cookie = (Cookie) cookie_list.get(cookies[idx]);
		if (cookie != null  &&  cookies[idx].hasExpired())
		    cookie_list.remove(cookie);		// expired, so remove
		else  					// new or replaced
		{
		    if (cookie_handler == null  ||
			cookie_handler.acceptCookie(cookies[idx], req, resp))
			cookie_list.put(cookies[idx], cookies[idx]);
		}
	    }
	}
    }


    /**
     * Discard all cookies for all contexts. Cookies stored in persistent
     * storage are not affected.
     */
    public static void discardAllCookies()
    {
	synchronized(cookie_cntxt_list)
	    { cookie_cntxt_list.clear(); }
    }


    /**
     * Discard all cookies for the given context. Cookies stored in persistent
     * storage are not affected.
     *
     * @param context the context Object
     */
    public static void discardAllCookies(Object context)
    {
	Hashtable cookie_list = HttpClientUtil.getList(cookie_cntxt_list, context);
	synchronized(cookie_list)
	    { cookie_list.clear(); }
    }


    /**
     * List all stored cookies for all contexts.
     *
     * @return an array of all Cookies
     */
    public static Cookie[] listAllCookies()
    {
	synchronized(cookie_cntxt_list)
	{
	    Cookie[] cookies = new Cookie[0];
	    int idx = 0;

	    Enumeration cntxt_list = cookie_cntxt_list.elements();
	    while (cntxt_list.hasMoreElements())
	    {
		Hashtable cntxt = (Hashtable) cntxt_list.nextElement();
		synchronized(cntxt)
		{
		    cookies = HttpClientUtil.resizeArray(cookies, idx+cntxt.size());
		    Enumeration cookie_list = cntxt.elements();
		    while (cookie_list.hasMoreElements())
			cookies[idx++] = (Cookie) cookie_list.nextElement();
		}
	    }

	    return cookies;
	}
    }


    /**
     * List all stored cookies for a given context.
     *
     * @param  context the context Object.
     * @return an array of Cookies
	 * @author  modified by Stefan Lieske, 2005/02/14
     */
    public static Cookie[] listAllCookies(Object context)
    {
	Hashtable cookie_list = HttpClientUtil.getList(cookie_cntxt_list, context);

	synchronized(cookie_list)
	{
	    Cookie[] cookies = new Cookie[cookie_list.size()];
	    int idx = 0;

	    Enumeration enumer = cookie_list.elements();
	    while (enumer.hasMoreElements())
		cookies[idx++] = (Cookie) enumer.nextElement();

	    return cookies;
	}
    }


    /**
     * Add the specified cookie to the list of cookies in the default context.
     * If a compatible cookie (as defined by <var>Cookie.equals()</var>)
     * already exists in the list then it is replaced with the new cookie.
     *
     * @param cookie the Cookie to add
     */
    public static void addCookie(Cookie cookie)
    {
	Hashtable cookie_list =
	    HttpClientUtil.getList(cookie_cntxt_list, HTTPConnection.getDefaultContext());
	cookie_list.put(cookie, cookie);
    }


    /**
     * Add the specified cookie to the list of cookies for the specified
     * context. If a compatible cookie (as defined by
     * <var>Cookie.equals()</var>) already exists in the list then it is
     * replaced with the new cookie.
     *
     * @param cookie  the cookie to add
     * @param context the context Object.
     */
    public static void addCookie(Cookie cookie, Object context)
    {
	Hashtable cookie_list = HttpClientUtil.getList(cookie_cntxt_list, context);
	cookie_list.put(cookie, cookie);
    }


    /**
     * Remove the specified cookie from the list of cookies in the default
     * context. If the cookie is not found in the list then this method does
     * nothing.
     *
     * @param cookie the Cookie to remove
     */
    public static void removeCookie(Cookie cookie)
    {
	Hashtable cookie_list =
	    HttpClientUtil.getList(cookie_cntxt_list, HTTPConnection.getDefaultContext());
	cookie_list.remove(cookie);
    }


    /**
     * Remove the specified cookie from the list of cookies for the specified
     * context. If the cookie is not found in the list then this method does
     * nothing.
     *
     * @param cookie  the cookie to remove
     * @param context the context Object
     */
    public static void removeCookie(Cookie cookie, Object context)
    {
	Hashtable cookie_list = HttpClientUtil.getList(cookie_cntxt_list, context);
	cookie_list.remove(cookie);
    }


    /**
     * Sets a new cookie policy handler. This handler will be called for each
     * cookie that a server wishes to set and for each cookie that this
     * module wishes to send with a request. In either case the handler may
     * allow or reject the operation. If you wish to blindly accept and send
     * all cookies then just disable the handler with
     * <code>CookieModule.setCookiePolicyHandler(null);</code>.
     *
     * <P>At initialization time a default handler is installed. This
     * handler allows all cookies to be sent. For any cookie that a server
     * wishes to be set two lists are consulted. If the server matches any
     * host or domain in the reject list then the cookie is rejected; if
     * the server matches any host or domain in the accept list then the
     * cookie is accepted (in that order). If no host or domain match is
     * found in either of these two lists and user interaction is allowed
     * then a dialog box is poped up to ask the user whether to accept or
     * reject the cookie; if user interaction is not allowed the cookie is
     * accepted.
     *
     * <P>The accept and reject lists in the default handler are initialized
     * at startup from the two properties
     * <var>HTTPClient.cookies.hosts.accept</var> and
     * <var>HTTPClient.cookies.hosts.reject</var>. These properties must
     * contain a "|" separated list of host and domain names. All names
     * beginning with a "." are treated as domain names, all others as host
     * names. An empty string which will match all hosts. The two lists are
     * further expanded if the user chooses one of the "Accept All from Domain"
     * or "Reject All from Domain" buttons in the dialog box.
     *
     * <P>Note: the default handler does not implement the rules concerning
     * unverifiable transactions (section 4.3.5,
     * <A HREF="http://www.cis.ohio-state.edu/htbin/rfc/rfc2109">RFC-2109</A>).
     * The reason for this is simple: the default handler knows nothing
     * about the application using this client, and it therefore does not
     * have enough information to determine when a request is verifiable
     * and when not. You are therefore encouraged to provide your own handler
     * which implements section 4.3.5 (use the
     * <var>CookiePolicyHandler.sendCookie</var> method for this).
     *
     * @param the new policy handler
     * @return the previous policy handler
     */
    public static synchronized CookiePolicyHandler
			    setCookiePolicyHandler(CookiePolicyHandler handler)
    {
	CookiePolicyHandler old = cookie_handler;
	cookie_handler = handler;
	return old;
    }
}


/**
 * A simple cookie policy handler.
 */

class DefaultCookiePolicyHandler implements CookiePolicyHandler
{
    /** a list of all hosts and domains from which to silently accept cookies */
    private String[] accept_domains = new String[0];

    /** a list of all hosts and domains from which to silently reject cookies */
    private String[] reject_domains = new String[0];

    /** the accept/reject prompter */
    private static CookiePrompter prompter = null;


    DefaultCookiePolicyHandler()
    {
	// have all cookies been accepted or rejected?
	String list;

	try
	    { list = System.getProperty("HTTPClient.cookies.hosts.accept"); }
	catch (Exception e)
	    { list = null; }
	String[] domains = HttpClientUtil.splitProperty(list);
	for (int idx=0; idx<domains.length; idx++)
	    addAcceptDomain(domains[idx].toLowerCase());

	try
	    { list = System.getProperty("HTTPClient.cookies.hosts.reject"); }
	catch (Exception e)
	    { list = null; }
	domains = HttpClientUtil.splitProperty(list);
	for (int idx=0; idx<domains.length; idx++)
	    addRejectDomain(domains[idx].toLowerCase());
    }


    /**
     * returns whether this cookie should be accepted. First checks the
     * stored lists of accept and reject domains, and if it is neither
     * accepted nor rejected by these then query the user via a popup.
     *
     * @param cookie   the cookie in question
     * @param req      the request
     * @param resp     the response
     * @return true if we accept this cookie.
     */
    public boolean acceptCookie(Cookie cookie, RoRequest req, RoResponse resp)
    {
	String server = req.getConnection().getHost();
	if (server.indexOf('.') == -1)  server += ".local";


	// Check lists. Reject takes priority over accept

	for (int idx=0; idx<reject_domains.length; idx++)
	{
	    if (reject_domains[idx].length() == 0  ||
		reject_domains[idx].charAt(0) == '.'  &&
		server.endsWith(reject_domains[idx])  ||
		reject_domains[idx].charAt(0) != '.'  &&
		server.equals(reject_domains[idx]))
		    return false;
	}

	for (int idx=0; idx<accept_domains.length; idx++)
	{
	    if (accept_domains[idx].length() == 0  ||
		accept_domains[idx].charAt(0) == '.'  &&
		server.endsWith(accept_domains[idx])  ||
		accept_domains[idx].charAt(0) != '.'  &&
		server.equals(accept_domains[idx]))
		    return true;
	}


	// Ok, not in any list, so ask the user (if allowed).

	if (prompter==null)
            return true;
	return prompter.accept(cookie, this, server);
    }


    /**
     * This handler just allows all cookies to be sent which were accepted
     * (i.e. no further restrictions are placed on the sending of cookies).
     *
     * @return true
     */
    public boolean sendCookie(Cookie cookie, RoRequest req)
    {
	return true;
    }


    void addAcceptDomain(String domain)
    {
	if (domain.indexOf('.') == -1)  domain += ".local";

	for (int idx=0; idx<accept_domains.length; idx++)
	{
	    if (domain.endsWith(accept_domains[idx]))
		return;
	    if (accept_domains[idx].endsWith(domain))
	    {
		accept_domains[idx] = domain;
		return;
	    }
	}
	accept_domains =
		    HttpClientUtil.resizeArray(accept_domains, accept_domains.length+1);
	accept_domains[accept_domains.length-1] = domain;
    }


    void addRejectDomain(String domain)
    {
	if (domain.indexOf('.') == -1)  domain += ".local";

	for (int idx=0; idx<reject_domains.length; idx++)
	{
	    if (domain.endsWith(reject_domains[idx]))
		return;
	    if (reject_domains[idx].endsWith(domain))
	    {
		reject_domains[idx] = domain;
		return;
	    }
	}

	reject_domains =
		    HttpClientUtil.resizeArray(reject_domains, reject_domains.length+1);
	reject_domains[reject_domains.length-1] = domain;
    }

}


interface CookiePrompter
{
    /**
     * @param cookie the cookie to accept or reject
     * @param h      the policy handler
     * @param server the host name of the server which is trying to set this
     *               cookie
     * @ return true if the cookie should be accepted
     */
    boolean accept(Cookie cookie, DefaultCookiePolicyHandler h, String server);
}


