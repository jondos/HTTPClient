/*
 * @(#)HTTPClientModuleAdapter.java			0.4 30/03/1998
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
 * This is an adapter class for modules, similar to the adapter classes
 * in the AWT. It is purely a convenience class which implements all
 * methods defined in the HTTPClientModule interface; the methods just
 * return. Instead of implementing the HTTPClientModule interface directly,
 * modules can just extend this class and need only override those handlers
 * where they wish to do something.
 *
 * @version	0.4  30/03/1998
 * @author	Ronald Tschal&auml;r
 * @since	0.4
 */

public class HTTPClientModuleAdapter implements HTTPClientModule
{
    /**
     * Invoked by the HTTPClient.
     */
    public int requestHandler(Request req, Response[] resp)
	    throws IOException, ModuleException
    {
	return REQ_CONTINUE;
    }


    /**
     * Invoked by the HTTPClient.
     */
    public void responsePhase1Handler(Response resp, RoRequest req)
	    throws IOException, ModuleException
    {
    }


    /**
     * Invoked by the HTTPClient.
     */
    public int responsePhase2Handler(Response resp, Request req)
	    throws IOException, ModuleException
    {
	return RSP_CONTINUE;
    }


    /**
     * Invoked by the HTTPClient.
     */
    public void responsePhase3Handler(Response resp, RoRequest req)
	    throws IOException, ModuleException
    {
    }


    /**
     * Invoked by the HTTPClient.
     */
    public void trailerHandler(Response resp, RoRequest req)
	    throws IOException, ModuleException
    {
    }
}

