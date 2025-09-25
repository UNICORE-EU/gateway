package eu.unicore.gateway.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;

import org.apache.commons.io.FileUtils;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.junit5.JUnit5Mockery;
import org.junit.jupiter.api.Test;

import eu.unicore.gateway.properties.GatewayProperties;
import jakarta.servlet.http.HttpServletResponse;

public class TestAcmeRenderer {

	@Test
	public void testAcmeDisabled() throws Exception {
		GatewayProperties p = new GatewayProperties("src/test/resources/gateway.properties");
		AcmeRenderer r = new AcmeRenderer(p);
		Mockery context = new JUnit5Mockery();
		HttpServletResponse res = context.mock(HttpServletResponse.class);
		context.checking(new Expectations() {{
			oneOf(res).sendError(404, "Not found");
		}});
		r.handleAcmeRequest("foo", null, res);
		context.assertIsSatisfied();
	}

	@Test
	public void testAcmeEnabled() throws Exception {
		GatewayProperties p = new GatewayProperties("src/test/resources/gateway.properties");
		p.setProperty(GatewayProperties.KEY_ACME_ENABLE, "true");
		p.setProperty(GatewayProperties.KEY_ACME_DIR, "target");
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try(PrintWriter out = new PrintWriter(bos)){
			File token = new File("target", "tokentest.txt");
			FileUtils.write(token, "test123", "UTF-8");
			AcmeRenderer r = new AcmeRenderer(p);
			Mockery context = new JUnit5Mockery();
			HttpServletResponse res = context.mock(HttpServletResponse.class);
			context.checking(new Expectations() {{
				oneOf(res).getWriter();will(returnValue(out));
				oneOf(res).setContentType("text/plain");
			}});
			r.handleAcmeRequest("tokentest.txt", null, res);
			context.assertIsSatisfied();
			out.flush();
			assertEquals("test123", bos.toString("UTF-8").strip());
		}
	}

	@Test
	public void testNoFile() throws Exception {
		GatewayProperties p = new GatewayProperties("src/test/resources/gateway.properties");
		p.setProperty(GatewayProperties.KEY_ACME_ENABLE, "true");
		p.setProperty(GatewayProperties.KEY_ACME_DIR, "target");
		AcmeRenderer r = new AcmeRenderer(p);
		Mockery context = new JUnit5Mockery();
		HttpServletResponse res = context.mock(HttpServletResponse.class);
		context.checking(new Expectations() {{
			oneOf(res).sendError(404, "Not found");
		}});
		r.handleAcmeRequest("no_such_file", null, res);
		context.assertIsSatisfied();
	}
}
