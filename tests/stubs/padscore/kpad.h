#pragma once
#include "wpad.h"
using KPADChan=WPADChan;
enum KPADError{KPAD_ERROR_OK=0};
struct Vec2{float x,y;};struct Vec3{float x,y,z;};
struct KPADStatus{
 uint32_t hold,trigger,release;uint8_t extensionType,format;Vec3 acc;Vec2 down,angle,pos;
 int posValid;float dist,accMagnitude;
 union {
 struct{uint32_t hold,trigger,release;Vec2 leftStick,rightStick;}pro;
 struct{uint32_t hold,trigger,release;Vec2 leftStick,rightStick;float leftTrigger,rightTrigger;}classic;
 };
 struct { Vec3 acc, angles, dirX, dirY, dirZ; } mplus;
};
inline int32_t real_KPADReadEx(KPADChan,KPADStatus*,uint32_t,KPADError*){return -101;}
inline int32_t real_KPADRead(KPADChan,KPADStatus*,uint32_t){return -101;}

using KPADMplsMode = WPADMplsMode;
enum KPADControlMplsStatus { KPAD_CONTROL_MPLS_STATUS_STARTED = 0, KPAD_CONTROL_MPLS_STATUS_FINISHED = 1 };
using KPADControlMplsCallback = void (*)(KPADChan, KPADControlMplsStatus);
inline void real_KPADSetControlMplsCallback(KPADChan, KPADControlMplsCallback) {}
inline void real_KPADEnableMpls(KPADChan, KPADMplsMode) {}
inline void real_KPADDisableMpls(KPADChan) {}
inline KPADMplsMode real_KPADGetMplsStatus(KPADChan) { return WPAD_MPLS_MODE_DISABLE; }
