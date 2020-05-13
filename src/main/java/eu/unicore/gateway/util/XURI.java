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


package eu.unicore.gateway.util;

import java.net.URI;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * helper class to handle URIs
 * 
 * @author roger
 */
public class XURI
{

  private final URI uri;

  private final LinkedList<String> pl;

  public XURI(URI uri)
  {
    this.uri = uri.normalize();
    pl = init();
  }
 
  public static XURI create(String uri)
  {
    return new XURI(URI.create(uri));
  }
  
  private LinkedList<String> init()
  {
	LinkedList<String> pl = new LinkedList<String>();
    StringTokenizer tok = new StringTokenizer(getPath(), "/");
    while (tok.hasMoreTokens())
    {
      pl.add(tok.nextToken());
    }
    return pl;
  }
  
  /**
   * gets the requested resource, starting from the given path index
   * (without leading slash)
   * @param index
   */
  public String getResource(int index){
	  StringBuilder sb = new StringBuilder();
	  boolean first = true;
	  if(index<=pl.size()){
		  Iterator<String>iter = getPathElements().listIterator(index);
		  while(iter.hasNext()){
			  if(!first)sb.append("/");
			  sb.append(iter.next());
			  first = false;
		  }
	  }
	  if(uri.getRawQuery()!=null)sb.append("?").append(uri.getRawQuery());
	  if(uri.getRawFragment()!=null)sb.append("#").append(uri.getRawFragment());

	  return sb.toString();
  }
  

  public String getPath()
  {
    return getURI().getRawPath();
  }

  public int countPathElements()
  {
    if (this.pl.size() == 1 && this.pl.getFirst().equals(".."))
    {
      return -1;
    }
    return this.pl.size();
  }

  public List<String> getPathElements()
  {
    return this.pl;
  }

  public String getPathElement(int i)
  {
    if (this.countPathElements() > i)
    {
      return getPathElements().listIterator(i).next();
    }
    else return null;
  }
  
  public URI getURI()
  {
    return this.uri;
  }

}
