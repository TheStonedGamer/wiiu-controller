package com.brian.wiiuremote;

/** Relative top-edge aiming; touchscreen input takes priority in the UI. */
public final class GyroPointer {
  private final float[] center = {1, 0, 0, 0};
  private float x, y;
  private double last;
  private boolean filtered;
  public void reset() { recenter(new float[] {1, 0, 0, 0}); }
  public void recenter(float[] q) {
    System.arraycopy(q, 0, center, 0, 4);
    x = y = 0; last = 0; filtered = false;
  }
  public void invalidate() { filtered = false; last = 0; }
  public float x() { return x; }
  public float y() { return y; }
  public boolean update(float[] q, double now, float sensitivity, float smoothing) {
    float a = center[0], b = -center[1], c = -center[2], d = -center[3];
    float w = a*q[0]-b*q[1]-c*q[2]-d*q[3];
    float rx = a*q[1]+b*q[0]+c*q[3]-d*q[2];
    float ry = a*q[2]-b*q[3]+c*q[0]+d*q[1];
    float rz = a*q[3]+b*q[2]-c*q[1]+d*q[0];
    float fx = 2*(rx*ry-w*rz), fy = 1-2*(rx*rx+rz*rz), fz = 2*(ry*rz+w*rx);
    if (fy <= 0 || Float.isNaN(fx) || Float.isInfinite(fx) || Float.isNaN(fy) || Float.isInfinite(fy) || Float.isNaN(fz) || Float.isInfinite(fz)) {
      invalidate(); return false;
    }
    float tx = (float)(Math.atan2(fx, fy) / Math.toRadians(35) * sensitivity);
    float ty = (float)(-Math.atan2(fz, Math.hypot(fx, fy)) / Math.toRadians(25) * sensitivity);
    tx = Math.max(-1, Math.min(1, tx)); ty = Math.max(-1, Math.min(1, ty));
    double dt = Math.max(0, Math.min(.1, now-last));
    float alpha = !filtered || smoothing <= 0 ? 1 : (float)(1-Math.exp(-dt/smoothing));
    x += (tx-x)*alpha; y += (ty-y)*alpha; last = now; filtered = true;
    return true;
  }
}
