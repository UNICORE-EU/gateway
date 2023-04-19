package eu.unicore.gateway.base;

import java.io.BufferedReader;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.XMLInputFactory2;

import eu.unicore.gateway.soap.SoapVersion;
import eu.unicore.gateway.util.BufferingProxyReader;

/**
 * Holds information during the processing of a single request.
 * This version uses raw Reader and Writer objects and also provides an XMLEventReader.
 * <p>
 * The usage is non intuitive: first data may be read from the XML reader. The characters which are 
 * consumed by the XML reader are recorded (internally in the reader). At any point of time
 * this class user may stop reading the XML events, and start reading from the Reader. The char data
 * processed as XML previously may be replied, and the rest of data may be then read normally.
 * 
 * @see BufferingProxyReader
 * @author golbi
 * @author roger
 */
public class RawMessageExchange 
{
	public final static String X509 = "cert";	
	public final static String REMOTE_IP = "peer";
	public final static String SOAP_ACTION = "soapaction";
	public final static String CONTENT_TYPE= "Content-Type";

	// special header for forwarding the client's IP address to the VSite
	public final static String CONSIGNOR_IP_HEADER = "X-UNICORE-Consignor-IP";

	// special header for forwarding the client's DN plus a signature to the VSite
	public final static String CONSIGNOR_HEADER = "X-UNICORE-Consignor";
	
	// special header for forwarding the gateway host as seen by the client to the VSite
	public final static String GATEWAY_EXTERNAL_URL = "X-UNICORE-Gateway";
		
	private BufferingProxyReader reader;
	private Writer writer;
	private boolean headerPresent;

	private XMLEventReader eventReader;
	private String requestUrl;
	private String wsaToAddress;
	private String wsaAction;
	private SoapVersion soapVersion;
	private Map<String,Object> parameterMap=new HashMap<String, Object>();
	private String destination;
	private HttpServletResponse servletResponse;
	private boolean isSOAP = true;
	private boolean isMultipart = false;
	private int multipartOffset = 0;
	private HttpServletRequest httpRequest;

	private static XMLInputFactory inFact = XMLInputFactory2.newInstance();

	public RawMessageExchange(Reader r, Writer w, int maxHeader)
	{
		reader = new BufferingProxyReader(r, maxHeader);
		writer = w;
		setHeaderPresent(false);
	}


	public Object getProperty(String key){
		return parameterMap.get(key);
	}

	public void setProperty(String key,Object value){
		parameterMap.put(key, value);
	}

	public String getRequestURL()
	{
		return requestUrl;
	}

	public void setRequestURL(String requesturl)
	{
		this.requestUrl = requesturl;
	}

	public Map<String,Object> getParameterMap()
	{
		return parameterMap;
	}

	public void setParameterMap(Map<String,Object> parametermap)
	{
		this.parameterMap = parametermap;
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public QName getSOAPHeaderName(){
		return getSoapVersion().getHeader();
	}

	public QName getSOAPFaultName(){
		return getSoapVersion().getFault();
	}

	public SoapVersion getSoapVersion() {
		return soapVersion;
	}

	public void setSoapVersion(SoapVersion soapVersion) {
		this.soapVersion = soapVersion;
	}

	public String getWsaToAddress() {
		return wsaToAddress;
	}

	public void setWsaToAddress(String wsaToAddress) {
		this.wsaToAddress = wsaToAddress;
	}

	public String getWsaAction() {
		return wsaAction;
	}

	public void setWsaAction(String wsaAction) {
		this.wsaAction = wsaAction;
	}

	public boolean isSOAP() {
		return isSOAP;
	}

	public void setSOAP(boolean isSOAP) {
		this.isSOAP = isSOAP;
	}

	public boolean isMultipart() {
		return isMultipart;
	}

	public int getMultipartOffset() {
		return multipartOffset;
	}

	public void setMultipart(boolean isMultipart) {
		this.isMultipart = isMultipart;
	}

	public XMLEventReader getEventReader() throws XMLStreamException {
		if(eventReader==null){
			if(isMultipart){
				try{
					// fast-forward to beginning of XML part...
					BufferedReader br = new BufferedReader(reader,1);
					while(true){
						String line = br.readLine();
						if(line.isEmpty()){
							multipartOffset = reader.getCurrentPosition();
							break;
						}
					}
				}catch(Exception ex){
					throw new XMLStreamException("That does not look like a supported message format");
				}
			}
			eventReader = inFact.createXMLEventReader(reader);
		}
		return eventReader;
	}

	public BufferingProxyReader getReader()
	{
		return reader;
	}

	public Writer getWriter()
	{
		return writer;
	}

	public void setHeaderPresent(boolean headerPresent)
	{
		this.headerPresent = headerPresent;
	}

	public boolean isHeaderPresent()
	{
		return headerPresent;
	}

	public HttpServletResponse getServletResponse()
	{
		return servletResponse;
	}

	public void setServletResponse(HttpServletResponse servletResponse)
	{
		this.servletResponse = servletResponse;
	}


	public HttpServletRequest getServletRequest()
	{
		return httpRequest;
	}

	public void setServletRequest(HttpServletRequest servletRequest)
	{
		this.httpRequest = servletRequest;
	}
}
