package eu.unicore.gateway.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.cert.X509Certificate;

import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.SiteOrganiser;
import eu.unicore.gateway.SiteOrganiser.SortOrder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class DefaultPageRenderer {
	
	private final Gateway gateway;
	
	public DefaultPageRenderer(Gateway gateway){
		this.gateway = gateway;
	}
	
	/**
	 * show the default Gateway page ("monkey page")
	 */
	public void doGETDefaultGWPage(HttpServletRequest req, HttpServletResponse res) throws IOException {
		SiteOrganiser so = gateway.getSiteOrganiser();  
		PrintWriter out=res.getWriter();
		res.setContentType("text/html");
		X509Certificate[] certs = (X509Certificate[]) req.getAttribute("jakarta.servlet.request.X509Certificate");
		String clientIP=req.getRemoteAddr();
		
		out.println("<html><link rel='stylesheet' type='text/css' href='resources/gateway.css'/>"+
				"<title>UNICORE Gateway</title><body>");
		StringBuilder top = new StringBuilder();
		top.append("<div id='header'><a href='http://www.unicore.eu'><img src='resources/unicore_logo.gif' border='0'/></a>");
		top.append("<br/> Gateway <br/>");
		if (certs != null)
		{
			top.append("<p class='username'>You are authenticated as: <br/>")
			.append(certs[0].getSubjectX500Principal().getName()).append("</p>");
		}
		top.append("<p class='username'>Your IP address: ").append(clientIP).append("</p></div>");
		
		out.println(getContentDiv(top.toString()));
		out.println("<br/>");
		if(!gateway.getProperties().isDetailedWebPageDisabled()){
			SortOrder ordering = SortOrder.NONE;
			if(req.getQueryString()!=null) {
				String sort = req.getParameter("sort");
				if(sort!=null) {
					try {
						ordering = SortOrder.valueOf(sort);
					}catch(Exception ex) {}
				}
			}
			out.println(getContentDiv(so.toHTMLString(ordering)));
		}
		else{
			out.println(getContentDiv("<br/>Detailed site listing disabled.<br/>"));
		}
		out.println("<br/>");

		out.println(getFooter());

		out.println("</html></body>");
	}

	private String getContentDiv(String content){
		String s="<div id='content'><b class='rtop'><b class='r1'></b><b class='r2'>"+
				"</b> <b class='r3'></b> <b class='r4'></b></b>"+content+
				"<b class=<'rbottom'><b class='r4'></b> <b class='r3'></b> <b class='r2'></b> <b class='r1'></b></b></div>";
		return s;
	}

	private String getFooter(){
		StringBuilder sb=new StringBuilder();
		sb.append("<div id='footer'><hr/> Version: "+Gateway.RELEASE_VERSION+" Up since: ").append(gateway.upSince());
		if(gateway.getProperties().isDynamicRegistrationEnabled()){
			sb.append("&nbsp;&nbsp;&nbsp;&nbsp;<a href='resources/register.html'>register a site</a>");
		}
		sb.append("</div>");
		return sb.toString();
	}

}