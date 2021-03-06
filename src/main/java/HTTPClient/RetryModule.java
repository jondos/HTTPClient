/*
 * @(#)RetryModule.java					0.3 30/01/1998
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
 * This module handles request retries when a connection closes prematurely.
 * It is triggered by the RetryException thrown by the StreamDemultiplexor.
 *
 * <P>This module is somewhat unique in that it doesn't strictly limit itself
 * to the HTTPClientModule interface and its return values. That is, it
 * sends request directly using the HTTPConnection.sendRequest() method. This
 * is necessary because this module will not only resend its request but it
 * also resend all other requests in the chain. Also, it rethrows the
 * RetryException in Phase1 to restart the processing of the modules.
 *
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 * @since	V0.3
 */

class RetryModule implements HTTPClientModule, GlobalConstants
{
    // Constructors

    /**
     */
    RetryModule()
    {
    }


    // Methods

    /**
     * Invoked by the HTTPClient.
     */
    public int requestHandler(Request req, Response[] resp)
    {
	return REQ_CONTINUE;
    }


    /**
     * Invoked by the HTTPClient.
     */
    public void responsePhase1Handler(Response resp, RoRequest roreq)
	    throws IOException, ModuleException
    {
	try
	{
	    resp.getStatusCode();
	}
	catch (RetryException re)
	{
	    if (DebugMods) HttpClientUtil.logLine("RtryM: Caught RetryException");

	    boolean got_lock = false;

	    try
	    {
	    synchronized (re.first)
	    {
		got_lock = true;

		// initialize idempotent sequence checking
		IdempotentSequence seq = new IdempotentSequence();
		for (RetryException e=re.first; e!=null; e=e.next)
		    seq.add(e.request);

		for (RetryException e=re.first; e!=null; e=e.next)
		{
		    Request req = e.request;
		    HTTPConnection con = req.getConnection();

		    /** Don't retry if either the current thread is interrupted, the sequence is not idempotent
		     * (Sec 8.1.4 and 9.1.2), or we've already retried enough
		     * times, or the headers have been read and parsed
		     * already
                     * @author      modified by Rolf Wendolsky, 2006/05/31 (added test for interruption)
		     */
		    if (Thread.currentThread().isInterrupted() || !seq.isIdempotent(req)  ||
			(con.ServProtVersKnown  &&
			 con.ServerProtocolVersion >= HTTP_1_1  &&
			 req.num_retries > 0)  ||
			((!con.ServProtVersKnown  ||
			  con.ServerProtocolVersion <= HTTP_1_0)  &&
			 req.num_retries > 4)  ||
			e.response.got_headers)
		    {
			e.first = null;
			continue;
		    }


		    /**
		     * if an output stream was used (i.e. we don't have the
		     * data to resend) then delegate the responsibility for
		     * resending to the application.
		     */
		    if (req.getStream() != null)
		    {
			e.first = null;
			req.getStream().reset();
			e.response.setRetryRequest(true);
			continue;
		    }


		    /* If we have an entity then setup either the entity-delay
		     * or the Expect header
		     */
		    if (req.getData() != null  &&  e.conn_reset)
		    {
			if (con.ServProtVersKnown  &&
			    con.ServerProtocolVersion >= HTTP_1_1)
			    req.setHeaders(HttpClientUtil.addToken(req.getHeaders(),
						    "Expect", "100-continue"));
			else
			    req.delay_entity = 5000L << req.num_retries;
		    }

		    /* If the next request in line has an entity and we're
		     * talking to an HTTP/1.0 server then close the socket
		     * after this request. This is so that the available()
		     * call (to watch for an error response from the server)
		     * will work correctly.
		     */
		    if (e.next != null  &&  e.next.request.getData() != null  &&
			(!con.ServProtVersKnown  ||
			 con.ServerProtocolVersion < HTTP_1_1)  &&
			 e.conn_reset)
		    {
			req.setHeaders(HttpClientUtil.addToken(req.getHeaders(),
						     "Connection", "close"));
		    }


		    /* If this an HTTP/1.1 server then don't pipeline retries.
		     * The problem is that if the server for some reason
		     * decides not to use persistent connections and it does
		     * not do a correct shutdown of the connection, then the
		     * response will be ReSeT. If we did pipeline then we
		     * would keep falling into this trap indefinitely.
		     *
		     * Note that for HTTP/1.0 servers, if they don't support
		     * keep-alives then the normal code will already handle
		     * this accordingly and won't pipe over the same
		     * connection.
		     */
		    if (con.ServProtVersKnown  &&
			con.ServerProtocolVersion >= HTTP_1_1  &&
			e.conn_reset)
		    {
			req.dont_pipeline = true;
		    }


		    // now resend the request

		    if (DebugDemux)
			HttpClientUtil.logLine("RtryM: Retrying request '" +
				     req.getMethod() + " " +
				     req.getRequestURI() + "'");

		    if (e.conn_reset)
			req.num_retries++;
		    e.response.http_resp.set(req,
				    con.sendRequest(req, e.response.timeout));

		    e.exception = null;
		    e.first = null;
		}
	    }
	    }
	    catch (NullPointerException npe)
		{ if (got_lock)  throw npe; }
	    catch (ParseException pe)
		{ throw new IOException(pe.getMessage()); }

	    if (re.exception != null)  throw re.exception;

	    re.restart = true;
	    throw re;
	}
    }


    /**
     * Invoked by the HTTPClient.
     */
    public int responsePhase2Handler(Response resp, Request req)
    {
	// reset any stuff we might have set previously
	req.delay_entity  = 0;
	req.dont_pipeline = false;
	req.num_retries   = 0;

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
}

