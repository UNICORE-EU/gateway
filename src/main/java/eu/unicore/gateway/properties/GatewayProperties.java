package eu.unicore.gateway.properties;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import eu.unicore.gateway.util.LogUtil;
import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.configuration.DocumentationReferenceMeta;
import eu.unicore.util.configuration.DocumentationReferencePrefix;
import eu.unicore.util.configuration.FilePropertiesHelper;
import eu.unicore.util.configuration.PropertyMD;
import eu.unicore.util.configuration.PropertyMD.DocumentationCategory;
import eu.unicore.util.jetty.HttpServerProperties;

public class GatewayProperties extends FilePropertiesHelper
{
	private static final Logger log = LogUtil.getLogger(LogUtil.CONFIGURATION,
		GatewayProperties.class);

	public static final File FILE_GATEWAY_PROPERTIES = new File("conf" + File.separator
		+ "gateway.properties");

	public static final String KEY_HOSTNAME = "hostname";
	public static final String KEY_CONSIGNORT_TOLERANCE = "consignorTokenTimeTolerance";
	public static final String KEY_CONSIGNORT_VALIDITY = "consignorTokenValidity";
	public static final String KEY_CONSIGNORT_SIGN = "signConsignorToken";
	public static final String KEY_MAX_HEADER = "soapMaxHeader";
	
	public static final String KEY_EXTERNAL_ADDRESS = "externalHostname";
	public static final String KEY_WEBPAGE_DISABLE = "disableWebpage";
	public static final String KEY_REG_ENABLED = "registration.enable";
	public static final String KEY_REG_INCL = "registration.allow";
	public static final String KEY_REG_EXCL = "registration.deny";
	public static final String KEY_REG_SECRET = "registration.secret";

	public static final String KEY_SOCKET_TIMEOUT = "client.socketTimeout";
	public static final String KEY_CHUNKED = "client.chunked";
	public static final String KEY_CONN_TIMEOUT = "client.connectionTimeout";
	public static final String KEY_CONN_KEEPALIVE = "client.keepAlive";
	public static final String KEY_CONN_GZIP = "client.gzip";
	public static final String KEY_PROTO_EXPECTCONTINUE = "client.expectContinue";
	public static final String KEY_CONN_MAX_TOTAL = "client.maxTotal";
	public static final String KEY_CONN_MAX_PERHOST = "client.maxPerService";

	
	public static final File FILE_JETTY_PROPERTIES = GatewayProperties.FILE_GATEWAY_PROPERTIES;

	@DocumentationReferencePrefix
	public static final String PREFIX="gateway.";
	
	@DocumentationReferenceMeta
	public static final Map<String, PropertyMD> DEFAULTS = new HashMap<>();
	public static final int DEFAULT_MAX_HDR = 102400;
	static 
	{
		DocumentationCategory consigCat = new DocumentationCategory("Passing Consignor info", "1");
		DocumentationCategory cliCat = new DocumentationCategory("Gateway -> Site client", "2");
		DocumentationCategory advCat = new DocumentationCategory("Advanced", "3");
		
		DEFAULTS.put(KEY_HOSTNAME, new PropertyMD().setMandatory().
				setDescription("external gateway bind address"));
		DEFAULTS.put(KEY_REG_ENABLED, 		new PropertyMD("false").setDescription(
				"Whether dynamic registration of sites is enabled."));
		DEFAULTS.put(KEY_REG_EXCL, 		new PropertyMD().setDescription(
				"Space separated list of denied hosts for dynamic registration."));
		DEFAULTS.put(KEY_REG_INCL, 		new PropertyMD().setDescription(
				"Space separated list of allowed hosts for dynamic registration."));
		DEFAULTS.put(KEY_REG_SECRET, 		new PropertyMD().setDescription(
				"Required secret for dynamic registration."));

		DEFAULTS.put(KEY_WEBPAGE_DISABLE, 	new PropertyMD("false").setCategory(advCat).setDescription(
				"Whether the (so called monkey) status web page should be disabled."));
		DEFAULTS.put(KEY_EXTERNAL_ADDRESS, 	new PropertyMD((String)null).setCategory(advCat).setDescription(
				"External address of the gateway, when it is accessible through a frontend server as Apache HTTP."));
		DEFAULTS.put(KEY_MAX_HEADER, 		new PropertyMD(DEFAULT_MAX_HDR+"").
				setBounds(1024, 1024000000).setCategory(advCat).setDescription(
				"Size in bytes of the accepted SOAP header. In the most cases you don't need to change it."));
		DEFAULTS.put(KEY_CONN_MAX_PERHOST, 	new PropertyMD("20").setCategory(cliCat).setDescription(
				"Maximum allowed number of connections per backend site."));
		DEFAULTS.put(KEY_CONN_MAX_TOTAL, 	new PropertyMD("100").setCategory(cliCat).setDescription(
				"Maximum total number of connections to backend sites allowed."));
		DEFAULTS.put(KEY_CHUNKED, 		new PropertyMD("true").setCategory(cliCat).setDescription(
				"Controls whether chunked passing of HTTP requests to backend sites is supported."));
		DEFAULTS.put(KEY_CONN_GZIP, 		new PropertyMD("true").setCategory(cliCat).setDescription(
				"Controls whether support for compression is announced to backend sites."));
		DEFAULTS.put(KEY_CONN_KEEPALIVE, 	new PropertyMD("true").setCategory(cliCat).setDescription(
				"Whether to keep alive the connections to backend sites."));
		DEFAULTS.put(KEY_CONN_TIMEOUT, 		new PropertyMD("30000").setCategory(cliCat).setDescription(
				"Connection timeout, used when connecting to backend sites."));
		DEFAULTS.put(KEY_SOCKET_TIMEOUT, 	new PropertyMD("30000").setCategory(cliCat).setDescription(
				"Connection timeout, used when connecting to backend sites."));
		DEFAULTS.put(KEY_PROTO_EXPECTCONTINUE, 	new PropertyMD("true").setCategory(cliCat).setDescription(
				"Controls whether the HTTP expect-continue mechanism is enabled on connections to backend sites."));

		DEFAULTS.put(KEY_CONSIGNORT_SIGN, 	new PropertyMD("false").setCategory(consigCat).setDescription(
				"Controls whether information about the authenticated client (the consignor) passed to backend sites should be signed, or not. Signing is slower, but is required when sites may be reached directly, bypassing the Gateway."));
		DEFAULTS.put(KEY_CONSIGNORT_TOLERANCE, 	new PropertyMD("30").setNonNegative().setCategory(consigCat).setDescription(
				"The validity time of the authenticated client information passed to backend sites will start that many seconds before the real authentication. It is used to mask time synchronization problems between machines."));
		DEFAULTS.put(KEY_CONSIGNORT_VALIDITY, 	new PropertyMD("60").setPositive().setCategory(consigCat).setDescription(
				"What is the validity time of the authenticated client information passed to backend sites. Increase it if there machines clocks are not synhronized."));

		DEFAULTS.put(HttpServerProperties.DEFAULT_PREFIX, new PropertyMD().setCanHaveSubkeys().setHidden().
				setDescription("Properties with this prefix are used to configure advanced Gateway's Jetty HTTP server settings. See separate documentation."));
		
		DEFAULTS.put("credential", new PropertyMD().setCanHaveSubkeys().setHidden().
				setDescription("Properties with this prefix are used to configure the Gateway's SSL credential. See separate documentation."));
		DEFAULTS.put("truststore", new PropertyMD().setCanHaveSubkeys().setHidden().
				setDescription("Properties with this prefix are used to configure the Gateway's SSL truststore. See separate documentation."));
	}


	public GatewayProperties(String name) throws ConfigurationException, IOException
	{
		this(new File(name));
	}

	public GatewayProperties(File f) throws ConfigurationException, IOException
	{
		super(PREFIX, f, DEFAULTS, log);
	}

	
	public int getSocketTimeout()
	{
		return getIntValue(KEY_SOCKET_TIMEOUT);
	}
	
	public int getConnectionTimeout()
	{
		return getIntValue(KEY_CONN_TIMEOUT);
	}

	public boolean isKeepAlive()
	{
		return getBooleanValue(KEY_CONN_KEEPALIVE);
	}

	public boolean isGzipEnabled()
	{
		return getBooleanValue(KEY_CONN_GZIP);
	}
	
	public boolean isExpectContinueEnabled()
	{
		return getBooleanValue(KEY_PROTO_EXPECTCONTINUE);
	}

	public int getMaxTotalConnections()
	{
		return getIntValue(KEY_CONN_MAX_TOTAL);
	}
	public int getMaxPerServiceConnections()
	{
		return getIntValue(KEY_CONN_MAX_PERHOST);
	}	
	
	public String getHostname()
	{
		return getValue(GatewayProperties.KEY_HOSTNAME);
	}
	
	public String getExternalHostname()
	{
		return getValue(GatewayProperties.KEY_EXTERNAL_ADDRESS);
	}
	
	public int getConsTTol()
	{
		return getIntValue(KEY_CONSIGNORT_TOLERANCE);
	}
	
	public int getConsTVal()
	{
		return getIntValue(KEY_CONSIGNORT_VALIDITY);
	}
	
	public boolean isConsTSign()
	{
		return getBooleanValue(KEY_CONSIGNORT_SIGN);
	}
	
	public boolean isDynamicRegistrationEnabled() 
	{
		boolean enabled = getBooleanValue(KEY_REG_ENABLED);
		if(enabled) {
			if(getValue(KEY_REG_SECRET)==null) {
				log.error("Dynamic registration enabled but no secret is configured - disabling.");
				properties.put(PREFIX+KEY_REG_ENABLED, "false");
				enabled = false;
			}
		}
		return enabled;
	}
	
	public boolean isDetailedWebPageDisabled() 
	{
		return getBooleanValue(KEY_WEBPAGE_DISABLE);
	}
	
	public boolean isChunkedDispatch()
	{
		return getBooleanValue(KEY_CHUNKED);
	}
	
	public String getRegistrationExcludes()
	{
		return getValue(KEY_REG_EXCL);
	}

	public String getRegistrationIncludes()
	{
		return getValue(KEY_REG_INCL);
	}
	
	public int getMaxSoapHeader()
	{
		return getIntValue(KEY_MAX_HEADER);
	}
}
