package cn.linkdeck.adb;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Kodi stable Android APK directory. Signature comparison is mandatory at install. */
public final class OfficialKodi {
  private static final Pattern APK=Pattern.compile("kodi-([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)-([A-Za-z]+)-(armeabi-v7a|arm64-v8a)\\.apk");
  private static final long MAX=150L*1024*1024;
  public static final class Asset {
    public final String name,version,abi,url;
    Asset(String name,String version,String abi,String url){this.name=name;this.version=version;this.abi=abi;this.url=url;}
  }
  private OfficialKodi(){}
  private static int compare(String a,String b){String[] x=a.split("\\."),y=b.split("\\.");for(int i=0;i<Math.max(x.length,y.length);i++){String left=i<x.length?x[i]:"0",right=i<y.length?y[i]:"0";left=left.replaceFirst("^0+(?!$)","");right=right.replaceFirst("^0+(?!$)","");if(left.length()!=right.length())return left.length()<right.length()?-1:1;int c=left.compareTo(right);if(c!=0)return c<0?-1:1;}return 0;}
  public static Asset parse(String html,String abi)throws IOException {
    if(html==null||html.length()>1024*1024||!abi.matches("armeabi-v7a|arm64-v8a"))throw new IOException("Kodi 目录数据无效");
    String folder=abi.equals("armeabi-v7a")?"arm":"arm64-v8a";
    Matcher m=APK.matcher(html);Asset best=null;
    while(m.find()){
      if(!m.group(3).equals(abi))continue;
      String name=m.group(),version=m.group(1);
      if(best==null||compare(version,best.version)>0)best=new Asset(name,version,abi,"https://mirrors.kodi.tv/releases/android/"+folder+"/"+name+"?https=1");
    }
    if(best==null)throw new IOException("Kodi 官方目录无当前架构的稳定 APK");
    return best;
  }
  private static HttpURLConnection open(String address,boolean mirror)throws IOException{
    URL url=new URL(address);
    for(int i=0;i<6;i++){
      if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443)||!mirror&&!url.getHost().equalsIgnoreCase("mirrors.kodi.tv"))throw new IOException("Kodi 资源跳转到非 HTTPS 或不可信地址");
      HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setUseCaches(false);c.setRequestProperty("User-Agent","LinkDeck-OfficialSource/"+AppVersion.NAME);
      int status=c.getResponseCode();if(status>=300&&status<400){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new IOException("Kodi 资源跳转缺少地址");url=new URL(url,next);continue;}
      if(status!=200){c.disconnect();throw new IOException("Kodi 官方目录返回 HTTP "+status);}
      return c;
    }
    throw new IOException("Kodi 资源跳转次数过多");
  }
  public static Asset latest(String abi)throws Exception{
    if(!abi.matches("armeabi-v7a|arm64-v8a"))throw new IOException("Kodi 不支持该电视 CPU 架构");
    String folder=abi.equals("armeabi-v7a")?"arm":"arm64-v8a";
    HttpURLConnection c=open("https://mirrors.kodi.tv/releases/android/"+folder+"/",false);
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>1024*1024)throw new IOException("Kodi 目录过大");}
      return parse(out.toString("UTF-8"),abi);
    }finally{c.disconnect();}
  }
  public static void download(Asset asset,File file)throws Exception{
    if(asset==null||!APK.matcher(asset.name).matches()||!asset.url.startsWith("https://mirrors.kodi.tv/releases/android/"))throw new IOException("Kodi 下载地址无效");
    // Mirrorbits serves a file's SHA-256 via ?sha256. Only the official host may supply this metadata.
    String folder=asset.abi.equals("armeabi-v7a")?"arm":asset.abi.equals("arm64-v8a")?"arm64-v8a":null;
    if(folder==null||!asset.url.equals("https://mirrors.kodi.tv/releases/android/"+folder+"/"+asset.name+"?https=1"))throw new IOException("Kodi 资源路径无效");
    String expected;
    HttpURLConnection checksum=open("https://mirrors.kodi.tv/releases/android/"+folder+"/"+asset.name+"?sha256",false);
    try(InputStream in=checksum.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] b=new byte[512];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>512)throw new IOException("Kodi 校验信息过大");}
      String raw=out.toString("UTF-8").trim();
      Matcher digest=Pattern.compile("(?i)^([0-9a-f]{64})(?:\\s+\\*?"+Pattern.quote(asset.name)+")?$").matcher(raw);
      if(!digest.matches())throw new IOException("Kodi 官方站点未提供有效 SHA-256，已停止自动安装");
      expected=digest.group(1);
    }finally{checksum.disconnect();}
    HttpURLConnection c=null;
    try{
      c=open(asset.url,true);long declared=c.getContentLengthLong();if(declared>MAX)throw new IOException("Kodi APK 超出大小限制");
      long count=0,deadline=System.nanoTime()+300L*1000000000L;
      try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)){
        byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("下载已停止");count+=n;if(count>MAX||System.nanoTime()>deadline)throw new IOException("下载超过大小或时间限制");out.write(b,0,n);}
      }
      if(count<4||declared>=0&&count!=declared||!expected.equalsIgnoreCase(AerialDownload.sha256(file)))throw new IOException("Kodi 官方 SHA-256 校验失败或文件不完整");
      try(InputStream in=new FileInputStream(file)){if(in.read()!=80||in.read()!=75)throw new IOException("下载文件不是 APK");}
    }catch(Exception e){file.delete();throw e;}finally{if(c!=null)c.disconnect();}
  }
}
