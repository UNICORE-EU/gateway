package eu.unicore.gateway.properties;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

import eu.unicore.util.Log;

/**
 * Provides access to the Gateway connections file, which contains the list
 * of configured back-end VSites
 */
public class ConnectionsProperties
{

	public static final Logger log = Log.getLogger(Log.CONFIGURATION, ConnectionsProperties.class);

	public static final File FILE_CONNECTIONS_PROPERTIES = new File("conf", "connections.properties");

	private volatile long lastAccess;
	private File file;
	private Properties properties;

	volatile private Map<String, Map<String,String>> siteInfo = new HashMap<>();

	static final String addressKey = "__address__";

	public ConnectionsProperties(File f) throws IOException
	{
		this.file = f;
		reloadIfChanged();
	}
	
	public boolean reloadIfChanged() throws IOException {
		if(!hasChanged()) {
			return false;
		}
		else {
			load();
			Map<String, Map<String,String>> newSiteMap = new HashMap<>();
			// read sites and addresses first
			for(Object _k: properties.keySet()) {
				String k = _k.toString();
				if(!k.contains(".")) {
					// it is basic site definition: "site = address"
					String addr = properties.getProperty(k);
					Map<String,String>info = new HashMap<>();
					info.put(addressKey, addr);
					newSiteMap.put(k, info);
					System.out.println(newSiteMap);
				}
			}
			// second run: read any additional data
			for(Object _k: properties.keySet()) {
				String k = _k.toString();
				if(k.contains(".")) {
					// it is metadata: "site.key = value"
					String val = properties.getProperty(k);
					String[] tok = k.split("\\.", 2);
					String site = tok[0];
					System.out.println("site = "+site);
					String attrName = tok[1];
					Map<String,String>info = newSiteMap.get(site);
					if(info!=null) {
						info.put(attrName, val);
					}
				}
			}
			this.siteInfo = newSiteMap;
			lastAccess = file.lastModified();
			return true;
		}
	}

	public boolean hasChanged()
	{
		long fileMod = file.lastModified();
		return (lastAccess==0 || lastAccess<fileMod);
	}

	void load() throws IOException 
	{
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")))
		{
			Properties _properties = new Properties();
			_properties.load(reader);
			this.properties = _properties;
		}
	}

	public Collection<String> getSiteNames()
	{
		return siteInfo.keySet();
	}

	public String getSiteAddress(String name)
	{
		return siteInfo.get(name).get(addressKey);
	}

	public Map<String,String> getSiteInfo(String name)
	{
		return siteInfo.get(name);
	}
}
