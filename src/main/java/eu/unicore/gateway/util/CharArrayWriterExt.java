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
