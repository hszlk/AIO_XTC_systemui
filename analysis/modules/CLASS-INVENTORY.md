# 剩余文件分析总表（CLASS INVENTORY / 模块范围划分）

> 目的：回答「剩下的文件到底有哪些、归谁分析、哪些根本不用管」，并作为 W3-W12 复刻的施工范围基线。
> 数据来源：`work\jadx18b\sources`（1.8.00 unquicken 后精确反编译，5136 个 .java）；手写实现树 `src\main\java`。
> 自动统计脚本：`analysis\modules\tools\inventory.py`（类清单 CSV）、`tools\reachability.py`（可达性/死代码判定）；输出 `_inventory.csv`、`_reachable.csv`、`_dead_sdk.csv`。

---

## 0. 总体规模（一句话）

| 指标 | 数值 |
|---|---|
| 反编译类总数（含 androidx/kotlin/okhttp 等第三方） | **3908** |
| 第三方库类（不打包、不分析） | **2682** |
| **应用层类（必须分析/复刻）** | **245** = `com.android.systemui.*` 192 + `com.xtc.statusbar.*` 53 |
| 其中已手写复刻 | **31**（W1 核心运行时 + W2 状态栏窗口骨架 + R/BuildConfig/androidx 兼容类） |
| 应用层传递依赖的 XTC SDK 类 | **281** |
| 其中有 132 类属于云控/上报（**已决定不实现**，调用点改写） | 132 |
| 需要复刻或替换的 SDK 依赖类 | **149**（其中 xtc-ui 55 / baseservice 23 / ultraframework 24 / utils 21 / assistantapi 7 等） |
| **完全不可达的死代码类** | **798**（不需要分析、不需要实现） |

死代码示例：`com.xtc.client.*` 的 BLE 客户端主体（34 类）、`com.xtc.ipc.*` 大部分、`com.xtc.im` 96 类、`com.xtc.ui` 207 类（未被 statusbar/systemui 引用）、`settingslib` 4 类、`funlist` 14 类。

---

## 1. 分析分工（4 个并行任务，产物 = 可驱动复刻的实现规格）

| 任务 | 负责人 | 范围（类数） | 输出 |
|---|---|---|---|
| task-5 状态栏主链路 + 通知链路 | risk-scan | systemui 66（statusbar/*、statusbar/phone/*）+ xtc-statusbar 53 | `analysis\modules\statusbar-notification\SPEC.md` |
| task-6 QS + 音量 + 省电 | mod-qs | systemui 45（qs/*）+ 相关 policy/布局/assets | `analysis\modules\qs-volume\SPEC.md` |
| task-7 录屏 + 手电筒/SOS + 助理 + 蓝牙 | mod-tools | systemui 23（screenrecord/flashlight/vassistant/media）+ 蓝牙接口 | `analysis\modules\media-tools\SPEC.md` |
| task-8 锁屏/Keyguard + 隐私/自启动/权限说明 + 富文本 + Provider | mod-misc | xtc-architecture 26 + xtc-ui 相关 13 + AIDL/Provider | `analysis\modules\misc-local\SPEC.md` |
| （Lead 已做）构建/云控/W1/W2 | lead | 31 类 + 构建流水线 + 云控专项 | `rebuild\`、`analysis\cloud-control\`、本文 |

每个 SPEC 固定六节：A 类清单与判定（复刻必需/可选/dead）· B 原逻辑（调用链/状态机/证据行号）· C **UI 元素与文案全清单**（为后续 UI 改话术准备）· D 复刻实现建议（新类/LOC/公开 API 或反射方案）· E 依赖与风险 · F 实施顺序。

---

## 2. 应用层类分布（必须处理）

| 包 | 类数 | 模块 | 状态 |
|---|---|---|---|
| `com.android.systemui.statusbar.*` | 66 | 状态栏主链路（StatusBar/PanelView/StatusBarIconView/CommandQueue…） | W2 骨架 7 类；其余待 W2/W3 |
| `com.android.systemui.qs.*` | 45 | QS 面板/开关/seekbar | QSPanel/QsOutCanScrollView 空壳；待 W4 |
| `com.android.systemui.screenrecord/flashlight/vassistant/media` | 23 | 录屏/手电/SOS/助理 | 5 个组件空壳；待 W8-W10 |
| `com.android.systemui.plugins.*` | 20 | 插件框架（OverlayPlugin/插件装载） | 判定：大部分不实现（依赖插件 APK 与外部权限） |
| `com.android.systemui` 顶层 | 38 | Application/Service/Factory/Dependency/R/BuildConfig + 混淆工具类 | **已实现 6 + W2 相关**；其余按需（UEvent 观察等） |
| `com.xtc.statusbar.*` | 53 | 手表状态栏整套（XtcStatusBarView/SwipeDismissLayout/Hint/LowPower…）+ 通知监听服务 | XtcNotificationListenerService/XtcStatusBarView 骨架；待 W2/W3 |

## 3. 需要复刻/替换的 SDK 依赖（149 类，按模块归属）

| SDK 区域 | 需要的类数 | 用途 | 复刻策略 |
|---|---|---|---|
| `com.xtc.ui.*` | 55 | 弹窗/Toast/开关/按钮/列表 item/富文本等基础 UI 组件 | 用 framework 控件重写（保留布局文件与 id）——**UI 改话术的主战场** |
| `com.xtc.ultraframework.*` | 24 | 系统服务封装（StatusBarServiceEx、AudioManager、AntiAddiction、BluetoothDeviceEx 等） | 能公开 API 的用公开 API；纯私有服务（StatusBarServiceEx）改用本包内实现 |
| `com.xtc.baseservice.*` | 23 | 与 launcher 的 IPC/状态桥（含 CameraInUseMonitor、BleClientManager） | 本地化：Camera 用 CameraManager；蓝牙用 BluetoothAdapter；IPC 用本包 Provider 补齐 |
| `com.xtc.utils.*` | 21 | 反射/编码/存储/网络状态工具 | 按需手写精简版 |
| `com.xtc.assistantapi.*` | 7 | 语音助理 directive 协议（FlashLight/VideoRecord） | 手写 JSON 协议 + binder 回调 |
| `com.xtc.ipc.client.*` | 6 | 与 launcher 的 ipc 客户端 | 判定：大多可去掉，仅保留 provider 通道 |
| `com.xtc.architecture.*` | 3 | MVP 基类（Activity 基类） | 用 framework Activity 重写 |
| `com.xtc.common.library` / `com.android.common` | 5 | 常量/工具（SharedKeyConstants_Launcher 等） | 常量本地化 |
| `com.xtc.client.*` | 3 | **BLE 连接状态消息**（被 XtcStatusBarView 使用） | 改用 `BluetoothAdapter` 公开 API 判定连接状态；不需要 proto 编解码 |

## 4. 已确认的判定（避免返工）

1. **上报/云控 132 类全部不实现**（bigdata 65 / httplib 40 / log 17 / domain 6 / funlist 2 / dns 1 / xtcoco 1），调用点改本地空实现或删除——见 `analysis\cloud-control\CLOUD-REMOVAL.md`。
2. **BLE 客户端（com.xtc.client 34 类）为死代码**，不实现；但注意 `XtcStatusBarView` 通过 `XtcBleConnectStateListener/BleConnectResponse` 消费"与手机的蓝牙连接状态"→ 复刻时用公开蓝牙 API 实现该图标状态。
3. **CameraInUseMonitor（com.xtc.baseservice.c）** 被录屏使用 → 用 `CameraManager.registerAvailabilityCallback` 复刻。
4. **插件框架（20 类）**：除非后续确认有实际插件 APK 使用，否则只保留 `ActivityStarter` 等最小接口。
5. **`settingslib` 4 类**：属框架库拷贝，除非被 UI 引用（当前判断为死），不实现。

## 5. 施工顺序（与 SPEC 对照）

W2 收尾（状态栏图标：信号/WiFi/蓝牙/通知角标、面板手势细节）→ W3 通知链路 → W4 QS+音量 → W5 省电提示 → W6 锁屏桥 → W7 录屏 → W8 手电/SOS → W9 助理 → W10 隐私/自启动/权限说明/富文本 → W11 Provider 补全 → W12 UI 话术替换（待用户规则）→ 最终逐条风险复核 + 设备实测。

## 6. 产物索引

- 本表：`analysis\modules\CLASS-INVENTORY.md`
- 机器清单：`analysis\modules\_inventory.csv`（3908 行：fqcn/area/owner/impl/status/kb）
- 可达/死代码：`_reachable.csv`、`_dead_sdk.csv`
- 4 份模块 SPEC：见 §1（分析进行中，落地后在此登记）
- 云控专项：`analysis\cloud-control\CLOUD-REMOVAL.md`；上报/权限风险：`analysis\risk\RISK-MAP.md`
