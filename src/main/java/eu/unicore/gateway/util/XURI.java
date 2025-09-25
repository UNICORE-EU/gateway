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
	LinkedList<String> pl = new LinkedList<>();
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
