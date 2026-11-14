package cn.linkdeck.adb;
import java.io.*;import java.nio.file.*;import java.util.*;
public class AerialChecks {
 static int count;static void check(boolean b,String m){if(!b)throw new AssertionError(m);count++;System.out.println("PASS: "+m);}
 interface Action{void run()throws Exception;}
 static void reject(Action a,String m)throws Exception {try{a.run();throw new AssertionError("accepted "+m);}catch(IOException expected){check(true,m);}}
 static class Fake implements ApkTransfer.Device {
  long code;boolean present;boolean fail,verifyFail;int installs,pushes;List<String> calls=new ArrayList<>();
  Fake(boolean p,long c){present=p;code=c;}
  public String shell(String c){calls.add(c);if(c.startsWith("pm path"))return present?"package:/data/app/aerial/base.apk\n":"";
   if(c.startsWith("dumpsys package"))return "Package [app.orchard.tv]\n versionCode="+code+" minSdk=21\n versionName=test\n";
   if(c.startsWith("pm install")){installs++;if(fail)return "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]";if(!verifyFail){present=true;code=93;}return "Success\n";}return "";}
  public void push(File f,String r){pushes++;}
 }
 public static void main(String[] args)throws Exception {
  check(AerialSupport.variant(14).equals("lite")&&AerialSupport.variant(20).equals("lite"),"Android 4.x selects Lite");
  check(AerialSupport.variant(21).equals("standard")&&AerialSupport.variant(22).equals("standard"),"Android 5.0 and user 5.1 select full version");
  reject(()->AerialSupport.variant(0),"unknown SDK cannot guess support");reject(()->AerialSupport.variant(13),"Android before 4.0 rejected");
  reject(()->AerialSupport.validateVariant("guard",20),"guard rejected on Android 4.x");reject(()->AerialSupport.validateVariant("lite",22),"Lite rejected on Android 5.1");
  reject(()->AerialSupport.validateVariant("arbitrary",22),"unknown download variant rejected");
  check(AerialSupport.versionCode("versionCode=93 minSdk=21 targetSdk=34\n")==93,"parse installed version code independent of 1.73 release name");
  check(AerialSupport.versionName("  versionName=1.73\n").equals("1.73"),"parse installed version name");
  check(AerialSupport.decision(false,-1,93)==-1&&AerialSupport.decision(true,92,93)==1,"fresh install and update distinguished");
  check(AerialSupport.decision(true,93,93)==0&&AerialSupport.decision(true,94,93)==2,"same and newer installed versions do not auto-update");
  reject(()->AerialSupport.decision(true,-1,93),"unreadable installed version blocks overwrite");
  check(AerialSupport.limitations(22,"coocaa").contains("限制主页键"),"Coocaa support does not promise home takeover");
  File apk=Files.createTempFile("aerial-check-",".apk").toFile();Files.write(apk.toPath(),new byte[]{80,75,3,4});
  Fake fresh=new Fake(false,-1);AerialInstaller.Installed old=AerialInstaller.read(fresh,AerialSupport.FULL);
  check(AerialInstaller.install(apk,AerialSupport.FULL,93,old,fresh).contains("主页键与开机接管尚未验证"),"successful APK install separates desktop takeover");
  check(fresh.installs==1&&fresh.calls.get(fresh.calls.size()-1).startsWith("dumpsys package"),"installation re-reads actual TV version");
  Fake newer=new Fake(true,94);AerialInstaller.Installed n=AerialInstaller.read(newer,AerialSupport.FULL);reject(()->AerialInstaller.install(apk,AerialSupport.FULL,93,n,newer),"downgrade blocked before push");check(newer.pushes==0,"newer installed version never transferred");
  Fake changed=new Fake(true,92);AerialInstaller.Installed before=AerialInstaller.read(changed,AerialSupport.FULL);changed.code=94;reject(()->AerialInstaller.install(apk,AerialSupport.FULL,93,before,changed),"changed TV version invalidates prepared plan");
  Fake bad=new Fake(true,92);bad.fail=true;AerialInstaller.Installed badBefore=AerialInstaller.read(bad,AerialSupport.FULL);reject(()->AerialInstaller.install(apk,AerialSupport.FULL,93,badBefore,bad),"signature/install failure stops without uninstall");check(bad.calls.stream().noneMatch(c->c.contains("uninstall")||c.contains("settings put")||c.contains("disable")),"installer never uninstalls or changes desktop settings");
  Fake unconfirmed=new Fake(false,-1);unconfirmed.verifyFail=true;AerialInstaller.Installed unBefore=AerialInstaller.read(unconfirmed,AerialSupport.FULL);reject(()->AerialInstaller.install(apk,AerialSupport.FULL,93,unBefore,unconfirmed),"pm Success alone cannot claim verified install");
  apk.delete();System.out.println("AERIAL POLICY/INSTALLER PASSED: "+count+" checks.");
 }
}
