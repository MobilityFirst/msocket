import edu.umass.cs.msocket.MServerSocket;
import edu.umass.cs.msocket.MSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class MSocketServer {

    private static final int    LOCAL_PORT = 6666;
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
                long run_number = 0;
                while(numRead >= 0) {
                    run_number += 1;
                    System.out.println("Round Numebr : " + run_number)
                    long start = System.nanoTime();

                    // get number of bytes to send
                    byte[] numByteArr = new byte[4];
                    try {
                        is.read(numByteArr);
                        ByteBuffer wrapped = ByteBuffer.wrap(numByteArr);
                        numRead = wrapped.getInt();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // send random bytes
                    if (numRead > 0) {
                        System.out.println("Ready to send "+numRead+" bytes.");

                        byte[] b = new byte[numRead];
                        new Random().nextBytes(b);

                        try {
			                       long write_time_start = System.nanoTime();
                             os.write(b);
                             os.flush();
			                       long write_time_elapsed = System.nanoTime() - write_time_start;
                             System.out.println("Time to write to the socket is: " + write_time_elapsed / 1000000000);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        // reset
                        numRead = 0;
                        long elapsed = System.nanoTime() - start;
                        LocalDateTime now = LocalDateTime.now();
                        System.out.println("[" + dtf.format(now) + "] Data sending finished. Time taken to just write to the socket " + elapsed / 1000000000 + " seconds");
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
