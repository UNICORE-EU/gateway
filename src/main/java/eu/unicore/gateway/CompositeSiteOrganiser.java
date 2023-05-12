package eu.unicore.gateway;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

	/**
	 * merge site lists from all registered organisers
	 */
	@Override
	public Set<Site> getSites() {
		Set<Site>result = new HashSet<>();
		for(SiteOrganiser so: siteOrganisers){
			if(so.getSites()!=null)
				result.addAll(so.getSites());
		}
		if(super.getSites()!=null)result.addAll(super.getSites());
		return result;
	}

}
