import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Based on example from: https://dzone.com/articles/java-8-permgen-metaspace
 * 
 * @author Severin Gehwolf
 *
 */
public class MetaspaceLeak {
	
	public static final int DEFAULT_NUM_ITER = 50_000;
	private static HashMap<String, Foo> classData = new HashMap<>();
	private static final String PROC_STATUS = "/proc/self/status";
	private static final AtomicBoolean finished = new AtomicBoolean(false);
	
	private void doLeakTest(int numIter) {
		for (int i=0; i < numIter; i++) {
			String strUrl = "file://" + i + ".jar";
			URL url;
			try {
				url = new URL(strUrl);
			} catch (MalformedURLException e) {
				System.err.println("Error: Malformed URL" + e);
				return;
			}
			URLClassLoader classLoader = new URLClassLoader(new URL[] { url } );
			Foo foo = (Foo) Proxy.newProxyInstance(classLoader,
					new Class<?>[] { Foo.class },
					new FooInvocationHandler(new FooImpl()));
			classData.put(strUrl, foo); // leak the class
		}
	}
	
	private void startRssReporter() {
		Thread rssReaderThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				while (!finished.get()) {
					grepVmRSS();
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						// ignore
					}
				}
				System.out.println("Finished monitoring RSS.");
			}
			
			private void grepVmRSS() {
				try(FileInputStream fin = new FileInputStream(new File(PROC_STATUS));
					    Scanner scanner = new Scanner(fin)) {
						String line;
						while (scanner.hasNextLine()) {
							line = scanner.nextLine();
							if (line.matches("VmRSS:.*")) {
								System.out.println(line);
							}
						}
				} catch (IOException e) {
					/// ignore
				}
			}
		});
		rssReaderThread.start();
	}
	
	public static void main(String[] args) {
		reportMemoryUsage();
		System.out.println("Running metaspace leak test.");
		int numIter = getNumIter(args);
		MetaspaceLeak ml = new MetaspaceLeak();
		try {
			ml.startRssReporter();
			ml.doLeakTest(numIter);
		} finally {
			finished.set(true);
			classData = null;
			reportMemoryUsage();
		}
		System.out.println("Metaspace leak test worked with " + numIter + " classes.");
	}
	
	private static void reportMemoryUsage() {
		System.out.println("----------------------------------------------------");
		MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
		MemoryUsage usage = memory.getNonHeapMemoryUsage();
		reportUsage("Non-heap memory", usage);
		usage = memory.getHeapMemoryUsage();
		reportUsage("Heap-memory", usage);
		System.out.println("----------------------------------------------------");
		System.out.flush();
	}
	
	private static void reportUsage(String typeMsg, MemoryUsage usage) {
		System.out.format("%s. Used: %dKB, committed: %dKB\n",
				typeMsg,
				usage.getUsed()/1024,
				usage.getCommitted()/1024);
		
	}
	
	private static int getNumIter(String[] args) {
		int numIter = DEFAULT_NUM_ITER;
		if (args.length == 1) {
			try {
				numIter = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				System.err.println("Error: Failed to parse " + args[0] + "as Integer. Using default of " + DEFAULT_NUM_ITER);
			}
		}
		return numIter;
	}

	public static class FooInvocationHandler implements InvocationHandler {
		
		private final Object fooImpl;
		 
		public FooInvocationHandler(Object impl) {
		   this.fooImpl = impl;
		}
		
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			return method.invoke(fooImpl, args);
		}
	}
	
	public static interface Foo {
		public void doIt();
	}
	
	public static class FooImpl implements Foo {
		public void doIt() { /* nothing */ }
	}
}
