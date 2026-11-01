#pragma once
struct OSMutex {};
inline void OSInitMutex(OSMutex*){}
inline void OSLockMutex(OSMutex*){}
inline void OSUnlockMutex(OSMutex*){}
