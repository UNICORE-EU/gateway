package eu.unicore.gateway.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;

/**
 * Proxy over a given Reader implementation. Basic rules are that:
 * all read operations are routed through here implemented generic read(char[], int, int),
 * marking and reset are unsupported and all other operations are passed to the underlying impl.
 * <p>
 * The purpose of this proxy is to buffer locally everything read from the underlying Reader 
 * until replayRest is called, so it can be played back. After the replayRest is invoked the reader 
 * only proxies the read operations.
 *  
 * @author golbi
 */
public class BufferingProxyReader extends Reader
{
	private Reader reader;
	private CharArrayWriterExt buffer;
	private int bufPtr;
	private int markedPos;
	private int maxHeader;

	public BufferingProxyReader(Reader reader, int maxHeader)
	{
		this.reader = reader;
		buffer = new CharArrayWriterExt(10240);
		bufPtr = 0;
		markedPos = 0;
		this.maxHeader = maxHeader;
	}

	@Override
	public int read(char[] cbuf, int off, int len) throws IOException
	{
		if (buffer == null)
		{
			return reader.read(cbuf, off, len);
		}
		if (len + bufPtr >= maxHeader)
			throw new IOException("Header is too large. Gateway supports up to " + 
				maxHeader + "b headers");
		int readChars = reader.read(cbuf, off, len);
		if (readChars < 1)
			return readChars;
		buffer.write(cbuf, off, readChars);
		bufPtr += readChars;
		return readChars;
	}
	
	public void replay(OutputStream os, int startOffset, int len, String charset) throws IOException 
	{
		String s = new String(buffer.getInternalBuffer(), startOffset, len);
		os.write(s.getBytes(charset));
	}

	public void replayRest(OutputStream os, int startOffset, String charset) throws IOException 
	{
		int len = bufPtr - startOffset;
		if (len > 0)
		{
			
			String s = new String(buffer.getInternalBuffer(), startOffset, len);
			os.write(s.getBytes(charset));
		}
		//just to ensure we get the memory free ASAP
		buffer = null;
	}

	public void disableBuffer(){
		assert markedPos <= 0;
		buffer = null;
	}
	
	public void setMarkedPos(int pos)
	{
		markedPos = pos;
	}
	
	public int getMarkedPos()
	{
		return markedPos;
	}
	
	
	
	
	@Override
	public void close() throws IOException
	{
		reader.close();
	}
	
	@Override
	public void mark(int readAheadLimit) throws IOException
	{
		throw new IOException("mark is not supported");
	}

	@Override
	public int read(char[] cbuf) throws IOException
	{
		return read(cbuf, 0, cbuf.length);
	}

	@Override
	public boolean ready() throws IOException
	{
		return reader.ready();
	}

	@Override
	public void reset() throws IOException
	{
		throw new IOException("reset is unsupported");
	}

	@Override
	public long skip(long n) throws IOException
	{
		return reader.skip(n);
	}
	
	public int getCurrentPosition(){
		return bufPtr;
	}
}
