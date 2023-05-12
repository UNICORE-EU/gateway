package eu.unicore.gateway.properties;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.configuration.DocumentationReferenceMeta;
import eu.unicore.util.configuration.DocumentationReferencePrefix;
import eu.unicore.util.configuration.FilePropertiesHelper;
import eu.unicore.util.configuration.PropertyMD;
import eu.unicore.util.jetty.HttpServerProperties;


public class GatewayHttpServerProperties extends HttpServerProperties
{
	public static final File FILE_JETTY_PROPERTIES = GatewayProperties.FILE_GATEWAY_PROPERTIES;

	/**
	 * deprecated NIO property
	 */
	@Deprecated
	public static final String USE_NIO = "useNIO";

	@DocumentationReferencePrefix
	public static final String PREFIX = GatewayProperties.PREFIX + HttpServerProperties.DEFAULT_PREFIX;
	
	@DocumentationReferenceMeta
	protected final static Map<String, PropertyMD> defaults = new HashMap<>();
	static 
	{
		defaults.put(USE_NIO, new PropertyMD("true").setDescription(
				"DEPRECATED, no effect"));
		defaults.putAll(HttpServerProperties.defaults);
	}
	
	public GatewayHttpServerProperties(String name) throws ConfigurationException, IOException
	{
		this(new File(name));
	}

	public GatewayHttpServerProperties(File f) throws ConfigurationException, IOException
	{
		super(FilePropertiesHelper.load(f), PREFIX, defaults);
		
	}
}
