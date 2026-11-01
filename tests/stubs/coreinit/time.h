#pragma once
#include <stdint.h>
static uint64_t testTime=1000;
inline uint64_t OSGetTime(){return testTime;}
inline uint64_t OSTicksToMilliseconds(uint64_t n){return n;}
inline uint64_t OSMillisecondsToTicks(uint64_t n){return n;}
inline void OSSleepTicks(uint64_t){}
