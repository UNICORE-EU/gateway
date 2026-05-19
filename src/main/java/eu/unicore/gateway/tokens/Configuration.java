package eu.unicore.gateway.tokens;

import java.util.Properties;

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
import org.eclipse.jetty.util.ssl.SslContextFactory;

import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.configuration.FilePropertiesHelper;

public class Configuration {

	public static SecurityHandler configureOIDC(GatewayProperties gwProps) {
		try{
			Properties p = FilePropertiesHelper.load(gwProps.getFile());
			TokenGeneratorProperties properties = new TokenGeneratorProperties(p);
			SecurityHandler.PathMapped securityHandler = new SecurityHandler.PathMapped();
			securityHandler.put(TokenGenerator.PATH+"/*", Constraint.ANY_USER);
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
			OpenIdConfiguration openIdConfig = new OpenIdConfiguration.Builder()
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
			LoginService loginService = new OpenIdLoginService(openIdConfig);
			securityHandler.setLoginService(loginService);
			securityHandler.setAuthenticator(new OpenIdAuthenticator(openIdConfig));
			return securityHandler;
		}catch(Exception e) {
			throw new ConfigurationException(null, e);
		}
	}

}
