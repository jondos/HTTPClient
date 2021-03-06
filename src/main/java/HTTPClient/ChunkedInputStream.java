/*
 * @(#)ChunkedInputStream.java				0.3 30/01/1998
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
import java.io.EOFException;
import java.io.InputStream;
import java.io.FilterInputStream;


/**
 * This class de-chunks an input stream.
 *
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 */
class ChunkedInputStream extends FilterInputStream
{
    /**
     * @param is the input stream to dechunk
     */
    ChunkedInputStream(InputStream is)
    {
	super(is);
    }


    byte[] one = new byte[1];
    public synchronized int read() throws IOException
    {
	int b = read(one, 0, 1);
	if (b == 1)
	    return (one[0] & 0xff);
	else
	    return -1;
    }


    private int chunk_len = -1;
    private boolean eof   = false;

    public synchronized int read(byte[] buf, int off, int len)
	    throws IOException
    {
	if (eof)  return -1;

	if (chunk_len == -1)    // it's a new chunk
	{
	    try
		{ chunk_len = Codecs.getChunkLength(in); }
	    catch (ParseException pe)
		{ throw new IOException(pe.toString()); }
	}

	if (chunk_len > 0)              // it's data
	{
	    if (len > chunk_len)  len = chunk_len;
	    int rcvd = in.read(buf, off, len);
	    if (rcvd == -1)
		throw new EOFException("Premature EOF encountered");

	    chunk_len -= rcvd;
	    if (chunk_len == 0) // got the whole chunk
	    {
		in.read();  // CR
		in.read();  // LF
		chunk_len = -1;
	    }

	    return rcvd;
	}
	else    			// the footers (trailers)
	{
	    // discard
	    Request dummy =
		    new Request(null, null, null, null, null, null);
	    new Response(dummy, null).readTrailers(in);

	    eof = true;
	    return -1;
	}
    }


    public synchronized long skip(long num)  throws IOException
    {
	byte[] tmp = new byte[(int) num];
	int got = read(tmp, 0, (int) num);

	if (got > 0)
	    return (long) got;
	else
	    return 0L;
    }


    public synchronized int available()  throws IOException
    {
	if (eof)  return 0;

	if (chunk_len != -1)
	    return chunk_len + in.available();
	else
	    return in.available();
    }
}

