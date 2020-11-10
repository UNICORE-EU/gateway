/*
 * Copyright (c) 2012 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package eu.unicore.gateway;

import java.io.File;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;

import junit.framework.TestCase;

public class TestHttpsServer extends TestCase {
	protected Gateway gw;
	protected FakeHttpsServer backend;
	
	@Override
	protected void setUp() throws Exception {
		File gp = new File("src/test/resources/gateway-ssl.properties");
		File sp = new File("src/test/resources/security.properties");
		File cp = new File("src/test/resources/connection.properties");
		gw = new Gateway(gp, cp, sp);
		gw.startGateway();
		backend = FakeHttpsServer.getInstance();
		backend.start();
	}
	
	@Override
	protected void tearDown()throws Exception{
		Thread.sleep(1000);
		backend.stop();
		gw.stopGateway();
	}

	public void testWithSSL() {
		String url="https://localhost:64433/SSL-SITE/service";
		try{
			HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
			HttpPost post= gw.getClientFactory().makePostMethod(url, 
					new ByteArrayEntity(TestServer.getBody(url)));

			HttpResponse response = hc.execute(post);
			System.out.println(response.getStatusLine());
			String resp = EntityUtils.toString(response.getEntity());
			System.out.println(resp);
			int status=response.getStatusLine().getStatusCode();
			assertEquals(HttpStatus.SC_OK, status);
			assertFalse(resp.contains("Fault"));
			assertTrue(resp.contains("OKOKOK"));
		} catch(Exception ex){
			ex.printStackTrace();
			fail();
		}
	}
	

	public void testGetWithSignedAssertionForwarding() {
		String url="https://localhost:64433/SSL-SITE/service";
		try{
			HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
			HttpGet get= new HttpGet(url);
			HttpResponse response = hc.execute(get);
			System.out.println(response.getStatusLine());
			String resp = EntityUtils.toString(response.getEntity());
			System.out.println(resp);
			int status=response.getStatusLine().getStatusCode();
			assertEquals(HttpStatus.SC_OK, status);
			assertFalse(resp.contains("Fault"));
			System.out.println(resp);
			assertTrue(resp.contains("Gateway"));
		} catch(Exception ex){
			ex.printStackTrace();
			fail();
		}
	}
}
