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
		
	    try
	    {
	    	MSocket ms = new MSocket(InetAddress.getByName(serverIPOrName), serverPort);
	    	InputStream is = ms.getInputStream();
	    	
	    	// TODO: Might need to bind it to a specific IP address here.
	    	FlowPath path1 = ms.addFlowPath(null);
	    	byte[] b = new byte[1024 * 1024];
		    int numRead = 0;
		    do
		    {
		    	numRead = is.read(b);
		    	System.out.println("Recvd " + numRead + " bytes from server");
		    } while(numRead != -1);
		    System.out.println("Closing socket.");
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
