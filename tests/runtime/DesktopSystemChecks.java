package cn.linkdeck.qa;
import android.os.*;import cn.linkdeck.adb.*;import java.io.*;import java.util.*;
public class DesktopSystemChecks extends RuntimeChecks {
 String shell(String command)throws Exception{File script=new File(getTargetContext().getExternalFilesDir(null),"qa09-shell.sh");try(OutputStream out=new FileOutputStream(script)){out.write(("#!/system/bin/sh\n"+command+" 2>&1\n").getBytes("UTF-8"));}ParcelFileDescriptor p=getUiAutomation().executeShellCommand("sh "+script.getAbsolutePath());try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(p);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
 void check(boolean b,String text){if(!b)throw new AssertionError(text);passes++;Bundle r=new Bundle();r.putString("stream","PASS: "+text+"\n");sendStatus(0,r);}
 public void onStart(){Bundle result=new Bundle();boolean successful=false;boolean installed=false;try{
  if(!shell("getprop ro.kernel.qemu").trim().equals("1"))throw new IOException("Disposable emulator only");
  if(shell("pm path app.orchard.tv").startsWith("package:"))throw new IOException("Use a clean emulator; this test will not replace an existing Aerial installation");
  File apk=new File(getTargetContext().getExternalFilesDir(null),"qa-desktop-fixture.apk");try(InputStream in=getContext().getAssets().open("desktop-fixture.apk");OutputStream out=new FileOutputStream(apk)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  shell("cp "+apk.getAbsolutePath()+" /data/local/tmp/linkdeck_desktop_fixture09.apk");String installResult=shell("pm install -r /data/local/tmp/linkdeck_desktop_fixture09.apk");
  check(installResult.contains("Success"),"isolated desktop permission fixture installed: "+installResult.trim());installed=true;
  shell("pm grant app.orchard.tv android.permission.WRITE_SECURE_SETTINGS");shell("pm revoke app.orchard.tv android.permission.WRITE_SECURE_SETTINGS");
  DesktopAssistant.Device d=c->shell(c);DesktopAssistant.Snapshot before=DesktopAssistant.inspect(d,"emulator-fixture");
  check(before.installed&&Boolean.parseBoolean(before.values.get(DesktopAssistant.PERMISSION))==false&&before.known.contains(DesktopAssistant.PERMISSION),"actual Android permission dump recognizes revoked state");
  DesktopBackupStore store=new DesktopBackupStore(new File(getTargetContext().getFilesDir(),"qa09-real-backup"));
  DesktopAssistant.Snapshot after=DesktopAssistant.repair(d,store,"emulator-fixture",before,Arrays.asList(DesktopAssistant.PERMISSION));
  check("true".equals(after.values.get(DesktopAssistant.PERMISSION)),"real pm grant is checked after durable backup");check(DesktopAssistant.hasBackup(store.load(before.id)),"real-device change has an app-private recoverable backup");
  DesktopAssistant.Snapshot restored=DesktopAssistant.restore(d,store,"emulator-fixture",after);check("false".equals(restored.values.get(DesktopAssistant.PERMISSION)),"real pm revoke restores original permission state");check(!DesktopAssistant.hasBackup(store.load(before.id)),"verified native restore completes backup transaction");
  result.putString("stream","DESKTOP REAL SYSTEM RESTORE PASSED: "+passes+" checks.\n");successful=true;
 }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));}finally{try{if(installed)shell("pm uninstall app.orchard.tv");shell("rm -f /data/local/tmp/linkdeck_desktop_fixture09.apk");}catch(Exception ignored){}}finish(successful?-1:0,result);}
}
