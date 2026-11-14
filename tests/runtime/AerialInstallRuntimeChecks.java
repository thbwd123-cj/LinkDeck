package cn.linkdeck.qa;
import android.os.*;import cn.linkdeck.adb.*;import java.io.*;
/** Only executes against the disposable emulator through instrumentation shell permissions. */
public class AerialInstallRuntimeChecks extends RuntimeChecks {
 String shell(String command)throws Exception {
  File script=new File(getTargetContext().getExternalFilesDir(null),"qa08-command.sh");
  try(OutputStream out=new FileOutputStream(script)){out.write(("#!/system/bin/sh\n"+command+" 2>&1\n").getBytes("UTF-8"));}
  ParcelFileDescriptor p=getUiAutomation().executeShellCommand("sh "+script.getAbsolutePath());
  try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(p);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}
 }
 void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);passes++;Bundle b=new Bundle();b.putString("stream","PASS: "+msg+"\n");sendStatus(0,b);}
 public void onStart(){Bundle result=new Bundle();File apk=null;try {
  if(!"1".equals(shell("getprop ro.kernel.qemu").trim()))throw new IOException("This optional install test is restricted to a disposable emulator");
  // Remove only the upstream test fixture from this disposable emulator, never a user TV.
  shell("pm uninstall "+AerialSupport.FULL);
  apk=new File(getTargetContext().getExternalFilesDir(null),"qa08-aerial-install.apk");
  try(InputStream in=getContext().getAssets().open("aerial-standard.apk");OutputStream out=new FileOutputStream(apk)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  android.content.pm.PackageInfo pi=getTargetContext().getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),0);
  check(pi!=null&&AerialSupport.FULL.equals(pi.packageName),"official APK identity parsed on Android runtime");
  ApkTransfer.Device device=new ApkTransfer.Device(){public String shell(String c)throws Exception{return AerialInstallRuntimeChecks.this.shell(c);}public void push(File f,String remote)throws Exception{AerialInstallRuntimeChecks.this.shell("cp "+AdbClient.quote(f.getAbsolutePath())+" "+AdbClient.quote(remote));}};
  AerialInstaller.Installed before=AerialInstaller.read(device,AerialSupport.FULL);
  String installed=AerialInstaller.install(apk,AerialSupport.FULL,pi.versionCode,before,device);
  check(installed.contains("已确认电视安装版本"),"official Aerial installation verified with real emulator PackageManager");
  AerialInstaller.Installed again=AerialInstaller.read(device,AerialSupport.FULL);check(again.present&&again.code==pi.versionCode,"installed code matches actual official APK");
  String replaced=AerialInstaller.install(apk,AerialSupport.FULL,pi.versionCode,again,device);check(replaced.contains("已确认"),"pm install -r covering existing Aerial verified");
  check(shell("find /data/local/tmp -maxdepth 1 -name 'linkdeck_phone_*.apk'").trim().isEmpty(),"temporary remote installation APKs cleaned up");
  result.putString("stream","AERIAL REAL EMULATOR INSTALL PASSED: "+passes+" checks.\n");finish(-1,result);
 }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));finish(0,result);}finally{if(apk!=null)apk.delete();}}
}
