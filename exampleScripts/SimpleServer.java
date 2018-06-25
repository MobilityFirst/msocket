import edu.umass.cs.msocket.MServerSocket;

public class SimpleServer 
{
	private static final int    LOCAL_PORT = 5454;
	private static final String LOCALHOST  = "127.0.0.1";
	  
	private MServerSocket mss = null;
	  
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
		

	}
}
