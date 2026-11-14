package cn.linkdeck.adb;
import java.io.*;
public final class PackageProbe {
 public static void validate(String pkg)throws IOException{if(pkg==null||!pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))throw new IOException("应用包名无效");}
 public static String path(AerialProbe.Shell shell,String pkg)throws Exception{
  validate(pkg);
  String command="pm path "+pkg;AdbClient.Result path=shell.run(command);
  boolean missing=path.text.isEmpty()||path.text.equals("Error: package "+pkg+" was not found")||path.text.equals("Error: package "+pkg+" not found.");
  if((path.exitCode==0||path.exitCode==1)&&missing){
   String listing=AerialProbe.query(shell,"pm list packages");boolean found=false,any=false;
   for(String line:listing.split("\\r?\\n")){line=line.trim();if(line.isEmpty())continue;if(!line.matches("package:[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*"))throw new AerialProbe.Failure("pm list packages",listing,0,new IOException("软件列表格式无法确认，不能判断未安装"));any=true;if(line.equals("package:"+pkg))found=true;}
   if(!any)throw new AerialProbe.Failure("pm list packages",listing,0,new IOException("软件列表为空，不能判断未安装"));
   if(!found)return "";
   throw new AerialProbe.Failure(command,path.raw,path.exitCode,new IOException("软件列表显示已安装，但未能读到基础 APK 路径；请保留现有安装，未继续传输"));
  }
  if(path.exitCode!=0)throw new AerialProbe.Failure(command,path.raw,path.exitCode,null);return path.text;
 }
}
