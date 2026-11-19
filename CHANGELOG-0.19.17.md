# 0.19.17 (vc65) — 红外新增索尼、三星 + 品牌使用指引扩到 16 个品牌

## 新增
- 红外遥控新增 2 个品牌、3 个方案（catalog.tsv 80 行 → 83 行）：
  三星方案 01 `Domestic_Samsung.ir`（Lucaslhm，CC0）13/13，Samsung32 地址 0x07 通用码组；
  三星方案 02 `Domestic_Samsung_125_xm_1_4190.ir`（IRDB，MIT）12/13（缺 HOME），
  BACK 用另一码 Exit 0x2D，是方案 01 的 BACK 备选；
  索尼方案 01 `Domestic_Sony_RMT_TX100D.ir`（IRDB，MIT）13/13。
  品牌选择里自动出现索尼、三星。
- 索尼方案走 SIRC→raw 转换：索尼遥控全是 SIRC 协议，`IrCodes.fromFlipper()` 直接拒收，
  成品文件是每键一帧 raw（40 kHz，起始 2400+600，bit1=1200+600、bit0=600+600，
  命令 LSB 在前再地址 LSB 在前）。转换规范来自 sbprojects 实测与 Flipper 固件源码，
  Python 生成器与 Java 独立实现各算一遍一致；每键 raw 用独立解码器解回地址/命令，
  13/13 往返一致；POWER、UP 另做手算抽查一致。
- 品牌使用指引（连接向导 → 按品牌查看设置图示）从 14 个品牌扩到 16 个：
  索尼（Android TV / Google TV 标准路径：设置 → 系统 → 关于 → 连按「Android TV OS build」
  7 次 → 开发者选项 → USB 调试；老机型走设备偏好设置 → 关于 → 连按「Build」7 次），
  三星（Tizen 系统没有 ADB，steps 为 null，走 `noGuide[]`：只能纯红外遥控，
  如需装应用或管理文件请接外接 Android 电视盒子）。

## 明确不做的
- 索尼 MENU（Options，SIRC15 0x97/0x36）与 BACK（SIRC15 0x97/0x23）是同年代
  Bravia 文件借用的码，TX100D 本身没有 MENU 键；需真机重点验证。
- 索尼单帧发送（与现有 NEC/Samsung32 键一致）。若某键按一次电视没反应，
  备选是每键 3 帧重发（帧间隔补到 45 ms，总长约 10.7 万 µs，远低于上限），
  真机验证后再决定是否改。
- 三星另一份码 `Samsung_125_xm_1_3618.ir` 等 8 个候选文件因撞码/缺键/多地址混用弃用，
  详见调研文件。索尼 `Sony_141_xm_1_3649.ir` 因方向键码组不同且 OK/BACK 撞码弃用。

## 待真机验证（用户侧）
- 三星方案 01：逐键验证，重点是 BACK（0x58 是否返回；若无效换方案 02 的 0x2D）、
  HOME（Smarthub 0x79）、SOURCE。
- 索尼方案 01：先验 POWER、音量、方向、OK、HOME，再验 BACK、MENU（借用的两键）、SOURCE；
  若某键按一次无反应，报出键名再决定是否改 3 帧重发。
- 索尼指引的新老两条 ADB 路径是否与电视菜单一致；三星“无 ADB、纯红外”说明是否准确。

## 测试
- IrCodesChecks 现跑 83 条目录（含 3 条新增，均要求 POWER + VOL_UP 且码型不同），343 个检查通过。
- ui1917_contract.py（0.19.7 基线；断言索尼、三星、Android TV OS build、Tizen 等新字符串在）。
- 其余 host 检查与 0.19.16 同一套（已知的本机环境失败除外）。

## 调研
- `SONY-SAMSUNG-IR-RESEARCH-0.19.17.md`（本地，不进仓库）：6 个索尼文件与 10 个三星文件
  逐个核查，成品文件内容全文附在报告里，生成器 `gen_1917.py` 留在目录内备查。
