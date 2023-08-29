package eu.unicore.gateway.base;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.forwarding.ProtocolUpgradeFilter;
import eu.unicore.gateway.util.FileWatcher;
import eu.unicore.gateway.util.LogUtil;
import eu.unicore.security.canl.CredentialProperties;
import eu.unicore.util.Log;
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

	private static final Logger logger=LogUtil.getLogger(Log.HTTP_SERVER, GatewayJettyServer.class);

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
		ServletContextHandler root = new ServletContextHandler(getServer(), "/", 
				ServletContextHandler.SESSIONS);
		ProtocolUpgradeFilter.ensureFilter(root.getServletContext(), gateway);
		ServletHolder servletHolder = new ServletHolder(new Servlet(gateway));
		root.addServlet(servletHolder, "/*");
		URL u = getClass().getResource("/eu/unicore/gateway");
		root.setResourceBase(u.toString());
		logger.debug("Adding resources servlet, base={}", u);
		root.addServlet(DefaultServlet.class,"/resources/*");
		return root;
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
