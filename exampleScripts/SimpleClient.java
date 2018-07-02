import static org.junit.Assert.fail;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;

import edu.umass.cs.msocket.FlowPath;
import edu.umass.cs.msocket.MSocket;
import edu.umass.cs.msocket.mobility.MobilityManagerClient;

public class SimpleClient 
{
	private static final int    LOCAL_PORT = 5454;
	private static final String LOCALHOST  = "127.0.0.1";
	
	public static void main(String[] args)
	{
		String serverIPOrName = null;
		if(args.length == 0)
			serverIPOrName = LOCALHOST;
		else
			serverIPOrName = args[0];
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
		
	    try
	    {
	    	MSocket ms = new MSocket(InetAddress.getByName(serverIPOrName), serverPort);
	    	InputStream is = ms.getInputStream();
	    	
	    	// TODO: Might need to bind it to a specific IP address here.
	    	// FlowPath path1 = ms.addFlowPath(null);
		for(int i=0; i<ms.getActiveFlowPaths().size(); i++)
	    	{
	    		FlowPath currfp = ms.getActiveFlowPaths().get(i);
	    		System.out.println("Flowpath id="+currfp.getFlowPathId()+" local ip="+currfp.getLocalEndpoint().toString());
	    	}
	    	byte[] b = new byte[1024 * 1024];
		    int numRead = 0;
		    int totalRead = 0;
		    long start = -1; // System.currentTimeMillis();
		    do
		    {
		    	numRead = is.read(b);
			if (start < 0)
			    start = System.currentTimeMillis();
			totalRead += numRead;
		    	//System.out.println("Received " + numRead + " bytes from server, total read "+totalRead+" bytes.");
		    } while(numRead != -1);
		    long elapsed = System.currentTimeMillis() - start;
		    System.out.println("Closing socket, it takes "+elapsed/1000.0+" seconds to transfer "+totalRead+" bytes. Throughput is "+totalRead/elapsed+"kB/s");
		    ms.close();
		    System.out.println("Socket closed");
			MobilityManagerClient.shutdownMobilityManager();
	    }
	    catch (Exception e)
	    {
	      e.printStackTrace();
	    }
	}
}
