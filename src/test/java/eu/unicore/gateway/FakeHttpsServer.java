package eu.unicore.gateway;

import java.net.URL;
import java.util.Properties;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.servlet.ServletContextHandler;

import eu.unicore.security.canl.AuthnAndTrustProperties;
import eu.unicore.security.canl.CredentialProperties;
import eu.unicore.security.canl.IAuthnAndTrustConfiguration;
import eu.unicore.security.canl.TruststoreProperties;
import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.jetty.HttpServerProperties;
import eu.unicore.util.jetty.JettyServerBase;

public class FakeHttpsServer extends JettyServerBase
{

	public FakeHttpsServer(URL listenUrl, IAuthnAndTrustConfiguration secConfiguration,
			HttpServerProperties extraSettings) throws ConfigurationException
	{
		super(listenUrl, secConfiguration, extraSettings);
		initServer();
		((ServletContextHandler)getRootHandler()).addServlet(FakeServlet.class.getName(), "/service");
	}

	@Override
	protected Handler createRootHandler() throws ConfigurationException {
		ServletContextHandler root = new ServletContextHandler(getServer(), "/", ServletContextHandler.SESSIONS); 
		return root; 
	}
	
	public static Properties getSecureProperties()
	{
		Properties p = new Properties();
		p.setProperty("j." + HttpServerProperties.FAST_RANDOM, "true");
		
		p.setProperty("k." + CredentialProperties.PROP_LOCATION, "src/test/resources/certs/gateway.jks");
		p.setProperty("k." + CredentialProperties.PROP_FORMAT, "JKS");
		p.setProperty("k." + CredentialProperties.PROP_PASSWORD, "the!gateway");
		p.setProperty("t." + TruststoreProperties.PROP_TYPE, 
				TruststoreProperties.TruststoreType.keystore.toString());
		p.setProperty("t." + TruststoreProperties.PROP_KS_PATH, "src/test/resources/certs/gateway-truststore.jks");
		p.setProperty("t." + TruststoreProperties.PROP_KS_TYPE, "JKS");
		p.setProperty("t." + TruststoreProperties.PROP_KS_PASSWORD, "the!gateway");
		p.setProperty("t." + TruststoreProperties.PROP_UPDATE, "-1");
		return p;
	}
	
	public static FakeHttpsServer getInstance() throws Exception {
		Properties p = getSecureProperties();
		int port = 65437;
		String host = "127.0.0.1";
		URL url = new URL("https://" + host + ":" + port);
	
		AuthnAndTrustProperties secCfg = new AuthnAndTrustProperties(p, "t.", "k.");
		HttpServerProperties extra = new HttpServerProperties(p, "j.");
		return new FakeHttpsServer(url, secCfg, extra);
	}
}
