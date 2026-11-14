package cn.linkdeck.adb;
import java.io.*;import java.util.*;
/** Pure decision rules used by every generic install source. */
public final class InstallPolicy {
 public static void check(String expected,String pkg,long candidate,boolean present,long installed,String existingSigners,String candidateSigners,int minimum,int sdk,Set<String> abis,String deviceAbis)throws IOException{
  PackageProbe.validate(pkg);
  if(expected!=null&&!expected.equals(pkg))throw new IOException("安装包与要更新的电视应用包名不一致，未安装");
  if(sdk<=0||deviceAbis==null||deviceAbis.isEmpty())throw new IOException("电视系统或架构未确认，不能判断兼容性");
  if(!ApkTransfer.compatible(minimum,sdk,abis,deviceAbis))throw new IOException("安装包与电视系统或 CPU 架构不兼容");
  if(candidate<0)throw new IOException("无法读取候选版本编号");
  if(present){if(installed<0)throw new IOException("电视已装版本无法确认，已停止覆盖");if(candidate<installed)throw new IOException("所选版本低于电视已装版本，普通更新不自动降级");if(existingSigners==null||existingSigners.isEmpty()||!existingSigners.equals(candidateSigners))throw new IOException("安装包签名与电视已装版本不一致或无法核实，保留旧版；不会自动卸载或清数据");}
 }
}
