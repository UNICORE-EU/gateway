package eu.unicore.gateway.forwarding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;

import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http.HttpStatus;

import eu.unicore.gateway.util.LogUtil;
import eu.unicore.util.httpclient.HttpUtils;
import eu.unicore.util.httpclient.IClientConfiguration;

/**
 * a server that can mimic the HTTP Upgrade mechanism for tunneling
 */
public class EchoEndpointServer implements Runnable {

	private static final Logger log = LogUtil.getLogger(LogUtil.GATEWAY,EchoEndpointServer.class);

	private final int port;

	private ServerSocket serverSocket;

	private volatile boolean stopping=false;

	private volatile boolean stopped=false;

	private volatile int statusCode=101;

	private String latestQuery;

	private List<String> latestHeaders = new ArrayList<>();
	
	private final boolean ssl;
	
	/**
	 * creates a FakeServer listening on the given port
	 * @param port
	 * @param ssl
	 * @throws IOException
	 */
	public EchoEndpointServer(int port, boolean ssl, IClientConfiguration secProps)throws IOException{
		this.ssl = ssl;
		serverSocket = ssl? createSSL(port, secProps) : createPlain(port);
		serverSocket.setSoTimeout(10*3600);
		this.port=serverSocket.getLocalPort();
	}

	private ServerSocket createPlain(int port) throws IOException {
		return new ServerSocket(port);
	}
	
	private ServerSocket createSSL(int port, IClientConfiguration secProps) throws IOException {
		ServerSocketFactory ssf = HttpUtils.createSSLContext(secProps).getServerSocketFactory();
		ServerSocket s = ssf.createServerSocket(port);
		SSLServerSocket sslServer = (SSLServerSocket)s;
		sslServer.setNeedClientAuth(secProps.doSSLAuthn());
		sslServer.setEnableSessionCreation(true);
		return sslServer;
	}

	/**
	 * creates a FakeServer listening on a free port
	 * @see getPort()
	 */
	public EchoEndpointServer()throws IOException{
		this(0, false, null);
	}

	public String getURI(){
		
		return "http"+(ssl?"s":"")+"://localhost:"+port;
	}

	private static int n=0;
	public synchronized void start(){
		Thread t=new Thread(this);
		t.setName("FakeVSiteListenerThread"+(n++));
		t.start();
	}

	public void stop(){
		stopping=true;
	}

	public boolean isStopped(){
		return stopped;
	}

	public List<String> getLatestRequestHeaders(){
		return latestHeaders;
	}

	public String getLatestQuery(){
		return latestQuery;
	}

	private void parseHttp(InputStream input) throws UnsupportedEncodingException{
		BufferedReader br=new BufferedReader(new InputStreamReader(input,"UTF-8"));
		try{
			String line = null;
			do {
				line = br.readLine();
			} while (line != null && line.length() == 0);
			String _latestQuery = line;
			List<String> _latestHeaders=new ArrayList<>();
			StringBuilder sb=new StringBuilder(1024);
			if (line != null) {
				line=br.readLine();
				int contentLength=0;
				while(line!= null && line.length()>0){
					_latestHeaders.add(line.trim());
					if(line.startsWith("Content-Length:")){
						try{
							contentLength=Integer.parseInt(line.substring("Content-Length:".length()).trim());
						}
						catch(Exception ex){ /* */}
					}
					line=br.readLine();
				}

				boolean chunked =_latestHeaders.contains("Transfer-Encoding: chunked");

				if((!chunked && contentLength==0) || line==null){
					latestQuery = _latestQuery;
					latestHeaders = _latestHeaders;
					return;
				}

				line=br.readLine();

				if(chunked){
					big_loop: while(line!= null){
						int chunksize = 0;
						try{
							chunksize=Integer.parseInt(line, 16);
						}
						catch(Exception ex){/*bad request, bad!*/}
						if(chunksize == 0) break;
						int counter=0;
						while(true){
							line=br.readLine();
							if(line==null) break big_loop;
							counter+=line.length(); //this only works with one-byte encodings
							sb.append(line);
							if(counter<chunksize) {
								sb.append("\n");
								counter+=2;
							}
							if(counter>=chunksize){
								break;
							}
						}
						line=br.readLine();
						while(line!= null && line.length()==0){
							line=br.readLine();
						}
					}
				}
				else{
					if(contentLength>0) while(line!= null){
						sb.append(line);
						line=br.readLine();
						if(line!=null) sb.append("\n");//not after the last line
						if(sb.length()>=contentLength) break;
					}
				}
			}
			latestHeaders = _latestHeaders;
		}
		catch(Exception e){
			log.warn("Fake server had problem with reading request: "+e.getMessage());
			e.printStackTrace();
		}
	}

	public void run(){
		Socket socket = null;
		while(!stopping){
			try {
			    if(socket==null)socket = serverSocket.accept();

				parseHttp(socket.getInputStream());
				log.info("Handling request.");
				String status="HTTP/1.1 "+statusCode+" "+HttpStatus.getMessage(statusCode)+"\n";
				socket.getOutputStream().write(status.getBytes());
				socket.getOutputStream().write(("Upgrade: "+ForwardingSetup.REQ_UPGRADE_HEADER_VALUE+"\n").getBytes());
				socket.getOutputStream().write("\r\n".getBytes());
				
				socket.getOutputStream().flush();
				byte[] buffer = new byte[1024];
				while(true) {
					log.info("Reading from client...");
					int n = socket.getInputStream().read(buffer);
					String line = new String(buffer).trim();
					log.info("---> got: '{}' from client.", line);
					if(n>0) {
						socket.getOutputStream().write((line+"\n").getBytes());
						socket.getOutputStream().flush();
					}
					if(n<0)break;
				}
			}catch(Exception ex){ 
				ex.printStackTrace();
				break;
			}
		}
		log.info("Stopped.");
		stopped=true;
		try{ socket.close(); }catch(Exception ex){}
		try{ serverSocket.close(); }catch(Exception ex){}
		serverSocket = null;
	}

	public int getPort(){
		return port;
	}

	public void setStatusCode(int statusCode){
		this.statusCode=statusCode;
	}
}
