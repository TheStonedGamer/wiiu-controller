package com.brian.wiiuremote.test;
import android.app.*;
import android.content.*;
import android.os.*;
public class ControllerInstrumentation extends Instrumentation {
 private Bundle options;
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);options=arguments;start();}
 @Override public void onStart(){
  Bundle result=new Bundle();int resultCode=Activity.RESULT_CANCELED;
  android.content.SharedPreferences settings=getTargetContext().getSharedPreferences("MainActivity",0);
  java.util.Map<String,Object> original=new java.util.HashMap<>(settings.getAll());
  if(options!=null && options.getString("restoreHost")!=null && !options.getString("restoreHost").isEmpty())original.put("host",options.getString("restoreHost"));
  try{
   getTargetContext().getSharedPreferences("MainActivity",0).edit().putString("host","127.0.0.1").putBoolean("pro",true).putBoolean("classic",false).putBoolean("motionPlus",false).remove("layout.pro").remove("layout.classic").putInt("player",3).commit();
   PhoneInputProbe.automation=getUiAutomation();PhoneInputProbe.automation.setRotation(UiAutomation.ROTATION_FREEZE_90);
   Activity activity=startActivitySync(new Intent().setClassName("com.brian.wiiuremote","com.brian.wiiuremote.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   Thread.sleep(1000);
   android.view.accessibility.AccessibilityNodeInfo root=PhoneInputProbe.automation.getRootInActiveWindow();
   boolean clicked=false;for(android.view.accessibility.AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText("CONNECT")){
     if(n.getText()!=null&&n.getText().toString().equalsIgnoreCase("Connect")){clicked=n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);break;}
   }
   if(!clicked)throw new AssertionError("Connect button not found");Thread.sleep(500);
   runOnMainSync(()->{android.view.View view=activity.findViewById(android.R.id.content);PhoneInputProbe.viewWidth=view.getWidth();PhoneInputProbe.viewHeight=view.getHeight();});
   android.view.ViewGroup container=(android.view.ViewGroup)activity.findViewById(android.R.id.content);
   PhoneInputProbe.dispatcher=event->runOnMainSync(()->container.getChildAt(0).dispatchTouchEvent(event));
   PhoneInputProbe.expectedMode=1;PhoneInputProbe.main(new String[0]);
   invoke(activity,"toggleMode",null);Thread.sleep(500);
   PhoneInputProbe.expectedMode=2;PhoneInputProbe.main(new String[0]);
   invoke(activity,"toggleMode",null);Thread.sleep(500);
   motionAndPortraitCheck(activity);
   invoke(activity,"toggleMode",null);invoke(activity,"toggleMode",null);Thread.sleep(500);
   PhoneInputProbe.automation.setRotation(UiAutomation.ROTATION_FREEZE_90);Thread.sleep(500);
   dimensions(activity);
   String oldLayout=getTargetContext().getSharedPreferences("MainActivity",0).getString("layout.pro",null);
   try{editorCheck(activity);}
   finally{android.content.SharedPreferences.Editor prefs=getTargetContext().getSharedPreferences("MainActivity",0).edit();if(oldLayout==null)prefs.remove("layout.pro");else prefs.putString("layout.pro",oldLayout);prefs.commit();}
   result.putString("result","PASS: Pro and Classic five-contact input, releases, portrait Remote layout, live gyro MotionPlus packets, and layout editor persistence");resultCode=Activity.RESULT_OK;
  }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));resultCode=Activity.RESULT_CANCELED;}
  finally {
   android.content.SharedPreferences.Editor restore=settings.edit().clear();
   for(java.util.Map.Entry<String,?> entry:original.entrySet()) {
    Object value=entry.getValue();String key=entry.getKey();
    if(value instanceof String)restore.putString(key,(String)value);
    else if(value instanceof Boolean)restore.putBoolean(key,(Boolean)value);
    else if(value instanceof Integer)restore.putInt(key,(Integer)value);
    else if(value instanceof Long)restore.putLong(key,(Long)value);
    else if(value instanceof Float)restore.putFloat(key,(Float)value);
   }
   restore.commit();
   if(PhoneInputProbe.automation!=null)PhoneInputProbe.automation.setRotation(UiAutomation.ROTATION_UNFREEZE);
  }
  finish(resultCode,result);
 }

 private void dimensions(Activity activity) {
  runOnMainSync(()->{android.view.View v=activity.findViewById(android.R.id.content);PhoneInputProbe.viewWidth=v.getWidth();PhoneInputProbe.viewHeight=v.getHeight();});
 }
 private void motionAndPortraitCheck(Activity activity)throws Exception {
  PhoneInputProbe.automation.setRotation(UiAutomation.ROTATION_FREEZE_0);
  runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
  Thread.sleep(800);dimensions(activity);
  if(PhoneInputProbe.viewHeight<=PhoneInputProbe.viewWidth)throw new AssertionError("Remote did not rotate to portrait");
  Object pad=field(activity,"pad");android.graphics.RectF a=button(pad,"A");
  if(a.bottom>928 || a.right>452)throw new AssertionError("Portrait control out of bounds");
  try(java.net.DatagramSocket server=new java.net.DatagramSocket(26760,java.net.InetAddress.getByName("127.0.0.1"))) {
   server.setSoTimeout(100);boolean valid=false,held=false;
   PhoneInputProbe.down=SystemClock.uptimeMillis();PhoneInputProbe.send(android.view.MotionEvent.ACTION_DOWN,point(a.centerX(),a.centerY()));
   long deadline=SystemClock.uptimeMillis()+2500;
   while(SystemClock.uptimeMillis()<deadline)try {
    byte[] bytes=new byte[128];java.net.DatagramPacket packet=new java.net.DatagramPacket(bytes,bytes.length);server.receive(packet);
    server.send(new java.net.DatagramPacket(new byte[]{'A','N','W','A'},4,packet.getAddress(),packet.getPort()));
    if(packet.getLength()==64 && bytes[4]==2 && bytes[23]==3) {
     valid|=bytes[46]==1;held|=(java.nio.ByteBuffer.wrap(bytes).getInt(8)&0x800)!=0;
    }
   }catch(java.net.SocketTimeoutException expected){}
   PhoneInputProbe.send(android.view.MotionEvent.ACTION_UP,point(a.centerX(),a.centerY()));
   if(!valid || !held)throw new AssertionError("Portrait MotionPlus missing real gyro or A input: gyro="+valid+" button="+held);
  }
 }
 private Object field(Object object,String name)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 private void invoke(Activity activity,String name,Boolean value)throws Exception{
  java.lang.reflect.Method m=value==null?activity.getClass().getDeclaredMethod(name):activity.getClass().getDeclaredMethod(name,boolean.class);m.setAccessible(true);
  runOnMainSync(()->{try{if(value==null)m.invoke(activity);else m.invoke(activity,value);}catch(Exception e){throw new RuntimeException(e);}});
 }
 private android.graphics.RectF button(Object pad,String label)throws Exception{
  for(Object control:(java.util.List<?>)field(pad,"controls"))if(label.equals(field(control,"label")))return new android.graphics.RectF((android.graphics.RectF)field(control,"rect"));
  throw new AssertionError("Control not found: "+label);
 }
 private float[][] point(float x,float y){float w=PhoneInputProbe.viewHeight>PhoneInputProbe.viewWidth?460:960,h=w==460?960:460;float scale=Math.min(PhoneInputProbe.viewWidth/w,PhoneInputProbe.viewHeight/h);return new float[][]{{x*scale+(PhoneInputProbe.viewWidth-w*scale)/2,y*scale+(PhoneInputProbe.viewHeight-h*scale)/2}};}
 private void drag(float x,float y,float dx,float dy)throws Exception{
  PhoneInputProbe.down=SystemClock.uptimeMillis();PhoneInputProbe.send(android.view.MotionEvent.ACTION_DOWN,point(x,y));
  PhoneInputProbe.send(android.view.MotionEvent.ACTION_MOVE,point(x+dx,y+dy));PhoneInputProbe.send(android.view.MotionEvent.ACTION_UP,point(x+dx,y+dy));Thread.sleep(150);
 }
 private void editorCheck(Activity activity)throws Exception{
  invoke(activity,"beginEdit",null);Thread.sleep(250);Object pad=field(activity,"pad");
  android.graphics.RectF before=button(pad,"A");drag(before.centerX(),before.centerY(),-32,24);
  android.graphics.RectF moved=button(pad,"A");if(Math.abs(moved.left-before.left)<24)throw new AssertionError("Editor did not move A");
  drag(moved.right-4,moved.bottom-4,24,24);android.graphics.RectF resized=button(pad,"A");
  if(resized.width()<moved.width()+16)throw new AssertionError("Editor did not resize A");
  invoke(activity,"finishEdit",true);Thread.sleep(200);
  if(!getTargetContext().getSharedPreferences("MainActivity",0).contains("layout.pro"))throw new AssertionError("Layout not persisted");
  invoke(activity,"beginEdit",null);Thread.sleep(200);android.graphics.RectF reloaded=button(pad,"A");
  if(!reloaded.equals(resized))throw new AssertionError("Saved layout changed on reload");
  drag(reloaded.centerX(),reloaded.centerY(),24,16);invoke(activity,"finishEdit",false);Thread.sleep(200);
  invoke(activity,"beginEdit",null);Thread.sleep(200);
  if(!button(pad,"A").equals(resized))throw new AssertionError("Cancel overwrote saved layout");
  invoke(activity,"finishEdit",false);
 }
}
