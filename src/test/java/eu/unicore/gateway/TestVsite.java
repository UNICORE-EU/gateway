package eu.unicore.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
		assertEquals(s1,s2);
	}
	
	@Test
	public void testVSiteNotEquals()throws Exception{
		URI gw=new URI("http://localhost");
		Site s1=new VSite(gw,"FOO","http://localhost:3456", null);
		Site s2=new VSite(gw,"FOO2","http://localhost:3456", null);
		assertNotSame(s1,s2);
	}

	@Test
	public void test2() throws Exception {
		VSite v0 = new VSite(new URI("http://0.0.0.0:8080"),
				"test", "http://localhost:1234", null);
		String resolved = v0.resolve("http://test.foo.org:8080/test/abc");
		assertEquals("http://localhost:1234/abc",resolved);
	}
}
