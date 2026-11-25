# DESIGN-0.19.22 — 触摸板 + 手机键盘中文输入

方向：用户 2026-11-24 在对比页上选了第 1 名「触摸板/手势 + 手机键盘输入」。
版本：0.19.22，versionCode 70，同签名，覆盖安装。

## 一、为什么是这个方案（已核实的事实）

- `adb shell input text` 只支持 ASCII（ADBKeyBoard README 原话 + 多方验证），中文搜片必须走电视端输入法。
  0.19.21 的键盘面板因此只允许英文数字，本版补中文。
- ADBKeyBoard（senzhk/ADBKeyBoard，GPLv2）：电视端虚拟输入法，收广播写字。
  包 `com.android.adbkeyboard`，IME `com.android.adbkeyboard/.AdbIME`，minSdk 21
  （创维 5.1 / 大眼橙 9.0 都能装）。稳定版 v2.4-dev，APK 18,595 字节，
  SHA-256 `e0d0cf276b710cb34c46121f58720f5285a83ed410b0d45f57a0677b67dc2852`，
  下载地址 `https://github.com/senzhk/ADBKeyBoard/releases/download/v2.4-dev/keyboardservice-debug.apk`
  （GitHub Releases API 已核对：tag v2.4-dev，asset 名一致；aapt2 核对包名/版本）。
- 因 GPLv2，**不把 APK 打进轻连 assets**，改走现有官方源下载模式：
  按需从上述官方地址下载，下载后验大小 + SHA-256 再安装，并在界面注明来源与协议。
- 中文发送走 `ADB_INPUT_B64`（base64 的 UTF-8），不用 `ADB_INPUT_TEXT`：
  README 注明 Oreo/P 起 am 命令的 UTF-8 直传可能失效，base64 绕开一切 shell 引号问题。
- 真鼠标光标做不了（需电视端虚拟鼠标设备或 root，纯 ADB 只有 `input roll` 轨迹球），
  本版不做；触摸板只做前两层：滑动→方向键、轻点→确定、长按→菜单，另加截屏点按。

## 二、触摸板（遥控页）

- 遥控页「方向控制」工具栏加一个「触摸板」开关（pref `remote_touchpad`，默认关）。
  开：D-pad 换成触摸板区域（同尺寸）；关：恢复方向盘。其余行（返回/主页/菜单、音量、
  快捷键、投影卡片）不动。
- 新 View `TouchPad.java`（仿 VolumeGlide 写法：Handler、requestDisallowInterceptTouchEvent、
  performClick、无障碍动作）。手势判定抽到纯类 `TouchPadLogic.java`（可 host 测试）：
  - 滑动阈值 48dp：自上次发键起，|dx|、|dy| 较大者满阈值即发一个方向键（19/20/21/22），
    优势轴余量保留，连续滑动连续发键；两次发键最短间隔 110ms。
  - 轻点：按下抬起 <350ms 且移动 <12dp → 确定（23）。
  - 长按：按下 >500ms 且移动 <12dp → 菜单（82），并抑制随后的轻点。
  - 每次发键一次触觉反馈。
- 回调一律走 `remoteKey(code)`，ADB 模式发 keyevent，红外模式发对应红外键，两边都可用。
- 截屏点按：`capture()` 对话框加「点按模式」开关。开后点图片即 `input tap x y`，
  坐标用 ImageView 矩阵的逆矩阵映射到位图坐标（screencap -p 是电视原生分辨率，
  位图坐标就是电视坐标）， clamp 到屏幕内；轻量发送（仿 `key()` 的串行 executor，
  不弹进度框），失败才 toast。默认关闭，避免看图时误触。

## 三、手机键盘中文输入

- 新纯类 `TvKeyboard.java`（可 host 测试）：
  - 常量：PKG、IME、DOWNLOAD_URL、EXPECTED_SHA256、EXPECTED_SIZE（18595）、LICENSE 注明。
  - `isAscii(String)`：全部 <128 才走 `input text`。
  - `base64(String)`：UTF-8 → Base64（无换行）。
  - `textCommand(String ascii)`：`input text '<…%s…>'`（沿用现有转义：空格→%s，AdbClient.quote）。
  - `broadcastCommand(String text)`：`am broadcast -a ADB_INPUT_B64 --es msg '<base64>'`。
  - `enableCommand()` / `setCommand()` / `restoreCommand(String original)` /
    `installedProbe()`（`pm list packages com.android.adbkeyboard`）/
    `currentImeProbe()`（`settings get secure default_input_method`）/
    `parseDefaultIme(String)`（去空白）。
  - `download(File, Progress)`：仿 AerialDownload 的可信地址 + 重定向检查（仅 github.com
    与 release-assets/objects.githubusercontent.com 的 HTTPS），验大小 + SHA-256。
- MainActivity 键盘面板改动：
  - 取消 ASCII 白名单校验（只保留 `%` 拒绝，因 `%s` 是空格转义）；说明文字改为
    「英文、数字直接发送；中文需电视端输入法（见下方状态）」。
  - 发送：ASCII → 原 `input text` 路径；含中文 → 若 pref `tv_keyboard_ready` 且已验证，
    发 B64 广播；否则弹对话框「中文输入需要电视端输入法」：[安装 ADBKeyBoard] [取消]，
    注明开源 GPLv2、来源 senzhk/ADBKeyBoard、只在电视上装一个输入法、不改手机。
  - 安装流程（run 对话框）：下载 → 验哈希 → ApkTransfer.install → `ime enable` →
    先读当前输入法存入 pref `tv_keyboard_original` → `ime set` → 读回
    `default_input_method` 核对 → 置 `tv_keyboard_ready`，状态行更新；
    装完自动把触发的那段文字发过去。
  - 面板加状态行（未安装 / 已安装，未启用 / 已启用）+「恢复电视输入法」按钮
    （`ime set` 回原输入法，清 ready 标志）。打开面板时后台刷新一次状态。
  - 面板加「退格」（keyevent 67）与「回车」（66）两个小按钮。

## 四、不做的事

- 不做真鼠标光标、不做投屏、不做语音（排在本版之后）。
- 不把 ADBKeyBoard APK 打进安装包；不改手机输入法；不碰现有 D-pad/红外逻辑。
- 切换回电视输入法前不删 ADBKeyBoard（留着下次用，用户可在电视应用里卸载）。

## 五、测试

- 新 `TouchPadChecks.java`（方向判定、阈值余量、轻点/长按分类、间隔常量）、
  `TvKeyboardChecks.java`（isAscii、base64 向量「你好」→ `5L2g5aW9`、广播/文本命令形态、
  常量、parseDefaultIme）。
- 新 `tests/ui1922_contract.py`（基线 79 不变；MainActivity 已在 changed 集；
  TouchPad.java / TvKeyboard.java 进 new 集；断言本版 token）。
- unified_test.py：classes += TouchPadLogic、TvKeyboard；checks += TouchPadChecks、TvKeyboardChecks；
  末尾合同 → ui1922。

## 六、真机待验（用户）

- 触摸板滑动手感（阈值 48dp 是否顺手、连续滑动是否丢键）、轻点/长按是否误触。
- 截屏点按是否点中（尤其创维 5.1 与大眼橙 9.0）。
- 中文输入：装 ADBKeyBoard 后在电视搜索框输中文片名；切回电视输入法是否正常。
