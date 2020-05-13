/**
 * Copyright (c) 2005, Forschungszentrum Juelich
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met: 
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *  * Neither the name of the Forschungszentrum Juelich nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.unicore.gateway;

import java.util.Collection;
import java.util.Formatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.unicore.gateway.cluster.MultiSite;

public abstract class BaseSiteOrganiser implements SiteOrganiser
{
	protected Gateway gateway;
	protected final Map<String,Site> sites = new ConcurrentHashMap<String,Site>();

	protected BaseSiteOrganiser(Gateway gw)
	{
		this.gateway = gw;
	}
	
	public Collection<Site> getSites()
	{
		return sites.values();
	}

	public VSite match(String wsato, String clientIP)
	{
		synchronized(sites){
			for (Site site : getSites())
			{
				if (site.accept(wsato))
				{
					return site.select(clientIP);
				}
			}
			return null;
		}
	}

	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb);
		for (Site site : getSites())
		{
			formatter.format(site.toString() + "\n");
		}
		formatter.close();
		return sb.toString();
	}

	public String toHTMLString()
	{
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb);
		formatter.format("<table class='sitetable'>");
		boolean even=false;
		String css;
		String image;
		String href;
		formatter.format("<tr class='heading'><td>Site name</td><td>Address</td><td>Requests served</td><td>Status</td><td>Message</td></tr>");
		for (Site site : getSites())
		{
			css=even?"even":"odd";
			String uri="N/A";
			String name=site.getName();
			if(site instanceof VSite){
				uri=((VSite)site).getRealURI().toString();
				href="<a href='"+uri+"'>"+uri+"</a>";
			}
			else{
				StringBuilder hr=new StringBuilder();
				for(VSite v: ((MultiSite)site).getConfiguredSites()){
					String vsiteUri=v.getRealURI().toString();
					hr.append("<a href='").append(vsiteUri).append("'>");
					hr.append(vsiteUri).append("</a>");
					hr.append("<br/>");
				}
				href=hr.toString();
			}
			int numRequests=site.getNumberOfRequests();
			
			try{
				image=site.ping()?"resources/happymonkey.png":"resources/sadmonkey.png";
			}catch(Exception e){
				image="resources/sadmonkey.gif";
			}
			String errorMessage=site.getStatusMessage();
			
			sb.append("\n");
			formatter.format("<tr class='%1$s'>"+
					"<td>%2$10s</td><td>%3$s</td><td align='right'>%4$10d</td><td><img src='%5$s' title='%6$s'/></td><td>%6$s</td></tr>", 
					css,name,href,numRequests,image,errorMessage);
			
			even=!even;
		}
		formatter.format("\n</table>");
		formatter.close();
		return sb.toString();
	}

}
