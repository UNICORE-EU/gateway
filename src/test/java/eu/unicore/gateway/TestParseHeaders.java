package eu.unicore.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.StringWriter;
import java.nio.charset.Charset;

import org.apache.logging.log4j.Logger;
import org.junit.Test;

import eu.unicore.gateway.base.RawMessageExchange;
import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.util.LogUtil;

public class TestParseHeaders {

	public static final Logger log = LogUtil.getLogger("gateway", TestParseHeaders.class);
	private File f1 = new File("src/test/resources/xmls/OKFirstHello.xml");
	private File f2 = new File("src/test/resources/xmls/OKFirstHelloWithDefaultNamespace.xml");
	private File f3 = new File("src/test/resources/xmls/OKFirstHelloWithTwoDefaultNamespaces.xml");

	private void gwParse(File f)
	{
		try{
			StringWriter w=new StringWriter();
			RawMessageExchange mex = new RawMessageExchange(new FileReader(f), w,
					GatewayProperties.DEFAULT_MAX_HDR);
			HeadersParser hp = new HeadersParser("http://localhost");
			hp.parseHeaders(mex);
			assertEquals("http://localhost:8080/s1/services/hello",mex.getWsaToAddress());
			assertEquals("http://hello.com/test",mex.getWsaAction());

			ByteArrayOutputStream bos=new ByteArrayOutputStream();
			POSTHandler.writeToOutputStream(mex, bos, null, "", new FakeConsignorProducer(), log);
			//System.out.println(bos);

		}catch(Exception e){
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void test1()
	{
		gwParse(f1);
	}

	@Test
	public void test2()
	{
		gwParse(f2);
	}

	@Test
	public void test3()
	{
		gwParse(f3);
	}

	@Test
	public void testResponseForwarding() throws Exception
	{
		File f = new File("src/test/resources/xmls/grpRequest.xml");
		File f2 = new File("src/test/resources/xmls/grpResponse.xml");
		String resp = Utils.readFile(f2);
		StringWriter w = new StringWriter();

		RawMessageExchange mex = new RawMessageExchange(new FileReader(f), w, 
				GatewayProperties.DEFAULT_MAX_HDR);

		ByteArrayInputStream bais = new ByteArrayInputStream(resp.getBytes());
		POSTHandler.forwardResponse(bais, Charset.forName("utf8"), mex, log);

		assertTrue("Forwarded response is spoiled", resp.equals(w.toString()));
	}
}

