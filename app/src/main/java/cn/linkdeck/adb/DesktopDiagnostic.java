package cn.linkdeck.adb;
import java.util.*;
/** Three bounded, read-only observations. Exports structured evidence, never raw system dumps. */
public final class DesktopDiagnostic {
 public static final String NORMAL="当前检查正常",MISSING="配置缺失",CRASH="应用崩溃",UNKNOWN="尚未确认";
 public static final class Verdict{public final String state,reason;Verdict(String s,String r){state=s;reason=r;}}
 public static final class Result{public final List<DesktopAssistant.Snapshot> samples=new ArrayList<>();public boolean interrupted,changed,newCrash;public String problem="";public DesktopAssistant.Snapshot last(){return samples.isEmpty()?null:samples.get(samples.size()-1);}public Verdict verdict(){if(newCrash)return new Verdict(CRASH,"采集期间新增当前 "+(last()!=null?last().profile.displayName:"桌面")+" 版本崩溃；请先处理应用异常，不反复开启守护。");if(interrupted||changed)return new Verdict(UNKNOWN,problem);return classify(last());}}
 public static Verdict classify(DesktopAssistant.Snapshot s){
  if(s==null)return new Verdict(UNKNOWN,"尚未完成电视读取。");
  if("应用崩溃".equals(s.outcome))return new Verdict(CRASH,s.outcomeDetail);
  if(!s.installed)return new Verdict(s.lite?UNKNOWN:MISSING,s.lite?"已安装 Lite；完整版守护检查不适用于 Lite，请核对其默认桌面行为。":"未检测到 "+s.profile.displayName+" 完整版，可先选择适合系统的官方版本。");
  if(s.userId!=0)return new Verdict(UNKNOWN,"当前用户未确认或非用户 0，本版不修改其桌面设置。");
  boolean direct=s.known.contains(DesktopAssistant.DEFAULT)&&s.profile.home.equals(s.values.get(DesktopAssistant.DEFAULT));
  if(!direct){
   for(String k:new String[]{DesktopAssistant.PERMISSION,DesktopAssistant.ACCESS,DesktopAssistant.ENABLED})if(!s.known.contains(k))return new Verdict(UNKNOWN,"核心配置未完整读到，请先检查连接与读取结果。");
   if(!"true".equals(s.values.get(DesktopAssistant.PERMISSION))||!DesktopAssistant.contains(s.values.get(DesktopAssistant.ACCESS),s.profile.guard)||!"1".equals(s.values.get(DesktopAssistant.ENABLED)))return new Verdict(MISSING,"守护权限或开关未完整配置；先查看崩溃证据，再选择具体修复项。");
   if(!Boolean.TRUE.equals(s.bound))return new Verdict(UNKNOWN,s.bound==null?"系统未提供可识别的绑定信息；可用实体主页键独立验收。":"配置完整但守护未绑定；需区分应用崩溃、系统服务列表或权限限制。");
  }
  if(!s.crashesKnown)return new Verdict(UNKNOWN,"配置已读到，但崩溃记录无法完整核实；不能当作没有崩溃。");
  return new Verdict(NORMAL,(direct?"已读到 "+s.profile.displayName+" 默认桌面。":"守护配置完整且检测到绑定。")+"未见本次新增异常；主页键和开机仍需人工验收。");
 }
 public static Result capture(DesktopAssistant.Device d,String endpoint,DesktopAssistant.Profile profile)throws Exception{
  Result r=new Result();DesktopAssistant.Snapshot first=null;
  for(int i=0;i<3;i++){
   DesktopAssistant.Snapshot s;try{if(i>0)d.pause(1500);s=DesktopAssistant.inspect(d,endpoint,profile);}catch(Exception e){r.interrupted=true;r.problem="采集读取中断（"+e.getClass().getSimpleName()+"），已保留先前读到的结果；不代表电视配置已改变。";break;}
   if(first==null)first=s;else if(!first.id.equals(s.id)||first.code!=s.code||first.userId!=s.userId){r.changed=true;r.problem="采集期间设备、当前用户或安装版本变化，已停止合并结果，请重新采集。";break;}
   r.samples.add(s);
   if(first.crashesKnown&&s.crashesKnown)for(String id:s.crashIds)if(!first.crashIds.contains(id))r.newCrash=true;
  }
  return r;
 }
 public static String report(Result r){StringBuilder b=new StringBuilder("轻连 ADB "+AppVersion.NAME+" · 一键故障报告\n");Verdict v=r.verdict();b.append("结果：").append(v.state).append("\n依据：").append(v.reason).append("\n读取次数：").append(r.samples.size()).append(" / 3\n\n");
  for(int i=0;i<r.samples.size();i++){DesktopAssistant.Snapshot s=r.samples.get(i);b.append("--- 观察 ").append(i+1).append(" ---\n").append(DesktopAssistant.report(s,null)).append("\n");if(r.newCrash&&i>0)for(String id:s.crashIds)if(!r.samples.get(0).crashIds.contains(id))b.append("新增异常分类：").append(s.crashReasons.get(id)).append("\n");}
  b.append("\n采集仅执行只读查询，不自动启动、授权、安装或重启电视。报告不含 IP、序列号、Wi-Fi、账号或原始崩溃消息；无法读取及未留存故障仍可能无法定位。当前检查正常不代表长期稳定或主页键、开机已通过。\n");return b.toString();}
}
