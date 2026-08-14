package eu.unicore.gateway.tokens;

import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.WWWAuthenticationProtocolHandler;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.openid.OpenIdAuthenticator;
import org.eclipse.jetty.security.openid.OpenIdConfiguration;
import org.eclipse.jetty.security.openid.OpenIdLoginService;
import org.eclipse.jetty.util.ajax.JSON;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.configuration.FilePropertiesHelper;

public class Configuration {

	public static SecurityHandler configureOIDC(GatewayProperties gwProps) throws Exception {		
		Properties p = FilePropertiesHelper.load(gwProps.getFile());
		TokenGeneratorProperties properties = new TokenGeneratorProperties(p);
		SecurityHandler.PathMapped securityHandler = new SecurityHandler.PathMapped();
		securityHandler.put(TokenGenerator.PATH+"/*", Constraint.ANY_USER);
		OpenIdConfiguration openIdConfig = getOIDCConfig(properties);
		LoginService loginService = new OpenIdLoginService(openIdConfig);
		securityHandler.setLoginService(loginService);
		securityHandler.setAuthenticator(new OpenIdAuthenticator(openIdConfig));
		return securityHandler;
	}

	public static OpenIdConfiguration getOIDCConfig(TokenGeneratorProperties properties) throws Exception {
		ClientConnector connector = new ClientConnector();
		connector.setSslContextFactory(new SslContextFactory.Client(true));
		HttpClient client = new HttpClient(new HttpClientTransportOverHTTP(connector))
		{
			@Override
			protected void doStart() throws Exception
			{
				super.doStart();
				getProtocolHandlers().remove(WWWAuthenticationProtocolHandler.NAME);
			}
		};

		checkProperties(properties, client);

		return new OpenIdConfiguration.Builder()
				.clientId(properties.getClientID())
				.clientSecret(properties.getClientSecret())
				.issuer(properties.getIssuer())
				.tokenEndpoint(properties.getTokenEndpoint())
				.authorizationEndpoint(properties.getAuthzEndpoint())
				.authenticationMethod(properties.getJettyAuthMode())
				.scopes(properties.getScope())
				.authenticateNewUsers(true)
				.httpClient(client)
				.build();
	}

	private static void checkProperties(TokenGeneratorProperties props, HttpClient client) throws Exception {
		if (props.getAuthzEndpoint() == null || props.getTokenEndpoint() == null)
		{
			Map<String, Object> discoveryDocument = fetchOIDCMetadata(props, client);
			String authzEndpoint = (String)discoveryDocument.get("authorization_endpoint");
	        if (authzEndpoint == null)throw new ConfigurationException("Missing OIDC authz endpoint");
	        String tokenEndpoint = (String)discoveryDocument.get("token_endpoint");
	        if (tokenEndpoint == null)throw new ConfigurationException("Missing OIDC token endpoint");
			props.setProperty(TokenGeneratorProperties.TOKEN_ENDPOINT, tokenEndpoint);
			props.setProperty(TokenGeneratorProperties.AUTHZ_ENDPOINT, authzEndpoint);	
		}
	}

	private static Map<String, Object> fetchOIDCMetadata(TokenGeneratorProperties props, HttpClient httpClient)
			throws Exception {
		String CONFIG_PATH = "/.well-known/openid-configuration";
		String provider = props.getIssuer();
		if (provider.endsWith("/"))provider = provider.substring(0, provider.length() - 1);
		httpClient.start();
		String responseBody = httpClient.GET(provider + CONFIG_PATH).getContentAsString();
		Object parsedResult = new JSON().fromJSON(responseBody);
		if (parsedResult instanceof Map)
		{
			return ((Map<?, ?>)parsedResult).entrySet().stream()
					.filter(entry -> entry.getValue() != null)
					.collect(Collectors.toMap(it -> it.getKey().toString(), Map.Entry::getValue));
		}
		else
		{
			throw new ConfigurationException("Could not parse OpenID provider's malformed response");
		}
	}
}
