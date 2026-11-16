package cn.linkdeck.adb;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Requests Android's cache trim and reports the observed /data free-space change. */
public final class CacheCleaner {
  public static final String SPACE_COMMAND="df -k /data";
  // A high desired free-space target asks PackageManager to reclaim available cache.
  public static final String TRIM_COMMAND="pm trim-caches 4096G";
  public interface Device { Reply shell(String command) throws Exception; }
  public interface BeforeTrim { void show(long bytes) throws Exception; }
  public static final class Reply {
    public final int code; public final String raw;
    public Reply(int code,String raw){this.code=code;this.raw=raw==null?"":raw;}
  }
  public static final class Outcome {
    public long before,after; public boolean afterKnown,failed,approximate; public String raw="",problem="";
    public String report(){
      StringBuilder s=new StringBuilder("清理前可用空间：").append(bytes(before));
      if(afterKnown){
        s.append("\n清理后可用空间：").append(bytes(after));
        long delta=after-before;
        if(delta>0)s.append("\n可用空间净增加：").append(bytes(delta));
        else if(delta==0)s.append("\n可用空间没有增加。");
        else s.append("\n可用空间净减少：").append(bytes(-delta));
      }else s.append("\n清理后空间未能确认。");
      if(approximate)s.append("\n电视只提供经过舍入的空间读数，以上数值及其变化为约数。\n");
      if(failed)s.append("\n\n清理未完成或结果未确认：").append(problem);
      else if(afterKnown&&after>before)s.append("\n\n已请求系统清理缓存。此数值是可用空间变化，期间其他活动也会影响结果。");
      else s.append("\n\n没有观察到空间增加；电视可能没有可回收缓存，或固件限制了清理。");
      s.append("\n\n电视返回原文：\n").append(raw.isEmpty()?"（无输出）":raw);
      return s.toString();
    }
  }
  private static final Pattern ROW=Pattern.compile("(?m)^.*?\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+\\d+%\\s+/data\\s*$");
  private static final Pattern LEGACY=Pattern.compile("(?m)^\\s*/data\\s+([0-9.]+[KMGTP])\\s+([0-9.]+[KMGTP])\\s+([0-9.]+)([KMGTP])\\s+\\d+\\s*$",Pattern.CASE_INSENSITIVE);
  private static final class Space {long bytes;boolean approximate;Space(long n,boolean a){bytes=n;approximate=a;}}
  private static Space parseSpace(String raw)throws IOException{
    String output=raw==null?"":raw;Matcher m=ROW.matcher(output);
    try{
      if(m.find())return new Space(Math.multiplyExact(Long.parseLong(m.group(3)),1024L),false);
      Matcher old=LEGACY.matcher(output);
      if(old.find()){
        int power="KMGTP".indexOf(old.group(4).toUpperCase(Locale.ROOT))+1;
        java.math.BigDecimal multiplier=java.math.BigDecimal.valueOf(1024L).pow(power);
        return new Space(new java.math.BigDecimal(old.group(3)).multiply(multiplier).setScale(0,java.math.RoundingMode.DOWN).longValueExact(),true);
      }
    }catch(ArithmeticException|NumberFormatException e){throw new IOException("电视可用空间数值无效",e);}
    throw new IOException("无法识别电视 /data 可用空间。电视返回：\n"+output);
  }
  public static long parseFree(String raw)throws IOException{return parseSpace(raw).bytes;}
  private static Space readSpace(Device device)throws Exception{
    Reply first=device.shell(SPACE_COMMAND);
    if(first.code==0){try{return parseSpace(first.raw);}catch(IOException ignored){}}
    Reply legacy=device.shell("df /data");
    if(legacy.code!=0)throw new IOException("读取电视空间失败：\n"+(legacy.raw.isEmpty()?"（无输出）":legacy.raw));
    return parseSpace(legacy.raw);
  }
  public static Outcome execute(Device device,BeforeTrim callback)throws Exception{
    Outcome result=new Outcome();Space before=readSpace(device);result.before=before.bytes;result.approximate=before.approximate;callback.show(result.before);
    try{
      Reply r=device.shell(TRIM_COMMAND);result.raw=r.raw;
      String lower=r.raw.toLowerCase(Locale.ROOT);
      if(r.code!=0||lower.contains("error")||lower.contains("exception")||lower.contains("failed")||lower.contains("failure")||lower.contains("permission denied")){
        result.failed=true;result.problem="电视拒绝或未能完成缓存清理（退出码 "+r.code+"）。";
      }
    }catch(Exception e){result.failed=true;result.problem="命令中断或连接失败，状态未确认。";result.raw=e.getMessage()==null?e.toString():e.getMessage();}
    try{Space after=readSpace(device);result.after=after.bytes;result.approximate|=after.approximate;result.afterKnown=true;}
    catch(Exception e){result.failed=true;result.problem+=(result.problem.isEmpty()?"":"\n")+"无法复查电视可用空间："+(e.getMessage()==null?e.toString():e.getMessage());}
    return result;
  }
  private static String bytes(long n){
    if(n<1024)return n+" B";String[] units={"KiB","MiB","GiB","TiB"};double v=n;int i=-1;while(v>=1024&&i<units.length-1){v/=1024;i++;}return String.format(Locale.ROOT,"%.2f %s",v,units[i]);
  }
  private CacheCleaner(){}
}
