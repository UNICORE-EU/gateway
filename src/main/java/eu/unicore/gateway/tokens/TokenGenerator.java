package eu.unicore.gateway.tokens;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLDecoder;
import java.security.Principal;
import java.util.Collection;
import java.util.Map;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.ee10.servlet.ServletCoreRequest;
import org.eclipse.jetty.security.AuthenticationState;
import org.eclipse.jetty.server.Request;

import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.Site;
import eu.unicore.gateway.SiteOrganiser;
import eu.unicore.gateway.VSite;
import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.util.LogUtil;
import eu.unicore.util.Log;
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

	public static final String PATH = "/generate-token";
	public static final String CREATE = "/generate-token/create";

	private final GatewayProperties gatewayProperties;
	private final SiteOrganiser sites;

	public TokenGenerator(GatewayProperties gatewayProperties, SiteOrganiser sites){
		this.gatewayProperties = gatewayProperties;
		this.sites = sites;
	}

	public void handleRequest(HttpServletRequest req, HttpServletResponse res) throws IOException {
		if(!gatewayProperties.isAPITokenGeneratorEnabled()) {
			// disabled - 404
			res.sendError(404, "Not found");
			return;
		}
		try {
			URL url = new URL(req.getRequestURL().toString());
			PrintWriter out = res.getWriter();
			res.setContentType("text/html");
			out.println("<html><link rel='stylesheet' type='text/css' href='/resources/gateway.css'/>"+
					"<title>UNICORE Gateway</title><body>");
			out.println(getContent(getHeader()));
			out.println("<br/>");
			Request baseRequest = ServletCoreRequest.wrap(req);
			Principal userPrincipal = AuthenticationState.getUserPrincipal(baseRequest);
			if (userPrincipal != null)
			{
				HttpSession session = req.getSession(false);
				@SuppressWarnings("unchecked")
				Map<String, String> oidcResponse = (Map<String, String>)session.getAttribute(
						"org.eclipse.jetty.security.openid.response");
				String accessToken = oidcResponse.get("access_token");
				if(CREATE.equals(url.getPath())){
					Site site = sites.getSite(req.getParameter("site"));
					String lifetime = req.getParameter("lifetime");
					String userprefs = req.getParameter("userprefs");
					String apiToken = generateAPIToken(accessToken, (VSite)site, lifetime, userprefs);
					out.println(getContent(getResultPage(apiToken, site.getName())));
				}
				else {
					out.println(getContent(getForm(sites.getSites())));
				}
			}
			else {
				res.sendError(401, "Not authenticated");
				return;
			}
			out.println("<br/>");
			out.println(getFooter());
			out.println("</html></body>");
		}
		catch(Exception e) {
			res.sendError(500, Log.createFaultMessage("Error generating token", e));
		}
	}

	String generateAPIToken(String accessToken, VSite site, String lifetime, String userprefs)
			throws Exception {
		HttpClient client = sites.getHTTPClient(site);
		String tokenURL = site.getMetadata().get("tokenURL");
		if(tokenURL==null) {
			throw new Exception("No token URL for <"+site.getName()+">");
		}
		log.info("Generating API token for <{}> via <{}>", site.getName(), tokenURL);
		HttpGet get = new HttpGet(tokenURL);
		get.addHeader("Authorization", "Bearer "+accessToken);
		if(userprefs!=null && userprefs.length()>0) {
			get.addHeader("X-UNICORE-User-Preferences", URLDecoder.decode(userprefs, "UTF-8"));
		}
		try (ClassicHttpResponse res = client.executeOpen(null, get, HttpClientContext.create())){
			String body = EntityUtils.toString(res.getEntity());
			if(200==res.getCode()) {
				return body;
			}
			else {
				throw new Exception(res.getCode()+" "+res.getReasonPhrase());
			}
		}
	}

	String getResultPage(String apiToken, String siteName) {
		StringBuilder sb = new StringBuilder();
		sb.append("<h4>API token for "+siteName+"</h4>");
		sb.append("<p>Please store securely and keep confidential!</p>");
		sb.append("<div class='tokentext'>"+apiToken+"</div>");
		sb.append("<br/><br/>"
				+ "<a href='data:,"+apiToken+
				"' download='UNICORE_API_Token_"+siteName+"'>"
				+ "<button type='button'>Download</button>"
				+ "</a>");
		return sb.toString();
	}

	String getForm(Collection<Site>sites) {
		StringBuilder sb = new StringBuilder();
		sb.append("<h4>Generate API token</h4>");
		sb.append("<p>Select site and set any additional parameters:</p>");
		sb.append("<div>");
		sb.append("<form class='tokenform' action='"+CREATE+"'>");
		boolean first = true;
		sb.append("<label class='fi'>Site:</label><br/>");
		for(Site site: sites) {
			String name = site.getName();
			sb.append("<input class='fi' type='radio' id='site_"+name+"' name='site' ");
			if(first) {
				sb.append("checked ");
				first = false;
			}
			sb.append("value='"+name+"'/>");
			sb.append("<label class='fi' for='site_"+name+"'>"+name+"</label><br/>");
		}
		sb.append("<br/>");
		sb.append("<label class='fi' for='lifetime'>Lifetime (sec):</label><br/>");
		sb.append("<input class='fi' type='text' id='lifetime' name='lifetime' value='86400'/><br/>");
		sb.append("<br/>");
		sb.append("<label class='fi' for='userprefs'>User preferences:</label><br/>");
		sb.append("<input class='fi' type='text' id='userprefs' name='userprefs' value=''/><br/>");
		sb.append("<br/><input class='fi' type='submit' value='Create token'/>");
		sb.append("</form></div>");
		return sb.toString();
	}

	String getHeader() {
		StringBuilder sb = new StringBuilder();
		sb.append("<div id='header'><a href='http://www.unicore.eu'><img src='/resources/unicore_logo.gif' border='0'/></a>");
		sb.append("<br/> Gateway <br/></div>");
		return sb.toString();
	}

	String getFooter(){
		StringBuilder sb = new StringBuilder();
		sb.append("<div id='footer'><hr/> Version: "+Gateway.RELEASE_VERSION);
		sb.append("&nbsp;&nbsp;&nbsp;&nbsp;<a href='/'>Back</a>");
		sb.append("</div>");
		return sb.toString();
	}

	private String getContent(String content){
		return "<div id='content'><b class='rtop'><b class='r1'></b><b class='r2'>" +
				"</b> <b class='r3'></b> <b class='r4'></b></b>" +
				content +
				"<b class=<'rbottom'><b class='r4'></b> <b class='r3'></b> <b class='r2'></b> <b class='r1'></b></b></div>";
	}
}