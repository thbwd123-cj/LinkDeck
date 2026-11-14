package cn.linkdeck.adb;
import java.io.*;import java.util.*;
public class DiagnosticChecks {
 static int n;static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;System.out.println("PASS: "+m);}
 static class Device extends DesktopChecks.Fake{
  int pauses,reads;boolean newCrash,loseConnection,versionChange;String records="";
  Device(){permission=true;settings.put(DesktopAssistant.ACCESS,DesktopAssistant.GUARD);settings.put(DesktopAssistant.ENABLED,"1");}
  public void pause(long ms){pauses++;if(newCrash)records="========================================\n2026-10-31 12:00:00 data_app_crash\nProcess: app.orchard.tv\nPackage: app.orchard.tv v93 (1.73)\njava.lang.NoSuchMethodError: getSystemService private-token\n at app.orchard.tv.guard.HomeGuardService.onServiceConnected(Source:7)\n";}
  public String shell(String c)throws Exception{
   if(loseConnection&&pauses>0)throw new IOException("private-address:5555 disconnected");
   if(c.equals("dumpsys dropbox --print data_app_crash"))return "Drop box contents: 1 entries\nSearching for: data_app_crash\n"+records;
   String s=super.shell(c);if(c.startsWith("dumpsys package")&&versionChange&&pauses>0)s=s.replace("versionCode=93","versionCode=94");return s;
  }
 }
 public static void main(String[] args)throws Exception{
  Device healthy=new Device();DesktopDiagnostic.Result a=DesktopDiagnostic.capture(healthy,"192.0.2.1:5555");ok(a.samples.size()==3&&healthy.pauses==2&&a.verdict().state.equals(DesktopDiagnostic.NORMAL),"one capture performs three bounded observations and yields scoped normal state");
  ok(healthy.writes.isEmpty(),"diagnostic capture performs zero TV mutations or app starts");
  String report=DesktopDiagnostic.report(a);ok(!report.contains("192.0.2.1")&&!report.contains("SERIAL-09")&&!report.contains("other.reader"),"fault report excludes endpoint, serial and other service lists");
  Device crash=new Device();crash.newCrash=true;DesktopDiagnostic.Result c=DesktopDiagnostic.capture(crash,"fixture");ok(c.newCrash&&c.verdict().state.equals(DesktopDiagnostic.CRASH),"new matching crash is routed to application fault rather than missing permissions");
  String cr=DesktopDiagnostic.report(c);ok(cr.contains("兼容性崩溃")&&!cr.contains("private-token")&&!cr.contains("Source:7"),"crash report preserves useful category without arbitrary crash messages or raw stack");
  Device history=new Device();history.newCrash=true;history.pause(0);history.newCrash=false;history.pauses=0;DesktopDiagnostic.Result h=DesktopDiagnostic.capture(history,"fixture");ok(!h.newCrash&&h.verdict().state.equals(DesktopDiagnostic.NORMAL),"historical unchanged crash does not label current observation as newly crashing");
  Device missing=new Device();missing.settings.put(DesktopAssistant.ENABLED,"0");DesktopDiagnostic.Result m=DesktopDiagnostic.capture(missing,"fixture");ok(m.verdict().state.equals(DesktopDiagnostic.MISSING),"known disabled guard routes to concrete configuration state");
  Device unbound=new Device();unbound.canBind=false;DesktopDiagnostic.Result u=DesktopDiagnostic.capture(unbound,"fixture");ok(u.verdict().state.equals(DesktopDiagnostic.UNKNOWN)&&u.verdict().reason.contains("未绑定"),"configured but unbound guard remains diagnostic uncertainty");
  Device denied=new Device(){public String shell(String c)throws Exception{if(c.equals("dumpsys dropbox --print data_app_crash"))throw new IOException("denied");return super.shell(c);}};
  ok(DesktopDiagnostic.capture(denied,"fixture").verdict().state.equals(DesktopDiagnostic.UNKNOWN),"unreadable crash source never produces clean health verdict");
  Device lost=new Device();lost.loseConnection=true;DesktopDiagnostic.Result l=DesktopDiagnostic.capture(lost,"fixture");ok(l.interrupted&&l.samples.size()==1&&l.verdict().state.equals(DesktopDiagnostic.UNKNOWN),"disconnect preserves first observation and produces partial result");
  ok(!DesktopDiagnostic.report(l).contains("private-address"),"connection exception cannot leak endpoint into exported report");
  Device changed=new Device();changed.versionChange=true;DesktopDiagnostic.Result v=DesktopDiagnostic.capture(changed,"fixture");ok(v.changed&&v.samples.size()==1&&v.verdict().state.equals(DesktopDiagnostic.UNKNOWN),"capture cannot combine observations across installed version changes");
  DesktopAssistant.Snapshot direct=new DesktopAssistant.Snapshot();direct.installed=true;direct.userId=0;direct.crashesKnown=true;direct.known.add(DesktopAssistant.DEFAULT);direct.values.put(DesktopAssistant.DEFAULT,DesktopAssistant.HOME);ok(DesktopDiagnostic.classify(direct).state.equals(DesktopDiagnostic.NORMAL),"confirmed direct HOME does not require optional guard setup");
  DesktopAssistant.Snapshot lite=new DesktopAssistant.Snapshot();lite.lite=true;ok(DesktopDiagnostic.classify(lite).state.equals(DesktopDiagnostic.UNKNOWN),"Lite does not get a false full-guard missing diagnosis");
  Device initialLost=new Device(){public String shell(String c)throws Exception{throw new IOException("lost");}};DesktopDiagnostic.Result empty=DesktopDiagnostic.capture(initialLost,"fixture");ok(empty.samples.isEmpty()&&empty.interrupted&&DesktopDiagnostic.report(empty).contains("0 / 3"),"initial failure still yields exportable partial report");
  System.out.println("ONE-CLICK DIAGNOSTICS PASSED: "+n+" checks.");
 }
}
