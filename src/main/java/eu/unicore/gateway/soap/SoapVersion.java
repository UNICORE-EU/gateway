package eu.unicore.gateway.soap;


import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * used to identify the SOAP version used in the current message exchange
 */
public interface SoapVersion {

    public double getVersion();

    public String getNamespace();

    public String getPrefix();

    public QName getHeader();

    public QName getBody();

    public QName getFault();

    // Helper methods for outputting SOAP related data
    //-------------------------------------------------------------------------
    public void writeStartElement(XMLStreamWriter out, String localName) throws XMLStreamException;

    public void writeFault(XMLStreamWriter out, SoapFault fault, String hostUri) throws XMLStreamException;

}
