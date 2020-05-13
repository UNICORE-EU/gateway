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


package eu.unicore.gateway;

import java.util.Collection;

/**
 * collects information about the gateway and the sites (vsites) available. 
 * 
 * could have 'simple' (i.e. from some kind of properties file) implementations, or it could have 
 * a more dynamic characteristics (discoverable via multi-cast, or WS-Discovery(?)) for another
 * more interesting implementation. 
 * 
 * @author roger
 */
public interface SiteOrganiser
{

	/**
	 * get the current set of sites
	 */
	public Collection<Site> getSites();

	/**
	 * select a matching vsite. The client IP may be ignored, or it may be used
	 * in load balancing configurations to achieve IP affinity
	 * 
	 * @param wsato - destination, usually obtained as value of the WS-Addressing To header
	 * @param clientIP - the IP of the client
	 * @return matching vsite
	 */
	public VSite match(String wsato, String clientIP);

	/**
	 * get a HTML representation of this site organiser
	 */
	public String toHTMLString();

}
