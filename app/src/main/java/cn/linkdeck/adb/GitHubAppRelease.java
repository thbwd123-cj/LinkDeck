package cn.linkdeck.adb;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import org.json.*;

/** Explicit repository binding; a GitHub tag never establishes APK package identity. */
public final class GitHubAppRelease {
  private static final long MAX=100L*1024*1024;
  public static final class Asset {
    public final String name,url,digest;public final long size;
    Asset(String name,String url,String digest,long size){this.name=name;this.url=url;this.digest=digest;this.size=size;}
  }
  public static final class Release {
    public final String repo,tag;public final List<Asset> apks;
    Release(String repo,String tag,List<Asset> apks){this.repo=repo;this.tag=tag;this.apks=Collections.unmodifiableList(apks);}
  }
  private GitHubAppRelease(){}
  public static String repository(String text)throws IOException {
    String v=text==null?"":text.trim();
    if(v.startsWith("https://github.com/"))v=v.substring("https://github.com/".length());
    if(v.endsWith("/"))v=v.substring(0,v.length()-1);
    if(v.length()>120||!v.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,38}/[A-Za-z0-9][A-Za-z0-9_.-]{0,99}")||v.contains(".."))throw new IOException("请输入 GitHub 仓库 owner/repo，例如作者名/项目名");
    return v;
  }
  public static Release parse(String repo,String json)throws Exception {
    repo=repository(repo);
    JSONObject root=new JSONObject(json);
    if(root.optBoolean("draft")||root.optBoolean("prerelease"))throw new IOException("当前发布不是稳定版");
    String tag=root.optString("tag_name","");
    if(!tag.matches("[A-Za-z0-9._+-]{1,90}"))throw new IOException("发布标签异常");
    JSONArray array=root.getJSONArray("assets");if(array.length()>100)throw new IOException("发布文件过多");
    List<Asset> apks=new ArrayList<>();
    for(int i=0;i<array.length();i++){
      JSONObject row=array.getJSONObject(i);String name=row.optString("name","");
      if(!name.toLowerCase(Locale.ROOT).endsWith(".apk"))continue;
      if(name.length()>160||!name.matches("[A-Za-z0-9_+(). -]+\\.[aA][pP][kK]"))continue;
      String address=row.optString("browser_download_url","");
      URL url=new URL(address);
      if(!url.getProtocol().equals("https")||!url.getHost().equalsIgnoreCase("github.com")||url.getUserInfo()!=null||url.getPort()!=-1
        ||!url.getPath().startsWith("/"+repo+"/releases/download/"+tag+"/")||!url.getPath().endsWith("/"+name)||url.getQuery()!=null||url.getRef()!=null)throw new IOException("APK 下载地址与指定仓库不一致");
      long size=row.optLong("size",-1);if(size<=0||size>MAX)continue;
      String digest=row.optString("digest","");
      if(!digest.isEmpty()&&!digest.equals("null")&&!digest.matches("sha256:[0-9a-fA-F]{64}"))throw new IOException("发布文件摘要格式错误");
      apks.add(new Asset(name,address,digest.matches("sha256:[0-9a-fA-F]{64}")?digest.substring(7).toLowerCase(Locale.ROOT):"",size));
    }
    return new Release(repo,tag,apks);
  }
  private static boolean allowed(URL url,boolean api)throws IOException{
    if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443))return false;
    String host=url.getHost().toLowerCase(Locale.ROOT);
    return api?host.equals("api.github.com"):
      host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com");
  }
  private static HttpURLConnection open(String address,boolean api)throws IOException{
    URL url=new URL(address);
    for(int hop=0;hop<6;hop++){
      if(!allowed(url,api))throw new IOException("GitHub 下载跳转到非允许地址，已停止");
      HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);
      c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setUseCaches(false);
      c.setRequestProperty("User-Agent","LinkDeck-AppUpdater/"+AppVersion.NAME+"");c.setRequestProperty("Accept",api?"application/vnd.github+json":"application/octet-stream");
      int status=c.getResponseCode();
      if(status>=300&&status<400){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new IOException("下载跳转缺少地址");url=new URL(url,next);continue;}
      if(status!=200){c.disconnect();throw new IOException("GitHub 返回 HTTP "+status+"；检查仓库地址、网络或访问频率");}
      return c;
    }
    throw new IOException("GitHub 下载跳转次数过多");
  }
  public static Release latest(String repo)throws Exception{
    repo=repository(repo);
    HttpURLConnection c=open("https://api.github.com/repos/"+repo+"/releases/latest",true);
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>2*1024*1024)throw new IOException("发布信息过大");}
      return parse(repo,out.toString("UTF-8"));
    }finally{c.disconnect();}
  }
  public static void download(Asset asset,File file)throws Exception{
    if(asset==null||asset.size<=0||asset.size>MAX)throw new IOException("安装包大小无效");
    HttpURLConnection c=null;
    try{
      c=open(asset.url,false);long declared=c.getContentLengthLong();
      if(declared>MAX||declared>=0&&declared!=asset.size)throw new IOException("下载大小与发布记录不一致");
      long count=0,deadline=System.nanoTime()+300L*1000000000L;
      try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)){
        byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){
          if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("下载已停止");
          count+=n;if(count>MAX||System.nanoTime()>deadline)throw new IOException("下载超过大小或时间限制");out.write(b,0,n);
        }
      }
      if(count!=asset.size)throw new IOException("下载文件不完整");
      try(InputStream in=new FileInputStream(file)){if(in.read()!=80||in.read()!=75)throw new IOException("下载文件不是 APK");}
      if(!asset.digest.isEmpty()&&!asset.digest.equals(AerialDownload.sha256(file)))throw new IOException("GitHub 发布摘要与下载文件不一致");
    }catch(Exception e){file.delete();throw e;}finally{if(c!=null)c.disconnect();}
  }
}
