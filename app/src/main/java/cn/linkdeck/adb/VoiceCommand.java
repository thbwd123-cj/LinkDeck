package cn.linkdeck.adb;
import java.util.*;
/** Pure voice-command parser: normalized recognized text -> key / app launch / search text.
* No Android framework use, so it runs on the host test runner. */
public final class VoiceCommand {
public enum Kind { KEY, LAUNCH, SEARCH, NONE}
public static final class Result {
public final Kind kind; public final int keyCode; public final String query;
private Result(Kind k,int code,String q){kind=k;keyCode=code;query=q;}
public static Result key(int code){return new Result(Kind.KEY,code,null);}
public static Result launch(String q){return new Result(Kind.LAUNCH,0,q);}
public static Result search(String q){return new Result(Kind.SEARCH,0,q);}
public static Result none(){return new Result(Kind.NONE,0,null);}
}
public static final String LANGUAGE = "zh-CN";
public static final int MAX_RESULTS = 3;
private static final Map<String,Integer> KEYS = new HashMap<String,Integer>();
private static void put(String phrase,int code){KEYS.put(phrase,code);}
static {
put("返回",4); put("回去",4); put("后退",4); put("退出",4);
put("主页",3); put("首页",3); put("回主页",3); put("回桌面",3); put("桌面",3);
put("菜单",82);
put("确认",23); put("确定",23); put("好的",23); put("打开",23);
put("上",19); put("向上",19); put("上移",19);
put("下",20); put("向下",20); put("下移",20);
put("左",21); put("向左",21); put("左移",21);
put("右",22); put("向右",22); put("右移",22);
put("大点声",24); put("声音大一点",24); put("音量大",24); put("调大音量",24); put("大声点",24);
put("小点声",25); put("声音小一点",25); put("音量小",25); put("调小音量",25); put("小声点",25);
put("静音",164);
put("播放",126); put("继续播放",126);
put("暂停",127);
put("快进",90); put("快退",89);
put("上一首",88); put("上一集",88); put("上一个",88);
put("下一首",87); put("下一集",87); put("下一个",87);
put("关机",26); put("关电视",26); put("开机",26); put("打开电视",26);
}
private static final String[] LAUNCH_PREFIXES = {"打开","启动","进入"};
private static final String[] SEARCH_PREFIXES = {"搜索一下","搜一下","搜索","搜","找"};

/** Normalize for matching: drop whitespace (incl. full-width space) and punctuation, lowercase ASCII. */
public static String normalize(String s){
if(s==null)return "";
StringBuilder b=new StringBuilder();
for(int i=0;i<s.length();i++){
char c=s.charAt(i);
if(c==' '||c=='\u3000'||c=='\t'||c=='\n'||c=='\r')continue;
if("，。、；：？！,.!?;:'\"“”‘’（）()《》〈〉~·…—-".indexOf(c)>=0)continue;
b.append(Character.toLowerCase(c));
}
return b.toString();
}
private static String stripPrefix(String raw,String prefix){
String t=raw==null?"":raw.trim();
if(t.startsWith(prefix))return t.substring(prefix.length()).trim();
return null;
}
/** Try one candidate. Returns null when this candidate matches nothing. */
static Result parseOne(String raw){
if(raw==null)return null;
String n=normalize(raw);
if(n.isEmpty())return null;
Integer code=KEYS.get(n);
if(code!=null)return Result.key(code.intValue());
for(String p:LAUNCH_PREFIXES){
if(n.startsWith(p)&&n.length()>p.length()){
String q=stripPrefix(raw,p);
if(q!=null&&!q.isEmpty())return Result.launch(q);
}
}
for(String p:SEARCH_PREFIXES){
if(n.startsWith(p)){
if(n.length()==p.length())return Result.search("");
String q=stripPrefix(raw,p);
if(q!=null&&!q.isEmpty())return Result.search(q);
}
}
if(n.contains("大声")||n.contains("音量大")||n.contains("声音大")||n.contains("调大"))return Result.key(24);
if(n.contains("小声")||n.contains("音量小")||n.contains("声音小")||n.contains("调小"))return Result.key(25);
return null;
}
/** First candidate that yields KEY/LAUNCH/SEARCH wins; otherwise the top candidate is search text. */
public static Result parse(List<String> candidates){
if(candidates==null)return Result.none();
String top=null;
for(String c:candidates){
if(c!=null&&!c.trim().isEmpty()){top=c;break;}
}
if(top==null)return Result.none();
for(String c:candidates){
Result r=parseOne(c);
if(r!=null)return r;
}
return Result.search(top.trim());
}
private VoiceCommand(){}
}
