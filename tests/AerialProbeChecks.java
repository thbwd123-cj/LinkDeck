package cn.linkdeck.adb;
import java.io.*;import java.util.*;
/** Reproduces empty exit-1 queries without a television; unknown evidence never authorizes a write. */
public class AerialProbeChecks {
 static int n;static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;System.out.println("PASS: "+m);}interface Action{void run()throws Exception;}static void reject(Action a,String m)throws Exception{try{a.run();throw new AssertionError("accepted: "+m);}catch(IOException e){ok(true,m);}}
 static String props="[ro.product.model]: [8H66 H7]\n[ro.product.brand]: [coocaa]\n[ro.build.version.sdk]: [22]\n[ro.build.fingerprint]: [qa/vendor/android5]\n[ro.product.cpu.abi]: [arm64-v8a]\n[ro.product.cpu.abi2]: [armeabi-v7a]\n";
 static class Fake implements AerialProbe.Shell {
  Map<String,AdbClient.Result> replies=new HashMap<>();List<String> calls=new ArrayList<>();
  Fake(){replies.put("getprop",new AdbClient.Result(props,0));replies.put("pm list packages",new AdbClient.Result("package:android\npackage:com.coocaa.home\n",0));}
  public AdbClient.Result run(String c){calls.add(c);AdbClient.Result r=replies.get(c);return r==null?new AdbClient.Result("",1):r;}
 }
 public static void main(String[] args)throws Exception{
  Fake f=new Fake();AerialProbe.Properties p=AerialProbe.properties(f);ok(p.sdk==22&&p.abis.equals("arm64-v8a,armeabi-v7a"),"Android 5 properties fall back to abi and abi2");ok(p.serial.isEmpty()&&p.model.equals("8H66 H7"),"missing optional serial does not block reliable model/fingerprint identity");ok(f.calls.equals(Arrays.asList("getprop")),"preflight never sends failing optional single-property queries");
  f.replies.put("getprop",new AdbClient.Result(props+"[ro.product.cpu.abilist]: [arm64-v8a]\n",0));ok(AerialProbe.properties(f).abis.equals("arm64-v8a"),"complete ABI list takes precedence");
  for(String key:new String[]{"ro.build.version.sdk","ro.build.fingerprint","ro.product.model","ro.product.cpu.abi"}){Fake bad=new Fake();String raw=props.replaceAll("(?m)^\\["+java.util.regex.Pattern.quote(key)+"\\]:.*\\n","");if(key.equals("ro.product.cpu.abi"))raw=raw.replaceAll("(?m)^\\[ro.product.cpu.abi2\\]:.*\\n","");bad.replies.put("getprop",new AdbClient.Result(raw,0));reject(()->AerialProbe.properties(bad),"required property missing blocks installation: "+key);}
  Fake conflict=new Fake();conflict.replies.put("getprop",new AdbClient.Result(props+"[ro.build.version.sdk]: [23]\n",0));reject(()->AerialProbe.properties(conflict),"conflicting property snapshots are not guessed");
  Fake abi=new Fake();abi.replies.put("getprop",new AdbClient.Result(props+"[ro.product.cpu.abilist]: [arm64-v8a,]\n",0));reject(()->AerialProbe.properties(abi),"malformed reported ABI list cannot be silently replaced");
  String path="pm path "+AerialSupport.FULL;Fake absent=new Fake();ok(AerialProbe.packagePath(absent,AerialSupport.FULL).isEmpty()&&absent.calls.equals(Arrays.asList(path,"pm list packages")),"empty exit 1 means absent only after independent package-list verification");
  Fake empty0=new Fake();empty0.replies.put(path,new AdbClient.Result("",0));ok(AerialProbe.packagePath(empty0,AerialSupport.FULL).isEmpty()&&empty0.calls.size()==2,"empty exit 0 is also independently verified");
  Fake installed=new Fake();installed.replies.put(path,new AdbClient.Result("package:/data/app/aerial/base.apk\n",0));ok(AerialProbe.packagePath(installed,AerialSupport.FULL).contains("base.apk")&&installed.calls.size()==1,"known installed APK path does not trigger unnecessary full listing");
  Fake contradiction=new Fake();contradiction.replies.put("pm list packages",new AdbClient.Result("package:android\npackage:app.orchard.tv\n",0));reject(()->AerialProbe.packagePath(contradiction,AerialSupport.FULL),"installed package with unreadable path is never treated as absent");
  for(String raw:new String[]{"","Error: Package manager unavailable\n","package:android\ntruncated error"}){Fake bad=new Fake();bad.replies.put("pm list packages",new AdbClient.Result(raw,0));reject(()->AerialProbe.packagePath(bad,AerialSupport.FULL),"unrecognized package inventory cannot prove absence");}
  Fake denied=new Fake();denied.replies.put(path,new AdbClient.Result("java.lang.SecurityException: denied\n",1));reject(()->AerialProbe.packagePath(denied,AerialSupport.FULL),"permission failure is never normalized to uninstalled");ok(denied.calls.size()==1,"permission failure does not trigger an absence fallback");
  Fake listFail=new Fake();listFail.replies.put("pm list packages",new AdbClient.Result("",1));try{AerialProbe.packagePath(listFail,AerialSupport.FULL);throw new AssertionError();}catch(AerialProbe.Failure e){ok(e.command.equals("pm list packages")&&e.exitCode==1&&e.getMessage().contains("电视未返回文本"),"failure names the actual command and empty return rather than generic exit 1");}
  Fake detail=new Fake();String raw="  line one\nline two  \n";detail.replies.put("read-only query",new AdbClient.Result(raw,7));try{AerialProbe.query(detail,"read-only query");throw new AssertionError();}catch(AerialProbe.Failure e){ok(e.raw.equals(raw)&&e.getMessage().contains(raw)&&e.getMessage().contains("退出码：7"),"complete multiline output and whitespace are preserved in detailed error");}
  AerialProbe.Shell timeout=c->{throw new AdbClient.ShellFailure("结果未确认","partial evidence\n","等待结果",null);};try{AerialProbe.query(timeout,path);throw new AssertionError();}catch(AerialProbe.Failure e){ok(e.exitCode==null&&e.raw.equals("partial evidence\n")&&e.getMessage().contains("未确认")&&e.getMessage().contains(path),"transport uncertainty keeps command/output and never becomes a false completion");}
  Fake wrong=new Fake();wrong.replies.put(path,new AdbClient.Result("",2));reject(()->AerialProbe.packagePath(wrong,AerialSupport.FULL),"unexpected exit code is not accepted as absence");ok(wrong.calls.size()==1,"unexpected exit code performs no follow-up or write");
  reject(()->AerialProbe.packagePath(new Fake(),"com.example.other"),"special package lookup restricted to Aerial");
  ok(absent.calls.stream().noneMatch(c->c.contains("install")||c.contains("settings")||c.contains("uninstall")),"recovery queries stay strictly read-only");
  System.out.println("AERIAL PREFLIGHT FIX PASSED: "+n+" checks.");
 }
}
