package cn.linkdeck.adb;

import java.util.List;

public final class SubnetAddressesChecks {
  private static int checks;
  private static void check(boolean value){checks++;if(!value)throw new AssertionError("subnet check "+checks);}
  private static void reject(byte[] address,int prefix){try{SubnetAddresses.around(address,prefix);throw new AssertionError("accepted invalid subnet");}catch(IllegalArgumentException expected){checks++;}}
  public static void main(String[] args){
    List<String> home=SubnetAddresses.around(new byte[]{(byte)192,(byte)168,31,42},24);
    check(home.size()==253);check(home.contains("192.168.31.120"));check(!home.contains("192.168.31.42"));check(!home.contains("192.168.32.1"));
    List<String> small=SubnetAddresses.around(new byte[]{10,2,3,65},26);
    check(small.size()==61);check(small.contains("10.2.3.66"));check(!small.contains("10.2.3.63"));check(!small.contains("10.2.3.127"));
    List<String> capped=SubnetAddresses.around(new byte[]{(byte)172,16,8,9},16);check(capped.size()==253);check(!capped.contains("172.16.9.1"));
    reject(null,24);reject(new byte[]{1,2,3},24);reject(new byte[]{1,2,3,4},15);reject(new byte[]{1,2,3,4},31);
    System.out.println("SUBNET ADDRESSES PASSED: "+checks+" checks");
  }
}
