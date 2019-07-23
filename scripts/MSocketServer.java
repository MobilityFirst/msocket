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
import java.util.Scanner;

public class MSocketServer implements Runnable{

    private static final int    LOCAL_PORT = 5556;
    private static final String LOCALHOST  = "127.0.0.1";
    private static MServerSocket mss = null;
    static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public MSocketServer(){

    }
    public void run(){
      String serverIPOrName = LOCALHOST;
      int serverPort = LOCAL_PORT;
      try{
        mss = new MServerSocket(serverPort, 0, InetAddress.getByName(serverIPOrName));
        MSocket msocket = mss.accept();
        RequestHandlingThread requestThread = new RequestHandlingThread(msocket);
        requestThread.start();
      }catch(IOException e){
        e.printStackTrace();
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


                while(numRead >= 0) {
                    long start = System.currentTimeMillis();


                    byte[] numByteArr = new byte[8];
                    try {

                        is.read(numByteArr);
                        ByteBuffer wrapped = ByteBuffer.wrap(numByteArr);
                        numRead = wrapped.getInt();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (numRead > 0) {


                        byte[] b = new byte[numRead];
                        new Random().nextBytes(b);

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

                    }

                }

                try {
                    msocket.close();
                    System.exit(0);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }


        }
    }
}
