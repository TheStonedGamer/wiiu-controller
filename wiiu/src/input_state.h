#pragma once
#include <stddef.h>
#include <stdint.h>
#include <string.h>
// Independent sequence, owner and timeout state for the 3DS and Android.
struct ControllerInput {
  uint8_t packet[64] = {};
  uint64_t lastMs = 0;
  uint32_t peer = 0;
  uint16_t sequence = 0;
  uint32_t previous[2] = {};
  bool delivered[2] = {};
  bool sampled = false, mplusEnabled = true;
  bool active(uint64_t now) const { return sampled && now - lastMs < 250; }
  static int identify(const uint8_t *p, size_t size) {
    if (size == 20 && !memcmp(p, "3DWC", 4) && p[4] == 1 && p[5] <= 1)
      return 1;
    if (size == 32 && !memcmp(p, "ANWC", 4) && p[4] == 1 && (p[5] <= 3) &&
        !(p[22] & ~1)) {
      if (p[23] > 2)
        return -1;
      for (unsigned i = 24; i < 32; i++)
        if (p[i])
          return -1;
      return p[5];
    }
    if (size == 64 && !memcmp(p, "ANWC", 4) && p[4] == 2 && p[5] <= 3 &&
        !(p[22] & ~1) && p[23] == 3 && !(p[46] & ~1)) {
      for (unsigned i = 24; i < 32; i++) if (p[i]) return -1;
      for (unsigned i = 47; i < 64; i++) if (p[i]) return -1;
      if (p[46] & 1) {
        double norm = 0;
        for (unsigned i = 38; i < 46; i += 2) {
          double q = int16_t((p[i] << 8) | p[i + 1]) / 32767.0;
          norm += q*q;
        }
        if (norm < .8 || norm > 1.2) return -1;
      }
      return p[5];
    }
    return -1;
  }
  unsigned mode() const { return packet[0] == '3' ? (packet[5] ? 2 : 1) : packet[23]; }
  bool accept(const uint8_t *p, size_t size, uint32_t source, uint64_t now) {
    if (identify(p, size) < 0)
      return false;
    uint16_t next = uint16_t((p[6] << 8) | p[7]);
    if (active(now) &&
        (source != peer || p[0] != packet[0] || int16_t(next - sequence) <= 0))
      return false;
    unsigned nextMode = p[0] == '3' ? (p[5] ? 2 : 1) : p[23];
    if (sampled && (nextMode != mode() || source != peer)) {
      mplusEnabled = true;
      memset(previous, 0, sizeof(previous));
      memset(delivered, 0, sizeof(delivered));
    }
    memset(packet, 0, sizeof(packet));
    memcpy(packet, p, size);
    lastMs = now;
    peer = source;
    sequence = next;
    sampled = true;
    return true;
  }
  int16_t axis(unsigned offset) const {
    return int16_t((packet[offset] << 8) | packet[offset + 1]);
  }
  uint32_t buttons() const {
    return (uint32_t(packet[8]) << 24) | (uint32_t(packet[9]) << 16) |
           (uint32_t(packet[10]) << 8) | packet[11];
  }
};
