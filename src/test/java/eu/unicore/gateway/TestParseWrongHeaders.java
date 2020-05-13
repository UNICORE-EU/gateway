package eu.unicore.gateway;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

import eu.unicore.bugsreporter.annotation.FunctionalTest;
import eu.unicore.gateway.base.RawMessageExchange;
import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.soap.SoapFault;
import eu.unicore.gateway.soap.SoapFault.FaultCode;

import junit.framework.TestCase;

public class TestParseWrongHeaders extends TestCase
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

	@FunctionalTest(id="gw_wrongHdr", description="Tests request without SOAP envelope")
	public void test1()
	{
		try
		{
			gwParse(new FileReader(f1), FaultCode.VERSION_MISMATCH);
		} catch (FileNotFoundException e)
		{
			fail("can't read test input file");
		}
	}

	@FunctionalTest(id="gw_wrongHdr", description="Tests request with invalid XML")
	public void test2()
	{
		try
		{
			gwParse(new FileReader(f2), FaultCode.SENDER);
		} catch (FileNotFoundException e)
		{
			fail("can't read test input file");
		}
	}

	@FunctionalTest(id="gw_wrongHdr", description="Tests request with wrong SOAP version")
	public void test3()
	{
		try
		{
			gwParse(new FileReader(f3), FaultCode.VERSION_MISMATCH);
		} catch (FileNotFoundException e)
		{
			fail("can't read test input file");
		}
	}

	@FunctionalTest(id="gw_wrongHdr", description="Tests request with large SOAP header")
	public void test4()
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

	public static String readFile(File f) throws IOException
	{
		BufferedReader r = new BufferedReader(new FileReader(f));
		char[] buf = new char[102400];
		r.read(buf);
		r.close();
		return new String(buf);
	}
}
