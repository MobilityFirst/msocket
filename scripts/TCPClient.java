import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.io.*;
import java.net.*;
import java.lang.*;
import java.nio.*;
import java.util.Arrays;


public class TCPClient implements Runnable{


    private static final int    LOCAL_PORT = 5455;
    private static final String LOCALHOST  = "127.0.0.1";
    private static final DecimalFormat df = new DecimalFormat("0.00##");
    private static final int TOTAL_ROUND = 200;
    private static final int numBytes = 1000000;

    public TCPClient(){

    }
    public static Long calc_avg(Long[] input){
            int len = input.length;
            Long sum = 0L;
            for(int i=0;i<len;i++){
                sum = sum  + input[i];
            }
            return sum/len;
    }
    public static Long calc_median(Long[] input){
      Arrays.sort(input);
      Long median;
      if (input.length % 2 == 0)
        median = ((long)input[input.length/2] + (long)input[input.length/2 - 1])/2;
      else
        median = (long) input[input.length/2];
      return median;
    }
    public void run(){
      String serverIPOrName = LOCALHOST;
      int serverPort = LOCAL_PORT;
      int numRound = TOTAL_ROUND;
      int numOfBytes= numBytes;
      long median_time = 0;

      try {

        Socket socket = null;
        socket = new Socket(serverIPOrName,serverPort);
        OutputStream os = socket.getOutputStream();
        InputStream is = socket.getInputStream();
        // wait for 2 seconds for all connections
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        int current_round = 0;
        Long[]  transferTime  = new Long[numRound];
        while (current_round < numRound) {
            int numSent = numOfBytes;

            byte[] b = new byte[numSent];
            ByteBuffer dbuf = ByteBuffer.allocate(4);
            dbuf.putInt(numSent);
            byte[] bytes = dbuf.array();
            int numRead;
            int totalRead = 0;
            long start = System.currentTimeMillis();
            os.write(bytes);
            do {
                numRead = is.read(b);
                if (numRead >= 0)
                    totalRead += numRead;

            } while (totalRead < numSent);

            long elapsed = System.currentTimeMillis() - start;


            transferTime[current_round] = elapsed;
            current_round++;
        }

        os.write(-1);
        os.flush();

        median_time = calc_avg(transferTime);
        System.out.println(median_time);
        socket.close();

    } catch (Exception e) {
        e.printStackTrace();
    }




    }

}
