package cn.linkdeck.adb;
import java.io.*;
import java.util.*;

public final class DebloatChecks {
 static final String P="com.ktcp.video";
 static int checks;
 interface Action{void run()throws Exception;}
 static void ok(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;System.out.println("PASS: "+label);}
 static void reject(Action f,String expected,String label)throws Exception{try{f.run();throw new AssertionError("accepted: "+label);}catch(IOException e){ok(e.getMessage()!=null&&e.getMessage().contains(expected),label+"; received: "+e.getMessage());}}
 static class Journal implements DebloatEngine.Ledger {
  Set<String> records=new HashSet<>();boolean failRemember;
  public void remember(String p)throws IOException{if(failRemember)throw new IOException("disk full");records.add(p);}
  public void forget(String p){records.remove(p);}
  public boolean contains(String p){return records.contains(p);}
 }
 static class Device implements DebloatEngine.Shell {
  boolean installed=true,system=true,immutablePath=true,help=true,completeList=true,critical=false,uidSystem=false,mutated=false,failRestore=false;
  String mode="success",unsafeRole="";int inspections=0;List<String> commands=new ArrayList<>();
  public DebloatEngine.Reply run(String command)throws Exception {
   commands.add(command);
   if(command.equals("pm list packages --user 0")){
    inspections++;if(mode.equals("query_failure")&&inspections>1)return new DebloatEngine.Reply(1,"Permission denial");
    return new DebloatEngine.Reply(0,(completeList?"package:android\n":"")+(installed?"package:"+P+"\n":""));
   }
   if(command.equals("pm list packages -s --user 0"))return new DebloatEngine.Reply(0,"package:android\n"+(system?"package:"+P+"\n":""));
   if(command.equals("pm path '"+P+"'"))return new DebloatEngine.Reply(0,"package:"+(immutablePath?"/system/app/example/base.apk":"/data/app/example/base.apk")+"\n");
   if(command.equals("dumpsys package '"+P+"'"))return new DebloatEngine.Reply(0,"Packages:\n  Package ["+P+"] (aaa):\n    userId="+(uidSystem?"1000":"10062")+"\n    versionCode=2\n"+(critical?"android.intent.category.HOME\n":"")+unsafeRole);
   if(command.equals("pm help"))return new DebloatEngine.Reply(0,help?"install-existing [--user USER_ID] PACKAGE\n":"list packages [--user USER_ID]\ninstall-existing PACKAGE\n");
   if(command.equals("pm uninstall -k --user 0 '"+P+"'")){
    mutated=true;if(mode.equals("deny"))return new DebloatEngine.Reply(0,"Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]\n");
    if(mode.equals("transport"))throw new IOException("socket closed after send");
    if(mode.equals("mismatch"))return new DebloatEngine.Reply(0,"Success\n");
    installed=false;return new DebloatEngine.Reply(0,"Success\n");
   }
   if(command.equals("pm install-existing --user 0 '"+P+"'")){
    mutated=true;if(failRestore)return new DebloatEngine.Reply(1,"Error: not available on this firmware\n");
    installed=true;return new DebloatEngine.Reply(0,"Package "+P+" installed for user: 0\n");
   }
   throw new AssertionError("unexpected command "+command);
  }
 }
 static DebloatEngine engine(DebloatCatalog c,Device d,Journal j){return new DebloatEngine(c,d,j);}
 public static void main(String[] args)throws Exception{
  DebloatCatalog catalog;try(InputStream in=new FileInputStream(args[0])){catalog=DebloatCatalog.read(in);}
  ok(catalog.entries().size()>=3,"independent catalog contains candidates");
  for(DebloatCatalog.Entry e:catalog.entries()){ok(!DebloatCatalog.protectedPackage(e.pkg),"catalog excludes protected package "+e.pkg);ok(e.name.matches(".*[\\u4e00-\\u9fff].*")&&!e.description.isEmpty(),"candidate has Chinese name and explanation "+e.pkg);}
  String badCatalog="{\"schema\":1,\"entries\":[{\"package\":\"com.android.settings\",\"name\":\"设置\",\"description\":\"说明\"}]}";
  reject(()->DebloatCatalog.read(new ByteArrayInputStream(badCatalog.getBytes("UTF-8"))),"受保护","protected entry rejected while loading catalog");
  for(String p:new String[]{"com.android.settings","com.android.systemui","com.android.packageinstaller","com.google.android.inputmethod.latin","com.google.android.tvlauncher","com.vendor.launcher","com.vendor.keyboard","com.vendor.settings","com.vendor.packageinstaller","app.orchard.tv","app.orchard.tv.lite","cn.linkdeck.adb"})ok(DebloatCatalog.protectedPackage(p),"core protected "+p);
  Device d=new Device();Journal j=new Journal();DebloatEngine e=engine(catalog,d,j);
  reject(()->e.uninstall("com.android.settings"),"核心组件","core rejected before any device command");ok(d.commands.isEmpty(),"no shell command for protected package");
  reject(()->e.uninstall("com.other.userapp"),"不在","manual non-catalog package rejected");ok(d.commands.isEmpty(),"unknown manual package never reaches shell");
  d=new Device();d.critical=true;j=new Journal();final Device a=d;final Journal ja=j;reject(()->engine(catalog,a,ja).uninstall(P),"关键系统功能","HOME role blocks candidate");ok(!a.mutated&&!ja.contains(P),"role protection before record or removal");
  for(String role:new String[]{"android.view.InputMethod","android.permission.BIND_INPUT_METHOD","android.settings.SETTINGS","android.intent.action.INSTALL_PACKAGE","android.permission.BIND_ACCESSIBILITY_SERVICE"}){
   final Device guarded=new Device();guarded.unsafeRole=role;final Journal g=new Journal();reject(()->engine(catalog,guarded,g).uninstall(P),"关键系统功能","dynamic core role blocked "+role);ok(!guarded.mutated&&!g.contains(P),"dynamic role has no mutation "+role);
  }
  d=new Device();d.system=false;j=new Journal();final Device nonSystem=d;final Journal jnon=j;reject(()->engine(catalog,nonSystem,jnon).uninstall(P),"系统预装","only preinstalled system candidates may be removed");ok(!nonSystem.mutated,"downloaded third-party app not touched");
  d=new Device();d.uidSystem=true;j=new Journal();final Device b=d;final Journal jb=j;reject(()->engine(catalog,b,jb).uninstall(P),"系统身份","shared system UID blocked");ok(!b.mutated,"system UID sends no uninstall");
  d=new Device();d.immutablePath=false;j=new Journal();final Device c=d;final Journal jc=j;reject(()->engine(catalog,c,jc).uninstall(P),"系统预装分区","data-app package cannot be called recoverable");ok(!c.mutated,"non-preloaded app cannot be removed");
  d=new Device();d.help=false;j=new Journal();final Device h=d;final Journal jh=j;reject(()->engine(catalog,h,jh).uninstall(P),"未显示支持","restore support must include user option");ok(!h.mutated,"missing recovery support blocks removal");
  d=new Device();d.completeList=false;j=new Journal();final Device il=d;final Journal jil=j;reject(()->engine(catalog,il,jil).uninstall(P),"列表不完整","incomplete package list fails closed");ok(!il.mutated,"incomplete list sends no mutation");
  d=new Device();j=new Journal();j.failRemember=true;final Device nr=d;final Journal jnr=j;reject(()->engine(catalog,nr,jnr).uninstall(P),"disk full","record failure aborts before uninstall");ok(!nr.mutated,"no uninstall without durable recovery record");
  d=new Device();d.mode="deny";j=new Journal();final Device deny=d;final Journal jd=j;reject(()->engine(catalog,deny,jd).uninstall(P),"Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]","failed uninstall returns exact TV error");ok(deny.installed&&!jd.contains(P),"verified failure is not reported or recorded as removed");
  d=new Device();d.mode="mismatch";j=new Journal();final Device mm=d;final Journal jm=j;reject(()->engine(catalog,mm,jm).uninstall(P),"仍显示应用已安装","success text alone is not success");ok(mm.installed&&!jm.contains(P),"failed verification does not claim success");
  d=new Device();d.mode="query_failure";j=new Journal();final Device q=d;final Journal jq=j;reject(()->engine(catalog,q,jq).uninstall(P),"状态未确认","query failure is uncertainty");ok(jq.contains(P),"uncertain outcome retains recovery record");
  d=new Device();d.mode="transport";j=new Journal();final Device tr=d;final Journal jt=j;reject(()->engine(catalog,tr,jt).uninstall(P),"结果未确认","transport interruption is uncertainty");ok(jt.contains(P),"transport failure retains recovery record");
  d=new Device();j=new Journal();DebloatEngine safe=engine(catalog,d,j);DebloatEngine.Outcome removed=safe.uninstall(P);ok(!d.installed&&j.contains(P)&&removed.summary.contains("已复查"),"verified uninstall and persistent recovery record");ok(d.commands.stream().filter(x->x.startsWith("pm uninstall")).allMatch(x->x.equals("pm uninstall -k --user 0 '"+P+"'")),"only retained-data uninstall command is used");
  d.failRestore=true;final DebloatEngine er=safe;reject(()->er.restore(P),"Error: not available on this firmware","restore failure preserves original output");ok(j.contains(P)&&!d.installed,"restore failure keeps record");d.failRestore=false;DebloatEngine.Outcome restored=safe.restore(P);ok(d.installed&&!j.contains(P)&&restored.summary.contains("已复查"),"verified restoration removes record");
  reject(()->DebloatIdentity.key("unknown","电视"),"序列号","ambiguous device cannot create restoration record");ok(DebloatIdentity.key("SERIAL12345","TVMODEL").equals(DebloatIdentity.key("SERIAL12345","TVMODEL")),"stable serial/model recovery scope");ok(!DebloatIdentity.key("SERIAL12345","TVMODEL").equals(DebloatIdentity.key("SERIAL54321","TVMODEL")),"different TV serial scopes do not match");
  System.out.println("DEBLOAT ENGINE PASSED: "+checks+" checks");
 }
}
