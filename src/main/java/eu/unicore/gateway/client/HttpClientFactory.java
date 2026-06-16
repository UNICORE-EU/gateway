package eu.unicore.gateway.client;

import java.net.URL;
import java.util.Properties;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.logging.log4j.Logger;

import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.util.LogUtil;
import eu.unicore.security.canl.IAuthnAndTrustConfiguration;
import eu.unicore.util.Log;
import eu.unicore.util.httpclient.DefaultClientConfiguration;
import eu.unicore.util.httpclient.HttpClientProperties;
import eu.unicore.util.httpclient.HttpUtils;

/**
 * Creates HttpClient for usage in gateway (generally when connecting to sites behind the gateway).
 * {@link HttpUtils} is used to perform the job, it is configured here from gateway properties.
 *  
 * @author K. Benedyczak
 */
public class HttpClientFactory
{
	private static final Logger log = Log.getLogger(LogUtil.GATEWAY, HttpClientFactory.class); 

	private final HttpClientProperties clientProperties;

	private final DefaultClientConfiguration clientCfg;

	public HttpClientFactory(IAuthnAndTrustConfiguration securityprops, GatewayProperties props) throws Exception {
		Properties properties = new Properties();
		properties.setProperty(HttpClientProperties.CONNECT_TIMEOUT, ""+props.getConnectionTimeout());
		properties.setProperty(HttpClientProperties.SO_TIMEOUT, ""+props.getSocketTimeout());
		properties.setProperty(HttpClientProperties.MAX_HOST_CONNECTIONS, ""+props.getMaxPerServiceConnections());
		properties.setProperty(HttpClientProperties.MAX_TOTAL_CONNECTIONS, ""+props.getMaxTotalConnections());
		properties.setProperty(HttpClientProperties.CONNECTION_CLOSE, Boolean.toString(!props.isKeepAlive()));
		clientProperties = new HttpClientProperties("", properties);
		clientCfg = new DefaultClientConfiguration(securityprops.getValidator(), 
				securityprops.getCredential());
		boolean sslEnabled = securityprops.getValidator() != null;
		clientCfg.setSslEnabled(sslEnabled);
		clientCfg.setSslAuthn(sslEnabled && securityprops.getCredential() != null);
		clientCfg.setHttpClientProperties(clientProperties);
		log.debug("Configured Gateway's client factory: [ssl={} sslAuthn={}]",
				clientCfg.isSslEnabled(), clientCfg.doSSLAuthn());
	}

	/**
	 * create a "one-shot" client that does not pool connections
	 *
	 * @param url
	 */
	public CloseableHttpClient client(URL url) throws Exception {
		return HttpUtils.client(url.toString(), clientCfg);
	}

	/**
	 * create a client that uses connection pool configured according to the gateway settings
	 * @param url
	 */
	public HttpClient pooledClient(URL url) throws Exception {
		PoolingHttpClientConnectionManager connMgr = HttpUtils.createPoolingConnectionManager(clientCfg);
		return HttpUtils.createClient(url.toString(), clientCfg, connMgr, false);
	}

	public DefaultClientConfiguration getClientConfiguration() {
		return clientCfg;
	}

}