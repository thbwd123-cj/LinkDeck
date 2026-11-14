package cn.linkdeck.adb;

/** Display-only comparison. APK versionCode and signature remain authoritative. */
public final class UpdateScan {
  private UpdateScan(){}
  /** Only a display hint. Actual candidate versionCode must be checked after download. */
  public static boolean looksNewer(String installed,String published){return compareNames(installed,published)==-1;}
  public static int compareNames(String installed,String published){
    if(installed==null||published==null)return Integer.MIN_VALUE;
    String a=installed.trim().replaceFirst("^[vV]","");
    String b=published.trim().replaceFirst("^[vV]","");
    if(!a.matches("[0-9]+(?:\\.[0-9]+){1,3}")||!b.matches("[0-9]+(?:\\.[0-9]+){1,3}"))return Integer.MIN_VALUE;
    String[] x=a.split("\\."),y=b.split("\\.");
    try{
      for(int i=0;i<Math.max(x.length,y.length);i++){
        long left=i<x.length?Long.parseLong(x[i]):0,right=i<y.length?Long.parseLong(y[i]):0;
        if(left!=right)return left<right?-1:1;
      }
      return 0;
    }catch(NumberFormatException e){return Integer.MIN_VALUE;}
  }
}
