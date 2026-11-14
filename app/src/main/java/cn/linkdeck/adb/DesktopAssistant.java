package cn.linkdeck.adb;
import java.io.*;
import java.security.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** A bounded, reversible desktop workflow. Stored plans contain data, never executable commands. */
public final class DesktopAssistant {
  public static final String PKG="app.orchard.tv", GUARD=PKG+"/"+PKG+".guard.HomeGuardService", LISTENER=PKG+"/"+PKG+".guard.RevivalListener", HOME=PKG+"/"+PKG+".MainActivity";
  public static final String ACCESS="enabled_accessibility_services", ENABLED="accessibility_enabled", NOTIFY="enabled_notification_listeners", PERMISSION="write_secure_settings", DEFAULT="default_home";
  public interface Device {String shell(String command)throws Exception;default void pause(long millis)throws Exception{Thread.sleep(millis);}}
  public interface Store {JSONObject load(String id)throws Exception;void save(String id,JSONObject value)throws Exception;}
  public static final class Snapshot {
    public String id,model,brand,release,version="未安装";public int sdk;public long code=-1,time=System.currentTimeMillis();
    public boolean installed,lite,guardDeclared,listenerDeclared,homeDeclared,xiaomi;
    public Boolean bound;public String focus,bindingEvidence;
    public int userId=-1;public String outcome, outcomeDetail;
    public boolean crashesKnown;public String crashNote="尚未读取";
    public final Set<String> crashIds=new HashSet<>();public final Map<String,String> crashReasons=new HashMap<>();
    public final Map<String,String> values=new LinkedHashMap<>();
    public final Set<String> known=new HashSet<>();
    public final Set<String> unread=new LinkedHashSet<>();
  }
  private static String read(Device d,String cmd)throws Exception{return d.shell(cmd).trim();}
  private static String packagePath(Device d,String pkg)throws Exception {
    String raw;
    try{raw=read(d,"pm path "+pkg);}catch(IOException e){String m=e.getMessage();if(m!=null&&(m.contains("Error: package "+pkg+" was not found")||m.contains("Unknown package: "+pkg)))return "";throw e;}
    if(raw.equals("Error: package "+pkg+" was not found")||raw.equals("Error: Unknown package: "+pkg))return "";
    if(!raw.isEmpty()&&!raw.startsWith("package:"))throw new IOException("无法确认应用安装状态："+pkg);
    return raw;
  }
  private static String nullable(String s){return s.equals("null")?null:s;}
  private static void settingValue(String key,String v)throws IOException {
    if(v==null||v.isEmpty())return;
    if(key.equals(ENABLED)){if(!v.equals("0")&&!v.equals("1"))throw new IOException("无障碍开关状态未知");return;}
    for(String token:v.split(":"))if(!token.isEmpty()&&!token.matches("[A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+"))throw new IOException("服务列表格式未知，未修改设置");
  }
  public static String canonical(String component) {
    String s=component.trim();int slash=s.indexOf('/');if(slash<1)return s;
    return s.substring(slash+1).startsWith(".")?s.substring(0,slash+1)+s.substring(0,slash)+s.substring(slash+1):s;
  }
  public static boolean contains(String list,String component) {
    if(list==null)return false;for(String x:list.split(":"))if(canonical(x).equals(component))return true;return false;
  }
  public static String add(String list,String component) {if(contains(list,component))return list;return list==null||list.isEmpty()?component:list+":"+component;}
  public static String remove(String list,String component) {
    if(list==null)return null;List<String> kept=new ArrayList<>();for(String x:list.split(":"))if(!x.isEmpty()&&!canonical(x).equals(component))kept.add(x);return String.join(":",kept);
  }
  public static String component(String raw) {
    Matcher m=Pattern.compile("(?:^|[\\s{])([A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+)").matcher(raw);
    String found=null;while(m.find()){String c=canonical(m.group(1));if(c.contains("ResolverActivity")||c.contains("ChooserActivity"))continue;found=c;}
    return found;
  }
  public static Boolean permission(String dump) {
    Matcher m=Pattern.compile("android\\.permission\\.WRITE_SECURE_SETTINGS:\\s*granted=(true|false)\\b").matcher(dump);
    if(m.find())return Boolean.valueOf(m.group(1));
    Matcher legacy=Pattern.compile("(?m)^([ \t]*)grantedPermissions:[ \t]*$").matcher(dump);
    if(legacy.find()&&dump.contains("android.permission.WRITE_SECURE_SETTINGS")) {
      int indent=legacy.group(1).length();
      for(String line:dump.substring(legacy.end()).split("\\n")) {
        if(line.trim().isEmpty())continue;int spaces=0;while(spaces<line.length()&&Character.isWhitespace(line.charAt(spaces)))spaces++;
        if(spaces<=indent)break;
        if(line.trim().equals("android.permission.WRITE_SECURE_SETTINGS"))return true;
      }
      return false;
    }
    // AOSP omits denied install permissions entirely. Accept absence only from a
    // complete, owner-user package dump with exactly one occurrence in requested permissions.
    String permission="android.permission.WRITE_SECURE_SETTINGS";
    int first=dump.indexOf(permission);
    if(first>=0&&dump.indexOf(permission,first+permission.length())<0
        &&dump.contains("Package [app.orchard.tv]")&&dump.contains("installPermissionsFixed=true")
        &&dump.contains("requested permissions:")&&!dump.toLowerCase(Locale.ROOT).contains("shareduser")
        &&Pattern.compile("(?m)^\\s*User 0:.*\\binstalled=true\\b").matcher(dump).find())return false;
    return null;
  }
  private static final Pattern USER_BLOCK=Pattern.compile("(?m)^\\s*User state\\[\\s*attributes:\\{([^}]*)\\}");
  private static int blockUser(String attrs){Matcher id=Pattern.compile("\\bid=(\\d+)\\b").matcher(attrs);if(!id.find())return -1;try{return Integer.parseInt(id.group(1));}catch(NumberFormatException e){return -1;}}
  public static int activeUser(String dump) {
    Matcher header=Pattern.compile("(?m)^\\s*currentUserId=(\\d+)\\s*$").matcher(dump);int declared=-1,headers=0;
    while(header.find()){headers++;try{declared=Integer.parseInt(header.group(1));}catch(NumberFormatException e){return -1;}}
    if(headers>1)return -1;
    Matcher m=USER_BLOCK.matcher(dump);int selected=-1,active=0;List<Integer> ids=new ArrayList<>();List<String> attrs=new ArrayList<>();
    while(m.find()){String value=m.group(1);int id=blockUser(value);if(id<0)return -1;ids.add(id);attrs.add(value);if(Pattern.compile("\\bcurrentUser=true\\b").matcher(value).find()){selected=id;active++;}}
    if(active>1||headers==1&&active==1&&selected!=declared)return -1;
    int user=headers==1?declared:active==1?selected:-1;if(user<0)return -1;int matches=0;
    for(int i=0;i<ids.size();i++)if(ids.get(i)==user){matches++;if(Pattern.compile("\\bcurrentUser=false\\b").matcher(attrs.get(i)).find())return -1;}
    return matches==1?user:-1;
  }
  private static String activeBlock(String dump) {
    Matcher m=USER_BLOCK.matcher(dump);List<Integer> starts=new ArrayList<>(),ends=new ArrayList<>();List<Boolean> active=new ArrayList<>();
    while(m.find()){starts.add(m.start());ends.add(m.end());active.add(blockUser(m.group(1))==activeUser(dump));}
    if(starts.isEmpty())return dump;
    if(activeUser(dump)<0)return null;
    for(int i=0;i<starts.size();i++)if(active.get(i))return dump.substring(ends.get(i),i+1<starts.size()?starts.get(i+1):dump.length());
    return null;
  }
  public static boolean exactHomeCategory(String dump) {
    return Pattern.compile("(?m)^\\s*(?:Category:\\s*\"android\\.intent\\.category\\.HOME\"|android\\.intent\\.category\\.HOME)\\s*$").matcher(dump).find();
  }
  private static boolean hasComponent(String raw,String wanted) {
    Matcher m=Pattern.compile("([A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+)").matcher(raw);
    while(m.find())if(canonical(m.group(1)).equals(wanted))return true;return false;
  }
  /** Only same-package, same-version records are retained; never persist raw dumps. */
  public static void crashRecords(Snapshot s,String raw) throws Exception {
    s.crashIds.clear();s.crashReasons.clear();
    if(raw.length()>768*1024||!raw.contains("Drop box contents:")||!raw.contains("Searching for: data_app_crash")) {
      s.crashesKnown=false;s.crashNote="系统崩溃记录不可完整识别，不能据此判断无崩溃";return;
    }
    s.crashesKnown=true;Map<String,Integer> occurrences=new HashMap<>();
    for(String entry:raw.split("(?m)^={8,}\\s*$")) {
      if(!Pattern.compile("(?m)^Process: "+Pattern.quote(PKG)+"\\s*$").matcher(entry).find())continue;
      Matcher version=Pattern.compile("(?m)^Package: "+Pattern.quote(PKG)+" v(\\d+)(?:\\s|$)").matcher(entry);
      if(!version.find()||s.code<0){s.crashesKnown=false;continue;}
      if(!version.group(1).equals(Long.toString(s.code)))continue;
      Matcher stamp=Pattern.compile("(?m)^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) data_app_crash\\b").matcher(entry);
      if(!stamp.find()){s.crashesKnown=false;continue;}
      StringBuilder trace=new StringBuilder();for(String line:entry.split("\n"))if(line.matches("[A-Za-z_$][A-Za-z0-9_.$]*(?:Exception|Error)(?::.*)?")||line.matches("\\s+(?:at |Caused by: |Suppressed: ).*"))trace.append(line.trim()).append('\n');
      if(trace.length()==0){s.crashesKnown=false;continue;}
      byte[] bytes=MessageDigest.getInstance("SHA-256").digest((stamp.group(1)+"|"+s.code+"|"+trace).getBytes("UTF-8"));StringBuilder hash=new StringBuilder();for(byte b:bytes)hash.append(String.format(Locale.ROOT,"%02x",b&255));
      String root=hash.toString();int count=occurrences.containsKey(root)?occurrences.get(root)+1:1;occurrences.put(root,count);String record=root+"#"+count;
      String reason=entry.contains("NoSuchMethodError")&&entry.contains("getSystemService")&&entry.contains("HomeGuardService.onServiceConnected")
          ?"Aerial 守护调用了系统不存在的 getSystemService 方法（兼容性崩溃）":"Aerial 当前版本发生应用崩溃";
      s.crashIds.add(record);s.crashReasons.put(record,reason);
    }
    s.crashNote=s.crashesKnown?(s.crashIds.isEmpty()?"未读到当前版本的已保存崩溃；不代表持续运行已验证":"读到当前版本历史崩溃；需比较操作前后记录，不能仅凭历史记录封禁版本") :"崩溃记录不完整，状态未确认";
  }
  public static final class VerificationFailure extends IOException {
    public final Snapshot snapshot;
    VerificationFailure(Snapshot s,String state,String reason){super(reason);snapshot=s;s.outcome=state;s.outcomeDetail=reason;}
  }
  public static final class UncertainBackup extends IOException {
    UncertainBackup(){super("备份含写入结果未确认的记录，本次没有自动恢复或暂停 Aerial。请先审阅并选择是否保留当前设置。");}
  }
  private static void supportedUser(Snapshot s)throws IOException {
    if(s.userId!=0)throw new IOException(s.userId<0?"无法确认当前 Android 用户，已停止修改":"当前 Android 用户为 "+s.userId+"；本版仅在用户 0 下修复，已停止修改");
  }
  private static void newCrash(Snapshot baseline,Snapshot now)throws VerificationFailure {
    if(!baseline.crashesKnown||!now.crashesKnown||baseline.code!=now.code)return;
    for(String id:now.crashIds)if(!baseline.crashIds.contains(id))throw new VerificationFailure(now,"应用崩溃",now.crashReasons.get(id)+"。已停止后续修改；已完成的改动与备份保留。继续开启或重启无法修复应用代码。");
  }
  private static void goals(Snapshot s,List<String> selected)throws VerificationFailure {
    for(String key:selected){boolean known=s.known.contains(key),met=false;
      if(key.equals(ACCESS)){known=known&&s.known.contains(ENABLED);met=contains(s.values.get(ACCESS),GUARD)&&"1".equals(s.values.get(ENABLED));}
      else if(key.equals(NOTIFY))met=contains(s.values.get(NOTIFY),LISTENER);
      else if(key.equals(PERMISSION))met="true".equals(s.values.get(PERMISSION));
      else if(key.equals(DEFAULT))met=HOME.equals(s.values.get(DEFAULT));
      if(!known||!met)throw new VerificationFailure(s,known?"未生效 / 部分完成":"结果未确认","最终复查未确认“"+label(key)+"”生效；没有重复写入。请查看运行状态与诊断，已保留备份。");
    }
  }
  public static Snapshot verifyOperation(Device d,String endpoint,Snapshot baseline,List<String> selected,boolean requireGuard)throws Exception {
    Snapshot result=null;
    for(int attempt=0;attempt<3;attempt++) {
      if(attempt>0)d.pause(1500);
      result=inspect(d,endpoint);identityCheck(baseline,result);supportedUser(result);
      if(result.code!=baseline.code)throw new VerificationFailure(result,"结果未确认","Aerial 版本已变化，已停止验证");
      newCrash(baseline,result);goals(result,selected);
      if(!requireGuard||attempt==2&&Boolean.TRUE.equals(result.bound)){
        result.outcome=requireGuard?"配置与绑定已核实":"所选配置已核实";
        result.outcomeDetail=requireGuard?"检测到守护已绑定；仍需实体主页键和开机验收。":"所选配置已核实；不代表守护或主页接管已成功。";return result;
      }
    }
    throw new VerificationFailure(result,result.bound==null?"运行状态未确认":"配置完成，守护未运行",result.bound==null?"所选配置已复查，但限次检查无法确认守护绑定；没有重复写入。":"所选配置已复查，但限次检查仍未检测到守护绑定；已停止，没有重复开启或重启。请查看新的崩溃记录或系统限制。");
  }
  /** A start request is not proof that the activity stayed alive. */
  public static Snapshot verifyLaunch(Device d,String endpoint,Snapshot baseline)throws Exception {
    Snapshot result=null;boolean sawAerial=false;
    for(int i=0;i<3;i++){
      if(i>0)d.pause(1500);result=inspect(d,endpoint);identityCheck(baseline,result);supportedUser(result);
      if(result.code!=baseline.code)throw new VerificationFailure(result,"结果未确认","Aerial 版本已变化，已停止启动验证");
      newCrash(baseline,result);
      boolean visible=result.focus!=null&&result.focus.startsWith(PKG+"/");
      if(sawAerial&&!visible)throw new VerificationFailure(result,"启动未确认","启动后的复查没有持续看到 Aerial 前台；请查看崩溃记录或其他界面是否覆盖。没有反复启动。");
      sawAerial=sawAerial||visible;
    }
    if(!sawAerial)throw new VerificationFailure(result,"启动未确认","限次检查未看到 Aerial 前台，没有重复启动。");
    boolean directHome=HOME.equals(result.values.get(DEFAULT));
    if(!directHome&&!Boolean.TRUE.equals(result.bound))throw new VerificationFailure(result,result.bound==null?"运行状态未确认":"启动观察完成，守护未运行","启动后看到 Aerial 前台，但未确认守护绑定；主页接管未通过。没有反复启动。");
    result.outcome="短时启动观察通过";result.outcomeDetail="限次观察中 Aerial 保持前台；"+(directHome?"系统默认桌面为 Aerial。":"检测到守护绑定。")+"仍需实体主页键与开机验收，不代表长期稳定性已验证。";return result;
  }
  public static Boolean bound(String dump) {
    dump=activeBlock(dump);if(dump==null)return null;
    Matcher m=Pattern.compile("(?im)^\\s*(?:Bound services|services):\\s*\\{").matcher(dump);
    if(!m.find())return null;
    int start=m.end(),depth=1,end=start;
    for(;end<dump.length()&&depth>0;end++){char c=dump.charAt(end);if(c=='{')depth++;if(c=='}')depth--;}
    if(depth!=0)return null;
    String section=dump.substring(start,end-1);
    if(section.trim().isEmpty())return false;
    int at=0,nodes=0;boolean unidentified=false;
    while((at=section.indexOf("Service[",at))>=0) {
      int begin=at+8,level=1,endNode=begin;
      for(;endNode<section.length()&&level>0;endNode++){char ch=section.charAt(endNode);if(ch=='[')level++;if(ch==']')level--;}
      if(level!=0)return null;
      String node=section.substring(begin,endNode-1);
      Matcher id=Pattern.compile("\\bid\\s*=\\s*([A-Za-z][A-Za-z0-9_.$]*(?:/[A-Za-z0-9_.$]+)?)").matcher(node);
      if(!id.find()||(!id.group(1).contains(".")&&!id.group(1).contains("/")))unidentified=true;
      else if(canonical(id.group(1)).equals(GUARD)||id.group(1).equals(PKG+".guard.HomeGuardService"))return true;
      nodes++;at=endNode;
    }
    if(nodes>0)return unidentified?null:Boolean.FALSE;
    // Some vendors print a raw component list instead of Service objects.
    Matcher raw=Pattern.compile("([A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+)").matcher(section);
    while(raw.find())if(canonical(raw.group(1)).equals(GUARD))return true;
    return null;
  }
  public static String identity(String endpoint,String serial,String fingerprint,String model)throws Exception {
    boolean unique=serial!=null&&serial.length()>3&&!Arrays.asList("unknown","null","0123456789abcdef").contains(serial.toLowerCase(Locale.ROOT))&&!serial.matches("0+");
    byte[] digest=MessageDigest.getInstance("SHA-256").digest(((unique?serial:endpoint)+"|"+fingerprint+"|"+model).getBytes("UTF-8"));
    StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
  }
  private static void value(Snapshot s,String key,String v){s.values.put(key,v);s.known.add(key);}
  public static Snapshot inspect(Device d,String endpoint)throws Exception {
    Snapshot s=new Snapshot();s.model=read(d,"getprop ro.product.model");s.brand=read(d,"getprop ro.product.brand");s.release=read(d,"getprop ro.build.version.release");
    try{s.sdk=Integer.parseInt(read(d,"getprop ro.build.version.sdk"));}catch(NumberFormatException e){throw new IOException("系统版本读取失败，未作桌面支持判断");}
    String serial=read(d,"getprop ro.serialno"),fingerprint=read(d,"getprop ro.build.fingerprint");
    if(s.model.isEmpty()||fingerprint.isEmpty()||fingerprint.equalsIgnoreCase("unknown")||fingerprint.equals("null"))throw new IOException("电视身份信息不足，无法绑定可恢复备份。");
    s.id=identity(endpoint,serial,fingerprint,s.model);
    String path=packagePath(d,PKG);
    if(!path.isEmpty()&&!path.startsWith("package:")&&!path.equals("Error: package "+PKG+" was not found"))throw new IOException("无法确认 Aerial 安装状态："+path);
    s.installed=path.startsWith("package:");
    if(s.installed){String dump=read(d,"dumpsys package "+PKG);s.code=AerialSupport.versionCode(dump);s.version=AerialSupport.versionName(dump);s.guardDeclared=dump.contains("guard.HomeGuardService");s.listenerDeclared=dump.contains("guard.RevivalListener");s.homeDeclared=exactHomeCategory(dump);Boolean p=permission(dump);if(p!=null)value(s,PERMISSION,p.toString());else s.unread.add(PERMISSION);}
    else {value(s,PERMISSION,"false");try{s.lite=packagePath(d,"app.orchard.tv.lite").startsWith("package:");}catch(Exception e){s.unread.add("lite");}}
    try{s.xiaomi=packagePath(d,"com.mitv.tvhome").startsWith("package:");}catch(Exception e){s.unread.add("xiaomi");}
    s.xiaomi=s.xiaomi||s.brand.toLowerCase(Locale.ROOT).contains("xiaomi")||s.brand.toLowerCase(Locale.ROOT).contains("redmi")||s.brand.contains("小米");
    try{String raw=read(d,"dumpsys accessibility");s.userId=activeUser(raw);s.bound=bound(raw);s.bindingEvidence=s.bound==null&&raw.contains("Service[label=")?"系统绑定列表仅含服务描述，缺少可靠组件标识，不能据此判定守护未运行。":s.bound==null?"系统运行状态输出未识别。":s.bound?"绑定列表识别到 Aerial 守护组件。":"已识别的绑定列表中未发现 Aerial 守护组件。";}catch(Exception e){s.unread.add("bound");s.bindingEvidence="运行状态读取失败，未作运行判断。";}
    for(String k:new String[]{ACCESS,ENABLED,NOTIFY})try {if(s.userId!=0)throw new IOException("当前用户未支持");String v=read(d,"settings --user 0 get secure "+k);if(v.startsWith("Error")||v.contains("Exception")||v.length()>32768)throw new IOException("系统设置返回无效");if(k.equals(ENABLED)&&!Arrays.asList("0","1","null","").contains(v))throw new IOException("无障碍开关状态未知");settingValue(k,nullable(v));value(s,k,nullable(v));}catch(Exception e){s.unread.add(k);}
    try {String home=null;try{home=component(read(d,"cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.DEFAULT"));}catch(Exception ignored){}if(home==null)home=component(read(d,"pm resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.DEFAULT"));if(home!=null)value(s,DEFAULT,home);else s.unread.add(DEFAULT);}catch(Exception e){s.unread.add(DEFAULT);}

    try{s.focus=focus(read(d,"dumpsys window windows"));}catch(Exception e){s.unread.add("focus");}
    if(s.homeDeclared&&s.sdk>=24)try{s.homeDeclared=hasComponent(read(d,"cmd package query-activities --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME"),HOME);}catch(Exception e){s.homeDeclared=false;s.unread.add("home_candidates");}
    if(s.installed)try{crashRecords(s,read(d,"dumpsys dropbox --print data_app_crash"));}catch(Exception e){s.crashesKnown=false;s.crashNote="崩溃记录读取失败，不能据此判断无崩溃";}
    return s;
  }
  public static String focus(String raw) {for(String line:raw.split("\n"))if(line.contains("mCurrentFocus")){String c=component(line);if(c!=null)return c;}return null;}
  public static List<String> candidates(Snapshot s) {
    List<String> r=new ArrayList<>();if(!s.installed||s.code<0||s.userId!=0)return r;
    if(s.known.contains(PERMISSION)&&"false".equals(s.values.get(PERMISSION)))r.add(PERMISSION);
    if(s.guardDeclared&&s.known.contains(ACCESS)&&s.known.contains(ENABLED)&&(!contains(s.values.get(ACCESS),GUARD)||!"1".equals(s.values.get(ENABLED))))r.add(ACCESS);
    if(s.listenerDeclared&&s.known.contains(NOTIFY)&&!contains(s.values.get(NOTIFY),LISTENER))r.add(NOTIFY);
    if(s.sdk>=24&&!s.xiaomi&&!s.unread.contains("xiaomi")&&s.homeDeclared&&s.known.contains(DEFAULT)&&!HOME.equals(s.values.get(DEFAULT)))r.add(DEFAULT);
    return r;
  }
  public static String label(String k) {switch(k){case PERMISSION:return "授予 Aerial 修改安全设置权限";case ACCESS:return "启用 Aerial 无障碍守护（保留其他服务）";case NOTIFY:return "启用 Aerial 通知监听（保留其他监听）";case DEFAULT:return "设为默认桌面（保存原桌面以便恢复）";case ENABLED:return "无障碍总开关";default:return k;}}
  public static String restoreLabel(String key) {
    switch(key){case PERMISSION:return "撤回本 App 新授予的安全设置权限";case ACCESS:return "撤回本 App 添加的 Aerial 无障碍守护";case NOTIFY:return "撤回本 App 添加的 Aerial 通知监听";case ENABLED:return "恢复无障碍总开关（保留其他服务）";case DEFAULT:return "恢复原默认桌面（保留后来手动更改）";default:return key;}
  }
  private static String current(Device d,String key)throws Exception {
    if(key.equals(PERMISSION)){String path=packagePath(d,PKG);if(path.isEmpty())return "false";Boolean b=permission(read(d,"dumpsys package "+PKG));if(b==null)throw new IOException("权限状态未确认");return b.toString();}
    if(key.equals(DEFAULT)){String c=component(read(d,"cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.DEFAULT"));if(c==null)throw new IOException("默认桌面状态未确认");return c;}
    if(!Arrays.asList(ACCESS,ENABLED,NOTIFY).contains(key))throw new IOException("未知备份项目");
    String v=read(d,"settings --user 0 get secure "+key);if(v.contains("Exception")||v.startsWith("Error")||v.length()>32768)throw new IOException("设置状态未确认");settingValue(key,nullable(v));return nullable(v);
  }
  private static Set<String> parts(String v){Set<String> s=new HashSet<>();if(v!=null)for(String x:v.split(":"))if(!x.isEmpty())s.add(canonical(x));return s;}
  private static boolean same(String key,String a,String b){return key.equals(ACCESS)||key.equals(NOTIFY)?parts(a).equals(parts(b)):Objects.equals(a,b);}
  private static String saved(JSONObject o,String key){return o.isNull(key)?null:o.optString(key);}
  private static void write(Device d,String key,String value)throws Exception {
    if(key.equals(PERMISSION)){if(!Arrays.asList("true","false").contains(value))throw new IOException("权限备份值无效");d.shell("pm "+(value.equals("true")?"grant":"revoke")+" "+PKG+" android.permission.WRITE_SECURE_SETTINGS");}
    else if(key.equals(DEFAULT)){if(value==null||!value.matches("[A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+"))throw new IOException("默认桌面备份无效");d.shell("cmd package set-home-activity --user 0 "+AdbClient.quote(value));}
    else {if(!Arrays.asList(ACCESS,ENABLED,NOTIFY).contains(key))throw new IOException("未知设置项目");d.shell(value==null?"settings --user 0 delete secure "+key:"settings --user 0 put secure "+key+" "+AdbClient.quote(value));}
  }
  private static void step(Device d,Store store,JSONObject journal,String id,String key,String before,String after)throws Exception {
    if(same(key,before,after))return;
    JSONArray changes=journal.getJSONArray("changes");if(changes.length()>=64)throw new IOException("备份已有 64 项变更，请先恢复已有记录。");
    JSONObject entry=new JSONObject().put("key",key).put("before",before==null?JSONObject.NULL:before).put("after",after==null?JSONObject.NULL:after).put("state","prepared");changes.put(entry);
    if(journal.toString().getBytes("UTF-8").length>768*1024)throw new IOException("备份空间不足，请先恢复已有记录。");
    store.save(id,journal);
    String live=current(d,key);
    if(!same(key,live,before)) {
      // We have not written this item: retain a skipped record, not ownership of
      // a value enabled by Aerial itself or another actor during the workflow.
      entry.put("state","skipped");store.save(id,journal);
      if(same(key,live,after))return;
      throw new SettingChanged(key,live);
    }
    entry.put("state","unknown");store.save(id,journal);
    write(d,key,after);
    String verified=current(d,key);
    if(!same(key,verified,after)) {
      boolean additive=key.equals(ACCESS)||key.equals(NOTIFY);
      if(!additive||!parts(verified).containsAll(parts(after)))throw new IOException("系统没有确认“"+label(key)+"”生效。已保留备份，请复查或恢复。");
    }
    entry.put("state","applied");store.save(id,journal);
  }
  private static final class SettingChanged extends IOException {
    final String key,live;
    SettingChanged(String key,String live){super("设置在执行前发生变化："+label(key));this.key=key;this.live=live;}
  }
  private static void ensure(Device d,Store store,JSONObject journal,String id,String key)throws Exception {
    for(int attempt=0;attempt<3;attempt++) {
      String live=current(d,key),desired;
      if(key.equals(ACCESS))desired=add(live,GUARD);
      else if(key.equals(NOTIFY))desired=add(live,LISTENER);
      else desired=key.equals(ENABLED)?"1":"true";
      if(same(key,live,desired))return;
      try{step(d,store,journal,id,key,live,desired);return;}
      catch(SettingChanged changed){
        if(!key.equals(ACCESS)&&!key.equals(NOTIFY))throw new IOException("“"+label(key)+"”发生新的改动，未覆盖该改动。已完成的项目已保留，请重新检查。",changed);
        // Rebase a service append on the latest list, preserving newly added services.
      }
    }
    throw new IOException("“"+label(key)+"”持续变化，已停止本项以保留其他服务。已完成的项目不会重复执行，请稍后重新检查。");
  }
  private static void identityCheck(Snapshot expected,Snapshot actual)throws IOException {if(!expected.id.equals(actual.id))throw new IOException("设备身份已变化，未修改设置。");}
  public static Snapshot repair(Device d,Store store,String endpoint,Snapshot reviewed,List<String> selected)throws Exception {
    Snapshot now=inspect(d,endpoint);identityCheck(reviewed,now);supportedUser(now);
    if(!now.installed||now.code!=reviewed.code)throw new IOException("Aerial 安装版本已变化，请重新检查。");
    List<String> reviewedOptions=candidates(reviewed);
    for(String key:selected) {
      if(!reviewedOptions.contains(key))throw new IOException("所选修复项未在检查结果中，请重新检查。");
      if(key.equals(DEFAULT)&&(!now.known.contains(DEFAULT)||(!same(DEFAULT,now.values.get(DEFAULT),HOME)&&!same(DEFAULT,now.values.get(DEFAULT),reviewed.values.get(DEFAULT)))))throw new IOException("默认桌面已由其他操作改变，本次未覆盖，请重新检查。");
      if(key.equals(ACCESS)&&(!now.guardDeclared||!now.known.contains(ACCESS)||!now.known.contains(ENABLED)))throw new IOException("无法确认当前守护配置，未替换服务列表。");
      if(key.equals(NOTIFY)&&(!now.listenerDeclared||!now.known.contains(NOTIFY)))throw new IOException("无法确认当前通知监听，未替换监听列表。");
      if(key.equals(PERMISSION)&&!now.known.contains(PERMISSION))throw new IOException("当前权限状态未确认，未重复授权。");
    }
    JSONObject j=store.load(now.id);
    if(j!=null&&!now.id.equals(j.optString("id")))throw new IOException("备份设备身份不匹配");
    if(!hasBackup(j))j=new JSONObject().put("id",now.id).put("created",System.currentTimeMillis()).put("changes",new JSONArray()).put("restored",false);
    if(hasUncertain(j))throw new UncertainBackup();
    List<String> completed=new ArrayList<>();
    for(String key:new LinkedHashSet<>(selected)) {
      if(key.equals(ACCESS)) {
        ensure(d,store,j,now.id,ACCESS);
        ensure(d,store,j,now.id,ENABLED);
      }else if(key.equals(DEFAULT)) {
        String live=current(d,DEFAULT);
        if(same(DEFAULT,live,HOME))continue;
        if(!same(DEFAULT,live,reviewed.values.get(DEFAULT)))throw new IOException("默认桌面已改变，未覆盖新的选择。请重新检查。");
        step(d,store,j,now.id,DEFAULT,live,HOME);
      }else ensure(d,store,j,now.id,key);
      Snapshot checked=inspect(d,endpoint);identityCheck(now,checked);supportedUser(checked);newCrash(now,checked);
      if(checked.code!=now.code)throw new VerificationFailure(checked,"结果未确认","Aerial 版本已变化，已停止后续修改");
      completed.add(key);goals(checked,completed);
    }
    return verifyOperation(d,endpoint,now,selected,selected.contains(ACCESS));
  }
  public static Snapshot restore(Device d,Store store,String endpoint,Snapshot reviewed)throws Exception {
    Snapshot now=inspect(d,endpoint);identityCheck(reviewed,now);supportedUser(now);JSONObject j=store.load(now.id);
    if(!hasBackup(j))throw new IOException("当前设备没有本 App 可恢复的变更。");
    if(!now.id.equals(j.optString("id")))throw new IOException("备份设备身份不匹配");
    if(hasUncertain(j))throw new UncertainBackup();
    JSONArray changes=j.getJSONArray("changes");
    if(now.installed)d.shell("am force-stop "+PKG);
    // Permissions first, to prevent the app from immediately re-adding removed services.
    for(String group:new String[]{PERMISSION,DEFAULT,NOTIFY,ACCESS,ENABLED})for(int i=changes.length()-1;i>=0;i--) {
      JSONObject e=changes.getJSONObject(i);String key=e.getString("key");if(!key.equals(group))continue;
      if(Arrays.asList("restored","preserved","skipped","prepared").contains(e.optString("state")))continue;
      String before=saved(e,"before"),after=saved(e,"after"),live=current(d,key),desired=live;
      if(key.equals(ACCESS)||key.equals(NOTIFY)) {
        String token=key.equals(ACCESS)?GUARD:LISTENER;
        if(!contains(before,token)&&contains(after,token)){desired=remove(live,token);if(desired!=null&&desired.isEmpty())desired=before==null?null:"";}
      }else if(key.equals(ENABLED)&&!parts(current(d,ACCESS)).isEmpty()){e.put("state","preserved");store.save(now.id,j);continue;}
      else if(same(key,live,after))desired=before;
      // A later user change wins. Never replay an old full service list or overwrite another HOME.
      if(!same(key,live,desired)){write(d,key,desired);if(!same(key,current(d,key),desired))throw new IOException("恢复后状态未确认："+label(key)+"。备份仍保留，可稍后继续恢复。");}
      e.put("state","restored");store.save(now.id,j);
    }
    if(now.installed)d.shell("am force-stop "+PKG);
    j.put("restored",true).put("restoredAt",System.currentTimeMillis());store.save(now.id,j);
    return inspect(d,endpoint);
  }
  public static boolean hasUncertain(JSONObject j) {
    if(j==null||j.optBoolean("restored"))return false;JSONArray a=j.optJSONArray("changes");if(a==null)return false;
    for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e!=null&&!Arrays.asList("applied","prepared","restored","preserved","skipped").contains(e.optString("state")))return true;}return false;
  }
  /** Explicit local-only disposition: keep TV settings, never infer ownership of uncertain writes. */
  public static Snapshot preserveUncertain(Device d,Store store,String endpoint,Snapshot reviewed)throws Exception {
    Snapshot now=inspect(d,endpoint);identityCheck(reviewed,now);supportedUser(now);JSONObject j=store.load(now.id);
    if(j==null||!now.id.equals(j.optString("id")))throw new IOException("备份身份未确认");
    JSONArray a=j.getJSONArray("changes");for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);if(!Arrays.asList("applied","prepared","restored","preserved","skipped").contains(e.optString("state")))e.put("state","preserved").put("reason","user_kept_current");}
    store.save(now.id,j);now.outcome="未确认记录已归档";now.outcomeDetail="按你的选择保留电视当前设置；本步骤没有向电视发送修改命令。其他已确认备份仍可单独恢复。";return now;
  }
  public static boolean hasBackup(JSONObject j){
    if(j==null||j.optBoolean("restored"))return false;JSONArray changes=j.optJSONArray("changes");if(changes==null)return false;
    for(int i=0;i<changes.length();i++){JSONObject e=changes.optJSONObject(i);if(e!=null&&!Arrays.asList("restored","preserved","skipped","prepared").contains(e.optString("state")))return true;}
    return false;
  }
  public static String summary(Snapshot s) {
    StringBuilder b=new StringBuilder();b.append("设备：").append(s.model).append(" · Android ").append(s.release).append("\n");
    b.append("Android 用户：").append(s.userId<0?"未确认":s.userId).append(s.userId==0?"":"（本版不修改，需确认用户 0）").append("\n");
    b.append("Aerial：").append(s.installed?s.version+"（"+s.code+"）":s.lite?"已安装 Lite，未安装完整版":"未安装").append("\n");
    b.append("默认桌面：").append(s.known.contains(DEFAULT)?s.values.get(DEFAULT):"未确认 / 系统不支持查询").append("\n");
    b.append("安全设置权限：").append(s.known.contains(PERMISSION)?"true".equals(s.values.get(PERMISSION))?"已授予":"未授予":"未确认").append("\n");
    b.append("守护配置：").append(s.known.contains(ACCESS)&&s.known.contains(ENABLED)?contains(s.values.get(ACCESS),GUARD)&&"1".equals(s.values.get(ENABLED))?"已开启":"未完整开启":"未确认").append("\n");
    b.append("守护运行：").append(s.bound==null?"未确认（系统未提供可识别的组件信息）":s.bound?"检测到已绑定":"尚未检测到绑定").append("\n");
    b.append("通知监听配置：").append(s.known.contains(NOTIFY)?contains(s.values.get(NOTIFY),LISTENER)?"已开启":"未开启":"未确认").append("\n");
    b.append("当前前台：").append(s.focus==null?"未确认":s.focus).append("\n\n");
    if(s.outcome!=null)b.append("本次结果：").append(s.outcome).append("\n").append(s.outcomeDetail).append("\n");
    b.append("崩溃检查：").append(s.crashNote).append("\n");
    if(!s.installed)b.append("请先安装对应版本。Lite 没有完整版桌面守护。\n");
    if(s.sdk<24)b.append("旧系统默认桌面命令支持有限，本版不自动修改；可在电视设置中选择。\n");
    if(s.xiaomi)b.append("检测到小米桌面，跳过默认桌面修改，通过守护方式处理。\n");
    else if(s.unread.contains("xiaomi"))b.append("厂商桌面兼容性未确认，已跳过自动默认桌面修改。\n");
    b.append("配置开启不等于服务运行，当前前台也不能代替主页键验收。开机接管需实际重启电视后验证，本助手不会自动重启电视。");return b.toString();
  }
  public static String report(Snapshot s,String manual) {
    String time=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.CHINA).format(new Date(s.time));
    return "轻连 ADB "+AppVersion.NAME+" · Aerial 桌面诊断\n检查时间："+time+"\n\n"+summary(s)+"\n\n绑定识别依据："+(s.bindingEvidence==null?"未提供":s.bindingEvidence)+"\n\n主页键人工验收：\n"+(manual==null?"尚未进行；请按电视实体遥控器主页键后读取结果。":manual)+"\n\n报告为脱敏状态摘要：不包含 IP、序列号、Wi-Fi 名称、账号、应用列表及其他服务名称。备份仅保存在手机本地，未附入报告。\n";
  }
}
