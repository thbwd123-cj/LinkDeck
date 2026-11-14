package cn.linkdeck.adb;

/** Android DHCP IPv4 values use little-endian integers. Keep this conversion testable. */
public final class WifiIpv4 {
  private WifiIpv4() {}
  public static byte[] address(int value) {
    return new byte[]{(byte)value,(byte)(value>>>8),(byte)(value>>>16),(byte)(value>>>24)};
  }
  public static int prefix(int mask) {
    int count=0;boolean zero=false;
    for(byte b:address(mask))for(int bit=7;bit>=0;bit--){
      if(((b>>>bit)&1)==0)zero=true;
      else {if(zero)return -1;count++;}
    }
    return count;
  }
  public static boolean usable(byte[] ip) {
    if(ip==null||ip.length!=4)return false;
    int first=ip[0]&255;
    return first!=0&&first!=127&&first!=169&&first<224;
  }
  public static String display(byte[] ip){return (ip[0]&255)+"."+(ip[1]&255)+"."+(ip[2]&255)+"."+(ip[3]&255);}
}
