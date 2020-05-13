package eu.unicore.gateway;

import java.io.File;
import java.io.FileNotFoundException;

import eu.unicore.bugsreporter.annotation.RegressionTest;
import eu.unicore.bugsreporter.annotation.RegressionTests;
import eu.unicore.util.configuration.ConfigurationException;

import junit.framework.TestCase;

public class TestInvalidSettings extends TestCase {

	@RegressionTests({
		@RegressionTest(url="https://sourceforge.net/tracker/index.php?func=detail&aid=3025126&group_id=102081&atid=633902", 
		description="This test verifies that gateway won't start at all with invalid security settings"),
		@RegressionTest(url="https://sourceforge.net/tracker/index.php?func=detail&aid=3006856&group_id=102081&atid=633902")
	})
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
			assertTrue(e.getMessage(), e.getMessage().contains("doesn't contain any key"));
		}
		
		try
		{
			new Gateway(gp, cp, sp5);
			fail("Started GW with ks without a password");
		} catch (ConfigurationException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("Keystore was tampered with, or password was incorrect"));
		}
		
		try
		{
			new Gateway(gp, cp, sp6);
			fail("Started GW with ts without a password");
		} catch (ConfigurationException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("Keystore was tampered with, or password was incorrect"));
		}
	}
}
