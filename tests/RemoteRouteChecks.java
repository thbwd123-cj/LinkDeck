package cn.linkdeck.adb;

public final class RemoteRouteChecks {
  private static void check(boolean value){if(!value)throw new AssertionError("remote transport routing");}
  public static void main(String[] args){
    int[] navigation={19,20,21,22,23,4,3,82};
    for(int code:navigation){
      check(RemoteRoute.forKey("ir",code)==RemoteRoute.IR);
      check(RemoteRoute.forKey("hybrid",code)==RemoteRoute.IR);
      check(RemoteRoute.forKey("adb",code)==RemoteRoute.ADB);
    }
    for(int code:new int[]{24,25,26,164}){
      check(RemoteRoute.forKey("ir",code)==RemoteRoute.IR);
      check(RemoteRoute.forKey("hybrid",code)==RemoteRoute.IR);
      check(RemoteRoute.forKey("adb",code)==RemoteRoute.ADB);
    }
    System.out.println("REMOTE ROUTE CHECKS PASSED");
  }
}
