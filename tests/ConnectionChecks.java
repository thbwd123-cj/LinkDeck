package cn.linkdeck.adb;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

public class ConnectionChecks {
  public static void main(String[] args) throws Exception {
    String mode = args[2];
    AdbClient client = new AdbClient();
    List<String> stages = new ArrayList<>();
    AdbIdentity identity = new AdbIdentity(new File(args[1]));
    long start = System.nanoTime();
    try {
      client.connect(
          "127.0.0.1",
          Integer.parseInt(args[0]),
          identity,
          stage -> {
            stages.add(stage);
            if (stage.contains("验证授权") && mode.equals("timeout")) {
              try {
                Field f = AdbClient.class.getDeclaredField("handshakeDeadline");
                f.setAccessible(true);
                f.setLong(client, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(150));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
            if (stage.contains("验证授权") && mode.equals("cancel")) client.close();
          });
      throw new AssertionError("Unexpected success");
    } catch (AdbClient.ConnectionFailure e) {
      String m = e.getMessage();
      if (!m.contains("目标：127.0.0.1:") || client.isConnected()) throw new AssertionError(m);
      String expected =
          mode.equals("refused")
              ? "阶段：网络连接"
              : mode.equals("timeout")
                  ? "电视授权等待超时"
                  : mode.equals("tls")
                      ? "连接向导"
                      : mode.equals("eof")
                          ? "电视关闭了 ADB 连接"
                          : mode.equals("protocol") ? "兼容的 ADB 协议" : "阶段：电视授权";
      if (!m.contains(expected)) throw new AssertionError(m);
      if (mode.equals("timeout") && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) > 3000)
        throw new AssertionError("Deadline not bounded");
      if (stages.size() < (mode.equals("refused") ? 1 : 2)) throw new AssertionError("No progress");
      System.out.println("PASS: " + mode + " stage, error detail, closed socket, progress");
    } finally {
      client.close();
    }
  }
}
