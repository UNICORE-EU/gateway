package eu.unicore.gateway;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

import org.junit.Test;

import eu.unicore.gateway.base.RawMessageExchange;
import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.soap.SoapFault;
import eu.unicore.gateway.soap.SoapFault.FaultCode;

public class TestParseWrongHeaders
{
	private File f1 = new File("src/test/resources/xmls/NoSoapEnv.xml");
	private File f2 = new File("src/test/resources/xmls/WrongXML.xml");
	private File f3 = new File("src/test/resources/xmls/WrongSoap.xml");

	private void gwParse(Reader r, FaultCode code)
	{
		try
		{
			StringWriter w = new StringWriter();
			RawMessageExchange mex = new RawMessageExchange(r, w, 
					GatewayProperties.DEFAULT_MAX_HDR);
			HeadersParser hp = new HeadersParser("http://localhost");
			hp.parseHeaders(mex);
			fail("Wrong headers parsed successfully");
		} catch (Exception e)
		{
			assertTrue(e instanceof SoapFault);
			SoapFault sf = (SoapFault)e;
			System.out.println("(OK) got fault: " + e);
			assertTrue("Wrong code", sf.getCode().equals(code));
		}
	}


	@Test
	public void test1() throws Exception
	{
			gwParse(new FileReader(f1), FaultCode.VERSION_MISMATCH);
	}

	@Test
	public void test2() throws Exception
	{
			gwParse(new FileReader(f2), FaultCode.SENDER);
	}

	@Test
	public void test3() throws Exception
	{
			gwParse(new FileReader(f3), FaultCode.VERSION_MISMATCH);
	}

	@Test
	public void test4() throws Exception
	{
		final char[] START = ("<env:Envelope xmlns:env='http://schemas.xmlsoap.org/soap/envelope/'><env:Header>   " +
				"                                             ").toCharArray();
		gwParse(new Reader()
		{
			int pos = 0;
			public int read(char[] cbuf, int off, int len) throws IOException
			{
				if (pos < START.length)
				{
					if (pos+len < START.length)
					{
						System.arraycopy(START, pos, cbuf, off, len);
						pos += len;
						return len;
					} else
					{
						int toR = START.length - pos;
						System.arraycopy(START, pos, cbuf, off, toR);
						pos += toR;
						return toR;
					}
				} else
				{
					System.arraycopy("<X/>".toCharArray(), 0, cbuf, off, 4);
					return 4;
				}
			}
			public void close() throws IOException	{}
		}, FaultCode.SENDER);
	}

}
