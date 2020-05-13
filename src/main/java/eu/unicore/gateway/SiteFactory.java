package eu.unicore.gateway;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import eu.unicore.gateway.cluster.MultiSite;
import eu.unicore.security.canl.AuthnAndTrustProperties;

public class SiteFactory {

	private SiteFactory(){};
	
	public static Site buildSite(URI hostURI, 
			String siteName, 
			String siteDesc, 
			AuthnAndTrustProperties securityCfg) throws URISyntaxException,UnknownHostException,IOException{
		
		if(siteDesc.startsWith("multisite:")){
			return new MultiSite(hostURI, siteName, siteDesc, securityCfg);
		}
		else{
			return new VSite(hostURI, siteName, siteDesc, securityCfg);
		}
	}
}
