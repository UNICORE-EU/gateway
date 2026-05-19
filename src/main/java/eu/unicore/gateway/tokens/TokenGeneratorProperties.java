package eu.unicore.gateway.tokens;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

import eu.unicore.gateway.util.LogUtil;
import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.configuration.DocumentationReferenceMeta;
import eu.unicore.util.configuration.DocumentationReferencePrefix;
import eu.unicore.util.configuration.PropertiesHelper;
import eu.unicore.util.configuration.PropertyMD;

public class TokenGeneratorProperties extends PropertiesHelper
{

	private static final Logger log = LogUtil.getLogger(LogUtil.CONFIGURATION, TokenGeneratorProperties.class);

	// OIDC server properties for API token generation
	public static final String ENABLE = "enable";
	public static final String ISSUER = "issuer";
	public static final String TOKEN_ENDPOINT = "tokenEndpoint";
	public static final String AUTHZ_ENDPOINT = "authorizationEndpoint";
	public static final String CLIENT_ID = "clientID";
	public static final String CLIENT_SECRET = "clientSecret";
	public static final String SCOPE = "scope";
	public static final String AUTH_MODE = "authentication";

	public static enum AuthMode {
		BASIC, POST
	}

	@DocumentationReferencePrefix
	public static final String PREFIX="gateway.apitokens.";

	@DocumentationReferenceMeta
	public static final Map<String, PropertyMD> DEFAULTS = new HashMap<>();
	static 
	{	
		DEFAULTS.put(ENABLE, new PropertyMD().setBoolean().setDefault("false").
				setDescription("Selects whether the API token generation facility should be enabled."));
		DEFAULTS.put(ISSUER, new PropertyMD().setMandatory().
				setDescription("Main OIDC endpoint."));
		DEFAULTS.put(TOKEN_ENDPOINT, new PropertyMD().
				setDescription("OIDC token endpoint. If not set, it will be discovered on startup."));
		DEFAULTS.put(AUTHZ_ENDPOINT, new PropertyMD().
				setDescription("OIDC authorization endpoint. If not set, it will be discovered on startup."));
		DEFAULTS.put(SCOPE, new PropertyMD("profile").
				setDescription("OIDC scope(s) to request (space-separated list)."));
		DEFAULTS.put(CLIENT_ID, new PropertyMD().setMandatory().
				setDescription("OAuth client ID."));
		DEFAULTS.put(CLIENT_SECRET, new PropertyMD().setMandatory().
				setDescription("OAuth client secret."));
		DEFAULTS.put(AUTH_MODE, new PropertyMD().setEnum(AuthMode.BASIC).setDefault("BASIC")
				.setDescription("How to authenticate (i.e. send client id/secret) to the OIDC server (BASIC or POST)."));
	}

	public TokenGeneratorProperties(Properties properties) throws ConfigurationException
	{
		super(PREFIX, properties, DEFAULTS, log);
	}

	public boolean isEnabled()
	{
		return getBooleanValue(ENABLE);
	}

	public String getJettyAuthMode()
	{
		return AuthMode.BASIC.equals(getEnumValue(AUTH_MODE, AuthMode.class)) ?
				"client_secret_basic" : "client_secret_post";
	}

	public String getClientID() {
		return getValue(CLIENT_ID);
	}

	public String getClientSecret() {
		return getValue(CLIENT_SECRET);
	}

	public String getIssuer() {
		return getValue(ISSUER);
	}

	public String getTokenEndpoint() {
		return getValue(TOKEN_ENDPOINT);
	}

	public String getAuthzEndpoint() {
		return getValue(AUTHZ_ENDPOINT);
	}

	public String[] getScope() {
		return getValue(SCOPE).split(" +");
	}

}