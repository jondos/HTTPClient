/*
 * @(#)ContentEncodingModule.java			0.3 30/01/1998
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

import java.io.IOException;
import java.util.Vector;
import java.util.zip.InflaterInputStream;
import java.util.zip.GZIPInputStream;


/**
 * This module handles the Content-Encoding response header. It currently
 * handles the "gzip", "deflate", "compress" and "identity" tokens.
 *
 * Note: This module requires JDK 1.1 or later.
 *
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 * @author      modified by Stefan K&ouml;psell, 04/11/24
 */

class ContentEncodingModule implements HTTPClientModule, GlobalConstants
{
  /** @author modified by Stefan K&ouml;psell, 04/11/24*/
   /* static
    {
	// This ensures that the loading of this class is only successful
	 // if we're in JDK 1.1 (or later) and have access to java.util.zip
	 //
	new InflaterInputStream(null);
    }*/
   /** @author modified by Stefan K&ouml;psell, 04/11/24 */

    // Constructors

    ContentEncodingModule()
    {
    }


    // Methods

    /**
     * Invoked by the HTTPClient.
     */
    public int requestHandler(Request req, Response[] resp)
	    throws ModuleException
    {
	// parse Accept-Encoding header

	NVPair[] hdrs = req.getHeaders();
	int idx = HttpClientUtil.getIndex(hdrs, "Accept-Encoding");

	Vector pae;
	if (idx == -1)
	{
	    idx = hdrs.length;
	    hdrs = HttpClientUtil.resizeArray(hdrs, idx+1);
	    req.setHeaders(hdrs);
	    pae = new Vector();
	}
	else
	{
	    try
		{ pae = HttpClientUtil.parseHeader(hdrs[idx].getValue()); }
	    catch (ParseException pe)
		{ throw new ModuleException(pe.toString()); }
	}


	// done if "*;q=1.0" present

	HttpHeaderElement all = HttpClientUtil.getElement(pae, "*");
	if (all != null)
	{
	    NVPair[] params = all.getParams();
	    for (idx=0; idx<params.length; idx++)
		if (params[idx].getName().equalsIgnoreCase("q"))  break;

	    if (idx == params.length)	// no qvalue, i.e. q=1.0
		return REQ_CONTINUE;

	    if (params[idx].getValue() == null  ||
		params[idx].getValue().length() == 0)
		throw new ModuleException("Invalid q value for \"*\" in " +
					  "Accept-Encoding header: ");

	    try
	    {
		if (Float.valueOf(params[idx].getValue()).floatValue() > 0.)
		    return REQ_CONTINUE;
	    }
	    catch (NumberFormatException nfe)
	    {
		throw new ModuleException("Invalid q value for \"*\" in " +
				"Accept-Encoding header: " + nfe.getMessage());
	    }
	}


	// Add gzip, deflate and compress tokens to the Accept-Encoding header

	if (!pae.contains(new HttpHeaderElement("deflate")))
	    pae.addElement(new HttpHeaderElement("deflate"));
	if (!pae.contains(new HttpHeaderElement("gzip")))
	    pae.addElement(new HttpHeaderElement("gzip"));
	if (!pae.contains(new HttpHeaderElement("x-gzip")))
	    pae.addElement(new HttpHeaderElement("x-gzip"));
	if (!pae.contains(new HttpHeaderElement("compress")))
	    pae.addElement(new HttpHeaderElement("compress"));
	if (!pae.contains(new HttpHeaderElement("x-compress")))
	    pae.addElement(new HttpHeaderElement("x-compress"));

	hdrs[idx] = new NVPair("Accept-Encoding", HttpClientUtil.assembleHeader(pae));

	return REQ_CONTINUE;
    }


    /**
     * Invoked by the HTTPClient.
     */
    public void responsePhase1Handler(Response resp, RoRequest req)
    {
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
		throws IOException, ModuleException
    {
	String ce = resp.getHeader("Content-Encoding");
	if (ce == null  ||  req.getMethod().equals("HEAD"))
	    return;

	Vector pce;
	try
	    { pce = HttpClientUtil.parseHeader(ce); }
	catch (ParseException pe)
	    { throw new ModuleException(pe.toString()); }

	if (pce.size() == 0)
	    return;

	String encoding = ((HttpHeaderElement) pce.firstElement()).getName();
	if (encoding.equalsIgnoreCase("gzip")  ||
	    encoding.equalsIgnoreCase("x-gzip"))
	{
	    if (DebugMods) HttpClientUtil.logLine("CEM:   pushing gzip-input-stream");

	    resp.inp_stream = new GZIPInputStream(resp.inp_stream);
	    pce.removeElementAt(pce.size()-1);
	    resp.deleteHeader("Content-length");
	}
	else if (encoding.equalsIgnoreCase("deflate"))
	{
	    if (DebugMods) HttpClientUtil.logLine("CEM:   pushing inflater-input-stream");

	    resp.inp_stream = new InflaterInputStream(resp.inp_stream);
	    pce.removeElementAt(pce.size()-1);
	    resp.deleteHeader("Content-length");
	}
	else if (encoding.equalsIgnoreCase("compress")  ||
		 encoding.equalsIgnoreCase("x-compress"))
	{
	    if (DebugMods)
		HttpClientUtil.logLine("CEM:   pushing uncompress-input-stream");

	    resp.inp_stream = new UncompressInputStream(resp.inp_stream);
	    pce.removeElementAt(pce.size()-1);
	    resp.deleteHeader("Content-length");
	}
	else if (encoding.equalsIgnoreCase("identity"))
	{
	    if (DebugMods) HttpClientUtil.logLine("CEM:   ignoring 'identity' token");
	    pce.removeElementAt(pce.size()-1);
	}
	else
	{
	    if (DebugMods)
		HttpClientUtil.logLine("CEM:   Unknown content encoding '" + encoding +
			     "'");
	}

	if (pce.size() > 0)
	    resp.setHeader("Content-Encoding", HttpClientUtil.assembleHeader(pce));
	else
	    resp.deleteHeader("Content-Encoding");
    }


    /**
     * Invoked by the HTTPClient.
     */
    public void trailerHandler(Response resp, RoRequest req)
    {
    }
}

