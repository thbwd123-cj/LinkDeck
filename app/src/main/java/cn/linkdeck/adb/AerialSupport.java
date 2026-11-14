package cn.linkdeck.adb;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Policy and state validation, independent of networking and UI. */
public final class AerialSupport {
  public static final String FULL="app.orchard.tv", LITE="app.orchard.tv.lite";
  public static String variant(int sdk) throws IOException {
    if(sdk<=0)throw new IOException("无法读取电视系统版本，请重新连接后再检查。");
    if(sdk<14)throw new IOException("Aerial 不支持 Android 4.0 以前的系统。");
    return sdk<21?"lite":"standard";
  }
  public static String packageName(String variant) {return variant.equals("lite")?LITE:FULL;}
  public static String title(String variant) {return variant.equals("lite")?"Lite 旧系统版":variant.equals("guard")?"守护版（不声明 HOME）":"标准版";}
  public static void validateVariant(String variant,int sdk)throws IOException {
    String recommended=variant(sdk);
    if(!Arrays.asList("standard","guard","lite").contains(variant))throw new IOException("未知 Aerial 版本类型");
    if(variant.equals("lite")&&!recommended.equals("lite"))throw new IOException("Android 5.0 及以上应使用完整版，避免额外安装 Lite 桌面。");
    if(!variant.equals("lite")&&recommended.equals("lite"))throw new IOException("当前系统需使用 Aerial Lite，完整版最低要求 Android 5.0。");
  }
  public static long versionCode(String dump) {
    Matcher m=Pattern.compile("(?m)\\bversionCode=(\\d+)\\b").matcher(dump);
    try{return m.find()?Long.parseLong(m.group(1)):-1;}catch(Exception e){return -1;}
  }
  public static String versionName(String dump) {
    Matcher m=Pattern.compile("(?m)^\\s*versionName=([^\\r\\n]+)").matcher(dump);return m.find()?m.group(1).trim():"未知";
  }
  /** -1 install, 0 same, 1 update, 2 installed newer; unreadable installed code is a hard stop. */
  public static int decision(boolean installed,long oldCode,long newCode)throws IOException {
    if(newCode<0)throw new IOException("官方安装包版本信息无效");
    if(!installed)return -1;
    if(oldCode<0)throw new IOException("检测到已安装 Aerial，但无法确认现有版本；暂不覆盖，请先查看任务详情。");
    return oldCode==newCode?0:oldCode<newCode?1:2;
  }
  public static String limitations(int sdk,String brand) {
    String s="系统与架构检查通过仅表示具备安装条件，不保证主页键、开机接管、屏保解码或全部功能正常。安装不会自动替换系统桌面。";
    if(sdk<21)s+="\nLite 是旧系统版本，没有桌面守护，主页键取决于固件的默认桌面选择。";
    String b=brand==null?"":brand.toLowerCase(Locale.ROOT);
    if(b.contains("skyworth")||b.contains("coocaa")||b.contains("创维")||b.contains("酷开"))s+="\n创维/酷开部分固件会先进入原桌面，或限制主页键；安装成功不能证明这些限制已解除。";
    return s;
  }
}
