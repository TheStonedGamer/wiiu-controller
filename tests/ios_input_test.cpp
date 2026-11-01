#include "../ios/input_buffer.h"
#include "../ios/motion_state.h"
#include "../ios/gyro_pointer.h"
#include <cassert>
#include <cstdio>
int main() {
  IOSInputBuffer b = {};
  ios_input_publish(&b, 1, 16);
  ios_input_publish(&b, 1.001, 0);
  assert(ios_input_poll(&b, 1.01, 0) == 16);
  assert(ios_input_poll(&b, 1.02, 0) == 0);
  ios_input_publish(&b, 2, 32);
  ios_input_publish(&b, 2.001, 0);
  assert(ios_input_poll(&b, 2.2, 0) == 0);
  for (unsigned i = 0; i < 100; i++) ios_input_publish(&b, 3, i);
  assert(b.count <= 32);
  memset(&b, 0, sizeof(b));
  assert(ios_input_poll(&b, 3, 0) == 0);
  PhoneMotion motion; phone_motion_reset(&motion);
  for (int r = 0; r < 4; r++) {
    float v[3]; phone_motion_rotate(1,2,3,r,v);
    float expected[4][3] = {{1,2,3},{-2,1,3},{-1,-2,3},{2,-1,3}};
    for (int i = 0; i < 3; i++) assert(v[i] == expected[r][i]);
  }
  for (int i = 0; i <= 100; i++) phone_motion_update(&motion, 1+i*.01, 0,0,M_PI/2,0);
  assert(fabs(motion.q[0] - sqrt(.5)) < .001);
  assert(fabs(motion.q[3] - sqrt(.5)) < .001);
  assert(fabs(motion.rate[2] - .25) < .001);
  phone_motion_reset(&motion); assert(motion.q[0] == 1 && motion.last == 0);
  GyroPointer pointer; gyro_pointer_reset(&pointer);
  float right[4] = {(float)cos(-17.5*M_PI/360),0,0,(float)sin(-17.5*M_PI/360)};
  assert(gyro_pointer_update(&pointer,right,1,1,0) && fabs(pointer.x-.5)<.001);
  float up[4] = {(float)cos(12.5*M_PI/360),(float)sin(12.5*M_PI/360),0,0};
  assert(gyro_pointer_update(&pointer,up,2,1,0) && fabs(pointer.y+.5)<.001);
  float roll[4] = {(float)cos(M_PI/8),0,(float)sin(M_PI/8),0};
  assert(gyro_pointer_update(&pointer,roll,3,1,0) && fabs(pointer.x)<.001 && fabs(pointer.y)<.001);
  gyro_pointer_center(&pointer,right);
  assert(gyro_pointer_update(&pointer,right,4,1,0) && fabs(pointer.x)<.001);
  gyro_pointer_reset(&pointer);
  const float identity[4] = {1,0,0,0};
  gyro_pointer_update(&pointer,identity,5,1,.1);
  gyro_pointer_update(&pointer,right,5.02,1,.1);
  assert(pointer.x>0 && pointer.x<.5);
  const float behind[4] = {0,0,0,1}; assert(!gyro_pointer_update(&pointer,behind,6,1,0));
  gyro_pointer_invalidate(&pointer); gyro_pointer_update(&pointer,identity,7,1,.1);
  assert(fabs(pointer.x)<.001);
  puts("iOS gyro pointer direction, roll isolation, recenter, smoothing and invalidation passed");
  puts("iOS quick tap, release, stale input, overflow and reset checks passed");
}
