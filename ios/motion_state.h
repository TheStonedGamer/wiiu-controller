#pragma once
#include <math.h>
#include <string.h>
typedef struct { double last; float q[4], rate[3]; } PhoneMotion;
static inline void phone_motion_reset(PhoneMotion *m) {
  memset(m, 0, sizeof(*m)); m->q[0] = 1;
}
// Screen coordinates: X right, Y top, Z out of the display. Rotation in quarter turns.
static inline void phone_motion_rotate(float x, float y, float z, unsigned rotation, float *out) {
  switch (rotation & 3) {
    case 1: out[0] = -y; out[1] = x; break;
    case 2: out[0] = -x; out[1] = -y; break;
    case 3: out[0] = y; out[1] = -x; break;
    default: out[0] = x; out[1] = y;
  }
  out[2] = z;
}
static inline void phone_motion_update(PhoneMotion *m, double time, float x, float y, float z, unsigned rotation) {
  float v[3]; phone_motion_rotate(x, y, z, rotation, v);
  double dt = m->last > 0 ? time - m->last : 0;
  m->last = time;
  for (int i = 0; i < 3; i++) m->rate[i] = v[i] / (2 * M_PI);
  if (dt <= 0 || dt > .1) return;
  float length = sqrtf(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
  if (length < .000001) return;
  float scale = sinf(length * dt / 2) / length;
  float d[4] = {cosf(length * dt / 2), v[0]*scale, v[1]*scale, v[2]*scale};
  float *a = m->q;
  float q[4] = {a[0]*d[0]-a[1]*d[1]-a[2]*d[2]-a[3]*d[3],
    a[0]*d[1]+a[1]*d[0]+a[2]*d[3]-a[3]*d[2],
    a[0]*d[2]-a[1]*d[3]+a[2]*d[0]+a[3]*d[1],
    a[0]*d[3]+a[1]*d[2]-a[2]*d[1]+a[3]*d[0]};
  float norm = sqrtf(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]+q[3]*q[3]);
  for (int i = 0; i < 4; i++) a[i] = q[i] / norm;
}
