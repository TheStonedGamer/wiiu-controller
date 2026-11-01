#include "controls.h"
#include <3ds.h>
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <malloc.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>
static void word(unsigned char *p, unsigned v) {
  p[0] = v >> 8;
  p[1] = v;
}
static void stage(const char *message) {
  FILE *log = fopen("sdmc:/3ds-wiiu-controller.log", "a");
  if (log) {
    fprintf(log, "%s\n", message);
    fclose(log);
  }
}
static bool classic = false;
static bool chooseAddress(char *ip, size_t size) {
  unsigned octets[4] = {192, 168, 1, 100};
  unsigned selected = 0;
  while (aptMainLoop()) {
    hidScanInput();
    u32 keys = hidKeysDown();
    if (keys & KEY_X) classic = !classic;
    if (keys & KEY_B)
      return false;
    if (keys & KEY_DLEFT)
      selected = (selected + 3) % 4;
    if (keys & KEY_DRIGHT)
      selected = (selected + 1) % 4;
    if (keys & KEY_DUP)
      octets[selected] = (octets[selected] + 1) % 256;
    if (keys & KEY_DDOWN)
      octets[selected] = (octets[selected] + 255) % 256;
    snprintf(ip, size, "%u.%u.%u.%u", octets[0], octets[1], octets[2],
             octets[3]);
    printf("\x1b[1;1H3DS Wii U Controller 0.5.1\n\nWii U: %-16s\nEditing octet "
           "%u\n\nLeft/right: select octet\nUp/down: change value\nA: connect  "
           " B: exit\nX: mode (%s)\n",
           ip, selected + 1, classic ? "Classic" : "Pro");
    if (keys & KEY_A)
      return true;
    gfxFlushBuffers();
    gfxSwapBuffers();
    gspWaitForVBlank();
  }
  return false;
}
int main(void) {
  stage("0.5.1: entered main");
  gfxInitDefault();
  stage("graphics initialized");
  consoleInit(GFX_TOP, NULL);
  gfxSetScreenFormat(GFX_BOTTOM, GSP_BGR8_OES);
  stage("console initialized; address editor");
  char ip[64] = "";
  if (!chooseAddress(ip, sizeof(ip))) {
    gfxExit();
    return 0;
  }
  consoleClear();
  stage("address selected; allocating socket buffer");
  void *mem = memalign(0x1000, 0x100000);
  int fd = -1;
  bool socReady = false;
  struct sockaddr_in dest = {0};
  dest.sin_family = AF_INET;
  dest.sin_port = htons(26760);
  Result networkResult = -1;
  if (mem && inet_pton(AF_INET, ip, &dest.sin_addr) == 1) {
    stage("calling socInit");
    networkResult = socInit(mem, 0x100000);
  }
  if (R_FAILED(networkResult)) {
    printf("Network setup failed: 0x%08lX\nPress START to exit.\n",
           (unsigned long)networkResult);
    stage("network setup failed");
  } else {
    socReady = true;
    fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd >= 0 && fcntl(fd, F_SETFL, O_NONBLOCK) < 0) {
      printf("Nonblocking setup failed: %d\n", errno);
      close(fd);
      fd = -1;
    }
    if (fd < 0)
      printf("Socket failed: %d\nPress START to exit.\n", errno);
    stage(fd < 0 ? "socket failed" : "socket ready; input loop");
  }
  consoleClear();
  printf("\x1b[36;1m\n  3DS / WII U CONTROLLER\x1b[0m\n\n  Target: %s:26760\n  "
         "Player: 2 | %s Controller\n\n  START + SELECT to exit\n",
         ip, classic ? "Classic" : "Pro");
  TouchControls controls = {0};
  unsigned seq = 0;
  u64 lastAck = 0;
  unsigned frames = 0, sent = 0, replies = 0;
  int sendError = 0, receiveError = 0;
  while (aptMainLoop()) {
    hidScanInput();
    u32 k = hidKeysHeld();
    if ((k & (KEY_START | KEY_SELECT)) == (KEY_START | KEY_SELECT) ||
        (fd < 0 && (k & KEY_START)))
      break;
    circlePosition l = {0}, r = {0};
    hidCircleRead(&l);
    hidCstickRead(&r);
    unsigned b = 0;
    const u32 keys[] = {KEY_DUP,    KEY_DLEFT, KEY_ZR,    KEY_X,     KEY_A,
                        KEY_Y,      KEY_B,     KEY_ZL,    KEY_R,     KEY_START,
                        KEY_SELECT, KEY_L,     KEY_DDOWN, KEY_DRIGHT};
    const unsigned bits[] = {1,   2,   4,    8,    16,   32,    64,
                             128, 512, 1024, 4096, 8192, 16384, 32768};
    for (unsigned i = 0; i < 14; i++)
      if (k & keys[i])
        b |= bits[i];
    touchPosition touch = {0};
    if (k & KEY_TOUCH)
      hidTouchRead(&touch);
    controlsUpdate(&controls, (k & KEY_TOUCH) != 0, touch.px, touch.py);
    if (controls.swap) {
      unsigned shoulders = b & (4 | 128 | 512 | 8192);
      b &= ~(4 | 128 | 512 | 8192);
      if (shoulders & 8192)
        b |= 128;
      if (shoulders & 512)
        b |= 4;
      if (shoulders & 128)
        b |= 8192;
      if (shoulders & 4)
        b |= 512;
    }
    if (!classic) b |= controls.buttons;
    if (controls.capture == 4) {
      r.dx = controls.rightX;
      r.dy = controls.rightY;
    }
    unsigned char packet[20] = {'3', 'D', 'W', 'C', 1, 0};
    packet[5] = classic ? 1 : 0;
    word(packet + 6, seq++);
    packet[8] = b >> 24;
    packet[9] = b >> 16;
    packet[10] = b >> 8;
    packet[11] = b;
    word(packet + 12, l.dx);
    word(packet + 14, l.dy);
    word(packet + 16, r.dx);
    word(packet + 18, r.dy);
    if (fd >= 0) {
      int n = sendto(fd, packet, sizeof(packet), 0, (struct sockaddr *)&dest,
                     sizeof(dest));
      if (n == sizeof(packet)) {
        sent++;
        sendError = 0;
      } else
        sendError = errno;
      char ack[5];
      for (unsigned i = 0; i < 64; i++) {
        int got = recv(fd, ack, sizeof(ack), 0);
        if (got < 0) {
          if (errno != EAGAIN && errno != EWOULDBLOCK)
            receiveError = errno;
          break;
        }
        if (got == 4 && !memcmp(ack, "3DWA", 4)) {
          lastAck = osGetTime();
          replies++;
          receiveError = 0;
        }
      }
      if (++frames % 30 == 0) {
        printf("\x1b[10;1HStatus: %-20s\nSent: %u  Replies: %u       \nSend "
               "errno: %d  Receive errno: %d      \n",
               lastAck && osGetTime() - lastAck < 1000 ? "Wii U receiving"
                                                       : "Waiting for Wii U",
               sent, replies, sendError, receiveError);
      }
    }
    printf("\x1b[15;1H  Shoulders: %-25s\n\n  A/B/X/Y : same buttons\n  START  "
           " : Plus\n  SELECT  : Minus\n  Touch   : right stick / L3 / R3\n",
           controls.swap ? "L = ZL | R = ZR" : "L = L  | R = R");
    controlsDraw(&controls, lastAck && osGetTime() - lastAck < 1000);
    gfxFlushBuffers();
    gfxSwapBuffers();
    gspWaitForVBlank();
  }
  if (fd >= 0) {
    unsigned char p[20] = {'3', 'D', 'W', 'C', 1, 0};
    p[5] = classic ? 1 : 0;
    word(p + 6, seq);
    sendto(fd, p, 20, 0, (struct sockaddr *)&dest, sizeof(dest));
    close(fd);
  }
  if (socReady)
    socExit();
  stage("normal shutdown");
  free(mem);
  gfxExit();
  return 0;
}
