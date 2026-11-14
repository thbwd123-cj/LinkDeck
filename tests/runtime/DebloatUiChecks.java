package cn.linkdeck.qa;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.view.accessibility.AccessibilityNodeInfo;
import cn.linkdeck.adb.*;import java.io.*;import java.net.*;import java.nio.*;import java.util.*;import java.util.concurrent.*;import java.util.regex.*;
/** Native UI and accessibility with a synthetic ADB peer. No actual TV is modified. */
public final class DebloatUiChecks extends AerialUiChecks {
 static int code(String s){byte[] b=s.getBytes();return (b[0]&255)|((b[1]&255)<<8)|((b[2]&255)<<16)|((b[3]&255)<<24);}
 static byte[] exact(InputStream in,int n)throws Exception{byte[] b=new byte[n];int off=0;while(off<n){int k=in.read(b,off,n-off);if(k<0)throw new EOFException();off+=k;}return b;}
 static void send(OutputStream out,String command,int a,int b,byte[] data)throws Exception{int sum=0;for(byte v:data)sum+=v&255;ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(code(command)).putInt(a).putInt(b).putInt(data.length).putInt(sum).putInt(code(command)^-1);out.write(h.array());out.write(data);out.flush();}
 volatile boolean installed=true,deny=true;final List<String> commands=new CopyOnWriteArrayList<>();
 String answer(String command){
  if(command.contains("pm list packages -s --user 0"))return "package:android\n"+(installed?"package:com.ktcp.video\n":"");
  if(command.contains("pm list packages --user 0"))return "package:android\n"+(installed?"package:com.ktcp.video\n":"");
  if(command.contains("pm path 'com.ktcp.video'"))return "package:/system/app/test/base.apk\n";
  if(command.contains("dumpsys package 'com.ktcp.video'"))return "Packages:\n Package [com.ktcp.video] (aaa):\n  userId=10062\n  versionCode=2\n";
  if(command.contains("pm help"))return "install-existing [--user USER_ID] PACKAGE\n";
  if(command.contains("pm uninstall -k --user 0 'com.ktcp.video'")){if(deny)return "Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]\n";installed=false;return "Success\n";}
  if(command.contains("pm install-existing --user 0 'com.ktcp.video'")){installed=true;return "Package com.ktcp.video installed for user: 0\n";}
  return "";
 }
 int mutationCount(){int n=0;for(String x:commands)if(x.contains("pm uninstall"))n++;return n;}
 boolean selectCandidate()throws Exception{
  AccessibilityNodeInfo root=appRoot();if(root==null)return false;
  for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText("云视听极光")){
   if(!n.isCheckable())continue;CharSequence d=n.getContentDescription();
   check(d!=null&&d.toString().contains("云视听极光")&&d.toString().contains("未选择"),"checkbox announces name and state");
   check(!n.isChecked(),"candidate starts unchecked");return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
  }return false;
 }
 public void onStart(){Bundle result=new Bundle();AdbClient client=null;ServerSocket listener=null;try{
  getTargetContext().getSharedPreferences("linkdeck",0).edit().putBoolean("resume_connection",false).putBoolean("reduce_motion",true).commit();
  activity=startActivitySync(new Intent().setClassName("cn.linkdeck.adb","cn.linkdeck.adb.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
  call("show",new Class[]{int.class},2);Thread.sleep(500);tap("去臃肿");waitFor("offline entry triggers existing connection prompt",()->(Boolean)field("connectionPromptVisible"));waitFor("offline prompt is visible",()->dialogHas("连接后即可操作"));dialogTap("继续浏览");
  listener=new ServerSocket(0);final ServerSocket peer=listener;
  Thread server=new Thread(()->{try(Socket socket=peer.accept()){InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();boolean hello=false;while(true){ByteBuffer h=ByteBuffer.wrap(exact(in,24)).order(ByteOrder.LITTLE_ENDIAN);int cmd=h.getInt(),local=h.getInt(),remote=h.getInt(),size=h.getInt();byte[] data=exact(in,size);if(!hello){send(out,"CNXN",0x01000000,4096,"device::\\0".replace("\\0","\0").getBytes("UTF-8"));hello=true;continue;}if(cmd==code("OPEN")){String service=new String(data,"UTF-8");commands.add(service);Matcher marker=Pattern.compile("__LINKDECK_[0-9a-f]+__").matcher(service);if(!marker.find())throw new IOException("no status marker");send(out,"OKAY",local+100,local,new byte[0]);String text=answer(service)+"\n"+marker.group()+"0\n";send(out,"WRTE",local+100,local,text.getBytes("UTF-8"));send(out,"CLSE",local+100,local,new byte[0]);}}}catch(Exception ignored){}});server.setDaemon(true);server.start();
  client=new AdbClient();client.connect("127.0.0.1",listener.getLocalPort(),new AdbIdentity(new File(getTargetContext().getCacheDir(),"qa194-"+UUID.randomUUID())));
  set("adb",client);set("host","127.0.0.1");set("device","测试电视");set("appLoaded",true);@SuppressWarnings("unchecked") Map<String,String> properties=(Map<String,String>)field("properties");properties.clear();properties.put("ro.serialno","DEBLOATTEST123456");properties.put("ro.product.model","DEBLOATTESTMODEL");
  getTargetContext().getSharedPreferences("linkdeck",0).edit().remove(DebloatIdentity.key("DEBLOATTEST123456","DEBLOATTESTMODEL")).commit();
  call("show",new Class[]{int.class},2);tap("去臃肿");waitFor("connected curated candidate and restoration section visible",()->dialogHas("云视听极光")&&dialogHas("已卸载与恢复"));
  dialogTap("卸载此项");Thread.sleep(400);check(mutationCount()==0,"unchecked candidate sends no uninstall");
  check(selectCandidate(),"candidate can be selected through accessibility action");Thread.sleep(300);dialogTap("卸载此项");waitFor("per-item confirmation names package and recovery",()->dialogHas("确认卸载 云视听极光")&&dialogHas("com.ktcp.video")&&dialogHas("卸载后可在下方恢复"));dialogTap("取消");check(mutationCount()==0,"cancelled confirmation never uninstalls");
  dialogTap("卸载此项");dialogTap("确认卸载");waitFor("failure displays exact TV response",()->dialogHas("DELETE_FAILED_DEVICE_POLICY_MANAGER"));check(installed&&mutationCount()==1,"failed uninstall does not remove the app");dialogTap("知道了");
  deny=false;dialogTap("卸载此项");dialogTap("确认卸载");waitFor("success requires a fresh package-list verification",()->dialogHas("卸载复查")&&dialogHas("用户 0 的应用列表中已无此应用"));check(!installed&&mutationCount()==2,"successful mock uninstall affects one chosen package");dialogTap("返回去臃肿");waitFor("uninstalled candidate appears in recovery",()->dialogHas("已卸载与恢复")&&dialogHas("恢复此项"));dialogTap("恢复此项");waitFor("restore verifies installed status",()->dialogHas("恢复复查")&&dialogHas("已恢复到用户 0"));check(installed,"mock restoration reinstates the package");
  result.putString("stream","DEBLOAT NATIVE UI PASSED: "+passes+" checks, synthetic ADB only.\n");finish(-1,result);
 }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));finish(0,result);}finally{if(client!=null)client.close();try{if(listener!=null)listener.close();}catch(Exception ignored){}}
 }
}
