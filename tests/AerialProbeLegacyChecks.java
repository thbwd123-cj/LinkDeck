package cn.linkdeck.adb;
import java.io.*;
public class AerialProbeLegacyChecks {
 static int n;static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;System.out.println("PASS: "+m);}
 public static void main(String[] args)throws Exception{
  try(AdbClient c=new AdbClient()){
   c.connect("127.0.0.1",Integer.parseInt(args[0]),new AdbIdentity(new File(args[1])));
   for(String command:new String[]{"pm path app.orchard.tv","getprop ro.product.cpu.abilist"}){boolean reproduced=false;try{c.shell(command).require();}catch(IOException e){reproduced=e.getMessage().equals("命令执行失败（1）");}ok(reproduced,"old strict path reproduces screenshot's empty exit-1 error: "+command);}
   AerialProbe.Properties p=AerialProbe.properties(c::shell);ok(p.sdk==22&&p.abis.equals("arm64-v8a,armeabi-v7a")&&p.serial.isEmpty(),"new property snapshot succeeds over real legacy ADB socket");
   ApkTransfer.Device d=new ApkTransfer.Device(){public String shell(String command)throws Exception{if(command.startsWith("pm path "))return AerialProbe.packagePath(c::shell,command.substring(8));return AerialProbe.query(c::shell,command);}public void push(File f,String path){throw new AssertionError("preflight must never push");}};
   ok(!AerialInstaller.read(d,AerialSupport.FULL).present,"fresh/full installation can be evaluated after confirmed absence");
   AerialInstaller.Installed lite=AerialInstaller.read(d,AerialSupport.LITE);ok(lite.present&&lite.code==12,"installed package still reads its real version through the same adapter");
   boolean precise=false;try{AerialProbe.query(c::shell,"denied_query");}catch(AerialProbe.Failure e){precise=e.command.equals("denied_query")&&Integer.valueOf(1).equals(e.exitCode)&&e.raw.contains("Permission denied")&&e.getMessage().contains("完整返回");}ok(precise,"real returned permission failure preserves command/status/output");
  }
  System.out.println("AERIAL LEGACY PREFLIGHT SOCKET PASSED: "+n+" checks.");
 }
}
