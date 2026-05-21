package eu.unicore.gateway.tokens;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.eclipse.jetty.security.openid.OpenIdConfiguration;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.junit5.JUnit5Mockery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import eu.unicore.gateway.FakeServer;
import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.VSite;
import eu.unicore.gateway.properties.GatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class TestTokenGenerator {

	private static Gateway gw;

	@BeforeAll
	public static void setUp() throws Exception{
		File gp = new File("src/test/resources/gateway.properties");
		File cp = new File("src/test/resources/connection.properties");
		gw = new Gateway(gp, cp);
		gw.start();
	}

	@AfterAll
	public static void tearDown()throws Exception{
		Thread.sleep(2000);
		gw.stop();
	}

	@Test
	public void testProperties() throws Exception {
		Properties p = new Properties();
		p.put("gateway.apitokens.enable", "true");
		p.put("gateway.apitokens.issuer", "https://localhost:2443/oauth2");
		p.put("gateway.apitokens.tokenEndpoint", "https://localhost:2443/oauth2/token");
		p.put("gateway.apitokens.authorizationEndpoint", "https://localhost:2443/oauth2-as/oauth2-authz");
		p.put("gateway.apitokens.scope", "openid profile");
		p.put("gateway.apitokens.clientID", "my_client");
		p.put("gateway.apitokens.clientSecret", "my_secret");
		p.put("gateway.apitokens.authentication", "POST");
		TokenGeneratorProperties genProps = new TokenGeneratorProperties(p);
		assertTrue(genProps.isEnabled());
		assertEquals("https://localhost:2443/oauth2", genProps.getIssuer());
		assertEquals("https://localhost:2443/oauth2/token", genProps.getTokenEndpoint());
		assertEquals("https://localhost:2443/oauth2-as/oauth2-authz", genProps.getAuthzEndpoint());
		assertEquals("my_client", genProps.getClientID());
		assertEquals("my_secret", genProps.getClientSecret());
		assertArrayEquals(new String[] {"openid", "profile"}, genProps.getScope());
		assertEquals("client_secret_post", genProps.getJettyAuthMode());

		OpenIdConfiguration oidc = Configuration.getOIDCConfig(genProps);
		assertEquals("my_client", oidc.getClientId());
		assertEquals("my_secret", oidc.getClientSecret());
	}

	@Test
	public void testPropertiesFile() throws Exception {
		GatewayProperties gwProps = new GatewayProperties(
				new File("src/test/resources/gateway-token.properties"));
		assertNotNull(Configuration.configureOIDC(gwProps));
	}

	@Test
	public void testDisabled() throws Exception {
		gw.getProperties().setProperty("apitokens.enable", "false");
		TokenGenerator r = new TokenGenerator(gw.getProperties(), gw.getSiteOrganiser());
		Mockery context = new JUnit5Mockery();
		HttpServletRequest req = context.mock(HttpServletRequest.class);
		HttpServletResponse res = context.mock(HttpServletResponse.class);
		context.checking(new Expectations() {{
			oneOf(res).sendError(404, "Not found");
		}});
		r.handleRequest(req, res);
		context.assertIsSatisfied();
	}

	@Test
	public void testNoAuth() throws Exception {
		gw.getProperties().setProperty("apitokens.enable", "true");
		TokenGenerator r = new TokenGenerator(gw.getProperties(), gw.getSiteOrganiser());
		Mockery context = new JUnit5Mockery();
		Map<String,String>attrs = new HashMap<>();
		HttpSession session = context.mock(HttpSession.class);
		context.checking(new Expectations() {{
			oneOf(session).getAttribute(TokenGenerator._ATTR_OIDC);
			will(returnValue(attrs));
		}});
		HttpServletRequest req = context.mock(HttpServletRequest.class);
		context.checking(new Expectations() {{
			oneOf(req).getSession(false); 
			will(returnValue(session));
		}});
		HttpServletResponse res = context.mock(HttpServletResponse.class);
		context.checking(new Expectations() {{
			oneOf(res).sendError(401, "Not authenticated");
		}});
		r.handleRequest(req, res);
		context.assertIsSatisfied();
	}

	@Test
	public void testShowForm() throws Exception {
		try(FakeServer s1=new FakeServer()){
			setup(s1);
			gw.getProperties().setProperty("apitokens.enable", "true");
			TokenGenerator r = new TokenGenerator(gw.getProperties(), gw.getSiteOrganiser());
			Map<String,String>attrs = new HashMap<>();
			attrs.put("access_token", "my-token");
			Mockery context = new JUnit5Mockery();
			HttpSession session = context.mock(HttpSession.class);
			context.checking(new Expectations() {{
				oneOf(session).getAttribute(TokenGenerator._ATTR_OIDC);
				will(returnValue(attrs));
			}});
			HttpServletRequest req = context.mock(HttpServletRequest.class);
			context.checking(new Expectations() {{
				oneOf(req).getRequestURL(); 
				will(returnValue(new StringBuffer("http://localhost:64433"
						+TokenGenerator.PATH)));
				oneOf(req).getSession(false); 
				will(returnValue(session));
			}});
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			PrintWriter p = new PrintWriter(os);
			HttpServletResponse res = context.mock(HttpServletResponse.class);
			context.checking(new Expectations() {{
				oneOf(res).getWriter();will(returnValue(p));
				oneOf(res).setContentType("text/html");
			}});
			r.handleRequest(req, res);
			context.assertIsSatisfied();
			p.flush();
			String html = new String(os.toByteArray(), "UTF-8");
			assertTrue(html.contains("FAKE1"));
		}
	}

	@Test
	public void testCreate() throws Exception {
		try(FakeServer s1=new FakeServer()){
			setup(s1);
			gw.getProperties().setProperty("apitokens.enable", "true");
			TokenGenerator r = new TokenGenerator(gw.getProperties(), gw.getSiteOrganiser());
			Map<String,String>attrs = new HashMap<>();
			attrs.put("access_token", "my-token");
			Mockery context = new JUnit5Mockery();
			HttpSession session = context.mock(HttpSession.class);
			context.checking(new Expectations() {{
				oneOf(session).getAttribute(TokenGenerator._ATTR_OIDC);
				will(returnValue(attrs));
			}});
			HttpServletRequest req = context.mock(HttpServletRequest.class);
			context.checking(new Expectations() {{
				oneOf(req).getRequestURL(); 
				will(returnValue(new StringBuffer("http://localhost:64433"
						+TokenGenerator.CREATE)));
				oneOf(req).getParameter("site"); 
				will(returnValue("FAKE1"));
				oneOf(req).getParameter("lifetime"); 
				will(returnValue("3600"));
				oneOf(req).getParameter("userprefs"); 
				will(returnValue(""));
				oneOf(req).getSession(false); 
				will(returnValue(session));
			}});
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			PrintWriter p = new PrintWriter(os);
			HttpServletResponse res = context.mock(HttpServletResponse.class);
			context.checking(new Expectations() {{
				oneOf(res).getWriter();will(returnValue(p));
				oneOf(res).setContentType("text/html");
			}});
			r.handleRequest(req, res);
			context.assertIsSatisfied();
			p.flush();
			String html = new String(os.toByteArray(), "UTF-8");
			assertTrue(html.contains("API token for FAKE1"));
		}
	}

	@Test
	public void testGenerator() throws Exception {
		try(FakeServer s1=new FakeServer()){
			s1.start();
			s1.setAnswer("my_token");
			String s1Url=s1.getURI();
			int status=doRegister("FAKE1",s1Url);
			assertEquals(HttpStatus.SC_CREATED,status);
			VSite s = (VSite)gw.getSiteOrganiser().getSite("FAKE1");
			s.getMetadata().put(TokenGenerator.TOKEN_URL, s1Url+"/token");
			System.out.println(s.getMetadata());
			TokenGenerator gen = new TokenGenerator(gw.getProperties(), gw.getSiteOrganiser());
			String token = gen.generateAPIToken("test123", s, "3600", "group:test");
			assertTrue("my_token".equals(token));
			assertTrue(s1.getLatestRequestHeaders().toString()
					.contains("X-UNICORE-User-Preferences: group:test"));
			// some random stuff to increase the code coverage :-)
			assertTrue(gen.getFooter().contains("Version"));
			assertTrue(gen.getHeader().contains("Gateway"));
			assertTrue(gen.getForm(gw.getSiteOrganiser().getSites()).contains("FAKE1"));
			gen.getResultPage(token, "FAKE1").contains("Download");
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

	private void setup(FakeServer s1) throws Exception {
		s1.start();
		s1.setAnswer("my_token");
		String s1Url=s1.getURI();
		int status=doRegister("FAKE1",s1Url);
		assertEquals(HttpStatus.SC_CREATED,status);
		VSite s = (VSite)gw.getSiteOrganiser().getSite("FAKE1");
		s.getMetadata().put(TokenGenerator.TOKEN_URL, s1Url+"/token");
	}
}
