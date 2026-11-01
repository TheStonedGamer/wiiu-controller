package com.brian.wiiuremote;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class ControllerRecoveryTest {
  static void check(boolean ok) {
    if (!ok)
      throw new AssertionError();
  }
  static void until(java.util.function.BooleanSupplier ready) throws Exception {
    long end = System.currentTimeMillis() + 6000;
    while (!ready.getAsBoolean()) {
      if (System.currentTimeMillis() > end)
        throw new AssertionError("Recovery timed out");
      Thread.sleep(10);
    }
  }
  static final class Responder implements AutoCloseable {
    final DatagramSocket socket;
    final Thread worker;
    final Set<Integer> ports = ConcurrentHashMap.newKeySet();
    volatile boolean replies = true, busy = false;
    Responder() throws Exception {
      socket = new DatagramSocket(26760, InetAddress.getByName("127.0.0.1"));
      worker = new Thread(() -> {
        while (!socket.isClosed())
          try {
            byte[] b = new byte[64];
            DatagramPacket p = new DatagramPacket(b, b.length);
            socket.receive(p);
            ports.add(p.getPort());
            if (replies) {
              byte[] ack = {'A', 'N', 'W', (byte)(busy ? 'B' : 'A')};
              socket.send(
                  new DatagramPacket(ack, 4, p.getAddress(), p.getPort()));
            }
          } catch (Exception e) {
            if (!socket.isClosed())
              throw new RuntimeException(e);
          }
      });
      worker.start();
    }
    public void close() throws Exception {
      socket.close();
      worker.join(1000);
    }
  }
  public static void main(String[] args) throws Exception {
    ControllerClient client = new ControllerClient();
    Responder server = new Responder();
    ScheduledExecutorService heartbeat =
        Executors.newSingleThreadScheduledExecutor();
    ControllerPacket.State held = new ControllerPacket.State(true, 4);
    held.buttons = 16 | 0x2000;
    held.lx = 100;
    held.rx = -80;
    heartbeat.scheduleAtFixedRate(
        () -> client.publish(held), 0, 25, TimeUnit.MILLISECONDS);
    try {
      client.start("127.0.0.1");
      until(() -> client.replies >= 5);
      server.busy = true;
      until(() -> client.error.contains("occupied") && client.lastAck == 0);
      server.busy = false;
      until(() -> client.error.isEmpty() && client.lastAck > 0);
      client.networkChanged();
      long beforeNetworkChange = client.replies;
      until(() -> client.replies > beforeNetworkChange);
      server.replies = false;
      final Responder first = server;
      until(() -> first.ports.size() >= 2);
      long previous = client.replies;
      server.replies = true;
      until(() -> client.replies > previous && client.error.isEmpty());
      server.close();
      until(() -> client.lastAck == 0 && !client.error.isEmpty());
      server = new Responder();
      long beforeRestart = client.replies;
      until(() -> client.replies > beforeRestart && client.lastAck > 0);
      // Consecutive starts must cancel the old sender rather than resurrecting
      // it.
      for (int i = 0; i < 5; i++) {
        client.start("127.0.0.1");
        Thread.sleep(30);
      }
      until(() -> client.replies >= 3);
      client.stop();
      Thread.sleep(80);
      long live =
          Thread.getAllStackTraces()
              .keySet()
              .stream()
              .filter(t -> t.isAlive() && t.getName().equals("WiiU-UDP"))
              .count();
      check(live == 0);
      System.out.println(
          "Dropped acknowledgements, socket recreation, server restart and "
          + "rapid start/stop recovery checks passed");
    } finally {
      heartbeat.shutdownNow();
      client.stop();
      server.close();
    }
  }
}
