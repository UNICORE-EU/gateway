package eu.unicore.gateway.cluster;

import java.io.File;
import java.net.URI;
import java.util.Map;

import org.junit.Test;

import eu.unicore.gateway.FakeServer;
import eu.unicore.gateway.VSite;
import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.security.canl.AuthnAndTrustProperties;
import eu.unicore.security.canl.CredentialProperties;
import eu.unicore.security.canl.TruststoreProperties;
import junit.framework.TestCase;

public class TestRoundRobinStrategy extends TestCase{
	
	@Test
	public void testRoundRobin()throws Exception{
		File spFile = new File("src/test/resources/security.properties");
		AuthnAndTrustProperties sp=new AuthnAndTrustProperties(spFile,
				GatewayProperties.PREFIX + TruststoreProperties.DEFAULT_PREFIX, 
				GatewayProperties.PREFIX + CredentialProperties.DEFAULT_PREFIX);
		URI gwURI=new URI("http://foo");
		String param=SelectionStrategy.HEALTH_CHECK_INTERVAL+"=100";
		String param1="strategy="+SelectionStrategy.ROUND_ROBIN_STRATEGY;
		
		String vsiteName="test";
		MultiSite ms=new MultiSite(gwURI,vsiteName,param+";"+param1,sp); 
		FakeServer s1=new FakeServer();
		FakeServer s2=new FakeServer();
		FakeServer s3=new FakeServer();
		s1.start();
		s2.start();
		s3.start();
		Thread.sleep(2000);
		
		VSite v1=new VSite(gwURI,vsiteName,s1.getURI(),sp);
		ms.registerVsite(v1);
		VSite v2=new VSite(gwURI,vsiteName,s2.getURI(),sp);
		ms.registerVsite(v2);
		VSite v3=new VSite(gwURI,vsiteName,s3.getURI(),sp);
		ms.registerVsite(v3);

		assertEquals(RoundRobin.class,ms.getSelectionStrategy().getClass());

		RoundRobin r=(RoundRobin)ms.getSelectionStrategy();
		Map<String,Boolean>health=r.health;
		r.checkHealth();
		assertEquals(3,health.size());
		assertEquals(Boolean.TRUE, health.get(s1.getURI().toString()));
		assertEquals(Boolean.TRUE, health.get(s2.getURI().toString()));
		assertEquals(Boolean.TRUE, health.get(s3.getURI().toString()));
		
		s1.stop();
		while(!s1.isStopped())Thread.sleep(1000);
		Thread.sleep(300);
		r.checkHealth();
		assertEquals(3,health.size());
		assertEquals(Boolean.FALSE, health.get(s1.getURI().toString()));
		assertEquals(Boolean.TRUE, health.get(s2.getURI().toString()));
		assertEquals(Boolean.TRUE, health.get(s3.getURI().toString()));
		
		s1.restart();
		Thread.sleep(300);
		r.checkHealth();
		assertEquals(3,health.size());
		assertEquals(Boolean.TRUE, health.get(s1.getURI().toString()));
		assertEquals(Boolean.TRUE, health.get(s2.getURI().toString()));
		assertEquals(Boolean.TRUE, health.get(s3.getURI().toString()));
		
		for(int i=0; i<100; i++){
			ms.select(null).accept(gwURI.toString()+"/"+vsiteName);
		}
		System.out.println("Number of calls: V1="+v1.getNumberOfRequests()
				+", V2="+v2.getNumberOfRequests()
				+", V3="+v3.getNumberOfRequests());
		assertTrue(v1.getNumberOfRequests()>20);
		assertTrue(v2.getNumberOfRequests()>20);
		assertTrue(v2.getNumberOfRequests()>20);
		
		s1.stop();
		s2.stop();
		s3.stop();
	}
	
}
