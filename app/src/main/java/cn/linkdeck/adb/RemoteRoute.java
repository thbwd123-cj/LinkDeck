package cn.linkdeck.adb;

/** The remote chooses one transport for every key. */
public final class RemoteRoute {
  public static final int IR=1, ADB=2;
  private RemoteRoute(){}
  public static int forKey(String mode,int androidKeyCode){
    if("adb".equals(mode))return ADB;
    return IR;
  }
}
