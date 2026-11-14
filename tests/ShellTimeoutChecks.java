package cn.linkdeck.adb;
import java.io.*;
public class ShellTimeoutChecks {
 public static void main(String[] args)throws Exception {
  try(AdbClient c=new AdbClient()) {
   c.connect("127.0.0.1",Integer.parseInt(args[0]),new AdbIdentity(new File(args[1])));
   String mode=args[2];long start=System.nanoTime();boolean caught=false;
   try {c.shell("echo "+mode,180);}catch(AdbClient.ShellFailure e){
    caught=true;
    if(!e.getMessage().contains("未确认")&&!e.getMessage().contains("不能确认"))throw new AssertionError("must be uncertain");
    if(mode.equals("partial")&&!e.raw.equals("  已返回部分文本  \n"))throw new AssertionError(e.raw);
    if(mode.equals("silent")&&!e.raw.isEmpty())throw new AssertionError("unexpected output");
    if(mode.equals("stream")&&!e.raw.contains("progress"))throw new AssertionError("stream lost");
    if(mode.equals("ack")&&!e.stage.contains("接受"))throw new AssertionError("wrong stage");
   }
   if(!caught)throw new AssertionError("incomplete must fail");
   if((System.nanoTime()-start)/1000000>1600)throw new AssertionError("total deadline not honored");
   if(!c.shell("echo recovered",2000).require().equals("recovered"))throw new AssertionError("channel cleanup");
   System.out.println("PASS: "+mode+" preserves uncertainty/output, bounded deadline and next command works");
  }
 }
}
