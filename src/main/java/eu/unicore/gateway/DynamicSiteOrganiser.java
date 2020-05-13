package eu.unicore.gateway;

import java.net.URI;
import org.apache.log4j.Logger;

import eu.unicore.gateway.cluster.MultiSite;
import eu.unicore.gateway.util.LogUtil;

public class DynamicSiteOrganiser extends BaseSiteOrganiser 
{
	private static final Logger logger=LogUtil.getLogger(LogUtil.GATEWAY,DynamicSiteOrganiser.class);
	private String exclPattern;
	private String inclPattern;
	
	protected DynamicSiteOrganiser(Gateway gw, String exclPattern, String inclPattern)
	{
		super(gw);
		this.exclPattern = exclPattern;
		this.inclPattern = inclPattern;
	}

	
	public boolean register(String name, URI realURI){
		try{
			checkExclusion(realURI);
			checkInclusion(realURI);
			Site existingSite=sites.get(name);
			if(existingSite!=null && existingSite instanceof MultiSite){
				((MultiSite)existingSite).registerVsite(realURI);
			}
			else{
				VSite newSite=new VSite(gateway.getHostURI(), name, realURI.toString(), gateway.getSecurityProperties());
				sites.put(name,newSite);
			}
			return true;
		}catch(IllegalArgumentException ae){
			logger.warn("Registration of <"+name+"> at "+realURI+" is not possible.",ae);
		}catch(Exception e){
			LogUtil.logException("Can't process registration of <"+name+"> at "+realURI,e,logger);
		}
		return false;
	}
	
	/**
	 * checks if the given URI is forbidden to register
	 * @param uri
	 */
	protected void checkExclusion(URI uri){
		if(exclPattern==null)return;
		//check if exclusion pattern
		String[]chk=exclPattern.split(" +");
		String test=uri.toString().toLowerCase();
		for(String pattern: chk){
			if(test.contains(pattern.toLowerCase())){
				//forbidden
				throw new IllegalArgumentException("Registration forbidden, URL contains '"+pattern+"'");
			}
		}
		return;
	}
	
	/**
	 * checks if the given URI is allowed to register
	 * @param uri
	 */
	protected void checkInclusion(URI uri){
		if(inclPattern==null)return;
		String[]chk=inclPattern.split(" +");
		String test=uri.toString().toLowerCase();
		for(String pattern: chk){
			if(test.contains(pattern.toLowerCase())){
				return;
			}
		}
		//not allowed
		throw new IllegalArgumentException("Registration not allowed, URL does not contain one of '"+inclPattern+"'");
	}
	

	//unit testing use
	void register(Site site){
		sites.put(site.getName(),site);
	}
}
