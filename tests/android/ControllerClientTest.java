package com.brian.wiiuremote;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
public class ControllerClientTest {
  static final class Sample {
    final byte[] data;
    final long time;
    Sample(byte[] d) {
      data = d;
      time = System.nanoTime() / 1000000L;
    }
    int buttons() { return ByteBuffer.wrap(data).getInt(8); }
  }
  static void check(boolean condition) {
    if (!condition)
      throw new AssertionError();
  }
  static void until(java.util.function.BooleanSupplier condition)
      throws Exception {
    long deadline = System.currentTimeMillis() + 4000;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > deadline)
        throw new AssertionError("Timed out waiting for network event");
      Thread.sleep(10);
    }
  }
  public static void main(String[] args) throws Exception {
    DatagramSocket server =
        new DatagramSocket(26760, InetAddress.getByName("127.0.0.1"));
    List<Sample> samples = new CopyOnWriteArrayList<>();
    Thread receive = new Thread(() -> {
      byte[] b = new byte[64];
      while (!server.isClosed())
        try {
          DatagramPacket d = new DatagramPacket(b, b.length);
          server.receive(d);
          samples.add(new Sample(Arrays.copyOf(b, d.getLength())));
          byte[] reply = {'A', 'N', 'W', 'A'};
          server.send(
              new DatagramPacket(reply, 4, d.getAddress(), d.getPort()));
        } catch (Exception e) {
          if (!server.isClosed())
            throw new RuntimeException(e);
        }
    });
    receive.start();
    ControllerClient client = new ControllerClient();
    try {
      ControllerPacket.State remote = new ControllerPacket.State(false);
      remote.buttons = 0x800;
      client.publish(remote);
      client.start("127.0.0.1");
      until(() -> client.replies >= 5);
      check(client.error.isEmpty() && client.lastAck > 0);
      ControllerPacket.State pro = new ControllerPacket.State(true);
      pro.buttons = 16;
      pro.lx = 100;
      client.publish(pro);
      until(()
                -> samples.stream().anyMatch(
                    s -> s.data[23] == 1 && s.buttons() == 16));
      int firstPro = -1;
      for (int i = 0; i < samples.size(); i++)
        if (samples.get(i).data[23] == 1) {
          firstPro = i;
          break;
        }
      check(firstPro > 0);
      Sample neutral = samples.get(firstPro - 1), newMode =
                                                      samples.get(firstPro);
      check(neutral.data[23] == 0 && neutral.buttons() == 0 &&
            newMode.time - neutral.time >= 300);
      ControllerPacket.State fourth = new ControllerPacket.State(true, 4);
      fourth.buttons = 32;
      client.publish(fourth);
      until(()
                -> samples.stream().anyMatch(
                    s -> s.data[5] == 3 && s.buttons() == 32));
      int firstFourth = -1;
      for (int i = 0; i < samples.size(); i++)
        if (samples.get(i).data[5] == 3) {
          firstFourth = i;
          break;
        }
      check(firstFourth > 0);
      check(samples.get(firstFourth - 1).data[5] == 2 &&
            samples.get(firstFourth - 1).buttons() == 0);
      check(samples.get(firstFourth).time - samples.get(firstFourth - 1).time >=
            300);
      ControllerPacket.State classic = new ControllerPacket.State(true, 4);
      classic.classic = true; classic.buttons = 0x2010; client.publish(classic);
      until(() -> samples.stream().anyMatch(s -> s.data[23] == 2 && s.buttons() == 0x2010));
      ControllerPacket.State motion = new ControllerPacket.State(false, 4);
      motion.motionPlus = true; motion.motionValid = true; motion.buttons = 0x800;
      client.publish(motion);
      until(() -> samples.stream().anyMatch(s -> s.data.length == 64 && s.data[23] == 3 && s.buttons() == 0x800));
      int firstMotion = -1;
      for (int i = 0; i < samples.size(); i++) if (samples.get(i).data[23] == 3) { firstMotion = i; break; }
      check(firstMotion > 0 && samples.get(firstMotion-1).data[23] == 2 && samples.get(firstMotion-1).buttons() == 0);
      check(samples.get(firstMotion).time - samples.get(firstMotion-1).time >= 300);
      client.stop();
      until(() -> samples.get(samples.size() - 1).buttons() == 0);
      check(samples.get(samples.size() - 1).data[23] == 3 &&
            samples.get(samples.size() - 1).data[5] == 3);
      int stopped = samples.size();
      Thread.sleep(80);
      check(samples.size() == stopped);
      client.publish(new ControllerPacket.State(true));
      client.start("127.0.0.1");
      until(() -> client.replies >= 3);
      client.stop();
      System.out.println("UDP send/ACK, neutral mode transition, reconnect "
                         + "delay, stop and restart checks passed");
    } finally {
      client.stop();
      server.close();
      receive.join(1000);
    }
  }
}
