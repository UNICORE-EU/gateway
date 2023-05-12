package eu.unicore.gateway.soap;

import java.io.Writer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.codehaus.stax2.XMLOutputFactory2;


/**
 * Represents a SOAP fault which occurred while processing the message.
 */
public class SoapFault extends Exception
{
	private static final long serialVersionUID = 1L;
	private String reason;
	private FaultCode code;
	private String serverUri;
	private static final XMLOutputFactory outFact = XMLOutputFactory2.newInstance();
	
	/**
	 * Those constants will be correctly mapped to SOAP 1.1 or 1.2 fault
	 * codes
	 */
	public static enum FaultCode
	{
		SENDER, RECEIVER, VERSION_MISMATCH, MUST_UNDERSTAND
	};

	public SoapFault(String serverUri, FaultCode code, String reason)
	{
		this(serverUri, code, reason, null);
	}

	public SoapFault(String serverUri, FaultCode code, String reason, Throwable cause)
	{
		super(cause);
		if (code == null)
			throw new IllegalArgumentException("FaultCode can not be null");
		if (reason == null)
			throw new IllegalArgumentException("Fault reason can not be null");

		this.code = code;
		this.reason = reason;
		this.serverUri = serverUri;
	}

	public String getReason()
	{
		return reason;
	}

	public FaultCode getCode()
	{
		return code;
	}

	public String toString()
	{
		String cause = getCause() == null ? "" : " cause: " + getCause().toString();
		return "SOAP Fault code: " + code.name() + " reason: " + reason
			+ " " + cause;
	}
	
	public void writeFaultToWriter(Writer w, SoapVersion soapVersion) throws XMLStreamException
	{
		XMLStreamWriter out = outFact.createXMLStreamWriter(w);
		out.writeStartDocument();
		out.setPrefix(soapVersion.getPrefix(), soapVersion.getNamespace());
		soapVersion.writeStartElement(out, "Envelope");
		out.writeNamespace(soapVersion.getPrefix(), soapVersion.getNamespace());
		soapVersion.writeStartElement(out, "Body");
		soapVersion.writeFault(out, this, serverUri);
		out.writeEndElement();
		out.writeEndElement();
		out.writeEndDocument();
	}
}