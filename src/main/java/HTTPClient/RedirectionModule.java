/*
 * @(#)RedirectionModule.java				0.3 30/01/1998
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

import java.net.InetAddress;
import java.net.ProtocolException;
import java.net.UnknownHostException;
import java.io.IOException;
import java.util.Hashtable;


/**
 * This module handles the redirection status codes 301, 302, 303, 305, 306
 * and 307.
 *
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 */

class RedirectionModule implements HTTPClientModule, GlobalConstants
{
    /** a list of permanent redirections (301) */
    private static Hashtable perm_redir_cntxt_list = new Hashtable();

    /** a list of deferred redirections (used with Response.retryRequest()) */
    private static Hashtable deferred_redir_list = new Hashtable();

    /** the level of redirection */
    private int level;

    /** the uri used in the last redirection */
    private HttpClientURI lastURI;

    /** used for deferred redirection retries */
    private boolean new_con;

    /** used for deferred redirection retries */
    private Request saved_req;


    // Constructors

    /**
     * Start with level 0.
     */
    RedirectionModule()
    {
	level     = 0;
	lastURI   = null;
	saved_req = null;
    }


    // Methods

    /**
     * Invoked by the HTTPClient.
     */
    public int requestHandler(Request req, Response[] resp)
    {
	HTTPConnection con = req.getConnection();
	HttpClientURI new_loc,
	    cur_loc;
	    

	// check for retries

	HttpOutputStream out = req.getStream();
	if (out != null  &&  deferred_redir_list.get(out) != null)
	{
	    copyFrom((RedirectionModule) deferred_redir_list.get(out));
	    req.copyFrom(saved_req);
	    deferred_redir_list.remove(out);

	    if (new_con)
		return REQ_NEWCON_RST;
	    else
		return REQ_RESTART;
	}


	// handle permanent redirections

	try
	{
	    cur_loc = new HttpClientURI(con.getProtocol(), con.getHost(),
			      con.getPort(), req.getRequestURI());
	}
	catch (ParseException pe)
	    { cur_loc = null; }

	Hashtable perm_redir_list = HttpClientUtil.getList(perm_redir_cntxt_list,
					    req.getConnection().getContext());
	if ((new_loc = (HttpClientURI) perm_redir_list.get(cur_loc)) != null)
	{
	    /* copy query if present in old url but not in new url. This
	     * isn't strictly conforming, but some scripts fail to properly
	     * propagate the query string to the Location header.
	     *
	     * Unfortunately it looks like we're fucked either way: some
	     * scripts fail if you don't propagate the query string, some
	     * fail if you do... God, don't you just love it when people
	     * can't read a spec? Anway, since we can't get it right for
	     * all scripts we opt to follow the spec.
	    String nres    = new_loc.getPath(),
		   oquery  = Util.getQuery(req.getRequestURI()),
		   nquery  = Util.getQuery(nres);
	    if (nquery == null  &&  oquery != null)
		nres += "?" + oquery;
	     */
	    String nres = new_loc.getPath();
	    req.setRequestURI(nres);

	    try
		{ lastURI = new HttpClientURI(new_loc, nres); }
	    catch (ParseException pe)
		{ }

	    if (DebugMods)
		HttpClientUtil.logLine("RdirM: matched request in permanent " +
			     "redirection list - redoing request to " +
			     lastURI);

	    if (!sameServer(con, new_loc))
	    {
		try
		    { con = new HTTPConnection(new_loc); }
		catch (ProtocolNotSuppException pnse)
		{
		    throw new Error("HTTPClient Internal Error: unexpected " +
				    "exception '" + pnse + "'");
		}

		con.setContext(req.getConnection().getContext());
		req.setConnection(con);
		return REQ_NEWCON_RST;
	    }
	    else
	    {
		return REQ_RESTART;
	    }
	}

	return REQ_CONTINUE;
    }


    /**
     * Invoked by the HTTPClient.
     */
    public void responsePhase1Handler(Response resp, RoRequest req)
	    throws IOException
    {
	int sts  = resp.getStatusCode();
	if (sts < 301  ||  sts > 307  ||  sts == 304)
	{
	    if (lastURI != null)		// it's been redirected
		resp.setEffectiveURI(lastURI);
	}
    }


    /**
     * Invoked by the HTTPClient.
     */
    public int responsePhase2Handler(Response resp, Request req)
	    throws IOException
    {
	/* handle various response status codes until satisfied */

	int sts  = resp.getStatusCode();
	switch(sts)
	{
	    case 302: // General (temporary) Redirection (handle like 303)

		if (DebugMods)
		    HttpClientUtil.logLine("RdirM: Received status: " + sts + " " +
				 resp.getReasonLine() + " - treating as 303");

		sts = 303;

	    case 301: // Moved Permanently
	    case 303: // See Other (use GET)
	    case 307: // Moved Temporarily (we mean it!)

		if (DebugMods)
		    HttpClientUtil.logLine("RdirM: Handling status: " + sts + " " +
				 resp.getReasonLine());

		// the spec says automatic redirection may only be done if
		// the second request is a HEAD or GET.
		if (!req.getMethod().equals("GET")  &&
		    !req.getMethod().equals("HEAD")  &&
		    sts != 303)
		{
		    if (DebugMods)
			HttpClientUtil.logLine("RdirM: not redirected because method " +
				     "is neither HEAD nor GET");

		    if (sts == 301  &&  resp.getHeader("Location") != null)
			update_perm_redir_list(req, 
				    resLocHdr(resp.getHeader("Location"), req));

		    resp.setEffectiveURI(lastURI);
		    return RSP_CONTINUE;
		}

	    case 305: // Use Proxy
	    case 306: // Switch Proxy

		if (DebugMods)
		    if (sts == 305  ||  sts == 306)
			HttpClientUtil.logLine("RdirM: Handling status: " + sts + " " +
				     resp.getReasonLine());

		// Don't accept 305 from a proxy
		if (sts == 305  &&  req.getConnection().getProxyHost() != null)
		{
		    if (DebugMods)
			HttpClientUtil.logLine("RdirM: 305 ignored because a proxy is " +
				     "already in use");

		    resp.setEffectiveURI(lastURI);
		    return RSP_CONTINUE;
		}


		/* the level is a primitive way of preventing infinite
		 * redirections. RFC-2068 set the max to 5, but the latest
		 * http draft has loosened this. Since some sites (notably
		 * M$) need more levels, this is now set to the (arbitrary)
		 * value of 15 (god only knows why they need to do even 5
		 * redirections...).
		 */
		if (level == 15  ||  resp.getHeader("Location") == null)
		{
		    if (DebugMods)
		    {
			if (level == 15)
			    HttpClientUtil.logLine("RdirM: not redirected because there "+
					 "were too many levels of redirection");
			else
			    HttpClientUtil.logLine("RdirM: not redirected because no " +
					 "Location header was present");
		    }

		    resp.setEffectiveURI(lastURI);
		    return RSP_CONTINUE;
		}
		level++;

		HttpClientURI loc = resLocHdr(resp.getHeader("Location"), req);

		HTTPConnection mvd;
		String nres;
		new_con = false;

		if (sts == 305)
		{
		    mvd = new HTTPConnection(req.getConnection().getProtocol(),
					     req.getConnection().getHost(),
					     req.getConnection().getPort());
		    mvd.setCurrentProxy(loc.getHost(), loc.getPort());
		    mvd.setContext(req.getConnection().getContext());
		    new_con = true;

		    nres = req.getRequestURI();

		    /* There was some discussion about this, and especially
		     * Foteos Macrides (Lynx) said a 305 should also imply
		     * a change to GET (for security reasons) - see the thread
		     * starting at
		     * http://www.ics.uci.edu/pub/ietf/http/hypermail/1997q4/0351.html
		     * However, this is not in the latest draft, but since I
		     * agree with Foteos we do it anyway...
		    */
		    req.setMethod("GET");
		    req.setData(null);
		    req.setStream(null);
		}
		else if (sts == 306)
		{
		    // We'll have to wait for Josh to create a new spec here.
		    return RSP_CONTINUE;
		}
		else
		{
		    if (sameServer(req.getConnection(), loc))
		    {
			mvd  = req.getConnection();
			nres = loc.getPath();
		    }
		    else
		    {
			try
			{
			    mvd  = new HTTPConnection(loc);
			    nres = loc.getPath();
			}
			catch (ProtocolNotSuppException pnse)
			{
			    if (req.getConnection().getProxyHost() == null  ||
				!loc.getScheme().equalsIgnoreCase("ftp"))
				return RSP_CONTINUE;

			    // We're using a proxy and the protocol is ftp -
			    // maybe the proxy will also proxy ftp...
			    mvd  = new HTTPConnection("http",
					    req.getConnection().getProxyHost(),
					    req.getConnection().getProxyPort());
			    mvd.setCurrentProxy(null, 0);
			    nres = loc.toExternalForm();
			}

			mvd.setContext(req.getConnection().getContext());
			new_con = true;
		    }


		    /* copy query if present in old url but not in new url.
		     * This isn't strictly conforming, but some scripts fail
		     * to propogate the query properly to the Location
		     * header.
		     *
		     * See comment on line 120.
		    String oquery  = Util.getQuery(req.getRequestURI()),
			   nquery  = Util.getQuery(nres);
		    if (nquery == null  &&  oquery != null)
			nres += "?" + oquery;
		     */

		    if (sts == 303  &&  !req.getMethod().equals("HEAD"))
		    {
			// 303 means "use GET"

			req.setMethod("GET");
			req.setData(null);
			req.setStream(null);
		    }
		    else
		    {
			// If they used an output stream then they'll have
			// to do the resend themselves
			if (req.getStream() != null)
			{
			    saved_req = (Request) req.clone();
			    deferred_redir_list.put(req.getStream(), this);
			    req.getStream().reset();
			    resp.setRetryRequest(true);
			}

			if (sts == 301)
			    try
			    {
				update_perm_redir_list(req, new HttpClientURI(loc, nres));
			    }
			    catch (ParseException pe)
				{ /* ??? */ }
		    }

		    // Adjust Referer, if present
		    HttpClientUtil.updateValue(req.getHeaders(), "Referer",
				     req.getConnection()+req.getRequestURI());
		}

		req.setConnection(mvd);
		req.setRequestURI(nres);

		try { resp.getInputStream().close(); }
		catch (IOException ioe) { }

		if (sts == 305  ||  sts == 306)
		{
		    if (DebugMods)
			HttpClientUtil.logLine("RdirM: resending request using proxy " + 
				     mvd.getProxyHost() + ":" +
				     mvd.getProxyPort());
		}
		else
		{
		    try
			{ lastURI = new HttpClientURI(loc, nres); }
		    catch (ParseException pe)
			{ /* ??? */ }

		    if (DebugMods)
			HttpClientUtil.logLine("RdirM: request redirected to " + lastURI +
				     " using method " + req.getMethod());
		}

		if (req.getStream() != null)
		    return RSP_CONTINUE;
		else if (new_con)
		    return RSP_NEWCON_REQ;
		else
		    return RSP_REQUEST;

	    default:

		return RSP_CONTINUE;
	}
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
    public void trailerHandler(Response resp, RoRequest req)
    {
    }


    /**
     * Update the permanent redirection list.
     *
     * @param the original request
     8 @param the new location
     */
    private static void update_perm_redir_list(RoRequest req, HttpClientURI new_loc)
    {
	HTTPConnection con = req.getConnection();
	HttpClientURI cur_loc = null;
	try
	{
	    cur_loc = new HttpClientURI(con.getProtocol(),
			      con.getHost(),
			      con.getPort(),
			      req.getRequestURI());
	}
	catch (ParseException pe)
	    { }

	if (!cur_loc.equals(new_loc))
	{
	    Hashtable perm_redir_list =
			HttpClientUtil.getList(perm_redir_cntxt_list, con.getContext());
	    perm_redir_list.put(cur_loc, new_loc);
	}
    }


    /**
     * The Location header field must be an absolute URI, but too many broken
     * servers use relative URIs. So, try as an absolute URI, and if that
     * fails try as a relative URI.
     *
     * @param  loc the Location header field
     * @param  req the Request to resolve relative URI's relative to
     * @return an absolute URI corresponding to the Location header field
     * @ throws ProtocolException if the Location header field is completely
     *                            unparseable
     */
    private HttpClientURI resLocHdr(String loc, RoRequest req)  throws ProtocolException
    {
	try
	    { return new HttpClientURI(loc); }
	catch (ParseException pe)
	{
	    // it might be a relative URL (i.e. another broken server)
	    try
	    {
		HttpClientURI base = new HttpClientURI(req.getConnection().getProtocol(),
				   req.getConnection().getHost(),
				   req.getConnection().getPort(),
				   req.getRequestURI());
		return new HttpClientURI(base, loc);
	    }
	    catch (ParseException pe2)
	    {
		throw new ProtocolException("Malformed URL in Location " +
					    "header: " + loc);
	    }
	}
    }


    /**
     * Tries to determine as best as possible if <var>url</var> refers
     * to the same server as <var>con</var> is talking with.
     *
     * @param con the HTTPConnection
     * @param uri the http URI
     * @return true if the url refers to the same server as the connection,
     *         false otherwise.
     */
    private boolean sameServer(HTTPConnection con, HttpClientURI uri)
    {
	if (!uri.getScheme().equalsIgnoreCase(con.getProtocol()))
	    return false;

	/* we can't do this, because otherwise a server can't redirect to
	 * a new host name (that resolves to the same ip-address as the
	 * old host name).
	try
	{
	    compAddr: if (!uri.getHost().equalsIgnoreCase(con.getHost()))
	    {
		InetAddress[] list1 = InetAddress.getAllByName(uri.getHost());
		InetAddress[] list2 = InetAddress.getAllByName(con.getHost());
		for (int idx1=0; idx1<list1.length; idx1++)
		    for (int idx2=0; idx2<list2.length; idx2++)
			if (list1[idx1].equals(list2[idx2]))
			    break compAddr;
		return false;
	    }
	}
	catch (UnknownHostException uhe)
	    { return false; }
	 */
	if (!uri.getHost().equalsIgnoreCase(con.getHost()))
	    return false;

	if (uri.getPort() != con.getPort())
	    return false;

	return true;
    }


    private void copyFrom(RedirectionModule other)
    {
	this.level     = other.level;
	this.lastURI   = other.lastURI;
	this.saved_req = other.saved_req;
    }
}

