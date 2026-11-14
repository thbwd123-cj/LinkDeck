package cn.linkdeck.adb;
import java.io.*;import java.util.*;import java.util.regex.*;import org.json.*;
/** Only read-only probes. No key injection, installs, screenshots, settings writes or launcher changes. */
public final class DeviceCapabilities {
 public static final String REMOTE="remote",APPS="apps",INSTALL="install",FILES="files",SCREENSHOT="screenshot",DESKTOP="desktop";
 public enum State { READY, SUCCEEDED, LIMITED, UNKNOWN, ABSENT }
 public static final class Item {
  public final String name;public State state;public String reason;
  Item(String n,State s,String r){name=n;state=s;reason=r;}
 }
 public interface Device {AdbClient.Result shell(String command)throws Exception;boolean connected();}
 public static final class Snapshot {
  public final Map<String,Item> items=new LinkedHashMap<>();public final Map<String,String> properties=new HashMap<>();
  public long checked=System.currentTimeMillis();public boolean partial;public String transport="传统 ADB";public int sdk,user=-1;public String brand="未知",model="未知",abis="未知";
  public Snapshot(){put(REMOTE,"遥控按键",State.UNKNOWN,"尚未检查命令接口");put(APPS,"读取应用",State.UNKNOWN,"尚未读取应用列表");put(INSTALL,"安装与更新",State.UNKNOWN,"是否允许安装需要逐包确认");put(FILES,"文件通道",State.UNKNOWN,"路径权限需在实际操作时核对");put(SCREENSHOT,"截图",State.UNKNOWN,"未读取电视画面");put(DESKTOP,"桌面与守护",State.LIMITED,"高级操作需要当前设备单独检查，不能套用其他型号结论");}
  void put(String id,String name,State state,String reason){items.put(id,new Item(name,state,reason));}
  public synchronized boolean blocked(String id){Item i=items.get(id);return i!=null&&(i.state==State.ABSENT);}
  public synchronized void success(String id){Item i=items.get(id);if(i!=null){i.state=State.SUCCEEDED;i.reason=id.equals(SCREENSHOT)?"本次已收到有效画面；不代表所有场景都能截图":id.equals(INSTALL)?"已有一次安装返回并核实版本；其他应用仍逐包检查":id.equals(REMOTE)?"按键命令已返回成功；电视实际响应仍需观察":"本次操作成功；其他路径、包名和权限仍分别核对";}}
  public synchronized String display(){StringBuilder b=new StringBuilder("设备："+model+" · "+brand+"\n系统："+ApkManifest.android(sdk)+"\n架构："+abis+"\n连接："+transport+"\n\n");for(Item i:items.values())b.append(label(i.state)).append(" · ").append(i.name).append("\n").append(i.reason).append("\n\n");if(partial)b.append("检测中断或部分结果不完整，请重新检测。\n");return b.append("基础检查通过不代表厂商允许全部操作；未知不等于不支持。没有发送测试按键、安装应用或修改电视设置。\n机型与固件兼容性仍需实机验收。").toString();}
  /** Allowlisted schema only: never raw outputs, endpoints, serials, GUIDs, pairing secrets or build fingerprints. */
  private String redact(String text){String v=text==null?"":text;for(String k:new String[]{"ro.serialno","ro.boot.serialno","ro.build.fingerprint"}){String value=properties.get(k);if(value!=null&&value.length()>2)v=v.replace(value,"[设备标识隐藏]");}return safe(v).replaceAll("(?i)(?:[0-9a-f]{0,4}:){2,}[0-9a-f:%.]*","[地址隐藏]");}
  public synchronized String export()throws JSONException {JSONObject root=new JSONObject().put("appVersion",AppVersion.NAME).put("schema",1).put("scope","local read-only capability checks; not a model-wide certification").put("checkedAt",checked).put("partial",partial).put("brand",redact(brand)).put("model",redact(model)).put("sdk",sdk).put("abis",redact(abis)).put("transport",transport.equals("加密无线 ADB")?"paired_tls":"legacy_tcp");JSONArray a=new JSONArray();for(Map.Entry<String,Item> e:items.entrySet()){Item i=e.getValue();a.put(new JSONObject().put("feature",e.getKey()).put("state",i.state.name()).put("explanation",redact(i.reason)));}root.put("capabilities",a);return root.toString(2);}
 }
 public static String label(State s){switch(s){case READY:return "基础检查通过";case SUCCEEDED:return "本次操作成功";case LIMITED:return "受限 / 需确认";case ABSENT:return "未提供接口";default:return "尚未确认";}}
 private static String safe(String text){if(text==null)return "";String v=text.replaceAll("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}","[地址隐藏]").replaceAll("(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?","[地址隐藏]").replaceAll("[\\r\\n\\t]"," ");return v.substring(0,Math.min(v.length(),160));}
 private static int number(String v){try{return Integer.parseInt(v);}catch(Exception e){return 0;}}
 public static Map<String,String> parseProperties(String raw){Map<String,String> p=new HashMap<>();Matcher m=Pattern.compile("^\\[([A-Za-z0-9_.-]+)\\]: \\[(.*)\\]\\s*$",Pattern.MULTILINE).matcher(raw);while(m.find())p.put(m.group(1),m.group(2));return p;}
 private static AdbClient.Result read(Device d,String c)throws Exception{if(!d.connected())throw new IOException("disconnected");return d.shell(c);}
 private static boolean denied(AdbClient.Result r){String text=r.text.toLowerCase(Locale.ROOT);return text.contains("permission denied")||text.contains("securityexception")||text.contains("not allowed");}
 private static void tool(Snapshot s,Device d,String feature,String command,String detail)throws Exception{
  AdbClient.Result r=read(d,"command -v "+command);Item i=s.items.get(feature);
  if(r.exitCode==0&&r.text.matches("/?[A-Za-z0-9_./-]+")){i.state=State.READY;i.reason=detail;}
  else if(denied(r)){i.state=State.LIMITED;i.reason="只读检查被设备拒绝，需检查调试权限";}
  else if(r.exitCode==1&&r.text.isEmpty()){i.state=State.ABSENT;i.reason="未找到 "+command+" 命令；此项暂不可用";}
 }
 public static Snapshot inspect(Device d,String transport){Snapshot s=new Snapshot();s.transport=transport;
  try{
   AdbClient.Result first=read(d,"getprop");if(first.exitCode!=0)throw new IOException("properties unknown");s.properties.putAll(parseProperties(first.raw));s.sdk=number(s.properties.get("ro.build.version.sdk"));s.model=s.properties.getOrDefault("ro.product.model","未知");s.brand=s.properties.getOrDefault("ro.product.brand","未知");s.abis=s.properties.getOrDefault("ro.product.cpu.abilist",s.properties.getOrDefault("ro.product.cpu.abi","未知"));
   tool(s,d,REMOTE,"input","输入工具已识别，未发送测试按键；实际效果待操作验证");
   AdbClient.Result packages=read(d,"pm list packages");Item apps=s.items.get(APPS);if(packages.exitCode==0&&Pattern.compile("(?m)^package:[A-Za-z0-9_.]+$").matcher(packages.text).find()){apps.state=State.READY;apps.reason="应用列表可读取；系统应用操作仍受固件权限限制";}else if(denied(packages)){apps.state=State.LIMITED;apps.reason="应用列表读取被拒绝";}
   AdbClient.Result help=read(d,"pm help");if(!denied(help)&&Pattern.compile("(?m)(?:^|\\s)install(?:\\s|\\[|$)").matcher(help.text).find()){s.items.get(INSTALL).state=State.READY;s.items.get(INSTALL).reason="安装接口已识别；签名、系统、存储和厂商限制需逐包核验";}
   tool(s,d,SCREENSHOT,"screencap","截图工具已识别；未读取、保存或上传电视画面，待用户主动截图验证");
   AdbClient.Result directory=read(d,"ls -d /sdcard");if(directory.exitCode==0&&directory.text.trim().equals("/sdcard")){s.items.get(FILES).state=State.READY;s.items.get(FILES).reason="共享存储入口可响应；未创建测试文件，上传与其他路径权限待操作核验";}else if(denied(directory)){s.items.get(FILES).state=State.LIMITED;s.items.get(FILES).reason="共享存储检查被拒绝；其他路径仍可能可用";}
   AdbClient.Result users=read(d,"dumpsys accessibility");if(users.exitCode==0)try{s.user=DesktopAssistant.activeUser(users.text);}catch(Exception ignored){}
   if(s.user!=0){s.items.get(INSTALL).state=State.LIMITED;s.items.get(INSTALL).reason="当前 Android 用户未确认或不是用户 0；此版停止安装与更新";s.items.get(DESKTOP).reason="当前用户未确认，停止桌面修改；只读诊断仍可使用";}
   Map<String,String> last=parseProperties(read(d,"getprop").raw);for(String key:new String[]{"ro.serialno","ro.build.fingerprint","ro.build.version.sdk"})if(!Objects.equals(s.properties.get(key),last.get(key)))throw new IOException("device changed");
  }catch(Exception e){s.partial=true;for(Item i:s.items.values())if(!i.name.equals("桌面与守护")){i.state=State.UNKNOWN;i.reason="检测未完成，无法形成可靠结论，请重新检测";}}
  return s;
 }
 public static String featureFor(String action){if(action.equals("读取安装包路径"))return APPS;if(action.contains("安装")||action.contains("传输手机应用"))return INSTALL;if(action.equals("读取应用列表"))return APPS;if(action.equals("截取屏幕"))return SCREENSHOT;if(action.equals("上传文件")||action.equals("下载文件")||action.equals("读取目录"))return FILES;return null;}
}
