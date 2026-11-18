package cn.linkdeck.adb;
import java.io.*;import java.util.*;import java.nio.file.*;import org.json.*;
public class HaloReleaseChecks {
 static int n;interface Action{void run()throws Exception;}static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;System.out.println("PASS: "+m);}static void reject(Action f,String m)throws Exception{try{f.run();throw new AssertionError("accepted: "+m);}catch(IOException expected){ok(true,m);}}
 static String hex(char c){char[] a=new char[64];Arrays.fill(a,c);return new String(a);}static String sha=hex('b'),cert=hex('c');
 static JSONObject release(String tag,String type)throws Exception{return new JSONObject().put("tag_name",tag).put("draft",false).put("prerelease",false).put("assets",new JSONArray().put(new JSONObject().put("name","HaloTV-"+HaloDownload.versionFromTag(tag)+"-"+type+".apk").put("size",4).put("browser_download_url","https://github.com/Null9797/halo-tv/releases/download/"+tag+"/HaloTV-"+HaloDownload.versionFromTag(tag)+"-"+type+".apk").put("url","https://api.github.com/repos/Null9797/halo-tv/releases/assets/73").put("digest","sha256:"+sha)));}
 static class InstallDevice implements ApkTransfer.Device {long code=259;boolean rejectDowngrade,unverified;List<String> commands=new ArrayList<>();public String shell(String c){commands.add(c);if(c.startsWith("pm path"))return "package:/data/app/halo/base.apk";if(c.startsWith("dumpsys package"))return "versionCode="+code+"\nversionName=fixture";if(c.startsWith("pm install -r")){if(rejectDowngrade)return "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]";code=unverified?259:258;return "Success";}return "";}public void push(File f,String p){commands.add("PUSH "+p);}}
 static class ProbeShell implements HaloProbe.Shell {boolean missing;public AdbClient.Result run(String c){if(c.startsWith("pm path"))return new AdbClient.Result(missing?"Error: package app.halotv.launcher was not found":"package:/data/app/halo/base.apk",0);if(c.startsWith("pm list packages"))return new AdbClient.Result(missing?"package:other.app":"package:app.halotv.launcher\npackage:other.app",0);return new AdbClient.Result("",0);}}
 public static void main(String[] args)throws Exception{
  ok(HaloDownload.releaseTag("3.3.6").equals("v3.3.6-halo"),"bare version is normalized to a Halo release tag");
  ok(HaloDownload.releaseTag("v3.3.6").equals("v3.3.6-halo"),"tag without suffix is normalized");
  ok(HaloDownload.releaseTag("v3.3.6-halo").equals("v3.3.6-halo"),"full Halo tag is accepted");
  reject(()->HaloDownload.releaseTag("../../latest"),"release tag path injection rejected");
  reject(()->HaloDownload.releaseTag("v3.2.4-halo-test"),"test release tag is not a stable release");
  reject(()->HaloDownload.releaseTag("v1.57-universal"),"pre-Halo universal tag is not a stable Halo release");
  reject(()->HaloDownload.releaseTag("v3.0.1-halo-r2"),"revision suffix tag is not a stable release");
  reject(()->HaloDownload.releaseTag("v1.2-halo-lite-beta"),"beta tag is not a stable release");
  ok(HaloDownload.versionFromTag("v3.3.6-halo").equals("3.3.6"),"dotted version is extracted from the tag");
  reject(()->HaloDownload.versionFromTag("v3.3.6"),"tag without -halo suffix has no dotted version");
  HaloDownload.Asset fixed=HaloDownload.pinnedMetadata(release("v3.3.6-halo","standard").toString(),"standard","v3.3.6-halo");
  ok(fixed.pinned&&fixed.mirror==null&&fixed.url.contains("/v3.3.6-halo/")&&fixed.digest.equals(sha),"fixed release never keeps current-website mirror and keeps its digest");
  ok(fixed.url.endsWith("/HaloTV-3.3.6-standard.apk"),"asset name embeds the dotted version and variant");
  reject(()->HaloDownload.pinnedMetadata(release("v3.3.5-halo","standard").toString(),"standard","v3.3.6-halo"),"mismatched selected and returned release tags rejected");
  JSONObject bad=release("v3.3.6-halo","standard");bad.getJSONArray("assets").getJSONObject(0).put("browser_download_url","https://github.com/Null9797/halo-tv/releases/latest/download/HaloTV-3.3.6-standard.apk");
  reject(()->HaloDownload.pinnedMetadata(bad.toString(),"standard","v3.3.6-halo"),"fixed release cannot silently redirect metadata to latest tag");
  JSONArray rows=new JSONArray().put(release("v3.3.6-halo","standard")).put(release("v3.3.5-halo","standard").put("prerelease",true)).put(release("v3.3.4-halo","standard").put("draft",true)).put(new JSONObject().put("tag_name","v3.2.4-halo-test").put("draft",false).put("prerelease",false).put("assets",new JSONArray().put(new JSONObject().put("name","HaloTV-3.2.4-standard.apk").put("size",4).put("browser_download_url","https://github.com/Null9797/halo-tv/releases/download/v3.2.4-halo-test/HaloTV-3.2.4-standard.apk").put("digest","sha256:"+sha)))).put(release("v3.3.6-halo","standard"));
  List<HaloDownload.Asset> list=HaloDownload.releases(rows.toString(),"standard");ok(list.size()==1&&list.get(0).tag.equals("v3.3.6-halo"),"catalog filters draft/prerelease/test tags and deduplicates tags");
  ok(HaloSupport.variant(28).equals("standard")&&HaloSupport.variant(19).equals("lite"),"variant follows the Android 5.0 boundary");
  reject(()->HaloSupport.variant(13),"Android 4.0 and older cannot install Halo TV");
  ok(HaloSupport.decision(false,-1,259)==-1&&HaloSupport.decision(true,259,259)==0&&HaloSupport.decision(true,258,259)==1&&HaloSupport.decision(true,260,259)==2,"install, same, update and newer-installed decisions match");
  reject(()->HaloSupport.decision(true,-1,259),"unreadable installed version blocks overwrite");
  ok(HaloSupport.title("guard").contains("守护"),"guard variant is labelled as the no-HOME build");
  ProbeShell probe=new ProbeShell();ok(HaloProbe.packagePath(probe,HaloSupport.FULL).startsWith("package:"),"installed package path is read");
  probe.missing=true;ok(HaloProbe.packagePath(probe,HaloSupport.FULL).isEmpty(),"missing package is reported as not installed, not an error");
  File apk=Files.createTempFile("halo-version-test", ".apk").toFile();Files.write(apk.toPath(),new byte[]{80,75,0,0});
  HaloDownload.Asset verified=new HaloDownload.Asset();verified.digest=HaloDownload.sha256(apk);verified.size=4;HaloDownload.verify(apk,verified);ok(true,"matching SHA-256 and size pass verification");
  verified.digest=hex('d');reject(()->HaloDownload.verify(apk,verified),"wrong SHA-256 stops installation");
  InstallDevice d=new InstallDevice();HaloInstaller.Installed checked=HaloInstaller.read(d,HaloSupport.FULL);
  reject(()->HaloInstaller.install(apk,HaloSupport.FULL,258,checked,d),"default installer still refuses implicit downgrade");
  ok(d.commands.stream().noneMatch(c->c.startsWith("PUSH")),"implicit downgrade is rejected before transmission");
  d.commands.clear();String result=HaloInstaller.install(apk,HaloSupport.FULL,258,checked,d,true);
  ok(d.code==258&&result.contains("已确认")&&d.commands.stream().anyMatch(c->c.startsWith("pm install -r -d ")),"explicit downgrade uses preserving flags and verifies final version");
  ok(d.commands.stream().noneMatch(c->c.contains("uninstall")||c.startsWith("pm clear")),"downgrade never falls back to uninstall or clearing data");
  InstallDevice deny=new InstallDevice();deny.rejectDowngrade=true;HaloInstaller.Installed dc=HaloInstaller.read(deny,HaloSupport.FULL);
  try{HaloInstaller.install(apk,HaloSupport.FULL,258,dc,deny,true);throw new AssertionError("must reject");}catch(IOException e){ok(e.getMessage().contains("电视拒绝")&&deny.code==259,"system downgrade denial is explicit and does not claim successful rollback");}
  ok(deny.commands.stream().noneMatch(c->c.contains("uninstall")||c.startsWith("pm clear")),"downgrade denial has no destructive fallback");
  InstallDevice race=new InstallDevice();HaloInstaller.Installed rc=HaloInstaller.read(race,HaloSupport.FULL);race.code=260;
  reject(()->HaloInstaller.install(apk,HaloSupport.FULL,258,rc,race,true),"installed version changes invalidate rollback consent");
  InstallDevice stale=new InstallDevice();stale.unverified=true;HaloInstaller.Installed sc=HaloInstaller.read(stale,HaloSupport.FULL);reject(()->HaloInstaller.install(apk,HaloSupport.FULL,258,sc,stale,true),"success text without matching final version cannot pass rollback");
  apk.delete();System.out.println("HALO RELEASE MANAGEMENT PASSED: "+n+" checks.");
 }
}
