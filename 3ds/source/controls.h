#ifndef CONTROLS_H
#define CONTROLS_H
#include <stdbool.h>
#include <stdint.h>
typedef struct {
  bool swap, touching;
  int capture, anchorX, anchorY, x, y;
  int16_t rightX, rightY;
  uint32_t buttons;
} TouchControls;
void controlsUpdate(TouchControls *c, bool touching, int x, int y);
void controlsDraw(const TouchControls *c, bool connected);
#endif
