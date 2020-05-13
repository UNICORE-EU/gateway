package eu.unicore.gateway;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.Charset;

import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.util.BufferingProxyReader;
import junit.framework.TestCase;

public class TestBufferingProxyReader extends TestCase 
{
	public void test1()throws Exception
	{
		StringReader sr = new StringReader("1234567890abcde");
		BufferingProxyReader proxy = new BufferingProxyReader(sr, null, 
				GatewayProperties.DEFAULT_MAX_HDR);
		char cbuf[] = new char[5];
		int r = proxy.read(cbuf);
		proxy.setMarkedPos(5);
		assertTrue("E1 - proxy reading len", r == cbuf.length);
		assertTrue("E2 - proxy reading", "12345".equals(new String(cbuf)));
		r = proxy.read(cbuf);
		assertTrue("E3 - proxy reading len", r == cbuf.length);
		assertTrue("E4 - proxy reading", "67890".equals(new String(cbuf)));
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream(15);
		proxy.replay(baos, 0, 5, Charset.defaultCharset().name());
		String s1 = new String(baos.toByteArray());
		assertTrue("E5 - replay: '" + s1 + "'", "12345".equals(s1));
		proxy.replayRest(baos, 5, Charset.defaultCharset().name());
		s1 = new String(baos.toByteArray());
		assertTrue("E6 - replayRest: '" + s1 + "'", "1234567890".equals(s1));
		
		r = proxy.read(cbuf);
		assertTrue("E7 - proxy reading rest len", r == cbuf.length);
		assertTrue("E8 - proxy reading rest", "abcde".equals(new String(cbuf)));
		proxy.close();
	}
	
	
	public void test2()throws Exception
	{
		String f1 = Utils.readFile(new File("src/test/resources/f1"));
		String f2 = Utils.readFile(new File("src/test/resources/f2"));
		String f3 = Utils.readFile(new File("src/test/resources/f3"));
		
		StringReader sr = new StringReader(f1+f2+f3);
		BufferingProxyReader proxy = new BufferingProxyReader(sr, null, 
				GatewayProperties.DEFAULT_MAX_HDR);
		char cbuf[] = new char[f1.length()];
		int r = proxy.read(cbuf);
		proxy.setMarkedPos(cbuf.length);
		assertTrue("E1 - proxy reading len", r == cbuf.length);
		assertTrue("E2 - proxy reading", f1.equals(new String(cbuf)));
		cbuf = new char[f2.length()];
		r = proxy.read(cbuf);
		assertTrue("E3 - proxy reading len", r == cbuf.length);
		assertTrue("E4 - proxy reading", f2.equals(new String(cbuf)));
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream(f1.length() + f2.length() + f3.length());
		proxy.replay(baos, 0, f1.length(), Charset.defaultCharset().name());
		String s1 = new String(baos.toByteArray());
		assertTrue("E5 - replay: '" + s1 + "'", f1.equals(s1));
		proxy.replayRest(baos, f1.length(), Charset.defaultCharset().name());
		s1 = new String(baos.toByteArray());
		assertTrue("E6 - replayRest: '" + s1 + "'", (f1+f2).equals(s1));
		
		cbuf = new char[f3.length()];
		r = proxy.read(cbuf);
		assertTrue("E7 - proxy reading rest len", r == cbuf.length);
		assertTrue("E8 - proxy reading rest", f3.equals(new String(cbuf)));
		proxy.close();
	}
	
	
}
