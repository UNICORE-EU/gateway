package eu.unicore.gateway.forwarding;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Objects;

import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.FilterMapping;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.AutoLock;

import eu.unicore.gateway.Gateway;
import eu.unicore.gateway.util.LogUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Based on Jetty's WebSocketUpgradeFilter
 *
 * @author schuller
 */
@ManagedObject("Protocol Upgrade Filter")
public class ProtocolUpgradeFilter implements Filter
{
	private static final Logger logger = LogUtil.getLogger(LogUtil.GATEWAY, ProtocolUpgradeFilter.class);

	private static final AutoLock LOCK = new AutoLock();

	private final Gateway gateway;

	public ProtocolUpgradeFilter(final Gateway gateway) {
		this.gateway = gateway;
	}

	private static FilterHolder getFilter(ServletContext servletContext)
	{
		ContextHandler contextHandler = Objects.requireNonNull(ServletContextHandler.getServletContextHandler(servletContext));
        ServletHandler servletHandler = contextHandler.getDescendant(ServletHandler.class);
        return servletHandler.getFilter(ProtocolUpgradeFilter.class.getName());
	}

	/**
	 * Ensure filter is available on the provided ServletContext
	 */
	public static void ensureFilter(ServletContext servletContext, Gateway gateway)
	{
		// Lock in case two concurrent requests are initializing the filter lazily.
		try (AutoLock l = LOCK.lock())
		{
			FilterHolder existingFilter = ProtocolUpgradeFilter.getFilter(servletContext);
			if (existingFilter != null)
				return;

			ContextHandler contextHandler = Objects.requireNonNull(ServletContextHandler.getServletContextHandler(servletContext));
	        ServletHandler servletHandler = contextHandler.getDescendant(ServletHandler.class);

			final String pathSpec = "/*";
			FilterHolder holder = new FilterHolder(new ProtocolUpgradeFilter(gateway));
			holder.setName(ProtocolUpgradeFilter.class.getName());
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

	private ForwardingSetup mapper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
    	HttpServletRequest httpRequest = (HttpServletRequest)request;
    	HttpServletResponse httpResponse = (HttpServletResponse)response;
        // Do preliminary check before proceeding to attempt an upgrade.
        if (ForwardingSetup.validateRequest(httpRequest))
        {
            if (mapper.upgrade(httpRequest, httpResponse))
            {
            	return;
            }
        }
        // If we reach this point, it means we had an incoming request to upgrade
        // but something went wrong
        if (response.isCommitted())
            return;

        // Handle normally
        chain.doFilter(request, response);
    }
	@Override
	public void init(FilterConfig config) throws ServletException
	{
		  mapper = ForwardingSetup.ensureMappings(config.getServletContext(), gateway);
	}
	
	
	
}
