/*
 * @(#)ExtBufferedInputStream.java			0.3 30/01/1998
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
import java.io.InputStream;
import java.io.FilterInputStream;


/**
 * This class is a modified copy of java.io.BufferedInputStream which fixes
 * the problem in fill when an InterrupedIOException occurs and which
 * extends the class to allow the setting of a string which terminates the
 * read (used for the headers and for the multipart/byte-ranges content type).
 *
 * @version	0.3  30/01/1998
 * @author	Ronald Tschal&auml;r
 * @author	Arthur van Hoff
 *
 * @author      modified by Stefan Lieske, 2005/02/15
 */

/*
 * @(#)BufferedInputStream.java	1.26 97/03/03
 * 
 * Copyright (c) 1995, 1996 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * This software is the confidential and proprietary information of Sun
 * Microsystems, Inc. ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Sun.
 * 
 * SUN MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THE
 * SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, OR NON-INFRINGEMENT. SUN SHALL NOT BE LIABLE FOR ANY DAMAGES
 * SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING
 * THIS SOFTWARE OR ITS DERIVATIVES.
 * 
 * CopyrightVersion 1.1_beta
 * 
 */

/** @author  modified by Stefan Lieske, 2005/02/15 */
//final class ExtBufferedInputStream extends FilterInputStream
final class ExtBufferedInputStream extends DemultiplexorInputStream
{
    /**
     * The buffer where data is stored. 
     *
     * @since   JDK1.0
     */
    private byte buf[];

    /**
     * The index one greater than the index of the last valid byte in 
     * the buffer. 
     *
     * @since   JDK1.0
     */
    private int count;

    /**
     * The current position in the buffer. This is the index of the next 
     * character to be read from the <code>buf</code> array. 
     *
     * @see     java.io.BufferedInputStream#buf
     * @since   JDK1.0
     */
    private int pos;
    
    /**
     * The end-of-data (terminator) string
     */
    private byte[] eod_str;
    private int[]  eod_cmp;

    /**
     * position of eod_str in buffer, or -1 if not found
     */
    private int eod_pos = -1;


    /**
     * Creates a new buffered input stream to read data from the 
     * specified input stream with a default 512-byte buffer size. 
     *
     * @param   in   the underlying input stream.
     * @since   JDK1.0
     */
    public ExtBufferedInputStream(InputStream in)
    {
	this(in, 2048);
    }


    /**
     * Creates a new buffered input stream to read data from the 
     * specified input stream with the specified buffer size. 
     *
     * @param   in     the underlying input stream.
     * @param   size   the buffer size.
     * @since   JDK1.0
     */
    public ExtBufferedInputStream(InputStream in, int size)
    {
	super(in);
	buf = new byte[size];
    }


    /**
     * Fills the buffer with more data, taking into account
     * shuffling and other tricks for dealing with marks.
     * Assumes that it is being called by a synchronized method.
     * This method also assumes that all data has already been read in,
     * hence pos > count.
     */
    private void fill() throws IOException
    {
	if (eod_str == null)
	    pos = 0;		/* no terminator: throw away the buffer */
	else if (pos >= buf.length)	/* no room left in buffer */
	{
	    if (buf.length > eod_str.length)	/* can throw away early part of the buffer */
	    {
		System.arraycopy(buf, pos-eod_str.length, buf, 0,
				 eod_str.length);
		pos = eod_str.length;
	    }
	    else		/* grow buffer */
	    {
		buf = Util.resizeArray(buf, eod_str.length*2);
	    }
	}

	count = pos;		// in case read() throws InterruptedIOException
	int n = in.read(buf, pos, buf.length - pos);
	count = (n <= 0 ? pos : n + pos);

	if (eod_str != null)
	{
	    int start = pos - eod_str.length;
	    if (start < 0)  start = 0;
	    eod_pos =
		Util.findStr(eod_str, eod_cmp, buf, start, count);
	    if (eod_pos >= 0)  eod_pos += eod_str.length;
	}
    }


    /**
     * Reads the next byte of data from this buffered input stream. The 
     * value byte is returned as an <code>int</code> in the range 
     * <code>0</code> to <code>255</code>. If no byte is available 
     * because the end of the stream has been reached, the value 
     * <code>-1</code> is returned. This method blocks until input data 
     * is available, the end of the stream is detected, or an exception 
     * is thrown. 
     * <p>
     * The <code>read</code> method of <code>BufferedInputStream</code> 
     * returns the next byte of data from its buffer if the buffer is not 
     * empty. Otherwise, it refills the buffer from the underlying input 
     * stream and returns the next character, if the underlying stream 
     * has not returned an end-of-stream indicator. 
     *
     * @return     the next byte of data, or <code>-1</code> if the end of the
     *             stream is reached.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterInputStream#in
     * @since      JDK1.0
     */
    public synchronized int read() throws IOException
    {
	if (eod_pos >= 0  &&  pos >= eod_pos)  return -1;

	if (pos >= count)
	{
	    fill();
	    if (pos >= count)
		return -1;
	}

	return buf[pos++] & 0xff;
    }


    /**
     * Reads bytes into a portion of an array.  This method will block until
     * some input is available, an I/O error occurs, or the end of the stream
     * is reached.
     *
     * <p> If this stream's buffer is not empty, bytes are copied from it into
     * the array argument.  Otherwise, the buffer is refilled from the
     * underlying input stream and, unless the stream returns an end-of-stream
     * indication, the array argument is filled with characters from the
     * newly-filled buffer.
     *
     * <p> As an optimization, if the buffer is empty, the mark is not valid,
     * and <code>len</code> is at least as large as the buffer, then this
     * method will read directly from the underlying stream into the given
     * array.  Thus redundant <code>BufferedInputStream</code>s will not copy
     * data unnecessarily.
     *
     * @param      b     destination buffer.
     * @param      off   offset at which to start storing bytes.
     * @param      len   maximum number of bytes to read.
     * @return     the number of bytes read, or <code>-1</code> if the end of
     *             the stream has been reached.
     * @exception  IOException  if an I/O error occurs.
     */
    public synchronized int read(byte b[], int off, int len) throws IOException
    {
	if (eod_pos >= 0  &&  pos >= eod_pos)  return -1;

	int avail = count - pos;
	if (avail <= 0)
	{
	    /* If the requested length is larger than the buffer, and if there
	     * is no terminator, do not bother to copy the bytes into the
	     * local buffer. Saves unnecessary copying.
	     */
	    if (len >= buf.length && eod_str == null)
		return in.read(b, off, len);

	    fill();
	    avail = count - pos;
	    if (avail <= 0)
		return -1;
	}

	int cnt = (avail < len) ? avail : len;
	if (eod_pos >= 0  &&  (eod_pos-pos) < cnt)
	    cnt = eod_pos - pos;

	System.arraycopy(buf, pos, b, off, cnt);
	pos += cnt;
	return cnt;
    }


    /**
     * Returns the number of bytes that can be read from this input 
     * stream without blocking. 
     * <p>
     * The <code>available</code> method of 
     * <code>BufferedInputStream</code> returns the sum of the the number 
     * of bytes remaining to be read in the buffer 
     * (<code>count&nbsp;- pos</code>) 
     * and the result of calling the <code>available</code> method of the 
     * underlying input stream. 
     *
     * @return     the number of bytes that can be read from this input
     *             stream without blocking.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterInputStream#in
     * @since      JDK1.0
     */
    public synchronized int available() throws IOException
    {
	if (eod_pos < 0)
	    return (count - pos) + in.available();
	else
	    return (eod_pos - pos);
    }


    /**
     * After setting a terminator read() and available() will act as if
     * the input stream ended just after the terminator; use null to
     * disable. Resets the atEnd() flag.
     */
    public void setTerminator(byte[] end_str, int[] end_cmp)
    {
	eod_str = end_str;
	eod_cmp = end_cmp;

	if (eod_str != null)
	{
	    eod_pos = Util.findStr(eod_str, eod_cmp, buf, pos, count);
	    if (eod_pos >= 0)  eod_pos += eod_str.length;
	}
	else
	    eod_pos = -1;
    }


    /**
     * has the terminator been reached? If this returns true, then the
     * next read() will return -1. Use this to distinguish between EOF
     * on the underlying stream and having reached the terminator.
     */
    public boolean atEnd()
    {
	return (eod_pos >= 0  &&  pos >= eod_pos);
    }


    /**
     * This is a hack to deal with empty trailers. If the next two bytes
     * on the stream are CR and LF respectively, then these are gobled
     * up and the method returns true; else nothing is read and false is
     * returned.
     */
    public boolean startsWithCRLF()  throws IOException
    {
	// the easy cases

	if (count - pos >= 2)
	{
	    if (buf[pos] == '\r'  &&  buf[pos+1]  == '\n')
	    {
		pos += 2;
		return true;
	    }
	    return false;
	}

	if ((count - pos) == 1  &&  buf[pos] != '\r')  return false;
	

	// Ok, so now we have to do some ugly messing about

	// move byte to beginning of buffer and reset buffer
	count = count - pos;
	if (count == 1)
	    buf[0] = buf[pos];
	pos = 0;

	// check for \r
	if (count == 0)
	    buf[count++] = (byte) in.read();
	if (buf[pos] != '\r')  return false;

	// check for \n
	buf[count++] = (byte) in.read();
	if (buf[pos+1] != '\n')  return false;

	// found CRLF - goble them up
	pos += 2;
	return true;
    }
}

