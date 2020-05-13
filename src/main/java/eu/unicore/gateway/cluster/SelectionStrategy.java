package eu.unicore.gateway.cluster;

import java.util.Map;

import eu.unicore.gateway.VSite;

public interface SelectionStrategy {

	public static final String HEALTH_CHECK_INTERVAL="strategy.healthcheck.interval";
	
	//some preconfigured strategies
	
	public static final String ROUND_ROBIN_STRATEGY="roundRobin";
	
	public static final String STICKY_ROUND_ROBIN_STRATEGY="stickyRoundRobin";
	
	public static final String PRIMARY_WITH_FALLBACK_STRATEGY="primaryWithFallback";
	
	
	/**
	 * initialise this strategy object
	 * 
	 * @param params
	 */
	public void init(MultiSite parent, Map<String, String>params);
	
	/**
	 * select one of the parent's configured sites
	 * 
	 * @param clientID - the ID of the current client
	 * 
	 * @return a {@link VSite}
	 */
	public VSite select(String clientID);
	
	
	
}
