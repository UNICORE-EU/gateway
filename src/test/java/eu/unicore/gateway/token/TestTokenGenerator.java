package eu.unicore.gateway.token;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.eclipse.jetty.security.openid.OpenIdConfiguration;
import org.junit.jupiter.api.Test;

import eu.unicore.gateway.tokens.Configuration;
import eu.unicore.gateway.tokens.TokenGeneratorProperties;

public class TestTokenGenerator {

	@Test
	public void testProperties() throws Exception {
		Properties p = new Properties();
		p.put("gateway.apitokens.enable", "true");
		p.put("gateway.apitokens.issuer", "https://localhost:2443/oauth2");
		p.put("gateway.apitokens.tokenEndpoint", "https://localhost:2443/oauth2/token");
		p.put("gateway.apitokens.authorizationEndpoint", "https://localhost:2443/oauth2-as/oauth2-authz");
		p.put("gateway.apitokens.scope", "openid profile");
		p.put("gateway.apitokens.clientID", "my_client");
		p.put("gateway.apitokens.clientSecret", "my_secret");
		p.put("gateway.apitokens.authentication", "POST");
		TokenGeneratorProperties genProps = new TokenGeneratorProperties(p);
		assertTrue(genProps.isEnabled());
		assertEquals("https://localhost:2443/oauth2", genProps.getIssuer());
		assertEquals("https://localhost:2443/oauth2/token", genProps.getTokenEndpoint());
		assertEquals("https://localhost:2443/oauth2-as/oauth2-authz", genProps.getAuthzEndpoint());
		assertEquals("my_client", genProps.getClientID());
		assertEquals("my_secret", genProps.getClientSecret());
		assertArrayEquals(new String[] {"openid", "profile"}, genProps.getScope());
		assertEquals("client_secret_post", genProps.getJettyAuthMode());

		OpenIdConfiguration oidc = Configuration.getOIDCConfig(genProps);
		assertEquals("my_client", oidc.getClientId());
		assertEquals("my_secret", oidc.getClientSecret());
	}

}
