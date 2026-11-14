# 双逻辑对照登记表（原APK运行逻辑 ⇄ 改造后运行逻辑）

> 规则：每完成一个模块即新增一节；「原逻辑」证据 = 反编译文件:行号（`work\jadx18b\sources`，1.8.00 unquicken 后反编译，与 1.7.50 交叉校验）；「新逻辑」证据 = `src\main\java` 下新代码。
> 状态：✅ 已完成复刻 / 🟡 阶段实现（骨架，行为待补） / ⛔ 已移除（记录依据）

---

## W1 · M1 核心运行时

### M1-01 SystemUIApplication  ✅
- 原APK运行逻辑：`SystemUIApplication.java:46-82` onCreate → 初始化日志(jacoco/blockcanary/se2) → `setTheme(Theme_SystemUI)` → `SystemUIFactory.createFromContext()` → SYSTEM 用户注册 priority=1000 的 BOOT_COMPLETED 动态接收器（收到后对所有已启动组件回调 onBootCompleted）→ 子进程则跳过 / 非 SYSTEM 用户启动 `config_systemUIServiceComponentsPerUser`。组件启动 `:114-155`：按资源数组反射 `newInstance`，注入 context+组件 Map，`start()`，boot 已完成则立即 onBootCompleted；超 1s 打警告。`startActivity` 覆写 `:181-205`：把带 repost action 的 intent 解包后交给 ActivityStarter 插件。
- 改造后逻辑：`src\main\java\com\android\systemui\SystemUIApplication.java` —— 保留主题/工厂/BOOT_COMPLETED(priority 1000)/反射组件启动/配置变更分发/组件 Map/子进程判断；`sys.boot_completed` 改为 SystemProperties 反射桥（`:225-234`），进程名读 `/proc/self/cmdline`（`:236-264`），启动耗时用 `android.os.Trace`。
- 差异与移除点：
  - ⛔ 移除 `com.xtc.log.LogConfig/LogUtil` 初始化、`JacocoUtils.init`、`com.github.moduth.blockcanary`、`com.xtc.core.se2.c.a`、`ac.a(ae...)`、`TypedValueCompat`（日志/覆盖率/卡顿监控/埋点 SDK）。
  - ⛔ 移除 `startActivity` 的 repost 插件路由（依赖插件框架与外部 intent 协议；非 UI 业务功能）。
  - 保留：Boot 时序、组件反射启动、异常即崩溃（与原包一致，避免静默降级）。
- 回归核对：✅ 组件按资源顺序启动 ✅ 首次启动按 boot 状态补发 onBootCompleted ✅ 子进程不重复启动 ✅ dump 可枚举。

### M1-02 SystemUIService  ✅
- 原逻辑：`SystemUIService.java:15-27` onCreate 调 `((SystemUIApplication)getApplication()).c()` 启动主组件列表；`Build.IS_DEBUGGABLE && SystemProperties.getBoolean("debug.crash_sysui")` 时故意抛异常；`dump()` 遍历组件或按类名后缀过滤。
- 改造后：`SystemUIService.java` 同契约；移除 debug.crash_sysui 隐藏 API 分支（开发用）。
- 差异：仅移除 debug 自杀开关（无业务影响，已在 PLAN 记录）。
- 回归：✅ onBind=null ✅ dump 全量/过滤 ✅ 启动主列表。

### M1-03 SystemBars  ✅
- 原逻辑：`SystemBars.java:26-58` 从 `@string/config_statusBarComponent` 读类名 → 反射实例化 → 注入 context/Map → start()；空配置或异常直接抛 RuntimeException；dump 转发。
- 改造后：`SystemBars.java` 等价实现（`getComponentMap()` 由 SystemUI 基类暴露）。
- 回归：✅ 配置为空即失败（同原包） ✅ 状态栏组件被正确拉起 ✅ dump 透传。

### M1-04 SystemUIFactory  ✅
- 原逻辑：`SystemUIFactory.java` 静态单例，`createFromContext` 读 `@string/config_systemUIFactoryComponent` 反射加载，失败抛 RuntimeException；另有 `createQSPanel(context,statusBar,...)` 等方法供状态栏使用。
- 改造后：`SystemUIFactory.java` 保留单例+加载+失败语义；控制器工厂方法随 W2/W4 波次补入。
- 回归：✅ 工厂可从资源类名加载 ✅ 失败即抛。

### M1-05 Dependency（依赖注册表）  ✅
- 原逻辑：`Dependency.java`（439 行）SystemUI 组件，双 Map（实例 + 懒工厂），静态 `get(Class)`/`getDependency`，懒注册 background looper、main/time-tick handler、leak report email、ActivityStarter、多个 controller（部分 controller 创建会触发插件/原生加载）。
- 改造后：`Dependency.java` 保留组件生命周期+注册表语义；预注册项只保留 main/background Looper+Handler；`DependencyKey` 强类型键；移除 leak-report email 与所有上报相关 controller 工厂。
- 差异：⛔ 上报/leak 依赖不注册（移除点：原 `f430b.put(d, ...leak_report_email...)`）。保留懒加载与线程模型，避免时区/时序差异。
- 回归：✅ get(未注册)=null ✅ 懒工厂只创建一次 ✅ destroy 清理后台线程。

### M1-06 SystemUI 基类 / SysUiServiceProvider  ✅
- 原逻辑：`o.java`（SystemUI 抽象基类，context+组件 Map+start/onConfigChanged/onBootCompleted/dump/putComponent）；`n.java`（SysUiServiceProvider 接口 + 静态 `a(context, cls)`）。
- 改造后：`SystemUI.java` / `SysUiServiceProvider.java` 等价契约（attach/startInternal 由 Application 驱动，避免外部误调）。
- 回归：✅ 生命周期顺序一致 ✅ 组件互查一致。

### M1-07 CommandQueue / CommandQueueStart  🟡
- 原逻辑：`statusbar/CommandQueue.java`（29KB）实现系统 IStatusBar 回调漏斗（disable flag、面板展开、通知增删、IME、导航键、截图、生物识别提示），由 `CommandQueueStart` 注册进组件 Map；是系统与 SystemUI 之间唯一通道。
- 改造后：`statusbar/CommandQueue.java` 保留 `CommandQueueStart` 组件及注册点（资源数组要求），回调集合在 W2 按「公开 API + 反射代理 IStatusBar」方式实现（不引入 hidden API 编译依赖）。
- 差异：回调未实现（阶段），已知移除项：原实现内嵌的上报/埋点调用不迁移。
- 待办：W2 完成 IStatusBar/IStatusBarService 反射桥与全部回调分发。

---

## W1 · M13/M14 组件占位与风险逻辑移除

| 组件（manifest 声明） | 原APK逻辑 | 改造后逻辑 | 判定 |
|---|---|---|---|
| `com.xtc.xtcoco.CodeCoverageReceiver` | Jacoco 覆盖率 collector，收到 `com.xtc.report.generate.ec` 后 dump 覆盖率交 bigdata 上报（`CodeCoverageReceiver`） | `CodeCoverageReceiver.java`：仅 Log.d 忽略，不采集不上报 | ⛔ 上报移除，声明保留 |
| `com.xtc.bigdata.monitor.receiver.MonitorStartReceiver` | watchdog/监控总开关：`com.xtc.watchdogs`、`com.xtc.launcher.ACTION_CHANGE_REGION`，priority 100000，拉起 bigdata monitor/crash/dropbox/日志上传 | `MonitorStartReceiver.java`：仅 Log.d 忽略 | ⛔ 云控+上报移除，声明保留 |
| `com.xtc.dns.provider.HttpDnsContentProvider` | HTTP DNS 本地缓存 + 远端查询（云端解析/远程配置通道），authority `com.android.systemui.com.xtc.httpdns.provider` | `HttpDnsContentProvider.java`：空实现，call() 返回 null（调用方回退系统 DNS），零网络 | ⛔ 云控通道移除，声明保留 |
| `com.android.systemui.statusbar.provider.StatusBarProxyProvider` | 向同厂系统应用暴露状态栏状态/展开收起（本地 IPC） | `StatusBarProxyProvider.java`：Provider 壳，call 分发 W2 补 | 🟡 保留（本地功能） |
| `com.xtc.statusbar.XtcNotificationListenerService` | 通知监听 → 转交 XTC 状态栏渲染 | `XtcNotificationListenerService.java`：监听壳（onListenerConnected/Disconnected），模型渲染 W3 补 | 🟡 保留 |
| `com.android.systemui.screenrecord.RecordingService` | 录屏前台服务（MediaProjection+MediaRecorder+mp4 封装+悬浮窗） | `RecordingService.java`：服务壳，W6 补管线 | 🟡 保留 |
| `com.android.systemui.vassistant.SystemControlService` | 助理 directive 服务（FlashLight/VideoRecord） | `SystemControlService.java`：binder 壳，W6 补协议 | 🟡 保留 |
| `com.android.systemui.flashlight.FlashLightActivity` / `FlashLightSOSDialog` | 手电筒/SOS UI | Activity 壳，W6 补（CameraManager.setTorchMode 公开 API） | 🟡 保留 |
| `com.android.systemui.media.MediaProjectionPermissionActivity` | 投屏授权 UI | Activity 壳，W6 补 | 🟡 保留 |
| `com.xtc.architecture.mvp.PermissionListActivity` | 权限说明列表（@array/permission_label，本地 UI） | Activity 壳，W5 补渲染；**权限检测/上报代码不迁移** | 🟡 保留（纯 UI） |
| `com.xtc.ui.widget.privacy.PrivacyUriActivity` | 隐私协议弹窗（本地） | Activity 壳，W5 补 | 🟡 保留 |
| `com.xtc.ui.widget.selfstart.SelfStartActivity` | 自启动设置跳转（本地） | Activity 壳，W5 补 | 🟡 保留 |
| `androidx.core.app.CoreComponentFactory` | appComponentFactory（androidx 类） | 自实现同名类，委托 framework 默认实例化（不打包 androidx） | ✅ 等价 |
| `androidx.core.content.FileProvider` | androidx FileProvider（authority com.android.systemui.fileprovider） | 自实现同名类（getUriForFile/openFile 基础版），W6 随录屏补全 xml 路径策略 | 🟡 等价壳 |

## W1 · 已标记移除点（全局清单，后续波次继续复核）

| 编号 | 位置（原包） | 性质 | 处置 |
|---|---|---|---|
| R-01 | `com.xtc.bigdata.*` 全栈（collector/monitor/crash/exception/CrashIntentService/DropboxScanner） | 上报（埋点/崩溃/设备信息） | ⛔ 不实现任何类；manifest 中 `CrashIntentService` 声明保留与否待终验决策（当前保留声明但无类会被系统忽略——见 W7 复核） |
| R-02 | `com.xtc.log.*`（LogConfig/LogUtil/CrashHandler/CrashListener/ILogger） | 日志落盘+上报 | ⛔ 不实现 |
| R-03 | `com.xtc.xtcoco.JacocoUtils` + `CodeCoverageReceiver` | 覆盖率上报 | ⛔ 不实现 / 空实现 |
| R-04 | `com.xtc.dns.*`（HttpDns*、READ_DNS 通道） | 云控 DNS/下发 | ⛔ 空 Provider，无网络 |
| R-05 | `com.xtc.ultraframework.*`（含 net/wifi、QamUtils 等 SDK 通道） | 厂商框架/可能含远程能力 | ⛔ 不打包（仅按需重写被 UI 依赖的本地能力） |
| R-06 | `libxtcSecurity.so` + 签名/完整性校验类 | 安全 SDK/权限检测 | ⛔ 不打包、不引用 |
| R-07 | `libmmkv.so` + MMKV 存储 | 键值存储（可含上报缓存） | ⛔ 不打包，改用 SharedPreferences |
| R-08 | okhttp3/okio/retrofit2/rx/kotlin/kotlinx/protobuf/ormlite/mp4parser | 依赖与网络栈 | ⛔ 不打包（mp4 录屏用自写 muxer） |
| R-09 | `moduleSwitch` / `funSwitch` meta-data 运行时解析 | 云控模块开关 | 🟡 声明保留（信息一致），代码不解析、不据此下载/启用任何模块 |
| R-10 | `android.permission.systemui.IDENTITY` / `com.xtc.http.bigdata.provider.*` 权限 | 权限/身份检测链路 | 🟡 声明保留（信息一致），无检测与上报代码 |

---

## W0.5 · 云控移除专项（本轮完成，详见 `analysis\cloud-control\CLOUD-REMOVAL.md`）

### 原APK云控链路（证据 → 复刻处置）
`SystemUIApplication.onCreate` → `c.a()/c.m290a()`（BigDataInitHelper，延迟120s）→ `BehaviorUtil/BehaviorCollector`（hostAppId=com.xtc.i3launcher）→ 采集写入 `content://com.xtc.i3launcher.provider`（launcher 落库上传）；网络侧 `com.xtc.httplib`（OkHttp+拦截器栈+配置热更新）→ `com.xtc.domain`（`@POST /route-service/route/getRouteInfo` 下发域名/机房，机房变化可 `powerManager.reboot`）→ `com.xtc.dns`（HttpDnsContentProvider exported + 明文 `http://119.29.29.98/d` + 可被 sdcard 篡改的 `httpdnsinfo.properties`）。
处置：全部不实现。复刻包无 `okhttp/retrofit/rx/kotlin/protobuf`，无域名/DNS/route/配置下发代码；`HttpDnsContentProvider` 在 parity 变体为空实现（call/query→null，零网络）。

### 声明层（strict 变体，AXML 手术）
删除 6 个元素：`CrashIntentService`(service)、`MonitorStartReceiver`(receiver)、`CodeCoverageReceiver`(receiver, exported=true)、`HttpDnsContentProvider`(provider)、`com.xtc.http.bigdata.provider.permission.READ_CONTENT/WRITE_CONTENT`(uses-permission)；AXML StringPool/ResourceMap 原样保留，仅重算 chunk size。
保留（非云控，勿删）：`moduleSwitch`/`funSwitch`/`supportScreenRecordAudio`/`XtcStayFront` 元数据、`StatusBarProxyProvider`、`IKeyguard*`、`SystemControlService`、`XtcNotificationListenerService`、`PermissionListActivity`/`PrivacyUriActivity`/`SelfStartActivity`。

### 双逻辑差异
- 原逻辑：开机静默启动上报 SDK；后台拉取路由/域名配置并覆盖本地域名；HTTP-DNS 旁路；可被 launcher 远程下发网络/模块开关；崩溃/覆盖率/监控广播入口 exported 暴露。
- 新逻辑：无采集器、无域名表、无路由下发、无 DNS 旁路、无远程开关读取；上报/监控/覆盖率组件在 parity 变体为 NOP、strict 变体连声明一并删除；静态门禁 `check_nocloud.py` 每次构建强制校验（0 命中）。

### 验证
strict APK 2,367,101 B：`arsc` sha256 与原包相同；xmltree 差异 30 行=仅被删元素；badging 仅少 2 权限+other-receivers；无云控扫描 PASS（类 0 / 字符串 0 / `Ljava/net/*` 0）；签名对齐通过。
parity APK 2,364,413 B：verify.ps1 7/7 PASS，16/16 组件类齐全，资源/清单 sha256 与原包一致。
---

## W2.5 · 下拉面板 UI 第一版：亮度/音量 + 伪玻璃背景 + 头部行（用户 UI 指令 2026-11-01）

### 原APK运行逻辑
- 亮度：`qs.tiles.BrightnessSettingSeekBar` / `qs.tiles.BrightnessFuncSetting` 读写 `Settings.System.SCREEN_BRIGHTNESS`(+auto mode)，ContentObserver 回刷，`brightness_value`/`level_max`/`level_min` 文案。
- 音量：`qs.tiles.VolumeFuncSetting` 驱动 `AudioManager.STREAM_MUSIC`，并依赖厂商 `com.xtc.httplib.netstate.SwitchNetworkManager` 的网络状态监听（`music_volume_value`/`volume_value`）。
- 面板：`XTCPanelView extends PanelView`（1110 行手势/动画基类）+ 窗口参数来自厂商 `StatusBarServiceEx.getWindowLayout()`；背景半透明，**API27 无合成器模糊**（无 RenderEffect/background blur）；头部行 `XtcStatusBarView` 按 22 个 view id 由各 policy controller 驱动（信号/飞机/WiFi/时间/电量等）。
- 顶部折叠栏 `status_bar.xml` 原为空布局（无可见内容）。

### 改造后运行逻辑（新增/重写文件）
- `qs/VerticalSeekBar.java`：自绘竖向滑条（半透明胶囊 + 竖向「亮度/音量」标签 + 填充轨道 + 圆角拇指），尺寸在 onSizeChanged 里按控件比例推导 → 自适应屏幕；无 ProgressBar/animator。
- `qs/BrightnessController.java`：写 `SCREEN_BRIGHTNESS`+`SCREEN_BRIGHTNESS_MODE_MANUAL`，ContentObserver 回刷；无厂商服务。
- `qs/VolumeController.java`：`AudioManager.STREAM_MUSIC` 读写 + `android.media.VOLUME_CHANGED_ACTION` 广播回刷；**移除** `com.xtc.httplib.netstate.SwitchNetworkManager`（网络栈依赖）。
- `qs/QSPanel.java`：程序化装配面板（原布局无这些控件、资源表不可改）：首行「背景音乐卡片 2.6 : 亮度条 1 : 音量条 1」，下两行圆形快捷占位（比例 0.92/0.72/0.86/0.52 与 0.58/0.80/0.52/0.92，与手绘图一致），高度/间距按屏幕宽高百分比。
- `qs/MusicCardView.java`：半透明圆角卡片，`setNowPlaying(title,sub)` 供音乐通知波次接入（当前占位「暂无后台播放」）。
- `statusbar/phone/GlassBackgroundView.java` + `ScreenBlur.java`：伪玻璃背景——面板**展开前**用反射调用 `SurfaceControl.screenshot` 抓 1/6 分辨率屏幕帧 → 两遍可分离 box blur → 作为面板背景绘制（centerCrop），因此下方播视频时面板呈现"模糊视频帧"的玻璃感；不常驻刷新（低端芯片 1GB/ Wear4100 功耗优先），每次展开刷新一帧。
- `xtc/statusbar/XtcStatusBarView.java`：头部行自绘 信号条/飞机(或WiFi)/时间/电量(含充电)，透明底、尺寸随高度自适应；WiFi/飞机状态由 StatusBar 在展开时读取 `WifiManager` + `Settings.Global.AIRPLANE_MODE_ON` 推送。
- 窗口/面板：`XTCPanelView` 增加玻璃层与 `setupContent()`；面板保持半透明；`StatusBar` 展开时联动窗口高度（已实现）。

### 差异与移除点
- 移除：`SwitchNetworkManager`（网络状态/云控家族）、厂商 `StatusBarServiceEx` 窗口参数（改标准 TYPE_STATUS_BAR + 公开 flags）、原 `PanelView` 的完整手势/动画状态机（后续波次补，当前为基础拖拽显隐）。
- 保留：半透明 UI 风格、头部行信息、`Settings.System` 亮度通道、AudioManager 音量通道（均为公开 API，无上报）。
- 已知取舍：实时连续模糊在 API27 不可行（无合成器模糊）；当前"展开时抓帧"近似玻璃；若要视频连续动态模糊需 ~5fps 抓帧（低端芯片有功耗/闪帧风险），默认不做。

### 回归核对
- ✅ 亮度条拖动即刻写系统亮度并回读同步
- ✅ 音量条拖动即刻改媒体音量并随系统广播同步
- ✅ 面板展开：玻璃背景（截图+模糊）显示；收起后释放可见性
- ✅ 头部行保留（信号/WiFi/飞机/时间/电量）
- ⏳ 音乐卡片真实数据（待 W3 音乐通知链路）
- ⏳ 圆形快捷按钮功能（待 W4 QS）
---

## W2.6 · 顶部状态栏行（按用户提供的另一版本 DC_SYSUI 复刻）

### 原APK运行逻辑（参考 DC_SYSUI 的 statusbar_bar.xml，非 1.8.00 本版）
- 布局 `<merge>`：左侧 LinearLayout（iv_signal_level、iv_airplane_mode(gone)、ll_data_traffic(4G 类型图标+上下箭头+漫游，gone)、iv_wifi(gone)、iv_hotspot(gone)、iv_im_state(gone)、iv_download(gone)、iv_hd_voice(gone)）；中间 TextView `tv_telecom_operators`（`centerInParent`，被 `XtcStatusBarView` 灌入 `HH:mm`/`hh:mm` 时间，13sp 白字，左右 5dp）；右侧 LinearLayout（iv_gps/iv_bluetooth/iv_headset/iv_alarm_clock 默认 gone + 电量 FrameLayout：iv_battery + tv_battery_num 数字）；最上覆盖 `StatusTipView stv_tip`。
- 参数：`dimen_status_bar_gap_2=2dp`、`rounded_corner_radius`、`dimen_status_bar_time_size=13sp`、`sp_8`、`color_ff333333`(非充电数字色)、`expanded_clock_text_color`(充电数字色)。
- 状态源（原版）：厂商 SignalController/WifiController/BluetoothController/BatteryHelper(baseservice)/AlarmController 等；电池图标映射 level/10+1（100→11）并区分充电系列。

### 改造后运行逻辑（本版实现）
- `com.xtc.statusbar.XtcStatusBarView` 改为 `RelativeLayout`，**代码内构建 statusbar_bar 同构子视图**（资源表不可改，不能新增 layout XML；所用 drawable 全部是 1.8.00 资源表里已有的同名图标：ic_signal_zero..four、ic_airplane_mode_on_status_bar、ic_data_traffic_*、ic_wifi_zero..four、icon_hot_spot、im_offline、ic_download、ic_hd_voice、ic_gps、ic_bluetooth(_mate)、ic_battery_status_bar_5(耳机位)、ic_alarm_clock、ic_battery_N/charging_N）。
- 状态接线全部公开 API：`ACTION_BATTERY_CHANGED`（电量分级 1..11 + 充电系列 + 数字颜色）、`DateFormat` 时间（24h/12h）、WiFi `isWifiEnabled`+`getConnectionInfo().getRssi()` 五档、蓝牙 `getProfileConnectionState(GATT)==CONNECTED→ic_bluetooth_mate`、`ACTION_HEADSET_PLUG`/`isWiredHeadsetOn`、`AlarmManager.getNextAlarmClock`、`LocationManager.isProviderEnabled(GPS)`、`PhoneStateListener.LISTEN_SIGNAL_STRENGTHS→getLevel()` 四档、`Settings.Global.AIRPLANE_MODE_ON` 切飞机图标并隐藏信号。
- 未接线项（与原版默认一致，保持 GONE）：移动数据流量组、热点、IM 状态、下载、HD Voice——原状态源为厂商私有服务/baseservice，本地无公开等价 API；`StatusTipView` 提示层留待提示体系波次。

### 差异与移除点
- 移除：baseservice/BatteryHelper 等厂商依赖、rx 定时器（改 Handler 30s 周期 + 广播驱动）、LogUtil 计时日志。
- 保留：行内元素顺序、间距、字号/颜色、电量映射与充电表现、时间居中。
- 取舍：`stv_tip` 提示层未实现（后续与 HintWindow/PopWindow 一起做）。

### 回归核对
- ✅ 时间按分钟与系统时间格式刷新
- ✅ 电量数字+图标同步、充电切换与变色
- ✅ WiFi/蓝牙/耳机/闹钟/GPS/信号/飞行模式随系统状态更新
- ⏳ 数据流量/热点/IM/下载/HD（默认隐藏，状态源待评估）
- ⏳ StatusTipView 提示层
### W2.6b · 充电电流显示（用户要求）
- 原逻辑（参考实现，用户提供 Kotlin 代码）：读 `/sys/class/power_supply/battery/current_now`，为空回退 `current_avg`；解析整数后 |值|>1000 视为 µA → /1000 转 mA，否则按 mA；正负号区分输入/输出；格式化 `%.1f mA`。
- 新逻辑：`XtcStatusBarView` 右侧组在电量图标**左侧**新增电流 TextView（8sp 白字，原 `sp_8` 资源），仅充电时显示；非充电/读不到节点即隐藏（不显示"未知"占位）。
  - 读取在独立 `HandlerThread("battery-current")` 上执行，主线程零 IO；仅充电期间每 5s 轮询一次，停止充电或视图销毁即停止并退出线程。
  - 显示绝对值 + 一位小数（部分内核充电时符号为负，显示绝对值对用户更直观）；节点不可读时安全隐藏。
- 差异：不引入厂商 BatteryHelper/baseservice；不写日志/不上报；无 rx 定时器。
- 回归：⏳ 需真机充电验证（关机/未充电时隐藏；插电后 5s 内出现数值）
### W3a · 下拉左上角「正在播放」迷你播放器卡片（按用户草图移植 DC_SYSP 的媒体信息段）
- 原逻辑参考：DC_SYSP（com.zcg.systemplus）的「状态栏歌词」是 Xposed hook（MainHook + s3/*，需 system_patch/SystemUIHook 作用域）在系统窗口里绘制；本模块内**没有**独立全屏悬浮窗（唯一 WindowManager 悬浮窗是录屏小窗 type=2038）。其音乐数据链为通知监听 → 媒体 token → 自定义渲染。
- 新逻辑（不依赖 hook，纯公开 API）：
  - `XtcNotificationListenerService` 过滤含 `android.mediaSession` token 的通知 → `NowPlayingBridge`（进程内静态桥，缓存最近一条）→ `NowPlayingController`（`MediaController(context, token)`，注册 Callback 监听 metadata/playback）。
  - `MusicCardView` 按草图实现：专辑封面（BitmapShader 圆角裁切）、歌名走马灯（仅面板展开时滚动，防耗电）、上一首/暂停-播放/下一首三个手绘按钮（MediaController.TransportControls: skipToPrevious / play-pause / skipToNext）；无媒体时显示音符占位并置灰按钮。
  - 任何后台播放器（网易云/酷狗/系统音乐）只要发 MediaSession 通知即可显示；无 App 白名单、无埋点、无网络。
- 差异：不搬运 DC_SYSP 的 Xposed hook/字符串加密/混淆框架；不使用原 vendor MusicController（349 行，含白名单与防沉迷回调）；歌词逐行显示暂不做（需 hook 音乐 App 才能取到歌词行，属 system_patch 范畴）。
- 回归：⏳ 真机验证（后台播放音乐→下拉面板左上角出现封面/歌名/控制；点击上一首/暂停/下一首生效；无媒体时占位）。
### W4a · 下拉 8 个快捷开关（磨砂玻璃圆钮）
- 用户规格：第一行 WiFi / 移动网络 / 蓝牙 / 热点；第二行 **屏幕手电 / 录屏 / 护眼 / 保持亮屏**（按用户指令用屏幕手电与录屏入口替换了飞行模式与省电）；都要模糊玻璃效果。
- 新实现 `qs/QsToggles.java` + `qs/GlassTileView.java`（替换原圆形占位，删除 CircleTileView）：
  - WiFi：`WifiManager.isWifiEnabled/setWifiEnabled`；移动网络：`TelephonyManager.isDataEnabled()` + 反射 `setDataEnabled`；蓝牙：`BluetoothAdapter.enable/disable`；热点：`WifiManager.setWifiApEnabled` 反射（先 `getWifiApConfiguration`）；全部由 `WIFI_STATE_CHANGED`/`BT_STATE_CHANGED` 等广播 + 10s 轮询回刷。
  - 飞行模式：`Settings.Global.AIRPLANE_MODE_ON` + `ACTION_AIRPLANE_MODE_CHANGED` 广播。
  - 省电：`PowerManager.isPowerSaveMode()` + 反射 `setPowerSaveMode`；`ACTION_POWER_SAVE_MODE_CHANGED` 回刷。
  - **护眼（参考 DC_xpos = Xposed Edge Pro 的 ScreenFilter，com.jozein.xedgepro.xposed.t3）**：全屏覆盖窗口 `TYPE_SYSTEM_OVERLAY` + `FLAG_NOT_FOCUSABLE|FLAG_NOT_TOUCHABLE|FLAG_LAYOUT_IN_SCREEN` + TRANSLUCENT，暖色半透明底 `0x33FFB74D`，添加/移除实现开关；状态存 SharedPreferences，进程重建后恢复。
  - **保持亮屏（参考 DC_xpos 的 stay awake，w1.java:3527）**：`PowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK /*=10*/, "XTCSystemUI:awake")`，`setReferenceCounted(false)`，acquire/release；状态持久化，面板销毁时释放、重新附着时按偏好恢复。
  - 玻璃视觉：径向渐变半透明白（0x4D→0x1F→0x0D）+ 白色描边 + 顶部高光弧 + 手绘图标（WiFi/信号/蓝牙/热点/飞机/电池叶子/眼睛/太阳），开态更亮、关态 60% 透明；无位图、无 Lottie、onDraw 零分配。
- 差异：移除 vendor QS tile 框架（QSTileImpl/TileLayout/TileLifecycleManager/Lottie 动画）；无白名单、无埋点、无网络。
- 回归：⏳ 真机验证 8 个开关（切状态后图标变化、系统设置同步、护眼覆盖层不影响触摸、保持亮屏不灭屏）。
---

## W5 · 提示层 / 面板手势 / Provider 联动（Lead）
- 原逻辑：`StatusBarWindowView`/`PanelView`（1110 行）手势状态机（拖拽/fling/falsing/peek）；`layout_inner_toast` + `com.xtc.ui.widget.toast.ToastUtil` 显示系统内提示；`StatusBarProxyProvider.call` 供 launcher 查询/收起面板。
- 新逻辑：
  - `StatusBarWindowView`：竖向拖拽 + `VelocityTracker` fling（>600px/s 也可开合）；`XTCPanelView` 展开时窗口高度切全屏、抓帧伪玻璃背景、`setExpanded` 驱动媒体卡走马灯。
  - `InfoToast`（新）：绑定 `R.id.tv_toast_inner`，淡入淡出 2.4s，进程内任何模块可用（录屏/手电/助理已接入）；不额外创建窗口。
  - `PanelStateHolder`（dev-misc）+ `StatusBarProxyProvider`：expanded/fullyExpanded/IM 状态、collapse 回调；`StatusBar` 接线（inflate 后 setCollapseCallback、展开回调 setPanelState）。
- 差异：未搬 vendor `PanelView` 的 peek/falsing/表冠细节（手表上未观察到依赖）；无埋点/日志。

## W6 · Keyguard 桥（dev-misc）
- 原逻辑：`statusbar.f`（StatusBarKeyguardViewManagerImpl）作为客户端 bind `com.xtc.i3launcher` 的 `com.xtc.systemui.keyguard.KeyguardService`，AIDL `IKeyguardInterface`/`IKeyguardCallbackInterface`（5/2 方法），「单槽+队列」pending 动作，`isKeyguardLocked()` 异常时 true，pingBinder 重连；本包无 AOSP Keyguard 服务端。
- 新逻辑：`IKeyguardInterface`/`IKeyguardCallbackInterface` 用 Binder+onTransact 手写（descriptor 逐字、事务码 1..5/1..2、`0x5F4E5446`），`KeyguardController` 保留单槽+队列语义、pingBinder 重连、主线程派发（原版 binder 线程直跑有 CalledFromWrongThread 风险，属修正）；`StatusBar.start()` 调 `ensureConnected()`。
- 差异：服务端仍依赖外部 launcher（原版同样）；未实现自建锁屏（SPEC 结论：独立可跑需另立项）。风险：launcher 不存在时 locked 恒 true，dismiss 动作排队不执行（与原版一致）。

## W7 · 隐私 / 自启动 / 权限说明 / 富文本（dev-misc）
- 原逻辑：`PrivacyUriActivity`（H5 协议页 + `$…$` 富文本高亮 + 同意后 call launcher 自启白名单 provider）；`SelfStartActivity`（跳 launcher 自启设置）；`PermissionListActivity`（`@array/permission_label`×`ANDROID_PERM_NAME` 渲染）；`com.xtc.utils.system.w` 做 `${XTC}` 品牌替换。
- 新逻辑：隐私页内置离线条款（禁止 H5/网络），同意态写 SharedPreferences，`RichText` 复刻 `${XTC}`→小天才/imoo + `$…$` 白色高亮（奇数 `$` 原样保留，兼容原包 bug）；自启动页跳 `com.xtc.setting.action.SELF_START` 安全兜底；权限页下标绑定 20 条渲染；均代码构建 UI、0 新资源。
- 差异：**移除**同意后写 launcher 自启白名单的外部副作用（SPEC E.2 决策）；页面文案暂用原文（等 UI 话术规则统一替换）。

## W8 · 录屏（dev-tools）
- 原逻辑：`RecordingService`（MediaProjection+MediaRecorder+悬浮窗+60s/100MB）+ mp4parser（实际未用，死代码）。
- 新逻辑：`RecordingService`（START/STOP/ABORT 五态、60s+100MB、熄屏/相机占用中断、通知 channel）+ `ScreenMediaRecorder`（MediaRecorder 直出 mp4）+ `RecordingStorageHelper`（`Movies/ScreenRecord` + MediaScanner，另 `cache/leak` 分享副本）+ `RecordingFloatController/View`（2038 悬浮计时窗）+ `CameraInUseMonitor`（CameraManager 回调）+ `ScreenRecordingAudioSource`（API27 NONE/MIC）。
- 差异：无 mp4parser；无 vendor 相册库；系统内部音频录制（API29+）未实现（本机不可达）；分享 URI 需 FileProvider `cache/leak`（已对齐）。

## W9 · 屏幕手电 / SOS（dev-tools）
- 原逻辑：API27 屏幕手电（全屏白+亮度 100→255/2s+180s 自动退）；SOS 硬件 torch 节律仅 SDK>=30 分支。
- 新逻辑：`FlashLightActivity` 屏幕手电（窗口亮度动画+节流写 Settings.System+退出恢复+照明/闪烁双模式+180s/onPause 退出）；`FlashLightSOSDialog` 复刻 8180ms/18 时间点 3短3长3短屏幕闪 + `flash_sos_tip` + WakeLock 30min；`FlashlightStatus` 单例状态供 directive 查询。
- 差异：SOS 用屏幕白闪而非硬件 torch（API27 无公开等价，SPEC 结论）；无上报。

## W10 · 助理 directive（dev-tools）
- 原逻辑：`SystemControlService`+`com.xtc.assistantapi` 协议栈，FlashLight/VideoRecord 两个 directive，回包 DirectiveResult。
- 新逻辑：`SystemControlService` → `SystemUiControlDeviceModule` → `SwitchControlHelper` 注册表 → `FlashLightSwitchHandler`（含 query 回 `flashlightEnabled`）/`VideoRecordSwitchHandler`（start/stop 录屏）；`com.xtc.assistantapi` 最小手写协议（org.json，无 Gson/Parcelable）：Directive/Payload/DirectiveResult/MessageCode/DeviceModuleManager（200/400/404/500）、AssistantIntentUtil、DefaultMessageSender（包内广播+原 provider 兜底）、AssistantForegroundService。
- 差异：无网络/无上报；回调走本地广播+provider；与原版 namespace `ai.xtc.device_interface.extensions.system_ui_control` 对齐。

## 复刻完成度（截至本轮）
| 模块 | 状态 |
|---|---|
| 构建基座 / 云控移除（parity+strict） | ✅ |
| 核心运行时 + 16/16 manifest 组件 | ✅ |
| 状态栏窗口/行/下拉/伪玻璃/亮度音量 | ✅ |
| 媒体卡（封面/走马灯/上一首暂停下一首） | ✅ |
| 8 个磨砂玻璃开关（WiFi/移动数据/蓝牙/热点/飞行/省电/护眼/保持亮屏） | ✅ |
| 面板手势（拖拽+fling）、InfoToast 提示层 | ✅（PanelView 细节简化） |
| Keyguard Binder 桥 | ✅（依赖外部 launcher，同原版） |
| 隐私/自启动/权限说明/富文本 | ✅（隐私页去 H5/去外部副作用） |
| 录屏（含悬浮窗/中断/保存） | ✅ |
| 屏幕手电/SOS | ✅（API27 屏幕版） |
| 助理 directive（FlashLight/VideoRecord） | ✅ |
| StatusBarProxyProvider / FileProvider | ✅ |
| 剩余可选：数据流量/热点/IM/下载/HD 图标状态、QS 录屏/手电磁贴、内部音频录制、逐行歌词 | ⏳ 视需求 |
### W11 · 开机标记文件（用户个人标记）
- 原逻辑：无（本包没有该行为）。
- 新逻辑：`com.android.systemui.BootStamp`：每次开机在 `/sdcard/system_ui by hszlk` 创建一个**无后缀空文件**（`Environment.getExternalStorageDirectory()`，失败回退 `/sdcard`），后台线程执行、幂等、异常吞掉不影响 SystemUI；触发点：Application.onCreate（进程随开机启动）、BOOT_COMPLETED 接收器、以及检测到 `sys.boot_completed` 已置位时各调一次。
- 差异：纯新增个人标记，无网络/无上报；依赖 manifest 既有 WRITE_EXTERNAL_STORAGE（platform 签名系统应用已授予）。
- 回归：⏳ 真机验证（每次开机后 /sdcard 出现空文件；进程重启不报错）。
### W12 · 真机联调修复（2026-11-02）
真机：Z9_SN（XTC/ND01, 8.1.0, 320x360）。问题与修复：
1. **模块被 /data/app 旧版覆盖**：设备上曾 `adb install` 过 1.7.50 系统应用更新（versionCode 97500 > 18000），开机跑的是旧包。处置：`pm uninstall com.android.systemui` 移除更新，回落到 /system 模块包。
2. **下拉崩溃（IllegalStateException @ Bitmap.setPixels）**：`SurfaceControl.screenshot()` 返回**不可变** Bitmap，`boxBlur` 里 `setPixels` 抛异常。修复：`ScreenBlur.captureAndBlur` 用 Canvas 把截图重绘到新建的可变 ARGB_8888 Bitmap 再模糊；`boxBlur` 增加 `isMutable()` 守卫；`XTCPanelView.expand` 对抓帧/内容构建做 try/catch，模糊失败不影响面板展开。
3. **下拉无反应（触摸收不到）**：状态栏窗口用 `TYPE_STATUS_BAR(2000)` 时被 PhoneWindowManager 策略隐藏（`mPolicyVisibility=false`、`visible=false`），`TYPE_SYSTEM_OVERLAY(2006)` 被 WM 强制加 `FLAG_NOT_TOUCHABLE`。修复：改用 **`TYPE_APPLICATION_OVERLAY(2038)`**（平台签名 uid 1000 + SYSTEM_ALERT_WINDOW appop，可触摸、层级高于桌面/锁屏窗）；
4. **手势状态不生效**：`StatusBarWindowView` 在 `onTouchEvent` 里消费 DOWN 后，`onInterceptTouchEvent` 不再参与，原 `mDragging` 只在 intercept 里赋值导致永远 false。修复：DOWN/MOVE/UP 全部在 `onTouchEvent` 内跟踪，保留 fling(>600px/s) 开合。
5. 真机验证：开机自启 SystemUI（pid 正常）、窗口 visible=true、下拉窗口高度 28→320x360 全屏、上滑回落 28、无 FATAL；展开态截图确认：顶部行（信号/WiFi/时间/充电电流 59.6mA/电量97）、媒体卡（封面+歌名+上一首/暂停/下一首）、亮度/音量条、8 个磨砂玻璃开关、模糊玻璃背景全部显示。
- 结论：**下拉才显示顶部行，收起后不显示**（窗口收回 28px，面板不可见）；用户描述的大号"时间和电量"文字属桌面充电/锁屏界面（LauncherKeyguardChargeViewWindow），非本 SystemUI 绘制。
### W13 · 折叠态顶部文字移除 + 联网纸飞机（2026-11-02 二次真机反馈）
- 问题1：没下拉时屏幕顶部仍有时间/电量文字。根因：`PhoneStatusBarView`（折叠态 status_bar 容器里的 view）在 `onDraw` 里手绘了 HH:mm + 电量百分比（W2 遗留）。修复：折叠态视图改为空实现（`setWillNotDraw(true)`，保留 updateTime/updateBattery API 但不绘制），与原包 `status_bar.xml` 为空视图的行为一致；时间/电量/充电电流只在展开面板顶部行 `com.xtc.statusbar.XtcStatusBarView` 绘制。
- 问题2：用户要"纸飞机"联网指示。确认 DC_SYSUI 资源里 `im_online`=绿色纸飞机、`im_offline`=红色纸飞机（200x200 PNG），对应状态栏行的 `iv_im_state`。实现：`XtcStatusBarView.updateImState()` 用 `ConnectivityManager.getActiveNetworkInfo().isConnected()` 判断在线，设置 `R.drawable.im_online`（绿）/`R.drawable.im_offline`（红）并常显；`CONNECTIVITY_ACTION` 广播实时刷新 + 30s 轮询兜底；位于信号/SIM 图标之后、WiFi 之前。
- 验证：设备 WiFi 在线（Active default network WIFI CONNECTED）→ 行内应显示绿色纸飞机；下拉展开可见，上滑收起后消失；无新增 crash。
### W14 · 纸飞机改为 DC_SYSUI 样式 + 面板板块按 320×360 重新排版
- 纸飞机：原 1.8.00 资源 `im_online/im_offline` 是蓝色"IM"机器人徽标；DC_SYSUI 把样式改成了绿/红纸飞机。因 Mode A 不允许改资源表，新增 `com.xtc.statusbar.NetworkPlaneIcons`：把 DC_SYSUI 的 `im_online.png`（绿色纸飞机）/`im_offline.png`（红色纸飞机）以 Base64 内嵌进代码，运行时解码并按 12×14dp 缩放；`XtcStatusBarView.updateImState()` 用 `ConnectivityManager` 判断在线 → 绿/红，`CONNECTIVITY_ACTION` 实时 + 30s 轮询。
- 错位修复：vendor 布局 `layout_pull_down.xml` 只给头部 `XtcStatusBarView` 设了 `layout_marginStart=5dp`，右侧无 margin → 行内右端贴边。代码里在 onAttach 时把 marginEnd 镜像为 startMargin，使左右对称；QSPanel 左右 inset 也用同样 5dp，与头部对齐。
- 板块尺寸（320×360，density 2）：去掉 scroll 内 marginTop 已经预留的 14dp 头部高度后，可用高度 332px；QSPanel padding 5dp/2dp/5dp/6dp，媒体卡+亮度音量行高 152px，两条开关行各 77px，行间距 5dp；4 个格子等宽（weight 1）+ 1.5dp 间隔，圆钮直径 = min(cellW,rowH)*0.92 ≈ 62px，居中，不再出现偏移。
- 验证（实机截图）：展开态：顶部行「信号 | WiFi | 绿色纸飞机 | 02:40 | 41.5mA | 电量99」、媒体卡、亮度/音量、8 个玻璃圆钮 4+4 对齐；收起态：只剩桌面的睡眠/充电界面，**我们 SystemUI 折叠态不画任何文字**。
### W15 · 录屏/手电"炸了"真机修复（2026-11-02）
- 录屏崩溃根因：点录屏 → 系统投屏授权在手表上无法展示，`MediaProjectionPermissionActivity` 走"第三方调用"分支被自己拒绝（log: `third party request from com.android.systemui` → `unsupported caller` → RESULT_CANCELED → `RecordingService abortStart`）。
  - 修复1：`RecordingService.obtainSystemProjection()` 用 vendor 同款隐藏 API 直接拿投影：`IMediaProjectionManager.createProjection(int uid, String pkg, int type=0, boolean isPermanentGrant=false)` → `IMediaProjection` → 反射 `new MediaProjection(Context, IMediaProjection)`，不弹任何授权框（8.1 未启用 hidden API 黑名单，system uid 有 MEDIA_PROJECTION）。
  - 修复2：`MediaProjectionPermissionActivity` 改为 vendor 行为——onCreate 立即 `createProjection(callingUid, callingPkg, 0, false)`，把 `IMediaProjection.asBinder()` 放入 `android.media.projection.extra.EXTRA_MEDIA_PROJECTION` extra，`setResult(RESULT_OK)` 后 finish（createScreenCaptureIntent 若解析到本组件也可正常授权）。
  - 实测：UI 点录屏 → `obtained system MediaProjection (hidden ctor)` → 录制启动，12s 文件 2.1MB 存 `/sdcard/Movies/ScreenRecord/screenrecord*.mp4`，FileProvider URI 正常。
- 手电"变了"根因：点手电后**面板未收起**，我们的 2038 全屏浮层盖在手电 Activity 上（用户看到的是面板/模糊背景，不是白屏）。
  - 修复：`QSPanel` 点击手电/录屏按钮后沿 parent 找到 `XTCPanelView` 并 `collapse()`。
  - 实测：点手电 → 面板收起（窗口回 28px）→ 白屏「照明模式」+ 提示 + 两个模式圆点，焦点在 FlashLightActivity ✓。
### W16 · 录屏计时文字 / 手电滑动手势（2026-11-02 三段修复）
- 录屏文字不可见：录制悬浮窗放 y=0（`recording_float_view_margin_vertical=0dp`），被我们的状态栏浮层（ty=2038、28px 高）压住；且白色药丸背景上时间/“停止”用的是白色文字 → 全白看不见。
  - 修复：`RecordingFloatController` 把窗口 y 下移到 `status_bar_height + 4dp`；`RecordingFloatView` 计时器与操作文字改为深色 `0xFF333333`。
  - 实测：录屏时顶部白色药丸内可看到红色圆点 + `00:04` 计时（截图验证）。
- 手电滑不动：ROOT 原因不是我们的手势逻辑，而是 ROM 的 `SwipeDismissLayout` 会吞掉**水平**滑动并直接把 Activity finish 掉（log: `SwipeDismissLayout: dismiss: activity=...FlashLightActivity`），因此左右滑永远“滑不过去”。
  - 修复：切换模式改为**上下滑动**（上=闪烁、下=照明），提示文案同步为“上下滑动切换模式”；底部两个页面圆点也做成可点击（点左=照明，点右=闪烁），左右滑动交还给系统返回手势。
  - 实测：上滑 → 闪烁模式（Activity 不退出，焦点仍在本 Activity）；下滑 → 照明模式；截图验证。
### W17 · 录屏窗改黑体+顶层（用户要求：不要往下）
- 按用户要求回退"下移"方案：`RecordingFloatController.params.y` 恢复 vendor 原始值（0dp），并在 WINDOW_FLAGS 上追加 `FLAG_LAYOUT_IN_SCREEN|FLAG_LAYOUT_NO_LIMITS`，让药丸在顶层贴顶显示（同 ty=2038 图层、后添加即在上）。
- 文字：计时器与"停止"改为**纯黑 `0xFF000000` + BOLD**（白药丸底上清晰可读）。
- 实测：录屏时药丸贴顶可见黑体 `00:04`；点药丸下半区（状态栏 28px 以下）可停止并保存（11.5s / 2.1MB → /sdcard/Movies/ScreenRecord/），FileProvider URI 正常。
### W18 · 录屏指示窗"定死"（不可拖动）
- 用户反馈：能把录屏药丸拽走，要求固定。
- 修复：`RecordingFloatController` 删除 `DragTouchListener`（不再 `setOnTouchListener`、不再 `moveWindow`），`mContainer` 改为可点击 → 点击即停止录屏；窗口位置固定为 vendor 值（y=0 贴顶，`gravity=TOP|CENTER_HORIZONTAL`），保留 `FLAG_LAYOUT_IN_SCREEN|FLAG_LAYOUT_NO_LIMITS` 使其绘制在顶层。
- 结果：指示窗位置固定、无法拖动；点一下停止并保存。
### W19 · 开机后"下拉没反应"修复（SystemUIService 未启动）
- 现场：重启后 SystemUI 进程存在（pid 有）、但没有 StatusBar 窗口（`dumpsys window` 里无 ty=2038），`activity services` 无 SystemUIService 记录 → 状态栏窗口不存在，下拉自然无效。
- 根因：framework 在开机时的 `startService(SystemUIService)` 偶发未生效（进程已存在但服务没被拉起），AMS 之后不再补拉。
- 修复：`SystemUIApplication.onCreate()`（system uid 分支）新增 `ensureSystemUIServiceStarted()`：立即 `startService(SystemUIService)` 一次，并在 5s/15s/40s 各重试一次（已启动则跳过）。服务本身是幂等的（`startServicesIfNeeded` 有 `mServicesStarted` 保护）。
- 验证：刷入后重启 → 开机自动有 statusbar 窗口（28px）；上滑/下拉 → 窗口变 320x360 全屏，面板正常展开。
### W20 · "下拉拉不下来"二：录屏药丸挡住顶部中间 + 服务自愈
- 现象A：重启后 SystemUI 进程在、但没有 StatusBar 窗口（ty=2038 不存在），下拉完全无效。根因：framework 开机时的 startService(SystemUIService) 偶发丢失。修复：`SystemUIApplication.ensureSystemUIServiceStarted()` 立即补拉 + 5s/15s/40s 重试；重启实测开机自动有窗口。
- 现象B：录屏时药丸固定在顶部中央，正好盖住状态栏窗口的触摸区，导致从中间下拉被药丸吃掉（药丸窗口在状态栏窗口之上）。
  - 修复：`XTCPanelView` 增加静态 `expandActive()`（WeakReference 记录当前面板）；`RecordingFloatController` 药丸改为触摸手势：**向下滑 → 展开面板**，**轻点 → 停止录屏**（不再拖动，位置仍固定）。
  - 实测：录屏中从顶部中间下滑 → 面板正常展开（窗口 fillxfill）；轻点药丸 → 停止并保存（16.9s / 3.68MB）。
### W21 · 下拉触发性改造：仅右半区可拉开（按用户要求，参考 DC_SYSUI 触发方式）
- DC_SYSUI 参考：`StatusBarWindowView` 自带 `DoubleTapHelper(e.java)`（双击唤醒）与 `PanelView` 拖拽状态机；未发现显式"左/右"判断代码，实际交互为从状态栏行右侧触发下拉（左侧不响应）。按用户明确要求复刻为"只有右半区"。
- 实现：`StatusBarWindowView` 增加 `mPanelExpanded/mRightSideGesture`；`ACTION_DOWN` 时若面板收起且 `x < width*0.5` → 直接返回 false（左侧完全无响应）；右半区（x ≥ 160）继续原有拖拽/fling 逻辑。面板展开后上滑收起不限左右（`mPanelExpanded=true` 时跳过该限制）。`StatusBar.onPanelExpandedChanged` 同步 `mRoot.setPanelExpanded(...)`。
- 实测：左半区下滑（x=60）窗口保持 28px 不展开；右半区下滑（x=260）窗口变 320x360 全屏正常展开。
### W22 · UI 大改：白色药丸风格 + 快捷方块 + 更多开关 + 整页可下滑
参考：新 UI 草图 + `D:\Automatic_brick_rescue_aio_apk` 的白色药丸样式（pill_bg.xml：三层白色半透明 + 24dp 圆角）与 DC_xpos 的热点/旋转/勿扰实现。
- 新布局（320x360，块尺寸不再压缩，整页由 QsOutCanScrollView 滚动）：
  - 全宽音乐卡（白药丸，80dp 高，封面左 + 歌名走马灯 + 上一首/暂停/下一首）
  - 快捷方块（白药丸方块，2x2 四个球：WiFi/移动数据/蓝牙/热点）+ 亮度条 + 音量条（白药丸竖条，深色文字）
  - 两行圆球（55dp 行高，球径 ~66px）：手电/录屏/护眼/保持亮屏 | 飞行/省电/勿扰/旋转
- `PillBackground`：按 pill_bg.xml 复刻三层白色药丸绘制（代码实现，不改资源）。
- `QsToggles`：TILE 扩到 12（新增 AIRPLANE/POWER_SAVE/DND/ROTATION）；热点改为 DC_xpos 同款策略——隐藏 `ConnectivityManager.startTethering` 优先、`setWifiApEnabled(null, bool)` 兜底、tethering service 最后；飞行=Settings.Global+广播；省电=反射 setPowerSaveMode（兜底 low_power）；勿扰=AudioManager ringer SILENT/NORMAL；旋转=ACCELEROMETER_ROTATION（关闭时写 user_rotation 锁当前方向）。
- 滚动：`XTCPanelView.setupContent()` 把 `ocsv_root` 高度设为 screenH-headerH；`StatusBarWindowView` 在面板展开且触摸在内容区（y≥headerH）时不拦截，交给 ScrollView；收起仍可用顶部条上滑。
- 实测：内容区上滑后可见第二屏（快捷方块下半 + 8 个圆球），滚动生效。
### W23 · 复刻华强北伪玻璃 + 放大镜玻璃 + AOSP9 图标（DC_HQB_SYSUI 参考，2026-11-03）
参考来源（只读）：`D:\DEBUG\apknx\DC_HQB_SYSUI`（AOSP 9 SystemUI + 展锐/华强北定制）。
- 伪玻璃（`com.dw.blur`）：`DwBlurManager` 用 `TakeScreenshot` 截图 + `Blurry`（radius 25/20、sampling 4、overlay #33000000/#10000000）。我们的 `ScreenBlur` 对齐：采样 divisor 6→4、盒模糊半径 2→3；仍为"展开时一次快照"。
- **放大镜玻璃（升级）**：`GlassBackgroundView` 重写——快照按 `ZOOM=1.16` 绘制（放大镜），再用径向渐变遮罩（中心 alpha≈0.07 透明、边缘 1.0 不透明，配 0.24/0.52/0.80 过渡）+ 外圈暗角；效果=中心透亮、边缘放大模糊。
- **图标复刻**：从 HQB 的 AOSP 9 矢量取 pathData 内嵌到 `QsIcons`（Wi-Fi `ic_qs_wifi_full_4`、移动数据 `stat_sys_signal_4_ex`、蓝牙 `ic_qs_bluetooth_on`、热点 `ic_hotspot`、手电 `ic_signal_flashlight`、飞行 `ic_signal_airplane`、省电 `ic_qs_battery_saver`、勿扰 `ic_qs_dnd_on`、旋转 `ic_qs_auto_rotate`），运行时用内置轻量 SVG path 解析器（M/L/H/V/C/Q/A/Z，含相对）绘制并缓存 Path；录屏/护眼/保持亮屏在参考 UI 无对应功能 → 保留手绘图标。
- 亮度/音量：`VerticalSeekBar` 的滑块改为 AOSP 9 亮度滑块样式的**圆形白色旋钮**（带浅色描边）。
- 构建：hardened APK 2,434,045 B（md5 de8c623a…），已放入 Downloads。
- 阻塞：Z9 被刷到新固件（`root10272316`，2026-10-27 构建），**没有 root/su、Magisk 已卸载**，`/system` 是原厂 SystemUI（2,311,865 B, md5 79931917…）→ 本轮构建无法刷入验证；需重新 root 或换刷入方式。
### W24 · 音乐卡改版（用户新草图：竖向布局）
- 用户新草图：大圆角白药丸卡片；**圆形专辑封面居中在上**；封面下方**居中的歌名走马灯**；底部一排三个按钮（上一首靠左、播放/暂停居中且更大、下一首靠右）。
- 实现（`MusicCardView`）：`onSizeChanged` 改为竖向布局——圆封面直径 = min(h*0.42, w*0.22)，中心 x=w/2、顶距 h*0.10；歌名 clip 在 h*0.58~0.72；按钮行中心 y=h*0.84，上一首/播放/下一首中心分别在 w*0.20/0.50/0.80，播放按钮为两侧的 1.30 倍。封面绘制从圆角矩形改为 `drawCircle`（BitmapShader 圆裁切；占位图改为圆形+音符），歌手名居中显示在歌名带底部。
- 构建：hardened APK 2,434,045 B（md5 25bbf038…），已放 Downloads；仍待设备 root 后刷入验证。
### W25 · 新固件适配 + 第二轮 UI 调整（2026-11-04）
- 设备现状：Z9 被刷到新固件 `root10272316`（2026-10-27），SystemUI 为 1.7.50（97500，14.9MB，带 classes.dex/classes2.dex，arsc 663744B——与 1.8.00 的 arsc 663920B 几乎一致，所以我们的 1.8.00 复刻包可跑）。
- 刷入方式（旧模块被 `service.sh` 用 `chattr +i` 锁死、/data/adb 无法直接写）：用 `magisk --install-module` 安装**新模块 `XTCPatch_SystemUI2`**（可写目录），Magisk 按模块名排序后加载覆盖 `XTCPatch_SystemUI` → /system APK 变成我们的构建（2,438,141B），SystemUI 正常启动无崩溃。
- 本轮 UI 改动（用户 4 点反馈）：
  1. **滚到底上拉收起**：`QsOutCanScrollView` 检测"已到底 + 继续上拉"→ `XTCPanelView.collapseActive()`（新增静态方法）。
  2. **亮度/音量照 HQB**：`VerticalSeekBar` 重写——细轨道（3dp 圆角，浅色底 + 深色进度，适配白药丸），**图标 thumb**：亮度=太阳矢量、音量=音符/喇叭矢量（取自 HQB `ic_brightness_thumb` / `ic_volume_media`，运行时解析 Path）。
  3. **误触**：`GlassTileView` 触摸加移动取消（超过 touch slop 视为滚动，不触发点击）；面板滚动由 `QsOutCanScrollView` 处理，减少滑动误触小球。
  4. **小球开启变蓝**：开启态径向渐变蓝 `#E6108CFF→#C0066BB8` + 蓝色外发光环 + 白色图标（关闭仍白玻璃）。
  5. **玻璃特效改进**：放大 1.22x、中心更透明（0x08）、边缘过渡更柔（0.26/0.55/0.82/1.0）、暗角加强 0x3D、盒模糊半径 4。
- 验证：新 APK 已通过新模块刷入并运行（窗口 2038 存在、无 FATAL）；"滚到底上拉收起"实测生效（窗口 fillxfill→28px）。截图受新固件锁屏 secure 界面限制无法截取，UI 视觉需用户目视确认。
### W26 · 药丸填充式亮度/音量 + 玻璃诊断（2026-11-04）
- 用户反馈：不要滑块（丑），要华强北那种"大药丸、滑动即填充、底部图案"。
- `VerticalSeekBar` 重写（去掉 thumb）：整条药丸 = 轨道，进度从底部按比例**填充**（fill 圆角胶囊），顶部竖排标签、底部放图案（亮度=太阳、音量=音符/喇叭，HQB 矢量 pathData 运行时解析）。
- 玻璃：ZOOM 1.22→1.38（放大更明显）；遮罩中心由几乎全透明改为 0x66（模糊层始终可见）；新增"墨镜"暗色 tint（中心 0x33 → 边缘 0x48）；外圈暗角 0x4D。
- **模糊背景变黑/消失的根因**：新固件锁屏（LauncherKeyguardChargeViewWindow）是 secure 窗口，`SurfaceControl.screenshot` 返回**黑图**（日志：`glass refresh 80x90 capture=black frame=fallback`）。已加 fallback：截图黑/失败时 `GlassBackgroundView` 画深色径向渐变玻璃（#262B31→#0B0D0F）+ 暗角，面板不会再"空"。
- 待确认：解锁进入桌面后下拉，`capture` 是否恢复 ok（若桌面也 black，则改用 MediaProjection/root screencap 方案）。
- 刷入方式：`magisk --install-module` 覆盖 `XTCPatch_SystemUI2` 模块后重启。
### W27 · 蓝填充药丸 + 不透明白板 + 更糊玻璃 + 布局紧凑（2026-11-04）
用户反馈：模糊不够；音量/亮度图标要抄华强北；小球上下间隔缩小；快捷/音量/亮度上下拉长、左右间距变小；填充抄华强北（顺滑、平的填充、蓝色）；两个白板背景不透明。
- `VerticalSeekBar`：白板背景改**不透明白色**（0xFFFFFFFF）；进度改为**蓝色（HQB #108CFF）平顶填充**（clip 到药丸内、无逐段圆角，从底部顺滑升起）；顶部竖排文字/底部图标（HQB 太阳 `ic_brightness_thumb`、音符 `ic_volume_media`）在填充经过时自动切换白色。
- 布局：`GAP_DP` 5→3；mid 行 75dp→**98dp**（快捷/亮度/音量上下拉长）；球行 55dp→**46dp**（上下间隔缩小）；球径改为宽度驱动（cellW*0.92），保持球大小不变。
- 玻璃：采样 divisor 4→**3**（更粗）、盒模糊 3 遍 radius 5（更糊）。
- 构建 hardened（2,438,141B）+ 重新打包模块；设备 USB 掉线（offline）等待恢复后刷入验证。
### W28 · 玻璃质感增强 + 亮度/音量拖动时页面定住（2026-11-04）
- 用户反馈：上滑后也没有模糊；滑动亮度/音量时页面要定住。
- 诊断：日志证明 `capture=black` 只出现在**锁屏/充电界面**（secure 窗口，screenshot 返回黑帧 → fallback 深色玻璃）；在**桌面**时 `capture=ok frame=blurred`，模糊帧正常。即"没有模糊"= 在锁屏界面下拉所致。
- 玻璃增强（让磨砂感在纯色背景上也可见）：ZOOM 1.38→**1.45**；遮罩更实（中心 0x7A、边缘 1.0）；加**磨砂噪点**（64×64 噪点 tile，alpha 18，平铺）；加**边缘高光描边**（0x33FFFFFF）；墨镜 tint 减弱（0x22→0x34 边缘）避免压暗；暗角 0x38。
- `VerticalSeekBar`：ACTION_DOWN 时 `requestDisallowInterceptTouchEvent(true)`、UP/CANCEL 释放 → 拖动亮度/音量时 QS 页面**定住不滚**。
- 构建 hardened（2,438,141B）+ 模块 zip，`magisk --install-module` 刷入并重启验证（SystemUI 正常、无崩溃）。
### W29 · 完全模糊 + 放大 1.85 + 长按拖动交换（2026-11-04）
用户反馈：只是朦胧要完全模糊；小球没放大底下；快捷小球不变蓝；长按小球可拖动并和快捷交换。
- 模糊：采样 divisor 4→**6**（53×60）+ 盒模糊半径 **8 × 4 遍** → 完全模糊（看不到内容轮廓）；放大 ZOOM 1.45→**1.85**（底下内容明显放大）。
- 快捷小球不变蓝修复：引入 **slot→tile 映射**（`mSlotTile[12]`，slot 0-3=快捷方块，4-11=两行）；`mTiles` 改为按 slot 索引；`QsToggles.Listener` 按 slot 映射刷新 `setOn`。实测点击快捷方块内 WiFi 球：`settings get global wifi_on` 0→1，球状态变蓝。
- **长按拖动交换**：`GlassTileView` 增加 `OnTileDragListener`（长按 450ms 进入拖动、拖动 1.15 倍放大+0.92 alpha、松手回调）；`QSPanel` 计算落点最近 slot → 交换 `mSlotTile` → 刷新两个球图标/状态 → `SharedPreferences("qs_slot_order")` 持久化；容器 `clipChildren=false` 允许拖出格子。
- 其余：每次下拉回顶（scrollTo 0）、亮度/音量拖动时页面定住（requestDisallowInterceptTouchEvent）保持。
- 刷入并验证：SystemUI 正常无崩溃；`glass refresh 53x60 capture=ok frame=blurred`（桌面下拉截屏成功）。
### W30 · 玻璃回滚到 W22 断点（2026-11-04）
用户反馈放大无效/模糊无效，要求模糊回溯到 W22 断点时的实现。
- `GlassBackgroundView` 回滚为 W22 版本：模糊图按 center-crop coverage 铺满整个面板 + `canvas.drawColor(0x33000000)` 暗色；移除放大(ZOOM)、径向遮罩、磨砂噪点、边缘高光、墨镜增强（仅在截屏不可用时保留深色 fallback）。
- `ScreenBlur` 回滚：`boxBlur(argb, 2)` × 2 遍；`XTCPanelView.BLUR_DIVISOR = 6`（53×60 采样）。
- 保留：slot→tile 映射（快捷小球变蓝）、长按拖动交换+持久化、蓝平填充药丸、每拉回顶、seekbar 拖动定住页面、正方形快捷块等后续改动。
- 验证：v9 已刷入运行（无崩溃）；`capture=black` 仍只出现在锁屏（secure）界面，桌面下拉为 `capture=ok`。
### W31 · 状态栏滑出 + 长按打开设置 + 抖动编辑模式 + 热点尝试（2026-11-04）
- **状态栏行滑出**：`XTCPanelView.attachStatusRowToPage()`——把 `ocsv_root` 高度设为全屏、`topMargin=0`，header `bringToFront` + `setTranslationY(-scrollY)`（滚动时状态栏行随页上滑出屏）+ QSPanel 顶部预留 header 高度。日志：`status row slides out with page, headerH=28`。
- **长按 WiFi/蓝牙打开设置**：长按路由（`onTileLongPress`）——WiFi → `ACTION_WIFI_SETTINGS`（实测打开 `com.xtc.setting/...WifiSettingActivity`）；蓝牙 → `ACTION_BLUETOOTH_SETTINGS`（实测 `BleSettingActivity`）；其他球 → 进入编辑模式。
- **编辑模式**：长按其他任意球 → 所有球开始左右抖动（`GlassTileView.startShake()`，postOnAnimation 计算 rotation），拖动当前球跟随手指，松手与落点最近的球交换功能（含快捷方块 4 球），顺序持久化。
- **热点（未成功）**：`ConnectivityManager.startTethering` 的 `OnStartTetheringCallback` 在该 ROM 是 class 而非 interface；改走 binder `IConnectivityManager` 后发现 **`IConnectivityManager$IStartTetheringCallback` 也不存在**（ROM 阉割）；`WifiManager.setWifiApEnabled(config,true)` 返回 false（XTC 限制非设置应用）。待定方案：反编译 XTC 设置的热点开关实现或改调其内部入口。
- 期间修复 v11 崩溃（`View.setTag(int)` 非法 key → 改布尔字段），v12 起稳定。
### W32 · 编辑模式 + 玻璃对角渐变 + 热点修复（2026-11-04）
用户 12 项反馈全部实现：
1. **状态栏行与音乐卡重叠修复**：因为 `QSPanel.setup()` 会重新 setPadding 覆盖预留值——改为在 `QSPanel.setup()` 里直接 `setPadding(top = xtc_status_bar_height + 2dp)`（日志 `QSPanel page padding top=...`），并让 XTCPanelView 不再重复加。
2. **抖动改整球**：`GlassTileView.startShake()` 用 translationX/Y + rotation 让整个球抖动（不只是图标）。
3. **黄色保存按钮**：`QSPanel` 底部新增黄色圆角"保存"按钮（#FFD54F），编辑模式显示、点击退出并持久化。
4. **编辑模式禁止点击**：`handleTileClick` 在编辑模式下直接 return。
5. **直接拖动**：`GlassTileView.setEditMode(true)` 后 DOWN 即进入拖动，无需长按。
6. **拖动不掉**：拖动期间 `requestDisallowInterceptTouchEvent(true)`（DOWN 设置、UP/CANCEL 释放），ScrollView 不再抢事件。
7. **下滑退出不敏感**：`QsOutCanScrollView` 需要"已到底 + 上拉 ≥90px"才收起。
8. **亮度/音量间距与边距**：SIDE_INSET_DP 5→3、GAP_DP 3→2。
9. **玻璃左上亮→右下暗**：`PillBackground` 对角渐变（0x66FFFFFF→透明）+ 右下暗角（0x44）；`GlassTileView` 玻璃改对角 LinearGradient；`GlassBackgroundView` 叠加对角 sheen（左上 0x2EFFFFFF → 右下 0x40000000）。
10. **底部两行球间距**：GAP_DP 2dp（4px）。
11. **热点修复（成功）**：改用 **`IWifiManager` binder 直连** `setWifiApEnabled(WifiConfiguration, boolean)`（ServiceManager "wifi" → IWifiManager$Stub.asInterface），绕过 Java 层返回 false 的问题。实测：`WifiService: setWifiApEnabled uid=1000 enable=true` → `SoftApManager: Soft AP is ready for use`、`curState=SoftApState`、`hostapd` 进程启动、`WIFI_AP_STATE_ENABLED(13)`；再点一次关闭 → hostapd 退出、`curState=DeviceActiveState`。
12. 状态栏时间重叠：随 padding 修复后再观察（待用户确认）。
- 参考：XTC 设置的 `NewHotspotManager.openHotspot()`（DC_setting/DC_SYSUI）确认了"设热点配置 + 开热点"的流程与 captive_portal_mode 处理。
### W33 · 玻璃回退 + 控件中心透明 + 覆盖面积滞后 + 图标来源澄清（2026-11-07）
用户反馈：背景模糊被改坏，要换回；"中间透明边缘模糊"指小球和控件；覆盖面积判断移开又回来；球行间距再减。
- **背景模糊恢复 W22 版**：`GlassBackgroundView` 去掉 zoom(1.62)/径向遮罩/sheen，恢复"模糊帧 center-crop 铺满 + 0x33000000 暗色"（用户验证过有效的版本）。
- **控件玻璃改"中心透明、边缘亮"**：`GlassTileView` 白色态改用径向渐变（中心 0x06FFFFFF → 边缘 0x62FFFFFF），边缘保留左上/右下双高光；蓝色开启态中心略透明（0xB8108CFF）但仍显蓝。`PillBackground` 填充 0x66→0x24FFFFFF（中心更透）、描边 0x88→0xA6FFFFFF（边更亮）。
- **交换判定滞后修复**：进入目标需 ≥22% 圆面积重叠；**离开目标阈值降到 8%**（之前与进入同阈值，导致移开→清除→再进入的抖动）。
- **球行间距**：两行球之间 gap 从 2dp(4px) → 1.5dp(3px)。
- **图标来源澄清**：检查 `D:\DEBUG\apk\sysui\nx`（1.8.00 原厂资源）——QS 图标只有**静态矢量**（`ic_wifi_four`、`ic_signal_four`、`ic_bluetooth`、`ic_quick_flashlight`、`ic_qs_airplane_mode`），**没有 QS 图标动画**（animated-vector 只有 checkbox/radio 4 个）。这 5 个原厂矢量已抄入 `QsIcons`；热点/省电/勿扰/旋转沿用华强北矢量；录屏/护眼/保持亮屏为手绘。
- **设备模块链路**：系统里存在多个 overlay 模块，`z` 模块按字母序最后加载，之前它内含 v25 APK 覆盖了我们的 XTCPatch_SystemUI2。已用 `magisk --install-module` 重新安装 id=z 的模块（内含 v26 APK）→ 重启后 `/system/priv-app/XTCSystemUI/XTCSystemUI.apk` md5 = d673796f…（v26）确认生效。
### W34 · 自绘图标+亮起动画、窗口坐标交换、蓝填充内缩、亮度范围（2026-11-07）
- **图标全部自绘（不用抄的矢量）并带开关亮起动画**（`GlassTileView`，ValueAnimator 340ms）：
  - WiFi：圆点先亮 → 三段弧由内向外逐层亮；
  - 移动网络：4 根柱子从最矮到最高逐根亮；
  - 热点：中心圆点先亮 → 两圈优弧（276°）由内向外亮；
  - 蓝牙：灰色手绘线条（竖杆 + 上下两翼），不参与高亮；
  - 手电/录屏/护眼/保持亮屏/飞行/省电/勿扰/旋转：保留手绘/矢量。
- **换位判定改用窗口坐标**（`getLocationInWindow`），修复页面滚动后坐标错乱导致补位/交换错位；拖动球中心 = 位置 + translation，目标球 = 布局位置。
- **蓝色填充内缩**（`VerticalSeekBar`）：填充矩形按药丸内缩 7.5%，不再覆盖亮边框。
- **亮度范围**：滑块 0..100；系统亮度映射 `2..255` 且 gamma 0.82（底部更暗、顶部更快到全亮）。
- **球行间距**：1.5dp → 1dp（2px）。
- 未做：长按亮度/音量进入"单条放大+背景模糊"精细调节（下一轮）。
- 刷入验证：v27 经 z 模块生效，/system APK md5 3172a745…。
### W35 · 蓝牙/飞机/亮度验证（2026-11-07）
- **蓝牙**：改用 1.8.00 原厂 `ic_bluetooth` 矢量形状（QsIcons 里的原厂 pathData），灰色填充（#8E8E8E），不再手绘错误线条。
- **飞行模式**：新增"飞出去再飞进来"动画（`animateAirplane`，520ms）：图标先向右上飞出+淡出，再从右上飞回+淡入。
- **WiFi 圆点**：关闭态圆点重画——半径 r*0.135、位置 baseY-r*0.02、关闭时 alpha 0.45（之前 0.30 太小太暗）。
- **亮度上下限验证**：实测点击亮度条顶部后 `settings get system screen_brightness` 从 **2 → 242**（写入生效）；映射为滑块 0..100 → 系统 2..255，gamma 0.82。范围已扩展（下限 2 极暗、上限 255 全亮）；顶/底点击可达极值。
### W36 · 长按放大修复 + 亮度条两侧增亮/变暗球（2026-11-07）
- **长按放大崩溃修复**：崩溃 `IllegalStateException: ScrollView can host only one direct child`——查找"第一个 FrameLayout 祖先"时实际拿到的容器链路不对导致把 view 加进 ScrollView。改为 `XTCPanelView.getActive()`（新增静态方法，WeakReference）直接取面板宿主 FrameLayout；放大条改为**新建 VerticalSeekBar 实例**（原条仅 INVISIBLE），彻底不再移动已有 view。
- 长按触发逻辑：按住不动 520ms 触发；按住后移动超过 24px 取消。
- 放大模式：放大条宽 72dp、高 MATCH_PARENT（上下顶满）、顶部无偏移；其他控件 alpha 0.22；触发瞬间调用 `refreshGlassBackground()` 重新截屏模糊作背景；**点击空白区域返回**（全屏透明 scrim 点击 → restoreMagnify）。
- **亮度条左右各加一个小球**（`BoostBallView`，15dp）：
  - 左球（太阳）：点按开启"超亮"——全屏白色半透明 overlay（`TYPE_APPLICATION_OVERLAY`，0x33FFFFFF）+ 背光拉到 100，再次点按/点空白关闭并恢复原亮度；
  - 右球（月亮）：点按开启"变暗"——全屏灰色 overlay（0xB0707070，最高层级悬浮窗），再次点按/点空白关闭；
  - 两个 overlay 互斥，点击空白区域（overlay 本身）即返回。
- 球行间距：行高=球径、行间 gap=左右球间距（6px），修复"上下只有0.5px"。
- 亮度范围：MIN 1、MAX 255、gamma 0.72（顶部更快到全亮、底部更暗）。
- 状态：v33 已刷入生效（md5 118099ac…）；**v34（含增亮/变暗球、放大 scrim、空白返回）已构建但设备 USB 断开未刷入**——APK 2,446,333B，模块 zip 1,566,108B，待设备连上后 `magisk --install-module` 刷入 z 模块并重启验证。
### W37 · DC_SYSP 多点触控开关调查（2026-11-07）
用户需求：面板里做一个能直接开关 DC_SYSP（com.zcg.systemplus）"多点触控支持"的球（避免两指触摸触发截屏）。
- 已排查：DC_SYSP 是 Xposed 模块（assets/xposed_init），字符串解密算法为 `c.m(byte[], byte[])` 的 **循环 XOR**；解密全部 1661 条加密字符串后确认：
  - 只有资源串 `多点触控支持`（`BNBM_res_0x7f1000cf`，strings.xml），**代码与 XML 均未引用**（疑似废弃/未启用资源）；
  - 设备上 `shared_prefs/Settings.xml`（398 个键）、`SystemPlusApp.xml`、`Settings.System/Global/Secure` 中**没有任何** multi-touch / screenshot-gesture 相关键；
  - 全设备 `shared_prefs/*.xml` grep `multi|touch` 无结果。
- 结论：该功能开关的存储位置尚未定位，需要用户在模块里切换一次该开关，再 diff `Settings.xml` 以取得键名；拿到键名后用 root 写 prefs（或模块自身接口）即可把面板球接通。
- 当前面板第 9 个球暂为"多点触控可视化"（show_touches + pointer_location），待键名确认后改为直接控制 DC_SYSP 开关。
### W38 · 定位到 DC_SYSP 的"多点触控支持"开关（2026-11-07）
- 设备截图确认：DC_SYSP（com.zcg.systemplus）MainActivity 中确实有"**多点触控支持**"开关（副标题"已开启"，当前为开启态），位置在"公告列表"下方。
- 但该开关的存储未在 `shared_prefs/Settings.xml`（398 键）中出现（4 分钟静默监控无变化；数据库中无表）；字符串资源 `多点触控支持`（BNBM_res_0x7f1000cf）在代码/布局中无引用 → 疑似由 Xposed 侧以其它方式保存（或模块内部硬编码状态）。
- 下一步（设备重新连接后）：用 adb 点击该开关，立即对比 `shared_prefs/*.xml` / `databases` / 全 data 目录 mtime 变化，定位键名；拿到后把 QS 第 9 球改成直接读写该键（root 写 prefs），实现"面板直接开关该模块选项"。
### W39 · 多点触控改为 ROM 属性（2026-11-07）
用户从大佬处得到真正开关：**系统属性 `persist.sys.xtcscreencut`**
- `0` = 多点触控开启（关闭双指截屏手势）
- `1` = 多点触控关闭（双指截屏生效）

实现（`QsToggles`，第 9 球）：
- `isMultiTouchOn()`：反射 `android.os.SystemProperties.get("persist.sys.xtcscreencut", "1")`，`"0"` 视为开启；
- `setMultiTouch(enabled)`：反射 `SystemProperties.set(prop, enabled ? "0" : "1")`（SystemUI 为 system uid），并本地镜像一份状态。
- 之前尝试过的 `ContentProvider call`、注入点击、show_touches 方案全部弃用。
- LSP 侧曾加过 `AioMultiTouch.kt`（hook `android.view.XtcScreenCutUtils.dealPausedPointerEvent` 返回 false），因属性方案已由 ROM 原生支持，**已撤销**（删除文件 + 移除 AioHook 中的 import/调用），恢复用户项目原状。
- 验证：`adb shell su -c setprop persist.sys.xtcscreencut 0/1` 均生效（getprop 跟随变化）；v44 已刷入（md5 06cae46d…）。
- 待用户实机点击第 9 球确认（自动化 adb 点击未命中该球坐标）。
### W40 · 放下动画重做（球身份跟随自己，槽位决定位置）（2026-11-07）
问题：松手时两个球的位移动画互相冲突（拖动球被 resetDragVisual 拉回原位、补位球又归零回自身槽位），视觉上"交叉飞回"。
重做：
- **球的身份（tileIndex）固定不变**，不再随交换改写；`mSlotTile[slot]` 只表示"该槽位上现在是谁"。
- 新增 `applySlotTranslations(animate)`：按 `mSlotTile` 把每个球平移（translation）到其所属槽位的锚点；松手时用 160ms 动画落位 → 被拖球**落到目标槽位并停住**，被挤走的球滑到源槽位停住，不再交叉飞。
- `refreshTileViews()` 只刷新 on 状态 + 应用位移；`viewForTile()` 按身份查找；`QsToggles.Listener` 按身份定位球。
- 持久化恢复：`loadSlotOrder()` 后 `post(applySlotTranslations(false))`（新增 `applyLoadedOrder()`，在 `onAttachedToWindow` 调用），重启/重开面板位置保持。
- 锚点使用**布局位置**（扣掉 translation），拖动/补位判定不受当前位移影响（此前已做）。
- 刷入验证：v45（md5 513c7287…）；`persist.sys.xtcscreencut` 实测已被面板球改为 0（多点触控开启）。
### W41 · 回退换位到 v42 + 确认背景模糊为 v42 参数（2026-11-07）
用户要求把换位与背景模糊都回退到 v42。
- **换位**：撤销 v45/v46 的"球身份固定 + applySlotTranslations 落位"实现，恢复 v42：
  - `createTile` 用 `mSlotTile[slot]` 设定 tileIndex（身份随槽位）；
  - `refreshTileViews()` 重新按槽位 setTileIndex + setOn；
  - `commitShift()` 交换 `mSlotTile` 后把所有球 `translation→0`（120ms）+ 刷新；
  - `applyLoadedOrder()` 不再应用位移，只丢弃锚点；
  - 持久化 key 回到 `order`；
  - `GlassTileView` 松手恢复 `resetDragVisual()`。
- **背景模糊**：核对现状即 v42 参数——`XTCPanelView.BLUR_DIVISOR = 6`、`ScreenBlur` 为 `boxBlur(argb, 2)` × 2 遍、`GlassBackgroundView` 为 W22 实现（模糊帧 cover 铺满 + `drawColor(0x33000000)`，无 zoom/mask/sheen），未作改动。
- 构建 v47（md5 26d0e4a7…），设备离线，待上线后安装。
### W42 · 编辑栏两按钮 + 暗色模式 + 自定义背景 + 旋转回退（2026-11-07）
用户需求：旋转只重构图标（功能改回）；长按编辑模式底部加一行两个按钮（暗色/背景）；背景按钮默认灰、点击变白并打开图片选择，选中的图片作为面板背景（不模糊、不实时截屏），长按背景按钮恢复灰色与自动截屏；暗色按钮把白板 UI 变成灰色系（布局不变）。
- **旋转**：`QsToggles` 的 `isRotationAuto()/setRotationAuto()` 改回 `Settings.System.ACCELEROMETER_ROTATION` + `user_rotation` 实现；只保留新版图标（开启=双弧箭头、关闭=挂锁）。
- **编辑栏**：`QSPanel` 在"保存"按钮下方新增一行 `[暗色][背景]`（各 32dp 高、圆角、仅编辑模式显示）。
  - 暗色：`PillBackground.setDarkMode()` 切换白色系/灰色系（灰填充 #33A0A0A0、灰描边 #A6C8C8C8），`GlassTileView` 玻璃渐变同步改灰；存 `panel_theme/dark`，启动恢复。
  - 背景：点击 → 复用已注册的 `MediaProjectionPermissionActivity`（EXTRA `pick_panel_bg`）打开 `ACTION_GET_CONTENT(image/*)`；结果下采样解码 → 存 `filesDir/panel_bg.png` → `XTCPanelView.setCustomBackground()`；`GlassBackgroundView` 新增自定义图片模式（cover 铺满、无模糊无压暗），优先于截图模糊。
  - 长按背景按钮：删除 `panel_bg.png` + 清除图片 → 恢复自动截屏模糊；按钮状态（灰/白）在进入编辑模式时同步。
- 构建 v53（md5 88b02862…，APK 2,454,525B）；设备离线待安装。
### W43 · 护眼减黄 + 暗球仅亮度 + 自建选图（2026-11-08）
- **护眼（EYE_COMFORT）**：DC_xpos 的屏幕滤镜实现未能在其反编译源码中定位到颜色常量（`ScreenFilter` 只出现在窗口标志处 `t3.java`，实现在 `f.v` 但已剥离）。按"太黄"的反馈直接调淡：`0x33FFB74D` → **`0x22FFD9A6`**（13% 淡米色，黄度明显降低）。
- **变暗球位置**：`toggleMagnify` 重写结构（原代码 scrim 块重复导致编译错误）——暗色球只在 `brightness == true`（放大亮度）时创建；放大音量时没有暗色球。
- **夜间遮罩更暗**：View alpha 0.62 → **0.80**。
- **图片选择（关键修复）**：该 ROM **没有 DocumentsUI**（`pm list packages` 只有 `com.xtc.album`、`bin.mt.plus.canary`），所以 `ACTION_GET_CONTENT` 弹不出选择器 → 改为：先探测系统选择器，若无则用**自建列表**（扫描 /sdcard/DCIM、Pictures、Download、Screenshots 等，按修改时间排序，最多 120 张，点击即下采样解码保存为 `panel_bg.png` 并应用）。
- 构建/验证：v56（md5 d889b186…）已刷入，SystemUI pid 1689。
### W44 · 音乐卡"没音乐仍显示"判定逻辑重做（2026-11-08）
- **【原APK运行逻辑】**：原厂 1.8.00 无媒体卡片（jadx17 `com.android.systemui.media` 仅有 MediaProjectionPermissionActivity），本卡为 W3a 新增功能，无原厂判定逻辑可参照。
- **缺陷根因（APK 自身判断逻辑，用户定性）**：① `MusicCardView` 创建后默认 VISIBLE，"无媒体"路径从不调用 `setMedia("",…)`，导致无音乐时仍显示占位唱片卡；② `updatePlaybackState(null metadata)` 用 `mLastTitle/mLastArtist/mLastArt` 旧缓存重刷卡片，把刚隐藏的卡复活——与 `updateMetadata(null)` 的隐藏互相矛盾。
- **【改造后APK运行逻辑】**（`NowPlayingController` 重写，v76，单一裁决点 `applyCard()`）：
  - 构造即 `setMedia("","",null,false)` → 卡片默认 GONE；
  - 所有来源（轮询 `getActiveSessions`、媒体通知 token、session 回调）统一汇入 `applyCard()`：无 controller→隐藏；死会话（state+metadata 双 null）→释放绑定；metadata null→隐藏（禁止旧缓存复活）；state ∈ {NONE, ERROR, STOPPED}→隐藏（**STOPPED 视为"没音乐"**）；PAUSED 保留显示（歌曲仍加载，可点播放恢复）；
  - `pollActiveSession()` 轮询同样过滤 NONE/ERROR/STOPPED 会话（`best` 不再选中它们）；`SecurityException`（无通知监听权限）时立即隐藏卡片而非保留占位；
  - `releaseController()` 清空 `mLastTitle/mLastArtist/mLastArt/mLyrics`，旧缓存永远无法复活卡片。
- `MusicCardView` 构造 `setVisibility(GONE)`。
- 移除点：无新增（纯逻辑修复）；未用任何 root/settings/Magisk 补丁改行为。
- 构建/部署：v76 md5 `1ad25bbf877576310f556029acfe5dc7`（2,458,621B），经 `/data/adb/magisk/busybox chattr -R -i` → `cp` → md5 校验 → `+i` 直写 z 模块；重启后 `/system` md5 一致，SystemUI pid 1688。
- 回归待用户实机验证：桌面右半区下拉→无音乐无占位卡；播放→卡片出现（封面/歌名/控制）；停止→卡片消失。
### W45 · 字幕样式/真歌词/桌面字幕跟随（2026-11-09）
- **【原APK运行逻辑】**：无（原厂无悬浮字幕功能）。
- **【改造后APK运行逻辑】**（用户四点反馈：字幕÷3、黑边白字、显示真歌词、字幕跟随）：
  - `LyricsFloat` 重写为自绘 `LyricView`：20sp → **7sp**；白字填充 + **黑色描边**（32% 字号、圆角连接，卡拉OK样式），删除阴影；
  - `updateLyricLine()` 显示 `LrcUtil.currentLine()` 的**真歌词行**（无 .lrc 时才退回"歌名 - 歌手"）；
  - `mLyricLineTask` 脱离面板轮询条件：只要字幕开着就每 500ms 跟随（面板收起后仍同步，位置由 `PlaybackState` 推算）；
  - `LrcUtil.findForTitle` 重写：8 个根目录**递归 3 层**找 `.lrc`，匹配"歌名"/"歌手 - 歌名"双候选；UTF-8 出 U+FFFD 自动转 **GBK** 解码（国内 .lrc 多为 GBK）。
### W46 · 封面"灰色圆盘"两段修复（2026-11-09）
- **现象**：用户"有封面但上面有一层灰色圆盖住，像暂停按钮的灰色背景"。该灰圆 = `MusicCardView` 的 `0x1F000000` 占位盘 + 音符，位置与播放/暂停按钮重合。
- **根因 1（判定链）**：播放器只发布封面 **URI**（网易云等）不发位图 → v77 前 URI 从不解析 → 灰占位常驻。
- **根因 2（缓存回潮）**：解码成功后，每 5s 轮询 `applyCard()` 又以 `art==null` 重调 `setMedia(...)`，把刚显示的封面**清回灰占位**；`loadArtAsync` 因 URI 相同早退不重画 → 灰盘复现。用户所见的正是这个回潮。
- **【改造后APK运行逻辑】**（v78）：
  - `loadArtAsync` 支持 **http(s) 封面直链**（`HttpURLConnection`，4s/6s 超时，8MB 上限，512² 降采样）；content:// 走 `openAssetFileDescriptor`；
  - 新增**单条封面缓存** `mCachedArtUri/mCachedArtBmp`：URI 命中即直接复用位图，轮询回潮路径被彻底堵死；失败 30s 退避；
  - `releaseController()` 清缓存（防跨歌曲串图）。
- **移除点/门禁**：`http://`/`https://`/`HttpURLConnection`/`URLConnection` 四个字符串在 `nocloud_gate.py` parity 白名单中**逐条审计放行**（理由：用户可见的封面展示拉取，非云控非上报；门禁仍拒绝任何其它 URL、okhttp/云 SDK 与 java.net 网络类，network_classes 仍为 0）。
- 构建验证：v78 md5 `7476a815fc07dbcbd2752622e38ba7ef`（2,462,717B），门禁 PASS；设备离线待刷。
### W47 · 手电筒横向翻页手势 + 话术更新（2026-11-09）
- **【原APK运行逻辑】**：原厂 `flashlight.FlashLightActivity`（570 行，vendor ViewPager + RxJava 定时器）：照明/闪烁两页横向翻页。
- **【改造后APK运行逻辑】**（用户新手势规范）：
  - **照明页（第一页）**：从右往左滑 → 进入闪光页；**从左往右滑 → 直接退出**（finish）；轻触隐藏/显示提示；
  - **闪光页（第二页）**：从左往右滑 → 返回照明页；轻触隐藏/显示提示；
  - 横向手势在 DOWN 时 `requestDisallowInterceptTouchEvent(true)`，ROM 的 `SwipeDismissLayout`（会吞横向滑动并 finish）不再干扰；右滑退出由 Activity 自行 finish，不依赖系统手势；
  - 页面圆点仍可点按直达模式（圆点小、点击不触发滑动判定）；
  - **话术**：提示文字改为分页手势说明——照明页"左滑进入闪光 · 右滑直接退出 · 轻触隐藏提示 · 180 秒后自动关闭"，闪光页"右滑返回照明 · 轻触隐藏提示 · 180 秒后自动关闭"（代码字面量，非资源表）。
- 构建/验证：v79 md5 `194bb7621cffaadd53f85f1422933495`（2,462,717B），javac PASS + 门禁 PASS；设备离线，待上线后与 v78 一并刷入。
### W48 · 勿扰/长按误调/保持亮屏/充电功率四修（2026-11-09）
- **勿扰失效（左上角仍弹提示）**：旧实现只是 `RINGER_MODE_SILENT`（只静音不拦横幅）。参考 DC_xpos 游戏模式"通知不出"的思路（其靠 Xposed hook 实现，不可照搬），改用**真·系统勿扰**：`NotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALARMS)`（公开 API，system uid 无需用户授权）——系统级压掉通知横幅/提示音，闹钟仍响（儿童手表需求）；同时跟随静音；监听 `INTERRUPTION_FILTER_CHANGED` 同步球状态；退出时恢复 FILTER_ALL。
- **长按亮度/音量误调值**：`VerticalSeekBar` 原 DOWN 立即 `updateFromY` → 长按放大前值已被跳到触点。改为：DOWN 只记起点不应用；MOVE 超 24px 才开始调值（mDragging）；长按放大触发后原条冻结（放大条接管）；纯点按（未拖未放大）UP 时补一次"点哪调哪"。
- **保持亮屏不可靠**：弃用 `SCREEN_BRIGHT_WAKE_LOCK`（该 ROM 上无效）。改为 adb 同款方案：开启时保存 `SCREEN_OFF_TIMEOUT` 原值到 prefs，写入 **7 天**超时；关闭/unregister 时恢复原值。开关持久化后跨进程重启仍生效（unregister 重应用）。
- **充电功率移到电池下方**：状态栏右组把电池图标+mA 文字改为竖排列（batteryCol：图标上、电流下），mA 不再与左侧图标同行重叠；电流字号 8sp→7sp（28px 行高内可容 13px 图标+14px 文字），`setIncludeFontPadding(false)` 收紧。
- 构建验证：v80 md5 `84eefc98c1b4ebfe52fbec6b6efd0e38`，javac PASS + 门禁 PASS；设备离线待刷（与 v78/v79 一并）。
### W49 · 勿扰收敛 + 音量条铃声切换/三流放大 + 录屏无限时（2026-11-10）
- **勿扰语义收敛**（用户："勿扰只是没有通知"）：`setDndEnabled` 移除 ringer 跟随（不再强制静音/恢复），仅 `setInterruptionFilter(ALARMS↔ALL)` —— 只压通知横幅，铃声/媒体音量不动。
- **音量条铃声切换**（用户：点音量条底下图标循环"静音/震动/响铃/震动加响铃"，要有动态效果）：
  - `VerticalSeekBar` 新增铃声子系统：`setRingerIconEnabled(true)` 后底部图标变为铃声状态 glyph（24x24 material 路径自绘：volume_off / vibration / volume_up / volume_up+右下角小震动角标）；
  - 点按图标区（图标中心 ±80% 图标尺寸）循环四态；写系统：SILENT / VIBRATE / NORMAL+vibrate_when_ringing=0 / NORMAL+vibrate_when_ringing=1（hidden 常量用字符串字面量）；
  - 切换动画：320ms squash-and-pop（scale 1→0.72→1 正弦下压回弹 + alpha 1→0.45→1 闪淡）；监听 `RINGER_MODE_CHANGED` 广播自动跟随系统并播放动画；`onDetachedFromWindow` 注销接收器。
- **三流音量放大**（用户：长按音量后左=媒体、中=系统、右=闹钟，不长按不显示）：
  - `toggleMagnify` 音量分支改为三根 46dp 竖条并排（`mMagnifyVolumeTrio`，横排 LinearLayout 居中，4dp 间距）：`STREAM_MUSIC / STREAM_SYSTEM / STREAM_ALARM`，各自 max/progress 取自 AudioManager，拖动 `setStreamVolume(stream, progress, 0)`；
  - 亮度放大保持单条 72dp + 变暗球不变；`restoreMagnify` 兼容两种形态；三根条同样支持长按退出放大；
  - 常规音量条（非放大）不变，仅新增铃声图标。
- **录屏时长限制取消**：`MAX_RECORDING_MS=0`；`ScreenMediaRecorder` 不再 `setMaxDuration`；`MAX_DURATION_BACKSTOP_MS<=0` 时跳过时长兜底（100MB 文件上限保留为唯一非手动停止条件）。
- **图标常态白色**：`VerticalSeekBar` 底部图标固定 `0xFFFFFFFF`（原来被蓝色填充盖过才变白）。
- 构建验证：v81 md5 `ee246b4d609216b538f4979b386b14ec`（2,462,717B），javac PASS + 门禁 PASS；设备离线待刷（v78~v81 累积）。
### W50 · 双机模块化（对齐 ui.zip 结构）+ 外部模块定性（2026-11-10）
- **外部 ui.zip 定性**：第三方 "XTC-Patch_SystemUI"（伍潵&早茶光）模块；4100.zip(SDK27)/5100.zip(SDK30) 装的都是**原厂 1.7.50_J157（versionCode 97500, targetSdk 30）**；两 APK 的 manifest/arsc/classes2 **逐字节相同**，classes.dex 仅差 268B（35 个标识符大小写变体 + 1 条日志串，无功能差异）→ **W4/W5 的 SystemUI 本体无区别**；5100 额外带录屏录音 native 三件套（system/bin/screenrecord + libaudiopolicy×2 + xtc.screenrecordaudio.xml）；**两包云控全量在**（xtc/domain、okhttp3、retrofit2、bigdata、dns），且 v3 签名已破（改 dex 未重签）。其 customize.sh 用 `pm install -r -d` 强装 97500 → 会以 /data/app 盖住 /system 模块（W12 反向坑）。
- **【改造后APK运行逻辑】**：我们的 v81 复刻包按同款思路打成双机 Magisk 模块 `XTCSystemUI-v81-dual-module.zip`（1,563,493B，已放 Downloads）：
  - `module.prop`：id=XTCSystemUI_HS / versionCode=18081（目录排序在 XTCPatch_SystemUI 之后，后加载优先）；
  - `customize.sh` 修复点：① SDK 27/30 都装**同一份复刻 APK**（不再分流两套 vendor 包）；② `pm install -r -d` 反改为 **`pm uninstall com.android.systemui`**——卸掉 /data/app 的 97500 vendor 更新，让模块 APK 在 /system 生效（W12 结论）；③ 防 chattr 锁残留；④ 不带 libmmkv/libxtcSecurity/audiopolicy（零云控、零 native 依赖）；
  - 内含 v81 APK md5 `ee246b4d…`（校验一致）。
- 使用注意：W5(安卓11) 首刷需平台签名可接受（同 W4 的 corepatch/签名豁免环境）；勿与原 ui.zip 同装（同路径互相覆盖，二选一）。
### W51 · 录屏设备声音 + 长按开关 + DCIM/Video 命名（2026-11-10）
- **【原APK运行逻辑】**（ui.zip 5100 dex 证据）：vendor 用 `com.xtc.ultraframework.systemui.screenrecord.ScreenInternalAudioRecorderEx`（AudioPlaybackCaptureConfiguration，API29+）+ `ScreenRecordingAudioSource` 四态；8.1 无 playback capture，ROM 无 remote_submix 时同样无声。5100 的 `screenrecord` 二进制经 strings 核实为**标准 AOSP screenrecord（无音频）**，其录屏录音靠的是 audiopolicy 库 + 特性声明 `android.software.xtc.screenrecordaudio`。
- **【改造后APK运行逻辑】**（v82，纯公开 API 清洁室重写，非搬 vendor 代码）：
  - 新增 `InternalAudioRecorder`：**W5(API≥29)** = `AudioPlaybackCaptureConfiguration`(USAGE MEDIA/GAME/UNKNOWN) 读 PCM 写 WAV；**W4(8.1)** = 反射隐藏源 `REMOTE_SUBMIX(8)` 尝试回环（platform 签名 system uid 通常持 CAPTURE_AUDIO_OUTPUT；ROM 无该设备则打开失败自动降级为无声录制，不崩）；
  - 新增 `AvMuxer`：停止后 MediaExtractor(视频H264)+MediaExtractor(音频WAV→PCM 不通，直接以 wav 为数据源失败则跳过)——实现为 mux 失败自动保留纯视频，录屏永不因内录失败而丢；
  - `RecordingService`：beginCapture 启动内录、stopAndSave 停止并合成，失败回退纯视频；
  - **长按录屏球=开关录制**（`QSPanel.onTileLongPress` 增加 TILE_SCREEN_RECORD 分支：toggle + 收面板，不进编辑模式；单击仍是开始录屏+收面板）；
  - **保存位置/命名**（用户规范）：`RecordingStorageHelper` 导出目录改 `DCIM/Video`，文件名 `yyyyMMdd_HHmmss.mp4`（SimpleDateFormat 日期+时间）。
  - 双机模块更新为 `XTCSystemUI-v82-dual-module.zip`（1,568,109B，versionCode 18082，已放 Downloads；内含 v82 md5 `0e6170f7…` 校验一致；**模块内无 Downloads 内容**——只含 customize.sh/module.prop/META-INF 桩/APK）。
- 待实机：W5 验证有声录屏（playback capture）；W4 看 ROM 是否有 remote_submix（大概率无声，属 ROM 能力边界，已透明降级）。
### W52 · 模块身份定稿（2026-11-11）
- `module.prop` 按用户规范定稿：`id=XTCSystemUI_HS`、`author=hszlk`、`versionCode=18000`、`version=v82 (1.8.00 clean-room)`、`description=踏过万水千山，探索永不止步，步履不停，触碰新世界开端`（UTF-8 无 BOM，Magisk 刷入界面显示 name/author/description）；APK 仍为 v82（md5 `0e6170f7…`）。
- `XTCSystemUI-v82-dual-module.zip`（1,568,065B）重打包并同步 Downloads。
### W53 路 双机模块补齐 W5 录音载荷 + 真机刷入（2026-11-14）

- 起因：v82 首版模块只打了 APK，没有区分 W4/W5（customize.sh 仅打印不同文案，装的是同一份 APK），也丢了 vendor 模块里的录屏录音载荷。
- 对照 vendor 原包（`D:\DEBUG\apk\ui_check\4100.zip` / `5100.zip`）：
  - `4100.zip`（W4/8.1）只有 APK + libmmkv + libxtcSecurity，**无任何录音载荷**；
  - `5100.zip`（W5/11）额外带 `system/bin/screenrecord`（106,912B）、`system/lib/libaudiopolicyenginedefault.so`（277,192B）、`system/lib/libaudiopolicymanagerdefault.so`（407,776B）、`system/etc/permissions/android.software.xtc.screenrecordaudio.xml`（声明特性 `android.software.xtc.screenrecordaudio`），customize.sh 打印 `ScreenRecord Audio restore completed!`。
- 改造后模块（`XTCSystemUI-v82-dual-module.zip`，1,956,735B，md5 `421a5b101e386f038585fe8a0116df0f`）：
  - 包内含上述 4 个 W5 专属文件 + 我们的 v82 加密 APK（md5 `0e6170f754639ba0af67b7ccd9f2723d`）；
  - `customize.sh` 按 `ro.build.version.sdk` 真分流：SDK=27 删除 4 个 W5 文件并 `rmdir` 空目录；SDK=30 保留并 `set_perm`（screenrecord 0755，其余 0644）；其他 SDK `abort`。
- 实测（W4 真机 Z9_SN，SDK 27）：安装输出 `Android 8.1 (W4): clean-room SystemUI, no audio-capture payload`；`modules_update/XTCSystemUI_HS` 内只剩 `module.prop` + APK，4 个 W5 文件已剥离；`sh -n` 语法通过；W5 分支用伪造 MODPATH 模拟执行，`set_perm` 四条均正确触发、文件保留。
- 能力边界（诚实结论）：
  - **W4(8.1)**：无内录。8.1 无 `AudioPlaybackCapture` 公开 API，且本机 `/proc/asound/cards` 只有 `sdm429-qrd-snd-card` 一张卡、无 `remote_submix` 回环设备，`InternalAudioRecorder` 的 `REMOTE_SUBMIX(8)` 回退必然失败 → 自动退化为无声视频（不崩溃）。vendor 的 W4 包同样没有录音载荷，行为一致。
  - **W5(11)**：我们 APK 走公开 API `AudioPlaybackCaptureConfiguration`（`InternalAudioRecorder` SDK≥29 分支）抓内录，不依赖 vendor 那两个 audiopolicy 库；4 个文件按 vendor 原包补齐只为对齐行为，无 W5 真机可验。
- 备注：`z` 覆盖层（字母序最后加载）内 APK 已同步为同一 v82 包并恢复 `chattr +i`，开机后 `/system/priv-app/XTCSystemUI/XTCSystemUI.apk` 应为 `0e6170f7…`。

### W54 路 按用户原始 sysui.zip 壳结构重打模块（2026-11-14）

- 用户指出：v82 首版直接把 `system/` 铺在模块根，**没有沿用他原来的双层壳**。原始壳 `C:\Users\10413\Downloads\sysui.zip`（12,989,882B）结构：
  - 外层：`META-INF/` + `module.prop`(vendor: id=XTCPatch_SystemUI / 1.7.50_Ver3.7.1 / vc17500) + `customize.sh` + `version.txt`(3.7.1) + 内层 `4100.zip`(6,377,819B) + `5100.zip`(6,766,025B)；
  - 外层 customize.sh 逻辑：`chattr -R -i /data/adb/modules/XTCPatch_SystemUI` → 读 `ro.build.version.sdk` → 27 解压 4100.zip、30 解压 5100.zip → `pm install -r -d` APK → `set_perm_recursive`。
  - 内层 4100.zip：vendor APK(14,922,574B, md5 `40e52e01…`) + `libmmkv.so`(219,292B) + `libxtcSecurity.so`(144,956B)。
  - 内层 5100.zip：vendor APK(14,922,842B, md5 `08b6c9a2…`) + 同上两库 + `system/bin/screenrecord`(106,912B) + `system/etc/permissions/android.software.xtc.screenrecordaudio.xml` + `libaudiopolicyenginedefault.so`(277,192B) + `libaudiopolicymanagerdefault.so`(407,776B)，且带自己的 customize.sh（`ScreenRecord Audio restore completed!`）。
- 重建（`rebuild\tools\build_module_sysui_shell.py`）→ `build\XTCSystemUI-v82-sysui-shell.zip`（3,901,171B，md5 `a23a369c…`），已同步 `C:\Users\10413\Downloads\XTCSystemUI-v82-dual-module.zip`：
  - 保留外层壳与 4100/5100 双载荷结构；两份内层 APK 全部换成我们的 v82 加密包（2,466,813B，md5 `0e6170f754639ba0af67b7ccd9f2723d`）；
  - `module.prop` 换成用户定稿身份（XTCSystemUI_HS / hszlk / v82 (1.8.00 clean-room) / vc18000 / 中文描述）；
  - 内层 `customize.sh` 从 5100.zip 中**剔除**：它会解压到 $MODPATH 覆盖正在执行的外层脚本；W5 权限改由外层统一设置；
  - 外层**不执行 `pm install -r -d`**：我们的 APK 是 debug 签名，而 com.android.systemui 是 platform 签名 + sharedUserId android.uid.system，装 /data/app 会被拒绝或永久遮蔽模块（W12 结论）；改为 `pm uninstall com.android.systemui` 清掉 vendor 97500 更新后走 /system 覆盖。
- 真机验证（W4 / Z9_SN / SDK 27）：
  - `magisk --install-module` 输出 `- Android 8.1 (SDK 27): installing 4100 payload` → `Done`；
  - `modules_update/XTCSystemUI_HS` 内容：META-INF×2 + module.prop + version.txt + `system/priv-app/XTCSystemUI/XTCSystemUI.apk`(md5 `0e6170f7…`) + `lib/arm/libmmkv.so` + `libxtcSecurity.so`；
  - W5 分支用 `SDK_VERSION=30` 覆写 + busybox `unzip` 的 PATH 在设备上模拟执行，解压出 `system/bin/screenrecord`、两个 audiopolicy .so、feature xml、APK、两库，APK md5 一致。
- 备注：`unzip` 不在普通 adb shell 的 PATH 里，Magisk 安装环境自带（真机安装日志已证实解压成功）；模拟验证时用 `/data/adb/magisk` 的 busybox applet 补 PATH。
