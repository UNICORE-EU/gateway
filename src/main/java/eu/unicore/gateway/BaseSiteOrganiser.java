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
			image=site.ping()?"resources/happymonkey.png":"resources/sadmonkey.png";
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
