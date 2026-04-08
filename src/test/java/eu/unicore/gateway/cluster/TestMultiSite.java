package eu.unicore.gateway.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import eu.unicore.gateway.FakeServer;
import eu.unicore.gateway.VSite;
import eu.unicore.util.configuration.ConfigurationException;



public class TestMultiSite {

	@Test
	public void testBasicConfig()throws Exception{
		String desc="foo=bar;spam=ham";
		MultiSite ms=new MultiSite(new URI("http://foo"),"test",desc); 
		Map<String,String>p=ms.getParams();
		assertEquals(2,p.entrySet().size());
		assertEquals("bar",p.get("foo"));
		assertEquals("ham",p.get("spam"));	
	}

	@Test
	public void testSetupSites()throws Exception{
		String desc = "vsites=http://localhost:8000 http://localhost:8010;spam=ham";
		MultiSite ms = new MultiSite(new URI("http://foo"),"test",desc); 
		List<VSite>sites = ms.getConfiguredSites();
		assertEquals(2, sites.size());
		ms.reloadConfig();
	}

	@Test
	public void testReadConfig()throws Exception{
		String desc = "config=src/test/resources/cluster.config";
		MultiSite ms=new MultiSite(new URI("http://foo"),"test",desc); 
		List<VSite>sites = ms.getConfiguredSites();
		assertEquals(2, sites.size());
		final String err = "no_equal_signs";
		assertThrows(ConfigurationException.class,
				()->new MultiSite(new URI("http://foo"),"test",err));
	}

	@Test
	public void testCreateStrategy()throws Exception{
		String desc = "config=src/test/resources/cluster.config";
		MultiSite ms = new MultiSite(new URI("http://foo"),"test",desc); 
		List<VSite>sites = ms.getConfiguredSites();
		assertEquals(2, sites.size());
		SelectionStrategy s=ms.getSelectionStrategy();
		assertNotNull(s);
		assertTrue(s instanceof PrimaryWithFallBack);

		final String err = "strategy=no_such_class";
		assertThrows(ConfigurationException.class,
				()->new MultiSite(new URI("http://foo"),"test",err));
	}

	@Test
	public void testMultiSitePing()throws Exception{
		URI gwURI = new URI("http://foo");
		MultiSite ms=new MultiSite(gwURI,"test",null); 
		assertTrue(ms.accept(gwURI+"/test"));
		assertFalse(ms.accept(gwURI+"/other"));

		FakeServer v1 = new FakeServer();
		FakeServer v2 = new FakeServer();
		v1.start();
		v2.start();
		Thread.sleep(2000);
		ms.registerVsite(new VSite(gwURI,"site",v1.getURI()));
		ms.registerVsite(new VSite(gwURI,"site",v2.getURI()));

		List<VSite>sites = ms.getConfiguredSites();
		assertEquals(2, sites.size());

		assertTrue(ms.ping());
		assertEquals("OK (2/2 nodes online)",ms.getStatusMessage());
		v1.stop();
		v2.stop();
	}

	@Test
	public void testDynamicRegistration()throws Exception{
		URI gwURI = new URI("http://foo");
		MultiSite ms = new MultiSite(gwURI,"test",null);
		assertEquals(0,ms.getConfiguredSites().size());
		FakeServer s1=new FakeServer();
		FakeServer s2=new FakeServer();
		s1.start();
		s2.start();
		Thread.sleep(2000);

		ms.registerVsite(new URI("http://localhost:"+s1.getPort()));
		assertEquals(1,ms.getConfiguredSites().size());

		//re-registration should not do any harm
		ms.registerVsite(new URI("http://localhost:"+s1.getPort()));
		assertEquals(1,ms.getConfiguredSites().size());
		assertEquals("OK (1/1 nodes online)",ms.getStatusMessage());

		//register 2nd site
		ms.registerVsite(new URI("http://localhost:"+s2.getPort()));
		assertEquals(2,ms.getConfiguredSites().size());
		assertEquals("OK (2/2 nodes online)",ms.getStatusMessage());

		s1.stop();
		s2.stop();
	}

	@Test
	public void testDefaultSelectionStrategy()throws Exception{
		URI gwURI=new URI("http://foo");
		String param=SelectionStrategy.HEALTH_CHECK_INTERVAL+"=1000";
		MultiSite ms=new MultiSite(gwURI,"test",param); 
		FakeServer s1=new FakeServer();
		FakeServer s2=new FakeServer();
		s1.start();
		s2.start();
		Thread.sleep(2000);

		VSite v1=new VSite(gwURI,"site",s1.getURI());
		v1.disablePingDelay();
		ms.registerVsite(v1);
		VSite v2=new VSite(gwURI,"site",s2.getURI());
		v2.disablePingDelay();
		ms.registerVsite(v2);

		VSite selected=ms.select("123.45.67.89");
		assertTrue(selected==v1);

		s1.stop();
		while(!s1.isStopped())Thread.sleep(1000);

		Thread.sleep(2000);

		selected=ms.select("123.45.67.89");
		assertTrue(selected==v2);

		s1.restart();
		Thread.sleep(2000);
		selected=ms.select("123.45.67.89");
		assertTrue(selected==v1);

		s1.stop();
		s2.stop();
	}
}
