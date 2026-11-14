package cn.linkdeck.adb;

import java.util.*;

/** Verification records what the user observed on their own television. */
public final class IrVerification {
  public static final String[] REQUIRED={"RIGHT","VOL_UP","UP","DOWN","LEFT","OK","BACK","HOME","MENU","VOL_DOWN","MUTE","POWER"};
  private static final String[] LABELS={"方向右","音量＋","方向上","方向下","方向左","确定","返回","主页","菜单","音量－","静音","电源"};
  private IrVerification(){}
  public static String displayName(String key){for(int i=0;i<REQUIRED.length;i++)if(REQUIRED[i].equals(key))return LABELS[i];return key;}
  public static Set<String> parse(String value){
    Set<String> result=new LinkedHashSet<>();
    if(value!=null)for(String item:value.split(","))for(String known:REQUIRED)if(known.equals(item))result.add(item);
    return result;
  }
  public static String serialize(Set<String> verified){
    List<String> result=new ArrayList<>();
    for(String key:REQUIRED)if(verified.contains(key))result.add(key);
    return androidFreeJoin(result);
  }
  private static String androidFreeJoin(List<String> values){StringBuilder out=new StringBuilder();for(String s:values){if(out.length()>0)out.append(',');out.append(s);}return out.toString();}
  public static boolean complete(IrCodes codes,Set<String> verified){
    if(codes==null)return false;
    for(String key:REQUIRED)if(!codes.has(key)||!verified.contains(key))return false;
    return true;
  }
  public static List<String> missing(IrCodes codes,Set<String> verified){
    List<String> result=new ArrayList<>();for(String key:REQUIRED)if(codes==null||!codes.has(key)||!verified.contains(key))result.add(key);return result;
  }
}
