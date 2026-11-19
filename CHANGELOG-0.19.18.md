# LinkDeck 0.19.18 变更

## 背景（用户真机反馈，2026-11-19，创维 8H66 H7，0.19.17）

1. 切 Aerial/Halo 两个方向都报"备份含写入结果未确认的记录"——`UncertainBackup` 安全锁在起作用，不是 bug：之前某次桌面操作留了未确认的写入记录，切换前必须先在"恢复本 App 的修改"里点"保留当前设置"归档。用户照做后切换得以继续。
2. 检查 Halo TV 官方安装报"非 Aerial 应用"——**真 bug**：0.19.14 引入的 `MainActivity.installedProof()` 只认 Aerial 两个包名，Halo 检查流程已装时调它必抛错。
3. 用户处理完未确认备份后重试，切到 Halo 报"已切换配置，但限次检查无法确认 Halo TV 守护绑定"——**真 bug**：切换第 4 步验证只做一次 `inspect()`，守护启动要几秒，等不及；修复流程 `verifyOperation` 是 3 次带 1.5 秒间隔的，切换这里写漏了。配置本身已切过去（待用户按电视主页键确认是不是 Halo 桌面）。

## 改动（2 处 + 测试）

- `MainActivity.installedProof()`：白名单加 `HaloSupport.FULL, HaloSupport.LITE`；报错文案改为"非 Aerial / Halo 桌面应用"。
- `DesktopAssistant.switchDesktop()` 第 4 步：绑定未确认时最多再查 2 轮（每轮 `pause(1500)` 后重新 `inspect` + 校验身份/版本 + `goals`），与 `verifyOperation` 的 3 次检查对齐；仍不确认才报原错。
- 测试：`DesktopChecks.Fake` 扩展——`pm path app.halotv.launcher` 视为已装；`dumpsys accessibility` 按无障碍列表里的守护回显对应组件（Halo 用全限定 `app.halotv.launcher/app.orchard.tv.guard.HomeGuardService`，因为 `canonical()` 只能展开同包短写）。
- 新增 `DesktopVerificationChecks` 用例：迟绑定（第 2 次暂停后才绑上）的 Aerial→Halo 切换成功（`pauses==2`， outcome 含"已切换到 Halo TV"，原守护已移除、新守护已启用、默认桌面已改）；永不绑定的切换仍在 2 次暂停后失败报"守护未运行"。
- ui1918 合同：新增 wants `HaloSupport.FULL,HaloSupport.LITE`、`非 Aerial / Halo 桌面应用`；其余基线不变。

## 未改动

- `UncertainBackup` 安全锁本身不动（按设计工作）。
- 切换的其余步骤（停用原守护、设默认桌面、逐项回读）不动；绑定重试只做读操作，不重复写。
