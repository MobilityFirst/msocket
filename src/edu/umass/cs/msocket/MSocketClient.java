package edu.umass.cs.msocket;

import edu.umass.cs.msocket.FlowPath;
import edu.umass.cs.msocket.MSocket;
import edu.umass.cs.msocket.mobility.MobilityManagerClient;
import java.util.Arrays;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;

public class MSocketClient {


    private static final int    LOCAL_PORT = 6666;
    private static final String LOCALHOST  = "127.0.0.1";

    private static DecimalFormat df = new DecimalFormat("0.00##");

    private static final int TOTAL_ROUND = 500;

    private static int numBytes = 512000;


    public static double calc_avg(Long[] input){
        int len = input.length;
        double sum = 0;
        for(int i=0;i<len;i++){
            sum = sum  + input[i];
        }
        return sum/len;
    }

    public static double calc_median(Long[] input){
        Arrays.sort(input);
        double median;
        if (input.length % 2 == 0)
            median = ((double)input[input.length/2] + (double)input[input.length/2 - 1])/2;
        else
            median = (double) input[input.length/2];
        return median;
    }

    public static void main(String[] args) {
        String serverIPOrName = null;
        int numRound = TOTAL_ROUND;
        int numOfBytes= 0;
        int is_mb=0;

        if (args.length == 0){
                serverIPOrName = LOCALHOST;
                numOfBytes = numBytes;
        }
        else if(args.length == 1){
            serverIPOrName = args[0];
	   is_mb = 0;
            numOfBytes = numBytes;
        }

        if(args.length == 2){
            serverIPOrName = args[0];
	   is_mb = Integer.parseInt(args[1]);
            numOfBytes = numBytes;
        }

        if (args.length == 3){
            serverIPOrName = args[0];
            numOfBytes = Integer.parseInt(args[2]);
            is_mb = Integer.parseInt(args[1]);
        }

        if(is_mb == 1){
         	numOfBytes = numOfBytes * 1000000;
         }else{
         	numOfBytes = numOfBytes * 1000;
         }


        int serverPort = LOCAL_PORT;

        if (System.getProperty("total") != null) {
            numOfBytes = Integer.parseInt(System.getProperty("total"));
        }

        if (System.getProperty("round") != null) {
            numRound = Integer.parseInt(System.getProperty("round"));
        }

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
            Long[]  transferTime  = new Long[numRound];
            while (rd < numRound) {
                Thread.sleep(5000);
                System.out.println("[Round number:] " + rd);
                int numSent = numOfBytes;
                System.out.println("[Client:] To read " + numSent + " bytes data from input stream...");


                byte[] b = new byte[numSent];

                ByteBuffer dbuf = ByteBuffer.allocate(4);
                dbuf.putInt(numSent);
                byte[] bytes = dbuf.array();

                int numRead;
                int totalRead = 0;

                long app_level_start = System.nanoTime();
                long write_time_start = System.nanoTime();
                os.write(bytes);
                long write_time_elapsed = System.nanoTime() - write_time_start;
                long read_time_start = System.nanoTime();
                do {
                    numRead = is.read(b);
                    if (numRead >= 0)
                        totalRead += numRead;

                } while (totalRead < numSent);
                long read_time_elapsed = System.nanoTime() - read_time_start;
                long app_level_elapsed = System.nanoTime() - app_level_start;
                System.out.println("[Write:] " + write_time_elapsed + " ns");
                System.out.println("[Read:] " + read_time_elapsed/ 1000000  + " ms");
                System.out.println("[TransferTime:] " + app_level_elapsed + " nano seconds");

                double n_bytes = numOfBytes;
                n_bytes = n_bytes/1000000.0;
                double app_level_elap = app_level_elapsed;
                app_level_elap = app_level_elap / 1000000000.0;
                System.out.println("[Thruput:] " +  n_bytes/app_level_elap + " MB/s");
                transferTime[rd] = app_level_elapsed;
                rd++;

            }

            os.write(-1);
            os.flush();

            ms.close();
            System.out.println("Socket closed");
            System.out.println("this is the average transferTime for MSocket of " + Integer.toString(numRound) + " rounds : " + Double.toString(calc_avg(transferTime)/1000000) + " ms");
            System.out.println("this is the median transferTime for MSocket of " + Integer.toString(numRound) + " rounds : " + Double.toString(calc_median(transferTime)/1000000) + " ms");
            MobilityManagerClient.shutdownMobilityManager();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
