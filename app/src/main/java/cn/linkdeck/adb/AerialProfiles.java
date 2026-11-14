package cn.linkdeck.adb;
import java.io.*;import java.util.*;import org.json.*;
/** User-confirmed, per-TV evidence. A version number alone is never a health verdict. */
public final class AerialProfiles {
 private static void key(String id,String pkg)throws IOException{if(id==null||!id.matches("[0-9a-f]{64}")||!Arrays.asList(AerialSupport.FULL,AerialSupport.LITE).contains(pkg))throw new IOException("电视或应用身份未确认");}
 public static JSONObject root(String raw)throws Exception{if(raw==null||raw.isEmpty())return new JSONObject().put("schema",1).put("devices",new JSONObject());if(raw.length()>512*1024)throw new IOException("稳定版本记录过大");JSONObject o=new JSONObject(raw);if(o.optInt("schema")!=1||o.optJSONObject("devices")==null)throw new IOException("稳定版本记录格式未识别，未覆盖原记录");return o;}
 public static JSONObject get(String raw,String id,String pkg)throws Exception{key(id,pkg);JSONObject o=root(raw).getJSONObject("devices").optJSONObject(id+"|"+pkg);if(o==null)return null;if(!id.equals(o.optString("id"))||!pkg.equals(o.optString("pkg"))||o.optLong("code",-1)<0||!o.optString("sha256").matches("[0-9a-f]{64}")||!(o.opt("hold") instanceof Boolean)||!o.optString("revision").matches("[0-9a-f-]{36}"))throw new IOException("稳定记录身份或指纹异常");return new JSONObject(o.toString());}
 public static String mark(String raw,String id,String pkg,long code,String name,String sha,String signers,String tag,int sdk,boolean runConfirmed,boolean homeConfirmed,boolean bootConfirmed,long time)throws Exception{
  key(id,pkg);if(code<0||name==null||name.isEmpty()||name.length()>100||!sha.matches("[0-9a-f]{64}")||!signers.matches("[0-9a-f]{64}(,[0-9a-f]{64})*")||!runConfirmed||!homeConfirmed)throw new IOException("请先确认持续使用与实体主页键正常，并核实安装包指纹");
  if(!tag.isEmpty())AerialDownload.releaseTag(tag);
  JSONObject root=root(raw),devices=root.getJSONObject("devices");String k=id+"|"+pkg;if(devices.length()>=128&&!devices.has(k))throw new IOException("已保存 128 台设备，请先管理现有记录");
  devices.put(k,new JSONObject().put("id",id).put("pkg",pkg).put("code",code).put("name",name).put("sha256",sha).put("signers",signers).put("tag",tag).put("sdk",sdk).put("run",true).put("home",true).put("boot",bootConfirmed).put("time",time).put("hold",true).put("source","user_confirmed").put("revision",UUID.randomUUID().toString()));return root.toString();
 }
 public static String hold(String raw,String id,String pkg,boolean on)throws Exception{JSONObject root=root(raw),record=get(raw,id,pkg);if(record==null)throw new IOException("尚无本机稳定版本记录");record.put("hold",on).put("revision",UUID.randomUUID().toString());root.getJSONObject("devices").put(id+"|"+pkg,record);return root.toString();}
 public static boolean verified(JSONObject record,long code,String sha){return record!=null&&record.optLong("code",-1)==code&&record.optString("sha256").equals(sha);}
 public static boolean blocked(JSONObject record,long code,String sha){return record!=null&&record.optBoolean("hold")&&!verified(record,code,sha);}
 public static String revision(JSONObject record){return record==null?"none":record.optString("revision");}
 public static void allow(JSONObject record,String reviewedRevision,long code,String sha,boolean once)throws IOException{if(!revision(record).equals(reviewedRevision))throw new IOException("本机稳定版本记录已变化，请重新评估安装");if(blocked(record,code,sha)&&!once)throw new IOException("本机已暂缓切换到未验证安装包，请先确认本次变更");}
 public static String description(JSONObject r){if(r==null)return "尚未标记本机稳定版本";return "本机已验证（用户确认）："+r.optString("name")+"（"+r.optLong("code")+"）\n持续使用与主页键：已确认 · 开机："+(r.optBoolean("boot")?"已确认":"尚未确认")+"\n未验证安装包："+(r.optBoolean("hold")?"暂缓切换":"每次仍需手动确认安装");}
}
