/*
 * @(#)StreamDemultiplexor.java				0.3 30/01/1998
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


import java.io.*;
import java.net.Socket;
import java.util.Vector;
import java.util.Enumeration;

/**
 * This class handles the demultiplexing of input stream. This is needed
 * for things like keep-alive in HTTP/1.0, persist in HTTP/1.1 and in HTTP-NG.
 *
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 *
 * @author      modified by Stefan Lieske, 2005/02/14
 */

class StreamDemultiplexor implements GlobalConstants
{
    /** the protocol were handling request for */
    private int                    Protocol;

    /** the connection we're working for */
    private HTTPConnection         Connection;

    /** the input stream to demultiplex */
    /** @author  modified by Stefan Lieske, 2005/02/15 */
    //private ExtBufferedInputStream Stream;
    private DemultiplexorInputStream Stream;

    /** the socket this hangs off */
    private Socket                 Sock = null;

    /** signals after the closing of which stream to close the socket */
    private ResponseHandler        MarkedForClose;

    /** timer used to close the socket if unused for a given time */
    private SocketTimeout.TimeoutEntry Timer = null;

    /** timer thread which implements the timers */
    private static SocketTimeout   TimerThread = null;

    /** a Vector to hold the list of response handlers were serving */
    private LinkedList             RespHandlerList;

    /** number of unread bytes in current chunk (if transf-enc == chunked) */
    private int                    chunk_len;

    /** end of headers - CRLF CRLF */
    private static byte[]          hdr_end = { (byte) '\r', (byte) '\n',
					       (byte) '\r', (byte) '\n' };
    private static int[]           hdr_cmp = HttpClientUtil.compile_search(hdr_end);
    private boolean                hdr_term_set = false;
    private boolean                trl_term_set = false;

    /** the currently set timeout for the socket */
    private int                    cur_timeout = 0;

    /**
     * Stores whether this demultiplexor is compatible to HTTP CONNECT requests. But this
     * compatibility mode has less performance than the normal mode and should only used, if HTTP
     * CONNECT requests have to be handled by the StreamDemultiplexor.
     *
     * @author  added by Stefan Lieske, 2005/02/14
     */
    private boolean m_httpConnectCompatibilityMode;

    /**
     * Stores the hidden socket after a call of close(), if m_httpConnectCompatibilityMode is
     * true.
     *
     * @author  added by Stefan Lieske, 2005/02/14
     */
    private Socket m_hiddenSocket;


    static
    {
	TimerThread = new SocketTimeout(60);
	TimerThread.start();
    }


    // Constructors

    /**
     * a simple contructor.
     *
     * @param protocol   the protocol used on this stream.
     * @param sock       the socket which we're to demux.
     * @param connection the http-connection this socket belongs to.
     * @param a_httpConnectCompatibilityMode Whether this demultiplexor is compatible to HTTP
     *                                       CONNECT requests. But this compatibility mode has
     *                                       less performance than the normal mode and should
     *                                       only used, if HTTP CONNECT requests have to be
     *                                       handled by the StreamDemultiplexor. If this value is
     *                                       true, releaseHttpConnectResources() or
     *                                       releaseSocket() has to called in order to release
     *                                       some additional needed resources after usage.
     */
    /** @author  modified by Stefan Lieske, 2005/02/14 */
    //StreamDemultiplexor(int protocol, Socket sock, HTTPConnection connection)
    //      throws IOException
    StreamDemultiplexor(int protocol, Socket sock, HTTPConnection connection, boolean a_httpConnectCompatibilityMode)
	    throws IOException
    {
	this.Protocol   = protocol;
	this.Connection = connection;
	RespHandlerList = new LinkedList();
        /** @author  added by Stefan Lieske, 2005/02/14	*/
	m_httpConnectCompatibilityMode = a_httpConnectCompatibilityMode;
	m_hiddenSocket = null;
	init(sock);
    }


    /**
     * Initializes the demultiplexor with a new socket.
     *
     * @param stream   the stream to demultiplex
     */
    private void init(Socket sock)  throws IOException
    {
	if (DebugDemux)
	    HttpClientUtil.logLine("Demux: Initializing Stream Demultiplexor (" +
			 this.hashCode() + ")");

	this.Sock       = sock;
	/** @author  modified by Stefan Lieske, 2005/02/15 */
	//this.Stream     = new ExtBufferedInputStream(sock.getInputStream());
	if (m_httpConnectCompatibilityMode) {
	  /* use the HTTP CONNECT compatible input stream implementation */
	  Stream = new LazyReadInputStream(sock.getInputStream());
	}
	else {
	  /* use the normal implementation with a better performance */
	  Stream = new ExtBufferedInputStream(sock.getInputStream());
	}
	MarkedForClose  = null;
	chunk_len       = -1;

	// start a timer to close the socket after 60 seconds
	Timer = TimerThread.setTimeout(this);
    }


    // Methods

    /**
     * Each Response must register with us.
     */
    void register(Response resp_handler, Request req)  throws RetryException
    {
	synchronized(RespHandlerList)
	{
	    if (Sock == null)
		throw new RetryException();

	    RespHandlerList.addToEnd(
				new ResponseHandler(resp_handler, req, this));
	}
    }

    /**
     * creates an input stream for the response.
     *
     * @param resp the response structure requesting the stream
     * @return an InputStream
     */
    RespInputStream getStream(Response resp)
    {
	ResponseHandler resph;
	for (resph = (ResponseHandler) RespHandlerList.enumerate();
	     resph != null; resph = (ResponseHandler) RespHandlerList.next())
	{
	    if (resph.resp == resp)  break;
	}

	if (resph != null)
	    return resph.stream;
	else
	    return null;
    }

    /**
     * Releases the Socket and makes it available for direct communication. This method is called
     * after a CONNECT request to get the Socket for further communication. This
     * StreamDemultiplexor will release all associated resources for this Socket and stops
     * monitoring it. If this StreamDemultiplexor is not in HTTP CONNECT compatibility mode or
     * reading the remaining responses from the stream failed, null is returned. The caller of
     * this method must be sure, that nobody else is sending new requests on the socket
     * connection.
     *
     * @return The plain Socket useable for further communication or null, if there was an error.
     *
     * @author  added by Stefan Lieske, 2005/02/14
     */
    public Socket releaseSocket() {
      if (DebugDemux) {
        HttpClientUtil.logLine("Demux: releasing socket (" + this.hashCode() + ")");
      }
      Socket releasedSocket = null;
      synchronized (this) {
        /* check whether HTTP CONNECT compatibility mode is used */
        if (m_httpConnectCompatibilityMode) {
	  try {
            synchronized(RespHandlerList) {
	      /* get all remaining responses from the stream */
	      ResponseHandler currentResponseHandler = (ResponseHandler)(RespHandlerList.enumerate());
	      while (currentResponseHandler != null) {
	        ResponseHandler nextResponseHandler = (ResponseHandler)(RespHandlerList.next());
	        if (nextResponseHandler == null) {
	          /* the current response handler is the last one -> this should be the OK message
	           * for the CONNECT request -> this message should only have a header and no body
	           * -> read the status code of this response will read all other responses and
	           * the whole header of the last message
	           */
	          currentResponseHandler.resp.getStatusCode();
	        }
	        currentResponseHandler = nextResponseHandler;
	      }
              /* hide the socket (makes it unavailable for other communication) */
              close((IOException) null, false);
            }
            releasedSocket = m_hiddenSocket;
            /* we have fetched the hidden socket -> disable the HTTP CONNECT compatibility mode */
            m_hiddenSocket = null;
            m_httpConnectCompatibilityMode = false;
	  }
	  catch (IOException e) {
            /* something went wrong while reading the remaining responses -> disable the
             * compatibility mode and close the socket
             */
            m_httpConnectCompatibilityMode = false;
            close((IOException) null, false);
	  }
        }
      }
      /* that's it */
      return releasedSocket;
    }

    /**
     * Returns whether the HTTP CONNECT compatibility mode is used or not.
     *
     * @return True, if HTTP CONNECT compatibility mode is enabled, false otherwise.
     *
     * @author  added by Stefan Lieske, 2005/02/14
     */
    public boolean isHttpConnectCompatibilityModeUsed() {
      return m_httpConnectCompatibilityMode;
    }

    /**
     * Restarts the timer thread that will close an unused socket after
     * 60 seconds.
     */
    void restartTimer()
    {
	if (Timer != null)  Timer.reset();
    }


    /**
     * reads an array of bytes from the master stream.
     */
    int read(byte[] b, int off, int len, ResponseHandler resph, int timeout)
	    throws IOException
    {
	if (resph.exception != null)
	    throw (IOException) resph.exception.fillInStackTrace();

	if (resph.eof)
	    return -1;


	// read the headers and data for all responses preceding us.

	ResponseHandler head;
	while ((head = (ResponseHandler) RespHandlerList.getFirst()) != null  &&
		head != resph)
	{
	    try
		{ head.stream.readAll(timeout); }
	    catch (IOException ioe)
	    {
		if (ioe instanceof InterruptedIOException)
		    throw ioe;
		else
		    throw (IOException) resph.exception.fillInStackTrace();
	    }
	}


	// Now we can read from the stream.

	synchronized(this)
	{
	    if (resph.exception != null)
		throw (IOException) resph.exception.fillInStackTrace();

	    if (DebugDemux)
	    {
		HttpClientUtil.logLine("Demux: Reading for stream " +
			     resph.stream.hashCode());
	    }

	    if (Timer != null)  Timer.hyber();

	    try
	    {
		int rcvd = -1;

		if (timeout != cur_timeout)
		{
		    if (DebugDemux)
			HttpClientUtil.logLine("Demux: Setting timeout to " + timeout +
				     " ms");

		    try
			{ Sock.setSoTimeout(timeout); }
		    catch (Throwable t)
			{ }
		    cur_timeout = timeout;
		}

		switch (resph.resp.cd_type)
		{
		    case CD_NONE:
			rcvd = Stream.read(b, off, len);
			if (rcvd == -1)
			    throw new EOFException("Premature EOF encountered");
			break;

		    case CD_HDRS:
			if (!hdr_term_set)
			{
			    Stream.setTerminator(hdr_end, hdr_cmp);
			    hdr_term_set = true;
			}

			if (Stream.atEnd())
			{
			    Stream.setTerminator(null, null);
			    hdr_term_set = false;
			    rcvd = 0;
			}
			else
			    rcvd = Stream.read(b, off, len);

			if (rcvd == -1)
			    throw new EOFException("Premature EOF encountered");

			break;

		    case CD_0:
			rcvd = -1;
			close(resph);
			break;

		    case CD_CLOSE:
			rcvd = Stream.read(b, off, len);
			if (rcvd == -1)
			    close(resph);
			break;

		    case CD_CONTLEN:
			int cl = resph.resp.ContentLength;
			if (len > cl - resph.stream.count)
			    len = cl - resph.stream.count;

			rcvd = Stream.read(b, off, len);
			if (rcvd == -1)
			    throw new EOFException("Premature EOF encountered");

			if (resph.stream.count+rcvd == cl)
			    close(resph);

			break;

		    case CD_CHUNKED:
			if (chunk_len == -1)	// it's a new chunk
			    chunk_len = Codecs.getChunkLength(Stream);

			if (chunk_len > 0)		// it's data
			{
			    if (len > chunk_len)  len = chunk_len;
			    rcvd = Stream.read(b, off, len);
			    if (rcvd == -1)
				throw new EOFException("Premature EOF encountered");
			    chunk_len -= rcvd;
			    if (chunk_len == 0)	// got the whole chunk
			    {
				Stream.read();	// CR
				Stream.read();	// LF
				chunk_len = -1;
			    }
			}
			else	// the footers (trailers)
			{
			    if (trl_term_set  ||  !Stream.startsWithCRLF())
			    {
				if (!trl_term_set)
				{
				    Stream.setTerminator(hdr_end, hdr_cmp);
				    trl_term_set = true;
				}
				resph.resp.readTrailers(Stream);
				if (!Stream.atEnd())
				    throw new EOFException("Premature EOF encountered");
				Stream.setTerminator(null, null);
				trl_term_set = false;
			    }
			    rcvd = -1;
			    close(resph);
			    chunk_len = -1;
			}
			break;

		    case CD_MP_BR:
			resph.setupBoundary(Stream);

			rcvd = Stream.read(b, off, len);
			if (rcvd == -1)
			    throw new EOFException("Premature EOF encountered");

			if (Stream.atEnd())
			{
			    Stream.setTerminator(null, null);
			    close(resph);
			}

			break;

		    default:
			throw new Error("Internal Error in StreamDemultiplexor: " +
					"Invalid cd_type " + resph.resp.cd_type);
		}

		restartTimer();
		return rcvd;

	    }
	    catch (InterruptedIOException ie)	// don't intercept this one
	    {
		restartTimer();
		throw ie;
	    }
	    catch (IOException ioe)
	    {
		if (DebugDemux)
		{
		    HttpClientUtil.logLine("Demux: (" + this.hashCode() + ")");
		    HttpClientUtil.logMessage("       ");
		    HttpClientUtil.logStackTrace(ioe);
		}

		close(ioe, true);
		throw resph.exception;		// set by retry_requests
	    }
	    catch (ParseException pe)
	    {
		if (DebugDemux)
		{
		    HttpClientUtil.logLine("Demux: (" + this.hashCode() + ")");
		    HttpClientUtil.logMessage("       ");
		    HttpClientUtil.logStackTrace(pe);
		}

		close(new IOException(pe.toString()), true);
		throw resph.exception;		// set by retry_requests
	    }
	}
    }

    /**
     * skips a number of bytes in the master stream. This is done via a
     * dummy read, as the socket input stream doesn't like skip()'s.
     */
    synchronized long skip(long num, ResponseHandler resph) throws IOException
    {
	if (resph.exception != null)
	    throw (IOException) resph.exception.fillInStackTrace();

	if (resph.eof)
	    return 0;

	byte[] dummy = new byte[(int) num];
	int rcvd = read(dummy, 0, (int) num, resph, 0);
	if (rcvd == -1)
	    return 0;
	else
	    return rcvd;
    }

    /**
     * Determines the number of available bytes.
     */
    synchronized int available(ResponseHandler resph) throws IOException
    {
	int avail = Stream.available();
	if (resph == null)  return avail;

	if (resph.exception != null)
	    throw (IOException) resph.exception.fillInStackTrace();

	if (resph.eof)
	    return 0;

	switch (resph.resp.cd_type)
	{
	    case CD_NONE:
		return avail;
	    case CD_HDRS:
		// this is something of a hack; I could return 0, but then
		// if you were waiting for something on a response that
		// wasn't first in line (and you didn't try to read the
		// other response) you'd wait forever. On the other hand,
		// we might be making a false promise here...
		return (avail > 0 ? 1 : 0);
	    case CD_0:
		return 0;
	    case CD_CLOSE:
		return avail;
	    case CD_CONTLEN:
		int cl = resph.resp.ContentLength;
		cl -= resph.stream.count;
		return (avail < cl ? avail : cl);
	    case CD_CHUNKED:
		return avail;	// not perfect...
	    case CD_MP_BR:
		return avail;	// not perfect...
	    default:
		throw new Error("Internal Error in StreamDemultiplexor: " +
				"Invalid cd_type " + resph.resp.cd_type);
	}

    }


    /**
     * Closes the socket and all associated streams. If <var>exception</var>
     * is not null then all active requests are retried.
     *
     * <P>There are five ways this method may be activated. 1) if an exception
     * occurs during read or write. 2) if the stream is marked for close but
     * no responses are outstanding (e.g. due to a timeout). 3) when the
     * markedForClose response is closed. 4) if all response streams up until
     * and including the markedForClose response have been closed. 5) if this
     * demux is finalized.
     *
     * @param exception the IOException to be sent to the streams.
     * @param was_reset if true then the exception is due to a connection
     *                  reset; otherwise it means we generated the exception
     *                  ourselves and this is a "normal" close.
     */
    synchronized void close(IOException exception, boolean was_reset)
    {
	if (Sock == null)	// already cleaned up
	    return;

	if (DebugDemux)
	    HttpClientUtil.logLine("Demux: Closing all streams and socket (" +
			 this.hashCode() + ")");
	if (DebugDemux)
	    HttpClientUtil.logStackTrace(new Throwable());

	/** @author  modified by Stefan Lieske, 2005/02/14 */
	//try
	//    { Stream.close(); }
	//catch (IOException ioe) { }
	//try
	//    { Sock.close(); }
	//catch (IOException ioe) { }
	if (m_httpConnectCompatibilityMode) {
	  /* create a dummy stream -> closing it is no problem */
	  Stream = new ExtBufferedInputStream(new ByteArrayInputStream(new byte[0]));
        }
	try {
	  Stream.close();
	}
	catch (IOException ioe) {
	}
	if (m_httpConnectCompatibilityMode) {
	  m_hiddenSocket = Sock;
	}
	else {
	  try {
	    Sock.close();
	  }
	  catch (IOException ioe) {
	  }
	}

	Sock = null;

	if (Timer != null)
	{
	    Timer.kill();
	    Timer = null;
	}

	Connection.DemuxList.remove(this);


	// Here comes the tricky part: redo outstanding requests!

	if (exception != null)
	    synchronized(RespHandlerList)
		{ retry_requests(exception, was_reset); }
    }


    /**
     * Retries outstanding requests. Well, actually the RetryModule does
     * that. Here we just throw a RetryException for each request so that
     * the RetryModule can catch and handle them.
     *
     * @param exception the exception that led to this call.
     * @param was_reset this flag is passed to the RetryException and is
     *                  used by the RetryModule to distinguish abnormal closes
     *                  from expected closes.
     */
    private void retry_requests(IOException exception, boolean was_reset)
    {
	RetryException  first = null,
			prev  = null;
	ResponseHandler resph = (ResponseHandler) RespHandlerList.enumerate();

	while (resph != null)
	{
	    /* if the application is already reading the data then the
	     * response has already been handled. In this case we must
	     * throw the real exception.
	     */
	    if (resph.resp.got_headers)
	    {
		resph.exception = exception;
	    }
	    else
	    {
		RetryException tmp = new RetryException(exception.getMessage());
		if (first == null)  first = tmp;

		tmp.request    = resph.request;
		tmp.response   = resph.resp;
		tmp.exception  = exception;
		tmp.conn_reset = was_reset;
		tmp.first      = first;
		tmp.addToListAfter(prev);

		prev = tmp;
		resph.exception = tmp;
	    }

	    RespHandlerList.remove(resph);
	    resph = (ResponseHandler) RespHandlerList.next();
	}
    }


    /**
     * Closes the associated stream. If this one has been markedForClose then
     * the socket is closed; else closeSocketIfAllStreamsClosed is invoked.
     */
    synchronized void close(ResponseHandler resph)
    {
	if (resph != (ResponseHandler) RespHandlerList.getFirst())
	    return;

	if (DebugDemux)
	    HttpClientUtil.logLine("Demux: Closing stream " + resph.stream.hashCode());

	resph.eof = true;
	RespHandlerList.remove(resph);

	if (resph == MarkedForClose)
	    close(new IOException("Premature end of Keep-Alive"), false);
	else
	    closeSocketIfAllStreamsClosed();
    }


    /**
     * Close the socket if all the streams have been closed.
     *
     * <P>When a stream reaches eof it is removed from the response handler
     * list, but when somebody close()'s the response stream it is just
     * marked as such. This means that all responses in the list have either
     * not been read at all or only partially read, but they might have been
     * close()'d meaning that nobody is interested in the data. So If all the
     * response streams up till and including the one markedForClose have
     * been close()'d then we can remove them from our list and close the
     * socket.
     *
     * <P>Note: if the response list is emtpy or if no response is
     * markedForClose then this method does nothing. Specifically it does
     * not close the socket. We only want to close the socket if we've been
     * told to do so.
     *
     * <P>Also note that there might still be responses in the list after
     * the markedForClose one. These are due to us having pipelined more
     * requests to the server than it's willing to serve on a single
     * connection. These requests will be retried if possible.
     */
    synchronized void closeSocketIfAllStreamsClosed()
    {
	synchronized(RespHandlerList)
	{
	    ResponseHandler resph =
				(ResponseHandler) RespHandlerList.enumerate();

	    while (resph != null  &&  resph.stream.closed)
	    {
		if (resph == MarkedForClose)
		{
		    // remove all response handlers first
		    ResponseHandler tmp;
		    do
		    {
			tmp = (ResponseHandler) RespHandlerList.getFirst();
			RespHandlerList.remove(tmp);
		    }
		    while (tmp != resph);

		    // close the socket
		    close(new IOException("Premature end of Keep-Alive"), false);
		    return;
		}

		resph = (ResponseHandler) RespHandlerList.next();
	    }
	}
    }


    /**
     * returns the socket associated with this demux
     */
    synchronized Socket getSocket()
    {
	if (MarkedForClose != null)
	    return null;

	if (Timer != null)  Timer.hyber();
	return Sock;
    }


    /**
     * Mark this demux to not accept any more request and to close the
     * stream after this <var>resp</var>onse or all requests have been
     * processed, or close immediately if no requests are registered.
     *
     * @param response the Response after which the connection should
     *                 be closed.
     */
    synchronized void markForClose(Response resp)
    {
	synchronized(RespHandlerList)
	{
	    if (RespHandlerList.getFirst() == null)	// no active request,
	    {	    				// so close the socket
		close(new IOException("Premature end of Keep-Alive"), false);
		return;
	    }
	}

	if (Timer != null)
	{
	    Timer.kill();
	    Timer = null;
	}

	ResponseHandler resph, lasth = null;
	for (resph = (ResponseHandler) RespHandlerList.enumerate();
	     resph != null; resph = (ResponseHandler) RespHandlerList.next())
	{
	    if (resph.resp == resp)	// new resp precedes any others
	    {
		MarkedForClose = resph;

		if (DebugDemux)
		    HttpClientUtil.logLine("Demux: stream " + resph.stream.hashCode() +
				 " marked for close");

		closeSocketIfAllStreamsClosed();
		return;
	    }

	    if (MarkedForClose == resph)
		return;	// already marked for closing after an earlier resp

	    lasth = resph;
	}

	if (lasth == null)
	    return;

	MarkedForClose = lasth;		// resp == null, so use last resph
	closeSocketIfAllStreamsClosed();

	if (DebugDemux)
	    HttpClientUtil.logLine("Demux: stream " + lasth.stream.hashCode() +
			 " marked for close");
    }


    /**
     * Emergency stop. Closes the socket and notifies the responses that
     * the requests are aborted.
     *
     * @since V0.3
     */
    void abort()
    {
	if (DebugDemux)
	    HttpClientUtil.logLine("Demux: Aborting socket (" + this.hashCode() + ")");


	// notify all responses of abort

	synchronized(RespHandlerList)
	{
	    for (ResponseHandler resph =
				(ResponseHandler) RespHandlerList.enumerate();
		 resph != null;
		 resph = (ResponseHandler) RespHandlerList.next())
	    {
		if (resph.resp.http_resp != null)
		    resph.resp.http_resp.markAborted();
		if (resph.exception == null)
		    resph.exception = new IOException("Request aborted by user");
	    }


	    /* Close the socket.
	     * Note: this duplicates most of close(IOException, boolean). We
	     * do *not* call close() because that is synchronized, but we want
	     * abort() to be asynch.
	     */
	    if (Sock != null)
	    {
		try
		{
		    /**  @author modified by Stefan Lieske, 2005/02/14 */
		    // try
		    // { Sock.setSoLinger(false, 0); }
		    // catch (Throwable t)
		    // { }
		    if (!m_httpConnectCompatibilityMode) {
		      try {
		        Sock.setSoLinger(false, 0);
		      }
		      catch (Throwable t) {
		      }
		    }

	            /** @author  modified by Stefan Lieske, 2005/02/14 */
	            //try
	            //    { Stream.close(); }
	            //catch (IOException ioe) { }
	            //try
	            //    { Sock.close(); }
	            //catch (IOException ioe) { }
	            if (m_httpConnectCompatibilityMode) {
	              /* create a dummy stream -> closing it is no problem */
	              Stream = new ExtBufferedInputStream(new ByteArrayInputStream(new byte[0]));
	            }
	            try {
	              Stream.close();
	            }
	            catch (IOException ioe) {
	            }
	            if (m_httpConnectCompatibilityMode) {
	              m_hiddenSocket = Sock;
	            }
	            else {
	              try {
	                Sock.close();
	              }
	              catch (IOException ioe) {
	              }
	            }
		    Sock = null;

		    if (Timer != null)
		    {
			Timer.kill();
			Timer = null;
		    }
		}
		catch (NullPointerException npe)
		    { }

		Connection.DemuxList.remove(this);
	    }
	}
    }

    /**
     * Releases resources used for HTTP CONNECT compatibility. The HTTP CONNECT compatibility mode
     * is disabled and cannot be enabled on this instance of StreamDemultiplexor any more. After a
     * call of this method, everything is in the state it would be, if the HTTP CONNECT
     * compatibility were never be enabled. Only the performance still will be lower than in
     * normal mode.
     *
     * @author  added by Stefan Lieske, 2005/02/14
     */
    public void releaseHttpConnectResources() {
      synchronized (this) {
        m_httpConnectCompatibilityMode = false;
        if (m_hiddenSocket != null) {
	  try {
	    m_hiddenSocket.getInputStream().close();
	  }
	  catch (IOException e) {
	  }
	  try {
	    m_hiddenSocket.getOutputStream().close();
	  }
	  catch (IOException e) {
	  }
	  try {
	    m_hiddenSocket.close();
	  }
	  catch (IOException e) {
	  }
	  m_hiddenSocket = null;
	}
      }
    }

    /**
     * A safety net to close the connection.
     */
    protected void finalize()  throws Throwable
    {
	/** @author  added by Stefan Lieske, 2005/02/14 */
	/* finalize() -> force closing also of the hidden Socket */
	if (m_hiddenSocket != null) {
	  try {
	    m_hiddenSocket.getInputStream().close();
	  }
	  catch (IOException e) {
	  }
	  try {
	    m_hiddenSocket.getOutputStream().close();
	  }
	  catch (IOException e) {
	  }
	  try {
	    m_hiddenSocket.close();
	  }
	  catch (IOException e) {
	  }
	  m_hiddenSocket = null;
	}
	m_httpConnectCompatibilityMode = false;
	close((IOException) null, false);
	super.finalize();
    }


    /**
     * produces a string.
     * @return a string containing the class name and protocol number
     */
    public String toString()
    {
	String prot;

	switch (Protocol)
	{
	    case HTTP:
		prot = "HTTP"; break;
	    case HTTPS:
		prot = "HTTPS"; break;
	    case SHTTP:
		prot = "SHTTP"; break;
	    case HTTP_NG:
		prot = "HTTP_NG"; break;
	    default:
		throw new Error("HTTPClient Internal Error: invalid protocol " +
				Protocol);
	}

	return getClass().getName() + "[Protocol=" + prot + "]";
    }


    /**
     * This thread is used to implement socket timeouts. It keeps a list of
     * timer entries and expries them after a given time.
     */
    private static class SocketTimeout extends Thread implements GlobalConstants
    {
	/**
	 * This class represents a timer entry. It is used to close an
	 * inactive socket after n seconds. Once running, the timer may be
	 * suspended (hyber()), restarted (reset()), or aborted (kill()).
	 * When the timer expires it invokes markForClose() on the
	 * associated stream demultipexer.
	 */
	private class TimeoutEntry
	{
	    boolean restart = false,
		    hyber   = false,
		    alive   = true;
	    StreamDemultiplexor demux;
	    TimeoutEntry next = null,
			 prev = null;

	    TimeoutEntry(StreamDemultiplexor demux)
	    {
		this.demux = demux;
	    }

	    void reset()
	    {
		hyber = false;
		if (restart)  return;
		restart = true;

		synchronized(time_list)
		{
		    // remove from current position
		    next.prev = prev;
		    prev.next = next;

		    // and add to end of timeout list
		    next = time_list[current];
		    prev = time_list[current].prev;
		    prev.next = this;
		    next.prev = this;
		}
	    }

	    void hyber()
	    {
		if (alive)  hyber = true;
	    }

	    void kill()
	    {
		alive   = false;
		restart = false;
		hyber   = false;

		synchronized(time_list)
		{
		    next.prev = prev;
		    prev.next = next;
		}
	    }
	}

	private TimeoutEntry[]  time_list;
	private int		current;


	SocketTimeout(int secs)
	{
	    super("SocketTimeout");

	    try { setDaemon(true); }
	    catch (SecurityException se) { }	// Oh well...
	    setPriority(MAX_PRIORITY);

	    time_list = new TimeoutEntry[secs];
	    for (int idx=0; idx<secs; idx++)
	    {
		time_list[idx] = new TimeoutEntry(null);
		time_list[idx].next = time_list[idx].prev = time_list[idx];
	    }
	    current = 0;
	}


	public TimeoutEntry setTimeout(StreamDemultiplexor demux)
	{
	    TimeoutEntry entry = new TimeoutEntry(demux);
	    synchronized(time_list)
	    {
		entry.next = time_list[current];
		entry.prev = time_list[current].prev;
		entry.prev.next = entry;
		entry.next.prev = entry;
	    }

	    return entry;
	}


	/**
	 * This timer is implemented by sleeping for 1 second and then
	 * checking the timer list.
	 */
	public void run()
	{
            TimeoutEntry entry, currentEntry;
	    while (true)
	    {
		try { sleep(1000L); } catch (InterruptedException ie) { }

		synchronized(time_list)
		{
		    // reset all restart flags
		    for (entry = time_list[current].next;
			 entry != time_list[current];
			 entry = entry.next)
		    {
			entry.restart = false;
		    }

		    current++;
		    if (current >= time_list.length)
			current = 0;

        /**
         * The following is a very sophisticated bugfix for deadlocks, as the time_list must always
         * be synchronized AFTER a StreamDemultiplexor. Here ist the original code, that was transformed to
         * fulfill this condition:

            // remove all expired timers
            for (TimeoutEntry entry = time_list[current].next;
                 entry != time_list[current];
                 entry = entry.next)
            {
               try
                        {
                            synchronized(entry.demux)
                            {
                                if (entry.alive  &&  !entry.hyber)
                                {
                                    entry.demux.markForClose(null);
                                    entry.kill();
                                }
                            }
                        }
                        catch (NullPointerException npe)
                            { }
                    }
         */

                   // remove all expired timers
                   entry = time_list[current].next;
                   currentEntry = time_list[current];
                }

		    for (;
			 entry != currentEntry;
			 entry = entry.next)
		    {
			try
			{
			    synchronized(entry.demux)
			    {
					//synchronized (time_list)
					{
						if (entry.alive && !entry.hyber)
						{
							entry.demux.markForClose(null);
							entry.kill();
						}
					}
				}
			}
			catch (NullPointerException npe)
			    { }
		    }

	    }
	}
    }

}

