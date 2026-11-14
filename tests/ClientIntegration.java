package cn.linkdeck.adb;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class ClientIntegration {
  static void check(boolean b, String msg) {
    if (!b) throw new AssertionError(msg);
  }

  public static void main(String[] args) throws Exception {
    check(
        DisplayState.restoreCommand(
                "Physical size: 1920x1080\n"
                    + "Override size: 1280x720\n"
                    + "Physical density: 240\n"
                    + "Override density: 180")
            .equals("wm size 1280x720 && wm density 180"),
        "preserve old display overrides");
    check(
        DisplayState.restoreCommand("Physical size: 1920x1080\nPhysical density: 240")
            .equals("wm size reset && wm density reset"),
        "restore default display");
    check(DisplayState.restoreCommand("unknown") == null, "refuse unparseable restore state");
    AdbIdentity identity = new AdbIdentity(new File(args[1]));
    try (AdbClient c = new AdbClient()) {
      c.connect("127.0.0.1", Integer.parseInt(args[0]), identity);
      if (args.length > 2 && args[2].equals("bad"))
        throw new AssertionError("Invalid packet accepted");
      check(c.isConnected(), "connection");
      check(c.shell("echo hello").require().equals("hello"), "shell marker parsing");
      AdbClient.Result r = c.shell("false");
      check(r.exitCode == 7, "exit status");
      boolean raised = false;
      try {
        r.require();
      } catch (IOException e) {
        raised = true;
      }
      check(raised, "exit failure must throw");
      ExecutorService pool = Executors.newFixedThreadPool(2);
      Future<String> a = pool.submit(() -> c.shell("echo alpha").require());
      Future<String> b = pool.submit(() -> c.shell("echo beta").require());
      check(a.get().equals("alpha") && b.get().equals("beta"), "multiplexed channels");
      pool.shutdown();
      byte[] bytes = new byte[150123];
      for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i % 251);
      c.push(new ByteArrayInputStream(bytes), "/sdcard/测试 file.bin", null);
      List<AdbClient.Entry> entries = c.list("/sdcard");
      check(entries.size() == 2, "directory size");
      check(entries.get(0).directory(), "directories first");
      check(entries.get(1).name.equals("测试 file.bin"), "unicode filename");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      c.pull("/sdcard/测试 file.bin", out, null);
      check(Arrays.equals(bytes, out.toByteArray()), "binary sync roundtrip");
      boolean denied = false;
      try {
        c.pull("/protected", new ByteArrayOutputStream(), null);
      } catch (IOException e) {
        denied = e.getMessage().equals("permission denied");
      }
      check(denied, "sync FAIL propagation");
      byte[] shot = c.screenshot();
      check(shot.length == 12 && shot[0] == (byte) 137, "binary screenshot");
      check(AdbClient.quote("a'b").equals("'a'\\''b'"), "shell escaping");
      System.out.println(
          "PASS: AUTH, key persistence, shell, nonzero exit, channel multiplexing, sync"
              + " push/list/pull, UTF-8, binary integrity, permission error, screenshot, shell"
              + " quoting");
    } catch (IOException e) {
      if (args.length > 2 && args[2].equals("bad")) {
        System.out.println("PASS: invalid checksum rejected");
        return;
      }
      throw e;
    }
  }
}
