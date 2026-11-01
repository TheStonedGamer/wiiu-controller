package com.brian.wiiuremote;

/** Gyroscope integration in display coordinates; rates are revolutions/second. */
public final class PhoneMotion {
  public final float[] quaternion = {1, 0, 0, 0}, rate = new float[3];
  private double last;
  public void reset() {
    last = 0;
    java.util.Arrays.fill(quaternion, 0); quaternion[0] = 1;
    java.util.Arrays.fill(rate, 0);
  }
  public static float[] rotate(float x, float y, float z, int rotation) {
    switch (rotation & 3) {
      case 1: return new float[] {-y, x, z};
      case 2: return new float[] {-x, -y, z};
      case 3: return new float[] {y, -x, z};
      default: return new float[] {x, y, z};
    }
  }
  public void update(double time, float x, float y, float z, int rotation) {
    float[] v = rotate(x, y, z, rotation);
    double dt = last > 0 ? time - last : 0; last = time;
    for (int i = 0; i < 3; i++) rate[i] = (float)(v[i] / (2 * Math.PI));
    if (dt <= 0 || dt > .1) return;
    double length = Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
    if (length < .000001) return;
    float scale = (float)(Math.sin(length * dt / 2) / length);
    float[] d = {(float)Math.cos(length * dt / 2), v[0]*scale, v[1]*scale, v[2]*scale};
    float[] a = quaternion;
    float[] q = {a[0]*d[0]-a[1]*d[1]-a[2]*d[2]-a[3]*d[3],
      a[0]*d[1]+a[1]*d[0]+a[2]*d[3]-a[3]*d[2],
      a[0]*d[2]-a[1]*d[3]+a[2]*d[0]+a[3]*d[1],
      a[0]*d[3]+a[1]*d[2]-a[2]*d[1]+a[3]*d[0]};
    double norm = Math.sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]+q[3]*q[3]);
    for (int i = 0; i < 4; i++) a[i] = (float)(q[i] / norm);
  }
  public boolean fresh(double now) { return last > 0 && now - last >= 0 && now - last < .2; }
}
