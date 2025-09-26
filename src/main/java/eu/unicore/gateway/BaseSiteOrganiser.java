package eu.unicore.gateway;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.hc.client5.http.classic.HttpClient;

import eu.unicore.gateway.cluster.MultiSite;

public abstract class BaseSiteOrganiser implements SiteOrganiser
{
	protected final Gateway gateway;

	protected final Map<String,Site> sites = new ConcurrentHashMap<>();

	protected BaseSiteOrganiser(Gateway gw)
	{
		this.gateway = gw;
	}

	@Override
	public Collection<Site> getSites()
	{
		return sites.values();
	}

	@Override
	public VSite match(String targetURL, String clientIP) throws URISyntaxException
	{
		synchronized(sites){
			for (Site site : getSites())
			{
				if (site.accept(targetURL))
				{
					return site.select(clientIP);
				}
			}
			return null;
		}
	}

	@Override
	public void reloadConfig() {
		for(Site s: sites.values()) {
			s.reloadConfig();
		}
		cachedClients.clear();
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

	public String toHTMLString(SortOrder order)
	{
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb);
		formatter.format("<table class='sitetable'>");
		boolean even=false;
		String css;
		String image;
		String href;
		formatter.format("<tr class='heading'>"
				+ "<td><a class='heading' href=\"?sort=NAME\">Site name</a></td>"
				+ "<td><a href=\"?sort=ADDRESS\">Address</a></td>"
				+ "<td><a href=\"?sort=REQUESTS\">Requests served</a></td>"
				+ "<td>Status</td>"
				+ "<td><a href=\"?sort=MESSAGE\">Message</a></td></tr>");
		List<Site> sites = new ArrayList<>();
		sites.addAll(getSites());
		Collections.sort(sites, getSorter(order));
		for (Site site : sites)
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

	public Comparator<Site>getSorter(SortOrder order){
		switch (order){
		case REQUESTS:
			return (a,b)->{
				return b.getNumberOfRequests()-a.getNumberOfRequests();
			};
		case MESSAGE:
			return (a,b)->{
				return a.getStatusMessage().compareTo(b.getStatusMessage());
			};
		case ADDRESS:
			return (a,b)->{
				String addrA = "n/a";
				String addrB = "n/a";
				if(a instanceof VSite) {
					addrA = ((VSite)a).getRealURI().toString();
				}
				if(b instanceof VSite) {
					addrB = ((VSite)b).getRealURI().toString();
				}
				return addrA.compareTo(addrB);
			};
			
		case NAME:
		default:
			return (a,b)->{
				return a.getName().compareTo(b.getName());
			};
		}
	}

	private final Map<URL, HttpClient> cachedClients = new HashMap<>();

	@Override
	public synchronized HttpClient getHTTPClient(VSite site) throws Exception{
		URL url = site.getRealURI().toURL();
		HttpClient c = cachedClients.get(url);
		if(c==null) {
			c = gateway.getClientFactory().makeHttpClient(url);
			cachedClients.put(url, c);
		}
		return c;
	}

}
