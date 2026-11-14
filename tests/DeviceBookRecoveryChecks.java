package cn.linkdeck.adb;
import java.io.*;import java.nio.file.*;import java.util.concurrent.atomic.AtomicInteger;
public class DeviceBookRecoveryChecks {
 public static void main(String[] args)throws Exception{
  File dir=Files.createTempDirectory("saved-devices").toFile();AtomicInteger resets=new AtomicInteger();
  DeviceBook.ReadResult intact=DeviceBook.read("[{\"host\":\"tv\"}]",dir,()->{resets.incrementAndGet();return true;});
  if(intact.recovered||intact.entries.length()!=1||resets.get()!=0)throw new AssertionError("intact records changed");
  DeviceBook.ReadResult bad=DeviceBook.read("{damaged",dir,()->{resets.incrementAndGet();return true;});
  File[] backups=dir.listFiles((d,n)->n.endsWith(".bak"));
  if(!bad.recovered||bad.entries.length()!=0||resets.get()!=1||backups.length!=1||!new String(Files.readAllBytes(backups[0].toPath()),"UTF-8").equals("{damaged"))throw new AssertionError("corrupt data not safely backed up");
  try{DeviceBook.read("{second",dir,()->false);throw new AssertionError("failed reset accepted");}catch(IOException expected){}
  if(dir.listFiles((d,n)->n.endsWith(".bak")).length!=2)throw new AssertionError("failure lost backup");
  try{DeviceBook.read("{third",new File(dir,"missing"),()->{throw new AssertionError("reset called without backup");});throw new AssertionError("backup failure accepted");}catch(IOException expected){}
  System.out.println("DEVICE BOOK PASSED: intact data, backup first, reset failure and backup failure");
 }
}
