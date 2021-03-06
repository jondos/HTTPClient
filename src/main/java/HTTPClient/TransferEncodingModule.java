/*
 * @(#)TransferEncodingModule.java			0.3 30/01/1998
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
 * This module handles the TransferEncoding response header. It currently
 * handles the "gzip", "deflate", "compress", "chunked" and "identity"
 * tokens.
 *
 * Note: This module requires JDK 1.1 or later.
 *
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 * @author      modified by Stefan K&ouml;psell, 04/11/24
 */

class TransferEncodingModule implements HTTPClientModule, GlobalConstants
{
  /** @author modified by Stefan K&ouml;psell, 04/11/24 */
   // static
   // {
	/* This ensures that the loading of this class is only successful
	 * if we're in JDK 1.1 (or later) and have access to java.util.zip
	 */
	//new InflaterInputStream(null);
   //}
   /** @author modified by Stefan K&ouml;psell, 04/11/24 */

    // Constructors

    TransferEncodingModule()
    {
    }


    // Methods

    /**
     * Invoked by the HTTPClient.
     */
    public int requestHandler(Request req, Response[] resp)
	    throws ModuleException
    {
	// Parse TE header

	NVPair[] hdrs = req.getHeaders();
	int idx = HttpClientUtil.getIndex(hdrs, "TE");

	Vector pte;
	if (idx == -1)
	{
	    idx = hdrs.length;
	    hdrs = HttpClientUtil.resizeArray(hdrs, idx+1);
	    req.setHeaders(hdrs);
	    pte = new Vector();
	}
	else
	{
	    try
		{ pte = HttpClientUtil.parseHeader(hdrs[idx].getValue()); }
	    catch (ParseException pe)
		{ throw new ModuleException(pe.toString()); }
	}


        // done if "*;q=1.0" present

        HttpHeaderElement all = HttpClientUtil.getElement(pte, "*");
        if (all != null)
	{
	    NVPair[] params = all.getParams();
	    for (idx=0; idx<params.length; idx++)
		if (params[idx].getName().equalsIgnoreCase("q"))  break;

	    if (idx == params.length)   // no qvalue, i.e. q=1.0
		return REQ_CONTINUE;

	    if (params[idx].getValue() == null  ||
		params[idx].getValue().length() == 0)
		throw new ModuleException("Invalid q value for \"*\" in TE " +
					  "header: ");

	    try
	    {
		if (Float.valueOf(params[idx].getValue()).floatValue() > 0.)
		    return REQ_CONTINUE;
	    }
	    catch (NumberFormatException nfe)
	    {
		throw new ModuleException("Invalid q value for \"*\" in TE " +
					  "header: " + nfe.getMessage());
	    }
	}


	// Add gzip, deflate and compress tokens to the TE header

	if (!pte.contains(new HttpHeaderElement("deflate")))
	    pte.addElement(new HttpHeaderElement("deflate"));
	if (!pte.contains(new HttpHeaderElement("gzip")))
	    pte.addElement(new HttpHeaderElement("gzip"));
	if (!pte.contains(new HttpHeaderElement("compress")))
	    pte.addElement(new HttpHeaderElement("compress"));

	hdrs[idx] = new NVPair("TE", HttpClientUtil.assembleHeader(pte));

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
	String te = resp.getHeader("Transfer-Encoding");
	if (te == null  ||  req.getMethod().equals("HEAD"))
	    return;

	Vector pte;
	try
	    { pte = HttpClientUtil.parseHeader(te); }
	catch (ParseException pe)
	    { throw new ModuleException(pe.toString()); }

	while (pte.size() > 0)
	{
	    String encoding = ((HttpHeaderElement) pte.lastElement()).getName();
	    if (encoding.equalsIgnoreCase("gzip"))
	    {
		if (DebugMods) HttpClientUtil.logLine("TEM:   pushing gzip-input-stream");

		resp.inp_stream = new GZIPInputStream(resp.inp_stream);
	    }
	    else if (encoding.equalsIgnoreCase("deflate"))
	    {
		if (DebugMods)
		    HttpClientUtil.logLine("TEM:   pushing inflater-input-stream");

		resp.inp_stream = new InflaterInputStream(resp.inp_stream);
	    }
	    else if (encoding.equalsIgnoreCase("compress"))
	    {
		if (DebugMods)
		    HttpClientUtil.logLine("TEM:   pushing uncompress-input-stream");

		resp.inp_stream = new UncompressInputStream(resp.inp_stream);
	    }
	    else if (encoding.equalsIgnoreCase("chunked"))
	    {
		if (DebugMods)
		    HttpClientUtil.logLine("TEM:   pushing chunked-input-stream");

		resp.inp_stream = new ChunkedInputStream(resp.inp_stream);
	    }
	    else if (encoding.equalsIgnoreCase("identity"))
	    {
		if (DebugMods) HttpClientUtil.logLine("TEM:   ignoring 'identity' token");
	    }
	    else
	    {
		if (DebugMods)
		    HttpClientUtil.logLine("TEM:   Unknown transfer encoding '" +
				 encoding + "'");

		break;
	    }

	    pte.removeElementAt(pte.size()-1);
	}

	if (pte.size() > 0)
	    resp.setHeader("Transfer-Encoding", HttpClientUtil.assembleHeader(pte));
	else
	    resp.deleteHeader("Transfer-Encoding");
    }


    /**
     * Invoked by the HTTPClient.
     */
    public void trailerHandler(Response resp, RoRequest req)
    {
    }
}

