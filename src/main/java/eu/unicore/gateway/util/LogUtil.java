package eu.unicore.gateway.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

import eu.unicore.util.Log;

public class LogUtil extends Log {

	/**
	 * logger prefix for general Gateway code
	 */
	public static final String GATEWAY="unicore.gateway";

	/**
	 * MDC context key for the client's IP
	 */
	public static final String MDC_IP="clientIP";

	/**
	 * MDC context key for the client's DN
	 */
	public static final String MDC_DN="clientName";

	// very conservative default logging
	public static void configureDefaultLogging() {
		ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
		builder.setStatusLevel(Level.ERROR);
		builder.setConfigurationName("BuilderTest");
		builder.add(builder.newFilter("ThresholdFilter", Filter.Result.ACCEPT, Filter.Result.NEUTRAL)
				.addAttribute("level", Level.WARN));
		AppenderComponentBuilder appenderBuilder = builder.newAppender("Stdout", "CONSOLE").addAttribute("target",
				ConsoleAppender.Target.SYSTEM_OUT);
		appenderBuilder.add(builder.newLayout("PatternLayout")
				.addAttribute("pattern", "%d [%t] %-5level: %msg%n%throwable"));
		appenderBuilder.add(builder.newFilter("MarkerFilter", Filter.Result.DENY, Filter.Result.NEUTRAL)
				.addAttribute("marker", "FLOW"));
		builder.add(appenderBuilder);
		builder.add(builder.newLogger("org.apache.logging.log4j", Level.DEBUG)
				.add(builder.newAppenderRef("Stdout")).addAttribute("additivity", false));
		builder.add(builder.newRootLogger(Level.ERROR).add(builder.newAppenderRef("Stdout")));
		Configurator.initialize(builder.build());	
	}
}
