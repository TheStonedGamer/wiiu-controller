package com.brian.wiiuremote;

/**
 * Wire protocol shared with the Aroma receiver; all multibyte values are big
 * endian.
 */
public final class ControllerPacket {
  public static final class State {
    public boolean pro, classic, pointer, motionPlus, motionValid;
    public float[] gyro = new float[3], quaternion = {1, 0, 0, 0};
    public int player = 3;
    public int buttons, lx, ly, rx, ry, px, py, ax, ay, az = 1000;
    public State(boolean pro) { this(pro, 3); }
    public State(boolean pro, int player) {
      this.pro = pro;
      this.player = player;
    }
  }
  public static State neutral(State s) {
    State n = new State(s.pro, s.player);
    n.classic = s.classic;
    n.motionPlus = s.motionPlus;
    return n;
  }
  public static int mode(State s) { return s.classic ? 2 : s.pro ? 1 : s.motionPlus ? 3 : 0; }
  public static State copy(State s) {
    State c = new State(s.pro, s.player);
    c.classic = s.classic;
    c.motionPlus = s.motionPlus; c.motionValid = s.motionValid;
    c.gyro = s.gyro.clone(); c.quaternion = s.quaternion.clone();
    c.pointer = s.pointer;
    c.buttons = s.buttons;
    c.lx = s.lx;
    c.ly = s.ly;
    c.rx = s.rx;
    c.ry = s.ry;
    c.px = s.px;
    c.py = s.py;
    c.ax = s.ax;
    c.ay = s.ay;
    c.az = s.az;
    return c;
  }
  private static int clamp(int v, int max) {
    return Math.max(-max, Math.min(max, v));
  }
  private static void word(byte[] p, int at, int v) {
    p[at] = (byte)(v >>> 8);
    p[at + 1] = (byte)v;
  }
  private static int quantize(float value, int scale, int limit) {
    if (Float.isNaN(value) || Float.isInfinite(value)) throw new IllegalArgumentException("Invalid motion sample");
    return clamp(Math.round(value * scale), limit);
  }
  public static byte[] encode(State s, int sequence) {
    boolean motion = mode(s) == 3;
    byte[] p = new byte[motion ? 64 : 32];
    p[0] = 'A';
    p[1] = 'N';
    p[2] = 'W';
    p[3] = 'C';
    p[4] = (byte)(motion ? 2 : 1);
    if (s.player < 1 || s.player > 4)
      throw new IllegalArgumentException("Select player 1, 2, 3 or 4");
    p[5] = (byte)(s.player - 1);
    word(p, 6, sequence);
    int b = s.buttons & (s.classic ? 0xFEFF : s.pro ? 0x3FEFF : 0x9F1F);
    p[8] = (byte)(b >>> 24);
    p[9] = (byte)(b >>> 16);
    p[10] = (byte)(b >>> 8);
    p[11] = (byte)b;
    if (s.pro || s.classic) {
      word(p, 12, clamp(s.lx, 156));
      word(p, 14, clamp(s.ly, 156));
      word(p, 16, clamp(s.rx, 156));
      word(p, 18, clamp(s.ry, 156));
    } else {
      word(p, 12, clamp(s.px, 156));
      word(p, 14, clamp(s.py, 156));
      word(p, 16, clamp(s.ax, 4000));
      word(p, 18, clamp(s.ay, 4000));
      word(p, 20, clamp(s.az, 4000));
      p[22] = (byte)(s.pointer ? 1 : 0);
    }
    p[23] = (byte)mode(s);
    if (motion && s.motionValid) {
      for (int i = 0; i < 3; i++) word(p, 32 + 2*i, quantize(s.gyro[i], 1000, 32767));
      for (int i = 0; i < 4; i++) word(p, 38 + 2*i, quantize(s.quaternion[i], 32767, 32767));
      p[46] = 1;
    }
    return p;
  }
}
