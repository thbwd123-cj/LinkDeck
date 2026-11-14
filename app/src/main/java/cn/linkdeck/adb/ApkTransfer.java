package cn.linkdeck.adb;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Single or split APK transfer. Never deletes phone application files. */
public final class ApkTransfer {
  public interface Device {
    String shell(String command) throws Exception;

    void push(File file, String remote) throws Exception;
  }

  public static Set<String> abis(List<File> files) throws IOException {
    Set<String> result = new TreeSet<>();
    for (File file : files)
      try (ZipFile zip = new ZipFile(file)) {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
          String name = entries.nextElement().getName();
          if (name.startsWith("lib/") && name.endsWith(".so")) {
            String[] parts = name.split("/");
            if (parts.length > 2) result.add(parts[1]);
          }
        }
      }
    return result;
  }

  public static boolean compatible(int minimum, int sdk, Set<String> abis, String deviceAbis) {
    if (sdk > 0 && minimum > sdk) return false;
    if (abis.isEmpty() || deviceAbis == null || deviceAbis.isEmpty()) return true;
    for (String abi : deviceAbis.split(",")) if (abis.contains(abi.trim())) return true;
    return false;
  }

  public static void install(List<File> files, Device device) throws Exception {install(files,device,false);}

  public static void install(List<File> files, Device device,boolean downgrade) throws Exception {
    if(downgrade&&files.size()!=1)throw new IOException("回退仅支持已核实的单 APK");
    if (files.isEmpty()) throw new IOException("没有可传输的安装文件");
    long total = 0;
    for (File file : files) {
      if (!file.isFile() || !file.canRead()) throw new IOException("安装文件不可读取，请刷新手机应用列表重试");
      total += file.length();
    }
    String prefix =
        "/data/local/tmp/linkdeck_phone_" + UUID.randomUUID().toString().replace("-", "");
    List<String> remote = new ArrayList<>();
    String session = null;
    boolean committed = false;
    try {
      for (int i = 0; i < files.size(); i++) {
        String path = prefix + "_" + i + ".apk";
        remote.add(path);
        device.push(files.get(i), path);
      }
      if (files.size() == 1) {
        requireSuccess(device.shell("pm install -r "+(downgrade?"-d ":"") + AdbClient.quote(remote.get(0))));
        return;
      }
      String created = device.shell("pm install-create -r -S " + total);
      Matcher match =
          Pattern.compile("Success: created install session \\[(\\d+)\\]").matcher(created);
      if (!match.find()) throw new IOException("电视未创建拆分安装会话：" + created);
      session = match.group(1);
      for (int i = 0; i < files.size(); i++)
        requireSuccess(
            device.shell(
                "pm install-write -S "
                    + files.get(i).length()
                    + " "
                    + session
                    + " "
                    + (i == 0 ? "base.apk" : "split_" + i + ".apk")
                    + " "
                    + AdbClient.quote(remote.get(i))));
      requireSuccess(device.shell("pm install-commit " + session));
      committed = true;
    } finally {
      if (session != null && !committed)
        try {
          device.shell("pm install-abandon " + session);
        } catch (Exception ignored) {
        }
      if (!remote.isEmpty())
        try {
          StringBuilder cleanup = new StringBuilder("rm -f");
          for (String path : remote) cleanup.append(' ').append(AdbClient.quote(path));
          device.shell(cleanup.toString());
        } catch (Exception ignored) {
        }
    }
  }

  private static void requireSuccess(String output) throws IOException {
    if (!Pattern.compile("(?m)^Success(?:\\b|:)").matcher(output).find())
      throw new IOException(output.isEmpty() ? "电视未确认安装成功" : output);
  }
}
