package cn.linkdeck.adb;
import java.io.*;import java.util.*;import org.json.*;
public class DesktopVerificationChecks {
 static int n;static void ok(boolean b,String message){if(!b)throw new AssertionError(message);n++;System.out.println("PASS: "+message);}
 static DesktopAssistant.VerificationFailure rejected(DesktopAssistant.Device d,DesktopChecks.Memory m,DesktopAssistant.Snapshot s,List<String> chosen)throws Exception{
  try{DesktopAssistant.repair(d,m,"fixture",s,chosen);throw new AssertionError("repair must remain incomplete");}catch(DesktopAssistant.VerificationFailure e){return e;}
 }
 static String crash(long code,String process,String stamp,String reason){return "========================================\n"+stamp+" data_app_crash (text, 1200 bytes)\nProcess: "+process+"\nPackage: "+process+" v"+code+" (fixture)\nBuild: private/build\n"+reason+"\n\tat app.orchard.tv.guard.HomeGuardService.onServiceConnected(Fixture.java:7)\n";}
 static final String COMPAT="java.lang.NoSuchMethodError: No virtual method getSystemService(Ljava/lang/Class;)Ljava/lang/Object; in class Landroid/content/Context;";
 static class Device extends DesktopChecks.Fake {
  int pauses,accessReads;String records="",afterWriteRecords,queryResult;boolean logDenied,unknownUser,unknownBound;int user=0;boolean reset;
  Device(){settings.put(DesktopAssistant.ENABLED,"0");}
  public void pause(long millis){pauses++;}
  public String shell(String c)throws Exception {
   c=c.replace("settings --user 0 ","settings ");
   if(c.equals("dumpsys accessibility")){
    if(reset&&accessReads++>=2)settings.put(DesktopAssistant.ENABLED,"0");
    if(unknownUser)return "vendor output unavailable";
    if(user!=0)return "User state[attributes:{id=0, currentUser=false}\nservices:{}]\nUser state[attributes:{id="+user+", currentUser=true}\nservices:{Service[id=app.orchard.tv/.guard.HomeGuardService]}]";
    if(unknownBound)return "User state[attributes:{id=0, currentUser=true}\nservices:{Service[label=Aerial]}]";
   }
   if(c.startsWith("cmd package query-activities")&&queryResult!=null)return queryResult;
   if(c.equals("dumpsys dropbox --print data_app_crash")){
    if(logDenied)throw new IOException("permission denied");return "Drop box contents: 12 entries\nSearching for: data_app_crash\n"+records+"\nUsage: dumpsys dropbox\n";
   }
   String out=super.shell(c);
   if(afterWriteRecords!=null&&(c.startsWith("pm grant")||c.startsWith("settings put secure")))records=afterWriteRecords;
   return out;
  }
 }
 static class Reset extends Device {
  boolean wrote,verified;
  public String shell(String c)throws Exception {
   c=c.replace("settings --user 0 ","settings ");
   if(c.equals("settings get secure "+DesktopAssistant.ENABLED)&&wrote){if(verified)settings.put(DesktopAssistant.ENABLED,"0");else verified=true;}
   String out=super.shell(c);if(c.startsWith("settings put secure "+DesktopAssistant.ENABLED+" "))wrote=true;return out;
  }
 }
 public static void main(String[] args)throws Exception {
  Reset reset=new Reset();reset.settings.put(DesktopAssistant.ACCESS,DesktopAssistant.GUARD);DesktopChecks.Memory rm=new DesktopChecks.Memory();
  DesktopAssistant.VerificationFailure rf=rejected(reset,rm,DesktopAssistant.inspect(reset,"fixture",DesktopAssistant.AERIAL),Arrays.asList(DesktopAssistant.ACCESS,DesktopAssistant.NOTIFY));
  ok("0".equals(rf.snapshot.values.get(DesktopAssistant.ENABLED))&&rf.snapshot.outcome.contains("未生效"),"final switch reset yields incomplete result and carries actual snapshot");
  ok(reset.writes.stream().noneMatch(c->c.startsWith("settings put secure "+DesktopAssistant.NOTIFY)),"reset aborts later selected writes without retry");
  Device missing=new Device();missing.canBind=false;DesktopChecks.Memory mm=new DesktopChecks.Memory();DesktopAssistant.VerificationFailure mf=rejected(missing,mm,DesktopAssistant.inspect(missing,"fixture",DesktopAssistant.AERIAL),Arrays.asList(DesktopAssistant.ACCESS));
  ok(missing.pauses==2&&mf.snapshot.outcome.contains("守护未运行"),"unbound guard stops after exactly three read-only checks");
  ok(missing.writes.stream().filter(c->c.startsWith("settings put secure "+DesktopAssistant.ENABLED)).count()==1,"runtime verification never repeats master-switch write");
  Device late=new Device(){public void pause(long ms){super.pause(ms);if(pauses==2)canBind=true;}};late.canBind=false;
  DesktopAssistant.Snapshot la=DesktopAssistant.repair(late,new DesktopChecks.Memory(),"fixture",DesktopAssistant.inspect(late,"fixture",DesktopAssistant.AERIAL),Arrays.asList(DesktopAssistant.ACCESS));
  ok(late.pauses==2&&Boolean.TRUE.equals(la.bound)&&la.outcome.contains("绑定已核实"),"late guard binding can finish within bounded read-only wait");
  Device sw=new Device(){public void pause(long ms){super.pause(ms);if(pauses==2)canBind=true;}};sw.canBind=false;sw.sdk=28;
  sw.settings.put(DesktopAssistant.ACCESS,DesktopAssistant.GUARD);
  DesktopAssistant.Snapshot fromSw=DesktopAssistant.inspect(sw,"fixture",DesktopAssistant.AERIAL);
  DesktopAssistant.Snapshot toSw=DesktopAssistant.inspect(sw,"fixture",DesktopAssistant.HALO);
  DesktopAssistant.Snapshot done=DesktopAssistant.switchDesktop(sw,new DesktopChecks.Memory(),"fixture",fromSw,toSw);
  ok(sw.pauses==2&&Boolean.TRUE.equals(done.bound)&&done.outcome.contains("已切换到 Halo TV"),"late Halo guard binding can finish within bounded switch re-checks");
  ok(!DesktopAssistant.contains(sw.settings.get(DesktopAssistant.ACCESS),DesktopAssistant.GUARD)&&DesktopAssistant.contains(sw.settings.get(DesktopAssistant.ACCESS),DesktopAssistant.HALO.guard),"switch removes outgoing guard and enables incoming guard");
  ok(sw.home.equals(DesktopAssistant.HALO.home),"switch sets incoming default desktop");
  Device unbound=new Device();unbound.canBind=false;unbound.sdk=28;
  unbound.settings.put(DesktopAssistant.ACCESS,DesktopAssistant.GUARD);
  DesktopAssistant.Snapshot fromUb=DesktopAssistant.inspect(unbound,"fixture",DesktopAssistant.AERIAL);
  DesktopAssistant.Snapshot toUb=DesktopAssistant.inspect(unbound,"fixture",DesktopAssistant.HALO);
  try{DesktopAssistant.switchDesktop(unbound,new DesktopChecks.Memory(),"fixture",fromUb,toUb);throw new AssertionError("switch must remain incomplete when guard never binds");}catch(DesktopAssistant.VerificationFailure e){ok(unbound.pauses==2&&e.snapshot.outcome.contains("守护未运行"),"switch still fails after bounded re-checks when guard never binds");}
  Device unknown=new Device();unknown.unknownBound=true;
  DesktopAssistant.Snapshot uf=DesktopAssistant.repair(unknown,new DesktopChecks.Memory(),"fixture",DesktopAssistant.inspect(unknown,"fixture",DesktopAssistant.AERIAL),Arrays.asList(DesktopAssistant.ACCESS));
  ok(uf.bound==null&&uf.outcome.contains("配置已写入，绑定状态未确认")&&uf.outcomeDetail.contains("主页键"),"unrecognized binding no longer fails repair once config is written; HOME key verifies it");
  ok(unknown.writes.stream().filter(c->c.startsWith("settings put secure "+DesktopAssistant.ACCESS)).count()==1&&unknown.writes.stream().filter(c->c.startsWith("settings put secure "+DesktopAssistant.ENABLED)).count()==1,"unrecognized binding never repeats config writes");
  String event=crash(93,DesktopAssistant.PKG,"2026-10-31 12:00:00",COMPAT);
  Device crash=new Device();crash.afterWriteRecords=event;DesktopChecks.Memory cm=new DesktopChecks.Memory();DesktopAssistant.VerificationFailure cf=rejected(crash,cm,DesktopAssistant.inspect(crash,"fixture",DesktopAssistant.AERIAL),Arrays.asList(DesktopAssistant.PERMISSION,DesktopAssistant.ACCESS,DesktopAssistant.NOTIFY));
  ok(cf.getMessage().contains("兼容性崩溃")&&cf.snapshot.outcome.equals("应用崩溃"),"new same-version guard compatibility crash aborts repair");
  ok(crash.writes.size()==1&&crash.writes.get(0).startsWith("pm grant")&&DesktopAssistant.hasBackup(cm.data),"new crash stops later writes and retains completed backup");
  Device diesAfterBinding=new Device(){public void pause(long ms){super.pause(ms);records=event;}};
  DesktopAssistant.VerificationFailure lateCrash=rejected(diesAfterBinding,new DesktopChecks.Memory(),DesktopAssistant.inspect(diesAfterBinding,"fixture",DesktopAssistant.AERIAL),Arrays.asList(DesktopAssistant.ACCESS));
  ok(diesAfterBinding.pauses==1&&lateCrash.snapshot.outcome.equals("应用崩溃"),"initial binding is observed long enough to catch a subsequent new crash");
  Device old=new Device();old.records=event;
  DesktopAssistant.Snapshot oa=DesktopAssistant.repair(old,new DesktopChecks.Memory(),"fixture",DesktopAssistant.inspect(old,"fixture",DesktopAssistant.AERIAL),Arrays.asList(DesktopAssistant.ACCESS));
  ok(Boolean.TRUE.equals(oa.bound)&&oa.crashIds.size()==1,"unchanged historical record does not ban the installed version");
  Device earlier=new Device();earlier.records=crash(94,DesktopAssistant.PKG,"2026-10-31 12:00:00",COMPAT);
  DesktopAssistant.Snapshot ea=DesktopAssistant.repair(earlier,new DesktopChecks.Memory(),"fixture",DesktopAssistant.inspect(earlier,"fixture",DesktopAssistant.AERIAL),Arrays.asList(DesktopAssistant.ACCESS));
  ok(ea.crashIds.isEmpty(),"old 1.74 crash is excluded when installed build is 1.73");
  Device unrelated=new Device();unrelated.afterWriteRecords=crash(93,"other.private.app","2026-10-31 12:00:00",COMPAT);
  DesktopAssistant.Snapshot ur=DesktopAssistant.repair(unrelated,new DesktopChecks.Memory(),"fixture",DesktopAssistant.inspect(unrelated,"fixture",DesktopAssistant.AERIAL),Arrays.asList(DesktopAssistant.ACCESS));
  ok(ur.crashIds.isEmpty(),"another app's crash cannot stop Aerial repair");
  Device denied=new Device();denied.logDenied=true;
  DesktopAssistant.Snapshot da=DesktopAssistant.repair(denied,new DesktopChecks.Memory(),"fixture",DesktopAssistant.inspect(denied,"fixture",DesktopAssistant.AERIAL),Arrays.asList(DesktopAssistant.ACCESS));
  ok(!da.crashesKnown&&DesktopAssistant.report(da,null).contains("不能据此判断无崩溃"),"unreadable logs remain explicitly unknown even if guard binds");
  DesktopAssistant.Snapshot parser=new DesktopAssistant.Snapshot();parser.code=93;DesktopAssistant.crashRecords(parser,"Drop box contents: 2 entries\nSearching for: data_app_crash\n"+event+event);
  ok(parser.crashIds.size()==2,"identical crash signatures in the same second retain occurrence counts");
  Set<String> ids=new HashSet<>(parser.crashIds);DesktopAssistant.crashRecords(parser,"Drop box contents: 2 entries\nSearching for: data_app_crash\n"+event+event+"Usage: changed footer\n");
  ok(ids.equals(parser.crashIds),"footer changes cannot turn historical crashes into new records");
  DesktopAssistant.crashRecords(parser,"Drop box contents: 2 entries\nSearching for: data_app_crash\nProcess: app.orchard.tv\nPackage: app.orchard.tv v93\n");
  ok(!parser.crashesKnown,"truncated matching record never asserts a clean crash log");
  DesktopAssistant.crashRecords(parser,"Drop box contents: 1 entries\nSearching for: data_app_crash\n========================================\n2026-10-31 12:00:00 data_app_crash\nProcess: app.orchard.tv\n");
  ok(!parser.crashesKnown,"matching process with missing package/version is unknown rather than clean");
  Device pending=new Device();DesktopChecks.Memory pm=new DesktopChecks.Memory(){public void save(String id,JSONObject j)throws Exception{super.save(id,j);JSONArray a=j.getJSONArray("changes");if(a.getJSONObject(a.length()-1).optString("state").equals("prepared"))pending.blocked=DesktopAssistant.ACCESS;}};
  DesktopAssistant.Snapshot ps=DesktopAssistant.inspect(pending,"fixture",DesktopAssistant.AERIAL);try{DesktopAssistant.repair(pending,pm,"fixture",ps,Arrays.asList(DesktopAssistant.ACCESS));throw new AssertionError("must interrupt");}catch(IOException expected){}
  ok(pending.writes.isEmpty()&&!DesktopAssistant.hasBackup(pm.data)&&!DesktopAssistant.hasUncertain(pm.data),"failure before send leaves only prepared record with no rollback ownership");
  Device legacy=new Device();legacy.settings.put(DesktopAssistant.ACCESS,DesktopAssistant.GUARD);DesktopAssistant.Snapshot ls=DesktopAssistant.inspect(legacy,"fixture",DesktopAssistant.AERIAL);DesktopChecks.Memory lm=new DesktopChecks.Memory();
  lm.data=new JSONObject().put("id",ls.id).put("restored",false).put("changes",new JSONArray().put(new JSONObject().put("key",DesktopAssistant.ACCESS).put("before",JSONObject.NULL).put("after",DesktopAssistant.GUARD).put("state","pending")));
  try{DesktopAssistant.restore(legacy,lm,"fixture",ls);throw new AssertionError("legacy pending must block");}catch(DesktopAssistant.UncertainBackup expected){}
  ok(legacy.writes.isEmpty()&&DesktopAssistant.contains(legacy.settings.get(DesktopAssistant.ACCESS),DesktopAssistant.GUARD),"legacy pending cannot remove externally added guard or force-stop app");
  DesktopAssistant.preserveUncertain(legacy,lm,"fixture",ls);
  ok(legacy.writes.isEmpty()&&!DesktopAssistant.hasBackup(lm.data)&&lm.data.getJSONArray("changes").getJSONObject(0).optString("state").equals("preserved"),"explicit keep-current archives unknown record with zero TV writes");
  Device inFlight=new Device();inFlight.ambiguous=DesktopAssistant.ACCESS;DesktopChecks.Memory im=new DesktopChecks.Memory();DesktopAssistant.Snapshot ins=DesktopAssistant.inspect(inFlight,"fixture",DesktopAssistant.AERIAL);
  try{DesktopAssistant.repair(inFlight,im,"fixture",ins,Arrays.asList(DesktopAssistant.ACCESS));throw new AssertionError("must interrupt");}catch(IOException expected){}
  ok(DesktopAssistant.hasUncertain(im.data)&&im.data.getJSONArray("changes").getJSONObject(0).optString("state").equals("unknown"),"sent but unverifiable write remains uncertain rather than auto-owned");
  inFlight.ambiguous=null;int count=inFlight.writes.size();try{DesktopAssistant.repair(inFlight,im,"fixture",DesktopAssistant.inspect(inFlight,"fixture",DesktopAssistant.AERIAL),Arrays.asList(DesktopAssistant.ACCESS));throw new AssertionError("uncertainty must resolve first");}catch(DesktopAssistant.UncertainBackup expected){}
  ok(inFlight.writes.size()==count,"subsequent repair cannot append writes before uncertain backup review");
  String users="User state[attributes:{id=0, currentUser=false}\nservices:{}]\nUser state[attributes:{id=10, currentUser=true}\nservices:{Service[id=app.orchard.tv/.guard.HomeGuardService]}]";
  ok(DesktopAssistant.activeUser(users)==10&&Boolean.TRUE.equals(DesktopAssistant.bound(users,DesktopAssistant.AERIAL)),"binding parser follows explicit current-user block");
  Device user=new Device();DesktopAssistant.Snapshot review=DesktopAssistant.inspect(user,"fixture",DesktopAssistant.AERIAL);user.user=10;try{DesktopAssistant.repair(user,new DesktopChecks.Memory(),"fixture",review,Arrays.asList(DesktopAssistant.ACCESS));throw new AssertionError("user switch must block");}catch(IOException expected){}
  ok(user.writes.isEmpty()&&DesktopAssistant.candidates(DesktopAssistant.inspect(user,"fixture",DesktopAssistant.AERIAL)).isEmpty(),"switch to unsupported user blocks every mutation and candidate");
  Device noUser=new Device();noUser.unknownUser=true;DesktopAssistant.Snapshot nu=DesktopAssistant.inspect(noUser,"fixture",DesktopAssistant.AERIAL);
  ok(nu.userId==-1&&DesktopAssistant.candidates(nu).isEmpty(),"unknown active user never inherits user-0 repair candidates");
  ok(!DesktopAssistant.exactHomeCategory("Category: \"android.intent.category.HOME.CC\"")&&DesktopAssistant.exactHomeCategory("Category: \"android.intent.category.HOME\""),"HOME category must match exact token rather than HOME.CC prefix");
  Device invalidHome=new Device();invalidHome.sdk=28;invalidHome.queryResult="other.pkg/.Home";DesktopAssistant.Snapshot ih=DesktopAssistant.inspect(invalidHome,"fixture",DesktopAssistant.AERIAL);
  ok(!ih.homeDeclared&&!DesktopAssistant.candidates(ih).contains(DesktopAssistant.DEFAULT),"default-home repair requires Aerial MainActivity among actual HOME candidates");
  Device switched=new Device();DesktopAssistant.Snapshot base=DesktopAssistant.inspect(switched,"fixture",DesktopAssistant.AERIAL);switched.afterWriteRecords=event;switched.canBind=false;
  try{DesktopAssistant.verifyOperation(switched,"fixture",base,Collections.emptyList(),true);throw new AssertionError("runtime binding must verify");}catch(DesktopAssistant.VerificationFailure expected){ok(expected.snapshot.outcome.contains("未运行"),"restart verification also refuses success without bound guard");}
  Device launch=new Device();launch.settings.put(DesktopAssistant.ACCESS,DesktopAssistant.GUARD);launch.settings.put(DesktopAssistant.ENABLED,"1");launch.focus=DesktopAssistant.HOME;
  DesktopAssistant.Snapshot launchBase=DesktopAssistant.inspect(launch,"fixture",DesktopAssistant.AERIAL),launchOk=DesktopAssistant.verifyLaunch(launch,"fixture",launchBase);
  ok(launch.pauses==2&&launchOk.outcome.contains("启动观察通过"),"restart observes foreground over three bounded samples");
  Device disappears=new Device(){public void pause(long ms){super.pause(ms);focus="com.coocaa.home/.Main";}};disappears.focus=DesktopAssistant.HOME;DesktopAssistant.Snapshot db=DesktopAssistant.inspect(disappears,"fixture",DesktopAssistant.AERIAL);
  try{DesktopAssistant.verifyLaunch(disappears,"fixture",db);throw new AssertionError("must reject foreground loss");}catch(DesktopAssistant.VerificationFailure expected){ok(expected.snapshot.outcome.contains("启动未确认"),"restart cannot succeed if Aerial leaves foreground during observation");}
  Device direct=new Device();direct.home=DesktopAssistant.HOME;direct.focus=DesktopAssistant.HOME;direct.canBind=false;DesktopAssistant.Snapshot directResult=DesktopAssistant.verifyLaunch(direct,"fixture",DesktopAssistant.inspect(direct,"fixture",DesktopAssistant.AERIAL));
  ok(directResult.outcome.contains("通过")&&directResult.outcomeDetail.contains("默认桌面"),"direct default HOME does not require an unnecessary guard binding");
  String report=DesktopAssistant.report(cf.snapshot,null);
  ok(!report.contains("private/build")&&!report.contains("Fixture.java")&&!report.contains("other.private.app")&&report.contains("兼容性崩溃"),"report exports sanitized outcome without raw stack or unrelated packages");
  System.out.println("DESKTOP VERIFICATION FIX PASSED: "+n+" checks.");
 }
}
