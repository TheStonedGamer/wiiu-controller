#pragma once
#include <stdint.h>
#include <string.h>

typedef struct {
  uint32_t buttons[32], latest;
  double times[32];
  unsigned count;
} IOSInputBuffer;

static inline void ios_input_publish(IOSInputBuffer *b, double now, uint32_t buttons) {
  if (buttons == b->latest) return;
  if (b->count == 32) b->count = 0;
  b->buttons[b->count] = buttons;
  b->times[b->count++] = now;
  b->latest = buttons;
}

static inline uint32_t ios_input_poll(IOSInputBuffer *b, double now, uint32_t current) {
  while (b->count) {
    uint32_t buttons = b->buttons[0];
    double age = now - b->times[0];
    --b->count;
    memmove(b->buttons, b->buttons + 1, b->count * sizeof(*b->buttons));
    memmove(b->times, b->times + 1, b->count * sizeof(*b->times));
    if (age <= .120) return buttons;
  }
  return current;
}
