import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

import edu.umass.cs.msocket.MServerSocket;
import edu.umass.cs.msocket.MSocket;

import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime; 

public class SimpleServer 
{
	private static final int    LOCAL_PORT = 5454;
	private static final String LOCALHOST  = "0.0.0.0";
	
	private static MServerSocket mss = null;
	
	public static void main(String[] args) throws UnknownHostException, IOException
	{
		String serverIPOrName = LOCALHOST;
		int serverPort = LOCAL_PORT;
		
		if(args.length == 1)
		{
			serverIPOrName = args[0];
		}
		
		if(args.length == 2)
		{
			serverIPOrName = args[0];
			serverPort = Integer.parseInt(args[1]);
		}
		mss = new MServerSocket(serverPort, 0, InetAddress.getByName(serverIPOrName));
		
		System.out.println("Listening for connections.");
		while(true)
		{
			MSocket msocket = mss.accept();
			RequestHandlingThread requestThread = new RequestHandlingThread(msocket);
			requestThread.start();
		}
	}
	
	
	private static class RequestHandlingThread extends Thread
	{
		private MSocket msocket;
		
		public RequestHandlingThread(MSocket msocket)
		{
			this.msocket = msocket;
		}
		
		public void run()
		{
			int numTimes = 1;
			int count = 0;
			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");  
			// Sleeping for 10 s to wait for other flowpaths to be added.
			try 
			{
				Thread.sleep(2000);
				System.out.println("Starting to send data.");
				OutputStream os = msocket.getOutputStream();
				byte[] b = new byte[1024 * 1024 * 16];
				int totalBytes = b.length; 	
				long start = System.currentTimeMillis();
				while(count < numTimes)
				{
					new Random().nextBytes(b);
					os.write(b);
					count++;
				}
				long elapsed = System.currentTimeMillis() - start;
				os.flush();
				
				LocalDateTime now = LocalDateTime.now();
				System.out.println("["+dtf.format(now)+"] Data sending finished. Closing socket, it takes "+elapsed/1000.0+" seconds to transfer "+totalBytes+" bytes. Throughput is "+totalBytes/elapsed+"kB/s");
				msocket.close();
				System.out.println("Socket closed.");
			} catch (InterruptedException | IOException e) {
				e.printStackTrace();
			}
		}
	}
}
