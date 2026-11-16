package cn.linkdeck.adb;
import java.util.*;
import java.io.*;
public final class CacheCleanerChecks {
  static int count=0;
  static void ok(boolean b,String text){if(!b)throw new AssertionError(text);count++;System.out.println("PASS: "+text);}
  static String df(long available){return "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/dm-0 100000 1000 "+available+" 10% /data\n";}
  static class Fake implements CacheCleaner.Device {
    List<String> commands=new ArrayList<>(); long before=1000,after=1500;int reads=0,code=0;String raw="";boolean brokenAfter=false,throwsTrim=false;
    public CacheCleaner.Reply shell(String c)throws Exception{
      commands.add(c);
      if(c.equals(CacheCleaner.SPACE_COMMAND)){reads++;if(reads==2&&brokenAfter)throw new IOException("df refused");return new CacheCleaner.Reply(0,df(reads==1?before:after));}
      if(c.equals(CacheCleaner.TRIM_COMMAND)){if(throwsTrim)throw new IOException("connection lost after request");return new CacheCleaner.Reply(code,raw);}
      throw new AssertionError(c);
    }
  }
  public static void main(String[] args)throws Exception{
    Fake f=new Fake();final Fake first=f;final boolean[] shown={false};CacheCleaner.Outcome a=CacheCleaner.execute(f,b->{ok(b==1024000,"before reading exact bytes");ok(first.commands.size()==1,"before value shown before trim");shown[0]=true;});
    ok(shown[0]&&a.before==1024000&&a.after==1536000&&a.afterKnown,"before and after exact bytes");
    ok(a.report().contains("500.00 KiB")&&!a.failed,"positive change reported from actual measurements");
    ok(f.commands.equals(Arrays.asList(CacheCleaner.SPACE_COMMAND,CacheCleaner.TRIM_COMMAND,CacheCleaner.SPACE_COMMAND)),"only space, cache trim and recheck commands");
    for(String c:f.commands)ok(!c.contains("pm clear")&&!c.contains("pm uninstall"),"no data clear or uninstall");
    f=new Fake();f.code=1;f.raw="Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]";a=CacheCleaner.execute(f,b->{});
    ok(a.failed&&a.report().contains(f.raw)&&!a.report().contains("清理成功"),"failure keeps TV original text without claiming success");
    f=new Fake();f.raw="Error: denied by policy";a=CacheCleaner.execute(f,b->{});ok(a.failed&&a.report().contains(f.raw),"error text even with exit zero rejected");
    f=new Fake();f.after=f.before;f.raw="";a=CacheCleaner.execute(f,b->{});ok(!a.failed&&a.report().contains("没有观察到空间增加")&&!a.report().contains("清理成功")&&a.report().contains("无输出"),"zero change honestly reported");
    f=new Fake();f.after=500;a=CacheCleaner.execute(f,b->{});ok(a.report().contains("净减少")&&!a.report().contains("清理成功"),"negative change reported");
    f=new Fake();f.brokenAfter=true;a=CacheCleaner.execute(f,b->{});ok(a.failed&&!a.afterKnown&&a.report().contains("df refused"),"missing post-check is unknown");
    f=new Fake();f.throwsTrim=true;a=CacheCleaner.execute(f,b->{});ok(a.failed&&a.afterKnown&&a.report().contains("connection lost after request"),"transport failure not mistaken for success");
    f=new Fake();final Fake ff=f;try{CacheCleaner.execute(f,b->{throw new IOException("no UI");});throw new AssertionError("callback");}catch(IOException e){ok(ff.commands.size()==1,"no trim when before display fails");}
    ok(CacheCleaner.parseFree("Filesystem 1K-blocks Used Available Use% Mounted\n/dev/block/verylong\n  12345 5432 4321 55% /data\n")==4321L*1024,"wrapped df row parsed");
    ok(CacheCleaner.parseFree("Filesystem Size Used Free Blksize\n/data 4.3G 1.8G 2.4G 4096\n")==2576980377L,"legacy rounded Android 5 storage parsed");
    List<String> legacyCommands=new ArrayList<>();final int[] n={0};CacheCleaner.Outcome legacy=CacheCleaner.execute(c->{legacyCommands.add(c);if(c.equals("df -k /data"))return new CacheCleaner.Reply(1,"unknown option");if(c.equals("df /data"))return new CacheCleaner.Reply(0,"Filesystem Size Used Free Blksize\n/data 4.3G 1.8G "+(++n[0]==1?"2.4G":"2.5G")+" 4096\n");return new CacheCleaner.Reply(0,"");},b->{});
    ok(legacy.approximate&&legacy.report().contains("约数")&&legacyCommands.size()==5,"old Android fallback and rounding disclosure");
    try{CacheCleaner.parseFree("unexpected df");throw new AssertionError("parse");}catch(IOException e){ok(true,"unknown df rejected");}
    System.out.println("CACHE CLEANER PASSED: "+count+" checks");
  }
}
