# 轻连 LinkDeck

轻连是一款 Android 手机端工具，用于连接支持 ADB 的 Android 电视或电视盒子。当前源码对应 **0.19.7（versionCode 55）**。手机需要 Android 8.0 及以上；电视兼容性取决于系统、厂商固件和授权情况。

## 配色

设置 → 界面与配色 → 颜色主题。可选择青蓝（默认）、松绿、紫罗兰、琥珀；选择后立即刷新当前页面并保存，重启后沿用。原有四套深色主题之外，新增「云白 · 浅色」。

## 功能

- 同网段候选地址发现、传统网络 ADB 与受支持设备的无线调试配对。
- ADB 遥控，以及手机具备红外发射器时的独立红外遥控；可管理设备地址。
- 电视应用列表、安装和已收录来源的更新检查，文件传输与命令输出。
- 一键清理：请求电视系统回收应用缓存，并比较清理前后的可用空间。
- Aerial TV 桌面管理、版本回退及诊断；实际权限和兼容性因设备而异。

一键清理依赖电视系统支持和权限，空间差值受系统其他活动影响；不会主动清除应用数据或卸载应用。USB OTG 直连尚未实现。真实手机和电视上的验证情况见本版说明。

## 工程结构

- `app/src/main/`：Android 手机应用源码、资源和清单。
- `helper/src/`：运行于兼容电视的只读应用元数据助手。
- `third_party/`：构建所需第三方组件及其源代码、二进制和许可资料。
- `tests/`：主机侧测试及部分 Android 运行测试源码。
- `build.py`：无需 Gradle 的构建脚本；`package_source.py` 用于项目内部源码打包。

此工程**不是 Gradle 工程**。需要 Python 3、Java 17 或更高版本、`keytool` 及可用网络；首次构建会下载 Android 36 平台、Android 构建工具、ECJ 和 D8 8.6.27。部分测试还需下载 JSON 依赖。D8 及特定第三方依赖有固定 SHA-256 检查，其他构建工具的获取方式见 `build.py`。

## 构建 APK

此仓库不包含签名私钥。先在安全的本机位置准备一个 Android 签名密钥；通过交互式提示设置密码，例如：

```bash
keytool -genkeypair -keystore /安全位置/your-key.jks -alias linkdeck -keyalg RSA -keysize 2048 -validity 10000
```

将下面两项设置为该密钥库的实际密码，使用系统支持的安全方式设置环境变量；不要将密码写入源码或提交到 GitHub。替换示例路径后运行：

```bash
# 预先在本机安全设置 LINKDECK_STORE_PASSWORD 与 LINKDECK_KEY_PASSWORD
python3 build.py --release-keystore /安全位置/your-key.jks --release-alias linkdeck
```

完成后 APK 位于 `dist/`。**新生成的密钥不能覆盖安装原有轻连 APK**；如需保留已有应用数据并覆盖升级，必须使用原签名密钥。密钥、密码、个人设备记录均不要上传到 GitHub。源码包不含原密钥；请单独保管现有原密钥备份。

运行主机侧检查（需准备脚本要求的工具和依赖）：

```bash
python3 tests/unified_test.py
python3 tests/focused_0192.py
python3 tests/cache_clean_test.py
```

这些测试不能替代手机、电视、TalkBack 和红外实机验证。少数使用官方 APK 的测试需要额外测试样本，缺失时会按脚本提示跳过。

## 上传 GitHub

解压源码包，新建仓库后上传解压出的 `LinkDeck` 文件夹**里面的文件和文件夹**，使 `README.md` 位于仓库根目录；不要仅把 ZIP 当作唯一源码文件上传。确保 `.gitignore` 一并上传，它通常是隐藏文件。不要上传 `signing/`、任何签名密钥、密码或本机 `.toolchain/`、`build/`、`dist/`。

GitHub 网页上传大量文件可能需要分批；也可在电脑上通过 Git 提交整个解压目录。公开仓库前请复查每个文件和提交历史，尤其不要把历史源码包或密钥一起上传。

## 许可

项目自身使用 Apache License 2.0，许可证文本见仓库根目录的 `LICENSE` 文件。第三方组件各自的许可和声明见 `app/src/main/assets/THIRD_PARTY_NOTICES.txt`、`app/src/main/assets/licenses/` 及 `third_party/`。
