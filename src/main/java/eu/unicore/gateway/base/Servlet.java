package eu.unicore.gateway.base;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.POSTHandler;
import eu.unicore.gateway.SiteOrganiser;
import eu.unicore.gateway.VSite;
import eu.unicore.gateway.client.HttpClientFactory;
import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.util.LogUtil;
import eu.unicore.util.Log;

/**
 * Servlet for forwarding HTTP requests to the Vsite<br/>
 * 
 * @author roger
 * @author schuller
 */
public class Servlet extends HttpServlet {

	private static final long serialVersionUID=2L;

	private static final Logger logger=LogUtil.getLogger(LogUtil.GATEWAY,Servlet.class);
	private final GatewayProperties properties;
	private final Gateway gateway;
	private final HttpClientFactory clientFactory;

	public Servlet(Gateway gw)
	{
		this.gateway = gw;
		this.properties = gw.getProperties();
		clientFactory = gateway.getClientFactory();
	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException{
		String clientIP=req.getRemoteAddr();
		String clientName="n/a";
		try{
			X509Certificate[] certPath = (X509Certificate[]) req.getAttribute("javax.servlet.request.X509Certificate");
			if (certPath != null){
				clientName=certPath[0].getSubjectX500Principal().getName();
			}
		}catch(Exception ex){}
		ThreadContext.put(LogUtil.MDC_IP, clientIP);
		ThreadContext.put(LogUtil.MDC_DN, clientName);

		if(logger.isDebugEnabled()){
			StringBuilder sb=new StringBuilder();
			sb.append("Processing request from ").append(clientIP);
			if(clientName!=null){
				sb.append(" Client name: ").append(clientName);
			}
			logger.debug(sb.toString());
		}
		try{
			super.service(req, res);
		}finally{
			ThreadContext.remove(LogUtil.MDC_DN);
			ThreadContext.remove(LogUtil.MDC_IP);
		}
	}

	@Override
	protected void doHead(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.debug("Processing HEAD request");
		doHttp("HEAD", req, resp);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
	{
		URL u=new URL(req.getRequestURL().toString());
		if("/".equals(u.getPath())){
			doGETDefaultGWPage(req, res);
		}
		else{
			doHttp("GET", req, res);
		}
	}
	
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		doHttp("PUT", req, res);
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		doHttp("DELETE", req, res);
	}
	
	@Override
	protected void doOptions(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		doHttp("OPTIONS", req, res);
	}
	
	/**
	 * generic HTTP forwarding: resolve a VSite, forward the request, collect result and
	 * return it to the client
	 * 
	 * @param method - HTTP method name (GET, PUT, ...)
	 * @param req
	 * @param res
	 * @throws ServletException
	 * @throws IOException
	 */
	protected void doHttp(String method,HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
	{
		SiteOrganiser so = gateway.getSiteOrganiser();     
		String url=fullRequestURL(req);
		//check if we can map the request to a virtual site
		VSite vsite=so.match(url, req.getRemoteAddr());
		if(vsite!=null){
			try{
				performRequestForwarding(method,vsite, url, req, res);
			}catch(URISyntaxException ue){
				String msg = Log.createFaultMessage("URI syntax", ue);
				res.sendError(HttpServletResponse.SC_BAD_REQUEST,msg);
			}
		}
		else{
			res.sendError(HttpServletResponse.SC_NOT_FOUND,"Could not find the requested resource.");
		}
	}

	/**
	 * forward the named HTTP request to the selected VSite
	 */
	private void performRequestForwarding(String request, VSite vsite, String url, 
			HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException, URISyntaxException {
		URI u = URI.create(vsite.resolve(url));
		URI uWithQuery = addQueryToURI(u, req.getQueryString());
		HttpUriRequestBase http=null;
		if("GET".equals(request)){
			http = new HttpGet(uWithQuery);
		}
		else if("PUT".equals(request)){
			http = new HttpPut(uWithQuery);
		}
		else if("DELETE".equals(request)){
			http = new HttpDelete(uWithQuery);
		}
		else if("HEAD".equals(request)){
			http = new HttpHead(uWithQuery);
		}
		else if("OPTIONS".equals(request)){
			http = new HttpOptions(uWithQuery);
		}
		if(http!=null){
			forwardRequestToVSite(http, u, vsite, req, res);
		}
		else{
			throw new IllegalStateException("Not implemented: "+request);
		}
	}

	public static void prepareRequest(HttpUriRequestBase http, URI uri, VSite vsite,
			HttpServletRequest req, Gateway gateway) {
		copyHeaders(req, http);
		String clientIP = req.getRemoteAddr();
		if(clientIP!=null) {
			http.addHeader(RawMessageExchange.CONSIGNOR_IP_HEADER, req.getRemoteAddr());
		}
		http.addHeader(RawMessageExchange.GATEWAY_EXTERNAL_URL, extractGatewayURL(req, vsite.getName()));
		if(gateway.getConsignorProducer()!=null){
			X509Certificate[] certPath = (X509Certificate[]) req.getAttribute("javax.servlet.request.X509Certificate");
			if (certPath != null)
			{
				try{
					Header consignor = gateway.getConsignorProducer().getConsignorHeader(certPath,clientIP);
					if(consignor!=null)http.addHeader(consignor);
				}catch(Exception e){
					Log.logException("Cannot create signed DN header",e,logger);
				}
			}
		}
	}
	/**
	 * forward the HTTP request to the selected VSite
	 */
	private void forwardRequestToVSite(HttpUriRequestBase http, URI uri, VSite vsite,
			HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException{
		prepareRequest(http, uri, vsite, req, gateway);
		try
		{
			HttpClient client =null;
			synchronized(vsite){
				client = vsite.getClient();
				if(client==null){
					client = clientFactory.makeHttpClient(uri.toURL());
					vsite.setClient(client);	
				}
			}
			if(http instanceof HttpEntityContainer){
				HttpEntityContainer httpWithEntity = (HttpEntityContainer)http;
				long contentLength = req.getContentLength();
				ContentType contentType = req.getContentType()!=null ?
					ContentType.parse(req.getContentType()) : ContentType.WILDCARD;
				InputStreamEntity requestEntity = new InputStreamEntity(
						req.getInputStream(), contentLength, contentType);
				httpWithEntity.setEntity(requestEntity);
			}
			
			try(ClassicHttpResponse response = client.executeOpen(null, http, HttpClientContext.create())){
				copyResponseHeaders(response, res);
				res.setStatus(response.getCode());
				OutputStream os = res.getOutputStream();
				writeResponseContent(response, os);
				os.flush();
			}
		}catch(Exception e){
			LogUtil.logException("Error performing "+http.getMethod()+" request.", e, logger);
			res.sendError(503, Log.createFaultMessage("Could not perform request", e));
		}
	}
	
	private void writeResponseContent(ClassicHttpResponse response, OutputStream os) throws IOException {
		if(response.getEntity()!=null && response.getEntity().getContent()!=null){
			InputStream is=response.getEntity().getContent();
			byte[] buf=new byte[1024];
			int r;
			while((r=is.read(buf))!=-1){
				os.write(buf, 0, r);
			}
		}
	}
	
	final static List<String>excludedHeaders = Arrays.asList(new String[]{
			"x-frame-options", "date",
	});
	
	private void copyResponseHeaders(ClassicHttpResponse fromSite, HttpServletResponse toClient){
		for (Header h: fromSite.getHeaders()){
			if(!excludedHeaders.contains(h.getName().toLowerCase())){
				toClient.addHeader(h.getName(), h.getValue());
			}
		}
	}

	public static URI addQueryToURI(URI u, String query) throws IOException
	{
		try 
		{
			if(query == null || query.length() == 0){
				return u;
			}
			else{
				return new URIBuilder(u).setCustomQuery(query).build();
			}
		} catch (URISyntaxException e1)
		{
			throw new IOException(e1);
		}
	}

	private static void copyHeaders(HttpServletRequest req, HttpUriRequestBase method){
		Enumeration<String> e=req.getHeaderNames();
		while(e.hasMoreElements()){
			String name=e.nextElement();
			if("Host".equalsIgnoreCase(name))continue;
			Enumeration<String> hdr = req.getHeaders(name);
			while(hdr.hasMoreElements()){
				method.addHeader(name, hdr.nextElement());
			}
		}
		method.removeHeaders(HttpHeaders.TRANSFER_ENCODING);
		method.removeHeaders(HttpHeaders.CONTENT_LENGTH);
		// prevent clients from sending these headers to the site
		method.removeHeaders(RawMessageExchange.CONSIGNOR_IP_HEADER);
		method.removeHeaders(RawMessageExchange.CONSIGNOR_HEADER);
		method.removeHeaders(RawMessageExchange.GATEWAY_EXTERNAL_URL);	
	}
	
	/**
	 * show the default Gateway page ("monkey page")
	 */
	private void doGETDefaultGWPage(HttpServletRequest req, HttpServletResponse res) throws IOException {
		SiteOrganiser so = gateway.getSiteOrganiser();  
		PrintWriter out=res.getWriter();
		res.setContentType("text/html");
		X509Certificate[] certs = (X509Certificate[]) req.getAttribute("javax.servlet.request.X509Certificate");
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
		if(!properties.isDetailedWebPageDisabled()){
			out.println(getContentDiv(so.toHTMLString()));
		}
		else{
			out.println(getContentDiv("<br/>Detailed site listing disabled.<br/>"));
		}
		out.println("<br/>");

		out.println(getFooter());

		out.println("</html></body>");
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
	{
		URL url=new URL(fullRequestURL(req));
		boolean isSoap = true;
		try
		{
			//check if it is a registration
			if("/VSITE_REGISTRATION_REQUEST".equals(url.getPath())){
				handleRegistration(req,res);
			}
			else{
				int maxHeaderSize = properties.getMaxSoapHeader();
				boolean isMultipart = false;
				String contentType = req.getHeader("Content-type");
				
				if(contentType!=null){
					contentType = contentType.toLowerCase();
					
					if(contentType.contains("application/soap+xml")){
						res.setContentType("application/soap+xml; charset=UTF-8");
					}
					else if(contentType.contains("multipart/related")){
						// for now, we support multipart only via the SOAP handler
						res.setContentType("application/soap+xml; charset=UTF-8");
						isMultipart = true;
					}
					else if(contentType.contains("text/xml")){
						res.setContentType("text/xml; charset=UTF-8");
					}
					else{
						// all others are non-SOAP
						String ct = contentType.split(";")[0];
						res.setContentType(ct+"; charset=UTF-8");
						isSoap = false;
					}
				}
				else{
					res.setContentType("text/xml; charset=UTF-8");
				}
				
				RawMessageExchange exchange = new RawMessageExchange(req.getReader(), 
						res.getWriter(), maxHeaderSize);
				exchange.setServletResponse(res);
				exchange.setServletRequest(req);
				exchange.setSOAP(isSoap);
				exchange.setMultipart(isMultipart);
				exchange.setProperty(RawMessageExchange.CONTENT_TYPE, contentType);
				
				X509Certificate[] certPath = (X509Certificate[]) req.getAttribute("javax.servlet.request.X509Certificate");
				if (certPath != null)
				{
					exchange.setProperty(RawMessageExchange.X509, certPath);
				}
				exchange.setProperty(RawMessageExchange.REMOTE_IP, req.getRemoteAddr());
				
				exchange.setRequestURL(url.toString());
				String soapAction = req.getHeader("SOAPAction");
				if(soapAction!=null){
					exchange.setProperty(RawMessageExchange.SOAP_ACTION, soapAction);
					exchange.setSOAP(true);
				}
				POSTHandler handler = new POSTHandler(gateway.getSiteOrganiser(), 
						gateway.getConsignorProducer(), clientFactory,
						properties.isChunkedDispatch(), 
						gateway.getHostURI().toString());
				handler.invoke(exchange);
				res.flushBuffer();
			}
		}
		catch (FileNotFoundException fne){
			res.sendError(404, fne.getMessage());
		}
		catch (Exception e){
			// result in a 500 response
			throw new ServletException("Failed to process POST request: " + e, e);
		}
	}

	protected void handleRegistration(HttpServletRequest req, HttpServletResponse res)throws ServletException,IOException{
		if(!properties.isDynamicRegistrationEnabled()){
			res.sendError(HttpServletResponse.SC_FORBIDDEN,"Dynamic registration is disabled.");
		}
		else{
			String name=req.getParameter("name");
			String address=req.getParameter("address");
			String secret = req.getParameter("secret");
			if(!properties.getValue(GatewayProperties.KEY_REG_SECRET).equals(secret)) {
				res.sendError(HttpServletResponse.SC_FORBIDDEN,"Wrong value for parameter 'secret' for dynamic registration.");
			}
			boolean success=true;
			try{
				URI uri=new URI(address);
				success=gateway.getDynamicSiteOrganiser().register(name,uri);
				if(success){
					PrintWriter pw=res.getWriter();
					pw.write("<html><body>Your request was processed successfully. <br/>" +
							"Please click <a href='/'>here</a> to return to the main gateway page.");
					res.setStatus(HttpServletResponse.SC_CREATED);
				}
			}catch(Exception e){
				LogUtil.logException("Error on registration of [name="+name+" address="+address+"]", e, logger);
			}
			if(!success){
				res.sendError(HttpServletResponse.SC_BAD_REQUEST,"Registration could not be processed.");
			}
		}
		res.flushBuffer();
	}

	/**
	 * reconstructs and returns the full request URL
	 * @param req - a {@link HttpServletRequest}
	 */
	public static String fullRequestURL(HttpServletRequest req){
		String query=req.getQueryString();
		StringBuffer requestURL=req.getRequestURL();
		if(query!=null){
			requestURL.append("?");
			requestURL.append(query);
		}
		return requestURL.toString();
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
		if(properties.isDynamicRegistrationEnabled()){
			sb.append("&nbsp;&nbsp;&nbsp;&nbsp;<a href='resources/register.html'>register a site</a>");
		}
		sb.append("</div>");
		return sb.toString();
	}

	public static String extractGatewayURL(HttpServletRequest req, String vsite){
		StringBuilder sb = new StringBuilder();
		sb.append(req.getScheme()).append("://");
		String host = req.getHeader("Host");
		if(host!=null){
			sb.append(host);
		}
		else{
			sb.append(req.getServerName()).append(":").append(req.getServerPort());
		}
		sb.append("/").append(vsite);
		return sb.toString();
	}
	
}
