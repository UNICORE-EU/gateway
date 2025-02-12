package eu.unicore.gateway;

/**
 * a Site
 */
public interface Site {

	/**
	 * the name of the site
	 */
	public String getName();

	/**
	 * the number of requests that have been processed by the site
	 */
	public int getNumberOfRequests();

	/**
	 * get a human-friendly status message
	 */
	public String getStatusMessage();

	/**
	 * ping the site
	 * @return true if the site is live, false if not, or a timeout occurs
	 */
	public boolean ping();

	/**
	 * check if the supplied URI matches this Vsite
	 * 
	 * @param uri
	 */
	public boolean accept(String uri);

	/**
	 * select a VSite from this Site based on the given client identifier (for example, the client IP)
	 * @param clientID
	 */
	public VSite select(String clientID);

	/**
	 * reload configuration - for example due to config changes
	 */
	public void reloadConfig();

}