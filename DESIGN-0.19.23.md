# 0.19.23 设计：语音遥控 / 语音搜片

## 用户决定

- 2026-11-25 14:41 用户问“下一步做啥”，给出 4 个方向后点选“做语音遥控”。
- 方向来源：2026-11-24 竞品对比研究（`research_notes/linkdeck-competitor-directions-20261124-1355/report.md`）排名第 2：语音遥控/语音搜片；悟空/Google TV/CetusPlay 都有；对搜片场景价值最大。
- 本版只做**点一下麦克风说话**（tap-to-talk），不做常驻监听、不做唤醒词。

## 方案（已核实的前提）

- 手机端用 Android 系统自带的 `SpeechRecognizer` 在线识别（`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`，`LANGUAGE_MODEL_FREE_FORM`，语言 `zh-CN`，开 partial results，取前 3 个候选）。
- 识别结果分两路：
  1. **本地指令**：命中命令表 → 直接发对应按键（`remoteKey()`，ADB/红外通用）或打开应用。
  2. **搜片文字**：不命中命令 → 当搜索文字发到电视，复用 0.19.22 的键盘路径（英文走 `input text`，中文走 ADBKeyBoard 的 base64 广播；中文且输入法未就绪时走同样的安装提示）。
- 无需电视端配合。电视搜索框须先获得焦点（和所有遥控一样，先点进搜索框再说话）。
- 识别需要联网（在线识别），需要 `RECORD_AUDIO` 录音权限（运行时申请）。
- 语音发给系统识别服务转文字（用哪家服务由手机系统决定，可能是 Google 或手机厂商自带的服务）；轻连本身不录音、不存语音、不上传语音到任何自己的服务器。

## 荣耀手机的不确定点（真机待验）

- 用户的 Honor Magic8 Pro 上 `SpeechRecognizer.isRecognitionAvailable()` 是否为真，取决于系统里有没有可用的识别服务。2026-11-21 的 Gemini 线索（Google 应用已装，但 Gemini 认不到它）说明这台手机的 Google 服务状态不确定。
- 若不可用，本版弹明确说明（“这台手机没有可用的语音识别服务”），不瞎猜厂商设置路径。
- 离线识别不在本版：不打识别模型进包（包体积、中文模型来源、准确率都未验证），只走在线识别。

## 指令表（纯类 VoiceCommand，host 可测）

- 输入：识别候选文字列表（前 3 个）。归一化：去空格（含全角空格）、去标点、ASCII 小写。
- 精确命令 → 按键：
  - 返回/回去/后退/退出 → 4；主页/首页/回主页/回桌面 → 3；菜单 → 82
  - 确认/确定/好的；单独的“打开” → 23（确定键）
  - 上/向上/上移 → 19；下/向下/下移 → 20；左/向左/左移 → 21；右/向右/右移 → 22
  - 大点声/声音大一点/音量大/调大音量/大声点 → 24；小点声/声音小一点/音量小/调小音量/小声点 → 25；静音 → 164
  - 播放/继续播放 → 126；暂停 → 127；快进 → 90；快退 → 89
  - 上一首/上一集/上一个 → 88；下一首/下一集/下一个 → 87
  - 关机/关电视/开机/打开电视 → 26
- 前缀规则：
  - “打开X / 启动X / 进入X” → 打开应用（X 去前后缀后按名字匹配电视应用列表）
  - “搜索X / 搜X / 找X / 搜一下X” → 搜片文字 X
  - 单独的“搜索/搜” → 空查询，不执行，提示“没听清要搜什么”
- 含“大声/大点声/音量大” → 24；含“小声/小点声/音量小” → 25（精确匹配优先，其次前缀，最后包含匹配）。
- 候选逐个试：第一个命中 KEY/LAUNCH 的候选胜出；都没有命中 → 取第一个候选当搜片文字；候选全空 → 无结果。
- “打开X”匹配：对 `apps` 列表的名字（含包名兜底）做归一化包含匹配；恰好一个 → 启动；多个 → 弹列表让用户点选；零个 → 提示“电视上没找到叫 X 的应用”，不发文字。`apps` 为空时先后台刷新一次列表（`loadApps()`，复用现有读取应用列表流程）再匹配。

## MainActivity 改动

- `AndroidManifest.xml`：加 `<uses-permission android:name="android.permission.RECORD_AUDIO"/>`。
- 遥控页工具栏（ADB 模式，键盘输入图标旁）：加麦克风图标按钮 → `startVoiceInput()`。
- `startVoiceInput()`：未连接 → toast；`SpeechRecognizer.isRecognitionAvailable()` 为 false → 说明对话框；`RECORD_AUDIO` 未授予 → `requestPermissions(..., 42)`，授权后自动开始；否则 `beginListening()`。
- `beginListening()`：主线程创建 `SpeechRecognizer` + `RecognitionListener`；Intent 语言 zh-CN、FREE_FORM、partial results、MAX_RESULTS 3；弹“正在听…”对话框（实时显示 partial 文字 + 取消按钮）。
- Listener：`onPartialResults` 更新对话框文字；`onResults` 取候选列表 → 销毁识别器、关对话框 → `handleVoiceResult()`；`onError` 按码给一句话原因（网络/没听清/没听到声音/权限/服务忙/服务出错）后关闭。
- `handleVoiceResult(List<String>)`：`VoiceCommand.parse()`：
  - KEY → `remoteKey(code)`，结果对话框“识别：X → 已执行：返回”。
  - LAUNCH → 按上节匹配并启动（`ApplicationLauncher.command(pkg)` 走现有 `run("打开应用")` 流程）。
  - SEARCH → 抽出 `sendText(String)`（`sendRemoteText()` 改为读输入框后调它）：中文且 `tv_keyboard_ready` 为 false → 复用 `installTvKeyboard()` 安装提示（pendingChineseText = 识别文字）。
  - 空 → 对话框“没听清，再说一次” + [重说]。
- 结果对话框：显示识别文字 + 执行动作，按钮 [重说] [完成]。
- 生命周期：`onPause()/onDestroy()` 时若识别器在跑，cancel + destroy，关对话框。`onRequestPermissionsResult()` 新增：code 42，授权 → 开始监听，拒绝 → 说明对话框（语音需要录音权限）。

## 不做

- 不做唤醒词/常驻监听、不做离线模型、不做“调到第 X 台”、不做音量数值设定、不做游戏手柄。
- 不改：握手/连接/后台保活、红外、桌面助手、应用更新、触摸板、键盘面板本身。

## 测试

- 新增 `VoiceCommand.java`（纯类）+ `tests/VoiceCommandChecks.java`：精确命令、前缀规则、包含规则、候选顺序、归一化、空查询、打开/搜索边界。
- 新增 `tests/ui1923_contract.py`：基线 79（VoiceCommand.java 进 new 集；AndroidManifest.xml 进 changed）；wants：RECORD_AUDIO、SpeechRecognizer、isRecognitionAvailable、zh-CN、麦克风入口、正在听、重说、VoiceCommand.parse、onRequestPermissionsResult 42、打开/搜索前缀、ApplicationLauncher.command 复用、sendText 抽取；assets 里无语音模型文件。
- `unified_test.py` 终点改 ui1923，classes/checks 各加 VoiceCommand/VoiceCommandChecks。

## 真机待验

- 麦克风按钮一点就听、识别准不准（普通话/口音）、搜片文字进没进电视搜索框。
- “返回/大点声/打开 Jellyfin”这类指令是否一次命中。
- 荣耀手机上识别服务是否可用；不可用时的说明是否清楚。
- 录音权限拒绝/授予后流程是否正常；切后台/锁屏时识别是否干净结束。
