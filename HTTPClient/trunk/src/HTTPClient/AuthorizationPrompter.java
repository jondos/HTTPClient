/*
 * @(#)AuthorizationPrompter.java			0.4 12/05/1998
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
 * This is the interface that a username/password prompter must implement.
 * The DefaultAuthorizationHandler invokes an instance of this each time
 * it needs a username and password to satisfy an authorization challenge
 * (for which it doesn't already have the necessary info).
 * 
 * This can be used to implement a different UI from the default popup box
 * for soliciting usernames and passwords, or for using an altogether
 * different way of getting the necessary auth info.
 *
 * @see DefaultAuthHandler#setAuthorizationPrompter(HTTPClient.AuthorizationPrompter)
 * @version	0.4  12/05/1998
 * @author	Ronald Tschal&auml;r
 */

public interface AuthorizationPrompter
{
    /**
     * This method is invoked whenever a username and password is required
     * for an authentication challenge to proceed.
     *
     * @param challenge the parsed challenge from the server; the host,
     *                  port, scheme, realm and params are set to the
     *                  values given by the server in the challenge.
     * @return an NVPair containing the username and password in the name
     *         and value fields, respectively, or null if the authorization
     *         challenge handling is to be aborted (e.g. when the user
     *         hits the <var>Cancel</var> button).
     */
    NVPair getUsernamePassword(AuthorizationInfo challenge);
}

