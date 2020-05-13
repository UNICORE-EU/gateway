/*
 * Copyright (c) 2009 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE file for licencing information.
 *
 * Created on 2009-08-20
 * Author: K. Benedyczak <golbi@mat.umk.pl>
 */

package eu.unicore.gateway.util;

import java.io.CharArrayWriter;

public class CharArrayWriterExt extends CharArrayWriter
{

	public CharArrayWriterExt(int initialSize)
	{
		super(initialSize);
	}

	public char[] getInternalBuffer()
	{
		return buf;
	}
}
