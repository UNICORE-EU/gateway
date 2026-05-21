package eu.unicore.gateway;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import eu.unicore.util.configuration.ConfigurationException;


/**
 * allows adding additional SiteOrganiser implementations 
 * on top of the StaticSiteOrganiser
 * 
 * @author schuller
 */
public class CompositeSiteOrganiser extends StaticSiteOrganiser {

	private final List<SiteOrganiser> siteOrganisers;

	public CompositeSiteOrganiser(Gateway gw, File properties) throws ConfigurationException, IOException{
		super(gw, properties);
		siteOrganisers = new ArrayList<>();
	}

	public void addSiteOrganiser(SiteOrganiser siteOrganiser){
		siteOrganisers.add(siteOrganiser);
	} 

	@Override
	public Site getSite(String name) {
		for(SiteOrganiser so: siteOrganisers){
			Site s = so.getSite(name);
			if(s!=null)return s;
		}
		return super.getSite(name);
	}

	/**
	 * merge site lists from all registered organisers
	 */
	@Override
	public Collection<Site> getSites() {
		Collection<Site>result = new HashSet<>();
		result.addAll(super.getSites());
		for(SiteOrganiser so: siteOrganisers){
			if(so.getSites()!=null)
				result.addAll(so.getSites());
		}
		return result;
	}

}
