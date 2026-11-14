package cn.linkdeck.adb;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class ApkTransferChecks {
  static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  static final class Fake implements ApkTransfer.Device {
    List<String> calls = new ArrayList<>();
    boolean failWrite, failCommit, failPush;

    public void push(File file, String remote) throws Exception {
      calls.add("push " + remote);
      if (failPush) throw new IOException("transfer interrupted");
    }

    public String shell(String command) {
      calls.add(command);
      if (command.startsWith("pm install-create"))
        return "Success: created install session [123]\n";
      if (command.startsWith("pm install-write") && failWrite) return "Failure [WRITE_FAILED]";
      if (command.startsWith("pm install-commit") && failCommit) return "Failure [COMMIT_FAILED]";
      return "Success\n";
    }
  }

  static File apk(Path path, String abi) throws Exception {
    try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(path))) {
      z.putNextEntry(new ZipEntry(abi == null ? "assets/file" : "lib/" + abi + "/libqa.so"));
      z.write(new byte[] {1, 2, 3});
      z.closeEntry();
    }
    return path.toFile();
  }

  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("linkdeck-apk");
    File base = apk(dir.resolve("base.apk"), null),
        split = apk(dir.resolve("split.apk"), "arm64-v8a");
    List<File> files = Arrays.asList(base, split);
    Set<String> abis = ApkTransfer.abis(files);
    check(abis.contains("arm64-v8a"), "inspect native libraries in split");
    check(!ApkTransfer.compatible(21, 22, abis, "armeabi-v7a,armeabi"), "block wrong architecture");
    check(
        !ApkTransfer.compatible(26, 22, Collections.emptySet(), "armeabi-v7a"), "block wrong sdk");
    check(
        ApkTransfer.compatible(21, 22, Collections.emptySet(), "armeabi-v7a"),
        "pure Java legacy app");
    Fake ok = new Fake();
    ApkTransfer.install(files, ok);
    check(
        ok.calls.stream()
            .anyMatch(c -> c.startsWith("pm install-write -S ") && c.contains("123 split_1.apk")),
        "all splits written");
    check(ok.calls.stream().anyMatch(c -> c.equals("pm install-commit 123")), "commit atomically");
    check(ok.calls.stream().noneMatch(c -> c.startsWith("pm install-abandon")), "keep success");
    check(ok.calls.get(ok.calls.size() - 1).startsWith("rm -f "), "clean remote files");
    for (int fault = 0; fault < 3; fault++) {
      Fake broken = new Fake();
      broken.failWrite = fault == 0;
      broken.failCommit = fault == 1;
      broken.failPush = fault == 2;
      boolean failed = false;
      try {
        ApkTransfer.install(files, broken);
      } catch (IOException e) {
        failed = true;
      }
      check(failed, "propagate failure");
      if (fault != 2)
        check(broken.calls.contains("pm install-abandon 123"), "abandon incomplete session");
      check(
          broken.calls.get(broken.calls.size() - 1).startsWith("rm -f "), "clean failed transfer");
    }
    Fake single = new Fake();
    ApkTransfer.install(Collections.singletonList(base), single);
    check(
        single.calls.stream().anyMatch(c -> c.startsWith("pm install -r ")),
        "single package direct install");
    check(base.exists() && split.exists(), "never delete phone application files");
    for (File file : files) file.delete();
    Files.delete(dir);
    System.out.println(
        "PASS: single/split transfer; actual ABI scan across splits; SDK and ABI rejection; session"
            + " commit/abandon; cleanup on push/write/commit errors; phone source files preserved");
  }
}
