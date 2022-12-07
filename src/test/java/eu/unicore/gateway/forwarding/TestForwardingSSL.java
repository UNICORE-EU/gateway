package eu.unicore.gateway.forwarding;

import java.io.IOException;

/**
 * Port forwarding with SSL gateway and SSL backend
 */
public class TestForwardingSSL extends TestForwarding {
	
	protected String getScheme() {
		return "https";
	}

	protected EchoEndpointServer createBackend() throws IOException {
		return new EchoEndpointServer(65438, true, gw.getClientFactory().getClientConfiguration());
	}

	protected String getPropertiesLoc() {
		return "src/test/resources/gateway-ssl.properties";
	}
}
