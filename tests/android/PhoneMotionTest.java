package com.brian.wiiuremote;
public class PhoneMotionTest {
  private static void near(float a, float b) { if (Math.abs(a-b) > .001) throw new AssertionError(a + " != " + b); }
  public static void main(String[] args) {
    for (int r = 0; r < 4; r++) {
      float[] v = PhoneMotion.rotate(1, 2, 3, r);
      float[][] expected = {{1,2,3}, {-2,1,3}, {-1,-2,3}, {2,-1,3}};
      for (int i = 0; i < 3; i++) near(v[i], expected[r][i]);
    }
    PhoneMotion m = new PhoneMotion();
    for (int i = 0; i <= 100; i++) m.update(1 + i*.01, 0, 0, (float)(Math.PI/2), 0);
    near(m.quaternion[0], (float)Math.sqrt(.5)); near(m.quaternion[3], (float)Math.sqrt(.5)); near(m.rate[2], .25f);
    if (!m.fresh(2.1) || m.fresh(2.3)) throw new AssertionError("Stale gyro handling");
    float old = m.quaternion[3]; m.update(4, 0, 0, 1, 0); near(m.quaternion[3], old);
    m.reset(); near(m.quaternion[0], 1); near(m.rate[2], 0);
    if (m.fresh(4)) throw new AssertionError("Reset retained sample");
    System.out.println("Motion rotation, 90-degree gyro integration, rate units, freshness and recenter passed");
  }
}
