import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.io.*;
import java.net.*;
import java.lang.*;
import java.nio.*;

public class TCPServer  implements Runnable{

    private static final int    LOCAL_PORT = 5455;
    private static final String LOCALHOST = "0.0.0.0";

    private static ServerSocket server;

    static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public TCPServer(){
    }

    public void run(){
      String serverIPOrName = LOCALHOST;
      int serverPort = LOCAL_PORT;
      try{
        server = new ServerSocket(serverPort);
      }catch(IOException e){
        e.printStackTrace();
      }

      try{
        Socket socket = server.accept();

        RequestHandlingThread requestThread = new RequestHandlingThread(socket);
        requestThread.start();
      }catch(IOException e){
        e.printStackTrace();
        }



    }
    private static class RequestHandlingThread extends Thread
   	{
        private Socket socket;

        public RequestHandlingThread(Socket sock)
        {
            this.socket = sock;
        }

        public void run()
        {
            int numRead = 0;

            InputStream is = null;
            OutputStream os = null;
            try {
                is = socket.getInputStream();
                os = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(is != null) {
                while(numRead >= 0) {
                    long start = System.currentTimeMillis();
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


                        byte[] b = new byte[numRead];
                        new Random().nextBytes(b);
                        try {
                            os.write(b);
                            os.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        // reset
                        numRead = 0;
                        long elapsed = System.currentTimeMillis() - start;
                        LocalDateTime now = LocalDateTime.now();

                    }

                }

                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }


        }
    }



}
