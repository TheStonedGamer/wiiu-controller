package com.brian.wiiuremote;
public class GyroPointerTest {
  private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
  private static void near(float a, float b) { if (Math.abs(a-b) > .001) throw new AssertionError(a+" != "+b); }
  private static float[] turn(int axis, double degrees) {
    float[] q = {(float)Math.cos(Math.toRadians(degrees)/2),0,0,0};
    q[axis] = (float)Math.sin(Math.toRadians(degrees)/2); return q;
  }
  public static void main(String[] args) {
    GyroPointer p = new GyroPointer();
    check(p.update(turn(3,0),1,1,0)); near(p.x(),0); near(p.y(),0);
    check(p.update(turn(3,-17.5),2,1,0)); near(p.x(),.5f); near(p.y(),0);
    check(p.update(turn(1,12.5),3,1,0)); near(p.x(),0); near(p.y(),-.5f);
    check(p.update(turn(2,45),4,1,0)); near(p.x(),0); near(p.y(),0);
    p.update(turn(3,-70),5,1,0); near(p.x(),1);
    check(!p.update(turn(3,180),6,1,0));
    float[] current = turn(3,-25); p.recenter(current);
    p.update(current,7,1,0); near(p.x(),0); near(current[3],turn(3,-25)[3]);
    p.reset(); p.update(turn(3,-17.5),8,2,0); near(p.x(),1);
    GyroPointer a = new GyroPointer(), b = new GyroPointer();
    a.update(turn(3,0),1,1,.1f); b.update(turn(3,0),1,1,.1f);
    for(int i=1;i<=20;i++) a.update(turn(3,-17.5),1+i*.01,1,.1f);
    for(int i=1;i<=10;i++) b.update(turn(3,-17.5),1+i*.02,1,.1f);
    near(a.x(),b.x()); check(a.x()>0 && a.x()<.5);
    a.invalidate(); a.update(turn(3,0),5,1,.1f); near(a.x(),0);
    ControllerPacket.State remote = new ControllerPacket.State(false);
    remote.pointer=true;remote.px=78;remote.py=-78;
    byte[] packet=ControllerPacket.encode(remote,1);
    check(packet[4]==1 && packet[23]==0 && packet[22]==1);
    System.out.println("Gyro pointer centering, aim directions, roll isolation, clamping, smoothing and Remote packets passed");
  }
}
