package eu.unicore.gateway;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.StringWriter;
import java.security.cert.X509Certificate;

import org.apache.logging.log4j.Logger;

import eu.unicore.gateway.base.RawMessageExchange;
import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.util.LogUtil;
import eu.unicore.security.canl.AuthnAndTrustProperties;
import eu.unicore.security.canl.CredentialProperties;
import eu.unicore.security.canl.TruststoreProperties;
import junit.framework.TestCase;

public class TestInsertConsignor extends TestCase
{
	public static final Logger log = LogUtil.getLogger("gateway", TestInsertConsignor.class);
	private File secProps = new File("src/test/resources/security.properties");

	public static String checkResult(String result, String input, String insertionPointAfter)
	{
		int ptr = input.indexOf(insertionPointAfter);
		ptr += insertionPointAfter.length();
		if (!result.substring(0, ptr).equals(input.substring(0, ptr)))
		{
			fail("beginning parts do not match");
		}
		
		String rest = input.substring(ptr);
		rest = rest.trim();
		result = result.trim();
		if (!result.endsWith(rest))
		{
			fail("ending parts do not match: 1)\n" + rest + "\n\n2)\n" + result);
		}
		
		int ptr2 = result.indexOf(rest);
		return result.substring(ptr, ptr2);
	}
	
	public String invokeCommon(ConsignorProducer cp, File f, boolean checkWSA)
	{
		try
		{
			StringWriter w = new StringWriter();
			RawMessageExchange mex = new RawMessageExchange(new FileReader(f), w, 
					GatewayProperties.DEFAULT_MAX_HDR);
			new HeadersParser("http://localhost").parseHeaders(mex);
			if (checkWSA)
			{
				assertEquals("http://localhost:8080/s1/services/hello", 
					mex.getWsaToAddress());
				assertEquals("http://hello.com/test", mex.getWsaAction());
			}
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			POSTHandler.writeToOutputStream(mex, bos, null, "127.0.0.1", cp, log);
			String result = new String(bos.toByteArray());
			System.out.println(result);
			assertTrue(result.contains("Header"));
			return result;
		} catch (Exception e)
		{
			e.printStackTrace();
			fail(e.getMessage());
			return null;
		}
	}

	public void testInsertConsignorNonSigned()
	{
		try
		{
			ConsignorProducer cp = new ConsignorProducer(false, 30,	60, 
					new AuthnAndTrustProperties(secProps,
							GatewayProperties.PREFIX + TruststoreProperties.DEFAULT_PREFIX, 
							GatewayProperties.PREFIX + CredentialProperties.DEFAULT_PREFIX));
			File f = new File("src/test/resources/xmls/OKFirstHello.xml");
			String r = invokeCommon(cp, f, true);
			checkResult(r, Utils.readFile(f), "<env:Header>");
		} catch (Exception e)
		{
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	public void testInsertConsignorNonSigned2()
	{
		try
		{
			ConsignorProducer cp = new ConsignorProducer(false, 30,	60, 
					new AuthnAndTrustProperties(secProps,
							GatewayProperties.PREFIX + TruststoreProperties.DEFAULT_PREFIX, 
							GatewayProperties.PREFIX + CredentialProperties.DEFAULT_PREFIX));
			File f = new File("src/test/resources/xmls/HeaderWithErrorProneNS.xml");
			String r = invokeCommon(cp, f, false);
			checkResult(r, Utils.readFile(f), "<soapenv:Header>");
		} catch (Exception e)
		{
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	public void testInsertConsignorSigned()
	{
		try
		{
			File f = new File("src/test/resources/xmls/OKFirstHello.xml");
			ConsignorProducer cp = new ConsignorProducer(true, 30, 60, 
					new AuthnAndTrustProperties(secProps,
							GatewayProperties.PREFIX + TruststoreProperties.DEFAULT_PREFIX, 
							GatewayProperties.PREFIX + CredentialProperties.DEFAULT_PREFIX));
			String r = invokeCommon(cp, f, true);
			checkResult(r, Utils.readFile(f), "<env:Header>");
		} catch (Exception e)
		{
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	public void testInsertConsignorNoSoapHeader()
	{
		try
		{
			File f = new File("src/test/resources/xmls/OKHelloNoSOAPHeader.xml");
			ConsignorProducer cp = new ConsignorProducer(true, 30, 60, 
					new AuthnAndTrustProperties(secProps,
							GatewayProperties.PREFIX + TruststoreProperties.DEFAULT_PREFIX, 
							GatewayProperties.PREFIX + CredentialProperties.DEFAULT_PREFIX));
			String r = invokeCommon(cp, f, false);
			checkResult(r, Utils.readFile(f), "<env:Envelope xmlns:env='http://schemas.xmlsoap.org/soap/envelope/'>");
		} catch (Exception e)
		{
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	public void testConsignorHTTPHeader()
	{
		try
		{
			AuthnAndTrustProperties sp = new AuthnAndTrustProperties(secProps,
					GatewayProperties.PREFIX + TruststoreProperties.DEFAULT_PREFIX, 
					GatewayProperties.PREFIX + CredentialProperties.DEFAULT_PREFIX);
			ConsignorProducer cp = new ConsignorProducer(true, 30, 60, sp);
			X509Certificate[]certChain=sp.getCredential().getCertificateChain();
			String r = cp.getConsignorHeader(certChain, "127.0.0.1").toString();
			System.out.println("Consignor header: \n"+r);
			assertTrue(r.contains(certChain[0].getSubjectX500Principal().getName()));
		} catch (Exception e)
		{
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
}
