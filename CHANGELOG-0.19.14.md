# 0.19.14 (vc62) — Halo TV 桌面管理 + 随时切换两个桌面助手

## 新增
- Halo TV 桌面管理：与 Aerial 同一套 4 入口（检查更新 / 版本管理 / 桌面助手 / 故障报告），
  覆盖 Null9797/halo-tv 官方发布（最新 3.3.6，tag `vX.Y.Z-halo`）。
- 随时切换两个桌面助手：Aerial ↔ Halo TV 一键切换。切换同时做两层——停用当前桌面的
  守护、启用目标桌面的守护并设为默认桌面；两个守护绝不同时启用；每项改动先备份、可恢复；
  切换后按回读核验（目标守护绑定 + 默认桌面 + 原守护已停用）。
- Halo TV 进官方应用更新扫描（检查更新入口与批量更新排除与 Aerial 一致，专属流程处理）。

## 实现要点
- 新类 HaloSupport / HaloDownload / HaloInstaller / HaloProbe（照抄 Aerial* 模式）：
  tag 只认 `vX.Y.Z-halo`（-test / -r2 / beta / universal 旧 tag 自动跳过）；asset 名
  `HaloTV-{版本}-{standard,guard,lite}.apk`；下载源 GitHub Releases 为主、
  halo-tv.pages.dev/updates/ 为镜像；digest 取 API 的 digest 字段，与 SHA256SUMS 一致。
- DesktopAssistant 按桌面 Profile 参数化（AERIAL / HALO）：包名、守护服务
  `app.orchard.tv.guard.HomeGuardService`、监听 `RevivalListener`、主界面组件按 Profile 取值；
  Aerial 路径行为不变（host 检查 DesktopChecks / DesktopRaceChecks / DesktopVerificationChecks
  全部通过）。
- Halo 的类名仍是 `app.orchard.tv.*`，所以打开 Halo 必须用全限定组件名
  `app.halotv.launcher/app.orchard.tv.MainActivity`（Lite 为
  `app.halotv.launcher.lite/app.orchard.tv.lite.HomeActivity`）。
- 桌面报告与故障报告按桌面分文件（…_halo.txt），不再互相覆盖。
- 桌面助手对话框新增“切换到 …”按钮；切换确认页说明两层改动与备份。

## 待真机验证（用户侧）
- Halo 安装/更新流程（3.3.6 standard，vc259）。
- 切换：关守护没有 Halo 官方写法，用的是本 App 恢复备份时同一套列表操作（从无障碍列表
  和通知监听列表里移除），原理上是同一个系统设置；需真机验证切过去后主页键与开机接管。
- set-home-activity 在用户电视固件上是否生效（Aerial 那路已在用，Halo 是同一条命令）。

## 测试
- 新 HaloReleaseChecks（tag 解析、asset 名、URL 校验、digest、releases 过滤、降级保护）。
- ui1914_contract.py（0.19.7 基线 + 允许改动集含 DesktopAssistant / DesktopDiagnostic /
  OfficialAppSources / MainActivity；断言 Halo 字符串与切换入口在、旧桌面字段已移除）。
- 其余 host 检查与 0.19.13 同一套（已知的本机环境失败除外）。
