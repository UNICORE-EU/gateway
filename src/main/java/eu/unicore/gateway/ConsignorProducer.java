/*
 * Copyright (c) 2007, 2008 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENSE.ICM file for licensing information.
 *
 * Created on May 22, 2007
 * Author: K. Benedyczak <golbi@mat.umk.pl>
 */

package eu.unicore.gateway;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlObject;
import org.codehaus.stax2.evt.XMLEventFactory2;

import xmlbeans.org.oasis.saml2.assertion.AssertionDocument;

import com.ctc.wstx.stax.WstxEventFactory;

import eu.emi.security.authn.x509.X509Credential;
import eu.unicore.gateway.soap.SoapVersion;
import eu.unicore.gateway.util.LogUtil;
import eu.unicore.samly2.SAMLConstants.AuthNClasses;
import eu.unicore.security.UnicoreSecurityFactory;
import eu.unicore.security.canl.AuthnAndTrustProperties;
import eu.unicore.security.consignor.ConsignorAPI;
import eu.unicore.security.consignor.ConsignorAssertion;
import eu.unicore.security.dsig.DSigException;

/**
 * Registry and producer of consignor assertions. Assertions are returned as
 * XMLEvents lists to ease insertion to header in main GatewayHandler.
 * If there is no header that this code can also generate wrapping header element.
 * Assertions are cached.
 * @author K. Benedyczak
 */
public class ConsignorProducer implements IConsignorProducer
{
	private static final Logger log =LogUtil.getLogger(LogUtil.GATEWAY,ConsignorProducer.class);
	//last CACHE_SIZE tokens are cached (but only if not-signed mode is on).
	private static final int CACHE_SIZE = 8;
	private String myDN;
	private PrivateKey myKey;
	private int negativeTolerance, validity;
	private boolean doSigned;
	private LinkedHashMap<Key, List<XMLEvent>> cache;
	private LinkedHashMap<Key, Header> cache2;

	public ConsignorProducer(boolean doSigned, int negativeTolerance,
			int validity, AuthnAndTrustProperties securityProperties) 
		throws KeyStoreException, NoSuchAlgorithmException, CertificateException, 
		FileNotFoundException, IOException
	{
		this.negativeTolerance = negativeTolerance;
		this.validity = validity;
		this.doSigned=doSigned;
		cache = new LinkedHashMap<Key, List<XMLEvent>>(CACHE_SIZE + 2, 1.0f, true)
		{
			private static final long serialVersionUID = 1L;

			protected boolean removeEldestEntry(
					Map.Entry<Key, List<XMLEvent>> eldest)
			{
				return size() > CACHE_SIZE;
			}
		};
		cache2 = new LinkedHashMap<Key, Header>(CACHE_SIZE + 2, 1.0f, true)
		{
			private static final long serialVersionUID = 1L;

			protected boolean removeEldestEntry(
					Map.Entry<Key, Header> eldest)
			{
				return size() > CACHE_SIZE;
			}
		};
		init(securityProperties);
	}
	
	private void init(AuthnAndTrustProperties securityProperties) throws KeyStoreException, NoSuchAlgorithmException, 
		CertificateException, FileNotFoundException, IOException
	{
		X509Credential credential = securityProperties.getCredential();
		X509Certificate cert = credential.getCertificate();
		myDN = cert.getSubjectX500Principal().getName();
		myKey = null;
		if (doSigned)
			myKey = credential.getKey();
	}
	
	public List<XMLEvent> getConsignorAssertion(X509Certificate[] certChain, String ip, SoapVersion soapVer) 
			throws Exception
	{
		List<XMLEvent>ret=null;
		
		X509Certificate cert = (certChain == null) ? null: certChain[0];
		
		if (myKey == null && cert!=null)
		{
			ret = cacheGet(cert,ip);
			if(ret!=null)return ret;
		}
		
		ret = new ArrayList<XMLEvent>();
		XMLEventFactory2 eventFactory=new WstxEventFactory();
		AssertionDocument assertionDoc = generateAssertion(certChain, ip);
		save(assertionDoc, eventFactory, ret);
		if (myKey == null && cert!=null){
			cacheAdd(cert, ip, ret);
		}
		return ret;
	}
	
	public Header getConsignorHeader(X509Certificate[] certChain, String ip) throws Exception {
		Header ret = null;
		X509Certificate cert = (certChain == null) ? null: certChain[0];
		
		if (myKey == null && cert!=null)
		{
			ret = headerCacheGet(cert,ip);
			if(ret!=null)return ret;
		}
		//if(myKey == null)return 
		// TODO yes it is a cheapo solution
		String dn = cert.getSubjectX500Principal().getName();
		String sig = sign(dn);
		
		BasicHeader h = new BasicHeader("X-UNICORE-Consignor", "DN=\""+dn+"\";DSIG="+sig);
		if (myKey == null && cert!=null){
			headerCacheAdd(cert,ip, h);
		}
		return h;
	}
	
	private AssertionDocument generateAssertion(X509Certificate []cert, String ip) throws DSigException {
		ConsignorAPI engine = UnicoreSecurityFactory.getConsignorAPI();
		ConsignorAssertion assertion;
		if (cert != null) 
		{
			if (myKey != null)
				assertion = engine.generateConsignorToken(myDN, cert, myKey,
					negativeTolerance, validity,
					AuthNClasses.TLS, ip);
			else
				assertion = engine.generateConsignorToken(myDN, cert, 
						AuthNClasses.TLS, ip);
		} else
		{
			log.debug("Creating ANONYMOUS SAML consignor token");
			if (myKey != null)
				assertion = engine.generateConsignorToken(myDN, 
						negativeTolerance, validity, myKey, ip);
			else
				assertion = engine.generateConsignorToken(myDN);
		}
		AssertionDocument doc = assertion.getXMLBeanDoc();
		if(log.isDebugEnabled())log.debug("Consignor token: "+doc);
		return doc;
	}
	
	//write an XmlObject into an XMLEvent list
	private void save(XmlObject obj, XMLEventFactory eventFactory, List<XMLEvent> events)throws XMLStreamException{
		XMLEventReader reader=XMLInputFactory.newInstance().createXMLEventReader(obj.newXMLStreamReader());
		while(reader.hasNext()){
			XMLEvent ev=reader.nextEvent();
			if(ev.isEndDocument())break;
			events.add(ev);
		}
	}
	
	private synchronized void cacheAdd(X509Certificate key, String ip, List<XMLEvent> value)
	{
		cache.put(new Key(key,ip), value);
	}

	private synchronized List<XMLEvent> cacheGet(X509Certificate key, String ip)
	{
		return cache.get(new Key(key,ip));
	}
	
	private synchronized void headerCacheAdd(X509Certificate key, String ip, Header value)
	{
		cache2.put(new Key(key,ip), value);
	}

	private synchronized Header headerCacheGet(X509Certificate key, String ip)
	{
		return cache2.get(new Key(key,ip));
	}
	
	private String sign(String toSign) throws GeneralSecurityException, IOException {
		if(myKey==null)return "";
		byte[] hashedToken = hash(toSign.getBytes());
		String alg = "RSA".equalsIgnoreCase(myKey.getAlgorithm())? "SHA1withRSA" : "SHA1withDSA";
		Signature signature = Signature.getInstance(alg);
		signature.initSign(myKey);
		signature.update(hashedToken);
		byte[]signed = signature.sign();
		return new String(Base64.encodeBase64(signed)); 
	}
	
	private byte[] hash(byte[]data) throws GeneralSecurityException {
		MessageDigest md = MessageDigest.getInstance("SHA1");
		md.update(data);
		return md.digest();
	}
	
	private static class Key{
		
		private X509Certificate cert;
		
		private String ip;
		
		public Key(X509Certificate cert,String ip){
			this.cert=cert;
			this.ip=ip;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((cert == null) ? 0 : cert.hashCode());
			result = prime * result + ((ip == null) ? 0 : ip.hashCode());
			return result;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Key other = (Key) obj;
			if (cert == null) {
				if (other.cert != null)
					return false;
			} else if (!cert.equals(other.cert))
				return false;
			if (ip == null) {
				if (other.ip != null)
					return false;
			} else if (!ip.equals(other.ip))
				return false;
			return true;
		}
		
	}
}
