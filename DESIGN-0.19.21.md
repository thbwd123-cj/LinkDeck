# DESIGN-0.19.21 — 后台保持连接

## 问题（用户 2026-11-19 23:46 / 23:52）

切后台就要重新连接；连接后没有「正在保持电视连接」通知。

已查明的三层原因：

1. **通知被系统隐藏（最可能的主因）**：Android 13+ 上通知默认关闭；用户拒绝
   `POST_NOTIFICATIONS` 后，前台服务的通知只在任务管理器里出现，不在通知栏
   显示（Android 官方文档）。轻连只在首次连接后问一次（`notificationAsked`），
   拒绝后不再问、不解释。服务可能一直在跑，用户看不见。
2. **划掉应用就主动断开**：`MainActivity.onDestroy()` 里 `isFinishing()` 时调
   `ConnectionService.disconnect()`，把会话关掉。`stopWithTask=false` 本来能让
   服务活过划掉，被这行抵消了。
3. **后台掉线无人接**：连接只活在 Activity 里；`recoverConnection()` 只在
   Activity resumed 时跑；进程被荣耀电源管理杀掉后，静态 Session 随进程消失，
   下次打开才重连。服务是 `START_NOT_STICKY`，被杀后不重启。

## 方案（四件，用户 2026-11-24 已批）

### (a) 划掉不断连

- 删 `MainActivity.onDestroy()` 里的 `if (isFinishing()) ConnectionService.disconnect(this);`。
- 显式断开只剩两条路：应用内「断开连接」按钮、通知上的「断开连接」动作
  （均调 `disconnect()`，不动）。
- 重进应用时 `onCreate → restoreSession()` 已会接管服务里的活会话（现成，无需改）。

### (b) 心跳保活

- 服务内单线程 `ScheduledExecutorService`（守护线程），启动后每 60 秒对活会话
  跑一次 `shell(":", 5000)` 空命令。socket 本身已开 TCP keepalive，这是应用层再确认。
- 成功：什么都不做。失败或 `!isConnected()`：走 (c) 重连。
- 服务停时关掉 executor；会话为空时停掉自己。不加 WakeLock、不拉起进程。

### (c) 后台自动重连

- 服务自己能重建连接：读同一份 `linkdeck` prefs（`host` / `port`、
  `connect_mode`、`tls-pin:<peerKey>`、`paired-host:<peerKey>`）+
  `getFilesDir()/identity` 身份文件，按 `MainActivity.connect()` 同样的路建
  `AdbClient`（加密走 `WirelessAdb.transport`，否则直连），
  `connect(h, port, identity)`（TCP 5 秒超时，握手等授权最多 60 秒），成功后
  `getprop` 取设备信息、换 Session、重发前台通知。
- 失败：保留旧会话（让 Activity 的 `recoverConnection` 还能接手），下个心跳
  周期再试一次——60 秒天然节流，不另做退避。
- 服务改 `START_STICKY`：进程被杀后系统重启服务，`onStartCommand(null)` 时
  若 Session 为空且 `resume_connection` 为真，先立即 `startForeground` 发一条
  「正在重新连接…」通知（5 秒前台超时前必须亮通知），再在后台线程重连；
  成功换通知正文，失败 `stopSelf()`（下次打开应用时 Activity 再连）。
- 竞态：Activity 活着时它自己的 `recoverConnection` 也会重连；后写者胜，
  `attach()` 的旧 client 关闭逻辑已兜底，不另加锁。
- 注意：后台重连只对已授权过的电视有效；电视端要重新点授权时，后台线程
  等到 60 秒握手期限后失败收尾，仍需用户打开应用走正常连接。

### (d) 启动失败说清原因

- `attach()` 的 catch 按异常区分：`SecurityException` →「缺少前台服务权限或
  类型不符」；`IllegalStateException` →「应用在后台时系统不允许启动前台服务」；
  其他 → 原文。去掉笼统的「系统暂不允许启动后台服务」。
- 连接成功后若 SDK≥33 且 `POST_NOTIFICATIONS` 未授予，弹一次说明：服务在跑，
  但通知被系统藏起来了（只在任务管理器可见），并给「去开启通知」按钮
  （跳通知设置 `ACTION_APP_NOTIFICATION_SETTINGS`；拒绝过一次后不再弹系统
  权限框，改走设置页）。同时把这段状态写进 `backgroundHelp()` 对话框
  （服务是否在跑、通知权限是否开、上次失败原因）。
- 权限后来补开时重发通知：`ConnectionService.repost()`（发 REFRESH 动作 →
  `onStartCommand` 重新 `startForeground` 贴同一条通知）；Activity 在 `onResume`
  时若权限已开且有活会话就调一次。

## 不改

- 连接本身的握手、授权、配对流程；`connect()` 的建 client 逻辑（服务侧按同样
  参数重建，不抽公共方法——避免动关键路径）。
- 显式断开的两条路；通知的文案与「断开连接」动作。
- 心跳只在服务活着时跑，不加 WakeLock、不拉起进程（被杀后靠 START_STICKY 重启）。

## 文件

- `ConnectionService.java`：心跳 executor、重连、`START_STICKY`、`REFRESH` / `repost()`、
  attach 失败原因。
- `MainActivity.java`：删 onDestroy 里的 disconnect；连接成功后的通知权限说明
  与跳转；onResume 时的 repost；`backgroundHelp()` 状态文案。
- `tests/KeepaliveChecks.java`（新增，纯 Java）：心跳间隔、重连参数拼装
  （connect_mode / tls-pin / paired-host / peerKey）、失败保留旧会话的规则。
- `tests/ui1921_contract.py`：新增 0.19.21 范围检查；`unified_test.py` 终点改 ui1921。

## 验证

- 主机检查：KeepaliveChecks + palette + ui1921；连接流程本身无真机可跑，
  靠源码级断言 + 既有 ConnectionChecks。
- 真机验收（待用户）：连接后通知栏出现「正在保持电视连接」；划掉应用后
  通知还在、遥控还能用；关电视再开、或等几分钟后不用重连；通知权限拒绝时
  有明确说明和去开启的入口；点通知「断开连接」确实断开。
