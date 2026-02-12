package eu.unicore.gateway;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.hc.client5.http.classic.HttpClient;

public abstract class BaseSiteOrganiser implements SiteOrganiser {

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

	@Override
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
