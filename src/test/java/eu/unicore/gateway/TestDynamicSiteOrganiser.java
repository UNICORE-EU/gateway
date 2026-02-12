package eu.unicore.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import eu.unicore.gateway.cluster.MultiSite;
import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.util.FrontPageRenderer;
import eu.unicore.gateway.util.FrontPageRenderer.SortOrder;
import eu.unicore.security.canl.AuthnAndTrustProperties;
import eu.unicore.security.canl.CredentialProperties;
import eu.unicore.security.canl.TruststoreProperties;

public class TestDynamicSiteOrganiser {
	
	private static Gateway gw;
	private static AuthnAndTrustProperties sp;

	@BeforeAll
	public static void setUp() throws Exception{
		File gp = new File("src/test/resources/gateway.properties");
		File cp = new File("src/test/resources/connection.properties");
		sp = new AuthnAndTrustProperties(gp,
				GatewayProperties.PREFIX + TruststoreProperties.DEFAULT_PREFIX, 
				GatewayProperties.PREFIX + CredentialProperties.DEFAULT_PREFIX);
		gw = new Gateway(gp, cp);
		gw.start();
	}

	@AfterAll
	public static void tearDown()throws Exception{
		Thread.sleep(2000);
		gw.stop();
	}

	@Test
	public void testDynamicRegistration()throws Exception{
		String gwURL = "http://foo";
		MultiSite ms = new MultiSite(new URI(gwURL), "test", null, sp);
		DynamicSiteOrganiser dso = gw.getDynamicSiteOrganiser();
		dso.register(ms);
		assertEquals(0, ms.getConfiguredSites().size());
		dso.register("test", new URI("http://localhost:12345"));
		assertEquals(1, ms.getConfiguredSites().size());
		dso.register("test", new URI("http://localhost:54321"));
		assertEquals(2, ms.getConfiguredSites().size());
		dso.register("new-site", new URI("http://localhost:6789"));
		assertEquals(2, dso.getSites().size());
		String html = FrontPageRenderer.toHTMLString(gwURL, dso, SortOrder.NONE, true);
		assertTrue(html.contains("http://localhost:54321"));
		assertTrue(html.contains("http://localhost:12345"));
		assertTrue(html.contains("http://localhost:6789"));
		html = FrontPageRenderer.toHTMLString(gwURL, dso, SortOrder.NONE, false);
		assertFalse(html.contains("http://localhost:54321"));
		assertFalse(html.contains("http://localhost:12345"));
		assertFalse(html.contains("http://localhost:6789"));
	}

	@Test
	public void testExclusionInclusionPatterns()throws Exception{
		String excl = "bad.org  some.thing   veryevil.COM   evil.good.org";
		String incl = "good.org";
		DynamicSiteOrganiser dso=new DynamicSiteOrganiser(gw,excl, incl);
		assertThrows(IllegalArgumentException.class,()->
			dso.checkExclusion(new URI("http://www.bad.org:123")));
		assertThrows(IllegalArgumentException.class,()->
			dso.checkExclusion(new URI("http://veryevil.com")));
		assertThrows(IllegalArgumentException.class,
				()->dso.checkExclusion(new URI("http://evil.good.org")));
		dso.checkInclusion(new URI("HTTP://WWW.GOOD.ORG"));
		assertThrows(IllegalArgumentException.class,
				()->dso.checkInclusion(new URI("http://evil.org")));
		assertFalse(dso.register("FOO", new URI("http://evil.org")));
	}
}
