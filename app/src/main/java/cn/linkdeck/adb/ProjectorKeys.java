package cn.linkdeck.adb;
import java.util.*;
/** 大眼橙投影专属入口：对焦设置 / 梯形校正。组件名来自用户 2026-11-19 诊断截图的当前前台（两个独立 Activity，可 am start 直达）。红外无大眼橙码源，这两个键只走 ADB。 */
public final class ProjectorKeys {
  private ProjectorKeys(){}
  public static final String PKG="com.obe.settings";
  public static final String FOCUS=PKG+"/com.obe.settings.projector.FocusSettingsActivity";
  public static final String KEYSTONE=PKG+"/com.obe.settings.projector.KeystoneSettingsActivity";
  public static String launchCommand(String component){return "am start -n "+component;}
  public static boolean isObeProjector(Map<String,String> properties){
    if(properties==null)return false;
    for(String k:new String[]{"ro.product.model","ro.product.brand"}){String v=properties.get(k);if(v!=null&&v.toLowerCase(Locale.ROOT).contains("obe"))return true;}
    return false;
  }
}
