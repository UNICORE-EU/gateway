/*********************************************************************************
 * Copyright (c) 2006-2007 Forschungszentrum Juelich GmbH 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * (1) Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the disclaimer at the end. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * 
 * (2) Neither the name of Forschungszentrum Juelich GmbH nor the names of its 
 * contributors may be used to endorse or promote products derived from this 
 * software without specific prior written permission.
 * 
 * DISCLAIMER
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ********************************************************************************/
 
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
		siteOrganisers=new ArrayList<SiteOrganiser>();
	}
	
	public void addSiteOrganiser(SiteOrganiser siteOrganiser){
		siteOrganisers.add(siteOrganiser);
	} 
	
	/**
	 * merge site lists from all registered organisers
	 */
	@Override
	public Set<Site> getSites() {
		Set<Site>result=new HashSet<Site>();
		for(SiteOrganiser so: siteOrganisers){
			if(so.getSites()!=null)
				result.addAll(so.getSites());
		}
		if(super.getSites()!=null)result.addAll(super.getSites());
		return result;
	}

		
}
