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

	@DocumentationReferencePrefix
	public static final String PREFIX = GatewayProperties.PREFIX + HttpServerProperties.DEFAULT_PREFIX;

	@DocumentationReferenceMeta
	protected final static Map<String, PropertyMD> defaults = new HashMap<>();
	static 
	{
		String[] deprecated = new String[] {"useNIO", "lowResourceMaxIdleTime", "highLoadConnections"};
		for(String s: deprecated) {
			defaults.put(s, new PropertyMD().setDescription("DEPRECATED, no effect"));
		}
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
