package cn.linkdeck.adb;

import android.util.AtomicFile;
import java.io.*;
import org.json.*;

/** The latest complete command result stays in app-private storage. */
final class CommandRecord {
  String title, command, target, time, output;
  String failureReason="";
  int exitCode;
  long elapsed;

  String document() {
    return "设备："
        + target
        + "\n时间："
        + time
        + "\n执行状态："
        + (exitCode < 0 ? "结果未确认（无有效退出码）" : "退出码 " + exitCode)
        + " · 用时 "
        + elapsed
        + " ms\n"
        + (failureReason.isEmpty() ? "" : "说明："+failureReason+"\n以下仅为已收到的文本，不代表完整结果。\n")
        + "\n$ "
        + command
        + "\n\n"
        + (output.isEmpty() ? "（无文本输出）" : output);
  }

  void save(File file) throws Exception {
    JSONObject json = new JSONObject();
    json.put("title", title);
    json.put("command", command);
    json.put("target", target);
    json.put("time", time);
    json.put("output", output);
    json.put("exit", exitCode);
    json.put("failureReason",failureReason);
    json.put("elapsed", elapsed);
    AtomicFile atomic = new AtomicFile(file);
    FileOutputStream out = null;
    try {
      out = atomic.startWrite();
      out.write(json.toString().getBytes("UTF-8"));
      atomic.finishWrite(out);
    } catch (Exception e) {
      if (out != null) atomic.failWrite(out);
      throw e;
    }
  }

  static CommandRecord read(File file) throws Exception {
    AtomicFile atomic = new AtomicFile(file);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream in = atomic.openRead()) {
      byte[] buffer = new byte[65536];
      int n;
      while ((n = in.read(buffer)) != -1) {
        bytes.write(buffer, 0, n);
        if (bytes.size() > 40 * 1024 * 1024) throw new IOException("结果文件超出读取上限");
      }
    }
    JSONObject j = new JSONObject(bytes.toString("UTF-8"));
    CommandRecord r = new CommandRecord();
    r.title = j.getString("title");
    r.command = j.getString("command");
    r.target = j.getString("target");
    r.time = j.getString("time");
    r.output = j.getString("output");
    r.exitCode = j.getInt("exit");
    r.failureReason = j.optString("failureReason", "");
    r.elapsed = j.getLong("elapsed");
    return r;
  }
}
