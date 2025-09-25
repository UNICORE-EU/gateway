package eu.unicore.gateway.forwarding;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Objects;

import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletHandler;
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

	/**
	 * Return the default {@link ProtocolUpgradeFilter} if present on the {@link ServletContext}.
	 *
	 * @param servletContext the {@link ServletContext} to use.
	 * @return the configured default {@link ProtocolUpgradeFilter} instance.
	 */
	public static FilterHolder getFilter(ServletContext servletContext)
	{
		ContextHandler contextHandler = Objects.requireNonNull(ContextHandler.getContextHandler(servletContext));
		ServletHandler servletHandler = contextHandler.getChildHandlerByClass(ServletHandler.class);
		return servletHandler.getFilter(ProtocolUpgradeFilter.class.getName());
	}

	/**
	 * Ensure a {@link ProtocolUpgradeFilter} is available on the provided {@link ServletContext},
	 * a new filter will added if one does not already exist.
	 * <p>
	 * The default {@link ProtocolUpgradeFilter} is also available via
	 * the {@link ServletContext} attribute named {@code org.eclipse.jetty.websocket.server.WebSocketUpgradeFilter}
	 * </p>
	 *
	 * @param servletContext the {@link ServletContext} to use.
	 * @return the configured default {@link ProtocolUpgradeFilter} instance.
	 */
	public static FilterHolder ensureFilter(ServletContext servletContext, Gateway gateway)
	{
		// Lock in case two concurrent requests are initializing the filter lazily.
		try (AutoLock l = LOCK.lock())
		{
			FilterHolder existingFilter = ProtocolUpgradeFilter.getFilter(servletContext);
			if (existingFilter != null)
				return existingFilter;

			ContextHandler contextHandler = Objects.requireNonNull(ContextHandler.getContextHandler(servletContext));
			ServletHandler servletHandler = contextHandler.getChildHandlerByClass(ServletHandler.class);

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

			// If we create the filter we must also make sure it is removed if the context is stopped.
			contextHandler.addEventListener(new LifeCycle.Listener()
			{
				@Override
				public void lifeCycleStopping(LifeCycle event)
				{
					servletHandler.removeFilterHolder(holder);
					servletHandler.removeFilterMapping(mapping);
					contextHandler.removeEventListener(this);
				}

				@Override
				public String toString()
				{
					return String.format("%sCleanupListener", ProtocolUpgradeFilter.class.getSimpleName());
				}
			});
			logger.debug("Adding {} mapped to {} in {}", holder, pathSpec, servletContext);
			return holder;
		}
	}

	private ForwardingSetup mapper;

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
	{		
		HttpServletRequest httpreq = (HttpServletRequest)request;
		HttpServletResponse httpresp = (HttpServletResponse)response;

		if (mapper.upgrade(httpreq, httpresp))
		{
			return;
		}
		// Otherwise, handle normally
		chain.doFilter(request, response);
	}

	@Override
	public void init(FilterConfig config) throws ServletException
	{
		  mapper = ForwardingSetup.ensureMappings(config.getServletContext(), gateway);
	}

	@Override
	public void destroy(){}

}
