package eu.unicore.gateway.util;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.annotation.ManagedObject;

/**
 * if acme is enabled and the server is running HTTPS, this handler is added
 * to the server to ensure that only acme requests are served over the
 * plain http connection
 *
 * @author schuller
 */
@ManagedObject
public class AcmeHandler extends Handler.Wrapper
{

	@Override
	public boolean handle(Request request, Response response, Callback callback) throws Exception
	{	
		if("https".equals(request.getHttpURI().getScheme().toLowerCase()) ||
			Request.getPathInContext(request).startsWith("/.well-known/acme-challenge/"))
		{
			return super.handle(request, response, callback);
		}
		return false;
	}

}
