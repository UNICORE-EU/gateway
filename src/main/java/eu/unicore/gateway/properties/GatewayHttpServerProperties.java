/**
 * Copyright (c) 2005, Forschungszentrum Juelich
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met: 
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *  * Neither the name of the Forschungszentrum Juelich nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.unicore.gateway.properties;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import eu.unicore.util.configuration.ConfigurationException;
import eu.unicore.util.configuration.DocumentationReferenceMeta;
import eu.unicore.util.configuration.DocumentationReferencePrefix;
import eu.unicore.util.configuration.FilePropertiesHelper;
import eu.unicore.util.configuration.PropertyMD;
import eu.unicore.util.jetty.HttpServerProperties;


public class GatewayHttpServerProperties extends HttpServerProperties
{
	public static final File FILE_JETTY_PROPERTIES = GatewayProperties.FILE_GATEWAY_PROPERTIES;

	/**
	 * deprecated NIO property
	 */
	@Deprecated
	public static final String USE_NIO = "useNIO";

	@DocumentationReferencePrefix
	public static final String PREFIX = GatewayProperties.PREFIX + HttpServerProperties.DEFAULT_PREFIX;
	
	@DocumentationReferenceMeta
	protected final static Map<String, PropertyMD> defaults = new HashMap<>();
	static 
	{
		defaults.put(USE_NIO, new PropertyMD("true").setDescription(
				"DEPRECATED, no effect"));
		defaults.putAll(HttpServerProperties.defaults);
	}
	
	public GatewayHttpServerProperties(String name) throws ConfigurationException, IOException
	{
		this(new File(name));
	}

	public GatewayHttpServerProperties(File f) throws ConfigurationException, IOException
	{
		super(FilePropertiesHelper.load(f), PREFIX, defaults);
		
	}
}
