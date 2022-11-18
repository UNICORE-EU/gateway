package eu.unicore.gateway.forwarding;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.util.Callback;

import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.util.LogUtil;

/**
 * Handles the client-to-vsite part of the forwarding for all running forwarding connections
 *
 * @author schuller
 */
public class Forwarder implements Runnable {

	private static final Logger log = LogUtil.getLogger(LogUtil.GATEWAY, Forwarder.class);

	private final Selector selector;

	private final List<SelectionKey> keys = new ArrayList<>();

	private final ByteBuffer buffer = ByteBuffer.allocate(4096);
	
	private static Forwarder _instance;
	
	public static synchronized Forwarder get(Gateway gateway) throws IOException {
		if(_instance==null) {
			_instance = new Forwarder();
			new Thread(_instance, "Forwarder").start();
		}
		return _instance;
	}

	private Forwarder() throws IOException {
		selector = Selector.open();
	}
	
	public synchronized void attach(final SocketChannel vsiteChannel, final ForwardingConnection toClient) 
	throws IOException {
		assert toClient!=null : "Client connection cannot be null";
		assert vsiteChannel!=null : "Vsite socket channel cannot be null";
		vsiteChannel.configureBlocking(false);
		SelectionKey key  = vsiteChannel.register(selector,
				SelectionKey.OP_READ,
				toClient);
		keys.add(key);
		log.info("New forwarding connection to {} started.", vsiteChannel.getRemoteAddress());
	}
	
	public void run() {
		try{
			log.info("TCP port forwarder starting.");
			while(true) {
				selector.select(50);
				selector.selectedKeys().forEach(key -> dataAvailable(key));
			}
		}catch(Exception ex) {
			log.error(ex);
		}
	}

	public void dataAvailable(SelectionKey key) {
		ForwardingConnection toClient = (ForwardingConnection)key.attachment();
		SocketChannel vsite = (SocketChannel)key.channel();
		try{
			if(key.isReadable()) {
				buffer.clear();
				int n = vsite.read(buffer);
				if(n>0) {
					log.debug("Got {} bytes from vsite.", n);
					buffer.flip();
					toClient.getEndPoint().write(Callback.NOOP, buffer);
				}
			}
		}catch(IOException ioe) {
			log.error(ioe);
		}
	}

}
