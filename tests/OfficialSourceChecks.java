package cn.linkdeck.adb;
import java.util.*;

public final class OfficialSourceChecks {
  private static int checks;
  private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("official source "+checks);}
  public static void main(String[] args)throws Exception{
    check(OfficialAppSources.forPackage("app.orchard.tv").type.equals(OfficialAppSources.AERIAL));
    check(OfficialAppSources.forPackage("org.jellyfin.androidtv").locator.equals("jellyfin/jellyfin-androidtv"));
    check(OfficialAppSources.forPackage("com.limelight").locator.equals("moonlight-stream/moonlight-android"));
    check(OfficialAppSources.forPackage("org.schabi.newpipe").locator.equals("TeamNewPipe/NewPipe"));
    check(OfficialAppSources.forPackage("org.videolan.vlc").type.equals(OfficialAppSources.VIDEOLAN));
    check(OfficialAppSources.forPackage("org.xbmc.kodi").type.equals(OfficialAppSources.KODI));
    check(OfficialAppSources.forPackage("com.some.unknown")==null);
    String index="<a href=\"VLC-Android-3.6.5-armeabi-v7a.apk\">old</a>"
      +"<a href=\"VLC-Android-3.7.1-arm64-v8a.apk\">new64</a>"
      +"<a href=\"VLC-Android-3.7.1-armeabi-v7a.apk\">new32</a>"
      +"<a href=\"VLC-Android-3.7.0-x86.apk\">oldx86</a>";
    List<OfficialVlc.Asset> latest=OfficialVlc.parse(index);
    check(latest.size()==2);
    check(latest.get(0).version.equals("3.7.1")&&latest.get(1).abi.equals("armeabi-v7a"));
    try{OfficialVlc.parse("<html>no apk</html>");throw new AssertionError("empty directory accepted");}
    catch(java.io.IOException expected){checks++;}
    OfficialKodi.Asset kodi=OfficialKodi.parse("<a>kodi-22.0-Piers_rc1-armeabi-v7a.apk</a><a>kodi-21.3-Omega-armeabi-v7a.apk</a><a>kodi-20.5-Nexus-armeabi-v7a.apk</a>","armeabi-v7a");
    check(kodi.version.equals("21.3")&&kodi.url.startsWith("https://mirrors.kodi.tv/releases/android/arm/"));
    check(UpdateScan.compareNames("v1.73","v1.83")<0);
    check(UpdateScan.compareNames("1.83","v1.83")==0);
    check(UpdateScan.compareNames("21.3","21.2")>0);
    check(UpdateScan.compareNames("unknown","v1.83")==Integer.MIN_VALUE);
    check(UpdateScan.looksNewer("1.73","1.83"));
    check(!UpdateScan.looksNewer("1.83","1.73"));
    check(!UpdateScan.looksNewer("unknown","2.0"));
    System.out.println("OFFICIAL SOURCE CHECKS PASSED: "+checks+" checks");
  }
}
