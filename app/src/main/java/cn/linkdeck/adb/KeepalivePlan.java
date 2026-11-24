package cn.linkdeck.adb;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * Pure keepalive policy: heartbeat interval and the preference keys the background
 * service reuses to rebuild a connection. No Android types, so the host checks can
 * run it. The digest matches AppCatalog.digest (SHA-256, UTF-8, lowercase hex).
 */
public final class KeepalivePlan {
  public static final long HEARTBEAT_MS = 60000;
  public static final long HEALTH_TIMEOUT_MS = 5000;

  public static String digest(String value) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
      StringBuilder s = new StringBuilder();
      for (byte b : hash) s.append(String.format(Locale.ROOT, "%02x", b & 255));
      return s.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static String peerKey(String host) {
    return digest(host.trim().toLowerCase(Locale.ROOT));
  }

  public static String tlsPinKey(String host) {
    return "tls-pin:" + peerKey(host);
  }

  public static String pairedHostKey(String host) {
    return "paired-host:" + peerKey(host);
  }

  public static boolean needsTls(String connectMode) {
    return "tls".equals(connectMode);
  }

  private KeepalivePlan() {}
}
