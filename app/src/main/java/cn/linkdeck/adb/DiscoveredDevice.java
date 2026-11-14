package cn.linkdeck.adb;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** One host can announce several distinct ADB endpoints. No announcement grants trust. */
public final class DiscoveredDevice {
  public final String host;
  public int legacyPort, pairingPort, securePort;

  private DiscoveredDevice(String host) { this.host = host; }

  public static final class Index {
    private final Map<String, DiscoveredDevice> devices = new LinkedHashMap<>();
    public boolean add(String host, int port, String type) {
      if (host == null || type == null || port < 1 || port > 65535) return false;
      String address = host.trim();
      if (address.isEmpty() || address.contains("/") || address.contains(" ")) return false;
      String kind = type.toLowerCase(Locale.ROOT);
      int slot;
      if (kind.startsWith("_adb-tls-pairing._tcp")) slot = 1;
      else if (kind.startsWith("_adb-tls-connect._tcp")) slot = 2;
      else if (kind.startsWith("_adb._tcp")) slot = 0;
      else return false;
      String key = address.toLowerCase(Locale.ROOT);
      DiscoveredDevice item = devices.get(key);
      if (item == null) { item = new DiscoveredDevice(address); devices.put(key, item); }
      int previous = slot == 0 ? item.legacyPort : slot == 1 ? item.pairingPort : item.securePort;
      if (previous == port) return false;
      if (slot == 0) item.legacyPort = port;
      else if (slot == 1) item.pairingPort = port;
      else item.securePort = port;
      return true;
    }
    public Iterable<DiscoveredDevice> all() { return devices.values(); }
    public int size() { return devices.size(); }
  }
}
