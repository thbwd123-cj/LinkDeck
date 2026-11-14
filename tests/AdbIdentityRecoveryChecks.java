package cn.linkdeck.adb;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class AdbIdentityRecoveryChecks {
 public static void main(String[] args) throws Exception {
  File dir=Files.createTempDirectory("adb-key-recovery").toFile();
  File pri=new File(dir,"adb_private.pk8"),pub=new File(dir,"adb_public.der");
  AdbIdentity first=new AdbIdentity(dir);
  byte[] original=first.publicKey().getEncoded();
  Files.write(pub.toPath(),new byte[]{1,2,3});
  if(!Arrays.equals(original,new AdbIdentity(dir).publicKey().getEncoded())) throw new AssertionError("public key recovery changed identity");
  Files.write(pri.toPath(),new byte[]{1,2,3});
  try {new AdbIdentity(dir);throw new AssertionError("corruption was not reported");}catch(IOException expected){if(!expected.getMessage().contains("重新授权"))throw expected;}
  if(Arrays.equals(original,new AdbIdentity(dir).publicKey().getEncoded()))throw new AssertionError("private key was not replaced");
  if(Arrays.stream(dir.list()).noneMatch(x->x.startsWith("adb_private.pk8.corrupt-")))throw new AssertionError("corrupt private key was not preserved");
  System.out.println("ADB IDENTITY RECOVERY PASSED: public restored; private corruption reported, backed up and regenerated");
 }
}
