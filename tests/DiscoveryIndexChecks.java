package cn.linkdeck.adb;

public final class DiscoveryIndexChecks {
  private static int checks;
  private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("discovery index check " + checks); }
  public static void main(String[] args) {
    DiscoveredDevice.Index index = new DiscoveredDevice.Index();
    check(index.add("192.0.2.10", 37123, "_adb-tls-connect._tcp."));
    check(index.add("192.0.2.10", 40123, "_adb-tls-pairing._tcp."));
    check(index.size() == 1);
    DiscoveredDevice device = index.all().iterator().next();
    check(device.securePort == 37123 && device.pairingPort == 40123 && device.legacyPort == 0);
    check(!index.add("192.0.2.10", 40123, "_adb-tls-pairing._tcp."));
    check(index.add("192.0.2.10", 5555, "_adb._tcp."));
    check(device.legacyPort == 5555);
    check(index.add("192.0.2.11", 5555, "_adb._tcp."));
    check(index.size() == 2);
    check(!index.add("192.0.2.11", 0, "_adb._tcp."));
    check(!index.add("192.0.2.11", 5555, "_http._tcp."));
    check(!index.add("192.0.2.11/invalid", 5555, "_adb._tcp."));
    check(index.size() == 2);
    System.out.println("DISCOVERY INDEX PASSED: " + checks + " checks");
  }
}
