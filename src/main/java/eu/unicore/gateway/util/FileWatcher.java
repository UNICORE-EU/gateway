package eu.unicore.gateway.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * helper to watch a file and invoke a specific action if modified.
 * 
 * @author schuller
 */
public class FileWatcher implements Runnable{

	private final File target;

	private final Runnable action;

	private long lastAccessed = System.currentTimeMillis();

	public FileWatcher(File target, Runnable action) throws FileNotFoundException{
		if(!target.exists() || !target.canRead()){
			throw new FileNotFoundException("File "+target.getAbsolutePath()+
					" does not exist or is not readable.");
		}
		this.action = action;
		this.target = target;
	}

	/**
	 * check if the target file has been touched and invoke the action if it has
	 */
	@Override
	public void run(){
		if(target.exists() && target.lastModified()>=lastAccessed){
			lastAccessed=System.currentTimeMillis();
			action.run();
		}
	}

	public void schedule(int time, TimeUnit unit) {
		getExecutor().scheduleWithFixedDelay(this, time, time, unit);
	}

	private ScheduledExecutorService executor;
	private synchronized ScheduledExecutorService getExecutor() {
		if(executor==null) {
			executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					Thread t = new Thread(r);
					t.setName("GWFileWatcherThread");
					return t;
				}
			});
		}
		return executor;
	}
}
