/**
 * Copyright (c) 2005, Forschungszentrum Juelich
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met: 
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *  * Neither the name of the Forschungszentrum Juelich nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package eu.unicore.gateway;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.logging.log4j.Logger;
import org.codehaus.stax2.XMLOutputFactory2;
import org.codehaus.stax2.XMLStreamWriter2;
import org.codehaus.stax2.evt.XMLEventFactory2;
import org.codehaus.stax2.ri.Stax2EventWriterImpl;

import com.ctc.wstx.stax.WstxEventFactory;

import eu.unicore.gateway.base.RawMessageExchange;
import eu.unicore.gateway.base.Servlet;
import eu.unicore.gateway.client.HttpClientFactory;
import eu.unicore.gateway.soap.SoapFault;
import eu.unicore.gateway.soap.SoapFault.FaultCode;
import eu.unicore.gateway.soap.SoapVersion;
import eu.unicore.gateway.util.AbstractStreamReaderRequestEntity;
import eu.unicore.gateway.util.BufferingProxyReader;
import eu.unicore.gateway.util.LogUtil;
import eu.unicore.util.Log;

/**
 * Main entry point for SOAP processing.
 * 
 * @author roger
 * @author golbi
 * 
 * TODO we could optimize: if the client accepts gzipped response and server provided it as 
 * gzipped (a common case we could forward as gzipped. However it would be troublesome as
 * jetty GZIP out handler should be disabled in this case and encoding set manually.
 * 
 */
public class POSTHandler
{
	private static final Logger log = LogUtil.getLogger(LogUtil.GATEWAY, POSTHandler.class);
	private final boolean chunked;
	private final SiteOrganiser organiser;
	private final IConsignorProducer consignorProducer;
	private final HttpClientFactory clientFactory;
	private final String serverUri;
	private HeadersParser hdrParser;
	private final static int FORWARD_BUF_SIZE = 10240;


	public POSTHandler(SiteOrganiser organiser, IConsignorProducer consignorProducer, 
		HttpClientFactory clientFactory, boolean chunked, String serverUri)
	{
		this.organiser = organiser;
		this.consignorProducer = consignorProducer;
		this.chunked = chunked;
		this.clientFactory = clientFactory;
		this.serverUri = serverUri;
		hdrParser = new HeadersParser(serverUri);
	}

	
	/**
	 * The message will be read and cached until either the headers have been fully read,
	 * or the destination can be figured out, from the WS-Addressing headers. 
	 * <p>	 
	 * Then, the cached XML will be written to the output, with the consignor assertion inserted
	 * just at the header beginning. 
	 * <p>
	 * If there is no header one is created (to carry the consignor assertion).
	 */
	public void invoke(final RawMessageExchange exchange) throws Exception
	{
		String destination = "?";
		try
		{
			if(exchange.isSOAP()){
				hdrParser.parseHeaders(exchange);
			}
			//get the certpath of the ssl connection (null if not ssl connection)
			final X509Certificate[] certs = (X509Certificate[])exchange.getProperty(
				RawMessageExchange.X509);
			final String clientIP=(String)exchange.getProperty(RawMessageExchange.REMOTE_IP);
			
			VSite site=figureOutDestination(exchange);
			if(site == null){
				// this results in a 404 response
				String msg = "Could not resolve a site for <"+getAddress(exchange)+">";
				throw new FileNotFoundException(msg);
			}
			
			// store GW URL as sent by the client
			String gwURL = Servlet.extractGatewayURL(exchange.getServletRequest(), site.getName());
			exchange.setProperty(RawMessageExchange.GATEWAY_EXTERNAL_URL, gwURL);
			
			destination = exchange.getDestination();
			URI uri = URI.create(destination);

			//We stream the content through.
			//If non-chunked mode is turned on (for debugging) 
			//we must cache as we need to know the content-length
			AbstractHttpEntity requestentity = null;
			if (chunked)
			{
				requestentity = new AbstractStreamReaderRequestEntity(chunked)
				{
					@Override
					public void writeTo(OutputStream os) throws IOException
					{
						try
						{
							writeToOutputStream(exchange, os, certs, clientIP, 
								consignorProducer, log);
						}
						catch (XMLStreamException e)
						{
							IOException ioe = new IOException();
							ioe.initCause(e);
							throw ioe;
						}
					}

					@Override
					public InputStream getContent() throws IOException,
							IllegalStateException
					{
						Reader r = exchange.getReader();
						return new ReaderInputStream(r, "UTF-8"); 
					}
					
					@Override
					public void close() throws IOException{
						// NOP, I guess
					}
				};
			}
			else
			{
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				writeToOutputStream(exchange, baos, certs, clientIP, consignorProducer, log);
				requestentity = new ByteArrayEntity(baos.toByteArray(), ContentType.APPLICATION_SOAP_XML);
			}

			log.debug("Dispatching to: {}", destination);
			HttpPost post = prepareForwardedPOST(uri, exchange, requestentity);
			callServiceAndForwardResponse(post, uri, exchange, site);
			log.debug("Exchange with {} completed", destination);
		} catch (SoapFault e)
		{
			Throwable cause = e.getCause() == null ? e : e.getCause();
			if (exchange.getSoapVersion() != null){
				e.writeFaultToWriter(exchange.getWriter(), exchange.getSoapVersion());
			}
			else{
				throw new ServletException("The request doesn't look like a valid SOAP request.",cause);
			}
		}
	}
	
	private HttpPost prepareForwardedPOST(URI uri, RawMessageExchange exchange, AbstractHttpEntity reqEntity) 
		throws Exception
	{
		HttpPost post = clientFactory.makePostMethod(uri.toASCIIString(), reqEntity);

		String contentType = (String)exchange.getProperty(RawMessageExchange.CONTENT_TYPE);
		if (contentType != null)
			post.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
		else
			post.setHeader(HttpHeaders.CONTENT_TYPE, "text/xml; charset=UTF-8");
		
		String soapAction = (String)exchange.getProperty(RawMessageExchange.SOAP_ACTION);
		if (soapAction!=null)
		{
			post.setHeader("SOAPAction", soapAction);
		} else
		{ 
			//If no SOAPAction in HTTP header let's try to get it from SOAP 
			//Header WSA element and put it as HTTP header so signature code on final server
			//will get it without parsing the request
			soapAction = exchange.getWsaAction();
			if (soapAction != null)
			{
				if(log.isDebugEnabled()) 
					log.debug("Generating HTTP SOAPAction from SOAP header: "+soapAction);
				post.setHeader("SOAPAction", soapAction);
			}
		}
		String clientIP=(String)exchange.getProperty(RawMessageExchange.REMOTE_IP);
		if(clientIP!=null){
			post.addHeader(RawMessageExchange.CONSIGNOR_IP_HEADER, clientIP);
		}
		String gwHost=(String)exchange.getProperty(RawMessageExchange.GATEWAY_EXTERNAL_URL);
		if(gwHost!=null){
			post.addHeader(RawMessageExchange.GATEWAY_EXTERNAL_URL, gwHost);
		}
		if(!exchange.isSOAP()){
			X509Certificate[] certPath = (X509Certificate[])exchange.getProperty(RawMessageExchange.X509);
			if (certPath != null)
			{
				try{
					Header consignor = consignorProducer.getConsignorHeader(certPath,clientIP);
					if(consignor!=null)post.addHeader(consignor);
				}catch(Exception e){
					Log.logException("Cannot create signed DN header",e,log);
				}
			}
		}
		copyRequestHeaders(post, exchange.getServletRequest());
		
		return post;
	}
	
	private void callServiceAndForwardResponse(HttpPost post, URI serviceUri, 
		RawMessageExchange exchange, VSite site) throws Exception
	{
		ClassicHttpResponse response = null;
		try
		{
			try
			{
				HttpClient client =null;
				synchronized(site){
					client = site.getClient();
					if(client==null){
						client = clientFactory.makeHttpClient(serviceUri.toURL());
						site.setClient(client);	
					}
				}
				response = client.executeOpen(null, post, HttpClientContext.create());
			} catch (Exception e)
			{
				String reason= "Problem when forwarding a client request to a VSite: "
					+ e.toString();
				if(exchange.isSOAP()){
					throw new SoapFault(serverUri, FaultCode.RECEIVER, reason, e);				
				}else{
					throw new ServletException(reason, e);
				}
			}
			log.debug("Status of dispatch to service: {} {}", response.getCode(), response.getReasonPhrase());
			try 
			{
				Header contentEnc = response.getFirstHeader("Content-Encoding");
				String contentEncoding = (contentEnc != null) ?	contentEnc.getValue() : null;
				exchange.getServletResponse().setStatus(response.getCode());
				copyResponseHeaders(response, exchange.getServletResponse());
				
				if(response.getEntity()!=null && response.getEntity().getContent()!=null){
					InputStream ris = "gzip".equalsIgnoreCase(contentEncoding) ? 
							new GZIPInputStream(response.getEntity().getContent()) : 
								response.getEntity().getContent();
					Charset charset = response.getEntity().getContentEncoding() == null ? 
							Charset.forName("utf8") :
							Charset.forName(response.getEntity().getContentEncoding());
					forwardResponse(ris, charset, exchange, log);
				}
			
			} catch (Exception e)
			{
				String reason = "Problem when forwarding a response " +
						"from a VSite to a client: " + e.toString();
				if(exchange.isSOAP()){
					throw new SoapFault(serverUri, FaultCode.RECEIVER, reason, e);
				}
				else{
					throw new ServletException(reason, e);
				}
			}
		} finally
		{
			IOUtils.closeQuietly(response);
		}
	}

	final static List<String>excludedResponseHeaders = Arrays.asList(new String[]{
			"content-encoding","content-length", "date", 
			"access-control-allow-origin", "access-control-allow-credentials",
			"access-control-expose-headers",
			"x-frame-options"
	});
	
	
	private void copyResponseHeaders(ClassicHttpResponse fromSite, HttpServletResponse toClient){
		for (Header h: fromSite.getHeaders()){
			if(!excludedResponseHeaders.contains(h.getName().toLowerCase())){
				toClient.addHeader(h.getName(), h.getValue());
			}
		}
	}

	final static List<String>excludedRequestHeaders = Arrays.asList(new String[]{
			"transfer-encoding", "content-encoding", "content-length", "content-type",
			"host", "soapaction", "x-unicore-gateway", "x-unicore-consignor-ip", "x-unicore-consignor"});
	
	private void copyRequestHeaders(HttpPost toSite, HttpServletRequest fromClient){
		Enumeration<String> headerNames = fromClient.getHeaderNames();
		while (headerNames.hasMoreElements()){
			String headerName = headerNames.nextElement();
			if(!excludedRequestHeaders.contains(headerName.toLowerCase())){
				toSite.addHeader(headerName,fromClient.getHeader(headerName));
			}
		}
	}
	
	//places destination in exchange and returns the vsite, or null if not found
	private VSite figureOutDestination(RawMessageExchange ex) throws URISyntaxException
	{
		VSite vsite = null;
		String address = getAddress(ex);
		String clientIP = (String)ex.getProperty(RawMessageExchange.REMOTE_IP);
		
		vsite = organiser.match(address, clientIP);
		if (vsite != null)
		{
			ex.setDestination(vsite.resolve(address));
		}
		
		return vsite;
	}

	private String getAddress(RawMessageExchange ex){
		String address = ex.getWsaToAddress();
		if (address == null)
		{
			address = ex.getRequestURL();
		}
		return address;
	}

	/*
	 * The methods below are made static as must be well tested.  
	 */
	public static void forwardResponse(InputStream ris, Charset responseCharSet,
		RawMessageExchange exchange, Logger log) throws Exception
	{
		Writer target = exchange.getWriter();
		byte[] buf = new byte[FORWARD_BUF_SIZE];
		int r = ris.read(buf, 0, buf.length);
		while (r > 0)
		{
			String s = new String(buf, 0, r, responseCharSet);
			if (log.isTraceEnabled())
				log.trace("Response part: " + s);
			target.write(s);
			r = ris.read(buf, 0, buf.length);
		}
		target.flush();
	}

	/**
	 * Copies data read from exchange to the os. At the marked position (in ex) consignor token is inserted. 
	 */
	public static void writeToOutputStream(RawMessageExchange ex, OutputStream os, X509Certificate[] certs, 
			String ip, IConsignorProducer producer, Logger log) throws XMLStreamException, IOException
	{
		try
		{
			BufferingProxyReader reader = ex.getReader();

			int boundary = reader.getMarkedPos();
			if(boundary>0){
				//replay buffered data up to the marked position
				reader.replay(os, 0, boundary, "UTF-8");

				//add consignor token
				ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
				writeConsignorAssertionToBaos(baos, !ex.isHeaderPresent(), certs, ip, 
						ex.getSoapVersion(), producer, log);
				baos.writeTo(os);

			}
			
			//replay the rest of the buffer
			reader.replayRest(os, boundary, "UTF-8");
			
			//copy the rest of the incoming request
			char[] buf = new char[1024];
			Writer w = new OutputStreamWriter(os);
			int r = reader.read(buf);
			while (r > 0)
			{
				w.write(buf, 0, r);
				r = reader.read(buf);
			}
			w.close();
		}
		catch (XMLStreamException e){
			LogUtil.logException("Error writing to destination.", e, log);
			throw e;
		}
	}
	
	private static void writeConsignorAssertionToBaos(ByteArrayOutputStream baos, boolean createHeader, 
		X509Certificate consignorCC[], String ip, SoapVersion soapVersion, 
		IConsignorProducer consignorProducer, Logger log) throws XMLStreamException
	{
		XMLOutputFactory2 xof = (XMLOutputFactory2)XMLOutputFactory2.newInstance();
		xof.setProperty(XMLOutputFactory2.IS_REPAIRING_NAMESPACES, false);
		XMLStreamWriter2 xsw = xof.createXMLStreamWriter(new OutputStreamWriter(baos), "UTF-8");
		XMLEventWriter xew = new Stax2EventWriterImpl(xsw);
		writeConsignorAssertion(xew, createHeader, consignorCC, ip, soapVersion, consignorProducer, log);
		xsw.close();
	}

	private static void writeConsignorAssertion(XMLEventWriter xew, boolean createHeader, 
		X509Certificate consignorCC[], String ip, SoapVersion soapVersion, 
		IConsignorProducer consignorProducer, Logger log)
	{
		try{
			XMLEventFactory2 events = null;
			List<XMLEvent> events2 = consignorProducer.getConsignorAssertion(consignorCC, ip, soapVersion);
			if (createHeader)
			{
				log.debug("Creating header");
				events=new WstxEventFactory();
				QName header = soapVersion.getHeader();
				xew.add(events.createStartElement(header.getPrefix(),header.getNamespaceURI(),header.getLocalPart()));
			}
			for (XMLEvent event: events2)
			{
				xew.add(event);
			}
			if (createHeader)
			{
				QName header = soapVersion.getHeader();
				xew.add(events.createEndElement(header.getPrefix(),header.getNamespaceURI(),header.getLocalPart()));			
			}
		} catch (Exception e){
			LogUtil.logException("Can't produce Consignor assertion. Shouldn't happen!", e, log);
		}
	}
}
