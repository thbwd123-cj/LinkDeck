# 轻连 0.19.10（versionCode 58）

本版只补一个漏：0.19.9 的弹窗染色只接了普通列表弹窗，单选与多选弹窗没接上。

## 漏掉的地方

- 0.19.9 的 DialogListTint 只在 DeckDialogBuilder 的两个 setItems 重载上生效。
- 选颜色主题用的是 setSingleChoiceItems，批量核验更新用的是 setMultiChoiceItems，两条路都没走染色；云白下选项文字是白色，浅底上看不清（真机截图确认）。
- 标题、按钮、单选圆圈本身不受影响，问题只在选项文字。

## 修复

- 在 DeckDialogBuilder 上补四个重载：setSingleChoiceItems（CharSequence[] 与 int 资源两种）、setMultiChoiceItems（同上），与 setItems 一样标记后走共用染色。
- 单选/多选行是 CheckedTextView，属于 TextView，现有 tint 逻辑直接覆盖，不用改 DialogListTint。
- 云白用 #172B3A 深字，深色用各主题原有浅色；行布局、项目 ID、选中状态、点击监听不变。

## 范围核对

- 只改 DeckDialogBuilder.java；MainActivity、所有 XML 资源、AndroidManifest 与 0.19.9 完全一致。
- 版本元数据升至 0.19.10 / 58。权限不变，满足覆盖安装的静态条件。

## 验证

- 列表染色主机行为测试通过：156 项检查（含新增 CheckedTextView 行染色与 6 个重载接线断言）；原调色板检查通过（111 项）；0.19.8/0.19.9 范围守卫通过；缓存清理、Aerial、桌面核验等主机检查通过。
- workflow_test 与 fake_adb_test 在 0.19.9 原版源码上同样失败（与本次改动无关的环境原因）；统一测试的 Java 编译步骤缺 json.jar，未能整套跑完。以上均不涉及本次改动的文件。
- APK v2/v3 签名通过；包名和证书与 0.19.9 一致，versionCode 从 57 升至 58。
- 当前没有连接的手机或 Android 模拟器，未执行实际覆盖安装与真机渲染检查。此处不以原型截图或模拟图片冒充实机证据。

## 待手机验收

1. 保留 0.19.9，打开本版 APK 选择更新，确认设置保留。
2. 切到云白，打开设置 → 界面与配色 → 选择颜色主题，确认 5 个主题名都是清晰深色；再切回深色确认浅字清晰。
3. 在电视应用更新扫描里打开批量核验更新（多选弹窗），确认云白下各行文字清晰。
