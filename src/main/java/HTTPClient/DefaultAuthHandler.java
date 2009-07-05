/*
 * @(#)DefaultAuthHandler.java				0.4 30/01/1998
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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Vector;
import java.util.StringTokenizer;

/**
 * This class is the default authorization handler. It currently handles the
 * authentication schemes "Basic", "Digest", "NTLM", and "SOCKS5" (used for
 * the SocksClient and not part of HTTP per se).
 *
 * @version	0.4  12/05/1998
 * @author	Ronald Tschal&auml;r
 * @author      modified by Stefan K&ouml;psell, 04/11/24
 * @author modified by Stefan Lieske, 2005/02/13
 * @since	V0.2
 */
public class DefaultAuthHandler implements AuthorizationHandler, GlobalConstants
{
    private static final byte[] NUL   = new byte[0];
    private static final byte[] zeros = new byte[24];

    /** @author  removed by Stefan Lieske, 2005/02/13 */
    //private static boolean send_lm_auth = false;

    private static AuthorizationPrompter prompter = null;
    private static ie.brd.crypto.algorithms.DES.DESAlgorithm DES =
			new ie.brd.crypto.algorithms.DES.DESAlgorithm(false);
    private static byte[] digest_secret = null;


    /** @author  removed by Stefan Lieske, 2005/02/13 */
    //static
    //{
	/*
	try
	{
	    send_lm_auth =
		Boolean.getBoolean("HTTPClient.defAuthHandler.NTLM.sendLMAuth");
	}
	catch (Exception e)
	    { }
	*/
        //send_lm_auth = true;
    //}


    private static String[] ordering = { "Digest", "NTLM", "Basic" };

    /**
     * Order challenges based on scheme: Digest, NTLM, Basic
     */
    public AuthorizationInfo[] orderChallenges(AuthorizationInfo[] list,
					       RoRequest req, RoResponse resp,
					       boolean proxy)
    {
	AuthorizationInfo[] new_list = new AuthorizationInfo[list.length];
	int nidx = 0;

	// order known schemes
	for (int oidx=0; oidx<ordering.length; oidx++)
	{
	    for (int sidx=0; sidx<list.length; sidx++)
		if (list[sidx] != null  &&
		    list[sidx].getScheme().equalsIgnoreCase(ordering[oidx]))
		{
		    new_list[nidx++] = list[sidx];
		    list[sidx] = null;
		}
	}
	// tack on any unknown ones to end
	for (int sidx=0; sidx<list.length; sidx++)
	    if (list[sidx] != null)
		new_list[nidx++] = list[sidx];

	return new_list;
    }


    /**
     * For Digest authentication we need to set the uri, response and
     * opaque parameters. For "Basic" and "SOCKS5" nothing is done.
     */
    public AuthorizationInfo fixupAuthInfo(AuthorizationInfo info,
					   Request req,
					   AuthorizationInfo challenge,
					   RoResponse resp, boolean proxy)
		    throws AuthSchemeNotImplException
    {
	// nothing to do for Basic, and SOCKS5 schemes

	if (info.getScheme().equalsIgnoreCase("Basic")  ||
	    info.getScheme().equalsIgnoreCase("SOCKS5"))
	    return info;
	else if (!info.getScheme().equalsIgnoreCase("Digest")  &&
		 !info.getScheme().equalsIgnoreCase("NTLM"))
	    throw new AuthSchemeNotImplException(info.getScheme());

	if (DebugAuth)
	    Util.logLine("Auth:  fixing up Authorization for host " +
			 info.getHost()+":"+info.getPort() +
			 "; scheme: " + info.getScheme() +
			 "; realm: " + info.getRealm());

	if (info.getScheme().equalsIgnoreCase("Digest"))
	    return digest_fixup(info, req, challenge, resp, proxy);
	else
	    return ntlm_fixup(info, req, challenge, resp);
    }


    /**
     * returns the requested authorization, or null if none was given.
     *
     * @param challenge the parsed challenge from the server.
     * @param req the request which solicited this response
     * @param resp the full response received
     * @param proxy true if the challenge comes from a proxy (i.e. from
     *              a Proxy-Authenticate)
     * @return a structure containing the necessary authorization info,
     *         or null
     * @exception AuthSchemeNotImplException if the authentication scheme
     *             in the challenge cannot be handled.
     */
    public AuthorizationInfo getAuthorization(AuthorizationInfo challenge,
					      RoRequest req, RoResponse resp,
					      boolean proxy)
		    throws AuthSchemeNotImplException
    {
	AuthorizationInfo cred;


	if (DebugAuth)
	    Util.logLine("Auth:  Requesting Authorization for host " +
			 challenge.getHost()+":"+challenge.getPort() +
			 "; challenge: " + challenge);


	// we only handle Basic, Digest, NTLM, and SOCKS5 authentication

	if (!challenge.getScheme().equalsIgnoreCase("Basic")  &&
	    !challenge.getScheme().equalsIgnoreCase("Digest")  &&
	    !challenge.getScheme().equalsIgnoreCase("NTLM")  &&
	    !challenge.getScheme().equalsIgnoreCase("SOCKS5"))
	    throw new AuthSchemeNotImplException(challenge.getScheme());


	// For digest authentication, check if stale is set

	if (challenge.getScheme().equalsIgnoreCase("Digest"))
	{
	    cred = digest_check_stale(challenge, req, resp);
	    if (cred != null)
		return cred;
	}


	// For NTLM check if this is step 2 of the handshake

	else if (challenge.getScheme().equalsIgnoreCase("NTLM"))
	{
	    cred = ntlm_check_step2(challenge, req, resp);
	    if (cred != null)
		return cred;
	}


	// Ask the user for username/password
        /** @author modified by Stefan K&ouml;psell, 04/11/24 */
	if (!req.allowUI()||prompter==null)
	    return null;
        /** @author modified by Stefan K&ouml;psell, 04/11/24 */
	//if (prompter == null)
	//    setDefaultPrompter();

	NVPair answer = prompter.getUsernamePassword(challenge);
	if (answer == null)
	    return null;


	// Now process the username/password

	if (challenge.getScheme().equalsIgnoreCase("Basic"))
	    cred = basic_gen_auth_info(challenge.getHost(), challenge.getPort(),
				       challenge.getRealm(), answer.getName(),
				       answer.getValue());

	else if (challenge.getScheme().equalsIgnoreCase("Digest"))
	{
	    cred = digest_gen_auth_info(challenge.getHost(),
					challenge.getPort(),
				        challenge.getRealm(), answer.getName(),
					answer.getValue(),
					req.getConnection().getContext());
	    cred = digest_fixup(cred, req, challenge, null, proxy);
	}

	else if (challenge.getScheme().equalsIgnoreCase("NTLM"))
	{
	    cred = ntlm_gen_auth_info(challenge.getHost(), challenge.getPort(),
				      challenge.getRealm(), answer.getName(),
				      answer.getValue());
	    cred = ntlm_fixup(cred, req, challenge, null);
	}

	else	// SOCKS5
	    cred = socks5_gen_auth_info(challenge.getHost(),
					challenge.getPort(),
					challenge.getRealm(),
					answer.getName(), answer.getValue());


	// try to get rid of any unencoded passwords in memory

	answer = null;
	System.gc();


	// Done

	if (DebugAuth) Util.logLine("Auth:  Got Authorization");

	return cred;
    }


    /**
     * We handle the "Authentication-Info" and "Proxy-Authentication-Info"
     * headers here.
     */
    public void handleAuthHeaders(Response resp, RoRequest req,
				  AuthorizationInfo prev,
				  AuthorizationInfo prxy)
	    throws IOException
    {
	String auth_info = resp.getHeader("Authentication-Info");
	String prxy_info = resp.getHeader("Proxy-Authentication-Info");

	if (auth_info == null  &&  prev != null  &&
	    hasParam(prev.getParams(), "qop", "auth-int"))
	    auth_info = "";

	if (prxy_info == null  &&  prxy != null  &&
	    hasParam(prxy.getParams(), "qop", "auth-int"))
	    prxy_info = "";

	try
	{
	    handleAuthInfo(auth_info, "Authentication-Info", prev, resp, req,
			   true);
	    handleAuthInfo(prxy_info, "Proxy-Authentication-Info", prxy, resp,
			   req, true);
	}
	catch (ParseException pe)
	    { throw new IOException(pe.toString()); }
    }


    /**
     * We handle the "Authentication-Info" and "Proxy-Authentication-Info"
     * trailers here.
     */
    public void handleAuthTrailers(Response resp, RoRequest req,
				   AuthorizationInfo prev,
				   AuthorizationInfo prxy)
	    throws IOException
    {
	String auth_info = resp.getTrailer("Authentication-Info");
	String prxy_info = resp.getTrailer("Proxy-Authentication-Info");

	try
	{
	    handleAuthInfo(auth_info, "Authentication-Info", prev, resp, req,
			   false);
	    handleAuthInfo(prxy_info, "Proxy-Authentication-Info", prxy, resp,
			   req, false);
	}
	catch (ParseException pe)
	    { throw new IOException(pe.toString()); }
    }


    private static void handleAuthInfo(String auth_info, String hdr_name,
				       AuthorizationInfo prev, Response resp,
				       RoRequest req, boolean in_headers)
	    throws ParseException, IOException
    {
	if (auth_info == null)  return;

	Vector pai = Util.parseHeader(auth_info);
	HttpHeaderElement elem;

	if (handle_nextnonce(prev, req,
			     elem = Util.getElement(pai, "nextnonce")))
	    pai.removeElement(elem);
	if (handle_discard(prev, req, elem = Util.getElement(pai, "discard")))
	    pai.removeElement(elem);

	if (in_headers)
	{
	    HttpHeaderElement qop = null;

	    if (pai != null  &&
		(qop = Util.getElement(pai, "qop")) != null  &&
		qop.getValue() != null)
	    {
		handle_rspauth(prev, resp, req, pai, hdr_name);
	    }
	    else if (prev != null  &&
		     (Util.hasToken(resp.getHeader("Trailer"), hdr_name)  &&
		      hasParam(prev.getParams(), "qop", null)  ||
		      hasParam(prev.getParams(), "qop", "auth-int")))
	    {
		handle_rspauth(prev, resp, req, null, hdr_name);
	    }

	    else if ((pai != null  &&  qop == null  &&
		      pai.contains(new HttpHeaderElement("digest")))  ||
		     (Util.hasToken(resp.getHeader("Trailer"), hdr_name)  &&
		      prev != null  &&
		      !hasParam(prev.getParams(), "qop", null)))
	    {
		handle_digest(prev, resp, req, hdr_name);
	    }
	}

	if (pai.size() > 0)
	    resp.setHeader(hdr_name, Util.assembleHeader(pai));
	else
	    resp.deleteHeader(hdr_name);
    }


    private static final boolean hasParam(NVPair[] params, String name,
					  String val)
    {
	for (int idx=0; idx<params.length; idx++)
	    if (params[idx].getName().equalsIgnoreCase(name)  &&
		(val == null  ||  params[idx].getValue().equalsIgnoreCase(val)))
		return true;

	return false;
    }


    public void addAuthorizationInfo(String scheme, String host, int port,
				     String realm, Object name, Object passwd,
				     Object context)
	    throws AuthSchemeNotImplException
    {
	AuthorizationInfo info = null;

	if (scheme.equalsIgnoreCase("Basic"))
	    info = basic_gen_auth_info(host, port, realm, (String) name,
				       (String) passwd);
	else if (scheme.equalsIgnoreCase("Digest"))
	    info = digest_gen_auth_info(host, port, realm, (String) name,
					(String) passwd, context);
	else if (scheme.equalsIgnoreCase("NTLM"))
	    info = ntlm_gen_auth_info(host, port, realm, (String) name,
				      (String) passwd);
	else if (scheme.equalsIgnoreCase("SOCKS5"))
	    info = socks5_gen_auth_info(host, port, realm, (String) name,
					(String) passwd);
	else
	    // we only handle Basic, Digest, NTLM, and SOCKS5 authentication
	    throw new AuthSchemeNotImplException(scheme);

	AuthorizationInfo.addAuthorization(info, context);
    }


    /*
     * Here are all the Basic specific methods
     */

    private static AuthorizationInfo basic_gen_auth_info(String host, int port,
							 String realm,
							 String user,
							 String pass)
    {
	return new AuthorizationInfo(host, port, "Basic", realm,
				     Codecs.base64Encode(user + ":" + pass));
    }


    /*
     * Here are all the SOCKS5 specific methods
     */

    private static AuthorizationInfo socks5_gen_auth_info(String host, int port,
							  String realm,
							  String user,
							  String pass)
    {
	NVPair[] upwd = { new NVPair(user, pass) };
	return new AuthorizationInfo(host, port, "SOCKS5", realm, upwd, null);
    }


    /*
     * Here are all the Digest specific methods
     */

    private static AuthorizationInfo digest_gen_auth_info(String host, int port,
							  String realm,
							  String user,
							  String pass,
							  Object context)
    {
	String A1 = user + ":" + realm + ":" + pass;
	String[] a1s = { new MD5(A1).asHex(), null };

	AuthorizationInfo prev = AuthorizationInfo.getAuthorization(host, port,
						    "Digest", realm, context);
	NVPair[] params;
	if (prev == null)
	{
	    params = new NVPair[4];
	    params[0] = new NVPair("username", user);
	    params[1] = new NVPair("uri", "");
	    params[2] = new NVPair("nonce", "");
	    params[3] = new NVPair("response", "");
	}
	else
	{
	    params = prev.getParams();
	    for (int idx=0; idx<params.length; idx++)
	    {
		if (params[idx].getName().equalsIgnoreCase("username"))
		{
		    params[idx] = new NVPair("username", user);
		    break;
		}
	    }
	}

	return new AuthorizationInfo(host, port, "Digest", realm, params, a1s);
    }


    /**
     * The fixup handler
     */
    private static AuthorizationInfo digest_fixup(AuthorizationInfo info,
						  RoRequest req,
						  AuthorizationInfo challenge,
						  RoResponse resp,
						  boolean proxy)
	    throws AuthSchemeNotImplException
    {
	// get various parameters from challenge

	int ch_domain=-1, ch_nonce=-1, ch_alg=-1, ch_opaque=-1, ch_stale=-1,
	    ch_dreq=-1, ch_qop=-1;
	NVPair[] ch_params = null;
	if (challenge != null)
	{
	    ch_params = challenge.getParams();

	    for (int idx=0; idx<ch_params.length; idx++)
	    {
		String name = ch_params[idx].getName().toLowerCase();
		if (name.equals("domain"))               ch_domain = idx;
		else if (name.equals("nonce"))           ch_nonce  = idx;
		else if (name.equals("opaque"))          ch_opaque = idx;
		else if (name.equals("algorithm"))       ch_alg    = idx;
		else if (name.equals("stale"))           ch_stale  = idx;
		else if (name.equals("digest-required")) ch_dreq   = idx;
		else if (name.equals("qop"))             ch_qop    = idx;
	    }
	}


	// get various parameters from info

	int uri=-1, user=-1, alg=-1, response=-1, nonce=-1, cnonce=-1, nc=-1,
	    opaque=-1, digest=-1, dreq=-1, qop=-1;
	NVPair[] params;
	String[] extra;

	synchronized(info)	// we need to juggle nonce, nc, etc
	{
	    params = info.getParams();

	    for (int idx=0; idx<params.length; idx++)
	    {
		String name = params[idx].getName().toLowerCase();
		if (name.equals("uri"))                  uri      = idx;
		else if (name.equals("username"))        user     = idx;
		else if (name.equals("algorithm"))       alg      = idx;
		else if (name.equals("nonce"))           nonce    = idx;
		else if (name.equals("cnonce"))          cnonce   = idx;
		else if (name.equals("nc"))              nc       = idx;
		else if (name.equals("response"))        response = idx;
		else if (name.equals("opaque"))          opaque   = idx;
		else if (name.equals("digest"))          digest   = idx;
		else if (name.equals("digest-required")) dreq     = idx;
		else if (name.equals("qop"))             qop      = idx;
	    }



	    // currently only MD5 hash (and "MD5-sess") is supported

	    if (alg != -1  &&
		!params[alg].getValue().equalsIgnoreCase("MD5")  &&
		!params[alg].getValue().equalsIgnoreCase("MD5-sess"))
		throw new AuthSchemeNotImplException("Digest auth scheme: " +
				    "Algorithm " + params[alg].getValue() +
				    " not implemented");

	    if (ch_alg != -1  &&
		!ch_params[ch_alg].getValue().equalsIgnoreCase("MD5") &&
		!ch_params[ch_alg].getValue().equalsIgnoreCase("MD5-sess"))
		throw new AuthSchemeNotImplException("Digest auth scheme: " +
				    "Algorithm " + ch_params[ch_alg].getValue()+
				    " not implemented");


	    // fix up uri and nonce

	    params[uri] = new NVPair("uri", req.getRequestURI());
	    String old_nonce = params[nonce].getValue();
	    if (ch_nonce != -1  &&
		!old_nonce.equals(ch_params[ch_nonce].getValue()))
		params[nonce] = ch_params[ch_nonce];


	    // update or add optional attributes (opaque, algorithm, cnonce,
	    // nonce-count, and qop

	    if (ch_opaque != -1)
	    {
		if (opaque == -1)
		{
		    params = Util.resizeArray(params, params.length+1);
		    opaque = params.length-1;
		}
		params[opaque] = ch_params[ch_opaque];
	    }

	    if (ch_alg != -1)
	    {
		if (alg == -1)
		{
		    params = Util.resizeArray(params, params.length+1);
		    alg = params.length-1;
		}
		params[alg] = ch_params[ch_alg];
	    }

	    if (ch_qop != -1  ||
		(ch_alg != -1  &&
		 ch_params[ch_alg].getValue().equalsIgnoreCase("MD5-sess")))
	    {
		if (cnonce == -1)
		{
		    params = Util.resizeArray(params, params.length+1);
		    cnonce = params.length-1;
		}

		if (digest_secret == null)
		    digest_secret = gen_random_bytes(20);

		long l_time = System.currentTimeMillis();
		byte[] time = new byte[8];
		time[0] = (byte) (l_time & 0xFF);
		time[1] = (byte) ((l_time >>  8) & 0xFF);
		time[2] = (byte) ((l_time >> 16) & 0xFF);
		time[3] = (byte) ((l_time >> 24) & 0xFF);
		time[4] = (byte) ((l_time >> 32) & 0xFF);
		time[5] = (byte) ((l_time >> 40) & 0xFF);
		time[6] = (byte) ((l_time >> 48) & 0xFF);
		time[7] = (byte) ((l_time >> 56) & 0xFF);

		MD5 hash = new MD5(digest_secret);
		hash.Update(time);
		params[cnonce] = new NVPair("cnonce", hash.asHex());
	    }


	    // select qop option

	    if (ch_qop != -1)
	    {
		if (qop == -1)
		{
		    params = Util.resizeArray(params, params.length+1);
		    qop = params.length-1;
		}

		String[] qops =
			    Util.splitList(ch_params[ch_qop].getValue(), ",");
		String p = null;
		for (int idx=0; idx<qops.length; idx++)
		{
		    if (qops[idx].equalsIgnoreCase("auth-int")  &&
			req.getStream() == null)
		    {
			p = "auth-int";
			break;
		    }
		    if (qops[idx].equalsIgnoreCase("auth"))
			p = "auth";
		}
		if (p == null)
		{
		    for (int idx=0; idx<qops.length; idx++)
			if (qops[idx].equalsIgnoreCase("auth-int"))
			    throw new AuthSchemeNotImplException(
				"Digest auth scheme: Can't comply with qop " +
				"option 'auth-int' because data not available");

		    throw new AuthSchemeNotImplException("Digest auth scheme: "+
				"None of the available qop options '" +
				ch_params[ch_qop].getValue() + "' implemented");
		}
    /** @author      modified by Stefan Lieske, 2005/02/13 */
    //params[qop] = new NVPair("qop", p);
    params[qop] = new NVPair("qop", p, false);
	    }


	    // increment nonce-count.

	    if (qop != -1)
	    {
		/* Note: we should actually be serializing all requests through
		 *       here so that the server sees the nonce-count in a
		 *       strictly increasing order. However, this would be a
		 *       *major* hassle to do, so we're just winging it. Most
		 *       of the time the requests will go over the wire in the
		 *       same order as they pass through here, but in MT apps
		 *       it's possible for one request to "overtake" another
		 *       between here and the synchronized block in
		 *       sendRequet().
		 */
		if (nc == -1)
		{
		    params = Util.resizeArray(params, params.length+1);
		    nc = params.length-1;
        /** @author      modified by Stefan Lieske, 2005/02/13 */
        //params[nc] = new NVPair("nc", "00000001");
        params[nc] = new NVPair("nc", "00000001", false);
		}
		else if (old_nonce.equals(params[nonce].getValue()))
		{
		    String c = Long.toHexString(
				Long.parseLong(params[nc].getValue(), 16) + 1);
        /** @author      modified by Stefan Lieske, 2005/02/13 */
        //params[nc] =
        //    new NVPair("nc", "00000000".substring(c.length()) + c, false);
		    params[nc] =
          new NVPair("nc", "00000000".substring(c.length()) + c, false);        
		}
		else
        /** @author      modified by Stefan Lieske, 2005/02/13 */
        //params[nc] = new NVPair("nc", "00000001");
        params[nc] = new NVPair("nc", "00000001", false);
	    }


	    // calc new session key if necessary

	    extra = (String[]) info.getExtraInfo();

	    if (challenge != null  &&
		(ch_stale == -1  ||
		 !ch_params[ch_stale].getValue().equalsIgnoreCase("true"))  &&
		alg != -1  &&
		params[alg].getValue().equalsIgnoreCase("MD5-sess"))
	    {
		extra[1] = new MD5(extra[0] + ":" + params[nonce].getValue() +
				   ":" + params[cnonce].getValue()).asHex();
		info.setExtraInfo(extra);
	    }


	    // update parameters for next auth cycle

	    info.setParams(params);
	}


	// calc "response" attribute

	String A1, A2, resp_val;

	if (alg != -1  &&  params[alg].getValue().equalsIgnoreCase("MD5-sess"))
	    A1 = extra[1];
	else
	    A1 = extra[0];

	A2 = req.getMethod() + ":" + params[uri].getValue();
	if (qop != -1  &&  params[qop].getValue().equalsIgnoreCase("auth-int"))
	{
	    MD5 entity_hash = new MD5();
	    entity_hash.Update(req.getData() == null ? NUL : req.getData());
	    A2 += ":" + entity_hash.asHex();
	}
	A2 = new MD5(A2).asHex();

	if (qop == -1)
	    resp_val =
		new MD5(A1 + ":" + params[nonce].getValue() + ":" + A2).asHex();
	else
	    resp_val =
		new MD5(A1 + ":" + params[nonce].getValue() + ":" +
			params[nc].getValue() + ":" +
			params[cnonce].getValue() + ":" +
			params[qop].getValue() + ":" + A2).asHex();

	params[response] = new NVPair("response", resp_val);


	// calc digest if necessary

	AuthorizationInfo new_info;

	boolean ch_dreq_val = false;
	if (ch_dreq != -1  &&
	    (ch_params[ch_dreq].getValue() == null  ||
	     ch_params[ch_dreq].getValue().equalsIgnoreCase("true")))
	    ch_dreq_val = true;

	if ((ch_dreq_val  ||  digest != -1)  &&  req.getStream() == null)
	{
	    NVPair[] d_params;
	    if (digest == -1)
	    {
		d_params = Util.resizeArray(params, params.length+1);
		digest = params.length;
	    }
	    else
		d_params = params;
	    d_params[digest] =
		new NVPair("digest",
		       calc_digest(req, extra[0], params[nonce].getValue()));

	    if (dreq == -1)	// if server requires digest, then so do we...
	    {
		dreq = d_params.length;
		d_params = Util.resizeArray(d_params, d_params.length+1);
		d_params[dreq] = new NVPair("digest-required", "true");
	    }

	    new_info = new AuthorizationInfo(info.getHost(), info.getPort(),
					     info.getScheme(), info.getRealm(),
					     d_params, extra);
	}
	else if (ch_dreq_val)
	    new_info = null;
	else
	    new_info = new AuthorizationInfo(info.getHost(), info.getPort(),
					     info.getScheme(), info.getRealm(),
					     params, extra);


	// add info for other domains, if listed

	if (ch_domain != -1)
	{
	    URI base = null;
	    try
	    {
		base = new URI(req.getConnection().getProtocol(),
			       req.getConnection().getHost(),
			       req.getConnection().getPort(),
			       req.getRequestURI());
	    }
	    catch (ParseException pe)
		{ }

	    StringTokenizer tok =
			new StringTokenizer(ch_params[ch_domain].getValue());
	    while (tok.hasMoreTokens())
	    {
		URI Uri;
		try
		    { Uri = new URI(base, tok.nextToken()); }
		catch (ParseException pe)
		    { continue; }

		AuthorizationInfo tmp =
		    AuthorizationInfo.getAuthorization(Uri.getHost(),
						       Uri.getPort(),
						       info.getScheme(),
						       info.getRealm(),
					     req.getConnection().getContext());
		if (tmp == null)
		{
		    params[uri] = new NVPair("uri", Uri.getPath());
		    tmp = new AuthorizationInfo(Uri.getHost(), Uri.getPort(),
					        info.getScheme(),
						info.getRealm(), params,
						extra);
		    AuthorizationInfo.addAuthorization(tmp);
		}
		if (!proxy)
		    tmp.addPath(Uri.getPath());
	    }
	}
	else if (!proxy  &&  challenge != null)
	{
	    // Spec says that if no domain attribute is present then the
	    // whole server should be considered being in the same space
	    AuthorizationInfo tmp =
		AuthorizationInfo.getAuthorization(challenge.getHost(),
						   challenge.getPort(),
						   info.getScheme(),
						   info.getRealm(),
					     req.getConnection().getContext());
	    if (tmp != null)  tmp.addPath("/");
	}


	// now return the one to use

	return new_info;
    }


    /**
     * @return the fixed info is stale=true; null otherwise
     */
    private static AuthorizationInfo digest_check_stale(
					      AuthorizationInfo challenge,
					      RoRequest req, RoResponse resp)
	    throws AuthSchemeNotImplException
    {
	AuthorizationInfo cred = null;

	NVPair[] params = challenge.getParams();
	for (int idx=0; idx<params.length; idx++)
	{
	    String name = params[idx].getName();
	    if (name.equalsIgnoreCase("stale")  &&
		params[idx].getValue().equalsIgnoreCase("true"))
	    {
		cred = AuthorizationInfo.getAuthorization(challenge, req, resp,
							  false, false);
		if (cred != null)	// should always be the case
		    return digest_fixup(cred, req, challenge, resp, false);
		break;			// should never be reached
	    }
	}

	return cred;
    }


    /**
     * Handle nextnonce field.
     */
    private static boolean handle_nextnonce(AuthorizationInfo prev,
					    RoRequest req,
					    HttpHeaderElement nextnonce)
    {
	if (prev == null  ||  nextnonce == null  ||
	    nextnonce.getValue() == null)
	    return false;

	AuthorizationInfo ai;
	try
	    { ai = AuthorizationInfo.getAuthorization(prev, req, null, false, false); }
	catch (AuthSchemeNotImplException asnie)
	    { ai = prev; /* shouldn't happen */ }
	synchronized(ai)
	{
	    NVPair[] params = ai.getParams();
	    params = Util.setValue(params, "nonce", nextnonce.getValue());
      /** @author  modified by Stefan Lieske, 2005/02/13 */
      //params = Util.setValue(params, "nc", "00000000");
      params = Util.setValue(params, "nc", "00000000", false);
	    ai.setParams(params);
	}

	return true;
    }


    /**
     * Handle digest field of the Authentication-Info response header.
     */
    private static boolean handle_digest(AuthorizationInfo prev, Response resp,
					 RoRequest req, String hdr_name)
	    throws IOException
    {
	if (prev == null)
	    return false;

	NVPair[] params = prev.getParams();
	VerifyDigest
	    verifier = new VerifyDigest(((String[]) prev.getExtraInfo())[0],
					Util.getValue(params, "nonce"),
					req.getMethod(),
					Util.getValue(params, "uri"),
					hdr_name, resp);

	if (resp.hasEntity())
	{
	    if (DebugAuth)
		Util.logLine("Auth:  pushing md5-check-stream to verify " +
			     "digest from " + hdr_name);
	    resp.inp_stream = new MD5InputStream(resp.inp_stream, verifier);
	}
	else
	{
	    if (DebugAuth)
		Util.logLine("Auth:  verifying digest from " + hdr_name);
	    verifier.verifyHash(new MD5().Final(), 0);
	}

	return true;
    }


    /**
     * Handle rspauth field of the Authentication-Info response header.
     */
    private static boolean handle_rspauth(AuthorizationInfo prev, Response resp,
					  RoRequest req, Vector auth_info,
					  String hdr_name)
	    throws IOException
    {
	if (prev == null)
	    return false;


	// get the parameters we sent

	NVPair[] params = prev.getParams();
	int uri=-1, alg=-1, nonce=-1, cnonce=-1, nc=-1;
	for (int idx=0; idx<params.length; idx++)
	{
	    String name = params[idx].getName().toLowerCase();
	    if (name.equals("uri"))            uri    = idx;
	    else if (name.equals("algorithm")) alg    = idx;
	    else if (name.equals("nonce"))     nonce  = idx;
	    else if (name.equals("cnonce"))    cnonce = idx;
	    else if (name.equals("nc"))        nc     = idx;
	}


	// create hash verifier to verify rspauth

	VerifyRspAuth
	    verifier = new VerifyRspAuth(params[uri].getValue(),
			      ((String[]) prev.getExtraInfo())[0],
			      (alg == -1 ? null : params[alg].getValue()),
			      params[nonce].getValue(),
			      (cnonce == -1 ? "" : params[cnonce].getValue()),
			      (nc == -1 ? "" : params[nc].getValue()),
			      hdr_name, resp);


	// if Authentication-Info in header and qop=auth then verify immediately

	HttpHeaderElement qop = null;
	if (auth_info != null  &&
	    (qop = Util.getElement(auth_info, "qop")) != null  &&
	    qop.getValue() != null  &&
	    (qop.getValue().equalsIgnoreCase("auth")  ||
	     !resp.hasEntity()  &&  qop.getValue().equalsIgnoreCase("auth-int"))
	   )
	{
	    if (DebugAuth)
		Util.logLine("Auth:  verifying rspauth from " + hdr_name);
	    verifier.verifyHash(new MD5().Final(), 0);
	}
	else
	{
	    // else push md5 stream and verify after body

	    if (DebugAuth)
		Util.logLine("Auth:  pushing md5-check-stream to verify " +
			     "rspauth from " + hdr_name);
	    resp.inp_stream = new MD5InputStream(resp.inp_stream, verifier);
	}

	return true;
    }


    /**
     * Calculates the digest of the request body. This was in RFC-2069
     * and draft-ietf-http-authentication-00.txt, but has subsequently
     * been removed. Here for backwards compatibility.
     */
    private static String calc_digest(RoRequest req, String A1_hash,
				      String nonce)
    {
	if (req.getStream() != null)
	    return "";

	int ct=-1, ce=-1, lm=-1, ex=-1, dt=-1;
	for (int idx=0; idx<req.getHeaders().length; idx++)
	{
	    String name = req.getHeaders()[idx].getName();
	    if (name.equalsIgnoreCase("Content-type"))
		ct = idx;
	    else if (name.equalsIgnoreCase("Content-Encoding"))
		ce = idx;
	    else if (name.equalsIgnoreCase("Last-Modified"))
		lm = idx;
	    else if (name.equalsIgnoreCase("Expires"))
		ex = idx;
	    else if (name.equalsIgnoreCase("Date"))
		dt = idx;
	}


	NVPair[] hdrs = req.getHeaders();
	byte[] entity_body = (req.getData() == null ? NUL : req.getData());
	MD5 entity_hash = new MD5();
	entity_hash.Update(entity_body);

	String entity_info = new MD5(req.getRequestURI() + ":" +
	     (ct == -1 ? "" : hdrs[ct].getValue()) + ":" +
	     entity_body.length + ":" +
	     (ce == -1 ? "" : hdrs[ce].getValue()) + ":" +
	     (lm == -1 ? "" : hdrs[lm].getValue()) + ":" +
	     (ex == -1 ? "" : hdrs[ex].getValue())).asHex();
	String entity_digest = A1_hash + ":" + nonce + ":" + req.getMethod() +
			":" + (dt == -1 ? "" : hdrs[dt].getValue()) +
			":" + entity_info + ":" + entity_hash.asHex();

	if (DebugAuth)
	{
	    Util.logLine("Auth:  Entity-Info: '" + req.getRequestURI() + ":" +
		 (ct == -1 ? "" : hdrs[ct].getValue()) + ":" +
		 entity_body.length + ":" +
		 (ce == -1 ? "" : hdrs[ce].getValue()) + ":" +
		 (lm == -1 ? "" : hdrs[lm].getValue()) + ":" +
		 (ex == -1 ? "" : hdrs[ex].getValue()) +"'");
	    Util.logLine("Auth:  Entity-Body: '" + entity_hash.asHex() + "'");
	    Util.logLine("Auth:  Entity-Digest: '" + entity_digest + "'");
	}

	return new MD5(entity_digest).asHex();
    }


    /**
     * Handle discard token
     */
    private static boolean handle_discard(AuthorizationInfo prev, RoRequest req,
					  HttpHeaderElement discard)
    {
	if (discard != null  &&  prev != null)
	{
	    AuthorizationInfo.removeAuthorization(prev,
					    req.getConnection().getContext());
	    return true;
	}

	return false;
    }


    /**
     * Generate <var>num</var> bytes of random data.
     *
     * @param num  the number of bytes to generate
     * @return a byte array of random data
     */
    private static byte[] gen_random_bytes(int num)
    {
	/* This is probably a much better generator, but it can be awfully
	 * slow (~ 6 secs / byte on my old LX)
	 */
	//return new java.security.SecureRandom().getSeed(num);

	/* this is faster, but needs to be done better... */
	byte[] data = new byte[num];
	try
	{
	    long fm = Runtime.getRuntime().freeMemory();
	    data[0] = (byte) (fm & 0xFF);
	    data[1] = (byte) ((fm >>  8) & 0xFF);

	    int h = data.hashCode();
	    data[2] = (byte) (h & 0xFF);
	    data[3] = (byte) ((h >>  8) & 0xFF);
	    data[4] = (byte) ((h >> 16) & 0xFF);
	    data[5] = (byte) ((h >> 24) & 0xFF);

	    long time = System.currentTimeMillis();
	    data[6] = (byte) (time & 0xFF);
	    data[7] = (byte) ((time >>  8) & 0xFF);
	}
	catch (ArrayIndexOutOfBoundsException aioobe)
	    { }

	return data;
    }


    /*
     * Here are all the NTLM specific methods
     */

    private static AuthorizationInfo ntlm_gen_auth_info(String host, int port,
							String realm,
							String user,
							String pass)
    {
	// hash the password
	byte[] lm_hpw = calc_lm_hpw(pass);
	byte[] nt_hpw = calc_ntcr_hpw(pass);

	// get the local host name
	String lhost = null;
	try
	    { lhost = System.getProperty("HTTPClient.defAuthHandler.NTLM.host"); }
	catch (SecurityException se)
	    { }
	if (lhost == null)
	    try
		{ lhost = InetAddress.getLocalHost().getHostName(); }
	    catch (Exception e)
		{ }
	if (lhost == null)
	    lhost = "localhost";	// ???

	int dot = lhost.indexOf('.');
	if (dot != -1)
	    lhost = lhost.substring(0, dot);

	// get user and domain name
	String domain = null;
	int slash;
	if ((slash = user.indexOf('\\')) != -1)
	    domain = user.substring(0, slash);
	else
	{
	    try
	    {
		domain =
		    System.getProperty("HTTPClient.defAuthHandler.NTLM.domain");
	    }
	    catch (SecurityException se)
		{ }
	    if (domain == null)
		domain = lhost;	// ???
	}

	user = user.substring(slash+1);

	// store info in extra_info field
	Object[] info = { user, lhost.toUpperCase().trim(),
			  domain.toUpperCase().trim(), lm_hpw, nt_hpw };

	return new AuthorizationInfo(host, port, "NTLM", realm, null, info);
    }


    /**
     * The fixup handler
     */
    private static AuthorizationInfo ntlm_fixup(AuthorizationInfo info,
						RoRequest req,
						AuthorizationInfo challenge,
						RoResponse resp)
	    throws AuthSchemeNotImplException
    {
	if (challenge == null)
	    return info;	// preemptive stuff - nothing to be done


	// get the various info we store in the extra_info field

	Object[] extra = (Object[]) info.getExtraInfo();
	String user   = (String) extra[0];
	String host   = (String) extra[1];
	String dom    = (String) extra[2];
	byte[] lm_hpw = (byte[]) extra[3];
	byte[] nt_hpw = (byte[]) extra[4];


	// response to the challenges

	byte[] msg;

	if (challenge.getCookie() == null)	// Initial challenge
	{
	    // send type 1 message

	    msg = new byte[32 + host.length() + dom.length()];
	    //"NTLMSSP".getBytes(0, 7, msg, 0);		// NTLMSSP message
	    Util.getBytes("NTLMSSP",msg,0);
            msg[8]  = 1;				// type 1
	    int off = 32, len;

      /** @author  modified by Stefan Lieske, 2005/02/13 */
      /* bytes 12, 13, 14, 15 are flags -> see http://davenport.sourceforge.net/ntlm.html
       * for a detailed description of the NTLM protocol, we support only LMv1 and NTLMv1
       * authentication at the moment and not v2 authentication
       */
      //msg[12] = (byte) 0x03;      // ???
      msg[12] = (byte) 0x07;
	    msg[13] = (byte) 0xb2;

	    len = host.length();
	    msg[24] = (byte) len;			// host length
	    msg[25] = (byte) (len >> 8);
	    msg[26] = (byte) len;			// host length
	    msg[27] = (byte) (len >> 8);
	    msg[28] = (byte) off;			// host offset
	    msg[29] = (byte) (off >> 8);
//	    host.getBytes(0, len, msg, off);		// host
	    Util.getBytes(host,len,msg,off);
            off += len;

	    len = dom.length();
	    msg[16] = (byte) len;			// domain length
	    msg[17] = (byte) (len >> 8);
	    msg[18] = (byte) len;			// domain length
	    msg[19] = (byte) (len >> 8);
	    msg[20] = (byte) off;			// domain offset
	    msg[21] = (byte) (off >> 8);
	    Util.getBytes(dom,len,msg,off);
            //dom.getBytes(0, len, msg, off);		// domain
	    off += len;
	}
	else					// expect type 2 message
	{
	    // decode message

	    String enc_msg = challenge.getCookie();
	    byte tmp[] = enc_msg.getBytes();
	    msg = Codecs.base64Decode(tmp);
      /** @author  modified by Stefan Lieske, 2005/02/13 */
      /* see http://davenport.sourceforge.net/ntlm.html for message format */      
      //if (msg.length != 40  ||  msg[8] != 2  ||  msg[16] != 40)
      //  throw new AuthSchemeNotImplException("NTLM auth scheme: " +
      //          "received invalid message");
      if (msg.length < 32) {
        throw new AuthSchemeNotImplException("NTLM auth scheme: Received invalid type-2 message (too short).");
      }
      /* message seems to be long enough */
      byte[] expectedMessageStart = new byte[12];
      /* message should start with NTLMSSP\0 String */
      System.arraycopy((new String("NTLMSSP")).getBytes(), 0, expectedMessageStart, 0, 7);
      expectedMessageStart[7] = 0;
      /* message type is 0x00000002 but little-endian coded */
      expectedMessageStart[8] = 2;
      expectedMessageStart[9] = 0;
      expectedMessageStart[10] = 0;
      expectedMessageStart[11] = 0;     
      for (int i = 0; i < 12; i++) {
        if (msg[i] != expectedMessageStart[i]) {
          throw new AuthSchemeNotImplException("NTLM auth scheme: Received invalid type-2 message (Byte " + Integer.toString(i) + " is invalid.");
        }
      }
      /* until byte 12 the received message is valid -> bytes 12..19 pointing to a security buffer
       * with the domain name of the server side, we could check this name if we would like to
       */
      /* get the flags, we need them to decide whether security buffers are unicode or OEM encoded
       * and whether NTLM scheme or only LM scheme is supported
       */
      boolean useUnicode = false;
      if ((msg[20] & 0x01) == 0x01) {
        useUnicode = true;
      }
      boolean ntlmSchemeSupported = false;
      if ((msg[21] & 0x02) == 0x02) {
        ntlmSchemeSupported = true;
      }

	    // get nonce

	    byte[] nonce = new byte[8];
	    System.arraycopy(msg, 24, nonce, 0, 8);


	    // create new type 3 message

      /** @author  modified by Stefan Lieske, 2005/02/13 */
      //msg = new byte[64 + 2*dom.length() + 2*user.length() +
      //   2*host.length() + 48];
      //"NTLMSSP".getBytes(0, 7, msg, 0);    // NTLMSSP message
      //msg[8]  = 3;        // type 3
      //int off = 64, len;

      //msg[60] = (byte) 0x01;      // ???
      //msg[61] = (byte) 0x82;

      //len = 2*dom.length();
      //msg[28] = (byte) len;      // domain length
      //msg[29] = (byte) (len >> 8);
      //msg[30] = (byte) len;      // domain length
      //msg[29] = (byte) (len >> 8);
      //msg[32] = (byte) off;      // domain offset
      //msg[33] = (byte) (off >> 8);
      //off = writeUnicode(dom, msg, off);    // domain

      //len = 2*user.length();
      //msg[36] = (byte) len;      // user length
      //msg[37] = (byte) (len >> 8);
      //msg[38] = (byte) len;      // user length
      //msg[39] = (byte) (len >> 8);
      //msg[40] = (byte) off;      // user offset
      //msg[41] = (byte) (off >> 8);
      //off = writeUnicode(user, msg, off);    // user

      //len = 2*host.length();
      //msg[44] = (byte) len;      // host length
      //msg[45] = (byte) (len >> 8);
      //msg[46] = (byte) len;      // host length
      //msg[47] = (byte) (len >> 8);
      //msg[48] = (byte) off;      // host offset
      //msg[49] = (byte) (off >> 8);
      //off = writeUnicode(host, msg, off);    // host

      //msg[12] = 24;        // lm hash length
      //msg[13] = 0;
      //msg[14] = 24;        // lm hash length
      //msg[15] = 0;
      //msg[16] = (byte) (off);      // lm hash offset
      //msg[17] = (byte) (off >> 8);
      //if (send_lm_auth)
        //System.arraycopy(calc_ntcr_resp(lm_hpw, nonce), 0, msg, off, 24);
      //else
        //System.arraycopy(zeros, 0, msg, off, 24);
      //off += 24;

      //msg[20] = 24;        // nt hash length
      //msg[21] = 0;
      //msg[22] = 24;        // nt hash length
      //msg[23] = 0;
      //msg[24] = (byte) (off);      // nt hash offset
      //msg[25] = (byte) (off >> 8);
      //System.arraycopy(calc_ntcr_resp(nt_hpw, nonce), 0, msg, off, 24);
      //off += 24;

      //msg[56] = (byte) (off);      // message length
      //msg[57] = (byte) (off >> 8);    // message length
      
      int domainNameLength = dom.length();
      int userNameLength = user.length();
      int workstationNameLength = host.length();
      if (useUnicode) {
        /* we need 2 bytes per character */
        domainNameLength = 2 * domainNameLength;
        userNameLength = 2 * userNameLength;
        workstationNameLength = 2 * workstationNameLength;
      }
      msg = new byte[64 + domainNameLength + userNameLength + workstationNameLength + 48];
      /* message starts with NTLMSSP\0 */
      System.arraycopy((new String("NTLMSSP")).getBytes(), 0, msg, 0, 7);
      msg[7] = 0;
      /* this is a type-3 NTLM message (little-endian encoded) */
      msg[8] = 3;
      msg[9] = 0;
      msg[10] = 0;
      msg[11] = 0;
      /* pointer to 24 byte LM response (2 byte data length, 2 byte buffer size, 4 byte offset
       * within this message, all values litte-endian) 
       */
      msg[12] = 24;
	    msg[13] = 0;
      msg[14] = 24;
	    msg[15] = 0;
      int lmOffset = msg.length - 48;
      msg[16] = (byte)(lmOffset);
      msg[17] = (byte)(lmOffset >> 8);
      msg[18] = (byte)(lmOffset >> 16);
      msg[19] = (byte)(lmOffset >> 24);
      /* pointer to 24 byte NTLM response */
      msg[20] = 24;
	    msg[21] = 0;
      msg[22] = 24;
	    msg[23] = 0;
      int ntlmOffset = msg.length - 24;
      msg[24] = (byte)(ntlmOffset);
      msg[25] = (byte)(ntlmOffset >> 8);
      msg[26] = (byte)(ntlmOffset >> 16);
      msg[27] = (byte)(ntlmOffset >> 24);
      /* pointer to domain name */
      msg[28] = (byte)(domainNameLength);
      msg[29] = (byte)(domainNameLength >> 8);
      msg[30] = (byte)(domainNameLength);
      msg[31] = (byte)(domainNameLength >> 8);
      msg[32] = 64;
      msg[33] = 0;
      msg[34] = 0;
      msg[35] = 0;
      /* pointer to user name */
      msg[36] = (byte)(userNameLength);
      msg[37] = (byte)(userNameLength >> 8);
      msg[38] = (byte)(userNameLength);
      msg[39] = (byte)(userNameLength >> 8);
      int userOffset = 64 + domainNameLength;
      msg[40] = (byte)(userOffset);
      msg[41] = (byte)(userOffset >> 8);
      msg[42] = (byte)(userOffset >> 16);
      msg[43] = (byte)(userOffset >> 24);
      /* pointer to workstation name */
      msg[44] = (byte)(workstationNameLength);
      msg[45] = (byte)(workstationNameLength >> 8);
      msg[46] = (byte)(workstationNameLength);
      msg[47] = (byte)(workstationNameLength >> 8);
      int workstationOffset = 64 + domainNameLength + userNameLength;
      msg[48] = (byte)(workstationOffset);
      msg[49] = (byte)(workstationOffset >> 8);
      msg[50] = (byte)(workstationOffset >> 16);
      msg[51] = (byte)(workstationOffset >> 24);
      /* pointer to session key, let it point to an empty buffer at the end of the message */
      msg[52] = 0;
      msg[53] = 0;
      msg[54] = 0;
      msg[55] = 0;
      msg[56] = (byte)(msg.length);
      msg[57] = (byte)(msg.length >> 8);
      msg[58] = (byte)(msg.length >> 16);
      msg[59] = (byte)(msg.length >> 24);
      /* flags should not have any effect */
      if (useUnicode) {
        msg[60] = 0x01;
      }
      else {
        msg[60] = 0x02;
      }
      if (ntlmSchemeSupported) {
        msg[61] = 0x02;
      }
      else {
        msg[61] = 0;
      }
      msg[62] = 0;
      msg[63] = 0;
      if (useUnicode) {
        /* add the domain name */
        writeUnicode(dom, msg, 64);
        /* add the user name */
        writeUnicode(user, msg, userOffset);
        /* add the workstation name */
        writeUnicode(host, msg, workstationOffset);
      }
      else {
        /* add the domain name */
        System.arraycopy(dom.getBytes(), 0, msg, 64, domainNameLength);
        /* add the user name */
        System.arraycopy(user.getBytes(), 0, msg, userOffset, userNameLength);
        /* add the workstation name */
        System.arraycopy(host.getBytes(), 0, msg, workstationOffset, workstationNameLength);
      }      
      /* write always also the LM response, normally this shouldn't be necessary if NTLM is
       * supported, but there are some issues with broken servers -> but keep in mind that
       * using LM auth will also weaken the security of the whole authentication
       */
      System.arraycopy(calc_ntcr_resp(lm_hpw, nonce), 0, msg, lmOffset, 24);
      /* write always also the NTLM response */
      System.arraycopy(calc_ntcr_resp(nt_hpw, nonce), 0, msg, ntlmOffset, 24);    
	}

	// return new AuthorizationInfo

	String cookie = new String(Codecs.base64Encode(msg));

	AuthorizationInfo cred = new AuthorizationInfo(challenge.getHost(),
						       challenge.getPort(),
						       challenge.getScheme(),
						       challenge.getRealm(),
						       cookie);
	cred.setExtraInfo(extra);
	info.setCookie(cookie);

	return cred;
    }


    /**
     * @return the info for step 2 if appropriate; null otherwise
     */
    private static AuthorizationInfo ntlm_check_step2(
					      AuthorizationInfo challenge,
					      RoRequest req, RoResponse resp)
		    throws AuthSchemeNotImplException
    {
	String auth = Util.getValue(req.getHeaders(), "Authorization");
	AuthorizationInfo cred =
	    AuthorizationInfo.getAuthorization(challenge, req, resp, false, false);

	/* If we received a cookie (i.e. type 2 message) in the challenge
	 * and our previously sent data was a type 1 message, then this must
	 * be step 2 in the handshake.
	 */
	if (challenge.getCookie() != null  &&  cred != null  &&
	    auth != null  &&  auth.startsWith("NTLM TlRMTVNTUAAB"))
	    return ntlm_fixup(cred, req, challenge, null);

	return null;
    }


    /**
     * Write unicode string to buffer in little-endian format.
     */
    private static int writeUnicode(String str, byte[] buf, int off)
    {
	int len = str.length();
	for (int idx=0; idx<len; idx++)
	{
	    int c = str.charAt(idx);
	    buf[off++] = (byte) c;
	    buf[off++] = (byte) (c >> 8);
	}

	return off;
    }


    /**
     * Calculates the NTCR hashed unicode password. See
     * ftp://samba.anu.edu.au/pub/samba/docs/ENCRYPTION.txt more info.
     *
     * @param passw the password
     * @return the hashed password with 5 zeros appended
     */
    private static byte[] calc_ntcr_hpw(String passw)
    {
	// put password into an array of bytes, writing the unicode chars
	// in little endian order

	byte[] uc = new byte[passw.length() * 2];
	for (int idx=0, dst=0; idx<passw.length(); idx++)
	{
	    char ch = passw.charAt(idx);
	    uc[dst++] = (byte) (ch & 0xFF);
	    uc[dst++] = (byte) (ch >>> 8);
	}


	// calc MD4 hash of password (as unicode array)

	byte[] hash = new MD4(uc).getHash();
	return Util.resizeArray(hash, 21);
    }


    /**
     * Calculates the LanManger hashed password. See
     * ftp://samba.anu.edu.au/pub/samba/docs/ENCRYPTION.txt more info.
     *
     * @param passw the password
     * @return the hashed password
     */
    private static byte[] calc_lm_hpw(String passw)
    {
	// uppercase the password
	passw = passw.toUpperCase();

	// store in byte array, truncating or extending to 14 bytes
	byte[] keys = new byte[14];
	//passw.getBytes(0, Math.min(passw.length(), 14), keys, 0);
        Util.getBytes(passw,Math.min(passw.length(), 14),keys,0);
	// DES encode the magic value with the above generated keys
	byte[] resp  = new byte[21],
	       /* the following must decrypted with an all-zeroes key
	       magic = { (byte) 0xAA, (byte) 0xD3, (byte) 0xB4, (byte) 0x35,
			 (byte) 0xB5, (byte) 0x14, (byte) 0x04, (byte) 0xEE},
		* to yield the real magic text:
	        */
	       magic = { (byte) 0x4B, (byte) 0x47, (byte) 0x53, (byte) 0x21,
			 (byte) 0x40, (byte) 0x23, (byte) 0x24, (byte) 0x25},
	       crypt = new byte[8];

	int[] ks = setup_key(keys, 0);
	DES.des_ecb_encrypt(magic, crypt, ks, true);
	System.arraycopy(crypt, 0, resp, 0, 8);

	ks = setup_key(keys, 7);
	DES.des_ecb_encrypt(magic, crypt, ks, true);
	System.arraycopy(crypt, 0, resp, 8, 8);

	// done
	return resp;
    }


    /**
     * Calculates the NTLM response. See
     * ftp://samba.anu.edu.au/pub/samba/docs/ENCRYPTION.txt more info.
     *
     * @param hpw   the hashed password
     * @param nonce the nonce from the server
     * @return the response String
     */
    private static byte[] calc_ntcr_resp(byte[] hpw, byte[] nonce)
    {
	// do the DES encryptions

	byte[] resp  = new byte[24],
	       crypt = new byte[8];

	int[] ks = setup_key(hpw, 0);
	DES.des_ecb_encrypt(nonce, crypt, ks, true);
	System.arraycopy(crypt, 0, resp, 0, 8);

	ks = setup_key(hpw, 7);
	DES.des_ecb_encrypt(nonce, crypt, ks, true);
	System.arraycopy(crypt, 0, resp, 8, 8);

	ks = setup_key(hpw, 14);
	DES.des_ecb_encrypt(nonce, crypt, ks, true);
	System.arraycopy(crypt, 0, resp, 16, 8);


	// done

	return resp;
    }


    private static int[] setup_key(byte[] k_56, int off)
    {
	// set DES key

	byte[] key = new byte[8];
	int[]  ks  = new int[32];

	key[0] = (byte) k_56[off];
	key[1] = (byte) ((k_56[off+0] << 7) | ((k_56[off+1] & 0xFF) >> 1));
	key[2] = (byte) ((k_56[off+1] << 6) | ((k_56[off+2] & 0xFF) >> 2));
	key[3] = (byte) ((k_56[off+2] << 5) | ((k_56[off+3] & 0xFF) >> 3));
	key[4] = (byte) ((k_56[off+3] << 4) | ((k_56[off+4] & 0xFF) >> 4));
	key[5] = (byte) ((k_56[off+4] << 3) | ((k_56[off+5] & 0xFF) >> 5));
	key[6] = (byte) ((k_56[off+5] << 2) | ((k_56[off+6] & 0xFF) >> 6));
	key[7] = (byte) (k_56[off+6] << 1);

	DES.des_set_odd_parity(key);
	DES.des_set_key(key, ks);

	return ks;
    }


    /**
     * Set a new username/password prompter.
     *
     * @param prompt the AuthorizationPrompter to use whenever a username
     *               and password are needed.
     * @return the previous prompter
     * @see AuthorizationPrompter
     */
    public static AuthorizationPrompter setAuthorizationPrompter(
					    AuthorizationPrompter prompt)
    {
	AuthorizationPrompter prev = prompter;
	prompter = prompt;
	return prev;
    }

    private static final byte[] unHex(String hex)
    {
	byte[] digest = new byte[hex.length()/2];

	for (int idx=0; idx<digest.length; idx++)
	{
	    digest[idx] = (byte) (0xFF & Integer.parseInt(
				  hex.substring(2*idx, 2*(idx+1)), 16));
	}

	return digest;
    }


    /**
     * Produce a string of the form "A5:22:F1:0B:53"
     */
    private static String hex(byte[] buf)
    {
	StringBuffer str = new StringBuffer(buf.length*3);
	for (int idx=0; idx<buf.length; idx++)
	{
	    str.append(Character.forDigit((buf[idx] >>> 4) & 15, 16));
	    str.append(Character.forDigit(buf[idx] & 15, 16));
	    str.append(':');
	}
	str.setLength(str.length()-1);

	return str.toString();
    }


    /**
     * This verifies the "rspauth" from draft-ietf-http-authentication-03
     */
    private static class VerifyRspAuth implements HashVerifier, GlobalConstants
    {
	private String     uri;
	private String     HA1;
	private String     alg;
	private String     nonce;
	private String     cnonce;
	private String     nc;
	private String     hdr;
	private RoResponse resp;


	public VerifyRspAuth(String uri, String HA1, String alg, String nonce,
			     String cnonce, String nc, String hdr,
			     RoResponse resp)
	{
	    this.uri    = uri;
	    this.HA1    = HA1;
	    this.alg    = alg;
	    this.nonce  = nonce;
	    this.cnonce = cnonce;
	    this.nc     = nc;
	    this.hdr    = hdr;
	    this.resp   = resp;
	}


	public void verifyHash(byte[] hash, long len)  throws IOException
	{
	    String auth_info = resp.getHeader(hdr);
	    if (auth_info == null)
		auth_info = resp.getTrailer(hdr);
	    if (auth_info == null)
		return;

	    Vector pai;
	    try
		{ pai = Util.parseHeader(auth_info); }
	    catch (ParseException pe)
		{ throw new IOException(pe.toString()); }

	    String qop;
	    HttpHeaderElement elem = Util.getElement(pai, "qop");
	    if (elem == null  ||  (qop = elem.getValue()) == null  ||
		(!qop.equalsIgnoreCase("auth")  &&
		 !qop.equalsIgnoreCase("auth-int")))
		return;

	    elem = Util.getElement(pai, "rspauth");
	    if (elem == null  ||  elem.getValue() == null) return;
	    byte[] digest = unHex(elem.getValue());

	    elem = Util.getElement(pai, "cnonce");
	    if (elem != null  &&  elem.getValue() != null  &&
		!elem.getValue().equals(cnonce))
		throw new IOException("Digest auth scheme: received wrong " +
				      "client-nonce '" + elem.getValue() +
				      "' - expected '" + cnonce + "'");

	    elem = Util.getElement(pai, "nc");
	    if (elem != null  &&  elem.getValue() != null  &&
		!elem.getValue().equals(nc))
		throw new IOException("Digest auth scheme: received wrong " +
				      "nonce-count '" + elem.getValue() +
				      "' - expected '" + nc + "'");

	    String A1, A2;
	    if (alg != null  &&  alg.equalsIgnoreCase("MD5-sess"))
		A1 = new MD5(HA1 + ":" + nonce + ":" + cnonce).asHex();
	    else
		A1 = HA1;

	    // draft-01 was: A2 = resp.getStatusCode() + ":" + uri;
	    A2 = ":" + uri;
	    if (qop.equalsIgnoreCase("auth-int"))
		A2 += ":" + MD5.asHex(hash);
	    A2 = new MD5(A2).asHex();

	    hash = new MD5(A1 + ":" + nonce + ":" +  nc + ":" + cnonce + ":" +
			   qop + ":" + A2).Final();

	    for (int idx=0; idx<hash.length; idx++)
	    {
		if (hash[idx] != digest[idx])
		    throw new IOException("MD5-Digest mismatch: expected " +
					  hex(digest) + " but calculated " +
					  hex(hash));
	    }

	    if (DebugAuth)
		Util.logLine("Auth:  rspauth from " + hdr +
			     " successfully verified");
	}
    }


    /**
     * This verifies the "digest" from rfc-2069
     */
    private static class VerifyDigest implements HashVerifier, GlobalConstants
    {
	private String     HA1;
	private String     nonce;
	private String     method;
	private String     uri;
	private String     hdr;
	private RoResponse resp;


	public VerifyDigest(String HA1, String nonce, String method, String uri,
			    String hdr, RoResponse resp)
	{
	    this.HA1    = HA1;
	    this.nonce  = nonce;
	    this.method = method;
	    this.uri    = uri;
	    this.hdr    = hdr;
	    this.resp   = resp;
	}


	public void verifyHash(byte[] hash, long len)  throws IOException
	{
	    String auth_info = resp.getHeader(hdr);
	    if (auth_info == null)
		auth_info = resp.getTrailer(hdr);
	    if (auth_info == null)
		return;

	    Vector pai;
	    try
		{ pai = Util.parseHeader(auth_info); }
	    catch (ParseException pe)
		{ throw new IOException(pe.toString()); }
	    HttpHeaderElement elem = Util.getElement(pai, "digest");
	    if (elem == null  ||  elem.getValue() == null)
		return;

	    byte[] digest = unHex(elem.getValue());

	    String entity_info = new MD5(
				    uri + ":" +
				    header_val("Content-type", resp) + ":" +
				    header_val("Content-length", resp) + ":" +
				    header_val("Content-Encoding", resp) + ":" +
				    header_val("Last-Modified", resp) + ":" +
				    header_val("Expires", resp)).asHex();
	    hash = new MD5(HA1 + ":" + nonce + ":" + method + ":" +
			   header_val("Date", resp) +
			   ":" + entity_info + ":" + MD5.asHex(hash)).Final();

	    for (int idx=0; idx<hash.length; idx++)
	    {
		if (hash[idx] != digest[idx])
		    throw new IOException("MD5-Digest mismatch: expected " +
					  hex(digest) + " but calculated " +
					  hex(hash));
	    }

	    if (DebugAuth)
		Util.logLine("Auth:  digest from " + hdr +
			     "successfully verified");
	}


	private final String header_val(String hdr_name, RoResponse resp)
		throws IOException
	{
	    String hdr = resp.getHeader(hdr_name);
	    String tlr = resp.getTrailer(hdr_name);
	    return (hdr != null ? hdr : (tlr != null ? tlr : ""));
	}
    }
}



