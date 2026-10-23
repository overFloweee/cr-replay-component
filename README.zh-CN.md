# CR Replay Component

[English](README.md) | **简体中文**

CR Replay Component 是一个面向 Android 的《皇室战争》本地回放组件。它将回放输入校验、规则选择、资源准备、原生核心接入和播放进程管理放在一个独立项目中，并提供最小 Android 示例宿主与本地构建工具。

战斗运算与画面渲染由外部提供的游戏核心执行。本项目提供接入代码和构建方法；完整构建还需要自行准备与登记摘要一致的游戏材料。

## 组件能力

- **输入校验与规则选择**：检查回放结构、版本标识和支持的命令，按内置登记选择规则；未知版本或冲突输入会被拒绝。
- **资源准备**：校验完整资源包与包内文件的摘要、大小和路径，将所需内容准备到应用私有目录。
- **独立播放进程**：每次播放使用独立运行目录和启动票据，在 `:replay` 进程中启动游戏宿主，记录结果并回收已退出的运行目录。
- **原生核心桥接**：通过 JNI 接入固定核心，提交回放、观察播放状态，并计算终局皇冠数。
- **本地构建与检查**：从外部材料恢复登记核心、生成规则与视觉包、构建示例 APK，并提供使用合成数据的主机测试。

示例宿主只提供本地播放入口，没有账号、云端归档、资源下载或远程目录服务。Android 接入源码位于 `android/`，当前通过源码编译集成，未提供 Maven 或 AAR 包。

## 项目结构

| 路径 | 内容 |
| --- | --- |
| `android/cn/crhistory/player/` | Java 接入层、资源校验、运行目录管理和本地示例页面 |
| `native/replay_bridge.cpp` | JNI 桥、固定核心适配、状态观察和终局计算 |
| `example/AndroidManifest.xml` | 示例应用与独立播放进程的声明 |
| `tools/` | 核心恢复、宿主适配、规则与视觉包生成、资源归档和 APK 构建工具 |
| `metadata/` | 核心身份、材料摘要、规则登记和显式版本别名 |
| `tests/` | Java、C++ 和 Python 主机检查及合成测试数据生成器 |
| `docs/` | 材料清单、兼容范围、代码来源和验证记录 |
| `licenses/` | 第三方来源与许可说明 |

## 构建示例应用

### 环境要求

- Python 3.9+。不要启用 `-O`、`-OO` 或 `PYTHONOPTIMIZE`，核心恢复依赖完整性断言。
- JDK 17+，包含 `java`、`javac` 和 `keytool`。
- Android SDK：Platform 36、Build Tools 36.0.0、NDK 27.1.12297006。
- 外部安装的 Apktool 3.0.3 JAR。
- 主机测试另需 `clang++`；示例应用的运行目标为 Android 11 / API 30+、`arm64-v8a`。

构建脚本包含 macOS 和 Linux 的工具链路径选择；当前完整构建记录来自 macOS。

### 准备外部材料

按[材料清单](docs/materials.md)准备精确版本的 APK、运行时快照、符号与导出信息、历史规则材料，以及单独提供的动画 JSON。核心与内容输入的身份登记在 [`metadata/supported_engine.json`](metadata/supported_engine.json)。

**只有游戏 APK 无法完成构建。** 本仓库不提供这些材料的下载或完整采集流程。材料缺失或摘要不符时，构建会失败。

### 执行构建

```sh
python3 tools/build.py \
  --input-root /path/to/local-input-root \
  --visual-source /path/to/asm_hero_electrowizard_battle.json \
  --apktool /path/to/apktool-3.0.3.jar \
  --sdk /path/to/android-sdk
```

`--input-root` 指向包含 `materials/` 的本地目录，具体层级见材料清单。构建会验证登记输入、恢复核心、生成资源与规则包、编译 Java 和原生桥、打包并签名 APK，最后核对签名和嵌入包内容。

主要输出位于 `build/local/`：

| 文件 | 用途 |
| --- | --- |
| `ReplayComponent.apk` | 使用本地开发密钥签名的示例应用 |
| `resources.pack` | 与本次构建配套的外部资源包 |
| `resource-registration.json` | 本次资源包的摘要、大小与运行时体积登记 |
| `replay-rules.zip`、`replay-visuals.zip` | 生成的规则包与视觉包 |
| `build-proof.json` | 构建源码摘要、产物身份和验证状态 |

开发密钥首次构建时生成在 `.local/`，仅用于本地开发。`build/` 和 `.local/` 均被 Git 忽略。使用本次生成的 APK 与资源包配对运行；`metadata/public_resources.json` 中保留的历史登记不代表新生成资源包的身份。

构建脚本会拒绝复用已有的 `build/local/decoded` 目录。重新构建前，先保留所需日志与证据，再清理可重建的解包目录和打包中间产物。

## 播放本地回放

准备一台符合要求的 Android 设备或已有模拟器，并将 `SERIAL` 替换为明确的 ADB 设备序列号：

```sh
adb -s SERIAL install -r build/local/ReplayComponent.apk
adb -s SERIAL shell am start -n org.crreplay.example/cn.crhistory.player.ExampleActivity
adb -s SERIAL shell mkdir -p /sdcard/Android/data/org.crreplay.example/files
adb -s SERIAL push build/local/resources.pack /sdcard/Android/data/org.crreplay.example/files/resources.pack
adb -s SERIAL push /path/to/replay.json /sdcard/Android/data/org.crreplay.example/files/replay.json
```

在示例页面点击 **Play local replay**。应用会导入并校验资源包，保存输入副本、选择规则，然后启动独立播放进程。回放文件上限为 16 MiB；真实回放文件由使用者自行提供，不随仓库分发。

返回后再次启动播放时，应用会为已退出的运行记录结果摘要并回收目录。摘要位于应用私有目录 `files/replay/evidence/`；完整遥测与结果文件保留在运行目录中，若需分析，应在下一次回收前导出。

示例不会生成独立的预期比分。验证真实回放时，需要另行提供可信的比赛结果与过程对照。

## 运行主机测试

```sh
ANDROID_HOME=/path/to/android-sdk python3 tests/check.py
```

测试使用合成数据，输出位于 `build/local/tests/`。首次运行会下载仅用于测试的 `org.json:json:20180813`，使用前核对固定 SHA-256。

覆盖内容包括版本映射与拒绝条件、规则和视觉包异常清理、资源路径与摘要检查、实际展开流的 2 GiB 边界、归档稳定性、12 个原生终局计算样例，以及 Python 优化模式拒绝。

完整构建与主机测试已有通过记录，见[验证记录](docs/review.md)。独立示例宿主的 Android 实际播放、返回与重启生命周期仍待设备验证。

## 兼容范围与限制

当前接入固定的国服 38.4.2 登记核心，包含 38.4.3、38.4.5、38.4.6、38.4.8、38.4.9、38.4.10、38.4.11 七份规则快照及少量显式别名。规则快照的数量不代表支持同等数量的独立游戏核心，也不代表这些版本的所有模式均已验证。

版本登记与合成测试只证明相应的选择和拒绝逻辑。部分登记仍标记为未就绪；未知组合会被拒绝。具体条件见[兼容说明](docs/compatibility.md)。

资源准备会校验完整归档和包内文件，但尚未证明播放期间所有材料都不受修改影响。终局皇冠数相同也不能证明战斗时序、对象轨迹或画面完全一致。

原生适配包含特定核心的地址、结构偏移、短指令签名和 Hook；构建工具会生成或修改外部客户端的 smali。替换游戏版本时，必须重新核对核心身份与适配条件。

## 许可证与来源

本项目源码采用 [Apache License 2.0](LICENSE)，允许在遵守许可条件下使用、修改和商业使用。相关第三方归属与许可见 [NOTICE](NOTICE)、[来源说明](docs/provenance.md)及 `licenses/`。测试依赖与外部构建工具适用各自许可。

游戏 APK、核心二进制、资源、运行时转储、真实回放、签名密钥和生成产物不包含在源码仓库中。源码许可不授予这些材料或修改后客户端的使用与分发权；使用者需自行确认所需授权。

本项目为非官方项目，与 Supercell 或腾讯没有隶属、合作或背书关系。具体开源内容见[源码范围](docs/public-scope.md)。本次公开仅包含源码、工具和文档；游戏材料及生成产物不随仓库分发。
