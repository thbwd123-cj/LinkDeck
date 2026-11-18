package cn.linkdeck.adb;
import java.io.*;import java.util.*;
/** Read-only preflight queries for Halo TV. Mirrors the package-path half of AerialProbe; properties are shared via AerialProbe. */
public final class HaloProbe {
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
  if(!Arrays.asList(HaloSupport.FULL,HaloSupport.LITE).contains(pkg))throw new IOException("非 Halo TV 包名");
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
}
