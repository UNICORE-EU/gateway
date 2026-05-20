package eu.unicore.gateway;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Map;

import eu.unicore.gateway.cluster.MultiSite;

public class SiteFactory {

	private SiteFactory(){};
	
	public static Site buildSite(URI hostURI, String siteName, String siteDesc, Map<String, String> meta)
			throws URISyntaxException,UnknownHostException,IOException{
		
		if(siteDesc.startsWith("multisite:")){
			return new MultiSite(hostURI, siteName, siteDesc, meta);
		}
		else{
			return new VSite(hostURI, siteName, siteDesc, meta);
		}
	}
}
