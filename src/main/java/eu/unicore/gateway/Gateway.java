/**
 * Copyright (c) 2005-2008, Forschungszentrum Juelich
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met: 
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *  * Neither the name of the Forschungszentrum Juelich nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.unicore.gateway;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import eu.unicore.gateway.base.GatewayJettyServer;
import eu.unicore.gateway.client.HttpClientFactory;
import eu.unicore.gateway.properties.ConnectionsProperties;
import eu.unicore.gateway.properties.GatewayHttpServerProperties;
import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.util.FileWatcher;
import eu.unicore.gateway.util.LogUtil;
import eu.unicore.security.canl.AuthnAndTrustProperties;
import eu.unicore.security.canl.CredentialProperties;
import eu.unicore.security.canl.TruststoreProperties;

/**
 * main gateway class
 */
public class Gateway 
{
	private static final Logger log = LogUtil.getLogger(LogUtil.GATEWAY,Gateway.class);
	private GatewayJettyServer jetty;
	private CompositeSiteOrganiser organiser;
	private DynamicSiteOrganiser dynamicOrganiser;
	private String upSince = "---";
	private ConsignorProducer consignorProducer;
	private GatewayProperties gatewayProperties;
	private AuthnAndTrustProperties securityProperties;
	private GatewayHttpServerProperties jettyProperties;
	private HttpClientFactory clientFactory;
	private URI hostURI;
	private final ScheduledExecutorService executorService;
	
	public Gateway(File mainProperties, File connections, File secProperties) throws Exception
	{
		executorService=Executors.newSingleThreadScheduledExecutor();
		configureGateway(mainProperties, connections, secProperties);
	}

	private void configureGateway(File mainProperties, File connections, File secProperties) 
		throws Exception
	{
		String message="UNICORE Gateway "+RELEASE_VERSION+" starting.";
		log.info(message);
		System.out.println(message);

		gatewayProperties=new GatewayProperties(mainProperties);
		jettyProperties = new GatewayHttpServerProperties(mainProperties);
		if(secProperties == null) {
			secProperties = mainProperties;
		}
		securityProperties = new AuthnAndTrustProperties(secProperties, 
				GatewayProperties.PREFIX +TruststoreProperties.DEFAULT_PREFIX, 
				GatewayProperties.PREFIX +CredentialProperties.DEFAULT_PREFIX);
		
		String host = gatewayProperties.getHostname();
		String externalHostName = gatewayProperties.getExternalHostname();
		if (externalHostName!=null) 
		{
			host = externalHostName;
			log.info("Using '"+externalHostName+"' as gateway address.");
		}
		clientFactory = new HttpClientFactory(securityProperties, gatewayProperties);
		hostURI = URI.create(host);
		organiser = new CompositeSiteOrganiser(this, connections);
		if (gatewayProperties.isDynamicRegistrationEnabled()){
			dynamicOrganiser=new DynamicSiteOrganiser(this, 
				gatewayProperties.getRegistrationExcludes(), gatewayProperties.getRegistrationIncludes());
			organiser.addSiteOrganiser(dynamicOrganiser);
		}
		log.info(organiser.toString());

		int tolerance = gatewayProperties.getConsTTol();
		int validity = gatewayProperties.getConsTVal();
		boolean doSign = gatewayProperties.isConsTSign();
		try
		{
			consignorProducer = new ConsignorProducer(doSign, tolerance, 
				validity, securityProperties);
		} catch (Exception e)
		{
			LogUtil.logException("Can't create ConsignorProducer instance.", e, log);
		}
	}

	
	public String upSince()
	{
		return upSince;
	}
	
	public SiteOrganiser getSiteOrganiser()
	{
		return organiser;
	}

	public DynamicSiteOrganiser getDynamicSiteOrganiser()
	{
		return dynamicOrganiser;
	}
	
	public ConsignorProducer getConsignorProducer() 
	{
		return consignorProducer;
	}

	public URI getHostURI() 
	{
		return hostURI;
	}

	public HttpClientFactory getClientFactory()
	{
		return clientFactory;
	}
	
	public GatewayProperties getProperties() 
	{
		return gatewayProperties;
	}
	
	public AuthnAndTrustProperties getSecurityProperties() 
	{
		return securityProperties;
	}

	public GatewayHttpServerProperties getJettyProperties()
	{
		return jettyProperties;
	}
	
	//the version of the gateway jar
	public static final String VERSION = Gateway.class.getPackage().getImplementationVersion() != null ? 
			 Gateway.class.getPackage().getImplementationVersion() : "DEVELOPMENT";

	//the UNICORE release
	public static final String RELEASE_VERSION = Gateway.class.getPackage().getSpecificationVersion() != null ? 
			 Gateway.class.getPackage().getSpecificationVersion() : "DEVELOPMENT";
			
	public final String getHeader() {
		String lineSep = System.getProperty("line.separator");
		String s = lineSep
				+ " _    _ _   _ _____ _____ ____  _____  ______"
				+ lineSep
				+ "| |  | | \\ | |_   _/ ____/ __ \\|  __ \\|  ____|"
				+ lineSep
				+ "| |  | |  \\| | | || |   | |  | | |__) | |__"
				+ lineSep
				+ "| |  | | . ` | | || |   | |  | |  _  /|  __|"
				+ lineSep
				+ "| |__| | |\\  |_| |_ |____ |__| | | \\ \\| |____"
				+ lineSep
				+ " \\____/|_| \\_|_____\\_____\\____/|_|  \\_\\______|"
				+ lineSep + "UNICORE Gateway version "  + RELEASE_VERSION+ ", https://www.unicore.eu";
		return s;
	}
	

	public void startGateway() throws Exception
	{
		String message=getHeader();
		log.info(message);
		System.out.println(message);
		
		startLogConfigWatcher();
		jetty = new GatewayJettyServer(this);
		jetty.start();
		upSince = new Date().toString();
		
		message = "UNICORE Gateway startup complete.";
		log.info(message);
		System.out.println(message);
	}	

	public void stopGateway() throws Exception
	{
		executorService.shutdownNow();
		jetty.stop();
		String message = "UNICORE Gateway stopped.";
		log.info(message);
		System.out.println(message);
	}	
	
	public ScheduledExecutorService getExecutorService(){
		return executorService;
	}
	
	/**
	 * sets up a watchdog that checks for changes to the log4j configuration file,
	 * and re-configures log4j if that file has changed
	 */
	private void startLogConfigWatcher()
	{
		final String logConfig = System.getProperty("log4j.configuration");
		if (logConfig == null)
		{
			log.warn("No log configuration file set.");
			return;
		}
		try
		{
			Runnable r = new Runnable()
			{
				public void run()
				{
					try{
						reConfigureLog4j(logConfig);
					}
					catch(MalformedURLException me){
						throw new RuntimeException(me);
					}
				}
			};
			File logProperties=logConfig.startsWith("file:")?new File(new URI(logConfig)):new File(logConfig);
			FileWatcher fw = new FileWatcher(logProperties,r);
			executorService.scheduleWithFixedDelay(fw, 5, 5, TimeUnit.SECONDS);
		} catch(FileNotFoundException fex)
		{
			log.warn("Log configuration file <"+logConfig+"> not found.");
		}
		catch(URISyntaxException use)
		{
			log.warn("Not a valid URI: <"+logConfig+">");
		}
	}

	/**
	 * re-configure log4j from the named properties file
	 */
	public static void reConfigureLog4j(String logConfig)throws MalformedURLException{
		log.info("LOG CONFIG MODIFIED, re-configuring.");
		if(logConfig.startsWith("file:")){
			PropertyConfigurator.configure(new URL(logConfig));	
		}else{
			PropertyConfigurator.configure(logConfig);
		}
	}
	
	public static void main(String[] args) throws Exception
	{
		File gwProperties = GatewayProperties.FILE_GATEWAY_PROPERTIES;
		File connProperties = ConnectionsProperties.FILE_CONNECTIONS_PROPERTIES;
		File secProperties = null;
		if (args.length > 0)
			gwProperties = new File(args[0]);
		if (args.length > 1)
			connProperties = new File(args[1]);
		if (args.length > 2)
			secProperties = new File(args[2]);
		try
		{
			Gateway instance = new Gateway(gwProperties, connProperties, secProperties);
			instance.startGateway();
		} catch(Exception e)
		{
			log.fatal("FATAL ERROR starting the Gateway, exiting: " + e.toString());
			System.err.println("FATAL ERROR starting the Gateway, exiting: " + e.toString());
			System.exit(-1);
		}
	}	
}
