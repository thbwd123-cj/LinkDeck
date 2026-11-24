package cn.linkdeck.adb;

/** Host checks for the 0.19.21 keepalive policy: heartbeat interval and the
 * preference keys the background service reuses to rebuild a connection. */
public final class KeepaliveChecks {
  static int checks;

  static void check(boolean b, String m) {
    if (!b) throw new AssertionError(m);
    checks++;
  }

  public static void main(String[] args) {
    check(KeepalivePlan.HEARTBEAT_MS == 60000, "Heartbeat runs every 60 s");
    check(KeepalivePlan.HEALTH_TIMEOUT_MS == 5000, "Health probe times out after 5 s");
    check(KeepalivePlan.needsTls("tls"), "tls mode needs the TLS transport");
    check(!KeepalivePlan.needsTls("legacy"), "legacy mode connects plain");
    check(!KeepalivePlan.needsTls(""), "empty mode connects plain");
    check(!KeepalivePlan.needsTls(null), "missing mode connects plain");
    check(
        KeepalivePlan.peerKey("192.168.31.185")
            .equals("b3a1df8cc7ce937cb90c2bd66435e31df4f7227737cce844757b13974069e537"),
        "peerKey is SHA-256 hex of the host");
    check(
        KeepalivePlan.peerKey(" 192.168.31.185 ").equals(KeepalivePlan.peerKey("192.168.31.185")),
        "peerKey trims surrounding whitespace");
    check(
        KeepalivePlan.peerKey("Example.COM").equals(KeepalivePlan.peerKey("example.com")),
        "peerKey lowercases the host");
    check(
        KeepalivePlan.tlsPinKey("192.168.31.185")
            .equals(
                "tls-pin:b3a1df8cc7ce937cb90c2bd66435e31df4f7227737cce844757b13974069e537"),
        "TLS pin key matches MainActivity.connect()");
    check(
        KeepalivePlan.pairedHostKey("192.168.31.185")
            .equals(
                "paired-host:b3a1df8cc7ce937cb90c2bd66435e31df4f7227737cce844757b13974069e537"),
        "Paired-host key matches MainActivity.connect()");
    System.out.println("KEEPALIVE PASSED: " + checks + " checks");
  }
}
