package eu.unicore.gateway;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
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

import eu.unicore.gateway.base.RawMessageExchange;
import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.soap.Soap11;
import eu.unicore.gateway.soap.Soap12;
import eu.unicore.gateway.soap.SoapVersion;
import junit.framework.TestCase;

public class TestServer extends TestCase {
	protected Gateway gw;

	@Override
	protected void setUp() throws Exception{
		File gp = new File("src/test/resources/gateway.properties");
		File sp = new File("src/test/resources/security.properties");
		File cp = new File("src/test/resources/connection.properties");
		gw = new Gateway(gp, cp, sp);
		gw.startGateway();
	}

	protected void tearDown()throws Exception{
		Thread.sleep(2000);
		gw.stopGateway();
	}

	public void testHead() throws Exception{
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL("http://localhost:64433/DEMO-SITE"));
		HttpHead head = new HttpHead("http://localhost:64433/DEMO-SITE");
		try(ClassicHttpResponse response = hc.executeOpen(null, head, HttpClientContext.create())){
			System.out.println("HEAD got reply: " + new StatusLine(response));
		}
	}

	public void testDynamicRegistration()throws Exception{
		gw.getProperties().setProperty(GatewayProperties.KEY_REG_ENABLED, "false");
		int status=doRegister("test","http://localhost:12345");
		assertEquals(HttpStatus.SC_FORBIDDEN,status);
		gw.getProperties().setProperty(GatewayProperties.KEY_REG_ENABLED, "true");
		status=doRegister("test","http://localhost:12345");
		assertEquals(HttpStatus.SC_CREATED,status);
	}

	public void testPost()throws Exception{
		FakeServer s1=new FakeServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);

		String url="http://localhost:64433/FAKE1/test";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpPost post=new HttpPost(url);
		post.setEntity(new ByteArrayEntity(getBody(url), ContentType.TEXT_XML));
		try(ClassicHttpResponse response = hc.executeOpen(null, post, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(HttpStatus.SC_OK, response.getCode());
			assertNotNull(response.getFirstHeader("Content-type"));
			assertTrue(response.getFirstHeader("Content-type").getValue().contains("text/xml"));
		}

		int expectedCode=503;
		s1.setStatusCode(expectedCode);
		
		post=new HttpPost(url);
		post.setEntity(new ByteArrayEntity(getBody(url), ContentType.APPLICATION_SOAP_XML));
		try(ClassicHttpResponse response = hc.executeOpen(null, post, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(expectedCode, response.getCode());
		}

		s1.stop();
	}

	public void testPostSOAP11Content()throws Exception{
		FakeServer s1=new FakeServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);

		String url="http://localhost:64433/FAKE1/test";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpPost post=new HttpPost(url);
		byte[] originalRequestBody = getBody(url);
		post.setEntity(new ByteArrayEntity(originalRequestBody, ContentType.APPLICATION_XML));
		try(ClassicHttpResponse response = hc.executeOpen(null, post, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(HttpStatus.SC_OK, response.getCode());
			String forwardedRequestBody = s1.getLatestRequestBody();
			byte[] responseBody = EntityUtils.toByteArray(response.getEntity());
			assertTrue("Server response was not properly forwarded",
					Arrays.equals(s1.getAnswer(), responseBody));

			Set<String> forwardedSoapHeaders = new HashSet<>();
			String forwardedSoapBody = parseSoap(forwardedRequestBody, forwardedSoapHeaders);
			Set<String> originalSoapHeaders = new HashSet<String>();
			String originalSoapBody = parseSoap(new String(originalRequestBody,"UTF-8"), originalSoapHeaders);
			assertEquals("SOAP body was not properly forwarded", originalSoapBody, forwardedSoapBody);
			assertTrue("Some SOAP headers were not forwarded", forwardedSoapHeaders.containsAll(originalSoapHeaders));
		}

		int expectedCode=503;

		s1.setStatusCode(expectedCode);

		post=new HttpPost(url);
		originalRequestBody = getBody(url);
		post.setEntity(new ByteArrayEntity(originalRequestBody, ContentType.APPLICATION_XML));
		try(ClassicHttpResponse response = hc.executeOpen(null, post, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(expectedCode, response.getCode());
		}

		s1.stop();
	}

	public void testPostSOAP11ContentText()throws Exception{
		FakeServer s1=new FakeServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);

		String url="http://localhost:64433/FAKE1/test";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpPost post=new HttpPost(url);
		byte[] originalRequestBody = getBody(url);
		post.setEntity(new ByteArrayEntity(originalRequestBody, ContentType.APPLICATION_XML));
		try(ClassicHttpResponse response = hc.executeOpen(null, post, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(HttpStatus.SC_OK, response.getCode());

			String forwardedRequestBody = s1.getLatestRequestBody();
			byte[] responseBody = EntityUtils.toByteArray(response.getEntity());
			assertTrue("Server response was not properly forwarded",
					Arrays.equals(s1.getAnswer(), responseBody));

			Set<String> forwardedSoapHeaders = new HashSet<>();
			String forwardedSoapBody = parseSoap(forwardedRequestBody, forwardedSoapHeaders);
			Set<String> originalSoapHeaders = new HashSet<>();
			String originalSoapBody = parseSoap(new String(originalRequestBody,"UTF-8"), originalSoapHeaders);

			assertEquals("SOAP body was not properly forwarded", originalSoapBody, forwardedSoapBody);
			assertTrue("Some SOAP headers were not forwarded", forwardedSoapHeaders.containsAll(originalSoapHeaders));
		}

		int expectedCode=503;

		s1.setStatusCode(expectedCode);

		post=new HttpPost(url);
		post.setEntity(new ByteArrayEntity(getBody(url), ContentType.APPLICATION_XML));
		try(ClassicHttpResponse response = hc.executeOpen(null, post, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(expectedCode, response.getCode());
		}
		s1.stop();
	}

	public void testPostNoSOAP()throws Exception{
		FakeServer s1=new FakeServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);

		String url="http://localhost:64433/FAKE1/test";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpPost post=new HttpPost(url);
		post.setEntity(new ByteArrayEntity(getBodyNoSOAP(url), ContentType.APPLICATION_SOAP_XML));
		try(ClassicHttpResponse response = hc.executeOpen(null, post, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getCode());
		}
		s1.stop();
	}

	public void testNonSOAPPost()throws Exception{
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
			assertEquals("Request was not properly forwarded",
					new String(originalRequestBody), forwardedRequestBody);

			byte[] responseBody = EntityUtils.toByteArray(response.getEntity());
			assertTrue("Server response was not properly forwarded",
					Arrays.equals(s1.getAnswer(), responseBody));

			// check auth header was forwarded
			List<String> lastHeaders = s1.getLatestRequestHeaders();
			checkHeader("Authorization", lastHeaders);
			checkHeader(RawMessageExchange.CONSIGNOR_IP_HEADER, lastHeaders);
			checkHeader(RawMessageExchange.GATEWAY_EXTERNAL_URL+": http://localhost:64433/FAKE1", lastHeaders);
		}
		s1.stop();
	}

	private void checkHeader(String headerName, List<String> headers){
		for(String h: headers){
			if(h.startsWith(headerName))return;
		}
		assertTrue("Header "+headerName+" was not forwarded",false);
	}
	
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
			assertEquals("Client query was not properly forwarded", originalQuery, s1.getLatestQuery());
			assertTrue("Consignor IP Header not present", filteredHeadersForwarded.contains(RawMessageExchange.CONSIGNOR_IP_HEADER+": 127.0.0.1"));
			assertTrue("GW Host Header not present", 
					filteredHeadersForwarded.contains(RawMessageExchange.GATEWAY_EXTERNAL_URL+": http://localhost:64433/FAKE1"));
			assertTrue("Headers were not forwarded", filteredHeadersForwarded.contains("X-testHeader: test123"));

			assertTrue("Server response was not properly forwarded",
					Arrays.equals(s1.getAnswer(), EntityUtils.toByteArray(response.getEntity())));
		}

		int expectedCode=503;
		s1.setStatusCode(expectedCode);
		get=new HttpGet(url);
		try(ClassicHttpResponse response = hc.executeOpen(null, get, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(expectedCode, response.getCode());
			assertTrue("Server response was not properly forwarded for status code <"+status+">",
					Arrays.equals(s1.getAnswer(), EntityUtils.toByteArray(response.getEntity())));
		}
		s1.stop();
	}

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
	
	public void testShowVersion()throws Exception{
		String url="http://localhost:64433/";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpGet get=new HttpGet(url);
		try(ClassicHttpResponse response = hc.executeOpen(null, get, HttpClientContext.create())){
			assertEquals(HttpStatus.SC_OK, response.getCode());
			assert(EntityUtils.toString(response.getEntity()).contains("Version: "+Gateway.VERSION));
		}
	}

	public void testPut()throws Exception{
		FakeServer s1=new FakeServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);

		String url="http://localhost:64433/FAKE1/test";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpPut put=new HttpPut(s1Url);
		AbstractHttpEntity entity = new ByteArrayEntity(getBody(url), ContentType.WILDCARD, true);
		put.setEntity(entity);
		s1.setStatusCode(HttpStatus.SC_NO_CONTENT);
		try(ClassicHttpResponse response = hc.executeOpen(null, put, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(HttpStatus.SC_NO_CONTENT, response.getCode());
		}
		s1.stop();
	}

	public void testMultipartPost()throws Exception{
		FakeServer s1=new FakeServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);

		String url="http://localhost:64433/FAKE1/test";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpPost post=new HttpPost(url);
		byte[] originalRequestBody = getMultipartBody(url);
		post.setEntity(new ByteArrayEntity(originalRequestBody, ContentType.WILDCARD));
		post.setHeader("MIME-Version", "1.0");
		post.setHeader("Content-type", "Multipart/Related; " +
				"boundary=MIME_boundary; " +
				"type=text/xml; " +
				"start=<foo@foo.com>");

		try(ClassicHttpResponse response = hc.executeOpen(null, post, HttpClientContext.create())){
			System.out.println(getStatusDesc(response));
			assertEquals(HttpStatus.SC_OK, response.getCode());
		}
		//check that SOAP part arrived OK
		String request = s1.getLatestRequestBody();
		System.out.println(request);
		int headerStart = request.indexOf("Header");
		int assertionPosition = request.indexOf("Assertion");
		assertTrue(assertionPosition > headerStart);
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
	 * get non-SOAP body for POSTing
	 * @param to - WS Addressing To header
	 */
	private byte[] getBodyNoSOAP(String to)throws IOException{
		ByteArrayOutputStream bos=new ByteArrayOutputStream();
		OutputStreamWriter wr=new OutputStreamWriter(bos);
		wr.write("P<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"\n"+
				"xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" \n"+
				"xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
		wr.write("PPP");
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
		wr.write("{foo: bar, ham: spam}");
		wr.flush();
		return bos.toByteArray();
	}
	
	/**
	 * get a multipart message (SOAP with attachment)
	 * @param to - WS Addressing To header
	 */
	public static byte[] getMultipartBody(String to)throws IOException{
		ByteArrayOutputStream bos=new ByteArrayOutputStream();
		FileInputStream input = new FileInputStream("src/test/resources/xmls/multipart.txt");
		IOUtils.copy(input, bos);
		input.close();
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

	private static XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

	/**
	 * Parses SOAP XML, extracts all headers and inserts them into a set, and returns the SOAP body as a string
	 * @param soap XML to parse
	 * @param headers output set where headers will go
	 * @return SOAP body
	 * @throws Exception
	 */
	private static String parseSoap(String soap, Set<String> headers) throws Exception{
		XMLEventReader reader = xmlInputFactory.createXMLEventReader(new StringReader(soap));
		try{
			SoapVersion soapv = null;
			while (reader.hasNext()) {
				XMLEvent event = reader.nextEvent();
				if (event.isStartElement())
				{
					QName q = event.isStartElement() ? event.asStartElement().getName() :
						event.asEndElement().getName();
					if (Soap11.envelope.equals(q)) {
						soapv = Soap11.getInstance();
					} else if (Soap12.envelope.equals(q)) {
						soapv = Soap12.getInstance();
					} else {
						throw new Exception("(Supported) SOAP Envelope element is not the first element");
					}
					break;
				}
			}
			while(reader.hasNext()){
				XMLEvent event = reader.nextEvent();
				boolean isInHeader = false;
				if (event.isStartElement()){
					QName q = event.isStartElement() ? event.asStartElement().getName() :
						event.asEndElement().getName();
					isInHeader = soapv.getHeader().equals(q);
					int lvl = 0;
					int pointer = reader.peek().getLocation().getCharacterOffset();
					while(reader.hasNext()){
						XMLEvent event2 = reader.nextEvent();
						if (event2.isStartElement()){
							lvl++;
						}
						if (event2.isEndElement()){
							lvl--;
						}
						if(lvl<0) {
							if(!isInHeader){
								int np = event2.getLocation().getCharacterOffset();
								return soap.substring(pointer, np);
							}
							break;
						}
						if(lvl==0 && isInHeader){
							int np = reader.peek().getLocation().getCharacterOffset();
							String header=soap.substring(pointer, np);
							header = header.replaceAll(">\\s+", ">");
							header = header.replaceAll("\\s+<", "<");
							header = header.trim();
							if(!header.isEmpty()) headers.add(header);
							pointer=np;
						}
					}
				}
			}
		} finally {
			reader.close();
		}
		return "";
	}

	private static String getStatusDesc(HttpResponse response)
	{
		return response.getCode() + " " + response.getReasonPhrase();
	}
}
