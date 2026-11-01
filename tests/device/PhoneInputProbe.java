package com.brian.wiiuremote.test;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.InputDevice;
import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.util.concurrent.*;
public class PhoneInputProbe {
 static volatile boolean running=true, combined=false, released=false, afterCancel=false;
 static volatile int phase=0,packets=0;
 static int expectedMode=1;
 interface Dispatcher {void send(MotionEvent event)throws Exception;}
 static Dispatcher dispatcher;
 static android.app.UiAutomation automation;
 static Object manager;static Method inject;
 static long down;
 static int viewWidth=2408,viewHeight=1080;
 static void send(int action,float[][] points)throws Exception{
  MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[points.length];
  MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[points.length];
  for(int i=0;i<points.length;i++){
   props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
   coords[i]=new MotionEvent.PointerCoords();coords[i].x=points[i][0];coords[i].y=points[i][1];coords[i].pressure=1;coords[i].size=.1f;
  }
  MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,points.length,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
  if(dispatcher!=null)dispatcher.send(e);else if(!(automation!=null?automation.injectInputEvent(e,true):((Boolean)inject.invoke(manager,e,2))))throw new AssertionError("Injection rejected");e.recycle();
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[]args)throws Exception{
  running=true;combined=released=afterCancel=false;phase=packets=0;
  if(automation==null){
  Class<?> type=Class.forName("android.hardware.input.InputManager");
  manager=type.getMethod("getInstance").invoke(null);inject=type.getMethod("injectInputEvent",android.view.InputEvent.class,int.class);
  }
  DatagramSocket server=new DatagramSocket(26760,InetAddress.getByName("127.0.0.1"));server.setSoTimeout(100);
  Thread reader=new Thread(()->{
   while(running)try{
    byte[] bytes=new byte[64];DatagramPacket d=new DatagramPacket(bytes,bytes.length);server.receive(d);packets++;
    ByteBuffer b=ByteBuffer.wrap(bytes);int buttons=b.getInt(8);
    if(bytes[23]==expectedMode){
     if(phase==1)System.out.println("sample buttons="+Integer.toHexString(buttons)+" lx="+b.getShort(12)+" rx="+b.getShort(16));
     if(phase==1 && (buttons&0x2090)==0x2090 && b.getShort(12)>70 && b.getShort(16)<-70)combined=true;
     if(phase==2 && buttons==0 && b.getShort(12)==0 && b.getShort(16)==0)released=true;
     if(phase==3 && buttons==0)afterCancel=true;
    }
    byte[] ack={'A','N','W','A'};server.send(new DatagramPacket(ack,4,d.getAddress(),d.getPort()));
   }catch(SocketTimeoutException expected){}catch(Exception e){if(running)throw new RuntimeException(e);}
  });reader.start();
  // Coordinates are canvas units; Galaxy A14 landscape is 2408x1080, canvas scale 1080/460.
  float scale=Math.min(viewWidth/960f,viewHeight/460f),offset=(viewWidth-960*scale)/2;
  float[][] p={{135,299},{815,299},{90,91},{230,91},{659,274}};
  for(float[] v:p){v[0]=v[0]*scale+offset;v[1]*=scale;}
  try{
   Thread.sleep(500);down=SystemClock.uptimeMillis();send(MotionEvent.ACTION_DOWN,new float[][]{p[0]});
   for(int i=1;i<p.length;i++){float[][] subset=java.util.Arrays.copyOf(p,i+1);send(MotionEvent.ACTION_POINTER_DOWN|(i<<8),subset);}
   p[0][0]+=50*scale;p[1][0]-=50*scale;phase=1;send(MotionEvent.ACTION_MOVE,p);Thread.sleep(350);
   check(combined,"Five-contact Pro input did not arrive: two sticks + ZL + L + A. Packets="+packets+" display="+viewWidth+"x"+viewHeight);
   phase=2;
   for(int i=p.length-1;i>0;i--)send(MotionEvent.ACTION_POINTER_UP|(i<<8),java.util.Arrays.copyOf(p,i+1));
   send(MotionEvent.ACTION_UP,new float[][]{p[0]});Thread.sleep(180);check(released,"Input did not return to neutral");
   down=SystemClock.uptimeMillis();send(MotionEvent.ACTION_DOWN,new float[][]{p[4]});Thread.sleep(100);
   phase=3;send(MotionEvent.ACTION_CANCEL,new float[][]{p[4]});Thread.sleep(150);check(afterCancel,"Cancel did not release controls");
   System.out.println("PASS: Galaxy A14 installed APK sent two sticks + ZL + L + A with five simultaneous contacts; release and CANCEL returned to neutral. Packets="+packets);
  }finally{try{send(MotionEvent.ACTION_CANCEL,new float[][]{p[0]});}catch(Exception ignored){}running=false;reader.join(500);server.close();}
 }
}
