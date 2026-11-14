# SPEC：状态栏主链路 + 通知链路（复刻实现规格）

> 目标：把「状态栏主链路 + 通知链路」的剩余文件分析完，产出可直接驱动复刻的实现规格。
> 代码证据源：`D:\DEBUG\apk\sysui\work\jadx18b\sources`（1.8.00 unquicken 后精确版，**唯一可信**；头部注释 `loaded from ...\work\dex_unq2\classes.dex`）。交叉校验：`work\jadx17\sources`。
> 资源：`D:\DEBUG\apk\sysui\nx\resources`。已有风险清单：`analysis\risk\RISK-MAP.md`（本文引用 R-xx）。已有手写实现：`D:\DEBUG\apk\sysui\src\main\java`（W1/W2）。
> 本目录同时保存两份原始清单，便于后续批量改话术：`_layout_ids.txt`（布局 id 抽取）、`_strings_zh.txt`（values/strings.xml 全部 201 条含中文的字符串，行号+key+原文）。
> 证据标记：**A** 方法体/常量可读；**B** 仅字符串/导入/布局可读；**C** 仅调用点/类名。

## 0. 结论先行（给 Lead / W3 的 8 条关键事实）

1. **折叠状态栏本身没有可见内容**：`status_bar.xml` 只有一个空的 `PhoneStatusBarView`（`nx\resources\res\layout\status_bar.xml:1-7`，无子 View）；真正可见的「状态栏信息行」是下拉面板顶部的 `XtcStatusBarView`（`layout_pull_down.xml:6-10`，`status_bar_view`）。→ **W2 在 `PhoneStatusBarView` 里自绘时钟/电量是新增行为，不是原包行为**；W3/W4 要决定「保留（更像手机）还是回退（更像原版手表）」。
2. **本包没有通知列表 / 通知中心 UI**：全仓无 `cancelNotification` / `getActiveNotifications` / `getRanking` / `isClearable` / HeadsUp 代码（grep 0 命中）。通知唯一消费方是音乐控制：`XtcNotificationListenerService → NotificationDataCenter(f) → MusicController(qs.tiles.music.a)`（`XtcNotificationListenerService.java:27-35`、`music\a.java:331`）。→ W3 若实现「通知列表」，属于**新增功能**而非复刻。
3. 下拉面板是 `XTCPanelView extends PanelView`，`PanelView` 是 1110 行的**手势/动画基类**（展开高度、fling、falsing、长按、back、清空区、peek 高度）；W2 的 `XTCPanelView extends FrameLayout` + alpha 动画只覆盖了 ~5%。
4. 窗口参数**不是**简单 `TYPE_STATUS_BAR`：原逻辑 `StatusBarWindowManager(m).a(view, barHeight)` → `StatusBarServiceEx.getInstance().getWindowLayout(ctx, barHeight, "StatusBar")`，失败才回退 `new WindowManager.LayoutParams(-1, barHeight, 2000, -2138832824, -3)`（`statusbar\phone\m.java:70-100`）。`2000` 即旧 `TYPE_STATUS_BAR`（`SharedKeyConstants_Launcher.ai.y = 2000`，`SharedKeyConstants_Launcher.java:73-120`）。
5. 状态栏图标（BLE/经典蓝牙/耳机/信号/电量/…）**不是** AOSP `StatusBarIconController` 驱动，而是 `com.xtc.statusbar.XtcStatusBarView`（1073 行自定义 RelativeLayout）+ XTC baseservice 回调。AOSP 的 `StatusBarIconView/StatusBarIconController*` 属 **dead/可选**。
6. **蓝牙图标是双通道叠加**：经典蓝牙（`BluetoothHelper`：adapter 使能 + `CONNECTION_STATE_CHANGED`==2 + XTC mate 广播 `xtc.matemode.action.MATE_ON/OFF`）与 BLE（`com.xtc.baseservice.c.a` = **BleClientManager**，`XtcBleConnectStateListener` 回调里 `getConnectDeviceList` 遍历 `BleConnectResponse.getConnectState()==2`）共同决定；两个静态标志 `f2781b`（classic 显示）/`f2782c`（BLE 已连）互斥控制隐藏。→ D 节给公开 API 近似方案。
7. `StatusBar` 的 `IStatusBar` 注册走 XTC `StatusBarServiceEx.registerStatusBar(...)` 返回 `XtcRegisterStatusBarResult`，`CommandQueue extends XtcIStatusBar extends IStatusBar.Stub`（`XtcIStatusBar.java:17`）；`config_systemUIServiceComponents` 第 2 项 `CommandQueue$CommandQueueStart` 负责把 CommandQueue 放进组件表（`arrays.xml:3-7`）。
8. 通知/提示类「浮层」由 `PopWindowManager(j)` 统一以 **window type 2002（TYPE_SYSTEM_ALERT）** 添加到 WindowManager（`j.java:93-105`），承载 HintWindowLayout / AntiAddictionExitLayout / DisabledForbidLayout —— W3 的「提示窗」应复用它，而不是另起 Activity。

---

## A. 类清单表（95 条：必需 50 / 可选 33 / dead 12）

> 判定口径：**复刻必需**=不实现则主链路/可见 UI 缺失或编译不过；**可选**=原包有但本产品未走通或有更简单替代；**dead**=引用计数=1（仅自身）或只被可选类引用。
> 被调用者列格式 `←X` 表示由 X 调用，`→Y` 表示调用 Y。

### A-1 `com.android.systemui.statusbar.phone`（23 条：必需 11 / 可选 11 / dead 1）

| # | 类全名 | 文件:行 | 一句话职责 | 调用者 / 被调用者 | 判定 |
|---|---|---|---|---|---|
| 1 | `StatusBar` | `statusbar\phone\StatusBar.java:1-1863`（`mo261a()` :490-539） | 状态栏组件总控：建窗口、注册 IStatusBar、驱动面板/图标/锁屏交互 | ←`SystemBars`；→`StatusBarWindowView/XTCPanelView/CommandQueue/StatusBarWindowManager` | 复刻必需 |
| 2 | `StatusBarWindowView` | `...\StatusBarWindowView.java:29-354` | 状态栏窗口根 View：分发/拦截触摸、返回键/音量键、ActionMode | ←`StatusBar.m547a()`；→`StatusBar.a()/m()/n()` | 复刻必需 |
| 3 | `PhoneStatusBarView` | `...\PhoneStatusBarView.java:11-179` | extends `PanelBar`；触摸转发给 `StatusBar.a(MotionEvent)`，bar 高度/透明度回调 | ←`CollapsedStatusBarFragment`（status_bar.xml 根） | 复刻必需 |
| 4 | `PanelView` | `...\PanelView.java:1-1110` | **下拉面板抽象基类**：展开高度计算、拖拽/fling/长按/falsing、动画、清空区、peek | ←`XTCPanelView` | 复刻必需 |
| 5 | `PanelBar` | `...\PanelBar.java:1-154` | 状态栏条抽象基类：状态 0/1/2、透明度、panel 绑定 | ←`PhoneStatusBarView` | 复刻必需 |
| 6 | `XTCPanelView` | `...\XTCPanelView.java:1-795` | 手表下拉面板：header+QS 容器、弹簧/缩放/alpha、触摸、PowerSave 分支 | ←`StatusBar.n()`；→`QSPanel/XtcStatusBarView/OutCanScrollView` | 复刻必需 |
| 7 | `CollapsedStatusBarFragment` | `...\CollapsedStatusBarFragment.java:1-18` | 仅 `onCreateView → inflate(status_bar)`，挂到 `status_bar_container` | ←`StatusBar.n()` :571-576 | 复刻必需 |
| 8 | `CommandQueue` | `statusbar\CommandQueue.java:1-800` | `IStatusBar` 回调中继（XtcIStatusBar 子类），主线程分发到 `CommandQueue.a` 监听者 | ←`StatusBar.mo261a()` :514-515；→`CommandQueue.a` 各实现 | 复刻必需 |
| 9 | `m`（StatusBarWindowManager） | `...\m.java:21-359` | 窗口状态机：bar 高度/全屏、flags、屏幕方向、`setHasTopUi`、fitsSystemWindows | ←`StatusBar.W()` :1462-1467 | 复刻必需 |
| 10 | `h`（PhoneStatusBarTransitions） | `...\h.java:1-94` | `BarTransitions` 子类：状态栏背景/图标模式过渡 | ←`PhoneStatusBarView` 构造 :43 | 复刻必需（最小化） |
| 11 | `b`（BarTransitions） | `...\b.java:1-240` | 状态栏模式 0..5 的 transition 基类 | ←`h` | 复刻必需（最小化） |
| 12 | `e`（DoubleTapHelper） | `...\e.java:1-139` | 双击检测（唤醒/相机） | ←`StatusBarWindowView` 构造 :80-90 | 可选 |
| 13 | `g`（NoisyVelocityTracker） | `...\g.java:1-94` | 速度跟踪（PanelView fling） | ←`PanelView` | 可选 |
| 14 | `i`（PlatformVelocityTracker） | `...\i.java:1-45` | 速度跟踪（framework 实现） | ←`PanelView` | 可选 |
| 15 | `n`（VelocityTrackerFactory） | `...\n.java:1-32` | 速度跟踪工厂 | ←`PanelView` | 可选 |
| 16 | `o`（VelocityTrackerInterface） | `...\o.java:1-13` | 速度跟踪接口 | ←`PanelView` | 可选 |
| 17 | `a`（AutoTileManager） | `...\a.java:1-63` | QS 自动添加磁贴（AOSP 遗留） | 引用=2（自身+?） | 可选 |
| 18 | `c`（BounceInterpolator） | `...\c.java:1-23` | 回弹插值器 | 引用=6（动画类） | 可选 |
| 19 | `d`（ConfigurationControllerImpl） | `...\d.java:1-82` | 配置变化分发 | 引用=1 | 可选 |
| 20 | `f`（LockscreenGestureLogger） | `...\f.java:1-30` | 锁屏手势打点（MetricsLogger） | 引用=1 | **dead**（且属上报 R-78 家族） |
| 21 | `j`（StatusBarIconController 接口） | `...\j.java:1-148` | AOSP 图标控制器接口（内部 `b` 回调组） | 引用=4 | 可选 |
| 22 | `k`（StatusBarIconControllerImpl） | `...\k.java:1-178` | 实现上者，向 `CommandQueue` 注册 | ←`Dependency`；引用=4 | 可选 |
| 23 | `l`（StatusBarIconList） | `...\l.java:1-60` | 图标槽位列表（`config_defaultCloudSearchServices` 反用为槽名数组） | ←`k` | 可选 |

### A-2 `com.android.systemui.statusbar`（14 条：必需 4 / 可选 9 / dead 1）

| # | 类全名 | 文件:行 | 职责 | 调用关系 | 判定 |
|---|---|---|---|---|---|
| 24 | `a`（FlingAnimationUtils） | `statusbar\a.java:1-176` | fling 动画曲线（PanelView 使用） | ←`PanelView.a(...)` :723 | 复刻必需 |
| 25 | `b`（LauncherProxyManager 接口） | `statusbar\b.java:1-10` | launcher 代理接口 | 引用=1 | 可选 |
| 26 | `c`（LauncherProxyManagerImpl） | `statusbar\c.java:1-89` | 代理实现（`ILauncherProxyInterface`） | 引用=1 | 可选 |
| 27 | `d`（ScalingDrawableWrapper） | `statusbar\d.java:1-20` | drawable 缩放包装 | 引用=1 | **dead** |
| 28 | `e`（StatusBarKeyguardViewManager 接口） | `statusbar\e.java:1-12` | 锁屏管理器接口（`hide/show/b()`） | ←`StatusBar` Dependency（:914 等） | 复刻必需 |
| 29 | `f`（StatusBarKeyguardViewManagerImpl） | `statusbar\f.java:1-148` | 实现上者，桥接 `IKeyguardCallbackInterface` | ←`Dependency` | 复刻必需 |
| 30 | `g`（StatusBarState） | `statusbar\g.java:1-24` | 状态常量（SHADE/KEYGUARD/SHADE_LOCKED） | 全仓无 `com.android.systemui.statusbar.g` 引用（仅 `StatusBar.g` 是 int 常量，同名字段）；`StatusBar.b()`/`PanelView` 直接用字面量 0/1/2 | 可选（AOSP 遗留；可删，保留也无害） |
| 31 | `ExpandableView` | `statusbar\ExpandableView.java:1-308` | 可展开行抽象（通知行基类） | 引用=104（自身层级） | 可选（本包无通知行） |
| 32 | `ExpandableOutlineView` | `...\ExpandableOutlineView.java:1-120` | 上者子类 | 引用=8 | 可选 |
| 33 | `StatusBarIconView` | `...\StatusBarIconView.java:1-744` | AOSP 通知图标 View（含角标/动画） | 引用=37（图标控制器） | 可选 |
| 34 | `AnimatedImageView` | `...\AnimatedImageView.java:1-116` | 带出现/消失动画的 ImageView | ←`StatusBarIconView` | 可选 |
| 35 | `AlphaOptimizedView` | `...\AlphaOptimizedView.java:1-23` | 透明优化 View（布局使用） | ←`super_status_bar.xml:5` | 复刻必需 |
| 36 | `AlphaOptimizedImageView` | `...\AlphaOptimizedImageView.java:1-23` | 同上 ImageView | 引用=5 | 可选 |
| 37 | `AlphaOptimizedFrameLayout` | `...\AlphaOptimizedFrameLayout.java:1-23` | 同上 FrameLayout | 引用=5 | 可选 |

### A-3 `com.xtc.statusbar`（主 21 条：必需 20 / 可选 1；耳机子包 17 条另计）

| # | 类全名 | 文件:行 | 职责 | 调用关系 | 判定 |
|---|---|---|---|---|---|
| 38 | `XtcStatusBarView` | `xtc\statusbar\XtcStatusBarView.java:1-1073` | **面板 header/状态图标行**：20+ 图标、运营商/时间文本、流量动画、提示浮层 | ←`XTCPanelView.onFinishInflate()` :293-294 | 复刻必需 |
| 39 | `XtcNotificationListenerService` | `...\XtcNotificationListenerService.java:1-36` | NLS：posted/removed → NotificationDataCenter | ←system_server 绑定 | 复刻必需 |
| 40 | `f`（NotificationDataCenter） | `...\f.java:1-79` | 观察者注册/分发（唯一注册方 MusicController） | ←`XtcNotificationListenerService`、`music\a.i()` :331 | 复刻必需 |
| 41 | `g`（NotificationServeCallback） | `...\g.java:1-12` | 通知回调接口（posted/removed） | ←`f` | 复刻必需 |
| 42 | `h`（NotificationUtils） | `...\h.java:1-128` | 通知过滤/类目/flags/package 白名单（音乐 App 列表） | ←`music\a`、`StatusBar`? | 复刻必需 |
| 43 | `StatusTipView` | `...\StatusTipView.java:1-85` | header 内文字提示：1s 淡入→1s 淡出 | ←`XtcStatusBarView`（`n` 接口实现） | 复刻必需 |
| 44 | `SwipeDismissLayout` | `...\SwipeDismissLayout.java:1-228` | 边缘右滑消失容器（ViewDragHelper），灭屏自动 dismiss | ←4 个提示布局根 | 复刻必需 |
| 45 | `HintWindowLayout` | `...\HintWindowLayout.java:1-69` | 通用提示窗（标题+内容+我知道了） | ←`PopWindowManager`、screenrecord | 复刻必需 |
| 46 | `AntiAddictionExitLayout` | `...\AntiAddictionExitLayout.java:1-105` | 防沉迷退出提示（`$..$` 高亮语法） | ←`PopWindowManager.a(String,String)` | 复刻必需 |
| 47 | `DisabledForbidLayout` | `...\DisabledForbidLayout.java:1-85` | 应用被家长禁用提示（应用名+如何退出） | ←`PopWindowManager.a(String)` | 复刻必需 |
| 48 | `LowPowerTipLayout` | `...\LowPowerTipLayout.java:1-62` | 低电量提示（取消/退出） | ←`qs\tiles\e`（PowerSaveTile） | 复刻必需 |
| 49 | `WatchLongEnduranceTipLayout` | `...\WatchLongEnduranceTipLayout.java:1-62` | 长续航提示（取消/开启） | ←`qs\tiles\e` | 复刻必需 |
| 50 | `j`（PopWindowManager） | `...\j.java:1-230` | TYPE_SYSTEM_ALERT 浮层统一管理（add/remove/dismissAll） | ←手电/防沉迷/禁用/录屏提示 | 复刻必需 |
| 51 | `a`（AlarmClockHelper） | `...\a.java:1-188` | 闹钟图标：读 `com.xtc.alarmclock` 的 `clock.db` `clock.isOpen` + XTC 广播 | ←`StatusContractFactory` | 复刻必需 |
| 52 | `BluetoothHelper` | `...\BluetoothHelper.java:1-86` | 经典蓝牙图标：adapter/连接状态/mate 广播 | ←`StatusContractFactory` | 复刻必需 |
| 53 | `c`（DownLoadHelper） | `...\c.java:1-67` | 下载图标：`com.xtc.systemupdate.downloadStart/Compelete` 广播 | ←`StatusContractFactory` | 复刻必需 |
| 54 | `e`（LocationHelper） | `...\e.java:1-219` | GPS 图标：LocationManager + XTC 定位广播 | ←`StatusContractFactory` | 复刻必需 |
| 55 | `d`（IStatusContract） | `...\d.java:1-69` | 4 个内部契约接口（a/b/c/d） | ←各 Helper | 复刻必需 |
| 56 | `m`（StatusContractFactory） | `...\m.java:1-28` | 工厂：BluetoothHelper/AlarmClockHelper/DownLoadHelper/LocationHelper | ←`XtcStatusBarView` 构造 :355-360 | 复刻必需 |
| 57 | `b`（Constant） | `...\b.java:1-103` | 事件名/埋点字段常量（screen_record 埋点、QS 点击埋点） | ←`statusbar\a\b`、`\a\c` | 复刻必需（或裁剪） |
| 58 | `n`（XTCStatusBarTipView） | `...\n.java:1-9` | 提示接口（a/b 两个方法） | ←`XtcStatusBarView` | 复刻必需 |
| 59 | `k`（R） | `...\k.java:1-4907` | 资源 ID 映射（生成物） | 全包 | 可选（用本工程 R 替代） |

**耳机子包 `com.xtc.statusbar.headset`（17 条：可选 8 / dead 9）**

| 类 | 文件 | 判定 | 理由 |
|---|---|---|---|
| `IHeadset` | `headset\IHeadset.java:1-90` | 可选 | `XtcStatusBarView.o`（耳机电池）用到；无 BLE 耳机时可空实现 |
| `d` HeadsetManager / `f` HeadsetServiceImpl / `a` HeadsetDataServiceImpl / `g` HeadsetUtils / `m` SimpleConnectStateChangeListener / `HeadsetConnectManager` / `HeadsetReceiver` | 同名文件 | 可选 | 耳机连接/电量链路，复刻可只保留接口 |
| `b` HeadsetExtra / `c` HeadsetGatt / `e` HeadsetScanner / `h` IHeadsetDataService / `i` ProtocolConstant / `j` RetryWithDelay / `k` ScanDevice / `l` SettingEntity / `n` SyncCharacteristic | 同名文件 | **dead** | 无 UI/主链路引用，属耳机协议栈（与 Lead「BLE proto 死代码」一致） |

### A-4 `com.android.systemui.statusbar.policy`（20 条：必需 15 / 可选 4 / dead 1）

| # | 类 | 文件 | 职责 | 判定 |
|---|---|---|---|---|
| 60 | `e` CallbackController | `policy\e.java` | 回调注册基接口 | 复刻必需 |
| 61 | `f` ConfigurationController | `policy\f.java` | 配置变化回调 | 复刻必需 |
| 62 | `k` Listenable | `policy\k.java` | 监听接口 | 复刻必需 |
| 63 | `l`/`m` PowerSaveModeController(+Impl) | `policy\l.java`、`m.java:1-44` | 省电模式（`persist.sys.wleswitch`） | 复刻必需 |
| 64 | `o`/`p` RingModeController(+Impl) | `policy\o.java`、`p.java:1-119` | 响铃/振动（`vibrate_when_ringing`） | 复刻必需 |
| 65 | `r`/`s` WifiController(+Impl) | `policy\r.java`、`s.java:1-91` | WiFi 开关 | 复刻必需 |
| 66 | `c`/`d` BluetoothController(+Impl) | `policy\c.java`、`d.java:1-243` | 蓝牙开关/连接 | 复刻必需 |
| 67 | `a`/`b` AirplaneModeController(+Impl) | `policy\a.java`、`b.java:1-100` | 飞行模式 | 复刻必需 |
| 68 | `g`/`q` FlashlightController + TorchFlashlightControllerImpl | `policy\g.java`、`q.java:1-613` | 手电控制（CameraManager torch） | 复刻必需 |
| 69 | `j` LegacyFlashlightControllerImpl | `policy\j.java` | 旧手电实现 | 可选 |
| 70 | `FlashlightControllerDelegate` | `policy\FlashlightControllerDelegate.java` | 手电委托 | 可选 |
| 71 | `h`/`i` IconLogger(+Impl) | `policy\h.java`、`i.java:1-88` | 图标使用 MetricsLogger 打点 | **dead**（且属 R-78 上报家族） |
| 72 | `n` QSTileFilter | `policy\n.java` | 磁贴过滤 | 可选 |

### A-5 手写依赖类（不在本包，但状态栏直接引用；共 8 类，全部「必须自写」）

`com.xtc.ui.widget.button.LongHollowButton`、`DoubleFlatButton`；`com.xtc.ui.widget.dialog.DoubleFlatBtnDialog`、`LongHollowBtnWithTitleDialog`；`com.xtc.ui.widget.toast.view.ToastUtil`；`com.xtc.ui.widget.util.DialogUtil`；`com.xtc.ui.widget.textview.MarqueeTextView`；`com.xtc.baseservice.util.ViewUtils`（`a(view, visibility)`）。
证据：`HintWindowLayout.java:9,46`、`LowPowerTipLayout.java:9,42`、`XtcStatusBarView.java:23,373`、`layout_music_shortcut.xml:32`、`q.java:25-30`。

---

## B. 原逻辑

### B-1 启动链（7 层，含证据）

```
① system_server 拉起 com.android.systemui.SystemUIService
② SystemUIApplication.a(config_systemUIServiceComponents)   SystemUIApplication.java（W2 已实现）
   items: Dependency / CommandQueue$CommandQueueStart / SystemBars       arrays.xml:3-7
③ SystemBars.b() → loadClass(config_statusBarComponent) → new StatusBar()  SystemBars.java:34-51
④ StatusBar.mo261a()                                       StatusBar.java:490-539
   ├─ WindowManager/Display/PowerManager/AccessibilityManager
   ├─ StatusBarServiceEx.getInstance().getBarService()      :501-505
   ├─ CommandQueue c(CommandQueue.class); f1066a.a(this)     :514-515   ← 注册监听
   ├─ StatusBarServiceEx.registerStatusBar(CommandQueue,...) :517       ← 返回 XtcRegisterStatusBarResult
   ├─ y() → W() → n() → m.a(window, barHeight)              :1458-1467  ← 建窗口
   ├─ disable/visibility/binders/icons 初始回放              :525-532
   └─ O() 充电状态 / policy.f 注册 / a.a 注册                :536-538
⑤ n() makeStatusBarView                                    :560-590
   ├─ m547a(ctx): inflate super_status_bar                  :678-681
   ├─ f1072a = findViewById(notification_panel)             :568-569
   ├─ fragment replace(status_bar_container, CollapsedStatusBarFragment) :576
   ├─ QSPanel f1065a = findViewById(quick_settings_panel); setHost(SystemUIFactory.a().a(...)) :578-581
   └─ 注册广播（SCREEN_OFF/CLOSE_SYSTEM_DIALOGS/PHONE_STATE/...） :601-614
⑥ CollapsedStatusBarFragment.onCreateView → inflate status_bar（空 PhoneStatusBarView）
⑦ PhoneStatusBarView 绑定：fragment 回调里 setBar(StatusBar)/setPanel(XTCPanelView)   :592-599
```

### B-2 窗口状态机（`StatusBarWindowManager m`）

`m` 内持有一份 `b` 状态（20 个 boolean + `a` int 状态，`m.java:308-358`），`f(b)` 统一 apply 到 `WindowManager.LayoutParams`：

| 方法 | 触发者 | 作用 | 证据 |
|---|---|---|---|
| `a(view, barHeight)` | `StatusBar.W()` | 取 vendor 窗口参数、`addView`、缓存副本 | `m.java:70-80` |
| `b(boolean)` | `StatusBar.h(z)` / `makeExpandedInvisible` | panel 展开/收起 → `height = MATCH_PARENT` vs `barHeight` | `m.java:240-245`、`d()`:152-163 |
| `b(int)` | `StatusBar.f(i)` | bar state（SHADE=0/KEYGUARD=1/SHADE_LOCKED=2） | `:265-269` |
| `c(boolean)` | `StatusBar.F()`（closing finished） | 面板关闭后窗口恢复 | `:247-251` |
| `f(boolean)` | `StatusBar.h(boolean)` | `fitsSystemWindows` | `:271-275` |
| `g(boolean)` | `StatusBar.g(boolean)` | panel fully expanded 标记 | `:277-281` |
| `h(boolean)` | `StatusBar.Z()` | doze 标记 | `:283-287` |
| `d(boolean)` | `StatusBar.setQsExpanded` | QS 展开 | `:253-257` |
| `a(boolean)` | — | light status bar（`SYSTEM_UI_FLAG_LIGHT_STATUS_BAR`） | `:106-109` |
| `c(int)` | `StatusBar.B()` 尺寸变化 | barHeight 变化 | `:289-292` |

窗口高度规则（`m594a(b)`，`m.java:166-168`）：`!b.l && (a() || d || h || g || j || s)` → 全屏，否则 `barHeight`；`a()` = `f1191a && !b`（panel 可见且非 QS 展开）。`screenOrientation` 由 `b()` 决定（`:124-135`：panel 或 r 时 2/5，否则 -1）。`setHasTopUi` 在窗口“是否顶层 UI”变化时调用（`:192-201`）。

### B-3 下拉面板手势状态机（`PanelView` + `XTCPanelView`）

触控入口有 2 条：
1. `StatusBarWindowView.dispatchTouchEvent/onInterceptTouchEvent/onTouchEvent`（`StatusBarWindowView.java:188-231`）：`StatusBar.d()` 为 doze 时走双击/唤醒；否则 super 分发；未消费的 `UP/CANCEL` 调 `StatusBar.b(1,false)`。
2. `StatusBar.b(int, boolean)`（`StatusBar.java:1376-1390`）：维护 `mPanelDisabled` 位 `y`，决定面板是否可交互（`S()/R()/T()/U()` = suspend/resume/cancel/schedule autohide）。

`PanelView` 状态机（`onTouchEvent` :346-457，`onInterceptTouchEvent` :546-620）：

```
DOWN: 记录 f989a(expandedHeight)/h(上次Y)/i(上次X)；
      y = m521f()||mo515a()  (面板已展开或可展开)
      z = m521f()||a(x,y)    (是否应从本次手势接管)
      若 !y → 走 j()/f()（touch 动画）
MOVE: f = y - this.h;
      超过 slop → this.n=true（进入拖拽）
      目标高度 fMax = max(0, e + f)（e=基准高度）
      falsing 判定 → this.p / this.s = c(x,y)（向上快速滑）
      setExpandedHeightInternal(fMax)（若允许）
UP  : a(event,x,y,false)
      → 计算速度 getCurrentExpandVelocity() / a(fB,fHypot,...) 判定 fling 方向
      → a(fB, z2, d(f,x)) → a(f, z, f2, f3, z2) 驱动动画
      → 未拖拽且已展开且短按 <longPress → a(360L, getPeekHeight(), true)（回弹到 peek）
      → 否则 post 长按 → 触发展开/收起
```

动画（`PanelView.a(float,boolean,float,float,boolean)` :707-770）：`getMaxPanelHeight()` 目标；`z3` 清空区逻辑（`getMaxPanelHeight()-getClearAllHeight()`，仅 `mo528m() && !mo529n()`）；向上 fling 时用 `FlingAnimationUtils(a)`，向下用 `qs_panel_collapse_duration_ms` 插值（0.7*d + 0.3*d*ratio）；`BoostFramework.perfHint(4224,...)`（性能提示，可选）。

`XTCPanelView` 关键扩展（`XTCPanelView.java`）：

| 能力 | 证据 | 行为 |
|---|---|---|
| 最大/展开高度 | `getMaxPanelHeight()` :440-442 → `dimen qs_panel_max_height`=`180dp`（构造 :220） | 面板最大 180dp |
| 缩放/alpha 动画 | `onFinishInflate` :280-327 | `fl_root` alpha、`QSPanel` scaleX/Y、`XtcStatusBarView` scaleX/Y 同步 = `PathInterpolator(0.65,0,0.65,1)` |
| header 弹簧 | :310-326 | `SpringAnimation(SCALE_X,1.0)` stiffness 350/damping 0.5，max 1.08 |
| 可见性联动 | `s()` :459-483 | `!f()`（非 fully expanded）→ `StatusBar.f(z)`；`e()` → `StatusBar.g(z)`；QS `setListening(g)`；header `a(g,0,false)` |
| 触摸接管 | `onTouchEvent` :416-426、`onGenericMotionEvent` :485-492 | `OutCanScrollView` 滚动；`com.xtc.wear.widget.a.b` 处理旋转表冠（`onGenericMotionEvent` 负数方向） |
| 关闭复位的 | `q()` :379-390 | scroll 归零/所有 scale/alpha 复位 |
| 省电模式分支 | `m()` :607-614 | `PowerManager.isPowerSaveMode()` 时走 `o();n()` |
| 背屏/容器 | `onFinishInflate` :283-300 | `fl_root` 背景 = `com.android.systemui.d.e.a().a(ctx)`（壁纸模糊/取色服务） |
| 触摸吞掉 | `XtcStatusBarView` 构造 :362-367 | `viewInflate.setOnTouchListener((v,e)->true)` → **header 区域不触发屏幕手势**，仅用于展示 |

### B-4 展开/收起时序（含 disable flags / autohide / HALL）

```
[展开] CommandQueue.a(show) 或手势 → StatusBar.mo551b()/b(str)   StatusBar.java:1216-1234
  → m556h() panelsEnabled?（:1088-1092：disable bit 65536 未置位 && p()）
  → p(): 高温/手表脱落/无障碍/通话中/充电 → 拒绝展开（:1094-1109）
  → h(true) makeExpandedVisible :1132-1142
       mExpandedVisible=true; m1074a.b(true); k(true); d(!z); b(1,true)
  → XTCPanelView.a(0.0f, true) 展开动画（:1221/:1232）
  → 动画过程 s(): StatusBar.f(panelExpanded) ; 到位 e(): StatusBar.g(fullyExpanded)
[收起] CommandQueue.a(int) → StatusBar.a(i) → a(i,false,false,1)   :1176-1203
  → post StatusBar.aa() → XTCPanelView.a(0.0f,false)（:1200-1203）
  → onClosingFinished → StatusBar.F() :1608-1619 → m.c(true) 窗口复位
[自动隐藏] autohide: t=201326592；StatusBar.S/R/T/U/V（:1397-1437）
[物理返回] StatusBarWindowView.dispatchKeyEvent KEYCODE_BACK → StatusBar.m() :1581-1587（面板开则收）
[HALL 合盖] StatusBar.K() UEventObserverEx 监听 hall 设备 → r()（:616-633）
[屏幕灭] StatusBar 广播 SCREEN_OFF → Q() + r()（:328-332）
[滑动唤出] XTCPanelView 构造 :222-255 `com.xtc.wear.widget.a.b` 旋转表冠/滑动 → setExpandedHeight 累积 → 松手按 0.9*max 决定展开/收起
```

### B-5 通知链路（**只有音乐，没有通知列表**）

```
① system_server 绑定 XtcNotificationListenerService（manifest permission BIND_NOTIFICATION_LISTENER_SERVICE）
   NotificationListenerServiceEx.onBind → registerAsSystemService(...)   NotificationListenerServiceEx.java:18-33
② onNotificationPosted(sbn) → f.a(this).a(sbn)       XtcNotificationListenerService.java:27-30
   onNotificationRemoved(sbn) → f.a(this).b(sbn)      :32-35
③ NotificationDataCenter(f) 维护 ArrayList<g>，遍历回调 mo435a/b   f.java:50-70
④ 唯一注册方：MusicController(qs.tiles.music.a) 构造末尾 i()→f.a(ctx).a(this)  music\a.java:325-332
⑤ MusicController.mo435a(sbn):
   ├─ h.a(ctx, packageName) 白名单校验（音乐 App 列表，NotificationUtils.java:26-32、:107-119）
   ├─ c(sbn): extras 里 IS_MUSIC=true → 记录 f785a；PLAY_STATE 16/32 时刷新   music\a.java:65-88
   ├─ 提供：m433a() 歌曲名、b() 歌手名、c() 专辑封面、a() 播放状态(16播放/32暂停)、m436a/m438b
   └─ 触发 InterfaceC0022a 回调 → MusicShortCutView/tile 刷新   MusicShortCutView.java:77-95
⑥ 通知点击/媒体键：MusicController.a(key) → extras 中 PendingIntent.send()
   PLAY_PAUSE_INTENT/PREVIOUS_INTENT/NEXT_INTENT/SONG_NAME_INTENT   music\a.java:195-220 + music\b.java:9-21
   （SONG_NAME_INTENT 走 ActivityStarter.postStartActivityDismissingKeyguard，:204-205）
⑦ 通知移除：b(sbn) → 若同 key 则清 f785a + 回调 b()（隐藏音乐快捷）
```

**证据支持「无通知列表」**：`cancelNotification`/`getActiveNotifications`/`getRanking`/`isClearable`/HeadsUp 代码全仓 0 命中；布局里无 notification_row/list；`notification_template_*` 只是 androidx 库资源（`res\layout\notification_template_*`，未被本包业务引用）。

### B-6 状态图标 → 数据源映射（`XtcStatusBarView`）

| 图标（view id） | 数据源 | 更新/判定 | 证据 |
|---|---|---|---|
| `iv_signal_level` | `com.xtc.baseservice.k`（Signal/Telecom provider）+ `TelephonyManager.getSimState()` | `k.a(level)` 选 `ic_signal_zero..four`；无卡 `ic_status_bar_no_sim_card`；`string_no_sim_card`/`string_sim_protect`/`string_sim_locking` 文案 | `XtcStatusBarView.java:505-594`、`:577-585` |
| `iv_airplane_mode` | `NetworkUtils`/系统 `airplane_mode_on` + XTC `k.a` | 显示 `ic_airplane_mode_on_status_bar`，隐藏 signal；运营商文本=飞行模式 | `:524-552`、`:640-652` |
| `ll_data_traffic`/`iv_data_traffic_network_type`/`tv_data_traffic_*` | `k.b(networkType)` + `TrafficStats.getMobileRx/TxPackets` | 网络制式 `ic_data_traffic_network_{2g,3g,4g,e,g}`；上下行箭头 1s 轮询，`_normal`/`_changed` | `:672-684`、`:867-911` |
| `iv_wifi` | `com.xtc.baseservice.p`（WifiManagerEx）+ `ic_wifi_{zero..four}`/`ic_wifi_network_unavalible` | 回调 `p.f`/`p.a` | `:239-259` |
| `iv_hotspot` | `baseservice.d.a`（热点） | 可见/隐藏 | `:443-457` |
| `iv_im_state` | `com.xtc.baseservice.e.a`（IM 在线状态回调）→ `im_online/im_offline` | `mo1758a(boolean)` → `l(z)` | `:937-959`、`:420-425` |
| `iv_download` | `DownLoadHelper`（`com.xtc.systemupdate.downloadStart/downloadCompelete`） | start→显示，complete→隐藏 | `c.java:17-30`、`XtcStatusBarView.java:782-796` |
| `iv_hd_voice` | `FuncUtil.supportVolteForOverSea()` | `ic_hd_voice_oversea` / `ic_hd_voice` | `:383-394` |
| `tv_telecom_operators` | `baseservice.k` 运营商名；`setShowContext(1)` 时显示时间 | `DateFormat` 12/24h → `HH:mm`/`hh:mm`；`operator_no_support_telecom` | `:654-670`、`:923-926` |
| `iv_gps` | `LocationHelper`（LocationManager + `com.xtc.location.*` 广播） | `d.InterfaceC0105d.a` 回调 → 显示/隐藏 `ic_gps` | `e.java:16-70`、`XtcStatusBarView.java:318-328` |
| `iv_bluetooth` | **经典**：`BluetoothHelper`（adapter 使能 + `CONNECTION_STATE_CHANGED==2` + `xtc.matemode.action.MATE_ON/OFF`）；**BLE**：`com.xtc.baseservice.c.a`（BleClientManager）的 `XtcBleConnectStateListener.onConnectionStateChange` → `getConnectDeviceList` → 任一 `getConnectState()==2` | 显示/隐藏 (`f2781b`/`f2782c` 双标志)；图标 `ic_bluetooth` 与 `ic_bluetooth_mate`（mate 态） | `BluetoothHelper.java:32-85`、`XtcStatusBarView.java:734-764`、`:990-1056` |
| `iv_headset` | `com.xtc.statusbar.headset.d`（HeadsetManager）+ `IHeadset.b`/`g.c(battery)` | 电池图标 `ic_headset_battery_XX.png`（10 档） | `XtcStatusBarView.java:928-935`、`:832-865` |
| `iv_alarm_clock` | `AlarmClockHelper`（查询 `com.xtc.alarmclock` `clock.db` 的 `clock.isOpen` + XTC 广播 `CLOCK_ACTION`） | 有未关闭闹钟→显示 | `a.java:164-187`、`XtcStatusBarView.java:766-780` |
| `iv_battery`/`tv_battery_num` | `com.xtc.baseservice.a.d`（Battery/Charge 状态） | `a(level, charging, showPercent)` → `ic_battery_*`；文字 8sp Roboto-Bold；充电色 `expanded_clock_text_color` | `XtcStatusBarView.java:686-701` |
| `stv_tip` | `n` 接口（`a(String)`/`b(String)`）+ `HintWindow`? | 文字提示，1s 淡入/1s 淡出 | `StatusTipView.java:50-84`、`XtcStatusBarView.java:1058-1072` |
| `iv_international_roaming` | `baseservice.k` 漫游标志 | 显示时隐藏流量行 | `XtcStatusBarView.java:618-638` |

### B-7 锁屏交互（与状态栏耦合点）

- `StatusBar` 通过 `Dependency.b(com.android.systemui.statusbar.e.class)`（`StatusBarKeyguardViewManager`）执行 `hide/show/requestDismiss`：`postStartActivityDismissingKeyguard` 等 4 处（`:914`, `:960`, `:1007` 及 `StatusBar.a(long,View)` 唤醒 `wakeUp`）。
- `StatusBarState`：`SHADE=0`/`KEYGUARD=1`/`SHADE_LOCKED=2`（`g.java`），`StatusBar.b()/c()/f(i)` 切换（`:1543-1602`）。
- 面板在锁屏下行为：`XTCPanelView.mo518c()`：`barState!=0` 时不可拖动（`:628-630`）；`h()`/`e()` 中 `m575s()` 判断 `barState==1`（锁屏）决定是否禁用 QS 展开。
- 唤醒：`StatusBar.a(long j, View v)` 在 `mDozing` 时 `PowerManager.wakeUp(t, "com.android.systemui:NODOZE")`（`:1621-1628`）。

### B-8 关键系统 API / 常量

| 项 | 值/API | 证据 |
|---|---|---|
| 窗口 type（fallback） | `2000`（旧 TYPE_STATUS_BAR） | `m.java:91`；`SharedKeyConstants_Launcher.ai.y=2000` |
| 窗口 flags（fallback） | `-2138832824`（含 FLAG_NOT_FOCUSABLE/LAYOUT_IN_SCREEN 等组合） | `m.java:91` |
| bar 高度 | `@dimen/status_bar_height` | `StatusBar.m537a():716-723` |
| header 高度 | `@dimen/xtc_status_bar_height`（layout_pull_down） | `layout_pull_down.xml:9` |
| 面板最大高度 | `@dimen/qs_panel_max_height` = 180dp | `XTCPanelView.java:220` + `dimens.xml` |
| disable bit | `65536`（DISABLE_EXPAND/系统面板禁用） | `StatusBar.m556h():1088-1092`、`CommandQueue.java:57-72` |
| autohide bit | `201326592` | `StatusBar.java:121` |
| 振动 | `Vibrator.vibrate(250L, AudioAttributes(content=4,usage=13))`（面板展开） | `StatusBar.C():1521-1523` |
| 通知监听 | `NotificationListenerService` + `registerAsSystemService` | `NotificationListenerServiceEx.java:18-33` |
| 亮度/超时 | `Settings.System screen_brightness*`（W2 已用） | RISK R-85 |
| 面板展开动效插值 | `com.android.systemui.k.c/k.m`（自定义） | `XTCPanelView.java:773`、`PanelView.java:729` |
| 弹簧 | `androidx.dynamicanimation SpringAnimation(SCALE_X)` | `XTCPanelView.java:310-326` |

---

## C. UI 元素清单（布局 → view id → 类 → 文案 key → 中文原文 → 交互）

> 覆盖：折叠栏 / 下拉面板 / header 状态图标行 / 4 个提示浮层 / 音乐快捷 / 内嵌 toast。共 **61 个 view id**、**30 条文案 key**（另有 6 条运行时拼接文本）。
> 「文案 key」列：`—` 表示无静态文案（代码设置/图标/运行时）；原文取自 `values/strings.xml`（`_strings_zh.txt` 有全量 201 条）。

### C-1 折叠状态栏（`res\layout\status_bar.xml` + `super_status_bar.xml`）

| 布局 | view id | 类 | 文案 key | 中文原文 | 交互 |
|---|---|---|---|---|---|
| `status_bar.xml:2` | —（根） | `PhoneStatusBarView` | — | — | 无可见子 View；触摸转发 `StatusBar.a(ev)`；`onFinishInflate` 后 bar 高度由 `h()` 设 `status_bar_height` |
| `super_status_bar.xml:5-11` | —（heads_up_scrim） | `AlphaOptimizedView` | — | — | `visibility=gone`，背景 `@drawable/heads_up_scrim`；AOSP 遗留 |
| `super_status_bar.xml:12-15` | `status_bar_container` | `FrameLayout` | — | — | 承载 `CollapsedStatusBarFragment` 的 `status_bar` |
| `super_status_bar.xml:16-20` | —（include） | `XTCPanelView`（`xtc_status_bar_expanded.xml`） | — | — | `visibility=invisible`，待展开 |
| `super_status_bar.xml:21` | `tv_toast_inner` | `TextView`（`layout_inner_toast.xml:7`） | 运行时（`com.xtc.baseservice.util.i`） | 运行时文本 | 内嵌 toast，`visibility=gone`，`layout_gravity=center`，背景 `inner_toast_background` |

### C-2 下拉面板（`xtc_status_bar_expanded.xml` + `layout_pull_down.xml`）

| 布局 | view id | 类 | 文案 key | 原文 | 交互 |
|---|---|---|---|---|---|
| `xtc_status_bar_expanded.xml:3` | `notification_panel` | `XTCPanelView` | — | — | 下拉面板根；手势见 B-3；`background=transparent` |
| `layout_pull_down.xml:3` | `fl_root` | `FrameLayout` | — | — | 面板内容根；alpha/scale 动画对象；背景=取色服务（`d.e.a()`） |
| `layout_pull_down.xml:7` | `status_bar_view` | `XtcStatusBarView` | — | — | header 行；`@dimen/xtc_status_bar_height`；`marginStart=@dimen/dp_5`；**触摸被吞（返回 true）** |
| `layout_pull_down.xml:12` | `ocsv_root` | `QsOutCanScrollView` | — | — | 可滚动容器；`canSlideRightOut=false`；`marginTop=xtc_status_bar_height`；`overScrollMode=never`；`focusable` |
| `layout_pull_down.xml:21` | `quick_settings_panel` | `QSPanel` | — | — | 快捷开关容器（QS wave）；`visibility=visible` |

### C-3 header 状态图标行（`res\layout-xhdpi\statusbar_bar.xml`，由 `XtcStatusBarView` 构造 inflate）

| 行 | view id | 类型 | 文案 key / 图标 | 原文 | 交互 |
|---|---|---|---|---|---|
| 6 | `linear_layout` | LinearLayout | — | — | 左侧图标组，`marginStart=rounded_corner_radius(0dp)` |
| 12 | `iv_signal_level` | ImageView | `ic_signal_zero..four` | — | 信号格（`baseservice.k.a(level)`） |
| 19 | `iv_airplane_mode` | ImageView | `ic_airplane_mode_on_status_bar` | — | 飞行模式，默认 gone |
| 28 | `ll_data_traffic` | LinearLayout | — | — | 流量组，默认 gone |
| 34 | `iv_data_traffic_network_type` | ImageView | `ic_data_traffic_network_*` | — | 网络制式 |
| 40 | `fl_data_up_down` | FrameLayout | — | — | 上下行箭头容器 |
| 45 | `tv_data_traffic_transmitted` | ImageView | `ic_data_traffic_transmitted_normal/changed` | — | 1s 轮询 TrafficStats |
| 50 | `tv_data_traffic_received` | ImageView | `ic_data_traffic_received_normal/changed` | — | 同上 |
| 56 | `iv_international_roaming` | ImageView | `ic_international_roaming` | — | 漫游，显示时隐藏流量组 |
| 64 | `iv_wifi` | ImageView | `ic_wifi_*`/`ic_wifi_network_unavalible` | — | WiFi 状态 |
| 71 | `iv_hotspot` | ImageView | `icon_hot_spot` | — | 热点 |
| 78 | `iv_im_state` | ImageView | `im_offline`/`im_online` | — | IM 在线状态 |
| 85 | `iv_download` | ImageView | `ic_download` | — | 下载中图标 |
| 92 | `iv_hd_voice` | ImageView | `ic_hd_voice`/`ic_hd_voice_oversea` | — | VoLTE |
| 103 | `tv_telecom_operators` | TextView | `string_airplane_mode`/`string_no_sim_card`/`string_sim_protect`/`string_sim_locking`/`operator_no_support_telecom` | 飞行模式 / 未插卡 / SIM卡保护中 / SIM卡锁定中 / 不支持电信 | 居中；`setShowContext(1)` 时改显时间（HH:mm/hh:mm）；字号 10sp/13sp（`dimen_status_bar_text_size`/`dimen_status_bar_time_size`） |
| 118 | `ll_status_right` | LinearLayout | — | — | 右侧图标组 |
| 124 | `iv_gps` | ImageView | `ic_gps` | — | GPS 定位中 |
| 131 | `iv_bluetooth` | ImageView | `ic_bluetooth`/`ic_bluetooth_mate` | — | 蓝牙连接（BLE/经典，见 B-6） |
| 138 | `iv_headset` | ImageView | `ic_headset_battery_XX.png` | — | 耳机电量 |
| 145 | `iv_alarm_clock` | ImageView | `ic_alarm_clock` | — | 闹钟 |
| 157 | `iv_battery` | ImageView | `ic_battery_*` | — | 电量图标 |
| 164 | `tv_battery_num` | TextView | 运行时数字 | — | 电量百分比；8sp Roboto-Bold；充电时 `expanded_clock_text_color` 否则 `color_ff333333` |
| 172 | `stv_tip` | `StatusTipView` | 运行时（`n.a/b`） | 运行时 | 全屏文字提示，1s 淡入→1s 淡出；显示时隐藏左侧/运营商/右侧三组 |

### C-4 提示浮层（`PopWindowManager` 以 TYPE_SYSTEM_ALERT(2002) 添加）

| 布局 | view id | 类 | 文案 key | 中文原文 | 交互 |
|---|---|---|---|---|---|
| `layout_hint_window.xml:3` | `swipe_layout` | `SwipeDismissLayout` | — | — | 右边缘右滑→`onFinish`（dismiss false）；灭屏自动 dismiss；`viewDragHelper` alpha 随位移 |
| `layout_hint_window.xml:25` | `tv_title` | TextView | `init_wx_notice`（默认） | 温馨提示 | 代码 `a(title, content)` 覆盖；20sp 粗体 `#55dd7b` |
| `layout_hint_window.xml:36` | `tv_content` | TextView | 运行时 | 运行时 | `sp_13` 白色，ScrollView 内 |
| `layout_hint_window.xml:45` | `btn_i_know` | `LongHollowButton` | `i_know` | 我知道了 | 点击→`onDismissed(true)`；描边 `#55DD7B` |
| `view_disabled_forbid_tip.xml:3` | `swipe_layout` | `SwipeDismissLayout` | — | — | 同上 |
| `view_disabled_forbid_tip.xml:24` | `tv_title` | TextView | `app_forbid` | 应用禁用 | 20sp 粗体 `#55dd7b` |
| `view_disabled_forbid_tip.xml:35` | `tv_time` | TextView | `app_forbid_remind`（`%s`=应用名） | 目前“%s”处于禁用状态，无法使用 | 代码 `setAppName()` 填充 |
| `view_disabled_forbid_tip.xml:44` | `tv_hint` | TextView | `how_to_exit_app_forbid` | 如何退出应用禁用？ | 点击→`PopWindowManager.a(title,content)` 弹 `HintWindowLayout`（`how_to_exit_app_forbid_content`） |
| `view_disabled_forbid_tip.xml:54` | `btn_i_know` | `LongHollowButton` | `i_know` | 我知道了 | 点击→dismiss |
| `activity_anti_exit_tip.xml:3` | `swipe_layout` | `SwipeDismissLayout` | — | — | 右滑 dismiss |
| `activity_anti_exit_tip.xml:17` | `tv_title` | TextView | 运行时 | 运行时（防沉迷标题） | 17sp `#55dd7b` |
| `activity_anti_exit_tip.xml:24` | `tv_content` | TextView | 运行时（含 `$..$` 高亮） | 运行时 | `AntiAddictionExitLayout.a(String)` 解析 `$` 区间改色并删 `$`（`:67-104`） |
| `layout_watch_long_endurance_tip.xml:19` | `tv_title` | TextView | `low_power` / `watch_long_battery` | 电量不足 / 省电模式 | 20sp `#ff4dd865` |
| `layout_watch_long_endurance_tip.xml:26` | `tv_content` | TextView | `low_power_tip` / `watch_long_battery_tip` | 电量不足10%啦！为了家长能够及时联系你，建议尽快充电，不退出长续航模式。/ 通过关闭手表部分功能来延长电池续航能力。保留接听电话、接收微信通知。可在表盘界面下拉退出此模式 | 15sp 白色，`minHeight=160dp` |
| `layout_watch_long_endurance_tip.xml:36` | `dl_button` | `DoubleFlatButton` | `cancel`/`exit`（低电量） 或 `cancel`/`open`（长续航） | 取消/退出 或 取消/开启 | 左→`a()`；右→`b()` |
| `layout_tile_tip_view.xml:5` | `ll_tip_root` | LinearLayout | — | — | 149dp×56dp，背景 `bg_ble_prompt`（QS 磁贴提示，QS wave） |
| `layout_tile_tip_view.xml:14` | `tv_tip_title` | TextView | 运行时 | 运行时 | 15sp 白色 |
| `layout_tile_tip_view.xml:30` | `tv_tip_content` | TextView | 运行时 | 运行时 | 13sp `color_tip_gray` |
| `layout_tile_tip_view.xml:47` | —（TextView+箭头） | TextView | `com_see` | 去看看 | 点击进入设置（`ic_arrow_right`） |
| `layout_pull_up_volume_tip.xml:5` | `fl_volume_tip` | LinearLayout | — | — | 音量提示卡（`VolumeFuncSetting`） |
| `layout_pull_up_volume_tip.xml:16` | `tv_volume_tip_title` | TextView | `pull_down_volume_tip_title` | 长按进入音量调节 | 14sp 白色 |
| `layout_pull_up_volume_tip.xml:27` | `tv_volume_tip_desc` | TextView | `pull_down_volume_tip_content` | 可调节来电或媒体音量 | 12sp `color_tip_gray` |
| `layout_pull_up_volume_tip.xml:39` | `tv_see_click` | TextView | `go_see` | 去看看&gt; | 12sp `#cc00de71`，点击进入音量设置 |
| `layout_pull_up_color_corrector_tip.xml:5` | `ll_color_crrector_root` | LinearLayout | `string_open_color_corrector` | 色彩校正已开启 | 色彩校正提示 |
| `layout_pull_up_color_corrector_tip.xml:19/29` | — | TextView | `string_modify` | 去修改&gt; | 12sp 点击设置 |

### C-5 音乐快捷（`layout-xhdpi\layout_music_shortcut.xml`，`MusicShortCutView` inflate）

| 行 | view id | 类 | 文案 key | 原文 | 交互 |
|---|---|---|---|---|---|
| 6 | `ll_state_fold` | LinearLayout | `format_fold_song_name`（`%s`） | 继续播放: %s | 折叠态，点击展开；默认 gone |
| 19 | `tv_fold_song_name` | TextView | 运行时 | 运行时（歌曲名） | 13sp 白色，singleLine |
| 28 | `ll_state_unfold` | FrameLayout | `pull_down_music_tips`（默认占位） | 无歌曲播放 | 展开态，默认 visible |
| 35 | `tv_unfold_song_name` | `MarqueeTextView` | 运行时（`unknown_singer` 兜底） | 未知歌手 | 滚动歌名 |
| 49 | `iv_previous` | ImageView | `selector_previous_song` | — | 上一曲→`music\a.m437b()` |
| 55 | `iv_unfold_play_pause` | ImageView | `ic_action_play_song`/`2131230881/2` | — | 播放/暂停→`music\a.m439c()`（暂停时 `2131230881`，播放时 `2131230882`） |
| 61 | `iv_next` | ImageView | `selector_next_song` | — | 下一曲→`music\a.m442e()` |

### C-6 文案汇总（30 条 key + 6 条运行时）

| # | key | 中文原文 | 使用处 |
|---|---|---|---|
| 1 | `init_wx_notice` | 温馨提示 | `layout_hint_window.xml:29`（默认 title） |
| 2 | `i_know` | 我知道了 | HintWindow/DisabledForbid 按钮 |
| 3 | `app_forbid` | 应用禁用 | `view_disabled_forbid_tip.xml:28` |
| 4 | `app_forbid_remind` | 目前“%s”处于禁用状态，无法使用 | `DisabledForbidLayout.setAppName()` |
| 5 | `how_to_exit_app_forbid` | 如何退出应用禁用？ | `view_disabled_forbid_tip.xml:49` |
| 6 | `how_to_exit_app_forbid_content` | 请在最新版$${XTC}APP-更多-应用禁用$中，修改$本应用的禁用设置$，即可正常使用 | `DisabledForbidLayout.java:57` |
| 7 | `low_power` | 电量不足 | `LowPowerTipLayout.java:40` |
| 8 | `low_power_tip` | 电量不足10%啦！为了…不退出长续航模式 | `LowPowerTipLayout.java:41` |
| 9 | `watch_long_battery` | 省电模式 | `WatchLongEnduranceTipLayout.java:40` |
| 10 | `watch_long_battery_tip` | 通过关闭手表部分功能…下拉退出此模式 | `WatchLongEnduranceTipLayout.java:41` |
| 11 | `cancel` | 取消 | 两提示左键 |
| 12 | `exit` | 退出 | 低电量右键 |
| 13 | `open` | 开启 | 长续航右键 |
| 14 | `pull_down_volume_tip_title` | 长按进入音量调节 | `layout_pull_up_volume_tip.xml:20` |
| 15 | `pull_down_volume_tip_content` | 可调节来电或媒体音量 | `:31` |
| 16 | `go_see` | 去看看&gt; | `:43` |
| 17 | `com_see` | 去看看 | `layout_tile_tip_view.xml:47` |
| 18 | `string_open_color_corrector` | 色彩校正已开启 | `layout_pull_up_color_corrector_tip.xml:19` |
| 19 | `string_modify` | 去修改&gt; | `:29` |
| 20 | `string_airplane_mode` | 飞行模式 | `XtcStatusBarView.java:517/529/552` |
| 21 | `string_no_sim_card` | 未插卡 | `:558` |
| 22 | `string_sim_protect` | SIM卡保护中 | `:579` |
| 23 | `string_sim_locking` | SIM卡锁定中 | `:582` |
| 24 | `operator_no_support_telecom` | 不支持电信 | `:665` |
| 25 | `tip_temporarily_inoperable` | 暂时无法操作 | `StatusBar.java:1037`（包管理器禁用提示） |
| 26 | `format_fold_song_name` | 继续播放: %s | `MusicShortCutView.java:83` |
| 27 | `pull_down_music_tips` | 无歌曲播放 | `layout_music_shortcut.xml:39` |
| 28 | `notification_no_next_media` | 暂无可切换的内容 | `music\a.java:216` |
| 29 | `unknown_singer` | 未知歌手 | `music\a.java:224`（歌手兜底） |
| 30 | `screen_record_using_tips` | 使用录屏会录制手表屏幕上显示的内容，并将其保存为视频。在录制过程中，要注意保护隐私信息… | `screenrecord\a.java:361`（经 `HintWindowLayout` 显示） |
| R1 | 运营商名 | 运行时（Telephony） | `tv_telecom_operators` |
| R2 | 时间 | `HH:mm`/`hh:mm`（12/24h） | `setShowContext(1)` |
| R3 | 电量百分比 | `N%` / 充电 `N%+`（W2） | `tv_battery_num` |
| R4 | 歌曲名/歌手/封面 | 通知 extras | MusicController |
| R5 | 防沉迷标题/内容 | `PopWindowManager.a(str,str2)` 入参 | `AntiAddictionExitLayout` |
| R6 | 内嵌 toast | `com.xtc.baseservice.util.i` | `tv_toast_inner` |

---

## D. 复刻实现建议

### D-1 W2 手写骨架 vs 原实现：差距清单（必须补的 11 项）

| # | W2 现状（`src\main\java`） | 原实现 | 差距/风险 |
|---|---|---|---|
| 1 | `StatusBar`（191 行）直接 `WindowManager.LayoutParams(MATCH_PARENT, barHeight, TYPE_STATUS_BAR, FLAG_NOT_FOCUSABLE\|NOT_TOUCH_MODAL\|LAYOUT_IN_SCREEN\|LAYOUT_NO_LIMITS\|HARDWARE_ACCELERATED, TRANSLUCENT)` + `addView` | `StatusBarWindowManager(m)`：vendor `StatusBarServiceEx.getWindowLayout()`、状态机（flags/height/screenOrientation/setHasTopUi/fitsSystemWindows）、`updateViewLayout` | 窗口层级/触摸/全屏切换语义可能不同；**建议直接移植 `m`**（359 行，去掉 vendor 依赖后 ~180 行） |
| 2 | `StatusBar` 无 `IStatusBar` 注册 | `StatusBarServiceEx.registerStatusBar(CommandQueue,...)` 回放 disable/visibility/binders/icons | 没有 `disable(65536)`→面板仍可展开；系统状态（电量/信号图标 slots）不同步（本包用 XTC 图标，可不回放 icons，但 **disable/windowState 必须**） |
| 3 | `StatusBar` 无 `CommandQueue` 监听 | `CommandQueue.a(this)` :514-515 + 全部回调 | 系统事件（展开/收起/back/disable/IME）无法到达面板 |
| 4 | `StatusBarWindowView`（111 行）自写拖拽阈值判定 | 原版：`dispatchTouchEvent` 先判 doze/双击，`onInterceptTouchEvent` 交 `StatusBar.d()`，`onTouchEvent` 调 `DoubleTapHelper(e)`，未消费→`StatusBar.b(1,false)`，返回键→`StatusBar.m()`，音量键→`MediaSessionLegacyHelper` | 手势/按键路径不同（尤其返回键、音量键、灭屏） |
| 5 | `PhoneStatusBarView extends FrameLayout` 自绘时钟/电量 | `extends PanelBar`：`onTouchEvent→StatusBar.a(ev)`、`onIntercept→StatusBar.a(ev)`、`getBarTransitions()`、`h()` 设高度、`i()` 触发 `StatusBar.x()` | **原版折叠栏无可见内容**；若保留自绘需明确「新增」并处理与 header 重复 |
| 6 | `XTCPanelView extends FrameLayout`，200ms alpha 动画、`INVISIBLE` 初始 | `extends PanelView`（1110 行）：拖拽跟随、fling、falsing、长按 peek、清空区、`SpringAnimation`、`PathInterpolator(0.65,0,0.65,1)`、header/QS 同步缩放、`OutCanScrollView` 联动、表冠 `onGenericMotionEvent`、`PowerSave` 分支 | **核心体验差距最大**；建议先移植 `PanelView` 抽象 + `XTCPanelView` 覆盖方法（见 D-2） |
| 7 | `XtcStatusBarView`（81 行）自绘 HH:mm+电池 | 1073 行 RelativeLayout：20 个图标 + 4 个 contract + 2 个常量标志 + StatusTipView + TrafficStats 轮询 + BLE/耳机/经典蓝牙 | 图标/状态源缺失；**这是「状态栏可见信息」的全部** |
| 8 | `QSPanel`/`QsOutCanScrollView` 空壳 | `QSPanel` 426 行（host/tiles/detail/expanded/listening）+ `OutCanScrollView` | QS wave；本 SPEC 只给边界（`setHost/setExpanded/setListening/a(str)`） |
| 9 | `CommandQueue`（31 行）只占位 | 800 行 + `XtcIStatusBar` 兼容层（30+ AIDL 方法）+ 主线程 Handler 分发 | 需按 D-3 的 hidden API 方案实现 |
| 10 | 无 `CollapsedStatusBarFragment` | 18 行 Fragment（inflate status_bar） | 需要（否则 `status_bar_container` 空，`StatusBar.f1068a` 为 null） |
| 11 | 无 `StatusTipView` / `PopWindowManager` / 4 个提示布局 | 见 A-3 | W3 提示窗体系依赖 |

**其他 W2 缺失但原版存在的功能**（可在后续 wave）：bar transitions（`b/h`）、autohide、HALL 合盖、doze 双击唤醒、`setHasTopUi`、BarState（SHADE/KEYGUARD）、`Vibrator` 面板反馈、`Surface` 透明度（`StatusBarWindowView.onDraw` padding 擦除）、ActionMode/FloatingToolbar（文本选择）。

### D-2 新类清单（包名 + 类名 + 职责 + 预计 LOC）

| # | 包/类 | 职责 | 预计 LOC | 来源（原类） |
|---|---|---|---|---|
| 1 | `com.android.systemui.statusbar.phone.StatusBar`（重写） | 组件编排：窗口、CommandQueue、面板、锁屏、图标同步 | 700~900 | `StatusBar.java` 裁剪掉 XTC 业务（BLE/长续航/温度/埋点）后 |
| 2 | `...phone.StatusBarWindowManager` | 窗口状态机（bar/full、flags、方向、topUi） | 180~220 | `m.java` |
| 3 | `...phone.PanelView` | 面板抽象基类（手势/动画/高度） | 450~600 | `PanelView.java` 去掉 BoostFramework/清空区可选 |
| 4 | `...phone.XTCPanelView` | 手表面板（header+QS+动画+表冠） | 250~350 | `XTCPanelView.java` |
| 5 | `...phone.PanelBar` | 状态栏条抽象 | 80~120 | `PanelBar.java` |
| 6 | `...phone.PhoneStatusBarView` | 折叠栏（**建议保持空布局+触摸转发**） | 60~100 | `PhoneStatusBarView.java` |
| 7 | `...phone.StatusBarWindowView` | 窗口根（触摸/按键分发） | 150~220 | `StatusBarWindowView.java` 去掉 ActionMode |
| 8 | `...statusbar.CommandQueue` | IStatusBar 回调和事件总线 | 200~350 + D-3 方案 | `CommandQueue.java` + `XtcIStatusBar` 裁剪 |
| 9 | `...statusbar.phone.CollapsedStatusBarFragment` | 挂 status_bar | 30 | 原 18 行 |
| 10 | `com.xtc.statusbar.XtcStatusBarView`（重写） | header 图标行（20 图标 + 3 组容器 + tip） | 500~700 | 原 1073 行，按公开 API 重写数据源 |
| 11 | `com.xtc.statusbar.StatusTipView` | 文字提示 | 80 | 原 85 行 |
| 12 | `com.xtc.statusbar.SwipeDismissLayout` | 右滑消失 | 150 | 原 228 行 |
| 13 | `com.xtc.statusbar.PopWindowManager` | 浮层窗口管理（2002） | 180 | 原 230 行 |
| 14 | `com.xtc.statusbar.HintWindowLayout`/`AntiAddictionExitLayout`/`DisabledForbidLayout` | 3 个提示页 | 60/80/60 | 原 69/105/85 行 |
| 15 | `com.xtc.statusbar.LowPowerTipLayout`/`WatchLongEnduranceTipLayout` | 省电提示 | 50/50 | 原各 62 行 |
| 16 | `com.xtc.statusbar.status.BluetoothIconState`（新） | 蓝牙图标状态（经典+BLE 双通道） | 120~160 | 替代 `BluetoothHelper` + BLE 回调（见 D-4） |
| 17 | `com.xtc.statusbar.status.BatteryIconState` | 电量/充电 | 60 | 替代 `baseservice.a.d` |
| 18 | `...status.SignalIconState` | 信号/运营商/SIM/飞行 | 140 | 替代 `baseservice.k` |
| 19 | `...status.WifiIconState` | WiFi/热点 | 80 | 替代 `baseservice.p` |
| 20 | `...status.MiscIconState`（GPS/闹钟/下载/耳机/HD/IM/流量） | 其余图标聚合 | 200 | 替代 4 个 Helper + TrafficStats |
| 21 | `com.android.systemui.qs.tiles.music.MusicController` | 音乐通知 → 播放控制 | 120~160 | `music\a.java` 裁剪 |
| 22 | `com.xtc.statusbar.notification.NotificationDataCenter`/`ServeCallback`/`Utils` | 通知转发/过滤 | 60/15/100 | 原 f/g/h |
| 23 | `com.xtc.statusbar.notification.XtcNotificationListenerService` | NLS 入口 | 40 | 原 36 行 |
| 24 | `...qs.tiles.music.MusicShortCutView` + `layout_music_shortcut` | 音乐快捷 UI | 120 | 原 181 行 |

合计新增/重写 ≈ **3,600~4,600 LOC**（不含 QS 面板与 tile 体系）。

### D-3 hidden API 替代方案（`IStatusBar` / `IStatusBarService`）

原实现依赖：`com.android.internal.statusbar.IStatusBar`（`XtcIStatusBar extends IStatusBar.Stub`）、`IStatusBarService`（`StatusBar.f1064a`）、`StatusBarManagerService` 的 `registerStatusBar`（vendor `StatusBarServiceEx` 包装）。

| 方案 | 做法 | 优点 | 风险 | 建议 |
|---|---|---|---|---|
| **A. 拷贝 AIDL 源码（推荐）** | 把 AOSP 8.1 的 `IStatusBar.aidl`、`IStatusBarService.aidl`、`StatusBarIcon.aidl`、`IKeyguardCallback` 等 AIDL **源码**放进工程（platform 签名 / system app 可编）；`CommandQueue extends IStatusBar.Stub` | 与原版语义 1:1；编译期类型安全 | 需要 hideden API 的 `framework.jar` 或 AIDL 源码（不能只靠公开 SDK） | **首选**：确认 Lead 的构建方式；若已能编译 `com.android.internal.*`（W2 未用，但原包有），直接沿用 |
| **B. 手写 Binder + onTransact（无 AIDL）** | 自写 `Binder` 子类覆盖 `onTransact(code,data,reply,flags)`，按 AOSP `IStatusBar.Stub.TRANSACTION_*` 码表（`disable=1`… 具体码表见 AOSP）读取 Parcel；在 `ServiceManager.getService("statusbar")` 回调中作为 listener 交给系统 | 不依赖隐藏类；与 A 等价 | 码表需从 AOSP 8.1 `IStatusBar.java` 对照（原包 `XtcIStatusBar` 方法顺序即 AIDL 顺序，可反推）；容易错位 | 备选 |
| **C. 反射/动态代理** | `Class.forName("com.android.internal.statusbar.IStatusBar$Stub")` + `Proxy.newProxyInstance`（仅接口可代理；Stub 是类，需用 `Binder`+`onTransact`） | 不编译隐藏 API | Android 8.1 的 `IStatusBar` 是 AIDL `Stub` **抽象类**，`java.lang.reflect.Proxy` 无法直接代理；只能用 C+A 混合 | 不推荐独立使用 |
| **D. 降级：不注册 IStatusBar** | 只用公开 `StatusBarManager`（`expandNotificationsPanel` 等也是 @hide），改为本地事件总线：面板只由本地手势/代理 Provider 打开；系统侧 disable 通过 `StatusBarManager.getDisableFlags`?（@hide） | 最省事 | **会失去系统驱动行为**：锁屏时禁止下拉、全屏时隐藏、IME/back 等；手表上通常由 launcher/代理控制，可接受但需产品确认 | 兜底方案 |

补充：
- `StatusBarServiceEx`（`com.xtc.ultraframework.systemui`）是 XTC 对 `IStatusBarService` 的包装（`getBarService()`/`getWindowLayout()`/`registerStatusBar()`/`statusBarVisibilityChanged()`/`hideTransientBars()`）。复刻版**建议自写 `StatusBarServiceCompat`**：优先反射 vendor 服务，失败回退 AOSP `ServiceManager.getService("statusbar")` + 手写 AIDL 代理。调用点：`StatusBar.java:501-505,517,1453`、`m.java:84`、`XtcIStatusBar`。
- `CommandQueue.a` 接口用 default 方法（Java 8，原包已用），复刻可原样保留，避免每个监听者实现全部 30+ 方法。

### D-4 BLE 连接状态 → 公开 API 近似（Lead 指定必写）

原状态机（`XtcStatusBarView.java:990-1056` + `BluetoothHelper.java` + `IHeadset`）：

```
经典蓝牙:
  BluetoothHelper.m1733a(): 注册 3 个 action
    xtc.matemode.action.MATE_ON  → callback.b(true)  → XtcStatusBarView.h(true)  → 图标 ic_bluetooth_mate
    xtc.matemode.action.MATE_OFF → callback.b(false) → h(false)                  → 图标 ic_bluetooth
    adapter.CONNECTION_STATE_CHANGED:
        extra CONNECTION_STATE==2 → f2765a=true → callback.a(true)  → XtcStatusBarView.g(true)   → iv_bluetooth VISIBLE
        否则                     → f2765a=false→ callback.a(false) → g(false)（若 BLE 未连则 VISIBLE=GONE）
BLE（手机 App 连接）:
  com.xtc.baseservice.c.a(ctx).a(listener)            ← BleClientManager
  onConnectionStateChange(BleConnectResponse) → getConnectDeviceList(Task.b)
     list 为空: f2782c=false; 若 classic 未显示 → iv_bluetooth GONE
     任一 device.getConnectState()==2: f2782c=true → iv_bluetooth VISIBLE
隐藏条件: g(false) 时若 f2782c(BLE 已连) 为 true → 不隐藏（:746-751）
```

| 原状态 | 枚举/资源 | 公开 API 近似（无 BLE proto） | 说明 |
|---|---|---|---|
| 已连接手机（BLE） | `BleConnectResponse.connectState==2` → `iv_bluetooth` VISIBLE | `BluetoothAdapter.getBondedDevices()` + `BluetoothDevice.ACTION_ACL_CONNECTED`；或 `adapter.getProfileConnectionState(BluetoothProfile.GATT)`（API 18+，GATT=7） | 手表侧 BLE 由 App 建立，系统侧只能看到 ACL/GATT 连接；建议「任一已配对设备 ACL 连接 → 视为已连接」 |
| 已连接（经典/mate） | `MATE_ON` → `ic_bluetooth_mate` | `adapter.getProfileConnectionState(HEADSET/A2DP)` + `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED` | mate 是 XTC 私有语义（手表-手机 mate 模式），公开 API 无法区分 → **建议统一显示 `ic_bluetooth`**，或保留 MATE 广播（若设备仍发） |
| 断开 | `CONNECTION_STATE!=2` 且 BLE 空 → GONE | `ACTION_ACL_DISCONNECTED` / adapter `STATE_OFF` | — |
| 蓝牙关闭 | `BluetoothHelper.mo1734a()` false → 隐藏 | `adapter.isEnabled()` + `BluetoothAdapter.ACTION_STATE_CHANGED` | — |
| 连接中（可选） | 原包无独立"连接中"图标 | `BluetoothDevice.BOND_BONDING` 可近似 | 原版没有该状态，**不实现** |

> 实现建议：新建 `BluetoothIconState`（D-2 #16），内部用 `BluetoothAdapter` + 2 个广播（`ACTION_ACL_CONNECTED/DISCONNECTED`、`ACTION_STATE_CHANGED`）+ `getProfileConnectionState(GATT)` 轮询（面板展开时 1s/次，可用 `TrafficStats` 同款 rx 定时器）；对外暴露 `boolean isConnected()` 与 `int iconRes()`（`ic_bluetooth`/`ic_bluetooth_mate`）。**不要实现 BLE proto 编解码**（`com.xtc.client.*`、`Ble*Request/Response` 全部不实现）。
> **注意命名**：`com.xtc.baseservice.c` = `CameraInUseMonitor`（相机占用，状态栏**不需要**）；`com.xtc.baseservice.c.a` = `BleClientManager`（BLE 连接，状态栏需要）。两者仅包名相近（`baseservice\c.java` vs `baseservice\c\a.java`）。

### D-5 XTC SDK 依赖判定表（Lead 提供的可达性统计 + 本次逐类核对）

> 统计口径（Lead）：应用层 245 类；传递依赖 SDK 281 类 = 132 云控/上报（不实现）+ 149 本地能力；状态栏/面板相关集中在 `com.xtc.ui.widget.*`（55）、`com.xtc.baseservice.*`（23）、`com.xtc.ultraframework.*`（24，含 `StatusBarServiceEx/AudioManagerEx/DisplayManagerEx/BluetoothDeviceEx`）、`com.xtc.utils.*`（21）。

| 依赖组（本模块用到的） | 代表类 | 判定 | 复刻方案 |
|---|---|---|---|
| `com.xtc.ultraframework.systemui.*` | `StatusBarServiceEx`、`XtcRegisterStatusBarResult`、`XtcIStatusBar` | **必须自写** | D-3 的兼容层（300~400 LOC） |
| `com.xtc.ultraframework.os.*` | `UEventObserverEx`、`SystemProperties`、`PowerManagerEx` | 必须自写/公开 API | `UEventObserver` 私有 → 反射读 `/sys` 或删（HALL 合盖可后置）；`SystemProperties` 用 `android.os.SystemProperties`（@hide，同样需 AIDL/反射）→ 建议反射 `getBoolean/getInt` |
| `com.xtc.ultraframework.keyguard.*` | `KeyguardEventManager`、`IKeyguard*` | 必须自写（已在原包 AIDL） | 保留 `IKeyguardInterface`/`IKeyguardCallbackInterface`；`KeyguardEventManager` 用 `IWindowManager.registerKeyguardListener`（隐藏 API，走 D-3 同策略） |
| `com.xtc.ultraframework.service.notification.NotificationListenerServiceEx` | 同上 | 必须自写（80 行） | 直接拷贝（用 `registerAsSystemService`，API 21+ 公开） |
| `com.xtc.ultraframework.app.IAntiActivityManager`/`antiaddiction` | 防沉迷回调 | 可选/可删 | MusicController 的防沉迷回调可裁剪（RISK R-77 家族） |
| `com.xtc.ultraframework.media.AudioManagerEx`/`hardware.DisplayManagerEx`/`net.wifi.WifiManagerEx` | 音量/亮度/WiFi 扩展 | 公开 API 替代 | `AudioManager`/`Settings.System`/`WifiManager`；仅"长续航限制"类提示需保留本地判断 |
| `com.xtc.ultraframework.bluetooth.BluetoothDeviceEx` | 蓝牙设备扩展 | 公开 API 替代 | `BluetoothDevice`；**注意 Lead 已确认本包仅 1 处引用，归 mod-tools** |
| `com.xtc.baseservice.*`（23 类） | `k`(信号/运营商)、`a.d`(电量)、`p`(WiFi/网络)、`e.a`(屏幕/IM)、`f`(模式/长续航)、`d.a`(热点)、`j`(rx 工具)、`util.i`(toast)、`util.b`(初始化)、`util.n`(顶层 Activity)、`AppChangeReceiver`、`ScreenReceiver` | **必须手写精简版**（本模块用到的 8~10 个） | 全部改用公开 API + 本地状态机；**上报相关（`BehaviorUtil`）一律不实现**（RISK R-13 空壳） |
| `com.xtc.ui.widget.*`（55 类中的按钮/弹窗/Toast/TextView） | `LongHollowButton`、`DoubleFlatButton`、`DoubleFlatBtnDialog`、`LongHollowBtnWithTitleDialog`、`ToastUtil`、`DialogUtil`、`MarqueeTextView` | **必须自写**（约 8 类） | 纯自定义 View/弹窗，可简化：`LongHollowButton`≈带描边 TextView；`DoubleFlatButton`≈两个 TextView；`Dialog` 用 `PopupWindow`/`Dialog`；`MarqueeTextView` 用 `TextView.setEllipsize(MARQUEE)` |
| `com.xtc.utils.*`（21 类） | `ae`(时区/语言)、`ab`(时区)、`date.a`(时间格式)、`system.aa/z`(属性)、`ui.d/k`(dp→px/屏幕宽高)、`storage.l`(MMKV 封装)、`encode.f`(Gson) | 公开 API 替代 | `TimeZone/Locale`、`DateFormat`、`Resources.getDisplayMetrics()`、`SharedPreferences`（不用 MMKV，避免 JNI）、`Gson`（可选保留） |
| `com.xtc.statusbar.headset.*`（17 类） | 耳机协议栈 | **dead/可选** | 不实现；耳机电池图标 hidden（`iv_headset` 默认 gone） |
| `com.xtc.client.*`（BLE proto、`com.xtc.dataservice.*`） | `BleConnectResponse` 等 | **dead**（Lead 已确认） | 不实现；用 D-4 公开 API 近似 |
| `com.xtc.bigdata.*` / `com.xtc.httplib.*` / `com.xtc.dns.*` / `com.xtc.common.bigdata.*` | 见 `RISK-MAP.md` | **不实现**（R-01~R-50） | `BehaviorUtil` 空壳；`LogConfig.saveLog(false)` |

### D-6 W3（通知实现）接口边界与实施顺序

**边界原则：W3 只做「通知数据面」，不做「通知 UI 列表」**（原包没有）。W3 与 W2/QS 的唯一接口是：

```java
// W3 提供（新包 com.xtc.statusbar.notification）
public interface NotificationServeCallback { void onPosted(StatusBarNotification sbn); void onRemoved(StatusBarNotification sbn); }
public final class NotificationDataCenter {
    public static NotificationDataCenter get(Context c);
    public void register(NotificationServeCallback cb);   // MusicController 用
    public void unregister(NotificationServeCallback cb);
}
public final class NotificationUtils {
    public static boolean isMusic(StatusBarNotification sbn);          // 原 h.a(sbn)
    public static boolean isOngoing/NoClear/GroupSummary(...);         // 原 h.a(sbn,int)
    public static boolean canShow(Context c, String pkg);              // 原 h.a(ctx,pkg)（音乐 App 白名单）
    public static String key(sbn);                                     // 原 h.b(sbn)
    public static String category(sbn);                                // 原 h.m1772a
}
public final class MusicController implements NotificationServeCallback {
    public boolean hasMusic(); public boolean isPlaying();
    public String songName(); public String singer(); public String albumCover();
    public void playPause(); public void previous(); public void next();
    public void addOnMusicStateChangedListener(Callback cb); // 原 InterfaceC0022a
}
```

W2/QS 侧消费点：
- `MusicShortCutView`（`qs.tiles.music`）→ `Dependency.get(MusicController.class)`，监听 `mo449a()/b()`（`MusicShortCutView.java:77-95,108`）；
- `QSPanel`/`XTCTileLayout` 只需在 `setListening(true)` 时让 MusicController 开始刷新（`QSPanel.setListening` 已有钩子，`QSPanel.java:261`）；
- `StatusBar` 只保留一处：`music.a.f()`（`:374`，广播 `com.android.common.library.b.m` 时刷新）——复刻可去掉。

**W3 实施顺序（前 5 步）**：

1. **通知入口**：`XtcNotificationListenerService`（拷贝 36 行 + `NotificationListenerServiceEx` 80 行）+ `NotificationDataCenter` + `NotificationServeCallback` + `NotificationUtils`（先只做 `isMusic/key/category` 三个方法）。验收：`adb shell cmd notification post` 后数据中心能收到。
2. **音乐控制器**：`MusicController`（裁剪原 349 行为 ~160 行）+ `MusicNotificationConstants`（`music\b.java` 的 9 个 extras key）；接 `PendingIntent` 播放/暂停/上下曲；`NotificationUtils.canShow()` 白名单（`music\h.java:26-32` 的 19 个包名）。
3. **音乐快捷 UI**：`MusicShortCutView` + `layout_music_shortcut`（7 个 id）+ `Dependency` 注册；折叠态/展开态切换、歌名滚动、播放/暂停图标切换、空状态「无歌曲播放」。验收：QQ 音乐/网易云手表版发通知→面板出歌名、可切歌。
4. **提示浮层体系**（与通知无关但同属 W3 的"通知式提示"）：`PopWindowManager`(2002) + `SwipeDismissLayout` + `HintWindowLayout` + `DisabledForbidLayout` + `AntiAddictionExitLayout`；先接录屏提示（`screenrecord\a.java:359-362`）与"应用禁用"（`j.a(String)`）。
5. **对齐"通知零实现"**：写一份 `NOTIFICATION-DEAD.md`（或并入本 SPEC 附录）：明确不实现通知列表/角标/清除/免打扰/HeadsUp 的原因与证据（B-5），避免后续误加功能；同时把 `RISK-MAP` R-78 相关的 `music\a` 防沉迷回调（`IAntiActivityManager`）标记为可选裁剪。

---

## E. 依赖与风险

### E-1 RISK-MAP 关联（本模块涉及的上报/云控点）

| RISK | 内容 | 本模块位置 | 处置 |
|---|---|---|---|
| R-01/R-03/R-04 | BigData/Jacoco/BlockCanary 初始化 | `SystemUIApplication.a()/f()` | 不实现（W2 已避开） |
| R-19 | WatchDogs 广播 | Manifest | 不实现 |
| R-78-22 | `WatchScreenOn` 埋点 | `statusbar\a\a.java:61`（`XtcStatusBarView` 屏幕回调） | 删调用 |
| R-78-23/24 | 录屏行为埋点 | `statusbar\a\b.java:119`、`a\c.java:45` | 删调用 |
| R-78-14 | 长续航状态埋点 | `baseservice\f\a.java:35` | 删调用 |
| R-79 | HomePressReceiver | 不在本模块 | — |
| R-80 | 日志落盘 | `SystemUIApplication:42-44` | `saveLog(false)` |
| R-85 | 亮度/超时 Settings | QS/亮度条 | 保留 |
| R-66 | StatusBarProxyProvider | 本模块出口（`isStatusBarExpanded/collapsePanel/onImStateChanged`） | 保留（W2 已实现） |

### E-2 最容易遗漏 / 误删的 5 个点（交给 Lead 复核）

1. **`status_bar.xml` 是空的**：W2 自绘的时钟/电量不是原版行为；若 W3/W4 继续在 `PhoneStatusBarView` 画内容，会与 `XtcStatusBarView`（面板 header）重复显示时间/电量 → 需产品决策「折叠栏是否显示信息」。
2. **通知链路只服务音乐**：`NotificationDataCenter` 的**唯一注册方**是 `MusicController`（`music\a.java:331`）。若把「通知监听服务」当成"通知中心后端"来扩展，会引入原包没有的通知列表/角标/清除逻辑（需另立项）。
3. **`XTCPanelView` 的基类是 `PanelView`（1110 行）**：W2 直接继承 `FrameLayout` 会丢掉拖拽跟随/fling/falsing/长按 peek/清空区/表冠/动效同步；补基类是本 wave 最大的工作量（约 450~600 LOC）。
4. **`XtcStatusBarView` header 的 `setOnTouchListener(...→true)`**（`:362-367`）：header 区域会**吞掉所有触摸**，因此「在 header 上下拉」与「在 QS 区域下拉」手势入口不同；W2 的整窗拖拽实现与之一致性要重新验证。
5. **蓝牙图标是 BLE + 经典双通道 + 两个静态标志**（`f2781b`/`f2782c`）；只按 `BluetoothAdapter` 广播实现会在"BLE 已连但经典关闭"时错误隐藏图标。另注意 **`com.xtc.baseservice.c`（CameraInUseMonitor）≠ `com.xtc.baseservice.c.a`（BleClientManager）**。

### E-3 其它风险/建议

| 风险 | 说明 | 缓解 |
|---|---|---|
| `IStatusBar` 注册失败 | 若 D-3 方案 A/B 均不可行，系统不会给 disable/expand 回调 | 先按 D 方案 D 降级（本地手势 + 代理 Provider），再迭代 |
| `StatusBarServiceEx` 缺失 | `m.a()` 回退到 legacy 窗口参数（type 2000），可能被 system_server 拒绝（需 `INTERNAL_SYSTEM_WINDOW` 权限，原包有） | 保留 manifest 权限；窗口参数先按原版 fallback 常量 |
| `getWindowLayout` vendor 参数 | 影响状态栏高度/圆角/位置 | 复刻用 `status_bar_height` + fallback 参数，UI 回归确认 |
| `PanelView` 依赖 `com.android.systemui.k`（插值器）/`BoostFramework`（隐藏） | `BoostFramework` 可删（性能提示非必需）；插值器可自写 | 见 D-2 #3 |
| 提示浮层权限 | TYPE_SYSTEM_ALERT(2002) 在 Android 8 需 `SYSTEM_ALERT_WINDOW`（原包有） | 保留权限 |
| 通知监听绑定 | NLS 需 manifest `BIND_NOTIFICATION_LISTENER_SERVICE` + 用户授权（原包已注册） | 保留 manifest 项 |
| `XtcStatusBarView` 图标资源 | 共 60+ drawable（见附录） | 必须整包保留 `res\drawable*`；注意 `layout-xhdpi`/`layout-en-xhdpi` 有 **en 专属布局**（`layout_tile_tip_view.xml`） |
| 文本长度/换行 | `layout_watch_long_endurance_tip` `tv_content` `minHeight=160dp`；`low_power_tip` 含 `\n` | 改话术时需保持行数一致，避免溢出 |

---

## 附录 1：状态栏/通知相关资源清单（复刻必须保留）

| 类别 | 资源 | 证据 |
|---|---|---|
| 布局 | `status_bar.xml`、`super_status_bar.xml`、`xtc_status_bar_expanded.xml`、`layout-xhdpi\layout_pull_down.xml`、`layout-xhdpi\statusbar_bar.xml`、`layout_inner_toast.xml`、`layout_hint_window.xml`、`view_disabled_forbid_tip.xml`、`activity_anti_exit_tip.xml`、`layout_watch_long_endurance_tip.xml`、`layout_tile_tip_view.xml`（含 `layout-en-xhdpi`）、`layout_pull_up_volume_tip.xml`、`layout_pull_up_color_corrector_tip.xml`、`layout-xhdpi\layout_music_shortcut.xml` | `res\layout*` |
| 图标（XML） | `ic_signal_{zero..four}`、`ic_battery_{1..11}`、`ic_battery_charging_{1..11}`、`ic_battery_status_bar_{1..10}`、`ic_wifi_{zero..four}`、`ic_wifi_network_unavalible`、`ic_bluetooth`、`ic_bluetooth_mate`、`ic_data_traffic_network_{2g,3g,4g,e,g}`、`ic_data_traffic_{received,transmitted}_{normal,changed}`、`ic_airplane_mode_on_status_bar`、`ic_alarm_clock`、`ic_gps`、`ic_download`、`ic_hd_voice`、`ic_hd_voice_oversea`、`ic_international_roaming`、`ic_status_bar_no_sim_card`、`im_online`/`im_offline`、`selector_previous_song`/`selector_next_song`、`ic_action_play_song`、`ic_arrow_right` | `res\drawable*\` |
| 图标（PNG） | `ic_headset_battery_{01..10}.png`、`ic_headset_battery_white_{01..10}.png`、`ic_bluetooth_volume.png`、`ic_wifidownload.png` | `res\drawable-*\` |
| 文案 | `values\strings.xml` 中 `app_forbid*`、`how_to_exit_app_forbid*`、`i_know`、`init_wx_notice`、`low_power*`、`watch_long_battery*`、`cancel/exit/open`、`pull_down_volume_tip_*`、`go_see/com_see`、`string_*`、`format_fold_song_name`、`pull_down_music_tips`、`notification_no_next_media`、`unknown_singer`、`tip_temporarily_inoperable`（共 30 key，见 C-6） | `_strings_zh.txt` |
| 尺寸/颜色 | `status_bar_height`、`xtc_status_bar_height`、`qs_panel_max_height=180dp`、`dimen_status_bar_text_size=10sp`、`dimen_status_bar_time_size=13sp`、`dimen_status_bar_gap_2=2dp`、`sp_8/sp_10/sp_13`、`expanded_clock_text_color`、`color_ff333333`、`status_bar_red_color`、`color_55DD7B`、`color_black` | `res\values\dimens.xml/colors.xml` |
| 动效 | `PathInterpolator(0.65,0,0.65,1)`、`SpringForce(stiffness=350,damping=0.5,max=1.08)`、`qs_panel_expand/collapse_duration_ms`、`ViewDragHelper(1.0f)` | `XTCPanelView.java:301-326`、`PanelView.java:723-732` |

## 附录 2：证据索引（核心文件行号速查）

| 文件 | 关键行 |
|---|---|
| `StatusBar.java` | 490-539 启动/注册；560-590 makeStatusBarView；1088-1109 panelsEnabled/expandPanelEnable；1132-1142 makeExpandedVisible；1216-1234 展开；1236-1249 收起；1376-1437 autohide；1458-1467 建窗口；1521-1523 振动；1543-1602 状态/状态机 |
| `StatusBarWindowManager(m).java` | 70-100 窗口参数；124-168 高度/方向/全屏；204-292 各 setter |
| `PanelView.java` | 154-213 抽象方法；346-457 onTouchEvent；546-620 onIntercept；688-770 动画；782-807 setExpandedHeight |
| `XTCPanelView.java` | 213-256 构造/表冠；280-327 inflate/动画；379-401 复位；440-483 高度/联动；607-614 省电 |
| `CommandQueue.java` | 20 extends XtcIStatusBar；85-196 listener 接口；205-208 注册；561-800 分发 |
| `XtcIStatusBar.java` | 17 extends IStatusBar.Stub；23-270 override 空实现 |
| `XtcStatusBarView.java` | 198-386 构造/inflate（331 statusbar_bar）；355-360 contract 工厂；443-457 热点；460-502 attach/detach；505-594 信号/飞行；654-701 运营商/电量；734-764 蓝牙；798-820 setShow；867-911 流量；928-935 耳机；990-1056 BLE 监听 |
| `XtcNotificationListenerService.java` | 27-35 通知回调 |
| `NotificationDataCenter(f).java` | 36-48 bindService；50-70 分发 |
| `NotificationUtils(h).java` | 26-32 音乐白名单；34-105 flags/category；107-119 canShow |
| `music/a.java` | 65-146 通知处理；195-220 PendingIntent；236-349 注册/回调 |
| `BluetoothHelper.java` | 32-57 适配器/广播；59-85 连接状态/mate |
| `PopWindowManager(j).java` | 93-105 窗口参数；137-198 三个浮层；209-229 dismiss |
| `SwipeDismissLayout.java` | 126-129 ViewDragHelper；169-223 拖拽/消失 |
| `StatusTipView.java` | 50-84 提示动画 |

---

**交付自检**：A 类清单 95 条（必需 50/可选 33/dead 12）✅；B 原逻辑含启动链/窗口状态机/面板手势/时序/通知链/图标源/锁屏/API 常量 ✅；C UI 元素 61 个 view id + 30 条文案（另 6 条运行时）✅；D 新类 24 个/LOC 估算/hidden API 4 方案/BLE 公开 API 近似/SDK 依赖判定/W3 边界与 5 步顺序 ✅；E 风险与 5 个易漏点 ✅。
