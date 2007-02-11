/*
 * @(#)ExtByteArrayOutputStream.java			0.4 30/01/1998
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
import java.io.IOException;

/**
 * This class is modeled after java.io.ByteArrayOutputStream. The main
 * addition are the write(String,...) methods.
 *
 * <P>This class' raison d'etre is an optimization one: wrapping a
 * DataOutputStream around a ByteArrayOutputStream was slow.
 *
 * @version	0.4  29/03/1998
 * @author	Ronald Tschal&auml;r
 * @since	V0.4
 */

final class ExtByteArrayOutputStream
{
    /** the buffer */
    private byte[] buf;

    /** the number of bytes current in buffer */
    private int    count;


    // Constructors

    /**
     * Constructs a new ExtByteArrayOutputStream with an initial buffer
     * size of 100 bytes.
     */
    public ExtByteArrayOutputStream()
    {
	this(100);
    }


    /**
     * Constructs a new ExtByteArrayOutputStream with the specified initial
     * buffer size.
     *
     * @param size initial size of buffer in bytes.
     */
    public ExtByteArrayOutputStream(int size)
    {
	buf = new byte[size];
	count = 0;
    }


    // Methods

    /**
     * Write a String to the buffer. The string is assumed to contain only
     * 8-bit chars, i.e. only the lower byte of each char is written.
     *
     * @param str the String to write
     */
    public final void write(String str)
    {
	int len = str.length();

	if (buf.length - count < len)
	    buf = Util.resizeArray(buf, Math.max(buf.length*2, count + 2*len));

//	str.getBytes(0, len, buf, count);
	Util.getBytes(str,buf,count);
	count += len;
    }


    /**
     * Write two Strings to the buffer. The strings are assumed to contain
     * only 8-bit chars, i.e. only the lower byte of each char is written.
     *
     * @param str1 the first String to write
     * @param str2 the second String to write
     */
    public final void write(String str1, String str2)
    {
	int len1 = str1.length(), len2 = str2.length();

	if (buf.length - count < (len1+len2))
	    buf = Util.resizeArray(buf,
				Math.max(buf.length*2, count + 2*(len1+len2)));

//        str1.getBytes(0, len1, buf, count);
	Util.getBytes(str1,buf,count);
	count += len1;
//	str2.getBytes(0, len2, buf, count);
	Util.getBytes(str2,buf,count);
	count += len2;
    }


    /**
     * Write three Strings to the buffer. The strings are assumed to contain
     * only 8-bit chars, i.e. only the lower byte of each char is written.
     *
     * @param str1 the first String to write
     * @param str2 the second String to write
     * @param str3 the third String to write
     */
    public final void write(String str1, String str2, String str3)
    {
	int len1 = str1.length(), len2 = str2.length(), len3 = str3.length();

	if (buf.length - count < (len1+len2+len3))
	    buf = Util.resizeArray(buf,
			    Math.max(buf.length*2, count + 2*(len1+len2+len3)));

//	str1.getBytes(0, len1, buf, count);
	Util.getBytes(str1,buf,count);
	count += len1;
//	str2.getBytes(0, len2, buf, count);
	Util.getBytes(str2,buf,count);
	count += len2;
//	str3.getBytes(0, len3, buf, count);
	Util.getBytes(str3,buf,count);
	count += len3;
    }


    /**
     * Write a String to the buffer. The string is assumed to contain only
     * 8-bit chars, i.e. only the lower byte of each char is written.
     *
     * @param str the String to write
     */
    public final void write(byte[] data)
    {
	if (buf.length - count < data.length)
	    buf = Util.resizeArray(buf,
			       Math.max(buf.length*2, count + 2*data.length));

	System.arraycopy(data, 0, buf, count, data.length);
	count += data.length;
    }


    /**
     * Reset the buffer so it can be reused.
     */
    public final void reset()
    {
	count = 0;
    }


    /**
     * Write the contents on the output stream;
     *
     * @param os the OutputStream on which to write the data
     * @exception IOException if <var>os</var> throws IOException
     */
    public final void writeTo(OutputStream os)  throws IOException
    {
	os.write(buf, 0, count);
    }
}

