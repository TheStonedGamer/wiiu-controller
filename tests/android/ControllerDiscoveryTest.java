package com.brian.wiiuremote;
import java.net.*;
public class ControllerDiscoveryTest {
  public static void main(String[] args) throws Exception {
    try (DatagramSocket server =
             new DatagramSocket(26760, InetAddress.getByName("127.0.0.1"))) {
      Thread responder = new Thread(() -> {
        try {
          byte[] b = new byte[8];
          DatagramPacket p = new DatagramPacket(b, b.length);
          server.receive(p);
          if (p.getLength() != 4 || b[3] != '?')
            throw new AssertionError();
          byte[] ack = {'W', 'U', 'D', '!'};
          server.send(new DatagramPacket(ack, 4, p.getAddress(), p.getPort()));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
      responder.start();
      String ip = ControllerDiscovery.find(
          socket -> {}, InetAddress.getByName("127.0.0.1"));
      if (!"127.0.0.1".equals(ip))
        throw new AssertionError();
      responder.join(1000);
      System.out.println(
          "Wii U discovery query/reply and address selection passed");
    }
  }
}
