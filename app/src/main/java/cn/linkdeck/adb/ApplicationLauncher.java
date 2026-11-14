package cn.linkdeck.adb;
import java.io.*;
/** TV category first, with a legacy-firmware compatible ordinary launcher fallback. */
public final class ApplicationLauncher {
 public static String command(String pkg){
  if(pkg==null||!pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))throw new IllegalArgumentException("应用包名无效");
  String base="monkey -p "+AdbClient.quote(pkg)+" -c ";
  String q=AdbClient.quote(pkg), component=AdbClient.quote(pkg+"/");
  return "__ld_launch_out=$("+base+"android.intent.category.LEANBACK_LAUNCHER 1 2>&1); __ld_launch_status=$?; "
    +"if [ \"$__ld_launch_status\" -ne 0 ]; then __ld_launch_out=$("+base+"android.intent.category.LAUNCHER 1 2>&1); __ld_launch_status=$?; fi; "
    +"echo \"$__ld_launch_out\"; if [ \"$__ld_launch_status\" -ne 0 ]; then exit \"$__ld_launch_status\"; fi; "
    +"sleep 1; if dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|mFocusedActivity|topResumedActivity' | grep -F "+component+" >/dev/null; then echo '已确认目标应用处于前台'; "
    +"elif pidof "+q+" >/dev/null 2>&1 || ps 2>/dev/null | awk -v p="+q+" '$NF == p || index($NF,p \":\")==1 {found=1} END {exit !found}'; then echo '目标应用进程存在，前台状态未确认'; "
    +"else echo '已请求启动，但未确认前台或进程，请查看电视'; fi";

 }
}
