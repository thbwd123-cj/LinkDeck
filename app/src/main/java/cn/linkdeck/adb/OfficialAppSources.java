package cn.linkdeck.adb;

/** Curated package-to-publisher mapping. No search result is treated as authority. */
public final class OfficialAppSources {
  public static final String AERIAL="aerial",GITHUB="github",VIDEOLAN="videolan",KODI="kodi",HALO="halo";
  public static final class Source {
    public final String name,type,locator;
    Source(String name,String type,String locator){this.name=name;this.type=type;this.locator=locator;}
  }
  private OfficialAppSources(){}
  public static Source forPackage(String pkg){
    if("app.orchard.tv".equals(pkg))return new Source("Aerial TV",AERIAL,"https://aerial.qust.me/");
    if("app.halotv.launcher".equals(pkg))return new Source("Halo TV",HALO,"Null9797/halo-tv");
    if("app.halotv.launcher.lite".equals(pkg))return new Source("Halo TV Lite",HALO,"Null9797/halo-tv");
    if("org.jellyfin.androidtv".equals(pkg))return new Source("Jellyfin Android TV",GITHUB,"jellyfin/jellyfin-androidtv");
    if("com.limelight".equals(pkg))return new Source("Moonlight",GITHUB,"moonlight-stream/moonlight-android");
    if("org.schabi.newpipe".equals(pkg))return new Source("NewPipe",GITHUB,"TeamNewPipe/NewPipe");
    if("org.videolan.vlc".equals(pkg))return new Source("VLC Android",VIDEOLAN,"https://download.videolan.org/pub/videolan/vlc-android/last/");
    if("org.xbmc.kodi".equals(pkg))return new Source("Kodi",KODI,"https://mirrors.kodi.tv/releases/android/");
    return null;
  }
}
