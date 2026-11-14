package cn.linkdeck.adb;
import java.io.*;import java.net.*;import java.nio.file.*;import java.util.*;
public class PinnedDownloadChecks{
 static List<String> requests=new ArrayList<>();static String mode="ok";static byte[] apk={80,75,0,0};static int n;
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;System.out.println("PASS: "+m);}
 static class Mock extends HttpURLConnection{Mock(URL u){super(u);requests.add(u.toString());}public int getResponseCode(){return mode.equals("outage")?503:200;}public long getContentLengthLong(){return apk.length;}public InputStream getInputStream(){return new ByteArrayInputStream(apk);}public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}}
 static File fixture()throws IOException{File f=Files.createTempFile("digest-test", ".apk").toFile();Files.write(f.toPath(),apk);f.deleteOnExit();return f;}
 public static void main(String[] args)throws Exception{
  URL.setURLStreamHandlerFactory(p->p.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Mock(u);}}:null);
  AerialDownload.Asset a=AerialDownload.pinnedMetadata(AerialVersionChecks.release("v1.73","standard").toString(),"standard","v1.73");File f=Files.createTempFile("pinned-test", ".apk").toFile();
  a.digest="";requests.clear();try{AerialDownload.downloadAsset(a,"standard",f,s->{});throw new AssertionError("missing digest accepted");}catch(IOException expected){ok(requests.isEmpty(),"missing digest prevents all downloads");}
  a.digest=AerialDownload.sha256(fixture());
  AerialDownload.downloadAsset(a,"standard",f,s->{});ok(f.length()==4&&requests.get(0).contains("/v1.73/"),"fixed release download uses selected immutable route");
  mode="outage";requests.clear();try{AerialDownload.downloadAsset(a,"standard",f,s->{});throw new AssertionError("must fail");}catch(IOException expected){ok(!f.exists()&&expected.getMessage().contains("未改下最新版"),"pinned resource outage removes partial file and explains fixed-version failure");}
  ok(requests.stream().noneMatch(u->u.contains("aerial.qust.me")||u.contains("/latest")),"historical APK failure never falls back to current website or latest release");
  f.delete();System.out.println("PINNED DOWNLOAD PASSED: "+n+" checks.");
 }
}
