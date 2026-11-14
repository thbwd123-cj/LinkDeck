package cn.linkdeck.adb;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** VideoLAN's stable APK directory and official per-file SHA-256 checksums. */
public final class OfficialVlc {
  public static final String BASE="https://download.videolan.org/pub/videolan/vlc-android/last/";
  private static final Pattern APK=Pattern.compile("VLC-Android-([0-9]+(?:\\.[0-9]+){1,3})-(arm64-v8a|armeabi-v7a|x86_64|x86)\\.apk");
  private static final long MAX=100L*1024*1024;
  public static final class Asset {
    public final String name,version,abi;
    Asset(String name,String version,String abi){this.name=name;this.version=version;this.abi=abi;}
  }
  private OfficialVlc(){}
  private static int compareVersion(String left,String right){
    String[] a=left.split("\\."),b=right.split("\\.");
    for(int i=0;i<Math.max(a.length,b.length);i++){
      String x=i<a.length?a[i]:"0",y=i<b.length?b[i]:"0";
      x=x.replaceFirst("^0+(?!$)","");y=y.replaceFirst("^0+(?!$)","");
      if(x.length()!=y.length())return x.length()<y.length()?-1:1;
      int c=x.compareTo(y);if(c!=0)return c<0?-1:1;
    }
    return 0;
  }
  public static List<Asset> parse(String html)throws IOException {
    if(html==null||html.length()>512*1024)throw new IOException("VideoLAN 目录信息过大");
    Matcher matcher=APK.matcher(html);Map<String,Asset> found=new LinkedHashMap<>();String newest=null;
    while(matcher.find()){
      String version=matcher.group(1),abi=matcher.group(2),name=matcher.group();
      if(newest==null||compareVersion(version,newest)>0)newest=version;
      found.put(name,new Asset(name,version,abi));
    }
    if(newest==null)throw new IOException("VideoLAN 官方目录未找到稳定 APK");
    List<Asset> result=new ArrayList<>();
    for(Asset a:found.values())if(a.version.equals(newest))result.add(a);
    return result;
  }
  private static HttpURLConnection open(String address,boolean mirror)throws IOException {
    URL url=new URL(address);
    for(int hop=0;hop<6;hop++){
      String host=url.getHost().toLowerCase(Locale.ROOT);
      if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443)
        ||!mirror&&!(host.equals("download.videolan.org")||host.equals("get.videolan.org")))throw new IOException("VideoLAN 资源跳转到不可信地址");
      HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setUseCaches(false);c.setRequestProperty("User-Agent","LinkDeck-OfficialSource/"+AppVersion.NAME);
      int status=c.getResponseCode();
      if(status>=300&&status<400){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new IOException("资源跳转缺少地址");url=new URL(url,next);continue;}
      if(status!=200){c.disconnect();throw new IOException("VideoLAN 返回 HTTP "+status);}
      return c;
    }
    throw new IOException("VideoLAN 资源跳转次数过多");
  }
  private static String read(String address,int max)throws Exception {
    HttpURLConnection c=open(address,false);
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>max)throw new IOException("VideoLAN 元数据过大");}
      return out.toString("UTF-8");
    }finally{c.disconnect();}
  }
  public static List<Asset> latest()throws Exception{return parse(read(BASE,512*1024));}
  public static void download(Asset asset,File file)throws Exception {
    if(asset==null||!APK.matcher(asset.name).matches())throw new IOException("未知 VideoLAN APK");
    String checksum=read(BASE+asset.name+".sha256",512);
    Matcher hash=Pattern.compile("(?i)(?:^|\\s)([0-9a-f]{64})(?:\\s|$)").matcher(checksum);
    if(!hash.find())throw new IOException("官方校验文件无有效 SHA-256");
    HttpURLConnection c=null;
    try{
      c=open(BASE+asset.name,true);long declared=c.getContentLengthLong();if(declared>MAX)throw new IOException("APK 超出大小限制");
      long count=0,deadline=System.nanoTime()+300L*1000000000L;
      try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)){
        byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){
          if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("下载已停止");
          count+=n;if(count>MAX||System.nanoTime()>deadline)throw new IOException("下载超过大小或时间限制");out.write(b,0,n);
        }
      }
      if(count<4||declared>=0&&count!=declared||!hash.group(1).equalsIgnoreCase(AerialDownload.sha256(file)))throw new IOException("官方 SHA-256 校验失败或文件不完整");
      try(InputStream in=new FileInputStream(file)){if(in.read()!=80||in.read()!=75)throw new IOException("下载文件不是 APK");}
    }catch(Exception e){file.delete();throw e;}finally{if(c!=null)c.disconnect();}
  }
}
