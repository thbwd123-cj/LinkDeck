package cn.linkdeck.qa;
import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;import android.view.*;import cn.linkdeck.adb.*;import java.io.*;import java.lang.reflect.*;import org.json.*;
public class DesktopUiChecks extends RuntimeChecks {
 void check(boolean b,String text){if(!b)throw new AssertionError(text);passes++;Bundle r=new Bundle();r.putString("stream","PASS: "+text+"\n");sendStatus(0,r);}
 String read(File f)throws Exception{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
 void modalTap(String label)throws Exception{final boolean[] found={false};runOnMainSync(()->{try{Dialog modal=(Dialog)field("desktopDialog");View v=find(modal.getWindow().getDecorView(),label);if(v!=null)found[0]=v.performClick();}catch(Exception e){throw new RuntimeException(e);}});if(!found[0])throw new AssertionError("Missing desktop action "+label);}
 public void onStart(){Bundle result=new Bundle();try{
  getTargetContext().getSharedPreferences("linkdeck",0).edit().putBoolean("resume_connection",false).commit();
  activity=startActivitySync(new Intent().setClassName("cn.linkdeck.adb","cn.linkdeck.adb.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(1500);
  call("show",new Class[]{int.class},2);Thread.sleep(700);shot("v09-apps");tap("桌面助手");waitFor("offline desktop helper requests a connection",()->dialogHas("连接后即可操作"));dialogTap("继续浏览");
  DesktopAssistant.Snapshot s=new DesktopAssistant.Snapshot();s.userId=0;s.id=DesktopAssistant.identity("fixture","TEST-SERIAL09","fake/build","测试电视");s.model="测试电视（界面预览，未连接）";s.brand="coocaa";s.sdk=22;s.release="5.1";s.installed=true;s.code=93;s.version="1.73";s.guardDeclared=true;s.listenerDeclared=true;s.bound=null;s.bindingEvidence="旧系统仅提供服务描述（界面测试样例）";s.focus="com.coocaa.home/com.coocaa.home.Main";
  s.known.add(DesktopAssistant.ACCESS);s.values.put(DesktopAssistant.ACCESS,"other.reader/.Service");s.known.add(DesktopAssistant.ENABLED);s.values.put(DesktopAssistant.ENABLED,"0");s.known.add(DesktopAssistant.NOTIFY);s.values.put(DesktopAssistant.NOTIFY,"other.listener/.Service");s.known.add(DesktopAssistant.PERMISSION);s.values.put(DesktopAssistant.PERMISSION,"false");
  AdbClient stub=new AdbClient();Field f=AdbClient.class.getDeclaredField("connected");f.setAccessible(true);f.setBoolean(stub,true);set("adb",stub);set("device","界面测试（模拟连接）");set("desktopClient",stub);set("desktopSnapshot",s);set("desktopEndpoint",field("host")+":"+getTargetContext().getSharedPreferences("linkdeck",0).getString("port","5555"));
  call("showDesktopPanel",new Class[]{});waitFor("desktop panel distinguishes missing configuration and runtime binding",()->dialogHas("未确认（系统未提供可识别的组件信息）")&&dialogHas("未完整开启"));shot("v09-desktop");
  modalTap("修复缺失授权 / 守护");waitFor("repair selection exposes only checked candidates",()->dialogHas("选择要修复的项目"));dialogTap("查看改动并备份");waitFor("repair preview explains exact service and master-switch changes",()->dialogHas("备份并执行")&&dialogHas("总开关"));shot("v09-repair-preview");dialogTap("取消");
  modalTap("主页键人工验收");waitFor("physical HOME check is an explicit user-guided read",()->dialogHas("实体遥控器")&&dialogHas("已按主页"));shot("v09-home-guide");dialogTap("取消");
  Dialog panel=(Dialog)field("desktopDialog");runOnMainSync(()->panel.dismiss());set("adb",null);stub.close();
  String report=DesktopAssistant.report(s,null)+"\nUTF-8 完整导出检查：云海与桌面。\n";File latest=new File(getTargetContext().getFilesDir(),"desktop_last_report.txt"),pending=new File(getTargetContext().getFilesDir(),"desktop_pending_export.txt");
  call("writeDesktopFile",new Class[]{File.class,String.class},latest,report);call("writeDesktopFile",new Class[]{File.class,String.class},pending,report);
  call("writeDesktopFile",new Class[]{File.class,String.class},latest,"新的报告，不应替换已冻结的导出内容。");
  File exported=new File(getTargetContext().getCacheDir(),"qa-desktop-report.txt");call("onActivityResult",new Class[]{int.class,int.class,Intent.class},106,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(exported)));
  waitFor("offline diagnostic export preserves exact frozen UTF-8 content",()->exported.exists()&&read(exported).equals(report));check(!pending.exists(),"successful export clears pending payload");
  call("writeDesktopFile",new Class[]{File.class,String.class},pending,report);activity=startActivitySync(new Intent().setClassName("cn.linkdeck.adb","cn.linkdeck.adb.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
  File restored=new File(getTargetContext().getCacheDir(),"qa-desktop-report-restored.txt");call("onActivityResult",new Class[]{int.class,int.class,Intent.class},106,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(restored)));
  waitFor("export payload survives Activity replacement without a TV connection",()->restored.exists()&&read(restored).equals(report));
  call("writeDesktopFile",new Class[]{File.class,String.class},pending,report);call("onActivityResult",new Class[]{int.class,int.class,Intent.class},106,Activity.RESULT_CANCELED,null);check(!pending.exists(),"cancel clears frozen report without deleting historical report");
  DesktopBackupStore store=new DesktopBackupStore(new File(getTargetContext().getFilesDir(),"qa09-backups"));JSONObject j=new JSONObject().put("id",s.id).put("changes",new JSONArray()).put("restored",false);store.save(s.id,j);check(new DesktopBackupStore(new File(getTargetContext().getFilesDir(),"qa09-backups")).load(s.id).optString("id").equals(s.id),"AtomicFile backup identity survives store recreation");
  check(!report.contains("other.reader")&&!report.contains("TEST-SERIAL09"),"exported report excludes unrelated services and device serial");
  exported.delete();restored.delete();latest.delete();result.putString("stream","DESKTOP NATIVE UI/EXPORT PASSED: "+passes+" checks.\n");finish(-1,result);
 }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));finish(0,result);}}
}
