package eu.unicore.gateway.forwarding;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;

import eu.unicore.gateway.util.LogUtil;
import eu.unicore.util.Log;

/**
 * Minimalistic implementation of {@link org.eclipse.jetty.io.Connection}
 * that forwards client data to the backend
 *
 * @author schuller
 */
public class ForwardingConnection extends AbstractConnection implements Connection.UpgradeTo
{
	private static final Logger LOG = LogUtil.getLogger(LogUtil.GATEWAY, ForwardingConnection.class);

	ByteBuffer buffer = ByteBuffer.allocate(4096);
	
	private final SocketChannel vsite;
	
	public ForwardingConnection(EndPoint endPoint, Executor executor, SocketChannel vsite)
	{
		super(endPoint, executor);
		endPoint.setIdleTimeout(-1);
		this.vsite = vsite;
	}

	@Override
	public void onUpgradeTo(ByteBuffer buffer) {
		LOG.debug("**** onUpgrade with {} bytes ", buffer.position());
		// should not have any payload here
		assert buffer.position()==0;
	}

	@Override
	public void onFillable() {
		try {
			buffer.clear();
			buffer.limit(0);
			int n = getEndPoint().fill(buffer);
			if(n>0) {
				LOG.debug("Read {} bytes from client", n);
				vsite.write(buffer);
				fillInterested();
			}
		}catch(IOException ioe) {
			Log.logException("Error handling forwarding to VSite "+vsite, ioe, LOG);
		}
	}

	@Override
	public void onOpen() {
		LOG.debug("onOpen");
		super.onOpen();
		fillInterested();
	}
}
