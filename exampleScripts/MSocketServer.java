import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.Scanner;


import edu.umass.cs.msocket.MServerSocket;
import edu.umass.cs.msocket.MSocket;

public class MSocketServer {

    private static final int    LOCAL_PORT = 5555;
    private static final String LOCALHOST  = "127.0.0.1";

    private static MServerSocket mss = null;

    static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

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
            int numRead = 0;
            Scanner reader = new Scanner(System.in);
            InputStream is = null;
            OutputStream os = null;
            try {
                is = msocket.getInputStream();
                os = msocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(is != null) {
                // client sends 0 to close the socket

                while(numRead >= 0) {
                    long start = System.currentTimeMillis();

                    // get number of bytes to send
                    byte[] numByteArr = new byte[8];
                    try {
                        System.out.println("waiting before read ");
                        is.read(numByteArr);
                        ByteBuffer wrapped = ByteBuffer.wrap(numByteArr);
                        numRead = wrapped.getInt();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    System.out.println("received number of bytes");
                    // send random bytes
                    if (numRead > 0) {
                        System.out.println("Ready to send "+numRead+" bytes.");

                        byte[] b = new byte[numRead];
                        new Random().nextBytes(b);
                        System.out.println("array initialized");
                        try {
                            os.write(b);
                            os.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        // reset
                        b = null;
                        numRead = 0;
                        long elapsed = System.currentTimeMillis() - start;
                        LocalDateTime now = LocalDateTime.now();
                        System.out.println("[" + dtf.format(now) + "] Data sending finished. It takes " + elapsed / 1000.0 + " seconds");
                    }

                }

                try {
                    msocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("Socket closed.");
            }


        }
    }
}
