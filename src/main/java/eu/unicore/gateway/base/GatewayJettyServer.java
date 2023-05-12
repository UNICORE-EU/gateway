package eu.unicore.gateway.base;

import java.net.URL;

import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.forwarding.ProtocolUpgradeFilter;
import eu.unicore.gateway.util.LogUtil;
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

	public GatewayJettyServer(Gateway gateway) throws Exception
	{
		super(new URL(gateway.getProperties().getHostname()), gateway.getSecurityProperties(),
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
}
