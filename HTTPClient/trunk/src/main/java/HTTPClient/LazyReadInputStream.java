/*
 *  This file is part of the HTTPClient package
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
 *  This file was added to the HTTPClient library (0.4dev) by Stefan Lieske, 2005/02/15.
 */
package HTTPClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.FilterInputStream;


/**
 * This class is a demultiplexor stream with lazy reading. It never reads more bytes from the
 * underlying stream then necessary (especially no buffering other than for searching the data
 * terminator is done). This behavior is needed when dealing with the HTTP CONNECT method to
 * prevent reading data from the TCP-raw part of the connection.
 * If no CONNECT is used, it's better to use a buffered stream for performance reasons.
 */
public class LazyReadInputStream extends DemultiplexorInputStream {

  /**
   * Stores the bytes needed for searching the terminator.
   */
  private byte m_buf[];

  /**
   * After a change of the terminator, there are maybe some bytes still in the search buffer ->
   * those bytes will be stored in this buffer and will be used first (before a read-operation is
   * performed on the underlying stream.
   */
  private byte m_oldBuf[];
        
  /**
   * The end-of-data (terminator) string
   */
  private byte[] m_eodStr;

  /**
   * Stores the underlying input stream.
   */
  private InputStream m_underlyingStream;

  /**
   * Whether the end of ther underlying stream is reached (a read-call returned -1) or not.
   */
  private boolean m_endOfUnderlyingStreamReached;

    
  /**
   * Creates a new LazyReadInputStream to read data from the specified input stream. 
   *
   * @param a_in The underlying input stream.
   */
  public LazyReadInputStream(InputStream a_in) {
    super(a_in);
    m_buf = null;
    m_eodStr = null;
    m_oldBuf = new byte[0];
    m_underlyingStream = a_in;
    m_endOfUnderlyingStreamReached = false;
  }
  

  /**
   * Reads the next byte of data from this LazyReadInputStream. The value byte is returned as an
   * <code>int</code> in the range <code>0</code> to <code>255</code>. If no byte is available 
   * because the end of the stream or the end-of-data terminator has been reached the value 
   * <code>-1</code> is returned. This method blocks until input data is available, the end of the
   * stream is detected, or an exception is thrown. 
   * <p>
   *
   * @return The next byte of data, or <code>-1</code> if the end of the stream or data is
   *         reached.
   *
   * @exception IOException  if an I/O error occurs.
   */
  public synchronized int read() throws IOException {    
    /* the default is to return EOF */
    int byteRead = -1;
    if (m_eodStr != null) {
      if (m_buf == null) {
        /* new eod string -> we have to refill the buffer first */
        m_buf = new byte[m_eodStr.length];
        int pos = 0;
        while (pos < m_buf.length) {
          int nextByte = readAhead();
          if (nextByte != -1) {
            m_buf[pos] = (byte)nextByte;
            pos++;
          }
          else {
            byte[] tempBuf = new byte[pos];
            System.arraycopy(m_buf, 0, tempBuf, 0, pos);
            m_buf = tempBuf;
          }
        }
      }
      boolean endOfDataFound = true;
      if (m_buf.length == m_eodStr.length) {
        for (int i = 0; i < m_eodStr.length; i++) {
          if (m_buf[i] != m_eodStr[i]) {
            endOfDataFound = false;
            break;
          }
        }
      }
      if (m_buf.length > 0) {
        byte[] tempBuf = new byte[m_buf.length - 1];
        System.arraycopy(m_buf, 1, tempBuf, 0, tempBuf.length);
        byteRead = m_buf[0] & 0xFF;
        if (endOfDataFound == true) {
          /* there is the eod string currently in the buffer (or it was there or the end of the
           * underlying stream is reached) -> don't read more bytes from the underlying stream ->
           * shrink the buffer with every byte read
           */
          m_buf = tempBuf;
        }
        else {
          /* we don't have found the eod yet -> refill the buffer with one byte at the end */     
          int readAheadByte = readAhead();
          if (readAheadByte != -1) {
            System.arraycopy(tempBuf, 0, m_buf, 0, tempBuf.length);
            m_buf[m_buf.length - 1] = (byte)readAheadByte;
          }  
          else {
            /* end of underlying stream reached */
            m_buf = tempBuf;
          }
        }
      }
    }
    else {
      /* there is no eod string */
      byteRead = readAhead();
    } 
    return byteRead;
  }


  /**
   * Reads bytes into a portion of an array.  This method will block until some input is
   * available, an I/O error occurs, or the end of the stream or data is reached. It's always
   * tried to read the specified number of bytes from the underlying stream. If the end of stream
   * is detected or the data terminator is found, this method returns the number of bytes read
   * until the end of the stream or the end of data. If the end of data or end of stream is
   * reached and no byte was read from the stream, -1 is returned (of course only, if at least one
   * byte should be read from the stream).
   *
   * @param a_buffer The destination buffer.
   * @param a_offset The offset in the destination buffer at which to start storing bytes.
   * @param a_length The maximum number of bytes to read.
   *
   * @return The number of bytes read, or <code>-1</code> if the end of the stream or the current
   *         data block has been reached and no byte could be read.
   *
   * @exception IOException If an I/O error occurs.
   */
  public synchronized int read(byte a_buffer[], int a_offset, int a_length) throws IOException {
    int bytesRead = 0;
    boolean endOfStreamReached = false;
    while ((!endOfStreamReached) && (bytesRead < a_length)) {
      int currentByte = read();
      if (currentByte == -1) {
        endOfStreamReached = true;
      }
      else {
        a_buffer[a_offset + bytesRead] = (byte)currentByte;
        bytesRead++;
      }
    }
    if ((endOfStreamReached) && (bytesRead == 0)) {
      /* we were not able to read any of the requested bytes */
      bytesRead = -1;
    }
    return bytesRead;
  }

  /**
   * Returns the minimum number of bytes that can be read from this input stream without blocking. 
   * If an end-of-data terminator is set, this number is never bigger than the length of the
   * data terminator string also if there are more bytes available in the underlying stream,
   * because the terminator could be at the next position in the stream (and because of
   * lazy-reading we don't know it yet). So in this case here is only the number of bytes returned
   * which can be read surely without reaching the end of data.
   * If no data terminating string is set, this method returns the real maximum number of
   * available bytes, which can be read without blocking.
   *
   * @return The minimum number of bytes which can be read from the stream without blocking and
   *         without reaching the end of data or end of stream.
   *
   * @exception IOException If an I/O error occurs.
   */
  public synchronized int available() throws IOException {
    int availableBytes = 0;
    if (m_eodStr == null) {
      /* it is possible to read the bytes from the old buffer + the available bytes in the
       * underlying stream
       */
      availableBytes = m_oldBuf.length + m_underlyingStream.available();
    }
    else {
      if (m_buf == null) {
        /* it would be possible to read the bytes from the old buffer + the available bytes in the
         * underlying stream, but first the buffer needs to be refilled (those bytes are not
         * available) and there is also the possibility to reach the eod after filling the buffer
         * -> maximum available is the size of the buffer also at least 0 bytes will be available
         */
        availableBytes = Math.max(0, Math.min(m_eodStr.length, m_oldBuf.length + m_underlyingStream.available() - m_eodStr.length));
      }
      else {
        /* it would be possible to read the bytes from the old buffer + the available bytes in the
         * underlying stream, but there is the possibility to reach the eod -> maximum available
         * is the size of the buffer
         */
        availableBytes = Math.min(m_buf.length, m_oldBuf.length + m_underlyingStream.available());
      }
    }
    return availableBytes;
  }

  /**
   * Changes the end-of-data terminator. Also it resets the end-of-data flag, so reading the data
   * until the next occurence of the data terminator is possible.
   *
   * @param a_terminator A byte signature which is used to find the end of a data block. If null
   *                     is specified, no search for a data delimiter is done.
   * @param a_searchOptimizer The distances in the terminator string for the Knuth-Morris-Pratt
   *                          search algorithm, see Util.compile_search() for creating it. This
   *                          value is only for compatibility to the DemultiplexorInputStream
   *                          interface, but is ignored completely within LazyReadInputStream. So
   *                          also null can be specified here.
   */
  public synchronized void setTerminator(byte[] a_terminator, int[] a_searchOptimizer) {
    m_eodStr = a_terminator;
    if (m_buf != null) {
      /* copy the bytes which are already in the buffer at the end of the buffer of old bytes
       * -> they will be used before the next bytes from the underlying stream are read
       */
      byte[] tempBuf = new byte[m_oldBuf.length + m_buf.length];
      System.arraycopy(m_oldBuf, 0, tempBuf, 0, m_oldBuf.length);
      System.arraycopy(m_buf, 0, tempBuf, m_oldBuf.length, m_buf.length);
      m_oldBuf = tempBuf;
    }
    /* invalidate the buffer -> it's refilled with the next read, if necessary */
    m_buf = null;
  }
  
  /**
   * This method returns only true, if the terminator is reached and also already read, so the
   * next call of read() would return -1 because the end of the data block is reached. If the end
   * of the underlying stream is reached without reaching the terminator or no terminator is set
   * or there are still available data until the next data terminator, false is returned.
   * If this method returns true, setTerminator() has to be called in order to read the next data
   * block from the stream.
   *
   * @return Whether the end of the current data block is reached or not.
   */
  public synchronized boolean atEnd() {
    boolean eodReached = false;
    if ((m_eodStr != null) && (m_buf != null) && (!m_endOfUnderlyingStreamReached)) {
      if (m_buf.length == 0) {
        /* not the end of the stream, but an empty buffer -> eod string was in buffer */
        eodReached = true;
      }
    }
    return eodReached;
  }

  /**
   * This is a hack to deal with empty trailers. If the next two bytes on the stream are CR and LF
   * respectively, then these are gobled up and the method returns true; else nothing is read and
   * false is returned. Data terminators are ignored by this call, it's always worked directly on
   * the pure stream.
   *
   * @return True, if the next two bytes returned by read() would be CR+LF. In this case, those
   *         two bytes are removed from the stream. If the next two bytes are not CR+LF, false is
   *         returned and the bytes are still in the stream and will be returned by the next two
   *         read calls.
   *
   * @exception IOException If an I/O error occurs. 
   */
  public synchronized boolean startsWithCRLF() throws IOException {
    boolean startsWithCRLF = false;
    if ((m_buf != null) && (m_buf.length > 0)) {
      /* check the first byte in the buffer */
      if (m_buf[0] == '\r') {
        /* first byte is matching */
        if (m_buf.length > 1) {
          /* check the second byte in the buffer */
          if (m_buf[1] == '\n') {
            startsWithCRLF = true;
            /* it matches -> take the bytes out of the buffer, by reading twice */
            read();
            read();
          }
        }
        else {
          /* we cannot verify the second byte against the buffer -> try the buffer of old bytes
           */
          if (m_oldBuf.length > 0) {
            if (m_oldBuf[0] == '\n') {
              startsWithCRLF = true;
              /* first: remove the second byte from the buffer of old bytes */
              readAhead();
              /* second: remove the first byte from the normal buffer */
              read();
            }
          }
          else {
            /* the second byte isn't in any buffer -> fetch it from the underlying stream */
            int secondByte = m_underlyingStream.read();
            if (secondByte != -1) {
              if (((byte)secondByte) == '\n') {
                startsWithCRLF = true;
                /* we have still to remove the first byte from the buffer */
                read();
              }
              else {
                /* store the second byte in the buffer of old bytes */
                m_oldBuf = new byte[1];
                m_oldBuf[0] = ((byte)secondByte);
              }
            }
          }
        }
      }
    } 
    else {
      /* there are no bytes in the normal buffer -> check the buffer of old bytes */
      if (m_oldBuf.length > 0) {
        if (m_oldBuf[0] == '\r') {
          if (m_oldBuf.length > 1) {
            if (m_oldBuf[1] == '\n') {
              startsWithCRLF = true;
              /* remove the bytes from the buffer of old bytes */
              readAhead();
              readAhead();
            }
          }
          else {
            /* we have to read the second byte from the underlying stream */
            int secondByte = m_underlyingStream.read();
            if (secondByte != -1) {
              if (((byte)secondByte) == '\n') {
                startsWithCRLF = true;
                /* we have still to remove the first byte from the buffer of old bytes */
                readAhead();
              }
              else {
                /* store the second byte also in the buffer of old bytes */
                byte[] tempBuf = new byte[2];
                tempBuf[0] = m_oldBuf[0];
                tempBuf[1] = ((byte)secondByte);
                m_oldBuf = tempBuf;
              }
            }
          }
        }
      }
      else {
        /* we don't have any bytes in any buffer -> we have to read from the underlying stream */
        int firstByte = m_underlyingStream.read();
        if (firstByte != -1) {
          if (((byte)firstByte) == '\r') {
            /* we have to read also a second byte */    
            int secondByte = m_underlyingStream.read();
            if (secondByte != -1) {
              if (((byte)secondByte) == '\n') {
                startsWithCRLF = true;
              }
              else {
                /* store both bytes in the buffer of old bytes */
                m_oldBuf = new byte[2];
                m_oldBuf[0] = ((byte)firstByte);
                m_oldBuf[1] = ((byte)secondByte);
              }
            }
            else {
              /* store the first byte in the buffer of old bytes */
              m_oldBuf = new byte[1];
              m_oldBuf[0] = ((byte)firstByte);
            }
          }
          else {
            /* store the first byte in the buffer of old bytes */
            m_oldBuf = new byte[1];
            m_oldBuf[0] = ((byte)firstByte);
          }
        }
      }
    }
    return startsWithCRLF;
  }  

  /**
   * This method reads the next byte. If there are still available bytes in the buffer of old
   * bytes, the first of them is returned (and of course removed from the buffer). If there are
   * no more bytes in the buffer, it is tried to read one byte from the underlying stream.
   *
   * @return The next byte to handle or -1, if the end of the stream is reached.
   *
   * @exception IOException If an I/O error occurs.
   */
  private synchronized int readAhead() throws IOException {
    int byteRead = -1;
    if (m_oldBuf.length > 0) {
      /* read it from the buffer of already read bytes */
      byte[] tempBuf = new byte[m_oldBuf.length - 1];
      System.arraycopy(m_oldBuf, 1, tempBuf, 0, tempBuf.length);
      byteRead = m_oldBuf[0] & 0xff;
      m_oldBuf = tempBuf;
    }
    else {
      /* no more old bytes in buffer -> read next byte from the underlying stream */
      byteRead = m_underlyingStream.read();
      if (byteRead == -1) {
        m_endOfUnderlyingStreamReached = true;
      }
    }
    return byteRead;
  }
      
}

