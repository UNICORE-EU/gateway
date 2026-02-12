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
	private AuthnAndTrustProperties clientPKISettings;
	private GatewayHttpServerProperties jettyProperties;
	private HttpClientFactory clientFactory;
	private URI hostURI;

	private File _securityConfigFile;

	public Gateway(File mainProperties, File connections) throws Exception
	{
		this(mainProperties, connections, null);
	}

	private Gateway(File mainProperties, File connections, File secProperties) throws Exception
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

	private void configureSecurity() throws Exception {
		securityProperties = new AuthnAndTrustProperties(_securityConfigFile,
				GatewayProperties.PREFIX + TruststoreProperties.DEFAULT_PREFIX,
				GatewayProperties.PREFIX + CredentialProperties.DEFAULT_PREFIX);
		// try to load separate client settings
		try {
			clientPKISettings = new AuthnAndTrustProperties(_securityConfigFile,
				"gateway.client.truststore.", "gateway.client.credential.");
				String dn = clientPKISettings.getCredential().getSubjectName();
				String issuer = clientPKISettings.getCredential().getCertificate().getIssuerX500Principal().getName();
				log.info("Using separate credential {} issued by {} for client calls.", dn, issuer);
		}catch(Exception e) {
			log.info("Using container's server credential/truststore for client calls.");
			clientPKISettings = securityProperties;
		}
		clientFactory = new HttpClientFactory(clientPKISettings, gatewayProperties);
		boolean doSign = gatewayProperties.isSignConsignor();
		consignorProducer = new ConsignorProducer(doSign, clientPKISettings);
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

	public GatewayJettyServer getJettyServer()
	{
		return jetty;
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
		try{
			consignorProducer.reinit(clientPKISettings);
		}catch(Exception e) {
			log.error("Error reloading consignorProducer", e);
		}	
		try {
			clientFactory = new HttpClientFactory(clientPKISettings, gatewayProperties);
		}catch(Exception e) {
			log.error("Error reloading clientFactory config", e);
		}
	}

	private URL getAcmeHttpURL(URL mainURL) throws MalformedURLException {
		return new URL("http://"+mainURL.getHost()+":"+gatewayProperties.getAcmePort());
	}

	//the version of the gateway jar
	public static final String VERSION = Gateway.class.getPackage().getImplementationVersion() != null ? 
			 Gateway.class.getPackage().getImplementationVersion() : "DEVELOPMENT";

	//the UNICORE release
	public static final String RELEASE_VERSION = Gateway.class.getPackage().getSpecificationVersion() != null ? 
			 Gateway.class.getPackage().getSpecificationVersion() : "DEVELOPMENT";
			
	public static final String getHeader() {
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
	

	public void start() throws Exception
	{
		URL mainURL = new URL(gatewayProperties.getHostname());
		URL[] urls = new URL[] { mainURL };
		if(gatewayProperties.isAcmeEnabled()) {
			log.info("Enabling ACME / Let's Encrypt support, token challenge directory: {}",
					gatewayProperties.getAcmeDirectory());
			boolean isSSL = gatewayProperties.getHostname().toLowerCase().startsWith("https");
			if(isSSL) {
				URL acmeURL = getAcmeHttpURL(mainURL);
				log.info("Adding ACME HTTP listener {}", acmeURL);
				// must add plain http listener for ACME
				urls = new URL[] { mainURL , getAcmeHttpURL(mainURL)};
			}
		}
		jetty = new GatewayJettyServer(urls, this);
		jetty.start();
		String message = "UNICORE Gateway startup complete.";
		log.info(message);
		System.out.println(message);
	}	

	public void stop() throws Exception
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
			System.err.println("*** NO log4j configuration set - will use defaults.");
			System.err.println("***");
			System.err.println("*** Please configure log4j with -Dlog4j.configurationFile=file:/path/to/config");
			System.err.println("*** and check the manual or server distribution for example configurations.");
			System.err.println("***");
		}
	}

	static Gateway instance;

	public static void main(String[] args) throws Exception
	{
		String message = getHeader();
		log.info(message);
		System.out.println(message);

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
		try {
			instance = new Gateway(gwProperties, connProperties, secProperties);
			instance.start();
		} catch(Exception e) {
			log.fatal("FATAL ERROR starting the Gateway, exiting", e);
			System.err.println("FATAL ERROR starting the Gateway, exiting");
			e.printStackTrace();
			System.exit(1);
		}
	}	
}