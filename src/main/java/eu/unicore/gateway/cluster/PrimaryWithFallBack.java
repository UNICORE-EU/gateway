package eu.unicore.gateway.cluster;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import eu.unicore.gateway.VSite;
import eu.unicore.gateway.util.LogUtil;

/**
 * This strategy will select the first vsite (i.e. the <em>primary</em> site) registered with a {@link MultiSite}, if
 * it is available. If the  primary site is down, the second vsite will be used.<br/>
 * The vsites are checked for availability not more frequently than a configurable interval,
 * usually 5 seconds.
 * 
 * @author schuller
 */
public class PrimaryWithFallBack implements SelectionStrategy {

	private static final Logger log = LogUtil.getLogger(LogUtil.GATEWAY,PrimaryWithFallBack.class);

	private long lastChecked=-1;
	
	//how often to ping (millis) the primary site
	private int interval=5000;
	
	private VSite primary;
	
	private VSite secondary;
	
	private boolean primaryDown;
	
	private MultiSite parent;
	
	public void init(MultiSite parent, Map<String, String> params) {
		this.parent=parent;
		String intervalS=params.get(HEALTH_CHECK_INTERVAL);
		if(intervalS!=null){
			interval=Integer.parseInt(intervalS);
			log.debug("Health check interval: "+interval+" millis.");
		}
	}

	public VSite select(String clientID) {
		List<VSite> sites=parent.getConfiguredSites();
		if(primary==null)primary=sites.get(0);
		if(secondary==null && sites.size()>1)secondary=sites.get(1);
		checkHealth();
		return primaryDown?secondary:primary;
	}

	protected void checkHealth(){
		if(lastChecked+interval<System.currentTimeMillis()){
			lastChecked=System.currentTimeMillis();
			boolean newState=!primary.ping();
			if(newState!=primaryDown){
				String state=newState?"DOWN":"UP";
				log.info("Primary node of <"+parent.getName()+"> is "+state);
			}
			primaryDown=newState;
			return;
		}
	}
	
}
