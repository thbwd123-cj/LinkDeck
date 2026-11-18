package cn.linkdeck.qa;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import cn.linkdeck.adb.*;import java.io.*;import java.lang.reflect.*;import org.json.*;
/** Synthetic UI workflow. No household TV connection or settings changes. */
public class DesktopFixUiChecks extends DesktopUiChecks {
 public void onStart(){Bundle result=new Bundle();try{
  getTargetContext().getSharedPreferences("linkdeck",0).edit().putBoolean("resume_connection",false).commit();
  activity=startActivitySync(new Intent().setClassName("cn.linkdeck.adb","cn.linkdeck.adb.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(1500);
  DesktopAssistant.Snapshot s=new DesktopAssistant.Snapshot();s.profile=DesktopAssistant.AERIAL;s.userId=0;s.id=DesktopAssistant.identity("fixture","UI-FIX-093","fake/build","测试电视");s.model="测试电视（界面预览，未连接）";s.brand="coocaa";s.sdk=22;s.release="5.1.1";s.installed=true;s.code=93;s.version="1.73";s.guardDeclared=true;s.listenerDeclared=true;s.bound=false;s.bindingEvidence="测试样例：绑定列表为空";s.crashNote="测试样例：崩溃日志未确认";
  for(String k:new String[]{DesktopAssistant.ACCESS,DesktopAssistant.ENABLED,DesktopAssistant.NOTIFY,DesktopAssistant.PERMISSION})s.known.add(k);
  s.values.put(DesktopAssistant.ACCESS,DesktopAssistant.GUARD);s.values.put(DesktopAssistant.ENABLED,"0");s.values.put(DesktopAssistant.NOTIFY,DesktopAssistant.LISTENER);s.values.put(DesktopAssistant.PERMISSION,"true");
  AdbClient stub=new AdbClient();Field connected=AdbClient.class.getDeclaredField("connected");connected.setAccessible(true);connected.setBoolean(stub,true);set("adb",stub);set("device","界面测试（模拟连接）");setState("aerialDesktop","client",stub);setState("aerialDesktop","snapshot",s);setState("aerialDesktop","endpoint",field("host")+":"+getTargetContext().getSharedPreferences("linkdeck",0).getString("port","5555"));
  DesktopBackupStore store=new DesktopBackupStore(new File(getTargetContext().getCacheDir(),"qa093-backups"));set("desktopStore",store);
  JSONObject backup=new JSONObject().put("id",s.id).put("restored",false).put("changes",new JSONArray().put(new JSONObject().put("key",DesktopAssistant.ACCESS).put("before",JSONObject.NULL).put("after",DesktopAssistant.GUARD).put("state","pending")));store.save(s.id,backup);
  call("showDesktopPanel",new Class[]{DesktopAssistant.Profile.class,Class.forName("cn.linkdeck.adb.MainActivity$DesktopState")},DesktopAssistant.AERIAL,field("aerialDesktop"));modalTap("恢复本 App 的修改");waitFor("uncertain backup opens its own review dialog",()->dialogHas("先处理未确认备份")&&dialogHas("保留当前设置"));
  check(dialogHas("不会自动回滚"),"review explains uncertainty instead of claiming helper ownership");shot("v093-uncertain-backup");dialogTap("取消");
  check(store.load(s.id).getJSONArray("changes").getJSONObject(0).optString("state").equals("pending"),"cancel retains old pending backup unchanged");
  Class<?> work=Class.forName("cn.linkdeck.adb.MainActivity$DesktopWork");Constructor<?> fail=DesktopAssistant.VerificationFailure.class.getDeclaredConstructor(DesktopAssistant.Snapshot.class,String.class,String.class);fail.setAccessible(true);
  Object failure=fail.newInstance(s,"未生效 / 部分完成","最终复查开关为 0，已停止后续修改；测试夹具。");
  Object proxy=java.lang.reflect.Proxy.newProxyInstance(work.getClassLoader(),new Class[]{work},(o,m,a)->{if(m.getName().equals("run"))throw (Throwable)failure;return null;});
  call("desktopAction",new Class[]{String.class,boolean.class,work,DesktopAssistant.Profile.class,Class.forName("cn.linkdeck.adb.MainActivity$DesktopState")},"修复 Aerial 桌面",true,proxy,DesktopAssistant.AERIAL,field("aerialDesktop"));
  waitFor("incomplete repair shows failure rather than success",()->dialogHas("修复 Aerial 桌面未完成")&&dialogHas("最终复查开关为 0")&&!(Boolean)field("busy"));
  File report=new File(getTargetContext().getFilesDir(),"desktop_last_report.txt");check(read(report).contains("本次结果：未生效 / 部分完成")&&read(report).contains("最终复查开关为 0"),"incomplete repair persists actual outcome for export");
  check(((DesktopAssistant.Snapshot)stateField("aerialDesktop","snapshot")).values.get(DesktopAssistant.ENABLED).equals("0"),"cached panel snapshot is refreshed on verification failure");
  JSONArray logs=new JSONArray(getTargetContext().getSharedPreferences("linkdeck",0).getString("logs","[]"));check(!logs.getJSONObject(0).getBoolean("ok"),"task history records incomplete repair as unsuccessful");
  shot("v093-incomplete-repair");dialogTap("知道了");call("showDesktopPanel",new Class[]{DesktopAssistant.Profile.class,Class.forName("cn.linkdeck.adb.MainActivity$DesktopState")},DesktopAssistant.AERIAL,field("aerialDesktop"));waitFor("panel exposes final incomplete result",()->dialogHas("未生效 / 部分完成")&&dialogHas("守护配置：未完整开启"));
  result.putString("stream","DESKTOP FIX NATIVE UI PASSED: "+passes+" checks.\n");finish(-1,result);
 }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));finish(0,result);}}
}
