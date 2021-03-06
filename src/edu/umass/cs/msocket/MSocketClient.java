package edu.umass.cs.msocket;

import edu.umass.cs.msocket.FlowPath;
import edu.umass.cs.msocket.MSocket;
import edu.umass.cs.msocket.mobility.MobilityManagerClient;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;

public class MSocketClient {


    private static final int    LOCAL_PORT = 5555;
    private static final String LOCALHOST  = "127.0.0.1";

    private static DecimalFormat df = new DecimalFormat("0.00##");

    private static final int TOTAL_ROUND = 2;
    private static int numBytes = Integer.MAX_VALUE - 2;

    public static void main(String[] args) {
        String serverIPOrName = null;
        int numRound = TOTAL_ROUND;
        int numOfBytes= 0;
        int is_mb=1;

        if (args.length == 0){
            serverIPOrName = LOCALHOST;
            numOfBytes = numBytes;
        }
        int serverPort = LOCAL_PORT;

        try {
            MSocket ms = new MSocket(InetAddress.getByName(serverIPOrName), serverPort);
            OutputStream os = ms.getOutputStream();
            InputStream is = ms.getInputStream();

            // wait for 2 seconds for all connections
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int rd = 0;
            for (int i = 0; i < ms.getActiveFlowPaths().size(); i++) {
                FlowPath currfp = ms.getActiveFlowPaths().get(i);

                System.out.println("Flowpath id=" + currfp.getFlowPathId() + " local ip=" + currfp.getLocalEndpoint().toString());
            }

            while (rd < numRound) {
                System.out.println(rd);
                if(rd==1) {
                    numOfBytes = 1000001;
                }
                System.out.println("[Client:] To read " + numOfBytes + " bytes data from input stream...");



                byte[] b = new byte[numOfBytes];

                ByteBuffer dbuf = ByteBuffer.allocate(8);
                dbuf.putInt(numOfBytes);
                byte[] bytes = dbuf.array();

                int numRead;
                int totalRead = 0;

                long start = System.currentTimeMillis();

                os.write(bytes);
                System.out.println("wrote the Number of bytes");
                do {
                    numRead = is.read(b);
                    if (numRead >= 0)
                        totalRead += numRead;

                } while (totalRead < numOfBytes);
                b = null;
                long elapsed = System.currentTimeMillis() - start;
                System.out.println("[Latency:] " + elapsed  + " ms");
                System.out.println("[Thruput:] " + df.format(numOfBytes/1000.0/elapsed ) + " MB/s");

                rd++;

            }

            os.write(-1);
            os.flush();

            ms.close();
            System.out.println("Socket closed");
            MobilityManagerClient.shutdownMobilityManager();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
