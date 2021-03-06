/*
 * @(#)AuthSchemeNotImplException.java			0.3 30/01/1998
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
 * Signals that the handling of a authorization scheme is not implemented.
 *
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 */

public class AuthSchemeNotImplException extends ModuleException
{

    /**
     * Constructs an AuthSchemeNotImplException with no detail message.
     * A detail message is a String that describes this particular exception.
     */
    public AuthSchemeNotImplException()
    {
	super();
    }


    /**
     * Constructs an AuthSchemeNotImplException class with the specified
     * detail message.  A detail message is a String that describes this
     * particular exception.
     *
     * @param msg the String containing a detail message
     */
    public AuthSchemeNotImplException(String msg)
    {
	super(msg);
    }
}

