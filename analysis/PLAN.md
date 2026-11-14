# XTCSystemUI 1.8.00 复刻与去云控/去上报/去权限检测 — 项目实施计划（Lead 维护）

> 目标 APK：`D:\DEBUG\apk\sysui\XTCSystemUI.apk`
> package `com.android.systemui`，versionCode `18000`，versionName `1.8.00.Develop_AW_XtcSystemUI_J168_Gc23339b`，minSdk 27 / targetSdk 30，`sharedUserId=android.uid.system`，`persistent=true`，运行于 Android 8.1（API 27）手表系统分区（厂商 dexpreopt 已剥离 classes.dex）。
> 交付根目录：`D:\DEBUG\apk\sysui\`（src / rebuild / analysis / work）。

## 0. 硬约束（用户规则）
1. 纯手写复刻全部功能，不直接搬运源文件代码（反编译结果只作为「原逻辑」证据）。
2. 精准移除所有云控、上报、权限检测逻辑；其余业务 100% 保留。
3. 每处理完一个模块同步写【原APK运行逻辑】与【改造后运行逻辑】差异对照（见 `analysis\DUAL-LOGIC.md`）。
4. UI 话术改造本阶段不做（用户指令：先复刻+去风险逻辑，UI 重构后置）。
5. 轻量化：最终 APK ≤ 3MB，framework-only（不打包 androidx / kotlin / okhttp / rx / protobuf 等第三方库）。
6. 每轮回复末尾输出 ≤350 字【本地断点快照】。

## 1. 已确认的技术事实（逆向结论）
| 项 | 结论 | 证据 |
|---|---|---|
| 目标 APK 无 classes.dex | 厂商 dexpreopt 剥离，代码在系统分区 `oat/arm/XTCSystemUI.vdex` | `work\root0\priv-app\XTCSystemUI\` |
| 代码版本 | 1.8.00 与同系列 1.7.50（`D:\DEBUG\_sysui.apk`，含 dex）几乎同构：类集合 5136/5136 完全同名、方法数 71446 vs 71445 | `work\tools\cmp_jadx.py` 结果 |
| 1.8.00 反编译 | vdex 010 unquicken 后 jadx 全量反编译成功（5136 java，108 个 jadx 级错误，与 1.7.50 的 109 个同量级，无未知指令） | `work\jadx18b\sources` |
| R 资源 ID | 原 dex 的 R 类被混淆为 `com.android.systemui.m`（含 16 类型嵌套类 + 139 个 styleable int[]）；据此 + `res\values\public.xml` 生成干净 `R.java`，3738 个 ID 与原 arsc 完全一致，javac 通过 | `work\tools\gen_r.py`、`src\main\java\com\android\systemui\R.java` |
| 资源策略（Mode A） | 不重编译资源：新 APK 复用原 `resources.arsc` / `res/` / `assets/` / 二进制 `AndroidManifest.xml` / `lib/`，只替换 `classes.dex`。资源表 3738 entry、104 locale、16 组件天然与原包一致 | `rebuild\tools\build.ps1`（build-eng） |
| 构建工具链 | JDK21 + SDK build-tools 30.0.3（aapt2/d8/apksigner/zipalign）+ platforms/android-30/android.jar；无 gradle/apktool | 已验证 |
| Manifest 地雷 | 必须原样保留：`android.Manifest.permission#WRITE_SECURE_SETTINGS`（名含#）、`android.perminssion.WRITE_MEDIA_STORAGE`（拼写错误）、`appComponentFactory=androidx.core.app.CoreComponentFactory` | `analysis\res\INVENTORY.md` |
| 平台签名 | 设备上以平台签名校验；重签名需配合设备 corepatch/签名豁免（用户环境已具备），本工程只负责产出正确签名的 APK | 用户环境 |

## 2. 工程结构
```
D:\DEBUG\apk\sysui\
├─ src\main\java\                      ← 唯一手写源码（framework-only，public API ≤27）
│  ├─ com\android\systemui\            ← SystemUI 本体（Application/服务/状态栏/QS/录屏/手电…）
│  ├─ com\xtc\...                      ← 仅保留 manifest/资源要求且业务需要的少量组件（其余 XTC SDK 不实现）
│  └─ androidx\core\...                ← 自实现的两个 manifest 引用类（CoreComponentFactory / FileProvider）
├─ rebuild\                            ← 构建流水线（build.ps1 / verify.ps1 / keystore / docs / 产物）
├─ analysis\res\INVENTORY.md           ← 资源与组件信息基线（res-inventory 交付）
├─ analysis\risk\                      ← 云控/上报/权限检测风险点清单（risk-scan 交付）
├─ analysis\DUAL-LOGIC.md              ← 双逻辑对照登记表（每模块一节，随波次更新）
├─ analysis\PLAN.md                    ← 本文件
└─ work\                               ← 逆向工作区（vdex/dex/jadx、比对脚本、校准产物）
```

## 3. 模块地图与依赖（复刻范围）
M0 构建基座（build.ps1/verify.ps1/签名/R.java）—— build-eng
M1 核心运行时：SystemUIApplication、SystemUIService、SystemBars、SystemUIFactory、Dependency、SystemUI 基类、SysUiServiceProvider
M2 状态栏与下拉面板：StatusBar、PhoneStatusBarView、PanelView、PanelBar、statusbar 队列
M3 通知：XtcNotificationListenerService → 通知模型/渲染（WeChat/来电等）
M4 锁屏/Keyguard：IKeyguardInterface/IKeyguardCallbackInterface
M5 QS/快捷开关：QSPanel、TileLayout、XTCTileLayout、SettingSeekBar、7 个开关（wifi/蓝牙/静音/振动/铃声/省电/…）
M6 音量面板：VolumeFuncSetting、音量条
M7 省电/长续航模式：LowPowerTipLayout、WatchLongEnduranceTipLayout
M8 屏幕录制：RecordingService、RecordingFloatView、MediaProjection、mp4 输出（自实现 muxer，替代 mp4parser）
M9 手电筒/SOS：FlashLightActivity、FlashLightSOSDialog、CameraManager.setTorchMode
M10 BLE 通信：proto 定义 → 手写 protobuf-lite 编解码（不引入 protobuf 库）
M11 助理服务：SystemControlService（FlashLight/VideoRecord directive）
M12 隐私/自启动/权限说明：PrivacyUriActivity、SelfStartActivity、PermissionListActivity、`$...$` 富文本话术渲染（保留原话术，本期不改文案）
M13 Provider：StatusBarProxyProvider、FileProvider、HttpDnsContentProvider（空实现）、FileProvider xml 路径
M14 风险逻辑移除（贯穿各模块）：
- 云控：HttpDnsContentProvider 及其 DNS/远程配置读取、moduleSwitch/funSwitch 运行时解析、任何远程下发开关
- 上报：bigdata 全栈（collector/monitor/crash/CrashIntentService/CodeCoverageReceiver）、LogConfig/LogUtil 持久化上报、Jacoco、BlockCanary、设备指纹采集
- 权限检测：XtcSecurity/libxtcSecurity.so 校验、签名/完整性校验、权限列表检测上报（保留纯 UI 的权限说明页）
- 第三方库：okhttp/retrofit/rx/kotlin/coroutines/mp4parser/mmkv/protobuf 全部不打包

## 4. 波次计划（每波完成即更新 DUAL-LOGIC.md + 断点快照）
| 波次 | 范围 | 验收 |
|---|---|---|
| W0 | 构建基座（build-eng）：Mode A 打包链路 + 校验脚本 + 签名 | 一条命令产出 APK；resources/badging/条目 diff 全绿；≤3MB |
| W1 ✅ | 核心运行时 + 全部 manifest 组件骨架（可启动、不崩） | 编译通过（27 类 / 45 class）；组件全部可实例化 |
| W2 | 状态栏窗口 + 时钟/电量/信号图标 + 下拉手势 + 面板容器 | 状态栏常驻、下拉可展开、返回可收起 |
| W3 | 通知链路（NLS→模型→列表渲染→点击处理） | 微信/系统通知可见、可清除 |
| W4 | QS 快捷开关 + 音量面板 + 省电模式 | 7 个开关/音量/省电与原行为对齐 |
| W5 | 锁屏/Keyguard + 隐私/自启动/权限说明页 + 富文本话术渲染 | 锁屏显示与解锁、页面文案与原文一致 |
| W6 | 录屏 + 手电筒/SOS + 助理服务 + BLE | 录屏文件可生成、手电筒可控、directive 可响应 |
| W7 | 移除清单逐项复核（风险代码 0 命中）+ 资源/信息一致性终验 | 风险扫描通过、APK 信息与原包一致 |

## 5. 双逻辑记录规范（DUAL-LOGIC.md）
每个模块固定四段：
1. **原APK运行逻辑**：入口、调用链、关键 API/常量、状态机（附反编译文件:行号）。
2. **改造后运行逻辑**：类/方法清单、等价点、实现差异（附新代码文件:行号）。
3. **差异与移除点**：逐条「移除/降级/保留」+ 依据（对应 analysis\risk 编号）。
4. **回归核对**：该模块对外行为（UI 可见项、广播/Provider/Service 契约、文件输出）逐一打勾。

## 6. 风险与决策点
- **平台签名**：重组包需设备端签名豁免（corepatch）或平台私钥；本工程交付「已签名 + 可验证」的 APK，签名算法与原包对齐（v2）。
- **资源不重编译**：意味着后续若必须改 manifest 组件（例如彻底删除上报组件声明），需要用 axml 二进制编辑而不是 aapt2 重建；当前策略是「保留声明 + 空实现」，既满足信息一致又不产生上报行为（已在 DUAL-LOGIC 记录）。
- **hidden API**：源码只允许公开 API（API≤27）；`SystemProperties` 用反射桥、进程名读 /proc、时间线用 android.os.Trace。
- **APK 体积**：资源 2.26MB 固定，代码目标 ≤200KB dex；当前 45 class ≈ 30KB dex，余量充足。
- **UI 话术**：本期不改；后续按用户规则统一替换，需要用户提供「指定规则」明细（原句→新句映射表），否则无法开工。

## 7. 云控移除专项（W0.5，已完成）

| 项 | 产物/结论 |
|---|---|
| 专项报告 | `analysis\cloud-control\CLOUD-REMOVAL.md`（云控面 18 项清单、处置、验证证据、复现命令） |
| strict 变体 | `analysis\cloud-control\out\XTCSystemUI-strict-cloud.apk` 2,367,101 B；AXML 物理删除 4 个云控/上报组件声明 + 2 条 bigdata 权限；dex 内不含任何 com.xtc.bigdata/dns/xtcoco 类 |
| 无云控门禁 | `analysis\cloud-control\tools\check_nocloud.py`：18 条类包黑名单 + 34 条 dex 字符串黑名单 + Ljava/net/* 扫描；strict 变体 0 命中 PASS |
| 一致性 | strict 的 `resources.arsc` 与原包 sha256 相同；xmltree 仅少 30 行（被删元素），新增 0 行 |
| 变体策略 | 默认 parity（信息 100%，占位类空实现）+ strict（声明物理删除）；由用户按需刷入 |
| 不删清单 | moduleSwitch/funSwitch/supportScreenRecordAudio/XtcStayFront 元数据、StatusBarProxyProvider、IKeyguard*、SystemControlService、XtcNotificationListenerService、PermissionList/PrivacyUri/SelfStart —— 属本地业务或外部功能声明 |
## 8. 剩余文件分析结论（2026-11-01，4 份 SPEC 完成）

详见 `analysis\modules\INDEX.md`。要点：
- 应用层 245 类中：已实现 31；待复刻主体 = 状态栏/通知 95、QS/音量/省电 43、录屏/手电/助理/蓝牙 58、锁屏/隐私/Provider 49；合计手写约 11k-13k LOC。
- SDK 传递依赖 281 类：132 为云控/上报（不实现），149 为本地能力（部分自写、部分公开 API 替代）；完全死代码 798 类（不分析不实现）。
- **范围修正**：① 原包无通知列表 UI（通知仅服务音乐卡片）；② 录屏无需自写 mp4 muxer（MediaRecorder 直出，mp4parser 死代码）；③ API27 手电是屏幕手电（非 setTorchMode）；④ `status_bar.xml` 原为空布局，折叠栏无可见内容；⑤ `XTCPanelView` 继承 1110 行 `PanelView`，W2 骨架仅覆盖 ~5%；⑥ BLE proto 为死代码但状态栏蓝牙图标需公开 API 近似。
- **待用户决策 10 项**（UI 话术规则、Lottie 依赖、省电写策略、音量提示跳转、隐私页形态、自启副作用、Keyguard 桥/自建、通知列表是否新增、折叠栏时钟电量去留、IStatusBar 注册方案）见 INDEX §2。
## 9. 全模块复刻完成（2026-11-02）

- 手写源码 195 个 class（`src\main\java`），dex 370,840 B，APK **2.33 MB / 2.34 MB（parity / strict）**，16/16 manifest 组件齐全。
- 已完成：核心运行时、状态栏与下拉（伪玻璃、行图标、充电电流）、亮度/音量、媒体卡、8 个磨砂玻璃开关、提示层、面板手势、Keyguard 桥、隐私/自启动/权限说明/富文本、录屏、屏幕手电/SOS、助理 directive、StatusBarProxyProvider、FileProvider。
- 每次构建 `build.ps1 -Variant both` + `verify.ps1` 全 PASS；`no-cloud` 门禁 strict 0 残留。
- 剩余：真机功能验证（需平台签名/签名豁免 + system 分区）；**UI 话术替换待用户「指定规则」**；可选增强见 `analysis\modules\INDEX.md` §5。
## 10. 防反编译加固（本地 R8 混淆变体）

- 新增 `rebuild\tools\build-obfuscated.ps1` + `rebuild\tools\proguard-rules.pro`：javac → **R8（-dontshrink/-dontoptimize，仅名字混淆）** → Mode A 打包 → zipalign → apksigner。
- 输出 `build\XTCSystemUI-hardened.apk`（2.41MB；dex 369,540→264,424 B，-28%）。
- Keep 规则保留：16 个 manifest 组件、`config_statusBarComponent`/`config_systemUIFactoryComponent`/`config_systemUIServiceComponents` 反射类、vendor 布局 XML 里的自定义 View 类名、appComponentFactory/FileProvider、assistantapi 协议名；其余类/方法/字段全部重命名。
- verify.ps1 对 hardened 变体全 PASS（资源/清单与原包一致、16/16 组件、签名、无云控门禁）。
- 说明：R8 混淆是"提高逆向成本"而非不可逆；若仍要加壳（360加固等）注意 sharedUserId 平台签名、directBoot、armeabi ABI 与"信息一致"四个冲突点，详见对话记录。
## 11. 真机联调完成（2026-11-02 03:15）

设备：Z9_SN（XTC/ND01, Android 8.1, 320x360）。最终运行版本：`rebuild\build\XTCSystemUI-hardened.apk`（R8 混淆，sha256 前缀 1a76baf4/0cdc1d86 系列），以 Magisk 模块方式替换 /system/priv-app/XTCSystemUI。

已验证通过的功能（实机 + 日志/截图）：
1. 开机自启、下拉展开 / 上滑收起；折叠态不绘制任何文字（只有展开时才显示状态栏行）
2. 状态栏行：信号、WiFi、绿色/红色纸飞机（联网状态，DC_SYSUI 样式）、时间、充电电流、电量；纸飞机随网络实时变化
3. 面板排版：320x360 下媒体卡+亮度音量行 152px、两条开关行各 77px、4 等分格子、圆钮 62px、左右 5dp 对称 inset
4. 媒体卡：封面、歌名走马灯、上一首/暂停/下一首；数据来自通知监听 → MediaController
5. 8 个磨砂玻璃开关：WiFi / 移动数据 / 蓝牙 / 热点 / 屏幕手电 / 录屏 / 护眼 / 保持亮屏
6. 录屏：隐藏 IMediaProjectionManager 授权（不弹窗）→ 录制 → 悬浮窗**定死不可拖动**（贴顶、黑体加粗计时、点击停止）→ 保存 `/sdcard/Movies/ScreenRecord/*.mp4` + FileProvider 分享 URI
7. 手电：白屏「照明模式」/ 闪烁模式（**上下滑动**切换，避免 ROM SwipeDismissLayout 吞掉水平滑动导致退出；底部圆点也可点）
8. 开机标记文件 `/sdcard/system_ui by hszlk`

遗留（用户可按需再提）：其余开关真机逐个点击验收、状态栏数据流量/热点/IM/下载/HD 图标、逐行歌词、API29+ 内部音频录制、UI 话术替换（等规则）。

工程产物（均在项目内，不依赖 Downloads）：
- `rebuild\build\XTCSystemUI-signed.apk`（parity，未混淆）
- `rebuild\build\XTCSystemUI-signed-strict.apk`（strict 云控移除）
- `rebuild\build\XTCSystemUI-hardened.apk` / `XTCSystemUI-hardened-final.apk`（R8 混淆，最终版）
- `rebuild\build\XTCSystemUI-module-fix3.zip`（Magisk 模块 zip，含最终 hardened APK）
- 源码 `src\main\java`（197 类）、文档 `analysis\**`、构建脚本 `rebuild\tools\**`
## 12. 暂停点（2026-11-02 14:05）

当前状态：
- 代码：新 UI（白色药丸风格 + 快捷方块 2x2 + 亮度/音量药丸 + 两行 12 开关 + 整页可滚动）已实现并通过 javac/构建；包 `rebuild\build\XTCSystemUI-hardened.apk`（sha256 前缀 0d08f1e5 / Final 已同步），已刷入 Z9 并跑起来。
- 已实机验证：整页可下滑（滚动后能看到下方 8 个圆球：手电/录屏/护眼/保持亮屏/飞行/省电/勿扰/旋转）；第一屏为音乐卡 + 快捷方块 + 亮度/音量（见 work\final-top.png、work\v22-top.png 等截图）。
- 未完成/待验证：
  1. 热点开启是否真的生效（日志显示 ROM 的 OnStartTetheringCallback 是 class 不是 interface，走 setWifiApEnabled(null,true) 兜底；需在 Z9 上点热点后查 hostapd/tether 状态）。
  2. 面板顶部状态栏行右侧电量/电流的细微排布（截图看略挤）。
  3. 手电/录屏/勿扰/旋转等新开关点击后的实际效果逐项验收。
  4. 设备：Z9_SN（USB 50089322）偶发掉线；另有一台 Z10（Android 11, 1.7.50）通过无线 adb 连着，本包不适用 Z10。
- 下次继续：接回 Z9 → 展开面板确认第一屏 → 滚动到底确认 8 球 → 逐一点击新开关 → 修热点（如需改走 WifiManager.setWifiApEnabled 带 WifiConfiguration，或 TetheringManager 反射）。