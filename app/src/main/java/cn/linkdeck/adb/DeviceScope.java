package cn.linkdeck.adb;
import java.io.*;import java.nio.charset.StandardCharsets;import java.security.*;
/** Recovery records are never silently attached to an IP address alone. */
public final class DeviceScope {
 private static String value(String s){return s==null?"":s.trim();}
 private static boolean unknown(String s){return s.isEmpty()||s.equalsIgnoreCase("unknown")||s.equalsIgnoreCase("null");}
 public static String recoveryKey(String host,String port,String serial,String fingerprint,String model)throws IOException{
  if(unknown(value(serial))||value(serial).matches("0+"))throw new IOException("电视没有提供可用序列号，不能自动归属恢复记录。可在应用页筛选停用应用并手动启用；本次不新增停用操作。");
  return installationKey(host,port,serial,fingerprint,model);
 }
 public static String installationKey(String host,String port,String serial,String fingerprint,String model)throws IOException{
  serial=value(serial);fingerprint=value(fingerprint);if(unknown(serial)&&unknown(fingerprint))throw new IOException("无法读取设备序列号或系统指纹，不能安全归属恢复记录。请重新连接后重试。");
  String identity=value(host)+"\n"+value(port)+"\n"+serial+"\n"+fingerprint+"\n"+value(model);
  try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder("disabled:v2:");for(byte v:digest)b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return b.toString();}catch(NoSuchAlgorithmException e){throw new IOException(e);}
 }
}
