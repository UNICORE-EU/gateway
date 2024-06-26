package eu.unicore.gateway;

import java.util.Collection;

import org.apache.hc.client5.http.classic.HttpClient;

/**
 * collects information about the gateway and the sites (vsites) available. 
 * 
 * could have 'simple' (i.e. from some kind of properties file) implementations, or it could have 
 * a more dynamic characteristics (discoverable via multi-cast, or WS-Discovery(?)) for another
 * more interesting implementation. 
 * 
 * @author roger
 */
public interface SiteOrganiser
{

	public enum SortOrder {
		NONE, NAME, REQUESTS, MESSAGE
	};

	/**
	 * get the current set of sites
	 */
	public Collection<Site> getSites();

	/**
	 * select a matching vsite. The client IP may be ignored, or it may be used
	 * in load balancing configurations to achieve IP affinity
	 * 
	 * @param wsato - destination, usually obtained as value of the WS-Addressing To header
	 * @param clientIP - the IP of the client
	 * @return matching vsite
	 */
	public VSite match(String wsato, String clientIP);

	/**
	 * get a HTML representation of this site organiser
	 */
	public String toHTMLString(SortOrder ordering);

	/**
	 *  reload configuration - for example due to config changes
	 */
	public void reloadConfig();

	/**
	 * get a client for talking to the site
	 */
	public HttpClient getHTTPClient(VSite site) throws Exception;

}
