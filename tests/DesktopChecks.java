package cn.linkdeck.adb;
import java.io.*;import java.util.*;import org.json.*;
public class DesktopChecks {
 static int n;interface Test {void run()throws Exception;}
 static void ok(boolean b,String s){if(!b)throw new AssertionError(s);n++;System.out.println("PASS: "+s);}
 static void fail(Test t,String s)throws Exception{try{t.run();throw new AssertionError("accepted "+s);}catch(IOException expected){ok(true,s);}}
 static class Memory implements DesktopAssistant.Store {
  JSONObject data;int saves,failAt;
  public JSONObject load(String id)throws Exception{return data==null?null:new JSONObject(data.toString());}
  public void save(String id,JSONObject v)throws Exception{saves++;if(saves==failAt)throw new IOException("disk full");data=new JSONObject(v.toString());}
 }
 static class Fake implements DesktopAssistant.Device {
  Map<String,String> settings=new HashMap<>();boolean permission,bound,xiaomi,gone;boolean canBind=true;String model="Hi3751V551",serial="SERIAL-09",home="com.coocaa.home/com.coocaa.home.Main",focus="com.coocaa.home/.Main";int sdk=22;String ambiguous,blocked;List<String> writes=new ArrayList<>();
  Fake(){settings.put(DesktopAssistant.ACCESS,"other.reader/.Service");settings.put(DesktopAssistant.ENABLED,"1");settings.put(DesktopAssistant.NOTIFY,"other.notify/.Listener");}
  static String value(String s){if(s.startsWith("'"))return s.substring(1,s.length()-1).replace("'\\''","'");return s;}
  public void pause(long millis){}
  public String shell(String c)throws Exception {
   c=c.replace("settings --user 0 ","settings ");
   if(c.startsWith("getprop ")){switch(c.substring(8)){case "ro.product.model":return model;case "ro.product.brand":return "coocaa";case "ro.build.version.release":return "5.1";case "ro.build.version.sdk":return ""+sdk;case "ro.serialno":return serial;case "ro.build.fingerprint":return "coocaa/fixture/build09";}return "";}
   if(c.equals("pm path app.orchard.tv")){if(gone)throw new IOException("Error: package app.orchard.tv was not found");return "package:/data/app/aerial.apk";}
   if(c.equals("pm path com.mitv.tvhome"))return xiaomi?"package:/system/app/mitv.apk":"";
   if(c.startsWith("pm path "))return "";
   if(c.equals("dumpsys dropbox --print data_app_crash"))return "Drop box contents: 0 entries\nSearching for: data_app_crash\n";
   if(c.startsWith("cmd package query-activities"))return DesktopAssistant.HOME;
   if(c.startsWith("dumpsys package"))return "versionCode=93\n versionName=1.73\n app.orchard.tv.guard.HomeGuardService\n app.orchard.tv.guard.RevivalListener\n android.intent.category.HOME\n android.permission.WRITE_SECURE_SETTINGS: granted="+permission;
   if(c.startsWith("settings get secure ")){String k=c.substring(20);if(k.equals(blocked))throw new IOException("setting unavailable");String v=settings.get(k);return v==null?"null":v;}
   if(c.contains("resolve-activity"))return home==null?"No activity found":home;
   if(c.equals("dumpsys accessibility"))return "User state[attributes:{id=0, currentUser=true}\nBound services:{"+((bound||canBind&&DesktopAssistant.contains(settings.get(DesktopAssistant.ACCESS),DesktopAssistant.GUARD)&&"1".equals(settings.get(DesktopAssistant.ENABLED)))?"Service[id=app.orchard.tv/.guard.HomeGuardService]":"")+"}\nEnabled services:{app.orchard.tv/.guard.HomeGuardService}";
   if(c.startsWith("dumpsys window"))return "mCurrentFocus=Window{abc u0 "+focus+"}\n secret fake-account@example.invalid";
   writes.add(c);
   if(c.startsWith("pm grant"))permission=true;else if(c.startsWith("pm revoke"))permission=false;
   else if(c.startsWith("settings put secure ")){String[] p=c.split(" ",5);settings.put(p[3],value(p[4]));if(p[3].equals(ambiguous))throw new IOException("disconnected after write");}
   else if(c.startsWith("settings delete secure "))settings.remove(c.substring(23));
   else if(c.startsWith("cmd package set-home-activity"))home=value(c.substring(c.indexOf("--user 0 ")+9));
   return "";
  }
 }
 public static void main(String[] args)throws Exception {
  ok(Boolean.TRUE.equals(DesktopAssistant.permission("requested permissions:\n  android.permission.WRITE_SECURE_SETTINGS\n    grantedPermissions:\n      android.permission.WRITE_SECURE_SETTINGS\n    User 0: ...")),"legacy Android 5 permission list recognizes granted state");
  ok(Boolean.FALSE.equals(DesktopAssistant.permission("requested permissions:\n  android.permission.WRITE_SECURE_SETTINGS\n    grantedPermissions:\n      android.permission.INTERNET\n    User 0: ...")),"legacy Android 5 requested permission is not mistaken for granted");
  String deniedDump="Package [app.orchard.tv] (fixture):\n    installPermissionsFixed=true\n    requested permissions:\n      android.permission.WRITE_SECURE_SETTINGS\n    User 0: installed=true stopped=true\n";
  ok(Boolean.FALSE.equals(DesktopAssistant.permission(deniedDump)),"complete AOSP package dump recognizes omitted denied install permission");
  ok(DesktopAssistant.permission(deniedDump.replace("    User 0: installed=true stopped=true", "truncated"))==null,"truncated dump cannot infer an omitted permission");
  ok(DesktopAssistant.permission(deniedDump+"android.permission.WRITE_SECURE_SETTINGS: granted=vendor-format") == null,"unrecognized permission state remains unknown");
  Fake f=new Fake();Memory m=new Memory();DesktopAssistant.Snapshot s=DesktopAssistant.inspect(f,"192.0.2.1:5555");
  ok(f.writes.isEmpty(),"inspection is read-only");ok(!DesktopAssistant.candidates(s).contains(DesktopAssistant.DEFAULT),"legacy Android 5.1 skips automatic default desktop mutation");
  ok(Boolean.FALSE.equals(s.bound)&&s.known.contains(DesktopAssistant.ACCESS),"enabled service is not mistaken for bound/running service");
  ok(DesktopAssistant.contains("app.orchard.tv/.guard.HomeGuardService",DesktopAssistant.GUARD),"short and full service names match");
  ok(DesktopAssistant.bound("Bound services:{Service[id=other.guard.HomeGuardService]}\n Enabled services:{app.orchard.tv/.guard.HomeGuardService}")==false,"unrelated bound service cannot claim Aerial is running");
  ok(DesktopAssistant.bound("vendor format unknown")==null,"unknown accessibility dump remains unconfirmed");
  List<String> selected=Arrays.asList(DesktopAssistant.PERMISSION,DesktopAssistant.ACCESS,DesktopAssistant.NOTIFY);
  DesktopAssistant.Snapshot fixed=DesktopAssistant.repair(f,m,"192.0.2.1:5555",s,selected);
  ok(f.permission&&DesktopAssistant.contains(f.settings.get(DesktopAssistant.ACCESS),DesktopAssistant.GUARD),"repair grants permission and enables only Aerial guard");
  ok(f.settings.get(DesktopAssistant.ACCESS).contains("other.reader")&&f.settings.get(DesktopAssistant.NOTIFY).contains("other.notify"),"repair preserves existing accessibility and notification services");
  ok(m.data.getJSONArray("changes").length()==3,"durable backup records actual changed values only");
  f.settings.put(DesktopAssistant.ACCESS,f.settings.get(DesktopAssistant.ACCESS)+":later.reader/.Service");f.settings.put(DesktopAssistant.NOTIFY,f.settings.get(DesktopAssistant.NOTIFY)+":later.notify/.Listener");
  DesktopAssistant.restore(f,m,"192.0.2.1:5555",fixed);
  ok(!f.permission&&!DesktopAssistant.contains(f.settings.get(DesktopAssistant.ACCESS),DesktopAssistant.GUARD),"restore revokes only newly granted Aerial permission and guard");
  ok(f.settings.get(DesktopAssistant.ACCESS).contains("later.reader")&&f.settings.get(DesktopAssistant.NOTIFY).contains("later.notify"),"restore preserves services added after backup");
  ok("1".equals(f.settings.get(DesktopAssistant.ENABLED)),"restore keeps accessibility enabled for other services");
  ok(!DesktopAssistant.hasBackup(m.data),"verified restore marks transaction complete");
  Fake empty=new Fake();empty.settings.clear();empty.settings.put(DesktopAssistant.ENABLED,"0");Memory em=new Memory();DesktopAssistant.Snapshot es=DesktopAssistant.inspect(empty,"tv");DesktopAssistant.Snapshot ef=DesktopAssistant.repair(empty,em,"tv",es,Arrays.asList(DesktopAssistant.ACCESS));DesktopAssistant.restore(empty,em,"tv",ef);
  ok(!empty.settings.containsKey(DesktopAssistant.ACCESS)&&"0".equals(empty.settings.get(DesktopAssistant.ENABLED)),"restore deletes originally absent service key and restores empty-list master switch");
  Fake disk=new Fake();Memory bad=new Memory();bad.failAt=1;DesktopAssistant.Snapshot ds=DesktopAssistant.inspect(disk,"tv");fail(()->DesktopAssistant.repair(disk,bad,"tv",ds,Arrays.asList(DesktopAssistant.PERMISSION)),"backup failure aborts before any remote write");ok(disk.writes.isEmpty(),"no remote write when backup cannot be saved");
  Fake unknown=new Fake();Memory um=new Memory();DesktopAssistant.Snapshot us=DesktopAssistant.inspect(unknown,"tv");unknown.ambiguous=DesktopAssistant.ACCESS;fail(()->DesktopAssistant.repair(unknown,um,"tv",us,Arrays.asList(DesktopAssistant.ACCESS)),"ambiguous remote write stops remaining repair steps");
  ok(um.data.getJSONArray("changes").getJSONObject(0).optString("state").equals("unknown"),"pending write retained durably for interrupted recovery");unknown.ambiguous=null;fail(()->DesktopAssistant.restore(unknown,um,"tv",us),"ambiguous write requires explicit backup disposition");DesktopAssistant.preserveUncertain(unknown,um,"tv",us);ok(DesktopAssistant.contains(unknown.settings.get(DesktopAssistant.ACCESS),DesktopAssistant.GUARD),"preserving uncertain backup never removes the possibly external service");
  Fake other=new Fake();Memory om=new Memory();DesktopAssistant.Snapshot os=DesktopAssistant.inspect(other,"tv");other.serial="DIFFERENT-DEVICE";fail(()->DesktopAssistant.repair(other,om,"tv",os,Arrays.asList(DesktopAssistant.PERMISSION)),"different TV identity blocks repair");ok(other.writes.isEmpty(),"wrong-device validation has no side effects");
  Fake modern=new Fake();modern.sdk=28;Memory mm=new Memory();DesktopAssistant.Snapshot ms=DesktopAssistant.inspect(modern,"tv");ok(DesktopAssistant.candidates(ms).contains(DesktopAssistant.DEFAULT),"supported default desktop with known previous HOME can be reviewed");DesktopAssistant.Snapshot mf=DesktopAssistant.repair(modern,mm,"tv",ms,Arrays.asList(DesktopAssistant.DEFAULT));modern.home="user.new/user.new.Home";DesktopAssistant.restore(modern,mm,"tv",mf);ok(modern.home.equals("user.new/user.new.Home"),"later user-selected desktop is never overwritten by restore");
  Fake homeRestore=new Fake();homeRestore.sdk=28;Memory hm=new Memory();DesktopAssistant.Snapshot hs=DesktopAssistant.inspect(homeRestore,"tv");String originalHome=homeRestore.home;DesktopAssistant.Snapshot hf=DesktopAssistant.repair(homeRestore,hm,"tv",hs,Arrays.asList(DesktopAssistant.DEFAULT));DesktopAssistant.restore(homeRestore,hm,"tv",hf);ok(originalHome.equals(homeRestore.home),"unchanged helper-selected HOME restores the original component");
  Fake priorPermission=new Fake();priorPermission.permission=true;Memory pm=new Memory();DesktopAssistant.Snapshot ps=DesktopAssistant.inspect(priorPermission,"tv");DesktopAssistant.Snapshot pf=DesktopAssistant.repair(priorPermission,pm,"tv",ps,Arrays.asList(DesktopAssistant.ACCESS));DesktopAssistant.restore(priorPermission,pm,"tv",pf);ok(priorPermission.permission,"permission granted before helper repair is never revoked");
  Fake mi=new Fake();mi.sdk=28;mi.xiaomi=true;ok(!DesktopAssistant.candidates(DesktopAssistant.inspect(mi,"tv")).contains(DesktopAssistant.DEFAULT),"Xiaomi skips default desktop modification");
  Fake unread=new Fake();unread.blocked=DesktopAssistant.ACCESS;ok(!DesktopAssistant.candidates(DesktopAssistant.inspect(unread,"tv")).contains(DesktopAssistant.ACCESS),"unreadable service list is not replaced");
  Fake absent=new Fake();absent.gone=true;ok(!DesktopAssistant.inspect(absent,"tv").installed,"explicit missing-package error is handled as not installed");
  String report=DesktopAssistant.report(s,null);ok(!report.contains("192.0.2.1")&&!report.contains(f.serial)&&!report.contains("other.reader")&&!report.contains("secret fake-account"),"export summary excludes address, serial, other services and full system dump");
  ok(DesktopAssistant.focus("mCurrentFocus=Window{a u0 app.orchard.tv/.MainActivity}").equals(DesktopAssistant.HOME),"foreground parser extracts exact component without window titles");
  System.out.println("DESKTOP ENGINE PASSED: "+n+" checks.");
 }
}
