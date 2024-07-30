package eu.unicore.gateway;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.Test;

import eu.unicore.gateway.cluster.MultiSite;


public class TestSiteFactory {

	@Test
	public void testVsite()throws Exception{
		Site s1=SiteFactory.buildSite(new URI("http://localhost"),"test", "http://localhost", null);
		assertNotNull(s1);
		assertTrue(s1 instanceof VSite);
	}

	@Test
	public void testMsite()throws Exception{
		Site s1=SiteFactory.buildSite(new URI("http://localhost"),"test", 
				"multisite: vsites=http://localhost:8010 http://localhost:8011", null);
		assertNotNull(s1);
		assertTrue(s1 instanceof MultiSite);
	}
}
