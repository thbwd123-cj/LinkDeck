package cn.linkdeck.adb;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import android.util.Base64;
import android.util.LruCache;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Device-scoped labels and version-keyed icons; no full APK downloads. */
public final class AppCatalog {
  private final Context context;
  private final File labelDirectory, iconDirectory;
  private final LruCache<String, Bitmap> memory =
      new LruCache<String, Bitmap>(4096) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
          return Math.max(1, value.getByteCount() / 1024);
        }
      };
  private AdbClient prepared;
  private final String remote = "/data/local/tmp/linkdeck-catalog-03.jar";

  public AppCatalog(Context context) {
    this.context = context;
    labelDirectory = new File(context.getFilesDir(), "catalog");
    iconDirectory = new File(context.getCacheDir(), "catalog-icons");
    labelDirectory.mkdirs();
    iconDirectory.mkdirs();
  }

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

  public String iconKey(String device, String pkg, String stamp) {
    return digest(device + "\n" + pkg + "\n" + stamp);
  }

  private byte[] read(File file, int max) throws IOException {
    try (InputStream in = new FileInputStream(file);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) {
        out.write(b, 0, n);
        if (out.size() > max) throw new IOException("Cache too large");
      }
      return out.toByteArray();
    }
  }

  public JSONObject cachedLabels(String device) {
    try {
      return new JSONObject(
          new String(
              read(new File(labelDirectory, digest(device) + ".json"), 2 * 1024 * 1024), "UTF-8"));
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  private void writeAtomic(File file, byte[] bytes) throws IOException {
    AtomicFile atomic = new AtomicFile(file);
    FileOutputStream stream = atomic.startWrite();
    try {
      stream.write(bytes);
      atomic.finishWrite(stream);
    } catch (IOException e) {
      atomic.failWrite(stream);
      throw e;
    }
  }

  private void prepare(AdbClient client) throws Exception {
    if (prepared == client && client.shell("if [ -f " + AdbClient.quote(remote) + " ]; then echo present; fi").require().trim().equals("present")) return;
    try (InputStream in = context.getAssets().open("catalog.jar")) {
      client.push(in, remote, null);
    }
    prepared = client;
  }

  private String command(String mode) {
    return "CLASSPATH="
        + AdbClient.quote(remote)
        + " app_process /system/bin cn.linkdeck.meta.MetadataMain "
        + mode;
  }

  public JSONObject refreshLabels(AdbClient client, String device) throws Exception {
    prepare(client);
    String output = client.shell(command("labels")).require();
    JSONObject labels = new JSONObject();
    for (String line : output.split("\n")) {
      if (!line.startsWith("{")) continue;
      try {
        JSONObject item = new JSONObject(line);
        String pkg = item.getString("package");
        if (pkg.length() < 300) labels.put(pkg, item);
      } catch (Exception ignored) {
      }
    }
    if (labels.length() == 0) throw new IOException("设备未返回应用名称，仍可使用包名管理");
    writeAtomic(
        new File(labelDirectory, digest(device) + ".json"), labels.toString().getBytes("UTF-8"));
    return labels;
  }

  public Bitmap peek(String key) {
    return memory.get(key);
  }

  public Bitmap cachedIcon(String key) {
    Bitmap b = memory.get(key);
    if (b != null) return b;
    try {
      byte[] bytes = read(new File(iconDirectory, key + ".png"), 128 * 1024);
      b = decode(bytes);
      if (b != null) memory.put(key, b);
    } catch (Exception ignored) {
    }
    return b;
  }

  private Bitmap decode(byte[] png) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(png, 0, png.length, options);
    if (options.outWidth < 1
        || options.outHeight < 1
        || options.outWidth > 192
        || options.outHeight > 192) return null;
    return BitmapFactory.decodeByteArray(png, 0, png.length);
  }

  public Map<String, Bitmap> loadIcons(
      AdbClient client, String device, Map<String, String> requests) throws Exception {
    Map<String, Bitmap> result = new HashMap<>();
    List<String> missing = new ArrayList<>();
    for (String pkg : requests.keySet()) {
      String key = iconKey(device, pkg, requests.get(pkg));
      Bitmap b = cachedIcon(key);
      if (b != null) result.put(pkg, b);
      else missing.add(pkg);
    }
    if (missing.isEmpty()) return result;
    prepare(client);
    StringBuilder args = new StringBuilder("icons");
    for (String pkg : missing) args.append(' ').append(AdbClient.quote(pkg));
    String output = client.shell(command(args.toString())).require();
    for (String line : output.split("\n")) {
      if (!line.startsWith("{")) continue;
      try {
        JSONObject item = new JSONObject(line);
        String pkg = item.getString("package");
        if (!requests.containsKey(pkg)) continue;
        String base64 = item.getString("icon");
        if (base64.length() > 180000) continue;
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        Bitmap b = decode(bytes);
        if (b == null) continue;
        String key = iconKey(device, pkg, requests.get(pkg));
        memory.put(key, b);
        writeAtomic(new File(iconDirectory, key + ".png"), bytes);
        result.put(pkg, b);
      } catch (Exception ignored) {
      }
    }
    trimIcons();
    return result;
  }

  private void trimIcons() {
    File[] files = iconDirectory.listFiles((dir, name) -> name.endsWith(".png"));
    if (files == null || files.length <= 300) return;
    Arrays.sort(files, Comparator.comparingLong(File::lastModified));
    for (int i = 0; i < files.length - 240; i++) files[i].delete();
  }
}
