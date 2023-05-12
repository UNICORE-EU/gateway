package eu.unicore.gateway.properties;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

import eu.unicore.util.Log;
import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.configuration.FilePropertiesHelper;



public class ConnectionsProperties extends FilePropertiesHelper
{
	public static final Logger log = Log.getLogger(Log.CONFIGURATION, ConnectionsProperties.class);
	public static final File FILE_CONNECTIONS_PROPERTIES = new File(
		"conf" + File.separator + "connections.properties");

	public ConnectionsProperties(File f) throws ConfigurationException, IOException
	{
		super("", f, null, log);
	}
	
	public Iterator<Object> getEntries()
	{
		return properties.keySet().iterator();
	}
	
	public String getSite(String name)
	{
		return properties.getProperty(name, null);
	}
	
	@Override
	protected void checkConstraints(Properties props)
	{
		//Do nothing - those properties are not constrained and keys are unknown
	}
	
	@Override
	protected void findUnknown(Properties props)
	{
		//Do nothing - those properties are not constrained and keys are unknown
	}
}
