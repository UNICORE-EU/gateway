package eu.unicore.gateway;

import java.net.ConnectException;
import java.net.InetAddress;
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
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;

import eu.emi.security.authn.x509.impl.SocketFactoryCreator2;
import eu.unicore.gateway.util.LogUtil;
import eu.unicore.gateway.util.XURI;
import eu.unicore.security.canl.AuthnAndTrustProperties;
import eu.unicore.util.Log;
import eu.unicore.util.httpclient.HostnameMismatchCallbackImpl;
import eu.unicore.util.httpclient.ServerHostnameCheckingMode;

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
	private final URI virtualURI;
	private final int index;
	private final boolean isSecure;
	private final URI realURI;
	private final InetAddress inetaddress;
	private final AuthnAndTrustProperties securityCfg;

	private final AtomicInteger numberOfRequests = new AtomicInteger(0);
	private String errorMessage="OK";
	private volatile boolean isUp = false;
	private volatile long lastPing = 0;
	private long pingDelay = 30*1000;
	private long pingTimeout = 10*1000;

	public VSite(URI gatewayURI, String name, String uri, AuthnAndTrustProperties securityCfg) throws UnknownHostException, URISyntaxException{
		if(uri==null)throw new IllegalArgumentException("URI can't be null.");
		if(name==null || name.trim().length()==0)throw new IllegalArgumentException("VSite needs a name.");
		this.realURI = new URI(uri);
		this.inetaddress = InetAddress.getByName(realURI.getHost());
		this.name = name;
		this.virtualURI = new URI(gatewayURI+"/"+name);
		index = new XURI(gatewayURI).countPathElements();
		isSecure = "HTTPS".equalsIgnoreCase(realURI.getScheme());
		this.securityCfg = securityCfg;
		log.info("New virtual site: <{}> at <{}>", virtualURI, uri);
	}

	@Override
	public String getName()
	{
		return name;
	}

	public URI getVirtualURI()
	{
		return virtualURI;
	}

	public URI getRealURI()
	{
		return realURI;
	}

	public InetAddress getInetAddress() 
	{
		return inetaddress;
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

	private SSLSocketFactory socketFactory = null;
	
	private synchronized SSLSocketFactory getSocketFactory() {
		if(socketFactory==null) {
			socketFactory = new SocketFactoryCreator2(
					securityCfg.getCredential(), securityCfg.getValidator(),
					new HostnameMismatchCallbackImpl(ServerHostnameCheckingMode.NONE))
					.getSocketFactory();
		}
		return socketFactory;
	}

	public void reloadConfig() {
		socketFactory=null;
	}

	// for testing
	public void disablePingDelay() {
		pingDelay = -1;
	}

	@Override
	public boolean ping()
	{
		if(lastPing+pingDelay>System.currentTimeMillis()) {
			return isUp;
		}
		Future<Boolean> res = pingService.submit( ()-> {
				Socket s = null;
				try{
					if(isSecure){
						s = getSocketFactory().createSocket(getRealURI().getHost(), getRealURI().getPort());
						((SSLSocket)s).getSession();
					}else{
						s = new Socket(getInetAddress(), getRealURI().getPort());
					}
					errorMessage="OK";
					if(!isUp){
						log.info("VSite '{}' @ {} is up.", name, getRealURI());
						isUp = true;
					}
					return Boolean.TRUE;
				}catch(ConnectException ce){
					if(isUp){
						log.info("VSite '{}' @ {} is down.", name, getRealURI());
						isUp = false;
					}
					errorMessage="Site is down: connection refused.";
				}catch(Exception e){
					if(isUp) {
						String msg = Log.getDetailMessage(e);
						log.info("VSite '{}' @ {} is unavailable: {}", name, getRealURI(),msg);
						errorMessage="Site unavailable: " + msg;
						isUp = false;
					}
				}
				finally{
					lastPing = System.currentTimeMillis();
					IOUtils.closeQuietly(s);
				}
				return Boolean.FALSE;
			}
		);
		try{
			return res.get(pingTimeout, TimeUnit.MILLISECONDS);
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
		return "VSite ::: name=" + getName() + ", virtualuri=" + getVirtualURI() + ", " + "realuri="+getRealURI().toString()+", inetaddress="+getInetAddress();
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
