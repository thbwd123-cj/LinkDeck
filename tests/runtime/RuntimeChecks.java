package cn.linkdeck.qa;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import cn.linkdeck.adb.AdbClient;
import cn.linkdeck.adb.AppCatalog;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public class RuntimeChecks extends Instrumentation {
  Activity activity;
  int passes = 0;
  boolean connectionOnly, connectedAudit;

  interface Check {
    boolean ok() throws Exception;
  }

  Object field(String name) throws Exception {
    Field f = activity.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(activity);
  }

  void set(String name, Object value) throws Exception {
    Field f = activity.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(activity, value);
  }

  void call(String name, Class<?>[] types, Object... args) throws Exception {
    Method m = activity.getClass().getDeclaredMethod(name, types);
    m.setAccessible(true);
    final Throwable[] e = {null};
    runOnMainSync(
        () -> {
          try {
            m.invoke(activity, args);
          } catch (Throwable t) {
            e[0] = t;
          }
        });
    if (e[0] != null) throw new Exception(e[0]);
  }

  void waitFor(String name, Check check) throws Exception {
    long end = System.currentTimeMillis() + 55000;
    while (System.currentTimeMillis() < end) {
      if (check.ok()) {
        passes++;
        Bundle b = new Bundle();
        b.putString("stream", "PASS: " + name + "\n");
        sendStatus(0, b);
        return;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("Timeout: " + name);
  }

  View find(View root, String text) {
    if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return root;
    if (root instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) root;
      for (int i = 0; i < g.getChildCount(); i++) {
        View v = find(g.getChildAt(i), text);
        if (v != null) return v;
      }
    }
    return null;
  }

  void tap(String text) throws Exception {
    final boolean[] found = {false};
    runOnMainSync(
        () -> {
          View v = find(activity.getWindow().getDecorView(), text);
          while (v != null) {
            if (v instanceof android.view.ViewGroup) {
              String[] tabs={"设备","遥控","应用","文件","工具"};
              for(int i=0;i<tabs.length;i++) if(tabs[i].equals(text)) {
                View hit=v.findViewWithTag("nav-tab-"+i);
                if(hit!=null&&hit.isShown()){hit.performClick();found[0]=true;return;}
              }
            }
            if (v.isClickable()) {
              v.performClick();
              found[0] = true;
              return;
            }
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
          }
        });
    if (!found[0]) throw new AssertionError("Missing button " + text);
  }

  AccessibilityNodeInfo appRoot(){
    android.app.UiAutomation automation=getUiAutomation();android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();
    if((info.flags&android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS)==0){info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);}
    android.view.accessibility.AccessibilityWindowInfo top=null;for(android.view.accessibility.AccessibilityWindowInfo window:automation.getWindows())if(window.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION&&(top==null||window.getLayer()>top.getLayer()))top=window;
    return top==null?automation.getRootInActiveWindow():top.getRoot();
  }

  boolean dialogHas(String text) {
    AccessibilityNodeInfo root = appRoot();
    return root != null && !root.findAccessibilityNodeInfosByText(text).isEmpty();
  }

  void dialogTap(String text) throws Exception {
    long end = System.currentTimeMillis() + 15000;
    while (System.currentTimeMillis() < end) {
      AccessibilityNodeInfo r = appRoot();
      if (r != null)
        for (AccessibilityNodeInfo n : r.findAccessibilityNodeInfosByText(text)) {
          if (!text.contentEquals(n.getText())) continue;
          while (n != null) {
            if (n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            n = n.getParent();
          }
        }
      Thread.sleep(200);
    }
    AccessibilityNodeInfo active=getUiAutomation().getRootInActiveWindow();Bundle debug=new Bundle();debug.putString("stream","A11Y action failed: "+text+" root="+describeNode(active,0)+"\n");sendStatus(0,debug);
    throw new AssertionError("Missing dialog action " + text);
  }

  String describeNode(AccessibilityNodeInfo node,int depth){if(node==null)return "null";if(depth>6)return "";StringBuilder result=new StringBuilder("["+node.getClassName()+":"+node.getText()+" click="+node.isClickable()+" enabled="+node.isEnabled()+" visible="+node.isVisibleToUser()+"]");for(int i=0;i<node.getChildCount();i++)result.append(describeNode(node.getChild(i),depth+1));return result.toString();}

  void awaitTask(String name,long after)throws Exception{waitFor("current task completed: "+name,()->{org.json.JSONArray a=((cn.linkdeck.adb.TaskJournal)field("journal")).snapshot();for(int i=0;i<a.length();i++){org.json.JSONObject t=a.getJSONObject(i);if(t.optString("name").equals(name)&&t.optLong("created")>=after&&!cn.linkdeck.adb.TaskJournal.active(t)){if(!t.optString("state").equals("success"))throw new AssertionError(t.toString());return true;}}return false;});}

  void idle() throws Exception {
    waitFor("task queue drained", () -> !(Boolean) field("busy"));
  }

  AdbClient client() throws Exception {
    return (AdbClient) field("adb");
  }

  void shot(String name) throws Exception {
    Thread.sleep(250);
    android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
    for(int attempt=0;bitmap==null&&attempt<4;attempt++){Thread.sleep(300);bitmap=getUiAutomation().takeScreenshot();}
    if(bitmap==null){android.os.ParcelFileDescriptor capture=getUiAutomation().executeShellCommand("screencap -p");try(InputStream source=new android.os.ParcelFileDescriptor.AutoCloseInputStream(capture)){bitmap=android.graphics.BitmapFactory.decodeStream(source);}}
    if (bitmap == null) throw new AssertionError("Screenshot unavailable after bounded platform retries and native capture");
    try (OutputStream out =
        new FileOutputStream(
            new File(getTargetContext().getExternalFilesDir(null), name + ".png"))) {
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }

  void connectionFeedback() throws Exception {
    runOnMainSync(() -> {
      try {
        ((EditText)field("hostInput")).setText("127.0.0.1");
        ((EditText)field("portInput")).setText("1");
      } catch(Exception e) {throw new RuntimeException(e);}
    });
    tap("连接设备");
    waitFor("connection refusal displays target and network stage",
        () -> !(Boolean)field("busy") && dialogHas("阶段：网络连接") && dialogHas("127.0.0.1:1"));
    dialogTap("知道了");
    try(java.net.ServerSocket server = new java.net.ServerSocket(0)) {
      Thread peer = new Thread(() -> {
        try(java.net.Socket socket = server.accept()) {
          socket.setSoTimeout(15000);
          while(socket.getInputStream().read() != -1) {}
        } catch(Exception ignored) {}
      });
      peer.setDaemon(true);
      peer.start();
      runOnMainSync(() -> {
        try {((EditText)field("portInput")).setText(String.valueOf(server.getLocalPort()));}
        catch(Exception e){throw new RuntimeException(e);}
      });
      tap("连接设备");
      waitFor("waiting connection shows stage and elapsed seconds",
          () -> dialogHas("端口已连通") && dialogHas("已等待"));
      dialogTap("取消连接");
      waitFor("cancel closes waiting transport and clears busy dialog",
          () -> !(Boolean)field("busy") && field("connectionDialog") == null);
      peer.join(2000);
      if(peer.isAlive())throw new AssertionError("Cancelled socket still open");
    }
  }

  void offlineScreens() throws Exception {
    tap("遥控");
    tap("红外遥控");
    waitFor("IR mode is available without ADB", () ->
        find(activity.getWindow().getDecorView(), "导入 .ir 遥控码") != null
            && find(activity.getWindow().getDecorView(), "方向控制") != null
            && find(activity.getWindow().getDecorView(), "音量") != null
            && find(activity.getWindow().getDecorView(), "键盘输入") == null
            && client() == null && !activity.isFinishing());
    tap("网络 ADB");
    waitFor(
        "offline remote shows real controls",
        () ->
            find(activity.getWindow().getDecorView(), "方向控制") != null
                && find(activity.getWindow().getDecorView(), "发送到设备") != null);
    shot("qa-remote-offline");
    tap("确定");
    waitFor("offline remote is connection-gated", () -> dialogHas("连接后即可操作"));
    dialogTap("继续浏览");
    tap("应用");
    waitFor(
        "offline apps page has local APK inspection",
        () ->
            find(activity.getWindow().getDecorView(), "安装 APK") != null
                && find(activity.getWindow().getDecorView(), "应用列表待连接后读取") != null);
    shot("qa-apps-offline");
    File apk = new File(getTargetContext().getCacheDir(), "offline-qa.apk");
    try (InputStream in = getContext().getAssets().open("target.apk");
        OutputStream out = new FileOutputStream(apk)) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }
    call("inspectApk", new Class[] {Uri.class}, Uri.fromFile(apk));
    waitFor(
        "local APK readable without connecting",
        () -> dialogHas("暂不能完整判断兼容性") && dialogHas("连接后安装"));
    dialogTap("取消");
    apk.delete();
    tap("文件");
    waitFor(
        "offline files page shows directory shortcuts",
        () ->
            find(activity.getWindow().getDecorView(), "下载目录") != null
                && find(activity.getWindow().getDecorView(), "文件列表待连接后读取") != null);
    shot("qa-files-offline");
    tap("下载目录");
    waitFor(
        "offline directory selection retained", () -> "/sdcard/Download".equals(field("folder")));
    tap("上传文件");
    waitFor("offline transfer does not open a file picker", () -> dialogHas("连接后即可操作"));
    dialogTap("继续浏览");
    set("folder", "/sdcard");
    tap("工具");
    waitFor(
        "offline tools show terminal and records",
        () ->
            find(activity.getWindow().getDecorView(), "命令终端") != null
                && find(activity.getWindow().getDecorView(), "操作记录") != null);
    shot("qa-tools-offline");
    runOnMainSync(
        () -> {
          try {
            ((EditText) field("commandInput")).setText("getprop ro.product.model");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    tap("遥控");
    tap("工具");
    waitFor(
        "command draft survives tab changes",
        () ->
            "getprop ro.product.model".contentEquals(((EditText) field("commandInput")).getText()));
    call(
        "saveShortcut",
        new Class[] {String.class, String.class},
        "离线检查",
        "getprop ro.product.model");
    waitFor(
        "shortcuts can be saved offline",
        () ->
            ((android.content.SharedPreferences) field("prefs"))
                .getString("shortcuts", "[]")
                .contains("离线检查"));
    tap("遥控");
    tap("确定");
    dialogTap("去连接");
    waitFor(
        "connection request remembers originating tab",
        () -> ((Integer) field("page")) == 0 && ((Integer) field("afterConnectPage")) == 1);
  }

  EditText firstEdit(View root) {
    if (root instanceof EditText) return (EditText) root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        EditText result = firstEdit(group.getChildAt(i));
        if (result != null) return result;
      }
    }
    return null;
  }

  Object targetRow() throws Exception {
    for (Object item : (ArrayList<?>) field("apps")) {
      Field p = item.getClass().getDeclaredField("pkg");
      p.setAccessible(true);
      if ("cn.linkdeck.qatarget".equals(p.get(item))) return item;
    }
    return null;
  }

  String rowString(Object row, String name) throws Exception {
    Field f = row.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return (String) f.get(row);
  }

  void catalogChecks() throws Exception {
    waitFor(
        "real application label from target system",
        () ->
            targetRow() != null
                && "轻连测试目标".equals(rowString(targetRow(), "name"))
                && !(Boolean) field("metadataLoading"));
    runOnMainSync(() -> firstEdit(activity.getWindow().getDecorView()).setText("轻连测试目标"));
    waitFor(
        "search by actual application name",
        () ->
            find(activity.getWindow().getDecorView(), "轻连测试目标") != null
                && ((Map<?, ?>) field("iconViews")).size() == 1);
    // Icons are intentionally lazy: scroll the selected app into view before expecting I/O.
    android.view.View icon = (android.view.View)((Map<?,?>)field("iconViews")).get("cn.linkdeck.qatarget");
    runOnMainSync(() -> icon.requestRectangleOnScreen(new android.graphics.Rect(0,0,icon.getWidth(),icon.getHeight()),true));
    Object target = targetRow();
    String deviceKey = (String) field("catalogDevice"), stamp = rowString(target, "stamp");
    AppCatalog store = (AppCatalog) field("catalog");
    String key = store.iconKey(deviceKey, "cn.linkdeck.qatarget", stamp);
    waitFor(
        "real application icon loaded and cached",
        () ->
            store.peek(key) != null
                && new File(getTargetContext().getCacheDir(), "catalog-icons/" + key + ".png")
                    .isFile());
    call("toggleFavorite", new Class[] {target.getClass()}, target);
    waitFor(
        "favorite persisted",
        () ->
            ((Set<?>) field("favorites")).contains("cn.linkdeck.qatarget")
                && getTargetContext()
                    .getSharedPreferences("linkdeck", 0)
                    .getStringSet(
                        "favorites:" + AppCatalog.digest(deviceKey), Collections.emptySet())
                    .contains("cn.linkdeck.qatarget"));
    set("onlyFavorites", true);
    runOnMainSync(() -> firstEdit(activity.getWindow().getDecorView()).setText(""));
    call("renderApps", new Class[] {});
    waitFor(
        "favorite filter shows selected application",
        () ->
            ((Map<?, ?>) field("iconViews")).size() == 1
                && ((Map<?, ?>) field("iconViews")).containsKey("cn.linkdeck.qatarget"));
    shot("qa-catalog-favorite");
    set("onlyFavorites", false);
    call("show", new Class[] {int.class}, 0);
    tap("重新连接");
    waitFor(
        "favorites restored after reconnect",
        () ->
            client() != null
                && client().isConnected()
                && !(Boolean) field("busy")
                && ((Set<?>) field("favorites")).contains("cn.linkdeck.qatarget"));
    tap("应用");
    waitFor(
        "application list reloaded after reconnect",
        () -> (Boolean) field("appLoaded") && !(Boolean) field("metadataLoading"));
    waitFor(
        "new launcher uses adaptive icon",
        () ->
            getTargetContext().getPackageManager().getApplicationIcon("cn.linkdeck.adb")
                instanceof android.graphics.drawable.AdaptiveIconDrawable);
  }

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    getTargetContext().getSharedPreferences("linkdeck",0).edit().putString("connect_mode","legacy").commit();
    connectionOnly = b != null && b.getString("connectionOnly", "false").equals("true");
    connectedAudit = b != null && b.getString("connectedAudit", "false").equals("true");
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      Intent launch =
          new Intent()
              .setClassName("cn.linkdeck.adb", "cn.linkdeck.adb.MainActivity")
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
      activity = startActivitySync(launch);
      waitFor(
          "native home activity rendered",
          () -> find(activity.getWindow().getDecorView(), "连接设备") != null);
      if (!connectedAudit) connectionFeedback();
      if (!connectionOnly && !connectedAudit) offlineScreens();
      if (connectedAudit) set("afterConnectPage", 1);
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
          "App connects to real adbd",
          () -> client() != null && client().isConnected() && !(Boolean) field("busy"));
      if (connectionOnly) {
        waitFor("success dismisses connection progress dialog", () -> field("connectionDialog") == null);
        result.putString("stream", "CONNECTION UI QA PASSED: " + passes + " checks.\n");
        finish(Activity.RESULT_OK, result);
        return;
      }
      waitFor("connection returns to originating tab", () -> ((Integer) field("page")) == 1);
      tap("遥控");
      tap("返回");
      Thread.sleep(
          1000); // target is this emulator; Back must return to home without destroying connection.
      waitFor(
          "remote Back preserves activity and connection",
          () -> !activity.isFinishing() && client().isConnected());
      call("show", new Class[] {int.class}, 1);
      tap("截取设备屏幕");
      waitFor("native screenshot preview", () -> dialogHas("当前屏幕"));
      dialogTap("关闭");
      idle();
      tap("应用");
      waitFor(
          "native applications page loads",
          () -> (Boolean) field("appLoaded") && !(Boolean) field("busy"));
      catalogChecks();
      ArrayList<?> apps = (ArrayList<?>) field("apps");
      Object target = null;
      for (Object a : apps) {
        Field p = a.getClass().getDeclaredField("pkg");
        p.setAccessible(true);
        if ("cn.linkdeck.qatarget".equals(p.get(a))) target = a;
      }
      if (target == null) throw new AssertionError("QA target missing");
      call("setEnabled", new Class[] {target.getClass(), boolean.class}, target, false);
      idle();
      waitFor(
          "native disable action applied",
          () -> client().shell("pm list packages -d").require().contains("cn.linkdeck.qatarget"));
      call("recovery", new Class[] {});
      dialogTap("cn.linkdeck.qatarget");
      dialogTap("继续");
      idle();
      waitFor(
          "recovery enables QA target",
          () -> !client().shell("pm list packages -d").require().contains("cn.linkdeck.qatarget"));
      call("show", new Class[] {int.class}, 3);
      idle();
      waitFor(
          "file page rendered", () -> find(activity.getWindow().getDecorView(), "上传文件") != null);
      byte[] bytes = new byte[170321];
      for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i % 251);
      File source = new File(getTargetContext().getCacheDir(), "qa-upload.bin");
      try (FileOutputStream out = new FileOutputStream(source)) {
        out.write(bytes);
      }
      call(
          "onActivityResult",
          new Class[] {int.class, int.class, Intent.class},
          101,
          Activity.RESULT_OK,
          new Intent().setData(Uri.fromFile(source)));
      dialogTap("继续");
      idle();
      String remote = "/sdcard/qa-upload.bin";
      ByteArrayOutputStream pulled = new ByteArrayOutputStream();
      client().pull(remote, pulled, null);
      waitFor(
          "native file upload handler preserves bytes",
          () -> Arrays.equals(bytes, pulled.toByteArray()));
      File dest = new File(getTargetContext().getCacheDir(), "qa-download.bin");
      set("saveRemote", remote);
      call(
          "onActivityResult",
          new Class[] {int.class, int.class, Intent.class},
          103,
          Activity.RESULT_OK,
          new Intent().setData(Uri.fromFile(dest)));
      idle();
      byte[] saved = new byte[(int) dest.length()];
      try (DataInputStream in = new DataInputStream(new FileInputStream(dest))) {
        in.readFully(saved);
      }
      waitFor("native download handler preserves bytes", () -> Arrays.equals(bytes, saved));
      client().shell("rm -f " + AdbClient.quote(remote));
      source.delete();
      dest.delete();
      File apk = new File(getTargetContext().getCacheDir(), "qa-target.apk");
      try (InputStream in = getContext().getAssets().open("target.apk");
          OutputStream out = new FileOutputStream(apk)) {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
      }
      call("inspectApk", new Class[] {Uri.class}, Uri.fromFile(apk));
      waitFor("native APK compatibility dialog", () -> dialogHas("安装前检查"));
      dialogTap("安装到设备");
      idle();
      waitFor(
          "native APK install result verified",
          () -> {
            org.json.JSONArray logs =
                new org.json.JSONArray(
                    ((android.content.SharedPreferences) field("prefs")).getString("logs", "[]"));
            for (int i = 0; i < logs.length(); i++) {
              org.json.JSONObject o = logs.getJSONObject(i);
              if (o.optString("action").equals("传输并安装 APK")) return o.optBoolean("ok");
            }
            return false;
          });
      apk.delete();
      tap("工具");
      call("displaySettings", new Class[] {});
      waitFor("display settings dialog opens", () -> dialogHas("恢复设备默认显示"));
      dialogTap("关闭");
      call("show", new Class[] {int.class}, 1);
      call("onBackPressed", new Class[] {});
      waitFor(
          "Back navigates to device page",
          () -> ((Integer) field("page")) == 0 && client().isConnected());
      tap("断开连接");
      waitFor("disconnect stays in app", () -> client() == null
          && !activity.isFinishing() && ((Integer) field("page")) == 0);
      result.putString("stream", "RUNTIME QA PASSED: " + passes + " checks.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable t) {
      result.putString("stream", android.util.Log.getStackTraceString(t));
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
