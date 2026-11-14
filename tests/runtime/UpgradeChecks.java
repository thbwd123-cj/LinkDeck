package cn.linkdeck.qa;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import cn.linkdeck.adb.*;
import java.io.*;
import java.util.*;

public class UpgradeChecks extends RuntimeChecks {
  void foreground() throws Exception {
    getTargetContext()
        .startActivity(
            new Intent()
                .setClassName("cn.linkdeck.adb", "cn.linkdeck.adb.MainActivity")
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    waitFor("activity returned to foreground", () -> activity.hasWindowFocus());
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      getTargetContext().getSharedPreferences("linkdeck",0).edit().putBoolean("resume_connection",false).commit();
      activity =
          startActivitySync(
              new Intent()
                  .setClassName("cn.linkdeck.adb", "cn.linkdeck.adb.MainActivity")
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
      waitFor(
          "upgrade home rendered", () -> field("hostInput") != null);
      runOnMainSync(
          () -> {
            try {
              ((EditText) field("hostInput")).setText("127.0.0.1");
              ((EditText) field("portInput")).setText("5555");
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      tap("连接设备");
      waitFor(
          "real ADB connection established",
          () -> client() != null && client().isConnected() && !(Boolean) field("busy"));
      waitFor(
          "connected-device foreground service started",
          () -> {
            ActivityManager am =
                (ActivityManager) getTargetContext().getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo service : am.getRunningServices(20))
              if (service.service.getClassName().equals("cn.linkdeck.adb.ConnectionService"))
                return service.foreground;
            return false;
          });
      AdbClient original = client();
      call("onBackPressed", new Class[] {});
      Thread.sleep(1800);
      waitFor(
          "root Back keeps live transport in background",
          () -> original.shell("echo kept", 5000).require().equals("kept"));
      foreground();
      waitFor(
          "foreground return reuses same connection",
          () -> client() == original && client().isConnected());
      ActivityMonitor monitor = addMonitor("cn.linkdeck.adb.MainActivity", null, false);
      runOnMainSync(() -> activity.recreate());
      Activity recreated = monitor.waitForActivityWithTimeout(30000);
      removeMonitor(monitor);
      if (recreated == null) throw new AssertionError("Activity recreation not observed");
      activity = recreated;
      waitFor(
          "activity recreation restores service-owned transport",
          () -> client() == original && client().isConnected());
      call("onBackPressed", new Class[] {});
      Thread.sleep(1000);
      original.close();
      foreground();
      waitFor(
          "lost background transport reconnects on return",
          () ->
              client() != null
                  && client() != original
                  && client().isConnected()
                  && !(Boolean) field("busy"));
      call("show", new Class[] {int.class}, 2);
      idle();
      waitFor(
          "application source actions visible",
          () ->
              find(activity.getWindow().getDecorView(), "选择手机应用") != null
                  && find(activity.getWindow().getDecorView(), "安装 APK") != null);
      Thread.sleep(2500);
      shot("v04-apps");
      tap("选择手机应用");
      waitFor("phone picker lists installed test app", () -> dialogHas("轻连测试目标"));
      Thread.sleep(1500);
      shot("v04-phone-picker");
      dialogTap("轻连测试目标");
      waitFor(
          "phone app compatibility inspection", () -> dialogHas("安装 / 更新确认") && dialogHas("来源：手机应用"));
      long installationStarted=System.currentTimeMillis();dialogTap("确认更新");awaitTask("安装或更新电视应用",installationStarted);dialogTap("知道了");idle();
      call("inspectPhoneApp", new Class[] {String.class}, "cn.linkdeck.splitqa");
      waitFor("installed split app inspected as complete set", () -> dialogHas("2 个文件"));
      installationStarted=System.currentTimeMillis();dialogTap("确认更新");awaitTask("安装或更新电视应用",installationStarted);dialogTap("知道了");idle();
      waitFor(
          "real split session install succeeds",
          () -> client().shell("pm path cn.linkdeck.splitqa").require().split("\n").length == 2);
      // Force an old-TV SDK without installing anything: incompatibility must block the action.
      set("deviceSdk", 22);
      call("inspectPhoneApp", new Class[] {String.class}, "cn.linkdeck.qatarget");
      waitFor(
          "old-TV version conflict blocks installation",
          () -> dialogHas("无法直接安装") && !dialogHas("安装到电视"));
      dialogTap("取消");
      set("deviceSdk", 28);
      // Simulate an old IP-only record for this synthetic target; require explicit review.
      client().shell("pm disable-user --user 0 cn.linkdeck.qatarget").require();
      android.content.SharedPreferences store=(android.content.SharedPreferences)field("prefs");store.edit().putStringSet("disabled:127.0.0.1",new java.util.HashSet<>(java.util.Arrays.asList("cn.linkdeck.qatarget"))).commit();
      java.lang.reflect.Method scope=activity.getClass().getDeclaredMethod("recoveryKey");scope.setAccessible(true);String scoped=(String)scope.invoke(activity);store.edit().remove(scoped).commit();
      call("recovery",new Class[]{});waitFor("old recovery history requires manual review",()->dialogHas("核对旧记录"));dialogTap("核对旧记录");waitFor("migration identifies weak old ownership",()->dialogHas("核对旧记录归属"));dialogTap("取消");waitFor("cancelled migration leaves old record intact",()->store.getStringSet("disabled:127.0.0.1",java.util.Collections.emptySet()).contains("cn.linkdeck.qatarget")&&store.getStringSet(scoped,java.util.Collections.emptySet()).isEmpty());
      call("recovery",new Class[]{});dialogTap("核对旧记录");long migrationStarted=System.currentTimeMillis();dialogTap("继续");awaitTask("核对旧恢复记录",migrationStarted);waitFor("confirmed migration attaches only actual disabled app",()->dialogHas("选择要重新启用的应用")&&store.getStringSet(scoped,java.util.Collections.emptySet()).contains("cn.linkdeck.qatarget"));dialogTap("cn.linkdeck.qatarget");long enabledAt=System.currentTimeMillis();dialogTap("继续");awaitTask("启用应用",enabledAt);waitFor("migrated app can be restored on the original device",()->!client().shell("pm list packages -d").require().contains("cn.linkdeck.qatarget"));
      call("show", new Class[] {int.class}, 0);
      idle();
      Thread.sleep(1800);
      shot("v04-home");
      tap("断开连接");
      waitFor(
          "manual disconnect clears automatic recovery intent",
          () ->
              !((android.content.SharedPreferences) field("prefs"))
                  .getBoolean("resume_connection", true));
      call("onBackPressed", new Class[] {});
      result.putString("stream", "UPGRADE QA PASSED: " + passes + " checks.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      try { shot("v04-failure"); } catch(Exception ignored) {}
      result.putString("stream", android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
