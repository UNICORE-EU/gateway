package eu.unicore.gateway;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;

import org.apache.log4j.Logger;

import eu.unicore.gateway.base.RawMessageExchange;
import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.util.StopWatch;
import eu.unicore.security.canl.AuthnAndTrustProperties;
import eu.unicore.security.canl.CredentialProperties;
import eu.unicore.security.canl.TruststoreProperties;
import junit.framework.TestCase;

public class TestPOSTProcessingPerf extends TestCase
{
	private static final Logger log = Logger.getLogger(TestPOSTProcessingPerf.class.getName());
	private File secProps = new File("src/test/resources/security.properties");
	private static final int ITERATIONS = 10000;
		
	
	/**
	 * 
	 * @param cp 
	 * @param fr provides a request
	 * @param os processed request is wrote here (aka the service)
	 * @param is provides a response
	 * @param w processed response is wrote here (aka the client)
	 */
	public void invokeCommon(ConsignorProducer cp, Reader fr, 
		OutputStream os, InputStream is, Writer w, StopWatch watch)
	{
		try
		{
			watch.start();
			RawMessageExchange mex = new RawMessageExchange(fr, w, 
					GatewayProperties.DEFAULT_MAX_HDR);
			watch.snapshot();
			new HeadersParser("http://localhost").parseHeaders(mex);
			watch.snapshot();
			POSTHandler.writeToOutputStream(mex, os, null, "", cp, log);
			watch.snapshot();
			POSTHandler.forwardResponse(is, Charset.forName("utf8"), mex, log);
			watch.snapshot();
		} catch (Exception e)
		{
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	public void testPerf()
	{
		try
		{
			AuthnAndTrustProperties sec = new AuthnAndTrustProperties(secProps,
					GatewayProperties.PREFIX + TruststoreProperties.DEFAULT_PREFIX, 
					GatewayProperties.PREFIX + CredentialProperties.DEFAULT_PREFIX);
			ConsignorProducer cp = new ConsignorProducer(false, 30,	60, sec);
			File f1 = new File("src/test/resources/xmls/grpRequest.xml");
			File f2 = new File("src/test/resources/xmls/grpResponse.xml");
			String response = Utils.readFile(f2);
			ByteArrayInputStream bais = new ByteArrayInputStream(response.getBytes());
			
			FileReader fr = new FileReader(f1);
			DiscardWriter w = new DiscardWriter();
			DiscardOutputStream baos = new DiscardOutputStream();

			for (int i=1; i<5000; i++)
			{
				StopWatch sw = new StopWatch();
				invokeCommon(cp, fr, baos, bais, w, sw);
				bais.reset();
				fr.close();
				fr = new FileReader(f1);
			}
			
			long time = System.currentTimeMillis();
			for (int i=1; i<ITERATIONS+1; i++)
			{
				long s1 = System.nanoTime();
				StopWatch sw = new StopWatch();
				invokeCommon(cp, fr, baos, bais, w, sw);
				bais.reset();
				fr.close();
				fr = new FileReader(f1);
				
				if (((i % 1000) == 0) || sw.getTotalTime() > 10000000)
				{
					long s2 = System.nanoTime() - s1;
					long t = System.currentTimeMillis();
					System.out.println("Average speed: " + (i*1000.0/(t-time)) + " exchanges/s\n" +
						"last iteration: " + sw.getTotalTime()/1000.0 + " " + s2/1000.0 + "\n"
						+ sw);
					
					//System.out.println("Memory: " + (
					//	rt.totalMemory() - rt.freeMemory())/1024);
				}
			}
			fr.close();
			time = System.currentTimeMillis() - time;
			System.out.println("Avarage speed: " + (ITERATIONS*1000.0/time) + " exchanges/s");
		} catch (Exception e)
		{
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	private static class DiscardWriter extends Writer
	{
		@Override
		public void close() throws IOException {}
		@Override
		public void flush() throws IOException {}
		@Override
		public void write(char[] cbuf, int off, int len) throws IOException{}
	}
	
	private static class DiscardOutputStream extends OutputStream
	{
		public void write(int b) throws IOException
		{
		}
	}
}
