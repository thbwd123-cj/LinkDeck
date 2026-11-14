package cn.linkdeck.adb;
import java.io.*;import java.net.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;
public final class WorkflowChecks {
 static int n;static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);n++;System.out.println("PASS: "+msg);}
 public static void main(String[] args)throws Exception{
  File dir=new File(args[0]);File base=new File(dir,"base.apk"),split=new File(dir,"split.apk"),required=new File(dir,"required.apk"),dep=new File(dir,"dependent.apk");
  check(ApkManifest.read(base).split.isEmpty(),"binary manifest identifies base APK");
  check(ApkManifest.read(split).split.equals("config.qa"),"binary manifest identifies standalone split");
  check(!ApkManifest.splits(Arrays.asList(split)).isEmpty(),"standalone split cannot install alone");
  check(ApkManifest.splits(Arrays.asList(base,split)).isEmpty(),"complete base plus split accepted");
  check(!ApkManifest.splits(Arrays.asList(base,split,split)).isEmpty(),"duplicate split rejected");
  check(ApkManifest.read(required).splitRequired,"reads split-required boolean");
  check(!ApkManifest.splits(Arrays.asList(required)).isEmpty(),"required splits missing from base rejected");
  check(ApkManifest.splits(Arrays.asList(required,split)).isEmpty(),"required base with declared split accepted");
  check(ApkManifest.read(dep).dependencies.contains("config.qa"),"reads uses-split dependency");
  check(!ApkManifest.splits(Arrays.asList(base,dep)).isEmpty(),"missing declared dependency rejected");
  check(ApkManifest.splits(Arrays.asList(base,split,dep)).isEmpty(),"declared dependency present accepted");
  check(ApkManifest.compatibility(26,22,Collections.emptySet(),"armeabi-v7a").contains("Android 5.1"),"explains old TV Android incompatibility");
  check(ApkManifest.compatibility(21,22,Collections.singleton("arm64-v8a"),"armeabi-v7a").contains("不匹配"),"explains ABI mismatch");
  check(ApkManifest.compatibility(21,0,Collections.emptySet(),null).contains("暂不能"),"offline result remains unverified");
  check(ApkManifest.installError("Failure [INSTALL_FAILED_MISSING_SPLIT]").contains("原始返回"),"friendly install error preserves raw response");
  for(byte[] bad:new byte[][]{new byte[0],new byte[]{3,0,8,0,100,0,0,0},new byte[]{3,0,8,0,16,0,0,0,1,0,8,0,0,0,0,0}}){try{ApkManifest.parse(bad);throw new AssertionError("accepted corrupt manifest");}catch(IOException expected){n++;}}
  try(ServerSocket server=new ServerSocket(0)) {
   String report=ConnectionDiagnostic.probe("127.0.0.1",server.getLocalPort());
   check(report.contains("端口可以连通")&&report.contains("不代表已通过 ADB 授权"),"TCP reachability never claims ADB authorization");
  }
  int closed;try(ServerSocket s=new ServerSocket(0)){closed=s.getLocalPort();}
  check(ConnectionDiagnostic.probe("127.0.0.1",closed).contains("原始信息"),"closed port report includes raw cause");
  check(ConnectionDiagnostic.reason(new SocketTimeoutException()).contains("不能仅据此"),"timeout does not assert TV is off");
  System.out.println("WORKFLOW LOGIC PASSED: "+n+" checks.");
 }
}
