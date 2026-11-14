# XTCSystemUI · 手表 SystemUI 手写复刻工程

> 面向 XTC 系列手表（W4 = Android 8.1 / SDK 27，W5 = Android 11 / SDK 30）的
> `com.android.systemui` **干净重写**工程：保留全部 manifest 组件、资源与业务功能，
> 精准移除云控、上报与权限探测逻辑，并以 Magisk 模块形式交付。

- **包名**：`com.android.systemui`
- **versionCode**：`18000`
- **versionName**：`1.8.00.Develop_AW_XtcSystemUI_J168_Gc23339b`
- **minSdk / targetSdk**：27 / 30
- **模块 id**：`XTCSystemUI_HS`（Magisk 模块，作者 `hszlk`）
- **源码规模**：91 个 Java 文件 / 约 2.0 万行，**纯 framework**（不打包 androidx / Kotlin / okhttp / Rx / protobuf）
- **产物**：`classes.dex` 约 267 个类，APK ≈ **2.47 MB**（目标 ≤ 3 MB）

---

## 1. 这个工程做了什么

厂商的 SystemUI 是一个高度定制的系统应用，代码被 dexpreopt 进了系统分区的 `vdex`。
本工程**不修改原包的资源**，而是用同样的类名/组件名/资源 ID **重新手写实现**全部业务逻辑，
再把自己编译出的 `classes.dex` 塞回原资源骨架里（见第 3 节 Mode A）。

已实现的主要模块：

| 模块 | 内容 |
|---|---|
| 核心运行时 | `SystemUIApplication` / `SystemUIService` / `SystemBars` / `SystemUIFactory` / `Dependency` |
| 状态栏 | `XtcStatusBarView`：信号、飞行、移动数据上下行、漫游、WiFi、热点、IM 在线、下载、HD 语音、时间、GPS、蓝牙、耳机、闹钟、电池 + 长按电池卡片 |
| 下拉面板 | `XTCPanelView`（展开/收起状态机、毛玻璃背景、滚动托管） |
| 通知 | `XtcNotificationListenerService` → 通知模型/渲染 |
| 快捷开关 | `QSPanel` / `GlassTileView` / `QsToggles`：8+4 个圆球开关、拖拽换位、编辑模式（间距展开 + 球下功能文字） |
| 音量/亮度 | `VerticalSeekBar`（长按放大，音量三流：媒体/系统/闹钟并带标注） |
| 音乐卡 | `MusicCardView` / `NowPlayingController`（MediaSession + 通知协议 + 音频栈兜底三级识别） |
| 锁屏 | `KeyguardController` / `IKeyguardInterface` / `IKeyguardCallbackInterface` |
| 录屏 | `RecordingService` / `RecordingFloatController` / 自写 `AvMuxer`（不依赖 mp4parser） |
| 手电筒 SOS | `FlashLightActivity` / `FlashLightSOSDialog`（`CameraManager.setTorchMode`） |
| 蓝牙通信 | 手写 protobuf-lite 编解码（不引入 protobuf 库） |
| 语音助手服务 | `SystemControlService`（FlashLight / VideoRecord directive） |
| 其他 | 开机标记文件、省电提示、长续航提示、锁屏充电界面适配等 |

### 移除的内容（去云控 / 去上报）

- 云端配置下发、埋点统计、`bigdata` 上报链路
- HttpDns / 自定义 DNS 云解析
- 运行时权限探测与自启动检查的**上报**部分（本地功能保留）
- 所有联网请求，除音乐封面图（`http(s)` 封面 URL，纯显示用途）

> 具体逐条比对记录见 [`analysis/DUAL-LOGIC.md`](analysis/DUAL-LOGIC.md) 与
> [`analysis/cloud-control/CLOUD-REMOVAL.md`](analysis/cloud-control/CLOUD-REMOVAL.md)。

---

## 2. 目录结构

```
sysui/
├── src/main/java/                  # 唯一手写源码树（framework-only）
│   ├── com/android/systemui/       # SystemUI 本体：状态栏/QS/录屏/媒体/锁屏/手电筒…
│   ├── com/xtc/                    # manifest/资源要求且业务需要的少量 XTC 组件
│   └── androidx/core/              # 自实现的两个 manifest 引用类（CoreComponentFactory / FileProvider）
├── rebuild/
│   ├── tools/                      # 构建与验证流水线（PowerShell 5.1 + Python）
│   ├── docs/BUILD.md               # 构建流水线详解（Mode A/B、参数、耗时、产物 sha256）
│   ├── respatch/patches.json       # 资源补丁清单
│   └── keystore/debug.keystore     # 本地调试签名（不入库，见 .gitignore）
├── analysis/
│   ├── PLAN.md                     # 工程实施计划与硬约束
│   ├── DUAL-LOGIC.md               # 【核心】逐模块「原逻辑 vs 改造后逻辑」对照账本
│   ├── modules/                    # 类清单 / 可达性分析
│   ├── res/INVENTORY.md            # 资源与组件清单基线
│   ├── risk/                       # 风险点清除清单（云控/上报/权限探测）
│   └── cloud-control/              # 去云控方案与验证报告
├── nx/                             # 原包资源/sources 解包结果（不入库）
├── work/                           # 逆向工作区：dex/vdex/jadx 反编译（不入库，数百 MB）
└── XTCSystemUI.apk                 # 厂商原包（不入库，需自备，见第 3 节）
```

---

## 3. 构建

### 3.1 环境要求

| 依赖 | 版本 / 说明 |
|---|---|
| Windows | 10/11，**Windows PowerShell 5.1**（脚本按 5.1 语法编写，未适配 pwsh 7） |
| JDK | 17+（提供 `javac` / `java`，用 `--release 8` 编译） |
| Android SDK | `build-tools;30.0.3`（`zipalign`、`apksigner`）+ `platforms;android-30`（`android.jar`）+ `build-tools;36.0.0`（R8 混淆变体） |
| Python | 3.x（`nocloud_gate.py` / `zip_check.py` 等校验脚本） |
| Gradle | **不需要**（纯 javac + d8/R8 + apksigner） |

> 脚本里的 SDK 路径默认是 `C:\Users\<user>\AppData\Local\Android\Sdk`，
> 请按 [`rebuild/tools/build.ps1`](rebuild/tools/build.ps1) 顶部变量改成你自己的路径。

### 3.2 为什么是 Mode A（只换 dex）

厂商 APK **没有 `classes.dex`**（代码被预优化进系统 `vdex`），但 `resources.arsc`、`res/**`、
`assets/**`、`lib/**`、二进制 `AndroidManifest.xml` 全部完好。若用 aapt2 重新编译资源会遇到
三个无法绕过的硬问题：

1. **资源 ID 全部重编号**：原包 3738 个 entry 的 ID 来自厂商编译期，重新 link 会生成全新 ID；
2. **私有框架资源无法解析**：原包引用了厂商框架私有资源（如 `@android:style/TextAppearance.Watch.AppErrorDialog`），公开 `android.jar` 里没有这些符号；
3. **9-patch 边框已被剥离**：aapt crunch 删掉了 113 个 `.9.png` 的 1px 边框，无法还原。

所以采用 **Mode A**：

```
厂商 APK（资源骨架，只读）
        │
        ├── javac（对着 android.jar 编译 src/main/java）
        ├── d8 或 R8（min-api 27 → classes.dex）
        ├── apk-pack.ps1（逐 entry 复制原包，仅替换/追加 classes.dex）
        ├── zipalign -p 4 -f
        └── apksigner（v1 + v2 + v3 签名）
```

最终 APK 与原包**资源表、组件、权限、locale 逐字节一致**，只有 `classes.dex` 不同。

### 3.3 使用前：放置厂商原包

由于版权原因，仓库**不含**厂商 APK。请把你自己的原包放到：

```
sysui/XTCSystemUI.apk
```

构建脚本以它为资源骨架。缺失时 `build.ps1` 会直接报错退出。

### 3.4 常用命令

```powershell
cd D:\DEBUG\apk\sysui

# 默认：parity 变体（d8，信息与原包 100% 一致，仅 dex 不同）
powershell -NoProfile -ExecutionPolicy Bypass -File rebuild\tools\build.ps1 -Clean

# 同时构建 strict 变体（移除云控声明与占位类）
powershell -NoProfile -ExecutionPolicy Bypass -File rebuild\tools\build.ps1 -Clean -Variant both

# R8 混淆「加密」变体（发布用）
powershell -NoProfile -ExecutionPolicy Bypass -File rebuild\tools\build-obfuscated.ps1
```

### 3.5 产物

| 文件 | 说明 |
|---|---|
| `rebuild/build/XTCSystemUI-signed.apk` | parity 变体（资源逐字节一致，仅 dex 不同） |
| `rebuild/build/XTCSystemUI-signed-strict.apk` | strict 变体（云控声明/占位类移除） |
| `rebuild/build/XTCSystemUI-hardened.apk` | **R8 混淆变体**（类名混淆，发布/模块使用） |
| `rebuild/build/verify-report.txt` | 8 组校验报告（签名 / 资源 / 组件 / 去云控门禁 …） |
| `rebuild/build/no-cloud-*.md` | 去云控静态扫描结果 |

> `verify.ps1` 共 8 组检查：签名一致性、资源表对齐、manifest 组件类是否全部存在于 dex、
> 云控类/字符串残留门禁、网络类门禁等。全部 PASS 才算构建成功。

---

## 4. Magisk 模块打包

发布形态是 Magisk 模块（模块 id `XTCSystemUI_HS`，双机型同包）：

```powershell
python rebuild\tools\build_module_sysui_shell.py
# 输出：rebuild\build\XTCSystemUI-v82-sysui-shell.zip
```

模块结构沿用厂商原有「双层壳」：

```
XTCSystemUI-v82-sysui-shell.zip
├── module.prop                 # id / name / author / versionCode / description
├── customize.sh                # 按 ro.build.version.sdk 分流
├── version.txt
├── META-INF/com/google/android/{update-binary,updater-script}
├── 4100.zip                    # W4 载荷：APK + libmmkv + libxtcSecurity
└── 5100.zip                    # W5 载荷：APK + 上述两库 + screenrecord + audiopolicy + feature xml
```

- **SDK 27（W4）**：只解压 `4100.zip`，不带录音载荷（8.1 无 `AudioPlaybackCapture`，
  且该机型音频策略没有 `remote_submix` 回环设备，录屏为无声视频）
- **SDK 30（W5）**：解压 `5100.zip`，额外恢复录屏内录三件套
- 脚本会**移除内层 `customize.sh`**（否则解压时会覆盖正在执行的外层脚本）
- 脚本**不执行 `pm uninstall com.android.systemui`**：debug 签名无法重新加入
  `sharedUserId android.uid.system`，误删会导致手表彻底失去 SystemUI（踩过的坑）

安装：Magisk App 里刷入 zip，或

```bash
adb push XTCSystemUI-v82-sysui-shell.zip /sdcard/
adb shell su -c "magisk --install-module /sdcard/XTCSystemUI-v82-sysui-shell.zip"
```

> 替换系统 SystemUI 需要**平台签名校验豁免**（本机环境已具备 corepatch / 签名豁免）。
> 没有该环境时只能用 parity 变体 + 原签名方式处理，或自行解决签名问题。

---

## 5. 文档索引

| 文档 | 内容 |
|---|---|
| [`analysis/PLAN.md`](analysis/PLAN.md) | 工程实施计划、硬约束、模块地图 M0–M12 |
| [`analysis/DUAL-LOGIC.md`](analysis/DUAL-LOGIC.md) | **核心账本**：每个模块「原 APK 逻辑」vs「改造后逻辑」+ 差异 + 回归验证记录（W1–W54） |
| [`analysis/risk/RISK-MAP.md`](analysis/risk/RISK-MAP.md) | 风险点总表（云控/上报/权限探测） |
| [`analysis/risk/ENDPOINTS.md`](analysis/risk/ENDPOINTS.md) | 原包网络端点清单 |
| [`analysis/risk/MODULES.md`](analysis/risk/MODULES.md) | 各业务模块风险拆解 |
| [`analysis/cloud-control/CLOUD-REMOVAL.md`](analysis/cloud-control/CLOUD-REMOVAL.md) | 去云控方案与门禁验证 |
| [`analysis/res/INVENTORY.md`](analysis/res/INVENTORY.md) | 资源 / 组件清单基线（3738 entry、304 locale、16 组件） |
| [`analysis/modules/INDEX.md`](analysis/modules/INDEX.md) | 类清单与可达性分析索引 |
| [`rebuild/docs/BUILD.md`](rebuild/docs/BUILD.md) | 构建流水线详解（含 Mode B 备选方案与踩坑记录） |

---

## 6. 已知限制

1. **W4（Android 8.1）无法录屏内录**：系统无 `AudioPlaybackCapture`，机型音频策略也无
   `remote_submix` 回环设备，只能录无声视频；厂商原包同样无录音载荷。
2. **部分 App 的音乐卡识别受限**：卡片优先走 `MediaSession`；没有发布 session 的播放器
   （如 `com.tme.karaokewatch` 全民K歌）在部分 ROM 上因系统强制清空
   `enabled_notification_listeners`，只能退化为「应用名 + 正在播放」，拿不到歌名/封面。
3. **构建脚本含本机绝对路径**：`build.ps1`、`build_module_sysui_shell.py` 里写死了
   `D:\DEBUG\apk\sysui` 与模块壳 zip 路径，复刻时请自行替换。
4. **PowerShell 5.1 专用**：脚本未适配 `pwsh` 7。

---

## 7. 免责声明

- 本仓库**仅用于个人学习、系统修复与逆向工程研究**，不提供任何厂商二进制文件
  （`XTCSystemUI.apk`、`vdex`、反编译产物均在 `.gitignore` 中排除）。
- 使用本工程产出的 APK / 模块需要**自行解决平台签名**，替换系统分区应用有**变砖风险**，
  请务必先备份 `boot` / 系统分区与 `/data/adb/modules`。
- 请勿将本工程用于商业分发、绕过付费或任何违反当地法律法规与厂商服务条款的用途。
- 所有商标、原始代码与资源版权归原厂商所有；本仓库只包含重新编写的实现代码与其分析记录。

---

## 8. 许可证

本项目以 **GNU Affero General Public License v3.0（AGPL-3.0）** 发布，全文见 [`LICENSE`](LICENSE)。

> AGPL-3.0 = GPL-3.0 + 一条网络条款：若把修改版作为网络服务对外提供，也必须向使用者开放源码。
> 复制、修改、分发请保留版权声明与许可文本；本工程不提供任何厂商二进制，见第 7 节。

---

## 9. 贡献

欢迎提交 Issue / PR，尤其是：

- 其他机型（W4/W5 以外）的适配与兼容性反馈
- 已移除云控逻辑的补充核查（对照 `analysis/risk/`）
- 新的圆球开关 / 面板交互体验改进

提交前请确保：

1. `build.ps1` 与 `verify.ps1` 全部 PASS；
2. 新的行为差异同步写入 `analysis/DUAL-LOGIC.md`；
3. 不引入新的第三方运行时依赖（保持 framework-only 与 ≤3 MB）。
