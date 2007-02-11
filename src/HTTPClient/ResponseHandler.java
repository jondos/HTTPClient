/*
 * @(#)ResponseHandler.java				0.4 30/01/1998
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

/**
 * This holds various information about an active response. Used by the
 * StreamDemultiplexor and RespInputStram.
 *
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 * @since	V0.2
 *
 * @author      modified by Stefan Lieske, 2005/02/15
 */
final class ResponseHandler implements GlobalConstants
{
    /** the response stream */
    RespInputStream     stream;

    /** the response class */
    Response            resp;

    /** the response class */
    Request             request;

    /** signals that the demux has closed the response stream, and that
	therefore no more data can be read */
    boolean             eof = false;

    /** this is non-null if the stream has an exception pending */
    IOException         exception = null;


    /**
     * Creates a new handler. This also allocates the response input
     * stream.
     *
     * @param resp     the reponse
     * @param request  the request
     * @param demux    our stream demultiplexor.
     */
    ResponseHandler(Response resp, Request request, StreamDemultiplexor demux)
    {
	this.resp     = resp;
	this.request  = request;
	this.stream   = new RespInputStream(demux, this);

	if (DebugDemux)
	    Util.logLine("Demux: Opening stream " + this.stream.hashCode() +
			 " (" + demux.hashCode() + ")");
    }


    private boolean set_terminator = false;

    /**
     * Gets the boundary string, compiles it for searching, and initializes
     * the buffered input stream.
     */
    /** @author  modified by Stefan Lieske, 2005/02/15 */
    //public void setupBoundary(ExtBufferedInputStream MasterStream)
    public void setupBoundary(DemultiplexorInputStream MasterStream)

		throws IOException, ParseException
    {
	if (set_terminator)  return;

	String endstr = "--" + Util.getParameter("boundary",
			    resp.getHeader("Content-Type")) +
			"--\r\n";
	byte[] endbndry = endstr.getBytes();
	int[] end_cmp = Util.compile_search(endbndry);
	MasterStream.setTerminator(endbndry, end_cmp);
	set_terminator = true;
    }
}

