package eu.unicore.gateway.client;

import java.net.URL;
import java.util.Properties;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
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
 * Additionally EXPECT_CONTINUE is set on the client and default host address and port too,
 * as gateway code uses relative URLs when invoking HTTP methods.
 * <p>
 * The class contains also convenience methods to create a configured PostMethod object.
 *   
 * @author K. Benedyczak
 */
public class HttpClientFactory
{
	private static final Logger log = Log.getLogger(LogUtil.GATEWAY, HttpClientFactory.class); 

	private final boolean keepAlive;
	private final boolean useExpectContinue;
	private final boolean enableGzip;
	private final HttpClientProperties clientProperties;
	private final DefaultClientConfiguration clientCfg;

	public HttpClientFactory(IAuthnAndTrustConfiguration securityprops, GatewayProperties props) throws Exception {
		Properties properties = new Properties();
		properties.setProperty(HttpClientProperties.CONNECT_TIMEOUT, ""+props.getConnectionTimeout());
		properties.setProperty(HttpClientProperties.SO_TIMEOUT, ""+props.getSocketTimeout());
		properties.setProperty(HttpClientProperties.MAX_HOST_CONNECTIONS, ""+props.getMaxPerServiceConnections());
		properties.setProperty(HttpClientProperties.MAX_TOTAL_CONNECTIONS, ""+props.getMaxTotalConnections());
		clientProperties = new HttpClientProperties("", properties);
		clientCfg = new DefaultClientConfiguration(securityprops.getValidator(), 
				securityprops.getCredential());
		boolean sslEnabled = securityprops.getValidator() != null;
		clientCfg.setSslEnabled(sslEnabled);
		clientCfg.setSslAuthn(sslEnabled && securityprops.getCredential() != null);
		clientCfg.setHttpClientProperties(clientProperties);
		this.keepAlive=props.isKeepAlive();
		this.enableGzip=props.isGzipEnabled();
		this.useExpectContinue=props.isExpectContinueEnabled();
		log.debug("Configured Gateway's client factory: [ssl={} sslAuthn={}]",
				clientCfg.isSslEnabled(), clientCfg.doSSLAuthn());
	}

	public HttpClient makeHttpClient(URL url) throws Exception {
		return url.getProtocol().toLowerCase().equals("https") ?
			HttpUtils.createClient(url.toString(), clientCfg):
			HttpUtils.createClient(clientProperties);
	}

	public HttpPost makePostMethod(String path, AbstractHttpEntity requestentity) {
		HttpPost post = new HttpPost(path);
		RequestConfig c = RequestConfig.custom().
				setExpectContinueEnabled(useExpectContinue).
				build();
		post.setConfig(c);
		post.setEntity(requestentity);
		if (enableGzip)
			post.setHeader("Accept-Encoding", "gzip");
		if (!keepAlive)
			post.setHeader("Connection", "close");
		return post;
	}

	public DefaultClientConfiguration getClientConfiguration() {
		return clientCfg;
	}

}