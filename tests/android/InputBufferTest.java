package com.brian.wiiuremote;
public class InputBufferTest {
  static void check(boolean ok) {
    if (!ok)
      throw new AssertionError();
  }
  public static void main(String[] args) {
    InputBuffer b = new InputBuffer();
    ControllerPacket.State s = new ControllerPacket.State(true, 4);
    b.publish(s, 1000);
    b.poll(1000);
    s.buttons = 0x2000 | 0x80 | 16;
    s.lx = 100;
    s.ly = -30;
    s.rx = -70;
    s.ry = 50;
    b.publish(s, 1001);
    s.buttons = 0;
    b.publish(s, 1002);
    ControllerPacket.State pressed = b.poll(1003);
    check(pressed.buttons == (0x2000 | 0x80 | 16) && pressed.lx == 100 &&
          pressed.rx == -70);
    check(b.poll(1004).buttons ==
          0); // Quick tap is retained even between network frames.
    s.buttons = 16;
    b.publish(s, 1005);
    s.buttons = 0; // Defensive copy.
    check(b.poll(1006).buttons == 16);
    ControllerPacket.State expired = b.poll(1600);
    check(expired.buttons == 0 && expired.lx == 0 && expired.rx == 0 &&
          expired.player == 4 && expired.pro);
    s.buttons = 32;
    b.publish(s, 1700);
    s.buttons = 0;
    b.publish(s, 1701);
    check(b.poll(1850).buttons ==
          0); // Do not replay old presses after an outage.
    s.buttons = 16;
    b.publish(s, 1900);
    s.player = 3;
    s.buttons = 0;
    b.publish(s, 1901);
    check(b.poll(1902).player == 3 && b.poll(1903).buttons == 0);
    s.classic = true; s.pro = true; b.publish(s, 2000);
    check(b.poll(2600).classic && b.poll(2600).buttons == 0);
    s.classic = false; s.pro = false; s.motionPlus = true; s.motionValid = true;
    b.publish(s, 2700);
    check(ControllerPacket.mode(b.poll(3300)) == 3 && !b.poll(3300).motionValid);
    System.out.println("Combined buttons + two sticks, quick taps, immutable "
                       + "snapshots, watchdog and stale-edge checks passed");
  }
}
