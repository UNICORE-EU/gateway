package eu.unicore.gateway.cluster;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.Logger;

import eu.unicore.gateway.Site;
import eu.unicore.gateway.VSite;
import eu.unicore.gateway.util.LogUtil;
import eu.unicore.gateway.util.XURI;
import eu.unicore.security.canl.AuthnAndTrustProperties;

/**
 * A site consisting of multiple subsites, which are used based on a configurable strategy
 * 
 * @author schuller
 */
public class MultiSite implements Site {

	private static final Logger log = LogUtil.getLogger(LogUtil.GATEWAY,MultiSite.class);

	private final String name;
	private final URI gatewayURI;
	private final URI virtualURI;
	private final int index;
	private final AuthnAndTrustProperties securityCfg;
	private int numberOfRequests=0;
	private final Map<String,String> params;
	private final List<VSite>configuredSites;
	private SelectionStrategy selectionStrategy;

	public MultiSite(URI gatewayURI, String name, String desc, AuthnAndTrustProperties securityCfg) 
			throws UnknownHostException, URISyntaxException, IOException{
		if(name==null || name.trim().length()==0)throw new IllegalArgumentException("Site needs a name.");
		this.name = name;
		this.gatewayURI=gatewayURI;
		this.virtualURI = new URI(gatewayURI+"/"+name);
		index=new XURI(gatewayURI).countPathElements();
		this.securityCfg = securityCfg;
		log.info("new multi-site: {}", virtualURI);
		params = new HashMap<>();
		configuredSites = new CopyOnWriteArrayList<>();
		parseDescription(desc);
		configureSites();
		configureSelectionStrategy();
	}

	public boolean accept(String uri)
	{
		try
		{
			XURI xuri = new XURI(new URI(uri));
			String sitename = xuri.getPathElement(index);
			boolean decision = (sitename != null && sitename.equalsIgnoreCase(getName()));
			if(decision)numberOfRequests++;
			return decision;
		}
		catch (URISyntaxException e)
		{
			LogUtil.logException("cannot parse the uri, " + uri + ". this must happen before attempting to map to a registered vsite",e);
			return false;
		}
	}

	public String getName() {
		return name;
	}

	public int getNumberOfRequests() {
		return numberOfRequests;
	}

	public String getStatusMessage() {
		int OK=0;
		for(VSite v: configuredSites){
			if(v.ping())OK++;
		}
		if(OK>0){
			return "OK ("+OK+"/"+configuredSites.size()+" nodes online)";
		}
		else {
			return "Site is down: 0/"+configuredSites.size()+" nodes online";
		}
	}

	public boolean ping() {
		boolean OK=false;
		for(VSite v: configuredSites){
			if(v.ping()){
				OK=true;
				break;
			}
		}
		return OK;
	}

	public VSite select(String clientID) {
		return selectionStrategy.select(clientID);
	}

	/**
	 * the returned list is thread-safe
	 */
	public List<VSite> getConfiguredSites() {
		return configuredSites;
	}

	public SelectionStrategy getSelectionStrategy() {
		return selectionStrategy;
	}

	public void setSelectionStrategy(SelectionStrategy selectionStrategy) {
		this.selectionStrategy = selectionStrategy;
	}


	public void registerVsite(URI address)throws URISyntaxException,UnknownHostException{
		//check if we already have one with this address
		for(VSite v: configuredSites){
			if(v.getRealURI().equals(address)){
				//TODO need to fire some event on the selection strategy, like
				//maybe "received site heartbeat event"?
				return;
			}
		}
		VSite v=new VSite(gatewayURI,name,address.toString(),securityCfg);
		registerVsite(v);
	}

	public void registerVsite(VSite vsite){
		configuredSites.add(vsite);
	}

	protected void parseDescription(String description)throws IOException{
		if(description==null)return;
		String desc=description.replace("multisite:", "");
		String[] elements=desc.split("\\s*;\\s*");
		for(String e: elements){
			String[]param=e.trim().split("\\s*=\\s*");
			if(param.length!=2){
				throw new IllegalArgumentException("Invalid parameter specification: "+e+". Expected format: key=value");
			}
			String key=param[0].toLowerCase();
			String value=param[1].trim();
			params.put(key, value);
		}

		//see if we have a config file to parse
		String configFile=params.get("config");
		if(configFile!=null){
			Properties fileProperties=new Properties();
			FileInputStream istream=new FileInputStream(configFile);
			try{
				fileProperties.load(istream);
			}
			finally{
				istream.close();
			}
			for(Object keyObj: fileProperties.keySet()){
				String key=String.valueOf(keyObj);
				params.put(String.valueOf(key), fileProperties.getProperty(key));
			}
		}
	}

	protected void configureSites()throws URISyntaxException, UnknownHostException{
		String siteDesc=params.get("vsites");
		if(siteDesc==null){
			log.info("No VSites configured for site <{}>", getName());
			return;
		}
		String[] vsites=siteDesc.split("\\s+");
		for(String vsite: vsites){
			VSite v=new VSite(gatewayURI,name,vsite,securityCfg);
			configuredSites.add(v);
			log.info("Configured vsite {} for <{}>", vsite, getName());
		}
	}

	protected void configureSelectionStrategy(){
		String strategyDesc=params.get("strategy");
		if(strategyDesc!=null){
			//check if it is one of the known ones
			if(SelectionStrategy.PRIMARY_WITH_FALLBACK_STRATEGY.equals(strategyDesc)){
				selectionStrategy=new PrimaryWithFallBack();
			}
			else if(SelectionStrategy.ROUND_ROBIN_STRATEGY.equals(strategyDesc)){
				selectionStrategy=new RoundRobin();
			}
			else{
				//else interpret as class name
				try{
					Class<?>clazz=Class.forName(strategyDesc);
					selectionStrategy = (SelectionStrategy)clazz.getConstructor().newInstance();
					log.info("Configured selection strategy <{}>", strategyDesc);
				}catch(Exception ex){
					throw new IllegalArgumentException(ex);
				}
			}
		}
		else{
			selectionStrategy=new PrimaryWithFallBack();
			log.info("Using default selection strategy <{}>", PrimaryWithFallBack.class.getName());
		}
		selectionStrategy.init(this,getParams());
	}

	Map<String, String> getParams() {
		return params;
	}
	
	@Override
	public void reloadConfig() {
		for(Site s: configuredSites) {
			s.reloadConfig();
		}
	}

}
