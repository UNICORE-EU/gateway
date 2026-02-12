package eu.unicore.gateway.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;

import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.Site;
import eu.unicore.gateway.SiteOrganiser;
import eu.unicore.gateway.VSite;
import eu.unicore.gateway.cluster.MultiSite;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FrontPageRenderer {

	private final Gateway gateway;

	private final boolean showDetailedWebpage;

	private final boolean showServiceAddresses;

	public FrontPageRenderer(Gateway gateway){
		this.gateway = gateway;
		this.showDetailedWebpage = !gateway.getProperties().isDetailedWebPageDisabled();
		this.showServiceAddresses = gateway.getProperties().isShowServiceAdresses();
	}

	/**
	 * show the Gateway home page ("monkey page")
	 */
	public void getFrontPage(HttpServletRequest req, HttpServletResponse res) throws IOException {
		SiteOrganiser so = gateway.getSiteOrganiser();  
		PrintWriter out = res.getWriter();
		res.setContentType("text/html");
		X509Certificate[] certs = (X509Certificate[]) req.getAttribute("jakarta.servlet.request.X509Certificate");
		String clientIP = req.getRemoteAddr();

		out.println("<html><link rel='stylesheet' type='text/css' href='resources/gateway.css'/>"+
				"<title>UNICORE Gateway</title><body>");
		StringBuilder top = new StringBuilder();
		top.append("<div id='header'><a href='http://www.unicore.eu'><img src='resources/unicore_logo.gif' border='0'/></a>");
		top.append("<br/> Gateway <br/>");
		if (certs != null)
		{
			top.append("<p class='username'>Your certificate is: <br/>")
			.append(certs[0].getSubjectX500Principal().getName()).append("</p>");
		}
		top.append("<p class='username'>Your IP address: ").append(clientIP).append("</p></div>");

		out.println(getContent(top.toString()));
		out.println("<br/>");
		if(showDetailedWebpage){
			String baseURL = getBaseURL(req);
			SortOrder ordering = SortOrder.NONE;
			if(req.getQueryString()!=null) {
				String sort = req.getParameter("sort");
				if(sort!=null) {
					try {
						ordering = SortOrder.valueOf(sort);
					}catch(Exception ex) {}
				}
			}
			out.println(getContent(toHTMLString(baseURL, so, ordering, showServiceAddresses)));
		}
		else{
			out.println(getContent("<br/>Detailed site listing disabled.<br/>"));
		}
		out.println("<br/>");
		out.println(getFooter());
		out.println("</html></body>");
	}

	public static String toHTMLString(String baseURL, SiteOrganiser so, SortOrder order, boolean showServiceAddresses)
	{
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb);
		formatter.format("<table class='sitetable'>");
		boolean even=false;
		String css;
		String image;
		String href;
		formatter.format("<tr class='heading'>"
				+ "<td><a class='heading' href=\"?sort=NAME\">Site name</a></td>"
				+ "<td><a href=\"?sort=ADDRESS\">Address</a></td>"
				+ "<td><a href=\"?sort=REQUESTS\">Requests served</a></td>"
				+ "<td>Status</td>"
				+ "<td><a href=\"?sort=MESSAGE\">Message</a></td></tr>");
		List<Site> sites = new ArrayList<>();
		sites.addAll(so.getSites());
		Collections.sort(sites, getSorter(order));
		for (Site site : sites)
		{
			css = even ? "even" : "odd";
			String uri;
			String name = site.getName();
			if(site instanceof VSite){
				if(showServiceAddresses) {
					uri = ((VSite)site).getRealURI().toString();
				}
				else {
					uri = baseURL+"/"+name;
				}
				href="<a href='"+uri+"'>"+uri+"</a>";
			}
			else{
				StringBuilder hr=new StringBuilder();
				for(VSite v: ((MultiSite)site).getConfiguredSites()){
					String vsiteUri = showServiceAddresses ?
							v.getRealURI().toString() : baseURL+"/"+v.getName();
					hr.append("<a href='").append(vsiteUri).append("'>");
					hr.append(vsiteUri).append("</a>");
					hr.append("<br/>");
				}
				href=hr.toString();
			}
			int numRequests = site.getNumberOfRequests();
			image = site.ping() ? "resources/happymonkey.png" : "resources/sadmonkey.png";
			String errorMessage = site.getStatusMessage();
			sb.append("\n");
			formatter.format("<tr class='%1$s'>"
					+ "<td>%2$10s</td><td>%3$s</td><td align='right'>%4$10d</td><td><img src='%5$s' title='%6$s'/></td><td>%6$s</td>"
					+ "</tr>", css, name, href, numRequests, image, errorMessage);
			even = !even;
		}
		formatter.format("\n</table>");
		formatter.close();
		return sb.toString();
	}

	public static enum SortOrder {
		NONE, NAME, REQUESTS, MESSAGE, ADDRESS
	};

	public static Comparator<Site>getSorter(SortOrder order){
		switch (order){
		case REQUESTS:
			return (a,b)->{
				return b.getNumberOfRequests()-a.getNumberOfRequests();
			};
		case MESSAGE:
			return (a,b)->{
				return a.getStatusMessage().compareTo(b.getStatusMessage());
			};
		case ADDRESS:
			return (a,b)->{
				String addrA = "n/a";
				String addrB = "n/a";
				if(a instanceof VSite) {
					addrA = ((VSite)a).getRealURI().toString();
				}
				if(b instanceof VSite) {
					addrB = ((VSite)b).getRealURI().toString();
				}
				return addrA.compareTo(addrB);
			};
			
		case NAME:
		default:
			return (a,b)->{
				return a.getName().compareTo(b.getName());
			};
		}
	}

	private String getContent(String content){
		return "<div id='content'><b class='rtop'><b class='r1'></b><b class='r2'>" +
				"</b> <b class='r3'></b> <b class='r4'></b></b>" +
				content +
				"<b class=<'rbottom'><b class='r4'></b> <b class='r3'></b> <b class='r2'></b> <b class='r1'></b></b></div>";
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

	private String getBaseURL(HttpServletRequest req){
		StringBuilder sb = new StringBuilder();
		sb.append(req.getScheme()).append("://");
		String host = req.getHeader("Host");
		if(host!=null){
			sb.append(host);
		}
		else{
			sb.append(req.getServerName()).append(":").append(req.getServerPort());
		}
		return sb.toString();
	}
}