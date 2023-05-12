package eu.unicore.gateway;

import java.security.cert.X509Certificate;
import java.util.List;

import javax.xml.stream.events.XMLEvent;

import org.apache.hc.core5.http.Header;

import eu.unicore.gateway.soap.SoapVersion;

public interface IConsignorProducer
{
	public List<XMLEvent> getConsignorAssertion(X509Certificate[] certChain, String ip, SoapVersion soapVer) 
		throws Exception;
	
	public Header getConsignorHeader(X509Certificate[] certChain, String ip)
		throws Exception;
	
}
