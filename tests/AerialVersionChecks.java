package cn.linkdeck.adb;
import java.io.*;import java.util.*;import java.nio.file.*;import org.json.*;
public class AerialVersionChecks {
 static int n;interface Action{void run()throws Exception;}static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;System.out.println("PASS: "+m);}static void reject(Action f,String m)throws Exception{try{f.run();throw new AssertionError("accepted: "+m);}catch(IOException expected){ok(true,m);}}
 static String hex(char c){char[] a=new char[64];Arrays.fill(a,c);return new String(a);}static String id=hex('a'),sha=hex('b'),cert=hex('c');
 static JSONObject release(String tag,String type)throws Exception{return new JSONObject().put("tag_name",tag).put("draft",false).put("prerelease",false).put("assets",new JSONArray().put(new JSONObject().put("name","aerial-"+type+".apk").put("size",4).put("browser_download_url","https://github.com/pengchujin/aerial-tv/releases/download/"+tag+"/aerial-"+type+".apk").put("url","https://api.github.com/repos/pengchujin/aerial-tv/releases/assets/73").put("digest","sha256:"+sha)));}
 static class InstallDevice implements ApkTransfer.Device {long code=94;boolean rejectDowngrade,unverified;List<String> commands=new ArrayList<>();public String shell(String c){commands.add(c);if(c.startsWith("pm path"))return "package:/data/app/aerial/base.apk";if(c.startsWith("dumpsys package"))return "versionCode="+code+"\nversionName=fixture";if(c.startsWith("pm install -r")){if(rejectDowngrade)return "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]";code=unverified?94:93;return "Success";}return "";}public void push(File f,String p){commands.add("PUSH "+p);}}
 public static void main(String[] args)throws Exception{
  String state=AerialProfiles.mark("",id,AerialSupport.FULL,93,"1.73",sha,cert,"v1.73",22,true,true,false,1);
  JSONObject row=AerialProfiles.get(state,id,AerialSupport.FULL);
  ok(row.optBoolean("hold")&&row.optString("source").equals("user_confirmed"),"manual stable marking enables per-TV hold with explicit provenance");
  ok(AerialProfiles.verified(row,93,sha)&&!AerialProfiles.verified(row,94,sha),"known version requires exact code and APK fingerprint");
  ok(!AerialProfiles.verified(row,93,hex('d'))&&AerialProfiles.blocked(row,93,hex('d')),"same version with different APK stays unverified");
  ok(AerialProfiles.get(state,hex('e'),AerialSupport.FULL)==null&&AerialProfiles.get(state,id,AerialSupport.LITE)==null,"profiles do not leak across TVs or full/Lite packages");
  reject(()->AerialProfiles.mark("",id,AerialSupport.FULL,93,"1.73",sha,cert,"v1.73",22,false,true,false,1),"run confirmation is mandatory");
  reject(()->AerialProfiles.mark("",id,AerialSupport.FULL,93,"1.73",sha,cert,"v1.73",22,true,false,false,1),"physical HOME confirmation is mandatory");
  reject(()->AerialProfiles.mark("",id,AerialSupport.FULL,93,"1.73","bad",cert,"v1.73",22,true,true,false,1),"invalid installed fingerprint cannot be marked stable");
  reject(()->AerialProfiles.root("{\"schema\":9,\"devices\":{}}"),"unknown profile schema is not overwritten");
  reject(()->AerialProfiles.allow(row,AerialProfiles.revision(row),94,hex('d'),false),"held unverified install is blocked before transfer");
  AerialProfiles.allow(row,AerialProfiles.revision(row),94,hex('d'),true);ok(true,"explicit override allows one reviewed plan without changing hold");
  ok(row.optBoolean("hold"),"one-install override does not globally unlock profile");
  JSONObject corrupt=AerialProfiles.root(state);corrupt.getJSONObject("devices").getJSONObject(id+"|"+AerialSupport.FULL).remove("hold");
  reject(()->AerialProfiles.get(corrupt.toString(),id,AerialSupport.FULL),"missing hold field cannot silently unlock corrupted record");
  reject(()->AerialSafety.basePath("package:/data/app/a/base.apk\npackage:/data/app/b/base.apk"),"multiple base APK paths fail closed");
  String unlocked=AerialProfiles.hold(state,id,AerialSupport.FULL,false);JSONObject next=AerialProfiles.get(unlocked,id,AerialSupport.FULL);
  reject(()->AerialProfiles.allow(next,AerialProfiles.revision(row),94,hex('d'),true),"profile revision changes invalidate prepared installation");
  AerialProfiles.allow(next,AerialProfiles.revision(next),94,hex('d'),false);ok(true,"explicitly resumed evaluation allows user-reviewed update");
  ok(AerialProfiles.get(state,id,AerialSupport.FULL).optString("sha256").equals(sha),"serialized stable profile round-trips without losing proof");
  ok(AerialDownload.releaseTag("1.73").equals("v1.73"),"numeric official release tag is normalized");
  reject(()->AerialDownload.releaseTag("../../latest"),"release tag path injection rejected");
  reject(()->AerialDownload.releaseTag("v1.73?download=latest"),"release tag query injection rejected");
  AerialDownload.Asset fixed=AerialDownload.pinnedMetadata(release("v1.73","standard").toString(),"standard","v1.73");
  ok(fixed.pinned&&fixed.mirror==null&&fixed.url.contains("/v1.73/"),"fixed release never keeps current-website mirror");
  reject(()->AerialDownload.pinnedMetadata(release("v1.74","standard").toString(),"standard","v1.73"),"mismatched selected and returned release tags rejected");
  JSONObject bad=release("v1.73","standard");bad.getJSONArray("assets").getJSONObject(0).put("browser_download_url","https://github.com/pengchujin/aerial-tv/releases/latest/download/aerial-standard.apk");
  reject(()->AerialDownload.pinnedMetadata(bad.toString(),"standard","v1.73"),"fixed release cannot silently redirect metadata to latest tag");
  JSONArray rows=new JSONArray().put(release("v1.73","standard")).put(release("v1.74","standard").put("prerelease",true)).put(release("v1.72","standard").put("draft",true)).put(release("v1.71","guard")).put(release("v1.73","standard"));
  List<AerialDownload.Asset> list=AerialDownload.releases(rows.toString(),"standard");ok(list.size()==1&&list.get(0).tag.equals("v1.73"),"catalog filters draft/prerelease/wrong asset and deduplicates tags");
  ok(AerialSafety.fullTagMatches("v1.73","1.73-guard")&&!AerialSafety.fullTagMatches("v1.73","1.74"),"full APK version must match selected official tag");
  ok(AerialSafety.basePath("package:/data/app/a/base.apk\npackage:/data/app/a/split_config.apk").endsWith("/base.apk"),"installed split package uses explicit base APK for signature proof");
  reject(()->AerialSafety.basePath("package:/data/app/a/split_1.apk\npackage:/data/app/a/split_2.apk"),"ambiguous installed base path rejected");
  AerialSafety.sameSigners(cert,cert);ok(true,"matching signing certificates accepted");
  reject(()->AerialSafety.sameSigners(cert,hex('d')),"changed signer blocked before update or rollback");
  File apk=Files.createTempFile("aerial-version-test", ".apk").toFile();Files.write(apk.toPath(),new byte[]{80,75,0,0});
  InstallDevice d=new InstallDevice();AerialInstaller.Installed checked=AerialInstaller.read(d,AerialSupport.FULL);
  reject(()->AerialInstaller.install(apk,AerialSupport.FULL,93,checked,d),"default installer still refuses implicit downgrade");
  ok(d.commands.stream().noneMatch(c->c.startsWith("PUSH")),"implicit downgrade is rejected before transmission");
  d.commands.clear();String result=AerialInstaller.install(apk,AerialSupport.FULL,93,checked,d,true);
  ok(d.code==93&&result.contains("已确认")&&d.commands.stream().anyMatch(c->c.startsWith("pm install -r -d ")),"explicit downgrade uses preserving flags and verifies final version");
  ok(d.commands.stream().noneMatch(c->c.contains("uninstall")||c.startsWith("pm clear")),"downgrade never falls back to uninstall or clearing data");
  InstallDevice deny=new InstallDevice();deny.rejectDowngrade=true;AerialInstaller.Installed dc=AerialInstaller.read(deny,AerialSupport.FULL);
  try{AerialInstaller.install(apk,AerialSupport.FULL,93,dc,deny,true);throw new AssertionError("must reject");}catch(IOException e){ok(e.getMessage().contains("电视拒绝")&&deny.code==94,"system downgrade denial is explicit and does not claim successful rollback");}
  ok(deny.commands.stream().noneMatch(c->c.contains("uninstall")||c.startsWith("pm clear")),"downgrade denial has no destructive fallback");
  InstallDevice race=new InstallDevice();AerialInstaller.Installed rc=AerialInstaller.read(race,AerialSupport.FULL);race.code=95;
  reject(()->AerialInstaller.install(apk,AerialSupport.FULL,93,rc,race,true),"installed version changes invalidate rollback consent");
  InstallDevice stale=new InstallDevice();stale.unverified=true;AerialInstaller.Installed sc=AerialInstaller.read(stale,AerialSupport.FULL);reject(()->AerialInstaller.install(apk,AerialSupport.FULL,93,sc,stale,true),"success text without matching final version cannot pass rollback");
  apk.delete();System.out.println("AERIAL VERSION MANAGEMENT PASSED: "+n+" checks.");
 }
}
