package eu.unicore.gateway.util;

import java.io.IOException;
import java.net.URL;
import java.util.EnumSet;
import java.util.Objects;

import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.AutoLock;

import eu.unicore.gateway.Gateway;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * if acme is enabled (and the server is running HTTPS), this filter ensures
 * that only acme requests are served over the plain http connection
 *
 * @author schuller
 */
@ManagedObject("Acme Filter")
public class AcmeFilter implements Filter
{
	private static final Logger logger = LogUtil.getLogger(LogUtil.GATEWAY, AcmeFilter.class);

	private static final AutoLock LOCK = new AutoLock();

	private static FilterHolder getFilter(ServletContext servletContext)
	{
		ContextHandler contextHandler = Objects.requireNonNull(ContextHandler.getContextHandler(servletContext));
		ServletHandler servletHandler = contextHandler.getChildHandlerByClass(ServletHandler.class);
		return servletHandler.getFilter(AcmeFilter.class.getName());
	}

	/**
	 * Ensure filter is available on the provided ServletContext
	 */
	public static void ensureFilter(ServletContext servletContext, Gateway gateway)
	{
		if(!gateway.getProperties().isAcmeEnabled())return;
		if(!gateway.getProperties().getHostname().toLowerCase().startsWith("https"))return;
		// Lock in case two concurrent requests are initializing the filter lazily.
		try (AutoLock l = LOCK.lock())
		{
			FilterHolder existingFilter = AcmeFilter.getFilter(servletContext);
			if (existingFilter != null)
				return;

			ContextHandler contextHandler = Objects.requireNonNull(ContextHandler.getContextHandler(servletContext));
			ServletHandler servletHandler = contextHandler.getChildHandlerByClass(ServletHandler.class);

			final String pathSpec = "/*";
			FilterHolder holder = new FilterHolder(new AcmeFilter());
			holder.setName(AcmeFilter.class.getName());
			holder.setAsyncSupported(true);

			FilterMapping mapping = new FilterMapping();
			mapping.setFilterName(holder.getName());
			mapping.setPathSpec(pathSpec);
			mapping.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));

			servletHandler.prependFilter(holder);
			servletHandler.prependFilterMapping(mapping);
			contextHandler.addEventListener(new LifeCycle.Listener()
			{
				@Override
				public void lifeCycleStopping(LifeCycle event)
				{
					servletHandler.removeFilterHolder(holder);
					servletHandler.removeFilterMapping(mapping);
					contextHandler.removeEventListener(this);
				}

			});
			logger.debug("Added {} mapped to {} in {}", holder, pathSpec, servletContext);
		}
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
	{		
		HttpServletRequest req = (HttpServletRequest)request;
		HttpServletResponse res = (HttpServletResponse)response;
		URL u = new URL(req.getRequestURL().toString());
		if(u.getProtocol().toLowerCase().equals("https")) {
			// handle normally
			chain.doFilter(request, response);
			return;
		}
		// otherwise, make sure it is an ACME request
		if(!u.getPath().startsWith("/.well-known/acme-challenge/")){
			res.sendError(404);
			Request.getBaseRequest(request).setHandled(true);
		}
	}

}
