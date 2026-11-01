package com.brian.wiiuremote;
/**
 * Logical canvas bounds preserve the toolbar and status footer on any screen
 * size.
 */
public final class LayoutGeometry {
  public static float[] fit(float x, float y, float w, float h, boolean circle,
                            boolean stick) {
    return fit(x, y, w, h, circle, stick, 960, 460);
  }
  public static float[] fit(float x, float y, float w, float h, boolean circle,
                            boolean stick, float canvasW, float canvasH) {
    if (Float.isNaN(x) || Float.isInfinite(x) || Float.isNaN(y) ||
        Float.isInfinite(y) || Float.isNaN(w) || Float.isInfinite(w) ||
        Float.isNaN(h) || Float.isInfinite(h))
      throw new IllegalArgumentException("Invalid layout coordinates");
    float minimum = stick ? 100 : 40;
    w = Math.max(minimum, Math.min(canvasW - 60, w));
    h = Math.max(minimum, Math.min(canvasH - (canvasH > canvasW ? 152 : 100), h));
    if (circle)
      w = h = Math.min(Math.min(canvasW - 60, canvasH - 100), Math.max(w, h));
    x = Math.max(8, Math.min(canvasW - 8 - w, x));
    y = Math.max(canvasH > canvasW ? 120 : 68, Math.min(canvasH - 32 - h, y));
    return new float[] {x, y, w, h};
  }
}
