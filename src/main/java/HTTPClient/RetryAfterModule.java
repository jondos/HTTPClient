/*
 * @(#)RetryAfterModule.java				0.4 30/03/1998
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
import java.util.Date;
import java.util.Hashtable;


/**
 * This module handles the "Retry-After" header. If a response contains
 * a Retry-After header and the delay specified by this header does not
 * exceed a given threshold then the request is delayed before being
 * resent. Additionally, this module also handles a 503 (Service unavailable)
 * response if the Retry-After header is set.
 *
 * @version	0.4  30/03/1998
 * @author	Ronald Tschal&auml;r
 */

public class RetryAfterModule implements HTTPClientModule, GlobalConstants
{
    /** maximum number of seconds a request will be delayed */
    private static int threshold;

    /** list of request marked for retry by appliation */
    private static Hashtable retry_list = new Hashtable();

    /** number of seconds to delay */
    private int delay;


    static
    {
	try
	    { threshold = Integer.getInteger("HTTPClient.retryafter.threshold", 30).intValue(); }
	catch (Exception e)
	    { threshold = 30; }
    }


    // Constructors

    /**
     */
    RetryAfterModule()
    {
	delay = -1;
    }


    // Methods

    /**
     * Invoked by the HTTPClient.
     */
    public int requestHandler(Request req, Response[] resp)
    {
	// first check list for a retried request

	Object dly;
	if (req.getStream() != null  &&
	    (dly = retry_list.get(req.getStream())) != null)
	{
	    delay = ((Integer) dly).intValue();
	    retry_list.remove(req.getStream());
	}


	// forget it if too long

	if (delay > threshold)
	{
	    if (DebugMods)
		HttpClientUtil.logLine("ReAfM: delay exceeds threshold (" + delay +
			     " > " + threshold + ") - aborting request");

	    return REQ_RETURN;
	}


	// delay request

	if (delay >= 0)
	{
	    if (DebugMods)
		HttpClientUtil.logLine("ReAfM: delaying request by " + delay + " sec");

	    try { Thread.sleep(delay * 1000L); }
	    catch (InterruptedException ie) { }
	    delay = -1;
	}

	return REQ_CONTINUE;
    }


    /**
     * Invoked by the HTTPClient.
     */
    public void responsePhase1Handler(Response resp, RoRequest req)
	    throws IOException, ModuleException
    {
	delay = -1;
	if (resp.getHeader("Retry-After") == null)  return;

	try
	    { delay = resp.getHeaderAsInt("Retry-After"); }
	catch (NumberFormatException nfe)
	{
	    // it's not a delta, so must be a date

	    Date ra;
	    try
		{ ra = resp.getHeaderAsDate("Retry-After"); }
	    catch (IllegalArgumentException iae)
	    {
		throw new ModuleException("Illegal value in Retry-After " +
					  "header: '" +
					  resp.getHeader("Retry-After") + "'");
	    }

	    if (ra == null)  return;


	    // get the server date

	    Date date;
	    try
		{ date = resp.getHeaderAsDate("Date"); }
	    catch (IllegalArgumentException iae)
	    {
		throw new ModuleException("Illegal value in Date header: '" +
					  resp.getHeader("Date") + "'");
	    }


	    // no date from server, so use now

	    if (date == null)  date = new Date();


	    // we can finally determine delta-seconds

	    delay = (int) ((ra.getTime() - date.getTime()) / 1000);

	    if (DebugMods)
		HttpClientUtil.logLine("ReAfM: delay = " + delay + " sec");
	}
    }


    /**
     * Invoked by the HTTPClient.
     */
    public int responsePhase2Handler(Response resp, Request req)
	    throws IOException
    {
	// handle 503 (Service unavailable) response

	if (resp.getStatusCode() == 503  &&  delay >= 0)
	{
	    // forget if delay too long

	    if (delay > threshold)
	    {
		if (DebugMods)
		    HttpClientUtil.logLine("ReAfM: delay exceeds threshold (" + delay +
				 " > " + threshold + ") - not retrying request");

		return RSP_CONTINUE;
	    }


	    // let application retry if an output stream was used

	    if (req.getStream() != null)
	    {
		retry_list.put(req.getStream(), new Integer(delay));
		req.getStream().reset();
		resp.setRetryRequest(true);

		return RSP_CONTINUE;
	    }


	    // retry the request

	    if (DebugMods)
		HttpClientUtil.logLine("ReAfM: handling 503 status - retrying request");

	    return RSP_REQUEST;
	}
	else
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
     * Set the threshold, above which a Retry-After will not be honored.
     * If the Retry-After header specifies a wait which is longer than
     * this threshold then an error is returned. The default threshold
     * is 30 seconds, and can be set via the
     * <var>HTTPClient.retryafter.threshold</var> property.
     *
     * @param secs maximum number of seconds a request will be delayed
     */
    public static void setThreshold(int secs)
    {
	threshold = secs;
    }
}

