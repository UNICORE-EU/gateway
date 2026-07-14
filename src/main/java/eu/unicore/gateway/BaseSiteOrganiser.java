package eu.unicore.gateway;

import java.io.Closeable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.HttpClient;

public abstract class BaseSiteOrganiser implements SiteOrganiser {

	protected final Gateway gateway;

	protected final Map<String, Site> sites = new ConcurrentHashMap<>();

	protected BaseSiteOrganiser(Gateway gw)
	{
		this.gateway = gw;
	}

	@Override
	public Collection<Site> getSites() throws IOException
	{
		return sites.values();
	}

	@Override
	public VSite match(String targetURL, String clientIP) throws URISyntaxException, IOException
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
	public Site getSite(String name) throws Exception
	{
		return sites.get(name);
	}

	@Override
	public synchronized void reloadConfig() {
		for(Site s: sites.values()) {
			s.reloadConfig();
		}
		for(var c: cachedClients.values()) {
			if(c instanceof Closeable) {
				IOUtils.closeQuietly((Closeable)c);
			}
		}
		cachedClients.clear();
	}

	private final Map<URL, HttpClient> cachedClients = new HashMap<>();

	@Override
	public synchronized HttpClient getHTTPClient(VSite site) throws Exception{
		URL url = site.getRealURI().toURL();
		HttpClient c = cachedClients.get(url);
		if(c==null) {
			c = gateway.getClientFactory().pooledClient(url);
			cachedClients.put(url, c);
		}
		return c;
	}

}
