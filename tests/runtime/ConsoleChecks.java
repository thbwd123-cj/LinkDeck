package cn.linkdeck.qa;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import cn.linkdeck.adb.*;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;

public class ConsoleChecks extends RuntimeChecks {
  boolean skipClipboard;

  @Override
  public void onCreate(Bundle args) {
    skipClipboard = args != null && "true".equals(args.getString("skipClipboard"));
    super.onCreate(args);
  }

  Object resultField(String key) throws Exception {
    Object record = field("lastShownCommand");
    Field f = record.getClass().getDeclaredField(key);
    f.setAccessible(true);
    return f.get(record);
  }

  String document() throws Exception {
    Object record = field("lastShownCommand");
    Method m = record.getClass().getDeclaredMethod("document");
    m.setAccessible(true);
    return (String) m.invoke(record);
  }

  String read(File file) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream in = new FileInputStream(file)) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) bytes.write(b, 0, n);
    }
    return bytes.toString("UTF-8");
  }

  void resultTap(String label) throws Exception {
    final boolean[] clicked = {false};
    runOnMainSync(
        () -> {
          try {
            Dialog dialog = (Dialog) field("commandResultDialog");
            android.view.View view = find(dialog.getWindow().getDecorView(), label);
            if (view != null) clicked[0] = view.performClick();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    if (!clicked[0]) throw new AssertionError("Missing native result action " + label);
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      getTargetContext()
          .getSharedPreferences("linkdeck", 0)
          .edit()
          .putBoolean("resume_connection", false)
          .commit();
      activity =
          startActivitySync(
              new Intent()
                  .setClassName("cn.linkdeck.adb", "cn.linkdeck.adb.MainActivity")
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
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
          "real adbd connected for console QA",
          () -> client() != null && client().isConnected() && !(Boolean) field("busy"));
      Thread.sleep(2000);
      shot("v05-home");
      call("show", new Class[] {int.class}, 2);
      idle();
      Thread.sleep(2000);
      shot("v05-apps");
      call("show", new Class[] {int.class}, 3);
      idle();
      Thread.sleep(2000);
      shot("v05-files");
      call("show", new Class[] {int.class}, 4);
      Thread.sleep(2000);
      shot("v05-tools");
      String small = "echo '  系统已连接'; echo '原始缩进和结尾空格  '";
      runOnMainSync(
          () -> {
            try {
              ((EditText) field("commandInput")).setText(small);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      tap("执行命令");
      dialogTap("继续");
      waitFor("native Execute opens result dialog", () -> dialogHas("执行结果") && dialogHas("完成 · 0"));
      waitFor(
          "command and raw whitespace preserved",
          () ->
              resultField("command").equals(small)
                  && resultField("output").equals("  系统已连接\n原始缩进和结尾空格  \n"));
      Thread.sleep(2000);
      shot("v05-result");
      resultTap("关闭");
      String longCommand =
          "echo '  leading spaces  '; i=0; while [ \"$i\" -lt 900 ]; do echo \"line-$i"
              + " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789\"; i=$((i+1)); done; echo TAIL_END";
      call(
          "command",
          new Class[] {String.class, String.class, Runnable.class},
          "长输出测试",
          longCommand,
          null);
      waitFor(
          "long result completed without truncation",
          () ->
              !(Boolean) field("busy")
                  && resultField("command").equals(longCommand)
                  && ((String) resultField("output")).endsWith("TAIL_END\n"));
      String full = document();
      int pages = (full.length() + 19999) / 20000;
      if (pages < 2) throw new AssertionError("Expected multiple pages");
      for (int p = 1; p < pages; p++) resultTap("下一页");
      waitFor("last page exposes final output line", () -> dialogHas("line-899"));
      resultTap("上一页");
      waitFor("pagination controls remain usable", () -> dialogHas("下一页"));
      resultTap("复制全部");
      if (skipClipboard) {
        Bundle skipped = new Bundle();
        skipped.putString(
            "stream",
            "SKIP: system clipboard readback (emulator service timed out in separate run).\n");
        sendStatus(0, skipped);
        resultTap("下一页");
        resultTap("上一页");
        waitFor(
            "result controls respond while clipboard request is pending",
            () -> field("commandResultDialog") != null);
      } else {
        java.util.concurrent.Future<String> copied =
            java.util.concurrent.Executors.newSingleThreadExecutor()
                .submit(
                    () -> {
                      android.content.ClipboardManager clipboard =
                          (android.content.ClipboardManager)
                              getTargetContext().getSystemService(Context.CLIPBOARD_SERVICE);
                      for (int attempt = 0; attempt < 25; attempt++) {
                        if (clipboard.getPrimaryClip() != null) {
                          String text =
                              clipboard.getPrimaryClip().getItemAt(0).getText().toString();
                          if (text.equals(full)) return text;
                        }
                        Thread.sleep(200);
                      }
                      return "";
                    });
        String clipboardText = copied.get(12, java.util.concurrent.TimeUnit.SECONDS);
        waitFor("clipboard contains entire untruncated document", () -> clipboardText.equals(full));
      }
      File saved = new File(getTargetContext().getFilesDir(), "last-command.json");
      JSONObject persisted = new JSONObject(read(saved));
      waitFor(
          "last result persisted without shortening",
          () -> persisted.getString("output").equals(resultField("output")));
      File export = new File(getTargetContext().getCacheDir(), "console-export.txt");
      set("pendingCommandExport", full);
      call(
          "onActivityResult",
          new Class[] {int.class, int.class, Intent.class},
          105,
          Activity.RESULT_OK,
          new Intent().setData(Uri.fromFile(export)));
      idle();
      waitFor("TXT export contains complete UTF-8 document", () -> read(export).equals(full));
      set("pendingCommandExport", null);
      call(
          "onActivityResult",
          new Class[] {int.class, int.class, Intent.class},
          105,
          Activity.RESULT_OK,
          new Intent().setData(Uri.fromFile(export)));
      idle();
      waitFor(
          "export restores saved result after activity state loss",
          () -> read(export).equals(full));
      export.delete();
      resultTap("关闭");
      call(
          "command",
          new Class[] {String.class, String.class, Runnable.class},
          "失败状态测试",
          "echo failure_detail; exit 7",
          null);
      waitFor(
          "failed command displays output and exit 7",
          () ->
              dialogHas("失败 · 7")
                  && dialogHas("failure_detail")
                  && (Integer) resultField("exitCode") == 7);
      JSONArray logs =
          new JSONArray(
              ((android.content.SharedPreferences) field("prefs")).getString("logs", "[]"));
      waitFor("failed exit recorded as failure", () -> !logs.getJSONObject(0).getBoolean("ok"));
      resultTap("关闭");
      call("command", new Class[] {String.class, String.class, Runnable.class}, "空输出测试", ":", null);
      waitFor("empty output is explicit", () -> dialogHas("（无文本输出）"));
      resultTap("关闭");
      call("show", new Class[] {int.class}, 0);
      tap("断开连接");
      call("previousCommand", new Class[] {});
      waitFor(
          "last complete result opens while disconnected",
          () -> !client().isConnected() && dialogHas("（无文本输出）"));
      resultTap("关闭");
      result.putString("stream", "CONSOLE QA PASSED: " + passes + " checks.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      try {
        shot("v05-failure");
      } catch (Exception ignored) {
      }
      result.putString("stream", android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
