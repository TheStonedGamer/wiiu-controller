package com.brian.wiiuremote;
public class LayoutGeometryTest {
  static void check(boolean ok) {
    if (!ok)
      throw new AssertionError();
  }
  public static void main(String[] args) {
    float[] r = LayoutGeometry.fit(-50, -20, 12, 12, false, false);
    check(r[0] == 8 && r[1] == 68 && r[2] == 40 && r[3] == 40);
    r = LayoutGeometry.fit(1000, 1000, 222, 222, false, true);
    check(r[0] + r[2] <= 952 && r[1] + r[3] <= 428);
    r = LayoutGeometry.fit(80, 80, 180, 60, true, false);
    check(r[2] == r[3]);
    r = LayoutGeometry.fit(80, 80, 30, 20, false, true);
    check(r[2] >= 100 && r[3] >= 100);
    r = LayoutGeometry.fit(80, 80, 9999, 9999, false, false);
    check(r[2] <= 900 && r[3] <= 360 && r[1] >= 68);
    boolean rejected = false;
    try {
      LayoutGeometry.fit(Float.NaN, 0, 40, 40, false, false);
    } catch (IllegalArgumentException e) {
      rejected = true;
    }
    check(rejected);
    float[] portrait = LayoutGeometry.fit(-10, 10, 1000, 1000, false, false, 460, 960);
    if (portrait[0] < 8 || portrait[1] < 120 || portrait[0] + portrait[2] > 452 || portrait[1] + portrait[3] > 928) throw new AssertionError("Portrait editor bounds");
    System.out.println("Editor movement/resize limits, reserved toolbar, " +
                       "circle aspect and invalid-coordinate checks passed");
  }
}
