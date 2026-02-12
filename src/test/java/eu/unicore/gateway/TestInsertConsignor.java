package eu.unicore.gateway;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.security.cert.X509Certificate;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import eu.unicore.gateway.properties.GatewayProperties;
import eu.unicore.gateway.util.LogUtil;
import eu.unicore.security.canl.AuthnAndTrustProperties;
import eu.unicore.security.canl.CredentialProperties;
import eu.unicore.security.canl.TruststoreProperties;

public class TestInsertConsignor
{
	public static final Logger log = LogUtil.getLogger("gateway", TestInsertConsignor.class);
	private File secProps = new File("src/test/resources/gateway.properties");

	@Test
	public void testConsignorHTTPHeader() throws Exception
	{
		AuthnAndTrustProperties sp = new AuthnAndTrustProperties(secProps,
				GatewayProperties.PREFIX + TruststoreProperties.DEFAULT_PREFIX, 
				GatewayProperties.PREFIX + CredentialProperties.DEFAULT_PREFIX);
		ConsignorProducer cp = new ConsignorProducer(true, sp);
		X509Certificate[]certChain=sp.getCredential().getCertificateChain();
		String r = cp.getConsignorHeader(certChain, "127.0.0.1").toString();
		System.out.println("Consignor header: \n"+r);
		assertTrue(r.contains(certChain[0].getSubjectX500Principal().getName()));
	}
	
	@Test
	public void testConsignorHTTPHeaderUnsigned() throws Exception
	{
		AuthnAndTrustProperties sp = new AuthnAndTrustProperties(secProps,
				GatewayProperties.PREFIX + TruststoreProperties.DEFAULT_PREFIX, 
				GatewayProperties.PREFIX + CredentialProperties.DEFAULT_PREFIX);
		ConsignorProducer cp = new ConsignorProducer(false, sp);
		X509Certificate[]certChain=sp.getCredential().getCertificateChain();
		String r = cp.getConsignorHeader(certChain, "127.0.0.1").toString();
		System.out.println("Consignor header: \n"+r);
		assertTrue(r.contains(certChain[0].getSubjectX500Principal().getName()));
	}
}
