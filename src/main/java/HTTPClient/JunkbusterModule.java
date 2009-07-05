/*
 * @(#)JunkbusterModule.java				0.4 30/03/1998
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

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Date;


/**
 * This module provides a similar functionality as the
 * <A HREF="http://internet.junkbuster.com/">Junkbuster</A>. It will
 * block any request for a URL which matches a blocklist; it will also
 * remove the <var>From</var>, <var>User-Agent</var>, and <var>Referer</var>
 * headers, if so instructed. In contrast with the real thing this
 * module won't touch cookies because the CookieModule already allows
 * you to disable cookie support. Also, unlike the real thing, this
 * module will not hide the IP-address.
 *
 * <P>The following properties are used by this module:
 * <DL>
 * <DT>HTTPClient.junkbuster.blockfile
 *     <DD>points to the blocklist file.
 * <DT>HTTPClient.junkbuster.remove_from
 *     <DD>if <var>true</var> causes the <var>From</var> header to be removed
 * <DT>HTTPClient.junkbuster.remove_useragent
 *     <DD>if <var>true</var> causes the <var>User-Agent</var> header to be
 *     removed
 * <DT>HTTPClient.junkbuster.remove_referer
 *     <DD>if <var>true</var> causes the <var>Referer</var> header to be removed
 * </DL>
 *
 * @version	0.4  02/04/1998
 * @author	Ronald Tschal&auml;r
 */

public class JunkbusterModule implements HTTPClientModule, GlobalConstants
{
    /** the blocklist */
    private static String    bl_file;
    private static String[]  bl_lines;
    private static String[]  bl_hosts;
    private static int[]     bl_ports;
    private static String[]  bl_paths;
    private static boolean[] bl_block;

    /** remove the From header? */
    private static boolean remove_from;

    /** remove the User-Agent header? */
    private static boolean remove_ua;

    /** remove the Referer header? */
    private static boolean remove_referer;


    static
    {
	// read remove_xxx properties

	try
	    { remove_from = Boolean.getBoolean("HTTPClient.junkbuster.remove_from"); }
	catch (Exception e)
	    { remove_from = false; }
	try
	    { remove_ua = Boolean.getBoolean("HTTPClient.junkbuster.remove_useragent"); }
	catch (Exception e)
	    { remove_ua = false; }
	try
	    { remove_referer = Boolean.getBoolean("HTTPClient.junkbuster.remove_referer"); }
	catch (Exception e)
	    { remove_referer = false; }


	// read blockfile

	try
	    { bl_file = System.getProperty("HTTPClient.junkbuster.blockfile"); }
	catch (Exception e)
	    { bl_file = null; }
	if (DebugMods)
	    if (bl_file != null)
		Util.logLine("JBM:   reading blockfile " + bl_file);
	readBlocklist(bl_file);
    }


    // Methods

    /**
     * Invoked by the HTTPClient.
     */
    public int requestHandler(Request req, Response[] resp)
    {
	// check blocklist

	String rule;
	if ((rule = isBlocked(req)) != null)
	{
	    NVPair[] ct  = { new NVPair("Content-type", "text/plain") };
	    byte[]   msg = ("JunkbusterModule: this url was blocked by the " +
			    "rule '" + rule + "'").getBytes();
	    resp[0] =
		new Response("HTTP/1.1", 403, "Forbidden", ct, msg, null, 0);

	    if (DebugMods) Util.logLine("JBM:   '" + req.getConnection() +
					req.getRequestURI() + "' blocked by " +
					"rule '" + rule + "'");

	    return REQ_RETURN;
	}


	// remove headers

	NVPair[] hdrs = req.getHeaders();

	if (remove_from)
	    hdrs = Util.removeAllValues(hdrs, "From");
	if (remove_ua)
	    hdrs = Util.removeAllValues(hdrs, "User-Agent");
	if (remove_referer)
	    hdrs = Util.removeAllValues(hdrs, "Referer");

	req.setHeaders(hdrs);


	// all done

	return REQ_CONTINUE;
    }


    /**
     * Invoked by the HTTPClient.
     */
    public void responsePhase1Handler(Response resp, RoRequest req)
	    throws IOException, ModuleException
    {
    }


    /**
     * Invoked by the HTTPClient.
     */
    public int responsePhase2Handler(Response resp, Request req)
	    throws IOException
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
    public void trailerHandler(Response resp, RoRequest req)
    {
    }


    /**
     * Enable/disable removing the From header.
     *
     * @param flag if true, causes the From header to be removed from
     *             requests
     */
    public static void removeFrom(boolean flag)
    {
	remove_from = flag;
    }


    /**
     * Enable/disable removing the User-Agent header.
     *
     * @param flag if true, causes the User-Agent header to be removed from
     *             requests
     */
    public static void removeUserAgent(boolean flag)
    {
	remove_ua = flag;
    }


    /**
     * Enable/disable removing the Referer header.
     *
     * @param flag if true, causes the Referer header to be removed from
     *             requests
     */
    public static void removeReferer(boolean flag)
    {
	remove_referer = flag;
    }


    private static String isBlocked(RoRequest req)
    {
	String host = req.getConnection().getHost();
	int    port = req.getConnection().getPort();
	String path = Util.getPath(req.getRequestURI());
	boolean blocked = false;
	String rule = null;

	for (int idx=0; idx<bl_hosts.length; idx++)
	{
	    final String bl_host = bl_hosts[idx],
		         bl_path = bl_paths[idx];
	    final int    bl_port = bl_ports[idx];

	    if ((bl_host == null  ||
		 bl_host.equals(host)  ||
		 (bl_host.length() < host.length()  &&
		  host.endsWith(bl_host)  &&
		  host.charAt(host.length()-bl_host.length()-1) == '.'))
		&&
		(bl_port == -1  ||  bl_port == port)
		&&
		(bl_path == null  ||  path.startsWith(bl_path)) )
	    {
		blocked = bl_block[idx];
		rule    = bl_lines[idx];
	    }
	}

	return (blocked ? rule : null);
    }


    private static synchronized void readBlocklist(String file)
    {
	if (file == null)  return;

	try
	{
	    BufferedReader blockfile = new BufferedReader(new FileReader(file));

	    bl_lines = new String[100];
	    bl_hosts = new String[100];
	    bl_ports = new int[100];
	    bl_paths = new String[100];
	    bl_block = new boolean[100];
	    int pos = 0;

	    String line;
	    while ((line = blockfile.readLine()) != null)
	    {
		if (pos == bl_hosts.length)
		{
		    bl_lines = Util.resizeArray(bl_lines, pos + 100);
		    bl_hosts = Util.resizeArray(bl_hosts, pos + 100);
		    bl_ports = Util.resizeArray(bl_ports, pos + 100);
		    bl_paths = Util.resizeArray(bl_paths, pos + 100);
		    bl_block = Util.resizeArray(bl_block, pos + 100);
		}


		// remove comments and ignore blank lines

		int beg = line.indexOf('#');
		if (beg != -1)
		    line = line.substring(0, beg);
		line = line.trim();
		if (line.length() == 0)  continue;

		bl_lines[pos] = line;


		// determine whether positive or negative rule

		beg = 0;
		if (line.charAt(0) == '~')
		{
		    bl_block[pos] = false;
		    beg = 1;
		}
		else
		    bl_block[pos] = true;


		// get domain:port part

		if (line.charAt(beg) != '/')
		{
		    int end = line.indexOf('/');
		    if (end == -1)  end = line.length();
		    int col = line.indexOf(':');
		    if (col > end)  col = -1;

		    if (col != -1)
		    {
			if (col > beg)
			    bl_hosts[pos] = line.substring(beg, col);
			bl_ports[pos] =
				Integer.parseInt(line.substring(col+1, end));
		    }
		    else
		    {
			bl_hosts[pos] = line.substring(beg, end);
			bl_ports[pos] = -1;
		    }

		    beg = end;
		}
		else
		{
		    bl_ports[pos] = -1;
		}


		// get path part

		if (beg < line.length())
		    bl_paths[pos] = line.substring(beg);

		pos++;
	    }

	    bl_lines = Util.resizeArray(bl_lines, pos);
	    bl_hosts = Util.resizeArray(bl_hosts, pos);
	    bl_ports = Util.resizeArray(bl_ports, pos);
	    bl_paths = Util.resizeArray(bl_paths, pos);
	    bl_block = Util.resizeArray(bl_block, pos);
	}
	catch (Exception e)
	{
	    bl_lines = new String[0];
	    bl_hosts = new String[0];
	    bl_ports = new int[0];
	    bl_paths = new String[0];
	    bl_block = new boolean[0];

	    if (DebugMods) Util.logLine("JBM:   Error reading `" + bl_file +
					"': " + e);
	}
    }
}

