package cn.linkdeck.adb;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** 手机键盘中文输入：电视端 ADBKeyBoard 输入法（senzhk/ADBKeyBoard，GPLv2）。
 *  adb shell input text 只支持 ASCII，中文必须走电视端输入法；本类只含常量、命令构造与下载校验，
 *  不直接碰 ADB 连接。APK 不打进安装包，按需从官方发布地址下载并验大小与哈希。 */
public final class TvKeyboard {
  private TvKeyboard(){}
  public static final String PKG = "com.android.adbkeyboard";
  public static final String IME = "com.android.adbkeyboard/.AdbIME";
  public static final String DOWNLOAD_URL = "https://github.com/senzhk/ADBKeyBoard/releases/download/v2.4-dev/keyboardservice-debug.apk";
  public static final String EXPECTED_SHA256 = "e0d0cf276b710cb34c46121f58720f5285a83ed410b0d45f57a0677b67dc2852";
  public static final long EXPECTED_SIZE = 18595;
  public static final String VERSION_NAME = "2.0";
  public static final int VERSION_CODE = 2;
  public static final String LICENSE = "GPLv2";
  public static final String SOURCE = "senzhk/ADBKeyBoard";
  /** 是否全是 ASCII（可走 input text）。 */
  public static boolean isAscii(String s) {
    if (s == null) return true;
    for (int i = 0; i < s.length(); i++) if (s.charAt(i) >= 128) return false;
    return true;
  }
  /** UTF-8 文本 → 无换行 Base64。 */
  public static String base64(String text) {
    return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }
  /** ASCII 文本命令：空格写 %s。 */
  public static String textCommand(String ascii) {
    return "input text " + quote(ascii.replace(" ", "%s"));
  }
  /** 任意文本命令：base64 走 ADB_INPUT_B64 广播，绕开 am 命令对 UTF-8 与引号的限制。 */
  public static String broadcastCommand(String text) {
    return "am broadcast -a ADB_INPUT_B64 --es msg " + quote(base64(text));
  }
  public static String enableCommand() { return "ime enable " + IME; }
  public static String setCommand() { return "ime set " + IME; }
  public static String restoreCommand(String original) { return "ime set " + original; }
  /** 是否已装：pm list packages 的输出里找包名。 */
  public static String installedProbe() { return "pm list packages " + PKG; }
  public static String currentImeProbe() { return "settings get secure default_input_method"; }
  public static String parseDefaultIme(String output) { return output == null ? "" : output.trim(); }
  public static boolean isInstalled(String pmListOutput) {
    return pmListOutput != null && pmListOutput.contains("package:" + PKG);
  }
  public static boolean isCurrentIme(String defaultIme) {
    return IME.equals(parseDefaultIme(defaultIme));
  }
  static String quote(String s) { return "'" + s.replace("'", "'\\''") + "'"; }
  public interface Progress { void update(String stage); }
  public static String sha256(File file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new FileInputStream(file)) {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
    }
    StringBuilder out = new StringBuilder();
    for (byte b : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", b));
    return out.toString();
  }
  static boolean trusted(URL u) {
    if (!u.getProtocol().equals("https") || u.getUserInfo() != null
        || (u.getPort() != -1 && u.getPort() != 443)) return false;
    String h = u.getHost().toLowerCase(Locale.ROOT), p = u.getPath();
    return h.equals("github.com") && p.startsWith("/senzhk/ADBKeyBoard/releases/download/")
        || h.equals("release-assets.githubusercontent.com")
        || h.equals("objects.githubusercontent.com");
  }
  /** 从官方发布地址下载并验大小与哈希（仿 AerialDownload 的可信地址与重定向纪律）。 */
  public static void download(File target, Progress progress) throws Exception {
    URL u = new URL(DOWNLOAD_URL);
    HttpURLConnection c = null;
    for (int hop = 0; hop < 6; hop++) {
      if (!trusted(u)) throw new IOException("下载重定向到了非官方或非 HTTPS 地址，已停止。");
      c = (HttpURLConnection) u.openConnection();
      c.setInstanceFollowRedirects(false);
      c.setConnectTimeout(10000);
      c.setReadTimeout(20000);
      c.setUseCaches(false);
      c.setRequestProperty("User-Agent", "LinkDeck/" + AppVersion.NAME);
      c.setRequestProperty("Accept", "application/octet-stream");
      int status = c.getResponseCode();
      if (status >= 300 && status < 400) {
        String location = c.getHeaderField("Location");
        c.disconnect();
        if (location == null) throw new IOException("下载重定向缺少地址");
        u = new URL(u, location);
        continue;
      }
      if (status != 200) {
        c.disconnect();
        throw new IOException("官方服务返回 HTTP " + status + "，请稍后重试或更换网络。");
      }
      break;
    }
    if (c == null) throw new IOException("下载重定向次数过多");
    try (InputStream in = c.getInputStream();
         OutputStream out = new FileOutputStream(target)) {
      byte[] buffer = new byte[8192];
      int n;
      long total = 0;
      while ((n = in.read(buffer)) != -1) {
        out.write(buffer, 0, n);
        total += n;
        if (total > EXPECTED_SIZE) throw new IOException("下载的安装包大小异常，未安装");
        if (progress != null) progress.update("正在下载中文输入法 · " + total + " / " + EXPECTED_SIZE + " 字节");
      }
    } finally {
      c.disconnect();
    }
    if (target.length() != EXPECTED_SIZE) throw new IOException("下载的安装包大小不符，未安装");
    if (!EXPECTED_SHA256.equals(sha256(target))) throw new IOException("下载的安装包校验不符，未安装");
  }
}
