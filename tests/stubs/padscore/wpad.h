#pragma once
#include <stdint.h>
enum WPADChan { WPAD_CHAN_0, WPAD_CHAN_1, WPAD_CHAN_2, WPAD_CHAN_3 };
enum WPADExtensionType { WPAD_EXT_CORE = 0, WPAD_EXT_CLASSIC = 2, WPAD_EXT_MPLUS = 5, WPAD_EXT_PRO_CONTROLLER = 31 };
enum WPADError { WPAD_ERROR_NONE = 0 };
constexpr int WPAD_FMT_MPLUS = 16;
constexpr int WPAD_FMT_CLASSIC = 6;
constexpr int WPAD_FMT_PRO_CONTROLLER = 22, WPAD_FMT_CORE_ACC_DPD = 2;
static bool testPhysical[4] = {};
inline WPADError real_WPADProbe(WPADChan chan, WPADExtensionType *type) {
  if (testPhysical[int(chan)]) {
    if (type)
      *type = WPAD_EXT_CORE;
    return WPAD_ERROR_NONE;
  }
  return static_cast<WPADError>(-101);
}

using BOOL = int;
using WPADCallback = void (*)(WPADChan, WPADError);
enum WPADMplsMode { WPAD_MPLS_MODE_DISABLE = 0, WPAD_MPLS_MODE_MPLS_ONLY = 4, WPAD_MPLS_MODE_MPLS_NUNCHUK = 5, WPAD_MPLS_MODE_MPLS_CLASSIC = 7 };
inline int32_t real_WPADIsMplsIntegrated(WPADChan) { return -101; }
inline int32_t real_WPADIsMplsAttached(WPADChan, BOOL*, WPADCallback) { return -101; }
