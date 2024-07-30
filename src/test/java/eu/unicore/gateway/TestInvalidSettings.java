package eu.unicore.gateway;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileNotFoundException;

import org.junit.jupiter.api.Test;

import eu.unicore.util.configuration.ConfigurationException;

public class TestInvalidSettings {

	@Test
	public void testInvalidSecuritySettings()
			throws Exception {
		File gp = new File("src/test/resources/gateway.properties");
		File cp = new File("src/test/resources/connection.properties");
		File sp1 = new File("src/test/resources/security-invalid1.properties");
		File sp2 = new File("src/test/resources/security-invalid2.properties");
		File sp3 = new File("src/test/resources/security-invalid3.properties");
		File sp4 = new File("src/test/resources/security-invalid4.properties");
		File sp5 = new File("src/test/resources/security-invalid5.properties");
		File sp6 = new File("src/test/resources/security-invalid6.properties");
		try
		{
			new Gateway(gp, cp, sp1);
			fail("Started GW with missing security config");
		} catch (FileNotFoundException e) {
			//expected
		} catch (Exception e) {
			e.printStackTrace();
			fail("Got wrong exception");
		}
		

		try
		{
			new Gateway(gp, cp, sp2);
			fail("Started GW with missing truststore");
		} catch (ConfigurationException e) {
		}

		try
		{
			new Gateway(gp, cp, sp3);
			fail("Started GW with wrong ks password");
		} catch (ConfigurationException e) {
		}

		try
		{
			new Gateway(gp, cp, sp4);
			fail("Started GW with ks without key alias");
		} catch (ConfigurationException e) {
			assertTrue(e.getMessage().contains("doesn't contain any key"));
		}
		
		try
		{
			new Gateway(gp, cp, sp5);
			fail("Started GW with ks without a password");
		} catch (ConfigurationException e) {
			assertTrue(e.getMessage().contains("Keystore was tampered with, or password was incorrect"));
		}
		
		try
		{
			new Gateway(gp, cp, sp6);
			fail("Started GW with ts without a password");
		} catch (ConfigurationException e) {
			assertTrue(e.getMessage().contains("Keystore was tampered with, or password was incorrect"));
		}
	}
}
