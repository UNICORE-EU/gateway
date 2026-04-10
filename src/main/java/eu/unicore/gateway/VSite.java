package eu.unicore.gateway;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Logger;

import eu.unicore.gateway.util.LogUtil;
import eu.unicore.gateway.util.XURI;
import eu.unicore.util.Log;

public class VSite implements Site {

	private static final Logger log = Log.getLogger(LogUtil.GATEWAY,VSite.class);

	private static final ExecutorService pingService = new ThreadPoolExecutor(3, 3,
			60L, TimeUnit.SECONDS,
			new ArrayBlockingQueue<Runnable>(100),
			new ThreadFactory() {
				int c = 1;
				@Override
				public Thread newThread(Runnable r) {
					Thread t = new Thread(r);
					t.setName("site-status-check-"+c);
					c++;
					return t;
				}
			});

	private final String name;
	private final int index;
	private final URI realURI;
	private final InetSocketAddress inetAddress;

	private final AtomicInteger numberOfRequests = new AtomicInteger(0);
	private String errorMessage="OK";
	private volatile boolean isUp = false;
	private volatile long lastPing = 0;
	private long pingDelay = 30*1000;
	private int pingTimeout = 10*1000;

	public VSite(URI gatewayURI, String name, String uri) throws UnknownHostException, URISyntaxException{
		if(uri==null)throw new IllegalArgumentException("URI can't be null.");
		if(name==null || name.trim().length()==0)throw new IllegalArgumentException("VSite needs a name.");
		this.realURI = new URI(uri);
		this.inetAddress = new InetSocketAddress(realURI.getHost(), realURI.getPort());
		this.name = name;
		this.index = new XURI(gatewayURI).countPathElements();
		log.info("New virtual site: <{}> at <{}>", name, uri);
	}

	@Override
	public String getName()
	{
		return name;
	}

	public URI getRealURI()
	{
		return realURI;
	}

	@Override
	public int getNumberOfRequests()
	{
		return numberOfRequests.get();
	}  

	@Override
	public String getStatusMessage(){
		return errorMessage;
	}

	@Override
	public void reloadConfig() {
		// N/A
	}

	// for testing
	public void disablePingDelay() {
		pingDelay = -1;
	}

	private final AtomicBoolean pingInProgress = new AtomicBoolean(false);

	@Override
	public boolean ping()
	{
		if(pingInProgress.get() || lastPing+pingDelay>System.currentTimeMillis()) {
			return isUp;
		}
		pingInProgress.set(true);
		Future<Boolean> res = pingService.submit( ()-> {
				try(Socket s = new Socket()) {
					s.connect(inetAddress, pingTimeout);
					errorMessage="OK";
					if(!isUp){
						log.info("VSite '{}' @ {} is up.", name, realURI);
						isUp = true;
					}
					return Boolean.TRUE;
				}catch(ConnectException ce){
					if(isUp){
						log.info("VSite '{}' @ {} is down.", name, realURI);
						isUp = false;
					}
					errorMessage="Site is down: connection refused.";
				}catch(Exception e){
					if(isUp) {
						String msg = Log.getDetailMessage(e);
						log.info("VSite '{}' @ {} is unavailable: {}", name, realURI, msg);
						errorMessage="Site unavailable: " + msg;
						isUp = false;
					}
				}
				finally{
					lastPing = System.currentTimeMillis();
					pingInProgress.set(false);
				}
				return Boolean.FALSE;
			}
		);
		try{
			// use a timeout here, too, just to be on the super-safe side
			return res.get(3 * pingTimeout, TimeUnit.MILLISECONDS);
		}catch(Exception tex){
			errorMessage = "Timeout";
			isUp = false;
		}
		return false;
	}

	public String resolve(String uri) throws URISyntaxException
	{   
		XURI xuri = new XURI(new URI(uri));
		String endpart = xuri.getResource(index+1);
		if (endpart.length() == 0)
			return getRealURI().toString();
		else 
			return getRealURI() + "/" + endpart;
	}

	@Override
	public boolean accept(String uri) throws URISyntaxException
	{
		XURI xuri = new XURI(new URI(uri));
		String sitename = xuri.getPathElement(index);
		boolean decision = (sitename != null && sitename.equalsIgnoreCase(getName()));
		if(decision)numberOfRequests.incrementAndGet();
		return decision;
	}

	@Override
	public final VSite select(String clientIP){
		return this;
	}

	@Override
	public String toString()
	{
		return "VSite ::: name=" + getName() + ", realuri=" + getRealURI().toString() + ", inetaddress=" + inetAddress;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((realURI == null) ? 0 : realURI.hashCode());
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
		VSite other = (VSite) obj;
		if (!name.equals(other.name))
			return false;
		if (!realURI.equals(other.realURI))
			return false;
		return true;
	}

}
