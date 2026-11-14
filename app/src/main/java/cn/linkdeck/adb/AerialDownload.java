package cn.linkdeck.adb;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import org.json.*;

/** Official HTTPS sources only. No downloaded scripts are executed. */
public final class AerialDownload {
  public interface Progress {void update(String stage);}
  public static final String API="https://api.github.com/repos/pengchujin/aerial-tv/releases/latest";
  private static final long MAX=100L*1024*1024;
  public static final class Asset {
    public String url, mirror, apiUrl, digest="", tag="官网当前版本";
    public long size=-1;public boolean pinned;
  }
  public static Asset metadata(String json,String variant)throws Exception {
    JSONObject release=new JSONObject(json);
    if(release.optBoolean("draft")||release.optBoolean("prerelease"))throw new IOException("不是稳定发布版本");
    JSONArray assets=release.getJSONArray("assets");String name="aerial-"+variant+".apk";
    for(int i=0;i<assets.length();i++) {
      JSONObject o=assets.getJSONObject(i);if(!name.equals(o.optString("name")))continue;
      String url=o.getString("browser_download_url");
      if(!url.startsWith("https://github.com/pengchujin/aerial-tv/releases/download/")||!url.endsWith("/"+name))throw new IOException("官方资源链接格式异常");
      Asset a=new Asset();a.url=url;a.mirror="https://aerial.qust.me/download/"+name;a.size=o.getLong("size");
      if(a.size<=0||a.size>MAX)throw new IOException("官方资源大小异常");
      String digest=o.optString("digest");
      if(!digest.isEmpty()&&!digest.equals("null")) {
        if(!digest.matches("sha256:[0-9a-fA-F]{64}"))throw new IOException("官方校验值格式异常");
        a.digest=digest.substring(7).toLowerCase(Locale.ROOT);
      }
      String api=o.optString("url");
      if(api.matches("https://api\\.github\\.com/repos/pengchujin/aerial-tv/releases/assets/[0-9]+"))a.apiUrl=api+"?download=1";
      a.tag=release.optString("tag_name","稳定发布");return a;
    }
    throw new IOException("官方稳定发布中未找到 "+name);
  }
  static boolean trusted(URL u) {
    if(!u.getProtocol().equals("https")||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))return false;
    String h=u.getHost().toLowerCase(Locale.ROOT),p=u.getPath();
    return h.equals("aerial.qust.me")&&p.startsWith("/download/")
        ||h.equals("api.github.com")&&p.startsWith("/repos/pengchujin/aerial-tv/")
        ||h.equals("github.com")&&p.startsWith("/pengchujin/aerial-tv/releases/")
        ||h.equals("release-assets.githubusercontent.com")||h.equals("objects.githubusercontent.com");
  }
  private static HttpURLConnection open(String address)throws IOException {
    URL u=new URL(address);
    for(int hop=0;hop<6;hop++) {
      if(!trusted(u))throw new IOException("下载重定向到了非官方或非 HTTPS 地址，已停止。");
      HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(false);
      c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setUseCaches(false);
      c.setRequestProperty("User-Agent","LinkDeck-Aerial/"+AppVersion.NAME);
      c.setRequestProperty("Accept",u.getHost().equals("api.github.com")&&!u.getPath().contains("/releases/assets/")?"application/vnd.github+json":"application/octet-stream");
      int status=c.getResponseCode();
      if(status>=300&&status<400) {String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("下载重定向缺少地址");u=new URL(u,location);continue;}
      if(status!=200){c.disconnect();throw new IOException("官方服务返回 HTTP "+status+"，请稍后重试或更换网络。");}
      return c;
    }
    throw new IOException("官方资源重定向次数过多");
  }
  public static Asset latest(String variant,Progress progress)throws Exception {
    if(!Arrays.asList("standard","guard","lite").contains(variant))throw new IOException("未知资源类型");
    progress.update("正在读取 Aerial 官方稳定发布…");
    String json;
    try {
      HttpURLConnection c=open(API);
      try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
        byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>2*1024*1024)throw new IOException("发布信息过大");}
        json=out.toString("UTF-8");
      }finally{c.disconnect();}
    }catch(IOException e) {
      throw new IOException("Aerial 官方发布信息暂不可用，无法取得版本和 SHA-256；本次未下载或安装。可稍后重试，或手动选择已核实的 APK。",e);
    }
    return metadata(json,variant);
  }
  public static String releaseTag(String input)throws IOException {String tag=input==null?"":input.trim();if(!tag.startsWith("v"))tag="v"+tag;if(tag.length()>48||!tag.matches("v[0-9]+(?:\\.[0-9]+){1,3}"))throw new IOException("请输入官方发布标签，例如 v1.73");return tag;}
  private static String jsonAt(String url)throws Exception {HttpURLConnection c=open(url);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>4*1024*1024)throw new IOException("发布信息过大");}return out.toString("UTF-8");}finally{c.disconnect();}}
  public static List<Asset> releases(String json,String variant)throws Exception {
    List<Asset> result=new ArrayList<>();Set<String> tags=new HashSet<>();JSONArray array=new JSONArray(json);
    if(array.length()>100)throw new IOException("版本列表过大");
    for(int i=0;i<array.length();i++){JSONObject row=array.getJSONObject(i);if(row.optBoolean("draft")||row.optBoolean("prerelease"))continue;try{String tag=releaseTag(row.optString("tag_name"));if(!tag.equals(row.optString("tag_name")))continue;Asset a=pinnedMetadata(row.toString(),variant,tag);if(tags.add(tag))result.add(a);}catch(IOException unavailable){}}
    return result;
  }
  public static List<Asset> releases(String variant,int page,Progress p)throws Exception {if(!Arrays.asList("standard","guard","lite").contains(variant)||page<1||page>10)throw new IOException("版本页码或类型无效");p.update("正在读取官方历史版本，第 "+page+" 页…");return releases(jsonAt("https://api.github.com/repos/pengchujin/aerial-tv/releases?per_page=30&page="+page),variant);}
  public static Asset pinnedMetadata(String json,String variant,String selected)throws Exception {String tag=releaseTag(selected);Asset a=metadata(json,variant);if(!tag.equals(a.tag)||!a.url.equals("https://github.com/pengchujin/aerial-tv/releases/download/"+tag+"/aerial-"+variant+".apk"))throw new IOException("官方发布标签与所选版本不一致");a.pinned=true;a.mirror=null;return a;}
  public static Asset pinned(String tag,String variant,Progress progress)throws Exception {tag=releaseTag(tag);if(!Arrays.asList("standard","guard","lite").contains(variant))throw new IOException("未知资源类型");progress.update("正在读取官方固定版本 "+tag+"…");return pinnedMetadata(jsonAt("https://api.github.com/repos/pengchujin/aerial-tv/releases/tags/"+tag),variant,tag);}
  public static Asset downloadPinned(String tag,String variant,File file,Progress p)throws Exception{return downloadAsset(pinned(tag,variant,p),variant,file,p);}
  public static String sha256(File file)throws Exception {
    MessageDigest digest=MessageDigest.getInstance("SHA-256");
    try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
    StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
  }
  public static void verify(File file,Asset asset)throws Exception {
    if(asset==null||asset.digest==null||!asset.digest.matches("[0-9a-f]{64}"))throw new IOException("官方发布缺少有效 SHA-256，已停止网络安装。");
    if(file.length()<4||file.length()>MAX||asset.size>0&&file.length()!=asset.size)throw new IOException("官方安装包长度不符，下载可能不完整，已停止安装。");
    try(InputStream in=new FileInputStream(file)){if(in.read()!=80||in.read()!=75)throw new IOException("下载内容不是 APK，可能被网络登录页面替换。");}
    if(!sha256(file).equals(asset.digest))throw new IOException("官方 SHA-256 校验未通过，已停止安装。发布版本可能刚刚变化，请重新检查。");
  }
  public static Asset download(String variant,File file,Progress progress)throws Exception {
    return downloadAsset(latest(variant,progress),variant,file,progress);
  }
  public static Asset downloadAsset(Asset asset,String variant,File file,Progress progress)throws Exception {
    if(asset==null||asset.digest==null||!asset.digest.matches("[0-9a-f]{64}")||asset.size<4||asset.size>MAX)throw new IOException("官方发布没有可核验的摘要与文件长度；没有开始下载。");
    IOException last=null;
    for(String address:new String[]{asset.url,asset.apiUrl,asset.mirror}) {
      if(address==null)continue;
      file.delete();HttpURLConnection c=null;
      try {
        progress.update("正在下载 Aerial · "+AerialSupport.title(variant));c=open(address);
        long declared=c.getContentLengthLong();if(declared>MAX)throw new IOException("下载文件超过大小限制");
        long count=0,lastUpdate=0,deadline=System.nanoTime()+300L*1000000000L;
        try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(file)) {
          byte[] b=new byte[65536];int n;
          while((n=in.read(b))!=-1) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("下载已停止");
            if(System.nanoTime()>deadline)throw new IOException("下载超过 5 分钟，请检查网络后重试。");
            count+=n;if(count>MAX)throw new IOException("下载文件超过大小限制");out.write(b,0,n);
            long now=System.currentTimeMillis();if(now-lastUpdate>500){progress.update("下载 Aerial · "+count/1024+" KB"+(declared>0?" / "+declared/1024+" KB":""));lastUpdate=now;}
          }
        }
        if(declared>=0&&count!=declared)throw new IOException("下载中断，未收到完整文件");
        progress.update("正在核对官方安装包…");verify(file,asset);return asset;
      }catch(IOException e){last=e;file.delete();if(e instanceof InterruptedIOException&&!(e instanceof SocketTimeoutException))throw e;}
      finally{if(c!=null)c.disconnect();}
    }
    throw new IOException((asset.pinned?"指定版本官方资源暂不可用，未改下最新版。请稍后重试。\n\n原始信息：":"Aerial 官方下载暂不可用。请检查手机网络后重试；也可从官网自行下载，再用“安装 APK”。\n\n原始信息：")+last,last);
  }
}
