package cn.linkdeck.adb;

public final class ConnectionAddressChecks {
  private static int checks;
  private static void check(boolean result){checks++;if(!result)throw new AssertionError("connection address check "+checks);}
  private static void reject(String host,String port){try{ConnectionAddress.parse(host,port);throw new AssertionError("accepted "+host);}catch(IllegalArgumentException expected){checks++;}}
  public static void main(String[] args){
    ConnectionAddress v4=ConnectionAddress.parse("192.0.2.8:37123","5555");check(v4.host.equals("192.0.2.8")&&v4.port==37123);
    ConnectionAddress v6=ConnectionAddress.parse("[2001:db8::1]:40123","5555");check(v6.host.equals("2001:db8::1")&&v6.port==40123);
    check(ConnectionAddress.parse("television.local","5555").port==5555);
    reject("2001:db8::1","5555");reject("fe80::1:5555","5555");
    reject("http://192.0.2.8","5555");reject("192.0.2.8:0","5555");reject("192.0.2.8","65536");reject("192.0.2.8:abc","5555");reject("[::1]oops","5555");reject("192.0.2.8/evil","5555");
    System.out.println("CONNECTION ADDRESS PASSED: "+checks+" checks");
  }
}
