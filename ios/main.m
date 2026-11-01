#import <QuartzCore/QuartzCore.h>
#import <UIKit/UIKit.h>
#import <CoreMotion/CoreMotion.h>
#include "input_buffer.h"
#include "motion_state.h"
#include "gyro_pointer.h"
#import <arpa/inet.h>
#import <fcntl.h>
#import <math.h>
#import <net/if.h>
#import <sys/socket.h>
#import <unistd.h>

static double clockNow(void) { return CACurrentMediaTime(); }
static void word(uint8_t *p, int v) {
  p[0] = (uint16_t)v >> 8;
  p[1] = v;
}
static int stick(float v) { return (int)lrintf(fmaxf(-156, fminf(156, v))); }
static UIColor *rgb(unsigned value) {
  return [UIColor colorWithRed:((value >> 16) & 255) / 255.0
                         green:((value >> 8) & 255) / 255.0
                          blue:(value & 255) / 255.0
                         alpha:1];
}
@interface RemoteView : UIView <UIDocumentPickerDelegate> {
  IOSInputBuffer edges;
  PhoneMotion motionState;
  GyroPointer aimState;
}
@property(nonatomic, strong) NSMutableArray *controls;
@property(nonatomic, strong) NSMutableDictionary *fingers;
@property(nonatomic) BOOL pro, classic, motionPlus, editing, enabled, gyroAim, aimValid;
@property(nonatomic) float aimSensitivity, aimSmoothing;
@property(nonatomic) int aimX, aimY;
@property(nonatomic) NSInteger player;
@property(nonatomic, strong) NSString *host, *status;
@property(nonatomic) int fd;
@property(nonatomic) uint16_t sequence;
@property(nonatomic) double lastReply, opened, pauseUntil;
@property(nonatomic) float sensitivity, deadzone;
@property(nonatomic) BOOL invert, haptics, snapGrid, showLabels, showStats, tilt;
@property(nonatomic) float opacity;
@property(nonatomic) NSInteger accent, remoteOrientation;
@property(nonatomic) unsigned lastRotation;
@property(nonatomic) unsigned long sent, replies;
@property(nonatomic, strong) CMMotionManager *motion;
@property(nonatomic, strong) NSMutableDictionary *selected;
@property(nonatomic) CGPoint dragStart;
@property(nonatomic) CGRect dragRect;
@property(nonatomic) BOOL resizing;
@property(nonatomic, strong) NSTimer *timer;
- (void)settings;
- (void)releaseAndClose;
- (void)layoutControls;
@end
@interface RemoteController : UIViewController
@property(nonatomic, strong) RemoteView *pad;
@end
@implementation RemoteController
- (void)loadView {
  self.pad = [[RemoteView alloc] initWithFrame:UIScreen.mainScreen.bounds];
  self.view = self.pad;
}
- (BOOL)prefersStatusBarHidden {
  return YES;
}
- (UIInterfaceOrientationMask)supportedInterfaceOrientations {
  if (self.pad.editing) return (UIInterfaceOrientationMask)(1 << UIApplication.sharedApplication.statusBarOrientation);
  if (self.pad.pro || self.pad.remoteOrientation == 2) return UIInterfaceOrientationMaskLandscape;
  return self.pad.remoteOrientation == 1 ? UIInterfaceOrientationMaskPortrait : UIInterfaceOrientationMaskAll;
}
- (void)viewDidAppear:(BOOL)animated {
  [super viewDidAppear:animated];
  static BOOL initial = NO;
  if (!initial) {
    initial = YES;
    [self.pad settings];
  }
}
@end
@implementation RemoteView
- (instancetype)initWithFrame:(CGRect)frame {
  if ((self = [super initWithFrame:frame])) {
    self.multipleTouchEnabled = YES;
    self.backgroundColor = rgb(0x0E1624);
    self.fd = -1;
    NSUserDefaults *prefs = NSUserDefaults.standardUserDefaults;
    [prefs registerDefaults:@{
      @"player" : @3,
      @"pro" : @YES,
      @"host" : @"192.168.1.100",
      @"sensitivity" : @1,
      @"gyroAim": @YES, @"aimSensitivity": @1, @"aimSmoothing": @0.06, @"deadzone" : @0.08, @"haptics": @YES, @"snapGrid": @YES, @"showLabels": @YES, @"opacity": @1
    }];
    self.player = MAX(1, MIN(4, [prefs integerForKey:@"player"]));
    self.pro = [prefs boolForKey:@"pro"];
    self.classic = [prefs boolForKey:@"classic"];
    self.host = [prefs stringForKey:@"host"];
    self.status = @"Open settings to connect";
    self.sensitivity = [prefs floatForKey:@"sensitivity"];
    self.deadzone = [prefs floatForKey:@"deadzone"];
    self.invert = [prefs boolForKey:@"invert"];
    self.haptics = [prefs boolForKey:@"haptics"];
    self.snapGrid = [prefs boolForKey:@"snapGrid"];
    self.showLabels = [prefs boolForKey:@"showLabels"];
    self.showStats = [prefs boolForKey:@"showStats"];
    self.opacity = MAX(.25, MIN(1, [prefs floatForKey:@"opacity"]));
    self.accent = [prefs integerForKey:@"accent"];
    self.motion = [CMMotionManager new];
    self.motionPlus = [prefs boolForKey:@"motionPlus"] && self.motion.gyroAvailable;
    self.gyroAim = [prefs boolForKey:@"gyroAim"] && self.motion.gyroAvailable;
    self.aimSensitivity = MAX(.25, MIN(3, [prefs floatForKey:@"aimSensitivity"]));
    self.aimSmoothing = MAX(0, MIN(.2, [prefs floatForKey:@"aimSmoothing"]));
    self.remoteOrientation = [prefs integerForKey:@"remoteOrientation"];
    [self resetMotion];
    self.controls = [NSMutableArray array];
    self.fingers = [NSMutableDictionary dictionary];
    [self layoutControls];
    self.timer = [NSTimer scheduledTimerWithTimeInterval:1.0 / 60
                                                  target:self
                                                selector:@selector(tick)
                                                userInfo:nil
                                                 repeats:YES];
    [[NSRunLoop mainRunLoop] addTimer:self.timer forMode:NSRunLoopCommonModes];
  }
  return self;
}
- (void)add:(NSString *)title
          x:(float)x
          y:(float)y
          w:(float)w
          h:(float)h
        bit:(unsigned)bit
     action:(int)action {
  NSString *key = bit ? [NSString stringWithFormat:@"button:%u", bit]
                      : [NSString stringWithFormat:@"action:%d", action];
  [self.controls addObject:[@{
                   @"label" : title,
                   @"rect" : [NSValue valueWithCGRect:CGRectMake(x, y, w, h)],
                   @"bit" : @(bit),
                   @"action" : @(action),
                   @"id" : key
                 } mutableCopy]];
}
- (BOOL)editable:(NSDictionary *)control {
  int action = [control[@"action"] intValue];
  return action != 10 && action != 11 && action < 20;
}
- (NSString *)layoutKey {
  return self.classic ? @"ios.layout.classic" : self.pro ? @"ios.layout.pro" : [self portrait] ? @"ios.layout.remotePortrait" : @"ios.layout.remote";
}
- (void)dpadX:(float)x
            y:(float)y
           up:(unsigned)up
         left:(unsigned)left
         down:(unsigned)down
        right:(unsigned)right {
  [self add:@"UP" x:x + 60 y:y w:56 h:56 bit:up action:0];
  [self add:@"LEFT" x:x y:y + 60 w:56 h:56 bit:left action:0];
  [self add:@"DOWN" x:x + 60 y:y + 120 w:56 h:56 bit:down action:0];
  [self add:@"RIGHT" x:x + 120 y:y + 60 w:56 h:56 bit:right action:0];
}
- (BOOL)portrait { return !self.pro && self.bounds.size.height > self.bounds.size.width; }
- (float)canvasW { return [self portrait] ? 460 : 960; }
- (float)canvasH { return [self portrait] ? 960 : 460; }
- (unsigned)rotation {
  switch (UIApplication.sharedApplication.statusBarOrientation) {
    case UIInterfaceOrientationLandscapeLeft: return 1;
    case UIInterfaceOrientationPortraitUpsideDown: return 2;
    case UIInterfaceOrientationLandscapeRight: return 3;
    default: return 0;
  }
}
- (void)layoutSubviews {
  [super layoutSubviews];
  static CGSize previous = {0, 0};
  if (!CGSizeEqualToSize(previous, self.bounds.size)) {
    previous = self.bounds.size; [self clear]; [self resetMotion]; [self layoutControls];
  }
}
- (void)remoteDpadX:(float)x y:(float)y {
  const unsigned bits[4][4] = {{8,1,4,2}, {2,8,1,4}, {4,2,8,1}, {1,4,2,8}};
  const unsigned *d = bits[[self rotation]];
  [self dpadX:x y:y up:d[0] left:d[1] down:d[2] right:d[3]];
  for (NSUInteger i = self.controls.count - 4; i < self.controls.count; i++) self.controls[i][@"id"] = [@"dpad:" stringByAppendingString:self.controls[i][@"label"]];
}
- (void)startMotion {
  [self.motion stopGyroUpdates]; [self.motion stopAccelerometerUpdates];
  if (self.pro || !self.enabled) return;
  if (self.tilt || self.motionPlus) { self.motion.accelerometerUpdateInterval = 1.0/60; [self.motion startAccelerometerUpdates]; }
  if (self.motionPlus || self.gyroAim) { self.motion.gyroUpdateInterval = 1.0/100; [self.motion startGyroUpdates]; }
}
- (void)layoutControls {
  [self.controls removeAllObjects];
  if ([self portrait]) {
    if (self.editing) { [self add:@"RESET" x:12 y:12 w:130 h:40 bit:0 action:22]; [self add:@"CANCEL" x:158 y:12 w:130 h:40 bit:0 action:21]; [self add:@"SAVE" x:304 y:12 w:144 h:40 bit:0 action:20]; }
    else { [self add:@"MODE" x:230 y:12 w:100 h:40 bit:0 action:10]; [self add:@"SETUP" x:340 y:12 w:108 h:40 bit:0 action:11]; }
  } else if (self.editing) {
    [self add:@"RESET" x:500 y:12 w:130 h:40 bit:0 action:22];
    [self add:@"CANCEL" x:650 y:12 w:122 h:40 bit:0 action:21];
    [self add:@"SAVE" x:784 y:12 w:140 h:40 bit:0 action:20];
  } else {
    [self add:@"MODE" x:650 y:12 w:122 h:40 bit:0 action:10];
    [self add:@"SETUP" x:784 y:12 w:140 h:40 bit:0 action:11];
  }
  if (self.pro) {
    [self add:@"ZL" x:20 y:68 w:145 h:46 bit:128 action:0];
    [self add:@"L" x:177 y:68 w:145 h:46 bit:8192 action:0];
    [self add:@"R" x:638 y:68 w:145 h:46 bit:512 action:0];
    [self add:@"ZR" x:795 y:68 w:145 h:46 bit:4 action:0];
    if (!self.classic) [self add:@"L3" x:380 y:384 w:86 h:40 bit:131072 action:0];
    if (!self.classic) [self add:@"R3" x:482 y:384 w:86 h:40 bit:65536 action:0];
    [self add:@"LEFT STICK" x:24 y:188 w:222 h:222 bit:0 action:1];
    [self add:@"RIGHT STICK" x:704 y:188 w:222 h:222 bit:0 action:2];
    [self dpadX:286 y:202 up:1 left:2 down:16384 right:32768];
    [self add:@"X" x:573 y:180 w:60 h:60 bit:8 action:0];
    [self add:@"Y" x:517 y:244 w:60 h:60 bit:32 action:0];
    [self add:@"A" x:629 y:244 w:60 h:60 bit:16 action:0];
    [self add:@"B" x:573 y:308 w:60 h:60 bit:64 action:0];
    [self add:@"-" x:380 y:122 w:65 h:44 bit:4096 action:0];
    [self add:@"HOME" x:454 y:122 w:84 h:44 bit:2048 action:0];
    [self add:@"+" x:547 y:122 w:65 h:44 bit:1024 action:0];
  } else if ([self portrait]) {
    [self remoteDpadX:142 y:140];
    [self add:@"A" x:65 y:340 w:140 h:140 bit:2048 action:0]; [self add:@"B" x:255 y:340 w:140 h:140 bit:1024 action:0];
    [self add:@"POINTER" x:40 y:540 w:380 h:190 bit:0 action:3];
    [self add:self.gyroAim ? @"AIM ON" : @"AIM OFF" x:40 y:490 w:180 h:40 bit:0 action:13];
    [self add:@"CENTER" x:240 y:490 w:180 h:40 bit:0 action:14];
    [self add:@"-" x:70 y:750 w:80 h:50 bit:4096 action:0]; [self add:@"HOME" x:170 y:750 w:120 h:50 bit:32768 action:0]; [self add:@"+" x:310 y:750 w:80 h:50 bit:16 action:0];
    [self add:@"1" x:85 y:825 w:80 h:80 bit:512 action:0]; [self add:@"2" x:285 y:825 w:80 h:80 bit:256 action:0];
    [self add:@"SHAKE" x:12 y:125 w:110 h:50 bit:0 action:4]; [self add:self.tilt ? @"TILT ON" : @"TILT OFF" x:338 y:125 w:110 h:50 bit:0 action:12];
  } else {
    [self remoteDpadX:40 y:204];
    [self add:@"POINTER" x:286 y:188 w:346 h:216 bit:0 action:3];
    [self add:self.gyroAim ? @"AIM ON" : @"AIM OFF" x:286 y:142 w:160 h:40 bit:0 action:13];
    [self add:@"CENTER" x:458 y:142 w:174 h:40 bit:0 action:14];
    [self add:@"A" x:722 y:178 w:86 h:86 bit:2048 action:0];
    [self add:@"B" x:822 y:244 w:76 h:76 bit:1024 action:0];
    [self add:@"1" x:690 y:322 w:66 h:66 bit:512 action:0];
    [self add:@"2" x:772 y:344 w:66 h:66 bit:256 action:0];
    [self add:@"-" x:284 y:90 w:70 h:46 bit:4096 action:0];
    [self add:@"HOME" x:366 y:90 w:100 h:46 bit:32768 action:0];
    [self add:@"+" x:478 y:90 w:70 h:46 bit:16 action:0];
    [self add:@"SHAKE" x:648 y:90 w:140 h:46 bit:0 action:4];
    [self add:self.tilt ? @"TILT ON" : @"TILT OFF" x:800 y:90 w:140 h:46 bit:0 action:12];
  }
  NSDictionary *saved =
      [NSUserDefaults.standardUserDefaults dictionaryForKey:[self layoutKey]];
  for (NSMutableDictionary *control in self.controls) {
    NSArray *v = saved[control[@"id"]];
    if ([self editable:control] && v.count == 4) {
      float w = MAX(40, MIN([self canvasW] - 60, [v[2] floatValue])),
            h = MAX(40, MIN([self canvasH] - ([self portrait] ? 152 : 100), [v[3] floatValue]));
      float x = MAX(8, MIN([self canvasW] - 8 - w, [v[0] floatValue])),
            y = MAX([self portrait] ? 120 : 68, MIN([self canvasH] - 32 - h, [v[1] floatValue]));
      control[@"rect"] = [NSValue valueWithCGRect:CGRectMake(x, y, w, h)];
    }
  }
  [self setNeedsDisplay];
}
- (float)scale {
  return MIN(self.bounds.size.width / [self canvasW], self.bounds.size.height / [self canvasH]);
}
- (CGPoint)logical:(CGPoint)p {
  float s = [self scale];
  return CGPointMake((p.x - (self.bounds.size.width - [self canvasW] * s) / 2) / s,
                     (p.y - (self.bounds.size.height - [self canvasH] * s) / 2) / s);
}
- (void)text:(NSString *)text
           x:(float)x
           y:(float)y
        size:(float)size
       color:(UIColor *)color {
  [text drawAtPoint:CGPointMake(x, y)
      withAttributes:@{
        NSFontAttributeName : [UIFont systemFontOfSize:size
                                                weight:UIFontWeightMedium],
        NSForegroundColorAttributeName : color
      }];
}
- (void)drawRect:(CGRect)rect {
  CGContextRef c = UIGraphicsGetCurrentContext();
  float s = [self scale];
  CGContextTranslateCTM(c, (self.bounds.size.width - [self canvasW] * s) / 2,
                        (self.bounds.size.height - [self canvasH] * s) / 2);
  CGContextScaleCTM(c, s, s);
  UIColor *accent = rgb(self.accent == 1 ? 0x69B2FF : self.accent == 2 ? 0xFFC362 : 0x5ADDC5), *ink = rgb(0xEDF4FF), *muted = rgb(0x91A5BF);
  if (![self portrait] || !self.editing) [self text:self.editing
                 ? @"EDIT LAYOUT"
                 : [NSString stringWithFormat:@"PLAYER %ld", (long)self.player]
           x:24
           y:10
        size:22
       color:accent];
  [self text:self.classic ? @"CLASSIC CONTROLLER" : self.pro ? @"PRO CONTROLLER" : self.motionPlus ? @"WII REMOTE + MOTIONPLUS" : @"WII REMOTE"
           x:[self portrait] ? 16 : 162
           y:[self portrait] ? 64 : 12
        size:[self portrait] ? 18 : 20
       color:ink];
  [self
       text:self.editing ? @"Drag controls; drag bottom-right corners to resize"
                         : self.status
          x:24
          y:[self portrait] ? 94 : 48
       size:14
      color:muted];
  for (NSDictionary *control in self.controls) {
    CGRect r = [control[@"rect"] CGRectValue];
    BOOL held = NO;
    CGPoint move = CGPointZero;
    for (NSDictionary *finger in self.fingers.allValues)
      if (finger[@"control"] == control) {
        held = YES;
        CGPoint p = [finger[@"point"] CGPointValue],
                origin = [finger[@"start"] CGPointValue];
        move = CGPointMake(p.x - origin.x, p.y - origin.y);
      }
    CGContextSaveGState(c);
    CGContextSetAlpha(c, self.editing || ![self editable:control] ? 1 : self.opacity);
    [(held ? rgb(0x265E60) : rgb(0x1D2B3F)) setFill];
    [[UIBezierPath bezierPathWithRoundedRect:r cornerRadius:14] fill];
    int action = [control[@"action"] intValue];
    if (action == 3) {
      [self text:self.gyroAim ? @"GYRO AIM / TOUCH OVERRIDE" : @"TOUCH TO AIM" x:r.origin.x+14 y:r.origin.y+12 size:14 color:muted];
      BOOL valid = self.aimValid; float px = self.aimX / 156.0f, py = self.aimY / 156.0f;
      for (NSDictionary *finger in self.fingers.allValues) if (finger[@"control"] == control) {
        CGPoint point = [finger[@"point"] CGPointValue]; valid = YES;
        px = (point.x-r.origin.x)/r.size.width*2-1; py = (point.y-r.origin.y)/r.size.height*2-1;
      }
      if (valid) {
        float x = r.origin.x+(MAX(-1,MIN(1,px))+1)*.5*r.size.width;
        float y = r.origin.y+(MAX(-1,MIN(1,py))+1)*.5*r.size.height;
        [accent setFill]; UIRectFill(CGRectMake(x-18,y-1,36,2)); UIRectFill(CGRectMake(x-1,y-18,2,36));
        [[UIBezierPath bezierPathWithOvalInRect:CGRectMake(x-6,y-6,12,12)] fill];
      }
    } else if (action == 1 || action == 2) {
      float radius = MIN(r.size.width, r.size.height) * .34;
      CGPoint center = CGPointMake(CGRectGetMidX(r), CGRectGetMidY(r));
      [rgb(0x2C415A) setFill];
      [[UIBezierPath
          bezierPathWithOvalInRect:CGRectMake(center.x - radius,
                                              center.y - radius, 2 * radius,
                                              2 * radius)] fill];
      [accent setFill];
      float knob = radius * .32;
      [[UIBezierPath
          bezierPathWithOvalInRect:CGRectMake(
                                       center.x +
                                           MAX(-1, MIN(1, move.x /
                                                              (radius * .92))) *
                                               radius * .6 -
                                           knob,
                                       center.y +
                                           MAX(-1, MIN(1, move.y /
                                                              (radius * .92))) *
                                               radius * .6 -
                                           knob,
                                       2 * knob, 2 * knob)] fill];
      if (self.showLabels || self.editing) [self text:control[@"label"]
               x:r.origin.x + 14
               y:CGRectGetMaxY(r) - 25
            size:14
           color:muted];
    } else if (self.showLabels || self.editing || ![self editable:control]) {
      NSString *title = control[@"label"];
      float font = title.length <= 2 ? 26 : 16;
      CGSize size = [title sizeWithAttributes:@{
        NSFontAttributeName : [UIFont systemFontOfSize:font
                                                weight:UIFontWeightMedium]
      }];
      [self text:title
               x:CGRectGetMidX(r) - size.width / 2
               y:CGRectGetMidY(r) - size.height / 2
            size:font
           color:ink];
    }
    CGContextRestoreGState(c);
    if (self.editing && self.selected == control) {
      [accent setStroke];
      UIBezierPath *outline = [UIBezierPath bezierPathWithRect:r];
      outline.lineWidth = 2;
      [outline stroke];
      [accent setFill];
      UIRectFill(
          CGRectMake(CGRectGetMaxX(r) - 18, CGRectGetMaxY(r) - 18, 18, 18));
    }
  }
  [self text:self.editing
                 ? @"Editing sends no controller input"
                 : self.showStats ? [NSString stringWithFormat:@"Sent %lu | Replies %lu | iOS 12+", self.sent, self.replies] : @"Multi-touch | SETUP for layouts and settings"
           x:24
           y:[self canvasH] - 26
        size:13
       color:muted];
}
- (void)clear {
  [self.fingers removeAllObjects];
  self.aimValid = NO;
  memset(&edges, 0, sizeof(edges));
  [self setNeedsDisplay];
}
- (void)savePrefs {
  NSUserDefaults *p = NSUserDefaults.standardUserDefaults;
  [p setInteger:self.player forKey:@"player"];
  [p setBool:self.pro forKey:@"pro"];
  [p setBool:self.classic forKey:@"classic"];
  [p setBool:self.motionPlus forKey:@"motionPlus"];
  [p setBool:self.gyroAim forKey:@"gyroAim"];
  [p setFloat:self.aimSensitivity forKey:@"aimSensitivity"];
  [p setFloat:self.aimSmoothing forKey:@"aimSmoothing"];
  [p setInteger:self.remoteOrientation forKey:@"remoteOrientation"];
  [p setBool:self.haptics forKey:@"haptics"];
  [p setBool:self.snapGrid forKey:@"snapGrid"];
  [p setBool:self.showLabels forKey:@"showLabels"];
  [p setBool:self.showStats forKey:@"showStats"];
  [p setFloat:self.opacity forKey:@"opacity"];
  [p setInteger:self.accent forKey:@"accent"];
  [p setObject:self.host forKey:@"host"];
  [p setFloat:self.sensitivity forKey:@"sensitivity"];
  [p setFloat:self.deadzone forKey:@"deadzone"];
  [p setBool:self.invert forKey:@"invert"];
}
- (UIViewController *)controller {
  UIResponder *r = self;
  while (r && ![r isKindOfClass:UIViewController.class])
    r = r.nextResponder;
  return (UIViewController *)r;
}
- (void)alert:(NSString *)message {
  UIAlertController *a =
      [UIAlertController alertControllerWithTitle:@"Wii U Controller"
                                          message:message
                                   preferredStyle:UIAlertControllerStyleAlert];
  [a addAction:[UIAlertAction actionWithTitle:@"OK"
                                        style:UIAlertActionStyleDefault
                                      handler:nil]];
  [[self controller] presentViewController:a animated:YES completion:nil];
}
- (void)settings {
  [self clear];
  UIAlertController *a = [UIAlertController
      alertControllerWithTitle:@"Controller settings"
                       message:@"Players 1–4 • Remote, Pro or Classic"
                preferredStyle:UIAlertControllerStyleAlert];
  [a addAction:[UIAlertAction actionWithTitle:@"Connect / address"
                                        style:0
                                      handler:^(UIAlertAction *x) {
                                        [self address];
                                      }]];
  [a addAction:[UIAlertAction
                   actionWithTitle:
                       [NSString stringWithFormat:@"Player %ld — choose player",
                                                  (long)self.player]
                             style:0
                           handler:^(UIAlertAction *x) {
                             [self choosePlayer];
                           }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Controller type" style:0 handler:^(UIAlertAction *x) { [self chooseMode]; }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Edit button layout"
                                        style:0
                                      handler:^(UIAlertAction *x) {
                                        [self releaseAndClose];
                                        [self clear];
                                        self.editing = YES;
                                        [self layoutControls];
                                      }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Stick settings"
                                        style:0
                                      handler:^(UIAlertAction *x) {
                                        [self stickSettings];
                                      }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Motion & orientation" style:0 handler:^(UIAlertAction *x) { [self motionSettings]; }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Appearance" style:0 handler:^(UIAlertAction *x) { [self appearance]; }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Export layouts" style:0 handler:^(UIAlertAction *x) { [self exportLayouts]; }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Import layouts" style:0 handler:^(UIAlertAction *x) { [self importLayouts]; }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Touch options" style:0 handler:^(UIAlertAction *x) { [self touchOptions]; }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Disconnect"
                                        style:2
                                      handler:^(UIAlertAction *x) {
                                        self.enabled = NO;
                                        [self startMotion];
                                        [self releaseAndClose];
                                        self.status = @"Disconnected";
                                      }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Close" style:1 handler:nil]];
  [[self controller] presentViewController:a animated:YES completion:nil];
}
- (void)chooseMode {
  [self clear];
  UIAlertController *a = [UIAlertController alertControllerWithTitle:@"Controller type" message:@"Some games require joining again after changing type." preferredStyle:1];
  NSArray *names = @[@"Wii Remote", @"Pro Controller", @"Classic Controller", @"Wii Remote + MotionPlus"];
  for (NSUInteger i = 0; i < names.count; i++) {
    NSInteger mode = i;
    [a addAction:[UIAlertAction actionWithTitle:names[i] style:0 handler:^(UIAlertAction *x) {
      [self releaseAndClose];
      if (mode == 3 && !self.motion.gyroAvailable) { [self alert:@"MotionPlus requires a hardware gyroscope"]; return; }
      self.pro = mode == 1 || mode == 2; self.classic = mode == 2; self.motionPlus = mode == 3;
      [self resetMotion]; [self startMotion]; [UIViewController attemptRotationToDeviceOrientation];
      [self savePrefs]; [self layoutControls]; self.pauseUntil = clockNow() + .35;
    }]];
  }
  [a addAction:[UIAlertAction actionWithTitle:@"Cancel" style:1 handler:nil]];
  [[self controller] presentViewController:a animated:YES completion:nil];
}
- (void)captureButtons {
  if (self.editing) return;
  unsigned buttons = 0;
  for (NSDictionary *f in self.fingers.allValues) {
    NSDictionary *c = f[@"control"];
    if ([c[@"action"] intValue] == 0 && CGRectContainsPoint([c[@"rect"] CGRectValue], [f[@"point"] CGPointValue])) buttons |= [c[@"bit"] unsignedIntValue];
  }
  ios_input_publish(&edges, clockNow(), buttons);
}
- (void)resetMotion { phone_motion_reset(&motionState); gyro_pointer_reset(&aimState); self.aimValid = NO; }
- (void)recenterAim { gyro_pointer_center(&aimState, motionState.q); self.aimValid = NO; }
- (void)toggleAim {
  if (!self.motion.gyroAvailable) { [self alert:@"Gyro aiming requires a hardware gyroscope"]; return; }
  self.gyroAim = !self.gyroAim; [self recenterAim]; [self clear]; [self savePrefs]; [self startMotion]; [self layoutControls];
}
- (void)motionSettings {
  UIAlertController *a = [UIAlertController alertControllerWithTitle:@"Motion & pointing" message:self.motion.gyroAvailable ? @"Point the phone's TOP EDGE at the TV center, then tap CENTER. Touch overrides gyro aiming. Sensitivity 25–300%; smoothing 0–200 ms." : @"No gyroscope: gyro aiming and MotionPlus unavailable." preferredStyle:1];
  [a addTextFieldWithConfigurationHandler:^(UITextField *f) { f.placeholder = @"Aim sensitivity (%)"; f.keyboardType = UIKeyboardTypeNumberPad; f.text = [NSString stringWithFormat:@"%.0f",self.aimSensitivity*100]; }];
  [a addTextFieldWithConfigurationHandler:^(UITextField *f) { f.placeholder = @"Aim smoothing (ms)"; f.keyboardType = UIKeyboardTypeNumberPad; f.text = [NSString stringWithFormat:@"%.0f",self.aimSmoothing*1000]; }];
  [a addAction:[UIAlertAction actionWithTitle:@"Save aim settings" style:0 handler:^(UIAlertAction *x) {
    self.aimSensitivity = MAX(.25,MIN(3,[a.textFields[0].text floatValue]/100));
    self.aimSmoothing = MAX(0,MIN(.2,[a.textFields[1].text floatValue]/1000)); [self savePrefs];
  }]];
  [a addAction:[UIAlertAction actionWithTitle:self.gyroAim ? @"Disable gyro aiming" : @"Enable gyro aiming" style:0 handler:^(UIAlertAction *x) { [self toggleAim]; }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Recenter pointer" style:0 handler:^(UIAlertAction *x) { [self recenterAim]; }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Reset MotionPlus orientation" style:0 handler:^(UIAlertAction *x) { [self resetMotion]; [self clear]; }]];
  NSArray *names = @[@"Automatic rotation", @"Vertical Wii Remote", @"Horizontal Wii Remote"];
  for (NSUInteger i = 0; i < names.count; i++) {
    NSInteger orientation = i;
    [a addAction:[UIAlertAction actionWithTitle:names[i] style:0 handler:^(UIAlertAction *x) {
      self.remoteOrientation = orientation; [self clear]; [self resetMotion]; [self savePrefs]; [UIViewController attemptRotationToDeviceOrientation]; [self layoutControls];
    }]];
  }
  [a addAction:[UIAlertAction actionWithTitle:@"Close" style:1 handler:nil]];
  [[self controller] presentViewController:a animated:YES completion:nil];
}
- (void)touchOptions {
  UIAlertController *a = [UIAlertController alertControllerWithTitle:@"Touch options" message:nil preferredStyle:1];
  [a addAction:[UIAlertAction actionWithTitle:self.haptics ? @"Disable haptics" : @"Enable haptics" style:0 handler:^(UIAlertAction *x) { self.haptics = !self.haptics; [self savePrefs]; }]];
  [a addAction:[UIAlertAction actionWithTitle:self.snapGrid ? @"Disable editor grid snapping" : @"Enable editor grid snapping" style:0 handler:^(UIAlertAction *x) { self.snapGrid = !self.snapGrid; [self savePrefs]; }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Close" style:1 handler:nil]];
  [[self controller] presentViewController:a animated:YES completion:nil];
}
- (void)appearance {
  UIAlertController *a = [UIAlertController alertControllerWithTitle:@"Appearance" message:@"Control opacity: 25–100%" preferredStyle:1];
  [a addTextFieldWithConfigurationHandler:^(UITextField *f) { f.keyboardType = UIKeyboardTypeNumberPad; f.text = [NSString stringWithFormat:@"%.0f", self.opacity * 100]; }];
  [a addAction:[UIAlertAction actionWithTitle:@"Save opacity" style:0 handler:^(UIAlertAction *x) { self.opacity = MAX(.25, MIN(1, [a.textFields[0].text floatValue] / 100)); [self savePrefs]; }]];
  [a addAction:[UIAlertAction actionWithTitle:self.showLabels ? @"Hide labels" : @"Show labels" style:0 handler:^(UIAlertAction *x) { self.showLabels = !self.showLabels; [self savePrefs]; }]];
  [a addAction:[UIAlertAction actionWithTitle:self.showStats ? @"Hide packet diagnostics" : @"Show packet diagnostics" style:0 handler:^(UIAlertAction *x) { self.showStats = !self.showStats; [self savePrefs]; }]];
  NSArray *colors = @[@"Mint accent", @"Blue accent", @"Amber accent"];
  for (NSUInteger i = 0; i < colors.count; i++) {
    NSInteger color = i;
    [a addAction:[UIAlertAction actionWithTitle:colors[i] style:0 handler:^(UIAlertAction *x) { self.accent = color; [self savePrefs]; }]];
  }
  [a addAction:[UIAlertAction actionWithTitle:@"Cancel" style:1 handler:nil]];
  [[self controller] presentViewController:a animated:YES completion:nil];
}
- (void)exportLayouts {
  NSMutableDictionary *out = [@{@"version": @1} mutableCopy];
  for (NSString *mode in @[@"remote", @"remotePortrait", @"pro", @"classic"]) {
    out[mode] = [NSUserDefaults.standardUserDefaults dictionaryForKey:[@"ios.layout." stringByAppendingString:mode]] ?: @{};
  }
  NSError *error = nil;
  NSData *data = [NSJSONSerialization dataWithJSONObject:out options:NSJSONWritingPrettyPrinted error:&error];
  NSURL *url = [NSURL fileURLWithPath:[NSTemporaryDirectory() stringByAppendingPathComponent:@"wiiu-controller-layout.json"]];
  if (!data || ![data writeToURL:url options:NSDataWritingAtomic error:&error]) { [self alert:@"Could not export layouts"]; return; }
  UIActivityViewController *share = [[UIActivityViewController alloc] initWithActivityItems:@[url] applicationActivities:nil];
  share.popoverPresentationController.sourceView = self;
  share.popoverPresentationController.sourceRect = CGRectMake(self.bounds.size.width / 2, 20, 1, 1);
  [[self controller] presentViewController:share animated:YES completion:nil];
}
- (void)importLayouts {
  UIDocumentPickerViewController *picker = [[UIDocumentPickerViewController alloc] initWithDocumentTypes:@[@"public.json", @"public.data"] inMode:UIDocumentPickerModeImport];
  picker.delegate = self;
  [[self controller] presentViewController:picker animated:YES completion:nil];
}
- (void)documentPicker:(UIDocumentPickerViewController *)picker didPickDocumentsAtURLs:(NSArray<NSURL *> *)urls {
  NSURL *url = urls.firstObject;
  BOOL accessed = [url startAccessingSecurityScopedResource];
  NSError *error = nil;
  NSNumber *fileSize = nil;
  [url getResourceValue:&fileSize forKey:NSURLFileSizeKey error:&error];
  NSData *data = !error && fileSize.unsignedLongLongValue <= 65536 ? [NSData dataWithContentsOfURL:url options:0 error:&error] : nil;
  if (accessed) [url stopAccessingSecurityScopedResource];
  id object = data ? [NSJSONSerialization JSONObjectWithData:data options:0 error:&error] : nil;
  BOOL valid = !error && [object isKindOfClass:NSDictionary.class] && [object[@"version"] isEqual:@1];
  NSMutableDictionary *layouts = [NSMutableDictionary dictionary];
  for (NSString *mode in @[@"remote", @"remotePortrait", @"pro", @"classic"]) {
    id layout = valid ? object[mode] : nil;
    if (!layout && ([mode isEqual:@"classic"] || [mode isEqual:@"remotePortrait"])) layout = @{};
    if (![layout isKindOfClass:NSDictionary.class] || [layout count] > 64) { valid = NO; break; }
    for (id key in layout) {
      id rect = layout[key];
      if (![key isKindOfClass:NSString.class] || ![rect isKindOfClass:NSArray.class] || [rect count] != 4) { valid = NO; break; }
      for (id n in rect) if (![n isKindOfClass:NSNumber.class] || !isfinite([n doubleValue])) valid = NO;
      if (!valid) break;
    }
    if (!valid) break;
    layouts[mode] = layout;
  }
  if (!valid) { [self alert:@"Invalid layout file (version 1 JSON, maximum 64 KB)"]; return; }
  [self clear];
  for (NSString *mode in layouts) [NSUserDefaults.standardUserDefaults setObject:layouts[mode] forKey:[@"ios.layout." stringByAppendingString:mode]];
  [self layoutControls];
}
- (void)choosePlayer {
  UIAlertController *a = [UIAlertController
      alertControllerWithTitle:@"Choose player"
                       message:@"Choose an unused slot. The 3DS uses player 2."
                preferredStyle:1];
  for (int i = 1; i <= 4; i++) {
    int player = i;
    [a addAction:[UIAlertAction
                     actionWithTitle:[NSString stringWithFormat:@"Player %d", i]
                               style:0
                             handler:^(UIAlertAction *x) {
                               [self releaseAndClose];
                               self.player = player;
                               [self clear];
                               [self savePrefs];
                               self.pauseUntil = clockNow() + .35;
                             }]];
  }
  [a addAction:[UIAlertAction actionWithTitle:@"Cancel" style:1 handler:nil]];
  [[self controller] presentViewController:a animated:YES completion:nil];
}
- (void)address {
  UIAlertController *a = [UIAlertController
      alertControllerWithTitle:@"Connect Wii U"
                       message:@"Same Wi-Fi as the console. Find requires the "
                               @"updated Aroma plugin."
                preferredStyle:1];
  [a addTextFieldWithConfigurationHandler:^(UITextField *f) {
    f.text = self.host;
    f.keyboardType = UIKeyboardTypeNumbersAndPunctuation;
  }];
  [a addAction:[UIAlertAction
                   actionWithTitle:@"Connect"
                             style:0
                           handler:^(UIAlertAction *x) {
                             NSString *host = [a.textFields.firstObject.text
                                 stringByTrimmingCharactersInSet:
                                     NSCharacterSet
                                         .whitespaceAndNewlineCharacterSet];
                             struct in_addr ip;
                             if (inet_pton(AF_INET, host.UTF8String, &ip) !=
                                 1) {
                               [self alert:@"Enter a valid IPv4 address"];
                               return;
                             }
                             [self releaseAndClose];
                             self.host = host;
                             self.enabled = YES;
                             [self startMotion];
                             [self clear];
                             [self savePrefs];
                             self.status = @"Connecting...";
                           }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Find Wii U on Wi-Fi"
                                        style:0
                                      handler:^(UIAlertAction *x) {
                                        [self discover];
                                      }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Cancel" style:1 handler:nil]];
  [[self controller] presentViewController:a animated:YES completion:nil];
}
- (void)stickSettings {
  UIAlertController *a = [UIAlertController
      alertControllerWithTitle:@"Stick settings"
                       message:@"Sensitivity 25–200%; deadzone 0–30%."
                preferredStyle:1];
  [a addTextFieldWithConfigurationHandler:^(UITextField *f) {
    f.placeholder = @"Sensitivity (%)";
    f.keyboardType = UIKeyboardTypeNumberPad;
    f.text = [NSString stringWithFormat:@"%.0f", self.sensitivity * 100];
  }];
  [a addTextFieldWithConfigurationHandler:^(UITextField *f) {
    f.placeholder = @"Deadzone (%)";
    f.keyboardType = UIKeyboardTypeNumberPad;
    f.text = [NSString stringWithFormat:@"%.0f", self.deadzone * 100];
  }];
  [a addAction:[UIAlertAction
                   actionWithTitle:@"Save"
                             style:0
                           handler:^(UIAlertAction *x) {
                             self.sensitivity = MAX(
                                 .25, MIN(2, [a.textFields[0].text floatValue] /
                                                 100));
                             self.deadzone = MAX(
                                 0, MIN(.3, [a.textFields[1].text floatValue] /
                                                100));
                             [self clear];
                             [self savePrefs];
                           }]];
  [a addAction:[UIAlertAction
                   actionWithTitle:self.invert ? @"Disable right Y inversion"
                                               : @"Invert right stick Y"
                             style:0
                           handler:^(UIAlertAction *x) {
                             self.invert = !self.invert;
                             [self clear];
                             [self savePrefs];
                           }]];
  [a addAction:[UIAlertAction actionWithTitle:@"Cancel" style:1 handler:nil]];
  [[self controller] presentViewController:a animated:YES completion:nil];
}
- (void)discover {
  self.status = @"Searching Wi-Fi...";
  dispatch_async(
      dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        int fd = socket(AF_INET, SOCK_DGRAM, 0), yes = 1;
        NSString *found = nil;
        if (fd >= 0) {
          setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &yes, sizeof(yes));
          struct timeval timeout = {0, 200000};
          setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
          unsigned interface = if_nametoindex("en0");
          if (interface)
            setsockopt(fd, IPPROTO_IP, IP_BOUND_IF, &interface,
                       sizeof(interface));
          struct sockaddr_in dest = {0};
          dest.sin_len = sizeof(dest);
          dest.sin_family = AF_INET;
          dest.sin_port = htons(26760);
          dest.sin_addr.s_addr = INADDR_BROADCAST;
          for (int i = 0; i < 12 && !found; i++) {
            if (i % 2 == 0)
              sendto(fd, "WUD?", 4, 0, (struct sockaddr *)&dest, sizeof(dest));
            char reply[8];
            struct sockaddr_in from = {0};
            socklen_t size = sizeof(from);
            int n =
                (int)recvfrom(fd, reply, 8, 0, (struct sockaddr *)&from, &size);
            if (n == 4 && !memcmp(reply, "WUD!", 4) &&
                from.sin_port == htons(26760)) {
              char ip[INET_ADDRSTRLEN];
              inet_ntop(AF_INET, &from.sin_addr, ip, sizeof(ip));
              found = [NSString stringWithUTF8String:ip];
            }
          }
          close(fd);
        }
        dispatch_async(dispatch_get_main_queue(), ^{
          if (found) {
            [self releaseAndClose];
            self.host = found;
            self.enabled = YES;
            [self startMotion];
            [self savePrefs];
            self.status = [@"Found " stringByAppendingString:found];
          } else
            [self alert:@"No Wii U found. Check Wi-Fi and Aroma, or enter the "
                        @"IP manually."];
        });
      });
}
- (void)finishEdit:(BOOL)save {
  if (save) {
    NSMutableDictionary *layout = [NSMutableDictionary dictionary];
    for (NSDictionary *c in self.controls)
      if ([self editable:c]) {
        CGRect r = [c[@"rect"] CGRectValue];
        layout[c[@"id"]] = @[
          @(r.origin.x), @(r.origin.y), @(r.size.width), @(r.size.height)
        ];
      }
    [NSUserDefaults.standardUserDefaults setObject:layout
                                            forKey:[self layoutKey]];
  }
  self.editing = NO;
  self.selected = nil;
  [self clear];
  [self layoutControls];
}
- (void)touchesBegan:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
  for (UITouch *t in touches) {
    CGPoint p = [self logical:[t locationInView:self]];
    for (NSMutableDictionary *c in self.controls)
      if (CGRectContainsPoint([c[@"rect"] CGRectValue], p)) {
        int action = [c[@"action"] intValue];
        if (self.editing) {
          if (action == 20) {
            [self finishEdit:YES];
            return;
          }
          if (action == 21) {
            [self finishEdit:NO];
            return;
          }
          if (action == 22) {
            id saved = [NSUserDefaults.standardUserDefaults
                objectForKey:[self layoutKey]];
            [NSUserDefaults.standardUserDefaults
                removeObjectForKey:[self layoutKey]];
            [self layoutControls];
            if (saved)
              [NSUserDefaults.standardUserDefaults setObject:saved
                                                      forKey:[self layoutKey]];
            return;
          }
          self.selected = c;
          self.dragStart = p;
          self.dragRect = [c[@"rect"] CGRectValue];
          self.resizing = p.x > CGRectGetMaxX(self.dragRect) - 28 &&
                          p.y > CGRectGetMaxY(self.dragRect) - 28;
          break;
        }
        if (action == 10) {
          [self chooseMode];
          return;
        }
        if (action == 11) {
          [self settings];
          return;
        }
        if (action == 13) { [self toggleAim]; return; }
        if (action == 14) { [self recenterAim]; return; }
        if (action == 12) {
          if (!self.motion.accelerometerAvailable) { [self alert:@"No accelerometer available"]; return; }
          self.tilt = !self.tilt;
          [self startMotion];
          [self clear]; [self layoutControls]; return;
        }
        BOOL occupied = NO;
        for (NSDictionary *f in self.fingers.allValues)
          if (f[@"control"] == c && action >= 1 && action <= 3)
            occupied = YES;
        if (occupied)
          break;
        self.fingers[[NSValue valueWithNonretainedObject:t]] = [@{
          @"control" : c,
          @"point" : [NSValue valueWithCGPoint:p],
          @"start" : [NSValue valueWithCGPoint:p]
        } mutableCopy];
        if (self.haptics && [c[@"bit"] unsignedIntValue]) {
          UIImpactFeedbackGenerator *feedback = [[UIImpactFeedbackGenerator alloc] initWithStyle:UIImpactFeedbackStyleLight];
          [feedback impactOccurred];
        }
        break;
      }
  }
  [self captureButtons];
  [self setNeedsDisplay];
}
- (void)touchesMoved:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
  for (UITouch *t in touches) {
    CGPoint p = [self logical:[t locationInView:self]];
    if (self.editing && self.selected) {
      float dx = p.x - self.dragStart.x, dy = p.y - self.dragStart.y;
      if (self.snapGrid) { dx = roundf(dx / 8) * 8; dy = roundf(dy / 8) * 8; }
      CGRect r = self.dragRect;
      if (self.resizing) {
        r.size.width = MAX(40, MIN([self canvasW] - 60, r.size.width + dx));
        r.size.height = MAX(40, MIN([self canvasH] - ([self portrait] ? 152 : 100), r.size.height + dy));
      } else {
        r.origin.x += dx;
        r.origin.y += dy;
      }
      r.origin.x = MAX(8, MIN([self canvasW] - 8 - r.size.width, r.origin.x));
      r.origin.y = MAX([self portrait] ? 120 : 68, MIN([self canvasH] - 32 - r.size.height, r.origin.y));
      self.selected[@"rect"] = [NSValue valueWithCGRect:r];
    } else {
      NSMutableDictionary *f =
          self.fingers[[NSValue valueWithNonretainedObject:t]];
      if (f)
        f[@"point"] = [NSValue valueWithCGPoint:p];
    }
  }
  [self captureButtons];
  [self setNeedsDisplay];
}
- (void)touchesEnded:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
  for (UITouch *t in touches)
    [self.fingers removeObjectForKey:[NSValue valueWithNonretainedObject:t]];
  [self captureButtons];
  [self setNeedsDisplay];
}
- (void)touchesCancelled:(NSSet<UITouch *> *)touches
               withEvent:(UIEvent *)event {
  [self clear];
}
- (int)axis:(float)value radius:(float)radius {
  float fraction = fabsf(value) / radius;
  if (fraction < self.deadzone)
    return 0;
  return stick(copysignf((fraction - self.deadzone) / (1 - self.deadzone) *
                             self.sensitivity * 156,
                         value));
}
- (void)packet:(uint8_t *)p neutral:(BOOL)neutral {
  memset(p, 0, [self packetSize]);
  memcpy(p, "ANWC", 4);
  p[4] = [self packetSize] == 64 ? 2 : 1;
  p[5] = (uint8_t)(self.player - 1);
  word(p + 6, self.sequence++);
  p[23] = self.classic ? 2 : self.pro ? 1 : self.motionPlus ? 3 : 0;
  unsigned buttons = 0;
  int lx = 0, ly = 0, rx = 0, ry = 0, px = 0, py = 0, ax = 0, az = 1000;
  BOOL pointer = NO;
  self.aimValid = NO;
  if (!neutral && !self.pro && self.gyroAim && motionState.last > 0 && clockNow()-motionState.last < .2 && gyro_pointer_update(&aimState, motionState.q, clockNow(), self.aimSensitivity, self.aimSmoothing)) {
    pointer = YES; self.aimValid = YES;
    px = self.aimX = stick(aimState.x*156); py = self.aimY = stick(aimState.y*156);
  } else gyro_pointer_invalidate(&aimState);
  if (!neutral)
    for (NSDictionary *f in self.fingers.allValues) {
      NSDictionary *c = f[@"control"];
      int action = [c[@"action"] intValue];
      CGRect r = [c[@"rect"] CGRectValue];
      CGPoint point = [f[@"point"] CGPointValue],
              start = [f[@"start"] CGPointValue];
      if (action == 1 || action == 2) {
        float radius = MIN(r.size.width, r.size.height) * .315;
        int x = [self axis:point.x - start.x radius:radius],
            y = [self axis:start.y - point.y radius:radius];
        if (action == 1) {
          lx = x;
          ly = y;
        } else {
          rx = x;
          ry = self.invert ? -y : y;
        }
      } else if (action == 3) {
        pointer = YES;
        px = stick(((point.x - r.origin.x) / r.size.width * 2 - 1) * 156);
        py = stick(((point.y - r.origin.y) / r.size.height * 2 - 1) * 156);
      } else if (action == 4)
        ax = ((int)(clockNow() * 14) % 2) ? 3000 : -3000;
      else if (CGRectContainsPoint(r, point))
        buttons |= [c[@"bit"] unsignedIntValue];
    }
  if (!neutral) buttons = ios_input_poll(&edges, clockNow(), buttons);
  buttons &= self.classic ? 0xFEFF : self.pro ? 0x3FEFF : 0x9F1F;
  p[8] = buttons >> 24;
  p[9] = buttons >> 16;
  p[10] = buttons >> 8;
  p[11] = buttons;
  if (self.pro) {
    word(p + 12, lx);
    word(p + 14, ly);
    word(p + 16, rx);
    word(p + 18, ry);
  } else {
    word(p + 12, px);
    word(p + 14, py);
    if (!neutral && (self.tilt || self.motionPlus) && self.motion.accelerometerData && clockNow() - self.motion.accelerometerData.timestamp < .2) {
      CMAcceleration a = self.motion.accelerometerData.acceleration;
      float v[3]; phone_motion_rotate(-a.x, -a.y, -a.z, [self rotation], v);
      ax = (int)lrint(v[0]*1000); word(p + 18, (int)lrint(v[1]*1000)); az = (int)lrint(v[2]*1000);
      for (NSDictionary *f in self.fingers.allValues) if ([f[@"control"][@"action"] intValue] == 4) ax = ((int)(clockNow() * 14) % 2) ? 3000 : -3000;
    }
    word(p + 16, ax);
    word(p + 20, az);
    p[22] = pointer ? 1 : 0;
    if (!neutral && self.motionPlus && motionState.last > 0 && clockNow() - motionState.last < .2) {
      for (int i = 0; i < 3; i++) word(p + 32 + i*2, (int)lrintf(fmaxf(-32767, fminf(32767, motionState.rate[i]*1000))));
      for (int i = 0; i < 4; i++) word(p + 38 + i*2, (int)lrintf(motionState.q[i]*32767));
      p[46] = 1;
    }
  }
}
- (void)releaseAndClose {
  if (self.fd >= 0) {
    for (int i = 0; i < 3; i++) {
      uint8_t packet[64];
      [self packet:packet neutral:YES];
      send(self.fd, packet, [self packetSize], 0);
    }
    close(self.fd);
    self.fd = -1;
  }
  [self clear];
  self.lastReply = 0;
}
- (NSUInteger)packetSize { return !self.pro && self.motionPlus ? 64 : 32; }
- (void)tick {
  if (self.lastRotation != [self rotation]) { self.lastRotation = [self rotation]; [self clear]; [self resetMotion]; [self layoutControls]; }
  if (self.enabled && !self.pro && (self.motionPlus || self.gyroAim) && self.motion.gyroData && self.motion.gyroData.timestamp != motionState.last) {
    CMGyroData *data = self.motion.gyroData;
    phone_motion_update(&motionState, data.timestamp, data.rotationRate.x, data.rotationRate.y, data.rotationRate.z, [self rotation]);
  }
  if (self.enabled && !self.editing && clockNow() >= self.pauseUntil) {
    if (self.fd < 0) {
      self.fd = socket(AF_INET, SOCK_DGRAM, 0);
      if (self.fd >= 0) {
        fcntl(self.fd, F_SETFL, O_NONBLOCK);
        unsigned interface = if_nametoindex("en0");
        if (interface)
          setsockopt(self.fd, IPPROTO_IP, IP_BOUND_IF, &interface,
                     sizeof(interface));
        struct sockaddr_in address = {0};
        address.sin_len = sizeof(address);
        address.sin_family = AF_INET;
        address.sin_port = htons(26760);
        if (inet_pton(AF_INET, self.host.UTF8String, &address.sin_addr) != 1 ||
            connect(self.fd, (struct sockaddr *)&address, sizeof(address)) <
                0) {
          [self releaseAndClose];
          self.pauseUntil = clockNow() + .5;
        }
        self.opened = clockNow();
      }
    }
    if (self.fd >= 0) {
      uint8_t packet[64];
      [self packet:packet neutral:NO];
      if (send(self.fd, packet, [self packetSize], 0) == (ssize_t)[self packetSize]) self.sent++;
      char reply[8];
      int n;
      for (int i = 0; i < 32; i++) {
        n = (int)recv(self.fd, reply, 8, 0);
        if (n < 0)
          break;
        if (n == 4 && !memcmp(reply, "ANWA", 4)) {
          self.replies++;
          self.lastReply = clockNow();
          self.status = [NSString stringWithFormat:@"CONNECTED  %@", self.host];
        } else if (n == 4 && !memcmp(reply, "ANWB", 4)) {
          self.lastReply = clockNow();
          self.status = @"Slot occupied — choose a different player";
        }
      }
      if (clockNow() - MAX(self.lastReply, self.opened) > 1.8) {
        [self releaseAndClose];
        self.pauseUntil = clockNow() + .2;
        self.status = @"Reconnecting — check Wi-Fi and Wii U";
      }
    }
  }
  [self setNeedsDisplay];
}
@end
@interface AppDelegate : UIResponder <UIApplicationDelegate>
@property(nonatomic, strong) UIWindow *window;
@property(nonatomic, strong) RemoteController *root;
@property(nonatomic) BOOL resumeConnection;
@end
@implementation AppDelegate
- (BOOL)application:(UIApplication *)app
    didFinishLaunchingWithOptions:(NSDictionary *)options {
  self.window = [[UIWindow alloc] initWithFrame:UIScreen.mainScreen.bounds];
  self.root = [RemoteController new];
  self.window.rootViewController = self.root;
  [self.window makeKeyAndVisible];
  app.idleTimerDisabled = YES;
  return YES;
}
- (void)applicationWillResignActive:(UIApplication *)app {
  self.resumeConnection = self.root.pad.enabled;
  self.root.pad.enabled = NO;
  [self.root.pad startMotion];
  [self.root.pad releaseAndClose];
}
- (void)applicationDidBecomeActive:(UIApplication *)app {
  if (self.resumeConnection) {
    self.root.pad.enabled = YES;
    [self.root.pad startMotion];
  }
}
@end
int main(int argc, char **argv) {
  @autoreleasepool {
    return UIApplicationMain(argc, argv, nil,
                             NSStringFromClass(AppDelegate.class));
  }
}
