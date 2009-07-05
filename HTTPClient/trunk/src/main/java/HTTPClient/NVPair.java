/*
 * @(#)NVPair.java					0.4 20/10/1998
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
 * This class holds a Name/Value pair of strings. It's used for headers,
 * form-data, attribute-lists, etc. This class is immutable.
 *
 * @version	0.4  20/10/1998
 * @author	Ronald Tschal&auml;r
 * @author      modified by Stefan Lieske, 2005/02/13
 */

public final class NVPair
{
    /** the name */
    private String name;

    /** the value */
    private String value;

    /**
     * Whether the value should be quoted to "value". Also all occurences of '"' within the value
     * will be replaced.
     *
     * @author  added by Stefan Lieske, 2005/02/13
     */
    private boolean m_quoteValue;


    // Constructors

    /**
     * Creates an empty name/value pair.
     */
    NVPair()
    {
	this("", "");
    }

    /**
     * Creates a copy of a given name/value pair.
     * @param p the name/value pair to copy
     */
    public NVPair(NVPair p)
    {
	this(p.name, p.value);
    }

    /**
     * Creates a new name/value pair and initializes it to the
     * specified name and value.
     * @param name  the name
     * @param value the value
     */
    public NVPair(String name, String value)
    {
      /** @author      modified by Stefan Lieske, 2005/02/13 */
      //this.name  = name;
      //this.value = value;
      this(name, value, true);
    }


    /**
     * Creates a new name/value pair and initializes it to the
     * specified name and value.
     *
     * @param a_name  the name
     * @param a_value the value
     * @param a_quoteValue Whether the value should be quoted or not when writing it to an output.
     *
     * @author  added by Stefan Lieske, 2005/02/13     
     */
    public NVPair(String a_name, String a_value, boolean a_quoteValue) {
      name = a_name;
      value = a_value;
      m_quoteValue = a_quoteValue;
    }

    // Methods

    /**
     * get the name
     *
     * @return the name
     */
    public final String getName()
    {
	return name;
    }

    /**
     * get the value
     *
     * @return the value
     */
    public final String getValue()
    {
	return value;
    }

    /**
     * Returns whether the value should be quoted or not.
     *
     * @return True, if the value needs quotation or false if not.
     *
     * @author  added by Stefan Lieske, 2005/02/13
     */
    public boolean quoteValue() {
      return m_quoteValue;
    }
    

    /**
     * produces a string containing the name and value of this instance.
     * @return a string containing the class name and the name and value
     */
    public String toString()
    {
	return getClass().getName() + "[name=" + name + ",value=" + value + "]";
    }
}

