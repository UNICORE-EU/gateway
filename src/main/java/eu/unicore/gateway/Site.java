package eu.unicore.gateway;

/**
 * a Site
 */
public interface Site {

	/**
	 * the name of the site
	 */
	public abstract String getName();

	/**
	 * the number of requests that have been processed by the site
	 */
	public abstract int getNumberOfRequests();

	/**
	 * get a human-friendly status message
	 */
	public abstract String getStatusMessage();

	/**
	 * ping the site, using a default timeout of 3OO ms.
	 * @return true if the site is live, false if not, or a timeout occurs
	 */
	public abstract boolean ping();

	/**
	 * ping with a given timeout
	 * @param timeout in ms
	 * @return true if the site is live, false if not, or a timeout occurs
	 */
	public abstract boolean ping(int timeout);

	/**
	 * check if the supplied URI matches this Vsite
	 * 
	 * @param uri
	 */
	public abstract boolean accept(String uri);

	/**
	 * select a VSite from this Site based on the given client identifier (for example, the client IP)
	 * @param clientID
	 */
	public abstract VSite select(String clientID);

	/**
	 * reload configuration - for example due to config changes
	 */
	public void reloadConfig();

}