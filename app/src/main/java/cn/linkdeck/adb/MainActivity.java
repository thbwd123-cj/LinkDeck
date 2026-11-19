package cn.linkdeck.adb;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.hardware.ConsumerIrManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

public class MainActivity extends Activity {
  private int BG = UiPalette.color(0xff0b131e),
      INK = UiPalette.color(0xffedf5ff),
      MUTED = UiPalette.color(0xffa4b3c8),
      TEAL = UiPalette.color(0xff4ce1cd),
      LINE = UiPalette.color(0xff304257),
      RED = UiPalette.color(0xffff9292);
  private final ExecutorService work = Executors.newSingleThreadExecutor(),
      keys = Executors.newSingleThreadExecutor(),
      catalogWork = Executors.newSingleThreadExecutor();
  private final Handler ui = new Handler(Looper.getMainLooper());
  private AdbClient adb;
  private IrCodes irProfile;
  private String pendingIrExportChoice;
  private String pendingIrReportChoice;
  private String pendingAppInventory;
  private boolean resumed, checkingConnection;
  private final ExecutorService recovery = Executors.newSingleThreadExecutor();
  private volatile AdbClient connectingClient;
  private volatile boolean connectionCancelled;
  private AlertDialog connectionDialog;
  private TaskJournal journal;
  private DeviceCapabilities.Snapshot capabilitySnapshot;
  private AdbClient capabilityClient;
  private WirelessAdb activePairing;
  private AlertDialog pairingDialog;
  private LanDiscovery discovery;
  private LinearLayout connectionPanel, connectionManager;
  private String installExpectedPackage;
  private AlertDialog aerialCenterDialog, haloCenterDialog, genericDialog;
  private GenericPlan genericPlan;
  private volatile String activeTaskId;
  private final Set<String> ownedTaskIds = new HashSet<>();
  private TextView connectionDetail;
  private long connectionStarted;
  private String connectionStage = "";
  private final Runnable connectionTicker =
      new Runnable() {
        public void run() {
          if (connectionDialog == null) return;
          long elapsed = (SystemClock.elapsedRealtime() - connectionStarted) / 1000;
          if (journal != null && activeTaskId != null) journal.progress(activeTaskId, connectionStage);
          connectionDetail.setText(connectionStage + "\n\n已等待 " + elapsed + " 秒\n请保持电视开机，并留意授权弹窗。");
          ui.postDelayed(this, 1000);
        }
      };
  private AdbIdentity identity;
  private AppCatalog catalog;
  private String catalogDevice = "", catalogMessage = "连接后读取应用名称与图标";
  private boolean metadataLoading;
  private final Set<String> favorites = new HashSet<>(),
      iconsPending = new HashSet<>(),
      iconsFailed = new HashSet<>();
  private final Map<String, ImageView> iconViews = new HashMap<>();
  private TextView catalogHint;
  private final Runnable iconRefresh = () -> loadVisibleIcons();
  private boolean onlyFavorites;
  private ScrollView pageScroll;

  private android.content.SharedPreferences prefs;
  private LinearLayout root, body, nav;
  private TextView status;
  private ProgressBar progress;
  private int page = 0;
  private EditText hostInput, portInput;
  private String host = "", folder = "/sdcard", device = "未连接设备", deviceAbi = "";
  private int deviceSdk = 0;
  private final Map<String, String> properties = new HashMap<>();
  private final ArrayList<AppRow> apps = new ArrayList<>();
  private final Set<String> selected = new HashSet<>();
  private String appQuery = "";
  private boolean showSystem = false, onlyDisabled = false, batchMode = false;
  private LinearLayout appList;
  private boolean appLoaded = false;
  private int appLimit = 60;
  private final Set<String> pendingJobs = new HashSet<>();
  private final Runnable searchUpdate =
      () -> {
        appLimit = 60;
        renderApps();
      };
  private final Runnable connectionMonitor =
      new Runnable() {
        @Override
        public void run() {
          if (!busy && status != null) {
            String message = connected() ? "●  " + device + " · " + host : "○  未连接 · 点此返回连接页";
            if (!message.contentEquals(status.getText())) status.setText(message);
            status.setTextColor(connected() ? TEAL : MUTED);
          }
          if (resumed
              && !busy
              && !connected()
              && connectionDialog == null
              && prefs.getBoolean("resume_connection", false)) recoverConnection();
          ui.postDelayed(this, 2000);
        }
      };
  private String saveRemote, saveName;
  private byte[] saveImage;
  private long lastProgress;
  private boolean busy = false;
  private int tasks = 0;
  private Uri sharedUri;
  private int afterConnectPage = -1;
  private File pendingInstall;
  private String pendingPhoneApp;
  private String pendingCommandExport;
  private CommandRecord lastShownCommand;
  private Dialog commandResultDialog;
  private final ExecutorService copyWork = Executors.newSingleThreadExecutor();
  private boolean copying;
  private String draftHost = "", draftPort = "5555", draftCommand = "", draftRemote = "";
  private EditText commandInput, remoteInput;
  private boolean connectionPromptVisible;

  private interface Job {
    String run() throws Exception;
  }

  private static final class AppRow {
    String pkg, path, name, stamp;
    boolean system, disabled;
  }

  @Override
  public void onCreate(Bundle state) {
    UiPalette.select(getSharedPreferences("linkdeck", MODE_PRIVATE).getString("color_theme","original"));
    setTheme(getResources().getIdentifier(UiPalette.themeName(),"style",getPackageName()));
    super.onCreate(state);
    prefs = getSharedPreferences("linkdeck", MODE_PRIVATE);
    if(state!=null)installExpectedPackage=state.getString("expectedPackage");
    journal = TaskJournal.get(this);
    catalog = new AppCatalog(this);
    host = prefs.getString("host", "");
    draftHost = host;
    draftPort = prefs.getString("port", "5555");
    if (Intent.ACTION_SEND.equals(getIntent().getAction()))
      sharedUri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    restoreSession();
    show(state == null ? 0 : state.getInt("page", 0));
    if (sharedUri != null) consumeShared();
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (Intent.ACTION_SEND.equals(intent.getAction())) {
      sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
      consumeShared();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    resumed = true;
    recoverConnection();
    ui.removeCallbacks(connectionMonitor);
    ui.post(connectionMonitor);
  }

  @Override
  public void onPause() {
    resumed = false;
    ui.removeCallbacks(connectionMonitor);
    super.onPause();
  }

  @Override
  public void onBackPressed() {
    if (page == 3 && !folder.equals("/sdcard") && !folder.equals("/")) {
      int at = folder.lastIndexOf('/');
      folder = at <= 0 ? "/" : folder.substring(0, at);
      show(3);
      return;
    }
    if (page != 0) {
      show(0);
      return;
    }
    if (busy && !connected()) {
      toast("连接任务仍在进行，可先取消连接");
      return;
    }
    if (connected()) {
      moveTaskToBack(true);
      toast(ConnectionService.backgroundAvailable?"已转入后台，电视连接继续保持":"已转入后台，当前连接可能中断");
    } else finish();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    ui.removeCallbacksAndMessages(null);
    if (connectingClient != null) connectingClient.close();
    if(activePairing!=null)activePairing.cancel();
    if(pairingDialog!=null)pairingDialog.dismiss();
    if(discovery!=null)discovery.close();
    if (connectionDialog != null) connectionDialog.dismiss();
    if (commandResultDialog != null) commandResultDialog.dismiss();
    if (aerialDialog != null) aerialDialog.dismiss();
    if (haloDialog != null) haloDialog.dismiss();
    if (aerialDesktop.dialog != null) aerialDesktop.dialog.dismiss();
    if (haloDesktop.dialog != null) haloDesktop.dialog.dismiss();
    if (aerialPlan != null) aerialPlan.file.delete();
    if (haloPlan != null) haloPlan.file.delete();
    if(aerialCenterDialog!=null)aerialCenterDialog.dismiss();
    if(haloCenterDialog!=null)haloCenterDialog.dismiss();
    if(genericDialog!=null)genericDialog.dismiss();
    if(genericPlan!=null)genericPlan.clean();
    if (isFinishing()) ConnectionService.disconnect(this);
    copyWork.shutdownNow();
    recovery.shutdownNow();
    work.shutdownNow();
    if(journal!=null)journal.interrupt(ownedTaskIds);
    keys.shutdownNow();
    catalogWork.shutdownNow();
  }

  @Override
  public void onConfigurationChanged(android.content.res.Configuration config) {
    super.onConfigurationChanged(config);
    if (root != null) show(page);
  }

  @Override
  public void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putInt("page", page);
    out.putString("expectedPackage",installExpectedPackage);
  }

  private void restoreSession() {
    ConnectionService.Session saved = ConnectionService.session;
    if (saved == null || !saved.client.isConnected()) return;
    adb = saved.client;
    capabilitySnapshot=saved.capabilities;capabilityClient=saved.client;
    host = saved.host;
    draftHost = host;
    draftPort = saved.port;
    device = saved.name;
    properties.clear();
    properties.putAll(saved.properties);
    applyDeviceInfo(saved.port);
  }

  private void applyDeviceInfo(String connectedPort) {
    try {
      deviceSdk = Integer.parseInt(properties.get("ro.build.version.sdk"));
    } catch (Exception ignored) {
      deviceSdk = 0;
    }
    deviceAbi =
        properties.containsKey("ro.product.cpu.abilist")
            ? properties.get("ro.product.cpu.abilist")
            : properties.get("ro.product.cpu.abi");
    catalogDevice =
        host
            + ":"
            + connectedPort
            + ":"
            + properties.get("ro.serialno")
            + ":"
            + properties.get("ro.build.fingerprint");
    favorites.clear();
    favorites.addAll(prefs.getStringSet(favoritesKey(), Collections.emptySet()));
  }

  private void recoverConnection() {
    if (checkingConnection || busy || !prefs.getBoolean("resume_connection", false)) return;
    if (!connected()) {
      afterConnectPage = page;
      connect(true);
      return;
    }
    final AdbClient current = adb;
    checkingConnection = true;
    recovery.execute(
        () -> {
          boolean healthy;
          try {
            healthy = current.shell(":", 5000).exitCode == 0;
          } catch (Exception e) {
            healthy = false;
          }
          final boolean ok = healthy;
          ui.post(
              () -> {
                checkingConnection = false;
                if (isFinishing() || isDestroyed() || current != adb) return;
                if (!ok && prefs.getBoolean("resume_connection", false)) {
                  current.close();
                  if (resumed && !busy) {
                    afterConnectPage = page;
                    connect(true);
                  }
                }
              });
        });
  }

  private int dp(float v) {
    return (int) (v * getResources().getDisplayMetrics().density + .5f);
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setLineSpacing(dp(2), 1);
    return t;
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setBaselineAligned(false);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private LinearLayout.LayoutParams full() {
    return new LinearLayout.LayoutParams(-1, -2);
  }

  private void gap(LinearLayout l, int h) {
    View v = new View(this);
    l.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
  }

  private LinearLayout card(LinearLayout parent) {
    LinearLayout c = column();
    c.setPadding(dp(16), dp(16), dp(16), dp(16));
    c.setBackground(new GlassSurface(c,(AuroraLayout)root,22,false));
    c.setElevation(dp(1));
    LinearLayout.LayoutParams p = full();
    p.bottomMargin = dp(12);
    parent.addView(c, p);
    return c;
  }

  private void section(LinearLayout l, String title, String sub) {
    LinearLayout heading = row();
    TextView label = text(title, 18, INK);
    label.setTypeface(null, Typeface.BOLD);
    heading.addView(label);
    l.addView(heading);
    if (sub != null && !sub.isEmpty()) {
      gap(l, 5);
      l.addView(text(sub, 12, MUTED));
    }
    gap(l, 14);
  }

  private TextView button(String label, boolean primary, Runnable action) {
    TextView b = text(label, 14, primary ? AuroraStyle.PRIMARY_TEXT : INK);
    b.setGravity(Gravity.CENTER);
    b.setFocusable(true);
    b.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());}});
    b.setTypeface(null, Typeface.BOLD);
    b.setPadding(dp(12), dp(11), dp(12), dp(11));
    b.setMinHeight(dp(48));
    DepthSurface bg = new DepthSurface(getResources().getDisplayMetrics().density,22,primary);
    b.setElevation(dp(4));
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(UiPalette.color(0x250e887c)), bg, null));
    pressMotion(b);
    String feature=label.contains("截取")?DeviceCapabilities.SCREENSHOT:label.equals("刷新列表")?DeviceCapabilities.APPS:null;
    if(capabilityClient==adb&&capabilitySnapshot!=null&&feature!=null&&capabilitySnapshot.blocked(feature)){b.setEnabled(false);b.setAlpha(.45f);b.setContentDescription(label+"，当前设备未提供接口；请查看能力报告");}
    b.setOnClickListener(v -> {if(page==1)v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);action.run();});
    return b;
  }

  private void addButton(LinearLayout l, String title, boolean primary, Runnable run) {
    LinearLayout.LayoutParams p = full();
    p.topMargin = dp(8);
    l.addView(button(title, primary, run), p);
  }

  private void pair(LinearLayout l, String a, Runnable ar, String b, Runnable br) {
    LinearLayout r = row();
    r.setGravity(Gravity.TOP);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.setMargins(0, dp(8), dp(5), 0);
    r.addView(button(a, false, ar), p);
    LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(0, -2, 1);
    q.setMargins(dp(5), dp(8), 0, 0);
    r.addView(button(b, false, br), q);
    l.addView(r);
  }

  private EditText field(String hint, String val) {
    EditText e = new EditText(this);
    e.setTextSize(15);
    e.setTextColor(INK);
    e.setHintTextColor(MUTED);
    e.setSingleLine(true);
    e.setHint(hint);
    e.setText(val);
    e.setPadding(dp(12), dp(12), dp(12), dp(12));
    GradientDrawable fieldBackground=shape(UiPalette.color(0xff101d2c),14);fieldBackground.setStroke(dp(1),LINE);e.setBackground(fieldBackground);
    e.setHighlightColor(UiPalette.color(0x664ce1cd));
    return e;
  }

  private LinearLayout remoteInputPanel;
  private VolumeGlide volumeGlide;
  private void show(int target) {
    BG=AuroraStyle.base();INK=UiPalette.color(0xffedf5ff);MUTED=UiPalette.color(0xffa4b3c8);
    TEAL=UiPalette.color(0xff4ce1cd);LINE=UiPalette.color(0xff304257);RED=UiPalette.color(0xffff9292);
    getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
    int barFlags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    int currentFlags=getWindow().getDecorView().getSystemUiVisibility();
    getWindow().getDecorView().setSystemUiVisibility(UiPalette.current()==4?currentFlags|barFlags:currentFlags&~barFlags);

    final int direction=Integer.compare(target,page);
    if (page == 0 && hostInput != null) {
      draftHost = hostInput.getText().toString();
      draftPort = portInput.getText().toString();
    }
    if (page == 4 && commandInput != null) draftCommand = commandInput.getText().toString();
    if (page == 1 && remoteInput != null) draftRemote = remoteInput.getText().toString();
    ui.removeCallbacks(searchUpdate);
    ui.removeCallbacks(iconRefresh);
    iconViews.clear();
    page = target;
    root = new AuroraLayout(this,()->motionEnabled());
    root.setPadding(0, 0, 0, 0);
    setContentView(root);
    root.setKeepScreenOn(busy);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets;
        });
    root.requestApplyInsets();
    LinearLayout header = column();
    header.setPadding(dp(20), dp(10), dp(20), dp(8));
    LinearLayout brand = row();
    TextView logo = text(new String[]{"轻连","遥控器","电视应用","文件","工具箱"}[page], page==0?25:22, INK);
    logo.setTypeface(null, Typeface.BOLD);
    brand.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));
    TextView version = text(AppVersion.NAME, 11, pageAccent());
    version.setPadding(dp(10), dp(5), dp(10), dp(5));
    version.setBackground(shape(pageTint(), 20));
    if(page==1){if(!irMode())brand.addView(iconButton("键盘输入",10,INK,()->toggleRemoteKeyboard()),new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout.LayoutParams powerLp=new LinearLayout.LayoutParams(dp(44),dp(44));powerLp.leftMargin=dp(8);View power=iconButton("电源 / 待机",11,RED,()->confirm("切换电源状态","可能使设备进入待机。",()->{if(irMode())irSend("POWER");else key(26);}));markIrKey(power,"POWER");brand.addView(power,powerLp);logo.setContentDescription("界面设置，遥控器页面，版本 "+AppVersion.NAME);logo.setFocusable(true);logo.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());}});logo.setOnClickListener(v->appearance());}else brand.addView(version);version.setContentDescription("界面设置，版本 "+AppVersion.NAME);version.setOnClickListener(v->appearance());
    brand.addView(iconButton("设置",4,MUTED,()->settingsMenu()),new LinearLayout.LayoutParams(dp(44),dp(44)));
    header.addView(brand);
    status =
        text(
            page==1&&irMode()?"◉  红外遥控 · 无需连接电视":connected() ? "●  " + device + " · " + host : "○  尚未连接 · 手机与设备需在同一网络",
            12,
            connected() ? TEAL : MUTED);
    gap(header, 6);
    status.setOnClickListener(
        v -> {
          if (!connected()) openConnectionManager();
        });
    status.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
    ConnectionLamp lamp=new ConnectionLamp(getResources().getDisplayMetrics().density,()->connected(),()->busy);
    lamp.setBounds(0,0,dp(24),dp(24));
    status.setCompoundDrawables(null,null,lamp,null);
    status.setCompoundDrawablePadding(dp(6));
    status.setSingleLine(true);
    status.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    header.addView(status);
    root.addView(header);
    progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setIndeterminate(true);
    progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
    ScrollView scroll = new ScrollView(this);
    pageScroll = scroll;
    scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> scheduleIcons());
    scroll.setFillViewport(true);
    body = column();
    body.setPadding(dp(14), dp(5), dp(14), dp(16));
    scroll.addView(
        body,
        new ScrollView.LayoutParams(
            Math.min(getResources().getDisplayMetrics().widthPixels, dp(760)),
            -2,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL));
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    nav = row();
    nav.setBackground(new GlassSurface(nav,(AuroraLayout)root,40,false));
    nav.setElevation(dp(10));
    nav.setPadding(dp(4), dp(5), dp(4), dp(5));
    String[] names = {"设备", "遥控", "应用", "文件", "工具"};
    for (int i = 0; i < names.length; i++) {
      final int n = i;
      FrameLayout item = new FrameLayout(this);
      item.setMinimumHeight(dp(58));
      LinearLayout visual = column();
      visual.setGravity(Gravity.CENTER);
      visual.setPadding(0, dp(2), 0, dp(2));
      visual.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      LinearLayout glyph = row();
      glyph.setGravity(Gravity.CENTER);
      if(i==page)visual.setBackground(new DepthSurface(getResources().getDisplayMetrics().density,28,true));
      glyph.addView(new LineIcon(this, i, i == page ? AuroraStyle.PRIMARY_TEXT : MUTED),new LinearLayout.LayoutParams(dp(24), dp(24)));
      visual.addView(glyph,new LinearLayout.LayoutParams(dp(54),dp(31)));
      if(i==page)DeckMotion.select(glyph,motionEnabled());
      TextView label=text(names[i],11,i==page?AuroraStyle.PRIMARY_TEXT:MUTED);
      label.setGravity(Gravity.CENTER);
      if(i==page)label.setTypeface(null,Typeface.BOLD);
      visual.addView(label);
      item.addView(visual,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
      // Keep the native input surface stationary. Only its decorative glyph moves.
      Button hit=new Button(this);
      hit.setText("");hit.setContentDescription(names[i]+"，导航"+(i==page?"，当前页面":""));hit.setTag("nav-tab-"+i);
      hit.setPadding(0,0,0,0);hit.setMinHeight(0);hit.setMinimumHeight(0);hit.setMinWidth(0);hit.setMinimumWidth(0);
      hit.setBackgroundTintList(null);hit.setStateListAnimator(null);hit.setElevation(0);
      hit.setBackground(new RippleDrawable(ColorStateList.valueOf(UiPalette.color(0x284ce1cd)),null,shape(Color.WHITE,18)));
      hit.setFocusable(true);hit.setFocusableInTouchMode(false);hit.setSelected(i==page);
      hit.setOnTouchListener((v,event)->{int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN)pressState(glyph,true);else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)pressState(glyph,false);return false;});
      hit.setOnClickListener(v->{if(page!=n)show(n);});
      item.addView(hit,new FrameLayout.LayoutParams(-1,-1));
      nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
    }
    LinearLayout.LayoutParams dock = full();
    dock.setMargins(dp(14), dp(8), dp(14), dp(10));
    root.addView(nav, dock);
    if (page == 0) home();
    else if (page == 1) remote();
    else if (page == 2) applications();
    else if (page == 3) files();
    else tools();
    DeckMotion.enter(body,direction,motionEnabled());
  }

  private boolean motionEnabled(){return android.animation.ValueAnimator.areAnimatorsEnabled()&&!prefs.getBoolean("reduce_motion",false);}
  private void appearance(){
    LinearLayout content=column();content.setPadding(dp(20),dp(10),dp(20),dp(8));
    addButton(content,"颜色主题 · "+UiPalette.NAMES[UiPalette.current()],true,()->choosePalette());
    android.widget.Switch reduce=new android.widget.Switch(this);reduce.setText("减少动态效果");reduce.setTextColor(INK);reduce.setTextSize(15);reduce.setPadding(0,dp(8),0,dp(12));reduce.setChecked(prefs.getBoolean("reduce_motion",false));reduce.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("reduce_motion",on).apply();((AuroraLayout)root).refreshMotion();});content.addView(reduce);
    content.addView(text("关闭极光流动、页面与按钮过渡。系统关闭动画时也会自动停用动效。",12,MUTED));
    appearanceDialog=new DeckDialogBuilder(this).setTitle("界面与配色").setView(content).setNegativeButton("关闭",null).show();
  }
  private AlertDialog appearanceDialog;
  private void choosePalette(){
    if(appearanceDialog!=null)appearanceDialog.dismiss();
    new DeckDialogBuilder(this).setTitle("选择颜色主题").setSingleChoiceItems(UiPalette.NAMES,UiPalette.current(),(d,index)->{
      if(!prefs.edit().putString("color_theme",UiPalette.IDS[index]).commit()){toast("配色未能保存，请重试");return;}
      d.dismiss();UiPalette.select(UiPalette.IDS[index]);
      getTheme().applyStyle(getResources().getIdentifier(UiPalette.themeName(),"style",getPackageName()),true);
      show(page);toast("已切换为 "+UiPalette.NAMES[index]);
    }).setNegativeButton("取消",(d,w)->appearance()).show();
  }

  private void pressState(View view,boolean down){/* Visual only; ordinary click pressed state belongs to the framework. */if(!motionEnabled()){view.setScaleX(1);view.setScaleY(1);view.setTranslationY(0);view.setTranslationZ(0);return;}view.animate().scaleX(down?.96f:1).scaleY(down?.96f:1).translationY(down?dp(1.5f):0).translationZ(down?-dp(2):0).setDuration(down?85:220).setInterpolator(down?new android.view.animation.DecelerateInterpolator():new android.view.animation.OvershootInterpolator(1.1f)).start();}
  private void pressMotion(View v){v.setOnTouchListener((view,event)->{int a=event.getActionMasked();if(a==MotionEvent.ACTION_DOWN)pressState(view,true);else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL)pressState(view,false);return false;});}
  private FrameLayout iconButton(String label,int icon,int color,Runnable action){FrameLayout frame=new FrameLayout(this);TextView hit=button(label,false,action);hit.setElevation(0);frame.setElevation(dp(4));hit.setTextColor(Color.TRANSPARENT);hit.setContentDescription(label);hit.setPadding(0,0,0,0);hit.setMinHeight(0);hit.setBackground(new RippleDrawable(ColorStateList.valueOf(UiPalette.color(0x334ce1cd)),new DepthSurface(getResources().getDisplayMetrics().density,50,false),null));frame.addView(hit,new FrameLayout.LayoutParams(-1,-1));frame.addView(new LineIcon(this,icon,color),new FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER));return frame;}
  private void toggleRemoteKeyboard(){if(remoteInputPanel!=null)remoteInputPanel.setVisibility(remoteInputPanel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);}
  private int pageAccent() { return TEAL; }
  private int pageTint() { return UiPalette.color(0xff153d40); }
  private int pageBackground() { return BG; }

  private GradientDrawable gradient(int first, int last, int radius) {
    GradientDrawable shape =
        new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {first, last});
    shape.setCornerRadius(dp(radius));
    return shape;
  }

  private void pageIntro(String title, String subtitle) {
    LinearLayout intro = row();
    intro.setPadding(dp(8), dp(8), dp(8), dp(10));
    LinearLayout copy = column();
    TextView heading = text(title, 22, INK);
    heading.setTypeface(null, Typeface.BOLD);
    
    copy.addView(text(subtitle, 12, MUTED));
    intro.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    LinearLayout.LayoutParams introParams = full();
    introParams.bottomMargin = dp(8);
    body.addView(intro, introParams);
    if (!connected()) {
      LinearLayout ribbon = row();
      ribbon.setPadding(dp(13), dp(9), dp(13), dp(9));
      ribbon.setBackground(shape(UiPalette.color(0xff102d31), 12));
      ribbon.addView(text("未连接 · 可浏览与准备", 12, TEAL), new LinearLayout.LayoutParams(0, -2, 1));
      TextView link = text("连接设备  ›", 12, TEAL);
      link.setTypeface(null, Typeface.BOLD);
      link.setPadding(dp(8), dp(6), 0, dp(6));
      link.setOnClickListener(v -> goConnect());
      ribbon.addView(link);
      body.addView(ribbon);
      gap(body, 14);
    }
  }

  private void emptyState(LinearLayout parent, int icon, String title, String description) {
    LinearLayout c = card(parent);
    LinearLayout r = row();
    r.setGravity(Gravity.TOP);
    LineIcon mark = new LineIcon(this, icon, pageAccent());
    LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(28), dp(28));
    ip.rightMargin = dp(13);
    r.addView(mark, ip);
    LinearLayout content = column();
    TextView h = text(title, 15, INK);
    h.setTypeface(null, Typeface.BOLD);
    content.addView(h);
    gap(content, 6);
    content.addView(text(description, 12, MUTED));
    r.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
    c.addView(r);
  }

  private void goConnect() {
    afterConnectPage = page;
    openConnectionManager();
  }

  private boolean requestConnection() {
    if (connected()) return true;
    if (!connectionPromptVisible) {
      connectionPromptVisible = true;
      new DeckDialogBuilder(this)
          .setTitle("连接后即可操作")
          .setMessage("页面内容可以继续浏览。发送按键、读取设备和传输文件需要先连接设备；连接后会回到当前页面。")
          .setNegativeButton("继续浏览", null)
          .setPositiveButton("去连接", (d, w) -> goConnect())
          .setOnDismissListener(d -> connectionPromptVisible = false)
          .show();
    }
    return false;
  }

  private boolean connected() {
    return adb != null && adb.isConnected();
  }

  private void require() throws IOException {
    if (!connected()) throw new IOException("设备已断开，请回到“设备”重新连接");
  }

  private void run(String name, Job job, Runnable done) {
    runAt(name, host, prefs.getString("port", "5555"), job, done);
  }

  private void runAt(String name, String taskHost, String taskPort, Job job, Runnable done) {
    if (isFinishing() || isDestroyed()) return;
    boolean local =
        name.startsWith("连接设备")
            || name.equals("连接诊断")
            || name.startsWith("扫描 ")
            || name.equals("保存截图")
            || name.equals("检查 APK 兼容性")
            || name.equals("读取手机应用")
            || name.equals("检查手机应用")
            || name.equals("读取命令结果")
            || name.equals("导出命令结果")
            || name.equals("准备桌面报告导出")
            || name.equals("导出桌面诊断报告")
            || name.equals("读取上次桌面报告")
            || name.equals("读取 Aerial 官方版本列表")
            || name.equals("准备故障报告导出")
            || name.equals("导出故障报告")
            || name.equals("读取上次故障报告")
            || name.equals("配对无线调试")
            || name.equals("准备兼容报告导出")
            || name.equals("导出兼容报告");
    if (!local && !requestConnection()) return;
    if(!local&&!capabilityAllowed(DeviceCapabilities.featureFor(name)))return;
    final String jobKey = taskHost + ":" + taskPort + ":" + name + (name.equals("读取目录") ? ":" + folder : "");
    if (!pendingJobs.add(jobKey)) {
      toast("该操作正在进行，请稍候");
      return;
    }
    final String taskId;
    try {taskId=journal.enqueue(name,taskHost,taskPort,name.equals("读取目录")?folder:"");}
    catch(Exception e){pendingJobs.remove(jobKey);dialog("任务无法开始",message(e));return;}
    tasks++;
    busy = true;
    root.setKeepScreenOn(true);
    progress.setVisibility(View.VISIBLE);
    status.setText("◌  " + name + "…");
    final String target = taskHost;
    final AdbClient expectedClient = adb;
    ownedTaskIds.add(taskId);
    work.execute(
        () -> {
          String result = "";
          Exception failure = null;
          journal.begin(taskId);
          activeTaskId = taskId;
          try {
            if (!local && (expectedClient == null || expectedClient != adb || !expectedClient.isConnected()))
              throw new IOException("设备连接已变化，任务未执行。请重新连接目标设备后再操作。");
            result = job.run();
          } catch (Exception e) {
            failure = e;
          }
          if(failure instanceof BatchFailure && ((BatchFailure)failure).succeeded>0)journal.finishPartial(taskId,message(failure));
          else journal.finish(taskId, failure == null, failure == null ? result : message(failure));
          if (taskId.equals(activeTaskId)) activeTaskId = null;
          final String r = result;
          final Exception err = failure;
          ui.post(
              () -> {
                if (isFinishing() || isDestroyed()) return;
                ownedTaskIds.remove(taskId);
                pendingJobs.remove(jobKey);
                tasks = Math.max(0, tasks - 1);
                busy = tasks > 0;
                root.setKeepScreenOn(busy);
                progress.setVisibility(busy ? View.VISIBLE : View.GONE);
                if(err==null&&capabilityClient==expectedClient&&capabilitySnapshot!=null){String feature=DeviceCapabilities.featureFor(name);if(feature!=null&&!name.startsWith("检查")&&!name.startsWith("读取"))capabilitySnapshot.success(feature);}
                appendLog(name, target, err == null, r, err);
                status.setText(connected() ? "●  " + device + " · " + host : "○  未连接设备");
                if (name.startsWith("连接设备")) {
                  ui.removeCallbacks(connectionTicker);
                  if (connectionDialog != null) connectionDialog.dismiss();
                  connectionDialog = null;
                  connectingClient = null;
                }
                if (err != null) {
                  if(name.equals("配对无线调试")&&activePairing!=null&&activePairing.cancelled()){activePairing=null;toast("已取消配对");}
                  else if(err instanceof BatchFailure){selected.clear();renderApps();dialog("批量处理结果",message(err));}
                  else if(name.startsWith("连接设备")&&!connectionCancelled&&approvalCause(err)!=null)approvePeer(taskHost,taskPort,approvalCause(err).fingerprint);
                  else if (err instanceof CommandFailure)
                    showCommandResult(((CommandFailure) err).record);
                  else if (!(name.startsWith("连接设备") && connectionCancelled))
                    dialog(name + "未完成", message(err));
                } else {
                  if (done != null) done.run();
                  if (r != null && !r.isEmpty() && r.length() < 100 && !r.startsWith("$ "))
                    toast(r);
                }
              });
        });
  }

  private String message(Exception e) {
    if(e instanceof AerialInstaller.RollbackFailure||e instanceof AerialProbe.Failure)return e.getMessage();
    if (e instanceof ConnectException)
      return "连接被拒绝。请确认电视已开启 ADB 调试，并核对端口；传统端口通常为 5555，但以设备实际设置为准。";
    if (e instanceof SocketTimeoutException) return "连接或响应超时。请检查同一 Wi-Fi、电视授权弹窗，以及路由器是否开启访客隔离。";
    if (e instanceof UnknownHostException) return "找不到设备地址，请核对 IP 地址或主机名。";
    if(e instanceof AdbClient.ShellFailure) {
      AdbClient.ShellFailure f=(AdbClient.ShellFailure)e;
      return f.getMessage()+"\n\n已返回文本（末尾）：\n"+(f.raw.isEmpty()?"（暂无输出）":f.raw.substring(Math.max(0,f.raw.length()-2000)));
    }
    String m = e.getMessage();
    return ApkManifest.installError(m == null ? e.getClass().getSimpleName() : m);
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  private void dialog(String title, String msg) {
    TextView t = text(msg, 14, INK);
    t.setTextIsSelectable(true);
    t.setPadding(dp(20), dp(12), dp(20), dp(12));
    ScrollView sc = new ScrollView(this);
    sc.addView(t);
    new DeckDialogBuilder(this)
        .setTitle(title)
        .setView(sc)
        .setPositiveButton("知道了", null)
        .setNeutralButton("复制", (d, w) -> copy(msg))
        .show();
  }

  private void confirm(String title, String msg, Runnable yes) {
    new DeckDialogBuilder(this)
        .setTitle(title)
        .setMessage(msg)
        .setNegativeButton("取消", null)
        .setPositiveButton("继续", (d, w) -> yes.run())
        .show();
  }

  private void copy(String s) {
    if (copying) {
      toast("正在复制；也可以导出 TXT");
      return;
    }
    copying = true;
    ui.postDelayed(
        () -> {
          if (copying && !isDestroyed()) toast("系统剪贴板响应较慢，可以使用 TXT 导出");
        },
        8000);
    copyWork.execute(
        () -> {
          Exception failure = null;
          try {
            ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("轻连", s));
          } catch (Exception e) {
            failure = e;
          }
          final boolean ok = failure == null;
          ui.post(
              () -> {
                copying = false;
                if (!isFinishing() && !isDestroyed()) toast(ok ? "已复制" : "复制失败，请导出 TXT");
              });
        });
  }

  private void prompt(String title, String value, java.util.function.Consumer<String> accept) {
    EditText e = field("请输入", value);
    LinearLayout l = column();
    l.setPadding(dp(20), dp(5), dp(20), 0);
    l.addView(e);
    AlertDialog a =
        new DeckDialogBuilder(this)
            .setTitle(title)
            .setView(l)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定", null)
            .create();
    a.setOnShowListener(
        d ->
            a.getButton(-1)
                .setOnClickListener(
                    v -> {
                      String s = e.getText().toString().trim();
                      if (s.isEmpty()) {
                        e.setError("不能为空");
                        return;
                      }
                      a.dismiss();
                      accept.accept(s);
                    }));
    a.show();
  }

  private int blend(int a,int b,float t){return Color.rgb((int)(Color.red(a)*(1-t)+Color.red(b)*t),(int)(Color.green(a)*(1-t)+Color.green(b)*t),(int)(Color.blue(a)*(1-t)+Color.blue(b)*t));}
  private void badge(LinearLayout row,String label,int color){TextView t=text(label,11,color);t.setPadding(dp(9),dp(4),dp(9),dp(4));GradientDrawable bg=shape(blend(color,BG,.85f),20);bg.setStroke(dp(1),blend(color,BG,.55f));t.setBackground(bg);row.addView(t);}
  private void recentTasks(){
    LinearLayout c=card(body),r=row();TextView title=text("最近任务",14,INK);title.setTypeface(null,Typeface.BOLD);r.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView more=text("全部记录  ›",12,MUTED);more.setPadding(dp(8),dp(6),0,dp(6));more.setOnClickListener(v->taskCenter());r.addView(more);c.addView(r);
    try{JSONArray logs=new JSONArray(prefs.getString("logs","[]"));gap(c,8);if(logs.length()==0)c.addView(text("还没有操作记录，准备好就开始吧。",12,MUTED));else{JSONObject last=logs.getJSONObject(0);c.addView(text((last.optBoolean("ok")?"✓  ":"!  ")+last.optString("action","最近操作"),13,last.optBoolean("ok")?TEAL:UiPalette.color(0xffffcf80)));}}catch(Exception e){c.addView(text("记录暂不可读，可在任务中心查看。",12,MUTED));}
  }

  private void backgroundHelp() {
    new DeckDialogBuilder(this)
        .setTitle("后台连接")
        .setMessage(
            "连接后会通过前台服务保持会话，通知栏可随时断开。返回应用时会检测连接，失效后重新连接。\n\n"
                + "如果手机系统仍限制后台运行，可在应用设置中允许后台活动。明确退出或手动断开后，不会自动连接。")
        .setNegativeButton("知道了", null)
        .setPositiveButton(
            "应用设置",
            (d, w) ->
                startActivity(
                    new Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))))
        .show();
  }

  private static final class BatchFailure extends IOException {
    final int succeeded,failed;
    BatchFailure(int success,int failure,String report){super(report);succeeded=success;failed=failure;}
  }
  private void settingsMenu(){new DeckDialogBuilder(this).setTitle("设置").setItems(new String[]{"界面与配色","后台连接设置","任务与记录","恢复中心","设备能力报告","连接向导与支持范围","关于轻连","开源组件与许可"},(d,w)->{if(w==0)appearance();else if(w==1)backgroundHelp();else if(w==2)taskCenter();else if(w==3)recovery();else if(w==4)capabilityReport();else if(w==5)connectionWizard();else if(w==6)dialog("关于轻连","版本 "+AppVersion.NAME+"。支持 ADB 与纯红外遥控、应用管理与安装、文件传输和命令回显。电视功能以实际授权为准。支持一键清理应用缓存，不清除用户数据与登录；能否清理取决于电视授权，结果以实际返回为准。");else licenses();}).setNegativeButton("关闭",null).show();}
  private void openConnectionManager(){if(page!=0)show(0);if(connectionPanel!=null){connectionPanel.setVisibility(View.VISIBLE);connectionManager.post(()->{connectionManager.requestRectangleOnScreen(new Rect(0,0,connectionManager.getWidth(),Math.min(connectionManager.getHeight(),dp(300))),true);hostInput.requestFocus();});}}
  private void chooseInstall(String pkg){installExpectedPackage=pkg;new DeckDialogBuilder(this).setTitle(pkg==null?"安装 / 更新": "更新此应用").setItems(new String[]{"选择手机已安装应用","选择 APK 文件"},(d,w)->{if(w==0)phoneApps();else pick(100);}).setNegativeButton("取消",(d,w)->installExpectedPackage=null).setOnCancelListener(d->installExpectedPackage=null).show();}
  private void aerialEntry(LinearLayout parent){LinearLayout c=card(parent);LinearLayout r=row();r.addView(text("Aerial TV",15,INK),new LinearLayout.LayoutParams(0,-2,1));TextView open=button("桌面管理  ›",false,()->aerialCenter());open.setTextSize(12);r.addView(open);c.addView(r);}
  private void aerialCenter(){LinearLayout panel=column();aerialCard(panel);ScrollView scroll=new ScrollView(this);scroll.addView(panel);aerialCenterDialog=new DeckDialogBuilder(this).setTitle("Aerial 桌面管理").setView(scroll).setNegativeButton("关闭",null).create();aerialCenterDialog.show();aerialCenterDialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(20),dp(570)),(int)(getResources().getDisplayMetrics().heightPixels*.82));}
  private void closeAerialCenter(){if(aerialCenterDialog!=null){aerialCenterDialog.dismiss();aerialCenterDialog=null;}}

  private void licenses(){try{StringBuilder text=new StringBuilder();for(String name:new String[]{"THIRD_PARTY_NOTICES.txt","licenses/Apache-2.0.txt","licenses/Conscrypt-NOTICE.txt","licenses/LGPL-3.0.txt","licenses/GPL-3.0.txt","licenses/Flipper-IRDB-CC0-1.0.txt","licenses/Flipper-Official-IRDB-MIT.txt"})try(InputStream in=getAssets().open(name);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);text.append("\n").append(name).append("\n").append(bytes.toString("UTF-8")).append("\n");}dialog("开源组件与许可",text.toString());}catch(Exception e){dialog("许可信息",message(e));}}
  private String peerKey(String host){return AppCatalog.digest(host.trim().toLowerCase(Locale.ROOT));}
  private void connectionWizard(){
    new DeckDialogBuilder(this).setTitle("连接电视 · 选择方式").setItems(new String[]{"Android 电视 / 盒子 · 传统 ADB","配对码无线调试 · 加密 ADB","查找开放 5555 的地址","按品牌查看设置图示","其他电视系统 / 不确定"},(d,w)->{
      if(w==0){prefs.edit().putString("connect_mode","legacy").apply();new DeckDialogBuilder(this).setTitle("传统 ADB 连接").setMessage("① 手机与电视连接同一网络。\n② 在电视设置中找到开发者选项及 ADB / 网络调试。各品牌入口不同，请以本机菜单为准。\n③ 填写电视当前地址与实际端口，再到电视上允许授权。\n\n5555 只是常用端口，不代表所有电视都使用它。若电视显示配对码，请改选加密无线调试。关闭向导后可以继续浏览全部页面。").setNegativeButton("关闭",null).setPositiveButton("填写连接地址",(x,y)->openConnectionManager()).show();}
      else if(w==1)pairingForm(draftHost,"");else if(w==2)discoverDevices();else if(w==3)brandGuide();else supportScope();
    }).setNegativeButton("取消",null).setNeutralButton("支持范围",(d,w)->supportScope()).show();
  }
  private void brandGuide(){
    String[] brands={"小米 / Redmi","海信 / VIDAA / Vidda","TCL / 雷鸟","创维 / 酷开","长虹","康佳","海尔 / 卡萨帝 / 统帅","华为智慧屏","荣耀智慧屏","乐视","联想","暴风","微鲸","PPTV","索尼","三星"};
    new DeckDialogBuilder(this).setTitle("电视调试设置图示").setItems(brands,(d,w)->{
      String[][] steps={
        {"设置 → 关于 → 连按「产品型号」进开发者模式","设置 → 账号与安全 → 打开「ADB 调试」","同时打开「安装未知来源的应用」（如需装应用）","设置 → 网络，记下电视 IP"},
        {"设置 → 声音 → 声音平衡，交替按「确认」「菜单」进工厂菜单","「选项」里把 To Fac 由 U 改成 M，重启电视","设置 → 全部设置，连按「菜单」打开原生设置","关于 → 连按版本号 → 开发者选项 → 打开 USB 调试"},
        {"设置 → 关于 → 本机信息","依次按「上、下、左、右」（间隔不超过 1 秒）","底部出现「更多」，打开 ADB","设置 → 网络，记下电视 IP"},
        {"设置 → 关于本机 → 本机信息，停在这一页","依次按「上、上、下、下、左、右、左、右」进工厂菜单","「通用设置」里把 ADB 设为开","连按返回退出，必要时断电重启"},
        {"按「节目源」键切到任意信源，按「菜单」键","依次按数字键 0、8、1、6 进工厂菜单","在工厂菜单里找到 ADB / USB 调试，设为开","只改 ADB 这一项，其他不要动"},
        {"信号源切到「模拟电视 / TV」","按「菜单」键，用数字键输入 2008","工厂菜单里找到 ADB 开关并打开","部分机型：再到「设置 → 通用设置 → 开发者选项」开一次"},
        {"按电视机身上的「信号源」键，停在信号源菜单","依次按「左、左、上、右」进隐藏菜单","把「ADB 调试开关」设为开","只改 ADB 这一项，不要选「工厂复位」"},
        {"先看「关于本机」里的 HarmonyOS 版本号","4.X 及以下：设置 → 关于本机 → 连点「版本号」7 次","设置 → 系统和更新 → 开发人员选项 → 开「USB 调试」","同时开下方的「启用 ADB」/「HDB 调试」（如有）"},
        null,
        {"按「信号源」键，停在信号源浮动菜单上","依次按数字键 2、5、8、0 呼出工厂菜单","「其他选项」里把 ADB / ADB 调试设为开","退出后断电重启，adb connect 电视 IP:5555"},
        {"不用在电视上开：ADB 5555 出厂就开着","电脑直接 adb connect 电视 IP:5555","注意：连得上，但改不了系统包","较新机型：无可靠专属路径"},
        {"电视切到 HDMI1","快速按「菜单、左、右、上、下、确认」","工厂模式里打开 adbd 开关","只改 adbd 这一项"},
        {"设置 → 安全 → 打开「ADB 调试」","设置 → 网络，记下电视 IP","电脑 adb connect 电视 IP:5555","电视上弹授权框时选「允许」"},
        null,
        {"设置 → 系统 → 关于","连按「Android TV OS build」7 次，直到提示已是开发者","返回 → 系统 → 开发者选项","打开「USB 调试」（老机型：设置 → 设备偏好设置 → 关于 → 连按「Build」7 次 → 开发者选项 → USB 调试）"},
        null
      };
      String[] noGuide={null,null,null,null,null,null,null,null,"本品牌目前无法开启 ADB。",null,null,null,null,"未找到本品牌专属的 ADB 开启方法。",null,"三星电视是 Tizen 系统，没有 ADB，只能用红外遥控（本应用支持纯红外模式）；如需装应用或管理文件，请连接外接的 Android 电视盒子。"};
      String[] notes={
        "小米待机后可能自动关闭 ADB；部分固件每次重启会把 ADB 开关复位，需重新开启。",
        "纯 VIDAA 系统（非安卓）没有 ADB；带数字键的老遥控器可在声音平衡处直接输 1969。",
        "Android 11+ 部分机型只有「无线调试」，第一次先用配对码配对。",
        "恢复出厂设置会把 ADB 开关一起清掉；按序列要缓慢，是「左右左右」不是「左左右右」。",
        "2020 年后 Android 9+ 机型可先试通用法（设置 → 关于本机 → 连点版本号 7 次）；近几年新款有用户反馈固件层面移除了 adb。",
        "ADB 开关在工厂菜单里的位置因机型而异；2008 必须用数字键输入，蓝牙语音遥控器没有数字键，需外接蓝牙键盘。",
        "阿里 YunOS 代（约 2014–2019）没有 ADB；卡萨帝 / 统帅未核实是否与海尔同入口。",
        "能不能开只看版本号：HarmonyOS 4.X 及以下可以；MateTV（HarmonyOS 5.1+）纯鸿蒙，没有 ADB。",
        "荣耀官方客服两次公开答复：智慧屏系列产品不支持 ADB 命令；开发人员选项里没有调试开关。",
        "上面步骤只覆盖 Q85 / CV560 板卡（Android 12）；老款 EUI 未核实；工厂菜单里不要进「屏参调整」和「老化模式」。",
        "较新机型无可靠专属路径；ideatv 连上之后系统包动不了，只有自己装的第三方包能卸。",
        "来源均为 2019–2020 年老帖，其他型号未经核实；暴风已停运，均为存量旧机型。",
        "路径只有一个直接来源；品牌已注销，均为存量旧机型。",
        "未找到 PPTV 专属的 ADB 开启方法；可先试通用安卓路径（设置 → 关于 → 连点版本号 7 次 → 开发者选项 → USB 调试），但未经核实。",
        "新机型按「Android TV OS build」（官方写按 10 次，多按几次也行）；老机型是设置 → 设备偏好设置 → 关于 → 连按「Build」7 次。开启后电视会弹「允许 USB 调试吗？」，点允许；部分固件第一次连 5555 需在电视上确认授权。",
        "三星电视是 Tizen 系统，开发者模式只是给 Tizen Studio 装应用用的，不提供 ADB；ADB 应用管理、装应用、传文件都用不上。本应用支持纯红外遥控，三星用户就用红外遥控。"};
      LinearLayout panel=column();panel.setPadding(dp(20),dp(10),dp(20),dp(10));
      panel.addView(text("以下是操作示意图，不是电视截图。不同型号、系统版本和地区的菜单可能不同，部分机型不提供 ADB。",13,MUTED));
      if(steps[w]==null){gap(panel,8);TextView no=text(noGuide[w],15,INK);no.setPadding(dp(14),dp(14),dp(14),dp(14));no.setBackground(shape(UiPalette.color(0xff203348),14));panel.addView(no,full());}
      else for(int i=0;i<steps[w].length;i++){gap(panel,8);TextView step=text((i+1)+"  "+steps[w][i],15,INK);step.setPadding(dp(14),dp(14),dp(14),dp(14));step.setBackground(shape(UiPalette.color(0xff203348),14));panel.addView(step,full());}
      if(notes[w]!=null){gap(panel,10);panel.addView(text(notes[w],13,TEAL));}
      gap(panel,10);panel.addView(text("不要修改未知工厂设置，进工厂菜单只改 ADB 这一项，其他不要动；授权弹窗以电视实际显示为准。USB 调试不一定同时开启网络 ADB。",12,MUTED));
      ScrollView scroll=new ScrollView(this);scroll.addView(panel);
      new DeckDialogBuilder(this).setTitle(brands[w]+" · 设置示意").setView(scroll).setNegativeButton("关闭",null).setPositiveButton("返回品牌",(x,y)->brandGuide()).show();
    }).setNegativeButton("关闭",null).show();
  }
  private void supportScope(){new DeckDialogBuilder(this).setTitle("轻连支持范围").setMessage("此版面向可开启 ADB 的 Android 电视与盒子。品牌名相同不代表固件与权限相同，连接后会进行只读能力检查。\n\n三星 Tizen、LG webOS 等系统不能直接使用现有 ADB 应用管理；可连接外接的 Android 盒子。\n\n遥控、安装、文件等分别检查。桌面接管和 Aerial 守护属于高级功能，需要当前设备单独验证，不能套用其他机型结论。\n\n当前为多设备内测版，尚不承诺所有品牌型号兼容。不会自动修改电视桌面或开启额外权限。").setNegativeButton("知道了",null).setPositiveButton("返回连接向导",(d,w)->connectionWizard()).show();}
  private void pairingForm(String address,String detectedPort){pairingForm(address,detectedPort,"");}
  private void pairingForm(String address,String detectedPort,String detectedConnectPort){
    LinearLayout panel=column();panel.setPadding(dp(20),dp(6),dp(20),dp(8));panel.addView(text("保持电视“使用配对码配对”窗口打开。配对端口与无线调试页面的连接端口通常不同，请分别填写。",12,MUTED));
    EditText hostField=field("电视地址，不含端口",address),pairPort=field("配对窗口的端口",detectedPort),code=field("六位配对码",""),connectPort=field("无线调试主页的连接端口",detectedConnectPort);pairPort.setInputType(2);connectPort.setInputType(2);code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});panel.addView(hostField);panel.addView(pairPort);panel.addView(code);panel.addView(connectPort);
    AlertDialog modal=new DeckDialogBuilder(this).setTitle("加密无线配对").setView(panel).setNegativeButton("取消",null).setPositiveButton("配对并连接",null).create();modal.setOnShowListener(x->modal.getButton(-1).setOnClickListener(v->{String h=hostField.getText().toString().trim(),p=pairPort.getText().toString(),c=connectPort.getText().toString();int pp,cp;try{pp=Integer.parseInt(p);cp=Integer.parseInt(c);if(h.isEmpty()||h.contains("/")||h.contains(" ")||pp<1||pp>65535||cp<1||cp>65535||!code.getText().toString().matches("[0-9]{6}"))throw new Exception();}catch(Exception e){code.setError("核对地址、两个端口和六位配对码");return;}char[] secret=code.getText().toString().toCharArray();code.setText("");modal.dismiss();pairWireless(h,pp,cp,secret);}));modal.setOnDismissListener(x->code.setText(""));modal.show();
  }
  private void pairWireless(String h,int pairingPort,int connectionPort,char[] code){
    if(busy){Arrays.fill(code,'\0');toast("请等待当前任务完成");return;}
    final WirelessAdb pairing=new WirelessAdb();activePairing=pairing;
    AlertDialog progressDialog=new DeckDialogBuilder(this).setTitle("正在配对电视").setMessage("正在交换配对信息。请保持电视配对码窗口打开；配对码不会保存到记录或诊断报告。").setNegativeButton("取消配对",(d,w)->pairing.cancel()).create();progressDialog.setCancelable(false);pairingDialog=progressDialog;progressDialog.show();
    runAt("配对无线调试",h,String.valueOf(pairingPort),()->{
      try{if(identity==null)identity=new AdbIdentity(new File(getFilesDir(),"identity"));String guid=pairing.pair(h,pairingPort,code,identity,new File(getFilesDir(),"identity"));if(pairing.cancelled())throw new IOException("配对已取消");if(!prefs.edit().remove("tls-pin:"+peerKey(h)).putString("paired-host:"+peerKey(h),guid).putString("host",h).putString("port",String.valueOf(connectionPort)).putString("connect_mode","tls").commit())throw new IOException("配对已返回，但本机记录未保存；请重新核对后连接");return "配对已完成，连接尚待核对";}finally{pairing.close();Arrays.fill(code,'\0');ui.post(()->progressDialog.dismiss());}
    },()->{activePairing=null;if(pairing.cancelled()){toast("已取消配对后的连接");return;}host=h;draftHost=h;draftPort=String.valueOf(connectionPort);show(0);hostInput.setText(h);portInput.setText(String.valueOf(connectionPort));connect(false);});
  }
  private WirelessAdb.ApprovalRequired approvalCause(Throwable error){for(Throwable t=error;t!=null;t=t.getCause())if(t instanceof WirelessAdb.ApprovalRequired)return (WirelessAdb.ApprovalRequired)t;return null;}
  private void approvePeer(String h,String p,String fingerprint){
    if(prefs.getString("paired-host:"+peerKey(h),"").isEmpty()){dialog("需要先配对","此地址没有本机配对记录，请使用连接向导。没有发送控制命令。");return;}
    new DeckDialogBuilder(this).setTitle("核对首次加密连接").setMessage("配对已完成。首次连接还需要确认目标是你刚操作的电视。\n地址："+h+":"+p+"\n\n公钥 SHA-256：\n"+fingerprint+"\n\n确认后记住该身份；后续若身份变化会停止连接，不会静默信任新设备。当前尚未发送遥控或管理命令。").setNegativeButton("取消",null).setPositiveButton("连接此设备",(d,w)->{if(!h.equals(host)||!p.equals(prefs.getString("port","5555"))){dialog("地址已经变化","请重新选择目标电视后连接。");return;}prefs.edit().putString("tls-pin:"+peerKey(h),fingerprint).apply();connect(true);}).show();
  }
  private void discoverDevices(){
    if(busy){toast("请等待当前任务完成");return;}
    AtomicBoolean cancelled=new AtomicBoolean();
    LinearLayout panel=column();panel.setPadding(dp(20),dp(8),dp(20),dp(10));
    TextView status=text("仅扫描手机所在 IPv4 网段；大网段最多扫描当前 /24。正在检查 5555 端口…",13,MUTED);panel.addView(status);
    LinearLayout list=column();ScrollView scroll=new ScrollView(this);scroll.addView(list);
    panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(280),getResources().getDisplayMetrics().heightPixels/3)));
    AlertDialog modal=new DeckDialogBuilder(this).setTitle("查找开放 5555 的地址").setView(panel)
        .setNegativeButton("取消",null).setPositiveButton("手动输入",(d,w)->openConnectionManager()).create();
    modal.setOnDismissListener(d->cancelled.set(true));modal.show();
    final String preferred=prefs.getString("connect_mode","legacy").equals("legacy")?prefs.getString("host",""):"";
    work.execute(()->{
      try{
        LegacySubnetScanner.Result found=LegacySubnetScanner.scan(getApplicationContext(),cancelled,preferred,ip->ui.post(()->{
          if(!modal.isShowing())return;
          status.setText("已发现候选地址，继续扫描实体 Wi-Fi 网段…");
          addButton(list,"候选设备 · "+ip+":5555",false,()->{
            modal.dismiss();prefs.edit().putString("connect_mode","legacy").apply();
            draftHost=ip;draftPort="5555";show(0);hostInput.setText(ip);portInput.setText("5555");connect(false);
          });
        }),scope->ui.post(()->{if(modal.isShowing())status.setText("仅扫描手机所在网段（大网段最多当前 /24） · "+scope+" · 端口 5555…");}));
        ui.post(()->{
          if(!modal.isShowing())return;
          if(found.hosts.isEmpty())status.setText("扫描 "+found.scope+" 未发现开放 5555 的地址。请核对电视 IP，或手动输入实际端口。");
          else status.setText("扫描 "+found.scope+" 找到 "+found.hosts.size()+" 个候选地址。点选后核验 ADB。");
        });
      }catch(Exception e){ui.post(()->{if(modal.isShowing())status.setText("扫描无法完成："+message(e)+" 请手动输入电视地址。");});}
    });
  }
  private DeviceCapabilities.Snapshot inspectCapabilities(AdbClient target){return DeviceCapabilities.inspect(new DeviceCapabilities.Device(){public AdbClient.Result shell(String c)throws Exception{return target.shell(c,6000);}public boolean connected(){return target.isConnected();}},target.isSecure()?"加密无线 ADB":"传统 ADB");}
  private boolean capabilityAllowed(String feature){
    if(!connected()||capabilityClient!=adb||capabilitySnapshot==null||feature==null)return true;
    if(capabilitySnapshot.blocked(feature)){DeviceCapabilities.Item item=capabilitySnapshot.items.get(feature);dialog("当前设备未提供此接口",item.name+"\n"+item.reason+"\n\n可在设备能力报告中重新检测。不会尝试替换其他品牌设置。");return false;}
    if(feature.equals(DeviceCapabilities.INSTALL)&&capabilitySnapshot.user!=0){dialog("当前用户尚未确认","此版需要确认 Android 用户 0 后安装。请重新检测；未继续传输。");return false;}return true;
  }
  private void capabilityReport(){
    if(capabilityClient!=adb||capabilitySnapshot==null){if(!requestConnection())return;refreshCapabilities();return;}
    final DeviceCapabilities.Snapshot snapshot=capabilitySnapshot;TextView text=text((connected()?"":"上次连接结果，仅供回看；当前未验证。\n\n")+snapshot.display(),13,INK);text.setTextIsSelectable(true);text.setPadding(dp(20),dp(8),dp(20),dp(8));ScrollView scroll=new ScrollView(this);scroll.addView(text);new DeckDialogBuilder(this).setTitle("设备能力报告").setView(scroll).setNegativeButton("关闭",null).setNeutralButton("导出脱敏报告",(d,w)->previewCompatibilityExport(snapshot)).setPositiveButton("重新检测",(d,w)->refreshCapabilities()).show();
  }
  private void refreshCapabilities(){if(!requestConnection())return;final AdbClient target=adb;final DeviceCapabilities.Snapshot[] value={null};run("检测设备能力",()->{value[0]=inspectCapabilities(target);return "";},()->{if(adb!=target)return;capabilitySnapshot=value[0];capabilityClient=target;if(ConnectionService.session!=null&&ConnectionService.session.client==target)ConnectionService.session.capabilities=value[0];show(page);capabilityReport();});}
  private File compatibilityPending(){return new File(getFilesDir(),"compatibility_pending.json");}
  private void previewCompatibilityExport(DeviceCapabilities.Snapshot snapshot){try{String report=snapshot.export();new DeckDialogBuilder(this).setTitle("诊断报告导出预览").setMessage(report+"\n\n包含型号、品牌、系统与功能检查结果；不包含地址、序列号、配对码、公钥或原始命令输出。仅在你选择保存后写出文件，不会自动上传。").setNegativeButton("取消",null).setPositiveButton("选择保存位置",(d,w)->run("准备兼容报告导出",()->{writeDesktopFile(compatibilityPending(),report);ui.post(()->createDocument(108,"application/json","LinkDeck_compatibility.json"));return "";},null)).show();}catch(Exception e){dialog("报告暂不可用",message(e));}}

  private void home() {
    LinearLayout hero=card(body);hero.setBackground(new GlassSurface(hero,(AuroraLayout)root,24,true));LinearLayout top=row();
    top.addView(new TvScene(this),new LinearLayout.LayoutParams(dp(112),dp(112)));
    LinearLayout info=column();info.setPadding(dp(12),dp(2),0,0);TextView name=text(connected()?"客厅电视":"连接你的电视",17,INK);name.setTypeface(null,Typeface.BOLD);info.addView(name);gap(info,7);LinearLayout badge=row();badge(badge,connected()?"已连接":"等待连接",connected()?TEAL:UiPalette.color(0xffffcf80));info.addView(badge);gap(info,8);
    info.addView(text(connected()?device:"让电视，触手可及",12,MUTED));gap(info,4);TextView addressHint=text(connected()?host+":"+prefs.getString("port","5555"):"手机与电视需在同一网络",11,MUTED);addressHint.setSingleLine(true);addressHint.setEllipsize(TextUtils.TruncateAt.MIDDLE);info.addView(addressHint);top.addView(info,new LinearLayout.LayoutParams(0,-2,1));hero.addView(top);
    TextView openRemote=button(connected()?"打开遥控":"查找电视",false,()->{if(connected())show(1);else discoverDevices();});openRemote.setTextColor(AuroraStyle.PRIMARY_TEXT);GradientDrawable openBg=gradient(UiPalette.color(0xff195252),UiPalette.color(0xff102332),24);openBg.setStroke(dp(1),UiPalette.color(0xff327f84));openRemote.setBackground(new RippleDrawable(ColorStateList.valueOf(UiPalette.color(0x334ce1cd)),new DepthSurface(getResources().getDisplayMetrics().density,24,false,true),null));LinearLayout.LayoutParams op=full();op.topMargin=dp(8);hero.addView(openRemote,op);
    if(connected()){addButton(hero,"切换电视",false,()->deviceBook());gap(hero,9);TextView keep=text(ConnectionService.backgroundAvailable?"●  后台保持连接 · 点击查看设置":"●  仅当前连接 · 后台保持未启用",11,TEAL);keep.setOnClickListener(v->backgroundHelp());hero.addView(keep);}
    if (pendingInstall != null) {
      LinearLayout pending = card(body);
      section(pending, "已准备一个安装包", "连接后会重新检查系统与架构，再由你确认安装。");
      addButton(
          pending,
          "取消待安装任务",
          false,
          () -> {
            pendingInstall.delete();
            pendingInstall = null;
            show(0);
          });
    }
    recentTasks();
    LinearLayout manager=card(body);LinearLayout c=column();
    TextView expand=button(connected()?"连接管理  ﹀":"连接设置  ﹀",false,()->{});manager.addView(expand);gap(manager,8);manager.addView(c);c.setVisibility(connected()?View.GONE:View.VISIBLE);
    expand.setOnClickListener(v->{boolean open=c.getVisibility()!=View.VISIBLE;c.setVisibility(open?View.VISIBLE:View.GONE);expand.setText(open?"收起连接设置  ﹀":"连接管理  ›");});
    section(c, connected() ? "连接信息" : "连接你的设备", prefs.getString("connect_mode","legacy").equals("tls")?"加密无线连接模式：请填写连接端口，不能填配对端口。":"传统 ADB 模式：填写设备实际端口；新设备可使用连接向导配对。");
    hostInput = field("电视 IP 地址 / 主机名", draftHost);
    portInput = field("端口", draftPort);
    portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    LinearLayout address = row();
    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, -2, 1);
    hp.rightMargin = dp(8);
    address.addView(hostInput, hp);
    address.addView(portInput, new LinearLayout.LayoutParams(dp(84), -2));
    c.addView(address);
    addButton(c, connected() ? "重新连接" : "连接设备", true, () -> connect(false));
    pair(
        c,
        "查找同网段地址",
        () -> discoverDevices(),
        "连接帮助",
        () ->
            dialog(
                "连接帮助",
                "① 手机和电视连接同一路由器。\n"
                    + "② 在电视开发者选项开启 ADB/网络调试。\n"
                    + "③ 填入电视 IP 和实际端口。\n"
                    + "④ 连接时在电视上允许调试授权。\n\n"
                    + "旧电视不支持配对码也可以使用传统 TCP ADB。\n"
                    + "配对码设备请使用连接向导中的加密配对；本版未提供 USB OTG。\n\n"
                    + "发现不了设备时请手动填写；设备可能不在手机所在网段，或路由器启用了隔离。"));
    pair(c, "保存 / 管理地址", () -> deviceBook(), "连接诊断", () -> diagnoseAddress());
    addButton(c,"配对码与连接帮助",false,()->connectionWizard());
    JSONArray savedDevices = DeviceBook.sorted(savedDevices());
    if (savedDevices.length() > 0) {
      gap(c, 12); c.addView(text("快速连接", 12, MUTED));
      for (int i=0;i<Math.min(3,savedDevices.length());i++) {
        JSONObject o=savedDevices.optJSONObject(i);
        if(o==null)continue;
        addButton(c,(o.optBoolean("pinned")?"★ ":"")+DeviceBook.label(o)+" · "+o.optString("host")+":"+o.optString("port"),false,()->connectSaved(o));
      }
    }
    connectionPanel=c;connectionManager=manager;
    if(!connected()){body.removeView(manager);body.addView(manager,1);}
    LinearLayout note = card(body);

    if(connected())addButton(note,"断开连接",false,()->{
      if(busy){toast("请等待当前任务完成");return;}
      ConnectionService.disconnect(this);
      adb=null;capabilityClient=null;capabilitySnapshot=null;appLoaded=false;
      show(0);
      toast("已断开，轻连仍保持打开");
    });
    if(connected())gap(note, 2);else body.removeView(note);
  }

  private void connect(boolean reuseAddress) {
    if (busy) {
      toast("请等待当前任务完成");
      return;
    }
    String rawHost = reuseAddress ? prefs.getString("host", "") : hostInput.getText().toString().trim();
    String rawPort =
        reuseAddress ? prefs.getString("port", "5555") : portInput.getText().toString().trim();
    final int port;final String h,p;
    try {
      ConnectionAddress address=ConnectionAddress.parse(rawHost,rawPort);h=address.host;port=address.port;p=String.valueOf(port);
    } catch (Exception e) {
      dialog("地址需要检查", "填写设备 IP 或主机名和端口 1–65535；也可以粘贴 IP:端口。IPv6 地址带端口时使用 [地址]:端口。不要带 http://。");
      return;
    }
    prefs.edit().putBoolean("resume_connection", false).apply();
    if (adb != null) adb.close();
    host = h;
    device = "设备";
    appLoaded = false;
    selected.clear();
    iconsPending.clear();
    iconsFailed.clear();
    metadataLoading = false;
    catalogDevice = "";
    properties.clear();
    prefs.edit().putString("host", h).putString("port", p).apply();
    capabilitySnapshot=null;capabilityClient=null;
    connectionCancelled = false;
    connectionStarted = SystemClock.elapsedRealtime();
    connectionStage = "正在准备本机授权密钥…";
    connectionDetail = text(connectionStage, 15, INK);
    connectionDetail.setPadding(dp(24), dp(16), dp(24), dp(16));
    connectionDialog =
        new DeckDialogBuilder(this)
            .setTitle("连接 " + h + ":" + port)
            .setView(connectionDetail)
            .setNegativeButton(
                "取消连接",
                (d, w) -> {
                  connectionCancelled = true;
                  AdbClient pending = connectingClient;
                  if (pending != null) pending.close();
                  toast("已取消连接");
                })
            .create();
    connectionDialog.setCancelable(false);
    connectionDialog.show();
    ui.post(connectionTicker);
    run(
        "连接设备（请留意电视授权弹窗）",
        () -> {
          boolean secure=prefs.getString("connect_mode","legacy").equals("tls");
          if(secure&&prefs.getString("paired-host:"+peerKey(h),"").isEmpty())throw new IOException("此地址没有配对记录，请使用连接向导先配对");
          AdbClient next = secure?new AdbClient(WirelessAdb.transport(new File(getFilesDir(),"identity"),prefs.getString("tls-pin:"+peerKey(h),""))):new AdbClient();
          connectingClient = next;
          try {
            if (identity == null) identity = new AdbIdentity(new File(getFilesDir(), "identity"));
            if (connectionCancelled) throw new IOException("连接已取消");
            next.connect(
                h,
                port,
                identity,
                stage ->
                    ui.post(
                        () -> {
                          if (connectionDialog != null && !connectionCancelled)
                            connectionStage = stage;
                        }));
            if (connectionCancelled) throw new IOException("连接已取消");
            ui.post(() -> connectionStage = "授权成功，正在读取电视信息…");
            adb = next;
            String raw = next.shell("getprop").require();
            for (String l : raw.split("\n")) {
              if (l.startsWith("[") && l.contains("]: [")) {
                int at = l.indexOf("]: [");
                properties.put(l.substring(1, at), l.substring(at + 4, l.length() - 1));
              }
            }
            device =
                properties.containsKey("ro.product.model") ? properties.get("ro.product.model") : h;
            try {
              deviceSdk = Integer.parseInt(properties.get("ro.build.version.sdk"));
            } catch (Exception ignored) {
              deviceSdk = 0;
            }
            deviceAbi =
                properties.containsKey("ro.product.cpu.abilist")
                    ? properties.get("ro.product.cpu.abilist")
                    : properties.get("ro.product.cpu.abi");
            if (connectionCancelled) throw new IOException("连接已取消");
            ui.post(()->connectionStage="已授权，正在进行只读能力检查…");
            capabilitySnapshot=inspectCapabilities(next);capabilityClient=next;
            if(connectionCancelled||!next.isConnected())throw new IOException("连接已取消或检测期间设备断开");
            rememberDevice(h, p, device);
            return "连接成功";
          } catch (Exception e) {
            next.close();
            throw e;
          }
        },
        () -> {
          applyDeviceInfo(p);
          try {
            boolean backgroundReady = ConnectionService.attach(this, adb, h, p, device, properties);
            if (!backgroundReady) ui.post(() -> dialog("后台连接未启用", "系统暂不允许启动后台服务；当前页面仍可使用连接，离开应用后连接可能中断。"));
            if(ConnectionService.session!=null)ConnectionService.session.capabilities=capabilitySnapshot;
            if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                && !prefs.getBoolean("notificationAsked", false)) {
              prefs.edit().putBoolean("notificationAsked", true).apply();
              requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 41);
            }
          } catch (Exception e) {
            dialog("后台连接未启用", "当前连接可以使用，但后台服务启动失败：" + message(e));
          }
          catalogMessage = "应用名称与图标会按需加载";
          int dest = afterConnectPage;
          afterConnectPage = -1;
          show(dest >= 0 ? dest : 0);
          consumeShared();
          if (pendingPhoneApp != null) {
            String pkg = pendingPhoneApp;
            pendingPhoneApp = null;
            inspectPhoneApp(pkg);
          }
          if (pendingInstall != null) {
            File file = pendingInstall;
            pendingInstall = null;
            inspectApk(Uri.fromFile(file));
          }
        });
  }

  private boolean deviceBookUnavailable=false,deviceBookNotice=false;
  private synchronized JSONArray savedDevices(){
    String raw=prefs.getString("devices","[]");
    try{
      DeviceBook.ReadResult result=DeviceBook.read(raw,getFilesDir(),()->{if(prefs.edit().putString("devices","[]").commit())return true;prefs.edit().putString("devices",raw).commit();return false;});
      deviceBookUnavailable=false;
      if(result.recovered&&!deviceBookNotice){deviceBookNotice=true;ui.post(()->dialog("设备列表提示","已保存设备损坏，已清空。损坏数据已备份在应用私有目录；请重新添加设备。"));}
      return result.entries;
    }catch(Exception failure){deviceBookUnavailable=true;if(!deviceBookNotice){deviceBookNotice=true;ui.post(()->dialog("设备列表无法读取","损坏数据未成功处理，原始数据或备份仍保留。请检查手机存储空间后重试。"));}return new JSONArray();}
  }

  private void rememberDevice(String h, String p, String n) {
    try {
      JSONArray old=savedDevices();
      if(deviceBookUnavailable)return;
      if(old.length()>=20 && DeviceBook.find(old,h,p)==null) return;
      JSONArray saved=DeviceBook.save(old,h,p,n,null,true);JSONObject entry=DeviceBook.find(saved,h,p);if(entry!=null)entry.put("transport",prefs.getString("connect_mode","legacy"));prefs.edit().putString("devices",saved.toString()).apply();
    } catch(Exception ignored) {}
  }

  private void consumeShared() {
    if (sharedUri != null) {
      Uri u = sharedUri;
      sharedUri = null;
      inspectApk(u);
    }
  }

  private void deviceDetails() {
    if (!requestConnection()) return;
    StringBuilder s = new StringBuilder();
    String[][] fields = {
      {"型号", "ro.product.model"},
      {"品牌", "ro.product.brand"},
      {"Android", "ro.build.version.release"},
      {"SDK", "ro.build.version.sdk"},
      {"CPU 架构", "ro.product.cpu.abilist"},
      {"主架构", "ro.product.cpu.abi"},
      {"安全补丁", "ro.build.version.security_patch"},
      {"系统版本", "ro.build.display.id"}
    };
    for (String[] f : fields)
      s.append(f[0])
          .append("：")
          .append(properties.containsKey(f[1]) ? properties.get(f[1]) : "未知")
          .append("\n");
    dialog("设备详情", s.toString());
  }

  private boolean irAvailable(String key){IrCodes profile=activeIr();return profile!=null&&profile.has(key);}
  private void markIrKey(View view,String key){
    if(!irMode())return;
    if(irAvailable(key))return;
    view.setEnabled(false);view.setAlpha(.38f);
    View target=view instanceof FrameLayout && ((FrameLayout)view).getChildCount()>0?((FrameLayout)view).getChildAt(0):view;target.setEnabled(false);
    CharSequence label=target.getContentDescription();
    if((label==null||label.length()==0)&&target instanceof TextView)label=((TextView)target).getText();
    target.setContentDescription((label==null?key:label)+"，当前码型没有此按键");
  }
  private void remote() {
    LinearLayout mode=row();
    String[] modes={"ir","adb"},modeLabels={"红外遥控","网络遥控"};
    for(int i=0;i<modes.length;i++){final String value=modes[i];boolean selectedMode=remoteMode().equals(value);LinearLayout.LayoutParams slot=new LinearLayout.LayoutParams(0,-2,1);slot.setMargins(i==0?0:dp(3),0,i==modes.length-1?0:dp(3),0);TextView choice=button(modeLabels[i]+(selectedMode?" ✓":""),selectedMode,()->{prefs.edit().putString("remote_mode",value).putBoolean("ir_mode",!value.equals("adb")).apply();show(1);});choice.setContentDescription(modeLabels[i]+(selectedMode?"，当前模式":"，切换到此模式"));choice.setSelected(selectedMode);mode.addView(choice,slot);}
    body.addView(mode);gap(body,12);
    if(irMode()&&activeIr()==null)addButton(body,"选择电视品牌与遥控码",true,()->chooseIrBrand());
    LinearLayout toolbar=row();TextView heading=text("方向控制",13,MUTED);toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));android.widget.Switch large=new android.widget.Switch(this);large.setText("大按键");large.setTextColor(MUTED);large.setTextSize(12);large.setChecked(prefs.getBoolean("largeRemote",false));large.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("largeRemote",on).apply();show(1);});toolbar.addView(large);body.addView(toolbar);gap(body,12);
    FrameLayout pad=new FrameLayout(this);pad.setAddStatesFromChildren(true);pad.setBackground(new RemoteFace());int maximum=Math.min(getResources().getDisplayMetrics().widthPixels-dp(36),dp(prefs.getBoolean("largeRemote",false)?344:280));int diameter=Math.min(maximum,Math.max(dp(230),(int)(getResources().getDisplayMetrics().heightPixels*(prefs.getBoolean("largeRemote",false)?.44f:.38f))));
    TextView center=button("确定",true,()->remoteKey(23));center.setContentDescription("确认");center.setTextSize(19);center.setPadding(0,0,0,0);RemoteKeyArt core=new RemoteKeyArt(-1);center.setTextColor(INK);center.setBackground(new RippleDrawable(ColorStateList.valueOf(UiPalette.color(0x5554dfca)),core,null));markIrKey(center,"OK");FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(94),dp(94),Gravity.CENTER);pad.addView(center,cp);
    String[] labels={"⌃","⌄","‹","›"};String[] descriptions={"向上","向下","向左","向右"};int[] codes={19,20,21,22};int[] positions={Gravity.TOP|Gravity.CENTER_HORIZONTAL,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,Gravity.LEFT|Gravity.CENTER_VERTICAL,Gravity.RIGHT|Gravity.CENTER_VERTICAL};
    String[] irDirections={"UP","DOWN","LEFT","RIGHT"};
    for(int i=0;i<4;i++){TextView key=button(labels[i],false,()->{});key.setTextColor(Color.TRANSPARENT);key.setTextSize(34);key.setPadding(0,0,0,0);key.setContentDescription(descriptions[i]);key.setBackground(new RippleDrawable(ColorStateList.valueOf(UiPalette.color(0x2256e5d1)),new RemoteKeyArt(i),null));repeatKey(key,codes[i]);markIrKey(key,irDirections[i]);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(66),dp(66),positions[i]);lp.setMargins(dp(7),dp(4),dp(7),dp(4));pad.addView(key,lp);}
    LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(diameter,diameter);pp.gravity=Gravity.CENTER_HORIZONTAL;body.addView(pad,pp);gap(body,12);
    int actionDiameter=Math.min((getResources().getDisplayMetrics().widthPixels-dp(28))/3,dp(76+28*Math.max(0,getResources().getConfiguration().fontScale-1)));
    LinearLayout actions=row();String[] names={"返回","主页","菜单"};String[] irActions={"BACK","HOME","MENU"};int[] keycodes={4,3,82};for(int i=0;i<3;i++){final int code=keycodes[i];LinearLayout key=column();key.setGravity(Gravity.CENTER);key.setPadding(dp(8),dp(12),dp(8),dp(10));DepthSurface bg=new DepthSurface(getResources().getDisplayMetrics().density,50,false);key.setBackground(new RippleDrawable(ColorStateList.valueOf(UiPalette.color(0x2256e5d1)),bg,null));key.addView(new LineIcon(this,5+i,INK),new LinearLayout.LayoutParams(dp(25),dp(25)));gap(key,5);TextView caption=text(names[i],12,INK);caption.setGravity(Gravity.CENTER);key.addView(caption);caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);key.setContentDescription(names[i]);pressMotion(key);key.setFocusable(true);key.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());}});key.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);remoteKey(code);});key.setElevation(dp(3));markIrKey(key,irActions[i]);FrameLayout slot=new FrameLayout(this);FrameLayout.LayoutParams circle=new FrameLayout.LayoutParams(actionDiameter,actionDiameter,Gravity.CENTER);slot.addView(key,circle);actions.addView(slot,new LinearLayout.LayoutParams(0,actionDiameter+dp(8),1));}body.addView(actions);gap(body,10);
    LinearLayout volume=card(body);volume.setPadding(dp(12),dp(10),dp(12),dp(9));volume.setBackground(new GlassSurface(volume,(AuroraLayout)root,28,false));TextView volumeTitle=text("音量",12,MUTED);volumeTitle.setGravity(Gravity.CENTER);volume.addView(volumeTitle);LinearLayout volumeRow=row();VolumeGlide glide=new VolumeGlide(this,()->!irMode()?requestConnection():irAvailable("VOL_UP")&&irAvailable("VOL_DOWN"),dir->remoteKey(dir>0?24:25),()->motionEnabled());volumeGlide=glide;View minus=iconButton("减小音量",12,INK,()->{if(irMode()||requestConnection()){remoteKey(25);glide.feedback(-1);}});repeatVolume((TextView)((FrameLayout)minus).getChildAt(0),25,()->glide.feedback(-1));markIrKey(minus,"VOL_DOWN");volumeRow.addView(minus,new LinearLayout.LayoutParams(dp(44),dp(44)));volumeRow.addView(glide,new LinearLayout.LayoutParams(0,dp(46),1));View plus=iconButton("增大音量",13,INK,()->{if(irMode()||requestConnection()){remoteKey(24);glide.feedback(1);}});repeatVolume((TextView)((FrameLayout)plus).getChildAt(0),24,()->glide.feedback(1));markIrKey(plus,"VOL_UP");volumeRow.addView(plus,new LinearLayout.LayoutParams(dp(44),dp(44)));volume.addView(volumeRow);TextView volumeHint=text("左右拖动调节 · 松手回中",10,MUTED);volumeHint.setGravity(Gravity.CENTER);volume.addView(volumeHint);if(irMode()&&(!irAvailable("VOL_UP")||!irAvailable("VOL_DOWN"))){glide.setEnabled(false);glide.setAlpha(.38f);volumeHint.setText("滑动音量需同时具备音量＋和音量－");}
    if(irMode()){
      LinearLayout extras=row();TextView mute=button("静音",false,()->irSend("MUTE"));markIrKey(mute,"MUTE");
      LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(0,-2,1);ml.rightMargin=dp(5);extras.addView(mute,ml);
      TextView source=button("信号源",false,()->irSend("SOURCE"));markIrKey(source,"SOURCE");
      LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(0,-2,1);sl.leftMargin=dp(5);extras.addView(source,sl);
      LinearLayout.LayoutParams mp=full();mp.topMargin=dp(8);body.addView(extras,mp);gap(body,12);infraredRemote();return;
    }
    LinearLayout shortcuts=row();body.addView(shortcuts);gap(body,12);
    LinearLayout input = card(body);remoteInputPanel=input;input.setVisibility(View.GONE);final ScrollView inputScroll=pageScroll;input.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->{if(v.getVisibility()==View.VISIBLE&&b>t&&b!=ob)inputScroll.post(()->{if(input.isAttachedToWindow()&&input.getVisibility()==View.VISIBLE)inputScroll.smoothScrollTo(0,input.getBottom());});});
    TextView keyboard=button("键盘输入",false,()->toggleRemoteKeyboard());
    LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(0,-2,1);kp.rightMargin=dp(5);shortcuts.addView(keyboard,kp);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=dp(5);shortcuts.addView(button("截取设备屏幕",false,()->capture()),sp);
    section(input, "输入文字", "直接输入仅支持英文、数字及部分符号；中文需设备端输入法支持，本版暂不提供。");
    EditText e = field("例如：youtube 或搜索关键词", draftRemote);
    remoteInput = e;
    input.addView(e);
    addButton(
        input,
        "发送到设备",
        true,
        () -> {
          String value = e.getText().toString();
          if (value.isEmpty()) return;
          if (!value.matches("[A-Za-z0-9 _.,:/@+?=\\-]*") || value.contains("%")) {
            dialog("暂不支持这些字符", "请使用英文、数字或常见符号。中文输入将在后续适配设备端输入法。");
            return;
          }
          run(
              "输入文字",
              () -> {
                require();
                return adb.shell("input text " + AdbClient.quote(value.replace(" ", "%s")))
                        .require()
                        .isEmpty()
                    ? "已发送"
                    : "已发送";
              },
              null);
        });
  }

  private String remoteMode(){String mode=prefs.getString("remote_mode",prefs.getBoolean("ir_mode",false)?"ir":"adb");return "hybrid".equals(mode)?"ir":mode;}
  private boolean irMode(){return prefs!=null&&!remoteMode().equals("adb");}
  private File irProfileFile(){return new File(getFilesDir(),"remote-profile.ir");}
  private File irProfileFile(String choice){
    String id=choice.substring(7);
    if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("无效遥控码编号");
    return new File(new File(getFilesDir(),"ir-profiles"),id+".ir");
  }
  private byte[] irBytes(File file) throws IOException{
    long length=file.length();if(length<=0||length>65536)throw new IOException("红外文件大小无效");
    byte[] bytes=new byte[(int)length];
    try(FileInputStream in=new FileInputStream(file)){int off=0,n;while(off<bytes.length&&(n=in.read(bytes,off,bytes.length-off))>0)off+=n;if(off!=bytes.length)throw new IOException("红外文件不完整");}
    return bytes;
  }
  private byte[] irBytes(String choice) throws IOException{
    if(choice.startsWith("custom:"))return irBytes(irProfileFile(choice));
    if(choice.equals("file"))return irBytes(irProfileFile());
    if(choice.startsWith("catalog:")){
      String id=choice.substring(8);IrCatalog.Entry entry=new IrCatalog(getAssets()).find(id);
      if(entry==null)throw new IOException("找不到遥控方案");
      try(InputStream in=getAssets().open("ir/"+entry.file+".ir");ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>65536)throw new IOException("红外文件过大");}return out.toByteArray();
      }
    }
    throw new IOException("当前方案不可导出");
  }
  private Set<String> savedIrChoices(){
    Set<String> out=new LinkedHashSet<>(prefs.getStringSet("ir_saved_choices",Collections.<String>emptySet()));
    String selected=prefs.getString("ir_profile","");if(selected.startsWith("custom:")||selected.startsWith("catalog:")||selected.equals("file"))out.add(selected);
    return out;
  }
  private void rememberIrChoice(String choice){Set<String> saved=savedIrChoices();saved.add(choice);prefs.edit().putStringSet("ir_saved_choices",saved).apply();}
  private String irChoiceName(String choice){
    if(choice.startsWith("catalog:"))try{IrCatalog.Entry entry=new IrCatalog(getAssets()).find(choice.substring(8));if(entry!=null)return entry.brand+" · "+entry.model;}catch(Exception ignored){}
    if(choice.startsWith("custom:"))return prefs.getString("ir_name_"+choice.substring(7),"自定义遥控码");
    if(choice.equals("file"))return "旧版导入方案";
    return "未知方案";
  }
  private String irVerified(String choice){
    String value=prefs.getString("ir_verified_choice_"+choice,null);
    if(value==null&&choice.startsWith("catalog:"))value=prefs.getString("ir_verified_"+choice.substring(8),"");
    return value==null?"":value;
  }
  private Set<String> irFailed(String choice){return IrVerification.parse(prefs.getString("ir_failed_choice_"+choice,""));}
  private void savedIrProfiles(){
    List<String> choices=new ArrayList<>(savedIrChoices());
    if(choices.isEmpty()){dialog("尚无已保存方案","先按品牌匹配或导入红外码。");return;}
    String selected=prefs.getString("ir_profile","");
    String[] names=new String[choices.size()];for(int i=0;i<choices.size();i++)names[i]=(choices.get(i).equals(selected)?"✓  ":"    ")+irChoiceName(choices.get(i));
    new DeckDialogBuilder(this).setTitle("已保存的红外方案").setItems(names,(d,which)->{
      String choice=choices.get(which);
      new DeckDialogBuilder(this).setTitle(irChoiceName(choice))
        .setPositiveButton("切换使用",(q,w)->{try{IrCodes parsed=IrCodes.fromFlipper(new String(irBytes(choice),"UTF-8"));if(prefs.edit().putString("ir_profile",choice).commit()){irProfile=parsed;show(1);}}catch(Exception e){dialog("无法切换方案",message(e));}})
        .setNeutralButton("导出 .ir",(q,w)->exportIrProfile(choice))
        .setNegativeButton("移出列表",(q,w)->new DeckDialogBuilder(this).setTitle("移出已保存方案？")
          .setMessage(choice.equals(prefs.getString("ir_profile",""))?"这是当前使用的方案。移出后遥控将等待你选择另一方案；码文件仍保留在手机中。":"仅移出列表，码文件仍保留在手机中。")
          .setPositiveButton("移出",(a,b)->{Set<String> saved=savedIrChoices();saved.remove(choice);
            android.content.SharedPreferences.Editor edit=prefs.edit().putStringSet("ir_saved_choices",saved);
            if(choice.equals(prefs.getString("ir_profile",""))){edit.putString("ir_profile","");irProfile=null;}
            edit.apply();show(1);})
          .setNegativeButton("取消",null).show()).show();
    }).setNegativeButton("关闭",null).show();
  }
  private void exportIrProfile(String choice){
    if(choice.isEmpty()||choice.equals("trial")){dialog("尚无可导出方案","请先选择或导入红外码。");return;}
    pendingIrExportChoice=choice;
    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/octet-stream");
    intent.putExtra(Intent.EXTRA_TITLE,"LinkDeck-"+irChoiceName(choice).replaceAll("[^A-Za-z0-9一-龥_-]","_")+".ir");startActivityForResult(intent,110);
  }
  private String irVerificationReport(String choice) throws IOException{
    IrCodes codes=IrCodes.fromFlipper(new String(irBytes(choice),"UTF-8"));
    Set<String> verified=IrVerification.parse(irVerified(choice)),failed=irFailed(choice);
    StringBuilder report=new StringBuilder("轻连红外按键验证结果\n记录方式：用户观察电视反应并手动确认；未独立检测电视接收结果。\n无设备地址、序列号或网络标识。\n\n");
    for(String key:IrVerification.REQUIRED){
      String status=!codes.has(key)?"码文件缺少":verified.contains(key)?"已确认有效":failed.contains(key)?"已确认无效":"未验证";
      report.append(IrVerification.displayName(key)).append("：").append(status).append('\n');
    }
    if(codes.has("SOURCE"))report.append("信号源：码文件提供，尚未纳入逐键验证\n");
    return report.toString();
  }
  private void exportIrVerification(String choice){
    if(choice.isEmpty()||choice.equals("trial")){dialog("尚无可导出方案","请先选择或导入红外码。");return;}
    pendingIrReportChoice=choice;
    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("text/plain");
    intent.putExtra(Intent.EXTRA_TITLE,"QingLian-IR-Keys.txt");startActivityForResult(intent,111);
  }
  private IrCodes activeIr(){
    if(irProfile!=null)return irProfile;
    String choice=prefs.getString("ir_profile","");
    if(choice.equals("trial"))return irProfile=IrCodes.skyworthQPlus();
    if(choice.startsWith("catalog:"))try{
      IrCatalog catalog=new IrCatalog(getAssets());
      return irProfile=catalog.load(catalog.find(choice.substring(8)));
    }catch(Exception e){toast("已保存的红外码无法读取，请重新匹配");return null;}
    if(choice.equals("file")||choice.startsWith("custom:"))try{
      byte[] bytes=irBytes(choice);
      return irProfile=IrCodes.fromFlipper(new String(bytes,"UTF-8"));
    }catch(Exception e){toast("红外码文件无法读取，请重新导入");}
    return null;
  }
  private void pickIrProfile(){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
    startActivityForResult(i,109);
  }
  private void infraredRemote(){
    ConsumerIrManager service=(ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
    boolean available=service!=null&&service.hasIrEmitter();
    LinearLayout info=card(body);
    info.addView(text(available?"手机红外发射器可用 · 对准电视红外接收窗口":"当前系统未向轻连开放红外发射器",14,available?TEAL:RED));
    gap(info,6);
    String choice=prefs.getString("ir_profile","");
    String selected="未匹配 · 选择品牌后逐组测试";
    if(choice.startsWith("catalog:"))try{IrCatalog.Entry entry=new IrCatalog(getAssets()).find(choice.substring(8));selected=entry==null?"已保存码型不可用":entry.brand+" · "+entry.model+"\n"+verifiedIrLabel(activeIr(),IrVerification.parse(irVerified(choice)),irFailed(choice));}catch(Exception ignored){}
    if(choice.equals("trial"))selected="旧版创维 Q+ 盒子试用码 · 非电视通用码";
    if(choice.equals("file")||choice.startsWith("custom:"))selected=irChoiceName(choice)+" · "+(activeIr()==null?"读取失败":activeIr().size()+" 个按键\n"+verifiedIrLabel(activeIr(),IrVerification.parse(irVerified(choice)),irFailed(choice)));
    info.addView(text(selected,12,MUTED));
    LinearLayout options=column();options.setVisibility(View.GONE);
    TextView toggle=button("遥控码管理  ›",false,()->{});
    toggle.setOnClickListener(v->{boolean open=options.getVisibility()!=View.VISIBLE;options.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(open?"收起遥控码管理  ﹀":"遥控码管理  ›");});
    LinearLayout.LayoutParams tp=full();tp.topMargin=dp(8);info.addView(toggle,tp);
    info.addView(options);
    pair(options,"按品牌匹配",()->chooseIrBrand(),"导入 .ir 遥控码",()->pickIrProfile());
    pair(options,"已保存方案",()->savedIrProfiles(),"导出当前方案",()->exportIrProfile(prefs.getString("ir_profile","")));
    if((choice.startsWith("catalog:")||choice.startsWith("custom:")||choice.equals("file"))&&activeIr()!=null){
      pair(options,"逐键验证",()->verifyIrProfile(choice,activeIr()),"修复单键",()->chooseIrRepairKey(choice,activeIr()));
      addButton(options,"导出按键验证结果",false,()->exportIrVerification(choice));
    }
  }
  private void chooseIrBrand(){
    try{
      IrCatalog catalog=new IrCatalog(getAssets());
      List<String> brands=catalog.brands();brands.add("华为 / 荣耀智慧屏 · 导入 .ir");brands.add("其他品牌 · 导入 .ir");
      LinearLayout panel=column();panel.setPadding(dp(16),dp(4),dp(16),dp(12));
      panel.addView(text("品牌是筛选条件，匹配结果以电视的实际反应为准。",12,MUTED));gap(panel,8);
      EditText search=field("搜索品牌","");panel.addView(search);
      ListView list=new ListView(this);ArrayList<String> visible=new ArrayList<>(brands);
      ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,visible){
        @Override public View getView(int pos,View old,android.view.ViewGroup parent){
          TextView item=text(getItem(pos),16,INK);item.setPadding(dp(14),dp(14),dp(14),dp(14));
          item.setBackgroundResource(android.R.drawable.list_selector_background);return item;
        }
      };
      list.setAdapter(adapter);panel.addView(list,new LinearLayout.LayoutParams(-1,dp(360)));
      AlertDialog chooser=new DeckDialogBuilder(this).setTitle("选择电视品牌").setView(panel).setNegativeButton("取消",null).create();
      list.setOnItemClickListener((parent,view,position,id)->{
          String selected=visible.get(position);chooser.dismiss();
          if(selected.contains("导入 .ir")){new DeckDialogBuilder(this).setTitle("该品牌尚无内置候选码")
            .setMessage("可导入对应电视遥控器的 Flipper .ir 文件。轻连不会把其他设备的码冒充为兼容码。")
            .setPositiveButton("导入 .ir",(q,w)->pickIrProfile()).setNegativeButton("关闭",null).show();return;}
          List<IrCatalog.Entry> entries=catalog.byBrand(selected);
          chooseIrModel(catalog,entries);
        });
      search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){visible.clear();for(String brand:brands)if(brand.toLowerCase(Locale.ROOT).contains(s.toString().toLowerCase(Locale.ROOT)))visible.add(brand);adapter.notifyDataSetChanged();}public void afterTextChanged(Editable e){}});
      chooser.show();
    }catch(Exception e){dialog("码库读取失败",message(e));}
  }
  private void chooseIrModel(IrCatalog catalog,List<IrCatalog.Entry> entries){
    if(entries.isEmpty()){dialog("尚无候选码","可导入对应电视的 .ir 文件。");return;}
    String[] names=new String[entries.size()];
    for(int i=0;i<entries.size();i++){
      IrCatalog.Entry entry=entries.get(i);
      try{names[i]=entry.model+" · "+catalog.load(entry).size()+" 键";}
      catch(Exception e){names[i]=entry.model+" · 文件不可用";}
    }
    new DeckDialogBuilder(this).setTitle(entries.get(0).brand+" · 选择遥控型号")
      .setItems(names,(d,which)->{
        IrCatalog.Entry entry=entries.get(which);IrCodes codes;
        try{codes=catalog.load(entry);}catch(Exception e){dialog("码型不可用",message(e));return;}
        String choice="catalog:"+entry.file;
        new DeckDialogBuilder(this).setTitle(entry.model)
          .setMessage("包含 "+codes.size()+" 个按键。可以先选用，再用遥控页验证实际反应；只有逐键确认后才会显示全部通过。")
          .setPositiveButton("使用此码",(q,w)->{
            if(prefs.edit().putString("ir_profile",choice).commit()){
              rememberIrChoice(choice);irProfile=codes;show(1);
            }else dialog("无法保存","遥控方案未写入手机，请重试。");
          })
          .setNeutralButton("逐键验证",(q,w)->verifyIrProfile(choice,codes))
          .setNegativeButton("返回型号列表",(q,w)->chooseIrModel(catalog,entries)).show();
      }).setNegativeButton("取消",null).show();
  }
  private void verifyIrProfile(String choice,IrCodes codes){
    testIrSequence(choice,codes,0,IrVerification.parse(irVerified(choice)),irFailed(choice));
  }
  private void testIrSequence(String choice,IrCodes codes,int step,Set<String> confirmed,Set<String> failed){
    if(step>=IrVerification.REQUIRED.length){
      String verified=IrVerification.serialize(confirmed);
      new DeckDialogBuilder(this).setTitle(IrVerification.complete(codes,confirmed)?"常用键全部通过":"仅部分按键可用")
        .setMessage(irChoiceName(choice)+"\n"+verifiedIrLabel(codes,confirmed,failed)+"\n只记录这次真实观察到的结果。")
        .setPositiveButton("保存并使用",(d,w)->{
          if(prefs.edit().putString("ir_profile",choice).putString("ir_verified_choice_"+choice,verified)
            .putString("ir_failed_choice_"+choice,IrVerification.serialize(failed)).commit()){
            rememberIrChoice(choice);irProfile=codes;show(1);
          }else dialog("无法保存","验证结果未写入手机，请重试。");
        }).setNegativeButton("不保存",null).show();
      return;
    }
    String key=IrVerification.REQUIRED[step];
    if(!codes.has(key)){testIrSequence(choice,codes,step+1,confirmed,failed);return;}
    IrCodes.Signal signal=codes.key(key);
    String hint=key.equals("POWER")?"电源可能让电视待机；可最后测试后用原遥控器开机。":"以电视的实际反应为准。";
    new DeckDialogBuilder(this).setTitle(irChoiceName(choice)+" · "+(step+1)+"/12")
      .setMessage("测试「"+IrVerification.displayName(key)+"」。\n"+hint)
      .setPositiveButton("发射",(d,w)->transmitIr(signal,()->{
        new DeckDialogBuilder(this).setTitle("「"+IrVerification.displayName(key)+"」有反应吗？")
          .setPositiveButton("有反应",(q,k)->{confirmed.add(key);failed.remove(key);testIrSequence(choice,codes,step+1,confirmed,failed);})
          .setNegativeButton("没反应",(q,k)->{failed.add(key);confirmed.remove(key);testIrSequence(choice,codes,step+1,confirmed,failed);})
          .setNeutralButton("重试",(q,k)->testIrSequence(choice,codes,step,confirmed,failed)).show();
      }))
      .setNegativeButton("跳过",(d,w)->testIrSequence(choice,codes,step+1,confirmed,failed))
      .setNeutralButton("取消",null).show();
  }
  private void chooseIrRepairKey(String choice,IrCodes base){
    if(base==null){dialog("无法修复","请先选择能读取的遥控方案。 ");return;}
    List<String> keysToRepair=new ArrayList<>();
    Set<String> failed=irFailed(choice),verified=IrVerification.parse(irVerified(choice));
    for(String key:IrVerification.REQUIRED)if(failed.contains(key))keysToRepair.add(key);
    for(String key:IrVerification.REQUIRED)if(!failed.contains(key)&&!verified.contains(key))keysToRepair.add(key);
    for(String key:IrVerification.REQUIRED)if(verified.contains(key))keysToRepair.add(key);
    String[] labels=new String[keysToRepair.size()];
    for(int i=0;i<labels.length;i++){String key=keysToRepair.get(i);
      labels[i]=IrVerification.displayName(key)+(failed.contains(key)?" · 已记录无反应":!base.has(key)?" · 原方案缺少":verified.contains(key)?" · 已验证可用":" · 未验证");
    }
    new DeckDialogBuilder(this).setTitle("选择要修复的按键")
      .setItems(labels,(d,which)->chooseIrRepairBrand(choice,base,keysToRepair.get(which)))
      .setNegativeButton("取消",null).show();
  }
  private void chooseIrRepairBrand(String choice,IrCodes base,String key){
    try{
      IrCatalog catalog=new IrCatalog(getAssets());
      String preferred=null;
      if(choice.startsWith("catalog:")){IrCatalog.Entry entry=catalog.find(choice.substring(8));if(entry!=null)preferred=entry.brand;}
      if(choice.startsWith("custom:"))preferred=prefs.getString("ir_repair_brand_"+choice.substring(7),null);
      final String first=preferred;
      if(first!=null){new DeckDialogBuilder(this).setTitle("试同品牌的「"+IrVerification.displayName(key)+"」")
        .setMessage("先测试「"+first+"」的不同码。每次只发射当前按键；找到有效码后会另存组合方案。")
        .setPositiveButton("开始测试",(d,w)->startIrRepair(choice,base,key,catalog,first))
        .setNeutralButton("换品牌",(d,w)->listIrRepairBrands(choice,base,key,catalog))
        .setNegativeButton("取消",null).show();
      }else listIrRepairBrands(choice,base,key,catalog);
    }catch(Exception e){dialog("码库读取失败",message(e));}
  }
  private void listIrRepairBrands(String choice,IrCodes base,String key,IrCatalog catalog){
    List<String> brands=catalog.brands();
    new DeckDialogBuilder(this).setTitle("选择电视品牌 · "+IrVerification.displayName(key))
      .setItems(brands.toArray(new String[0]),(d,which)->startIrRepair(choice,base,key,catalog,brands.get(which)))
      .setNegativeButton("取消",null).show();
  }
  private void startIrRepair(String choice,IrCodes base,String key,IrCatalog catalog,String brand){
    List<IrCatalog.Entry> entries=new ArrayList<>();List<IrCodes.Signal> signals=new ArrayList<>();
    for(IrCatalog.Entry entry:catalog.byBrand(brand))try{
      IrCodes.Signal signal=catalog.load(entry).key(key);
      if(signal==null||IrCodes.sameSignal(signal,base.key(key)))continue;
      boolean duplicate=false;for(IrCodes.Signal existing:signals)if(IrCodes.sameSignal(existing,signal)){duplicate=true;break;}
      if(!duplicate){entries.add(entry);signals.add(signal);}
    }catch(Exception ignored){}
    if(entries.isEmpty()){dialog("没有其他候选码","该品牌暂无不同的「"+IrVerification.displayName(key)+"」红外码。可以换品牌或导入 .ir 文件。");return;}
    testIrRepairCandidate(choice,base,key,brand,entries,signals,0);
  }
  private void testIrRepairCandidate(String choice,IrCodes base,String key,String brand,List<IrCatalog.Entry> entries,List<IrCodes.Signal> signals,int index){
    if(index>=entries.size()){dialog("候选码已试完","「"+IrVerification.displayName(key)+"」没有找到有效码，原方案未改变。可以选择其他品牌或导入码文件。");return;}
    IrCatalog.Entry entry=entries.get(index);IrCodes.Signal signal=signals.get(index);
    String caution=key.equals("POWER")?"电源键可能让电视待机，请备好原遥控器。\n":"";
    new DeckDialogBuilder(this).setTitle("测试「"+IrVerification.displayName(key)+"」 · "+(index+1)+"/"+entries.size())
      .setMessage(caution+"候选来源："+entry.model+"\n请将手机对准电视，再点发射。")
      .setPositiveButton("发射",(d,w)->transmitIr(signal,()->new DeckDialogBuilder(this)
        .setTitle("电视有正确反应吗？")
        .setMessage("只判断「"+IrVerification.displayName(key)+"」；若电视执行了别的操作，请选没反应。")
        .setPositiveButton("有效，保存",(q,v)->saveIrRepair(choice,base,key,brand,signal))
        .setNegativeButton("无效，下一个",(q,v)->testIrRepairCandidate(choice,base,key,brand,entries,signals,index+1))
        .setNeutralButton("重发",(q,v)->testIrRepairCandidate(choice,base,key,brand,entries,signals,index)).show()))
      .setNegativeButton("跳过此码",(d,w)->testIrRepairCandidate(choice,base,key,brand,entries,signals,index+1))
      .setNeutralButton("取消",null).show();
  }
  private void saveIrRepair(String oldChoice,IrCodes base,String key,String brand,IrCodes.Signal signal){
    try{
      IrCodes repaired=base.replacing(key,signal);
      byte[] payload=repaired.toFlipperRaw().getBytes("UTF-8");
      IrCodes.fromFlipper(new String(payload,"UTF-8"));
      byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(payload);
      StringBuilder id=new StringBuilder();for(byte value:digest)id.append(String.format(Locale.ROOT,"%02x",value&255));
      String choice="custom:"+id;
      File target=irProfileFile(choice),directory=target.getParentFile();
      if(!directory.exists()&&!directory.mkdirs())throw new IOException("无法创建遥控方案目录");
      if(!target.exists()){
        android.util.AtomicFile atomic=new android.util.AtomicFile(target);FileOutputStream out=atomic.startWrite();
        try{out.write(payload);atomic.finishWrite(out);}catch(Exception e){atomic.failWrite(out);throw e;}
      }else if(!Arrays.equals(irBytes(target),payload))throw new IOException("已有同名方案内容不一致");
      Set<String> confirmed=IrVerification.parse(irVerified(oldChoice));confirmed.add(key);
      Set<String> failed=irFailed(oldChoice);failed.remove(key);
      String name=irChoiceName(oldChoice)+" · 修复"+IrVerification.displayName(key);
      if(name.length()>80)name=name.substring(0,80);
      if(!prefs.edit().putString("ir_profile",choice).putString("ir_name_"+id,name)
        .putString("ir_repair_brand_"+id,brand)
        .putString("ir_verified_choice_"+choice,IrVerification.serialize(confirmed))
        .putString("ir_failed_choice_"+choice,IrVerification.serialize(failed)).commit())throw new IOException("方案选择未保存");
      rememberIrChoice(choice);irProfile=repaired;show(1);
      dialog("按键已修复","已把「"+IrVerification.displayName(key)+"」保存到新方案；其余按键沿用原方案。原方案可在已保存方案中切回。");
    }catch(Exception e){dialog("保存修复码失败",message(e));}
  }
  private String verifiedIrLabel(IrCodes codes,Set<String> verified,Set<String> failed){
    if(IrVerification.complete(codes,verified))return "本机常用 12 键逐键验证通过";
    List<String> missing=IrVerification.missing(codes,verified);
    if(verified.isEmpty()){
      int available=0;for(String key:IrVerification.REQUIRED)if(codes!=null&&codes.has(key))available++;
      if(failed.isEmpty())return "码文件含 "+available+"/12 个常用键 · 尚未在本机逐键验证";
    }
    List<String> labels=new ArrayList<>();for(String key:IrVerification.REQUIRED)if(failed.contains(key))labels.add(IrVerification.displayName(key));
    String failures=labels.isEmpty()?"":"；无反应 "+android.text.TextUtils.join("、",labels);
    return "本机已验证 "+verified.size()+"/12 键"+failures+"；其余未验证或缺失 "+(missing.size()-labels.size())+" 键";
  }
  private void transmitIr(IrCodes.Signal signal){transmitIr(signal,null);}
  private void transmitIr(IrCodes.Signal signal,Runnable sent){
    ConsumerIrManager service=(ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
    if(service==null||!service.hasIrEmitter()){dialog("红外不可用","当前系统没有向轻连开放红外发射功能。");return;}
    keys.execute(()->{try{service.transmit(signal.frequency,signal.pattern);if(sent!=null)ui.post(sent);}catch(Exception e){ui.post(()->dialog("红外发射失败",message(e)));}});
  }
  private void remoteKey(int code){
    if(RemoteRoute.forKey(remoteMode(),code)==RemoteRoute.ADB){key(code);return;}
    switch(code){
      case 19:irSend("UP");break;case 20:irSend("DOWN");break;
      case 21:irSend("LEFT");break;case 22:irSend("RIGHT");break;
      case 23:irSend("OK");break;case 4:irSend("BACK");break;
      case 3:irSend("HOME");break;case 82:irSend("MENU");break;
      case 24:irSend("VOL_UP");break;case 25:irSend("VOL_DOWN");break;
      case 178:irSend("SOURCE");break;
    }
  }
  private void irSend(String name){
    IrCodes profile=activeIr();
    if(profile==null){toast("请先选择红外码型");return;}
    IrCodes.Signal signal=profile.key(name);
    if(signal==null){toast("当前遥控码文件没有这个按键");return;}
    transmitIr(signal);
  }

  private void repeatKey(TextView v,int code){
    final boolean[] touching={false};final Runnable[] tick=new Runnable[1];
    v.setOnClickListener(view->{if(!touching[0]){view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);remoteKey(code);}});
    tick[0]=()->{if(!touching[0]||!v.isAttachedToWindow()||!v.hasWindowFocus()){touching[0]=false;v.setPressed(false);pressState(v,false);v.setAlpha(1);return;}remoteKey(code);ui.postDelayed(tick[0],irMode()?270:180);};
    v.setOnTouchListener((view,event)->{int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){if(!irMode()&&!requestConnection())return true;touching[0]=true;view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);remoteKey(code);ui.postDelayed(tick[0],450);view.setPressed(true);pressState(view,true);return true;}if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){ui.removeCallbacks(tick[0]);view.setPressed(false);pressState(view,false);if(action==MotionEvent.ACTION_UP&&touching[0])view.performClick();touching[0]=false;return true;}return true;});
  }

  private void repeatVolume(TextView view,int code,Runnable feedback){
    final boolean[] held={false};
    HoldRepeater repeater=new HoldRepeater(new HoldRepeater.Schedule(){public void post(Runnable r,long ms){ui.postDelayed(r,ms);}public void remove(Runnable r){ui.removeCallbacks(r);}},
      ()->held[0]&&view.isAttachedToWindow()&&view.hasWindowFocus()&&view.isEnabled(),()->{remoteKey(code);feedback.run();},180);
    view.setOnClickListener(v->{if(!held[0]){v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);remoteKey(code);feedback.run();}});
    view.setOnTouchListener((v,event)->{
      int action=event.getActionMasked();
      if(action==MotionEvent.ACTION_DOWN){if(!v.isEnabled())return true;if(!irMode()&&!requestConnection())return true;held[0]=true;v.setPressed(true);v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);repeater.start();return true;}
      if(action==MotionEvent.ACTION_MOVE&&(event.getX()<0||event.getY()<0||event.getX()>v.getWidth()||event.getY()>v.getHeight())){held[0]=false;repeater.stop();v.setPressed(false);return true;}
      if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){repeater.stop();v.setPressed(false);if(action==MotionEvent.ACTION_UP&&held[0])v.performClick();held[0]=false;return true;}return true;
    });
  }

  private long keyWarning = 0;
  private final java.util.concurrent.atomic.AtomicBoolean keyPending =
      new java.util.concurrent.atomic.AtomicBoolean(false);

  private void key(int code) {
    if (!requestConnection()) return;
    if(!capabilityAllowed(DeviceCapabilities.REMOTE))return;
    if (!keyPending.compareAndSet(false, true)) return;
    keys.execute(
        () -> {
          try {
            require();
            adb.shell("input keyevent " + code).require();
            if(capabilityClient==adb&&capabilitySnapshot!=null)capabilitySnapshot.success(DeviceCapabilities.REMOTE);
          } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - keyWarning > 2500) {
              keyWarning = now;
              ui.post(() -> toast("遥控失败：" + message(e)));
            }
          } finally {
            keyPending.set(false);
          }
        });
  }

  private void capture() {
    final byte[][] result = {null};
    run(
        "截取屏幕",
        () -> {
          require();
          result[0] = adb.screenshot();
          if (result[0].length < 8 || result[0][0] != (byte) 137 || result[0][1] != 80)
            throw new IOException("设备未返回 PNG 截图，可能不支持 exec 截图通道");
          return "";
        },
        () -> {
          saveImage = result[0];
          BitmapFactory.Options o = new BitmapFactory.Options();
          o.inJustDecodeBounds = true;
          BitmapFactory.decodeByteArray(saveImage, 0, saveImage.length, o);
          o.inSampleSize = Math.max(1, Math.max(o.outWidth, o.outHeight) / 1600);
          o.inJustDecodeBounds = false;
          Bitmap b = BitmapFactory.decodeByteArray(saveImage, 0, saveImage.length, o);
          if (b == null) {
            dialog("截图失败", "无法解析设备画面");
            return;
          }
          ImageView im = new ImageView(this);
          im.setImageBitmap(b);
          im.setAdjustViewBounds(true);
          im.setPadding(dp(12), dp(12), dp(12), dp(12));
          new DeckDialogBuilder(this)
              .setTitle("当前屏幕")
              .setView(im)
              .setNegativeButton("关闭", null)
              .setPositiveButton(
                  "保存 PNG",
                  (d, w) ->
                      createDocument(
                          104, "image/png", "LinkDeck_" + System.currentTimeMillis() + ".png"))
              .show();
        });
  }

  private void applications() {
    pageIntro("应用", "把手机应用装到电视，常用应用一眼找到。");
    LinearLayout install = card(body);
    install.setPadding(dp(16), dp(10), dp(16), dp(12));
    install.addView(text("安装 / 更新到电视", 13, MUTED));
    addButton(install,"检查已收录应用更新",true,()->scanInstalledUpdates());
    pair(install, "选择手机应用", () -> {installExpectedPackage=null;phoneApps();}, "安装 APK", () -> {installExpectedPackage=null;pick(100);});
    install.addView(text("仅核对已验证的官方来源；其他应用请手动选择 APK。安装前检查版本、签名与电视兼容性。",12,MUTED));
    addButton(install,"一键清理",false,()->cleanCache());
    aerialEntry(body);
    haloEntry(body);
    LinearLayout c = card(body);
    LinearLayout heading = row();
    TextView title = text("电视上的应用", 18, INK);
    title.setTypeface(null, Typeface.BOLD);
    heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    TextView refresh = button("刷新列表", false, () -> loadApps());
    refresh.setTextSize(12);
    refresh.setMinHeight(dp(40));
    refresh.setPadding(dp(12), dp(8), dp(12), dp(8));
    heading.addView(refresh);
    TextView bulk =
        button(
            batchMode ? "完成" : "批量",
            false,
            () -> {
              batchMode = !batchMode;
              if (!batchMode) selected.clear();
              show(2);
            });
    bulk.setTextSize(12);
    bulk.setMinHeight(dp(40));
    bulk.setPadding(dp(12), dp(8), dp(12), dp(8));
    LinearLayout.LayoutParams bulkParams = new LinearLayout.LayoutParams(-2, -2);
    bulkParams.leftMargin = dp(6);
    heading.addView(bulk, bulkParams);
    c.addView(heading);
    gap(c, 10);
    catalogHint = text(connected() ? catalogMessage : "连接后读取电视应用；上方可先选择手机应用。", 12, MUTED);
    c.addView(catalogHint);
    gap(c, 10);
    EditText search = field("搜索应用名称或包名", appQuery);
    c.addView(search);
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            appQuery = s.toString();
            ui.removeCallbacks(searchUpdate);
            ui.postDelayed(searchUpdate, 180);
          }

          public void afterTextChanged(Editable e) {}
        });
    LinearLayout filters = row();
    CheckBox sys = new CheckBox(this);
    sys.setText("系统应用");
    sys.setTextSize(12);
    sys.setChecked(showSystem);
    sys.setOnCheckedChangeListener(
        (b, v) -> {
          showSystem = v;
          renderApps();
        });
    filters.addView(sys);
    CheckBox dis = new CheckBox(this);
    dis.setText("仅停用");
    dis.setTextSize(12);
    dis.setChecked(onlyDisabled);
    dis.setOnCheckedChangeListener(
        (b, v) -> {
          onlyDisabled = v;
          renderApps();
        });
    filters.addView(dis);
    CheckBox favorite = new CheckBox(this);
    favorite.setText("仅收藏");
    favorite.setTextSize(12);
    favorite.setChecked(onlyFavorites);
    favorite.setOnCheckedChangeListener(
        (v, on) -> {
          onlyFavorites = on;
          appLimit = 60;
          renderApps();
        });
    filters.addView(favorite);
    HorizontalScrollView filterScroll = new HorizontalScrollView(this);
    filterScroll.setHorizontalScrollBarEnabled(false);
    filterScroll.addView(filters);
    c.addView(filterScroll);
    if (batchMode) pair(c, "批量停用已选", () -> batch(false), "批量启用已选", () -> batch(true));
    appList = column();
    body.addView(appList);
    if (!connected()) renderApps();
    else if (appLoaded) renderApps();
    else loadApps();
  }

  private void loadApps() {
    iconsFailed.clear();
    run(
        "读取应用列表",
        () -> {
          require();
          String all = adb.shell("pm list packages -f").require(),
              sys = adb.shell("pm list packages -s").require(),
              disabled = adb.shell("pm list packages -d").require();
          Set<String> system = packages(sys), off = packages(disabled);
          JSONObject cached = catalog.cachedLabels(catalogDevice);
          ArrayList<AppRow> list = new ArrayList<>();
          for (String line : all.split("\n")) {
            if (!line.startsWith("package:")) continue;
            int at = line.lastIndexOf('=');
            if (at < 8) continue;
            AppRow a = new AppRow();
            a.pkg = line.substring(at + 1).trim();
            a.path = line.substring(8, at);
            a.system = system.contains(a.pkg);
            a.disabled = off.contains(a.pkg);
            JSONObject meta = cached.optJSONObject(a.pkg);
            a.name = meta == null ? a.pkg : meta.optString("name", a.pkg);
            a.stamp = meta == null ? "" : meta.optString("stamp", "");
            list.add(a);
          }
          Collections.sort(list, (a, b) -> a.pkg.compareToIgnoreCase(b.pkg));
          synchronized (apps) {
            apps.clear();
            apps.addAll(list);
          }
          appLoaded = true;
          return "已读取 " + list.size() + " 个应用";
        },
        () -> {
          if (page == 2) renderApps();
          refreshCatalog();
        });
  }

  private Set<String> packages(String raw) {
    Set<String> s = new HashSet<>();
    for (String l : raw.split("\n")) if (l.startsWith("package:")) s.add(l.substring(8).trim());
    return s;
  }
  private void exportAppInventory(){
    if(!connected()||!appLoaded){dialog("先读取应用列表","连接电视后点“刷新列表”，再导出第三方应用包名。");return;}
    StringBuilder list=new StringBuilder("轻连第三方应用包名清单\n仅包含本次读取的包名；没有设备地址或序列号。\n\n");
    synchronized(apps){for(AppRow app:apps)if(!app.system)list.append(app.pkg).append('\n');}
    pendingAppInventory=list.toString();createDocument(112,"text/plain","QingLian-TV-Apps.txt");
  }

  private boolean matchesApp(AppRow a) {
    String q = appQuery.toLowerCase(Locale.ROOT);
    return (!a.system || showSystem)
        && (!onlyDisabled || a.disabled)
        && (!onlyFavorites || favorites.contains(a.pkg))
        && (a.pkg.toLowerCase(Locale.ROOT).contains(q)
            || (a.name != null && a.name.toLowerCase(Locale.ROOT).contains(q)));
  }

  private String favoritesKey() {
    return "favorites:" + AppCatalog.digest(catalogDevice);
  }

  private void toggleFavorite(AppRow a) {
    if (!favorites.add(a.pkg)) favorites.remove(a.pkg);
    prefs.edit().putStringSet(favoritesKey(), new HashSet<>(favorites)).apply();
    renderApps();
  }

  private void renderApps() {
    if (appList == null || page != 2) return;
    appList.removeAllViews();
    iconViews.clear();
    if (!connected()) {
      emptyState(appList, 2, "应用列表待连接后读取", "连接后显示应用名称与图标，支持星标收藏。你现在仍可查看本地 APK 信息。");
      return;
    }
    int count = 0, shown = 0;
    TextView summary = text("", 12, MUTED);
    appList.addView(summary);
    gap(appList, 10);
    synchronized (apps) {
      for (AppRow a : apps) {
        if (!matchesApp(a)) continue;
        count++;
        if (shown >= appLimit) continue;
        shown++;
        LinearLayout c = card(appList), r = row();
        c.setPadding(dp(10), dp(14), dp(12), dp(14));
        CheckBox check = new CheckBox(this);
        check.setChecked(selected.contains(a.pkg));
        check.setContentDescription("选择 " + a.pkg);
        check.setOnCheckedChangeListener(
            (v, on) -> {
              if (on) selected.add(a.pkg);
              else selected.remove(a.pkg);
              summary.setText("匹配 " + appsMatchCount() + " 个 · 已选择 " + selected.size() + " 个");
            });
        if (batchMode) r.addView(check);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setBackground(shape(UiPalette.color(0xff21374b), 12));
        icon.setPadding(dp(4), dp(4), dp(4), dp(4));
        icon.setImageResource(
            getResources().getIdentifier("ic_app_placeholder", "drawable", getPackageName()));
        String key = catalog.iconKey(catalogDevice, a.pkg, a.stamp == null ? "" : a.stamp);
        icon.setTag(key);
        Bitmap image = catalog.peek(key);
        if (image != null) icon.setImageBitmap(image);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        imageParams.rightMargin = dp(11);
        r.addView(icon, imageParams);
        iconViews.put(a.pkg, icon);
        LinearLayout labels = column();
        TextView name = text(a.name == null ? a.pkg : a.name, 15, INK);
        name.setTypeface(null, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name);
        TextView pkg = text(a.pkg, 11, MUTED);
        pkg.setMaxLines(1);
        pkg.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(pkg);
        labels.addView(
            text(
                (a.system ? "系统" : "第三方") + " · " + (a.disabled ? "已停用" : "已启用")
                    + (!a.system&&OfficialAppSources.forPackage(a.pkg)!=null?" · 官方来源可查":""),
                11,
                a.disabled ? RED : TEAL));
        r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView star =
            text(
                favorites.contains(a.pkg) ? "★" : "☆",
                24,
                favorites.contains(a.pkg) ? UiPalette.color(0xffb2842c) : MUTED);
        star.setGravity(Gravity.CENTER);
        star.setContentDescription((favorites.contains(a.pkg) ? "取消收藏 " : "收藏 ") + a.pkg);
        star.setOnClickListener(v -> toggleFavorite(a));
        r.addView(star, new LinearLayout.LayoutParams(dp(42), dp(48)));
        c.addView(r);
        c.setOnClickListener(v -> appActions(a));
      }
    }
    summary.setText("匹配 " + count + " 个 · 已选择 " + selected.size() + " 个");
    if (count > shown)
      addButton(
          appList,
          "再显示 60 个（剩余 " + (count - shown) + "）",
          false,
          () -> {
            appLimit += 60;
            renderApps();
          });
    if (!selected.isEmpty())
      addButton(
          appList,
          "取消全部选择",
          false,
          () -> {
            selected.clear();
            renderApps();
          });
    if (count == 0)
      emptyState(
          appList,
          2,
          onlyFavorites ? "还没有匹配的收藏" : "没有符合条件的应用",
          onlyFavorites ? "关闭“仅收藏”，点击应用右侧的星标即可加入收藏。" : "可尝试其他名称、包名或筛选条件。");
    scheduleIcons();
  }

  private int appsMatchCount() {
    int n = 0;
    synchronized (apps) {
      for (AppRow a : apps) if (matchesApp(a)) n++;
    }
    return n;
  }

  private void scheduleIcons() {
    if (page != 2) return;
    ui.removeCallbacks(iconRefresh);
    ui.postDelayed(iconRefresh, 180);
  }

  private void refreshCatalog() {
    if (metadataLoading || !connected() || catalogDevice.isEmpty()) return;
    metadataLoading = true;
    catalogMessage = "正在读取应用名称，列表仍可操作";
    if (catalogHint != null && page == 2) catalogHint.setText(catalogMessage);
    final AdbClient client = adb;
    final String deviceKey = catalogDevice;
    catalogWork.execute(
        () -> {
          JSONObject result = null;
          Exception failure = null;
          try {
            result = catalog.refreshLabels(client, deviceKey);
          } catch (Exception e) {
            failure = e;
          }
          final JSONObject data = result;
          final Exception error = failure;
          ui.post(
              () -> {
                if (isFinishing()
                    || isDestroyed()
                    || adb != client
                    || !deviceKey.equals(catalogDevice)) return;
                metadataLoading = false;
                if (error == null) {
                  synchronized (apps) {
                    for (AppRow a : apps) {
                      JSONObject item = data.optJSONObject(a.pkg);
                      if (item != null) {
                        a.name = item.optString("name", a.pkg);
                        a.stamp = item.optString("stamp", "");
                      }
                    }
                  }
                  catalogMessage = "名称已更新 · 图标按需加载 · 星标收藏";
                } else catalogMessage = "名称读取暂不可用，仍可按包名管理；刷新列表可重试";
                appendLog(
                    "读取应用名称",
                    host,
                    error == null,
                    error == null ? "读取 " + data.length() + " 个名称" : "",
                    error);
                if (catalogHint != null && page == 2) catalogHint.setText(catalogMessage);
                renderApps();
              });
        });
  }

  private void loadVisibleIcons() {
    if (page != 2 || !connected() || metadataLoading) return;
    final AdbClient client = adb;
    final String deviceKey = catalogDevice;
    final Map<String, String> requests = new LinkedHashMap<>();
    final Map<String, String> requestKeys = new HashMap<>();
    synchronized (apps) {
      for (AppRow a : apps) {
        ImageView view = iconViews.get(a.pkg);
        if (view == null
            || a.stamp == null
            || a.stamp.isEmpty()
            || !view.getGlobalVisibleRect(new Rect())) continue;
        String key = catalog.iconKey(deviceKey, a.pkg, a.stamp);
        if (catalog.peek(key) != null || iconsPending.contains(key) || iconsFailed.contains(key))
          continue;
        requests.put(a.pkg, a.stamp);
        requestKeys.put(a.pkg, key);
        iconsPending.add(key);
        if (requests.size() == 8) break;
      }
    }
    if (requests.isEmpty()) return;
    catalogWork.execute(
        () -> {
          Map<String, Bitmap> images = Collections.emptyMap();
          try {
            images = catalog.loadIcons(client, deviceKey, requests);
          } catch (Exception ignored) {
          }
          final Map<String, Bitmap> loaded = images;
          ui.post(
              () -> {
                if (isFinishing()
                    || isDestroyed()
                    || adb != client
                    || !deviceKey.equals(catalogDevice)) return;
                for (String pkg : requests.keySet()) {
                  String key = requestKeys.get(pkg);
                  iconsPending.remove(key);
                  Bitmap image = loaded.get(pkg);
                  if (image == null) {
                    iconsFailed.add(key);
                    continue;
                  }
                  ImageView view = iconViews.get(pkg);
                  if (view != null && key.equals(view.getTag())) view.setImageBitmap(image);
                }
                scheduleIcons();
              });
        });
  }

  private void appActions(AppRow a) {
    OfficialAppSources.Source official=OfficialAppSources.forPackage(a.pkg);
    List<String> menu=new ArrayList<>(Arrays.asList(
      "在设备上打开",
      a.disabled ? "启用应用" : "停用应用",
      "导出安装包",
      "清除应用数据",
      "为当前用户卸载",
      "查看包详情",
      favorites.contains(a.pkg) ? "取消收藏" : "加入收藏",
      "更新此应用"));
    if(official!=null&&!a.system)menu.add("检查已核实来源");
    menu.add("从 GitHub 检查更新");
    String[] choices=menu.toArray(new String[0]);
    new DeckDialogBuilder(this)
        .setTitle(a.name == null ? a.pkg : a.name)
        .setItems(
            choices,
            (d, w) -> {
              if (w == 0)
                command(
                    "启动应用",
                    ApplicationLauncher.command(a.pkg),
                    null);
              else if (w == 1)
                confirm(
                    a.disabled ? "启用应用" : "停用应用",
                    a.pkg + (a.system ? "\n这是系统应用，停用可能影响桌面、输入或其他系统功能。" : "") + "\n停用成功后可在恢复中心启用。",
                    () -> setEnabled(a, a.disabled));
              else if (w == 2) exportApk(a);
              else if (w == 3)
                confirm(
                    "清除数据，无法撤销",
                    a.pkg + "\n账号、设置和本地数据可能丢失。恢复中心不能恢复已清除的数据。",
                    () -> command("清除应用数据", "pm clear " + AdbClient.quote(a.pkg), null));
              else if (w == 4)
                confirm(
                    "为当前用户卸载",
                    a.pkg + "\n应用数据可能丢失。系统应用只尝试从用户 0 卸载；部分旧系统不支持。卸载不提供一键恢复。",
                    () ->
                        command(
                            "卸载应用",
                            "pm uninstall --user 0 " + AdbClient.quote(a.pkg),
                            () -> loadApps()));
              else if (w == 5) command("包详情", "dumpsys package " + AdbClient.quote(a.pkg), null);
              else if(w==6) toggleFavorite(a);
              else if(w==7)chooseInstall(a.pkg);
              else if(w==8&&official!=null&&!a.system)checkOfficialSource(a);
              else githubUpdate(a);
            })
        .show();
  }

  private static final class CommandFailure extends IOException {
    final CommandRecord record;

    CommandFailure(CommandRecord r) {
      super(
          (r.exitCode<0 ? "结果未确认" : "退出码 "+r.exitCode)
              + "\n" + r.failureReason
              + "\n$ "
              + r.command
              + "\n"
              + r.output.substring(0, Math.min(1500, r.output.length())));
      record = r;
    }
  }

  private File lastCommandFile() {
    return new File(getFilesDir(), "last-command.json");
  }

  private void command(String title, String cmd, Runnable done) {
    final CommandRecord[] result = {null};
    final String target = host + ":" + prefs.getString("port", "5555");
    run(
        title,
        () -> {
          require();
          long start = SystemClock.elapsedRealtime();
          AdbClient.Result response=null;
          AdbClient.ShellFailure incomplete=null;
          try { response=adb.shell(cmd,90000); }
          catch(AdbClient.ShellFailure e) { incomplete=e; }
          CommandRecord record = new CommandRecord();
          record.title = title;
          record.command = cmd;
          record.target = target;
          record.time =
              new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
          record.output = incomplete==null ? response.raw : incomplete.raw;
          record.exitCode = incomplete==null ? response.exitCode : -1;
          record.failureReason = incomplete==null ? "" : incomplete.getMessage();
          record.elapsed = SystemClock.elapsedRealtime() - start;
          result[0] = record;
          try {
            record.save(lastCommandFile());
            if(activeTaskId!=null){File history=new File(getFilesDir(),"command-results");history.mkdirs();String name=activeTaskId+".json";record.save(new File(history,name));journal.attachResult(activeTaskId,name);pruneCommandHistory(history);}
          } catch (Exception e) {
            ui.post(() -> toast("命令已返回，本次结果未能保存到本机"));
          }
          if (record.exitCode != 0) throw new CommandFailure(record);
          return "$ "
              + cmd
              + "\n"
              + record.output.substring(0, Math.min(1500, record.output.length()));
        },
        () -> {
          showCommandResult(result[0]);
          if (done != null) done.run();
        });
  }

  private void previousCommand() {
    final CommandRecord[] result = {null};
    run(
        "读取命令结果",
        () -> {
          if (!lastCommandFile().exists()) throw new IOException("还没有已保存的命令结果，先连接设备执行一条命令。");
          result[0] = CommandRecord.read(lastCommandFile());
          return "";
        },
        () -> showCommandResult(result[0]));
  }

  private void showCommandResult(CommandRecord record) {
    if (isFinishing() || isDestroyed()) return;
    if (commandResultDialog != null) commandResultDialog.dismiss();
    lastShownCommand = record;
    final String document = record.document();
    final int chunk = 20000, pages = Math.max(1, (document.length() + chunk - 1) / chunk);
    final int[] current = {0};
    Dialog dialog = new Dialog(this, getResources().getIdentifier(UiPalette.dialogName(), "style", getPackageName()));
    commandResultDialog = dialog;
    dialog.setOnDismissListener(
        d -> {
          if (commandResultDialog == dialog) commandResultDialog = null;
        });
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout panel = column();
    panel.setBackground(shape(UiPalette.color(0xff1b2b3e), 28));
    panel.setClipToOutline(true);
    panel.setPadding(dp(18), dp(18), dp(18), dp(12));
    LinearLayout top = row();
    TextView title = text("执行结果", 22, INK);
    title.setTypeface(null, Typeface.BOLD);
    top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    TextView state =
        text(
            record.exitCode < 0 ? "结果未确认" : record.exitCode == 0 ? "完成 · 0" : "失败 · " + record.exitCode,
            12,
            record.exitCode == 0 ? UiPalette.color(0xff9fe6ca) : UiPalette.color(0xffffb6aa));
    state.setPadding(dp(12), dp(6), dp(12), dp(6));
    state.setBackground(shape(UiPalette.color(0xff253e50), 16));
    top.addView(state);
    panel.addView(top);
    gap(panel, 8);
    panel.addView(text("已收到的文本完整保留 · 长结果分页查看 · 可导出 TXT", 12, UiPalette.color(0xffaabbd0)));
    gap(panel, 14);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    TextView code = text("", 13, UiPalette.color(0xffe0ebfa));
    code.setTypeface(Typeface.MONOSPACE);
    code.setTextIsSelectable(true);
    code.setPadding(dp(14), dp(14), dp(14), dp(14));
    code.setBackground(shape(UiPalette.color(0xff0d1724), 18));
    scroll.addView(code);
    panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout pager = row();
    pager.setGravity(Gravity.CENTER);
    TextView prev = button("上一页", false, () -> {}),
        next = button("下一页", false, () -> {}),
        position = text("", 12, UiPalette.color(0xffaabbd0));
    position.setGravity(Gravity.CENTER);
    pager.addView(prev, new LinearLayout.LayoutParams(dp(84), dp(44)));
    pager.addView(position, new LinearLayout.LayoutParams(0, -2, 1));
    pager.addView(next, new LinearLayout.LayoutParams(dp(84), dp(44)));
    final Runnable render =
        () -> {
          int start = Math.min(document.length(), current[0] * chunk),
              end = Math.min(document.length(), (current[0] + 1) * chunk);
          if (start > 0 && Character.isHighSurrogate(document.charAt(start - 1))) start--;
          if (end > 0
              && end < document.length()
              && Character.isHighSurrogate(document.charAt(end - 1))) end--;
          code.setText(document.substring(start, end));
          position.setText((current[0] + 1) + " / " + pages + " 页\n" + document.length() + " 字符");
          prev.setEnabled(current[0] > 0);
          prev.setAlpha(current[0] > 0 ? 1f : .35f);
          next.setEnabled(current[0] < pages - 1);
          next.setAlpha(current[0] < pages - 1 ? 1f : .35f);
          scroll.scrollTo(0, 0);
        };
    prev.setOnClickListener(
        v -> {
          if (current[0] > 0) {
            current[0]--;
            render.run();
          }
        });
    next.setOnClickListener(
        v -> {
          if (current[0] < pages - 1) {
            current[0]++;
            render.run();
          }
        });
    if (pages > 1) {
      gap(panel, 10);
      panel.addView(pager);
    } else {
      gap(panel, 8);
      panel.addView(
          text(
              (record.exitCode<0 ? "结果未确认" : "退出码 "+record.exitCode)
                  + " · "
                  + record.elapsed
                  + " ms · "
                  + record.output.length()
                  + " 字符",
              12,
              UiPalette.color(0xffaabbd0)));
    }
    pair(
        panel,
        "复制全部",
        () -> {
          if (document.length() > 100000) dialog("结果较长", "为避免系统剪贴板截断，请使用“导出 TXT”保存全部结果。");
          else copy(document);
        },
        "导出 TXT",
        () -> {
          pendingCommandExport = document;
          createDocument(
              105, "text/plain", "LinkDeck_Command_" + System.currentTimeMillis() + ".txt");
        });
    addButton(panel, "关闭", false, () -> dialog.dismiss());
    dialog.setContentView(panel);
    dialog.setOnShowListener(
        d -> {
          Window w = dialog.getWindow();
          w.setBackgroundDrawableResource(android.R.color.transparent);
          w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
          WindowManager.LayoutParams attrs=w.getAttributes(); attrs.dimAmount=.32f; w.setAttributes(attrs);
          w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(28),dp(680)), (int)(getResources().getDisplayMetrics().heightPixels*.86f));
        });
    render.run();
    dialog.show();
  }

  private void setEnabled(AppRow a, boolean enabled) {
    run(
        enabled ? "启用应用" : "停用应用",
        () -> {
          require();
          if(!enabled)recoveryKey(); // Never lose the identity of a newly disabled app.
          String out =
              adb.shell(
                      "pm "
                          + (enabled ? "enable" : "disable-user --user 0")
                          + " "
                          + AdbClient.quote(a.pkg))
                  .require();
          Set<String> off = packages(adb.shell("pm list packages -d").require());
          if (off.contains(a.pkg) == enabled) throw new IOException("设备未应用状态变更：" + out);
          a.disabled = !enabled;
          try{saveDisabled(a.pkg, !enabled);}catch(IOException e){if(!enabled)throw e;}
          return "已" + (enabled ? "启用" : "停用");
        },
        () -> renderApps());
  }

  private String recoveryKey() throws IOException {
    return DeviceScope.recoveryKey(host,prefs.getString("port","5555"),properties.get("ro.serialno"),properties.get("ro.build.fingerprint"),properties.get("ro.product.model"));
  }

  private void saveDisabled(String pkg, boolean disabled) throws IOException {
    Set<String> s = new HashSet<>(prefs.getStringSet(recoveryKey(), Collections.emptySet()));
    if (disabled) s.add(pkg);
    else s.remove(pkg);
    if(!prefs.edit().putStringSet(recoveryKey(), s).commit())throw new IOException("恢复记录未能保存，请核对应用当前状态");
  }

  private void batch(boolean enable) {
    if (selected.isEmpty()) {
      toast("请先勾选应用");
      return;
    }
    final ArrayList<AppRow> chosen = new ArrayList<>();
    synchronized (apps) {
      for (AppRow a : apps) if (selected.contains(a.pkg)) chosen.add(a);
    }
    confirm(
        "批量" + (enable ? "启用" : "停用") + " " + chosen.size() + " 个应用",
        "操作作用于勾选项，包括筛选后隐藏的项目。系统应用可能影响设备运行；每项都会记录结果。",
        () -> {
          final StringBuilder report = new StringBuilder();
          final int[] counts={0,0,0};
          run(
              "批量处理应用",
              () -> {
                require();
                if(!enable)recoveryKey();
                for (AppRow a : chosen) {
                  try {
                    if (a.disabled != enable) {
                      counts[2]++;
                      report.append("跳过（状态未变化）：").append(a.pkg).append("\n");
                      continue;
                    }
                    adb.shell(
                            "pm "
                                + (enable ? "enable" : "disable-user --user 0")
                                + " "
                                + AdbClient.quote(a.pkg))
                        .require();
                    Set<String> off = packages(adb.shell("pm list packages -d").require());
                    if (off.contains(a.pkg) == enable) throw new IOException("状态未生效");
                    a.disabled = !enable;
                    try{saveDisabled(a.pkg, !enable);}catch(IOException e){if(!enable)throw e;}
                    counts[0]++;
                    report.append("成功：").append(a.pkg).append('\n');
                  } catch (Exception e) {
                    counts[1]++;
                    report
                        .append("失败：")
                        .append(a.pkg)
                        .append(" · ")
                        .append(message(e))
                        .append('\n');
                  }
                }
                report.insert(0,"成功 "+counts[0]+" · 失败 "+counts[1]+" · 跳过 "+counts[2]+"\n\n");
                if(counts[1]>0)throw new BatchFailure(counts[0],counts[1],report.toString());
                return report.toString();
              },
              () -> {
                selected.clear();
                renderApps();
                dialog("批量处理结果", report.toString());
              });
        });
  }

  private void exportApk(AppRow a) {
    final String[] paths = {null};
    run(
        "读取安装包路径",
        () -> {
          paths[0] = adb.shell("pm path " + AdbClient.quote(a.pkg)).require();
          return "";
        },
        () -> {
          ArrayList<String> list = new ArrayList<>();
          for (String l : paths[0].split("\n"))
            if (l.startsWith("package:")) list.add(l.substring(8).trim());
          if (list.isEmpty()) {
            dialog("无法导出", "设备未返回安装包路径");
            return;
          }
          if (list.size() > 1) {
            dialog("这是拆分安装应用", "本版暂不导出拆分 APK，以免给出无法完整安装的文件。该应用包含 " + list.size() + " 个安装包。");
            return;
          }
          saveRemote = list.get(0);
          saveName = a.pkg + ".apk";
          createDocument(103, "application/vnd.android.package-archive", saveName);
        });
  }

  private void pick(int request) {
    if (request != 100 && !requestConnection()) return;
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("*/*");
    startActivityForResult(i, request);
  }

  private void createDocument(int request, String type, String name) {
    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType(type);
    i.putExtra(Intent.EXTRA_TITLE, name);
    startActivityForResult(i, request);
  }

  private String displayName(Uri u) {
    try (Cursor c =
        getContentResolver()
            .query(u, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (c != null && c.moveToFirst()) {
        String name = c.getString(0);
        if (name != null && !name.trim().isEmpty()) return name;
      }
    } catch (Exception ignored) {
    }
    String last = u.getLastPathSegment();
    if (last != null && !last.isEmpty()) {
      int slash = last.lastIndexOf('/');
      last = slash >= 0 ? last.substring(slash + 1) : last;
      if (!last.isEmpty() && !last.equals(".") && !last.equals("..") && !last.contains("\0"))
        return last;
    }
    return "file_" + System.currentTimeMillis();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) {
      if(request==110)pendingIrExportChoice=null;
      if(request==111)pendingIrReportChoice=null;
      if(request==112)pendingAppInventory=null;
      if(request==100)installExpectedPackage=null;
      if(request==108)new android.util.AtomicFile(compatibilityPending()).delete();
      if (request == 105) pendingCommandExport = null;
      if (request == 106) new android.util.AtomicFile(desktopReportFile(true,lastDesktopExportProfile==null?DesktopAssistant.AERIAL:lastDesktopExportProfile)).delete();
      if (request == 107) new android.util.AtomicFile(faultReportFile(true,lastFaultExportProfile==null?DesktopAssistant.AERIAL:lastFaultExportProfile)).delete();
      return;
    }
    if(request==109){
      try(InputStream in=getContentResolver().openInputStream(data.getData());ByteArrayOutputStream out=new ByteArrayOutputStream()){
        if(in==null)throw new IOException("无法打开遥控码文件");
        byte[] buf=new byte[4096];int n;
        while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>65536)throw new IOException("红外文件超过 64 KB");}
        byte[] payload=out.toByteArray();
        IrCodes parsed=IrCodes.fromFlipper(new String(payload,"UTF-8"));
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(payload);
        StringBuilder id=new StringBuilder();for(byte value:digest)id.append(String.format(Locale.ROOT,"%02x",value&255));
        String choice="custom:"+id;
        File target=irProfileFile(choice),directory=target.getParentFile();
        if(!directory.exists()&&!directory.mkdirs())throw new IOException("无法创建红外方案目录");
        File temp=new File(directory,id+".tmp");
        try(FileOutputStream file=new FileOutputStream(temp)){file.write(payload);file.getFD().sync();}
        if(target.exists()){temp.delete();}else if(!temp.renameTo(target))throw new IOException("遥控码文件保存失败");
        String display="自定义遥控码";
        try(Cursor cursor=getContentResolver().query(data.getData(),new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
          if(cursor!=null&&cursor.moveToFirst()){String name=cursor.getString(0);if(name!=null&&!name.trim().isEmpty())display=name.length()>80?name.substring(0,80):name;}
        }catch(Exception ignored){}
        if(!prefs.edit().putString("ir_profile",choice).putString("ir_name_"+id,display).commit())throw new IOException("遥控码选择未保存");
        rememberIrChoice(choice);
        irProfile=parsed;show(1);toast("已导入 "+parsed.size()+" 个红外按键");
      }catch(Exception e){dialog("导入遥控码失败",message(e));}
      return;
    }
    if(request==110){
      String choice=pendingIrExportChoice;pendingIrExportChoice=null;
      try{if(choice==null)throw new IOException("导出方案已失效");
        byte[] bytes=irBytes(choice);
        try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"w")){if(out==null)throw new IOException("无法打开保存位置");out.write(bytes);}
        toast("红外方案已导出");
      }catch(Exception e){dialog("导出红外方案失败",message(e));}
      return;
    }
    if(request==111){
      String choice=pendingIrReportChoice;pendingIrReportChoice=null;
      try{if(choice==null)throw new IOException("导出方案已失效");
        byte[] bytes=irVerificationReport(choice).getBytes("UTF-8");
        try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"w")){if(out==null)throw new IOException("无法打开保存位置");out.write(bytes);}
        toast("按键验证结果已导出");
      }catch(Exception e){dialog("导出验证结果失败",message(e));}
      return;
    }
    if(request==112){
      String content=pendingAppInventory;pendingAppInventory=null;
      try{if(content==null)throw new IOException("应用清单已失效");
        try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"w")){if(out==null)throw new IOException("无法保存应用清单");out.write(content.getBytes("UTF-8"));}
        toast("第三方应用包名已导出");
      }catch(Exception e){dialog("导出应用清单失败",message(e));}
      return;
    }
    if(request==108){final Uri destination=data.getData();run("导出兼容报告",()->{String frozen=readDesktopFile(compatibilityPending());try(OutputStream out=getContentResolver().openOutputStream(destination,"w")){if(out==null)throw new IOException("无法打开保存位置");out.write(frozen.getBytes("UTF-8"));}new android.util.AtomicFile(compatibilityPending()).delete();return "已保存脱敏兼容报告";},null);return;}
    if(request==107){final Uri destination=data.getData();run("导出故障报告",()->{String payload=readDesktopFile(faultReportFile(true,lastFaultExportProfile==null?DesktopAssistant.AERIAL:lastFaultExportProfile));try(OutputStream out=getContentResolver().openOutputStream(destination,"w")){if(out==null)throw new IOException("无法打开导出位置");out.write(payload.getBytes("UTF-8"));}new android.util.AtomicFile(faultReportFile(true,lastFaultExportProfile==null?DesktopAssistant.AERIAL:lastFaultExportProfile)).delete();return "故障报告已导出";},null);return;}
    if (request == 106) {
      final Uri target=data.getData();
      run("导出桌面诊断报告",()->{
        String report=readDesktopFile(desktopReportFile(true,lastDesktopExportProfile==null?DesktopAssistant.AERIAL:lastDesktopExportProfile));
        try(OutputStream out=getContentResolver().openOutputStream(target,"w")){out.write(report.getBytes("UTF-8"));}
        new android.util.AtomicFile(desktopReportFile(true,lastDesktopExportProfile==null?DesktopAssistant.AERIAL:lastDesktopExportProfile)).delete();return "已导出桌面诊断报告";
      },null);return;
    }
    if (request == 105) {
      final String document = pendingCommandExport;
      pendingCommandExport = null;
      final Uri target = data.getData();
      run(
          "导出命令结果",
          () -> {
            String payload =
                document == null ? CommandRecord.read(lastCommandFile()).document() : document;
            try (OutputStream out = getContentResolver().openOutputStream(target, "w")) {
              out.write(payload.getBytes("UTF-8"));
            }
            return "已导出全部命令结果";
          },
          null);
      return;
    }

    Uri u = data.getData();
    if (request == 100) inspectApk(u);
    else if (request == 101) {
      String name = displayName(u);
      if (name.contains("/") || name.contains("\0")) {
        dialog("文件名无效", "请选择其他文件");
        return;
      }
      final String dest = join(folder, name);
      confirm(
          "上传文件",
          name + "\n目标：" + dest + "\n同名文件将被覆盖。",
          () ->
              run(
                  "上传文件",
                  () -> {
                    require();
                    try (InputStream in = getContentResolver().openInputStream(u)) {
                      adb.push(in, dest, n -> transferProgress(n));
                    }
                    return "上传完成";
                  },
                  () -> {
                    if (page == 3) loadFiles();
                  }));
    } else if (request == 103) {
      final String remote = saveRemote;
      run(
          "下载文件",
          () -> {
            require();
            try (OutputStream out = getContentResolver().openOutputStream(u, "w")) {
              adb.pull(remote, out, n -> transferProgress(n));
            } catch (Exception e) {
              try {
                android.provider.DocumentsContract.deleteDocument(getContentResolver(), u);
              } catch (Exception ignored) {
              }
              throw e;
            }
            return "已保存";
          },
          null);
    } else if (request == 104) {
      final byte[] bytes = saveImage;
      run(
          "保存截图",
          () -> {
            if (bytes == null) throw new IOException("截图已失效，请重新截取");
            try (OutputStream o = getContentResolver().openOutputStream(u, "w")) {
              o.write(bytes);
            }
            return "已保存截图";
          },
          null);
    }
  }

  private void taskProgress(String stage) {
    String id=activeTaskId;
    if(id!=null)journal.progress(id,stage);
    ui.post(()->{if(status!=null)status.setText("◌  "+stage);});
  }

  private void transferProgress(long n) {
    long now = System.currentTimeMillis();
    if (now - lastProgress < 500) return;
    lastProgress = now;
    String id=activeTaskId;
    if(id!=null) journal.progress(id,"当前文件已传输 · "+size(n));
    ui.post(() -> status.setText("◌  正在传输 · " + size(n)));
  }

  private static final class PhoneApp {
    String pkg, name, version;
    int minimum;
    boolean system, tv;
    long updated;
    ApplicationInfo info;
    List<File> files = new ArrayList<>();
  }

  private PhoneApp readPhoneApp(PackageInfo pi) {
    ApplicationInfo ai = pi.applicationInfo;
    PhoneApp app = new PhoneApp();
    app.pkg = pi.packageName;
    app.version = pi.versionName;
    app.minimum = ai.minSdkVersion;
    app.name = ai.loadLabel(getPackageManager()).toString();
    app.info = ai;
    app.updated = pi.lastUpdateTime;
    app.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    app.tv = getPackageManager().getLeanbackLaunchIntentForPackage(app.pkg) != null;
    app.files.add(new File(ai.sourceDir));
    if (ai.splitSourceDirs != null)
      for (String file : ai.splitSourceDirs) app.files.add(new File(file));
    return app;
  }

  private void phoneApps() {
    LinearLayout content = column();
    content.setPadding(dp(20), dp(8), dp(20), dp(8));
    content.addView(text("从手机直接选应用，自动读取安装文件。应用数据和登录状态不会复制到电视。", 12, MUTED));
    gap(content, 12);
    EditText search = field("搜索手机应用名称或包名", installExpectedPackage==null?"":installExpectedPackage);
    content.addView(search);
    CheckBox system = new CheckBox(this);
    system.setText("显示系统应用");
    system.setTextSize(12);
    content.addView(system);
    TextView count = text("正在读取手机应用…", 12, MUTED);
    content.addView(count);
    ScrollView scroll = new ScrollView(this);
    LinearLayout list = column();
    scroll.addView(list);
    content.addView(scroll, new LinearLayout.LayoutParams(-1, dp(340)));
    AlertDialog dialog =
        new DeckDialogBuilder(this)
            .setTitle("手机应用 → 电视")
            .setView(content)
            .setNegativeButton("关闭", (d,w)->installExpectedPackage=null)
            .setOnCancelListener(d->installExpectedPackage=null)
            .create();
    final List<PhoneApp> all = new ArrayList<>();
    final int[] limit = {40};
    final Runnable[] render = {null};
    render[0] =
        () -> {
          if (!dialog.isShowing()) return;
          list.removeAllViews();
          String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
          int matches = 0, shown = 0;
          for (PhoneApp app : all) {
            if ((app.system && !system.isChecked())
                || !(app.name + " " + app.pkg).toLowerCase(Locale.ROOT).contains(query)) continue;
            matches++;
            if (shown++ >= limit[0]) continue;
            LinearLayout row = row();
            row.setPadding(0, dp(12), 0, dp(12));
            ImageView icon = new ImageView(this);
            try {
              icon.setImageDrawable(app.info.loadIcon(getPackageManager()));
            } catch (Exception ignored) {
            }
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
            iconParams.rightMargin = dp(12);
            row.addView(icon, iconParams);
            LinearLayout copy = column();
            TextView name = text(app.name, 15, INK);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(name);
            TextView detail = text(app.pkg, 11, MUTED);
            detail.setSingleLine(true);
            detail.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            copy.addView(detail);
            String state =
                (connected() && deviceSdk > 0 && app.minimum > deviceSdk)
                    ? "系统版本不兼容"
                    : "最低 SDK " + app.minimum;
            copy.addView(
                text(
                    state + " · " + app.files.size() + " 个安装文件" + (app.tv ? " · TV 入口" : ""),
                    11,
                    MUTED));
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            TextView action = text("查看 ›", 12, TEAL);
            action.setPadding(dp(8), 0, 0, 0);
            row.addView(action);
            row.setBackground(
                new RippleDrawable(
                    ColorStateList.valueOf(UiPalette.color(0x150e887c)), shape(UiPalette.color(0xff1a2d40),12), null));
            row.setOnClickListener(
                v -> {
                  dialog.dismiss();
                  inspectPhoneApp(app.pkg);
                });
            list.addView(row);
            View line = new View(this);
            line.setBackgroundColor(LINE);
            list.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
          }
          count.setText(matches + " 个应用 · " + (connected() ? "选择后检查电视兼容性" : "可离线查看，连接后安装"));
          if (matches == 0) list.addView(text("没有匹配的应用", 14, MUTED));
          if (matches > limit[0])
            addButton(
                list,
                "显示更多",
                false,
                () -> {
                  limit[0] += 40;
                  render[0].run();
                });
        };
    final Runnable update =
        () -> {
          limit[0] = 40;
          render[0].run();
        };
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            ui.removeCallbacks(update);
            ui.postDelayed(update, 180);
          }

          public void afterTextChanged(Editable e) {}
        });
    system.setOnCheckedChangeListener((b, on) -> update.run());
    dialog.setOnDismissListener(d -> ui.removeCallbacks(update));
    dialog.show();
    // Keep the picker within short screens and above the keyboard.
    scroll.getLayoutParams().height =
        Math.min(dp(340), getResources().getDisplayMetrics().heightPixels / 2);
    run(
        "读取手机应用",
        () -> {
          ArrayList<PhoneApp> found = new ArrayList<>();
          for (PackageInfo pi : getPackageManager().getInstalledPackages(0)) {
            try {
              if (pi.applicationInfo != null && pi.applicationInfo.sourceDir != null)
                found.add(readPhoneApp(pi));
            } catch (Exception ignored) {
            }
          }
          Collections.sort(found, (a, b) -> a.name.compareToIgnoreCase(b.name));
          ui.post(
              () -> {
                all.addAll(found);
                render[0].run();
              });
          return "";
        },
        null);
  }

  private void inspectPhoneApp(String pkg) {
    final String expected=installExpectedPackage;installExpectedPackage=null;
    final PhoneApp[] selectedApp = {null};
    final Set<String> abis = new TreeSet<>();
    final String[] splitIssue = {""};
    run(
        "检查手机应用",
        () -> {
          if(expected!=null&&!expected.equals(pkg))throw new IOException("所选应用与要更新的电视应用包名不一致");
          PhoneApp app = readPhoneApp(getPackageManager().getPackageInfo(pkg, 0));
          abis.addAll(ApkTransfer.abis(app.files));
          splitIssue[0] = ApkManifest.splits(app.files);
          selectedApp[0] = app;
          return "";
        },
        () -> {
          PhoneApp app = selectedApp[0];
          long total = 0;
          for (File file : app.files) total += file.length();
          boolean compatible = splitIssue[0].isEmpty() &&
              ApkTransfer.compatible(
                  app.minimum, connected() ? deviceSdk : 0, abis, connected() ? deviceAbi : null);
          if(connected()&&compatible){prepareGeneric(app.files,false,expected,"手机应用");return;}
          String info =
              app.name
                  + "\n"
                  + app.pkg
                  + "\n版本："
                  + app.version
                  + "\n安装文件："
                  + app.files.size()
                  + " 个 · "
                  + size(total)
                  + "\n最低系统："
                  + ApkManifest.android(app.minimum)
                  + " / 电视："
                  + (connected() && deviceSdk > 0 ? ApkManifest.android(deviceSdk) : "待确认")
                  + "\n应用架构："
                  + (abis.isEmpty() ? "无原生库限制" : abis)
                  + "\n电视架构："
                  + (connected() ? deviceAbi : "连接后检查")
                  + "\n\n"
                  + ApkManifest.compatibility(app.minimum, connected()?deviceSdk:0, abis, connected()?deviceAbi:null)
                  + (splitIssue[0].isEmpty()?"已检查基础包与分包声明。清单未声明的额外依赖仍由电视安装器校验。":splitIssue[0])
                  + "\n\n"
                  + (app.tv ? "提供 TV 启动入口。" : "未检测到 TV 启动入口，可能需要鼠标或触屏操作。")
                  + "\n只安装应用，不复制账号、数据和额外下载资源。";
          AlertDialog.Builder b =
              new DeckDialogBuilder(this)
                  .setTitle("手机应用安装检查")
                  .setMessage(info)
                  .setNegativeButton("取消", null);
          if (compatible)
            b.setPositiveButton(
                connected() ? "安装到电视" : "连接后安装",
                (d, w) -> {
                  if (connected()) installPhoneApp(app);
                  else {
                    pendingPhoneApp = pkg;
                    goConnect();
                  }
                });
          b.show();
        });
  }

  private void installPhoneApp(PhoneApp app) {prepareGeneric(app.files,false,null,"手机应用");}

  private void inspectApk(Uri uri) {
    final String expected=installExpectedPackage;installExpectedPackage=null;
    final File local = new File(getCacheDir(), "incoming_" + System.currentTimeMillis() + ".apk");
    final String[] info = {null};
    final boolean[] compatible = {true};
    final boolean matchingDevice = connected();
    final int targetSdk = matchingDevice ? deviceSdk : 0;
    final String targetAbi = matchingDevice ? deviceAbi : null;
    run(
        "检查 APK 兼容性",
        () -> {
          try (InputStream in = getContentResolver().openInputStream(uri);
              OutputStream out = new FileOutputStream(local)) {
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
          }
          if ("file".equals(uri.getScheme())) {
            File previous = new File(uri.getPath());
            if (previous.getName().startsWith("incoming_")
                && getCacheDir().equals(previous.getParentFile())
                && !previous.equals(local)) previous.delete();
          }
          String splits = ApkManifest.splits(Collections.singletonList(local));
          if (!splits.isEmpty()) { local.delete(); throw new IOException(splits); }
          PackageInfo pi = getPackageManager().getPackageArchiveInfo(local.getAbsolutePath(), 0);
          if (pi == null) {
            local.delete();
            throw new IOException("无法识别为有效 APK，拆分安装包、APKS、XAPK 暂不支持");
          }
          if(expected!=null&&!expected.equals(pi.packageName)){local.delete();throw new IOException("安装包包名与要更新的电视应用不一致");}
          ApplicationInfo ai = pi.applicationInfo;
          ai.sourceDir = local.getAbsolutePath();
          ai.publicSourceDir = local.getAbsolutePath();
          int min = ai.minSdkVersion;
          Set<String> abis = new HashSet<>();
          try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(local)) {
            Enumeration<? extends java.util.zip.ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
              String n = en.nextElement().getName();
              if (n.startsWith("lib/") && n.endsWith(".so")) {
                String[] parts = n.split("/");
                if (parts.length > 2) abis.add(parts[1]);
              }
            }
          }
          boolean sdkOk = targetSdk == 0 || min <= targetSdk, abiOk = abis.isEmpty();
          if (targetAbi != null)
            for (String a : targetAbi.split(",")) if (abis.contains(a.trim())) abiOk = true;
          boolean abiKnown = targetAbi != null && !targetAbi.isEmpty();
          compatible[0] = sdkOk && (!abiKnown || abiOk);
          info[0] =
              "名称："
                  + ai.loadLabel(getPackageManager())
                  + "\n包名："
                  + pi.packageName
                  + "\n版本："
                  + pi.versionName
                  + "\n大小："
                  + size(local.length())
                  + "\n最低系统："
                  + ApkManifest.android(min)
                  + " / 目标设备："
                  + ApkManifest.android(targetSdk)
                  + "\nAPK 架构："
                  + (abis.isEmpty() ? "无原生库限制" : abis)
                  + "\n设备架构："
                  + (targetAbi == null ? "连接后匹配" : targetAbi)
                  + "\n\n"
                  + ApkManifest.compatibility(min,targetSdk,abis,targetAbi)
                  + "\n已检查单包的分包声明；签名、存储空间和设备限制由电视最终校验。";
          return "";
        },
        () -> {
          if(connected()&&compatible[0]){prepareGeneric(Collections.singletonList(local),true,expected,"APK 文件");return;}
          AlertDialog.Builder b =
              new DeckDialogBuilder(this)
                  .setTitle("安装前检查")
                  .setMessage(info[0])
                  .setNegativeButton("取消", (d, w) -> local.delete());
          if (compatible[0])
            b.setPositiveButton(
                connected() ? "安装到设备" : "连接后安装",
                (d, w) -> {
                  if (connected()) install(local);
                  else {
                    if (pendingInstall != null) pendingInstall.delete();
                    pendingInstall = local;
                    goConnect();
                  }
                });
          b.setOnCancelListener(d -> local.delete());
          b.show();
        });
  }

  private void install(File local) {prepareGeneric(Collections.singletonList(local),true,null,"APK 文件");}

  private void checkOfficialSource(AppRow app){
    OfficialAppSources.Source source=updateSource(app.pkg);
    if(source==null){dialog("尚无已核实来源","目前没有足够证据把这个电视包名对应到一个官方直接下载渠道。可以使用“更新此应用”选择你确认的 APK；轻连仍会检查包名、签名和版本。");return;}
    if(app.system){dialog("系统应用不提供此入口","系统应用由电视固件管理，轻连不主动下载覆盖。");return;}
    if(source.type.equals(OfficialAppSources.AERIAL)){checkAerial("auto");return;}
    if(source.type.equals(OfficialAppSources.HALO)){checkHalo("auto");return;}
    if(source.type.equals(OfficialAppSources.GITHUB)){checkGitHubApp(app.pkg,source.locator);return;}
    if(source.type.equals(OfficialAppSources.VIDEOLAN)){checkVideoLanApp(app.pkg);return;}
    if(source.type.equals(OfficialAppSources.KODI))checkKodiApp(app.pkg);
  }
  private static final class UpdateScanRow {
    String pkg,name,installed,published,status;boolean candidate;
  }
  private OfficialAppSources.Source updateSource(String pkg){
    OfficialAppSources.Source official=OfficialAppSources.forPackage(pkg);
    if(official!=null)return official;
    String repo=prefs.getString("github_repo_"+pkg,"");
    try{return repo.isEmpty()?null:new OfficialAppSources.Source(pkg,OfficialAppSources.GITHUB,GitHubAppRelease.repository(repo));}
    catch(Exception ignored){return null;}
  }
  private void scanInstalledUpdates(){
    if(!requestConnection())return;
    final AdbClient client=adb;final String endpoint=host+":"+prefs.getString("port","5555");
    final List<UpdateScanRow> rows=new ArrayList<>();final List<String> unmatched=new ArrayList<>();
    run("扫描已装应用更新",()->{
      Set<String> all=packages(client.shell("pm list packages").require());
      Set<String> system=packages(client.shell("pm list packages -s").require());
      List<String> sorted=new ArrayList<>(all);Collections.sort(sorted);
      String abis=null;
      for(String pkg:sorted){
        if(system.contains(pkg))continue;
        OfficialAppSources.Source source=updateSource(pkg);
        if(source==null){unmatched.add(pkg);continue;}
        UpdateScanRow row=new UpdateScanRow();row.pkg=pkg;row.name=source.name;rows.add(row);
        try{
          taskProgress("检查 "+source.name+" 的发布版本…");
          GenericState installed=genericState(client,pkg,false);
          if(!installed.present)throw new IOException("本次未读到电视已装版本");
          row.installed=installed.name+"（"+installed.code+"）";
          String published;
          if(source.type.equals(OfficialAppSources.GITHUB)){
            GitHubAppRelease.Release release=GitHubAppRelease.latest(source.locator);
            if(release.apks.isEmpty())throw new IOException("最新稳定发布没有单个 APK");
            published=release.tag;
          }else if(source.type.equals(OfficialAppSources.AERIAL)){
            GitHubAppRelease.Release release=GitHubAppRelease.latest("pengchujin/aerial-tv");
            if(release.apks.isEmpty())throw new IOException("官方发布未提供 APK");
            published=release.tag;
          }else if(source.type.equals(OfficialAppSources.HALO)){
            GitHubAppRelease.Release release=GitHubAppRelease.latest("Null9797/halo-tv");
            if(release.apks.isEmpty())throw new IOException("官方发布未提供 APK");
            published=release.tag;
          }else if(source.type.equals(OfficialAppSources.VIDEOLAN)){
            if(abis==null)abis=AerialProbe.properties(c->client.shell(c)).abis;
            List<OfficialVlc.Asset> found=OfficialVlc.latest();published=null;
            for(OfficialVlc.Asset asset:found)if(Arrays.asList(abis.split(",")).contains(asset.abi)){published=asset.version;break;}
            if(published==null)throw new IOException("最新稳定版没有适合电视架构的 APK");
          }else{
            if(abis==null)abis=AerialProbe.properties(c->client.shell(c)).abis;
            published=OfficialKodi.latest(kodiAbi(abis)).version;
          }
          row.published=published;
          int comparison=UpdateScan.compareNames(installed.name,published);
          row.candidate=UpdateScan.looksNewer(installed.name,published);
          row.status=row.candidate?"发现较新发布，安装条件待下载核验":comparison==0?"发布名称与已装相同":comparison>0?"发布名称未高于已装":"版本名称无法直接比较，需检查 APK";
        }catch(Exception e){row.status="来源检查未完成："+message(e);}
      }
      if(adb!=client||!endpoint.equals(host+":"+prefs.getString("port","5555")))throw new IOException("电视连接已变化，扫描结果已作废");
      return "";
    },()->showUpdateScan(rows,unmatched,client,endpoint));
  }
  private String kodiAbi(String abis)throws IOException{
    List<String> available=Arrays.asList(abis.split(","));
    if(available.contains("arm64-v8a"))return "arm64-v8a";
    if(available.contains("armeabi-v7a"))return "armeabi-v7a";
    throw new IOException("Kodi 没有适合此电视架构的 APK（"+abis+"）");
  }
  private void checkKodiApp(String pkg){
    final OfficialKodi.Asset[] asset={null};final GenericState[] installed={null};
    final AdbClient client=adb;final String endpoint=host+":"+prefs.getString("port","5555");
    run("检查 Kodi 官方更新",()->{
      installed[0]=genericState(client,pkg,false);
      if(!installed[0].present)throw new IOException("电视应用已不存在，请重新读取应用列表");
      String abis=AerialProbe.properties(c->client.shell(c)).abis;
      asset[0]=OfficialKodi.latest(kodiAbi(abis));
      if(adb!=client||!endpoint.equals(host+":"+prefs.getString("port","5555")))throw new IOException("电视连接已变化，请重新检查");
      return "";
    },()->{
      if(adb!=client||!endpoint.equals(host+":"+prefs.getString("port","5555"))){dialog("连接已变化","请重新检查目标电视。");return;}
      new DeckDialogBuilder(this).setTitle("Kodi · 官方稳定版 "+asset[0].version)
        .setMessage("电视已装："+installed[0].name+"（"+installed[0].code+"）\n候选："+asset[0].name+"\nKodi 官方镜像未在此入口提供发布摘要。下载后必须核对电视已装应用签名、实际包名、版本及架构，才能确认安装。")
        .setPositiveButton("下载并核验",(d,w)->downloadVerifiedApp(pkg,"Kodi 官方镜像",file->OfficialKodi.download(asset[0],file)))
        .setNegativeButton("取消",null).show();
    });
  }
  private void showUpdateScan(List<UpdateScanRow> rows,List<String> unmatched,AdbClient client,String endpoint){
    if(adb!=client||!endpoint.equals(host+":"+prefs.getString("port","5555"))){dialog("扫描结果已失效","电视连接已变化，请重新扫描。");return;}
    LinearLayout content=column();content.setPadding(dp(16),dp(4),dp(16),dp(12));
    int newer=0;for(UpdateScanRow row:rows)if(row.candidate)newer++;
    content.addView(text("读取到 "+(rows.size()+unmatched.size())+" 个第三方应用 · "+rows.size()+" 个已配置来源 · "+newer+" 个较新发布线索",15,INK));
    gap(content,7);content.addView(text("扫描只读取发布信息。作者来源或用户指定仓库均需下载后检查实际包名、版本及签名；确认更新前不会安装。",12,MUTED));
    for(UpdateScanRow row:rows){
      LinearLayout card=card(content);
      card.addView(text(row.name,16,INK));
      card.addView(text(row.pkg+"\n电视："+(row.installed==null?"未确认":row.installed)+"\n发布："+(row.published==null?"未确认":row.published)+"\n"+row.status,12,row.candidate?TEAL:MUTED));
      addButton(card,row.candidate?"查看候选 APK 并核验":"查看此应用来源",false,()->{
        AppRow app=new AppRow();app.pkg=row.pkg;app.name=row.name;checkOfficialSource(app);
      });
    }
    List<UpdateScanRow> candidates=new ArrayList<>();
    for(UpdateScanRow row:rows)if(row.candidate&&!"app.orchard.tv".equals(row.pkg)&&!"app.halotv.launcher".equals(row.pkg)&&!"app.halotv.launcher.lite".equals(row.pkg))candidates.add(row);
    if(!candidates.isEmpty())addButton(content,"选择批量更新 · "+candidates.size()+" 项",true,()->selectBatchUpdates(candidates));
    if(rows.isEmpty())content.addView(text("本次没有匹配到已核实来源的应用。",13,MUTED));
    if(!unmatched.isEmpty())addButton(content,"查看未匹配来源的 "+unmatched.size()+" 个应用",false,()->{
      String[] names=unmatched.toArray(new String[0]);
      new DeckDialogBuilder(this).setTitle("未匹配来源 · 选择应用")
        .setItems(names,(d,which)->{AppRow app=new AppRow();app.pkg=names[which];app.name=names[which];githubUpdate(app);})
        .setNegativeButton("关闭",null).show();
    });
    ScrollView scroll=new ScrollView(this);scroll.addView(content);scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(480)));
    new DeckDialogBuilder(this).setTitle("电视应用更新扫描").setView(scroll)
      .setPositiveButton("重新扫描",(d,w)->scanInstalledUpdates()).setNegativeButton("关闭",null).show();
  }
  private void selectBatchUpdates(List<UpdateScanRow> candidates){
    String[] labels=new String[candidates.size()];boolean[] checked=new boolean[candidates.size()];
    for(int i=0;i<labels.length;i++){UpdateScanRow row=candidates.get(i);labels[i]=row.name+" · "+row.installed+" → "+row.published;checked[i]=true;}
    new DeckDialogBuilder(this).setTitle("选择要核验的更新")
      .setMultiChoiceItems(labels,checked,(d,which,isChecked)->checked[which]=isChecked)
      .setPositiveButton("下载并核验",(d,w)->{
        List<UpdateScanRow> selectedRows=new ArrayList<>();for(int i=0;i<checked.length;i++)if(checked[i])selectedRows.add(candidates.get(i));
        if(selectedRows.isEmpty()){dialog("未选择应用","请至少选择一项。");return;}
        stageBatchUpdates(selectedRows);
      }).setNegativeButton("取消",null).show();
  }
  private static final class BatchUpdate {
    final List<GenericPlan> ready=new ArrayList<>();final List<String> skipped=new ArrayList<>();
  }
  private void stageBatchUpdates(List<UpdateScanRow> rows){
    if(!requestConnection())return;
    final AdbClient client=adb;final String endpoint=host+":"+prefs.getString("port","5555");
    final BatchUpdate batch=new BatchUpdate();
    run("下载并核验批量更新",()->{
      for(UpdateScanRow row:rows){
        GenericPlan plan=null;
        try{
          if(adb!=client||!endpoint.equals(host+":"+prefs.getString("port","5555")))throw new IOException("电视连接已变化");
          OfficialAppSources.Source source=updateSource(row.pkg);
          if(source==null||source.type.equals(OfficialAppSources.AERIAL))throw new IOException("此来源需要单独检查");
          taskProgress("下载并核验 " +row.name+" · "+(batch.ready.size()+batch.skipped.size()+1)+"/"+rows.size());
          File file=new File(getCacheDir(),"batch_update_"+UUID.randomUUID()+".apk");
          plan=new GenericPlan();plan.files=Collections.singletonList(file);plan.owned=true;plan.client=client;plan.endpoint=endpoint;plan.source=source.type+" · "+source.locator;
          if(source.type.equals(OfficialAppSources.GITHUB)){
            GitHubAppRelease.Release release=GitHubAppRelease.latest(source.locator);
            if(release.apks.size()!=1)throw new IOException("发布包含 "+release.apks.size()+" 个 APK，请单独选择正确文件");
            GitHubAppRelease.download(release.apks.get(0),file);
          }else if(source.type.equals(OfficialAppSources.VIDEOLAN)){
            String abis=AerialProbe.properties(c->client.shell(c)).abis;OfficialVlc.Asset chosen=null;
            for(OfficialVlc.Asset asset:OfficialVlc.latest())if(Arrays.asList(abis.split(",")).contains(asset.abi)){chosen=asset;break;}
            if(chosen==null)throw new IOException("没有匹配电视架构的 APK");
            OfficialVlc.download(chosen,file);
          }else if(source.type.equals(OfficialAppSources.KODI)){
            String abis=AerialProbe.properties(c->client.shell(c)).abis;
            OfficialKodi.download(OfficialKodi.latest(kodiAbi(abis)),file);
          }else throw new IOException("未知来源");
          inspectGenericPlan(plan,row.pkg);
          if(!plan.before.present||plan.code<=plan.before.code)throw new IOException("候选版本编号没有高于电视已装版本");
          batch.ready.add(plan);plan=null;
        }catch(Exception e){batch.skipped.add(row.pkg+"："+message(e));}
        finally{if(plan!=null)plan.clean();}
      }
      return "核验通过 "+batch.ready.size()+" 项，跳过 "+batch.skipped.size()+" 项";
    },()->confirmBatchUpdates(batch,client,endpoint));
  }
  private void confirmBatchUpdates(BatchUpdate batch,AdbClient client,String endpoint){
    if(adb!=client||!endpoint.equals(host+":"+prefs.getString("port","5555"))){cleanBatch(batch);dialog("连接已变化","批量更新已停止，请重新扫描。");return;}
    StringBuilder summary=new StringBuilder();
    for(GenericPlan plan:batch.ready)summary.append("✓ ").append(plan.name).append(" · ").append(plan.before.name).append(" → ").append(plan.version).append("（").append(size(plan.total)).append("）\n");
    for(String issue:batch.skipped)summary.append("跳过 · ").append(issue).append("\n");
    if(batch.ready.isEmpty()){dialog("没有可安装项目",summary.toString());return;}
    summary.append("\n将依次更新 ").append(batch.ready.size()).append(" 项。每项安装前再次核对电视身份、已装版本和文件摘要；失败项会记录并继续下一项。安装后仍需打开应用确认运行正常。");
    new DeckDialogBuilder(this).setTitle("确认批量更新 · "+batch.ready.size()+" 项")
      .setMessage(summary.toString()).setPositiveButton("确认更新",(d,w)->executeBatchUpdates(batch))
      .setNegativeButton("取消",(d,w)->cleanBatch(batch)).setOnCancelListener(d->cleanBatch(batch)).show();
  }
  private void cleanBatch(BatchUpdate batch){for(GenericPlan plan:batch.ready)plan.clean();}
  private void executeBatchUpdates(BatchUpdate batch){
    final List<String> results=new ArrayList<>();
    run("批量更新电视应用",()->{
      try{
        for(int i=0;i<batch.ready.size();i++){
          GenericPlan plan=batch.ready.get(i);
          try{taskProgress("更新 "+(i+1)+"/"+batch.ready.size()+" · "+plan.name);installGenericPlan(plan);results.add("成功 · "+plan.pkg+" → "+plan.version);}
          catch(Exception e){results.add("失败 · "+plan.pkg+"："+message(e));
            if(adb!=plan.client||!plan.endpoint.equals(host+":"+prefs.getString("port","5555"))){results.add("电视连接已变化，剩余项目未安装");break;}
          }finally{plan.clean();}
        }
      }finally{cleanBatch(batch);}
      return android.text.TextUtils.join("\n",results);
    },()->{if(page==2)loadApps();dialog("批量更新结果",android.text.TextUtils.join("\n",results));});
  }
  private void checkVideoLanApp(String pkg){
    final List<OfficialVlc.Asset>[] assets=new List[]{null};final GenericState[] installed={null};final String[] tvAbis={null};
    final AdbClient client=adb;final String endpoint=host+":"+prefs.getString("port","5555");
    run("检查 VideoLAN 官方更新",()->{
      installed[0]=genericState(client,pkg,false);
      if(!installed[0].present)throw new IOException("电视应用已不存在，请重新读取应用列表");
      tvAbis[0]=AerialProbe.properties(c->client.shell(c)).abis;
      assets[0]=new ArrayList<>();for(OfficialVlc.Asset asset:OfficialVlc.latest())
        if(Arrays.asList(tvAbis[0].split(",")).contains(asset.abi))assets[0].add(asset);
      if(assets[0].isEmpty())throw new IOException("VideoLAN 最新稳定版没有匹配电视 CPU 架构的 APK（"+tvAbis[0]+"）");
      if(adb!=client||!endpoint.equals(host+":"+prefs.getString("port","5555")))throw new IOException("电视连接已变化，请重新检查");
      return "";
    },()->{
      if(adb!=client||!endpoint.equals(host+":"+prefs.getString("port","5555"))){dialog("连接已变化","请重新检查目标电视。");return;}
      String[] labels=new String[assets[0].size()];for(int i=0;i<labels.length;i++)labels[i]=assets[0].get(i).version+" · "+assets[0].get(i).abi;
      new DeckDialogBuilder(this).setTitle("VLC · VideoLAN 官方稳定版")
        .setItems(labels,(d,which)->downloadVerifiedApp(pkg,"VideoLAN 官方目录",file->OfficialVlc.download(assets[0].get(which),file)))
        .setNegativeButton("取消",null).show();
    });
  }

  private void githubUpdate(AppRow app){
    if(!requestConnection())return;
    if(app.system){dialog("系统应用不提供此入口","系统应用通常由固件更新管理。请在确认来源与设备支持后，使用“更新此应用”手动检查安装包。");return;}
    EditText field=field("owner/repo",prefs.getString("github_repo_"+app.pkg,""));
    new DeckDialogBuilder(this).setTitle("为「"+(app.name==null?app.pkg:app.name)+"」指定 GitHub 仓库")
      .setMessage("GitHub 无法从电视包名自动确认官方仓库。请填写你信任的项目地址；仅读取其最新稳定发布，并由你选择 APK。")
      .setView(field)
      .setPositiveButton("检查稳定版",(d,w)->{
        String repo;
        try{repo=GitHubAppRelease.repository(field.getText().toString());}
        catch(Exception e){dialog("仓库地址无效",message(e));return;}
        checkGitHubApp(app.pkg,repo);
      }).setNegativeButton("取消",null).show();
  }
  private void checkGitHubApp(String pkg,String repo){
    final GitHubAppRelease.Release[] release={null};final GenericState[] installed={null};
    final AdbClient client=adb;final String endpoint=host+":"+prefs.getString("port","5555");
    run("检查 GitHub 应用更新",()->{
      installed[0]=genericState(client,pkg,false);
      if(!installed[0].present)throw new IOException("电视上的应用已不存在，请重新读取列表");
      release[0]=GitHubAppRelease.latest(repo);
      if(adb!=client||!endpoint.equals(host+":"+prefs.getString("port","5555")))throw new IOException("电视连接已变化，请重新检查");
      if(release[0].apks.isEmpty())throw new IOException("最新稳定发布没有可直接安装的单个 APK；压缩包、APKS 和 XAPK 不会自动转换");
      return "";
    },()->{
      if(adb!=client||!endpoint.equals(host+":"+prefs.getString("port","5555"))){dialog("连接已变化","请重新检查目标电视上的应用。");return;}
      prefs.edit().putString("github_repo_"+pkg,repo).apply();
      String[] labels=new String[release[0].apks.size()];
      for(int i=0;i<labels.length;i++){GitHubAppRelease.Asset a=release[0].apks.get(i);labels[i]=a.name+" · "+size(a.size);}
      new DeckDialogBuilder(this).setTitle(repo+" · "+release[0].tag)
        .setItems(labels,(d,which)->downloadGitHubApp(pkg,repo,release[0].tag,release[0].apks.get(which)))
        .setNegativeButton("取消",null).show();
    });
  }
  private void downloadGitHubApp(String pkg,String repo,String tag,GitHubAppRelease.Asset asset){
    downloadVerifiedApp(pkg,"GitHub "+repo+" · "+tag,file->GitHubAppRelease.download(asset,file));
  }
  private interface VerifiedApkDownload {void run(File target)throws Exception;}
  private void downloadVerifiedApp(String pkg,String source,VerifiedApkDownload downloader){
    File file=new File(getCacheDir(),"github_app_"+UUID.randomUUID()+".apk");
    final AdbClient client=adb;final String endpoint=host+":"+prefs.getString("port","5555");
    run("下载官方应用更新",()->{
      try{
        downloader.run(file);
        if(adb!=client||!endpoint.equals(host+":"+prefs.getString("port","5555")))throw new IOException("电视连接已变化，请重新检查");
        PackageInfo candidate=archiveInfo(file);
        if(!pkg.equals(candidate.packageName))throw new IOException("GitHub APK 包名是 "+candidate.packageName+"，与电视应用 "+pkg+" 不一致；未安装");
        GenericState installed=genericState(client,pkg,false);
        if(!installed.present||packageVersion(candidate)<=installed.code)throw new IOException("候选 APK 版本编号未高于电视已装版本；未执行覆盖安装");
        String splits=ApkManifest.splits(Collections.singletonList(file));if(!splits.isEmpty())throw new IOException(splits);
        return "";
      }catch(Exception e){file.delete();throw e;}
    },()->{
      if(adb!=client||!endpoint.equals(host+":"+prefs.getString("port","5555"))){file.delete();dialog("连接已变化","请重新检查目标电视上的应用。");return;}
      prepareGeneric(Collections.singletonList(file),true,pkg,source);
    });
  }

  private static final class GenericState {boolean present;long code=-1;String path="",name="未安装",signers="";}
  private static final class GenericPlan {
    AdbClient client;String endpoint,pkg,name,version,source,signers,identity;long code,total;int sdk,minimum;String abis;
    boolean owned,consumed;GenericState before;List<File> files;List<String> hashes=new ArrayList<>();
    void clean(){if(owned&&files!=null)for(File f:files)f.delete();}
  }
  private GenericState genericState(AdbClient client,String pkg,boolean proof)throws Exception{
    GenericState state=new GenericState();String raw=PackageProbe.path(c->client.shell(c),pkg);if(raw.isEmpty())return state;
    state.present=true;state.path=AerialSafety.basePath(raw);String dump=client.shell("dumpsys package "+AdbClient.quote(pkg)).require();state.code=AerialSupport.versionCode(dump);state.name=AerialSupport.versionName(dump);
    if(state.code<0)throw new IOException("电视已有该应用，但版本未能读取，未继续安装");
    if(proof){InstalledProof verified=readInstalledProof(client,pkg);if(verified.code!=state.code||!verified.path.equals(state.path))throw new IOException("核对时电视应用已变化，请重新检查");state.signers=verified.signers;}
    return state;
  }
  private void prepareGeneric(List<File> selected,boolean owned,String expected,String source){
    if(!requestConnection()){if(owned)for(File f:selected)f.delete();return;}
    if(genericDialog!=null)genericDialog.dismiss();if(genericPlan!=null&&!genericPlan.consumed)genericPlan.clean();
    final GenericPlan plan=new GenericPlan();plan.files=new ArrayList<>(selected);plan.owned=owned;plan.client=adb;plan.endpoint=host+":"+prefs.getString("port","5555");plan.source=source;genericPlan=plan;
    run("检查应用安装更新",()->{
      try{inspectGenericPlan(plan,expected);return "";}
      catch(Exception e){plan.clean();throw e;}
    },()->showGenericPlan(plan));
  }
  private void inspectGenericPlan(GenericPlan plan,String expected)throws Exception{
        String issue=ApkManifest.splits(plan.files);if(!issue.isEmpty())throw new IOException(issue);
        PackageInfo candidate=archiveInfo(plan.files.get(0));plan.pkg=candidate.packageName;plan.version=candidate.versionName;plan.code=packageVersion(candidate);plan.minimum=candidate.applicationInfo.minSdkVersion;plan.signers=signerSet(candidate);candidate.applicationInfo.sourceDir=plan.files.get(0).getAbsolutePath();candidate.applicationInfo.publicSourceDir=candidate.applicationInfo.sourceDir;plan.name=candidate.applicationInfo.loadLabel(getPackageManager()).toString();
        for(File f:plan.files){plan.total+=f.length();plan.hashes.add(AerialDownload.sha256(f));}
        AerialProbe.Properties tv=AerialProbe.properties(c->plan.client.shell(c));plan.sdk=tv.sdk;plan.abis=tv.abis;plan.identity=DeviceScope.installationKey(host,prefs.getString("port","5555"),tv.serial,tv.fingerprint,tv.model);
        if(DesktopAssistant.activeUser(plan.client.shell("dumpsys accessibility").require())!=0)throw new IOException("当前电视用户未确认或非用户 0，未准备更新");
        plan.before=genericState(plan.client,plan.pkg,true);InstallPolicy.check(expected,plan.pkg,plan.code,plan.before.present,plan.before.code,plan.before.signers,plan.signers,plan.minimum,plan.sdk,ApkTransfer.abis(plan.files),plan.abis);
  }
  private void showGenericPlan(GenericPlan plan){
    String kind=plan.before.present?(plan.code>plan.before.code?"更新":"同版本覆盖"):"首次安装";
    String report=plan.name+"\n"+plan.pkg+"\n\n电视版本："+plan.before.name+(plan.before.present?"（"+plan.before.code+"）":"")+"\n候选版本："+plan.version+"（"+plan.code+"）"+"\n操作："+kind+"\n来源："+plan.source+" · "+plan.files.size()+" 个文件 · "+size(plan.total)+"\n系统："+ApkManifest.android(plan.sdk)+" · 最低 "+ApkManifest.android(plan.minimum)+"\n架构："+plan.abis+"\n签名："+(plan.before.present?"与电视已装版本一致":"首次安装，由电视最终校验")+"\n分包签名由电视安装会话最终校验。\n\n覆盖安装通常保留数据；账号、数据迁移和遥控适配由应用自身决定。安装后将重新读取版本。不会自动卸载、清数据或降级。";
    genericDialog=new DeckDialogBuilder(this).setTitle("安装 / 更新确认").setMessage(report).setNegativeButton("取消",(d,w)->plan.clean()).setPositiveButton(plan.before.present?"确认更新":"确认安装",(d,w)->{plan.consumed=true;executeGeneric(plan);}).setOnCancelListener(d->plan.clean()).create();genericDialog.show();
  }
  private void executeGeneric(GenericPlan plan){
    run("安装或更新电视应用",()->{
      try{return installGenericPlan(plan);}
      finally{plan.clean();}
    },()->{if(page==2)loadApps();dialog("安装版本已确认","已重新读取并核对版本："+plan.code+"\n"+plan.pkg+"\n\n运行稳定性、登录状态仍需打开应用验证。");});
  }
  private String installGenericPlan(GenericPlan plan)throws Exception{
        if(adb!=plan.client||!plan.endpoint.equals(host+":"+prefs.getString("port","5555")))throw new IOException("设备连接变化，未继续安装");
        AerialProbe.Properties tv=AerialProbe.properties(c->plan.client.shell(c));if(!plan.identity.equals(DeviceScope.installationKey(host,prefs.getString("port","5555"),tv.serial,tv.fingerprint,tv.model)))throw new IOException("电视身份变化，未安装");
        GenericState current=genericState(plan.client,plan.pkg,false);if(current.present!=plan.before.present||current.code!=plan.before.code||!current.path.equals(plan.before.path))throw new IOException("电视已装应用发生变化，请重新选择并检查");
        InstallPolicy.check(plan.pkg,plan.pkg,plan.code,current.present,current.code,plan.before.signers,plan.signers,plan.minimum,tv.sdk,ApkTransfer.abis(plan.files),tv.abis);
        for(int i=0;i<plan.files.size();i++)if(!plan.hashes.get(i).equals(AerialDownload.sha256(plan.files.get(i))))throw new IOException("来源安装文件已变化，请重新选择");
        ApkTransfer.install(plan.files,new ApkTransfer.Device(){public void push(File file,String path)throws Exception{taskProgress("正在传输 · "+file.getName());try(InputStream in=new FileInputStream(file)){plan.client.push(in,path,n->transferProgress(n));}}public String shell(String command)throws Exception{if(command.startsWith("pm install"))taskProgress("正在安装，等待电视确认…");return plan.client.shell(command,120000).require();}});
        taskProgress("正在复查电视安装版本…");GenericState after=genericState(plan.client,plan.pkg,false);if(!after.present||after.code!=plan.code)throw new IOException("安装命令返回成功，但最终版本未能核实。请先检查电视，勿直接重复安装。目标版本："+plan.code+"；读回："+after.code);
        appLoaded=false;return "已确认电视版本："+after.name+"（"+after.code+"）\n包名："+plan.pkg;
  }

  private String join(String path, String name) {
    return (path.equals("/") ? "/" : path + "/") + name;
  }

  private String size(long n) {
    return n < 1024
        ? n + " B"
        : n < 1048576
            ? String.format(Locale.ROOT, "%.1f KB", n / 1024d)
            : n < 1073741824
                ? String.format(Locale.ROOT, "%.1f MB", n / 1048576d)
                : String.format(Locale.ROOT, "%.2f GB", n / 1073741824d);
  }

  private LinearLayout fileList;

  private void files() {
    pageIntro("文件空间", "手机与设备之间，文件来去自如。");
    LinearLayout shortcuts = card(body);
    section(shortcuts, "常用位置", "这些是目录快捷入口，实际文件在连接后读取。");
    pair(
        shortcuts,
        "内部存储",
        () -> openFolder("/sdcard"),
        "下载目录",
        () -> openFolder("/sdcard/Download"));
    LinearLayout c = card(body);
    section(c, "设备文件", "访问范围由设备的 ADB 权限决定。首版不跟随目录符号链接。");
    TextView path = text(folder, 14, TEAL);
    path.setTextIsSelectable(true);
    c.addView(path);
    pair(
        c,
        "上一级",
        () -> {
          if (!folder.equals("/")) {
            int at = folder.lastIndexOf('/');
            folder = at <= 0 ? "/" : folder.substring(0, at);
          }
          show(3);
        },
        "转到路径",
        () ->
            prompt(
                "设备绝对路径",
                folder,
                s -> {
                  if (!s.startsWith("/") || s.contains("\0")) {
                    toast("请填写绝对路径");
                    return;
                  }
                  folder = s.length() > 1 && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
                  show(3);
                }));
    pair(
        c,
        "上传文件",
        () -> pick(101),
        "新建文件夹",
        () ->
            prompt(
                "文件夹名称",
                "",
                s -> {
                  if (s.contains("/") || s.equals(".") || s.equals("..") || s.contains("\0")) {
                    toast("名称无效");
                    return;
                  }
                  command("新建文件夹", "mkdir " + AdbClient.quote(join(folder, s)), () -> loadFiles());
                }));
    addButton(c, "刷新当前目录", false, () -> loadFiles());
    fileList = column();
    body.addView(fileList);
    if (connected()) loadFiles();
    else emptyState(fileList, 3, "文件列表待连接后读取", "你可以先选择目录、查看上传下载入口。连接后会读取选定路径，目录权限以设备实际授权为准。");
  }

  private void openFolder(String path) {
    folder = path;
    show(3);
  }

  private void loadFiles() {
    final String path = folder;
    final List<AdbClient.Entry>[] entries = new List[] {null};
    run(
        "读取目录",
        () -> {
          require();
          entries[0] = adb.list(path);
          return "";
        },
        () -> {
          if (page != 3 || !folder.equals(path)) return;
          fileList.removeAllViews();
          if (entries[0].isEmpty()) fileList.addView(text("目录为空，或设备未授予读取权限。", 13, MUTED));
          for (AdbClient.Entry e : entries[0]) {
            LinearLayout c = card(fileList);
            c.addView(text((e.directory() ? "▣  " : "↧  ") + e.name, 15, INK));
            gap(c, 4);
            c.addView(text(e.directory() ? "文件夹" : size(e.size), 12, MUTED));
            c.setOnClickListener(
                v -> {
                  if (e.directory()) {
                    folder = join(path, e.name);
                    show(3);
                  } else fileActions(path, e);
                });
            c.setOnLongClickListener(
                v -> {
                  fileActions(path, e);
                  return true;
                });
          }
        });
  }

  private void fileActions(String base, AdbClient.Entry e) {
    final String path = join(base, e.name);
    String[] options =
        e.directory() ? new String[] {"重命名", "删除文件夹"} : new String[] {"下载到手机", "重命名", "删除文件"};
    new DeckDialogBuilder(this)
        .setTitle(e.name)
        .setItems(
            options,
            (d, w) -> {
              int action = e.directory() ? w + 1 : w;
              if (action == 0) {
                saveRemote = path;
                saveName = e.name;
                createDocument(103, "application/octet-stream", e.name);
              } else if (action == 1)
                prompt(
                    "重命名",
                    e.name,
                    s -> {
                      if (s.contains("/") || s.equals(".") || s.equals("..") || s.contains("\0")) {
                        toast("名称无效");
                        return;
                      }
                      String dest = join(base, s);
                      command(
                          "重命名",
                          "if [ -e "
                              + AdbClient.quote(dest)
                              + " ]; then echo '目标名称已存在'; exit 1; else mv "
                              + AdbClient.quote(path)
                              + " "
                              + AdbClient.quote(dest)
                              + "; fi",
                          () -> loadFiles());
                    });
              else
                confirm(
                    "删除后无法撤销",
                    path + (e.directory() ? "\n本版仅删除空文件夹，保留非空目录。" : "\n文件将从设备删除。"),
                    () ->
                        command(
                            "删除文件",
                            (e.directory() ? "rmdir " : "rm ") + AdbClient.quote(path),
                            () -> loadFiles()));
            })
        .show();
  }

  private void tools() {
    pageIntro("工具箱", "常用操作随手用，命令与记录随时查。");
    LinearLayout cleanup=card(body);
    section(cleanup,"存储管理","查看电视空间，或请求系统清理应用缓存。");
    pair(cleanup,"查看剩余空间",()->scanTvStorage(),"打开电视存储设置",()->openTvStorageSettings());
    addButton(cleanup,"一键清理",false,()->cleanCache());
    addButton(cleanup,"查看下载文件",false,()->openFolder("/sdcard/Download"));
    LinearLayout c = card(body);
    section(c, "日常工具", "先了解设备状态，再做有依据的调整。");
    pair(
        c,
        "设备详情",
        () -> deviceDetails(),
        "性能快照",
        () ->
            command(
                "性能快照",
                "echo '[负载：1 / 5 / 15 分钟]'; cat /proc/loadavg; echo '[内存，单位 kB]'; cat /proc/meminfo"
                    + " | head -20; echo '[存储]'; df /data /sdcard",
                null));
    pair(c, "显示参数", () -> displaySettings(), "任务与记录", () -> taskCenter());
    addButton(c,"导出第三方应用包名",false,()->exportAppInventory());
    addButton(
        c,
        "正常重启设备",
        false,
        () ->
            confirm(
                "重启设备",
                "设备会断开连接，开机后可能需要重新开启网络调试。",
                () ->
                    run(
                        "发送重启请求",
                        () -> {
                          require();
                          try (AdbClient.Channel channel = adb.open("reboot:")) {}
                          adb.close();
                          return "已发送重启请求，请查看设备状态";
                        },
                        () -> show(0))));
    LinearLayout terminal = card(body);
    terminal.setBackground(new GlassSurface(terminal,(AuroraLayout)root,22,false));
    terminal.addView(text("TERMINAL  /  命令工作台", 12, UiPalette.color(0xffe8bf85)));
    gap(terminal, 8);
    TextView terminalTitle = text("命令终端", 21, INK);
    terminalTitle.setTypeface(null, Typeface.BOLD);
    terminal.addView(terminalTitle);
    gap(terminal, 6);
    terminal.addView(text("支持多行编辑，执行后完整回显；上次结果离线也能查看。", 12, UiPalette.color(0xffb0bfd5)));
    gap(terminal, 14);
    EditText cmd = field("例如：getprop ro.product.model", draftCommand);
    commandInput = cmd;
    cmd.setTypeface(Typeface.MONOSPACE);
    cmd.setTextColor(UiPalette.color(0xffedf3ff));
    cmd.setHintTextColor(UiPalette.color(0xff9fb2ce));
    cmd.setBackground(shape(UiPalette.color(0xff21334e), 14));
    cmd.setSingleLine(false);
    cmd.setMinLines(2);
    terminal.addView(cmd);
    addButton(
        terminal,
        "执行命令",
        true,
        () -> {
          String s = cmd.getText().toString().trim();
          if (s.isEmpty()) return;
          confirm("执行自定义命令", s + "\n\n请确认命令内容；删除、清除数据等操作无法撤销。", () -> command("自定义命令", s, null));
        });
    addButton(
        terminal,
        "保存为快捷命令",
        false,
        () -> {
          String s = cmd.getText().toString().trim();
          if (s.isEmpty()) {
            toast("请先输入命令");
            return;
          }
          prompt("快捷命令名称", "", n -> saveShortcut(n, s));
        });
    addButton(terminal, "上次执行结果", false, () -> previousCommand());
    renderShortcuts(terminal);
    body.removeView(terminal);
    body.addView(terminal, body.indexOfChild(c));
    c.setVisibility(View.GONE);
    TextView more=button("更多设备工具  ›",false,()->{});
    more.setOnClickListener(v->{boolean open=c.getVisibility()!=View.VISIBLE;c.setVisibility(open?View.VISIBLE:View.GONE);more.setText(open?"收起设备工具  ﹀":"更多设备工具  ›");});
    body.addView(more,body.indexOfChild(c));
  }

  private String tvStorage(AdbClient client)throws Exception{
    AdbClient.Result result=client.shell("df -k /data",30000);
    return result.exitCode==0?result.require():client.shell("df /data",30000).require();
  }
  private void scanTvStorage(){
    if(!requestConnection())return;
    final String[] report={""};
    run("查看电视剩余空间",()->{report[0]=tvStorage(adb);return "";},()->new DeckDialogBuilder(this)
      .setTitle("电视内部存储").setMessage(report[0]+"\n\n该读数仅供查看，不会删除任何内容。")
      .setPositiveButton("一键清理",(d,w)->cleanCache())
      .setNeutralButton("打开电视存储设置",(d,w)->openTvStorageSettings())
      .setNegativeButton("关闭",null).show());
  }
  private void cleanCache(){
    if(!requestConnection())return;
    final AdbClient client=adb;
    final CacheCleaner.Outcome[] outcome={null};
    run("一键清理",()->{
      outcome[0]=CacheCleaner.execute(command->{
        if(client!=adb||!client.isConnected())throw new IOException("电视连接已变化或中断");
        try{AdbClient.Result response=client.shell(command,120000);return new CacheCleaner.Reply(response.exitCode,response.raw);}
        catch(AdbClient.ShellFailure e){throw new IOException(e.getMessage()+"\n"+e.raw,e);}
      },before->{
        taskProgress("清理前可用约 "+size(before)+" · 正在请求系统清理缓存");
        java.util.concurrent.CountDownLatch displayed=new java.util.concurrent.CountDownLatch(1);
        ui.post(()->displayed.countDown());
        if(!displayed.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("无法显示清理前空间，未执行清理");
      });
      if(outcome[0].failed||!outcome[0].afterKnown||outcome[0].after<=outcome[0].before)throw new IOException(outcome[0].report());
      return "";
    },()->dialog("一键清理结果",outcome[0].report()));
  }
  private void openTvStorageSettings(){
    if(!requestConnection())return;
    run("打开电视存储设置",()->{
      AdbClient target=adb;
      for(String action:new String[]{"android.settings.INTERNAL_STORAGE_SETTINGS","android.settings.MANAGE_APPLICATIONS_SETTINGS","android.settings.SETTINGS"}){
        AdbClient.Result result=target.shell("am start -a "+action,30000);
        String output=result.text.toLowerCase(Locale.ROOT);
        if(result.exitCode==0&&!output.contains("error:")&&!output.contains("exception")&&!output.contains("unable to resolve"))return action;
      }
      throw new IOException("电视固件没有提供可打开的设置页面。请用电视原装遥控器进入设置 → 应用 → 应用存储 → 清除缓存。请勿选择「清除数据」。");
    },()->dialog("已请求打开电视设置","请看电视屏幕，用遥控器进入应用管理，选择占空间的应用，逐个点「清除缓存」。不要点「清除数据」，后者可能删除登录与配置。部分电视会拦截设置页面跳转。"));
  }

  private void saveShortcut(String name, String cmd) {
    try {
      JSONArray a = new JSONArray(prefs.getString("shortcuts", "[]"));
      JSONObject o = new JSONObject();
      o.put("name", name);
      o.put("cmd", cmd);
      a.put(o);
      prefs.edit().putString("shortcuts", a.toString()).apply();
      show(4);
    } catch (Exception e) {
      dialog("保存失败", message(e));
    }
  }

  private void renderShortcuts(LinearLayout l) {
    try {
      JSONArray a = new JSONArray(prefs.getString("shortcuts", "[]"));
      for (int i = 0; i < a.length(); i++) {
        final int index = i;
        JSONObject o = a.getJSONObject(i);
        String n = o.getString("name"), cmd = o.getString("cmd");
        TextView b = button(n, false, () -> confirm(n, cmd, () -> command(n, cmd, null)));
        LinearLayout.LayoutParams p = full();
        p.topMargin = dp(8);
        l.addView(b, p);
        b.setOnLongClickListener(
            v -> {
              confirm(
                  "删除快捷命令",
                  n,
                  () -> {
                    a.remove(index);
                    prefs.edit().putString("shortcuts", a.toString()).apply();
                    show(4);
                  });
              return true;
            });
      }
    } catch (Exception ignored) {
    }
  }

  private void recovery() {
    if(!requestConnection())return;
    final String key;try{key=recoveryKey();}catch(IOException e){dialog("设备身份未确认",e.getMessage());return;}
    Set<String> records=new HashSet<>(prefs.getStringSet(key,Collections.emptySet()));
    Set<String> legacy=new HashSet<>(prefs.getStringSet("disabled:"+host,Collections.emptySet()));
    if(records.isEmpty()){
      AlertDialog.Builder b=new DeckDialogBuilder(this).setTitle("恢复中心").setMessage("当前设备暂无停用记录。只恢复启用状态，不恢复卸载或清除的数据。\n\n记录按设备身份、地址和端口区分。"+(legacy.isEmpty()?"":"\n发现旧版按 IP 保存的记录，核对后可手动导入。" )).setNegativeButton("关闭",null);
      if(!legacy.isEmpty())b.setPositiveButton("核对旧记录",(d,w)->importLegacyRecovery(key,legacy));b.show();return;
    }
    String[] names=records.toArray(new String[0]);AlertDialog.Builder b=new DeckDialogBuilder(this).setTitle("选择要重新启用的应用").setItems(names,(d,w)->{AppRow a=new AppRow();a.pkg=names[w];a.disabled=true;confirm("恢复启用",a.pkg+"\n设备："+device+" · "+host+":"+prefs.getString("port","5555"),()->setEnabled(a,true));}).setNegativeButton("关闭",null);
    if(!legacy.isEmpty())b.setNeutralButton("旧版记录",(d,w)->importLegacyRecovery(key,legacy));b.show();
  }
  private void importLegacyRecovery(String key,Set<String> legacy){
    confirm("核对旧记录归属", "旧记录没有端口和设备身份，不能自动认定属于当前电视。\n当前："+device+" · "+host+":"+prefs.getString("port","5555")+"\n\n"+String.join("\n",legacy)+"\n\n仅在确认这些应用由你在此电视停用时继续。只导入目前仍停用的应用，不执行启用。",()->run("核对旧恢复记录",()->{
      if(!key.equals(recoveryKey()))throw new IOException("设备身份变化，请重新核对");Set<String> disabled=packages(adb.shell("pm list packages -d").require());Set<String> merged=new HashSet<>(prefs.getStringSet(key,Collections.emptySet()));for(String pkg:legacy)if(disabled.contains(pkg))merged.add(pkg);
      if(!prefs.edit().putStringSet(key,merged).remove("disabled:"+host).commit())throw new IOException("旧记录未保存，请重试");return "已按当前设备保存恢复记录";
    },()->recovery()));
  }

  private void displaySettings() {
    final String[] values = {null};
    run(
        "读取显示参数",
        () -> {
          require();
          values[0] = adb.shell("wm size; wm density").require();
          return values[0];
        },
        () -> {
          LinearLayout panel = column();
          panel.setPadding(dp(20), dp(8), dp(20), dp(16));
          panel.addView(text(values[0], 14, INK));
          gap(panel, 8);
          panel.addView(text("修改可能导致界面大小异常。首次修改前的输出会保存在本机；默认恢复会清除设备已有的自定义覆盖。", 12, MUTED));
          final AlertDialog alert =
              new DeckDialogBuilder(this)
                  .setTitle("显示参数")
                  .setView(panel)
                  .setNegativeButton("关闭", null)
                  .create();
          addButton(
              panel,
              "修改 DPI",
              false,
              () -> {
                alert.dismiss();
                changeDisplay(true, values[0]);
              });
          addButton(
              panel,
              "修改分辨率",
              false,
              () -> {
                alert.dismiss();
                changeDisplay(false, values[0]);
              });
          String before = prefs.getString("displayBefore:" + host, "");
          if (!before.isEmpty())
            addButton(
                panel,
                "恢复本 App 首次修改前的显示",
                false,
                () -> {
                  alert.dismiss();
                  String cmd = displayRestoreCommand(before);
                  if (cmd == null) {
                    dialog("无法解析旧设置", before + "\n请对照上述记录手动恢复，或使用恢复默认显示。");
                    return;
                  }
                  confirm(
                      "恢复之前的显示参数",
                      before + "\n请确认是同一台设备。",
                      () ->
                          command(
                              "恢复之前显示",
                              cmd,
                              () -> prefs.edit().remove("displayBefore:" + host).apply()));
                });
          addButton(
              panel,
              "恢复设备默认显示",
              false,
              () -> {
                alert.dismiss();
                confirm(
                    "恢复默认显示",
                    "将清除自定义 DPI 和分辨率覆盖。",
                    () -> command("恢复默认显示", "wm size reset && wm density reset", null));
              });
          alert.show();
        });
  }

  private String displayRestoreCommand(String before) {
    return DisplayState.restoreCommand(before);
  }

  private void changeDisplay(boolean dpi, String before) {
    prompt(
        dpi ? "DPI（例如 240）" : "分辨率（例如 1280x720）",
        "",
        v -> {
          boolean valid = dpi ? v.matches("[0-9]{2,4}") : v.matches("[0-9]{3,4}x[0-9]{3,4}");
          if (!valid) {
            toast("请输入正确格式");
            return;
          }
          if (dpi && (Integer.parseInt(v) < 72 || Integer.parseInt(v) > 1000)) {
            toast("DPI 范围为 72–1000");
            return;
          }
          confirm(
              "修改显示参数",
              v + "\n可能导致界面异常，请保留当前连接以便恢复。",
              () -> {
                if (!prefs.contains("displayBefore:" + host))
                  prefs.edit().putString("displayBefore:" + host, before).apply();
                command("修改显示参数", "wm " + (dpi ? "density " : "size ") + v, null);
              });
        });
  }

  private String[] currentAddress() {
    String h=page==0&&hostInput!=null?hostInput.getText().toString().trim():draftHost.trim();
    String p=page==0&&portInput!=null?portInput.getText().toString().trim():draftPort.trim();
    try {
      int port=Integer.parseInt(p);
      if(h.isEmpty()||h.length()>253||h.contains("/")||h.matches(".*\\s.*")||port<1||port>65535)throw new Exception();
      return new String[]{h,Integer.toString(port)};
    }catch(Exception e){dialog("地址需要检查","请先在设备页填写 IP 或主机名，端口为 1–65535，不要带 http://。");return null;}
  }

  private void saveAddress() {
    String[] address=currentAddress();if(address==null)return;
    editDevice(address[0],address[1]);
  }

  private void editDevice(String h,String p) {
    JSONArray book=savedDevices();
    JSONObject old=DeviceBook.find(book,h,p);
    if(deviceBookUnavailable){dialog("保存失败","损坏的设备资料未能备份，原始数据已保留，请检查手机存储空间。");return;}
    if(old==null&&book.length()>=20){dialog("收藏夹已满","最多保存 20 台设备，请先移除不再使用的地址。");return;}
    EditText name=field("例如：客厅电视",old==null?"":old.optString("alias"));
    name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
    LinearLayout content=column();content.setPadding(dp(24),dp(8),dp(24),dp(12));
    content.addView(text(h+":"+p,13,MUTED));gap(content,10);content.addView(name);
    new DeckDialogBuilder(this).setTitle("设备昵称").setView(content).setNegativeButton("取消",null)
      .setPositiveButton("保存",(d,w)->{
        try {
          JSONArray latest=savedDevices();
          if(deviceBookUnavailable)throw new IOException("损坏的设备资料未能备份，未覆盖原始数据");
          prefs.edit().putString("devices",DeviceBook.save(latest,h,p,null,name.getText().toString().trim(),false).toString()).apply();
          if(page==0)show(0);toast("已保存设备地址");
        }catch(Exception e){dialog("保存失败",message(e));}
      }).show();
  }

  private void connectSaved(JSONObject o) {
    if(busy){toast("请等待当前任务完成");return;}
    if(page!=0)show(0);
    prefs.edit().putString("connect_mode",o.optString("transport","legacy")).apply();hostInput.setText(o.optString("host"));portInput.setText(o.optString("port","5555"));connect(false);
  }

  private void deviceBook() {
    JSONArray entries=DeviceBook.sorted(savedDevices());
    LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),dp(8));
    content.addView(text("最多 20 台 · 点击设备直接连接或切换 · 长按管理",12,MUTED));
    addButton(content,"保存当前地址",false,()->saveAddress());
    ScrollView scroll=new ScrollView(this);LinearLayout list=column();scroll.addView(list);
    content.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(340),getResources().getDisplayMetrics().heightPixels/3)));
    AlertDialog modal=new DeckDialogBuilder(this).setTitle("设备收藏夹").setView(content).setNegativeButton("关闭",null).create();
    if(entries.length()==0){gap(list,20);list.addView(text("还没有收藏设备。可在设备页保存地址，成功连接的设备也会自动加入。",14,MUTED));}
    for(int i=0;i<entries.length();i++){
      JSONObject o=entries.optJSONObject(i);if(o==null)continue;
      String last=o.optLong("last")>0?new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(o.optLong("last"))):"尚无连接时间";
      TextView entry=button((o.optBoolean("pinned")?"★ ":"")+DeviceBook.label(o)+"\n"+o.optString("host")+":"+o.optString("port")+" · "+last,false,()->{modal.dismiss();connectSaved(o);});
      LinearLayout.LayoutParams entryLp=full();entryLp.topMargin=dp(8);list.addView(entry,entryLp);
      addButton(list,"管理 "+DeviceBook.label(o),false,()->manageSavedDevice(modal,o));
      entry.setOnLongClickListener(v->{manageSavedDevice(modal,o);return true;});

    }
    modal.show();
  }

  private void manageSavedDevice(AlertDialog modal,JSONObject o){
    String h=o.optString("host"),p=o.optString("port","5555");
    new DeckDialogBuilder(this).setTitle(DeviceBook.label(o)).setItems(new String[]{"连接此设备","诊断此地址","修改昵称",o.optBoolean("pinned")?"取消置顶":"置顶设备","移除收藏"},(d,which)->{
      modal.dismiss();
      if(which==0)connectSaved(o);else if(which==1)diagnose(h,p);else if(which==2)editDevice(h,p);else try{
        JSONArray latest=savedDevices();if(deviceBookUnavailable)throw new IOException("损坏的设备资料未能备份，未覆盖原始数据");
        if(which==4)latest=DeviceBook.remove(latest,h,p);else{JSONObject found=DeviceBook.find(latest,h,p);if(found!=null)found.put("pinned",!found.optBoolean("pinned"));latest=DeviceBook.sorted(latest);}
        prefs.edit().putString("devices",latest.toString()).apply();if(page==0)show(0);deviceBook();
      }catch(Exception e){dialog("修改失败",message(e));}
    }).show();
  }

  private void diagnoseAddress() {String[] a=currentAddress();if(a!=null)diagnose(a[0],a[1]);}
  private void diagnose(String h,String p) {
    final String[] report={""};final AdbClient current=adb;
    runAt("连接诊断",h,p,()->{
      String id=activeTaskId;if(id!=null)journal.progress(id,"正在检查地址与端口…");
      if(current!=null&&current==adb&&current.isConnected()&&h.equals(host)&&p.equals(prefs.getString("port","5555"))) {
        try {
          current.shell(":",5000).require();
          report[0]="目标："+h+":"+p+"\n\n✓ 当前 ADB 已连接\n✓ 只读 Shell 响应正常\n\n如果某个应用操作失败，可在任务中心查看对应任务的原始原因。";
        }catch(Exception e){report[0]="目标："+h+":"+p+"\n\n当前 ADB 无法正常响应。请重新连接并检查电视授权。\n\n原始信息："+message(e);}
      }else report[0]=ConnectionDiagnostic.probe(h,Integer.parseInt(p));
      return report[0];
    },()->dialog("连接诊断",report[0]));
  }

  private void taskCenter() {
    LinearLayout content=column();content.setPadding(dp(18),dp(4),dp(18),dp(8));
    TextView summary=text("",12,MUTED);content.addView(summary);
    final int[] filter={0};final Runnable[] render={null};
    final long[] revision={-1};final int[] lastFilter={-1};
    pair(content,"全部",()->{filter[0]=0;render[0].run();},"进行中",()->{filter[0]=1;render[0].run();});
    pair(content,"失败 / 中断",()->{filter[0]=2;render[0].run();},"清除已结束",()->new DeckDialogBuilder(this).setTitle("清除任务历史？").setMessage("只清除已结束任务的记录，进行中的任务和电视文件不会删除。").setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{journal.clearFinished();pruneCommandHistory(new File(getFilesDir(),"command-results"));render[0].run();}).show());
    ScrollView scroll=new ScrollView(this);LinearLayout list=column();scroll.addView(list);content.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(340),getResources().getDisplayMetrics().heightPixels/3)));
    AlertDialog modal=new DeckDialogBuilder(this).setTitle("任务与记录").setView(content).setNegativeButton("关闭",null).setNeutralButton("旧版操作记录",(d,w)->logs()).create();
    render[0]=()->{
      if(revision[0]==journal.revision()&&lastFilter[0]==filter[0])return;
      revision[0]=journal.revision();lastFilter[0]=filter[0];
      int scrollY=scroll.getScrollY();
      list.removeAllViews();JSONArray entries=journal.snapshot();int active=0,shown=0;
      for(int i=0;i<entries.length();i++) {
        JSONObject o=entries.optJSONObject(i);if(o==null)continue;
        boolean ongoing=TaskJournal.active(o);if(ongoing)active++;
        if(filter[0]==1&&!ongoing)continue;
        if(filter[0]==2&&!o.optString("state").equals("failed")&&!o.optString("state").equals("interrupted")&&!o.optString("state").equals("partial"))continue;
        shown++;
        String caption=TaskJournal.state(o)+" · "+o.optString("name")+"\n"+o.optString("host")+":"+o.optString("port");
        if(ongoing&&!o.optString("progress").isEmpty())caption+="\n"+o.optString("progress");
        if(!ongoing){long elapsed=o.optLong("ended")-o.optLong("started",o.optLong("created"));caption+=" · "+Math.max(0,elapsed/1000)+" 秒";}
        addButton(list,caption,false,()->taskDetail(o));
      }
      summary.setText((filter[0]==0?"全部":filter[0]==1?"进行中":"失败 / 中断")+" · "+shown+" 条 · "+active+" 项进行中\n历史最多约 80 条；命令全文按任务保存，合计最多 64 MiB。");
      if(shown==0){gap(list,20);list.addView(text("这里还没有任务",14,MUTED));}
      scroll.post(()->scroll.scrollTo(0,scrollY));
    };
    final Runnable pulse=new Runnable(){public void run(){if(modal.isShowing()){render[0].run();ui.postDelayed(this,1500);}}};
    modal.setOnDismissListener(d->ui.removeCallbacks(pulse));modal.show();render[0].run();ui.postDelayed(pulse,1500);
  }

  private void pruneCommandHistory(File directory){
    File[] files=directory.listFiles((dir,name)->name.endsWith(".json"));if(files==null)return;Set<String> retained=new HashSet<>();JSONArray tasks=journal.snapshot();for(int i=0;i<tasks.length();i++)retained.add(tasks.optJSONObject(i).optString("commandFile"));Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));long total=0;int count=0;for(File file:files){total+=file.length();if(!retained.contains(file.getName())||++count>80||total>64L*1024*1024)file.delete();}
  }

  private void taskDetail(JSONObject o) {
    String detail=TaskJournal.state(o)+" · "+o.optString("name")+"\n目标："+o.optString("host")+":"+o.optString("port")+"\n创建："+new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(o.optLong("created")))+"\n\n"+o.optString("progress")+"\n"+o.optString("detail",TaskJournal.active(o)?"尚未收到任务完成状态。":"无额外文本返回。");
    TextView text=text(detail,14,INK);text.setTextIsSelectable(true);text.setPadding(dp(24),dp(12),dp(24),dp(12));
    ScrollView scroll=new ScrollView(this);scroll.addView(text);
    AlertDialog.Builder b=new DeckDialogBuilder(this).setTitle("任务详情").setView(scroll).setNegativeButton("关闭",null);
    String resultName=o.optString("commandFile");if(resultName.matches("[0-9a-f-]{36}\\.json"))b.setNeutralButton("完整命令结果",(d,w)->run("读取命令结果",()->{File file=new File(new File(getFilesDir(),"command-results"),resultName);if(!file.isFile())throw new IOException("该完整结果已按历史保留上限清理，任务摘要仍可查看");CommandRecord record=CommandRecord.read(file);ui.post(()->showCommandResult(record));return "";},null));
    String name=o.optString("name");boolean failed=o.optString("state").equals("failed")||o.optString("state").equals("interrupted");
    if(failed&&(name.equals("连接诊断")||name.equals("读取应用列表")||name.equals("读取目录")))
      b.setPositiveButton("重新读取",(d,w)->{
        if(name.equals("连接诊断")){diagnose(o.optString("host"),o.optString("port","5555"));return;}
        if(!connected()||!host.equals(o.optString("host"))||!prefs.getString("port","5555").equals(o.optString("port"))){dialog("请先连接原设备","该任务属于另一地址，连接原设备后才能重新读取。");return;}
        if(name.equals("读取应用列表")){appLoaded=false;show(2);}else {folder=o.optString("path","/sdcard");show(3);}
      });
    b.show();
  }

  private static String signerSet(PackageInfo pi)throws Exception{
    android.content.pm.Signature[] signatures=Build.VERSION.SDK_INT>=28&&pi.signingInfo!=null?pi.signingInfo.getApkContentsSigners():pi.signatures;
    if(signatures==null||signatures.length==0)throw new IOException("无法读取安装包签名");TreeSet<String> values=new TreeSet<>();
    for(android.content.pm.Signature signature:signatures){byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());StringBuilder hex=new StringBuilder();for(byte b:hash)hex.append(String.format(Locale.ROOT,"%02x",b&255));values.add(hex.toString());}return String.join(",",values);
  }
  private PackageInfo archiveInfo(File file)throws IOException{
    int flags=Build.VERSION.SDK_INT>=28?(PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_SIGNATURES):PackageManager.GET_SIGNATURES;
    PackageInfo info=getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),flags);if(info==null||info.applicationInfo==null)throw new IOException("无法识别 APK 清单与签名");return info;
  }
  private static final class InstalledProof {String pkg,name,sha,signers,path;long code;boolean homeCategory;}
  private InstalledProof installedProof(AdbClient client,String pkg)throws Exception{
    if(!Arrays.asList(AerialSupport.FULL,AerialSupport.LITE).contains(pkg))throw new IOException("非 Aerial 应用");
    return readInstalledProof(client,pkg);
  }
  private InstalledProof readInstalledProof(AdbClient client,String pkg)throws Exception{
    String path=AerialSafety.basePath(PackageProbe.path(c->client.shell(c),pkg));File copy=new File(getCacheDir(),"aerial_proof_"+UUID.randomUUID()+".apk");
    try{
      taskProgress("正在核对电视现有安装包签名与指纹…");
      try(OutputStream out=new FileOutputStream(copy)){client.pull(path,out,n->{if(n>100L*1024*1024)throw new IllegalStateException("已安装 APK 超过核对大小限制");});}
      PackageInfo pi=archiveInfo(copy);if(!pkg.equals(pi.packageName))throw new IOException("电视基础 APK 包名不符");
      if(!path.equals(AerialSafety.basePath(PackageProbe.path(c->client.shell(c),pkg))))throw new IOException("核对期间安装路径变化，请重新检查");
      InstalledProof proof=new InstalledProof();proof.pkg=pkg;proof.path=path;proof.name=pi.versionName;proof.code=packageVersion(pi);proof.sha=AerialDownload.sha256(copy);proof.signers=signerSet(pi);proof.homeCategory=ApkManifest.read(copy).homeCategory;return proof;
    }finally{copy.delete();}
  }
  private String installationIdentity(ApkTransfer.Device d,AerialProbe.Properties info,String endpoint)throws Exception{
    if(DesktopAssistant.activeUser(d.shell("dumpsys accessibility"))!=0)throw new IOException("当前 Android 用户未确认或非用户 0，未准备安装");
    return DesktopAssistant.identity(endpoint,info.serial,info.fingerprint,info.model);
  }
  private void historicalOptions(DesktopAssistant.Profile profile){
    if(profile==DesktopAssistant.AERIAL)closeAerialCenter();else closeHaloCenter();
    new DeckDialogBuilder(this).setTitle("官方历史版本与回退").setItems(new String[]{"浏览官方发布列表","输入官方发布标签（如 v1.73）"},(d,w)->{
      if(w==0)chooseHistoryVariant(null,profile);else{EditText tag=field("例如 v1.73","");new DeckDialogBuilder(this).setTitle("指定官方发布版本").setView(tag).setNegativeButton("取消",null).setPositiveButton("下一步",(a,b)->{try{chooseHistoryVariant(profile==DesktopAssistant.AERIAL?AerialDownload.releaseTag(tag.getText().toString()):HaloDownload.releaseTag(tag.getText().toString()),profile);}catch(Exception e){dialog("发布标签未识别",message(e));}}).show();}
    }).setNegativeButton("关闭",null).show();
  }
  private void chooseHistoryVariant(String tag,DesktopAssistant.Profile profile){new DeckDialogBuilder(this).setTitle("选择安装类型").setItems(new String[]{"标准版 · Android 5.0+","守护版 · 无 HOME 声明","Lite · Android 4.0–4.4"},(d,i)->{String type=new String[]{"standard","guard","lite"}[i];if(tag==null)browseReleases(type,1,profile);else if(profile==DesktopAssistant.AERIAL)checkAerial(type,tag);else checkHalo(type,tag);}).setNegativeButton("取消",null).show();}
  private void browseReleases(String type,int page,DesktopAssistant.Profile profile){
    final List<String>[] tags=new List[]{null};run("读取 "+profile.displayName+" 官方版本列表",()->{
      List<String> found=new ArrayList<>();
      if(profile==DesktopAssistant.AERIAL)for(AerialDownload.Asset a:AerialDownload.releases(type,page,stage->taskProgress(stage)))found.add(a.tag);
      else for(HaloDownload.Asset a:HaloDownload.releases(type,page,stage->taskProgress(stage)))found.add(a.tag);
      tags[0]=found;return "";},()->{
      LinearLayout list=column();list.setPadding(dp(20),dp(4),dp(20),dp(4));list.addView(text("官方稳定发布 · 第 "+page+" 页\n旧版本仅表示可选，不代表已在本机验证。",14,INK));
      AlertDialog[] modal={null};for(String tag:tags[0])addButton(list,tag+" · "+(profile==DesktopAssistant.AERIAL?AerialSupport.title(type):HaloSupport.title(type)),false,()->{if(modal[0]!=null)modal[0].dismiss();if(profile==DesktopAssistant.AERIAL)checkAerial(type,tag);else checkHalo(type,tag);});
      if(tags[0].isEmpty())list.addView(text("本页未找到该类型的稳定发布，可输入官方标签查询。",14,MUTED));
      if(page<10&&!tags[0].isEmpty())addButton(list,"下一页",false,()->{modal[0].dismiss();browseReleases(type,page+1,profile);});
      ScrollView scroll=new ScrollView(this);scroll.addView(list);modal[0]=new DeckDialogBuilder(this).setTitle("官方历史版本").setView(scroll).setNegativeButton("关闭",null).create();modal[0].show();
    });
  }
  private File faultReportFile(boolean pending,DesktopAssistant.Profile profile){return new File(getFilesDir(),pending?(profile==DesktopAssistant.HALO?"fault_pending_export_halo.txt":"fault_pending_export.txt"):(profile==DesktopAssistant.HALO?"fault_last_report_halo.txt":"fault_last_report.txt"));}
  private DesktopDiagnostic.Result lastFault;
  private DesktopAssistant.Profile lastFaultExportProfile, lastDesktopExportProfile;
  private void captureDesktopFault(DesktopAssistant.Profile profile,DesktopState state){
    if(profile==DesktopAssistant.AERIAL)closeAerialCenter();else closeHaloCenter();
    if(busy){toast("请等待当前任务完成");return;}if(!requestConnection())return;final AdbClient target=adb;final String endpoint=host+":"+prefs.getString("port","5555");final DesktopDiagnostic.Result[] result={null};
    if(state.dialog!=null)state.dialog.dismiss();
    run("一键故障采集",()->{
      result[0]=DesktopDiagnostic.capture(c->{if(adb!=target)throw new IOException("连接已变化");taskProgress(c.startsWith("dumpsys dropbox")?"读取 "+profile.displayName+" 崩溃证据…":"只读采集电视状态，请保持电视画面…");return target.shell(c,8000).require();},endpoint,profile);
      writeDesktopFile(faultReportFile(false,profile),DesktopDiagnostic.report(result[0]));return "";
    },()->{lastFault=result[0];if(result[0].last()!=null){state.snapshot=result[0].last();state.client=target;state.endpoint=endpoint;state.manual=null;state.outcome=null;}showFaultResult(result[0],profile,state);});
  }
  private void showFaultResult(DesktopDiagnostic.Result r,DesktopAssistant.Profile profile,DesktopState state){
    DesktopDiagnostic.Verdict v=r.verdict();LinearLayout list=column();list.setPadding(dp(22),dp(8),dp(22),dp(10));int color=v.state.equals(DesktopDiagnostic.CRASH)?UiPalette.color(0xffff9292):v.state.equals(DesktopDiagnostic.NORMAL)?TEAL:UiPalette.color(0xffffcf80);
    TextView badge=text(v.state,22,color);badge.setTypeface(null,Typeface.BOLD);list.addView(badge);gap(list,8);list.addView(text(v.reason,14,INK));gap(list,10);
    if(r.last()!=null)list.addView(text("电视："+r.last().model+"\n"+profile.displayName+"："+r.last().version+"\n已完成 "+r.samples.size()+" / 3 次只读观察",14,MUTED));
    if(v.state.equals(DesktopDiagnostic.CRASH))addButton(list,"选择官方版本或回退",true,()->historicalOptions(profile));
    else if(v.state.equals(DesktopDiagnostic.MISSING))addButton(list,"查看具体配置与修复项",true,()->showDesktopPanel(profile,state));
    else if(r.last()!=null)addButton(list,"查看已读到的详细状态",true,()->showDesktopPanel(profile,state));
    else addButton(list,"重新采集",true,()->captureDesktopFault(profile,state));
    addButton(list,"导出本次故障报告",false,()->exportFaultReport(DesktopDiagnostic.report(r),profile));addButton(list,"查看完整脱敏报告",false,()->dialog("故障报告",DesktopDiagnostic.report(r)));
    ScrollView scroll=new ScrollView(this);scroll.addView(list);new DeckDialogBuilder(this).setTitle("检查结果").setView(scroll).setNegativeButton("关闭",null).show();
  }
  private void exportFaultReport(String report,DesktopAssistant.Profile profile){lastFaultExportProfile=profile;run("准备故障报告导出",()->{writeDesktopFile(faultReportFile(true,profile),report);return "";},()->createDocument(107,"text/plain","LinkDeck_Fault_"+System.currentTimeMillis()+".txt"));}
  private void faultReport(DesktopAssistant.Profile profile,DesktopState state){
    if(profile==DesktopAssistant.AERIAL)closeAerialCenter();else closeHaloCenter();if(state.dialog!=null)state.dialog.dismiss();
    if(!faultReportFile(false,profile).exists()){
      new DeckDialogBuilder(this).setTitle("故障报告").setMessage("暂无故障报告。").setNegativeButton("关闭",null).setPositiveButton("立即采集",(d,w)->captureDesktopFault(profile,state)).show();return;
    }
    final String[] report={null};run("读取上次故障报告",()->{report[0]=readDesktopFile(faultReportFile(false,profile));return "";},()->new DeckDialogBuilder(this).setTitle("故障报告").setMessage(report[0]).setNegativeButton("关闭",null).setNeutralButton("导出 TXT",(d,w)->exportFaultReport(report[0],profile)).setPositiveButton("重新采集",(d,w)->captureDesktopFault(profile,state)).show());
  }
  private void haloFaultReport(){faultReport(DesktopAssistant.HALO,haloDesktop);}

  private static final class AerialPlan {
    File file;AdbClient client;AerialDownload.Asset asset;AerialInstaller.Installed installed;
    String variant,pkg,version,host,port,brand,abis,model,id,sha,signers,installedSigners;
    String selectedTag;boolean transition,downgradeConfirmed,signatureVerified;
    int sdk,minimum,decision;long code;boolean consumed,explicit;
  }
  private volatile AerialPlan aerialPlan;
  private AlertDialog aerialDialog;

  private void aerialCard(LinearLayout parent) {
    LinearLayout c=card(parent),r=row();r.addView(new TvScene(this,true),new LinearLayout.LayoutParams(dp(57),dp(57)));
    LinearLayout copy=column();copy.setPadding(dp(12),0,0,0);TextView title=text("Aerial TV",18,INK);title.setTypeface(null,Typeface.BOLD);copy.addView(title);gap(copy,5);String state="官方安装 · 桌面与版本管理";if(connected()&&aerialDesktop.client==adb&&aerialDesktop.snapshot!=null&&aerialDesktop.snapshot.installed)state="上次读取："+aerialDesktop.snapshot.version+" · 状态需复查";copy.addView(text(state,11,MUTED));r.addView(copy,new LinearLayout.LayoutParams(0,-2,1));c.addView(r);
    pair(c,"检查更新",()->checkAerial("auto"),"桌面助手",()->checkDesktop());
    pair(c,"版本管理",()->versionManager(),"故障报告",()->faultReport(DesktopAssistant.AERIAL,aerialDesktop));
  }

  private void versionManager() {
    closeAerialCenter();
    new DeckDialogBuilder(this).setTitle("版本管理")
      .setMessage("Android 5.0+：默认使用标准版。固件明确禁止安装桌面类应用时，可手动尝试守护版。\nAndroid 4.0–4.4：自动选择 Lite。\n\n只负责官方 APK 安装、更新和版本核验。不会停用原桌面，不会自动修改无障碍、屏保或默认桌面设置；已安装 Aerial 的既有设置由它自身保留。旧酷开固件的主页键和开机接管需另行验证。")
      .setNegativeButton("关闭",null)
      .setPositiveButton("历史版本与回退",(d,w)->historicalOptions(DesktopAssistant.AERIAL)).show();
  }

  private ApkTransfer.Device aerialDevice(AdbClient client) {
    return new ApkTransfer.Device() {
      public String shell(String command)throws Exception{
        if(command.equals("pm path "+AerialSupport.FULL)||command.equals("pm path "+AerialSupport.LITE))return AerialProbe.packagePath(c->client.shell(c),command.substring(8));
        return AerialProbe.query(c->client.shell(c),command);
      }
      public void push(File file,String remote)throws Exception {
        taskProgress("正在传输 Aerial 到电视…");
        try(InputStream in=new FileInputStream(file)){client.push(in,remote,n->transferProgress(n));}
      }
    };
  }

  private static long packageVersion(PackageInfo pi) {return Build.VERSION.SDK_INT>=28?pi.getLongVersionCode():pi.versionCode&0xffffffffL;}

  private void checkAerial(String requested) {checkAerial(requested,null);}
  private void checkAerial(String requested,String selectedTag) {
    closeAerialCenter();
    if(busy){toast("请等待当前任务完成");return;}
    if(!requestConnection())return;
    if(aerialDialog!=null)aerialDialog.dismiss();
    if(aerialPlan!=null&&!aerialPlan.consumed)aerialPlan.file.delete();
    final AerialPlan plan=new AerialPlan();plan.client=adb;plan.host=host;plan.port=prefs.getString("port","5555");plan.explicit=!requested.equals("auto")||selectedTag!=null;plan.selectedTag=selectedTag;
    plan.file=new File(getCacheDir(),"aerial_"+UUID.randomUUID()+".apk");
    run("检查 Aerial 官方安装",()->{
      try {
        taskProgress("正在读取电视系统与 Aerial 已装版本…");
        ApkTransfer.Device target=aerialDevice(plan.client);
        AerialProbe.Properties info=AerialProbe.properties(c->plan.client.shell(c));plan.sdk=info.sdk;
        plan.variant=requested.equals("auto")?AerialSupport.variant(plan.sdk):requested;
        AerialSupport.validateVariant(plan.variant,plan.sdk);plan.pkg=AerialSupport.packageName(plan.variant);
        plan.installed=AerialInstaller.read(target,plan.pkg);
        // Preserve guard installations when the existing package explicitly has no HOME category.

        plan.abis=info.abis;plan.brand=info.brand;plan.model=info.model;
        if(plan.installed.present&&plan.installed.code<0)throw new IOException("检测到已装 Aerial，但无法读取当前版本，已停止覆盖安装。\n"+plan.installed.dump);
        plan.id=installationIdentity(target,info,plan.host+":"+plan.port);
        if(plan.installed.present){InstalledProof existing=installedProof(plan.client,plan.pkg);if(existing.code!=plan.installed.code)throw new IOException("电视已装版本变化");plan.installedSigners=existing.signers;if(requested.equals("auto")&&plan.sdk>=21)plan.variant=existing.homeCategory?"standard":"guard";}
        plan.asset=selectedTag==null?AerialDownload.download(plan.variant,plan.file,stage->taskProgress(stage)):AerialDownload.downloadPinned(selectedTag,plan.variant,plan.file,stage->taskProgress(stage));
        validateAerialArchive(plan);
        if(plan.installed.present){AerialSafety.sameSigners(plan.installedSigners,plan.signers);plan.signatureVerified=true;}
        if(selectedTag!=null&&plan.pkg.equals(AerialSupport.FULL)&&!AerialSafety.fullTagMatches(selectedTag,plan.version))throw new IOException("包内版本与所选官方标签不一致，未改装其他版本");
        plan.decision=AerialSupport.decision(plan.installed.present,plan.installed.code,plan.code);
        if(isFinishing()||isDestroyed())throw new IOException("页面已关闭，已清理本次下载");
        aerialPlan=plan;
        return aerialReport(plan);
      }catch(Exception e){plan.file.delete();throw e;}
    },()->showAerialPlan(plan));
  }

  private void validateAerialArchive(AerialPlan plan)throws Exception {
    PackageInfo pi=archiveInfo(plan.file);
    if(pi==null||pi.applicationInfo==null||!plan.pkg.equals(pi.packageName))throw new IOException("下载包未通过 Aerial 包名或签名信息检查，已停止安装。");
    String splits=ApkManifest.splits(Collections.singletonList(plan.file));if(!splits.isEmpty())throw new IOException(splits);
    plan.minimum=pi.applicationInfo.minSdkVersion;plan.version=pi.versionName;plan.code=packageVersion(pi);plan.signers=signerSet(pi);plan.sha=AerialDownload.sha256(plan.file);
    Set<String> abis=ApkTransfer.abis(Collections.singletonList(plan.file));
    if(!ApkTransfer.compatible(plan.minimum,plan.sdk,abis,plan.abis))throw new IOException(ApkManifest.compatibility(plan.minimum,plan.sdk,abis,plan.abis));
  }

  private String aerialReport(AerialPlan p) {
    String state=p.decision==-1?"可以安装":p.decision==1?"发现可用更新":p.decision==0?"已是同一版本":p.asset.pinned?"所选版本较旧：仅可手动尝试保留数据回退":"电视上版本更高，本次不会降级";
    return "电视："+p.model+" · "+ApkManifest.android(p.sdk)+"\n架构："+p.abis+"\n选择："+AerialSupport.title(p.variant)+"\n已安装："+(p.installed.present?p.installed.name+"（"+p.installed.code+"）":"未安装")+"\n官方安装包："+p.version+"（"+p.code+"） · "+size(p.file.length())+"\n发布："+p.asset.tag+(p.asset.pinned?"（固定发布，不回退到最新版）":"")+"\n签名："+(p.installed.present?(p.signatureVerified?"与电视已安装证书一致":"尚未确认"):"首次安装，以官方来源和系统校验为准")+"\n\n"+state+"\n最低要求："+ApkManifest.android(p.minimum)+"\n"+(p.asset.digest.isEmpty()?"来自官方 HTTPS 地址；本次未取得官方文件摘要。":"已匹配官方发布的 SHA-256。")+"\n\n"+AerialSupport.limitations(p.sdk,p.brand)+"\n\n更新使用覆盖安装，保留应用数据；签名不一致时会停止，不会自动卸载旧版。";
  }

  private void showAerialPlan(AerialPlan plan) {
    if(aerialDialog!=null)aerialDialog.dismiss();
    LinearLayout panel=column();panel.setPadding(dp(18),0,dp(18),dp(4));
    LinearLayout content=column(),brand=row();brand.addView(new TvScene(this,true),new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout copy=column();copy.setPadding(dp(12),0,0,0);TextView title=text("Aerial TV",18,INK);title.setTypeface(null,Typeface.BOLD);copy.addView(title);copy.addView(text("电视："+plan.model,11,MUTED));brand.addView(copy,new LinearLayout.LayoutParams(0,-2,1));content.addView(brand);gap(content,12);
    sheetRow(content,"当前版本",plan.installed.present?plan.installed.name:"未安装",INK);
    sheetRow(content,"目标版本",plan.version,INK);
    sheetRow(content,"电视系统",ApkManifest.android(plan.sdk),INK);
    sheetRow(content,"签名校验",plan.installed.present?(plan.signatureVerified?"与电视一致":"尚未确认"):"首次安装，由系统核验",plan.installed.present&&plan.signatureVerified?TEAL:UiPalette.color(0xffffcf80));
    gap(content,10);TextView hint=text("安装要求符合不等于实际稳定。主页键、开机与持续运行仍需实测。",12,UiPalette.color(0xffffcf80));hint.setPadding(dp(12),dp(10),dp(12),dp(10));hint.setBackground(shape(UiPalette.color(0xff3d3326),12));gap(content,8);
    TextView details=button("查看检查详情  ﹀",false,()->{});details.setTextSize(12);TextView report=text(aerialReport(plan),13,MUTED);report.setTextIsSelectable(true);report.setPadding(0,dp(12),0,dp(8));report.setVisibility(View.GONE);details.setOnClickListener(v->{boolean expand=report.getVisibility()!=View.VISIBLE;report.setVisibility(expand?View.VISIBLE:View.GONE);details.setText(expand?"收起检查详情  ﹀":"查看检查详情  ›");});content.addView(details);content.addView(report);
    ScrollView scroll=new ScrollView(this);scroll.setPadding(0,0,dp(7),0);scroll.addView(content);int reserved=dp(330+60*Math.max(0,getResources().getConfiguration().fontScale-1));panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(330),Math.max(dp(110),getResources().getDisplayMetrics().heightPixels-reserved))));gap(panel,8);panel.addView(hint);
    AlertDialog modal=new DeckDialogBuilder(this).setTitle("安装前确认").setView(panel).create();aerialDialog=modal;
    boolean install=plan.decision==-1||plan.decision==1||plan.decision==0&&plan.explicit||plan.decision==2&&plan.asset.pinned;
    String action=plan.decision==-1?"安装到电视":plan.decision==1?"更新到电视":plan.decision==2?"评估保留数据回退":"覆盖为所选版本";
    addButton(panel,install?action:"打开现有 Aerial",true,()->{if(install)reviewAerialInstall(plan);else openAerial(plan.pkg);modal.dismiss();});
    TextView cancel=button("取消",false,()->modal.dismiss());cancel.setBackgroundColor(Color.TRANSPARENT);panel.addView(cancel);
    modal.setOnDismissListener(d->{if(!plan.consumed&&!plan.transition)plan.file.delete();if(aerialDialog==modal)aerialDialog=null;});modal.show();
  }
  private void sheetRow(LinearLayout parent,String label,String value,int color){LinearLayout row=row();row.setPadding(dp(2),dp(7),dp(2),dp(7));TextView left=text(label,13,MUTED);row.addView(left,new LinearLayout.LayoutParams(0,-2,1));TextView right=text(value,13,color);right.setGravity(Gravity.RIGHT);right.setMaxWidth(dp(190));row.addView(right);parent.addView(row);View divider=new View(this);divider.setBackgroundColor(LINE);parent.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));}

  private void reviewAerialInstall(AerialPlan plan){
    boolean rollback=plan.decision==2;
    if(!rollback){installAerial(plan);return;}plan.transition=true;
    String text="将尝试 pm install -r -d，保留应用数据降级。签名必须一致，但电视可能拒绝；拒绝后停止，不会转为卸载或清除数据。旧版本能否读取新版数据仍未知，轻连无法完整备份 Aerial 私有数据。\n\n确认后才会传输并请求安装；取消则清理本次下载。";
    AlertDialog confirm=new DeckDialogBuilder(this).setTitle("确认本次保留数据回退").setMessage(text).setNegativeButton("取消",null)
      .setPositiveButton("理解限制，尝试回退",(d,w)->{plan.downgradeConfirmed=rollback;installAerial(plan);}).create();aerialDialog=confirm;
    confirm.setOnDismissListener(d->{if(!plan.consumed)plan.file.delete();if(aerialDialog==confirm)aerialDialog=null;});confirm.show();
  }
  private void installAerial(AerialPlan plan) {
    if(busy||!connected()||adb!=plan.client||!host.equals(plan.host)||!prefs.getString("port","5555").equals(plan.port)) {
      dialog("请重新检查","设备连接或任务状态已变化，本次没有安装。请重新点击 Aerial 检查按钮。");return;
    }
    plan.consumed=true;final String[] report={""};
    run("安装或更新 Aerial",()->{
      try {
        taskProgress("正在复查 Aerial 安装包与电视版本…");
        AerialDownload.verify(plan.file,plan.asset);validateAerialArchive(plan);
        ApkTransfer.Device target=aerialDevice(plan.client);
        AerialProbe.Properties info=AerialProbe.properties(c->plan.client.shell(c));int sdk=info.sdk;String abis=info.abis;
        AerialSupport.validateVariant(plan.variant,sdk);
        String id=installationIdentity(target,info,plan.host+":"+plan.port);if(!id.equals(plan.id))throw new IOException("电视身份已变化，未安装");
        if(plan.installed.present){InstalledProof current=installedProof(plan.client,plan.pkg);if(current.code!=plan.installed.code)throw new IOException("电视现有版本变化");AerialSafety.sameSigners(current.signers,plan.signers);}
        if(plan.downgradeConfirmed&&(!plan.asset.pinned||!plan.signatureVerified))throw new IOException("回退来源或签名尚未核实");
        if(abis.isEmpty()||!ApkTransfer.compatible(plan.minimum,sdk,ApkTransfer.abis(Collections.singletonList(plan.file)),abis))throw new IOException("电视系统或 CPU 信息已变化，请重新检查。");
        report[0]=AerialInstaller.install(plan.file,plan.pkg,plan.code,plan.installed,new ApkTransfer.Device(){
          public String shell(String command)throws Exception{if(command.startsWith("pm install"))taskProgress("正在安装 Aerial，等待电视返回…");return target.shell(command);}
          public void push(File file,String remote)throws Exception{target.push(file,remote);}
        },plan.downgradeConfirmed);
        appLoaded=false;return report[0];
      }finally{plan.file.delete();}
    },()->new DeckDialogBuilder(this).setTitle("Aerial 安装包已确认").setMessage(report[0]+"\n\n可先打开 Aerial；默认桌面和守护授权请按电视内的引导设置。旧酷开主页键限制仍需实际验证。")
      .setNegativeButton("知道了",null).setPositiveButton("打开 Aerial",(d,w)->openAerial(plan.pkg)).show());
  }

  private void openAerial(String pkg) {
    if(!pkg.equals(AerialSupport.FULL)&&!pkg.equals(AerialSupport.LITE))return;
    command("打开 Aerial","am start -n "+pkg+(pkg.equals(AerialSupport.LITE)?"/.HomeActivity":"/.MainActivity"),null);
  }

  private void haloEntry(LinearLayout parent){LinearLayout c=card(parent);LinearLayout r=row();r.addView(text("Halo TV",15,INK),new LinearLayout.LayoutParams(0,-2,1));TextView open=button("桌面管理  ›",false,()->haloCenter());open.setTextSize(12);r.addView(open);c.addView(r);}
  private void haloCenter(){LinearLayout panel=column();haloCard(panel);ScrollView scroll=new ScrollView(this);scroll.addView(panel);haloCenterDialog=new DeckDialogBuilder(this).setTitle("Halo TV 桌面管理").setView(scroll).setNegativeButton("关闭",null).create();haloCenterDialog.show();haloCenterDialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(20),dp(570)),(int)(getResources().getDisplayMetrics().heightPixels*.82));}
  private void closeHaloCenter(){if(haloCenterDialog!=null){haloCenterDialog.dismiss();haloCenterDialog=null;}}

  private static final class HaloPlan {
    File file;AdbClient client;HaloDownload.Asset asset;HaloInstaller.Installed installed;
    String variant,pkg,version,host,port,brand,abis,model,id,sha,signers,installedSigners;
    String selectedTag;boolean transition,downgradeConfirmed,signatureVerified;
    int sdk,minimum,decision;long code;boolean consumed,explicit;
  }
  private volatile HaloPlan haloPlan;
  private AlertDialog haloDialog;

  private void haloCard(LinearLayout parent) {
    LinearLayout c=card(parent),r=row();r.addView(new TvScene(this,true),new LinearLayout.LayoutParams(dp(57),dp(57)));
    LinearLayout copy=column();copy.setPadding(dp(12),0,0,0);TextView title=text("Halo TV",18,INK);title.setTypeface(null,Typeface.BOLD);copy.addView(title);gap(copy,5);String state="官方安装 · 桌面与版本管理";if(connected()&&haloDesktop.client==adb&&haloDesktop.snapshot!=null&&haloDesktop.snapshot.installed)state="上次读取："+haloDesktop.snapshot.version+" · 状态需复查";copy.addView(text(state,11,MUTED));r.addView(copy,new LinearLayout.LayoutParams(0,-2,1));c.addView(r);
    pair(c,"检查更新",()->checkHalo("auto"),"桌面助手",()->checkHaloDesktop());
    pair(c,"版本管理",()->haloVersionManager(),"故障报告",()->haloFaultReport());
  }

  private void haloVersionManager() {
    closeHaloCenter();
    new DeckDialogBuilder(this).setTitle("版本管理")
      .setMessage("Android 5.0+：默认使用标准版。固件明确禁止安装桌面类应用时，可手动尝试守护版。\nAndroid 4.0–4.4：自动选择 Lite。\n\n只负责官方 APK 安装、更新和版本核验。不会停用原桌面，不会自动修改无障碍、屏保或默认桌面设置；已安装 Halo TV 的既有设置由它自身保留。守护版不声明 HOME，完全依赖桌面守护接管主页键；Lite 没有桌面守护。Halo TV 与 Aerial 可同时安装，但请勿同时启用两个桌面守护，可在桌面助手里随时切换。")
      .setNegativeButton("关闭",null)
      .setPositiveButton("历史版本与回退",(d,w)->historicalOptions(DesktopAssistant.HALO)).show();
  }

  private ApkTransfer.Device haloDevice(AdbClient client) {
    return new ApkTransfer.Device() {
      public String shell(String command)throws Exception{
        if(command.equals("pm path "+HaloSupport.FULL)||command.equals("pm path "+HaloSupport.LITE))return HaloProbe.packagePath(c->client.shell(c),command.substring(8));
        return HaloProbe.query(c->client.shell(c),command);
      }
      public void push(File file,String remote)throws Exception {
        taskProgress("正在传输 Halo TV 到电视…");
        try(InputStream in=new FileInputStream(file)){client.push(in,remote,n->transferProgress(n));}
      }
    };
  }

  private void checkHalo(String requested) {checkHalo(requested,null);}
  private void checkHalo(String requested,String selectedTag) {
    closeHaloCenter();
    if(busy){toast("请等待当前任务完成");return;}
    if(!requestConnection())return;
    if(haloDialog!=null)haloDialog.dismiss();
    if(haloPlan!=null&&!haloPlan.consumed)haloPlan.file.delete();
    final HaloPlan plan=new HaloPlan();plan.client=adb;plan.host=host;plan.port=prefs.getString("port","5555");plan.explicit=!requested.equals("auto")||selectedTag!=null;plan.selectedTag=selectedTag;
    plan.file=new File(getCacheDir(),"halo_"+UUID.randomUUID()+".apk");
    run("检查 Halo TV 官方安装",()->{
      try {
        taskProgress("正在读取电视系统与 Halo TV 已装版本…");
        ApkTransfer.Device target=haloDevice(plan.client);
        AerialProbe.Properties info=AerialProbe.properties(c->plan.client.shell(c));plan.sdk=info.sdk;
        plan.variant=requested.equals("auto")?HaloSupport.variant(plan.sdk):requested;
        HaloSupport.validateVariant(plan.variant,plan.sdk);plan.pkg=HaloSupport.packageName(plan.variant);
        plan.installed=HaloInstaller.read(target,plan.pkg);
        // Preserve guard installations when the existing package explicitly has no HOME category.
        plan.abis=info.abis;plan.brand=info.brand;plan.model=info.model;
        if(plan.installed.present&&plan.installed.code<0)throw new IOException("检测到已装 Halo TV，但无法读取当前版本，已停止覆盖安装。\n"+plan.installed.dump);
        plan.id=installationIdentity(target,info,plan.host+":"+plan.port);
        if(plan.installed.present){InstalledProof existing=installedProof(plan.client,plan.pkg);if(existing.code!=plan.installed.code)throw new IOException("电视已装版本变化");plan.installedSigners=existing.signers;if(requested.equals("auto")&&plan.sdk>=21)plan.variant=existing.homeCategory?"standard":"guard";}
        plan.asset=selectedTag==null?HaloDownload.download(plan.variant,plan.file,stage->taskProgress(stage)):HaloDownload.downloadPinned(selectedTag,plan.variant,plan.file,stage->taskProgress(stage));
        validateHaloArchive(plan);
        if(plan.installed.present){AerialSafety.sameSigners(plan.installedSigners,plan.signers);plan.signatureVerified=true;}
        if(selectedTag!=null&&plan.pkg.equals(HaloSupport.FULL)&&!HaloDownload.versionFromTag(selectedTag).equals(plan.version))throw new IOException("包内版本与所选官方标签不一致，未改装其他版本");
        plan.decision=HaloSupport.decision(plan.installed.present,plan.installed.code,plan.code);
        if(isFinishing()||isDestroyed())throw new IOException("页面已关闭，已清理本次下载");
        haloPlan=plan;
        return haloReport(plan);
      }catch(Exception e){plan.file.delete();throw e;}
    },()->showHaloPlan(plan));
  }

  private void validateHaloArchive(HaloPlan plan)throws Exception {
    PackageInfo pi=archiveInfo(plan.file);
    if(pi==null||pi.applicationInfo==null||!plan.pkg.equals(pi.packageName))throw new IOException("下载包未通过 Halo TV 包名或签名信息检查，已停止安装。");
    String splits=ApkManifest.splits(Collections.singletonList(plan.file));if(!splits.isEmpty())throw new IOException(splits);
    plan.minimum=pi.applicationInfo.minSdkVersion;plan.version=pi.versionName;plan.code=packageVersion(pi);plan.signers=signerSet(pi);plan.sha=HaloDownload.sha256(plan.file);
    Set<String> abis=ApkTransfer.abis(Collections.singletonList(plan.file));
    if(!ApkTransfer.compatible(plan.minimum,plan.sdk,abis,plan.abis))throw new IOException(ApkManifest.compatibility(plan.minimum,plan.sdk,abis,plan.abis));
  }

  private String haloReport(HaloPlan p) {
    String state=p.decision==-1?"可以安装":p.decision==1?"发现可用更新":p.decision==0?"已是同一版本":p.asset.pinned?"所选版本较旧：仅可手动尝试保留数据回退":"电视上版本更高，本次不会降级";
    return "电视："+p.model+" · "+ApkManifest.android(p.sdk)+"\n架构："+p.abis+"\n选择："+HaloSupport.title(p.variant)+"\n已安装："+(p.installed.present?p.installed.name+"（"+p.installed.code+"）":"未安装")+"\n官方安装包："+p.version+"（"+p.code+"） · "+size(p.file.length())+"\n发布："+p.asset.tag+(p.asset.pinned?"（固定发布，不回退到最新版）":"")+"\n签名："+(p.installed.present?(p.signatureVerified?"与电视已安装证书一致":"尚未确认"):"首次安装，以官方来源和系统校验为准")+"\n\n"+state+"\n最低要求："+ApkManifest.android(p.minimum)+"\n"+(p.asset.digest.isEmpty()?"来自官方 HTTPS 地址；本次未取得官方文件摘要。":"已匹配官方发布的 SHA-256。")+"\n\n"+HaloSupport.limitations(p.sdk,p.brand)+"\n\n更新使用覆盖安装，保留应用数据；签名不一致时会停止，不会自动卸载旧版。";
  }

  private void showHaloPlan(HaloPlan plan) {
    if(haloDialog!=null)haloDialog.dismiss();
    LinearLayout panel=column();panel.setPadding(dp(18),0,dp(18),dp(4));
    LinearLayout content=column(),brand=row();brand.addView(new TvScene(this,true),new LinearLayout.LayoutParams(dp(52),dp(52)));LinearLayout copy=column();copy.setPadding(dp(12),0,0,0);TextView title=text("Halo TV",18,INK);title.setTypeface(null,Typeface.BOLD);copy.addView(title);copy.addView(text("电视："+plan.model,11,MUTED));brand.addView(copy,new LinearLayout.LayoutParams(0,-2,1));content.addView(brand);gap(content,12);
    sheetRow(content,"当前版本",plan.installed.present?plan.installed.name:"未安装",INK);
    sheetRow(content,"目标版本",plan.version,INK);
    sheetRow(content,"电视系统",ApkManifest.android(plan.sdk),INK);
    sheetRow(content,"签名校验",plan.installed.present?(plan.signatureVerified?"与电视一致":"尚未确认"):"首次安装，由系统核验",plan.installed.present&&plan.signatureVerified?TEAL:UiPalette.color(0xffffcf80));
    gap(content,10);TextView hint=text("安装要求符合不等于实际稳定。主页键、开机与持续运行仍需实测。",12,UiPalette.color(0xffffcf80));hint.setPadding(dp(12),dp(10),dp(12),dp(10));hint.setBackground(shape(UiPalette.color(0xff3d3326),12));gap(content,8);
    TextView details=button("查看检查详情  ﹀",false,()->{});details.setTextSize(12);TextView report=text(haloReport(plan),13,MUTED);report.setTextIsSelectable(true);report.setPadding(0,dp(12),0,dp(8));report.setVisibility(View.GONE);details.setOnClickListener(v->{boolean expand=report.getVisibility()!=View.VISIBLE;report.setVisibility(expand?View.VISIBLE:View.GONE);details.setText(expand?"收起检查详情  ﹀":"查看检查详情  ›");});content.addView(details);content.addView(report);
    ScrollView scroll=new ScrollView(this);scroll.setPadding(0,0,dp(7),0);scroll.addView(content);int reserved=dp(330+60*Math.max(0,getResources().getConfiguration().fontScale-1));panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(330),Math.max(dp(110),getResources().getDisplayMetrics().heightPixels-reserved))));gap(panel,8);panel.addView(hint);
    AlertDialog modal=new DeckDialogBuilder(this).setTitle("安装前确认").setView(panel).create();haloDialog=modal;
    boolean install=plan.decision==-1||plan.decision==1||plan.decision==0&&plan.explicit||plan.decision==2&&plan.asset.pinned;
    String action=plan.decision==-1?"安装到电视":plan.decision==1?"更新到电视":plan.decision==2?"评估保留数据回退":"覆盖为所选版本";
    addButton(panel,install?action:"打开现有 Halo TV",true,()->{if(install)reviewHaloInstall(plan);else openHalo(plan.pkg);modal.dismiss();});
    TextView cancel=button("取消",false,()->modal.dismiss());cancel.setBackgroundColor(Color.TRANSPARENT);panel.addView(cancel);
    modal.setOnDismissListener(d->{if(!plan.consumed&&!plan.transition)plan.file.delete();if(haloDialog==modal)haloDialog=null;});modal.show();
  }

  private void reviewHaloInstall(HaloPlan plan){
    boolean rollback=plan.decision==2;
    if(!rollback){installHalo(plan);return;}plan.transition=true;
    String text="将尝试 pm install -r -d，保留应用数据降级。签名必须一致，但电视可能拒绝；拒绝后停止，不会转为卸载或清除数据。旧版本能否读取新版数据仍未知，轻连无法完整备份 Halo TV 私有数据。\n\n确认后才会传输并请求安装；取消则清理本次下载。";
    AlertDialog confirm=new DeckDialogBuilder(this).setTitle("确认本次保留数据回退").setMessage(text).setNegativeButton("取消",null)
      .setPositiveButton("理解限制，尝试回退",(d,w)->{plan.downgradeConfirmed=rollback;installHalo(plan);}).create();haloDialog=confirm;
    confirm.setOnDismissListener(d->{if(!plan.consumed)plan.file.delete();if(haloDialog==confirm)haloDialog=null;});confirm.show();
  }
  private void installHalo(HaloPlan plan) {
    if(busy||!connected()||adb!=plan.client||!host.equals(plan.host)||!prefs.getString("port","5555").equals(plan.port)) {
      dialog("请重新检查","设备连接或任务状态已变化，本次没有安装。请重新点击 Halo TV 检查按钮。");return;
    }
    plan.consumed=true;final String[] report={""};
    run("安装或更新 Halo TV",()->{
      try {
        taskProgress("正在复查 Halo TV 安装包与电视版本…");
        HaloDownload.verify(plan.file,plan.asset);validateHaloArchive(plan);
        ApkTransfer.Device target=haloDevice(plan.client);
        AerialProbe.Properties info=AerialProbe.properties(c->plan.client.shell(c));int sdk=info.sdk;String abis=info.abis;
        HaloSupport.validateVariant(plan.variant,sdk);
        String id=installationIdentity(target,info,plan.host+":"+plan.port);if(!id.equals(plan.id))throw new IOException("电视身份已变化，未安装");
        if(plan.installed.present){InstalledProof current=installedProof(plan.client,plan.pkg);if(current.code!=plan.installed.code)throw new IOException("电视现有版本变化");AerialSafety.sameSigners(current.signers,plan.signers);}
        if(plan.downgradeConfirmed&&(!plan.asset.pinned||!plan.signatureVerified))throw new IOException("回退来源或签名尚未核实");
        if(abis.isEmpty()||!ApkTransfer.compatible(plan.minimum,sdk,ApkTransfer.abis(Collections.singletonList(plan.file)),abis))throw new IOException("电视系统或 CPU 信息已变化，请重新检查。");
        report[0]=HaloInstaller.install(plan.file,plan.pkg,plan.code,plan.installed,new ApkTransfer.Device(){
          public String shell(String command)throws Exception{if(command.startsWith("pm install"))taskProgress("正在安装 Halo TV，等待电视返回…");return target.shell(command);}
          public void push(File file,String remote)throws Exception{target.push(file,remote);}
        },plan.downgradeConfirmed);
        appLoaded=false;return report[0];
      }finally{plan.file.delete();}
    },()->new DeckDialogBuilder(this).setTitle("Halo TV 安装包已确认").setMessage(report[0]+"\n\n可先打开 Halo TV；默认桌面和守护授权请按电视内的引导设置。旧酷开主页键限制仍需实际验证。")
      .setNegativeButton("知道了",null).setPositiveButton("打开 Halo TV",(d,w)->openHalo(plan.pkg)).show());
  }

  private void openHalo(String pkg) {
    if(!pkg.equals(HaloSupport.FULL)&&!pkg.equals(HaloSupport.LITE))return;
    // Halo keeps Aerial's class namespace, so the component must be fully qualified.
    command("打开 Halo TV","am start -n "+(pkg.equals(HaloSupport.LITE)?"app.halotv.launcher.lite/app.orchard.tv.lite.HomeActivity":"app.halotv.launcher/app.orchard.tv.MainActivity"),null);
  }

  private static final class DesktopState {
    DesktopAssistant.Snapshot snapshot;AdbClient client;String endpoint,manual,outcome;AlertDialog dialog;
  }
  private final DesktopState aerialDesktop=new DesktopState(), haloDesktop=new DesktopState();
  private DesktopBackupStore desktopStore;
  private interface DesktopWork {DesktopAssistant.Snapshot run(DesktopAssistant.Device device,String endpoint,DesktopAssistant.Snapshot reviewed)throws Exception;}
  private DesktopBackupStore desktopStore() {if(desktopStore==null)desktopStore=new DesktopBackupStore(new File(getFilesDir(),"desktop_backups"));return desktopStore;}
  private File desktopReportFile(boolean pending,DesktopAssistant.Profile profile) {return new File(getFilesDir(),pending?(profile==DesktopAssistant.HALO?"desktop_pending_export_halo.txt":"desktop_pending_export.txt"):(profile==DesktopAssistant.HALO?"desktop_last_report_halo.txt":"desktop_last_report.txt"));}
  private void writeDesktopFile(File file,String text)throws Exception {
    android.util.AtomicFile atomic=new android.util.AtomicFile(file);FileOutputStream out=atomic.startWrite();
    try{out.write(text.getBytes("UTF-8"));atomic.finishWrite(out);}catch(Exception e){atomic.failWrite(out);throw e;}
  }
  private String readDesktopFile(File file)throws Exception {
    if(!file.exists()&&!new File(file+".bak").exists())throw new IOException("还没有有效报告，请先执行对应检查或故障采集，再重新导出。");
    try(InputStream in=new android.util.AtomicFile(file).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>1024*1024)throw new IOException("报告异常过大");}return out.toString("UTF-8");}
  }
  private boolean desktopCurrent(DesktopAssistant.Profile profile,DesktopState state) {
    if(!connected()||state.snapshot==null||state.client!=adb||!Objects.equals(state.endpoint,host+":"+prefs.getString("port","5555"))){dialog("请先重新检查","设备连接已变化，请重新点击“检查 "+profile.displayName+" 桌面”。");return false;}return true;
  }
  private void desktopAction(String name,boolean reviewed,DesktopWork work,DesktopAssistant.Profile profile,DesktopState state) {
    if(busy){toast("请等待当前任务完成");return;}if(!requestConnection())return;
    if(reviewed&&!desktopCurrent(profile,state))return;
    final AdbClient target=adb;final String endpoint=host+":"+prefs.getString("port","5555");final DesktopAssistant.Snapshot before=state.snapshot;
    final DesktopAssistant.Snapshot[] result={null};
    if(state.dialog!=null)state.dialog.dismiss();
    run(name,()->{
      taskProgress(name+"，正在读取电视状态…");
      DesktopAssistant.Device device=command->{
        if(adb!=target)throw new IOException("连接设备已变化，已停止。");
        String stage=command.startsWith("getprop")?"识别电视系统与身份…":command.contains("resolve-activity")?"检查默认桌面…":command.startsWith("dumpsys dropbox")?"核对当前版本崩溃记录…":command.equals("dumpsys accessibility")?"读取守护运行状态与当前用户…":command.startsWith("dumpsys window")?"读取电视当前前台…":command.startsWith("settings --user 0 get")?"读取桌面守护配置…":command.startsWith("settings --user 0 put")||command.startsWith("settings --user 0 delete")||command.startsWith("pm grant")||command.startsWith("pm revoke")||command.contains("set-home-activity")?"执行已确认的设置改动…":command.startsWith("am force-stop")?"暂停 "+profile.displayName+"…":"检查 "+profile.displayName+" 安装与授权…";
        taskProgress(stage);return target.shell(command,8000).require();
      };
      try {result[0]=work.run(device,endpoint,before);}
      catch(DesktopAssistant.VerificationFailure failed){
        result[0]=failed.snapshot;
        try{writeDesktopFile(desktopReportFile(false,profile),DesktopAssistant.report(failed.snapshot,state.manual));}catch(Exception saving){failed.addSuppressed(saving);}
        ui.post(()->{if(isFinishing()||isDestroyed()||adb!=target)return;state.snapshot=failed.snapshot;state.client=target;state.endpoint=endpoint;state.outcome=failed.snapshot.outcome+"："+failed.getMessage();});
        throw failed;
      }
      writeDesktopFile(desktopReportFile(false,profile),DesktopAssistant.report(result[0],state.manual));
      return DesktopAssistant.summary(result[0]);
    },()->{state.snapshot=result[0];state.client=target;state.endpoint=endpoint;if(result[0].outcome!=null)state.outcome=result[0].outcomeDetail;showDesktopPanel(profile,state);});
  }
  private void checkDesktop() {
    closeAerialCenter();aerialDesktop.manual=null;aerialDesktop.outcome=null;desktopAction("检查 Aerial 桌面",false,(d,e,s)->DesktopAssistant.inspect(d,e,DesktopAssistant.AERIAL),DesktopAssistant.AERIAL,aerialDesktop);}
  private void checkHaloDesktop() {
    closeHaloCenter();haloDesktop.manual=null;haloDesktop.outcome=null;desktopAction("检查 Halo TV 桌面",false,(d,e,s)->DesktopAssistant.inspect(d,e,DesktopAssistant.HALO),DesktopAssistant.HALO,haloDesktop);}
  private void showDesktopPanel(DesktopAssistant.Profile profile,DesktopState state) {
    if(state.snapshot==null)return;
    if(state.dialog!=null)state.dialog.dismiss();
    LinearLayout content=column();content.setPadding(dp(20),dp(4),dp(20),dp(8));
    ScrollView scroll=new ScrollView(this);LinearLayout list=column();scroll.addView(list);content.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(480),(int)(getResources().getDisplayMetrics().heightPixels*.65f))));
    if(state.outcome!=null){list.addView(text(state.outcome,14,TEAL));gap(list,10);}
    DesktopDiagnostic.Verdict verdict=DesktopDiagnostic.classify(state.snapshot);TextView stateBadge=text(verdict.state,20,verdict.state.equals(DesktopDiagnostic.CRASH)?UiPalette.color(0xffff9292):TEAL);stateBadge.setTypeface(null,Typeface.BOLD);list.addView(stateBadge);list.addView(text(verdict.reason,13,MUTED));gap(list,10);
    TextView summary=text(DesktopAssistant.summary(state.snapshot),14,INK);summary.setTextIsSelectable(true);list.addView(summary);
    if(state.manual!=null){gap(list,10);list.addView(text(state.manual,13,TEAL));}
    DesktopAssistant.Profile other=profile==DesktopAssistant.AERIAL?DesktopAssistant.HALO:DesktopAssistant.AERIAL;
    DesktopState otherState=profile==DesktopAssistant.AERIAL?haloDesktop:aerialDesktop;
    addButton(list,"切换到 "+other.displayName,false,()->switchDesktopAction(profile,state,other,otherState));
    addButton(list,"故障报告",false,()->faultReport(profile,state));
    addButton(list,"修复缺失授权 / 守护",true,()->reviewDesktopRepair(profile,state));
    pair(list,"主页键人工验收",()->reviewHomeCheck(profile,state),"重新检查",()->{if(profile==DesktopAssistant.AERIAL)checkDesktop();else checkHaloDesktop();});
    pair(list,"恢复本 App 的修改",()->reviewDesktopRestore(profile,state),"导出诊断报告",()->exportDesktopReport(profile,state));
    pair(list,"重新启动 "+profile.displayName,()->restartDesktop(profile,state),"电视桌面设置",()->{if(desktopCurrent(profile,state))command("打开电视桌面设置","am start -a android.settings.HOME_SETTINGS",null);});
    addButton(list,"打开电视无障碍设置",false,()->{if(desktopCurrent(profile,state))command("打开电视无障碍设置","am start -a android.settings.ACCESSIBILITY_SETTINGS",null);});
    state.dialog=new DeckDialogBuilder(this).setTitle(profile.displayName+" 桌面助手").setView(content).setNegativeButton("关闭",null).create();state.dialog.show();
  }
  private void switchDesktopAction(DesktopAssistant.Profile from,DesktopState fromState,DesktopAssistant.Profile to,DesktopState toState) {
    if(!desktopCurrent(from,fromState))return;
    DesktopAssistant.Snapshot target=toState.snapshot;
    String targetNote=target==null?"尚未检查过 "+to.displayName+"，切换时会重新读取。":!target.installed&&!target.lite?"尚未安装 "+to.displayName+"，切换前请先安装。":target.installed?"已装 "+to.displayName+" "+target.version:"已装 "+to.displayName+" Lite";
    new DeckDialogBuilder(this).setTitle("切换桌面到 "+to.displayName+"？")
      .setMessage("切换会同时做两件事：停用 "+from.displayName+" 的桌面守护，启用 "+to.displayName+" 的守护并设为默认桌面。两个守护绝不会同时启用。\n\n"+targetNote+"\n\n每项改动都会先备份，可恢复。切换后仍需按实体主页键验收，并重启电视确认开机接管。")
      .setNegativeButton("取消",null).setPositiveButton("切换",(d,w)->{
        if(busy){toast("请等待当前任务完成");return;}if(!requestConnection())return;
        final AdbClient client=adb;final String endpoint=host+":"+prefs.getString("port","5555");
        final DesktopAssistant.Snapshot[] result={null};
        if(fromState.dialog!=null)fromState.dialog.dismiss();
        run("切换桌面到 "+to.displayName,()->{
          taskProgress("正在读取两个桌面状态…");
          DesktopAssistant.Device device=command->{
            if(adb!=client)throw new IOException("连接设备已变化，已停止。");
            taskProgress(command.contains("set-home-activity")?"正在切换默认桌面…":command.startsWith("settings --user 0 put")||command.startsWith("settings --user 0 delete")||command.startsWith("pm grant")||command.startsWith("pm revoke")?"正在切换桌面守护…":"正在读取电视状态…");
            return client.shell(command,8000).require();
          };
          try {result[0]=DesktopAssistant.switchDesktop(device,desktopStore(),endpoint,fromState.snapshot,toState.snapshot);}
          catch(DesktopAssistant.VerificationFailure failed){
            result[0]=failed.snapshot;
            try{writeDesktopFile(desktopReportFile(false,to),DesktopAssistant.report(failed.snapshot,fromState.manual));}catch(Exception saving){failed.addSuppressed(saving);}
            ui.post(()->{if(isFinishing()||isDestroyed()||adb!=client)return;toState.snapshot=failed.snapshot;toState.client=client;toState.endpoint=endpoint;toState.outcome=failed.snapshot.outcome+"："+failed.getMessage();});
            throw failed;
          }
          writeDesktopFile(desktopReportFile(false,to),DesktopAssistant.report(result[0],fromState.manual));
          return DesktopAssistant.summary(result[0]);
        },()->{
          toState.snapshot=result[0];toState.client=client;toState.endpoint=endpoint;
          if(result[0].outcome!=null)toState.outcome=result[0].outcomeDetail;
          fromState.snapshot=null;fromState.client=null;fromState.endpoint=null;fromState.manual=null;fromState.outcome=null;
          showDesktopPanel(to,toState);
        });
      }).show();
  }
  private void reviewDesktopRepair(DesktopAssistant.Profile profile,DesktopState state) {
    if(!desktopCurrent(profile,state))return;final List<String> candidates=DesktopAssistant.candidates(state.snapshot);
    if(candidates.isEmpty()){dialog("没有可直接修复的缺失项","没有可安全写入的缺失配置，也可能当前用户不支持或状态未知。请查看守护运行及崩溃检查；配置完整仍未绑定，不代表修复完成。不要反复开启或重启，历史崩溃也不能单独证明当前仍有故障。");return;}
    String[] labels=new String[candidates.size()];boolean[] selected=new boolean[candidates.size()];
    for(int i=0;i<labels.length;i++){labels[i]=DesktopAssistant.label(candidates.get(i),profile);selected[i]=!candidates.get(i).equals(DesktopAssistant.DEFAULT);}
    new DeckDialogBuilder(this).setTitle("选择要修复的项目")
      .setMultiChoiceItems(labels,selected,(d,index,on)->selected[index]=on)
      .setNegativeButton("取消",null).setPositiveButton("查看改动并备份",(d,w)->{
        List<String> chosen=new ArrayList<>();StringBuilder preview=new StringBuilder();
        for(int i=0;i<selected.length;i++)if(selected[i]){chosen.add(candidates.get(i));preview.append("• ").append(labels[i]).append("\n");}
        if(chosen.isEmpty()){toast("未选择修复项目");return;}
        preview.append("\n每项修改前先保存原值；只补充 "+profile.displayName+" 所需项目，保留其他服务。启用守护会打开无障碍总开关，列表内原有服务也可能随之运行。默认桌面仅在已读到原桌面且系统支持时修改，小米和旧系统跳过。修改后会核验所选配置；选择守护时还会限次检查绑定并比较新增崩溃。未生效、未绑定或出现新崩溃时停止并保留备份，不会重复写入。不能据此承诺开机接管。");
        new DeckDialogBuilder(this).setTitle("确认本次具体改动").setMessage(preview.toString()).setNegativeButton("取消",null).setPositiveButton("备份并执行",(a,b)->{
          state.manual=null;state.outcome=null;
          desktopAction("修复 "+profile.displayName+" 桌面",true,(device,endpoint,st)->DesktopAssistant.repair(device,desktopStore(),endpoint,st,chosen),profile,state);
        }).show();
      }).show();
  }
  private void reviewDesktopRestore(DesktopAssistant.Profile profile,DesktopState state) {
    if(!desktopCurrent(profile,state))return;
    final JSONObject[] backup={null};final String id=state.snapshot.id;
    run("读取桌面备份",()->{backup[0]=desktopStore().load(id);return "";},()->{
      if(!DesktopAssistant.hasBackup(backup[0])){dialog("暂无可恢复的修改","当前设备没有本 App 记录的未恢复变更。备份与设备身份绑定；更换地址或固件后可能需要重新核对。");return;}
      if(DesktopAssistant.hasUncertain(backup[0])){
        StringBuilder uncertain=new StringBuilder("以下项目的写入结果未确认，不能认定来自轻连，本次不会自动回滚：\n");
        JSONArray entries=backup[0].optJSONArray("changes");for(int i=0;i<entries.length();i++){JSONObject e=entries.optJSONObject(i);if(e!=null&&!Arrays.asList("applied","prepared","restored","preserved","skipped").contains(e.optString("state")))uncertain.append("• ").append(DesktopAssistant.label(e.optString("key"),profile)).append("\n");}
        uncertain.append("\n保留当前设置：只归档这些未确认记录，不向电视写设置。其他已确认修改仍保留备份，可之后单独恢复。取消会完整保留备份。");
        new DeckDialogBuilder(this).setTitle("先处理未确认备份").setMessage(uncertain.toString()).setNegativeButton("取消",null)
          .setPositiveButton("保留当前设置",(d,w)->{state.outcome=null;desktopAction("归档未确认桌面备份",true,(device,endpoint,st)->DesktopAssistant.preserveUncertain(device,desktopStore(),endpoint,st),profile,state);}).show();return;
      }
      StringBuilder details=new StringBuilder();JSONArray changes=backup[0].optJSONArray("changes");
      for(int i=0;i<changes.length();i++){JSONObject entry=changes.optJSONObject(i);if(entry==null||Arrays.asList("restored","preserved","skipped","prepared").contains(entry.optString("state")))continue;details.append("• ").append(DesktopAssistant.restoreLabel(entry.optString("key"),profile)).append(entry.optString("state").equals("pending")?"（上次结果未知）":"").append("\n");if(entry.optString("key").equals(DesktopAssistant.DEFAULT))details.append("  原桌面：").append(entry.optString("before")).append("\n");}
      details.append("\n只撤回本 App 记录的改动，保留其他服务以及之后手动选择的桌面。恢复期间会暂停 "+profile.displayName+"；之后再次打开它，可能由 "+profile.displayName+" 自身重新启用守护。未能确认恢复时会保留备份以便继续。");
      new DeckDialogBuilder(this).setTitle("恢复修改预览").setMessage(details.toString()).setNegativeButton("取消",null).setPositiveButton("恢复并复查",(d,w)->{
        state.manual=null;state.outcome="已撤回本 App 可恢复的改动，保留外部变更；"+profile.displayName+" 已暂停。";
        desktopAction("恢复 "+profile.displayName+" 桌面修改",true,(device,endpoint,st)->DesktopAssistant.restore(device,desktopStore(),endpoint,st),profile,state);
      }).show();
    });
  }
  private void reviewHomeCheck(DesktopAssistant.Profile profile,DesktopState state) {
    if(!desktopCurrent(profile,state))return;
    new DeckDialogBuilder(this).setTitle("请使用电视实体遥控器")
      .setMessage("请按电视遥控器的主页键，等待约 10 秒，再点击下面的“已按主页，读取”。本步骤不模拟按键，不修改默认桌面，也不会重启电视。")
      .setNegativeButton("取消",null).setPositiveButton("已按主页，读取",(d,w)->desktopAction(profile.displayName+" 主页键人工验收",true,(device,endpoint,st)->{
        DesktopAssistant.Snapshot now=DesktopAssistant.inspect(device,endpoint,profile);
        if(!st.id.equals(now.id))throw new IOException("设备身份已变化，未作主页键判断。");
        boolean current=now.focus!=null&&(now.focus.startsWith(profile.pkg+"/")||now.focus.startsWith(profile.litePkg+"/"));
        state.manual=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date())+" · "+(now.focus==null?"未确认：未读取到前台窗口。":current?"本次主页操作后看到 "+profile.displayName+" 前台，当前验收通过。":"本次未看到 "+profile.displayName+" 前台："+now.focus)+"\n依赖你已按实体主页键；不代表开机接管已通过。";
        state.outcome="主页键结果已记录，本步骤没有更改电视设置。";return now;
      },profile,state)).show();
  }
  private void restartDesktop(DesktopAssistant.Profile profile,DesktopState state) {
    if(!desktopCurrent(profile,state))return;
    new DeckDialogBuilder(this).setTitle("重新启动 "+profile.displayName+"？").setMessage("只暂停并重新打开 "+profile.displayName+"，会中断它当前的界面。不会重启电视、清除数据或停用原桌面。启动后限次核验绑定并比较新崩溃。未绑定或发现新增崩溃时返回未完成结果，不会反复重启。")
      .setNegativeButton("取消",null).setPositiveButton("重新启动",(d,w)->{
        state.manual=null;state.outcome=null;
        desktopAction("重新启动 "+profile.displayName+" 桌面",true,(device,endpoint,st)->{
          DesktopAssistant.Snapshot now=DesktopAssistant.inspect(device,endpoint,profile);
          if(!st.id.equals(now.id)||!now.installed||now.code!=st.code||now.userId!=0)throw new IOException(profile.displayName+" 安装、设备或当前用户状态变化，请重新检查。");
          device.shell("am force-stop "+profile.pkg);
          Thread.sleep(3000);
          String out=device.shell("am start -n "+profile.home);
          if(!out.contains("Starting: Intent")||out.contains("Error:")||out.contains("Exception"))throw new IOException(profile.displayName+" 启动请求未确认：\n"+out);
          return DesktopAssistant.verifyLaunch(device,endpoint,now);
        },profile,state);
      }).show();
  }
  private void exportDesktopReport(DesktopAssistant.Profile profile,DesktopState state) {
    run("准备桌面报告导出",()->{lastDesktopExportProfile=profile;String report=readDesktopFile(desktopReportFile(false,profile));writeDesktopFile(desktopReportFile(true,profile),report);return "";},()->createDocument(106,"text/plain","LinkDeck_"+profile.displayName.replace(" ","")+"_Diagnostic_"+System.currentTimeMillis()+".txt"));
  }
  private void lastDesktopReport(DesktopAssistant.Profile profile) {
    final String[] report={null};run("读取上次桌面报告",()->{report[0]=readDesktopFile(desktopReportFile(false,profile));return "";},()->{
      TextView text=text(report[0],14,INK);text.setTextIsSelectable(true);text.setPadding(dp(24),dp(8),dp(24),dp(8));ScrollView scroll=new ScrollView(this);scroll.addView(text);
      new DeckDialogBuilder(this).setTitle("上次桌面报告（历史记录）").setView(scroll).setNegativeButton("关闭",null).setPositiveButton("导出 TXT",(d,w)->exportDesktopReport(profile,profile==DesktopAssistant.AERIAL?aerialDesktop:haloDesktop)).show();
    });
  }

  private void appendLog(String name, String target, boolean ok, String result, Exception error) {
    try {
      JSONArray old = new JSONArray(prefs.getString("logs", "[]")), a = new JSONArray();
      JSONObject o = new JSONObject();
      o.put("time", new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));
      o.put("device", target);
      o.put("action", name);
      o.put("ok", ok);
      o.put("state",error instanceof BatchFailure&&((BatchFailure)error).succeeded>0?"partial":ok?"success":"failed");
      String detail = ok ? result : message(error);
      o.put("detail", detail == null ? "" : detail.substring(0, Math.min(detail.length(), 1500)));
      a.put(o);
      for (int i = 0; i < old.length() && a.length() < 200; i++) a.put(old.get(i));
      prefs.edit().putString("logs", a.toString()).apply();
    } catch (Exception ignored) {
    }
  }

  private void logs() {
    try {
      JSONArray a = new JSONArray(prefs.getString("logs", "[]"));
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < a.length(); i++) {
        JSONObject o = a.getJSONObject(i);
        s.append(o.getString("time"))
            .append(" · ")
            .append(o.optString("device"))
            .append("\n")
            .append(o.getBoolean("ok") ? "✓ " : "× ")
            .append(o.getString("action"))
            .append("\n")
            .append(o.optString("detail"))
            .append("\n\n");
      }
      dialog("最近 200 条操作记录", s.length() == 0 ? "暂无记录" : s.toString());
    } catch (Exception e) {
      dialog("读取失败", message(e));
    }
  }
}
