/*
 * Copyright (c) 2009 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE file for licencing information.
 *
 * Created on 2009-10-23
 * Author: K. Benedyczak <golbi@mat.umk.pl>
 */

package eu.unicore.gateway;

import java.security.cert.X509Certificate;
import java.util.List;

import javax.xml.stream.events.XMLEvent;

import org.apache.http.Header;

import eu.unicore.gateway.soap.SoapVersion;

public interface IConsignorProducer
{
	public List<XMLEvent> getConsignorAssertion(X509Certificate[] certChain, String ip, SoapVersion soapVer) 
		throws Exception;
	
	public Header getConsignorHeader(X509Certificate[] certChain, String ip)
		throws Exception;
	
}
