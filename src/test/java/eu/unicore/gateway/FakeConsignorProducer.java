package eu.unicore.gateway;

import java.security.cert.X509Certificate;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;

class FakeConsignorProducer implements IConsignorProducer
{
	
	public Header getConsignorHeader(X509Certificate[] certChain, String ip){
		return new BasicHeader("X-UNICORE-Consignor", "DN=\"CN=Test\";DSIG=abcd");
	}
}