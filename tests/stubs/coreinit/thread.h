#pragma once
#include <stdint.h>
struct OSThread{};
constexpr int OS_THREAD_ATTRIB_AFFINITY_CPU1=1;
inline bool OSCreateThread(OSThread*,int(*)(int,const char**),int,char*,void*,uint32_t,int,int){return false;}
inline void OSResumeThread(OSThread*){}
inline void OSJoinThread(OSThread*,int*){}
