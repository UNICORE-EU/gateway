package eu.unicore.gateway;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Date;

import org.apache.logging.log4j.Logger;

import eu.unicore.gateway.base.GatewayJettyServer;
import eu.unicore.gateway.client.HttpClientFactory;
import eu.unicore.gateway.properties.ConnectionsProperties;
import eu.unicore.gateway.properties.GatewayHttpServerProperties;
import eu.unicore.gateway.properties.GatewayProperties;
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
	private final String upSince;
	private ConsignorProducer consignorProducer;
	private GatewayProperties gatewayProperties;
	private AuthnAndTrustProperties securityProperties;
	private GatewayHttpServerProperties jettyProperties;
	private HttpClientFactory clientFactory;
	private URI hostURI;

	private File _securityConfigFile;

	public Gateway(File mainProperties, File connections, File secProperties) throws Exception
	{
		configureGateway(mainProperties, connections, secProperties);
		upSince = new Date().toString();
	}

	private void configureGateway(File mainProperties, File connections, File secProperties) 
		throws Exception
	{
		String message="UNICORE Gateway "+RELEASE_VERSION+" starting.";
		log.info(message);
		System.out.println(message);
		gatewayProperties=new GatewayProperties(mainProperties);
		jettyProperties = new GatewayHttpServerProperties(mainProperties);
		_securityConfigFile = secProperties;
		if(secProperties == null) {
			_securityConfigFile = mainProperties;
		}
		String host = gatewayProperties.getHostname();
		String externalHostName = gatewayProperties.getExternalHostname();
		if (externalHostName!=null) 
		{
			host = externalHostName;
			log.info("Using '{}' as gateway address.", externalHostName);
		}
		hostURI = URI.create(host);
		configureSecurity();
		organiser = new CompositeSiteOrganiser(this, connections);
		if (gatewayProperties.isDynamicRegistrationEnabled()){
			dynamicOrganiser=new DynamicSiteOrganiser(this, 
				gatewayProperties.getRegistrationExcludes(),
				gatewayProperties.getRegistrationIncludes());
			organiser.addSiteOrganiser(dynamicOrganiser);
		}
		log.info(organiser.toString());
	}

	public void configureSecurity() throws Exception {
		securityProperties = new AuthnAndTrustProperties(_securityConfigFile,
				GatewayProperties.PREFIX + TruststoreProperties.DEFAULT_PREFIX,
				GatewayProperties.PREFIX + CredentialProperties.DEFAULT_PREFIX);
		int tolerance = gatewayProperties.getConsTTol();
		int validity = gatewayProperties.getConsTVal();
		boolean doSign = gatewayProperties.isConsTSign();
		consignorProducer = new ConsignorProducer(doSign, tolerance,
				validity, securityProperties);
		clientFactory = new HttpClientFactory(securityProperties, gatewayProperties);
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

	public void reloadConfig() {
		getSiteOrganiser().reloadConfig();
		getDynamicSiteOrganiser().reloadConfig();
		try{
			consignorProducer.reinit(securityProperties);
		}catch(Exception e) {
			log.error("Error reloading consignorProducer", e);
		}	
		try {
			clientFactory = new HttpClientFactory(securityProperties, gatewayProperties);
		}catch(Exception e) {
			log.error("Error reloading clientFactory config", e);
		}
	}

	private URL getAcmeHttpURL(URL mainURL) throws MalformedURLException {
		int port = gatewayProperties.getIntValue(GatewayProperties.KEY_ACME_HTTP_PORT);
		return new URL("http://"+mainURL.getHost()+":"+port);
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
		URL mainURL = new URL(gatewayProperties.getHostname());
		URL[] urls = new URL[] { mainURL };
		if(gatewayProperties.getBooleanValue(GatewayProperties.KEY_ACME_ENABLE)) {
			urls = new URL[] { mainURL , getAcmeHttpURL(mainURL)};
		}
		jetty = new GatewayJettyServer(urls, this);
		jetty.start();
		
		message = "UNICORE Gateway startup complete.";
		log.info(message);
		System.out.println(message);
	}	

	public void stopGateway() throws Exception
	{
		jetty.stop();
		String message = "UNICORE Gateway stopped.";
		log.info(message);
		System.out.println(message);
	}	

	/**
	 * warn if no log4j2 config is set
	 */
	static void checkLogSystem() {
		if(System.getProperty("log4j.configurationFile")==null) {
			System.err.println("***");
			if(System.getProperty("log4j.configuration")!=null) {
				System.err.println("*** Outdated log4j configuration - logging might not work as expected.");
				System.err.println("*** Please remove -Dlog4j.configuration=...");
			}
			else {
				System.err.println("*** NO log4j configuration set - will use defaults.");
			}
			System.err.println("***");
			System.err.println("*** Please configure log4j with -Dlog4j.configurationFile=file:/path/to/config");
			System.err.println("*** and check the manual or server distribution for example configurations.");
			System.err.println("***");
		}
	}

	public static void main(String[] args) throws Exception
	{
		checkLogSystem();
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
			log.fatal("FATAL ERROR starting the Gateway, exiting", e);
			System.err.println("FATAL ERROR starting the Gateway, exiting");
			e.printStackTrace();
			System.exit(1);
		}
	}	
}
