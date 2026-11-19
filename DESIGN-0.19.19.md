# DESIGN-0.19.19 — 投影专属键 + 守护核验误报 + 桌面助手简化

## 背景（用户原话与证据）

- 卧室投影：大眼橙 X7（OBE_NEW X7，192.168.31.185），Android 9.0.0-RS-20220707.2053，
  用户 0，Halo TV 3.3.7（263）。原装遥控器找不到了，手机就是遥控器。
- 轻连 0.19.18 的 83 个红外方案里没有大眼橙；flipperdevices/IRDB（电视 597 项、
  投影 126 项）及 Lucaslhm 分叉里也没有大眼橙 / Dayan / OBE 文件，网上无现成码组。
  红外路线走不通。
- 用户要遥控器上有投影专属键：自动对焦、梯形校正（2026-11-19 22:54 选“都要”）。
- 用户 23:10 发诊断截图，已拿到两个界面的组件名（两个独立 Activity，可直达）：
  - 对焦设置：`com.obe.settings/com.obe.settings.projector.FocusSettingsActivity`
  - 梯形校正：`com.obe.settings/com.obe.settings.projector.KeystoneSettingsActivity`
- 0.19.18 修复报“未完成”是误报：配置已写入（goals 已过），卡在 verifyOperation
  的 bound==null 分支——大眼橙固件的 `dumpsys accessibility` 输出里没有可识别的
  Bound services 段，bound() 解析不到。守护随后实际已运行（用户 22:49“可以了”，
  主页键回 Halo）。
- 用户 23:11：桌面助手面板太复杂（9 个按钮 + 一大段诊断），要求优化。

## 改动

### 1. 投影专属键（主）

- 新增 `ProjectorKeys.java`：常量 FOCUS / KEYSTONE（上面两个组件）；
  `isObeProjector(Map<String,String> properties)`：ro.product.model 或
  ro.product.brand 含 "obe"（不区分大小写）即为大眼橙投影；
  `launchCommand(component)`：拼出 `am start -n <组件>`（单点，便于单测）。
- `MainActivity.remote()`：仅网络遥控（ADB）模式、在快捷键行之后，若
  `isObeProjector(properties)` 则加一张“投影”卡片，两个键
  （pair：对焦设置 / 梯形校正）→ `openProjectorScreen(title, component)`。
  红外模式不变（红外没有大眼橙码，这两个键只走 ADB）。
- `openProjectorScreen`：requestConnection() + REMOTE 能力检查后，用 run()
  执行 `adb.shell(launchCommand, 15000).require()`，并检查输出含
  Error / does not exist / Permission Denial / Unable to 即报
  “设备没有这个功能”。成功返回“已打开”。失败走 run() 的统一失败提示，
  不重复写、不自动重试。
- 不改动现有遥控键、不改动红外码逻辑；非大眼橙设备上两个键不出现。

### 2. 守护核验误报（次）

- `DesktopAssistant.verifyOperation()`：3 次复查后，若 `bound==null`
  （系统未提供可识别的绑定信息）且 goals 已过（配置已确认写入），不再抛
  VerificationFailure，改为返回结果，outcome=“配置已写入，绑定状态未确认”，
  outcomeDetail=“所选配置已复查并写入；系统未提供可识别的绑定信息，
  无法据此确认守护是否运行。请按实体主页键验收。”
- `bound==Boolean.FALSE`（已识别列表中确实没有守护）仍按原来抛
  “配置完成，守护未运行”。`verifyOperation` 仅 repair() 调用，
  switchDesktop 有自己的复查，不受影响。
- 诊断里的“守护运行：未确认”一行保持原样（诚实），面板文案不变。

### 3. 桌面助手简化（次）

`MainActivity.showDesktopPanel()`，回调全部不变，只改排布：

- 顶部保留：本次结果（若有）+ 状态徽标 + 一句话原因（已是一句话状态）。
  完整诊断摘要默认隐藏，加“查看详细诊断”按钮切换显示/收起。
- 常用按钮：切换到 X；修复缺失授权 / 守护（candidates 为空时置灰，
  透明度 .38，内容说明“没有可直接修复的缺失项”）；主页键人工验收 / 重新检查。
- “更多操作”折叠项（默认收起）：故障报告；恢复本 App 的修改 / 导出诊断报告；
  重新启动 X / 电视桌面设置；打开电视无障碍设置。
- 面板每次重建时折叠状态回到默认（收起），不做跨次持久化。

## 不做

- 不加红外大眼橙方案（无公开码源，不编造码）。
- 不改 Halo 更新源：投影上的 Halo 3.3.7（263）比 0.19.14 调研时的最新
  3.3.6（259）还新；轻连 Halo 检查读的是 Halo 官方 updates
  independent.json 实时数据，若该 feed 已列 3.3.7，检查自然能看到，
  无需改代码。构建时顺手 curl 核对一次，只记录结论。
- 不碰切换流程、不碰红外码库、不碰品牌指引。

## 测试

- 新增 `tests/ProjectorKeysChecks.java`：isObeProjector 对 OBE_NEW X7 / 品牌 OBE
  为真，对创维 8H66 H7 / 空 / 无关品牌为假；launchCommand 与两个组件名
  与用户截图逐字一致。
- `tests/DesktopVerificationChecks.java`：unknownBound 用例改按新行为——
  repair 不抛错，返回 outcome 含“绑定状态未确认”，且 ACCESS / ENABLED
  各只写一次；bound==FALSE 的 switched 用例保持抛错不变。
- 新增 `tests/ui1919_contract.py`：沿用 ui1918 的基线比对（changed 集不变，
  new 集加 ProjectorKeys.java），另断言 0.19.19 关键字：
  对焦设置、梯形校正、FocusSettingsActivity、KeystoneSettingsActivity、
  com.obe.settings、更多操作、查看详细诊断、配置已写入，绑定状态未确认、
  am start -n。
- `tests/unified_test.py`：classes 加 ProjectorKeys，checks 加
  ProjectorKeysChecks（含执行行），末尾契约改为 ui1919_contract.py。
- Host-check 按既定 runner 模式跑（跳过已知的环境失败与被 supersede 的契约），
  以 palette + ui1919 收尾。

## 发布

- 0.19.19 / vc67，同签名；CHANGELOG-0.19.19.md。
- 按 0.19.18 既定流程发布：文本 push-files、catalog.jar 经浏览器临时子目录、
  浏览器建 release（建任务时 files 带 APK）、重新下载验哈希。
