package com.brian.wiiuremote;
import java.nio.*;
import java.nio.file.*;
public class ControllerPacketTest {
  static void check(boolean ok) {
    if (!ok)
      throw new AssertionError();
  }
  public static void main(String[] args) throws Exception {
    ControllerPacket.State remote = new ControllerPacket.State(false);
    remote.buttons = 0x800;
    remote.pointer = true;
    remote.px = 500;
    remote.py = -500;
    remote.ax = 5000;
    remote.ay = -5000;
    remote.az = 1000;
    byte[] r = ControllerPacket.encode(remote, 65535);
    ByteBuffer b = ByteBuffer.wrap(r).order(ByteOrder.BIG_ENDIAN);
    check(r.length == 32 && r[5] == 2 && r[23] == 0 && r[22] == 1);
    check(b.getShort(6) == -1 && b.getInt(8) == 0x800);
    check(b.getShort(12) == 156 && b.getShort(14) == -156);
    check(b.getShort(16) == 4000 && b.getShort(18) == -4000);
    Files.write(Paths.get("build/remote-packet.bin"), r);
    ControllerPacket.State pro = new ControllerPacket.State(true);
    pro.buttons = 0x20010;
    pro.lx = -156;
    pro.ly = 156;
    pro.rx = 78;
    pro.ry = -78;
    byte[] p = ControllerPacket.encode(pro, 0);
    b = ByteBuffer.wrap(p);
    check(p[23] == 1 && p[22] == 0 && b.getInt(8) == 0x20010 &&
          b.getShort(16) == 78);
    Files.write(Paths.get("build/pro-packet.bin"), p);
    check(
        ByteBuffer
            .wrap(ControllerPacket.encode(new ControllerPacket.State(true), 1))
            .getInt(8) == 0);
    ControllerPacket.State fourth = new ControllerPacket.State(true, 4);
    byte[] fourthPacket = ControllerPacket.encode(fourth, 2);
    check(fourthPacket[5] == 3 && fourthPacket[23] == 1);
    Files.write(Paths.get("build/p4-packet.bin"), fourthPacket);
    byte[] second =
        ControllerPacket.encode(new ControllerPacket.State(false, 2), 3);
    check(second[5] == 1 && second[23] == 0);
    Files.write(Paths.get("build/p2-packet.bin"), second);
    byte[] first =
        ControllerPacket.encode(new ControllerPacket.State(false, 1), 4);
    check(first[5] == 0);
    Files.write(Paths.get("build/p1-packet.bin"), first);
    ControllerPacket.State classic = new ControllerPacket.State(true, 3);
    classic.classic = true;
    classic.buttons = 0x32290;
    classic.lx = -156; classic.ly = 156; classic.rx = 78; classic.ry = -78;
    byte[] c = ControllerPacket.encode(classic, 10);
    check(c[23] == 2 && ByteBuffer.wrap(c).getInt(8) == 0x2290);
    check(ByteBuffer.wrap(c).getShort(18) == -78);
    check(ControllerPacket.copy(classic).classic);
    check(ControllerPacket.mode(ControllerPacket.neutral(classic)) == 2);
    Files.write(Paths.get("build/classic-packet.bin"), c);
    ControllerPacket.State motion = new ControllerPacket.State(false, 4);
    motion.motionPlus = true; motion.motionValid = true;
    motion.gyro = new float[] { .25f, -.5f, 1 };
    motion.quaternion = new float[] { (float)Math.sqrt(.5), 0, 0, (float)Math.sqrt(.5) };
    motion.buttons = 0x800;
    byte[] mp = ControllerPacket.encode(motion, 20);
    ByteBuffer mb = ByteBuffer.wrap(mp);
    check(mp.length == 64 && mp[4] == 2 && mp[23] == 3 && mp[46] == 1);
    check(mb.getShort(32) == 250 && mb.getShort(34) == -500 && mb.getShort(36) == 1000);
    ControllerPacket.State copy = ControllerPacket.copy(motion);
    motion.gyro[0] = 9; check(copy.gyro[0] == .25f);
    byte[] neutralMotion = ControllerPacket.encode(ControllerPacket.neutral(motion), 21);
    check(neutralMotion.length == 64 && neutralMotion[23] == 3 && neutralMotion[46] == 0);
    Files.write(Paths.get("build/motion-packet.bin"), mp);
    boolean invalidRejected = false;
    try {
      ControllerPacket.encode(new ControllerPacket.State(false, 0), 0);
    } catch (IllegalArgumentException expected) {
      invalidRejected = true;
    }
    check(invalidRejected);
    System.out.println("Android packet encoding, mode, neutral, clamp and "
                       + "signed-axis checks passed");
  }
}
