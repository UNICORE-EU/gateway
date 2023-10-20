package eu.unicore.gateway;

import java.security.cert.X509Certificate;

import org.apache.hc.core5.http.Header;

public interface IConsignorProducer
{
	
	public Header getConsignorHeader(X509Certificate[] certChain, String ip)
		throws Exception;
	
}
