package cn.linkdeck.adb;
import java.io.*;import java.net.*;import java.nio.file.*;import java.util.*;
public class AerialDownloadChecks {
 static byte[] json,apk;static String mode="normal";static int count;static List<String> requested=new ArrayList<>();
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);count++;System.out.println("PASS: "+m);}
 static class Mock extends HttpURLConnection {
  Mock(URL u){super(u);requested.add(u.toString());}
  boolean metadata(){return url.toString().equals(AerialDownload.API);}
  public int getResponseCode(){if(metadata())return mode.equals("fallback")?503:200;if(url.getHost().equals("github.com"))return mode.equals("redirect")?302:503;return 200;}
  public String getHeaderField(String n){return n.equals("Location")?"http://evil.invalid/payload.apk":null;}
  public long getContentLengthLong(){return metadata()?json.length:mode.equals("oversize")?101L*1024*1024:apk.length+(mode.equals("truncated")?100:0);}
  public InputStream getInputStream(){byte[] bytes=metadata()?json:apk;if(!metadata()&&mode.equals("tamper")){bytes=bytes.clone();bytes[bytes.length-1]^=1;}return new ByteArrayInputStream(bytes);}
  public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}
 }
 public static void main(String[] args)throws Exception {
  json=Files.readAllBytes(Paths.get(args[0]));apk=Files.readAllBytes(Paths.get(args[1]));
  URL.setURLStreamHandlerFactory(p->p.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Mock(u);}}:null);
  File dest=Files.createTempFile("aerial-download-",".apk").toFile();
  AerialDownload.Asset a=AerialDownload.download("standard",dest,s->{});
  check(dest.length()==apk.length&&!a.digest.isEmpty(),"download fallback preserves pinned release digest and size");
  check(requested.stream().anyMatch(u->u.contains("/releases/assets/")),"official asset API fallback exercised");
  mode="fallback";requested.clear();try{AerialDownload.download("standard",dest,s->{});throw new AssertionError("metadata outage accepted");}catch(IOException expected){check(requested.stream().noneMatch(u->u.contains("aerial.qust.me")),"metadata outage never downloads unverifiable fallback");}
  for(String fail:new String[]{"tamper","truncated","oversize"}){mode=fail;try{AerialDownload.download("standard",dest,s->{});throw new AssertionError("accepted "+fail);}catch(IOException expected){check(!dest.exists(),fail+" download rejected and partial file removed");}}
  mode="redirect";requested.clear();AerialDownload.download("standard",dest,s->{});check(requested.stream().noneMatch(u->u.contains("evil.invalid")),"insecure redirect never contacted; official fallback still works");
  check(!AerialDownload.trusted(new URL("https://github.com/other/repo/releases/x.apk"))&&!AerialDownload.trusted(new URL("https://aerial.qust.me.evil.invalid/download/a.apk")),"unrelated repository and lookalike hostname rejected");
  dest.delete();System.out.println("AERIAL DOWNLOAD STATE MACHINE PASSED: "+count+" checks.");
 }
}
