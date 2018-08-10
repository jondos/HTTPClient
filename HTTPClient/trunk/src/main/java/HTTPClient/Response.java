/*
 * @(#)Response.java					0.4 04/02/1998
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

import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.EOFException;
import java.net.ProtocolException;
import java.util.Date;
import java.util.Vector;
import java.util.StringTokenizer;
import java.util.NoSuchElementException;


/**
 * This class represents an intermediate response. It's used internally by the
 * modules. When all modules have handled the response then the HTTPResponse
 * fills in its fields with the data from this class.
 *
 * @version	0.4  04/02/1998
 * @author	Ronald Tschal&auml;r
 */

public final class Response implements RoResponse, GlobalConstants, Cloneable
{
    /** our http connection */
    private HTTPConnection connection;

    /** our stream demux */
    private StreamDemultiplexor stream_handler;

    /** the HTTPResponse we're coupled with */
            HTTPResponse http_resp;

    /** the timeout for read operations */
            int          timeout = 0;

    /** our input stream (usually from the stream demux). Push input streams
     *  onto this if necessary. */
    public  InputStream  inp_stream;

    /** our response input stream from the stream demux */
    private RespInputStream  resp_inp_stream = null;

    /** the method used in the request */
    private String       method;

    /** the resource in the request (for debugging purposes) */
            String       resource;

    /** was a proxy used for the request? */
    private boolean      used_proxy;

    /** did the request contain an entity? */
    private boolean      sent_entity;

    /** the status code returned. */
            int          StatusCode = 0;

    /** the reason line associated with the status code. */
            String       ReasonLine;

    /** the HTTP version of the response. */
            String       Version;

    /** the final URI of the document. */
            HttpClientURI          EffectiveURI = null;

    /** any headers which were received and do not fit in the above list. */
            CIHashtable  Headers = new CIHashtable();

    /** any trailers which were received and do not fit in the above list. */
            CIHashtable  Trailers = new CIHashtable();

    /** the ContentLength of the data. */
            int          ContentLength = -1;

    /** this indicates how the length of the entity body is determined */
            int          cd_type = CD_NONE;

    /** the data (body) returned. */
            byte[]       Data = null;

    /** signals if we in the process of reading the headers */
            boolean      reading_headers = false;

    /** signals if we have got and parsed the headers yet */
            boolean      got_headers = false;

    /** signals if we have got and parsed the trailers yet */
            boolean      got_trailers = false;

    /** signals if reading the response data was interrupted */
    private boolean      interrupted = false;

    /** remembers any exception received while reading/parsing headers */
    private IOException  exception = null;

    /** should this response be handled further? */
            boolean      final_resp = false;

    /** should the request be retried by the application? */
            boolean      retry = false;


    // Constructors

    /**
     * Creates a new Response and registers it with the stream-demultiplexor.
     */
    Response(Request request, boolean used_proxy,
	     StreamDemultiplexor stream_handler)
	    throws IOException
    {
	this.connection     = request.getConnection();
	this.method         = request.getMethod();
	this.resource       = request.getRequestURI();
	this.used_proxy     = used_proxy;
	this.stream_handler = stream_handler;
	sent_entity         = (request.getData() != null) ? true : false;

	stream_handler.register(this, request);
	resp_inp_stream     = stream_handler.getStream(this);
	inp_stream          = resp_inp_stream;
    }


    /**
     * Creates a new Response that reads from the given stream. This is
     * used for the CONNECT subrequest which is used in establishing an
     * SSL tunnel through a proxy.
     *
     * @param request the subrequest
     * @param is      the input stream from which to read the headers and
     *                data.
     */
    Response(Request request, InputStream is)
    {
	this.connection = request.getConnection();
	this.method     = request.getMethod();
	this.resource   = request.getRequestURI();
	used_proxy      = false;
	stream_handler  = null;
	sent_entity     = (request.getData() != null) ? true : false;
	inp_stream      = is;
    }


    /**
     * Create a new response with the given info. This is used when
     * creating a response in a requestHandler().
     *
     * <P>If <var>data</var> is not null then that is used; else if the
     * <var>is</var> is not null that is used; else the entity is empty.
     * If the input stream is used then <var>cont_len</var> specifies
     * the length of the data that can be read from it, or -1 if unknown.
     *
     * @param version  the response version (such as "HTTP/1.1")
     * @param status   the status code
     * @param reason   the reason line
     * @param headers  the response headers
     * @param data     the response entity
     * @param is       the response entity as an InputStream
     * @param cont_len the length of the data in the InputStream
     */
    public Response(String version, int status, String reason, NVPair[] headers,
		    byte[] data, InputStream is, int cont_len)
    {
	this.Version    = version;
	this.StatusCode = status;
	this.ReasonLine = reason;
	if (headers != null)
	    for (int idx=0; idx<headers.length; idx++)
		setHeader(headers[idx].getName(), headers[idx].getValue());
	if (data != null)
	    this.Data   = data;
	else if (is == null)
	    this.Data   = new byte[0];
	else
	{
	    this.inp_stream = is;
	    ContentLength   = cont_len;
	}

	got_headers  = true;
	got_trailers = true;
    }


    // Methods

    /**
     * give the status code for this request. These are grouped as follows:
     * <UL>
     *   <LI> 1xx - Informational (new in HTTP/1.1)
     *   <LI> 2xx - Success
     *   <LI> 3xx - Redirection
     *   <LI> 4xx - Client Error
     *   <LI> 5xx - Server Error
     * </UL>
     *
     * @exception IOException If any exception occurs on the socket.
     */
    public final int getStatusCode()  throws IOException
    {
	if (!got_headers)  getHeaders(true);
	return StatusCode;
    }

    /**
     * give the reason line associated with the status code.
     *
     * @exception IOException If any exception occurs on the socket.
     */
    public final String getReasonLine()  throws IOException
    {
	if (!got_headers)  getHeaders(true);
	return ReasonLine;
    }

    /**
     * get the HTTP version used for the response.
     *
     * @exception IOException If any exception occurs on the socket.
     */
    public final String getVersion()  throws IOException
    {
	if (!got_headers)  getHeaders(true);
	return Version;
    }

    /**
     * Wait for either a '100 Continue' or an error.
     *
     * @return the return status.
     */
    int getContinue()  throws IOException
    {
	getHeaders(false);
	return StatusCode;
    }

    /**
     * get the final URI of the document. This is set if the original
     * request was deferred via the "moved" (301, 302, or 303) return
     * status.
     *
     * @exception IOException If any exception occurs on the socket.
     */
    public final HttpClientURI getEffectiveURI()  throws IOException
    {
	if (!got_headers)  getHeaders(true);
	return EffectiveURI;
    }

    /**
     * set the final URI of the document. This is only for internal use.
     */
    public void setEffectiveURI(HttpClientURI final_uri)
    {
	EffectiveURI = final_uri;
    }

    /**
     * retrieves the field for a given header.
     *
     * @param  hdr the header name.
     * @return the value for the header, or null if non-existent.
     * @exception IOException If any exception occurs on the socket.
     */
    public String getHeader(String hdr)  throws IOException
    {
	if (!got_headers)  getHeaders(true);
	return (String) Headers.get(hdr.trim());
    }

    /**
     * retrieves the field for a given header. The value is parsed as an
     * int.
     *
     * @param  hdr the header name.
     * @return the value for the header if the header exists
     * @exception NumberFormatException if the header's value is not a number
     *                                  or if the header does not exist.
     * @exception IOException if any exception occurs on the socket.
     */
    public int getHeaderAsInt(String hdr)
		throws IOException, NumberFormatException
    {
	return Integer.parseInt(getHeader(hdr));
    }

    /**
     * retrieves the field for a given header. The value is parsed as a
     * date; if this fails it is parsed as a long representing the number
     * of seconds since 12:00 AM, Jan 1st, 1970. If this also fails an
     * IllegalArgumentException is thrown.
     *
     * <P>Note: When sending dates use Util.httpDate().
     *
     * @param  hdr the header name.
     * @return the value for the header, or null if non-existent.
     * @exception IOException If any exception occurs on the socket.
     * @exception IllegalArgumentException If the header cannot be parsed
     *            as a date or time.
     */
    public Date getHeaderAsDate(String hdr)
		throws IOException, IllegalArgumentException
    {
	String raw_date = getHeader(hdr);
	if (raw_date == null)  return null;

	// asctime() format is missing an explicit GMT specifier
	if (raw_date.toUpperCase().indexOf("GMT") == -1)
	    raw_date += " GMT";

	Date date;

	try
	    { 
            date = HttpClientUtil.parseDate(raw_date); }
	catch (IllegalArgumentException iae)
	{
	    long time;
	    try
		{ time = Long.parseLong(raw_date); }
	    catch (NumberFormatException nfe)
		{ throw iae; }
	    if (time < 0)  time = 0;
	    date = new Date(time * 1000L);
	}

	return date;
    }


    /**
     * Set a header field in the list of headers. If the header already
     * exists it will be overwritten; otherwise the header will be added
     * to the list. This is used by some modules when they process the
     * header so that higher level stuff doesn't get confused when the
     * headers and data don't match.
     *
     * @param header The name of header field to set.
     * @param value  The value to set the field to.
     */
    public void setHeader(String header, String value)
    {
	Headers.put(header.trim(), value.trim());
    }


    /**
     * Removes a header field from the list of headers. This is used by
     * some modules when they process the header so that higher level stuff
     * doesn't get confused when the headers and data don't match.
     *
     * @param header The name of header field to remove.
     */
    public void deleteHeader(String header)
    {
	Headers.remove(header.trim());
    }


    /**
     * Retrieves the field for a given trailer. Note that this should not
     * be invoked until all the response data has been read. If invoked
     * before, it will force the data to be read via <code>getData()</code>.
     *
     * @param  trailer the trailer name.
     * @return the value for the trailer, or null if non-existent.
     * @exception IOException If any exception occurs on the socket.
     */
    public String getTrailer(String trailer)  throws IOException
    {
	if (!got_trailers)  getTrailers();
	return (String) Trailers.get(trailer.trim());
    }


    /**
     * Retrieves the field for a given tailer. The value is parsed as an
     * int.
     *
     * @param  trailer the tailer name.
     * @return the value for the trailer if the trailer exists
     * @exception NumberFormatException if the trailer's value is not a number
     *                                  or if the trailer does not exist.
     * @exception IOException if any exception occurs on the socket.
     */
    public int getTrailerAsInt(String trailer)
		throws IOException, NumberFormatException
    {
	return Integer.parseInt(getTrailer(trailer));
    }


    /**
     * Retrieves the field for a given trailer. The value is parsed as a
     * date; if this fails it is parsed as a long representing the number
     * of seconds since 12:00 AM, Jan 1st, 1970. If this also fails an
     * IllegalArgumentException is thrown.
     *
     * <P>Note: When sending dates use Util.httpDate().
     *
     * @param  trailer the trailer name.
     * @return the value for the trailer, or null if non-existent.
     * @exception IllegalArgumentException if the trailer's value is neither a
     *            legal date nor a number.
     * @exception IOException if any exception occurs on the socket.
     * @exception IllegalArgumentException If the header cannot be parsed
     *            as a date or time.
     */
    public Date getTrailerAsDate(String trailer)
		throws IOException, IllegalArgumentException
    {
	String raw_date = getTrailer(trailer);
	if (raw_date == null) return null;

	// asctime() format is missing an explicit GMT specifier
	if (raw_date.toUpperCase().indexOf("GMT") == -1)
	    raw_date += " GMT";

	Date   date;

	try
	    { date = HttpClientUtil.parseDate(raw_date); }
	catch (IllegalArgumentException iae)
	{
	    // some servers erroneously send a number, so let's try that
	    long time;
	    try
		{ time = Long.parseLong(raw_date); }
	    catch (NumberFormatException nfe)
		{ throw iae; }	// give up
	    if (time < 0)  time = 0;
	    date = new Date(time * 1000L);
	}

	return date;
    }


    /**
     * Set a trailer field in the list of trailers. If the trailer already
     * exists it will be overwritten; otherwise the trailer will be added
     * to the list. This is used by some modules when they process the
     * trailer so that higher level stuff doesn't get confused when the
     * trailer and data don't match.
     *
     * @param trailer The name of trailer field to set.
     * @param value   The value to set the field to.
     */
    public void setTrailer(String trailer, String value)
    {
	Trailers.put(trailer.trim(), value.trim());
    }


    /**
     * Removes a trailer field from the list of trailers. This is used by
     * some modules when they process the trailer so that higher level stuff
     * doesn't get confused when the trailers and data don't match.
     *
     * @param trailer The name of trailer field to remove.
     */
    public void deleteTrailer(String trailer)
    {
	Trailers.remove(trailer.trim());
    }


    /**
     * Reads all the response data into a byte array. Note that this method
     * won't return until <em>all</em> the data has been received (so for
     * instance don't invoke this method if the server is doing a server
     * push). If getInputStream() had been previously called then this method
     * only returns any unread data remaining on the stream and then closes
     * it.
     *
     * @see #getInputStream()
     * @return an array containing the data (body) returned. If no data
     *         was returned then it's set to a zero-length array.
     * @exception IOException If any io exception occured while reading
     *			      the data
     */
    public synchronized byte[] getData()  throws IOException
    {
	if (!got_headers)  getHeaders(true);

	if (Data == null  ||  interrupted)
	{
	    try
		{ readResponseData(inp_stream); }
	    catch (InterruptedIOException ie)		// don't intercept
		{ throw ie; }
	    catch (IOException ioe)
	    {
		if (DebugResp)
		{
		    HttpClientUtil.logLine("Resp:  (" + inp_stream.hashCode() + ")");
		    HttpClientUtil.logMessage("       ");
		    HttpClientUtil.logStackTrace(ioe);
		}
		try { inp_stream.close(); } catch (Exception e) { }
		throw ioe;
	    }

	    inp_stream.close();
	}

	return Data;
    }

    /**
     * Gets an input stream from which the returned data can be read. Note
     * that if getData() had been previously called it will actually return
     * a ByteArrayInputStream created from that data.
     *
     * @see #getData()
     * @return the InputStream.
     * @exception IOException If any exception occurs on the socket.
     */
    public synchronized InputStream getInputStream()  throws IOException
    {
	if (!got_headers)  getHeaders(true);

	if (Data == null)
	    return inp_stream;
	else
	    return new ByteArrayInputStream(Data);
    }

    /**
     * Some responses such as those from a HEAD or with certain status
     * codes don't have an entity. This is detected by the client and
     * can be queried here. Note that this won't try to do a read() on
     * the input stream (it will however cause the headers to be read
     * and parsed if not already done).
     *
     * @return true if the response has an entity, false otherwise
     */
    public synchronized boolean hasEntity()  throws IOException
    {
	if (!got_headers)  getHeaders(true);

	return (cd_type != CD_0);
    }

    /**
     * Should the request be retried by the application? This can be used
     * by modules to signal to the application that it should retry the
     * request. It's used when the request used an <var>HttpOutputStream</var>
     * and the module is therefore not able to retry the request itself.
     * This flag is <var>false</var> by default.
     *
     * <P>If a module sets this flag then it must also reset() the
     * the <var>HttpOutputStream</var> so it may be reused by the application.
     * It should then also use this <var>HttpOutputStream</var> to recognize
     * the retried request in the requestHandler().
     *
     * @param flag indicates whether the application should retry the request.
     */
    public void setRetryRequest(boolean flag)
    {
	retry = flag;
    }

    /**
     * @return true if the request should be retried.
     */
    public boolean retryRequest()
    {
	return retry;
    }


    // Helper Methods

    /**
     * Gets and parses the headers. Sets up Data if no data will be received.
     *
     * @param skip_cont  if true skips over '100 Continue' status codes.
     * @exception IOException If any exception occurs while reading the headers.
     */
    private synchronized void getHeaders(boolean skip_cont)  throws IOException
    {
	if (got_headers)  return;
	if (exception != null)
	    throw (IOException) exception.fillInStackTrace();

	reading_headers = true;
	try
	{
	    do
	    {
		Headers.clear();	// clear any headers from 100 Continue
		char[] headers = readResponseHeaders(inp_stream);
		parseResponseHeaders(headers);
	    } while ((StatusCode == 100  &&  skip_cont)  ||	// Continue
		     (StatusCode > 101  &&  StatusCode < 200));	// Unknown
	}
	catch (IOException ioe)
	{
	    if (!(ioe instanceof InterruptedIOException))
		exception = ioe;
	    if (ioe instanceof ProtocolException)	// thrown internally
	    {
		cd_type = CD_CLOSE;
		if (stream_handler != null)
		    stream_handler.markForClose(this);
	    }
	    throw ioe;
	}
	finally
	    { reading_headers = false; }
	if (StatusCode == 100) return;

	got_headers = true;


	// parse the Transfer-Encoding header

	boolean te_chunked = false, te_is_identity = true, ct_mpbr = false;
	Vector  te_hdr = null;
	try
	    { te_hdr = HttpClientUtil.parseHeader(getHeader("Transfer-Encoding")); }
	catch (ParseException pe)
	    { }
	if (te_hdr != null)
	{
	    te_chunked = ((HttpHeaderElement) te_hdr.lastElement()).getName().
			 equalsIgnoreCase("chunked");
	    for (int idx=0; idx<te_hdr.size(); idx++)
		if (((HttpHeaderElement) te_hdr.elementAt(idx)).getName().
		    equalsIgnoreCase("identity"))
		    te_hdr.removeElementAt(idx--);
		else
		    te_is_identity = false;
	}


	// parse Content-Type header

	try
	{
	    String hdr;
	    if ((hdr = getHeader("Content-Type")) != null)
	    {
		Vector phdr = HttpClientUtil.parseHeader(hdr);
		ct_mpbr = phdr.contains(new HttpHeaderElement("multipart/byteranges"))  ||
			  phdr.contains(new HttpHeaderElement("multipart/x-byteranges"));
	    }
	}
	catch (ParseException pe)
	    { }


	// now determine content-delimiter

	if (method.equals("HEAD")  ||  ContentLength == 0  ||
	    StatusCode <  200  ||
	    StatusCode == 204  ||  StatusCode == 205  || StatusCode == 304)
	{
	    Data = new byte[0];		// we will not receive any more data
	    cd_type = CD_0;
	    inp_stream.close();
	}
	else if (te_chunked)
	{
	    cd_type = CD_CHUNKED;

	    te_hdr.removeElementAt(te_hdr.size()-1);
	    if (te_hdr.size() > 0)
		setHeader("Transfer-Encoding", HttpClientUtil.assembleHeader(te_hdr));
	    else
		deleteHeader("Transfer-Encoding");
	}
	else if (ContentLength != -1  &&  te_is_identity)
	    cd_type = CD_CONTLEN;
	else if (ct_mpbr  &&  te_is_identity)
	    cd_type = CD_MP_BR;
	else
	{
	    cd_type = CD_CLOSE;
	    ContentLength = -1;
	    if (stream_handler != null)
		stream_handler.markForClose(this);

	    if (Version.equals("HTTP/0.9"))
	    {
		inp_stream =
			new SequenceInputStream(new ByteArrayInputStream(Data),
						inp_stream);
		Data = null;
	    }
	}

	if (DebugResp)
	{
	    HttpClientUtil.logLine("Resp:  Response entity delimiter: " +
		(cd_type == CD_0       ? "No Entity"      :
		 cd_type == CD_CLOSE   ? "Close"          :
		 cd_type == CD_CONTLEN ? "Content-Length" :
		 cd_type == CD_CHUNKED ? "Chunked"        :
		 cd_type == CD_MP_BR   ? "Multipart"      :
		 "???" ) + " (" + inp_stream.hashCode() + ")");
	}

	// special handling if this is the first response received
	if (isFirstResponse)
	{
	    if (!connection.handleFirstRequest(req, this))
	    {
		// got a buggy server - need to redo the request
		Response resp;
		try
		    { resp = connection.sendRequest(req, timeout); }
		catch (ModuleException me)
		    { throw new IOException(me.toString()); }
		resp.getVersion();

		this.StatusCode    = resp.StatusCode;
		this.ReasonLine    = resp.ReasonLine;
		this.Version       = resp.Version;
		this.EffectiveURI  = resp.EffectiveURI;
		this.ContentLength = resp.ContentLength;
		this.Headers       = resp.Headers;
		this.inp_stream    = resp.inp_stream;
		this.Data          = resp.Data;

		req = null;
	    }
	}


	// remove erroneous connection tokens

	if (connection.ServerProtocolVersion < HTTP_1_1)
	{
	    Vector pco;
	    try
		{ pco = HttpClientUtil.parseHeader(getHeader("Connection")); }
	    catch (ParseException pe)
		{ pco = null; }

	    if (pco != null)
	    {
		if (connection.getProxyHost() != null)
		    pco.removeAllElements();

		for (int idx=0; idx<pco.size(); idx++)
		{
		    String name =
			    ((HttpHeaderElement) pco.elementAt(idx)).getName();
		    if (!name.equalsIgnoreCase("keep-alive"))
		    {
			pco.removeElementAt(idx);
			deleteHeader(name);
			idx--;
		    }
		}

		if (pco.size() > 0)
		    setHeader("Connection", HttpClientUtil.assembleHeader(pco));
		else
		    deleteHeader("Connection");
	    }

	    try
		{ pco = HttpClientUtil.parseHeader(getHeader("Proxy-Connection")); }
	    catch (ParseException pe)
		{ pco = null; }

	    if (pco != null)
	    {
		if (connection.getProxyHost() == null)
		    pco.removeAllElements();

		for (int idx=0; idx<pco.size(); idx++)
		{
		    String name =
			    ((HttpHeaderElement) pco.elementAt(idx)).getName();
		    if (!name.equalsIgnoreCase("keep-alive"))
		    {
			pco.removeElementAt(idx);
			deleteHeader(name);
			idx--;
		    }
		}

		if (pco.size() > 0)
		    setHeader("Proxy-Connection", HttpClientUtil.assembleHeader(pco));
		else
		    deleteHeader("Proxy-Connection");
	    }
	}
	else
	{
	    deleteHeader("Proxy-Connection");
	}
    }


    /* these are external to readResponseHeaders() because we need to be
     * able to restart after an InterruptedIOException
     */
    private byte[]       buf     = new byte[600];
    private char[]       hdrs    = new char[600];
    private int          buf_pos = 0;
    private int          hdr_pos = 0;
    private boolean      reading_lines = false;

    /**
     * Reads the response headers received, folding continued lines.
     *
     * <P>Some of the code is a bit convoluted because we have to be able
     * restart after an InterruptedIOException.
     *
     * @inp    the input stream from which to read the response
     * @return a (newline separated) list of headers
     * @exception IOException if any read on the input stream fails
     */
    private char[] readResponseHeaders(InputStream inp)  throws IOException
    {
	if (DebugResp)
	{
	    if (buf_pos == 0)
		HttpClientUtil.logLine("Resp:  Reading Response headers " +
			     inp_stream.hashCode());
	    else
		HttpClientUtil.logLine("Resp:  Resuming reading Response headers " +
			     inp_stream.hashCode());
	}


	// read 5 bytes to see type of response
	if (!reading_lines)
	{
	    try
	    {
		cd_type = CD_NONE;

		// Skip any leading white space to accomodate buggy responses
		if (buf_pos == 0)
		{
		    int c;
		    do
		    {
			if ((c = inp.read()) == -1)
			    throw new EOFException("Encountered premature EOF "
						   + "while reading Version");
		    } while (Character.isWhitespace( (char) (c & 0xFF) )) ;
		    buf[0] = (byte) (c & 0xFF);
		    buf_pos = 1;
		}

		// Now read first five bytes (the version string)
		while (buf_pos < 5)
		{
		    int got = inp.read(buf, buf_pos, 5-buf_pos);
		    if (got == -1)
			throw new EOFException("Encountered premature EOF " +
						"while reading Version");
		    buf_pos += got;
		}
	    }
	    catch (EOFException eof)
	    {
		if (DebugResp)
		{
		    HttpClientUtil.logLine("Resp:  (" + inp_stream.hashCode() + ")");
		    HttpClientUtil.logMessage("       ");
		    HttpClientUtil.logStackTrace(eof);
		}
		throw eof;
	    }
	    for (int idx=0; idx<buf_pos; idx++)
		hdrs[hdr_pos++] = (char) (buf[idx] & 0xFF);

	    reading_lines = true;
	}

	// check for 'HTTP/' or 'HTTP ' (NCSA bug)
	if (hdrs[0] == 'H'  &&  hdrs[1] == 'T'  &&  hdrs[2] == 'T'  &&
	    hdrs[3] == 'P'  &&  (hdrs[4] == '/'  ||  hdrs[4] == ' '))
	{
	    cd_type = CD_HDRS;
	    readHeaderBlock(inp);
	}

	// reset variables for next round
	buf_pos = 0;
	reading_lines = false;

	char[] tmp = HttpClientUtil.resizeArray(hdrs, hdr_pos);
	hdr_pos = 0;
	return tmp;
    }


    char[] trailers;

    /**
     * This is called by the StreamDemultiplexor to read all the trailers
     * of a chunked encoded entity.
     *
     * @param inp the raw input stream to read from
     * @exception IOException if any IOException is thrown by the stream
     */
    void readTrailers(InputStream inp)  throws IOException
    {
	try
	{
	    readHeaderBlock(inp);
	    trailers = HttpClientUtil.resizeArray(hdrs, hdr_pos);
	}
	catch (IOException ioe)
	{
	    if (!(ioe instanceof InterruptedIOException))
		exception = ioe;
	    throw ioe;
	}
    }


    /**
     * Reads a complete header block and stores it in the <var>hdrs</var>
     * buffer. This assumes the underlying stream has been set up to
     * return EOF at the end of the block.
     *
     * <P>This method is restartable after an InterruptedIOException.
     *
     * @param inp the input stream to read from
     * @exception IOException if any IOException is thrown by the stream
     */
    private void readHeaderBlock(InputStream inp)  throws IOException
    {
	int got;

	while ((got = inp.read(buf, 0, buf.length)) > 0)
	{
	    if (hdr_pos + got > hdrs.length)
		hdrs = HttpClientUtil.resizeArray(hdrs, (hdr_pos + got) * 2);
	    for (int idx=0; idx<got; idx++)
		hdrs[hdr_pos++] = (char) (buf[idx] & 0xFF); 	// ISO-8859-1
	}

	hdr_pos -= 2;				// remove last CRLF
    }


    /**
     * Parses the headers received into a new Response structure.
     *
     * @param  headers a (CRLF separated) list of headers
     * @exception ProtocolException if any part of the headers do not
     *            conform
     */
    private void parseResponseHeaders(char[] headers) throws ProtocolException
    {
	if (DebugResp)
	{
	    HttpClientUtil.logLine("Resp:  Parsing Response headers from Request \"" +
			 method + " " + resource + "\":  (" +
			 inp_stream.hashCode() + ")");
	    String nl = System.getProperty("line.separator");
	    HttpClientUtil.logMessage(nl + new String(headers) + nl);
	}


	// Detect and handle HTTP/0.9 responses

	if (!(headers[0] == 'H'  &&  headers[1] == 'T'  &&  headers[2] == 'T' &&
	    headers[3] == 'P'  &&  (headers[4] == '/'  ||  headers[4] == ' ')))
	{
	    Version    = "HTTP/0.9";
	    StatusCode = 200;
	    ReasonLine = "OK";

	    Data = new byte[headers.length];
	    for (int idx=0; idx<Data.length; idx++)
		Data[idx] = (byte) headers[idx];

	    return;
	}


	// get the status line

	int beg = 0;
	int end = HttpClientUtil.findSpace(headers, beg);
	if (end - beg > 4)
	    Version = new String(headers, beg, end-beg);
	else
	    Version = "HTTP/1.0";		// NCSA bug

	beg = HttpClientUtil.skipSpace(headers, end);
	end = HttpClientUtil.findSpace(headers, beg);
	if (beg == end)
	    throw new ProtocolException("Invalid HTTP status line received: " +
					"no status code found in '" +
					new String(headers) + "'");
	try
	    { StatusCode = Integer.parseInt(new String(headers, beg, end-beg)); }
	catch (NumberFormatException nfe)
	{
	    throw new ProtocolException("Invalid HTTP status line received: " +
					"status code '" +
					new String(headers, beg, end-beg) +
					"' not a number in '" +
					new String(headers) + "'");
	}

	beg = end;
	while (end < headers.length  &&  headers[end] != '\r'  &&
	       headers[end] != '\n')
	    end++;
	ReasonLine = new String(headers, beg, end-beg).trim();


	/* If the status code shows an error and we're sending (or have sent)
	 * an entity and it's length is delimited by a Content-length header,
	 * then we must close the the connection (if indeed it hasn't already
	 * been done) - RFC-2068, Section 8.2 .
	 */
	if (StatusCode >= 300  &&  sent_entity)
	{
	    if (stream_handler != null)
		stream_handler.markForClose(this);
	}


	// get the rest of the headers

	parseHeaderFields(headers, HttpClientUtil.skipSpace(headers, end), Headers);


	/* make sure the connection isn't closed prematurely if we have
	 * trailer fields
	 */
	if (Headers.get("Trailer") != null  &&  resp_inp_stream != null)
	    resp_inp_stream.dontTruncate();

	// Mark the end of the connection if it's not to be kept alive

	int vers;
	if (Version.equals("HTTP/0.9")  ||  Version.equals("HTTP/1.0"))
	    vers = 0;
	else
	    vers = 1;

	try
	{
	    String con = (String) Headers.get("Connection"),
		  pcon = (String) Headers.get("Proxy-Connection");

	    // parse connection header
	    if ((vers == 1  &&  con != null  &&  HttpClientUtil.hasToken(con, "close"))
		||
		(vers == 0  &&
		 !((!used_proxy && con != null &&
					HttpClientUtil.hasToken(con, "keep-alive"))  ||
		   (used_proxy && pcon != null &&
					HttpClientUtil.hasToken(pcon, "keep-alive")))
		)
	       )
		if (stream_handler != null)
		    stream_handler.markForClose(this);
	}
	catch (ParseException pe) { }
    }


    /**
     * If the trailers have not been read it calls <code>getData()</code>
     * to first force all data and trailers to be read. Then the trailers
     * parsed into the <var>Trailers</var> hashtable.
     *
     * @exception IOException if any exception occured during reading of the
     *                        response
     */
    private synchronized void getTrailers()  throws IOException
    {
	if (got_trailers)  return;
	if (exception != null)
	    throw (IOException) exception.fillInStackTrace();

	if (DebugResp)
	    HttpClientUtil.logLine("Resp:  Reading Response trailers " +
			 inp_stream.hashCode());

	try
	{
	    if (trailers == null)
	    {
		if (resp_inp_stream != null)
		    resp_inp_stream.readAll(timeout);
	    }

	    if (trailers != null)
	    {
		if (DebugResp)
		{
		    HttpClientUtil.logLine("Resp:  Parsing Response trailers from " +
				 "Request \"" + method + " " + resource +
				 "\":  (" + inp_stream.hashCode() + ")");
		    String nl = System.getProperty("line.separator");
		    HttpClientUtil.logMessage(nl + new String(hdrs, 0, hdr_pos) + nl);
		}

		parseHeaderFields(trailers, 0, Trailers);
	    }
	}
	catch (IOException ioe)
	{
	    if (!(ioe instanceof InterruptedIOException))
		exception = ioe;
	    throw ioe;
	}

	got_trailers = true;
    }


    /**
     * Parses the given header block as fields of the form "<name>: <value>"
     * into the given list. Continuation lines are honored. Multiple
     * headers with the same name are stored as one header with the values
     * joined by a ",".
     *
     * @param hdrs  the header or trailer block
     * @param beg   the position in hdrs where parsing is to begin
     * @param list  the Hashtable to store the parsed fields in
     * @exception ProtocolException if any part of the headers do not
     *                              conform
     */
    private void parseHeaderFields(char[] hdrs, int beg, CIHashtable list)
	    throws ProtocolException
    {
	int end = beg, len = hdrs.length;

	while (end < len)
	{
	    // get name

	    while (end < len  &&  !Character.isWhitespace(hdrs[end])  &&
		   hdrs[end] != ':')
		end++;
	    String hdr_name = new String(hdrs, beg, end-beg);


	    // skip spaces

	    while (end < len  &&  Character.isWhitespace(hdrs[end]))
		end++;


	    // skip ':'

	    /* It seems there are a number of broken servers out there. So
	     * we'll try make the best of things and don't throw this execption
	     * after all
	    if (end >= len  ||  hdrs[end] != ':')
	    {
		while (end < len  &&  hdrs[end] != '\n')  end++;
		throw new ProtocolException("Invalid HTTP header received: " +
					    "no ':' found in '" +
					    new String(hdrs, beg, end-beg) +
					    "'");
	    }
	    */

	    if (end < len  &&  hdrs[end] == ':'  &&  hdrs[end-1] != '\n')
		beg = end + 1;
	    else
		beg = end;


	    // find start of value

	    String hdr_value = "";
	    if (hdrs[end-1] != '\n')
	    {
		// skip spaces

		while (beg < len  &&  Character.isWhitespace(hdrs[beg]))
		    beg++;


		// find end of value

		end = beg;
		while (end < len  &&  hdrs[end] != '\n')
		    end++;
		if (hdrs[end-1] == '\r')
		    hdr_value = new String(hdrs, beg, end-1-beg);
		else
		    hdr_value = new String(hdrs, beg, end-beg);
		end++;

		while (end < len  && (hdrs[end] == ' '  ||  hdrs[end] == '\t'))
		{
		    beg = end + 1;
		    while (beg < len  &&  (hdrs[beg] == ' '  ||  hdrs[beg] == '\t'))
			beg++;
		    end = beg;
		    while (end < len  &&  hdrs[end] != '\n')
			end++;
		    if (hdrs[end-1] == '\r')
			hdr_value += ' ' + new String(hdrs, beg, end-1-beg);
		    else
			hdr_value += ' ' + new String(hdrs, beg, end-beg);
		    end++;
		}

		beg = end;
	    }

	    // special case Content-length
	    if (hdr_name.equalsIgnoreCase("Content-length"))
	    {
		try
		{
		    ContentLength = Integer.parseInt(hdr_value.trim());
		    if (ContentLength < 0)
			throw new NumberFormatException();
		}
		catch (NumberFormatException nfe)
		{
		    throw new ProtocolException("Invalid Content-length header"+
						" received: '"+hdr_value + "'");
		}
		list.put(hdr_name, hdr_value);
	    }


	    // add header to hashtable

	    else
	    {
		String old_value  = (String) list.get(hdr_name);
		if (old_value == null)
		    list.put(hdr_name, hdr_value);
		else
		    list.put(hdr_name, old_value + ", " + hdr_value);
	    }
	}
    }


    /**
     * Reads the response data received. Does not return until either
     * Content-Length bytes have been read or EOF is reached.
     *
     * @inp       the input stream from which to read the data
     * @exception IOException if any read on the input stream fails
     */
    private void readResponseData(InputStream inp) throws IOException
    {
	if (Data == null)
	    Data = new byte[0];


	// read response data

	int off = Data.length;

	try
	{
	    interrupted = false;

	    if (getHeader("Content-Length") != null  &&
		ContentLength != -1  &&
		getHeader("Transfer-Encoding") == null)
	    {
		int rcvd = 0;
		Data = HttpClientUtil.resizeArray(Data, ContentLength);

		do
		{
		    off  += rcvd;
		    rcvd  = inp.read(Data, off, ContentLength-off);
		} while (rcvd != -1  &&  off+rcvd < ContentLength);

		/* Don't do this!
		 * If we do, then getData() won't work after a getInputStream()
		 * because we'll never get all the expected data. Instead, let
		 * the underlying RespInputStream throw the EOF.
		if (rcvd == -1)	// premature EOF
		{
		    throw new EOFException("Encountered premature EOF while " +
					    "reading headers: received " + off +
					    " bytes instead of the expected " +
					    ContentLength + " bytes");
		}
		*/
		if (rcvd == -1)
		    Data = HttpClientUtil.resizeArray(Data, off);
	    }
	    else
	    {
		int inc  = 1000,
		    rcvd = 0;

		do
		{
		    off  += rcvd;
		    Data  = HttpClientUtil.resizeArray(Data, off+inc);
		} while ((rcvd = inp.read(Data, off, inc)) != -1);

		Data = HttpClientUtil.resizeArray(Data, off);
	    }
	}
	catch (InterruptedIOException iioe)
	{
	    Data = HttpClientUtil.resizeArray(Data, off);
	    interrupted = true;
	    throw iioe;
	}
	catch (IOException ioe)
	{
	    Data = HttpClientUtil.resizeArray(Data, off);
	    throw ioe;
	}
	finally
	{
	    if (!interrupted)
	    {
		try
		    { inp.close(); }
		catch (IOException ioe)
		    { }
	    }
	}
    }


    Request        req = null;
    boolean isFirstResponse = false;
    /**
     * This marks this response as belonging to the first request made
     * over an HTTPConnection. The <var>con</var> and <var>req</var>
     * parameters are needed in case we have to do a resend of the request -
     * this is to handle buggy servers which barf upon receiving a request
     * marked as HTTP/1.1 .
     *
     * @param con The HTTPConnection used
     * @param req The Request sent
     */
    void markAsFirstResponse(Request req)
    {
	this.req = req;
	isFirstResponse = true;
    }


    /**
     * @return a clone of this request object
     */
    public Object clone()
    {
	Response cl;
	try
	    { cl = (Response) super.clone(); }
	catch (CloneNotSupportedException cnse)
	    { throw new InternalError(cnse.toString()); /* shouldn't happen */ }

	cl.Headers  = (CIHashtable) Headers.clone();
	cl.Trailers = (CIHashtable) Trailers.clone();

	return cl;
    }
}

