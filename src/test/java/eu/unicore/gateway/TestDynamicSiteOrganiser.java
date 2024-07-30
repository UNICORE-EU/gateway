package eu.unicore.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import eu.unicore.gateway.SiteOrganiser.SortOrder;
import eu.unicore.gateway.cluster.MultiSite;
import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.security.canl.AuthnAndTrustProperties;
import eu.unicore.security.canl.CredentialProperties;
import eu.unicore.security.canl.TruststoreProperties;

public class TestDynamicSiteOrganiser {
	
	@Test
	public void testDynamicRegistration()throws Exception{
		File gpFile = new File("src/test/resources/gateway.properties");
		File spFile = new File("src/test/resources/security.properties");
		File connFile = new File("src/test/resources/connection.properties");
		AuthnAndTrustProperties sp=new AuthnAndTrustProperties(spFile,
				GatewayProperties.PREFIX + TruststoreProperties.DEFAULT_PREFIX, 
				GatewayProperties.PREFIX + CredentialProperties.DEFAULT_PREFIX);
		Gateway gw=new Gateway(gpFile,connFile,spFile);
		
		URI gwURI=new URI("http://foo");
		MultiSite ms=new MultiSite(gwURI, "test", null, sp);
		
		DynamicSiteOrganiser dso=gw.getDynamicSiteOrganiser();
		dso.register(ms);
		
		assertEquals(0,ms.getConfiguredSites().size());
		
		dso.register("test",new URI("http://localhost:12345"));
		assertEquals(1,ms.getConfiguredSites().size());
		
		dso.register("test",new URI("http://localhost:54321"));
		assertEquals(2,ms.getConfiguredSites().size());
		
		dso.register("new-site", new URI("http://localhost:6789"));
		assertEquals(2,dso.getSites().size());
		
		String html=dso.toHTMLString(SortOrder.NONE);
		
		assertTrue(html.contains("http://localhost:54321"));
		assertTrue(html.contains("http://localhost:12345"));
		assertTrue(html.contains("http://localhost:6789"));
	}

	@Test
	public void testExclusionInclusionPatterns()throws URISyntaxException{
		String excl="bad.org  some.thing   veryevil.COM   evil.good.org";
		String incl="good.org";
		
		DynamicSiteOrganiser dso=new DynamicSiteOrganiser(null,excl, incl);
		
		try{
			dso.checkExclusion(new URI("http://www.bad.org:123"));
			fail("Expected exception.");
		}catch(IllegalArgumentException e){
			//OK
		}
		
		try{
			dso.checkExclusion(new URI("http://veryevil.com"));
			fail("Expected exception.");
		}catch(IllegalArgumentException e){
			//OK
		}
		try{
			dso.checkExclusion(new URI("http://evil.good.org"));
			fail("Expected exception.");
		}catch(IllegalArgumentException e){
			//OK
		}
		dso.checkInclusion(new URI("HTTP://WWW.GOOD.ORG"));
	}
}
