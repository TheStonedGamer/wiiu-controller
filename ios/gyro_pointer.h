#pragma once
#include <math.h>
#include <stdbool.h>
#include <string.h>
typedef struct { float center[4], x, y; double last; bool filtered; } GyroPointer;
static inline void gyro_pointer_center(GyroPointer *p, const float *q) {
  memcpy(p->center, q, sizeof(p->center)); p->x = p->y = 0; p->last = 0; p->filtered = false;
}
static inline void gyro_pointer_reset(GyroPointer *p) {
  const float q[4] = {1, 0, 0, 0}; gyro_pointer_center(p, q);
}
static inline void gyro_pointer_invalidate(GyroPointer *p) { p->last = 0; p->filtered = false; }
static inline bool gyro_pointer_update(GyroPointer *p, const float *q, double now, float sensitivity, float smoothing) {
  float a=p->center[0], b=-p->center[1], c=-p->center[2], d=-p->center[3];
  float w=a*q[0]-b*q[1]-c*q[2]-d*q[3], x=a*q[1]+b*q[0]+c*q[3]-d*q[2],
        y=a*q[2]-b*q[3]+c*q[0]+d*q[1], z=a*q[3]+b*q[2]-c*q[1]+d*q[0];
  float fx=2*(x*y-w*z), fy=1-2*(x*x+z*z), fz=2*(y*z+w*x);
  if (fy <= 0 || !isfinite(fx) || !isfinite(fy) || !isfinite(fz)) { gyro_pointer_invalidate(p); return false; }
  float tx=fmaxf(-1,fminf(1,atan2f(fx,fy)/(35*M_PI/180)*sensitivity));
  float ty=fmaxf(-1,fminf(1,-atan2f(fz,hypotf(fx,fy))/(25*M_PI/180)*sensitivity));
  double dt=fmax(0,fmin(.1,now-p->last));
  float alpha=!p->filtered || smoothing <= 0 ? 1 : 1-expf(-dt/smoothing);
  p->x+=(tx-p->x)*alpha; p->y+=(ty-p->y)*alpha; p->last=now; p->filtered=true;
  return true;
}
