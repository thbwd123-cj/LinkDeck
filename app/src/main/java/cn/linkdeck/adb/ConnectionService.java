package cn.linkdeck.adb;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Owns the live transport independently of Activity recreation. No boot auto-start.
 * 0.19.21: the service survives the app being swiped away, sends a heartbeat every
 * 60 s, reconnects by itself when the socket dies, and restarts (START_STICKY) after
 * the system kills the process. An explicit disconnect is the only way to stop it. */
public final class ConnectionService extends Service {
  private static final String STOP = "cn.linkdeck.adb.DISCONNECT";
  private static final String REFRESH = "cn.linkdeck.adb.REFRESH_NOTIFICATION";
  private static final int ID = 41;
  static volatile Session session;
  static volatile boolean backgroundAvailable;
  static volatile String lastStartError;
  private Session owned;
  private ScheduledExecutorService keepalive;

  static final class Session {
    volatile DeviceCapabilities.Snapshot capabilities;
    final AdbClient client;
    final String host, port, name;
    final Map<String, String> properties;

    Session(
        AdbClient client, String host, String port, String name, Map<String, String> properties) {
      this.client = client;
      this.host = host;
      this.port = port;
      this.name = name;
      this.properties = Collections.unmodifiableMap(new HashMap<>(properties));
    }
  }

  static synchronized boolean attach(
      Context context,
      AdbClient client,
      String host,
      String port,
      String name,
      Map<String, String> properties) {
    Session old = session;
    if (old != null && old.client != client) old.client.close();
    session = new Session(client, host, port, name, properties);
    context
        .getSharedPreferences("linkdeck", 0)
        .edit()
        .putBoolean("resume_connection", true)
        .apply();
    try {
      context.startForegroundService(new Intent(context, ConnectionService.class));
      backgroundAvailable=true;lastStartError=null;return true;
    } catch (SecurityException e) {
      backgroundAvailable=false;lastStartError="缺少前台服务权限或类型不符，系统拒绝启动后台服务。";
      context.getSharedPreferences("linkdeck", 0).edit().putBoolean("resume_connection", false).apply();
      return false; // Keep the live session usable while the activity remains open.
    } catch (IllegalStateException e) {
      backgroundAvailable=false;lastStartError="应用在后台时，系统不允许启动前台服务。";
      context.getSharedPreferences("linkdeck", 0).edit().putBoolean("resume_connection", false).apply();
      return false; // Keep the live session usable while the activity remains open.
    } catch (RuntimeException e) {
      backgroundAvailable=false;lastStartError="后台服务启动失败："+e.getMessage();
      context.getSharedPreferences("linkdeck", 0).edit().putBoolean("resume_connection", false).apply();
      return false; // Keep the live session usable while the activity remains open.
    }
  }

  /** Re-post the foreground notification, e.g. after the user grants the
   * notification permission late. No-op when there is no live session. */
  static void repost(Context context) {
    if (session == null) return;
    try {
      context.startForegroundService(
          new Intent(context, ConnectionService.class).setAction(REFRESH));
    } catch (RuntimeException ignored) {
    }
  }

  static synchronized void disconnect(Context context) {
    context
        .getSharedPreferences("linkdeck", 0)
        .edit()
        .putBoolean("resume_connection", false)
        .apply();
    Session old = session;
    backgroundAvailable=false;session = null;
    if (old != null) old.client.close();
    context.stopService(new Intent(context, ConnectionService.class));
  }

  @Override
  public void onCreate() {
    super.onCreate();
    NotificationManager nm = getSystemService(NotificationManager.class);
    NotificationChannel channel =
        new NotificationChannel("connection", "电视连接", NotificationManager.IMPORTANCE_LOW);
    channel.setDescription("保持你主动建立的电视连接，可在通知中断开");
    nm.createNotificationChannel(channel);
  }

  private boolean resumeWanted() {
    return getSharedPreferences("linkdeck", 0).getBoolean("resume_connection", false);
  }

  private void ensureKeepalive() {
    if (keepalive != null && !keepalive.isShutdown()) return;
    keepalive =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "linkdeck-keepalive");
              t.setDaemon(true);
              return t;
            });
    keepalive.scheduleWithFixedDelay(
        this::heartbeat, KeepalivePlan.HEARTBEAT_MS, KeepalivePlan.HEARTBEAT_MS, TimeUnit.MILLISECONDS);
  }

  private void heartbeat() {
    Session s = session;
    if (s == null || !resumeWanted()) {
      stopSelf();
      return;
    }
    if (s.client.isConnected()) {
      try {
        if (s.client.shell(":", KeepalivePlan.HEALTH_TIMEOUT_MS).exitCode == 0) return;
      } catch (Exception ignored) {
      }
    }
    reconnect(s);
  }

  /** Rebuild the connection from the stored prefs and identity, exactly like
   * MainActivity.connect() does. On failure the dead session is kept, so the
   * Activity can still retry; the next heartbeat tick tries again. */
  private void reconnect(Session dead) {
    if (!resumeWanted()) return;
    AdbClient next = null;
    try {
      SharedPreferences prefs = getSharedPreferences("linkdeck", 0);
      String h = prefs.getString("host", "");
      int port;
      try {
        port = Integer.parseInt(prefs.getString("port", "5555"));
      } catch (NumberFormatException e) {
        return;
      }
      if (h.isEmpty() || !resumeWanted()) return;
      String mode = prefs.getString("connect_mode", "legacy");
      boolean secure = KeepalivePlan.needsTls(mode);
      if (secure && prefs.getString(KeepalivePlan.pairedHostKey(h), "").isEmpty()) return;
      next =
          secure
              ? new AdbClient(
                  WirelessAdb.transport(
                      new File(getFilesDir(), "identity"),
                      prefs.getString(KeepalivePlan.tlsPinKey(h), "")))
              : new AdbClient();
      AdbIdentity identity = new AdbIdentity(new File(getFilesDir(), "identity"));
      if (!resumeWanted()) {
        next.close();
        return;
      }
      next.connect(h, port, identity, stage -> {});
      if (!resumeWanted()) {
        next.close();
        return;
      }
      String raw = next.shell("getprop").require();
      Map<String, String> props = new HashMap<>();
      for (String l : raw.split("\n")) {
        if (l.startsWith("[") && l.contains("]: [")) {
          int at = l.indexOf("]: [");
          props.put(l.substring(1, at), l.substring(at + 4, l.length() - 1));
        }
      }
      String name = props.containsKey("ro.product.model") ? props.get("ro.product.model") : h;
      Session fresh = new Session(next, h, String.valueOf(port), name, props);
      synchronized (ConnectionService.class) {
        if (session != dead || !resumeWanted()) {
          next.close();
          return;
        }
        session = fresh;
        owned = fresh;
      }
      if (dead != null) dead.client.close();
      postNotification(fresh);
    } catch (Exception e) {
      if (next != null) next.close();
    }
  }

  private Notification buildNotification(Session current) {
    Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent content =
        PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, ConnectionService.class).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    int icon = getResources().getIdentifier("ic_connection", "drawable", getPackageName());
    return new Notification.Builder(this, "connection")
        .setSmallIcon(icon)
        .setContentTitle("轻连 · " + current.name)
        .setContentText("正在保持电视连接 · " + current.host + ":" + current.port)
        .setContentIntent(content)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .addAction(new Notification.Action.Builder(icon, "断开连接", stop).build())
        .build();
  }

  private void postNotification(Session current) {
    Notification notification = buildNotification(current);
    if (Build.VERSION.SDK_INT >= 29)
      startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
    else startForeground(ID, notification);
  }

  private void postConnectingNotification() {
    int icon = getResources().getIdentifier("ic_connection", "drawable", getPackageName());
    Notification notification =
        new Notification.Builder(this, "connection")
            .setSmallIcon(icon)
            .setContentTitle("轻连 · 正在重新连接")
            .setContentText("正在恢复电视连接…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build();
    if (Build.VERSION.SDK_INT >= 29)
      startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
    else startForeground(ID, notification);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && STOP.equals(intent.getAction())) {
      disconnect(this);
      stopSelf();
      return START_NOT_STICKY;
    }
    ensureKeepalive();
    Session current = session;
    if (current == null) {
      // The process was killed and the system restarted the service: re-establish
      // from the stored connection, if the user still wants it.
      if (!resumeWanted()) {
        stopSelf();
        return START_NOT_STICKY;
      }
      postConnectingNotification();
      keepalive.execute(
          () -> {
            reconnect(null);
            if (session == null) stopSelf();
          });
      return START_STICKY;
    }
    if (!current.client.isConnected()) {
      keepalive.execute(() -> reconnect(current));
      // Keep the foreground notification up while the reconnect runs.
      postNotification(current);
      return START_STICKY;
    }
    owned = current;
    postNotification(current);
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    if (keepalive != null) keepalive.shutdownNow();
    synchronized (ConnectionService.class) {
      Session old = owned;
      if (session == old) { backgroundAvailable=false;session = null; if (old != null) old.client.close(); }
    }
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
