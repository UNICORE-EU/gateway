package eu.unicore.gateway.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;

import junit.framework.TestCase;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import eu.unicore.bugsreporter.annotation.RegressionTest;
import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.properties.ConnectionsProperties;

public class TestFileWatcher extends TestCase {

	Boolean actionRun=Boolean.FALSE;

	public void testRun()throws Exception{
		File f=File.createTempFile("gwtest", "test");
		f.deleteOnExit();

		Runnable action=new Runnable() {
			public void run() {
				actionRun=Boolean.TRUE;
			}
		};

		FileWatcher fw=new FileWatcher(f, action);
		Thread.sleep(1000);
		f.setLastModified(System.currentTimeMillis());
		Thread.sleep(1000);
		fw.run();
		assertTrue(actionRun);
	}

	public void testNoSuchFile()throws Exception{
		File f=new File(String.valueOf(System.currentTimeMillis()));

		Runnable action=new Runnable() {
			public void run() {
			}
		};

		try{
			new FileWatcher(f, action);
			fail("Expected file not found exception.");
		}catch(FileNotFoundException fne){
			/* OK */
		}

	}

	@RegressionTest(url="https://sourceforge.net/tracker/?func=detail&aid=3474470&group_id=102081&atid=633902", id=3474470)
	public void testLog4jReconfigure()throws MalformedURLException{
		String relName="src/test/resources/test_log4j.properties";
		File f=new File(relName);
		//test with file:// URL
		String name="file://"+f.getAbsolutePath();
		assertFalse(Logger.getLogger("test123").isDebugEnabled());
		Gateway.reConfigureLog4j(name);
		assertTrue(Logger.getLogger("test123").isDebugEnabled());
		
		//test with relative filename
		Logger.getLogger("test123").setLevel(Level.INFO);
		assertFalse(Logger.getLogger("test123").isDebugEnabled());
		Gateway.reConfigureLog4j(relName);
		assertTrue(Logger.getLogger("test123").isDebugEnabled());
		
		//test with absolute path
		Logger.getLogger("test123").setLevel(Level.INFO);
		assertFalse(Logger.getLogger("test123").isDebugEnabled());
		Gateway.reConfigureLog4j(f.getAbsolutePath());
		assertTrue(Logger.getLogger("test123").isDebugEnabled());
	}

	@RegressionTest(url="https://sourceforge.net/p/unicore/bugs/626/", id=100626)
	public void testReloadConnectionsProperties() throws Exception {
		ConnectionsProperties cp=new ConnectionsProperties(new File("src/test/resources/connection.properties"));
		cp.reload();
	}
	
}
