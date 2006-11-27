/*
 * @(#)URI.java						0.4 02/02/1998
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


import java.net.URL;
import java.net.MalformedURLException;
import java.util.BitSet;

/**
 * This class represents a generic URI, as defined in
 * draft-fielding-uri-syntax-03 . This is similar to java.net.URL,
 * with the following enhancements:
 * <UL>
 * <LI>it doesn't require a URLStreamhandler to exist for the scheme; this
 *     allows this class to be used to hold any URI, construct absolute
 *     URIs from relative ones, etc.
 * <LI>it handles escapes correctly
 * <LI>equals() works correctly
 * <LI>relative URIs are correctly constructed
 * <LI>it has methods for accessing various fields such as userinfo,
 *     fragment, params, etc.
 * <LI>it handles less common forms of resources such as the "*" used in
 *     http URLs.
 * </UL>
 *
 * <P>Ideally, java.net.URL should subclass URI.
 *
 * @see		<A HREF="ftp://ds.internic.net/internet-drafts/draft-fielding-uri-syntax-03.txt">draft-fielding-uri-syntax-03</A>
 * @version	0.4  17/05/1998
 * @author	Ronald Tschal&auml;r
 * @since	V0.4
 */

public class URI
{
    /* various character classes as defined in the draft */
    protected static BitSet alphanumChar;
    protected static BitSet markChar;
    protected static BitSet reservedChar;
    protected static BitSet unreservedChar;
    protected static BitSet uricChar;
    protected static BitSet pcharChar;
    protected static BitSet userinfoChar;
    protected static BitSet schemeChar;
    protected static BitSet reg_nameChar;

    static
    {
	alphanumChar = new BitSet(128);
	for (int ch='0'; ch<='9'; ch++)  alphanumChar.set(ch);
	for (int ch='A'; ch<='Z'; ch++)  alphanumChar.set(ch);
	for (int ch='a'; ch<='z'; ch++)  alphanumChar.set(ch);

	markChar = new BitSet(128);
	markChar.set('-');
	markChar.set('_');
	markChar.set('.');
	markChar.set('!');
	markChar.set('~');
	markChar.set('*');
	markChar.set('\'');
	markChar.set('(');
	markChar.set(')');

	reservedChar = new BitSet(128);
	reservedChar.set(';');
	reservedChar.set('/');
	reservedChar.set('?');
	reservedChar.set(':');
	reservedChar.set('@');
	reservedChar.set('&');
	reservedChar.set('=');
	reservedChar.set('+');
	reservedChar.set('$');
	reservedChar.set(',');

	unreservedChar = new BitSet(128);
	unreservedChar.or(alphanumChar);
	unreservedChar.or(markChar);

	uricChar = new BitSet(128);
	uricChar.or(unreservedChar);
	uricChar.or(reservedChar);

	pcharChar = new BitSet(128);
	pcharChar.or(unreservedChar);
	pcharChar.set(':');
	pcharChar.set('@');
	pcharChar.set('&');
	pcharChar.set('=');
	pcharChar.set('+');
	pcharChar.set('$');
	pcharChar.set(',');

	userinfoChar = new BitSet(128);
	userinfoChar.or(unreservedChar);
	userinfoChar.set(';');
	userinfoChar.set(':');
	userinfoChar.set('&');
	userinfoChar.set('=');
	userinfoChar.set('+');
	userinfoChar.set('$');
	userinfoChar.set(',');

	schemeChar = new BitSet(128);
	schemeChar.or(alphanumChar);
	schemeChar.set('+');
	schemeChar.set('-');
	schemeChar.set('.');

	reg_nameChar = new BitSet(128);
	reg_nameChar.or(unreservedChar);
	reg_nameChar.set('$');
	reg_nameChar.set(',');
	reg_nameChar.set(';');
	reg_nameChar.set(':');
	reg_nameChar.set('@');
	reg_nameChar.set('&');
	reg_nameChar.set('=');
	reg_nameChar.set('+');
    }


    /* our uri in pieces */

    protected boolean is_generic;
    protected String  scheme;
    protected String  opaque;
    protected String  userinfo;
    protected String  host;
    protected int     port = -1;
    protected String  path;
    protected String  query;
    protected String  fragment;


    /* cache the java.net.URL */

    protected URL     url = null;


    // Constructors

    /**
     * Constructs a URI from the given string representation. The string
     * must be an absolute URI.
     */
    public URI(String uri)  throws ParseException
    {
	this((URI) null, uri);
    }


    /**
     * Constructs a URI from the given string representation, relative to
     * the given base URI.
     */
    public URI(URI base, String rel_uri)  throws ParseException
    {
	/* Parsing is done according to the following RE:
	 *
	 *  ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
	 *   12            3  4          5       6  7        8 9
	 *
	 * 2: scheme
	 * 4: authority
	 * 5: path
	 * 7: query
	 * 9: fragment
	 */

	char[] uri = rel_uri.toCharArray();
	int pos = 0, idx, len = uri.length;


	// trim()

	while (pos < len  &&  Character.isSpace(uri[pos]))    pos++;
	while (len > 0    &&  Character.isSpace(uri[len-1]))  len--;


	// strip the special "url" or "uri" scheme

	if (pos < len-3  &&  uri[pos+3] == ':'  &&
	    (uri[pos+0] == 'u'  ||  uri[pos+0] == 'U')  &&
	    (uri[pos+1] == 'r'  ||  uri[pos+1] == 'R')  &&
	    (uri[pos+2] == 'i'  ||  uri[pos+2] == 'I'  ||
	     uri[pos+2] == 'l'  ||  uri[pos+2] == 'L'))
	    pos += 4;


	// get scheme: (([^:/?#]+):)?

	idx = pos;
	while (idx < len  &&  uri[idx] != ':'  &&  uri[idx] != '/'  &&
	       uri[idx] != '?'  &&  uri[idx] != '#')
	    idx++;
	if (idx < len  &&  uri[idx] == ':')
	{
	    scheme = rel_uri.substring(pos, idx).trim().toLowerCase();
	    pos = idx + 1;
	}


	// check and resolve scheme

	String final_scheme = scheme;
	if (scheme == null)
	{
	    if (base == null)
		throw new ParseException("No scheme found");
	    final_scheme = base.scheme;
	}


	// check for generic vs. opaque

	is_generic = usesGenericSyntax(final_scheme);
	if (!is_generic)
	{
	    if (base != null  &&  scheme == null)
		throw new ParseException("Can't resolve relative URI for " +
					 "scheme " + final_scheme);

	    opaque = rel_uri.substring(pos);
	    return;
	}


	// get authority: (//([^/?#]*))?

	if (pos < len-1  &&  uri[pos] == '/'  &&  uri[pos+1] == '/')
	{
	    pos += 2;
	    idx = pos;
	    while (idx < len  &&  uri[idx] != '/'  &&  uri[idx] != '?'  &&
		   uri[idx] != '#')
		idx++;

	    parse_authority(rel_uri.substring(pos, idx), final_scheme);
	    pos = idx;
	}


	// get path: ([^?#]*)

	idx = pos;
	while (idx < len  &&  uri[idx] != '?'  &&  uri[idx] != '#')
	    idx++;
	this.path = rel_uri.substring(pos, idx);
	pos = idx;


	// get query: (\?([^#]*))?

	if (pos < len  &&  uri[pos] == '?')
	{
	    pos += 1;
	    idx = pos;
	    while (idx < len  &&  uri[idx] != '#')
		idx++;
	    this.query = unescape(rel_uri.substring(pos, idx));
	    pos = idx;
	}


	// get fragment: (#(.*))?

	if (pos < len  &&  uri[pos] == '#')
	    this.fragment = unescape(rel_uri.substring(pos+1, len));


	// now resolve the parts relative to the base

	if (base != null)
	{
	    if (scheme != null)  return;		// resolve scheme
	    scheme = base.scheme;

	    if (host != null)  return;			// resolve authority
	    userinfo = base.userinfo;
	    host     = base.host;
	    port     = base.port;

	    if (path.length() == 0  &&  query == null)	// current doc
	    {
		path  = base.path;
		query = base.query;
		return;
	    }

	    if (path.length() == 0  ||  path.charAt(0) != '/')	// relative uri
	    {
		idx = base.path.lastIndexOf('/');
		if (idx == -1)  return;		// weird one
		path = base.path.substring(0, idx+1) + path;

		len = path.length();
		if (!((idx = path.indexOf("/.")) != -1  &&
		      (idx == len-2  ||  path.charAt(idx+2) == '/'  ||
		       (path.charAt(idx+2) == '.'  &&
			(idx == len-3  ||  path.charAt(idx+3) == '/')) )))
		    return;

		char[] p = new char[path.length()];		// clean path
		path.getChars(0, p.length, p, 0);

		for (idx=1; idx<len; idx++)
		{
		    if (p[idx] == '.'  &&  p[idx-1] == '/')
		    {
			int end;
			if (idx == len-1)		// trailing "/."
			{
			    end  = idx;
			    idx += 1;
			}
			else if (p[idx+1] == '/')	// "/./"
			{
			    end  = idx - 1;
			    idx += 1;
			}
			else if (p[idx+1] == '.'  &&
				 (idx == len-2  ||  p[idx+2] == '/')) // "/../"
			{
			    end  = idx - 2;
			    while (end > 0  &&  p[end] != '/')  end--;
			    if (p[end] != '/')  continue;
			    if (idx == len-2) end++;
			    idx += 2;
			}
			else
			    continue;
			System.arraycopy(p, idx, p, end, len-idx);
			len -= idx - end;
			idx = end;
		    }
		}
		path = new String(p, 0, len);
	    }
	}
    }


    /**
     * Parse the authority specific part
     */
    private void parse_authority(String authority, String scheme)
	    throws ParseException
    {
	/* The authority is further parsed according to:
	 *
	 *  ^(([^@]*)@?)([^:]*)?(:(.*))?
	 *   12         3       4 5
	 *
	 * 2: userinfo
	 * 3: host
	 * 5: port
	 */

	char[] uri = authority.toCharArray();
	int pos = 0, idx, len = uri.length;


	// get userinfo: (([^@]*)@?)

	idx = pos;
	while (idx < len  &&  uri[idx] != '@')
	    idx++;
	if (idx < len  &&  uri[idx] == '@')
	{
	    this.userinfo = unescape(authority.substring(pos, idx));
	    pos = idx + 1;
	}


	// get host: ([^:]*)?

	idx = pos;
	while (idx < len  &&  uri[idx] != ':')
	    idx++;
	this.host = authority.substring(pos, idx);
	pos = idx;


	// get port: (:(.*))?

	if (pos < (len-1)  &&  uri[pos] == ':')
	{
	    int p;
	    try
	    {
		p = Integer.parseInt(authority.substring(pos+1, len));
		if (p < 0)  throw new NumberFormatException();
	    }
	    catch (NumberFormatException e)
	    {
		throw new ParseException(authority.substring(pos+1, len) +
					 " is an invalid port number");
	    }
	    if (p == defaultPort(scheme))
		this.port = -1;
	    else
		this.port = p;
	}
    }


    /**
     * Constructs a URI from the given parts, using the default port for
     * this scheme (if known).
     */
    public URI(String scheme, String host, String path)  throws ParseException
    {
	this(scheme, null, host, -1, path, null, null);
    }


    /**
     * Constructs a URI from the given parts.
     */
    public URI(String scheme, String host, int port, String path)
	    throws ParseException
    {
	this(scheme, null, host, port, path, null, null);
    }


    /**
     * Constructs a URI from the given parts.
     */
    public URI(String scheme, String userinfo, String host, int port,
	       String path, String query, String fragment)
	    throws ParseException
    {
	if (scheme == null)
	    throw new ParseException("missing scheme");
	this.scheme = scheme.trim();
	if (userinfo != null)        this.userinfo = unescape(userinfo.trim());
	if (host != null)                this.host     = host.trim();
	if (port != defaultPort(scheme)) this.port     = port;
	if (path != null)                this.path     = path.trim();	// ???
	if (query != null)               this.query    = query.trim();
	if (fragment != null)            this.fragment = fragment.trim();

	this.is_generic = true;
    }


    /**
     * Constructs an opaque URI from the given parts.
     */
    public URI(String scheme, String opaque)
	    throws ParseException
    {
	if (scheme == null)
	    throw new ParseException("missing scheme");
	this.scheme = scheme.trim().toLowerCase();
	this.opaque = opaque;

	this.is_generic = false;
    }


    // Class Methods

    /**
     * @return true if the scheme should be parsed according to the
     *         generic-URI syntax
     */
    public static boolean usesGenericSyntax(String scheme)
    {
	scheme = scheme.trim();

	if (scheme.equalsIgnoreCase("http")    ||
	    scheme.equalsIgnoreCase("https")   ||
	    scheme.equalsIgnoreCase("shttp")   ||
	    scheme.equalsIgnoreCase("coffee")  ||
	    scheme.equalsIgnoreCase("ftp")     ||
	    scheme.equalsIgnoreCase("file")    ||
	    scheme.equalsIgnoreCase("gopher")  ||
	    scheme.equalsIgnoreCase("nntp")    ||
	    scheme.equalsIgnoreCase("telnet")  ||
	    scheme.equalsIgnoreCase("imap")    ||
	    scheme.equalsIgnoreCase("wais")    ||
	    scheme.equalsIgnoreCase("nfs")     ||
	    scheme.equalsIgnoreCase("ldap")    ||
	    scheme.equalsIgnoreCase("prospero"))
		return true;

	/* Schemes which definitely don't use the generic-URI syntax and
	 * must therefore never appear in the above list:
	 * "mailto", "news", "urn"
	 */
	return false;
    }


    /**
     * Return the default port used by a given protocol.
     *
     * @param protocol the protocol
     * @return the port number, or 0 if unknown
     */
    public final static int defaultPort(String protocol)
    {
	String prot = protocol.trim();

	if (prot.equalsIgnoreCase("http")  ||
	    prot.equalsIgnoreCase("shttp")  ||
	    prot.equalsIgnoreCase("http-ng")  ||
	    prot.equalsIgnoreCase("coffee"))
	    return 80;
	else if (prot.equalsIgnoreCase("https"))
	    return 443;
	else if (prot.equalsIgnoreCase("ftp"))
	    return 21;
	else if (prot.equalsIgnoreCase("telnet"))
	    return 23;
	else if (prot.equalsIgnoreCase("nntp"))
	    return 119;
	else if (prot.equalsIgnoreCase("smtp"))
	    return 25;
	else if (prot.equalsIgnoreCase("gopher"))
	    return 70;
	else if (prot.equalsIgnoreCase("wais"))
	    return 210;
	else if (prot.equalsIgnoreCase("whois"))
	    return 43;
	else if (prot.equalsIgnoreCase("imap"))
	    return 143;
	else if (prot.equalsIgnoreCase("prospero"))
	    return 1525;
	else if (prot.equalsIgnoreCase("ldap"))
	    return 389;
	else if (prot.equalsIgnoreCase("nfs"))
	    return 2049;
	else
	    return 0;
    }


    // Instance Methods

    /**
     * @return the scheme (often also referred to as protocol)
     */
    public String getScheme()
    {
	return scheme;
    }


    /**
     * @return the opaque part, or null if this URI is generic
     */
    public String getOpaque()
    {
	return opaque;
    }


    /**
     * @return the host
     */
    public String getHost()
    {
	return host;
    }


    /**
     * @return the port
     */
    public int getPort()
    {
	return port;
    }


    /**
     * @return the user info
     */
    public String getUserinfo()
    {
	return userinfo;
    }


    /**
     * @return the path; this includes the query string
     */
    public String getPath()
    {
	if (query != null)
	    if (path != null)
		return path + "?" + query;
	    else
		return "?" + query;
	return path;
    }


    /**
     * @return the query string
     */
    public String getQueryString()
    {
	return query;
    }


    /**
     * @return the fragment
     */
    public String getFragment()
    {
	return fragment;
    }


    /**
     * Does the scheme specific part of this URI use the generic-URI syntax?
     *
     * <P>In general URI are split into two categories: opaque-URI and
     * generic-URI. The generic-URI syntax is the syntax most are familiar
     * with from URLs such as ftp- and http-URLs, which is roughly:
     * <PRE>
     * generic-URI = scheme ":" [ "//" server ] [ "/" ] [ path_segments ] [ "?" query ]
     * </PRE>
     * (see draft-fielding-uri-syntax-03 for exact syntax). Only URLs
     * using the generic-URI syntax can be used to create and resolve
     * relative URIs.
     *
     * <P>Whether a given scheme is parsed according to the generic-URI
     * syntax or wether it is treated as opaque is determined by an internal
     * table of URI schemes.
     *
     * @see <A HREF="ftp://ds.internic.net/internet-drafts/draft-fielding-uri-syntax-03.txt">draft-fielding-uri-syntax-03</A>
     */
    public boolean isGenericURI()
    {
	return is_generic;
    }


    /**
     * Will try to create a java.net.URL object from this URI.
     *
     * @return the URL
     * @exception MalFormedURLException if no handler is available for the
     *            scheme
     */
    public URL toURL()  throws MalformedURLException
    {
	if (url != null)  return url;

	if (opaque != null)
	    return (url = new URL(scheme + ":" + opaque));

	StringBuffer file = new StringBuffer(100);

	if (path != null)
	    file.append(escape(path.toCharArray(), uricChar));

	if (query != null)
	{
	    file.append('?');
	    file.append(escape(query.toCharArray(), uricChar));
	}

	if (fragment != null)
	{
	    file.append('#');
	    file.append(escape(fragment.toCharArray(), uricChar));
	}

	url = new URL(scheme, host, port, file.toString());
	return url;
    }


    /**
     * @return a string representation of this URI suitable for use in
     *         links, headers, etc.
     */
    public String toExternalForm()
    {
	StringBuffer uri = new StringBuffer(100);

	if (scheme != null)
	{
	    uri.append(scheme);
	    uri.append(':');
	}

	if (opaque != null)		// it's an opaque-uri
	{
	    uri.append(escape(opaque.toCharArray(), uricChar));
	    return uri.toString();
	}

	if (userinfo != null  ||  host != null  ||  port != -1)
	    uri.append("//");

	if (userinfo != null)
	{
	    uri.append(escape(userinfo.toCharArray(), userinfoChar));
	    uri.append('@');
	}

	if (host != null)
	    uri.append(host.toCharArray());

	if (port != -1)
	{
	    uri.append(':');
	    uri.append(port);
	}

	if (path != null)
	    uri.append(path.toCharArray());

	if (query != null)
	{
	    uri.append('?');
	    uri.append(escape(query.toCharArray(), uricChar));
	}

	if (fragment != null)
	{
	    uri.append('#');
	    uri.append(escape(fragment.toCharArray(), uricChar));
	}

	return uri.toString();
    }


    /**
     * @see #toExternalForm
     */
    public String toString()
    {
	return toExternalForm();
    }


    /**
     * @return true if <var>other</var> is either a URI or URL and it
     *         matches the current URI
     */
    public boolean equals(Object other)
    {
	if (other instanceof URI)
	{
	    URI o = (URI) other;
	    return (scheme.equalsIgnoreCase(o.scheme)  &&
		    (
		     !is_generic  &&
		     (opaque == null  &&  o.opaque == null  ||
		      opaque != null  &&  o.opaque != null  &&
		      opaque.equals(o.opaque))  ||

		     is_generic  &&
		     (userinfo == null  &&  o.userinfo == null  ||
		      userinfo != null  &&  o.userinfo != null  &&
		      userinfo.equals(o.userinfo))  &&
		     (host == null  &&  o.host == null  ||
		      host != null  &&  o.host != null  &&
		      host.equalsIgnoreCase(o.host))  &&
		     port == o.port  &&
		     (path == null  &&  o.path == null  ||
		      path != null  &&  o.path != null  &&
		      unescapeNoPE(path).equals(unescapeNoPE(o.path)))  &&
		     (query == null  &&  o.query == null  ||
		      query != null  &&  o.query != null  &&
		      unescapeNoPE(query).equals(unescapeNoPE(o.query)))  &&
		     (fragment == null  &&  o.fragment == null  ||
		      fragment != null  &&  o.fragment != null  &&
		      unescapeNoPE(fragment).equals(unescapeNoPE(o.fragment)))
		    ));
	}

	if (other instanceof URL)
	{
	    URL o = (URL) other;
	    String h, f;

	    if (userinfo != null)
		h = userinfo + "@" + host;
	    else
		h = host;

	    if (query != null)
		f = path + "?" + query;
	    else
		f = path;

	    return (scheme.equalsIgnoreCase(o.getProtocol())  &&
		    (!is_generic  &&  opaque.equals(o.getFile())  ||
		     is_generic  &&
		     (h == null  &&  o.getHost() == null  ||
		      h != null  &&  o.getHost() != null  &&
		      h.equalsIgnoreCase(o.getHost()))  &&
		     (port == o.getPort()  ||
		      o.getPort() == defaultPort(scheme))  &&
		     (f == null  &&  o.getFile() == null  ||
		      f != null  &&  o.getFile() != null  &&
		      unescapeNoPE(f).equals(unescapeNoPE(o.getFile())))  &&
		     (fragment == null  &&  o.getRef() == null  ||
		      fragment != null  &&  o.getRef() != null  &&
		      unescapeNoPE(fragment).equals(unescapeNoPE(o.getRef())))
		     )
		    );
	}

	return false;
    }


    /**
     * Escape any character not in the given character class.
     *
     * @param elem         the array of characters to escape
     * @param allowed_char the BitSet of all allowed characters
     * @return the elem array with all characters not in allowed_char
     *         escaped
     */
    private static char[] escape(char[] elem, BitSet allowed_char)
    {
	int cnt=0;
	for (int idx=0; idx<elem.length; idx++)
	    if (!allowed_char.get(elem[idx]))  cnt++;

	if (cnt == 0)  return elem;

	char[] tmp = new char[elem.length + 2*cnt];
	for (int idx=0, pos=0; idx<elem.length; idx++, pos++)
	{
	    if (allowed_char.get(elem[idx]))
		tmp[pos] = elem[idx];
	    else
	    {
		if (elem[idx] > 255)
		    throw new RuntimeException("Can't handle non 8-bt chars");
		tmp[pos++] = '%';
		tmp[pos++] = hex[(elem[idx] >> 4) & 0xf];
		tmp[pos]   = hex[elem[idx] & 0xf];
	    }
	}

	return tmp;
    }

    private static final char[] hex =
	    {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};


    /**
     * Unescape escaped characters (i.e. %xx).
     *
     * @param str the string to unescape
     * @return the unescaped string
     * @exception ParseException if the two digits following a `%' are
     *            not a valid hex number
     */
    static final String unescape(String str)  throws ParseException
    {
	if (str == null  ||  str.indexOf('%') == -1)
	    return str;  				// an optimization

	char[] buf = str.toCharArray();
	char[] res = new char[buf.length];

	int didx=0;
	for (int sidx=0; sidx<buf.length; sidx++, didx++)
	{
	    if (buf[sidx] == '%')
	    {
		int ch;
                try
                {
		    ch = Integer.parseInt(str.substring(sidx+1,sidx+3), 16);
		    if (ch < 0)  throw new NumberFormatException();
                }
                catch (NumberFormatException e)
                {
                    throw new ParseException(str.substring(sidx,sidx+3) +
                                            " is an invalid code");
                }
		res[didx] = (char) ch;
		sidx += 2;
	    }
	    else
		res[didx] = buf[sidx];
	}

	return new String(res, 0, didx);
    }


    /**
     * Unescape escaped characters (i.e. %xx). If a ParseException would
     * be thrown then just return the original string.
     *
     * @param str the string to unescape
     * @return the unescaped string, or the original string if unescaping
     *         would throw a ParseException
     * @see #unescape(java.lang.String)
     */
    private static final String unescapeNoPE(String str)
    {
	try
	    { return unescape(str); }
	catch (ParseException pe)
	    { return str; }
    }


    /**
     * Run test set.
     */
    public static void main(String args[])  throws Exception
    {
	System.err.println();
	System.err.println("*** URI Tests ...");


	/* Relative URI test set, taken from Section C of
	 * draft-fielding-uri-syntax-03
	 */

	URI base = new URI("http://a/b/c/d;p?q");

	// normal examples
	testParser(base, "g:h",        "g:h");
	testParser(base, "g",          "http://a/b/c/g");
	testParser(base, "./g",        "http://a/b/c/g");
	testParser(base, "g/",         "http://a/b/c/g/");
	testParser(base, "/g",         "http://a/g");
	testParser(base, "//g",        "http://g");
	testParser(base, "?y",         "http://a/b/c/?y");
	testParser(base, "g?y",        "http://a/b/c/g?y");
	testParser(base, "g?y",        "http://a/b/c/g?y");
	testParser(base, "#s",         "http://a/b/c/d;p?q#s");
	testParser(base, "g#s",        "http://a/b/c/g#s");
	testParser(base, "g?y#s",      "http://a/b/c/g?y#s");
	testParser(base, ";x",         "http://a/b/c/;x");
	testParser(base, "g;x",        "http://a/b/c/g;x");
	testParser(base, "g;x?y#s",    "http://a/b/c/g;x?y#s");
	testParser(base, ".",          "http://a/b/c/");
	testParser(base, "./",         "http://a/b/c/");
	testParser(base, "..",         "http://a/b/");
	testParser(base, "../",        "http://a/b/");
	testParser(base, "../g",       "http://a/b/g");
	testParser(base, "../..",      "http://a/");
	testParser(base, "../../",     "http://a/");
	testParser(base, "../../g",    "http://a/g");

	// abnormal examples
	testParser(base, "",           "http://a/b/c/d;p?q");
	testParser(base, "/./g",       "http://a/./g");
	testParser(base, "/../g",      "http://a/../g");
	testParser(base, "g.",         "http://a/b/c/g.");
	testParser(base, ".g",         "http://a/b/c/.g");
	testParser(base, "g..",        "http://a/b/c/g..");
	testParser(base, "..g",        "http://a/b/c/..g");
	testParser(base, "./../g",     "http://a/b/g");
	testParser(base, "./g/.",      "http://a/b/c/g/");
	testParser(base, "g/./h",      "http://a/b/c/g/h");
	testParser(base, "g/../h",     "http://a/b/c/h");
	testParser(base, "g;x=1/./y",  "http://a/b/c/g;x=1/y");
	testParser(base, "g;x=1/../y", "http://a/b/c/y");
	testParser(base, "g?y/./x",    "http://a/b/c/g?y/./x");
	testParser(base, "g?y/../x",   "http://a/b/c/g?y/../x");
	testParser(base, "g#s/./x",    "http://a/b/c/g#s/./x");
	testParser(base, "g#s/../x",   "http://a/b/c/g#s/../x");
	testParser(base, "http:g",     "http:g");


	/* equality tests */

	// protocol
	testNotEqual("http://a/", "nntp://a/");
	testNotEqual("http://a/", "https://a/");
	testNotEqual("http://a/", "shttp://a/");
	testEqual("http://a/", "Http://a/");
	testEqual("http://a/", "hTTP://a/");
	testEqual("url:http://a/", "hTTP://a/");
	testEqual("urI:http://a/", "hTTP://a/");

	// host
	testEqual("http://a/", "Http://A/");
	testEqual("http://a.b.c/", "Http://A.b.C/");
	testEqual("http:///", "Http:///");
	testNotEqual("http:///", "Http://a/");

	// port
	testEqual("http://a.b.c/", "Http://A.b.C:80/");
	testEqual("nntp://a", "nntp://a:119");
	testEqual("nntp://a/", "nntp://a:119/");
	testNotEqual("nntp://a", "nntp://a:118");
	testNotEqual("nntp://a", "nntp://a:0");
	testEqual("telnet://:23/", "telnet:///");
	testPE("ftp://:a/");
	testPE("ftp://:-1/");
	testPE("ftp://::1/");

	// userinfo
	testNotEqual("ftp://me@a", "ftp://a");
	testNotEqual("ftp://me@a", "ftp://Me@a");
	testEqual("ftp://Me@a", "ftp://Me@a");
	testEqual("ftp://Me:My@a:21", "ftp://Me:My@a");
	testNotEqual("ftp://Me:My@a:21", "ftp://Me:my@a");

	// path
	testEqual("ftp://a/b%2b/", "ftp://a/b+/");
	testEqual("ftp://a/b%2b/", "ftp://a/b+/");
	testEqual("ftp://a/b%5E/", "ftp://a/b^/");
	testNotEqual("ftp://a/b%3f/", "ftp://a/b?/");

	System.err.println("*** Tests finished successfuly");
    }

    private static void testParser(URI base, String relURI, String result)
	    throws Exception
    {
	if (!(new URI(base, relURI).toString().equals(result)))
	{
	    String nl = System.getProperty("line.separator");
	    throw new Exception("Test failed: " + nl +
				"  base-URI = <" + base + ">" + nl +
				"  rel-URI  = <" + relURI + ">" + nl+
				"  expected   <" + result + ">" + nl+
				"  but got    <" + new URI(base, relURI) + ">");
	}
    }

    private static void testEqual(String one, String two)  throws Exception
    {
	if (!(new URI(one).equals(new URI(two))))
	{
	    String nl = System.getProperty("line.separator");
	    throw new Exception("Test failed: " + nl +
				"  <" + one + "> != <" + two + ">");
	}
    }

    private static void testNotEqual(String one, String two)  throws Exception
    {
	if ((new URI(one).equals(new URI(two))))
	{
	    String nl = System.getProperty("line.separator");
	    throw new Exception("Test failed: " + nl +
				"  <" + one + "> == <" + two + ">");
	}
    }

    private static void testPE(String uri)  throws Exception
    {
	boolean got_pe = false;
	try
	    { new URI(uri); }
	catch (ParseException pe)
	    { got_pe = true; }
	if (!got_pe)
	{
	    String nl = System.getProperty("line.separator");
	    throw new Exception("Test failed: " + nl +
				"  <" + uri + "> should be invalid");
	}
    }
}

