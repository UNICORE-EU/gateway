package eu.unicore.gateway;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

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

	private static final ExecutorService pingService=new ThreadPoolExecutor(2, 10,
			60L, TimeUnit.SECONDS,
			new SynchronousQueue<Runnable>());

	private final String name;
	private final URI virtualURI;
	private final int index;
	private final boolean isSecure;
	private final URI realURI;
	private final InetAddress inetaddress;
	private final AuthnAndTrustProperties securityCfg;

	//for monitoring
	private final AtomicInteger numberOfRequests=new AtomicInteger(0);
	private String errorMessage="OK";

	private Boolean isUp=null;

	/**
	 * @param name
	 * @param uri 
	 * @throws UnknownHostException 
	 */
	public VSite(URI gatewayURI, String name, String uri, AuthnAndTrustProperties securityCfg) throws UnknownHostException, URISyntaxException{
		if(uri==null)throw new IllegalArgumentException("URI can't be null.");
		this.realURI=new URI(uri);
		this.inetaddress = InetAddress.getByName(realURI.getHost());
		if(name==null || name.trim().length()==0)throw new IllegalArgumentException("VSite needs a name.");
		this.name = name;
		this.virtualURI = new URI(gatewayURI+"/"+name);
		index=new XURI(gatewayURI).countPathElements();
		isSecure="HTTPS".equalsIgnoreCase(realURI.getScheme());
		this.securityCfg = securityCfg;
		log.info("new virtual site: <"+virtualURI.toString()+"> at <"+uri+">" );
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

	/**
	 * return the real internet address of the site
	 */
	public InetAddress getInetAddress() 
	{
		return this.inetaddress;
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
	public boolean ping()
	{
		return ping(5000);
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

	@Override
	public boolean ping(int timeout)
	{
		Future<Boolean> res = pingService.submit(new Callable<Boolean>(){
			public Boolean call(){
				Socket s=null;
				try{
					if(isSecure){
						s = getSocketFactory().createSocket(getRealURI().getHost(), getRealURI().getPort());
						((SSLSocket)s).getSession();
					}else{
						s=new Socket(getInetAddress(), getRealURI().getPort());
					}
					errorMessage="OK";
					if(isUp==null || !isUp){
						log.info("VSite '"+name+"' @ "+getRealURI()+" is up.");
						isUp=true;
					}
					return Boolean.TRUE;
				}catch(ConnectException ce){
					if(isUp==null || isUp){
						log.info("VSite '"+name+"' @ "+getRealURI()+" is down.");
						isUp=false;
					}
					errorMessage="Site is down: connection refused.";
				}catch(Exception e){
					LogUtil.logException("Error pinging VSite '"+name+"' @ "+getRealURI()+"",e,log);
					errorMessage="Site unavailable: " + Log.getDetailMessage(e);
					isUp = false;
				}
				finally{
					if(s!=null)try{
						s.close();
					}catch(IOException ioe){
						LogUtil.logException("Error closing socket", ioe,log);
					}
				}
				return Boolean.FALSE;
			}
		});
		
		try{
			return res.get(timeout, TimeUnit.MILLISECONDS);
		}catch(Exception tex){
			LogUtil.logException("Error waiting for ping result", tex, log);
			errorMessage = "Timeout";
			isUp = false;
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see eu.unicore.gateway.Site#resolve(java.lang.String)
	 */
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
	public boolean accept(String uri)
	{
		try
		{
			XURI xuri = new XURI(new URI(uri));
			String sitename = xuri.getPathElement(index);
			boolean decision = (sitename != null && sitename.equalsIgnoreCase(getName()));
			if(log.isTraceEnabled()){
				log.trace("is " + uri + " a correct URI for this (" + this.toString() + ")Vsite ? " + decision);
			}
			if(decision)numberOfRequests.incrementAndGet();
			return decision;
		}
		catch (URISyntaxException e)
		{
			LogUtil.logException("cannot parse the uri, " + uri + ". this must happen before attempting to map to a registered vsite",e);
			return false;
		}
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
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (realURI == null) {
			if (other.realURI != null)
				return false;
		} else if (!realURI.equals(other.realURI))
			return false;
		return true;
	}



}
