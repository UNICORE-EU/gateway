package eu.unicore.gateway.forwarding;

import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.HttpResponseInformationCallback;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;

/**
 * intercepts 101 response from server and makes the network socket available via get()
 *
 * @author schuller
 */
public class RemoteSocketHolder extends BasicFuture<Socket> implements HttpResponseInformationCallback {

	private AtomicBoolean closed = new AtomicBoolean(false);

	public RemoteSocketHolder() {
		super(null);
	}

	@Override
	public void execute(HttpResponse response, HttpConnection connection, HttpContext context) throws HttpException {
		if(101!=response.getCode()){
			return;
		}
		Header upgradeHdr = response.getHeader("Upgrade");
		if(upgradeHdr==null || !ForwardingSetup.REQ_UPGRADE_HEADER_VALUE.equals(upgradeHdr.getValue())) {
			// not a 101 that we can understand
			return;
		}
		// OK, so steal the socket and make it available via this class's Future.get() method
		completed(((ManagedHttpClientConnection)connection).getSocket());
		while(!closed.get()) {
			try{
				Thread.sleep(1000);
			}catch(InterruptedException ie) {}
		}
		connection.close(CloseMode.IMMEDIATE);
	}

	public void close() {
		closed.set(true);
		try{
			IOUtils.closeQuietly(get(100, TimeUnit.MILLISECONDS));
		}catch(Exception ie) {};
	}

}
