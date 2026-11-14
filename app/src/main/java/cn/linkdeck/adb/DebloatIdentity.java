package cn.linkdeck.adb;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** Keep recovery records scoped to a non-placeholder TV serial and model, independent of IP. */
public final class DebloatIdentity {
 private DebloatIdentity(){}
 public static String key(String serial,String model)throws IOException {
  serial=serial==null?"":serial.trim();model=model==null?"":model.trim();
  String lower=serial.toLowerCase(Locale.ROOT);
  if(serial.length()<6||serial.length()>128||lower.equals("unknown")||lower.equals("null")||lower.equals("0123456789abcdef")||lower.equals("1234567890")||lower.matches("0+")||model.isEmpty()||model.equalsIgnoreCase("unknown"))throw new IOException("电视未提供可辨认的序列号和型号，无法安全保存该设备的恢复记录，本次不能卸载。请用电视设置管理应用。");
  try{byte[] b=MessageDigest.getInstance("SHA-256").digest((serial+"\n"+model).getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder("debloat:v1:");for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}catch(NoSuchAlgorithmException e){throw new IOException(e);}
 }
}
