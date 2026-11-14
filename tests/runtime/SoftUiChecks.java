package cn.linkdeck.qa;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.lang.reflect.*;
public class SoftUiChecks extends RuntimeChecks {
 public void onStart(){Bundle b=new Bundle();try{
 getTargetContext().getSharedPreferences("linkdeck",0).edit().putBoolean("resume_connection",false).putBoolean("reduce_motion",false).commit();
 activity=startActivitySync(new Intent().setClassName("cn.linkdeck.adb","cn.linkdeck.adb.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
 Thread.sleep(2000);waitFor("soft backdrop loaded",()->((View)field("root")).getBackground().getClass().getSimpleName().equals("SoftBackdrop"));
 waitFor("soft alert dialog theme",()->{android.util.TypedValue v=new android.util.TypedValue();activity.getTheme().resolveAttribute(android.R.attr.alertDialogTheme,v,true);return v.resourceId==activity.getResources().getIdentifier("SoftDialog","style",activity.getPackageName());});
 shot("soft06-home");
 for(int i=1;i<5;i++){call("show",new Class[]{int.class},i);Thread.sleep(850);waitFor("page "+i+" settles visible",()->((View)field("body")).getAlpha()==1f&&((View)field("body")).getTranslationY()==0f);if(i==2)shot("soft06-apps");if(i==4)shot("soft06-tools");}
 call("show",new Class[]{int.class},0);Thread.sleep(600);
 call("dialog",new Class[]{String.class,String.class},"连接前的小提示","手机与电视连接同一 Wi-Fi 后，即可开始。\n请在电视上允许调试授权。");Thread.sleep(1200);shot("soft06-confirm");dialogTap("知道了");Thread.sleep(500);
 Class<?> c=Class.forName("cn.linkdeck.adb.CommandRecord");Constructor<?> ct=c.getDeclaredConstructor();ct.setAccessible(true);Object record=ct.newInstance();
 for(String key:new String[]{"title","target","time","command","output"}){Field f=c.getDeclaredField(key);f.setAccessible(true);f.set(record,key.equals("title")?"界面预览":key.equals("target")?"电视 · 预览示例":key.equals("time")?"界面演示，不代表真实连接":key.equals("command")?"getprop ro.product.model":"Hi3751V551\n\n这是排版示例，实际返回以连接设备后执行为准。\n");}
 call("showCommandResult",new Class[]{c},record);Thread.sleep(1200);Dialog d=(Dialog)field("commandResultDialog");waitFor("command result floats within screen",()->d.getWindow().getAttributes().width<activity.getResources().getDisplayMetrics().widthPixels);shot("soft06-result");runOnMainSync(()->d.dismiss());waitFor("result dismissal releases modal",()->field("commandResultDialog")==null);
 b.putString("stream","SOFT UI QA PASSED: "+passes+" checks.\n");finish(-1,b);
 }catch(Throwable t){b.putString("stream",android.util.Log.getStackTraceString(t));finish(0,b);}}
}
