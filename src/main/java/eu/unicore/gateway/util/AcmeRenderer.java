package eu.unicore.gateway.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;

import eu.unicore.gateway.properties.GatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ACME / Let's Encrypt support
 *
 * @author schuller
 */
public class AcmeRenderer {

	private static final Logger log = LogUtil.getLogger(LogUtil.GATEWAY, AcmeRenderer.class);

	private final GatewayProperties gatewayProperties;

	public AcmeRenderer(GatewayProperties gatewayProperties){
		this.gatewayProperties = gatewayProperties;
	}

	/**
	 * return the named file from the directory that holds the ACME token
	 */
	public void handleAcmeRequest(String file, HttpServletRequest req, HttpServletResponse res) throws IOException {
		if(!gatewayProperties.getBooleanValue(GatewayProperties.KEY_ACME_ENABLE)) {
			// acme disabled - 404
			res.sendError(404, "Not found");
			return;
		}
		File tokenDirectory = gatewayProperties.getFileValue(GatewayProperties.KEY_ACME_DIR, true);
		File tokenFile = new File(tokenDirectory, file);
		if(!tokenFile.exists()) {
			res.sendError(404, "Not found");
			return;
		}
		log.info("Serving token file <{}>", tokenFile.getAbsolutePath());
		String token = FileUtils.readFileToString(tokenFile,"UTF-8");
		PrintWriter out = res.getWriter();
		res.setContentType("text/plain");
		out.println(token);
	}

}