/*
 * @(#)RoRequest.java					0.3 30/01/1998
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
 * This interface represents the read-only interface of an http request.
 * It is the compile-time type passed to various handlers which might
 * need the request info but musn't modify the request.
 *
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 */

public interface RoRequest
{
    /**
     * @return the HTTPConnection this request is associated with
     */
    public HTTPConnection getConnection();

    /**
     * @return the request method
     */
    public String getMethod();

    /**
     * @return the request-uri
     */
    public String getRequestURI();

    /**
     * @return the headers making up this request
     */
    public NVPair[] getHeaders();

    /**
     * @return the body of this request
     */
    public byte[] getData();

    /**
     * @return the output stream on which the body is written
     */
    public HttpOutputStream getStream();

}

