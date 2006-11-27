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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This abstract class defines some methods every input stream of a demultiplexor has to
 * implement. The demultiplexor will choose an implementation depending on the need of
 * compatibilty for HTTP CONNECT requests and the performance of the implementation.
 */ 
public abstract class DemultiplexorInputStream extends FilterInputStream {

  /**
   * This initializes the new instance of DemultiplexorInputStream. This constructor delegates
   * only to the parent constructor (FilterInputStream).
   */
  public DemultiplexorInputStream(InputStream a_underlyingStream) {
    super(a_underlyingStream);
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
  public abstract int read() throws IOException;   

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
  public abstract int read(byte a_buffer[], int a_offset, int a_length) throws IOException;

  /**
   * Returns the minimum number of bytes that can be read from this input stream without blocking.
   * If a data block terminator is set, this number will not be larger than the number of bytes
   * until the terminator is reached. 
   *
   * @return The minimum number of bytes which can be read from the stream without blocking and
   *         without reaching the end of data or end of stream.
   *
   * @exception IOException If an I/O error occurs.
   */
  public abstract int available() throws IOException;

  /**
   * Changes the end-of-data terminator. Also it resets the end-of-data flag, so reading the data
   * until the next occurence of the data terminator is possible.
   *
   * @param a_terminator A byte signature which is used to find the end of a data block. If null
   *                     is specified, no search for a data delimiter is done.
   * @param a_searchOptimizer The distances in the terminator string for the Knuth-Morris-Pratt
   *                          search algorithm, see Util.compile_search() for creating it.
   */
  public abstract void setTerminator(byte[] a_terminator, int[] a_searchOptimizer);
  
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
  public abstract boolean atEnd();

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
  public abstract boolean startsWithCRLF() throws IOException;
    
}

