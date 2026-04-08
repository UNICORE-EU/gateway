package eu.unicore.gateway.tokens;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.ee10.servlet.ServletCoreRequest;
import org.eclipse.jetty.security.AuthenticationState;
import org.eclipse.jetty.server.Request;

import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.util.LogUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Helper to generate UNICORE API tokens
 *
 * @author schuller
 */
public class TokenGenerator {

	private static final Logger log = LogUtil.getLogger(LogUtil.GATEWAY, TokenGenerator.class);

	private final GatewayProperties gatewayProperties;

	public TokenGenerator(GatewayProperties gatewayProperties){
		this.gatewayProperties = gatewayProperties;
	}

	/**
	 * return the named file from the directory that holds the ACME token
	 */
	public void handleRequest(HttpServletRequest req, HttpServletResponse res) throws IOException {
		PrintWriter out = res.getWriter();
		res.setContentType("text/plain");
		Request baseRequest = ServletCoreRequest.wrap(req);
		Principal userPrincipal = AuthenticationState.getUserPrincipal(baseRequest);
		out.println("userPrincipal: " + userPrincipal);
        if (userPrincipal != null)
        {
            // You can access the full openid claims for an authenticated session.
            HttpSession session = req.getSession(false);
            @SuppressWarnings("unchecked")
            Map<String, String> claims = (Map<String, String>)session.getAttribute("org.eclipse.jetty.security.openid.claims");
            out.println("claims: " + claims);
            out.println("name: " + claims.get("name"));
            out.println("sub: " + claims.get("sub"));
        }
        
	}

}