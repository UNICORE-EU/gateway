package eu.unicore.gateway.base;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.ee10.servlet.ResourceServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Handler;

import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.forwarding.ProtocolUpgradeFilter;
import eu.unicore.gateway.util.AcmeHandler;
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
		return root;
	}

	@Override
	protected Handler configureHandlers(Handler root) {
		if(gateway.getProperties().isAcmeEnabled() &&
				gateway.getProperties().getHostname().toLowerCase().startsWith("https")) 
		{
			AcmeHandler h = new AcmeHandler();
			h.setHandler(super.configureHandlers(root));
			return h;
			
		}
		else{
			return super.configureHandlers(root);
		}
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

	@Override
	public void reloadCredential() {
		super.reloadCredential();
		gateway.reloadConfig();
	}
}
