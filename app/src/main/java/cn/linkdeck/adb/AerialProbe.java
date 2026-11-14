package cn.linkdeck.adb;
import java.io.*;import java.util.*;import java.util.regex.*;
/** Read-only preflight queries. Never infer absence or compatibility from an unknown failure. */
public final class AerialProbe {
 public interface Shell {AdbClient.Result run(String command)throws Exception;}
 public static final class Failure extends IOException {
  public final String command,raw;public final Integer exitCode;
  Failure(String c,String output,Integer code,Throwable cause){super("失败阶段："+(c.startsWith("pm install")?"请求安装":"读取电视信息")+"\n命令："+c+"\n"+(code==null?"完成状态：未确认（不会自动重试）":"退出码："+code)+"\n\n完整返回：\n"+(output==null||output.isEmpty()?"（电视未返回文本）":output)+(cause==null?"":"\n\n原因："+(cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage())),cause);command=c;raw=output==null?"":output;exitCode=code;}
 }
 private static AdbClient.Result execute(Shell shell,String command)throws Exception{
  try{return shell.run(command);}catch(Failure e){throw e;}catch(Exception e){String raw=e instanceof AdbClient.ShellFailure?((AdbClient.ShellFailure)e).raw:"";throw new Failure(command,raw,null,e);}
 }
 private static String completed(AdbClient.Result result,String command)throws IOException{if(result.exitCode!=0)throw new Failure(command,result.raw,result.exitCode,null);return result.text;}
 public static String query(Shell shell,String command)throws Exception{return completed(execute(shell,command),command);}
 public static String packagePath(Shell shell,String pkg)throws Exception{
  if(!Arrays.asList(AerialSupport.FULL,AerialSupport.LITE).contains(pkg))throw new IOException("非 Aerial 包名");
  String command="pm path "+pkg;AdbClient.Result path=execute(shell,command);
  boolean missing=path.text.isEmpty()||path.text.equals("Error: package "+pkg+" was not found")||path.text.equals("Error: package "+pkg+" not found.");
  if((path.exitCode==0||path.exitCode==1)&&missing){
   String listing=query(shell,"pm list packages");boolean found=false,any=false;
   for(String line:listing.split("\\r?\\n")){line=line.trim();if(line.isEmpty())continue;if(!line.matches("package:[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*"))throw new Failure("pm list packages",listing,0,new IOException("软件列表格式无法确认，不能判断未安装"));any=true;if(line.equals("package:"+pkg))found=true;}
   if(!any)throw new Failure("pm list packages",listing,0,new IOException("软件列表为空，不能判断未安装"));
   if(!found)return "";
   throw new Failure(command,path.raw,path.exitCode,new IOException("软件列表显示已安装，但未能读到基础 APK 路径；请保留现有安装，未继续传输"));
  }
  return completed(path,command);
 }
 public static final class Properties {
  public final int sdk;public final String model,brand,abis,serial,fingerprint;
  private Properties(Map<String,String> p)throws IOException{
   try{sdk=Integer.parseInt(value(p,"ro.build.version.sdk"));}catch(NumberFormatException e){throw new IOException("属性列表缺少有效系统版本（ro.build.version.sdk），未判断兼容性");}
   if(sdk<1||sdk>1000)throw new IOException("电视系统版本超出可识别范围");
   model=value(p,"ro.product.model");brand=value(p,"ro.product.brand");serial=value(p,"ro.serialno");fingerprint=value(p,"ro.build.fingerprint");
   if(unknown(model)||unknown(fingerprint))throw new IOException("属性列表缺少电视型号或系统指纹，未准备安装");
   String all=value(p,"ro.product.cpu.abilist");LinkedHashSet<String> set=new LinkedHashSet<>();
   if(!all.isEmpty())for(String abi:all.split(",",-1)){abi=abi.trim();if(!abi.matches("[A-Za-z0-9_-]+"))throw new IOException("CPU 架构列表无法识别，未判断兼容性");set.add(abi);}
   else for(String key:new String[]{"ro.product.cpu.abi","ro.product.cpu.abi2"}){String abi=value(p,key);if(!abi.isEmpty()){if(!abi.matches("[A-Za-z0-9_-]+"))throw new IOException("旧系统 CPU 架构无法识别");set.add(abi);}}
   if(set.isEmpty())throw new IOException("属性列表未提供 CPU 架构，未判断兼容性");abis=String.join(",",set);
  }
 }
 private static boolean unknown(String v){return v.isEmpty()||v.equalsIgnoreCase("unknown")||v.equalsIgnoreCase("null");}
 private static String value(Map<String,String> p,String k){String v=p.get(k);return v==null?"":v.trim();}
 public static Properties properties(Shell shell)throws Exception{
  String raw=query(shell,"getprop");Map<String,String> p=new HashMap<>();Matcher m=Pattern.compile("^\\[([A-Za-z0-9_.-]+)\\]: \\[(.*)\\]\\s*$",Pattern.MULTILINE).matcher(raw);
  while(m.find()){String old=p.put(m.group(1),m.group(2));if(old!=null&&!old.equals(m.group(2)))throw new IOException("电视属性出现冲突值，请重新检查");}
  return new Properties(p);
 }
}
