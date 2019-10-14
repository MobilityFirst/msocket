import java.io.*;


public class runtcpserverclient{

  public static void main(String[] args){
    TCPServer server = new TCPServer();
    Thread s = new Thread(server);
    s.start();


    TCPClient client = new TCPClient();
    Thread c = new Thread(client);
    c.start();


  }
}
