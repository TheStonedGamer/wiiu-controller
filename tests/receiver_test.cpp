#include "../wiiu/src/main.cpp"
#include <cassert>
#include <fstream>
#include <vector>
static std::vector<uint8_t> packet(const char *name) {
  std::ifstream f(name, std::ios::binary);
  assert(f);
  return {std::istreambuf_iterator<char>(f), {}};
}
static int motionEvents = 0;
static void motionCallback(KPADChan chan, KPADControlMplsStatus status) {
  // A callback may re-enter KPAD without deadlocking the receiver.
  assert(hook_KPADGetMplsStatus(chan) == WPAD_MPLS_MODE_MPLS_ONLY);
  motionEvents += status == KPAD_CONTROL_MPLS_STATUS_STARTED ? 1 : 10;
}
int main() {
  auto remote = packet("android/build/remote-packet.bin"),
       pro = packet("android/build/pro-packet.bin");
  uint8_t ds[20] = {'3', 'D', 'W', 'C', 1, 0};
  ds[11] = 16;
  ds[12] = 0;
  ds[13] = 156;
  assert(ControllerInput::identify(ds, 20) == 1);
  assert(ControllerInput::identify(remote.data(), 32) == 2);
  assert(ControllerInput::identify(remote.data(), 31) == -1);
  assert(ControllerInput::identify(remote.data(), 33) == -1);
  auto malformed = remote;
  malformed[23] = 3;
  assert(ControllerInput::identify(malformed.data(), 32) == -1);
  malformed = remote;
  malformed[31] = 1;
  assert(ControllerInput::identify(malformed.data(), 32) == -1);
  assert(inputs[1].accept(ds, 20, 1, testTime));
  assert(inputs[2].accept(remote.data(), 32, 2, testTime));
  assert(!inputs[2].accept(remote.data(), 32, 2, testTime + 1));
  auto wrapped = remote;
  wrapped[6] = wrapped[7] = 0;
  assert(inputs[2].accept(wrapped.data(), 32, 2, testTime + 1));
  wrapped[7] = 1;
  assert(!inputs[2].accept(wrapped.data(), 32, 9, testTime + 2));
  testTime += 1;
  KPADStatus out = {};
  KPADError error;
  WPADExtensionType type;
  assert(hook_KPADReadEx(WPAD_CHAN_0, &out, 1, &error) == -101);
  assert(static_cast<int>(hook_WPADProbe(WPAD_CHAN_0, &type)) == -101);
  assert(hook_KPADReadEx(WPAD_CHAN_1, &out, 1, &error) == 1 &&
         out.pro.hold == 16 && out.pro.trigger == 16 &&
         out.pro.leftStick.x == 1);
  assert(hook_KPADReadEx(WPAD_CHAN_2, &out, 1, &error) == 1 &&
         out.hold == 0x800 && out.extensionType == WPAD_EXT_CORE &&
         out.posValid == 1 && out.pos.x == 1 && out.pos.y == -1);
  assert(hook_WPADProbe(WPAD_CHAN_2, &type) == WPAD_ERROR_NONE &&
         type == WPAD_EXT_CORE);
  testTime += 300;
  assert(hook_KPADReadEx(WPAD_CHAN_2, &out, 1, &error) == 1 &&
         out.release == 0x800 && out.hold == 0 && out.posValid == -1);
  assert(hook_KPADReadEx(WPAD_CHAN_2, &out, 1, &error) == -101);
  assert(inputs[2].accept(pro.data(), 32, 9, testTime));
  assert(hook_WPADProbe(WPAD_CHAN_2, &type) == WPAD_ERROR_NONE &&
         type == WPAD_EXT_PRO_CONTROLLER);
  assert(hook_KPADReadEx(WPAD_CHAN_2, &out, 1, &error) == 1 &&
         out.pro.hold == 0x20010 && out.pro.leftStick.x == -1 &&
         out.pro.rightStick.x == .5f);
  assert(inputs[1].packet[11] ==
         16); // Phone input and mode changes never overwrite the 3DS.
  testTime += 300;
  assert(hook_KPADReadEx(WPAD_CHAN_2, &out, 1, &error) == 1 &&
         out.pro.hold == 0 && out.pro.release == 0x20010);
  // Analog-only disconnect still produces one neutral sample.
  pro[8] = pro[9] = pro[10] = pro[11] = 0;
  pro[7] = 1;
  assert(inputs[2].accept(pro.data(), 32, 9, testTime));
  assert(hook_KPADReadEx(WPAD_CHAN_2, &out, 1, &error) == 1);
  testTime += 300;
  assert(hook_KPADReadEx(WPAD_CHAN_2, &out, 1, &error) == 1 &&
         out.pro.leftStick.x == 0);
  assert(hook_KPADReadEx(WPAD_CHAN_2, &out, 1, &error) == -101);
  // P3 and P4 accept simultaneous senders and different controller types.
  auto fourth = packet("android/build/p4-packet.bin");
  fourth[11] = 32;
  assert(ControllerInput::identify(fourth.data(), 32) == 3);
  auto badSlot = fourth;
  badSlot[5] = 4;
  assert(ControllerInput::identify(badSlot.data(), 32) == -1);
  assert(inputs[3].accept(fourth.data(), 32, 42, testTime));
  assert(inputs[2].accept(remote.data(), 32, 9, testTime));
  assert(hook_WPADProbe(WPAD_CHAN_3, &type) == WPAD_ERROR_NONE &&
         type == WPAD_EXT_PRO_CONTROLLER);
  assert(hook_KPADReadEx(WPAD_CHAN_3, &out, 1, &error) == 1 &&
         out.pro.hold == 32);
  assert(hook_KPADReadEx(WPAD_CHAN_2, &out, 1, &error) == 1 &&
         out.hold == 0x800);
  assert(inputs[1].packet[11] == 16 && inputs[2].packet[11] == 0 &&
         inputs[3].packet[11] == 32);
  testTime += 200;
  fourth[7] = 3;
  assert(inputs[3].accept(fourth.data(), 32, 42, testTime));
  testTime += 100;
  assert(hook_KPADReadEx(WPAD_CHAN_2, &out, 1, &error) == 1 &&
         out.release == 0x800);
  assert(hook_KPADReadEx(WPAD_CHAN_3, &out, 1, &error) == 1 &&
         out.pro.hold == 32);
  assert(static_cast<int>(hook_WPADProbe(WPAD_CHAN_0, &type)) == -101);
  auto second = packet("android/build/p2-packet.bin");
  second[10] = 8; // Wii Remote A.
  assert(ControllerInput::identify(second.data(), 32) == 1);
  ControllerInput locked;
  assert(locked.accept(ds, 20, 1, testTime));
  assert(!locked.accept(second.data(), 32, 1, testTime + 1));
  assert(!locked.accept(second.data(), 32, 2, testTime + 1));
  testTime += 300;
  assert(inputs[1].accept(second.data(), 32, 2, testTime));
  assert(hook_WPADProbe(WPAD_CHAN_1, &type) == WPAD_ERROR_NONE &&
         type == WPAD_EXT_CORE);
  assert(hook_KPADReadEx(WPAD_CHAN_1, &out, 1, &error) == 1 &&
         out.hold == 0x800);
  assert(!inputs[1].accept(ds, 20, 1, testTime + 1));
  testTime += 300;
  ds[7] = 5;
  assert(inputs[1].accept(ds, 20, 1, testTime));
  assert(hook_WPADProbe(WPAD_CHAN_1, &type) == WPAD_ERROR_NONE &&
         type == WPAD_EXT_PRO_CONTROLLER);
  assert(inputs[1].packet[23] ==
         0); // A shorter 3DS packet clears old Android metadata.
  auto first = packet("android/build/p1-packet.bin");
  first[10] = 8;
  assert(ControllerInput::identify(first.data(), 32) == 0);
  assert(inputs[0].accept(first.data(), 32, 7, testTime));
  assert(hook_WPADProbe(WPAD_CHAN_0, &type) == WPAD_ERROR_NONE &&
         type == WPAD_EXT_CORE);
  assert(hook_KPADReadEx(WPAD_CHAN_0, &out, 1, &error) == 1 &&
         out.hold == 0x800);
  testPhysical[0] = true;
  assert(physicalConnected(WPAD_CHAN_0));
  assert(hook_KPADReadEx(WPAD_CHAN_0, &out, 1, &error) == -101);
  assert(hook_WPADProbe(WPAD_CHAN_0, &type) == WPAD_ERROR_NONE &&
         type == WPAD_EXT_CORE);
  testPhysical[0] = false;
  // Classic uses the extension structure and strips Pro-only stick clicks.
  testTime += 300;
  auto classic = packet("android/build/classic-packet.bin");
  assert(ControllerInput::identify(classic.data(), 32) == 2);
  assert(inputs[2].accept(classic.data(), 32, 9, testTime));
  assert(hook_WPADProbe(WPAD_CHAN_2, &type) == WPAD_ERROR_NONE && type == WPAD_EXT_CLASSIC);
  assert(hook_KPADReadEx(WPAD_CHAN_2, &out, 1, &error) == 1);
  assert(out.extensionType == WPAD_EXT_CLASSIC && out.format == WPAD_FMT_CLASSIC);
  assert(out.classic.hold == 0x2290 && out.classic.trigger == 0x2290);
  assert(out.classic.leftStick.x == -1 && out.classic.rightStick.y == -.5f);
  assert(out.classic.leftTrigger == 1 && out.classic.rightTrigger == 1);
  assert(out.hold == 0 && out.posValid == -1);
  testTime += 300;
  assert(hook_KPADReadEx(WPAD_CHAN_2, &out, 1, &error) == 1);
  assert(out.classic.release == 0x2290 && out.classic.hold == 0);
  assert(out.classic.leftTrigger == 0 && out.classic.leftStick.x == 0);
  assert(hook_KPADReadEx(WPAD_CHAN_2, &out, 1, &error) == -101);
  ds[5] = 1; ds[7]++;
  assert(ControllerInput::identify(ds, 20) == 1);
  assert(inputs[1].accept(ds, 20, 1, testTime));
  assert(hook_WPADProbe(WPAD_CHAN_1, &type) == WPAD_ERROR_NONE && type == WPAD_EXT_CLASSIC);
  assert(hook_KPADReadEx(WPAD_CHAN_1, &out, 1, &error) == 1 && out.classic.hold == 16);
  ds[5] = 2;
  assert(ControllerInput::identify(ds, 20) == -1);
  testTime += 300;
  auto motion = packet("android/build/motion-packet.bin");
  assert(ControllerInput::identify(motion.data(), motion.size()) == 3);
  assert(ControllerInput::identify(motion.data(), 63) == -1);
  auto invalidMotion = motion; invalidMotion[63] = 1;
  assert(ControllerInput::identify(invalidMotion.data(), 64) == -1);
  invalidMotion = motion; memset(invalidMotion.data() + 38, 0, 8);
  assert(ControllerInput::identify(invalidMotion.data(), 64) == -1);
  assert(inputs[3].accept(motion.data(), 64, 42, testTime));
  assert(hook_WPADProbe(WPAD_CHAN_3, &type) == WPAD_ERROR_NONE && type == WPAD_EXT_MPLUS);
  assert(hook_WPADIsMplsIntegrated(WPAD_CHAN_3) == 1);
  BOOL attached = false;
  assert(hook_WPADIsMplsAttached(WPAD_CHAN_3, &attached, nullptr) == 0 && attached);
  assert(hook_KPADReadEx(WPAD_CHAN_3, &out, 1, &error) == 1 && out.format == WPAD_FMT_MPLUS);
  assert(out.hold == 0x800 && out.mplus.acc.x == .25f && out.mplus.acc.y == -.5f);
  assert(std::fabs(out.mplus.dirX.y - 1) < .001 && std::fabs(out.mplus.angles.z - .25f) < .001);
  hook_KPADSetControlMplsCallback(WPAD_CHAN_3, motionCallback);
  hook_KPADDisableMpls(WPAD_CHAN_3);
  assert(hook_KPADGetMplsStatus(WPAD_CHAN_3) == WPAD_MPLS_MODE_DISABLE);
  hook_KPADEnableMpls(WPAD_CHAN_3, WPAD_MPLS_MODE_MPLS_ONLY);
  assert(motionEvents == 11 && hook_KPADGetMplsStatus(WPAD_CHAN_3) == WPAD_MPLS_MODE_MPLS_ONLY);
  testPhysical[3] = true;
  assert(hook_WPADIsMplsIntegrated(WPAD_CHAN_3) == -101);
  testPhysical[3] = false;
  testTime += 300;
  assert(hook_KPADReadEx(WPAD_CHAN_3, &out, 1, &error) == 1 && out.hold == 0);
  assert(out.mplus.acc.x == 0 && out.mplus.dirX.x == 1);
  // Discovery answers without claiming or modifying a controller slot.
  fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
  assert(fd >= 0);
  sockaddr_in local = {};
  local.sin_family = AF_INET;
  local.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
  local.sin_port = 0;
  assert(bind(fd, (sockaddr *)&local, sizeof(local)) == 0);
  socklen_t len = sizeof(local);
  assert(getsockname(fd, (sockaddr *)&local, &len) == 0);
  int probe = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
  assert(probe >= 0);
  assert(sendto(probe, "WUD?", 4, 0, (sockaddr *)&local, len) == 4);
  receivePackets();
  char ack[8] = {};
  assert(recv(probe, ack, sizeof(ack), MSG_DONTWAIT) == 4 &&
         !memcmp(ack, "WUD!", 4));
  close(probe);
  close(fd);
  fd = -1;
  puts("Receiver slot isolation, P1 passthrough, mode detection, sequence "
       "wrap, owner lock and disconnect checks passed");
}
