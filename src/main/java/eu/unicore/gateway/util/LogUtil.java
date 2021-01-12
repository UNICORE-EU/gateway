package eu.unicore.gateway.util;

import eu.unicore.util.Log;

public class LogUtil extends Log {

	/**
	 * logger prefix for general Gateway code
	 */
	public static final String GATEWAY="unicore.gateway";

	/**
	 * MDC context key for the client's IP
	 */
	public static final String MDC_IP="clientIP";

	/**
	 * MDC context key for the client's DN
	 */
	public static final String MDC_DN="clientName";

}
