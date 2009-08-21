/*
 * @(#)Request.java					0.3 30/01/1998
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


/**
 * This class represents an http request. It's used by classes which
 * implement the HTTPClientModule interface.
 *
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 */

public final class Request implements RoRequest, Cloneable
{
    /** the current HTTPConnection */
    private HTTPConnection connection;

    /** the request method to be used (e.g. GET, POST, etc) */
    private String         method;

    /** the request-uri */
    private String         req_uri;

    /** the headers to be used */
    private NVPair[]       headers;

    /** the entity (if any) */
    private byte[]         data;

    /** or an output stream on which the entity will be written */
    private HttpOutputStream stream;

    /** number of millisecs to wait for an error from the server before sending
	the entity (used when retrying requests) */
            long           delay_entity = 0;

    /** number of retries so far */
            int            num_retries = 0;

    /** disable pipelining of following request */
            boolean        dont_pipeline = false;

    /** was this request aborted by the user? */
            boolean        aborted = false;

    /** is this an internally generated subrequest? */
            boolean        internal_subrequest = false;


    // Constructors

    /**
     * Creates a new request structure.
     *
     * @param con      the current HTTPConnection
     * @param method   the request method
     * @param req_uri  the request-uri
     * @param headers  the request headers
     * @param data     the entity as a byte[]
     * @param stream   the entity as a stream
     */
    public Request(HTTPConnection con, String method, String req_uri,
		   NVPair[] headers, byte[] data, HttpOutputStream stream)
    {
	this.connection = con;
	this.method     = method;
	this.req_uri    = req_uri;
	this.headers    = headers;
	this.data       = data;
	this.stream     = stream;
    }


    // Methods

    /**
     * @return the HTTPConnection this request is associated with
     */
    public HTTPConnection getConnection()
    {
	return connection;
    }

    /**
     * @param con the HTTPConnection this request is associated with
     */
    public void setConnection(HTTPConnection  con)
    {
	this.connection = con;
    }


    /**
     * @return the request method
     */
    public String getMethod()
    {
	return method;
    }

    /**
     * @param method the request method (e.g. GET, POST, etc)
     */
    public void setMethod(String method)
    {
	this.method = method;
    }


    /**
     * @return the request-uri
     */
    public String getRequestURI()
    {
	return req_uri;
    }

    /**
     * @param req_uri the request-uri
     */
    public void setRequestURI(String req_uri)
    {
	this.req_uri = req_uri;
    }


    /**
     * @return the headers making up this request
     */
    public NVPair[] getHeaders()
    {
	return headers;
    }

    /**
     * @param headers the headers for this request
     */
    public void setHeaders(NVPair[] headers)
    {
	this.headers = headers;
    }


    /**
     * @return the body of this request
     */
    public byte[] getData()
    {
	return data;
    }

    /**
     * @param data the entity for this request
     */
    public void setData(byte[] data)
    {
	this.data = data;
    }


    /**
     * @return the output stream on which the body is written
     */
    public HttpOutputStream getStream()
    {
	return stream;
    }

    /**
     * @param stream an output stream on which the entity is written
     */
    public void setStream(HttpOutputStream stream)
    {
	this.stream = stream;
    }

    /**
     * @return a clone of this request object
     */
    public Object clone()
    {
	Request cl;
	try
	    { cl = (Request) super.clone(); }
	catch (CloneNotSupportedException cnse)
	    { throw new InternalError(cnse.toString()); /* shouldn't happen */ }

	cl.headers = new NVPair[headers.length];
	System.arraycopy(headers, 0, cl.headers, 0, headers.length);

	return cl;
    }


    /**
     * Copy all the fields from <var>other</var> to this request.
     *
     * @param other the Request to copy from
     */
    public void copyFrom(Request other)
    {
	this.connection          = other.connection;
	this.method              = other.method;
	this.req_uri             = other.req_uri;
	this.headers             = other.headers;
	this.data                = other.data;
	this.stream              = other.stream;
	this.delay_entity        = other.delay_entity;
	this.num_retries         = other.num_retries;
	this.dont_pipeline       = other.dont_pipeline;
	this.aborted             = other.aborted;
	this.internal_subrequest = other.internal_subrequest;
    }


    /**
     * @return a string containing the method and request-uri
     */
    public String toString()
    {
	return getClass().getName() + ": " + method + " " + req_uri;
    }
}

