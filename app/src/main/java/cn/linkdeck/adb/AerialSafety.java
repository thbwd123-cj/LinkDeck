package cn.linkdeck.adb;
import java.io.*;import java.util.*;
public final class AerialSafety {
 public static String basePath(String output)throws IOException{
  List<String> paths=new ArrayList<>();for(String line:output.trim().split("\\r?\\n")){if(!line.startsWith("package:/")||!line.endsWith(".apk")||line.length()>4096||line.indexOf('\0')>=0)throw new IOException("已安装 APK 路径未确认");paths.add(line.substring(8));}
  if(paths.size()==1)return paths.get(0);String base=null;for(String p:paths)if(p.endsWith("/base.apk")){if(base!=null)throw new IOException("存在多个基础 APK 路径");base=p;}if(base!=null)return base;throw new IOException("无法识别分包应用的基础 APK");
 }
 public static void sameSigners(String oldSet,String newSet)throws IOException{if(oldSet==null||newSet==null||!oldSet.matches("[0-9a-f]{64}(,[0-9a-f]{64})*")||!newSet.matches("[0-9a-f]{64}(,[0-9a-f]{64})*")||!new TreeSet<>(Arrays.asList(oldSet.split(","))).equals(new TreeSet<>(Arrays.asList(newSet.split(",")))))throw new IOException("安装包签名与电视现有应用不一致或无法核实，已停止；不会自动卸载旧版");}
 public static boolean fullTagMatches(String tag,String version){try{return AerialDownload.releaseTag(tag).substring(1).equals(version.replaceFirst("-(?:guard|standard)$",""));}catch(Exception e){return false;}}
 public static String downgradeFailure(String error){if(error!=null&&error.contains("INSTALL_FAILED_VERSION_DOWNGRADE"))return "电视拒绝保留数据降级。没有改为卸载或清除数据；请重新检查现有版本，再决定其他回退方式。\n"+error;return "本次回退安装未能确认完成，请重新检查现有版本。没有自动卸载或清除数据。\n"+error;}
}
