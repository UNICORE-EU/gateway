package eu.unicore.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.Test;

public class TestVsite {

	@Test
	public void test1()throws Exception{
		VSite v0 = new VSite(new URI("http://localhost:8080"),
			"test", "http://localhost:1234", null);
		assertTrue(v0.accept("http://localhost:8080/test/bar"));
		assertTrue(v0.accept("http://localhost:8080/test"));
		assertEquals("http://localhost:1234/index.html",v0.resolve("http://localhost:8080/test/index.html"));
		assertEquals("http://localhost:1234/index.html?res=xxx",v0.resolve("http://localhost:8080/test/index.html?res=xxx"));
		assertEquals("http://localhost:1234/index.html?res=xxx#test123",v0.resolve("http://localhost:8080/test/index.html?res=xxx#test123"));
		assertEquals("http://localhost:1234",v0.resolve("http://localhost:8080/test"));
		
		VSite v1=new VSite(new URI("http://localhost/foo"),"test",
			"http://localhost:1234", null);
		assertTrue(v1.accept("http://localhost/foo/test/bar"));
		assertEquals("http://localhost:1234/index.html",v1.resolve("http://localhost/foo/test/index.html"));
	}
	
	@Test
	public void testVSiteEquals()throws Exception{
		URI gw=new URI("http://localhost");
		Site s1=new VSite(gw,"FOO","http://localhost:1234", null);
		Site s2=new VSite(gw,"FOO","http://localhost:1234", null);
		Site s3=new VSite(gw,"BAR","http://localhost:1234", null);
		Site s4=new VSite(gw,"FOO","http://localhost:4321", null);
		assertEquals(s1,s2);
		assertEquals(s1,s1);
		assertNotEquals(s1, s3);
		assertNotEquals(s1, s4);
		assertNotEquals(s1, null);
		assertNotEquals(s1, "");
	}

	@Test
	public void test2() throws Exception {
		VSite v0 = new VSite(new URI("http://0.0.0.0:8080"),
				"test", "http://localhost:1234", null);
		String resolved = v0.resolve("http://test.foo.org:8080/test/abc");
		assertEquals("http://localhost:1234/abc",resolved);
	}
	
	@Test
	public void testVsiteStatus() throws Exception{
		VSite v = null; 
		try(FakeServer s1=new FakeServer()){
			s1.start();
			URI gw = new URI("http://localhost");
			v = new VSite(gw,"FOO",s1.getURI(), null);
			assertTrue(v.ping());
			System.out.println(v.getStatusMessage());
		}
		v.disablePingDelay();
		Thread.sleep(1000);
		assertFalse(v.ping());
		System.out.println(v.getStatusMessage());		
	}
}
