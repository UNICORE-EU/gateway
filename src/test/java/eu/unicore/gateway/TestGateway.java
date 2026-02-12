package eu.unicore.gateway;

import org.junit.jupiter.api.Test;

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
}
