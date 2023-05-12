package eu.unicore.gateway;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Iterator;

import org.apache.logging.log4j.Logger;

import eu.unicore.gateway.properties.ConnectionsProperties;
import eu.unicore.gateway.util.LogUtil;
import eu.unicore.util.configuration.ConfigurationException;

/**
 * reads a connections file to build up the vsite set
 */
public class StaticSiteOrganiser extends BaseSiteOrganiser
{
	private static final Logger log = LogUtil.getLogger(LogUtil.GATEWAY, 
		StaticSiteOrganiser.class);
	private ConnectionsProperties props;

	
	public StaticSiteOrganiser(Gateway gw, File connections) throws ConfigurationException, IOException
	{      
		super(gw);
		props = new ConnectionsProperties(connections);
		readConnectionsFile();
	}

	@Override
	public Collection<Site> getSites() {
		rereadConnectionsFile();
		return super.getSites();
	}

	@Override
	public VSite match(String wsato, String clientIP) {
		rereadConnectionsFile();
		return super.match(wsato, clientIP);
	}

	private void rereadConnectionsFile()
	{
		try
		{
			if(!props.reloadIfChanged())
				return;
		} catch (IOException e1)
		{
			LogUtil.logException("I/O problem reading connections file", e1, log);
		}
		readConnectionsFile();
	}
	
	private void readConnectionsFile()
	{
		log.info("Reading connections file.");
		synchronized(sites){
			sites.clear();
			Iterator<?> it = props.getEntries();
			while (it.hasNext())
			{
				String siteName = (String) it.next();
				String addr = props.getSite(siteName); 
				try
				{
					Site site = SiteFactory.buildSite(gateway.getHostURI(), siteName, addr,	gateway.getSecurityProperties());
					if(sites.put(siteName,site)==null){
						log.info("Added site : "+site.toString());	
					}
				}
				catch (URISyntaxException e)
				{
					LogUtil.logException("cannot parse " + addr + " from the connections file. ingnoring this entry.",e,log);
				}
				catch (UnknownHostException e)
				{
					LogUtil.logException("cannot get a Inetaddress for " + addr,e,log);
				}
				catch (IOException e)
				{
					LogUtil.logException("I/O problem",e,log);
				}
			}
		}
	}
}



