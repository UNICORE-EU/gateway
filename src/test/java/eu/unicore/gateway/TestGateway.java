package eu.unicore.gateway;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import eu.unicore.gateway.util.FileWatcher;

public class TestGateway {

	@Test
	public void testMain() throws Exception{
		String[] args = new String[] {
				"src/test/resources/gateway.properties",
				"src/test/resources/connection.properties",
		};
		Gateway.main(args);
		Gateway.instance.getJettyServer().reloadCredential();;
		Gateway.instance.getSiteOrganiser().reloadConfig();
		Gateway.instance.stop();
	}

	@Test
	public void testSeparateClientConfig() throws Exception{
		String[] args = new String[] {
				"src/test/resources/gateway-separate-clientcert.properties",
				"src/test/resources/connection.properties",
		};
		Gateway.main(args);
		Gateway.instance.getJettyServer().reloadCredential();;
		Gateway.instance.getSiteOrganiser().reloadConfig();
		Gateway.instance.stop();
	}
	
	@Test
	public void testFileWatcher() throws Exception{
		assertThrows(FileNotFoundException.class, ()->new FileWatcher(new File("./__no_such_file___"), ()->{}));
		File tmp = File.createTempFile("__gw", "_test");
		final AtomicBoolean b = new AtomicBoolean(false);
		var fw = new FileWatcher(tmp, ()->{
			b.set(true);
		});
		Thread.sleep(1000);
		tmp.setLastModified(System.currentTimeMillis());
		fw.run();
		assertTrue(b.get());
	}

}
