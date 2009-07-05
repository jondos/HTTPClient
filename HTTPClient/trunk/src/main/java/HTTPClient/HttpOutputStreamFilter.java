/*
 * @(#)HttpOutputStreamFilter.java			0.4 17/04/1998
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

import java.io.OutputStream;


/**
 * This is the interface that a filter must implement to get a
 * FilterOutputStream pushed onto the output stream of a request; it is
 * used as the parameter to <code>HttpOutputStream.addFilter()</code>.
 *
 * @version	0.4  30/01/1998
 * @author	Ronald Tschal&auml;r
 * @since	V0.4
 */
public interface HttpOutputStreamFilter
{
    /**
     * This is invoked by the HttpOutputStream just after the request
     * headers have been sent, but before the request method returns to
     * the user. Code implementing this method will typically just do a
     *
     * <code>return new FilterOutputStream(out);</code>
     *
     * @param out      the underlying output stream
     * @param request  the request
     * @return the new filter output stream
     */
    public OutputStream pushStream(OutputStream out, RoRequest request);
}

