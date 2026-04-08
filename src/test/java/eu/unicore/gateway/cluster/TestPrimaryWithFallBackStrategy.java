package eu.unicore.gateway.cluster;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.Test;

import eu.unicore.gateway.FakeServer;
import eu.unicore.gateway.VSite;

public class TestPrimaryWithFallBackStrategy {
	
	@Test
	public void testPrimarySelectWithFallback()throws Exception{
		URI gwURI=new URI("http://foo");
		String param=SelectionStrategy.HEALTH_CHECK_INTERVAL+"=1000";
		String param1="strategy="+SelectionStrategy.PRIMARY_WITH_FALLBACK_STRATEGY;
		MultiSite ms=new MultiSite(gwURI,"test",param+";"+param1); 
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
