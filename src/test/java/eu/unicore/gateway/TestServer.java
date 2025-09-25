package eu.unicore.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.StatusLine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import eu.unicore.gateway.base.Servlet;
import eu.unicore.gateway.properties.GatewayProperties;

public class TestServer {
	
	private static Gateway gw;

	@BeforeAll
	public static void setUp() throws Exception{
		File gp = new File("src/test/resources/gateway.properties");
		File sp = new File("src/test/resources/security.properties");
		File cp = new File("src/test/resources/connection.properties");
		gw = new Gateway(gp, cp, sp);
		gw.startGateway();
	}

	@AfterAll
	public static void tearDown()throws Exception{
		Thread.sleep(2000);
		gw.stopGateway();
	}

	@Test
	public void testHead() throws Exception{
		FakeServer s1 = new FakeServer();
		s1.start();
		String s1Url = s1.getURI();
		int status=doRegister("FAKE1", s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);
		String url = "http://localhost:64433/FAKE1";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpHead head = new HttpHead(url);
		try(ClassicHttpResponse response = hc.executeOpen(null, head, HttpClientContext.create())){
			System.out.println("HEAD got reply: " + new StatusLine(response));
		}
		s1.stop();
	}

	@Test
	public void testOptions() throws Exception{
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL("http://localhost:64433/DEMO-SITE"));
		HttpOptions opts = new HttpOptions("http://localhost:64433/DEMO-SITE");
		try(ClassicHttpResponse response = hc.executeOpen(null, opts, HttpClientContext.create())){
			System.out.println("OPTIONS got reply: " + new StatusLine(response));
		}
	}

	@Test
	public void testDynamicRegistration()throws Exception{
		gw.getProperties().setProperty(GatewayProperties.KEY_REG_ENABLED, "false");
		int status=doRegister("test","http://localhost:12345");
		assertEquals(HttpStatus.SC_FORBIDDEN,status);
		gw.getProperties().setProperty(GatewayProperties.KEY_REG_ENABLED, "true");
		status=doRegister("test","http://localhost:12345");
		assertEquals(HttpStatus.SC_CREATED,status);
	}

	@Test
	public void testPost()throws Exception{
		FakeServer s1=new FakeServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);

		String url="http://localhost:64433/FAKE1/test";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpPost post=new HttpPost(url);
		byte[] originalRequestBody = getNonSOAPPostBody();
		post.setEntity(new ByteArrayEntity(originalRequestBody, ContentType.APPLICATION_JSON));
		String userName = "demouser";
		String password = "test123";
		
		post.addHeader(Utils.getBasicAuth(userName, password));

		try(ClassicHttpResponse response = hc.executeOpen(null, post, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(HttpStatus.SC_OK, response.getCode());

			String forwardedRequestBody = s1.getLatestRequestBody();
			System.out.println(forwardedRequestBody);
			assertEquals(new String(originalRequestBody), forwardedRequestBody);

			byte[] responseBody = EntityUtils.toByteArray(response.getEntity());
			assertTrue(Arrays.equals(s1.getAnswer(), responseBody));

			// check auth header was forwarded
			List<String> lastHeaders = s1.getLatestRequestHeaders();
			checkHeader("Authorization", lastHeaders);
			checkHeader(Servlet.CONSIGNOR_IP_HEADER, lastHeaders);
			checkHeader(Servlet.GATEWAY_EXTERNAL_URL+": http://localhost:64433/FAKE1", lastHeaders);
		}
		s1.stop();
	}

	private void checkHeader(String headerName, List<String> headers){
		for(String h: headers){
			if(h.startsWith(headerName))return;
		}
		assertTrue(false, "Header "+headerName+" was not forwarded");
	}
	
	@Test
	public void testGet()throws Exception{
		FakeServer s1=new FakeServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);

		String queryPath = "/test";
		String url="http://localhost:64433/FAKE1"+queryPath;
		String originalQuery = "GET "+queryPath+" HTTP/1.1";

		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpGet get=new HttpGet(url);
		get.addHeader("X-testHeader", "test123");
		try(ClassicHttpResponse response = hc.executeOpen(null, get, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(HttpStatus.SC_OK, response.getCode());
			List<String> filteredHeadersForwarded=new ArrayList<>();
			for(String h: s1.getLatestRequestHeaders()){
				String string=h.trim();
				if(!string.startsWith("Host:")){
					filteredHeadersForwarded.add(string);
				}
			}
			assertEquals(originalQuery, s1.getLatestQuery());
			assertTrue(filteredHeadersForwarded.contains(Servlet.CONSIGNOR_IP_HEADER+": 127.0.0.1"));
			assertTrue(filteredHeadersForwarded.contains(Servlet.GATEWAY_EXTERNAL_URL+": http://localhost:64433/FAKE1"));
			assertTrue(filteredHeadersForwarded.contains("X-testHeader: test123"));
			assertTrue(Arrays.equals(s1.getAnswer(), EntityUtils.toByteArray(response.getEntity())));
		}

		int expectedCode=503;
		s1.setStatusCode(expectedCode);
		get=new HttpGet(url);
		try(ClassicHttpResponse response = hc.executeOpen(null, get, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(expectedCode, response.getCode());
			assertTrue(Arrays.equals(s1.getAnswer(), EntityUtils.toByteArray(response.getEntity())));
		}
		s1.stop();
	}

	@Test
	public void testGetWrongAddress()throws Exception{
		String queryPath = "/test";
		String url="http://localhost:64433/DOESNOTEXIST"+queryPath;

		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpGet get=new HttpGet(url);
		try(ClassicHttpResponse response = hc.executeOpen(null, get, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(404, response.getCode());
		}
	}

	@Test
	public void testGetPathWithSpaces()throws Exception{
		FakeServer s1=new FakeServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);
		
		String queryPath = "/test%20file";
		String url="http://localhost:64433/FAKE1"+queryPath;

		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpGet get=new HttpGet(url);

		try(ClassicHttpResponse response = hc.executeOpen(null, get, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(200, response.getCode());
		}
		s1.stop();
	}
	
	@Test
	public void testGetPathWithQuery()throws Exception{
		FakeServer s1=new FakeServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);
		
		String queryPath = "/test?xyz=foo";
		String url="http://localhost:64433/FAKE1"+queryPath;

		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpGet get=new HttpGet(url);

		try(ClassicHttpResponse response = hc.executeOpen(null, get, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(200, response.getCode());
			assertTrue(s1.getLatestQuery().contains("?xyz=foo"));
		}
		s1.stop();
	}
	
	@Test
	public void testGetWithVSiteOffline()throws Exception{
		int status=doRegister("FAKE1","http://some_offline_site");
		assertEquals(HttpStatus.SC_CREATED,status);
	
		String url="http://localhost:64433/FAKE1/rest/core";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpGet get=new HttpGet(url);

		try(ClassicHttpResponse response = hc.executeOpen(null, get, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			String errorBody = EntityUtils.toString(response.getEntity());
			System.out.println(errorBody);
			assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, response.getCode());
		}
	}
	
	@Test
	public void testShowVersion()throws Exception{
		String url="http://localhost:64433/";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpGet get=new HttpGet(url);
		try(ClassicHttpResponse response = hc.executeOpen(null, get, HttpClientContext.create())){
			assertEquals(HttpStatus.SC_OK, response.getCode());
			String res = EntityUtils.toString(response.getEntity());
			assert res.contains("Version: "+Gateway.VERSION);
		}
	}

	@Test
	public void testPut()throws Exception{
		FakeServer s1=new FakeServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);

		String url="http://localhost:64433/FAKE1/test";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpPut put=new HttpPut(url);
		AbstractHttpEntity entity = new ByteArrayEntity(getBody(url), ContentType.WILDCARD, true);
		put.setEntity(entity);
		s1.setStatusCode(HttpStatus.SC_NO_CONTENT);
		try(ClassicHttpResponse response = hc.executeOpen(null, put, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(HttpStatus.SC_NO_CONTENT, response.getCode());
		}
		s1.stop();
	}

	@Test
	public void testPut2()throws Exception{
		FakeServer s1=new FakeServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);

		String url="http://localhost:64433/FAKE1/test";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpPut put=new HttpPut(url);
		AbstractHttpEntity entity = new ByteArrayEntity(getBody(url), ContentType.APPLICATION_JSON, true);
		put.setEntity(entity);
		s1.setStatusCode(HttpStatus.SC_NO_CONTENT);
		try(ClassicHttpResponse response = hc.executeOpen(null, put, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(HttpStatus.SC_NO_CONTENT, response.getCode());
		}
		s1.stop();
	}
	
	/**
	 * get a minimalistic SOAP message
	 * @param to - WS Addressing To header
	 */
	public static byte[] getBody(String to)throws IOException{
		ByteArrayOutputStream bos=new ByteArrayOutputStream();
		OutputStreamWriter wr=new OutputStreamWriter(bos);

		wr.write("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"\n"+
				"xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" \n"+
				"xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"+
				"<soap:Header>");
		wr.write("<wsa:To xmlns:wsa=\"http://www.w3.org/2005/08/addressing\">");
		wr.write(to);
		wr.write("</wsa:To>\n");
		wr.write("</soap:Header>");
		wr.write("<soap:Body>test</soap:Body>");
		wr.write("</soap:Envelope>");
		wr.flush();
		return bos.toByteArray();
	}

	/**
	 * get a small non-SOAP message
	 * @param to - WS Addressing To header
	 */
	public static byte[] getNonSOAPPostBody()throws IOException{
		ByteArrayOutputStream bos=new ByteArrayOutputStream();
		OutputStreamWriter wr=new OutputStreamWriter(bos);
		wr.write("{foo: bar, ham: spam}\n");
		wr.flush();
		return bos.toByteArray();
	}

	private int doRegister(String name, String address)throws Exception{
		String url="http://localhost:64433/VSITE_REGISTRATION_REQUEST";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpPost post=new HttpPost(url);
		List<NameValuePair> parameters = new ArrayList<>();
		parameters.add(new BasicNameValuePair("name", name));
		parameters.add(new BasicNameValuePair("address", address));
		parameters.add(new BasicNameValuePair("secret", "super-secret-password"));
		UrlEncodedFormEntity postEntity = new UrlEncodedFormEntity(parameters);
		post.setEntity(postEntity);
		try(ClassicHttpResponse response = hc.executeOpen(null, post, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			return response.getCode();
		}
	}

	private static String getStatusDesc(HttpResponse response)
	{
		return response.getCode() + " " + response.getReasonPhrase();
	}
}
