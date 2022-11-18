/*
 * Copyright (c) 2012 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package eu.unicore.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestHttpsServer {
	protected Gateway gw;
	protected FakeHttpsServer backend;
	
	@Before
	public void setUp() throws Exception {
		File gp = new File("src/test/resources/gateway-ssl.properties");
		File sp = new File("src/test/resources/security.properties");
		File cp = new File("src/test/resources/connection.properties");
		gw = new Gateway(gp, cp, sp);
		gw.startGateway();
		backend = FakeHttpsServer.getInstance();
		backend.start();
	}
	
	@After
	public void tearDown()throws Exception{
		Thread.sleep(1000);
		backend.stop();
		gw.stopGateway();
	}

	@Test
	public void testWithSSL() throws Exception {
		String url="https://localhost:64433/SSL-SITE/service";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpPost post= gw.getClientFactory().makePostMethod(url, 
				new ByteArrayEntity(TestServer.getBody(url), ContentType.APPLICATION_SOAP_XML));
		try(ClassicHttpResponse response = hc.executeOpen(null, post, HttpClientContext.create())){
			System.out.println(new StatusLine(response));
			String resp = EntityUtils.toString(response.getEntity());
			System.out.println(resp);
			int status=response.getCode();
			assertEquals(HttpStatus.SC_OK, status);
			assertFalse(resp.contains("Fault"));
			assertTrue(resp.contains("OKOKOK"));
		}
	}
	
	@Test
	public void testGetWithSignedAssertionForwarding() throws Exception {
		String url="https://localhost:64433/SSL-SITE/service";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpGet get= new HttpGet(url);
		try(ClassicHttpResponse response = hc.executeOpen(null, get, HttpClientContext.create())){
			System.out.println(new StatusLine(response));
			String resp = EntityUtils.toString(response.getEntity());
			System.out.println(resp);
			int status=response.getCode();
			assertEquals(HttpStatus.SC_OK, status);
			assertFalse(resp.contains("Fault"));
			System.out.println(resp);
			assertTrue(resp.contains("Gateway"));
		}
	}
}
