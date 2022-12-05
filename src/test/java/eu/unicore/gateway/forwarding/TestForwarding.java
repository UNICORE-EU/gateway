package eu.unicore.gateway.forwarding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import eu.unicore.gateway.Gateway;

public class TestForwarding {

	protected Gateway gw;

	@Before
	public void setUp() throws Exception{
		File gp = new File("src/test/resources/gateway.properties");
		File sp = new File("src/test/resources/security.properties");
		File cp = new File("src/test/resources/connection.properties");
		gw = new Gateway(gp, cp, sp);
		gw.startGateway();
	}

	@After
	public void tearDown()throws Exception{
		gw.stopGateway();
	}

	@Test
	public void testGetWithUpgrade() throws Exception{
		EchoEndpointServer s1 = new EchoEndpointServer();
		s1.start();
		String s1Url=s1.getURI();
		int status=doRegister("TEST",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);
		ForwardingSetup fs = new ForwardingSetup(gw);
		URI u = new URI("http://localhost:64433/TEST/test");
		final HttpGet req = new HttpGet(u.toString());
		req.addHeader("Connection", "Upgrade");
		req.addHeader("Upgrade", ForwardingSetup.REQ_UPGRADE_HEADER_VALUE);
		Socket remoteSocket = fs.openSocket(u);
		fs.doHandshake(remoteSocket, u, req.getHeaders());
		assertNotNull(remoteSocket);
		System.out.println("Got socket connected to: "+ remoteSocket.getRemoteSocketAddress() +
				" local address: " + remoteSocket.getLocalAddress()+":"+remoteSocket.getLocalPort());
		BufferedReader r = new BufferedReader(new InputStreamReader(remoteSocket.getInputStream()));
		for(int i=0; i<10; i++) {
			String out = "test_"+i;
			remoteSocket.getOutputStream().write((out+"\n").getBytes());
			remoteSocket.getOutputStream().flush();
			System.out.println("---> "+out);
			String line = r.readLine();
			System.out.println("<--- "+line);
			assertEquals(out, line);
		}
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
			return response.getCode();
		}
	}

}
