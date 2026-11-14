package cn.linkdeck.qa;
import android.app.*;import android.content.*;import android.os.*;import android.widget.*;import cn.linkdeck.adb.*;import org.json.*;import java.lang.reflect.*;import java.io.*;import java.util.*;
public class WorkflowUiChecks extends RuntimeChecks {
 void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);passes++;Bundle b=new Bundle();b.putString("stream","PASS: "+msg+"\n");sendStatus(0,b);}
 JSONObject task(TaskJournal j,String id){JSONArray a=j.snapshot();for(int i=0;i<a.length();i++)if(id.equals(a.optJSONObject(i).optString("id")))return a.optJSONObject(i);throw new AssertionError("task missing");}
 TaskJournal journal(android.content.SharedPreferences p)throws Exception{Constructor<?> c=TaskJournal.class.getDeclaredConstructor(android.content.SharedPreferences.class);c.setAccessible(true);return (TaskJournal)c.newInstance(p);}
 public void onStart(){Bundle result=new Bundle();try{
  JSONArray book=DeviceBook.save(new JSONArray(),"192.0.2.10","5555","TV","客厅电视",true);
  book=DeviceBook.save(book,"192.0.2.10","5555","New Model",null,true);
  check(book.length()==1&&DeviceBook.label(book.getJSONObject(0)).equals("客厅电视"),"reconnect keeps alias and deduplicates endpoint");
  book=DeviceBook.save(book,"192.0.2.10","5556","Other",null,true);book.getJSONObject(1).put("pinned",true);book=DeviceBook.sorted(book);
  check(book.length()==2&&book.getJSONObject(0).optBoolean("pinned"),"separate ports retained and pin sorted first");
  check(DeviceBook.remove(book,"192.0.2.10","5555").length()==1,"remove only matching endpoint");
  android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("qa07",0);prefs.edit().clear().commit();TaskJournal j=journal(prefs);
  String id=j.enqueue("传输并安装 APK","192.0.2.10","5555","");check(task(j,id).optString("state").equals("queued"),"new task queues persistently");
  j.begin(id);j.progress(id,"已传输 10 KB");check(task(j,id).optString("state").equals("running")&&task(j,id).optString("progress").contains("10 KB"),"running task progress updates");
  TaskJournal restored=journal(prefs);check(task(restored,id).optString("state").equals("interrupted")&&task(restored,id).optString("detail").contains("结果未知"),"restart does not invent installation success or replay tasks");
  String active=restored.enqueue("读取目录","192.0.2.10","5555","/sdcard");restored.clearFinished();check(restored.snapshot().length()==1&&task(restored,active)!=null,"clear history retains active tasks");
  restored.begin(active);restored.finish(active,false,"Failure [INSTALL_FAILED_OLDER_SDK]");TaskJournal again=journal(prefs);check(task(again,active).optString("detail").contains("INSTALL_FAILED_OLDER_SDK"),"failure detail survives journal reload");
  String lost=again.enqueue("测试任务","test","0","");again.interrupt(Collections.singleton(lost));check(task(again,lost).optString("state").equals("interrupted"),"activity teardown marks unfinished task unknown");
  for(int i=0;i<90;i++){String q=again.enqueue("只读测试","test","0","");again.finish(q,true,"ok");}check(again.snapshot().length()<=80,"finished task retention bounded");
  activity=startActivitySync(new Intent().setClassName("cn.linkdeck.adb","cn.linkdeck.adb.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(1000);
  ((android.content.SharedPreferences)field("prefs")).edit().remove("devices").apply();
  call("rememberDevice",new Class[]{String.class,String.class,String.class},"192.0.2.10","5555","测试电视（示例地址）");
  call("deviceBook",new Class[]{});Thread.sleep(500);waitFor("device manager opens native dialog",()->dialogHas("设备收藏夹"));shot("v07-devices");dialogTap("关闭");
  ((android.content.SharedPreferences)field("prefs")).edit().remove("devices").apply();
  call("diagnose",new Class[]{String.class,String.class},"127.0.0.1","1");
  waitFor("diagnostic task finishes offline",()->!(Boolean)field("busy"));Thread.sleep(1000);shot("v07-diagnostic");waitFor("diagnostic explains closed local test port",()->dialogHas("原始信息"));dialogTap("知道了");
  call("taskCenter",new Class[]{});Thread.sleep(500);waitFor("task center shows real diagnostic completion",()->dialogHas("连接诊断"));shot("v07-tasks");dialogTap("关闭");
  call("inspectApk",new Class[]{android.net.Uri.class},android.net.Uri.fromFile(new File(getTargetContext().getApplicationInfo().sourceDir)));
  waitFor("offline APK preflight completes",()->!(Boolean)field("busy"));Thread.sleep(1000);waitFor("offline install check never claims compatibility",()->dialogHas("安装前检查")&&dialogHas("暂不能完整判断"));shot("v07-preflight");dialogTap("取消");
  call("show",new Class[]{int.class},0);Thread.sleep(500);shot("v07-home");
  result.putString("stream","WORKFLOW UI QA PASSED: "+passes+" checks.\n");finish(-1,result);
 }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));finish(0,result);}}
}
