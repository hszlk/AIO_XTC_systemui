# XTCSystemUI 逐模块「原逻辑」说明（MODULES）

> 代码根：`D:\DEBUG\apk\sysui\work\jadx17\sources\`（简写 `<SRC>`）；资源根：`D:\DEBUG\apk\sysui\nx\resources\`（简写 `<RES>`）。
> 每个模块给出：入口组件 → 关键类 → 调用链（3~8 层，逐层带 `文件:行号`）→ 依赖库 → 系统 API → **本模块内的云控/上报调用（关联 RISK-MAP 编号）**。
> 复刻原则：**链条中凡标注 ⚠️ 的层是上报/云控，可摘除；其余层是业务，必须保留。**

## 0. 顶层结构（先看这个）

```
AndroidManifest.xml
├─ application: com.android.systemui.SystemUIApplication (persistent=true, sharedUserId=android.uid.system)
├─ service: com.android.systemui.SystemUIService (exported=true)          ← 系统启动 SystemUI 的入口
├─ array config_systemUIServiceComponents = [Dependency, CommandQueue$CommandQueueStart, SystemBars]  ← 见 <RES>\res\values\arrays.xml:3-7
│     └─ SystemBars → 反射加载 config_statusBarComponent = com.android.systemui.statusbar.phone.StatusBar
│              （<RES>\res\values\strings.xml:55；<SRC>\com\android\systemui\SystemBars.java:34-51）
└─ 其余组件按功能挂载：SystemControlService / RecordingService / XtcNotificationListenerService /
   CodeCoverageReceiver / MonitorStartReceiver / FlashLightActivity / FlashLightSOSDialog /
   MediaProjectionPermissionActivity / PermissionListActivity / PrivacyUriActivity / SelfStartActivity /
   4 个 Provider（FileProvider / StatusBarProxyProvider / HttpDnsContentProvider / 无）
```

**关键事实**：`SystemUIApplication.a()`（`<SRC>\com\android\systemui\SystemUIApplication.java:84-94`）做了 6 件事，其中 4 件是第三方能力初始化：

| 顺序 | 代码 | 结论 |
|---|---|---|
| 1 | `com.android.systemui.d.d.a(this)`（`:86`） | 业务（SystemUI 组件容器） |
| 2 | `com.xtc.baseservice.util.b.a(this)`（`:87`） | 业务（baseservice 初始化，状态栏/音量依赖） |
| 3 | `b()` → `e()` → `LogConfig.builder().saveLog(true).module("ibwatch")...`（`:96-97`、`:42-44`） | ⚠️ 日志落盘（R-80） |
| 4 | `f()` → `com.android.systemui.c`（BigDataInitHelper）→ 120s 后 `BehaviorUtil.init`（`:100-104`、`:42-87` 的 c.java） | ⚠️ **云控总开关（R-01/R-02/R-03）** |
| 5 | `ac.a(ae.n())`（`:90`） | 工具类（时区/语言） |
| 6 | `JacocoUtils.getInstance().init(...)`（`:91`）+ `BlockCanary.a(this,...)`（`:92`）+ `TypedValueCompat.init`（`:93`） | ⚠️ 代码覆盖率（R-24）/卡顿采集（R-21） |

---

## M-1 SystemUIService 启动流程

| 项 | 内容 |
|---|---|
| 入口组件 | `com.android.systemui.SystemUIService`（`<RES>\AndroidManifest.xml:106-108`，`exported=true`，由 system_server 启动） |
| 关键类 | `SystemUIService`、`SystemUIApplication`、`SystemBars`、`SystemUIFactory`、`Dependency`、`CommandQueue` |
| 依赖库 | 无第三方（纯 framework） |
| 系统 API | `Service.onCreate`、`ClassLoader.loadClass`、`WindowManager`、`IStatusBarService` |

调用链（7 层）：

```
① ActivityThread 创建进程 → Application 构造
② SystemUIApplication.onCreate()            <SRC>\com\android\systemui\SystemUIApplication.java:46-82
   ├─ com.xtc.core.se2.c.a(this)            :48   （空实现 SecureCheck，R-56）
   ├─ super.onCreate(); setTheme(Theme_SystemUI) :49-50
   ├─ a()  ← 上述 6 件事（含 ⚠️ bigdata/jacoco/blockcanary）  :84-94
   ├─ SystemUIFactory.a(this)               :52   → 读 config_systemUIFactoryComponent
   │        （<RES>\res\values\strings.xml:56 = com.android.systemui.SystemUIFactory；
   │         <SRC>\com\android\systemui\SystemUIFactory.java:26-37 反射实例化）
   └─ 注册 BOOT_COMPLETED 高优先级 receiver：boot 完成后调用各 o.c()  :53-73
③ SystemUIService.onCreate()                <SRC>\com\android\systemui\SystemUIService.java:18-25
   └─ ((SystemUIApplication)getApplication()).c()  :21
④ SystemUIApplication.c()                   <SRC>\com\android\systemui\SystemUIApplication.java:106-108
   └─ a(getResources().getStringArray(m.c.config_systemUIServiceComponents))
⑤ SystemUIApplication.a(String[])           :114-155
   ├─ Class.forName(str).newInstance()      :131-132   ← 反射创建 3 个组件
   ├─ o.a(application); o.a = Dependency map :133-135
   ├─ o.mo261a()                            :136     ← SystemBars.mo261a() → b()
   └─ 若 boot_completed=1 → o.c()           :142-144
⑥ SystemBars.b()                            <SRC>\com\android\systemui\SystemBars.java:34-51
   ├─ getString(config_statusBarComponent)  :35 → "com.android.systemui.statusbar.phone.StatusBar"
   └─ loadClass(...).newInstance() → StatusBar :41-44
⑦ StatusBar.mo261a()（即 o.a()）→ start() → 创建 StatusBarWindowView / 注册 IStatusBarService / 起 QSPanel
```

⚠️ 本模块内的云控/上报：

| RISK 编号 | 内容 | 位置 |
|---|---|---|
| R-01/R-02/R-03 | `f()` → BigDataInitHelper → 120s Timer → `BehaviorUtil.init` | `<SRC>\com\android\systemui\SystemUIApplication.java:100-104` |
| R-04 | `JacocoUtils.init` | `<SRC>\com\android\systemui\SystemUIApplication.java:91` |
| R-21 | BlockCanary 卡顿采集初始化 | `<SRC>\com\android\systemui\SystemUIApplication.java:92` |
| R-80 | `LogConfig.builder()...saveLog(true)` | `<SRC>\com\android\systemui\SystemUIApplication.java:42-44` |
| R-56 | `com.xtc.core.se2.c.a(this)`（空安全自检） | `<SRC>\com\android\systemui\SystemUIApplication.java:48` |

---

## M-2 状态栏与下拉通知中心（下拉面板 / 快捷开关容器）

| 项 | 内容 |
|---|---|
| 入口组件 | `StatusBar`（`config_statusBarComponent`）；`SystemUIService` 拉起 |
| 关键类 | `com.android.systemui.statusbar.phone.StatusBar` / `PhoneStatusBarView` / `StatusBarWindowView` / `PanelView` / `XTCPanelView` / `CollapsedStatusBarFragment`、`com.android.systemui.qs.QSPanel` / `XTCTileLayout` / `TileGridLayout`、`com.android.systemui.statusbar.CommandQueue` |
| 依赖库 | `rxjava`、`androidx.appcompat`、`lottie`（assets `pull_up_setting/*.json`）、`com.google.zxing`(仅隐私页) |
| 系统 API | `WindowManager.LayoutParams(TYPE_STATUS_BAR)`、`IStatusBarService.registerStatusBar`、`StatusBarManager`、`GestureDetector`、`Vibrator`、`IPackageManager`、`PowerManager.isPowerSaveMode()` |

调用链（6 层）：

```
① StatusBar.start()（o.a() 覆写）              <SRC>\com\android\systemui\statusbar\phone\StatusBar.java（全文 1863 行）
② 创建 StatusBarWindowView（含 XTCPanelView 下拉面板） → addView(TYPE_STATUS_BAR)
③ 注册 IStatusBarService：ServiceManager.getService("statusbar") → registerStatusBar(...)
   （StatusBar 实现 CommandQueue.a 回调，处理 disable flags / expand notification panel 等）
④ 下拉展开：StatusBarWindowView 手势 → XTCPanelView 展开 → QSPanel / XTCTileLayout 显示快捷开关
   ├─ <SRC>\com\android\systemui\statusbar\phone\XTCPanelView.java:608（isPowerSaveMode() 判断省电模式 UI）
   └─ <SRC>\com\android\systemui\qs\XTCTileLayout.java:154/189-192/219（亮度条读 Settings.System）
⑤ 快捷开关列表来源：Settings.Secure "qs_auto_tiles"（<SRC>\com\android\systemui\qs\a.java:66）
   + 默认值 <RES>\res\values\strings.xml:163-164
     quick_settings_tiles_default = wifi,bt,flashlight,ringMode,powerSave,screenRecord,airplane
⑥ Tile 点击 → 各 QSTile（见 M-10）→ 改系统设置 / 起 Activity / 调系统服务
```

⚠️ 本模块内的云控/上报：

| RISK 编号 | 内容 | 位置 |
|---|---|---|
| R-78-02 | 快捷开关点击埋点 `BehaviorUtil.clickEvent(ctx, C0049b.e)` | `<SRC>\com\android\systemui\qs\tiles\a.java:94` |
| R-78-22 | 亮屏埋点 `WatchScreenOn` | `<SRC>\com\xtc\statusbar\a\a.java:61` |
| R-76 | 录屏开关受 FunSwitch 控制 | `<SRC>\com\android\systemui\screenrecord\c.java:272-281` |
| R-85 | 亮度/超时写 `Settings.System`（本地，保留） | `<SRC>\com\android\systemui\qs\XTCTileLayout.java:154-219` |

---

## M-3 锁屏 / Keyguard

| 项 | 内容 |
|---|---|
| 入口组件 | **无独立 Activity/Service**；通过 AIDL 对外提供 `IKeyguardInterface`，由 `KeyguardEventManager` 监听 system_server 的 keyguard 事件 |
| 关键类 | `com.android.systemui.IKeyguardInterface`（244 行 AIDL 生成类）、`IKeyguardCallbackInterface`（148 行）、`com.xtc.ultraframework.keyguard.KeyguardEventManager`；权限 `com.xtc.systemui.keyguard.permission.KEYGUARD_SERVICE`（`<RES>\AndroidManifest.xml:56`） |
| 依赖库 | 无 |
| 系统 API | `IWindowManager.registerKeyguardListener(IKeyguardEventCallback)`、`ActivityManagerNative/ActivityTaskManager.setLockScreenShown()`、`PowerManager.isInteractive()` |

调用链（5 层）：

```
① 系统（window/ActivityManager）持有 keyguard 状态
② KeyguardEventManager.getInstance()                        <SRC>\com\xtc\ultraframework\keyguard\KeyguardEventManager.java:43-52
   ├─ mService = IWindowManager.Stub.asInterface(ServiceManager.getService("window"))  :17
   └─ registerListener(Listener) → mService.registerKeyguardListener(mCallback)        :54-61
③ KeyguardEventManager.Listener 回调：hideKeyguard/showKeyguard/
   onStartedGoingToSleep/onFinishedWakingUp/onLaunchFromHall      :20-41
④ SystemUI 侧（StatusBar / 锁屏浮层）注册 Listener 并更新状态
⑤ setLockScreenShown(boolean) → ActivityTaskManager/ActivityManagerNative  :72-80
① 外部（launcher/设置）通过 com.xtc.systemui.keyguard.permission.KEYGUARD_SERVICE
   绑定 IKeyguardInterface → isKeyguardLocked()/isKeyguardSecure()/requestDismissKeyguard()
   （<SRC>\com\android\systemui\IKeyguardInterface.java:41-49）
```

结论：**本包没有 AOSP 的 KeyguardViewMediator/KeyguardUpdateMonitor**（反编译树中不存在 `com\android\systemui\keyguard` 包，全仓 grep `KeyguardViewMediator|KeyguardUpdateMonitor` = 0 命中），锁屏 UI 由 XTC 框架/launcher 承担，SystemUI 只做事件桥接。复刻版**必须保留 `IKeyguardInterface`/`IKeyguardCallbackInterface` 两个 AIDL 类与权限名**，否则外部绑定会 `ClassNotFoundException`。

⚠️ 本模块内无明显云控/上报调用（无 BehaviorUtil 调用点）。注意不要误删 `IKeyguard*`。

---

## M-4 通知监听（NotificationListener）

| 项 | 内容 |
|---|---|
| 入口组件 | `com.xtc.statusbar.XtcNotificationListenerService`（`<RES>\AndroidManifest.xml:147-153`，`permission=android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`，action `android.service.notification.NotificationListenerService`） |
| 关键类 | `XtcNotificationListenerService`、`com.xtc.ultraframework.service.notification.NotificationListenerServiceEx`、`com.xtc.statusbar.f`（分发器）、`NotificationManagerEx` |
| 依赖库 | 无第三方 |
| 系统 API | `NotificationListenerService.onNotificationPosted/onNotificationRemoved`、`StatusBarNotification`、`INotificationManager` |

调用链（5 层）：

```
① system_server NotificationManagerService 绑定（因 BIND_NOTIFICATION_LISTENER_SERVICE + intent-filter）
② XtcNotificationListenerService.onCreate()                <SRC>\com\xtc\statusbar\XtcNotificationListenerService.java:15-19
   └─ super.onCreate()  (NotificationListenerServiceEx)
③ onNotificationPosted(sbn)                                  :27-30
   └─ f.a(this).a(sbn)         ← 本包内通知分发（状态栏图标/音乐卡片/未读）
④ onNotificationRemoved(sbn)                                 :32-35
   └─ f.a(this).b(sbn)
⑤ 分发到 StatusBar 更新图标；音乐控制走 com.android.systemui.qs.tiles.music.*（rx + dataservice）
```

⚠️ 本模块内：`com.xtc.statusbar.h.java:114` 读 meta-data `com.xtc.music.notification_version` 判断音乐通知版本（本地）；无上报调用。

---

## M-5 屏幕录制（MediaProjection）

| 项 | 内容 |
|---|---|
| 入口组件 | `com.android.systemui.media.MediaProjectionPermissionActivity`（`<RES>\AndroidManifest.xml:103-105`，`exported=true`）；`com.android.systemui.screenrecord.RecordingService`（`:120-123`，`exported=false`，`foregroundServiceType=mediaProjection`） |
| 关键类 | `RecordingService`、`screenrecord.a/b/c/d/e/f/g`、`RecordingFloatView`、`ScreenRecordAudioTipsLayout`、`ScreenRecordAudioSource`、`config/RemoteRecordingConfig`、`MediaProjectionPermissionActivity`、`com.xtc.statusbar.a.b`（录屏行为埋点）、`com.xtc.baseservice.j`（rx 工具） |
| 依赖库 | `rxjava`、MediaRecorder（framework） |
| 系统 API | `MediaProjectionManager`（`MANAGE_MEDIA_PROJECTION`/`READ_FRAME_BUFFER`）、`MediaRecorder`（`setVideoSource(SURFACE)`/`setAudioSource`）、`NotificationManager`（前台服务通知）、`StorageManager`（空间校验）、`Settings.Global` |

调用链（8 层）：

```
① 外部（launcher/设置/快捷开关）startActivity(MediaProjectionPermissionActivity) 或 startService(RecordingService.a(ctx, useAudio))
   <RES>\AndroidManifest.xml:103-105、:120-123
② RecordingService.onStartCommand(intent)                   <SRC>\com\android\systemui\screenrecord\RecordingService.java:92-130
   ├─ action = com.android.systemui.screenrecord.START / STOP / STOP_FROM_NOTIF  :42-44
   ├─ m455a() 建通知渠道 "screen_record"                                        :34
   └─ action==START → this.f813a=true → j.a(Supplier).c(...).a(...)              :114-130
③ com.android.systemui.screenrecord.a（录屏会话）：
   ├─ 校验空间/时长：RemoteRecordingConfig DEFAULT_RECORDING_DURATION_MS=60000 / DEFAULT_STORAGE_SPACE_MB=100
   │        <SRC>\com\android\systemui\screenrecord\config\RemoteRecordingConfig.java:5-24
   ├─ 校验功能开关：d.a(funSwitch.getSwitchStatus())  ⚠️ FunSwitch
   │        <SRC>\com\android\systemui\screenrecord\c.java:272-281
   └─ Settings.Global 音频开关：<SRC>\com\android\systemui\screenrecord\a.java:319
④ MediaProjectionManager.getMediaProjection(resultCode, data) → createVirtualDisplay(...)
⑤ MediaRecorder 录 Surface + 可选音频（ScreenRecordingAudioSource）
⑥ 前台通知（RecordingFloatView 悬浮按钮 + RecordingService 通知）→ 用户 STOP
⑦ 保存文件：写 MediaStore/相册（provider P-14 `content://com.xtc.album.provider`）
⑧ 状态回传：com.xtc.baseservice / 广播 → StatusBar 更新图标
```

⚠️ 本模块内的云控/上报：

| RISK 编号 | 内容 | 位置 |
|---|---|---|
| R-76 | FunSwitch 控制录屏入口是否开放 | `<SRC>\com\android\systemui\screenrecord\c.java:272-281` |
| R-78-23/24 | 录屏行为埋点（时长/结果/失败码） | `<SRC>\com\xtc\statusbar\a\b.java:119`、`a\c.java:45` |
| R-69 | meta-data `supportScreenRecordAudio=1`（被外部读取，**不可删**） | `<RES>\AndroidManifest.xml:144-146` |
| R-68 | `moduleSwitch` 中录屏 module=3547（被外部读取，**不可删**） | `<RES>\AndroidManifest.xml:138-140` |

---

## M-6 手电筒 / SOS 灯

| 项 | 内容 |
|---|---|
| 入口组件 | `com.android.systemui.flashlight.FlashLightActivity`（`<RES>\AndroidManifest.xml:89-93`，`showWhenLocked=true`、action `android.settings.FLASH_LIGHT`）；`FlashLightSOSDialog`（`:94-102`，`XtcStayFront=true`） |
| 关键类 | `FlashLightActivity`、`FlashLightSOSDialog`、`FlashLightGuideDialog`、`com.xtc.baseservice.b.c`（埋点）、`com.android.systemui.statusbar.policy.FlashlightControllerDelegate`/`g`、`com.xtc.funlist.IXtcFunList`（功能点校验） |
| 依赖库 | `androidx.viewpager`(自研 com.android.common.library.widget.viewpager)、`rxjava` |
| 系统 API | `CameraManager.setTorchMode`/`Camera`（闪光灯）、`WindowManager`（锁屏上显示）、`Vibrator`、`PowerManager` |

调用链（6 层）：

```
① 用户长按/语音/快捷开关 → startActivity(FlashLightActivity)（action android.settings.FLASH_LIGHT）
   <SRC>\com\android\systemui\flashlight\FlashLightActivity.java:54
② FlashLightActivity.onCreate → 全屏亮白 ViewPager + 亮度控制 → camera torch 常亮
   （<SRC>\com\android\systemui\flashlight\FlashLightActivity.java:47、:137-167、:260-285）
③ SOS 模式：启动 FlashLightSOSDialog（常驻前台，meta-data XtcStayFront）
④ 退出/计时：Handler + Message（180s 自动关）<SRC>\com\android\systemui\flashlight\FlashLightActivity.java:62
⑤ 功能可用性校验：com.xtc.funlist.IXtcFunList（FUNC_SUPPORT_*）
⑥ 埋点：com.xtc.baseservice.b.c.a(ctx, duration) / a(ctx)  ⚠️
```

⚠️ 本模块内的云控/上报：

| RISK 编号 | 内容 | 位置 |
|---|---|---|
| R-78-09 | `customEvent("launcher_operation_flashLight", {flashTime})` | `<SRC>\com\xtc\baseservice\b\c.java:15` |
| R-78-10 | `customEvent("launcher_flashlight_sos", null)` | `<SRC>\com\xtc\baseservice\b\c.java:19` |
| R-70 | `XtcStayFront` meta-data（framework 读取，**不可删**） | `<RES>\AndroidManifest.xml:99-101` |

---

## M-7 音量面板

| 项 | 内容 |
|---|---|
| 入口组件 | 无独立组件；由下拉面板 `XTCPanelView`/`QSPanel` 内嵌 `VolumeFuncSetting`（`com.android.systemui.qs.tiles.VolumeFuncSetting`，703 行） |
| 关键类 | `VolumeFuncSetting`、`SettingSeekBar`、`com.xtc.baseservice.a`（AudioManager 封装）、`com.xtc.baseservice.i/j/o`、`com.xtc.ultraframework.media.AudioManager.AudioManagerEx`、`IVolumeControllerEx`、`com.xtc.baseservice.f.b`（长续航限制回调） |
| 依赖库 | `rxjava` |
| 系统 API | `AudioManager`（STREAM_MUSIC/STREAM_VOICE_CALL/STREAM_SYSTEM/STREAM_ALARM/STREAM_RING）、`BroadcastReceiver(android.media.VOLUME_CHANGED_ACTION)`、`Settings.System("vibrate_when_ringing")`、`BluetoothHeadset`（A2DP 音量） |

调用链（5 层）：

```
① 下拉面板 → QSPanel 创建 VolumeFuncSetting  <SRC>\com\android\systemui\qs\tiles\VolumeFuncSetting.java:105-110
② VolumeFuncSetting 注册广播：android.media.VOLUME_CHANGED_ACTION、
   com.xtc.i3launcher.VOLUME_UPDATE_ACTION、android.settings.VOLUME_SETTINGS          :33-42
③ SeekBar 拖动 → com.xtc.baseservice.a/AudioManagerEx.setStreamVolume(...)
④ 限制条件（长续航/高温/睡眠/上课模式）→ com.xtc.baseservice.f.b 回调 → 弹提示
   （文案 watch_long_endurance_unsupport_edit_volume 等，<SRC>\com\xtc\baseservice\f.java:143-262）
⑤ 蓝牙已连接时同步 A2DP 音量（BluetoothDeviceEx）
```

⚠️ 本模块内：`<SRC>\com\android\systemui\qs\tiles\VolumeFuncSetting.java:21` 引用 `SwitchNetworkManager`（网络调度类，属云控库 R-41，本模块未调用其方法）；无直接 BehaviorUtil 上报。

---

## M-8 省电模式 / 长续航

| 项 | 内容 |
|---|---|
| 入口组件 | PowerSaveTile（`com.android.systemui.qs.tiles.e`，class 注释 `Apple PowerSaveTile`）、`LowPowerTipLayout`、`WatchLongEnduranceTipLayout`；系统服务 `com.xtc.baseservice.l`（setExactPowerSave）、`com.xtc.baseservice.f` |
| 关键类 | `PowerSaveModeControllerImpl`（`statusbar/policy/m.java`）、`PowerSaveModeController`（`policy/l.java`）、`com.xtc.baseservice.a.b`（电量查询）、`com.xtc.baseservice.l`、`com.xtc.baseservice.f`（长续航状态机）、`ILauncherProxyInterface.setWatchToWatchPowerSaveMode` |
| 依赖库 | `rxjava`、`lottie`（`pull_up_setting/power_save.json`） |
| 系统 API | `SystemProperties`（`persist.sys.wleswitch`）、`PowerManager.isPowerSaveMode()`、`BatteryManager` |

调用链（7 层）：

```
① 下拉面板 QuickSetting → PowerSaveTile.handleClick()          <SRC>\com\android\systemui\qs\tiles\e.java:62-95
② l.a()/l.b()   ← PowerSaveModeControllerImpl
   ├─ SystemProperties.getBoolean("persist.sys.wleswitch", false)   <SRC>\com\android\systemui\statusbar\policy\m.java:11、:31-33
   └─ com.xtc.baseservice.a.b.a(ctx).a() <= 10（电量≤10 才能开启）  :40-43
③ l.a(true/false) → Dependency.b(statusbar.b.class).mo494a(z)        :35-38
④ com.android.systemui.statusbar.c → ILauncherProxyInterface
   setWatchToWatchPowerSaveMode(z)                                  <SRC>\com\android\systemui\statusbar\c.java:52
   （失败 LogUtil.e :56）
⑤ launcher/系统服务切换省电模式 → 写 persist.sys.powersave.*
   （<SRC>\com\xtc\utils\system\z.java:10、:102）
⑥ 长续航状态机：com.xtc.baseservice.f（watch_long_endurance） → 限制音量/音乐/操作并弹提示
⑦ 埋点：com.xtc.baseservice.f.a  ⚠️
```

⚠️ 本模块内的云控/上报：

| RISK 编号 | 内容 | 位置 |
|---|---|---|
| R-78-11~14 | 长续航状态/可用时长/点击埋点 | `<SRC>\com\xtc\baseservice\f\a.java:17-35` |
| R-72 | `persist.sys.wleswitch` 开关（本地系统属性，保留） | `<SRC>\com\android\systemui\statusbar\policy\m.java:11` |
| R-68 | `queryModuleSwitchByInt` 控制长续航可用性（**降级本地默认值**） | `<SRC>\com\xtc\system\account\h.java:134-140` |

---

## M-9 BLE 通信（手表 ↔ 手机/耳机）

| 项 | 内容 |
|---|---|
| 入口组件 | 无独立组件；由 `StatusBar`/`com.xtc.statusbar.headset.*`/`com.xtc.ultraframework.bluetooth.*` 常驻使用；proto 定义在 APK 根 `*.proto`（`BleScanRequest/BleConnectRequest/BleCommunicateRequest/...`） |
| 关键类 | `com.xtc.ultraframework.bluetooth.{BluetoothAdapterEx, BluetoothDeviceEx, BluetoothA2dpEx, BluetoothHeadsetEx, BluetoothOfflineEx, BluetoothAutoCloseManager}`；`com.xtc.statusbar.headset.{HeadsetConnectManager(1804 行), HeadsetReceiver, ...}`；`com.xtc.statusbar.BluetoothHelper`；proto 生成类 `com.xtc.dataservice.api.*Proto`（`DomainProto` 1488 行、`RequestProto` 2451 行）+ `com.google.protobuf.*`；`retrofit2.converter.protobuf.ProtoConverterFactory` |
| 依赖库 | `protobuf-javalite`、`retrofit2`、`okhttp3`、`rxjava` |
| 系统 API | `BluetoothAdapter.enable/disable/startDiscovery/setScanMode`、`BluetoothDevice`、`BluetoothA2dp`、`BluetoothHeadset`、`BluetoothGatt`（经 framework 扩展）、`content://com.xtc.setting/HAS_BLE_CONNECTED` |

调用链（6 层）：

```
① 开机/状态栏初始化 → com.xtc.statusbar.headset.HeadsetConnectManager（1804 行，BLE 耳机连接状态机）
② 蓝牙状态监听：HeadsetReceiver（广播） + BluetoothAdapterEx
   ├─ setDiscoverableTimeout(i)/setScanMode(23, i)   <SRC>\com\xtc\statusbar\b\a.java:35-43
   └─ hasConnectedBle：getType(content://com.xtc.setting/HAS_BLE_CONNECTED)  <SRC>\com\xtc\statusbar\b\a.java:26-33
③ 业务数据：BleScanRequest/BleConnectRequest/BleCommunicateRequest(Response) 
   → protobuf 序列化（com.xtc.dataservice.api.request.RequestProto.Ble*）
④ 传输：IM/HTTP 通道（com.xtc.httplib + com.xtc.im）→ 手机端
⑤ 回包：BleBindStateRequest/BleServerVersionResponse 等 proto 解析
⑥ 状态栏显示：蓝牙图标/电量（StatusBar / com.xtc.statusbar.headset.*）
```

⚠️ 本模块内：BLE 本身是**业务核心，不可删**；关联风险仅为随包的网络库（R-35/R-36 若保留 httplib 会拦截请求）与 `com.xtc.statusbar.BluetoothHelper` 中的日志。**注意**：`Ble*Request.proto` 与 `ProtocolManagerEx` 必须保留。

---

## M-10 快捷开关（Quick Settings Tiles）

| 项 | 内容 |
|---|---|
| 入口组件 | `QSPanel` / `XTCTileLayout` / `TileGridLayout`；开关定义 `<RES>\res\values\strings.xml:163-164` = `wifi,bt,flashlight,ringMode,powerSave,screenRecord,airplane`；动画资源 `assets/pull_up_setting/{bluetooth,power_save,vibration,bell,bell_vibration,silence,wifi}.json` |
| 关键类 | `com.android.systemui.qs.tiles.{a(通用点击埋点基类), b(BluetoothTile), d(FunSwitch 基类), e(PowerSaveTile), f(RingModeTile), h(WifiTile), VolumeFuncSetting, BrightnessSettingSeekBar, music.*}`；`com.android.systemui.qs.tileimpl.{a,b,c,d,XTCQSIconView,XTCQSTileBaseView,BaseShortCutView}`；`com.android.systemui.qs.external.TileLifecycleManager`（第三方 TileService） |
| 依赖库 | `lottie`（assets json）、`rxjava`、`androidx` |
| 系统 API | `BluetoothAdapter`、`WifiManager`、`AudioManager`、`CameraManager`、`ConnectivityManager`、`Settings.Secure("qs_auto_tiles")`、`TileService.bind`（`BIND_QUICK_SETTINGS_TILE`） |

调用链（6 层）：

```
① QSPanel 读取开关列表：Settings.Secure.getString("qs_auto_tiles")（<SRC>\com\android\systemui\qs\a.java:66）
   + QSFactory 按 spec 创建 Tile（plugins/qs/QSFactory.java）
② Tile.mo348a() 依赖注入：Dependency.b(...)（如 BluetoothTile → statusbar.policy.c，<SRC>\...\tiles\b.java:63-65）
③ Tile.e()（点击）→ 改系统状态 / 起 Activity（ACTION_*_SETTINGS）/ 调 FunSwitch
   ├─ WifiTile：<SRC>\...\tiles\h.java:146（lottie "pull_up_setting/wifi.json"）
   ├─ BluetoothTile：<SRC>\...\tiles\b.java:74-80（enable/disable）
   ├─ RingModeTile：<SRC>\...\tiles\f.java:112-118（vibration/bell/silence json）
   ├─ PowerSaveTile：<SRC>\...\tiles\e.java:62-95（见 M-8）
   └─ ScreenRecordTile：起 MediaProjectionPermissionActivity（见 M-5）
④ 外部 TileService：TileLifecycleManager 绑定/解绑（<SRC>\...\qs\external\TileLifecycleManager.java:400 存 SharedPreferences）
⑤ 状态刷新：refreshState() → XTCQSTileBaseView 播放 lottie
⑥ 点击埋点：tiles/a.java:94 ⚠️
```

⚠️ 本模块内的云控/上报：

| RISK 编号 | 内容 | 位置 |
|---|---|---|
| R-78-02 | `clickEvent(ctx, C0049b.e)` | `<SRC>\com\android\systemui\qs\tiles\a.java:94` |
| R-67 | FunSwitch（`content://com.xtc.fundata/FunSwitch`）控制开关可用性 | `<SRC>\com\xtc\system\account\d.java:27-83` |
| R-69 | manifest `funSwitch` meta-data 决定录屏开关（外部读取，**不可删**） | `<RES>\AndroidManifest.xml:141-143` |

---

## M-11 隐私协议弹窗

| 项 | 内容 |
|---|---|
| 入口组件 | `com.xtc.ui.widget.privacy.PrivacyUriActivity`（`<RES>\AndroidManifest.xml:184-186`，`singleTop`）；`XTCPrivacy`/`PrivacyFirstDialog` 由各应用（launcher/设置/相机等）以库方式调用 |
| 关键类 | `XTCPrivacy`、`PrivacyFirstDialog`、`PrivacyBean`、`PrivacyCommon`、`PrivacyUtilities`、`CtaPermissionUtil`、`PrivacyUriActivity`（二维码）、`com.xtc.ui.widget.permission.cta.*` |
| 依赖库 | `com.google.zxing`（二维码）、`rxjava`、`gson` |
| 系统 API | `PackageManager.getPackageInfo(..., 4224)`（读 requestedPermissions/metaData）、`ActivityCompat.requestPermissions`、`Bitmap`/`QRCodeWriter` |

调用链（7 层）：

```
① 应用启动时调用 XTCPrivacy.showPrivacyDialog(...) / PrivacyFirstDialog.show()
② CtaPermissionUtil.getPrivacy(context, pkg)  <SRC>\com\xtc\ui\widget\util\CtaPermissionUtil.java:119-150
   ├─ packageManager.getPackageInfo(pkg, 4224)  :133
   ├─ 读 meta-data privacy_url / agreement_url / disclaimer_url / required_permissions  :135-155
   └─ 过滤危险权限 + 生成中文说明（getMessageBody :51-60）
③ PrivacyFirstDialog 展示协议/免责声明，按钮“同意/不同意”
④ 同意 → 记录到本地（SharedPreferences/MMKV）→ 继续启动流程
⑤ “查看完整隐私政策” → PrivacyUriActivity.start(ctx, url, ...)  :122-153
⑥ PrivacyUriActivity.buildQrCode() → QRCodeWriter.encode(url)  <SRC>\com\xtc\ui\widget\privacy\PrivacyUriActivity.java:55-120
⑦ 文案：R.string.privacy_url（“手机扫码查看《隐私政策》”，<RES>\res\values\strings.xml:152）
```

⚠️ 本模块内：**不联网、不上报**（二维码只是把 URL 画出来，手机扫码访问）。唯一风险是 `CtaPermissionUtil` 会读取**全机已安装应用**的权限列表（`getAuthorityInfos`，`<SRC>\com\xtc\ui\widget\util\CtaPermissionUtil.java:76-113`）——本机行为，保留即可；不要误删 `PrivacyUriActivity`（组件在 Manifest 注册，外部可能显式启动）。

---

## M-12 自启动（Self Start）

| 项 | 内容 |
|---|---|
| 入口组件 | `com.xtc.ui.widget.selfstart.SelfStartActivity`（`<RES>\AndroidManifest.xml:187`，无 intent-filter/exported=false，由库内部 `startActivity` 拉起）；启动权限查询走 `content://com.xtc.launcher.self.start` |
| 关键类 | `SelfStartActivity`、`SelfStartUtils`、`SelfStartConstant`、`CtaPermissionUtil`（`LAUNCHER_SELF_START_URI`） |
| 依赖库 | `rxjava` |
| 系统 API | `ContentProviderClient.call(...)`、`PackageManager.resolveActivity`、`startActivity(ACTION_SETTINGS_SELF_START)` |

调用链（6 层）：

```
① 业务（如隐私弹窗后/首次启动）调用 SelfStartUtils.checkSelfStartReminded(ctx, pkg, text)
   <SRC>\com\xtc\ui\widget\selfstart\SelfStartUtils.java:20-65
② RxJava 异步（Observable.fromCallable）→ isHadSelfStartPermission(ctx, pkg)   :28-39、:79-127
   ├─ acquireUnstableContentProviderClient(Uri.parse("content://com.xtc.launcher.self.start"))  :87
   ├─ call("METHOD_GET_PACKAGE", pkg, null) → Bundle.getBoolean("SELF_START_STR")  :95-99
   └─ provider 不存在 → return true（视为已有权限，安全降级）                      :88-93
③ 无权限 → isTargetActivityExist()：resolveActivity(ACTION_SETTINGS_SELF_START)  :130-138
④ onNext(true) → startActivity(SelfStartActivity)                              :54-58
⑤ SelfStartActivity.onCreate → DoubleFlatBtnDialog（“取消 / 去设置”）            <SRC>\...\SelfStartActivity.java:19-24、:35-60
⑥ “去设置”→ sendBroadcast/startActivity(ACTION_SETTINGS_SELF_START, EXTRA_PACKAGE_NAME)
```

⚠️ 本模块内：无上报。**保留**（用户体验必需）；若复刻版不接 launcher，`isHadSelfStartPermission` 已天然返回 true，无需改动。

---

## M-13 代理 Provider（StatusBarProxyProvider）

| 项 | 内容 |
|---|---|
| 入口组件 | `com.android.systemui.statusbar.provider.StatusBarProxyProvider`（`<RES>\AndroidManifest.xml:133-137`：`exported=true`、`permission=com.xtc.systemui.statusbar.permission.StatusBarProxyProvider`、`authorities=com.xtc.systemui.statusbar.proxy`） |
| 关键类 | `StatusBarProxyProvider`、`StatusBar`；外部调用方 `com.xtc.i3launcher` |
| 依赖库 | 无 |
| 系统 API | `ContentProvider.call()`、`getCallingPackage()`、`Dependency`→`StatusBar` |

调用链（5 层）：

```
① launcher 进程：contentResolver.call(Uri.parse("content://com.xtc.systemui.statusbar.proxy"), method, arg, extras)
② StatusBarProxyProvider.call(str, str2, bundle)          <SRC>\com\android\systemui\statusbar\provider\StatusBarProxyProvider.java:71-125
   ├─ 权限校验 a()：getCallingPackage() 必须 == "com.xtc.i3launcher"，否则 return super.call(...)  :127-136
   ├─ method "isStatusBarExpanded" / "isStatusBarFullyExpanded" → 返回 Bundle{expanded/fullyExpanded}  :97-121
   ├─ method "collapsePanel" → StatusBar.r()（收起面板）  :106-110
   └─ method "onImStateChanged" → com.xtc.baseservice.e.a.a().a(imState)  :112-114
③ StatusBar 实际执行（展开/收起/IM 状态刷新）
④ 返回 Bundle 给 launcher
```

⚠️ 本模块内：无上报。这是**业务必需组件**（launcher 靠它控制下拉），`<RES>\AndroidManifest.xml:61-63` 的 signature 权限 + 调用包名校验是唯一防线，**不要因“权限检测”而误删**。

---

## M-14 系统控制服务（SystemControlService / directive）

| 项 | 内容 |
|---|---|
| 入口组件 | `com.android.systemui.vassistant.SystemControlService`（`<RES>\AndroidManifest.xml:109-119`：`exported=true`，action `com.xtc.assistantapi.directive`，meta-data `assistantapi-directive=[FlashLight,VideoRecord]`） |
| 关键类 | `SystemControlService`、`vassistant.c`（Directive 处理器）、`vassistant.SwitchPayload`、`com.xtc.assistantapi.launch.AssistantForegroundService`、`com.xtc.assistantapi.message.{Directive,Payload}` |
| 依赖库 | `com.xtc.assistantapi`（AIDL + Parcelable） |
| 系统 API | `Service.onStartCommand`、`AIDL`、`content://com.xtc.vassistant.provider` / `com.xtc.openapi.assistant` / `com.xtc.i3launcher.assistant` |

调用链（6 层）：

```
① 语音助手/launcher 发 intent action=com.xtc.assistantapi.directive（或 bind）
② AssistantForegroundService.onStartCommand（<SRC>\com\xtc\assistantapi\launch\AssistantForegroundService.java）
③ SystemControlService.mo1231a() → new vassistant.c(this)（单 handler）
   <SRC>\com\android\systemui\vassistant\SystemControlService.java:18-23
④ vassistant.c 解析 Directive（<SRC>\com\android\systemui\vassistant\c.java:58-148）
   ├─ "FlashLight" → 手电筒开/关（见 M-6）
   └─ "VideoRecord" → 录屏开/关（见 M-5）
⑤ 执行结果/状态经 Payload 回传（SwitchPayload，<SRC>\com\android\systemui\vassistant\SwitchPayload.java）
⑥ 外部 provider 调用：content://com.xtc.vassistant.provider 等（<SRC>\com\xtc\assistantapi\c\h.java:21-23）
```

⚠️ 本模块内：无上报。**保留**（`assistantapi-directive` meta-data 决定语音可控的能力，删了语音助手无法控制手电/录屏）。

---

## M-15 监控/上报模块（bigdata，需要整体摘除的部分）

> 该模块不是产品功能，而是 1.8.00 额外带入的“大数据 SDK”。单独列出便于 Lead 整体裁剪。

| 项 | 内容 |
|---|---|
| 入口组件 | ① `SystemUIApplication.f()` → `BigDataInitHelper`（延迟 120s）② `com.xtc.bigdata.monitor.receiver.MonitorStartReceiver`（`com.xtc.watchdogs` 广播）③ `com.xtc.bigdata.collector.exception.CrashIntentService`（内部 intent）④ `com.xtc.xtcoco.CodeCoverageReceiver`（`com.xtc.report.generate.ec` 广播） |
| 关键类 | `BehaviorCollector`、`CollectionManager`、`ShareHelper`、`QueueUtils`、`BaseAttr/BaseAttrManager/MachineAttr/UserAttr`、`CrashHandler/CrashIntentService/CrashInfo`、`WatchDogs/ANRWatchDog/LooperMonitor/ANRMonitor`、`DropboxScanner`、`PerfMonitor`、`InitManager`、`OdexConfig/OdexInfo`、`JacocoUtils` |
| 依赖库 | `ormlite`（`com.j256.ormlite`）、`MMKV`、`gson`、`rxjava` |
| 系统 API | `Thread.setDefaultUncaughtExceptionHandler`、`Application.ActivityLifecycleCallbacks`、`ContentResolver.acquireUnstableContentProviderClient`、`ActivityManager.getRunningAppProcesses`、`Environment.getExternalStorageDirectory`、`ApplicationInfo.metaData("ChannleId")` |

调用链（8 层，完整“采集→落库”链路）：

```
① SystemUIApplication.f() → BigDataInitHelper.a(app); m290a()        <SRC>\com\android\systemui\SystemUIApplication.java:100-104
② Timer(120s) → BigDataInitHelper.b()                               <SRC>\com\android\systemui\c.java:49-87
   ├─ CrashHandler.setCrashListener(...)                             :75-80   ⚠️ R-03
   └─ BehaviorUtil.init(new BehaviorCollector.Builder(app)
        .setHostAppId("com.xtc.i3launcher").setDeviceType("watch")
        .openActivityDurationTrack(true).setAutoCollectEvent(false)
        .setDebugMode(false).build())                                :81      ⚠️ R-01
③ BehaviorCollector.Builder.build() → InitManager.init()            <SRC>\com\xtc\bigdata\collector\BehaviorCollector.java:140-161
   └─ CrashHandler.registerCrashHandler()（setDefaultUncaughtExceptionHandler）  <SRC>\com\xtc\bigdata\collector\init\InitManager.java:57
④ 事件产生（页面/点击/异常/性能/DNS/流量…）→ BehaviorUtil.* → BbkBehavior → BehaviorCollector.getInstance().xxxEvent()
   <SRC>\com\xtc\common\bigdata\BbkBehavior.java:60-149
⑤ CollectionManager.insert(event) → QueueUtils.add(event)           <SRC>\com\xtc\bigdata\collector\CollectionManager.java:162-170
   └─ QueueUtils HandlerThread("bigdata_task")                       <SRC>\com\xtc\bigdata\collector\utils\QueueUtils.java:38-42
⑥ 批量取事件 → event.makeData() → ShareHelper.getInstance().insert(ContentValues)  <SRC>\com\xtc\bigdata\collector\ShareHelper.java:37-54
⑦ UriUtils.getContentUri() = content://com.xtc.i3launcher.provider/<Columns…>
   <SRC>\com\xtc\bigdata\common\utils\UriUtils.java:11-21
   acquireUnstableContentProviderClient(...).insert/bulkInsert
⑧ launcher 侧 UserBehaviorProvider 落库 → 上传到 watchda.eebbk.net（**不在本包**）
```

⚠️ 本模块内的 RISK 编号：**R-01~R-29、R-78-01/15/25、R-79~R-84**。复刻版建议**整包不打包** `com.xtc.bigdata.*` + `com.xtc.xtcoco.*`，仅保留 `com.xtc.common.bigdata.BehaviorUtil` 空壳（若不想改 25 处调用点）。

---

## M-16 文件 Provider（FileProvider）

| 项 | 内容 |
|---|---|
| 入口组件 | `androidx.core.content.FileProvider`（`<RES>\AndroidManifest.xml:124-132`，`authorities=com.android.systemui.fileprovider`，路径配置 `@xml/fileprovider`） |
| 关键类 | `androidx.core.content.FileProvider`；使用方：录屏保存、截图、日志导出（`<SRC>\com\xtc\utils_screenshot_carry_data.*`） |
| 依赖库 | `androidx.core` |
| 系统 API | `FileProvider.getUriForFile`、`Intent.FLAG_GRANT_READ_URI_PERMISSION` |
| 结论 | **保留无害**（业务必需）；与上报无关，但**不要误删**，否则录屏/分享功能崩溃 |

---

## 附：模块 × RISK 对照速查

| 模块 | 关联 RISK |
|---|---|
| M-1 启动 | R-01, R-02, R-03, R-04, R-21, R-24, R-56, R-80 |
| M-2 状态栏/下拉 | R-78-02, R-78-22, R-85, R-76 |
| M-3 锁屏 | （无上报；保留 IKeyguard*） |
| M-4 通知监听 | R-67(开关), R-68 |
| M-5 录屏 | R-76, R-78-23, R-78-24, R-69, R-68 |
| M-6 手电筒/SOS | R-78-09, R-78-10, R-70 |
| M-7 音量 | R-41(引用), R-77 |
| M-8 省电/长续航 | R-78-11~14, R-72, R-68 |
| M-9 BLE | R-35/R-36(随包网络库) |
| M-10 快捷开关 | R-78-02, R-67, R-69 |
| M-11 隐私协议 | R-59~R-61, R-65 |
| M-12 自启动 | R-62, R-63 |
| M-13 代理 Provider | R-66 |
| M-14 系统控制服务 | （无上报；保留 directive meta-data） |
| M-15 监控上报 | R-01~R-29, R-78, R-79~R-84 |
| M-16 FileProvider | （无） |
