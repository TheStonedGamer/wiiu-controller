package com.brian.wiiuremote;
import java.io.IOException;
import java.net.*;
public final class ControllerDiscovery {
  public static String find(ControllerClient.SocketBinder binder)
      throws IOException {
    return find(binder, InetAddress.getByName("255.255.255.255"));
  }
  static String find(ControllerClient.SocketBinder binder,
                     InetAddress broadcast) throws IOException {
    try (DatagramSocket socket = new DatagramSocket()) {
      binder.bind(socket);
      socket.setBroadcast(true);
      socket.setSoTimeout(200);
      long until = System.nanoTime() / 1000000L + 2600, next = 0;
      while (System.nanoTime() / 1000000L < until) {
        long now = System.nanoTime() / 1000000L;
        if (now >= next) {
          byte[] query = {'W', 'U', 'D', '?'};
          socket.send(new DatagramPacket(query, 4, broadcast, 26760));
          next = now + 400;
        }
        try {
          byte[] bytes = new byte[8];
          DatagramPacket reply = new DatagramPacket(bytes, bytes.length);
          socket.receive(reply);
          if (reply.getLength() == 4 && bytes[0] == 'W' && bytes[1] == 'U' &&
              bytes[2] == 'D' && bytes[3] == '!' && reply.getPort() == 26760)
            return reply.getAddress().getHostAddress();
        } catch (SocketTimeoutException ignored) {
        }
      }
      throw new SocketTimeoutException("Wii U discovery timed out");
    }
  }
}
