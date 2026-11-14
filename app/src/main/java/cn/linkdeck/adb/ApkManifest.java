package cn.linkdeck.adb;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

/** Reads only manifest split metadata from the Android binary XML string pool and start tags. */
public final class ApkManifest {
  public String split="", packageName="";
  public boolean splitRequired,homeCategory;
  public final Set<String> dependencies=new TreeSet<>();
  public static ApkManifest read(File apk) throws IOException {
    try(ZipFile z=new ZipFile(apk)) {
      ZipEntry e=z.getEntry("AndroidManifest.xml");
      if(e==null)throw new IOException("安装包缺少 AndroidManifest.xml");
      try(InputStream in=z.getInputStream(e);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
        byte[] block=new byte[8192];int n;
        while((n=in.read(block))!=-1){out.write(block,0,n);if(out.size()>4*1024*1024)throw new IOException("安装包清单异常过大");}
        return parse(out.toByteArray());
      }
    }
  }
  public static ApkManifest parse(byte[] bytes) throws IOException {
    try {
      ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      if(bytes.length<8||u16(b,0)!=3)throw new IOException("无法读取二进制 APK 清单");
      int end=b.getInt(4);if(end>bytes.length||end<8)throw new IOException("APK 清单长度无效");
      List<String> strings=new ArrayList<>();ApkManifest result=new ApkManifest();boolean manifest=false;
      for(int at=u16(b,2);at+8<=end;) {
        int type=u16(b,at),header=u16(b,at+2),size=b.getInt(at+4);
        if(header<8||size<header||size>end-at)throw new IOException("APK 清单结构无效");
        if(type==1) {
          if(header<28)throw new IOException("字符串池无效");
          int count=b.getInt(at+8),flags=b.getInt(at+16),start=b.getInt(at+20);
          if(count<0||count>100000||header+4L*count>size)throw new IOException("字符串池长度无效");
          strings.clear();
          for(int i=0;i<count;i++) {
            int off=b.getInt(at+header+i*4);if(off<0||start<0||start+(long)off>=size)throw new IOException("字符串偏移无效");
            int[] p={at+start+off};int len;
            if((flags&256)!=0) {length8(b,p);len=length8(b,p);strings.add(decode(bytes,p[0],len,at+size,StandardCharsets.UTF_8));}
            else {len=length16(b,p);strings.add(decode(bytes,p[0],Math.multiplyExact(len,2),at+size,StandardCharsets.UTF_16LE));}
          }
        } else if(type==0x102) {
          if(header<16||size<36)throw new IOException("XML 节点无效");
          String tag=str(strings,b.getInt(at+20));
          int attrStart=u16(b,at+24),stride=u16(b,at+26),count=u16(b,at+28);
          if(attrStart<20||stride<20||16L+attrStart+(long)stride*count>size)throw new IOException("XML 属性无效");
          if(tag.equals("manifest"))manifest=true;
          for(int i=0;i<count;i++) {
            int a=at+16+attrStart+i*stride;
            String name=str(strings,b.getInt(a+4));int raw=b.getInt(a+8),value=b.getInt(a+16),valueType=bytes[a+15]&255;
            String v=raw>=0?str(strings,raw):valueType==3?str(strings,value):Integer.toString(value);
            if(tag.equals("manifest")&&name.equals("split"))result.split=v;
            if(tag.equals("manifest")&&name.equals("package"))result.packageName=v;
            if(tag.equals("application")&&name.equals("isSplitRequired"))result.splitRequired=valueType==18?value!=0:v.equals("true");
            if(tag.equals("category")&&name.equals("name")&&v.equals("android.intent.category.HOME"))result.homeCategory=true;
            if(tag.equals("uses-split")&&name.equals("name")&&!v.isEmpty())result.dependencies.add(v);
          }
        }
        at+=size;
      }
      if(!manifest)throw new IOException("APK 清单缺少 manifest 节点");
      return result;
    }catch(IndexOutOfBoundsException|ArithmeticException e){throw new IOException("APK 清单损坏",e);}
  }
  private static int u16(ByteBuffer b,int p){return b.getShort(p)&65535;}
  private static int length8(ByteBuffer b,int[] p){int n=b.get(p[0]++)&255;return (n&128)!=0?((n&127)<<8)|(b.get(p[0]++)&255):n;}
  private static int length16(ByteBuffer b,int[] p){int n=u16(b,p[0]);p[0]+=2;if((n&32768)==0)return n;int low=u16(b,p[0]);p[0]+=2;return ((n&32767)<<16)|low;}
  private static String str(List<String> s,int n)throws IOException {if(n<0||n>=s.size())throw new IOException("APK 字符串引用无效");return s.get(n);}
  private static String decode(byte[] b,int at,int n,int end,Charset c)throws IOException {if(n<0||at<0||(long)at+n>end)throw new IOException("APK 字符串越界");return new String(b,at,n,c);}
  public static String splits(List<File> files) throws IOException {
    Set<String> names=new HashSet<>();ApkManifest base=null;List<ApkManifest> manifests=new ArrayList<>();
    for(File file:files){ApkManifest m=read(file);manifests.add(m);if(m.split.isEmpty()){if(base!=null)return "发现多个基础包，请重新选择完整应用。";base=m;}else if(!names.add(m.split))return "发现重复分包："+m.split;}
    if(base==null)return "这是分包，缺少基础包，不能单独安装。请从“选择手机应用”传输完整应用。";
    if(base.splitRequired&&names.isEmpty())return "基础包要求配套分包。请从“选择手机应用”传输完整应用，或选择独立完整版 APK。";
    for(ApkManifest m:manifests){if(!m.packageName.equals(base.packageName))return "基础包与分包的包名不一致。";for(String name:m.dependencies)if(!names.contains(name))return "缺少必需分包："+name+"。请重新选择完整应用。";}
    return "";
  }
  public static String android(int sdk) {
    switch(sdk){case 21:return "Android 5.0";case 22:return "Android 5.1";case 23:return "Android 6";case 24:case 25:return "Android 7";case 26:case 27:return "Android 8";case 28:return "Android 9";case 29:return "Android 10";case 30:return "Android 11";case 31:case 32:return "Android 12";case 33:return "Android 13";case 34:return "Android 14";case 35:return "Android 15";case 36:return "Android 16";default:return sdk>0?"SDK "+sdk:"未知版本";}
  }
  public static String compatibility(int min,int sdk,Set<String> abis,String deviceAbis) {
    StringBuilder s=new StringBuilder();
    if(sdk>0&&min>sdk)s.append("✕ 此应用最低要求 ").append(android(min)).append("，电视为 ").append(android(sdk)).append("，无法直接安装。\n");
    if(!ApkTransfer.compatible(0,0,abis,deviceAbis))s.append("✕ CPU 架构不匹配：应用 ").append(abis).append("，电视 ").append(deviceAbis).append("。\n");
    if(s.length()==0)s.append(sdk>0&&deviceAbis!=null&&!deviceAbis.isEmpty()?"✓ 系统版本和 CPU 初步检查通过。\n":"待连接设备，或设备信息不完整，暂不能完整判断兼容性。\n");
    return s.toString();
  }
  public static String installError(String raw) {
    String reason="";
    if(raw.contains("INSTALL_FAILED_OLDER_SDK"))reason="电视系统版本低于应用要求，请选择兼容旧安卓的版本。";
    else if(raw.contains("INSTALL_FAILED_NO_MATCHING_ABIS")||raw.contains("INSTALL_FAILED_CPU_ABI_INCOMPATIBLE"))reason="CPU 架构不兼容，请选择适合电视架构的安装包。";
    else if(raw.contains("INSTALL_FAILED_MISSING_SPLIT"))reason="缺少配套分包，请使用“选择手机应用”传输完整应用。";
    else if(raw.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE"))reason="电视已有同包名但签名不同的应用，请核对来源。卸载旧应用会删除其数据，不会自动卸载。";
    else if(raw.contains("INSTALL_FAILED_VERSION_DOWNGRADE"))reason="安装版本低于电视现有版本，请选择更新版本。";
    else if(raw.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE"))reason="电视可用存储空间不足，请先整理文件后重试。";
    return reason.isEmpty()?raw:reason+"\n\n原始返回：\n"+raw;
  }
}
