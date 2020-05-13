package eu.unicore.gateway.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.http.HttpServletRequest;

/**
 * demo handler for the protocol switching logic
 * @see ProtocolPluginHandler
 * @author schuller
 */
public class Echo implements ProtocolPluginHandler {

	public String getProtocol() {
		return "ECHO";
	}
	
	public void configure(HttpServletRequest req){
		//nothing to configure
	}
	
	//just echo incoming content to the output
	public void handle(InputStream in, OutputStream out) throws IOException {
		while(true){
			int b=in.read();
			if(b>=0)out.write(b);
			else break;
		}
	}

}
