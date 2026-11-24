# DESIGN-0.19.20 — 白色主题主按钮改用方案三柔和青蓝渐变

## 背景

用户在真机截图（云白 · 浅色主题，0.19.19）上指出：连接页两个大渐变按钮
（「查找电视」「连接设备」）的颜色和白色主题不搭。

实测取色（截图 `147524_0_k0vp.jpg`）：按钮是极光渐变，左端薄荷绿 #73e7c2，
中间被 `accent()` 在浅色主题下映射成的深青 #2d6864，右端紫 #6c5ee9；
深色字 #03211b 压在中间深青段上发糊。问题根源：极光渐变是为深色底设计的，
浅色主题下中间段太暗、两端太艳。

## 用户决策

做了配色预览页（artifact `qing-lian-bai-se-zhu-ti-an-niu-pei-se-yu-lan`），
5 个方案并排对比，用户 2026-11-24 选定**方案三 · 柔和青蓝渐变**。
核查员从预览源码取回精确色值（inspection 3a499a17）：

- 按钮：`linear-gradient(90deg, #67e8f9 0%, #38bdf8 55%, #818cf8 100%)`，文字 `#0b2a3d`
- 底部导航选中项：`linear-gradient(90deg, #67e8f9 0%, #38bdf8 60%, #818cf8 100%)`，文字 `#0b2a3d`
- 第一张卡片描边：`linear-gradient(135deg, #67e8f9, #818cf8)`

方案三去掉了中间深青段，全程走浅色；仍比方案一（深青纯色）、方案二（雾蓝灰）
更活泼，是“保留渐变手感”的折中选择。

## 改动范围（只动浅色主题，深色主题原样）

| 位置 | 现状 | 0.19.20（仅 light） |
|---|---|---|
| 主按钮渐变（`DepthSurface` emphasized → `AuroraStyle.primaryStops()`） | `{GREEN, accent(), VIOLET}`，浅色下中间段为深青 `0xff006b60` | `{0xff67e8f9, 0xff38bdf8, 0xff818cf8}` |
| 主按钮/导航选中项文字 | `PRIMARY_TEXT 0xff03211b`（常量） | 新增 `primaryText()`：浅色 `0xff0b2a3d`，其余 `PRIMARY_TEXT` |
| 底部导航选中项底（`DepthSurface` emphasized） | 同主按钮 | 同主按钮（共用 `primaryStops()`） |
| 首张卡片描边（`GlassSurface` highlighted，唯一一处是连接页 hero 卡） | `AuroraGlow.gradient`（GREEN→VIOLET） | 新增 `AuroraGlow.frameGradient`（`frameStart/frameEnd`：浅色 `#67e8f9→#818cf8`） |

不改：`glowStart()/glowEnd()`（背景极光光斑、按键按压辉光保持原样），
`DepthSurface` 的对角渐变几何（`{0,.6f,1}`，与预览的横向 90deg、55% 中点
在视觉上无实质差别），`PRIMARY_TEXT` 常量（深色主题与原型断言仍用它）。

## 文件

- `AuroraStyle.java`：新增 `SOFT_*` 常量、`primaryStops()` 浅色分支、
  `primaryText()`、`frameStart()`、`frameEnd()`。
- `AuroraGlow.java`：新增 `frameGradient()`。
- `GlassSurface.java`：`glow` 改用 `frameGradient()`（只影响 highlighted 的 hero 卡）。
- `MainActivity.java`：4 处 `PRIMARY_TEXT` 改为 `primaryText()`（主按钮文字、
  导航选中图标/文字、「查找电视」按钮文字）。
- `tests/AuroraChecks.java`：`primaryStops()` 断言按主题分支；文字对比度改按
  `primaryText()` 对 `primaryStops()` 两端；新增浅色卡片描边断言。
- `tests/ui1920_contract.py`：新增 0.19.20 范围检查；`unified_test.py` 终点改 ui1920。

## 验证

- 主机检查：AuroraChecks（五主题对比度，含浅色新断言）+ palette + ui1920。
- 真机验收（待用户）：白色主题下看按钮右端淡紫段上的深色字是否清晰、
  首张卡渐变描边是否仍显抢眼；深色主题确认无变化。
