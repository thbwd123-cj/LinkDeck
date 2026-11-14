package cn.linkdeck.adb;
import java.io.*;import java.util.*;import org.json.*;
public class DesktopRaceChecks {
 static int n;static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;System.out.println("PASS: "+m);}
 static class Auto extends DesktopChecks.Fake {
  boolean flagOnAccess,allOnGrant,addOnVerify,unknownVendor;
  public String shell(String c)throws Exception {
   c=c.replace("settings --user 0 ","settings ");
   if(unknownVendor&&c.equals("pm path com.mitv.tvhome"))throw new IOException("vendor package query unsupported");
   String r=super.shell(c);
   if(c.startsWith("settings put secure "+DesktopAssistant.ACCESS+" ")){
    if(flagOnAccess)settings.put(DesktopAssistant.ENABLED,"1");
    if(addOnVerify)settings.put(DesktopAssistant.ACCESS,settings.get(DesktopAssistant.ACCESS)+":late.reader/.Service");
   }
   if(allOnGrant&&c.startsWith("pm grant")){
    settings.put(DesktopAssistant.ACCESS,DesktopAssistant.add(settings.get(DesktopAssistant.ACCESS),DesktopAssistant.GUARD));settings.put(DesktopAssistant.ENABLED,"1");settings.put(DesktopAssistant.NOTIFY,DesktopAssistant.add(settings.get(DesktopAssistant.NOTIFY),DesktopAssistant.LISTENER));
   }
   return r;
  }
 }
 static class RaceStore extends DesktopChecks.Memory {
  Auto device;int mode,changes;boolean fired;
  public void save(String id,JSONObject value)throws Exception {
   super.save(id,value);JSONArray a=value.getJSONArray("changes");JSONObject last=a.getJSONObject(a.length()-1);
   if(last.optString("key").equals(DesktopAssistant.ACCESS)&&last.optString("state").equals("prepared")&&(!fired||mode==2)){
    fired=true;changes++;
    if(mode==1)device.settings.put(DesktopAssistant.ACCESS,DesktopAssistant.add(device.settings.get(DesktopAssistant.ACCESS),DesktopAssistant.GUARD));
    else device.settings.put(DesktopAssistant.ACCESS,device.settings.get(DesktopAssistant.ACCESS)+":concurrent.reader/.S"+changes);
   }
  }
 }
 public static void main(String[] args)throws Exception {
  Auto a=new Auto();a.flagOnAccess=true;a.settings.put(DesktopAssistant.ENABLED,"0");DesktopChecks.Memory m=new DesktopChecks.Memory();DesktopAssistant.Snapshot before=DesktopAssistant.inspect(a,"fixture");
  if(args.length>0&&args[0].equals("baseline")){
   try{DesktopAssistant.repair(a,m,"fixture",before,Arrays.asList(DesktopAssistant.ACCESS));throw new AssertionError("baseline unexpectedly passed");}catch(IOException e){if(!e.getMessage().contains("电视设置已变化"))throw e;System.out.println("REPRODUCED 0.9.0 screenshot error: "+e.getMessage());return;}
  }
  DesktopAssistant.Snapshot after=DesktopAssistant.repair(a,m,"fixture",before,Arrays.asList(DesktopAssistant.ACCESS));
  ok(DesktopAssistant.contains(after.values.get(DesktopAssistant.ACCESS),DesktopAssistant.GUARD)&&"1".equals(after.values.get(DesktopAssistant.ENABLED)),"Aerial auto-enabling the master switch no longer aborts repair");
  ok(a.writes.stream().noneMatch(c->c.startsWith("settings put secure "+DesktopAssistant.ENABLED)),"already enabled master switch is not rewritten");
  Auto grant=new Auto();grant.allOnGrant=true;grant.settings.put(DesktopAssistant.ENABLED,"0");DesktopChecks.Memory gm=new DesktopChecks.Memory();DesktopAssistant.Snapshot gs=DesktopAssistant.inspect(grant,"fixture");
  DesktopAssistant.repair(grant,gm,"fixture",gs,Arrays.asList(DesktopAssistant.PERMISSION,DesktopAssistant.ACCESS,DesktopAssistant.NOTIFY));
  ok(gm.data.getJSONArray("changes").length()==1,"Aerial's self-enabled service entries are not claimed as helper writes");
  DesktopAssistant.restore(grant,gm,"fixture",gs);ok(DesktopAssistant.contains(grant.settings.get(DesktopAssistant.ACCESS),DesktopAssistant.GUARD)&&!grant.permission,"restore keeps external service changes while revoking only helper grant");
  Auto complete=new Auto();DesktopChecks.Memory cm=new DesktopChecks.Memory();DesktopAssistant.Snapshot cs=DesktopAssistant.inspect(complete,"fixture");complete.permission=true;complete.settings.put(DesktopAssistant.ACCESS,DesktopAssistant.GUARD);complete.settings.put(DesktopAssistant.ENABLED,"1");
  DesktopAssistant.repair(complete,cm,"fixture",cs,Arrays.asList(DesktopAssistant.PERMISSION,DesktopAssistant.ACCESS));ok(complete.writes.isEmpty()&&cm.data==null,"items completed after review are accepted without writes or backup ownership");
  Auto merge=new Auto();RaceStore rm=new RaceStore();rm.device=merge;DesktopAssistant.Snapshot ms=DesktopAssistant.inspect(merge,"fixture");DesktopAssistant.repair(merge,rm,"fixture",ms,Arrays.asList(DesktopAssistant.ACCESS));
  ok(merge.settings.get(DesktopAssistant.ACCESS).contains("concurrent.reader")&&DesktopAssistant.contains(merge.settings.get(DesktopAssistant.ACCESS),DesktopAssistant.GUARD),"list changed after backup is re-read and merged without losing concurrent services");
  DesktopAssistant.restore(merge,rm,"fixture",ms);ok(merge.settings.get(DesktopAssistant.ACCESS).contains("concurrent.reader")&&!DesktopAssistant.contains(merge.settings.get(DesktopAssistant.ACCESS),DesktopAssistant.GUARD),"rebased backup restores only the helper-added token");
  Auto external=new Auto();RaceStore em=new RaceStore();em.device=external;em.mode=1;DesktopAssistant.Snapshot es=DesktopAssistant.inspect(external,"fixture");DesktopAssistant.repair(external,em,"fixture",es,Arrays.asList(DesktopAssistant.ACCESS));
  ok(external.writes.isEmpty()&&!DesktopAssistant.hasBackup(em.data),"external completion during backup is skipped and not offered for rollback");
  external.settings.put(DesktopAssistant.ACCESS,DesktopAssistant.remove(external.settings.get(DesktopAssistant.ACCESS),DesktopAssistant.GUARD));
  DesktopAssistant.repair(external,em,"fixture",DesktopAssistant.inspect(external,"fixture"),Arrays.asList(DesktopAssistant.ACCESS));
  ok(em.data.getJSONArray("changes").length()==1&&DesktopAssistant.hasBackup(em.data),"skip-only journal cannot accumulate stale entries or block a later real repair");
  Auto verified=new Auto();verified.addOnVerify=true;DesktopChecks.Memory vm=new DesktopChecks.Memory();DesktopAssistant.repair(verified,vm,"fixture",DesktopAssistant.inspect(verified,"fixture"),Arrays.asList(DesktopAssistant.ACCESS));ok(verified.settings.get(DesktopAssistant.ACCESS).contains("late.reader"),"concurrent additive service change during read-back is preserved");
  Auto churn=new Auto();RaceStore hm=new RaceStore();hm.device=churn;hm.mode=2;try{DesktopAssistant.repair(churn,hm,"fixture",DesktopAssistant.inspect(churn,"fixture"),Arrays.asList(DesktopAssistant.ACCESS));throw new AssertionError("unbounded retry accepted");}catch(IOException e){ok(churn.writes.isEmpty()&&hm.changes==3&&!DesktopAssistant.hasBackup(hm.data),"continuous list changes stop after bounded retries without overwrites");}
  Auto vendor=new Auto();vendor.sdk=28;vendor.unknownVendor=true;DesktopAssistant.Snapshot vs=DesktopAssistant.inspect(vendor,"fixture");ok(!vs.xiaomi&&!DesktopAssistant.summary(vs).contains("小米")&&!DesktopAssistant.candidates(vs).contains(DesktopAssistant.DEFAULT),"failed vendor query does not misclassify Coocaa as Xiaomi or enable HOME writes");
  ok(DesktopAssistant.bound("services:{Service[label=Aerial, feedbackType[FEEDBACK_GENERIC], capabilities=1, eventTypes=[TYPE_WINDOW_STATE_CHANGED], notificationTimeout=0]}]")==null,"real Android 5.1 label-only bound service format remains unconfirmed");
  ok(Boolean.TRUE.equals(DesktopAssistant.bound("Bound services:{Service[id=app.orchard.tv/.guard.HomeGuardService]}")),"explicit Aerial component still confirms bound service");
  ok(DesktopAssistant.bound("Bound services:{Service[id=other.pkg/.S], Service[label=Aerial]}")==null,"mixed identified and legacy service entries do not falsely exclude Aerial");
  ok(Boolean.FALSE.equals(DesktopAssistant.bound("Bound services:{Service[label=app.orchard.tv.guard.HomeGuardService,id=other.pkg/.S]}")),"service label is never mistaken for component identity");
  System.out.println("DESKTOP CONCURRENCY/LEGACY FIX PASSED: "+n+" checks.");
 }
}
