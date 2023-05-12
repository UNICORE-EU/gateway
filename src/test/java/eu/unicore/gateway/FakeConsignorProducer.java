package eu.unicore.gateway;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.events.XMLEvent;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.codehaus.stax2.evt.XMLEventFactory2;

import com.ctc.wstx.stax.WstxEventFactory;

import eu.unicore.gateway.soap.SoapVersion;

class FakeConsignorProducer implements IConsignorProducer
{
	@Override
	public List<XMLEvent> getConsignorAssertion(X509Certificate[] certChain, String ip,
			SoapVersion soapVer) throws Exception
	{
		List<XMLEvent> ret = new ArrayList<XMLEvent>();
		XMLEventFactory2 events = new WstxEventFactory();
		ret.add(events.createStartElement("tst", "http://consignor/test", "test"));
		ret.add(events.createEndElement("tst", "http://consignor/test", "test"));			
		return ret;
	}
	
	public Header getConsignorHeader(X509Certificate[] certChain, String ip){
		return new BasicHeader("X-UNICORE-Consignor", "DN=\"CN=Test\";DSIG=abcd");
	}
}