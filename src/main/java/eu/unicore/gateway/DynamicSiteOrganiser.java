package eu.unicore.gateway;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import org.apache.logging.log4j.Logger;

import eu.unicore.gateway.cluster.MultiSite;
import eu.unicore.gateway.util.LogUtil;

public class DynamicSiteOrganiser extends BaseSiteOrganiser 
{
	private static final Logger logger=LogUtil.getLogger(LogUtil.GATEWAY,DynamicSiteOrganiser.class);
	private final String exclPattern;
	private final String inclPattern;

	public DynamicSiteOrganiser(Gateway gw, String exclPattern, String inclPattern)
	{
		super(gw);
		this.exclPattern = exclPattern;
		this.inclPattern = inclPattern;
	}

	public boolean register(String name, URI realURI) throws UnknownHostException, URISyntaxException {
		try{
			checkExclusion(realURI);
			checkInclusion(realURI);
			Site existingSite = sites.get(name);
			if(existingSite!=null && existingSite instanceof MultiSite){
				((MultiSite)existingSite).registerVsite(realURI);
			}
			else{
				VSite newSite = new VSite(gateway.getHostURI(), name, realURI.toString(), gateway.getSecurityProperties());
				sites.put(name,newSite);
			}
			return true;
		}catch(IllegalArgumentException ae){
			logger.warn("Cannot register <{}> at <{}>: {}", name, realURI, ae.getMessage());
			return false;
		}
	}

	/**
	 * checks if the given URI is forbidden to register
	 * @param uri
	 */
	void checkExclusion(URI uri){
		if(exclPattern!=null) {
			String[]chk = exclPattern.split(" +");
			String test = uri.toString().toLowerCase();
			for(String pattern: chk){
				if(test.contains(pattern.toLowerCase())){
					throw new IllegalArgumentException("URL contains '"+pattern+"'");
				}
			}
		}
	}

	/**
	 * checks if the given URI is allowed to register
	 * @param uri
	 */
	void checkInclusion(URI uri){
		if(inclPattern!=null) {
			String[]chk = inclPattern.split(" +");
			String test = uri.toString().toLowerCase();
			for(String pattern: chk){
				if(test.contains(pattern.toLowerCase())){
					return;
				}
			}
			throw new IllegalArgumentException("URL does not contain one of '"+inclPattern+"'");
		}
	}

	// unit testing use
	void register(Site site){
		sites.put(site.getName(),site);
	}
}
