#include "input_state.h"
#include <arpa/inet.h>
#include <cmath>
#include <coreinit/mutex.h>
#include <coreinit/thread.h>
#include <coreinit/time.h>
#include <cstring>
#include <padscore/kpad.h>
#include <padscore/wpad.h>
#include <sys/socket.h>
#include <unistd.h>
#include <wups.h>
WUPS_PLUGIN_NAME("Wii U Controller");
WUPS_PLUGIN_DESCRIPTION("3DS, Android, iOS: Remote, Pro and Classic");
WUPS_PLUGIN_VERSION("0.5.0");
WUPS_PLUGIN_AUTHOR("Brian");
WUPS_PLUGIN_LICENSE("MIT");
static OSMutex mutex;
struct Guard {
  Guard() { OSLockMutex(&mutex); }
  ~Guard() { OSUnlockMutex(&mutex); }
};
INITIALIZE_PLUGIN() { OSInitMutex(&mutex); }
static int fd = -1;
static OSThread receiver;
alignas(32) static unsigned char receiverStack[16384];
static bool running = false, threadCreated = false;
static ControllerInput inputs[4];
static KPADControlMplsCallback motionCallbacks[4] = {};
static uint64_t nowMs() { return OSTicksToMilliseconds(OSGetTime()); }
static int inputFor(KPADChan chan) {
  return chan >= WPAD_CHAN_0 && chan <= WPAD_CHAN_3 ? int(chan) : -1;
}
static bool physicalConnected(KPADChan chan);
static void receivePackets() {
  if (fd < 0)
    return;
  uint8_t p[65];
  for (int i = 0; i < 64; i++) {
    sockaddr_in source = {};
    socklen_t size = sizeof(source);
    int n =
        recvfrom(fd, p, sizeof(p), MSG_DONTWAIT, (sockaddr *)&source, &size);
    if (n < 0)
      break;
    if (n == 4 && !memcmp(p, "WUD?", 4)) {
      sendto(fd, "WUD!", 4, MSG_DONTWAIT, (sockaddr *)&source, size);
      continue;
    }
    int slot = ControllerInput::identify(p, size_t(n));
    if (slot < 0)
      continue;
    bool busy = physicalConnected(static_cast<KPADChan>(slot)) ||
                (inputs[slot].active(nowMs()) &&
                 (inputs[slot].peer != source.sin_addr.s_addr ||
                  inputs[slot].packet[0] != p[0]));
    if (busy) {
      sendto(fd, p[0] == '3' ? "3DWB" : "ANWB", 4, MSG_DONTWAIT,
             (sockaddr *)&source, size);
      continue;
    }
    if (inputs[slot].accept(p, size_t(n), source.sin_addr.s_addr, nowMs()))
      sendto(fd, p[0] == '3' ? "3DWA" : "ANWA", 4, MSG_DONTWAIT,
             (sockaddr *)&source, size);
  }
}
static int receiveLoop(int, const char **) {
  unsigned retry = 0;
  for (;;) {
    {
      Guard guard;
      if (!running)
        break;
      if (fd < 0 && retry++ % 100 == 0) {
        fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        if (fd >= 0) {
          sockaddr_in address = {};
          address.sin_family = AF_INET;
          address.sin_port = htons(26760);
          address.sin_addr.s_addr = INADDR_ANY;
          if (bind(fd, (sockaddr *)&address, sizeof(address)) < 0) {
            close(fd);
            fd = -1;
          }
        }
      }
      receivePackets();
    }
    OSSleepTicks(OSMillisecondsToTicks(10));
  }
  return 0;
}
ON_APPLICATION_START() {
  Guard guard;
  if (threadCreated)
    return;
  for (auto &input : inputs)
    input = ControllerInput{};
  memset(motionCallbacks, 0, sizeof(motionCallbacks));
  running = true;
  threadCreated = OSCreateThread(
      &receiver, receiveLoop, 0, nullptr, receiverStack + sizeof(receiverStack),
      sizeof(receiverStack), 16, OS_THREAD_ATTRIB_AFFINITY_CPU1);
  if (threadCreated)
    OSResumeThread(&receiver);
  else
    running = false;
}
ON_APPLICATION_ENDS() {
  {
    Guard guard;
    running = false;
  }
  if (threadCreated)
    OSJoinThread(&receiver, nullptr);
  Guard guard;
  threadCreated = false;
  if (fd >= 0)
    close(fd);
  fd = -1;
  for (auto &input : inputs)
    input = ControllerInput{};
}
static float normalized(int16_t value, float scale) {
  float v = value / scale;
  return v > 1 ? 1 : v < -1 ? -1 : v;
}
static int inject(KPADChan chan, KPADStatus *out, uint32_t count,
                  KPADError *err, int consumer) {
  int index = inputFor(chan);
  if (index < 0 || !out || !count || physicalConnected(chan))
    return -1;
  ControllerInput &input = inputs[index];
  bool active = input.active(nowMs());
  if (!active && !input.delivered[consumer])
    return -1;
  memset(out, 0, sizeof(*out));
  uint32_t buttons = active ? input.buttons() : 0;
  uint32_t pressed = buttons & ~input.previous[consumer],
           released = input.previous[consumer] & ~buttons;
  input.previous[consumer] = buttons;
  input.delivered[consumer] = active;
  if (input.mode() == 1) {
    out->extensionType = WPAD_EXT_PRO_CONTROLLER;
    out->format = WPAD_FMT_PRO_CONTROLLER;
    out->pro.hold = buttons;
    out->pro.trigger = pressed;
    out->pro.release = released;
    if (active) {
      out->pro.leftStick = {normalized(input.axis(12), 156),
                            normalized(input.axis(14), 156)};
      out->pro.rightStick = {normalized(input.axis(16), 156),
                             normalized(input.axis(18), 156)};
    }
  } else if (input.mode() == 2) {
    out->extensionType = WPAD_EXT_CLASSIC;
    out->format = WPAD_FMT_CLASSIC;
    out->posValid = -1;
    out->acc = {0, 0, 1};
    out->accMagnitude = 1;
    out->down = {0, -1};
    out->angle = {1, 0};
    out->classic.hold = buttons & 0xFEFF;
    out->classic.trigger = pressed & 0xFEFF;
    out->classic.release = released & 0xFEFF;
    if (active) {
      out->classic.leftStick = {normalized(input.axis(12), 156), normalized(input.axis(14), 156)};
      out->classic.rightStick = {normalized(input.axis(16), 156), normalized(input.axis(18), 156)};
      out->classic.leftTrigger = (buttons & 0x2000) ? 1.0f : 0.0f;
      out->classic.rightTrigger = (buttons & 0x200) ? 1.0f : 0.0f;
    }
  } else {
    out->extensionType = input.mode() == 3 && input.mplusEnabled ? WPAD_EXT_MPLUS : WPAD_EXT_CORE;
    out->format = input.mode() == 3 && input.mplusEnabled ? WPAD_FMT_MPLUS : WPAD_FMT_CORE_ACC_DPD;
    out->hold = buttons & 0x9F1F;
    out->trigger = pressed & 0x9F1F;
    out->release = released & 0x9F1F;
    out->acc = {0, 0, 1};
    out->down = {0, -1};
    out->angle = {1, 0};
    out->posValid = -1;
    if (active) {
      out->acc = {input.axis(16) / 1000.0f, input.axis(18) / 1000.0f,
                  input.axis(20) / 1000.0f};
      if (input.packet[22] & 1) {
        out->posValid = 1;
        out->pos = {normalized(input.axis(12), 156),
                    normalized(input.axis(14), 156)};
        out->dist = 1;
      }
    }
    if (input.mode() == 3 && input.mplusEnabled) {
      out->mplus.dirX = {1, 0, 0}; out->mplus.dirY = {0, 1, 0}; out->mplus.dirZ = {0, 0, 1};
      if (active && (input.packet[46] & 1)) {
        // The pinned WUT calls this field acc; newer WUT names it vel (turns/s).
        out->mplus.acc = {input.axis(32) / 1000.0f, input.axis(34) / 1000.0f, input.axis(36) / 1000.0f};
        float w = input.axis(38) / 32767.0f, x = input.axis(40) / 32767.0f,
              y = input.axis(42) / 32767.0f, z = input.axis(44) / 32767.0f;
        float norm = std::sqrt(w*w+x*x+y*y+z*z);
        w /= norm; x /= norm; y /= norm; z /= norm;
        out->mplus.dirX = {1-2*(y*y+z*z), 2*(x*y+w*z), 2*(x*z-w*y)};
        out->mplus.dirY = {2*(x*y-w*z), 1-2*(x*x+z*z), 2*(y*z+w*x)};
        out->mplus.dirZ = {2*(x*z+w*y), 2*(y*z-w*x), 1-2*(x*x+y*y)};
        constexpr float turn = 6.28318530718f;
        float pitch = 2*(w*y-z*x);
        out->mplus.angles = {std::atan2(2*(w*x+y*z), 1-2*(x*x+y*y))/turn,
          std::asin(pitch > 1 ? 1 : pitch < -1 ? -1 : pitch)/turn,
          std::atan2(2*(w*z+x*y), 1-2*(y*y+z*z))/turn};
      }
    }
    out->accMagnitude =
        std::sqrt(out->acc.x * out->acc.x + out->acc.y * out->acc.y +
                  out->acc.z * out->acc.z);
  }
  if (err)
    *err = KPAD_ERROR_OK;
  return 1;
}
DECL_FUNCTION(int32_t, KPADReadEx, KPADChan chan, KPADStatus *out,
              uint32_t count, KPADError *err) {
  {
    Guard guard;
    int n = inject(chan, out, count, err, 0);
    if (n >= 0)
      return n;
  }
  return real_KPADReadEx(chan, out, count, err);
}
DECL_FUNCTION(int32_t, KPADRead, KPADChan chan, KPADStatus *out,
              uint32_t count) {
  {
    Guard guard;
    int n = inject(chan, out, count, nullptr, 1);
    if (n >= 0)
      return n;
  }
  return real_KPADRead(chan, out, count);
}
DECL_FUNCTION(WPADError, WPADProbe, WPADChan chan, WPADExtensionType *type) {
  WPADError physical = real_WPADProbe(chan, type);
  if (physical == WPAD_ERROR_NONE)
    return physical;
  {
    Guard guard;
    int index = inputFor(chan);
    if (index >= 0 && inputs[index].active(nowMs())) {
      if (type)
        *type = inputs[index].mode() == 1 ? WPAD_EXT_PRO_CONTROLLER
                    : inputs[index].mode() == 2 ? WPAD_EXT_CLASSIC
                    : inputs[index].mode() == 3 && inputs[index].mplusEnabled ? WPAD_EXT_MPLUS : WPAD_EXT_CORE;
      return WPAD_ERROR_NONE;
    }
  }
  return physical;
}
static bool physicalConnected(KPADChan chan) {
  WPADExtensionType type;
  return real_WPADProbe(chan, &type) == WPAD_ERROR_NONE;
}
WUPS_MUST_REPLACE(KPADReadEx, WUPS_LOADER_LIBRARY_PADSCORE, KPADReadEx);
WUPS_MUST_REPLACE(KPADRead, WUPS_LOADER_LIBRARY_PADSCORE, KPADRead);
WUPS_MUST_REPLACE(WPADProbe, WUPS_LOADER_LIBRARY_PADSCORE, WPADProbe);

// Keep callbacks outside the state mutex: games may call back into KPAD.
DECL_FUNCTION(void, KPADSetControlMplsCallback, KPADChan chan, KPADControlMplsCallback callback) {
  { Guard guard; int index = inputFor(chan); if (index >= 0) motionCallbacks[index] = callback; }
  real_KPADSetControlMplsCallback(chan, callback);
}
DECL_FUNCTION(void, KPADEnableMpls, KPADChan chan, KPADMplsMode mode) {
  KPADControlMplsCallback callback = nullptr; bool handled = false;
  { Guard guard; int index = inputFor(chan);
    if (index >= 0 && !physicalConnected(chan) && inputs[index].active(nowMs()) && inputs[index].mode() == 3) {
      inputs[index].mplusEnabled = true; callback = motionCallbacks[index]; handled = true;
    }
  }
  if (!handled) { real_KPADEnableMpls(chan, mode); return; }
  if (callback) { callback(chan, KPAD_CONTROL_MPLS_STATUS_STARTED); callback(chan, KPAD_CONTROL_MPLS_STATUS_FINISHED); }
}
DECL_FUNCTION(void, KPADDisableMpls, KPADChan chan) {
  { Guard guard; int index = inputFor(chan);
    if (index >= 0 && !physicalConnected(chan) && inputs[index].active(nowMs()) && inputs[index].mode() == 3) {
      inputs[index].mplusEnabled = false; return;
    }
  }
  real_KPADDisableMpls(chan);
}
DECL_FUNCTION(KPADMplsMode, KPADGetMplsStatus, KPADChan chan) {
  { Guard guard; int index = inputFor(chan);
    if (index >= 0 && !physicalConnected(chan) && inputs[index].active(nowMs()))
      return inputs[index].mode() == 3 && inputs[index].mplusEnabled ? WPAD_MPLS_MODE_MPLS_ONLY : WPAD_MPLS_MODE_DISABLE;
  }
  return real_KPADGetMplsStatus(chan);
}
DECL_FUNCTION(int32_t, WPADIsMplsIntegrated, WPADChan chan) {
  { Guard guard; int index = inputFor(chan);
    if (index >= 0 && !physicalConnected(chan) && inputs[index].active(nowMs())) return inputs[index].mode() == 3 ? 1 : 0;
  }
  return real_WPADIsMplsIntegrated(chan);
}
DECL_FUNCTION(int32_t, WPADIsMplsAttached, WPADChan chan, BOOL *attached, WPADCallback callback) {
  bool handled = false, motion = false;
  { Guard guard; int index = inputFor(chan);
    if (index >= 0 && !physicalConnected(chan) && inputs[index].active(nowMs())) { handled = true; motion = inputs[index].mode() == 3; }
  }
  if (!handled) return real_WPADIsMplsAttached(chan, attached, callback);
  if (attached) *attached = motion;
  if (callback) callback(chan, WPAD_ERROR_NONE);
  return WPAD_ERROR_NONE;
}
WUPS_MUST_REPLACE(KPADSetControlMplsCallback, WUPS_LOADER_LIBRARY_PADSCORE, KPADSetControlMplsCallback);
WUPS_MUST_REPLACE(KPADEnableMpls, WUPS_LOADER_LIBRARY_PADSCORE, KPADEnableMpls);
WUPS_MUST_REPLACE(KPADDisableMpls, WUPS_LOADER_LIBRARY_PADSCORE, KPADDisableMpls);
WUPS_MUST_REPLACE(KPADGetMplsStatus, WUPS_LOADER_LIBRARY_PADSCORE, KPADGetMplsStatus);
WUPS_MUST_REPLACE(WPADIsMplsIntegrated, WUPS_LOADER_LIBRARY_PADSCORE, WPADIsMplsIntegrated);
WUPS_MUST_REPLACE(WPADIsMplsAttached, WUPS_LOADER_LIBRARY_PADSCORE, WPADIsMplsAttached);
