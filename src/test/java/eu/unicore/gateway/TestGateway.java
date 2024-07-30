package eu.unicore.gateway;

import org.junit.jupiter.api.Test;

public class TestGateway {

	@Test
	public void testMain() throws Exception{
		String[] args = new String[] {
				"src/test/resources/gateway.properties",
				"src/test/resources/connection.properties",
				"src/test/resources/security.properties"
		};
		Gateway.main(args);
		Gateway.instance.reloadConfig();
		Gateway.instance.stopGateway();
	}

}
