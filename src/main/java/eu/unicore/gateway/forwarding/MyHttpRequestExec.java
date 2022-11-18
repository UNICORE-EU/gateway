package eu.unicore.gateway.forwarding;

import java.io.IOException;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpResponseInformationCallback;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Hijacks the HTTP protocol execution to inject a handler in case of a 101 response
 *
 * @author schuller
 */
public class MyHttpRequestExec extends HttpRequestExecutor {

	private RemoteSocketHolder forwarder = new RemoteSocketHolder();
	
	public MyHttpRequestExec(RemoteSocketHolder forwarder) {
		super();
		this.forwarder = forwarder;
	}
	
	public void setLocalForwarder(final RemoteSocketHolder receiver) {
		this.forwarder = receiver;
	}

	@Override
	public ClassicHttpResponse execute(ClassicHttpRequest request, HttpClientConnection conn,
			HttpResponseInformationCallback informationCallback, HttpContext context)
			throws IOException, HttpException {
		return super.execute(request, conn, forwarder, context);
	}

	@Override
	public ClassicHttpResponse execute(ClassicHttpRequest request, HttpClientConnection conn, HttpContext context)
			throws IOException, HttpException {
		return super.execute(request, conn, forwarder, context);
	}
	
	
}
