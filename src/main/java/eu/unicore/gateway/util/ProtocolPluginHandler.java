package eu.unicore.gateway.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.http.HttpServletRequest;

/**
 * a handler for custom protocols that should be run on the gateway connection 
 * 
 * @author schuller
 */
public interface ProtocolPluginHandler {

	/**
	 * configure the handler
	 * @param request - the {@link HttpServletRequest}
	 */
	public void configure(HttpServletRequest request);
	
	/**
	 * start processing
	 * @param in - input stream for reading from the client
	 * @param out - output stream for writing to the client
	 * @throws IOException
	 */
	public void handle(InputStream in, OutputStream out)throws IOException;
	
	/**
	 * get the name of the protocol
	 */
	public String getProtocol();
	
}
