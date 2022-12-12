package eu.unicore.gateway.forwarding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.StatusLine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import eu.unicore.gateway.Gateway;
import eu.unicore.util.ChannelUtils;

/**
 * Port forwarding with plain HTTP gateway and backend
 */
public class TestForwarding {

	protected Gateway gw;

	protected EchoEndpointServer echo;
	
	protected String getScheme() {
		return "http";
	}

	protected EchoEndpointServer createBackend() throws IOException {
		return new EchoEndpointServer();
	}

	protected String getPropertiesLoc() {
		return "src/test/resources/gateway.properties";
	}

	@Before
	public void setUp() throws Exception{
		File gp = new File(getPropertiesLoc());
		File sp = new File("src/test/resources/security.properties");
		File cp = new File("src/test/resources/connection.properties");
		gw = new Gateway(gp, cp, sp);
		gw.startGateway();
		echo = createBackend();
		echo.start();
	}

	@After
	public void tearDown()throws Exception{
		gw.stopGateway();
	}
	
	@Test
	public void testPortForwarding() throws Exception{
		echo.setStatusCode(101);
		String s1Url = echo.getURI();
		int status=doRegister("TEST",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);
		ForwardingSetup fs = new ForwardingSetup(gw);
		URI u = new URI(getScheme()+"://localhost:64433/TEST/test?port=1234");
		final HttpGet req = new HttpGet(u.toString());
		req.addHeader("Connection", "Upgrade");
		req.addHeader("Upgrade", ForwardingSetup.REQ_UPGRADE_HEADER_VALUE);
		SocketChannel remote = fs.openSocketChannel(u);
		fs.doHandshake(remote, u, req.getHeaders());
		System.out.println("Got socket connected to: "+ remote.getRemoteAddress() +
				" local address: " + remote.getLocalAddress());
		assertTrue(echo.getLatestQuery().contains("?port=1234"));
		PrintWriter w = new PrintWriter(new OutputStreamWriter(ChannelUtils.newOutputStream(remote, 65536)), true);
		Reader r = new InputStreamReader(ChannelUtils.newInputStream(remote, 65536));
		BufferedReader br = new BufferedReader(r);
		for(int i=0; i<10; i++) {
			String out = "test_"+i;
			w.println(out);
			System.out.println("---> "+out);
			String line = br.readLine();
			System.out.println("<--- "+line);
			assertEquals(out, line);
		}
	}

	/**
	 * test that the 'Protocol: upgrade' mechanism is not triggered when
	 * the server replies with 432 (session invalid)
	 * @throws Exception
	 */
	@Test
	public void testPortForwarding432Response() throws Exception{
		echo.setStatusCode(432);
		String s1Url = echo.getURI();
		int status=doRegister("TEST",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);
		URL u = new URL(getScheme()+"://localhost:64433/TEST/test?port=1234");
		HttpClient hc = gw.getClientFactory().makeHttpClient(u);
		final HttpGet req = new HttpGet(u.toString());
		req.addHeader("Connection", "Upgrade");
		req.addHeader("Upgrade", ForwardingSetup.REQ_UPGRADE_HEADER_VALUE);
		try(ClassicHttpResponse response = hc.executeOpen(null, req, HttpClientContext.create())){
			System.out.println("---> "+new StatusLine(response));
			assertEquals(432, response.getCode());
		};
		echo.stop();
	}
	
	private int doRegister(String name, String address)throws Exception{
		String url = getScheme()+"://localhost:64433/VSITE_REGISTRATION_REQUEST";
		HttpClient hc = gw.getClientFactory().makeHttpClient(new URL(url));
		HttpPost post=new HttpPost(url);
		List<NameValuePair> parameters = new ArrayList<>();
		parameters.add(new BasicNameValuePair("name", name));
		parameters.add(new BasicNameValuePair("address", address));
		parameters.add(new BasicNameValuePair("secret", "super-secret-password"));
		UrlEncodedFormEntity postEntity = new UrlEncodedFormEntity(parameters);
		post.setEntity(postEntity);
		try(ClassicHttpResponse response = hc.executeOpen(null, post, HttpClientContext.create())){
			return response.getCode();
		}
	}

}
