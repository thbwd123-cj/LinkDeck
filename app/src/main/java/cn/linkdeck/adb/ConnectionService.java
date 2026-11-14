package cn.linkdeck.adb;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.util.*;

/** Owns the live transport independently of Activity recreation. No boot auto-start. */
public final class ConnectionService extends Service {
  private static final String STOP = "cn.linkdeck.adb.DISCONNECT";
  private static final int ID = 41;
  static volatile Session session;
  static volatile boolean backgroundAvailable;
  private Session owned;

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
      backgroundAvailable=true;return true;
    } catch (IllegalStateException | SecurityException e) {
      backgroundAvailable=false;
      context.getSharedPreferences("linkdeck", 0).edit().putBoolean("resume_connection", false).apply();
      return false; // Keep the live session usable while the activity remains open.
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

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && STOP.equals(intent.getAction())) {
      disconnect(this);
      stopSelf();
      return START_NOT_STICKY;
    }
    Session current = session;
    if (current == null || !current.client.isConnected()) {
      stopSelf();
      return START_NOT_STICKY;
    }
    owned = current;
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
    Notification notification =
        new Notification.Builder(this, "connection")
            .setSmallIcon(icon)
            .setContentTitle("轻连 · " + current.name)
            .setContentText("正在保持电视连接 · " + current.host + ":" + current.port)
            .setContentIntent(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(icon, "断开连接", stop).build())
            .build();
    if (Build.VERSION.SDK_INT >= 29)
      startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
    else startForeground(ID, notification);
    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
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
