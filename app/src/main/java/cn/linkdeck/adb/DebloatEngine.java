package cn.linkdeck.adb;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** A narrow, per-item operation. Never construct or execute a multi-package mutation. */
public final class DebloatEngine {
 public interface Shell {Reply run(String command)throws Exception;}
 public interface Ledger {void remember(String pkg)throws Exception;void forget(String pkg)throws Exception;boolean contains(String pkg);}
 public static final class Reply {public final int code;public final String raw;public Reply(int c,String r){code=c;raw=r==null?"":r;}}
 public static final class Snapshot {public final Set<String> installed,system;Snapshot(Set<String> i,Set<String> s){installed=i;system=s;}}
 public static final class Outcome {public final String summary,raw;Outcome(String s,String r){summary=s;raw=r;}}
 private static final Pattern PACKAGE_LINE=Pattern.compile("package:([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*)");
 private static final Pattern PACKAGE_HEADER=Pattern.compile("(?m)^\\s*Package \\[(.+?)\\]");
 private static final Pattern RESPONSE_FAILURE=Pattern.compile("(?im)^\\s*(failure|error|exception|permission denial|unknown command|securityexception)(?:[\\s:\\[]|$)");
 private static final Pattern SYSTEM_UID=Pattern.compile("(?i)(?:userId|appId)\\s*[=:]\\s*1000(?:\\s|$)|android\\.uid\\.system");
 private static final String[] CRITICAL={"android.intent.category.HOME","android.view.InputMethod","android.permission.BIND_INPUT_METHOD","android.settings.SETTINGS","android.intent.action.INSTALL_PACKAGE","android.intent.action.UNINSTALL_PACKAGE","android.content.pm.action.CONFIRM_PERMISSIONS","android.permission.BIND_ACCESSIBILITY_SERVICE","android.permission.INSTALL_PACKAGES","android.permission.DELETE_PACKAGES"};
 private final DebloatCatalog catalog;private final Shell shell;private final Ledger ledger;
 public DebloatEngine(DebloatCatalog c,Shell s,Ledger l){if(c==null||s==null||l==null)throw new IllegalArgumentException("missing dependency");catalog=c;shell=s;ledger=l;}
 private static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
 private static String text(Reply r){return r.raw.trim();}
 private static boolean failure(Reply r){return r.code!=0||RESPONSE_FAILURE.matcher(r.raw).find();}
 private static String raw(Reply r){return r.raw.isEmpty()?"（电视没有返回文字）":r.raw;}
 private Reply command(String cmd)throws Exception {Reply r=shell.run(cmd);if(r==null)throw new IOException("电视未返回命令结果："+cmd);return r;}
 public static Set<String> parsePackages(Reply reply)throws IOException {
  if(reply==null||failure(reply))throw new IOException("读取用户 0 应用失败：\n"+(reply==null?"无返回":raw(reply)));
  Set<String> found=new HashSet<>();for(String line:reply.raw.split("\\r?\\n")){line=line.trim();if(line.isEmpty())continue;Matcher m=PACKAGE_LINE.matcher(line);if(!m.matches())throw new IOException("应用列表格式无法核实，已停止：\n"+line);found.add(m.group(1));}
  if(!found.contains("android"))throw new IOException("应用列表不完整，无法核实用户 0 的安装状态");
  return found;
 }
 public Snapshot scan()throws Exception{return new Snapshot(parsePackages(command("pm list packages --user 0")),parsePackages(command("pm list packages -s --user 0")));}
 private boolean installed(String pkg)throws Exception{return parsePackages(command("pm list packages --user 0")).contains(pkg);}
 private void checkDeviceRole(String pkg)throws Exception {
  Reply dump=command("dumpsys package "+quote(pkg));if(failure(dump))throw new IOException("无法核查应用组件，已停止：\n"+raw(dump));
  Matcher m=PACKAGE_HEADER.matcher(dump.raw);boolean exact=false;while(m.find())if(m.group(1).equals(pkg)){exact=true;break;}
  if(!exact||!dump.raw.contains("versionCode="))throw new IOException("设备未提供可核实的完整应用信息，已停止：\n"+raw(dump));
  for(String component:CRITICAL)if(dump.raw.contains(component))throw new IOException("检测到该应用可能承担关键系统功能，已禁止卸载："+component);
  if(SYSTEM_UID.matcher(dump.raw).find())throw new IOException("该应用使用系统身份，已禁止卸载");
 }
 private void checkPreinstalledPath(String pkg)throws Exception {
  Reply reply=command("pm path "+quote(pkg));if(failure(reply))throw new IOException("无法核对预装路径，已停止：\n"+raw(reply));
  int count=0;for(String line:reply.raw.split("\\r?\\n")){line=line.trim();if(line.isEmpty())continue;
   if(!line.startsWith("package:/")||line.contains("..")||line.contains("\\0"))throw new IOException("预装路径无法核实，已停止：\n"+line);
   String path=line.substring(8);if(!(path.startsWith("/system/")||path.startsWith("/system_ext/")||path.startsWith("/product/")||path.startsWith("/vendor/")||path.startsWith("/oem/")))throw new IOException("未确认应用位于系统预装分区，无法保证可恢复；本次未卸载。电视路径：\n"+line);
   count++;
  }
  if(count==0)throw new IOException("电视没有返回预装路径，已停止卸载");
 }
 private void checkRestoreSupport()throws Exception {
  Reply help=command("pm help");String h=help.raw.toLowerCase(Locale.ROOT);
  // Some older pm implementations return nonzero for help; require the advertised subcommand, regardless of exit status.
  if(!Pattern.compile("(?im)^\\s*(?:pm\\s+)?install-existing\\b[^\\r\\n]*--user\\b").matcher(h).find())throw new IOException("电视未显示支持用户 0 的应用恢复命令，为避免无法恢复，本次未卸载。电视返回：\n"+raw(help));
 }
 public Outcome uninstall(String pkg)throws Exception {
  catalog.allowed(pkg);Snapshot before=scan();if(!before.installed.contains(pkg))throw new IOException("该应用当前不在用户 0 的安装列表，未执行卸载");
  if(!before.system.contains(pkg))throw new IOException("该应用未被电视识别为系统预装应用；无法确认可恢复，本次未卸载");
  checkPreinstalledPath(pkg);checkDeviceRole(pkg);checkRestoreSupport();
  ledger.remember(pkg); // Persist recovery record before the device can be changed.
  Reply response;
  try{response=command("pm uninstall -k --user 0 "+quote(pkg));}
  catch(Exception e){throw new IOException("卸载命令结果未确认，请勿重复操作；恢复记录已保留。原始信息：\n"+e.getMessage(),e);}
  boolean remains;
  try{remains=installed(pkg);}catch(Exception e){throw new IOException("卸载后无法复查，状态未确认；恢复记录已保留。电视原始返回：\n"+raw(response)+"\n复查信息："+e.getMessage(),e);}
  if(failure(response)){
   if(remains){try{ledger.forget(pkg);}catch(Exception ignored){}}
   throw new IOException((remains?"电视拒绝卸载，应用仍在用户 0。可能是固件限制或权限不足。":"电视返回失败，但应用不在用户 0 列表；状态不一致，请到下方核对恢复记录。")+"\n电视原始返回：\n"+raw(response));
  }
  if(remains){try{ledger.forget(pkg);}catch(Exception ignored){}throw new IOException("电视返回完成，但复查仍显示应用已安装；不算卸载成功。电视原始返回：\n"+raw(response));}
  if(!text(response).matches("(?is).*\\bsuccess\\b.*"))throw new IOException("应用已不在用户 0 的列表，但电视未返回明确成功信息；恢复记录已保留，请核实。电视原始返回：\n"+raw(response));
  return new Outcome("已复查：用户 0 的应用列表中已无此应用。恢复记录已保留，恢复是否成功取决于电视支持。",raw(response));
 }
 public Outcome restore(String pkg)throws Exception {
  catalog.allowed(pkg);if(!ledger.contains(pkg))throw new IOException("本机没有该电视的恢复记录，未执行恢复");
  if(installed(pkg)){ledger.forget(pkg);return new Outcome("经复查该应用已在用户 0 安装；已移除恢复记录。","");}
  Reply response;
  try{response=command("pm install-existing --user 0 "+quote(pkg));}
  catch(Exception e){throw new IOException("恢复结果未确认，记录已保留。原始信息：\n"+e.getMessage(),e);}
  boolean present;
  try{present=installed(pkg);}catch(Exception e){throw new IOException("恢复后无法复查，记录已保留。电视原始返回：\n"+raw(response)+"\n复查信息："+e.getMessage(),e);}
  if(failure(response)||!present)throw new IOException((present?"电视返回失败但应用已在用户 0 列表，结果不一致。":"电视未确认恢复；可能是固件不支持或应用文件不再保留。")+"恢复记录已保留。\n电视原始返回：\n"+raw(response));
  ledger.forget(pkg);return new Outcome("已复查：应用已恢复到用户 0。",raw(response));
 }
}
