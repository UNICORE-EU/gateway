package eu.unicore.gateway;

import java.net.URI;

import eu.unicore.gateway.cluster.MultiSite;

import junit.framework.TestCase;;

public class TestSiteFactory extends TestCase {

	public void testVsite()throws Exception{
		try{
			Site s1=SiteFactory.buildSite(new URI("http://localhost"),"test", "http://localhost", null);
			assertNotNull(s1);
			assertTrue(s1 instanceof VSite);
		}catch(Exception ex){
			ex.printStackTrace();
			fail(ex.getMessage());
		}
	}

	public void testMsite()throws Exception{
		Site s1=SiteFactory.buildSite(new URI("http://localhost"),"test", 
				"multisite: vsites=http://localhost:8010 http://localhost:8011", null);
		assertNotNull(s1);
		assertTrue(s1 instanceof MultiSite);
	}
}
