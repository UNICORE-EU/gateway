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
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import eu.unicore.bugsreporter.annotation.FunctionalTest;
import eu.unicore.bugsreporter.annotation.RegressionTest;
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

	public void testHead()throws Exception{
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL("http://localhost:64433/DEMO-SITE"));
		HttpHead head = new HttpHead("http://localhost:64433/DEMO-SITE");
		HttpResponse resp=hc.execute(head);
		System.out.println("HEAD got reply: "+resp.getStatusLine());
	}


	@FunctionalTest(id="gw_dynReg", description="Tests whether dynamic registration is possible and if can be disabled.")
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
		post.setEntity(new ByteArrayEntity(getBody(url)));
		try{
			HttpResponse response = hc.execute(post);
			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(HttpStatus.SC_OK, status);
			assertNotNull(response.getFirstHeader("Content-type"));
			assertTrue(response.getFirstHeader("Content-type").getValue().contains("text/xml"));
			post.reset();
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}

		int expectedCode=503;
		s1.setStatusCode(expectedCode);
		
		post=new HttpPost(url);
		post.setEntity(new ByteArrayEntity(getBody(url)));
		try{
			HttpResponse response = hc.execute(post);
			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(expectedCode, status);
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}


		s1.stop();
	}

	@FunctionalTest(id="gw_fwPostReq", 
			description="Tests whether the gateway properly forwards POST/SOAP requests to the backend site; "
					+ "requests are of application/xml mimetype.")
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
		post.setEntity(new ByteArrayEntity(originalRequestBody));
		post.setHeader("Content-type", "application/xml; charset=UTF-8");

		try{
			HttpResponse response = hc.execute(post);
			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(HttpStatus.SC_OK, status);
			assertTrue(post.getFirstHeader("Content-type").getValue().contains("application/xml"));

			String forwardedRequestBody = s1.getLatestRequestBody();
			byte[] responseBody = EntityUtils.toByteArray(response.getEntity());
			assertTrue("Server response was not properly forwarded",
					Arrays.equals(s1.getAnswer(), responseBody));

			Set<String> forwardedSoapHeaders = new HashSet<String>();
			String forwardedSoapBody = parseSoap(forwardedRequestBody, forwardedSoapHeaders);
			Set<String> originalSoapHeaders = new HashSet<String>();
			String originalSoapBody = parseSoap(new String(originalRequestBody,"ISO-8859-1"), originalSoapHeaders);

			assertEquals("SOAP body was not properly forwarded", originalSoapBody, forwardedSoapBody);
			assertTrue("Some SOAP headers were not forwarded", forwardedSoapHeaders.containsAll(originalSoapHeaders));
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}

		int expectedCode=503;

		s1.setStatusCode(expectedCode);

		post=new HttpPost(url);
		originalRequestBody = getBody(url);
		post.setEntity(new ByteArrayEntity(originalRequestBody));
		try{
			HttpResponse response = hc.execute(post);
			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(expectedCode, status);
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}


		s1.stop();
	}

	@FunctionalTest(id="gw_fwPostReq", 
			description="Tests whether the gateway properly forwards POST/SOAP requests to the backend site; "
					+ "requests are of text/xml mimetype.")
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
		post.setEntity(new ByteArrayEntity(originalRequestBody));
		post.setHeader("Content-type", "text/xml; charset=UTF-8");

		try{
			HttpResponse response = hc.execute(post);
			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(HttpStatus.SC_OK, status);
			assertTrue(post.getFirstHeader("Content-type").getValue().contains("text/xml"));

			String forwardedRequestBody = s1.getLatestRequestBody();
			byte[] responseBody = EntityUtils.toByteArray(response.getEntity());
			assertTrue("Server response was not properly forwarded",
					Arrays.equals(s1.getAnswer(), responseBody));

			Set<String> forwardedSoapHeaders = new HashSet<String>();
			String forwardedSoapBody = parseSoap(forwardedRequestBody, forwardedSoapHeaders);
			Set<String> originalSoapHeaders = new HashSet<String>();
			String originalSoapBody = parseSoap(new String(originalRequestBody,"ISO-8859-1"), originalSoapHeaders);

			assertEquals("SOAP body was not properly forwarded", originalSoapBody, forwardedSoapBody);
			assertTrue("Some SOAP headers were not forwarded", forwardedSoapHeaders.containsAll(originalSoapHeaders));
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}

		int expectedCode=503;

		s1.setStatusCode(expectedCode);

		post=new HttpPost(url);
		post.setEntity(new ByteArrayEntity(getBody(url)));
		try{
			HttpResponse response = hc.execute(post);
			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(expectedCode, status);
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
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
		post.setEntity(new ByteArrayEntity(getBodyNoSOAP(url)));
		try{
			HttpResponse response = hc.execute(post);
			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, status);
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}
		s1.stop();
	}

	@FunctionalTest(id="gw_fwPostNonSOAPReq", 
			description="Tests whether the gateway properly forwards non-SOAP POST requests " +
			"to the backend site; requests are of text/json mimetype.")
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
		post.setEntity(new ByteArrayEntity(originalRequestBody));
		post.setHeader("Content-type", "application/json; charset=UTF-8");
		String userName = "demouser";
		String password = "test123";
		
		post.addHeader(Utils.getBasicAuth(userName, password));

		try{
			HttpResponse response = hc.execute(post);
			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(HttpStatus.SC_OK, status);
			assertTrue(post.getFirstHeader("Content-type").getValue().contains("application/json"));

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
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}

		s1.stop();
	}

	private void checkHeader(String headerName, List<String> headers){
		for(String h: headers){
			if(h.startsWith(headerName))return;
		}
		assertTrue("Header "+headerName+" was not forwarded",false);
	}
	
	@FunctionalTest(id="gw_fwGetReq", description="Tests whether the gateway properly forwards GET requests to the backend site.")
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

		try{
			HttpResponse response = hc.execute(get);
			List<String> filteredHeadersForwarded=new ArrayList<String>();
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

			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(HttpStatus.SC_OK, status);
			assertTrue("Server response was not properly forwarded",
					Arrays.equals(s1.getAnswer(), EntityUtils.toByteArray(response.getEntity())));
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}

		int expectedCode=503;
		s1.setStatusCode(expectedCode);
		get=new HttpGet(url);
		try{
			HttpResponse response = hc.execute(get);
			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(expectedCode, status);
			assertTrue("Server response was not properly forwarded for status code <"+status+">",
					Arrays.equals(s1.getAnswer(), EntityUtils.toByteArray(response.getEntity())));
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}
		s1.stop();
	}

	@RegressionTest(id=3314648, url="http://sourceforge.net/tracker/?func=detail&aid=3314648&group_id=102081&atid=633902")
	public void testGetWrongAddress()throws Exception{

		String queryPath = "/test";
		String url="http://localhost:64433/DOESNOTEXIST"+queryPath;

		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpGet get=new HttpGet(url);

		try{
			HttpResponse response = hc.execute(get);
			System.out.println(getStatusDesc(response));
			int status=response.getStatusLine().getStatusCode();
			assertEquals(404, status);
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}
	}

	
	@RegressionTest(id=790, url="http://sourceforge.net/p/unicore/bugs/790/")
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

		try{
			HttpResponse response = hc.execute(get);
			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(200, status);
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}
		finally{
			s1.stop();
		}
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

		try{
			HttpResponse response = hc.execute(get);
			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(200, status);
			assertTrue(s1.getLatestQuery().contains("?xyz=foo"));
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}
		finally{
			s1.stop();
		}
	}
	
	@FunctionalTest(id="gw_showVersion", description="Tests whether the gateway shows its version on the default page.")
	public void testShowVersion()throws Exception{
		String url="http://localhost:64433/";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpGet get=new HttpGet(url);
		try{
			HttpResponse response = hc.execute(get);
			System.out.println(getStatusDesc(response));
			int status=response.getStatusLine().getStatusCode();
			assertEquals(HttpStatus.SC_OK, status);
			assert(EntityUtils.toString(response.getEntity()).contains("Version: "+Gateway.VERSION));
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}
	}

	@FunctionalTest(id="gw_fwPutReq", description="Tests whether the gateway properly forwards PUT requests to the backend site.")
	public void testPut()throws Exception{
		FakeServer s1=new FakeServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);

		String url="http://localhost:64433/FAKE1/test";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpPut put=new HttpPut(s1Url);
		AbstractHttpEntity entity=new ByteArrayEntity(getBody(url));
		entity.setChunked(true); // 'false' does not work?!
		put.setEntity(entity);
		try{
			s1.setStatusCode(HttpStatus.SC_NO_CONTENT);
			HttpResponse response = hc.execute(put);
			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(HttpStatus.SC_NO_CONTENT, status);
			s1.stop();
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
		}

	}

	@FunctionalTest(id="gw_fwPostMultipartReq", 
			description="Tests whether the gateway properly forwards multipart POST requests " +
			"to the backend site.")
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
		post.setEntity(new ByteArrayEntity(originalRequestBody));
		post.setHeader("MIME-Version", "1.0");
		post.setHeader("Content-type", "Multipart/Related; " +
				"boundary=MIME_boundary; " +
				"type=text/xml; " +
				"start=<foo@foo.com>");

		try{
			HttpResponse response = hc.execute(post);
			System.out.println(getStatusDesc(response));
			status=response.getStatusLine().getStatusCode();
			assertEquals(HttpStatus.SC_OK, status);
		}
		catch(Exception ex){
			ex.printStackTrace();
			fail();
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
		try{
			List<NameValuePair> parameters = new ArrayList<NameValuePair>();
			parameters.add(new BasicNameValuePair("name", name));
			parameters.add(new BasicNameValuePair("address", address));
			parameters.add(new BasicNameValuePair("secret", "super-secret-password"));
			
			UrlEncodedFormEntity postEntity = new UrlEncodedFormEntity(parameters);
			post.setEntity(postEntity);
			System.out.println("Time before starting: " + System.currentTimeMillis());
			HttpResponse response = hc.execute(post);
			System.out.println(getStatusDesc(response));
			return response.getStatusLine().getStatusCode();
		}
		finally{
			System.out.println("Time after call: " + System.currentTimeMillis());
			post.releaseConnection();
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
		StatusLine sl = response.getStatusLine();
		return sl.getStatusCode() + " " + sl.getReasonPhrase();
	}
}
