package cn.linkdeck.qa;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import java.lang.reflect.*;
import java.util.*;

/** Emulator check of the accessibility tree. This is not a spoken TalkBack test. */
public final class Accessibility193Checks extends Instrumentation {
 private Activity activity;
 private final List<String> passed=new ArrayList<>();
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 private void showPage(int page)throws Exception{
  Method show=activity.getClass().getDeclaredMethod("show",int.class);show.setAccessible(true);
  final Exception[] error={null};runOnMainSync(()->{try{show.invoke(activity,page);}catch(Exception e){error[0]=e;}});if(error[0]!=null)throw error[0];waitForIdleSync();Thread.sleep(500);
 }
 private void collect(View root,List<View> views){views.add(root);if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),views);}}
 private View findByDescription(String name){List<View> views=new ArrayList<>();collect(activity.getWindow().getDecorView(),views);for(View v:views){CharSequence d=v.getContentDescription();if(d!=null&&d.toString().equals(name))return v;}return null;}
 private void checkControl(String name){View v=findByDescription(name);if(v==null)throw new AssertionError("Missing accessibility label: "+name);AccessibilityNodeInfo node=v.createAccessibilityNodeInfo();if(!node.isClickable())throw new AssertionError("Not clickable: "+name);if(!node.isEnabled())throw new AssertionError("Not enabled: "+name);if(!node.isFocusable())throw new AssertionError("Not focusable: "+name);if(!"android.widget.Button".equals(node.getClassName().toString()))throw new AssertionError("Wrong button role: "+name+" / "+node.getClassName());node.recycle();passed.add(name);}
 @Override public void onStart(){Bundle result=new Bundle();try{
  getTargetContext().getSharedPreferences("linkdeck",0).edit().putString("remote_mode","adb").putBoolean("resume_connection",false).commit();
  activity=startActivitySync(new Intent().setClassName("cn.linkdeck.adb","cn.linkdeck.adb.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
  Thread.sleep(1000);showPage(1);
  final String[] labels={"键盘输入","电源 / 待机","设置","向上","向下","向左","向右","确认","返回","主页","菜单","减小音量","增大音量","设备，导航","遥控，导航，当前页面","应用，导航","文件，导航","工具，导航"};
  for(String label:labels)checkControl(label);
  getTargetContext().getSharedPreferences("linkdeck",0).edit().putString("remote_mode","ir").remove("ir_profile").commit();showPage(1);
  for(String label:new String[]{"向上","向下","向左","向右","确认","返回","主页","菜单","减小音量","增大音量","静音","信号源","电源 / 待机"}){
   View v=findByDescription(label+"，当前码型没有此按键");if(v==null)throw new AssertionError("Missing disabled IR label: "+label);AccessibilityNodeInfo info=v.createAccessibilityNodeInfo();if(info.isEnabled())throw new AssertionError("IR key unexpectedly enabled: "+label);info.recycle();
  }
  getTargetContext().getSharedPreferences("linkdeck",0).edit().putString("remote_mode","adb").commit();showPage(1);
  View nav=findByDescription("应用，导航");final boolean[] clicked={false};runOnMainSync(()->clicked[0]=nav.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,null));waitForIdleSync();if(!clicked[0]||findByDescription("应用，导航，当前页面")==null)throw new AssertionError("Accessibility navigation action failed");
  result.putString("stream","PASS: 18 named, focusable button controls, 13 disabled IR labels and accessibility navigation action\n");finish(-1,result);
 }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));finish(0,result);}}
}
