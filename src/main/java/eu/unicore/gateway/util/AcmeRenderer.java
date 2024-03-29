package eu.unicore.gateway.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;

import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.util.Log;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ACME / Let's Encrypt support
 *
 * @author schuller
 */
public class AcmeRenderer {

	private static final Logger log = LogUtil.getLogger(LogUtil.GATEWAY, AcmeRenderer.class);

	private final Gateway gateway;

	public AcmeRenderer(Gateway gateway){
		this.gateway = gateway;
	}

	/**
	 * return the named file from the directory that holds the ACME token
	 */
	public void handleAcmeRequest(String file, HttpServletRequest req, HttpServletResponse res) throws IOException {
		File tokenDirectory = gateway.getProperties().getFileValue(GatewayProperties.KEY_ACME_DIR, true);
		if(tokenDirectory==null) {
			// acme disabled - 404
			res.sendError(404, "Not found");
			return;
		}
		else {
			File tokenFile = new File(tokenDirectory, file);
			if(!tokenFile.exists()) {
				res.sendError(404, "Not found");
				return;
			}
			try {
				log.info("Serving token file <{}>", tokenFile.getAbsolutePath());
				String token = FileUtils.readFileToString(tokenFile,"UTF-8");
				PrintWriter out=res.getWriter();
				res.setContentType("text/plain");
				out.println(token);
			}catch(Exception  ex) {
				res.sendError(500, Log.createFaultMessage("", ex));
			}
		}
	}

}