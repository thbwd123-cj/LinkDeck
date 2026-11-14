package cn.linkdeck.qa;
import android.app.*;import android.content.*;import android.os.*;import android.widget.*;import cn.linkdeck.adb.*;
/** Final-binary smoke and screenshots from real native controls, no synthetic device report. */
public class Preview13Checks extends AerialUiChecks {
 public void onStart(){Bundle result=new Bundle();try{
  getTargetContext().getSharedPreferences("linkdeck",0).edit().putBoolean("resume_connection",false).putString("connect_mode","legacy").putString("host","").putString("port","5555").putBoolean("reduce_motion",false).commit();
  activity=startActivitySync(new Intent().setClassName("cn.linkdeck.adb","cn.linkdeck.adb.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));check(getTargetContext().getPackageManager().getPackageInfo("cn.linkdeck.adb",0).versionName.equals("0.13.0"),"final version opens its native dashboard");shot("v013-home");call("connectionWizard",new Class[]{});waitFor("final wizard offers both transport types",()->dialogHas("传统 ADB")&&dialogHas("加密 ADB"));shot("v013-wizard");dialogTap("取消");
  runOnMainSync(()->{try{((EditText)field("hostInput")).setText("127.0.0.1");((EditText)field("portInput")).setText("5555");}catch(Exception e){throw new RuntimeException(e);}});tap("连接设备");waitFor("final binary connects to real emulator adbd",()->client()!=null&&client().isConnected()&&!(Boolean)field("busy"));DeviceCapabilities.Snapshot actual=(DeviceCapabilities.Snapshot)field("capabilitySnapshot");check(actual!=null&&actual.user==0&&actual.sdk==33,"final native snapshot recognizes the actual Android 13 current user");call("capabilityReport",new Class[]{});waitFor("final report is displayed",()->dialogHas("设备能力报告"));shot("v013-capabilities");dialogTap("关闭");tap("断开连接");result.putString("stream","FINAL 013 NATIVE SMOKE PASSED: "+passes+" checks.\n");finish(-1,result);
 }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(0,result);}}
}
