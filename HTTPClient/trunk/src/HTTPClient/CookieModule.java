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

import java.awt.Frame;
import java.awt.Panel;
import java.awt.Label;
import java.awt.Color;
import java.awt.Button;
import java.awt.Graphics;
import java.awt.Dimension;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.GridLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;


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
 *
 * <P>A cookie jar is used to store cookies between sessions. This file is
 * read when this class is loaded and is written when the application exits;
 * only cookies from the default context are saved. The name of the file is
 * controlled by the property <var>HTTPClient.cookies.jar</var> and defaults
 * to a system dependent name. The reading and saving of cookies can be
 * inhibited by setting the property <var>HTTPClient.cookies.dontSave</var>.
 *
 * @see <a href="http://home.netscape.com/newsref/std/cookie_spec.html">Netscape's cookie spec</a>
 * @see <a href="ftp://ds.internic.net/internet-drafts/draft-ietf-http-state-man-mec-10.txt">HTTP State Management Mechanism spec</a>
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 * @author      modified by Stefan Köpsell, 04/11/24
 * @since	V0.3
 */

public class CookieModule implements HTTPClientModule, GlobalConstants
{
    /** the list of known cookies */
    private static Hashtable cookie_cntxt_list = new Hashtable();

    /** the file to use for persistent cookie storage */
    private static File cookie_jar = null;

    /** the cookie policy handler */
    private static CookiePolicyHandler cookie_handler =
					    new DefaultCookiePolicyHandler();



    // read in cookies from disk at startup

    static
    {
	boolean persist;
	try
	{
	    // don't use Boolean.getBoolean, as netscape won't throw a
	    // security exception for that...
	    String dont = System.getProperty("HTTPClient.cookies.dontSave");
	    persist = !Boolean.valueOf(dont).booleanValue();
	}
	catch (Exception e)
	    { persist = false; }

	if (persist)
	{
	    cookie_jar = new File(getCookieJarName());
	    if (cookie_jar.isFile()  &&  cookie_jar.canRead())
	    {
		try
		{
		    ObjectInputStream ois =
			new ObjectInputStream(new FileInputStream(cookie_jar));
		    cookie_cntxt_list.put(HTTPConnection.getDefaultContext(),
					  (Hashtable) ois.readObject());
		    ois.close();
		}
		catch (Exception e)
		    { }
	    }

	    // the nearest thing to atexit() I know of...
	    try
		{ System.runFinalizersOnExit(true); }
	    catch (Throwable t)
		{ }
	}
    }

	/**
     * @author  modified by Stefan Lieske, 2005/02/14
     */
    static void classFinalize()
    {
	if (cookie_jar != null  &&  cookie_jar.isFile()  &&
	    cookie_jar.canWrite())
	{
	    Hashtable cookie_list = new Hashtable();
	    Enumeration enumer = Util.getList(cookie_cntxt_list,
					    HTTPConnection.getDefaultContext())
				   .elements();

	    // discard cookies which are not to be kept across sessions

	    while (enumer.hasMoreElements())
	    {
		Cookie cookie = (Cookie) enumer.nextElement();
		if (!cookie.discard())
		    cookie_list.put(cookie, cookie);
	    }


	    // save any remaining cookies in jar

	    if (cookie_list.size() > 0)
	    {
		try
		{
		    ObjectOutputStream oos =
			new ObjectOutputStream(new FileOutputStream(cookie_jar));
		    oos.writeObject(cookie_list);
		    oos.close();
		}
		catch (IOException ioe)
		    { }
	    }
	}
    }


    private static String getCookieJarName()
    {
	String file = null;

	try
	    { file = System.getProperty("HTTPClient.cookies.jar"); }
	catch (Exception e)
	    { }

	if (file == null)
	{
	    // default to something reasonable

	    String os = System.getProperty("os.name");
	    if (os.equalsIgnoreCase("Windows 95")  ||
		os.equalsIgnoreCase("16-bit Windows")  ||
		os.equalsIgnoreCase("Windows"))
	    {
		file = System.getProperty("java.home") +
		       File.separator + ".httpclient_cookies";
	    }
	    else if (os.equalsIgnoreCase("Windows NT"))
	    {
		file = System.getProperty("user.home") +
		       File.separator + ".httpclient_cookies";
	    }
	    else if (os.equalsIgnoreCase("OS/2"))
	    {
		file = System.getProperty("user.home") +
		       File.separator + ".httpclient_cookies";
	    }
	    else if (os.equalsIgnoreCase("Mac OS")  ||
		     os.equalsIgnoreCase("MacOS"))
	    {
		file = "System Folder" + File.separator +
		       "Preferences" + File.separator +
		       "HTTPClientCookies";
	    }
	    else		// it's probably U*IX or VMS
	    {
		file = System.getProperty("user.home") +
		       File.separator + ".httpclient_cookies";
	    }
	}

	return file;
    }


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

	req.setHeaders(Util.removeAllValues(req.getHeaders(), "Cookie"));


	// Now set any new cookie headers

	Hashtable cookie_list =
	    Util.getList(cookie_cntxt_list, req.getConnection().getContext());
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
		    remove_list.addElement(cookie); /** @author modified by Stefan Köpsell, 04/11/24 */
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
	    hdrs = Util.resizeArray(hdrs, hdrs.length+1);
	    hdrs[hdrs.length-1] = new NVPair("Cookie", value.toString());

	    // add Cookie2 header if necessary
	    if (!cookie2)
	    {
		int idx = Util.getIndex(hdrs, "Cookie2");
		if (idx == hdrs.length)
		    hdrs = Util.addValue(hdrs, "Cookie2", "$Version=\"1\"");
	    }

	    req.setHeaders(hdrs);

	    if (DebugMods)
		Util.logLine("CookM: Sending cookies '" + value + "'");
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
	    Util.logLine("CookM: Received and parsed " + cookies.length +
			 " cookies:");
	    for (int idx=0; idx<cookies.length; idx++)
		Util.logLine("CookM: Cookie " + idx + ": " +cookies[idx]);
	}

	Hashtable cookie_list =
	    Util.getList(cookie_cntxt_list, req.getConnection().getContext());
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
	Hashtable cookie_list = Util.getList(cookie_cntxt_list, context);
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
		    cookies = Util.resizeArray(cookies, idx+cntxt.size());
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
	Hashtable cookie_list = Util.getList(cookie_cntxt_list, context);

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
	    Util.getList(cookie_cntxt_list, HTTPConnection.getDefaultContext());
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
	Hashtable cookie_list = Util.getList(cookie_cntxt_list, context);
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
	    Util.getList(cookie_cntxt_list, HTTPConnection.getDefaultContext());
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
	Hashtable cookie_list = Util.getList(cookie_cntxt_list, context);
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
	String[] domains = Util.splitProperty(list);
	for (int idx=0; idx<domains.length; idx++)
	    addAcceptDomain(domains[idx].toLowerCase());

	try
	    { list = System.getProperty("HTTPClient.cookies.hosts.reject"); }
	catch (Exception e)
	    { list = null; }
	domains = Util.splitProperty(list);
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

	if (!req.allowUI())  return true;

	if (prompter == null)
	    setDefaultPrompter();

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
		    Util.resizeArray(accept_domains, accept_domains.length+1);
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
		    Util.resizeArray(reject_domains, reject_domains.length+1);
	reject_domains[reject_domains.length-1] = domain;
    }


    /**
     * Set the default cookie accept prompter. It first tries to figure out
     * if the AWT is running by enumerating the top level threads and looking
     * for any that start with "AWT-". If any are found then the GUI popup
     * prompter is used; otherwise the command line prompter is used.
     */
    private synchronized static void setDefaultPrompter()
    {
	if (prompter != null)  return;


	// Try and figure out if the AWT is running

	boolean awt_running = false;

	ThreadGroup root = Thread.currentThread().getThreadGroup();
	while (root.getParent() != null)  root = root.getParent();

	Thread[] t_list = new Thread[root.activeCount() + 5];
	int t_num = root.enumerate(t_list, false);
	for (int idx=0; idx<t_num; idx++)
	{
	    if (t_list[idx].getName().startsWith("AWT-"))
	    {
		awt_running = true;
		break;
	    }
	}


	// if the AWT is running use the popup box; else use the
	// the command line prompter.

	if (awt_running)
	    prompter = new BasicCookieBox();
	else
	    prompter = new BasicCookiePrompt();
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


/**
 * A simple popup that asks whether the cookie should be accepted or rejected,
 * or if cookies from whole domains should be silently accepted or rejected.
 *
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 */
class BasicCookieBox extends Frame implements CookiePrompter
{
    private final static String title = "Set Cookie Request";
    private Dimension           screen;
    private GridBagConstraints  constr;
    private Label		name_value_label;
    private Label		domain_value;
    private Label		ports_label;
    private Label		ports_value;
    private Label		path_value;
    private Label		expires_value;
    private Label		discard_note;
    private Label		secure_note;
    private Label		c_url_note;
    private Panel		left_panel;
    private Panel		right_panel;
    private Label   		comment_label;
    private TextArea		comment_value;
    private TextField		domain;
    private Button		default_focus;
    private boolean             accept;
    private boolean             accept_domain;


    /**
     * Constructs the popup.
     */
    BasicCookieBox()
    {
	super(title);

	screen = getToolkit().getScreenSize();

	addNotify();
	addWindowListener(new Close());

	GridBagLayout layout;
	setLayout(layout = new GridBagLayout());
	constr = new GridBagConstraints();

	constr.gridwidth = GridBagConstraints.REMAINDER;
	constr.anchor = GridBagConstraints.WEST;
	add(new Label("The server would like to set the following cookie:"), constr);

	Panel p = new Panel();
	left_panel = new Panel();
	left_panel.setLayout(new GridLayout(4,1));
	left_panel.add(new Label("Name=Value:"));
	left_panel.add(new Label("Domain:"));
	left_panel.add(new Label("Path:"));
	left_panel.add(new Label("Expires:"));
	ports_label = new Label("Ports:");
	p.add(left_panel);

	right_panel = new Panel();
	right_panel.setLayout(new GridLayout(4,1));
	right_panel.add(name_value_label = new Label());
	right_panel.add(domain_value = new Label());
	right_panel.add(path_value = new Label());
	right_panel.add(expires_value = new Label());
	ports_value = new Label();
	p.add(right_panel);
	add(p, constr);
	secure_note = new Label("This cookie will only be sent over secure connections");
	discard_note = new Label("This cookie will be discarded at the end of the session");
	c_url_note = new Label("");
	comment_label = new Label("Comment:");
	comment_value =
		new TextArea("", 3, 45, TextArea.SCROLLBARS_VERTICAL_ONLY);
	comment_value.setEditable(false);

	add(new Panel(), constr);

	constr.gridwidth = 1;
	constr.anchor = GridBagConstraints.CENTER;
	constr.weightx = 1.0;
	add(default_focus = new Button("Accept"), constr);
	default_focus.addActionListener(new Accept());

	Button b;
	constr.gridwidth = GridBagConstraints.REMAINDER;
	add(b= new Button("Reject"), constr);
	b.addActionListener(new Reject());

	constr.weightx = 0.0;
	p = new Separator();
	constr.fill = GridBagConstraints.HORIZONTAL;
	add(p, constr);

	constr.fill   = GridBagConstraints.NONE;
	constr.anchor = GridBagConstraints.WEST;
	add(new Label("Accept/Reject all cookies from a host or domain:"), constr);

	p = new Panel();
	p.add(new Label("Host/Domain:"));
	p.add(domain = new TextField(30));
	add(p, constr);

	add(new Label("domains are characterized by a leading dot (`.');"), constr);
	add(new Label("an empty string matches all hosts"), constr);

	constr.anchor    = GridBagConstraints.CENTER;
	constr.gridwidth = 1;
	constr.weightx   = 1.0;
	add(b = new Button("Accept All"), constr);
	b.addActionListener(new AcceptDomain());

	constr.gridwidth = GridBagConstraints.REMAINDER;
	add(b = new Button("Reject All"), constr);
	b.addActionListener(new RejectDomain());

	pack();

	constr.anchor    = GridBagConstraints.WEST;
	constr.gridwidth = GridBagConstraints.REMAINDER;
    }


    public Dimension getMaximumSize()
    {
	return new Dimension(screen.width*3/4, screen.height*3/4);
    }


    /**
     * our event handlers
     */
    private class Accept implements ActionListener
    {
        public void actionPerformed(ActionEvent ae)
        {
	    accept = true;
	    accept_domain = false;
            synchronized (BasicCookieBox.this)
		{ BasicCookieBox.this.notifyAll(); }
        }
    }

    private class Reject implements ActionListener
    {
        public void actionPerformed(ActionEvent ae)
	{
	    accept = false;
	    accept_domain = false;
            synchronized (BasicCookieBox.this)
		{ BasicCookieBox.this.notifyAll(); }
	}
    }

    private class AcceptDomain implements ActionListener
    {
        public void actionPerformed(ActionEvent ae)
        {
	    accept = true;
	    accept_domain = true;
            synchronized (BasicCookieBox.this)
		{ BasicCookieBox.this.notifyAll(); }
	}
    }

    private class RejectDomain implements ActionListener
    {
        public void actionPerformed(ActionEvent ae)
	{
	    accept = false;
	    accept_domain = true;
            synchronized (BasicCookieBox.this)
		{ BasicCookieBox.this.notifyAll(); }
	}
    }


    private class Close extends WindowAdapter
    {
	public void windowClosing(WindowEvent we)
	{
	    new Reject().actionPerformed(null);
	}
    }


    /**
     * the method called by the DefaultCookiePolicyHandler.
     *
     * @return true if the cookie should be accepted
     */
    public synchronized boolean accept(Cookie cookie,
				       DefaultCookiePolicyHandler h,
				       String server)
    {
	// set the new values

	name_value_label.setText(cookie.getName() + "=" + cookie.getValue());
	domain_value.setText(cookie.getDomain());
	path_value.setText(cookie.getPath());
	if (cookie.expires() == null)
	    expires_value.setText("never");
	else
	    expires_value.setText(cookie.expires().toString());
	int pos = 2;
	if (cookie.isSecure())
	    add(secure_note, constr, pos++);
	if (cookie.discard())
	    add(discard_note, constr, pos++);

	if (cookie instanceof Cookie2)
	{
	    Cookie2 cookie2 = (Cookie2) cookie;

	    // set ports list
	    if (cookie2.getPorts() != null)
	    {
		((GridLayout) left_panel.getLayout()).setRows(5);
		left_panel.add(ports_label, 2);
		((GridLayout) right_panel.getLayout()).setRows(5);
		int[] ports = cookie2.getPorts();
		StringBuffer plist = new StringBuffer();
		plist.append(ports[0]);
		for (int idx=1; idx<ports.length; idx++)
		{
		    plist.append(", ");
		    plist.append(ports[idx]);
		}
		ports_value.setText(plist.toString());
		right_panel.add(ports_value, 2);
	    }

	    // set comment url
	    if (cookie2.getCommentURL() != null)
	    {
		c_url_note.setText("For more info on this cookie see: " +
				    cookie2.getCommentURL());
		add(c_url_note, constr, pos++);
	    }

	    // set comment
	    if (cookie2.getComment() != null)
	    {
		comment_value.setText(cookie2.getComment());
		add(comment_label, constr, pos++);
		add(comment_value, constr, pos++);
	    }
	}


	// invalidate all labels, so that new values are displayed correctly

	name_value_label.invalidate();
	domain_value.invalidate();
	ports_value.invalidate();
	path_value.invalidate();
	expires_value.invalidate();
	left_panel.invalidate();
	right_panel.invalidate();
	secure_note.invalidate();
	discard_note.invalidate();
	c_url_note.invalidate();
	comment_value.invalidate();
	invalidate();


	// set default domain test

	domain.setText(cookie.getDomain());


	// display

	setResizable(true);
	pack();
	setResizable(false);
	setLocation((screen.width-getPreferredSize().width)/2,
		    (int) ((screen.height-getPreferredSize().height)/2*.7));
	setVisible(true);
	default_focus.requestFocus();


	// wait for user input

	try { wait(); } catch (InterruptedException e) { }

	setVisible(false);


	// reset popup

	remove(secure_note);
	remove(discard_note);
	left_panel.remove(ports_label);
	((GridLayout) left_panel.getLayout()).setRows(4);
	right_panel.remove(ports_value);
	((GridLayout) right_panel.getLayout()).setRows(4);
	remove(c_url_note);
	remove(comment_label);
	remove(comment_value);


	// handle accept/reject domain buttons

	if (accept_domain)
	{
	    String dom = domain.getText().trim().toLowerCase();

	    if (accept)
		h.addAcceptDomain(dom);
	    else
		h.addRejectDomain(dom);
	}

	return accept;
    }
}


/**
 * A simple separator element.
 */
class Separator extends Panel
{
    public void paint(Graphics g)
    {
	int w = getSize().width,
	    h = getSize().height/2;

	g.setColor(Color.darkGray);
	g.drawLine(2, h-1, w-2, h-1);
	g.setColor(Color.white);
	g.drawLine(2, h, w-2, h);
    }

    public Dimension getMinimumSize()
    {
	return new Dimension(4, 2);
    }
}


/**
 * A simple prompt that asks whether the cookie should be accepted or rejected,
 * or if cookies from whole domains should be silently accepted or rejected.
 *
 * @version	0.4  15/05/1998
 * @author	Ronald Tschal&auml;r
 */
class BasicCookiePrompt implements CookiePrompter
{
    /**
     * the method called by the DefaultCookiePolicyHandler.
     *
     * @return true if the cookie should be accepted
     */
    public synchronized boolean accept(Cookie cookie,
				       DefaultCookiePolicyHandler h,
				       String server)
    {
	// Print out cookie info

	System.out.println("The server would like to set the following cookie:");
	System.out.println("  Name=Value: " +
			   cookie.getName() + "=" + cookie.getValue());
	System.out.println("  Domain:     " + cookie.getDomain());
	if (cookie instanceof Cookie2  && ((Cookie2) cookie).getPorts() != null)
	{
	    int[] ports = ((Cookie2) cookie).getPorts();
	    StringBuffer plist = new StringBuffer("  Ports:      ");
	    plist.append(ports[0]);
	    for (int idx=1; idx<ports.length; idx++)
	    {
		plist.append(", ");
		plist.append(ports[idx]);
	    }
	    System.out.println(plist.toString());
	}
	System.out.println("  Path:       " + cookie.getPath());
	if (cookie.expires() == null)
	    System.out.println("  Expires:    never");
	else
	    System.out.println("  Expires:    " + cookie.expires().toString());

	if (cookie.discard())
	    System.out.println("  This cookie will be discarded at the end " +
			       "of the session");
	if (cookie.isSecure())
	    System.out.println("  This cookie will only be sent over secure " +
			       "connections");

	if (cookie instanceof Cookie2)
	{
	    Cookie2 cookie2 = (Cookie2) cookie;

	    // print comment url
	    if (cookie2.getCommentURL() != null)
		System.out.println("  For more info on this cookie see: " +
				   cookie2.getCommentURL());

	    // print comment
	    if (cookie2.getComment() != null)
		System.out.println("  Comment:    " + cookie2.getComment());
	}


	// get input from user

	BufferedReader inp =
	    new BufferedReader(new InputStreamReader(System.in));
	String domain = cookie.getDomain();
	boolean accept = false;
	boolean accept_domain = false;

	try
	{
	    input: do
	    {
		System.out.print("(a)ccept, (r)eject, set (d)domain, (h)elp: ");
		System.out.flush();
		String answer = inp.readLine();
		if (answer == null)  break input;

		switch (answer.charAt(0))
		{
		    case 'a':
			accept = true; accept_domain = false;
			break input;
		    case 'r':
			accept = false; accept_domain = false;
			break input;
		    case 'A':
			accept = true; accept_domain = true;
			break input;
		    case 'R':
			accept = false; accept_domain = true;
			break input;
		    case 'd':
		    case 'D':
			System.out.println("Current host/domain is \"" +
					   domain + '\"');
			System.out.print("host/domain: ");
			System.out.flush();
			domain = inp.readLine();
			if (domain == null)  break input;
			break;
		    case 'h':
		    case 'H':
			print_help(domain);
			break;
		    default:
			break;
		}
	    } while(true);
	}
	catch (IOException ioe)
	    { }


	// handle accept/reject domain buttons

	if (accept_domain)
	{
	    if (accept)
		h.addAcceptDomain(domain);
	    else
		h.addRejectDomain(domain);
	}

	return accept;
    }


    private static void print_help(String domain)
    {
	for (int idx=0; idx<help.length; idx++)
	{
	    if (idx == 3)
		System.out.println(help[idx] + domain + '\"');
	    else
		System.out.println(help[idx]);
	}
    }

    private static String [] help = {
"\"a\" and \"r\" accept or reject the current cookie",
"\"A\" and \"R\" accept or reject all the cookies from the host or domain",
"\"d\" allows you to edit the host or domain for \"A\" and \"R\"",
"The current host/domain is \"",
"Domains are distinguished by a leading dot; an empty string matches all hosts."
    };
}

