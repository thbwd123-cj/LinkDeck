package cn.linkdeck.meta;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import org.json.JSONObject;

/** One-shot read-only resource reader, launched as the already-authorized ADB shell user. */
public final class MetadataMain {
  public static void main(String[] args) {
    try {
      if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
      Class<?> at = Class.forName("android.app.ActivityThread");
      Method systemMain = at.getDeclaredMethod("systemMain");
      systemMain.setAccessible(true);
      Object thread = systemMain.invoke(null);
      Method getContext = at.getDeclaredMethod("getSystemContext");
      getContext.setAccessible(true);
      PackageManager pm = ((Context) getContext.invoke(thread)).getPackageManager();
      if (args.length == 1 && "labels".equals(args[0])) {
        for (PackageInfo info : pm.getInstalledPackages(0)) {
          try {
            ApplicationInfo app = info.applicationInfo;
            if (app == null) continue;
            CharSequence title = app.loadLabel(pm);
            String name = title == null ? info.packageName : title.toString();
            if (name.trim().isEmpty()) name = info.packageName;
            if (name.length() > 512) name = name.substring(0, 512);
            JSONObject item = new JSONObject();
            item.put("package", info.packageName);
            item.put("name", name);
            item.put("stamp", info.lastUpdateTime + ":" + info.versionCode);
            System.out.println(item.toString());
          } catch (Exception ignored) {
          }
        }
      } else if (args.length >= 2 && "icons".equals(args[0]) && args.length <= 9) {
        for (int i = 1; i < args.length; i++) {
          try {
            Drawable icon = pm.getApplicationInfo(args[i], 0).loadIcon(pm);
            Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, 96, 96);
            icon.draw(new Canvas(bitmap));
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
            bitmap.recycle();
            JSONObject item = new JSONObject();
            item.put("package", args[i]);
            item.put("icon", Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP));
            System.out.println(item.toString());
          } catch (Exception ignored) {
          }
        }
      } else throw new IllegalArgumentException("Unsupported metadata request");
      System.exit(0);
    } catch (Throwable error) {
      System.err.println("Metadata reader unavailable: " + error.getClass().getSimpleName());
      System.exit(2);
    }
  }
}
