package eu.unicore.gateway.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Used only if performance testing is done.
 * @author golbi
 */
public class StopWatch
{
	List<Long> periods = new ArrayList<Long>();
	long last;
	long start;
	
	public void start()
	{
		last = System.nanoTime();
		start = last;
	}
	
	public void snapshot()
	{
		long now = System.nanoTime();
		periods.add((now - last)/1000);
		last = now;
	}
	
	public List<Long> getPeriods()
	{
		return periods;
	}
	
	public long getTotalTime()
	{
		return last - start;
	}
	
	public String toString()
	{
		StringBuilder sb = new StringBuilder(256);
		for (Long p: periods)
		{
			sb.append(p).append("\t");
		}
		return sb.toString();
	}
}
