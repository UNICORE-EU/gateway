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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.events.XMLEvent;

import org.apache.commons.codec.binary.Base64;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;

import eu.emi.security.authn.x509.X509Credential;
import eu.unicore.security.canl.AuthnAndTrustProperties;

/**
 * Registry and producer of consignor assertions. Assertions are returned as
 * XMLEvents lists to ease insertion to header in main GatewayHandler.
 * If there is no header that this code can also generate wrapping header element.
 * Assertions are cached.
 * @author K. Benedyczak
 */
public class ConsignorProducer implements IConsignorProducer
{
	//last CACHE_SIZE tokens are cached (but only if not-signed mode is on).
	private static final int CACHE_SIZE = 8;
	private PrivateKey myKey;
	private boolean doSigned;
	private LinkedHashMap<Key, List<XMLEvent>> cache;
	private LinkedHashMap<Key, Header> cache2;

	public ConsignorProducer(boolean doSigned, AuthnAndTrustProperties securityProperties) 
		throws KeyStoreException, NoSuchAlgorithmException, CertificateException, 
		FileNotFoundException, IOException
	{
		this.doSigned=doSigned;
		cache = new LinkedHashMap<>(CACHE_SIZE + 2, 1.0f, true)
		{
			private static final long serialVersionUID = 1L;

			protected boolean removeEldestEntry(
					Map.Entry<Key, List<XMLEvent>> eldest)
			{
				return size() > CACHE_SIZE;
			}
		};
		cache2 = new LinkedHashMap<>(CACHE_SIZE + 2, 1.0f, true)
		{
			private static final long serialVersionUID = 1L;

			protected boolean removeEldestEntry(
					Map.Entry<Key, Header> eldest)
			{
				return size() > CACHE_SIZE;
			}
		};
		reinit(securityProperties);
	}
	
	public void reinit(AuthnAndTrustProperties securityProperties) throws KeyStoreException, NoSuchAlgorithmException, 
		CertificateException, FileNotFoundException, IOException
	{
		X509Credential credential = securityProperties.getCredential();
		myKey = doSigned? credential.getKey() : null;
		cache.clear();
		cache2.clear();
	}
	public Header getConsignorHeader(X509Certificate[] certChain, String ip) throws Exception {
		Header ret = null;
		X509Certificate cert = (certChain == null) ? null: certChain[0];
		if (myKey == null && cert!=null)
		{
			ret = headerCacheGet(cert,ip);
			if(ret!=null)return ret;
		}
		String dn = cert.getSubjectX500Principal().getName();
		String sig = sign(dn);
		ret = new BasicHeader("X-UNICORE-Consignor", "DN=\""+dn+"\";DSIG="+sig);
		if (myKey == null && cert!=null){
			headerCacheAdd(cert,ip, ret);
		}
		return ret;
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
