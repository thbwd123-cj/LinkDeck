package cn.linkdeck.adb;
import java.util.*;
public class VoiceCommandChecks {
 static int n;static void ok(boolean b,String message){if(!b)throw new AssertionError(message);n++;System.out.println("PASS: "+message);}
 static VoiceCommand.Result parse(String... c){return VoiceCommand.parse(Arrays.asList(c));}
 public static void main(String[] args){
  ok(parse("返回").kind==VoiceCommand.Kind.KEY&&parse("返回").keyCode==4,"back is key 4");
  ok(parse("主页").keyCode==3,"home is key 3");
  ok(parse("菜单").keyCode==82,"menu is key 82");
  ok(parse("确定").keyCode==23,"confirm is key 23");
  ok(parse("打开").keyCode==23,"bare open is confirm, not a launch");
  ok(parse("上").keyCode==19&&parse("下").keyCode==20&&parse("左").keyCode==21&&parse("右").keyCode==22,"direction keys");
  ok(parse("大点声").keyCode==24&&parse("小点声").keyCode==25,"volume keys");
  ok(parse("把声音调大一点").keyCode==24,"volume up by contains rule");
  ok(parse("把声音调小一点").keyCode==25,"volume down by contains rule");
  ok(parse("静音").keyCode==164,"mute is key 164");
  ok(parse("播放").keyCode==126&&parse("暂停").keyCode==127,"play and pause");
  ok(parse("快进").keyCode==90&&parse("快退").keyCode==89,"fast forward and rewind");
  ok(parse("下一集").keyCode==87&&parse("上一集").keyCode==88,"next and previous episode");
  ok(parse("关电视").keyCode==26,"power is key 26");
  ok(parse("打开电视").keyCode==26,"open tv is power, not a launch");
  VoiceCommand.Result l=parse("打开Jellyfin");
  ok(l.kind==VoiceCommand.Kind.LAUNCH&&"Jellyfin".equals(l.query),"open app strips the prefix");
  ok(parse("打开哔哩哔哩").query.equals("哔哩哔哩"),"launch query keeps the app name");
  ok(parse("启动当贝市场").kind==VoiceCommand.Kind.LAUNCH,"start prefix launches");
  VoiceCommand.Result s=parse("搜索三体");
  ok(s.kind==VoiceCommand.Kind.SEARCH&&"三体".equals(s.query),"search strips the prefix");
  ok(parse("搜甄嬛传").query.equals("甄嬛传"),"short search prefix works");
  ok(parse("找猫和老鼠").query.equals("猫和老鼠"),"find prefix works");
  ok(parse("搜索").kind==VoiceCommand.Kind.SEARCH&&parse("搜索").query.isEmpty(),"bare search is an empty query");
  VoiceCommand.Result f=parse("三体");
  ok(f.kind==VoiceCommand.Kind.SEARCH&&"三体".equals(f.query),"plain text falls back to search");
  ok(parse("哈利波特").query.equals("哈利波特"),"search keeps the raw text");
  VoiceCommand.Result a=parse("三体三体","返回");
  ok(a.kind==VoiceCommand.Kind.KEY&&a.keyCode==4,"later candidate can win when the first is only search text");
  VoiceCommand.Result b=parse("打开Jellyfin","返回");
  ok(b.kind==VoiceCommand.Kind.LAUNCH,"launch beats a later key candidate");
  ok(parse("返回。").keyCode==4,"trailing punctuation is stripped");
  ok(parse("  大 点 声 ").keyCode==24,"spaces are stripped before matching");
  ok(parse("返回，").keyCode==4,"full-width punctuation is stripped");
  ok(parse("").kind==VoiceCommand.Kind.NONE,"empty candidate is none");
  ok(VoiceCommand.parse(null).kind==VoiceCommand.Kind.NONE,"null candidates is none");
  ok(VoiceCommand.parse(new ArrayList<String>()).kind==VoiceCommand.Kind.NONE,"empty list is none");
  ok(VoiceCommand.LANGUAGE.equals("zh-CN"),"recognition language is zh-CN");
  ok(VoiceCommand.MAX_RESULTS==3,"three recognition candidates are requested");
  ok(VoiceCommand.normalize("三 体。").equals("三体"),"normalize drops spaces and punctuation");
  System.out.println("VOICE COMMAND PASSED: "+n+" checks.");
 }
}
