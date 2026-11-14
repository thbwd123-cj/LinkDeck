package cn.linkdeck.adb;

import java.io.*;
import java.util.*;

/** Optional integration check against an isolated Android emulator, never a user device. */
public final class AndroidDeviceProbe {
  private static void check(boolean good, String message) {
    if (!good) throw new AssertionError(message);
    System.out.println("PASS: " + message);
  }

  public static void main(String[] args) throws Exception {
    try (AdbClient c = new AdbClient()) {
      c.connect(args[0], Integer.parseInt(args[1]), new AdbIdentity(new File(args[2])));
      check(c.shell("getprop ro.build.version.sdk").require().equals("28"), "real Android 9 adbd handshake and shell");
      String path = "/sdcard/Download/LinkDeck-QA-" + UUID.randomUUID() + ".bin";
      byte[] data = new byte[220123];
      for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 253);
      try {
        c.push(new ByteArrayInputStream(data), path, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        c.pull(path, out, null);
        check(Arrays.equals(data, out.toByteArray()), "real adbd 220123-byte sync roundtrip");
        check(c.list("/sdcard/Download").stream().anyMatch(e -> path.endsWith(e.name)), "real adbd directory enumeration");
        byte[] png = c.screenshot();
        check(png.length > 8 && png[0] == (byte) 137 && png[1] == 80, "real Android PNG screenshot");
        check(c.shell("false").exitCode != 0, "real Android command failure status");
      } finally {
        c.shell("rm -f " + AdbClient.quote(path));
      }
    }
    System.out.println("ANDROID DEVICE PROBE PASSED");
  }
}
