/*
 * @(#)AuthorizationHandler.java			0.4 28/09/1998
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
 * This is the interface that an Authorization handler must implement. You
 * can implement your own auth handler to add support for auth schemes other
 * than the ones handled by the default handler, to use a different UI for
 * soliciting usernames and passwords, or for using an altogether different
 * way of getting the necessary auth info.
 *
 * @see AuthorizationInfo#setAuthHandler(HTTPClient.AuthorizationHandler)
 * @version	0.4  28/09/1998
 * @author	Ronald Tschal&auml;r
 */

public interface AuthorizationHandler
{
    /**
     * This is method invoked just after a WWW-Authenticate or
     * Proxy-Authenticate has been parsed and before any attempt is made
     * find or query for any of the necessary authorization info. The
     * handler is expected to order the challenges from most preferred
     * to least preferred. Usually this is done based on the scheme (i.e.
     * most secure to least secure), but can be done based on any criteria.
     * The AuthorizationModule will then try to handle each challenge in
     * that order.
     *
     * @param challenges the parsed challenges from the server
     * @param req        the request which provoked this response
     * @param resp       the full response.
     * @param proxy      true if the challenge is from a proxy (i.e from
     *                   a Proxy-Authenticate header)
     * @return an ordered array of challenges, with index 0 being the most
     *         preferred
     */
    AuthorizationInfo[] orderChallenges(AuthorizationInfo[] challenges,
				        RoRequest req, RoResponse resp,
				        boolean proxy);

    /**
     * This method is invoked whenever a 401 or 407 response is received and
     * no candidate info is found in the list of known auth info. Usually
     * this method will query the user for the necessary info.
     *
     * <P>If the returned info is not null it will be added to the list of
     * known info. If the info is valid for more than one (host, port, realm,
     * scheme) tuple then this method must add the corresponding auth infos
     * itself.
     *
     * <P>This method must check <var>req.allow_ui</var> and only attempt
     * user interaction if it's <var>true</var>.
     *
     * @param challenge the parsed challenge from the server; the host,
     *                  port, scheme, realm and params are set to the
     *                  values given by the server in the challenge.
     * @param req       the request which provoked this response; if the
     *                  invocation does not return null then this is also
     *                  the new request to be sent, and can be modified
     *                  accordingly.
     * @param resp      the full response.
     * @param proxy     true if the challenge is from a proxy (i.e from
     *                  a Proxy-Authenticate header)
     * @return the authorization info to use when retrying the request,
     *         or null if the request is not to be retried. The necessary
     *         info includes the host, port, scheme and realm as given in
     *         the <var>challenge</var> parameter, plus either the basic
     *         cookie or any necessary params.
     * @exception AuthSchemeNotImplException if the authorization scheme
     *             in the challenge cannot be handled.
     */
    AuthorizationInfo getAuthorization(AuthorizationInfo challenge,
				       RoRequest req, RoResponse resp,
				       boolean proxy)
	    throws AuthSchemeNotImplException;


    /**
     * This method is invoked whenever auth info is chosen from the list of
     * known info in the AuthorizationInfo class to be sent with a request.
     * This happens when either auth info is being preemptively sent or if
     * a 401 response is retrieved and a matching info is found in the list
     * of known info. The intent of this method is to allow the handler to
     * fix up the info being sent based on the actual request (e.g. in digest
     * authentication the digest-uri, nonce and response-digest usually need
     * to be recalculated).
     *
     * @param info      the authorization info retrieved from the list of
     *                  known info.
     * @param req       the request this info is targeted for.
     * @param challenge the authorization challenge received from the server
     *                  if this is in response to a 401, or null if we are
     *                  preemptively sending the info.
     * @param resp      the full 401 response received, or null if we are
     *                  preemptively sending the info.
     * @param proxy     true if the info is for a proxy (i.e. for a
     *                  Proxy-Authorization header)
     * @return the authorization info to be sent with the request, or null
     *         if none is to be sent.
     * @exception AuthSchemeNotImplException if the authorization scheme
     *             in the info cannot be handled.
     */
    AuthorizationInfo fixupAuthInfo(AuthorizationInfo info, Request req,
				    AuthorizationInfo challenge,
				    RoResponse resp, boolean proxy)
	    throws AuthSchemeNotImplException;


    /**
     * Sometimes even non-401 responses will contain headers pertaining to
     * authorization (such as the "Authentication-Info" header). Therefore
     * this method is invoked for each response received, even if it is not
     * a 401 or 407 response. In case of a 401 or 407 response the methods
     * <code>fixupAuthInfo()</code> and <code>getAuthorization()</code> are
     * invoked <em>after</em> this method.
     *
     * @param resp the full Response
     * @param req  the Request which provoked this response
     * @param prev the previous auth info sent, or null if none was sent
     * @param prxy the previous proxy auth info sent, or null if none was sent
     * @exception IOException if an exception occurs during the reading of
     *            the headers.
     */
    void handleAuthHeaders(Response resp, RoRequest req,
			   AuthorizationInfo prev, AuthorizationInfo prxy)
	    throws IOException;


    /**
     * This method is similar to <code>handleAuthHeaders</code> except that
     * it is invoked if any headers in the trailer were sent. This also
     * implies that it is invoked after any <code>fixupAuthInfo()</code> or
     * <code>getAuthorization()</code> invocation.
     *
     * @param resp the full Response
     * @param req  the Request which provoked this response
     * @param prev the previous auth info sent, or null if none was sent
     * @param prxy the previous proxy auth info sent, or null if none was sent
     * @exception IOException if an exception occurs during the reading of
     *            the trailers.
     * @see #handleAuthHeaders(Response, RoRequest, AuthorizationInfo, AuthorizationInfo)
     */
    void handleAuthTrailers(Response resp, RoRequest req,
			    AuthorizationInfo prev, AuthorizationInfo prxy)
	    throws IOException;


    /**
     * <code>AuthorizationInfo.addAuthorization(...)</code> invokes this
     * method to do the actual auth info setup. This method is expected
     * to create an instance of <var>AuthorizationInfo</var>, fill in all
     * necessary fields, and then invoke
     * <code>AuthorizationInfo.addAuthorization(AuthorizationInfo, context)</code>.
     *
     * @param scheme  the authentication scheme for which to use this info
     * @param host    the server or proxy host for which this info is valid
     * @param port    the server or proxy port for which this info is valid
     * @param realm   the realm for which this info is valid
     * @param name    (usually) the username
     * @param passwd  (usually) the password
     * @param context the context for which this info is valid
     * @exception AuthSchemeNotImplException if the authorization scheme
     *             in the info cannot be handled.
     * @see AuthorizationInfo#addAuthorization(HTTPClient.AuthorizationInfo, java.lang.Object)
     */
    void addAuthorizationInfo(String scheme, String host, int port,
			      String realm, Object name, Object passwd,
			      Object context)
	    throws AuthSchemeNotImplException;
}

