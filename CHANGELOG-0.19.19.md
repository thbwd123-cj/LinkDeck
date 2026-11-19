# LinkDeck 0.19.19 变更

## 背景（用户真机反馈，2026-11-19，卧室投影 大眼橙 X7，0.19.18）

1. 用户问大眼橙有没有红外遥控——**没有**：0.19.18 的 83 个红外方案、flipperdevices/IRDB
   （电视 597 项、投影 126 项）及 Lucaslhm 分叉里都没有大眼橙 / Dayan / OBE 码组，
   网上也搜不到现成码组，红外路线走不通。
2. 用户要遥控器上有投影专属键：自动对焦、梯形校正（选了“都要”）。原装遥控器
   已丢失，物理按键抓包的查法作废。
3. 用户发来投影系统设置页截图：“投影”一组里有对焦设置、梯形校正、缩放位移、
   投影模式、亮度调节；又发来两次桌面助手诊断的“当前前台”，拿到两个界面的
   组件名（两个独立 Activity，可直达）。
4. 0.19.18 的修复报“未完成”是误报：配置已写入（goals 已过），卡在
   `verifyOperation` 的 bound==null 分支——大眼橙固件的 `dumpsys accessibility`
   输出里没有可识别的 Bound services 段，bound() 解析不到。守护随后实际已运行
   （用户 22:49“可以了”，主页键回 Halo）。
5. 用户说桌面助手面板太复杂（9 个按钮 + 一大段诊断），要求优化。

## 改动（3 处 + 测试）

- 新增 `ProjectorKeys.java`：对焦设置
  `com.obe.settings/com.obe.settings.projector.FocusSettingsActivity`、梯形校正
  `com.obe.settings/com.obe.settings.projector.KeystoneSettingsActivity`；
  `isObeProjector(properties)` 按 ro.product.model / ro.product.brand 含 "obe"
  判定大眼橙投影；`launchCommand()` 拼 `am start -n <组件>`。
- `MainActivity.remote()`：仅网络遥控（ADB）模式、在快捷键行之后，若为大眼橙投影
  则加“投影”卡片，两个键（对焦设置 / 梯形校正）→ `openProjectorScreen()`，
  用 `adb.shell("am start -n …", 15000).require()` 打开，输出含 Error / does not
  exist / Permission Denial / Unable to 即报“设备没有这个功能”。红外模式不变，
  非大眼橙设备上两个键不出现。
- `DesktopAssistant.verifyOperation()`：3 次复查后，若 `bound==null` 且配置已确认
  写入，不再抛 VerificationFailure，返回 outcome=“配置已写入，绑定状态未确认”
  + outcomeDetail（请按实体主页键验收）。`bound==false`（已识别列表中确实没有
  守护）仍按原来抛“配置完成，守护未运行”。仅 repair() 调用 verifyOperation，
  switchDesktop 有自己的复查，不受影响。
- `MainActivity.showDesktopPanel()`：顶部保留本次结果 + 状态徽标 + 一句话原因；
  完整诊断默认隐藏，加“查看详细诊断”按钮切换显示/收起；常用只留切换、修复
  （candidates 为空时置灰并说明“没有可直接修复的缺失项”）、主页键人工验收 /
  重新检查；其余 6 项（故障报告、恢复本 App 的修改、导出诊断报告、重新启动、
  电视桌面设置、打开电视无障碍设置）收进“更多操作”折叠项。全部回调不变。
- 测试：新增 `ProjectorKeysChecks`（OBE_NEW X7 / 品牌 obe 为真，创维 8H66 H7 /
  空为假；两个组件名与 launchCommand 与用户截图逐字一致）；
  `DesktopVerificationChecks` 的 unknownBound 用例改按新行为（repair 不抛错，
  outcome 含“配置已写入，绑定状态未确认”，ACCESS / ENABLED 各只写一次）；
  ui1919 合同（new 集加 ProjectorKeys.java，新增 wants 对焦设置、梯形校正、
  FocusSettingsActivity、KeystoneSettingsActivity、com.obe.settings、
  am start -n、更多操作、查看详细诊断、配置已写入，绑定状态未确认、
  没有可直接修复的缺失项、isObeProjector）。

## 未改动

- 不加红外大眼橙方案（无公开码源，不编造码）。
- 不改 Halo 更新源：投影上的 Halo 已是 3.3.7（263）；轻连 Halo 检查读的是
  Halo 官方 updates independent.json 实时数据，构建时 curl 核对过，该 feed
  已列 3.3.7（Standard / Guard 263，Lite 117），检查自然能看到。
- 不碰切换流程、红外码库、品牌指引、UncertainBackup 安全锁。
