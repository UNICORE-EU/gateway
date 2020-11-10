package eu.unicore.gateway.cluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.Logger;

import eu.unicore.gateway.VSite;
import eu.unicore.gateway.util.LogUtil;

/**
 * this strategy selects one of the configured VSites, in a round robin fashion.<br/>
 * 
 * 
 * TODO 
 * 
 * If the "sticky" attribute is set to <code>true</code>, the strategy will try to 
 * route a certain client always to the same VSite 
 * 
 * @author schuller
 */
public class RoundRobin implements SelectionStrategy {

	private static final Logger log = LogUtil.getLogger(LogUtil.GATEWAY,RoundRobin.class);

	private MultiSite parent;

	private long lastChecked=-1;

	//sites available for selection
	List<VSite>sitesUp=new ArrayList<VSite>();
	
	//how often to check site health (millis)
	private int interval=5000;

	//node health status map (key: node uri, value: true=up, false=down)
	final Map<String, Boolean>health=new HashMap<String, Boolean>();
	
	final Random random=new Random();
	
	public void init(MultiSite parent, Map<String, String> params) {
		this.parent=parent;
		String intervalS=params.get(HEALTH_CHECK_INTERVAL);
		if(intervalS!=null){
			interval=Integer.parseInt(intervalS);
			log.debug("Health check interval: "+interval+" millis.");
		}
	}

	public VSite select(String clientID) {
		checkHealth();
		int size=sitesUp.size();
		if(size==0)return null;
		int selected=random.nextInt(size);
		return sitesUp.get(selected);
	}

	protected synchronized void checkHealth(){
		List<VSite>sites=parent.getConfiguredSites();
		if(lastChecked+interval<System.currentTimeMillis()){
			Map<String, Boolean>newHealth=new HashMap<String, Boolean>();
			List<VSite>up=new ArrayList<VSite>();
			lastChecked=System.currentTimeMillis();
			for(VSite v: sites){
				String key=v.getRealURI().toString();
				boolean newState=v.ping();
				Boolean oldStateB=health.get(key);
				boolean oldState=oldStateB==null? false: oldStateB.booleanValue();
				if(newState!=oldState){
					String state=newState?"UP":"DOWN";
					log.info("Vsite "+parent.getName()+" node <"+key+"> is "+state);
				}
				newHealth.put(key, Boolean.valueOf(newState));
				if(newState){
					up.add(v);
				}
			}
			sitesUp=up;
			health.clear();
			health.putAll(newHealth);
			return;
		}
	}

}
