#include "controls.h"
#include <3ds.h>
#include <string.h>
#define BG 0x101827
#define PANEL 0x1B2940
#define MUTED 0x91A5BF
#define TEXT 0xEDF4FF
#define ACCENT 0x5ADDC5
static bool inside(int x, int y, int left, int top, int w, int h) {
  return x >= left && x < left + w && y >= top && y < top + h;
}
static int16_t stick(int delta) {
  if (delta >= -4 && delta <= 4)
    return 0;
  int v = delta * 156 / 48;
  return v > 156 ? 156 : v < -156 ? -156 : v;
}
void controlsUpdate(TouchControls *c, bool touching, int x, int y) {
  c->buttons = 0;
  c->rightX = c->rightY = 0;
  if (touching && !c->touching) {
    c->capture = 0;
    if (inside(x, y, 12, 40, 296, 36)) {
      c->swap = !c->swap;
      c->capture = 1;
    } else if (inside(x, y, 12, 86, 84, 60))
      c->capture = 2;
    else if (inside(x, y, 12, 156, 84, 60))
      c->capture = 3;
    else if (inside(x, y, 108, 86, 200, 130)) {
      c->capture = 4;
      c->anchorX = x;
      c->anchorY = y;
    }
  }
  c->touching = touching;
  c->x = x;
  c->y = y;
  if (!touching) {
    c->capture = 0;
    return;
  }
  if (c->capture == 2 && inside(x, y, 12, 86, 84, 60))
    c->buttons = 0x20000;
  if (c->capture == 3 && inside(x, y, 12, 156, 84, 60))
    c->buttons = 0x10000;
  if (c->capture == 4) {
    c->rightX = stick(x - c->anchorX);
    c->rightY = stick(c->anchorY - y);
  }
}
static unsigned char *fb;
static void pixel(int x, int y, unsigned color) {
  if (x < 0 || x >= 320 || y < 0 || y >= 240)
    return;
  unsigned char *p = fb + (x * 240 + 239 - y) * 3;
  p[0] = color;
  p[1] = color >> 8;
  p[2] = color >> 16;
}
static void box(int x, int y, int w, int h, unsigned color) {
  for (int a = x; a < x + w; a++)
    for (int b = y; b < y + h; b++)
      pixel(a, b, color);
}
static void text(int x, int y, const char *s, unsigned color) {
  ConsoleFont *font = &consoleGetDefault()->font;
  for (; *s; s++, x += 8) {
    int c = (unsigned char)*s - font->asciiOffset;
    if (c < 0 || c >= font->numChars)
      continue;
    for (int row = 0; row < 8; row++)
      for (int col = 0; col < 8; col++)
        if (font->gfx[c * 8 + row] & (0x80 >> col))
          pixel(x + col, y + row, color);
  }
}
static void disk(int cx, int cy, int radius, unsigned color) {
  for (int y = -radius; y <= radius; y++)
    for (int x = -radius; x <= radius; x++)
      if (x * x + y * y <= radius * radius &&
          inside(cx + x, cy + y, 108, 86, 200, 130))
        pixel(cx + x, cy + y, color);
}
void controlsDraw(const TouchControls *c, bool connected) {
  fb = gfxGetFramebuffer(GFX_BOTTOM, GFX_LEFT, NULL, NULL);
  box(0, 0, 320, 240, BG);
  text(12, 14, "TOUCH CONTROLS", TEXT);
  box(284, 13, 24, 10, connected ? ACCENT : 0xEEAD59);
  box(12, 40, 296, 36, c->swap ? 0x245A60 : PANEL);
  text(24, 49, c->swap ? "L > ZL    R > ZR" : "L > L     R > R", TEXT);
  text(24, 62, "TAP TO SWAP SHOULDERS", c->swap ? ACCENT : MUTED);
  box(12, 86, 84, 60, c->buttons & 0x20000 ? 0x245A60 : PANEL);
  text(42, 105, "L3", TEXT);
  text(22, 125, "L CLICK", MUTED);
  box(12, 156, 84, 60, c->buttons & 0x10000 ? 0x245A60 : PANEL);
  text(42, 175, "R3", TEXT);
  text(22, 195, "R CLICK", MUTED);
  box(108, 86, 200, 130, PANEL);
  int cx = c->capture == 4 ? c->anchorX : 208;
  int cy = c->capture == 4 ? c->anchorY : 154;
  disk(cx, cy, 42, 0x2B405A);
  disk(cx, cy, 39, PANEL);
  disk(cx + c->rightX * 32 / 156, cy - c->rightY * 32 / 156, 12, ACCENT);
  text(120, 94, "RIGHT STICK", TEXT);
  text(122, 202, "DRAG TO AIM", MUTED);
  text(12, 226, "CIRCLE PAD = LEFT STICK", MUTED);
}
