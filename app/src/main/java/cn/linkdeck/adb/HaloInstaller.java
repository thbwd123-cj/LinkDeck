package cn.linkdeck.adb;
import java.io.*;
import java.util.*;

/** Side effects are limited to pm install -r and temporary APK files. No desktop settings changed. Mirrors AerialInstaller. */
public final class HaloInstaller {
  public static final class RollbackFailure extends IOException {RollbackFailure(String message,Throwable cause){super(message,cause);}}
  public static final class Installed {
    public boolean present;
    public long code=-1;
    public String name="未安装", dump="";
  }
  public static Installed read(ApkTransfer.Device device,String pkg)throws Exception {
    if(!pkg.equals(HaloSupport.FULL)&&!pkg.equals(HaloSupport.LITE))throw new IOException("非 Halo TV 包名");
    String path=device.shell("pm path "+pkg).trim();Installed state=new Installed();
    if(path.isEmpty()||path.equals("Error: package "+pkg+" was not found"))return state;
    if(!path.startsWith("package:"))throw new IOException("无法确认 Halo TV 是否已安装：\n"+path);
    state.present=true;state.dump=device.shell("dumpsys package "+pkg);
    state.code=HaloSupport.versionCode(state.dump);state.name=HaloSupport.versionName(state.dump);return state;
  }
  public static String install(File file,String pkg,long newCode,Installed checked,ApkTransfer.Device device)throws Exception {
    return install(file,pkg,newCode,checked,device,false);
  }
  public static String install(File file,String pkg,long newCode,Installed checked,ApkTransfer.Device device,boolean confirmedDowngrade)throws Exception {
    Installed current=read(device,pkg);
    if(current.present!=checked.present||current.code!=checked.code)throw new IOException("电视上的 Halo TV 版本已经变化，请重新检查后安装。");
    int decision=HaloSupport.decision(current.present,current.code,newCode);
    if(decision==2&&!confirmedDowngrade)throw new IOException("电视上已有更新版本，本次不会降级安装。");
    if(confirmedDowngrade&&decision!=2)throw new IOException("回退条件已变化，请重新评估");
    try{ApkTransfer.install(Collections.singletonList(file),device,confirmedDowngrade);}catch(Exception e){if(confirmedDowngrade)throw new RollbackFailure(AerialSafety.downgradeFailure(e.getMessage()),e);throw e;}
    Installed after=read(device,pkg);
    if(!after.present||after.code!=newCode)throw new IOException("安装命令已返回成功，但未能核实电视上的最终版本，请先检查电视，不要重复安装。\n版本输出：\n"+after.dump);
    return "已确认电视安装版本："+after.name+"（"+after.code+"）\n"+(confirmedDowngrade?"已读回回退目标版本；旧版本读取现有数据的能力仍需打开应用验证。":"APK 覆盖安装完成。")+"主页键与开机接管尚未验证。";
  }
}
