/*********************************************************************************
 * Copyright (c) 2006 Forschungszentrum Juelich GmbH 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * (1) Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the disclaimer at the end. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * 
 * (2) Neither the name of Forschungszentrum Juelich GmbH nor the names of its 
 * contributors may be used to endorse or promote products derived from this 
 * software without specific prior written permission.
 * 
 * DISCLAIMER
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ********************************************************************************/


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
