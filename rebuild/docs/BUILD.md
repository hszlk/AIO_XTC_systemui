# XTCSystemUI 1.8.00 复刻工程 — 构建流水线说明

> 交付产物：
> - `rebuild\build\XTCSystemUI-signed.apk`（parity 变体，信息 100% 一致）
> - `rebuild\build\XTCSystemUI-signed-strict.apk`（strict 变体，云控/上报声明与占位类移除）
>
> 包名 `com.android.systemui`，versionCode 18000，versionName `1.8.00.Develop_AW_XtcSystemUI_J168_Gc23339b`，minSdk 27 / targetSdk 30。

---

## 1. 快速开始

```powershell
# 全量构建两个变体（javac → d8 → 打包 → zipalign → 签名 → 双份校验）
powershell -NoProfile -ExecutionPolicy Bypass -File D:\DEBUG\apk\sysui\rebuild\tools\build.ps1 -Clean -Variant both

# 只构建 parity 变体（默认，与原包信息 100% 一致）
powershell -NoProfile -ExecutionPolicy Bypass -File D:\DEBUG\apk\sysui\rebuild\tools\build.ps1 -Clean

# 只构建 strict 变体（云控/上报移除）
powershell -NoProfile -ExecutionPolicy Bypass -File D:\DEBUG\apk\sysui\rebuild\tools\build.ps1 -Variant strict

# 单独校验（parity / strict / both 三种）
powershell -NoProfile -ExecutionPolicy Bypass -File D:\DEBUG\apk\sysui\rebuild\tools\verify.ps1 `
  -OriginalApk D:\DEBUG\apk\sysui\XTCSystemUI.apk `
  -SignedApk    D:\DEBUG\apk\sysui\rebuild\build\XTCSystemUI-signed.apk `
  -StrictApk    D:\DEBUG\apk\sysui\rebuild\build\XTCSystemUI-signed-strict.apk `
  -Variant both
```

> 本机只有 Windows PowerShell 5.1（`powershell.exe`），**没有** `pwsh`（PowerShell 7）。所有脚本按 5.1 语法编写。
> 若当前会话已允许脚本执行，也可直接 `& D:\DEBUG\apk\sysui\rebuild\tools\build.ps1 ...`。

### build.ps1 参数

| 参数 | 作用 |
| --- | --- |
| `-Clean` | 先删除整个 `build\`（中间产物 + 产物），再从零构建；同时把 `-Clean` 传给 strict 变体脚本 |
| `-Variant parity\|strict\|both` | 构建哪些变体，默认 `parity` |
| `-StrictCloud` | `-Variant both` 的简写（保留旧调用习惯；显式 `-Variant` 优先） |
| `-SkipJava` | 复用上一次的 `build\obj\dex\classes.dex`，跳过 javac + d8（只改打包/签名时用） |
| `-SkipVerify` | 构建完不跑 `verify.ps1` |
| `-SkipNoCloudScan` | 跳过校验第 8 组「无云控门禁」（**不建议**；这是证明 strict 无云控残留的门禁） |

### 耗时（本机实测）

| 步骤 | 耗时 |
| --- | --- |
| parity 构建（javac 3.2s / d8 3.5s / 打包 6.4s / zipalign 0.1s / apksigner 1.6s） | ≈ 22 s |
| strict 构建（调用 Lead 的 `build_strict_cloud.ps1`：AXML 手术 + javac + d8 + 重打包 + 对齐 + 签名 + 扫描） | ≈ 11 s |
| 两份 verify（各 8 组检查，含 `aapt2 dump resources` 14,253 行 ×2 变体、dexdump、python 门禁） | ≈ 17 s |
| **`-Clean -Variant both` 端到端（实测 wall clock）** | **≈ 50 s** |

### 产物

| 文件 | 大小 | sha256 | 说明 |
| --- | --- | --- | --- |
| `build\XTCSystemUI-signed.apk` | **2,364,413 B (2.25 MB)** | `D8E62F3E…13E033C2` | parity：资源/清单/组件/权限与原包逐字节一致，仅多 `classes.dex` |
| `build\XTCSystemUI-signed-strict.apk` | **2,367,101 B (2.26 MB)** | `7A0FB614…C38EABEA` | strict：manifest 去掉 6 个云控声明，dex 不含 4 个云控占位类 |
| `build\XTCSystemUI-unsigned.apk` | 2,263,968 B | — | parity 签名前（zipalign 后） |
| `build\obj\classes.dex` | 192,344 B | — | parity 的 dex（50 个类） |
| `build\build-report.txt` / `build-report-strict.txt` | — | — | 两个变体的构建报告（大小、sha256、耗时） |
| `build\verify-report.txt` / `verify-report-strict.txt` | — | — | 两份校验报告（逐项 PASS/FAIL） |
| `build\no-cloud-parity.md` / `no-cloud-strict.md` | — | — | Lead 的 `check_nocloud.py` 原始扫描报告（UTF-8） |
| `build\no-cloud-gate-parity.json` / `-strict.json` | — | — | 门禁结构化结果（命中项 + 白名单判定） |
| `build\hidden-api-check.txt` | — | — | hidden API 静态检查结果 |

APK 条目数两个变体都是 **1132**（原包 1131 + `classes.dex`）。两者均远低于 3 MB 上限。

---

## 2. 构建策略：Mode A（两个变体共用）

### 2.1 为什么不是「用 aapt2 重新编译资源」

厂商 APK **没有 `classes.dex`**（代码被预优化进系统 vdex），但 `resources.arsc`、`res/**`、`assets/**`、`lib/**`、二进制 `AndroidManifest.xml` 和根目录资源文件全部完好。用 aapt2 从 jadx 解码树重建资源会遇到三个不可绕过的硬问题（Mode B 里全部踩到了，见第 10 节）：

1. **资源 ID 全部重编号**：原包 3738 个 entry 的 ID 来自厂商编译期，aapt2 重新 link 会生成全新 ID。
2. **私有框架资源无法解析**：原包引用厂商框架私有资源（`@android:style/TextAppearance.Watch.AppErrorDialog`、`@android:color/car_yellow_700`、`^attr-private.*` 等），公开 `android.jar` 里没有这些符号，aapt2 link 直接失败。
3. **9-patch 边框已被剥离**：aapt crunch 删掉了 113 个 `.9.png` 的 1px 边框，只留私有 `npTc` chunk，aapt2 无法还原。

### 2.2 Mode A 的做法

> **原包资源一个字节都不动，只替换 `classes.dex`（strict 变体额外做 manifest 手术），再对齐 + 签名。**

```
厂商 APK (资源骨架，只读)
   │  逐条目复制（本地头 + 压缩数据原样，data descriptor 规范化）
   │  剔除 META-INF/{MANIFEST.MF, CERT.SF, CERT.RSA}（旧 v1 残留）
   │  追加 classes.dex（DEFLATE）
   ▼
build\obj\skeleton.apk  ──zipalign -p -f 4──▶  build\XTCSystemUI-unsigned.apk
   ──apksigner（v1+v2+v3）──▶  build\XTCSystemUI-signed.apk
```

strict 变体在此基础上（全部由 Lead 的脚本完成，本工程只编排）：
替换 `AndroidManifest.xml` 为剥离 6 个云控声明后的二进制 manifest、dex 排除 4 个占位类源文件。

### 2.3 代码编译

| 项 | 值 |
| --- | --- |
| 源码主目录 | `D:\DEBUG\apk\sysui\src\main\java`（Lead 编写，构建脚本只读） |
| 源码备用目录 | `rebuild\app\src\main\java`（bootstrap 兜底；同名相对路径以主目录优先） |
| javac | JDK 21 `javac --release 8 -encoding UTF-8 -classpath <android-30 android.jar>` |
| d8 | `com.android.tools.r8.D8 --min-api 27 --release --lib android.jar` |
| parity 规模 | 27 个 `.java` → 46 个 `.class` → `classes.dex` 192,344 B（dex 内 50 个类） |

### 2.4 清单（AndroidManifest.xml）

parity 变体：`app\src\main\AndroidManifest.xml` 是从原包解出的**二进制 manifest**（17,628 B），不作为编译输入，而是可审计副本；`build.ps1` 每次构建校验它与原包内的 manifest **逐字节一致**，不一致直接失败。
strict 变体：该文件被 Lead 的 `axml_strip.py` 手术成 16,508 B（删 6 个声明，字符串池/资源映射不动）。

---

## 3. 变体与云控移除（parity / strict）

### 3.1 两个变体的差异

| 维度 | parity（默认） | strict |
| --- | --- | --- |
| 用途 | 与原包「信息 100% 一致」，作为行为对照/回归基线 | 发布用：移除云控/上报/网络能力 |
| manifest | 与原包**逐字节一致**（sha256 `856EA422…`） | 去掉 6 个声明，17,628 → 16,508 B（`473FEAFB…`） |
| dex 类数 | 50（含 4 个 no-op 云控占位类） | 46（4 个占位类源码不参与编译） |
| 组件 | 16/16 全部有实现 | 12/12（移除 1 service + 2 receiver + 1 provider） |
| `resources.arsc` | 与原包一致 | **同样与原包一致**（`FC35556C…`） |
| badging | 177 行与原包逐行相同 | 174 行：少 2 条 bigdata `uses-permission` + `other-receivers` |
| `aapt2 dump xmltree` | 230 行与原包相同 | 200 行 = 原包减去那 6 个声明的 30 行，**新增 0 行** |
| 云控静态残留 | 4 个 no-op 占位类（白名单放行，逐条列出） | **0**（类、字符串、`Ljava/net/*` 全 0） |
| 大小 | 2,364,413 B | 2,367,101 B |

> 为什么 strict 反而略大？strict 的 manifest 更小，但它重新打包时用的是 Python `zipfile` 重新压缩（与原包不同的 deflate 参数），压缩率略低；`resources.arsc`/资源内容仍与原件一致。差异 < 3 KB，不影响任何校验。

### 3.2 strict 变体怎么做的（Lead 已实现，本工程只编排）

`analysis\cloud-control\tools\build_strict_cloud.ps1`（**本工程不复制、不修改**，仅通过 `build.ps1 -Variant strict/both` 调用）：

1. `axml_strip.py`：二进制 AXML 手术，删除 6 个声明（元素 + 子节点），字符串池/资源映射保持原样；
2. 从暂存源码树剔除 4 个占位类：`CodeCoverageReceiver`、`MonitorStartReceiver`、`HttpDnsContentProvider`、`CrashIntentService`；
3. javac（`-source 8 -target 8`，classpath 仅 `android.jar`）；
4. d8（build-tools 36.0.0，`--min-api 27 --release`）；
5. `mk_strict_apk.py`：vendor APK 逐条目复制 + 替换 manifest + 追加 dex（压缩方式沿用原条目）；
6. zipalign + apksigner（同一 debug keystore）+ `check_nocloud.py` 扫描。

产物先落在 `analysis\cloud-control\out\XTCSystemUI-strict-cloud.apk`，随即由本工程发布为 `build\XTCSystemUI-signed-strict.apk`（sha256 与原产物一致）。

### 3.3 校验门禁：第 8 组「无云控/无网络」

`verify.ps1` 对**每个变体**都跑第 8 组，规则不同：

- **strict**：`check_nocloud.py` 退出码必须为 0（类黑名单 18 条前缀 + 字符串黑名单 34 条 + `Ljava/net/*` + HTTP/OKHttp/SSL 类），**无白名单**；再用 `tools\nocloud_gate.py` 复核：类命中 0、字符串命中 0、网络类 0。
- **parity**：允许上述 4 个 no-op 占位类带来的残留，但必须**逐条命中白名单**，任何白名单外的命中即 FAIL。当前实际情况：

| 类型 | 命中内容 | 放行理由 |
| --- | --- | --- |
| 类 | `Lcom/xtc/bigdata/collector/exception/CrashIntentService;` | no-op 占位（manifest 声明需要它存在） |
| 类 | `Lcom/xtc/bigdata/monitor/receiver/MonitorStartReceiver;` | no-op 占位 |
| 字符串 | `HttpDnsContentProvider.java` | 占位类的 SourceFile 属性 |
| 字符串 | `HttpDnsProvider` | 占位类里的日志 TAG |
| 字符串 | `...CrashIntentService;` / `...MonitorStartReceiver;` / `...HttpDnsContentProvider;` | 占位类的类描述符（被 `bigdata`/`HttpDns` 规则命中） |
| 字符串 | `httpdns provider disabled (cloud DNS removed); callers fall back to system resolver` | 占位类的日志文案（本身说明云控已移除） |

另有 2 个占位类（`CodeCoverageReceiver`、`HttpDnsContentProvider`）不在类黑名单前缀内，但前者被「4 个占位类必须存在」的组件校验覆盖、后者由上面的字符串命中覆盖。白名单以**精确字符串**匹配（不是前缀/正则），新增任何残留都会 FAIL。

> 白名单定义在 `tools\nocloud_gate.py` 的 `PARITY_ALLOWED_CLASSES` / `PARITY_ALLOWED_STRINGS`，每条都带 `reason`，并逐条打印在校验报告里。`nocloud_gate.py` 通过 import 复用 Lead 的 `check_nocloud.py` 的黑名单与 dex 解析器，避免两套规则漂移。

### 3.4 禁止删除清单（strict 变体必须保留，删了会破坏功能/信息一致性）

以下声明**不属于**云控/上报，strict 变体刻意保留（与 `analysis\cloud-control\CLOUD-REMOVAL.md` 一致）：

| 保留项 | 类型 | 保留理由 |
| --- | --- | --- |
| `moduleSwitch`（`<meta-data>`） | 功能开关数据 | 本地功能位，不联网；删了会影响 SystemUI 功能判定 |
| `funSwitch`（`<meta-data>`） | 功能开关数据 | 同上 |
| `supportScreenRecordAudio`（`<meta-data>`） | 能力声明 | 录屏能力开关 |
| `XtcStayFront`（`FlashLightSOSDialog` 的 `<meta-data>`） | 界面行为 | 手电筒 SOS 页面常驻前台行为 |
| `StatusBarProxyProvider`（provider） | 跨进程服务 | XTC 状态栏代理，本工程保留其实现（非云控） |
| `IKeyguard*`（aidl/接口引用） | 锁屏服务契约 | 锁屏/密钥保护相关接口，删除会破坏锁屏联动 |
| `SystemControlService`（service） | 本地指令服务 | 手电筒/录音等本地指令入口（`com.xtc.assistantapi.directive`） |
| `XtcNotificationListenerService`（service） | 通知监听 | 通知栏核心链路 |
| `PermissionListActivity`（activity） | 权限说明页 | 本地 UI |
| `PrivacyUriActivity`（activity） | 隐私说明页 | 本地 UI |
| `SelfStartActivity`（activity） | 自启动设置页 | 本地 UI |

判定原则：**只有「以云端配置/上报/网络为唯一目的」的声明才可删**；本地功能位、跨进程契约、UI 页面一律保留。

---

## 4. 目录结构

```
rebuild\
├─ tools\
│  ├─ build.ps1              ← 主构建脚本（parity / strict / both，一条命令）
│  ├─ verify.ps1             ← 验收脚本（每变体 8 组检查）
│  ├─ apk-pack.ps1           ← 自研 zip 重打包器（Mode A 核心，含 CRC32 自检）
│  ├─ zip_check.py           ← 逐条目 CRC-32 校验器（python zipfile）
│  ├─ nocloud_gate.py        ← 无云控门禁：复用 Lead 的规则 + 显式白名单
│  ├─ check-hidden-api.ps1   ← hidden API 静态检查
│  ├─ build-aapt2.ps1.bak    ← Mode B（aapt2 重建资源）备用脚本
│  ├─ stage-res.ps1          ← Mode B：资源 staging + 修复引擎
│  ├─ ninepatch.py           ← 研究用：解析 aapt crunch 后的 npTc/npOl chunk
│  └─ png_probe.py           ← 研究用：PNG chunk 探查
├─ respatch\patches.json     ← Mode B：字面补丁 + 类型化值规则 + 丢弃规则
├─ app\src\main\
│  ├─ AndroidManifest.xml            ← 原包二进制 manifest（审计/校验用）
│  ├─ AndroidManifest.aapt2-decoded.xml ← jadx 明文 manifest（仅 Mode B）
│  └─ java\                          ← bootstrap 兜底源码（Lead 目录缺文件时才用到）
├─ keystore\debug.keystore   ← 调试签名证书（口令见 7.3，构建时自动生成）
├─ build\                    ← 全部中间产物与最终产物
└─ docs\BUILD.md             ← 本文件
```

只读输入（**本工程绝不写入**）：`D:\DEBUG\apk\sysui\XTCSystemUI.apk`、`D:\DEBUG\apk\sysui\src\main\java`、`D:\DEBUG\apk\sysui\nx\resources`、`D:\DEBUG\apk\sysui\analysis\cloud-control\tools`。

---

## 5. 环境与工具链

| 组件 | 路径 / 版本 | 用途 |
| --- | --- | --- |
| OS | Windows + Windows PowerShell 5.1（无 pwsh 7） | 脚本宿主 |
| JDK | 21.0.12.1 LTS（`java`/`javac`/`keytool`） | 编译、d8、签名 |
| Android SDK | `C:\Users\10413\AppData\Local\Android\Sdk` | — |
| build-tools 30.0.3 | `aapt2.exe` / `zipalign.exe` / `dexdump.exe` + `lib\apksigner.jar` | 校验、对齐、签名 |
| build-tools 36.0.0 | `lib\d8.jar`（R8 8.x） | **仅**用于 dex 生成（原因见 6.2） |
| android.jar | `platforms\android-30\android.jar` | 唯一编译 classpath（framework-only） |
| Python | `py` 启动器，3.14.3 | strict 变体链路 + 无云控门禁 |

不使用 Gradle、不使用 apktool、不联网下载任何依赖。

---

## 6. 工具链坑与处理（复现必读）

### 6.1 SDK 的 `*.bat` 包装器在本机是坏的

`d8.bat`、`apksigner.bat` 开头 `call ...\tools\lib\find_java.bat` 定位 java。现代 SDK 已删除 `tools\lib`，于是 `if not defined java_exe goto :EOF` **静默退出且退出码 0**（`d8.bat` 曾“成功”却没产出 dex）。
处理：全部改用 `java -cp lib\d8.jar com.android.tools.r8.D8` / `java -jar lib\apksigner.jar`。

### 6.2 旧版 R8 在匿名内部类上崩溃

build-tools 30.0.3 / 34.0.0 的 R8 对 `SystemUIApplication$1.class`（字段初始化器里的匿名内部类）抛内部 NPE；36.0.0（R8 8.x）正常。处理：dex 固定用 36.0.0 的 d8.jar，其余工具仍用 30.0.3。

### 6.3 不能用 .NET `ZipArchive` Update 模式改 APK

会写坏本地头 size/crc，aapt2 报 `size/crc32 mismatch / failed opening zip`。处理：自研 `apk-pack.ps1` 做原始 zip 手术。

### 6.4 厂商 APK 是「流式 zip」：842/1131 条目用 data descriptor

本地头 crc/size=0 且 flag bit3=1。原样搬运后 aapt2 拒绝打开。处理：把中央目录的权威 crc/size 写回本地头、清 bit3、丢弃 descriptor，**压缩数据字节不动**。

### 6.5 PowerShell 5.1 数值/语法坑（含一个曾产出坏 APK 的严重坑）

| 坑 | 症状 | 处理 |
| --- | --- | --- |
| **`0xFFFFFFFF` 是 Int32 `-1`** | `[int64]-1` 符号扩展后 `-shr` 是**算术右移**，CRC-32 全错。**上一版 parity APK 的 `classes.dex` 条目 CRC 因此是错的**（`zipfile` 读不了，`aapt2`/`apksigner` 都不校验 CRC 所以没暴露） | CRC32 全程用十进制 `4294967295` + Int64 运算；`Get-Crc32` 内置标准向量自检（`"123456789"` → `0xCBF43926`），失败即中止构建；校验第 5 组新增逐条目 CRC 复查（`tools\zip_check.py`） |
| **变量名大小写不敏感** | `$strictReport`（列表）与 `$StrictReport`（路径）是同一变量，导致报告路径被覆盖为空、`Set-Content` 失败 | 路径变量改名 `$StrictReportPath` |
| `"$Var: ..."` | `变量引用无效。':' 后面的变量名称字符无效` | 写 `"$($Var): ..."` |
| `Test-Path $a -and (Test-Path $b)` | `找不到与参数名称"and"匹配的参数` | 加括号 |
| 数组取空区间 | `$cd[$p+46..$p+45]`（extraLen=0）会产出 2 个元素 | 显式判 `if ($extraLen -gt 0)` |
| `Write-Host` | 输出进不了调用方输出流，`2>&1` 抓不到 | 子脚本改用 `Write-Output` |
| python 中文输出乱码 | 控制台 GBK 解码 UTF-8 | 调 python 前后切 `[Console]::OutputEncoding = UTF8` + `PYTHONIOENCODING=utf-8` |

### 6.6 命令行长度

Mode B 中 `aapt2 link` 需要逐 `.flat` 传参，会超过 Windows 32k 上限；解法是把 `.flat` 硬链接成 `f0001.flat` 短名并在该目录内执行（`build-aapt2.ps1.bak` 已实现）。Mode A 不涉及。

---

## 7. 验收结果

### 7.1 parity 变体（`verify-report.txt`，8/8 组全 PASS）

| # | 检查 | 结果 | 数据 |
| --- | --- | --- | --- |
| 0 | APK 大小 ≤ 3 MB | PASS | 2,364,413 B ≤ 3,145,728 B |
| 1 | `aapt2 dump badging` 与原包一致 | PASS | **177 行逐行相同**（package/versionCode/versionName/minSdk/targetSdk/全部 uses-permission/组件/locale/native-code） |
| 2 | `aapt2 dump resources` 与原包一致 | PASS | **14,253 行逐行相同**（arsc 未改动） |
| 3 | `resources.arsc` / `AndroidManifest.xml` sha256 | PASS | arsc `FC35556C…`、manifest `856EA422…` 双方相同 |
| 4 | 条目集合 = 原包 + `classes.dex` | PASS | 1131 → 1132；丢失 0、多余 0（签名条目由 apksigner 重生成） |
| 5 | 打包 | PASS | `zipalign -c -v 4` 通过；arsc 保持 STORED（663,920 = 663,920）；每个共有条目压缩方式与原包一致（0 不一致）；**1132 条全部 CRC-32 可读**；dex 可被 dexdump 解析（038） |
| 6 | 签名 | PASS | apksigner verify 通过（v2/v3 true）；`--min-sdk-version 21` 下 v1 也 true（覆盖 Android 8.1 pre-v2 安装路径） |
| 7 | manifest 组件类 | PASS | **16/16** 组件类都在 dex 内（含 4 个 no-op 云控占位类） |
| 8 | 无云控门禁 | PASS | 命中 2 类 + 6 字符串，**全部在 4 个占位类白名单内**（逐条列于报告）；网络类 0 |

### 7.2 strict 变体（`verify-report-strict.txt`，8/8 组全 PASS）

| # | 检查 | 结果 | 数据 |
| --- | --- | --- | --- |
| 0 | APK 大小 ≤ 3 MB | PASS | 2,367,101 B |
| 1 | badging 差异仅限云控声明 | PASS | 177 → 174 行；删除 3（2 条 bigdata 权限 + `other-receivers`），**新增 0** |
| 2 | manifest 手术 | PASS | xmltree 230 → 200 行；**removed declarations 6/6**；删除 30 行全部属于那 6 个声明；**残差 diff 0**；6 个目标声明全部消失 |
| 3 | `resources.arsc` 与原包一致 | PASS | `FC35556C…` 双方相同；manifest 按设计不同（17,628 → 16,508 B） |
| 4 | 条目集合 = 原包 + `classes.dex` | PASS | 1131 → 1132，丢失 0、多余 0 |
| 5 | 打包 | PASS | 同 parity（zipalign / STORED / 压缩方式 / 1132 条 CRC / dex 解析） |
| 6 | 签名 | PASS | v2/v3 true；v1（`--min-sdk-version 21`）true |
| 7 | 组件类 | PASS | 12/12 保留组件在 dex 内；**4 个云控占位类全部不在 dex 内** |
| 8 | 无云控门禁 | PASS | `check_nocloud.py` 退出码 0；门禁：类 0、字符串 0、网络类 0（**无白名单**） |

### 7.3 签名与可复现性

| 项 | 值 |
| --- | --- |
| keystore | `rebuild\keystore\debug.keystore`（PKCS12，首次构建自动生成） |
| alias / 口令 | `androiddebugkey` / `android`、`android` |
| 证书 | `CN=Android Debug, O=Android, C=US`，RSA 2048，10000 天 |
| 方案 | v1 + v2 + v3（v4 `.idsig` 显式关闭，避免旁路文件） |
| 可复现性 | 同签钥同源码连续构建 parity 变体，SHA-256 完全相同（`D8E62F3E…`）；strict 产物与 Lead 原产物 sha256 一致（`7A0FB614…`） |

> `apksigner verify` 默认按 APK 自己的 minSdk 27 校验，v1 天然不在该路径（显示 false 属正常）；脚本额外用 `--min-sdk-version 21` 复核 v1。

---

## 8. hidden API 静态检查

`tools\check-hidden-api.ps1` 在 d8 后运行，结果写入 `build\hidden-api-check.txt` 并阻断构建：

- **源码扫描**：按黑名单 `android.os.SystemProperties`、`android.app.ActivityThread`、`android.os.ServiceManager(Native)`、`android.os.SystemService`、`android.view.WindowManagerGlobal`、`android.util.Slog`、`android.util.TimingsTraceLog`、`android.app.StatusBarManager`、`com.android.internal.*`；命中若在**字符串字面量内**判为「反射桥」（WARN），否则 FAIL。
- **dex 扫描**：`dexdump -d` 按类型描述符匹配（字符串常量不误报）。

当前结果：**PASS**，1 处反射桥 `SystemUIApplication.java:180 Class.forName("android.os.SystemProperties")`。编译期用的是公开 `android.jar`，任何**静态** hidden API 引用在 javac 阶段就会失败，本检查是第二道闸。

---

## 9. 已知限制 / 遗留问题

1. **必须平台签名才能安装（预期）**：manifest 带 `android:sharedUserId="android.uid.system"` + `persistent="true"`，设备生效需厂商平台密钥签名并通常放入 system 分区。debug.keystore 仅用于结构校验。
2. **反射桥需 hidden API 豁免**：`SystemProperties` 走反射，Android 9+ 需平台签名/系统镜像豁免；若要彻底去掉，可改读 `/proc` 或轮询 `sys.boot_completed`。
3. **无运行时验证**：本轮只到「编译通过 + 资源/清单一致 + 签名通过 + 静态云控门禁」，没有设备/模拟器行为对比（原包代码在 vdex）。
4. **strict 依赖 Lead 的脚本与 python**：`build.ps1 -Variant strict/both` 需要 `analysis\cloud-control\tools\build_strict_cloud.ps1` 与 `py`（3.14.3）存在；缺失时构建会明确报错。strict 变体的中间产物写在 `analysis\cloud-control\{build,out}`（由 Lead 的脚本负责），本工程只读取其产物并发布到 `build\`。
5. **apksigner 的 47 条 META-INF 警告（预期）**：原包 `META-INF/` 下有 47 个资源型条目（`androidx.*.version`、`*.kotlin_module`、`services/*` 等），v1 无法保护 META-INF 自身；为保证资源一致有意保留，不影响安装。
6. **strict 变体体积略大（+2.7 KB）**：`mk_strict_apk.py` 用 Python 重新压缩条目，与 aapt 的 deflate 参数不同，属预期。

---

## 10. 附：Mode B（用 aapt2 从解码资源重建）— 仅作备用/参考

脚本：`tools\build-aapt2.ps1.bak`（重命名为 `build-aapt2.ps1` 即可运行）；资源修复引擎：`tools\stage-res.ps1` + `respatch\patches.json`。

**状态**：链路已打通到 **aapt2 link 成功、生成 1.48 MB 未签名 APK、R.java 生成成功**，但因 2.1 节三条硬伤，**资源表必然偏离原包**，不作交付路径。保留价值：证明资源可编译、记录每处修复决策、将来若拿到厂商 `framework-res` 私有符号可复用。

### 10.1 结构性处理

| 处理 | 数量 | 原因 |
| --- | --- | --- |
| 剔除 `res\values\public.xml` | 1 | 解码出的 `<public>` ID 表与 aapt2 重新分配 ID 冲突 |
| `.9.png` → `.png` | 113 | aapt crunch 已剥离 1px 边框（真实分区数据在私有 `npTc` chunk，aapt2 无法读回）。改名**不改资源名**（`foo.9.png` 与 `foo.png` 都映射 `R.drawable.foo`），仅降级为普通位图 |

### 10.2 私有框架资源 → 本地近似值（字面补丁）

| 资源 | 处理 |
| --- | --- |
| `@android:dimen/indeterminate_progress_alpha_26` | → `@dimen/status_bar_icon_drawing_size` |
| `@android:dimen/lock_pattern_dot_line_width` | → `@dimen/status_bar_icon_drawing_size` |
| `@android:style/TextAppearance.Watch.AppErrorDialog`（2 处：`Theme.SystemUI` / `qs_base` 父样式） | → `@android:style/TextAppearance.Material` |
| `@android:color/car_yellow_700`（2 处） | → `#FFC0CA33` |
| `@android:color/datepicker_default_normal_text_color_material_dark` | → `#FFFFFFFF` |
| `<item name="android:^attr-private.*">`（4 处） | 整条删除（公开 SDK 无此 attr） |

### 10.3 类型化值还原（jadx 把 flags/enum 导成裸数字，aapt2 只认符号名）

| 属性 | 裸值 → 符号 | 命中 |
| --- | --- | --- |
| `android:layout_width` / `layout_height` | `-2` → `match_parent` | 2 / 7 |
| `android:layout_width` / `layout_height` | `-1` → `wrap_content` | 7 / 2 |
| `android:layout_gravity` | `0x1` → `center_horizontal` | 3 |
| `android:layout_gravity` | `0x800013` → `start|center_vertical` | 1 |
| `android:gravity` | `0x10` → `center_vertical`，`0x11` → `center` | 1 / 4 |
| `android:textStyle` | `0x0` → `normal` | 1 |
| `android:windowSoftInputMode` | `0x20` → `adjustPan` | 3 |
| `android:textAlignment` | `5` → `center`，`6` → `viewStart` | 3 / 1 |
| `android:ellipsize` | `3` → `end` | 3 |
| `android:breakStrategy` | `0` → `simple`，`1` → `high_quality` | 1 / 1 |
| `android:hyphenationFrequency` | `0` → `none` | 2 |

所有规则带**最小命中数断言**：上游解码一旦变化，构建失败而不是静默产出不同 APK。

### 10.4 Mode A vs Mode B

| 维度 | Mode A（默认） | Mode B（备用） |
| --- | --- | --- |
| 资源来源 | 原包字节拷贝 | jadx 解码树 + aapt2 重编译 |
| 资源 ID | 与原包一致 | 全部重编号 |
| 资源表 dump | 与原包逐行一致 | 必然不同 |
| 私有框架资源 | 原样保留 | 只能改近似值 |
| 9-patch | 原样（含 crunch 后形态） | 降级为普通位图 |
| 是否可用 | **是** | 仅参考 |

---

## 11. 变更记录

| 日期 | 变更 |
| --- | --- |
| 2026-11-01 | 初版：Mode B（aapt2 重建资源）打通至 link 成功，完成 28 类资源修复；因资源表必然偏离原包，转为备用 |
| 2026-11-01 | 切换为 Mode A（原包资源骨架 + classes.dex）：自研 zip 重打包器、data descriptor 规范化、d8 版本处理、签名与 7 组验收全绿；接入 Lead 的 `sysui\src\main\java`；新增 hidden API 检查 |
| 2026-11-01 | 修复 `classes.dex` 条目 CRC-32 错误（PowerShell `0xFFFFFFFF`→Int32 −1 导致算术右移；上一版产物 CRC 不可读）：CRC32 改用十进制掩码 + 标准向量自检，新增 `zip_check.py` 逐条目 CRC 校验（第 5 组），parity 产物 sha256 随之更新 |
| 2026-11-01 | 新增 strict 云控移除变体：`build.ps1 -Variant parity\|strict\|both`（`-StrictCloud` 为 `both` 简写）、`-SkipNoCloudScan`；编排 Lead 的 `build_strict_cloud.ps1`（不复制实现）；新增 `tools\nocloud_gate.py` 门禁 + verify 第 8 组（parity 白名单逐条列出 / strict 必须 0 命中）；`verify.ps1` 支持变体并新增 strict 专项校验（xmltree 减法推导、badging delta、12/4 组件类、arsc 一致）；新增 `build-report-strict.txt` / `verify-report-strict.txt` |
