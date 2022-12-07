package eu.unicore.gateway.forwarding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

import javax.net.ssl.SSLEngine;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.Header;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpTransport;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.SiteOrganiser;
import eu.unicore.gateway.VSite;
import eu.unicore.gateway.base.Servlet;
import eu.unicore.gateway.util.LogUtil;
import eu.unicore.util.ChannelUtils;
import eu.unicore.util.SSLSocketChannel;
import eu.unicore.util.httpclient.DefaultClientConfiguration;
import eu.unicore.util.httpclient.HttpUtils;
import eu.unicore.util.jetty.forwarding.Forwarder;
import eu.unicore.util.jetty.forwarding.ForwardingConnection;
import eu.unicore.util.jetty.forwarding.UpgradeHttpServletRequest;
import eu.unicore.util.jetty.forwarding.UpgradeHttpServletResponse;

/**
 * establishes a ForwardingConnection to the requested back-end site,
 * and setups the data forwarding from the backend to the client
 *
 * @author schuller
 */
public class ForwardingSetup {

	private static final Logger logger = LogUtil.getLogger(LogUtil.GATEWAY,ForwardingSetup.class);

	public static final String REQ_UPGRADE_HEADER_VALUE = "UNICORE-Socket-Forwarding";

	public static final String UPGRADE_MAPPING_ATTRIBUTE = ForwardingSetup.class.getName();

	private final Gateway gateway;

	public ForwardingSetup(final Gateway gateway) {
		this.gateway = gateway;
	}

	public static ForwardingSetup getMappings(ServletContext servletContext)
	{
		return (ForwardingSetup)servletContext.getAttribute(UPGRADE_MAPPING_ATTRIBUTE);
	}

	public static ForwardingSetup ensureMappings(ServletContext servletContext, final Gateway gateway)
	{
		ForwardingSetup mapping = getMappings(servletContext);
		if (mapping == null)
		{
			mapping = new ForwardingSetup(gateway);
			servletContext.setAttribute(UPGRADE_MAPPING_ATTRIBUTE, mapping);
		}

		return mapping;
	}

	public boolean upgrade(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		if (!validateRequest(request))
			return false;

		// find VSite to forward to
		SiteOrganiser so = gateway.getSiteOrganiser();     
		String url = Servlet.fullRequestURL(request);
		String clientIP = request.getRemoteAddr();
		VSite vsite = so.match(url,clientIP);
		if(vsite==null){
			response.sendError(404, "The requested resource could not be found");
			return false;
		}

		// protocol upgrade handshake with VSite
		SocketChannel vsiteChannel = null;
		try{
			vsiteChannel = connectToVsite(vsite, url, request);
			assert vsiteChannel!=null: "Vsite did not handle Upgrade request";
		}catch(Exception e) {
			throw new IOException(e);
		}
		// Handle error responses
		Request baseRequest = Request.getBaseRequest(request);
		if (response.isCommitted())
		{
			logger.debug("not upgraded: response committed {}", request);
			baseRequest.setHandled(true);
			return false;
		}
		int httpStatus = response.getStatus();
		if (httpStatus > 200)
		{
			logger.debug("not upgraded: invalid http code {} {}", httpStatus, request);
			response.flushBuffer();
			baseRequest.setHandled(true);
			return false;
		}

		ForwardingConnection toClient = createForwardingConnection(baseRequest, vsiteChannel);
		logger.debug("forwarding-connection {} to vsite {}", toClient, vsiteChannel);
		if (toClient == null)
			throw new IOException("not upgraded: no connection");
		HttpChannel httpChannel = baseRequest.getHttpChannel();
		httpChannel.getConnector().getEventListeners().forEach(toClient::addEventListener);

		baseRequest.setHandled(true);
		Response baseResponse = baseRequest.getResponse();
		prepare101Response(baseResponse);
		baseResponse.flushBuffer();

		baseRequest.setAttribute(HttpTransport.UPGRADE_CONNECTION_ATTRIBUTE, toClient);

		// Save state from request/response and remove reference to the base request/response.
		new UpgradeHttpServletRequest(request).upgrade();
		new UpgradeHttpServletResponse(response).upgrade();
		Forwarder.get().attach(toClient);
		logger.info("Forwarding to {}, connection={}", vsite, toClient);
		return true;
	}

	protected boolean validateRequest(HttpServletRequest request)
	{
		return
				HttpMethod.GET.is(request.getMethod())
				&& HttpVersion.HTTP_1_1.is(request.getProtocol())
				&& "upgrade".equalsIgnoreCase(request.getHeader("Connection"))
				&& REQ_UPGRADE_HEADER_VALUE.equalsIgnoreCase(request.getHeader("Upgrade"))
				;
	}

	protected ForwardingConnection createForwardingConnection(Request baseRequest, SocketChannel vsiteChannel)
	{
		HttpChannel httpChannel = baseRequest.getHttpChannel();
		Connector connector = httpChannel.getConnector();
		return new ForwardingConnection(httpChannel.getEndPoint(),
				connector.getExecutor(),
				vsiteChannel);
	}

	protected void prepareErrorResponse(Response response, int code, String message) throws IOException
	{
		response.sendError(code, message);
	}

	protected void prepare101Response(Response response)
	{
		response.setStatus(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
		HttpFields.Mutable responseFields = response.getHttpFields();
		responseFields.put(UPGRADE_HDR);
		responseFields.put(CONNECTION_HDR);
	}

	private static final HttpField UPGRADE_HDR = new PreEncodedHttpField(HttpHeader.UPGRADE,
			REQ_UPGRADE_HEADER_VALUE);

	private static final HttpField CONNECTION_HDR = new PreEncodedHttpField(HttpHeader.CONNECTION,
			HttpHeader.UPGRADE.asString());

	protected SocketChannel connectToVsite(VSite vsite, String requestURL, HttpServletRequest req) throws Exception {
		URI u = URI.create(vsite.resolve(requestURL));
		URI uWithQuery = Servlet.addQueryToURI(u, req.getQueryString());
		final HttpGet get = new HttpGet(uWithQuery);
		Servlet.prepareRequest(get, uWithQuery, vsite, req, gateway);
		SocketChannel s = openSocketChannel(u);
		doHandshake(s, uWithQuery, get.getHeaders());
		return s;
	}

	@SuppressWarnings("resource")
	public void doHandshake(SocketChannel s, URI u, Header[] headers) throws Exception {
		OutputStream os = ChannelUtils.newOutputStream(s, 65536);
		PrintWriter pw = new PrintWriter(os, true, Charset.forName("UTF-8"));
		String path = u.getPath();
		if(u.getQuery()!=null) {
			path += "?"+u.getQuery();
		}
		pw.format("GET %s HTTP/1.1\r\n", path);
		logger.debug("--> GET {} HTTP/1.1", path);
		pw.format("Host: %s\r\n", u.getHost());
		logger.debug("--> Host: {}", u.getHost());
		for(Header h: headers) {
			pw.format("%s: %s\r\n", h.getName(), h.getValue());
			logger.debug("--> {}: {}", h.getName(), h.getValue());
		}
		pw.format("\r\n");
		logger.debug("-->");
		InputStream is = ChannelUtils.newInputStream(s, 65536);
		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		boolean first = true;
		String line=null;
		while( (line=in.readLine())!=null) {
			logger.debug("<-- {}", line);
			if(line.length()==0)break;
			if(first && line!=null && !line.startsWith("HTTP/1.1 101")) {
				throw new IOException("Backend site cannot handle UNICORE-Socket-Forwarding");
			}
			first = false;
		}
	}

	public SocketChannel openSocketChannel(URI u) throws Exception {
		SocketChannel s = SocketChannel.open(new InetSocketAddress(u.getHost(), u.getPort()));
		s.configureBlocking(false);
		if("http".equalsIgnoreCase(u.getScheme())){
			return s;
		}
		else if("https".equalsIgnoreCase(u.getScheme())) {
			DefaultClientConfiguration cc = gateway.getClientFactory().getClientConfiguration();
			SSLEngine sslEngine = HttpUtils.createSSLContext(cc).createSSLEngine(u.getHost(), u.getPort());
			sslEngine.setUseClientMode(true);
			return new SSLSocketChannel(s, sslEngine, null);
		}
		else throw new IOException();
	}

}