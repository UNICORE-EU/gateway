package eu.unicore.gateway.base;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.WWWAuthenticationProtocolHandler;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.eclipse.jetty.ee10.servlet.ResourceServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.openid.OpenIdAuthenticator;
import org.eclipse.jetty.security.openid.OpenIdConfiguration;
import org.eclipse.jetty.security.openid.OpenIdLoginService;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.acme.AcmeHandler;
import eu.unicore.gateway.forwarding.ProtocolUpgradeFilter;
import eu.unicore.gateway.util.FileWatcher;
import eu.unicore.security.canl.CredentialProperties;
import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.jetty.JettyServerBase;

/**
 * Wraps a Jetty server and allows to configure it using the Gateway properties file.<br/>
 * Adds two servlets, one serving static resources for the website, the other handling 
 * requests.
 * 
 * @author schuller
 * @author K. Benedyczak
 */
public class GatewayJettyServer extends JettyServerBase {

	private final Gateway gateway;

	public GatewayJettyServer(URL[] URLs, Gateway gateway) throws Exception
	{
		super(URLs, gateway.getSecurityProperties(),
			gateway.getJettyProperties());
		this.gateway = gateway;
		initServer();
	}

	@Override
	protected Handler createRootHandler() throws ConfigurationException
	{
		ServletContextHandler root = new ServletContextHandler("/",	ServletContextHandler.SESSIONS);
		ProtocolUpgradeFilter.ensureFilter(root.getServletContext(), gateway);
		ServletHolder servletHolder = new ServletHolder(new Servlet(gateway));
		root.addServlet(servletHolder, "/*");
		ServletHolder resHolder = new ServletHolder(new ResourceServlet());
		URL u = getClass().getResource("/eu/unicore/gateway/resources");
		resHolder.setInitParameter("baseResource", u.toString());
		root.addServlet(resHolder, "/resources/*");
		root.setSecurityHandler(configureOIDC());
		root.setSessionHandler(new SessionHandler());
		return root;
	}

	@Override
	protected Handler configureHandlers(Handler root) {
		Handler r = super.configureHandlers(root);
		if(gateway.getProperties().isAcmeEnabled() &&
				gateway.getProperties().getHostname().toLowerCase().startsWith("https")) 
		{
			AcmeHandler h = new AcmeHandler();
			h.setHandler(r);
			r = h;
		}
		return r;
	}

	@Override
	protected void initServer() throws ConfigurationException{
		super.initServer();
		CredentialProperties cProps = gateway.getSecurityProperties().getCredentialProperties(); 
		if(cProps.isDynamicalReloadEnabled()) {
			String path = cProps.getValue(CredentialProperties.PROP_LOCATION);
			try{
				FileWatcher fw = new FileWatcher(new File(path), () -> {
					gateway.getSecurityProperties().reloadCredential();
					reloadCredential();
				});
				fw.schedule(10, TimeUnit.SECONDS);	
			}catch(FileNotFoundException fe) {
				throw new ConfigurationException("", fe);
			}
		}
	}

	private SecurityHandler configureOIDC() {
		SecurityHandler.PathMapped securityHandler = new SecurityHandler.PathMapped();
		securityHandler.put("/token/*", Constraint.ANY_USER);
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
				.clientId("oauth-client2")
				.clientSecret("70916334-b7ed-42c6-9a57-8485f633103b")
				.issuer("https://localhost:2443/oauth2")
				.httpClient(client)
				.authenticateNewUsers(true)
				.build();
		LoginService loginService = new OpenIdLoginService(openIdConfig);
		securityHandler.setLoginService(loginService);
		securityHandler.setAuthenticator(new OpenIdAuthenticator(openIdConfig));
		return securityHandler;
	}

	@Override
	public void reloadCredential() {
		super.reloadCredential();
		gateway.reloadConfig();
	}
}
