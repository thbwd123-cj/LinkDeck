package cn.linkdeck.adb;

import java.util.Arrays;

public final class WifiIpv4Checks {
  private static int count;
  private static void check(boolean value){count++;if(!value)throw new AssertionError("Wi-Fi IPv4 check "+count);}
  public static void main(String[] args){
    byte[] address=WifiIpv4.address(0x2a1fa8c0);
    check(Arrays.equals(address,new byte[]{(byte)192,(byte)168,31,42}));
    check(WifiIpv4.display(address).equals("192.168.31.42"));
    check(SubnetAddresses.around(address,WifiIpv4.prefix(0x00ffffff)).contains("192.168.31.120"));
    check(WifiIpv4.prefix(0x00ffffff)==24);
    check(WifiIpv4.prefix(0x00ff00ff)==-1);
    check(!WifiIpv4.usable(WifiIpv4.address(0)));
    check(!WifiIpv4.usable(new byte[]{(byte)169,(byte)254,1,2}));
    check(WifiIpv4.usable(address));
    System.out.println("WI-FI IPV4 PASSED: "+count+" checks");
  }
}
