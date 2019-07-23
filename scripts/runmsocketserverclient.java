import java.io.*;

public class runmsocketserverclient{
  public static void main(String[] args){
    MSocketServer server = new MSocketServer();
    Thread s = new Thread(server);
    s.start();


    MSocketClient client = new MSocketClient();
    Thread c = new Thread(client);
    c.start();


  }
}
