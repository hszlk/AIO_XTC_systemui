# XTCSystemUI 1.8.00 —— 快捷开关 QS / 音量 / 省电·长续航 复刻实现规格（SPEC）

> 目标包 `com.android.systemui` 1.8.00（XTC 儿童手表 SystemUI，Android 8.1 / API 27，platform 签名）。
> 本文只做**只读分析 + 复刻规格**，未修改任何代码或既有文件。
> 产出目录：`D:\DEBUG\apk\sysui\analysis\modules\qs-volume\`

## 0. 证据口径（务必先读）

| 缩写 | 绝对路径 | 说明 |
|---|---|---|
| `<J>` | `D:\DEBUG\apk\sysui\work\jadx18b\sources` | **唯一可信代码源**，1.8.00 精确版（unquicken 后全量反编译，方法体完整） |
| `<R>` | `D:\DEBUG\apk\sysui\nx\resources` | 原包解码资源（`res/`、`assets/`） |
| `<S>` | `D:\DEBUG\apk\sysui\src\main\java` | 已有手写实现（W1 运行时 + W2 窗口骨架），本模块待补 |
| `<RISK>` | `D:\DEBUG\apk\sysui\analysis\risk\RISK-MAP.md` | 风险总表（引用编号即可，本文不重写） |

补充约定：

* jadx 的 `/* JADX INFO: compiled from: Xxx.java */` 注释保留了**原始类名**，本文所有「原始类名」均从该注释取得（例：`<J>\com\android\systemui\qs\d.java:34` → `QSTileHost.java`）。混淆名用于定位文件。
* 反编译代码里的 `com.android.systemui.m` **就是 `R`**：`m.l`=layout、`m.i`=id、`m.g`=dimen、`m.j`=integer、`m.h`=drawable、`m.n`=style、`m.C0016m`=string。本文已把 `m.*` 还原成 `R.*`，并给出**数字 id 对应的资源名**（用 `<R>\res\values\public.xml` 反查）。
* 代码里直接写数字的资源 id（如 `2131558642`）已全部反查成名字；反查结果集中在 §C.3/§C.5。
* 交叉校验：`work\jadx17\sources`（1.7.50）同文件 diff 极小（本模块仅 `qs/tiles/c.java`、`tiles/SettingSeekBar.java` 行数略有差异，逻辑一致），未发现 1.8.00 行为回退。

---

## A. 类清单与判定（`com.android.systemui.qs` 共 **43 个类** + 2 个 lambda 文件）

判定口径：
* **必需** = 复刻必须自研（去掉埋点/云控后逻辑仍需保留）
* **可选** = 原包已存在但运行期无调用点，或属"锦上添花"
* **dead** = 可完全不实现
* **移交** = 属其它模块（`mod-tools`：手电筒/录屏），本模块只留接口位

| # | 原始类名 | 混淆文件:行 | 职责 | 判定 |
|---|---|---|---|---|
| 1 | `AutoAddTracker` | `<J>\com\android\systemui\qs\a.java:16` | 自动添加磁贴（`Prefs.Key.QS_*_ADDED`）+ `Settings.Secure.qs_auto_tiles` | **dead**（全仓无引用；本产品磁贴固定 7 个） |
| 2 | `TileQueryHelper` | `<J>\com\android\systemui\qs\a\a.java:25` | 枚举第三方 `TileService` 供磁贴选择器 | **dead**（选择器 UI 不存在，全仓无引用） |
| 3 | `AutoSizingList` | `<J>\com\android\systemui\qs\AutoSizingList.java:14` | 按高度自适应行数的 LinearLayout | **可选**（无引用；旧 AOSP 遗留） |
| 4 | `PathInterpolatorBuilder` | `<J>\com\android\systemui\qs\b.java:9` | 自建 PathInterpolator | **dead**（无引用，已改用 `android.view.animation.PathInterpolator`） |
| 5 | `BaseTileTipView` | `<J>\com\android\systemui\qs\BaseTileTipView.java:9` | 磁贴气泡提示抽象基类（inflate + 重建） | **必需** |
| 6 | `QSHost` | `<J>\com\android\systemui\qs\c.java:9` | 磁贴宿主接口（`createTile/createTileView/getTiles/collapsePanels…`） | **必需**（可裁剪到 6 个方法） |
| 7 | `QSTileHost` | `<J>\com\android\systemui\qs\d.java:36` | 磁贴宿主实现：spec 列表持久化、tile 生命周期 | **必需**（去插件化） |
| 8 | `SecureSetting` | `<J>\com\android\systemui\qs\e.java:12` | 监听 Settings.Secure 变化 | **dead**（无引用） |
| 9 | `CustomTile` | `<J>\com\android\systemui\qs\external\a.java:30` | `custom(component)` 第三方磁贴 | **dead**（见 §F） |
| 10 | `PackageManagerAdapter` | `<J>\com\android\systemui\qs\external\b.java:14` | 第三方磁贴包管理适配 | **dead** |
| 11 | `QSTileServiceWrapper` | `<J>\com\android\systemui\qs\external\c.java:9` | 第三方磁贴 Binder 包装 | **dead** |
| 12 | `TileServiceManager` | `<J>\com\android\systemui\qs\external\d.java:20` | 第三方磁贴服务生命周期 | **dead** |
| 13 | `TileServices`（`IQSService.Stub`） | `<J>\com\android\systemui\qs\external\e.java:29` | 对外 Binder 服务，供第三方磁贴回写状态 | **dead** |
| 14 | `TileLifecycleManager` | `<J>\com\android\systemui\qs\external\TileLifecycleManager.java:27` | 第三方磁贴 ServiceConnection/DeathRecipient | **dead** |
| 15 | `TouchAnimator` | `<J>\com\android\systemui\qs\f.java:13` | 手势值动画 | **dead**（无引用） |
| 16 | `QsOutCanScrollView` | `<J>\com\android\systemui\qs\QsOutCanScrollView.java:9` | 下拉面板外层滚动容器（`OutCanScrollView` 子类，仅拦「已到底时的滚轮」） | **必需**（`<S>` 已有 39 行壳） |
| 17 | `QSPanel` | `<J>\com\android\systemui\qs\QSPanel.java:26` | QS 面板容器：建 TileLayout、绑定 Host、tile 列表管理 | **必需**（`<S>` 现有 36 行空壳，需重写） |
| 18 | `TileGridLayout` | `<J>\com\android\systemui\qs\TileGridLayout.java:10` | 第 3 区固定单元格网格（2 列） | **必需** |
| 19 | `FuncSetting` | `<J>\com\android\systemui\qs\tileimpl\a.java:9` | 长按型设置项基类（音量/亮度条） | **必需** |
| 20 | `IQSTileBehavior` | `<J>\com\android\systemui\qs\tileimpl\b.java:5` | 磁贴埋点回调 `onClick(int)/onLongClick()` | **必需**（复刻时空实现） |
| 21 | `BaseShortCutView` | `<J>\com\android\systemui\qs\tileimpl\BaseShortCutView.java:19` | 音乐快捷卡片基类（加/减/移除三态） | **必需**（若做音乐卡片） |
| 22 | `QSFactoryImpl` | `<J>\com\android\systemui\qs\tileimpl\c.java:16` | **spec → Tile 类映射表（7 个 spec 的唯一入口）** | **必需** |
| 23 | `QSTileImpl` | `<J>\com\android\systemui\qs\tileimpl\d.java:25` | 磁贴抽象基类：Handler 消息机、回调、listening、状态收敛 | **必需（核心）** |
| 24 | `XTCQSIconView` | `<J>\com\android\systemui\qs\tileimpl\XTCQSIconView.java:14` | **Lottie 图标视图**（播放 `pull_up_setting/*.json`） | **必需（核心）** |
| 25 | `XTCQSTileBaseView` | `<J>\com\android\systemui\qs\tileimpl\XTCQSTileBaseView.java:18` | 磁贴卡片视图（背景、按压动画、点击/长按） | **必需** |
| 26 | `TileLayout` | `<J>\com\android\systemui\qs\TileLayout.java:13` | AOSP 网格布局（`quick_settings_num_columns`=3） | **可选**（`QSPanel.c()` 硬编码用 `XTCTileLayout`，此类已无调用点） |
| 27 | `AirplaneTile` | `<J>\com\android\systemui\qs\tiles\a.java:12` | 飞行模式开关 | **必需** |
| 28 | `BluetoothTile` | `<J>\com\android\systemui\qs\tiles\b.java:10` | 蓝牙开关 | **必需** |
| 29 | `BrightnessSettingSeekBar` | `<J>\com\android\systemui\qs\tiles\BrightnessSettingSeekBar.java:8` | 亮度条（含"点图标区"判定） | **必需** |
| 30 | `BrightnessFuncSetting` | `<J>\com\android\systemui\qs\tiles\c.java:27` | 亮度条逻辑（Settings + 动画 + 自动亮度） | **必需** |
| 31 | `FlashlightTile` | `<J>\com\android\systemui\qs\tiles\d.java:18` | 手电筒开关 | **移交** `mod-tools` |
| 32 | `PowerSaveTile` | `<J>\com\android\systemui\qs\tiles\e.java:14` | 省电/长续航开关 + 2 个确认弹窗 | **必需** |
| 33 | `RingModeTile` | `<J>\com\android\systemui\qs\tiles\f.java:16` | 铃音模式开关（静音/振动/铃声/振动+铃声 4 态） | **必需** |
| 34 | `ScreenRecordTile` | `<J>\com\android\systemui\qs\tiles\g.java:17` | 录屏开关 | **移交** `mod-tools` |
| 35 | `WifiTile` | `<J>\com\android\systemui\qs\tiles\h.java:20` | WLAN 开关（含热点冲突确认框） | **必需** |
| 36 | `MusicController` | `<J>\com\android\systemui\qs\tiles\music\a.java:30` | 通知栏音乐控制（读 Notification extras / PendingIntent） | **必需**（若做音乐卡片，否则可选） |
| 37 | `MusicNotificationConstants` | `<J>\com\android\systemui\qs\tiles\music\b.java:5` | 音乐通知 extras 键常量 | **必需**（随 36） |
| 38 | `MusicSessionRecorder` | `<J>\com\android\systemui\qs\tiles\music\c.java:19` | 记录音乐播放时长 | **可选** |
| 39 | `MusicShortCutView` | `<J>\com\android\systemui\qs\tiles\music\MusicShortCutView.java:17` | 音乐卡片视图（折叠/展开两态） | **必需**（若做音乐卡片） |
| 40 | `SettingSeekBar` | `<J>\com\android\systemui\qs\tiles\SettingSeekBar.java:16` | **通用设置滑条**（分档、动画、长按、竖向手势让位） | **必需（核心）** |
| 41 | `VolumeFuncSetting` | `<J>\com\android\systemui\qs\tiles\VolumeFuncSetting.java:29` | 音量条逻辑（通话/媒体/蓝牙SCO、图标、提示、模式门禁） | **必需（核心）** |
| 42 | `TileTipView` | `<J>\com\android\systemui\qs\TileTipView.java:12` | 磁贴气泡提示实现（`layout_tile_tip_view`） | **必需** |
| 43 | `XTCTileLayout` | `<J>\com\android\systemui\qs\XTCTileLayout.java:34` | **QS 主体容器**：2+2+3 分栏、音量/亮度条、音乐、色彩校正提示、气泡提示 | **必需（核心）** |

统计：**43 类 = 必需 27 + 可选 3 + dead 11 + 移交 2**。

外部依赖（不在 `qs` 包，但本模块直接调用，需 Lead 统一排期）：

| 依赖 | 文件:行 | 用途 | 复刻处置 |
|---|---|---|---|
| `WifiControllerImpl`（`policy.s`） | `<J>\...\statusbar\policy\s.java:14-105` | WLAN 状态/开关/热点检测 | 自研 ~90 行 |
| `BluetoothControllerImpl`（`policy.d`） | `...\policy\d.java:18-271` | 蓝牙状态/开关 | 自研 ~150 行（裁剪） |
| `RingModeControllerImpl`（`policy.p`） | `...\policy\p.java:17-139` | 铃音模式读写 + `vibrate_when_ringing` 观察 | 自研 ~120 行 |
| `PowerSaveModeControllerImpl`（`policy.m`） | `...\policy\m.java:9-44` | 省电开关（`persist.sys.wleswitch` + 电量 ≤10） | 自研 ~45 行，见 RISK R-72 |
| `AirplaneModeControllerImpl`（`policy.b`） | `...\policy\b.java:16-116` | 飞行模式（Settings.Global + 广播 + 数据中心上报） | 自研 ~70 行，**删上报** |
| `ConfigurationController`（`policy.f`） | `...\policy\f.java:7-23` | 配置变化回调（`d_()` 用于重刷文案） | 可选 ~40 行 |
| `ModeManager`（`baseservice.f`） | `<J>\com\xtc\baseservice\f.java:18-367` | 手表模式（上课/睡眠/省电/长续航…）→ 音量/铃音门禁文案 | 本地化 ~120 行 |
| `AudioHelper`（`baseservice.a`） | `<J>\com\xtc\baseservice\a.java:21-489` | 各流音量读写、SCO 音量、提示音 | 本地化 ~120 行（去掉 SoundPool 部分） |
| `RingModeHelper`（`baseservice.i`） | `<J>\com\xtc\baseservice\i.java:11-120` | 4 态铃音模式 → AudioManager 映射 | 自研 ~90 行（**关键算法**） |
| `BatteryHelper`（`baseservice.a.b`） | `<J>\com\xtc\baseservice\a\b.java:21-367` | 电量百分比（省电开关门禁 `b()<=10`） | 自研 ~40 行（只取 `ACTION_BATTERY_CHANGED` 的 level/scale） |
| `DisplayManagerEx` | `<J>\com\xtc\ultraframework\hardware\DisplayManagerEx.java` | 亮度 level↔255 映射、动画时长 | 本地化 ~70 行，见 RISK R-85 |
| `XTCStatusBarController`（`statusbar.c.a`） | `<J>\com\xtc\statusbar\c\a.java:9` + 实现 `c\b.java:11-76` | 磁贴的 toast（白/红） | 复用 W2 状态栏提示通道 |
| `PopWindowManager`（`statusbar.j`） | `<J>\com\xtc\statusbar\j.java:203 行` | 省电/长续航弹窗装载 | 属 popup 模块 |
| `DoubleFlatButton` / `DoubleFlatBtnWithTitleDialog` / `PressAnimHelper` / `MarqueeTextView` | `<J>\com\xtc\ui\widget\...` | 弹窗按钮、按压动画、跑马灯 | 属 W3 控件库 |
| `Lottie`（`com.airbnb.lottie.LottieAnimationView`） | 第三方库（APK 内置） | 7 个开关动画 | **需引入 lottie ≥3.0 依赖**（见 §E 风险 R1） |

---

## B. 七个开关逐项：原逻辑 → 公开 API 复刻方案 → 文案

### B.0 七个 spec 的唯一映射点（先看这里）

`QSFactoryImpl.createTile(spec)`：`<J>\com\android\systemui\qs\tileimpl\c.java:27-56`

| spec 字符串 | 生成类 | 证据 | 本模块 |
|---|---|---|---|
| `wifi` | `WifiTile` | `c.java:29-31` | ✅ |
| `bt`（`QSFactory.QS_SPEC_BLUETOOTH`） | `BluetoothTile` | `c.java:32-34` | ✅ |
| `flashlight` | `FlashlightTile` | `c.java:35-37` | 移交 |
| `ringMode`（`QS_SPEC_RING_MODE`） | `RingModeTile` | `c.java:38-40` | ✅ |
| `airplane` | `AirplaneTile` | `c.java:41-43` | ✅ |
| `powerSave`（`QS_SPEC_POWER_SAVE`） | `PowerSaveTile` | `c.java:44-46` | ✅ |
| `screenRecord`（`QS_SPEC_SCREEN_RECORD`） | `ScreenRecordTile` | `c.java:47-49` | 移交 |
| `custom(...)` | `CustomTile` | `c.java:50-52` | dead |

磁贴列表来自字符串资源（不是硬编码）：
* `R.string.quick_settings_tiles` = `default`（`<R>\res\values\strings.xml:162`）
* `R.string.quick_settings_tiles_default` = `wifi,bt,flashlight,ringMode,powerSave,screenRecord,airplane`（`strings.xml:163`）
* `R.string.quick_settings_tiles_stock` 同上（`strings.xml:164`，供 `TileQueryHelper` 用，dead）
* 解析逻辑：`<J>\com\android\systemui\qs\d.java:289-309`（`default` 关键字展开）

**最终面板布局**（`qs_panel_default_columns`=2，`<R>\res\values\integers.xml:9`）：
`XTCTileLayout.a(index)` → 下标 0..1 进 `ll_tile_container_one`；2..3 进 `ll_tile_container_two`；4..6 进 `gl_tile_container_three`（2 列网格）。证据 `<J>\...\qs\XTCTileLayout.java:330-339`。
即：**第 1 行 wifi、bt；第 2 行 flashlight、ringMode；第 3 行 powerSave、screenRecord、airplane**。

磁贴通用生命周期（所有开关共用，必须原样复刻）：
`<J>\...\qs\tileimpl\d.java`：`click()` 206-228（`isAvailable()` 否→`a(false)`提示；`mo352b()`=是否需要收起面板）、`longClick()` 235-245、`refreshState()` 247-250、`setListening()` 148-164、`Handler` 消息表 436-549（1=addCallback 2=click 4=longClick 5=refresh 10=destroy 11=clearState 12=removeCallbacks 13=removeCallback 14=setListening 15=re-add）。

### B.1 WLAN（`wifi`）

| 项 | 原逻辑 | 证据 |
|---|---|---|
| 数据源 | `WifiController.mo619a()` → `WifiManager.isWifiEnabled()`；`b()` → 热点是否开启 | `<J>\...\policy\s.java:63-65`、`:74-76` |
| 点击前置检查 | **点击时**若热点开启（`controller.b()`）：先收起面板（`QSHost.mo333a()`），再 post 弹「开启提醒」确认框；确认后 `statusBar.b()`（关热点）再 `WifiTile.b()` 真开 | `tiles\h.java:84-100`、`:101-119`、`:190-215` |
| 点击 | `setWifiEnabled(!当前)`；再写系统属性 `z.G/z.H`（`SystemProperties.setBoolean`）；toast 白字 | `tiles\h.java:102-126` |
| 状态刷新 | 注册 `android.net.wifi.WIFI_STATE_CHANGED`（state∈{1,3} 时回调 `onWifiEnableStateChanged`） | `policy\s.java:18-36` |
| 动画 | `animationResource="pull_up_setting/wifi.json"`；开→progress 0.0 且 doAnimate=true；关→progress 1.0 | `tiles\h.java:146-153` |
| 落盘 | 无 SharedPreferences；仅 `Settings.Secure.sysui_qs_tiles`（磁贴列表） | `qs\d.java:37` |
| 埋点 | `com.xtc.statusbar.a.c.a(ctx,"wifi",i)`（**删**，RISK R-78 类） | `tiles\h.java:158-165` |

**复刻公开 API**：`WifiManager.isWifiEnabled()/setWifiEnabled(boolean)` + `registerReceiver(WIFI_STATE_CHANGED)`；热点冲突检测用 `WifiManager` 的反射 `isWifiApEnabled()`（或直接省略确认框，产品可后续接回）。
系统属性 `z.G/z.H` 为私有属性（RISK R-74 类，属 launcher 约定），**复刻建议删除**（不影响开关）。

**文案**：`wifi_opened`「WLAN已打开」、`wifi_closed`「WLAN已关闭」、`open_tips`「开启提醒」、`open_hot_spot_or_wifi_switch_tips`「手表%s，确定要开启吗？」、`open_wifi_when_hot_spot_open_tips`「正在共享个人热点，打开WLAN会关闭个人热点」、`sure`「确定」、`cancel`「取消」。证据 `tiles\h.java:112`、`:192-215`。

### B.2 蓝牙（`bt`）

| 项 | 原逻辑 | 证据 |
|---|---|---|
| 数据源 | `BluetoothController.mo599a()`=`isEnabled()`；`b()`=`BluetoothAdapter.getState()`（`STATE_ON`=23 判为"已开"） | `tiles\b.java:75-106`、`policy\d.java:86-88` |
| 点击 | 当前关→`controller.c()`（`BluetoothHelper.m1765a()`）+ toast `blue_opened`「蓝牙已打开」；当前开→`controller.d()`（`adapter.disable()`）+ toast `blue_half_open`「蓝牙连接已断开」 | `tiles\b.java:74-87` |
| 事件 | `STATE_CHANGED / CONNECTION_STATE_CHANGED / SCAN_MODE_CHANGED` + 5 个厂商 action | `policy\d.java:55-71` |
| 动画 | `"pull_up_setting/bluetooth.json"`，**`maxAnimationProgress=0.9`**（关闭态常显 0.9 帧），连接中 `0→1.0` | `tiles\b.java:107-120` |
| 气泡提示（**首次引导**） | 开启时：若 `funSwitch` 开 **且** 用户**从未从 QS 进过蓝牙设置页**（MMKV 键 `sp_enter_blue_setting_from_qs` 为 false）→ `QSHost.a(spec, connect_ble_device, like_ble_earphones)` 弹气泡；否则 `QSHost.b(spec)` 收起。进入设置页时把该标记置 true（`share.b.a(ctx,true)`） | `tiles\b.java:121-131`、`:144-152`、`<J>\com\android\common\library\share\b.java:227-233`、`SharedKeyConstants_SystemUi.java:30` |
| 长按 | `Intent("android.settings.BLUETOOTH_SETTINGS")` + `FLAG_ACTIVITY_NEW_TASK|FLAG_ACTIVITY_CLEAR_TASK`（`addFlags(0x10008000)`） | `tiles\b.java:144-152` |

**复刻公开 API**：`BluetoothAdapter.getDefaultAdapter()`；`isEnabled()/enable()/disable()`（API27 上 `enable/disable` 需 `BLUETOOTH_ADMIN`，platform 签名可直接调）；状态用 `getState()==STATE_ON`；监听 `android.bluetooth.adapter.action.STATE_CHANGED`。
气泡提示的"未绑定"条件来自 `com.xtc.system.account.h.m1843a(ctx,"…",false)`（账号云控，RISK R-68）→ **复刻置 false/删除**，只保留"未连接蓝牙耳机"条件。

**文案**：`blue_opened`「蓝牙已打开」、`blue_half_open`「蓝牙连接已断开」、`connect_ble_device`「连接蓝牙设备」、`like_ble_earphones`「如蓝牙耳机等」。证据 `tiles\b.java:78`、`:127`。

### B.3 铃声 / 静音 / 振动（`ringMode`，一套代码 4 个状态、**7 个动画里的 4 个**）

这是本模块最复杂的开关：**一个磁贴承载 4 种状态**，`value` 恒为 false，状态全靠 `animationResource + animationProgress` 表达。

| 内部值 | 语义 | 动画 asset | 文案 key |
|---|---|---|---|
| 0 | 静音 | `pull_up_setting/silence.json` | `volume_slient`「静音」 |
| 1 | 振动 | `pull_up_setting/vibration.json` | `remind_mode_vibrate`「振动」 |
| 2 | 铃声 | `pull_up_setting/bell.json` | `remind_mode_ring`「铃声」 |
| 3 | 振动+铃声 | `pull_up_setting/bell_vibration.json` | `remind_mode_vibrate_ring`「振动+铃声」 |

初始化映射表：`<J>\...\qs\tiles\f.java:104-129`（`f763a`=状态顺序表，`f764a`=值→动画，`b`=值→文案）。
**静音是否可用**：`o.mo603a()`（`RingModeControllerImpl.mo603a()` → `AudioUtils.a(ctx)`：`hasSystemFeature("android.software.mcu.volume_split")` 且 `FunSwitch` 开关，见 `<J>\...\util\a.java:12-32`）。不支持静音时，表里剔除 0 态，并在 init 时若当前是静音则**强制切成振动**（`f.java:95-103`，注释原文「当前不支持静音，改为振动」）。

| 项 | 原逻辑 | 证据 |
|---|---|---|
| 数据源 | `RingModeHelper.mo1260a()`：`AudioManager.getRingerMode()` 0→静音；2→`vibrate_when_ringing==1 ? 3 : 2`；1→`persist.sys.ringmode.zero ? 3 : 1` | `<J>\com\xtc\baseservice\i.java:56-68` |
| 写操作 | 1→`setRingerMode(RINGER_MODE_VIBRATE)`；0→`SILENT`；2→`NORMAL`+`vibrate_when_ringing=0`；3→`NORMAL`+`vibrate_when_ringing=1` | `i.java:71-113` |
| 点击 | 环形轮转：支持改铃声→`index=当前+1 mod n`；不支持（上课/睡眠）→ 只在静音/振动间循环，并首次提示 | `tiles\f.java:143-177` |
| 状态刷新 | `RINGER_MODE_CHANGED` 广播 + `Settings.System.vibrate_when_ringing` ContentObserver + ModeManager 回调 | `policy\p.java:42-75` |
| 动画 | 点击后 `animationProgress=0.0`、`doAnimate=true`；刷新时 `progress=1.0`、不再播 | `tiles\f.java:196-202` |
| 长按 | 支持改铃声→父类（进 `REMINDMODE_SETTINGS`）；否则 toast `ModeManager.e()` 文案 | `tiles\f.java:228-235` |
| 禁用 | `mo350a()`=`o.c()`（`SystemProperties.getBoolean(z.am)&&getBoolean(z.al)`）；点击时再查一次 → toast `remind_mode_forbid_by_guardian` | `tiles\f.java:150-153`、`policy\p.java:107-110` |

**复刻公开 API**：
```
AudioManager am = ctx.getSystemService(AudioManager.class);
am.getRingerMode();                           // 0/1/2
am.setRingerMode(AudioManager.RINGER_MODE_*); // 0/1/2
Settings.System.putInt(cr, "vibrate_when_ringing", 0/1);
```
即完全可用 framework 公开 API 复现 4 态，**无需 RingModeHelper 的私有属性**；`persist.sys.ringmode.zero` 仅用于"静音态也响铃"的厂商语义，复刻可去掉。

**文案**：`remind_mode_vibrate`「振动」、`remind_mode_ring`「铃声」、`remind_mode_vibrate_ring`「振动+铃声」、`volume_slient`「静音」、`remind_mode_forbid_by_guardian`「已禁用，去家长APP设置」、`tip_class_mode_edit_ring_mode`「上课禁用中，仅支持振动或静音调节」、`tip_sleep_mode_edit_ring_mode`「睡眠模式中，仅支持振动或静音调节」。证据 `tiles\f.java:122-128`、`:152`、`:225`，ModeManager `<J>\com\xtc\baseservice\f.java:285-307`。

### B.4 省电 / 长续航（`powerSave`）

| 项 | 原逻辑 | 证据 |
|---|---|---|
| 数据源 | `PowerSaveModeController.a()` → `SystemProperties.getBoolean("persist.sys.wleswitch", false)` | `<J>\...\policy\m.java:30-33` |
| 可用性 | `mo350a()` = 已开启 **或** 云控开关（`com.xtc.watch.longendurance` 的 funSwitch==1） | `tiles\e.java:131-136` |
| 电量门禁 | `controller.b()` = `BatteryHelper`（`<J>\com\xtc\baseservice\a\b.java:44-46`，`a.b.a()`) 电量 ≤ **10%** | `policy\m.java:40-43` |
| 不可用时点击 | 父类 `click()` 走 `a(false)`：弹 PopWindowManager 提示，内容 = `getTileLabel()`「长续航」 | `tiles\e.java:138-141`、`tileimpl\d.java:210-228` |
| 点击-开启 | 若云控开关关闭→直接 return；否则弹 `WatchLongEnduranceTipLayout` 确认 → 确认后 `powerSaveController.a(true)` | `tiles\e.java:72-82`、`:154-201` |
| 点击-关闭 | 电量≤10 → 弹 `LowPowerTipLayout` 劝阻；否则 `a(false)` | `tiles\e.java:83-93`、`:210-255` |
| 广播 | 确认/取消后发 `com.xtc.setting.updateWatchLongEnduranceStatus` 给设置侧 | `tiles\e.java:203-208` |
| 长按 | 父类 `j()`：`Intent("com.xtc.launcher.WatchLongEnduranceRecorderSettingActivity")`；`resolveActivity` 为空则只打 log（不弹框、不跳转） | `tiles\e.java:16-18`、`:143-147`、`tileimpl\d.java:304-318` |
| 磁贴标题 | `getTileLabel()` = `dialog_longbattery`「长续航」 | `tiles\e.java:149-152` |
| 动画 | `"pull_up_setting/power_save.json"`，开→`backgroundIcon=setting_func_enable_bg_green` + doAnimate；关→`setting_func_disable_bg` | `tiles\e.java:99-116` |

**复刻公开 API**：framework 只能**读** `PowerManager.isPowerSaveMode()`；"省电/长续航"是本机厂商业务（写 `persist.sys.wleswitch` 由外部服务消费）。复刻方案：
1. 只读态：`PowerManager.isPowerSaveMode()` 做 UI 显示；
2. 写态：保留 `SystemProperties.setBoolean("persist.sys.wleswitch", …)`（`<J>\com\xtc\ultraframework\os\SystemProperties` 反射 `android.os.SystemProperties`，platform 签名可用）；
3. 完全不想碰私有属性时，可退化为"本地开关 + 发广播 `com.xtc.setting.updateWatchLongEnduranceStatus`"。
见 RISK R-72（判"保留无害"）。

**文案**：`dialog_longbattery`「长续航」、`watch_long_battery`「省电模式」、`watch_long_battery_tip`「通过关闭手表部分功能\n来延长电池续航能力。\n保留接听电话、接收微\n聊通知。可在表盘界面\n下拉退出此模式」、`low_power`「电量不足」、`low_power_tip`「电量不足10%啦！为了\n家长能够及时联系你，\n建议尽快充电，不退出\n长续航模式。」、`open`「开启」、`exit`「退出」、`cancel`「取消」。证据 `tiles\e.java:151`、`<J>\com\xtc\statusbar\LowPowerTipLayout.java:40-44`、`WatchLongEnduranceTipLayout.java:40-44`。

### B.5 飞行模式（`airplane`）

| 项 | 原逻辑 | 证据 |
|---|---|---|
| 数据源 | `Settings.Global.getInt("airplane_mode_on",0)!=0` | `<J>\com\xtc\utils\system\NetworkUtils.java:434-436` |
| 写操作 | `Settings.Global.putInt("airplane_mode_on",…)` + 广播 `android.intent.action.AIRPLANE_MODE`（extra=`state`） | `NetworkUtils.java:438-443` |
| 点击 | 取反 → 写 → toast；另上报数据中心（**删**） | `tiles\a.java:86-99`、`policy\b.java:103-115` |
| 刷新 | 监听 `ACTION_AIRPLANE_MODE` | `policy\b.java:23-30` |
| 图标 | 静态 `R.drawable.ic_qs_airplane_mode`（**无 lottie**，`BooleanState`） | `tiles\a.java:103-116` |

**复刻公开 API**：`ConnectivityManager.setAirplaneMode(boolean)` 是 @hide（platform 签名可反射调用）；或用 `Settings.Global` + 广播（与原版一致，需 `WRITE_SECURE_SETTINGS`/system 权限）。推荐二者：先试 `Settings.Global`，失败再反射。
**RISK**：`DataPoint` 上报（`policy\b.java:103-115`）属"数据中心 IPC 上报"，**必须删除**。

**文案**：`airplane_opened`「飞行模式已打开」、`airplane_closed`「飞行模式已关闭」（`tiles\a.java:97`）。注意**没有 lottie**，图标为静态矢量图。

### B.6 手电筒 / 录屏（`flashlight` / `screenRecord`）—— 本模块边界

* `FlashlightTile`：`<J>\...\tiles\d.java:18-244`，`implements com.android.systemui.vassistant.b.a`；用 `TorchFlashlightControllerImpl`（`policy\q.java` 613 行）。
* `ScreenRecordTile`：`<J>\...\tiles\g.java:17-244`；录屏服务在 `com.android.systemui.screenrecord.*`。
* 二者**归 `mod-tools`**；本模块只需保证 `QSFactoryImpl` 里保留这两个分支（或由 `mod-tools` 提供类），文字/动画资源见 §C。

### B.7 7 个开关的公开 API 映射（一句话版）

> `wifi → WifiManager.isWifiEnabled()/setWifiEnabled()`；`bt → BluetoothAdapter.isEnabled()/enable()/disable()`（`getState()==STATE_ON` 判态）；`ringMode → AudioManager.getRingerMode()/setRingerMode()` + `Settings.System("vibrate_when_ringing")` 组合出 0 静音/1 振动/2 铃声/3 振动+铃声 四态；`powerSave → PowerManager.isPowerSaveMode()（读）+ SystemProperties("persist.sys.wleswitch")（写，厂商业务）`；`airplane → Settings.Global("airplane_mode_on") + ACTION_AIRPLANE_MODE 广播（或反射 ConnectivityManager.setAirplaneMode）`；`flashlight/screenRecord 属 mod-tools，本期不实现`。

---

## C. UI 元素与文案全清单（供后续改 UI 话术用）

### C.1 布局装配树（原包真实结构）

```
layout_pull_down.xml (res/layout-xhdpi)             ← 下拉面板根
└─ FrameLayout @id/fl_root
   ├─ XtcStatusBarView @id/status_bar_view          ← 状态栏模块（W2）
   └─ QsOutCanScrollView @id/ocsv_root              ← 本模块
      └─ QSPanel @id/quick_settings_panel
         ├─ (divider View, R.layout.qs_divider)     ← 代码 b() 从未被调用 ⇒ 实际不显示
         └─ XTCTileLayout                           ← 直接 new，R.layout.xtc_tile_layout
            └─ LinearLayout(vertical)               ← xtc_tile_layout 根（addView 进来）
               ├─ ll_tile_container_one   (LinearLayout, horizontal)
               ├─ fl_tile_tip_one         (TileTipView, gone, 149dp)
               ├─ ll_tile_container_two   (LinearLayout, horizontal)
               ├─ fl_tile_tip_two         (TileTipView, gone, 149dp)
               ├─ fl_volume_bar           (FrameLayout 43dp)
               │   ├─ iv_volume           (ImageView 44dp)
               │   └─ sb_volume           (SettingSeekBar)
               ├─ <include layout_pull_up_volume_tip>   @id/fl_volume_tip
               │   ├─ tv_volume_tip_title / tv_volume_tip_desc / tv_see_click
               ├─ fl_brightness_bar       (FrameLayout 43dp)
               │   ├─ sb_brightness       (BrightnessSettingSeekBar)
               │   └─ iv_brightness       (ImageView)
               ├─ music_short_cut          (MusicShortCutView)
               │   ├─ ll_state_fold / tv_fold_song_name
               │   └─ ll_state_unfold / tv_unfold_song_name / iv_previous / iv_unfold_play_pause / iv_next
               ├─ gl_tile_container_three (TileGridLayout, 2 列)
               ├─ <include layout_pull_up_color_corrector_tip>  @id/ll_color_crrector_root
               │   ├─ TextView（色彩校正已开启，无 id）
               │   └─ TextView（去修改>，无 id）
               └─ ImageView（下拉箭头 ic_pull_arrow_open，无 id，24dp）
```
证据：`<R>\res\layout-xhdpi\layout_pull_down.xml:11-25`、`<R>\res\layout\xtc_tile_layout.xml:7-93`、`<J>\...\qs\QSPanel.java:142-147`、`<J>\...\qs\XTCTileLayout.java:127-166`。

### C.2 控件级清单（51 个 UI 元素，含 7 张 tile 卡片与 2 类运行时弹窗）

| # | id | 控件类 | 承载文案 key | 中文原文 | 交互 | 证据 |
|---|---|---|---|---|---|---|
| 1 | `fl_root` | FrameLayout | — | — | 下拉面板根容器 | `layout_pull_down.xml:3` |
| 2 | `status_bar_view` | `XtcStatusBarView` | （状态栏模块） | — | 顶部状态栏 | `:6-10` |
| 3 | `ocsv_root` | `QsOutCanScrollView` | — | — | 只有当"已滚到底+继续下滑"时才吞事件 | `:11-19`，`QsOutCanScrollView.java:24-31` |
| 4 | `quick_settings_panel` | `QSPanel` | — | — | QS 容器 | `:20-24` |
| 5 | （无 id） | `View` | — | — | `qs_divider` 分隔线（**运行期不出现**） | `QSPanel.java:131-136` 无调用点 |
| 6 | 根 LinearLayout | LinearLayout | — | — | `XTCTileLayout` 内容根 | `xtc_tile_layout.xml:2-6` |
| 7 | `ll_tile_container_one` | LinearLayout | — | — | 放 0、1 号磁贴（wifi/bt） | `:7-12` |
| 8 | `fl_tile_tip_one` | `TileTipView` | 运行时由 QSHost 传入（如 `connect_ble_device`） | — | 气泡提示 1；点击→`tile.longClick()` | `:13-19`、`XTCTileLayout.java:342-369` |
| 9 | `ll_tile_container_two` | LinearLayout | — | — | 放 2、3 号磁贴 | `:20-25` |
| 10 | `fl_tile_tip_two` | `TileTipView` | 同上 | — | 气泡提示 2 | `:26-31` |
| 11 | `fl_volume_bar` | FrameLayout | — | — | 音量条容器（缩放同步） | `:32-50`、`XTCTileLayout.java:454-470` |
| 12 | `iv_volume` | ImageView | — | — | 音量图标（随档位/静音/蓝牙SCO 切换 5 种 drawable） | `:39-43`、`VolumeFuncSetting.java:425-441` |
| 13 | `sb_volume` | `SettingSeekBar` | — | — | 拖拽调音量；**长按 1s** → 进音量设置/模式提示 | `:44-49`、`VolumeFuncSetting.java:231-259` |
| 14 | `fl_volume_tip` | LinearLayout | `pull_down_volume_tip_title`/`_content`/`go_see` | 长按进入音量调节 / 可调节来电或媒体音量 / 去看看&gt; | 点击"去看看>"/长按 → **仅隐藏提示**（不跳转） | `layout_pull_up_volume_tip.xml:5-45`、`VolumeFuncSetting.java:249-259` |
| 15 | `tv_volume_tip_title` | TextView | `pull_down_volume_tip_title` | 长按进入音量调节 | 静态 | `:16-21` |
| 16 | `tv_volume_tip_desc` | TextView | `pull_down_volume_tip_content` | 可调节来电或媒体音量 | 静态 | `:27-33` |
| 17 | `tv_see_click` | TextView | `go_see` | 去看看&gt; | 点击 → 隐藏整条提示 + 置 `volume_tip_clicked=true` | `:39-45`、`<J>\...\util\q.java:146-153` |
| 18 | `fl_brightness_bar` | FrameLayout | — | — | 亮度条容器 | `xtc_tile_layout.xml:52-72` |
| 19 | `sb_brightness` | `BrightnessSettingSeekBar` | — | — | 拖拽调亮度；点图标区 → 切自动/手动；长按 → 亮度设置 | `:59-64`、`XTCTileLayout.java:151-158` |
| 20 | `iv_brightness` | ImageView | — | — | 亮度图标（手动=`vector_drawable_light`，自动=`vector_drawable_auto_light`） | `:65-71`、`XTCTileLayout.java:210-214` |
| 21 | `music_short_cut` | `MusicShortCutView` | `pull_down_music_tips`/`format_fold_song_name` | 无歌曲播放 / 继续播放: %s | 折叠/展开两态；播放/上下曲 | `:73-79`、`layout-xhdpi\layout_music_shortcut.xml` |
| 22 | `ll_state_fold` | LinearLayout | `format_fold_song_name` | 继续播放: %s | 点击 → 展开（`MusicController.m434a()`） | `layout_music_shortcut.xml:6-26` |
| 23 | `tv_fold_song_name` | TextView | 歌曲名 | — | 同上 | `:15-25` |
| 24 | `ll_state_unfold` | FrameLayout | `pull_down_music_tips` | 无歌曲播放 | — | `:27-31` |
| 25 | `tv_unfold_song_name` | `MarqueeTextView` | 歌曲名（空则默认文案） | 无歌曲播放 | 点击 → 打开播放器 | `:32-40` |
| 26 | `iv_previous` | ImageView | — | — | 上一曲 | `:47-52` |
| 27 | `iv_unfold_play_pause` | ImageView | — | — | 播放/暂停（图标 `ic_action_pause_song`/`ic_action_play_song`） | `:53-58` |
| 28 | `iv_next` | ImageView | — | — | 下一曲 | `:59-64` |
| 29 | `gl_tile_container_three` | `TileGridLayout` | — | — | 放 4..6 号磁贴（2 列网格） | `xtc_tile_layout.xml:80-83` |
| 30 | `ll_color_crrector_root` | LinearLayout | `string_open_color_corrector`/`string_modify` | 色彩校正已开启 / 去修改&gt; | 点击 → 进色彩校正 App（非中文/非普通模式则 toast）；仅 `Settings.Secure("accessibility_display_daltonizer_enabled")==1` 时可见 | `layout_pull_up_color_corrector_tip.xml:5-31`、`XTCTileLayout.java:159-175`、`:444-452` |
| 31 | （无 id） | TextView | `string_open_color_corrector` | 色彩校正已开启 | 静态 | `:13-20` |
| 32 | （无 id） | TextView | `string_modify` | 去修改&gt; | 静态 | `:21-31` |
| 33 | （无 id） | ImageView | — | — | 下拉箭头 `ic_pull_arrow_open`，`paddingHorizontal=68dp` | `xtc_tile_layout.xml:85-93` |
| 34 | `ll_tip_root` | LinearLayout | — | — | 气泡提示根（背景 `bg_ble_prompt`，149×56dp） | `layout_tile_tip_view.xml:2-10` |
| 35 | `tv_tip_title` | TextView | 运行时传入 | — | 气泡标题（15sp 白） | `:11-18` |
| 36 | `tv_tip_content` | TextView | 运行时传入 | — | 气泡内容（13sp 灰） | `:26-32` |
| 37 | （无 id） | TextView | `com_see` | 去看看 | 静态 | `:41-47` |
| 38 | （无 id） | ImageView | — | — | `ic_arrow_right` | `:48-53` |
| 39 | — | `XTCQSTileBaseView` #1 | 见 B.1 | — | wifi 磁贴卡片 | `XTCQSTileBaseView.java:43-53` |
| 40 | — | `XTCQSTileBaseView` #2 | 见 B.2 | — | 蓝牙磁贴卡片 | 同上 |
| 41 | — | `XTCQSTileBaseView` #3 | `fun_switch_name_flashlight` | 手电筒 | 手电筒磁贴（移交） | `tiles\d.java` |
| 42 | — | `XTCQSTileBaseView` #4 | 见 B.3 | — | 铃音模式磁贴卡片 | 同 B.3 |
| 43 | — | `XTCQSTileBaseView` #5 | `dialog_longbattery` | 长续航 | 省电磁贴卡片 | `tiles\e.java:151` |
| 44 | — | `XTCQSTileBaseView` #6 | `screen_record` | 录屏 | 录屏磁贴（移交） | `tiles\g.java` |
| 45 | — | `XTCQSTileBaseView` #7 | `string_airplane_mode` | 飞行模式 | 飞行磁贴卡片 | `tiles\a.java` |
| 46 | `tv_title`（长续航弹窗） | TextView | `watch_long_battery` | 省电模式 | 20sp 绿色 `#ff4dd865` | `layout_watch_long_endurance_tip.xml:15-22` |
| 47 | `tv_content`（长续航弹窗） | TextView | `watch_long_battery_tip` | 通过关闭手表部分功能… | 15sp 白，`minHeight=160dp` | `:23-32` |
| 48 | `dl_button` 左键 | `DoubleFlatButton` | `cancel` | 取消 | 点取消→关闭弹窗+发状态广播 | `:35-40` |
| 49 | `dl_button` 右键 | `DoubleFlatButton` | `open` / `exit` | 开启 / 退出 | 低电量框=「退出」，长续航框=「开启」 | `<J>\...\LowPowerTipLayout.java:43-44` vs `WatchLongEnduranceTipLayout.java:43-44` |
| 50 | — | `DoubleFlatBtnWithTitleDialog` | `open_tips`+`open_hot_spot_or_wifi_switch_tips`/`open_wifi_when_hot_spot_open_tips` | 开启提醒 / 手表%s，确定要开启吗？ | 热点开着时点 wifi → 确认框（`sure`/`cancel`） | `tiles\h.java:190-215` |
| 51 | — | `DoubleFlatBtnWithTitleDialog` | `remove_confirm_tip_title`/`remove_confirm_tip_content` | 移除"%s"卡片 / 移除此卡片不会删除APP任何数据,确定移除? | 音乐卡片"减号"移除确认（`BaseShortCutView.java:110`） | `BaseShortCutView.java:108-136` |

> 图例：`#39-#45` 的 7 张卡片每张内部结构 = `XTCQSTileBaseView(FrameLayout)` + `View(背景)` + `XTCQSIconView(LottieAnimationView 90×90/92×92)`；尺寸来自 `dimens.xml`：cell 71×43dp、margin 6dp（`tile_grid_layout_default_cell_*`），磁贴行高/边距 `qs_tile_height=106dp` 等（仅 `TileLayout` 用，可选类）。

### C.3 文案全清单（**67 条**，原样中文，后续改话术请逐条替换）

| # | 资源 key | 中文原文 | 出现位置（类:行 / 布局行） |
|---|---|---|---|
| 1 | `pull_down_volume_tip_title` | 长按进入音量调节 | `layout_pull_up_volume_tip.xml:20`；`XTCTileLayout.java:474` |
| 2 | `pull_down_volume_tip_content` | 可调节来电或媒体音量 | `layout_pull_up_volume_tip.xml:31`；`XTCTileLayout.java:475` |
| 3 | `go_see` | 去看看&gt; | `layout_pull_up_volume_tip.xml:43`；`XTCTileLayout.java:476` |
| 4 | `string_open_color_corrector` | 色彩校正已开启 | `layout_pull_up_color_corrector_tip.xml:19` |
| 5 | `string_modify` | 去修改&gt; | `layout_pull_up_color_corrector_tip.xml:29` |
| 6 | `com_see` | 去看看 | `layout_tile_tip_view.xml:47` |
| 7 | `pull_down_music_tips` | 无歌曲播放 | `layout_music_shortcut.xml:39` |
| 8 | `format_fold_song_name` | 继续播放: %s | `MusicShortCutView.java:83` |
| 9 | `unknown_singer` | 未知歌手 | `qs\tiles\music\a.java:224`（`2131558630`） |
| 10 | `notification_no_next_media` | 暂无可切换的内容 | `qs\tiles\music\a.java:216` |
| 11 | `quick_settings_tiles` | default | `qs\d.java:293` |
| 12 | `quick_settings_tiles_default` | wifi,bt,flashlight,ringMode,powerSave,screenRecord,airplane | `qs\d.java:291` |
| 13 | `quick_settings_tiles_stock` | 同 12 | `qs\d.java:125`、`qs\a\a.java:81` |
| 14 | `wifi_opened` | WLAN已打开 | `tiles\h.java:112`（`2131558642`） |
| 15 | `wifi_closed` | WLAN已关闭 | `tiles\h.java:112`（`2131558641`） |
| 16 | `open_tips` | 开启提醒 | `tiles\h.java:192`（`2131558528`） |
| 17 | `open_hot_spot_or_wifi_switch_tips` | 手表%s，确定要开启吗？ | `tiles\h.java:192`（`2131558527`） |
| 18 | `open_wifi_when_hot_spot_open_tips` | 正在共享个人热点，打开WLAN会关闭个人热点 | `tiles\h.java:192`（`2131558529`） |
| 19 | `blue_opened` | 蓝牙已打开 | `tiles\b.java:78`（`2131558442`） |
| 20 | `blue_half_open` | 蓝牙连接已断开 | `tiles\b.java:78`（`2131558441`） |
| 21 | `connect_ble_device` | 连接蓝牙设备 | `tiles\b.java:127`（`2131558454`） |
| 22 | `like_ble_earphones` | 如蓝牙耳机等 | `tiles\b.java:127`（`2131558507`） |
| 23 | `remind_mode_vibrate` | 振动 | `tiles\f.java:122`（`2131558575`） |
| 24 | `remind_mode_ring` | 铃声 | `tiles\f.java:124`（`2131558574`） |
| 25 | `remind_mode_vibrate_ring` | 振动+铃声 | `tiles\f.java:126`（`2131558576`） |
| 26 | `volume_slient` | 静音 | `tiles\f.java:128`（`2131558634`）；`VolumeFuncSetting.java:516` |
| 27 | `remind_mode_forbid_by_guardian` | 已禁用，去家长APP设置 | `tiles\f.java:152`、`:225`（`2131558573`） |
| 28 | `dialog_longbattery` | 长续航 | `tiles\e.java:151`（`2131558476`） |
| 29 | `watch_long_battery` | 省电模式 | `WatchLongEnduranceTipLayout.java:40` |
| 30 | `watch_long_battery_tip` | 通过关闭手表部分功能\n来延长电池续航能力。\n保留接听电话、接收微\n聊通知。可在表盘界面\n下拉退出此模式 | `WatchLongEnduranceTipLayout.java:41` |
| 31 | `low_power` | 电量不足 | `LowPowerTipLayout.java:40` |
| 32 | `low_power_tip` | 电量不足10%啦！为了\n家长能够及时联系你，\n建议尽快充电，不退出\n长续航模式。 | `LowPowerTipLayout.java:41` |
| 33 | `exit` | 退出 | `LowPowerTipLayout.java:44` |
| 34 | `open` | 开启 | `WatchLongEnduranceTipLayout.java:44` |
| 35 | `cancel` | 取消 | `LowPowerTipLayout.java:43`、`WatchLongEnduranceTipLayout.java:43`、`tiles\h.java:192`、`BaseShortCutView.java:110` |
| 36 | `sure` | 确定 | `tiles\h.java:192`、`BaseShortCutView.java:110`（`2131558611`） |
| 37 | `airplane_opened` | 飞行模式已打开 | `tiles\a.java:97`（`2131558432`） |
| 38 | `airplane_closed` | 飞行模式已关闭 | `tiles\a.java:97`（`2131558431`） |
| 39 | `volume_value` | 音量:%s | `VolumeFuncSetting.java:520`（`2131558635`） |
| 40 | `music_volume_value` | 媒体音量:%s | `VolumeFuncSetting.java:522`（`2131558520`） |
| 41 | `voice_call_volume` | 通话音量:%s | `VolumeFuncSetting.java:522`（`2131558633`） |
| 42 | `call_still_ring` | 来电仍会响铃 | `VolumeFuncSetting.java:469`（`2131558445`） |
| 43 | `system_volume_low` | 系统音量低，来电不会响铃 | `VolumeFuncSetting.java:476`（`2131558613`） |
| 44 | `brightness_value` | 亮度:%s | `strings.xml:46`（**原代码未引用**，为备用/旧版文案） |
| 45 | `level_max` | 已调至最大亮度 | `tiles\c.java:186`（`2131558505`） |
| 46 | `level_min` | 已调至最小亮度 | `tiles\c.java:181`（`2131558506`） |
| 47 | `auto_brightness_open` | 自动亮度已开启 | `tiles\c.java:303`（`2131558440`） |
| 48 | `auto_brightness_close` | 自动亮度已关闭 | `tiles\c.java:303`（`2131558439`） |
| 49 | `watch_long_endurance_unsupport_edit_volume` | 省电模式中，暂无法更改设置 | `ModeManager.d()`：`baseservice\f.java:251` |
| 50 | `watch_long_endurance_unsupport_operation` | 省电模式中，暂时无法操作 | `f.java:207` |
| 51 | `watch_long_endurance_unsupport_music` | 省电模式中，退出后可正常使用 | `f.java:162` |
| 52 | `long_battery_unsupport_edit_volume` | 长续航模式中，暂无法更改设置 | `f.java:245` |
| 53 | `long_battery_unsupport_operation` | 长续航模式中，暂时无法操作 | `f.java:201` |
| 54 | `long_battery_unsupport_edit_music` | 长续航模式中，退出后可正常使用 | `f.java:156` |
| 55 | `tip_class_unsupport_edit_volume` | 上课禁用中，暂无法更改设置 | `f.java:241` |
| 56 | `tip_class_unsupport_operation` | 上课禁用中，暂时无法操作 | `f.java:197` |
| 57 | `tip_class_unsupport_edit_music` | 上课禁用中，退出后可正常使用 | `f.java:154` |
| 58 | `tip_sleep_unsupport_edit_volume` | 睡眠模式中，暂无法更改设置 | `f.java:249` |
| 59 | `tip_sleep_unsupport_operation` | 睡眠模式中，暂时无法操作 | `f.java:205` |
| 60 | `tip_sleep_unsupport_edit_music` | 睡眠模式中，退出后可正常使用 | `f.java:160` |
| 61 | `tip_official_disable_unsupport_edit_volume` | 智能禁用中，暂无法更改设置 | `f.java:247` |
| 62 | `tip_official_disable_unsupport_operation` | 智能禁用中，暂时无法操作 | `f.java:203` |
| 63 | `tip_official_disable_unsupport_edit_music` | 智能禁用中，退出后可正常使用 | `f.java:158` |
| 64 | `tip_high_temp_unsupport_edit_volume` | 高温禁用中，暂无法更改设置 | `f.java:243` |
| 65 | `tip_high_temp_unsupport_operation` | 高温禁用中，暂时无法操作 | `f.java:199` |
| 66 | `tip_class_mode_edit_ring_mode` | 上课禁用中，仅支持振动或静音调节 | `f.java:302` |
| 67 | `tip_sleep_mode_edit_ring_mode` | 睡眠模式中，仅支持振动或静音调节 | `f.java:304` |

> 附：`fun_switch_name_flashlight`「手电筒」、`screen_record`「录屏」、`string_airplane_mode`「飞行模式」为 tile 语义名（后两者用于无障碍 label），属移交模块，未计入 67。
> 另有 `remove_confirm_tip_title`/`remove_confirm_tip_content`/`tip_temporarily_inoperable` 在包内存在但本模块路径未触达（音乐卡片移除确认除外，见 #51 元素）。

### C.4 7 个开关动画 asset（`<R>\assets\pull_up_setting\`）

| 文件 | Lottie 版本 | 帧率 | 帧数(op) | 画布 | 用于 | 证据（播放语义） |
|---|---|---|---|---|---|---|
| `wifi.json` | 5.12.1 | 25 | 17 | 90×90 | WifiTile | `tiles\h.java:146-153`：开→`progress=0, doAnimate=true`；关→`progress=1.0` |
| `bluetooth.json` | 5.9.0 | 25 | 22 | 90×90 | BluetoothTile | `tiles\b.java:107-120`：**`maxAnimationProgress=0.9`**，关态停在 0.9 帧；连接中 `0→1.0` |
| `vibration.json` | 5.6.10 | 25 | 18 | 90×90 | RingModeTile(1) | `tiles\f.java:112,198-200`：`progress=0 + doAnimate` |
| `bell.json` | 5.6.10 | 25 | 27 | 90×90 | RingModeTile(2) | `tiles\f.java:114` |
| `bell_vibration.json` | 5.6.10 | 25 | 27 | 90×90 | RingModeTile(3) | `tiles\f.java:116` |
| `silence.json` | 5.12.1 | 25 | 11 | **92×92** | RingModeTile(0) | `tiles\f.java:118` |
| `power_save.json` | 5.9.0 | 25 | 16 | 90×90 | PowerSaveTile | `tiles\e.java:106-115`：开 `0+animate`，关 `0`（无动画） |

播放器实现（**必须复刻**）：`XTCQSIconView.setIcon(State)` — `<J>\...\tileimpl\XTCQSIconView.java:31-54`
```
if (state instanceof QSTile.LottieBooleanState) {
    LottieBooleanState s = (LottieBooleanState) state;
    if (!TextUtils.isEmpty(s.animationResource) && !Objects.equals(mLastRes, s.animationResource)) {
        mLastRes = s.animationResource;
        lottie.setAnimation(mLastRes);            // 从 assets 加载 "pull_up_setting/x.json"
    }
    float max  = s.maxAnimationProgress < 0 ? 0 : min(1, s.maxAnimationProgress);
    float prog = s.animationProgress   >= 0 ? min(s.animationProgress, max) : 0;
    lottie.setMaxProgress(max);
    lottie.setProgress(prog);
    if (s.doAnimate && mAnimationEnabled) lottie.playAnimation();
}
```
`LottieBooleanState` 字段定义：`<J>\...\plugins\qs\QSTile.java:291-325`（`animationResource/animationProgress/maxAnimationProgress/doAnimate`）。

### C.5 图标 / 背景 / 尺寸资源（代码里以数字 id 出现，已反查）

| 资源 | 数字 id | 用在哪 |
|---|---|---|
| `drawable/setting_func_enable_bg` | 2131231052 | 磁贴开启背景（`XTCQSTileBaseView.java:95`） |
| `drawable/setting_func_disable_bg` | 2131231051 | 磁贴关闭背景（`:97`；`tiles\e.java:112`） |
| `drawable/setting_func_enable_bg_green` | 2131231053 | 省电开启背景（`tiles\e.java:108`） |
| `drawable/ic_bluetooth_volume` | 2131230921 | 音量图标-蓝牙SCO（`VolumeFuncSetting.java:429`） |
| `drawable/ic_system_volume` / `_mute` | 2131230990 / 0991 | 音量图标-系统/静音（`:432`、`:439`） |
| `drawable/ic_media_volume` / `_mute` | 2131230966 / 0967 | 音量图标-媒体（`:435`） |
| `drawable/quick_setting_brightness_icon` | 2131231032 | 无自动亮度时的亮度图标（`XTCTileLayout.java:157`） |
| `drawable/vector_drawable_light` / `_auto_light` | — | 亮度图标手动/自动（`XTCTileLayout.java:213`） |
| `drawable/ic_action_pause_song` / `_play_song` | 2131230881 / 0882 | 音乐播放键（`MusicShortCutView.java:88`） |
| `drawable/bg_normal_shortcut` | 2131230850 | 音乐卡片背景（`MusicShortCutView.java:97`） |
| `drawable/add` / `sub` | 2131230801 / 2131231078 | 音乐卡片 +/- 角标（`BaseShortCutView.java:216`、`:229`） |
| `drawable/bg_volume_tip` | — | 音量提示条背景（`layout_pull_up_volume_tip.xml:6`） |
| `drawable/bg_ble_prompt` | — | 磁贴气泡背景（`layout_tile_tip_view.xml:6`） |
| `drawable/ic_pull_arrow_open` | — | 面板底部下拉箭头（`xtc_tile_layout.xml:91`） |
| `dimen/tile_grid_layout_default_cell_width/height/margin` | — | 71dp / 43dp / 6dp（`XTCTileLayout.java:265-269`、`TileGridLayout.java:29-31`） |
| `integer/qs_panel_default_columns` | — | 2（分栏阈值，`XTCTileLayout.java:132`） |
| `integer/qs_panel_collapse_duration_ms`=50 / `qs_panel_expand_duration_ms`=50 | — | 面板收起/展开时长（`XTCPanelView.java:302`） |
| `dimen/qs_panel_max_height`=180dp | — | 面板最大高度（`XTCPanelView.java:220`） |

### C.6 关键交互参数（复刻必须一致）

| 行为 | 参数 | 证据 |
|---|---|---|
| 磁贴按压动画 | `PressAnimHelper`，`smallScale=0.8f`；down→press，up/cancel→release | `XTCQSTileBaseView.java:51-52`、`:103-111` |
| 磁贴点击防抖 | `com.xtc.statusbar.b.c` 延迟 **400ms** | `XTCQSTileBaseView.java:62` |
| 气泡提示出现动画 | `scaleX/Y 0→1`，**200ms**，`PathInterpolator(0.42,0,0.58,1)` | `XTCTileLayout.java:377-387` |
| 气泡提示收起 | `visibility=8`（无动画） | `:426-431` |
| 音量/亮度条长按判定 | 触摸 **1000ms** 未移动 → 长按 | `SettingSeekBar.java:242` |
| 已点过音量提示 | `MMKV` 键 `volume_tip_clicked`；点一次后永不再显示 | `<J>\...\util\q.java:146-153`、`VolumeFuncSetting.java:459-462` |
| 亮度动画 | `ValueAnimator` 时长 = `DisplayManagerEx.getAutoBrightnessAnimateDuration(0..100)` | `tiles\c.java:293` |
| 面板缩放同步 | 状态栏 `SpringAnimation` 回调把 scale 传给 `QSPanel.a(float)` → 每张 tile + 音量/亮度条 + 提示条 | `XTCTileLayout.java:454-470`、`XTCPanelView.java:318-326` |
| 列布局 | 每行 2 个；第 3 区 `TileGridLayout` 2 列 | `xtc_tile_layout.xml`、`TileGridLayout.java:32` |

---

## D. 音量 / 亮度 / 省电面板：结构、状态机、文案、公开 API

### D.1 音量条（`sb_volume` + `iv_volume` + `fl_volume_tip`）

**结构**：`FrameLayout(fl_volume_bar, 43dp)` 内 `ImageView(44dp, iv_volume)` + `SettingSeekBar(match, sb_volume)`；紧下面是可隐藏的 `fl_volume_tip` 提示条。证据 `<R>\res\layout\xtc_tile_layout.xml:32-51`。

**两种音量通道**（`VolumeFuncSetting`；`m399b()` = `AudioManager.getMode()`∈{2,3} 即通话中）：

| 维度 | 非通话（`VOLUME_TYPE` 决定文案/图标） | 通话中（`AudioManager.getMode()==2/3`） |
|---|---|---|
| 读当前值 | `AudioHelper.m1242c()` = `getStreamVolume(3)` 媒体 | `AudioHelper.j()` = `getStreamVolume(0)` 通话；有蓝牙 SCO 时 `AudioHelper.m()` = `getBluetoothScoVolume()` 再按比例换算 |
| 写 | `AudioHelper.b(int)` → `setStreamVolume(3, i, 8)` 媒体 | `AudioHelper.c(int)`：有 SCO → `f()`（`AudioManagerEx.setBluetoothScoVolume`，按 `SCOmax/callMax` 比例换算）；否则 `e(int)` → `setStreamVolume(0, i, 8)` 通话 |
| 级别上限 | `AudioHelper.e()` = `getStreamMaxVolume(3)` | `AudioHelper.k()` = `getStreamMaxVolume(0)`；SCO max = `AudioHelper.l()` |
| SeekBar 配置 | `setLevelMode(maxMusic*100)`、`setLevelMin(0)` | `setLevelMode(maxVoice*100)`、`setLevelMin(100)`（至少 1 级） |
| 文案（`VOLUME_TYPE.ALL`） | `volume_value`「音量:%s」；值为 0 → `volume_slient`「静音」 | 同左（通话时 `VOLUME_TYPE` 恒按实际分支） |
| 文案（`VOLUME_TYPE.MUSIC` = 支持"音量分离"机型） | `music_volume_value`「媒体音量:%s」 | `voice_call_volume`「通话音量:%s」 |
| 图标（`VOLUME_TYPE.ALL`） | `ic_system_volume` / `ic_system_volume_mute` | 同左 |
| 图标（`VOLUME_TYPE.MUSIC`） | 有 SCO → `ic_bluetooth_volume`；通话中 → system 系；否则 `ic_media_volume` / `ic_media_volume_mute` | `ic_bluetooth_volume`（SCO）或 system 系 |

`VOLUME_TYPE` 判定：`AudioUtils.a(ctx)` = `Build≥30` 或 `hasSystemFeature("android.software.mcu.volume_split")` 且云控开关 → 支持分离时 `VOLUME_TYPE.MUSIC`，否则 `ALL`（`VolumeFuncSetting.java:118-128`、`<J>\com\xtc\baseservice\util\a.java:12-32`）。
证据：`VolumeFuncSetting.java:192-229`、`:302-318`、`:351-361`、`:425-441`、`:514-523`；`AudioHelper`（`<J>\com\xtc\baseservice\a.java:181-352`）。

**SeekBar 分档算法**（`SettingSeekBar`，**复刻重点**）：
```
setLevelMode(levels) : mLevels=levels; mSmooth=true; setMax(levels*1000)
setLevelMin(level)   : mMin=level*1000
setCurrentLevel(lv)  : setProgress(lv*1000)
getMax()             : super.getMax()/1000          // 对外暴露"档数"
onProgressChanged    : index = progress/1000 ; 若 (index != mLastIndex) && mCallbackFlag → callback.a(index)
onTouchEvent MOVE    : setProgress(max(mDownProgress + (dx/width)*1000*mLevels, mMin))
onTouchEvent UP      : callback.b(index)  where index = progress/1000, progress%1000>500 时 +1
getImpliedLevel(sb,i): max=getMax(); lv=max/100; last=lv-1;
                       i==0 → 0；i >= max-((max-last*100)/2) → lv；
                       否则 (int)((i/max)*last)+1     // ⚠ 原码为整型除法，中段恒为 1；复刻建议改浮点
```
证据：`SettingSeekBar.java:165-197`、`:211-274`、`:329-331`；档位反算 `VolumeFuncSetting.java:497-510`。
**竖向手势让位**：拖动时若 `|dy|*2 > |dx|` → `parent.requestDisallowInterceptTouchEvent(false)` 并 `return false`（让外层 `QsOutCanScrollView` 接管滚动）。`SettingSeekBar.java:248-256`。
**长按**：`postDelayed(1000ms)`；支持编辑 → `callback.onLongClick`；不支持 → toast 模式文案。`SettingSeekBar.java:225-243`、`:323-326`。

**状态机**：
```
空闲 --点击磁贴以外的区域--> 无变化
空闲 --onStartTrackingTouch--> 拖动中(记 downProgress/档位, 禁止父滚动)
拖动中 --MOVE--> 更新 progress + 图标 + 预览文案（不写系统）
拖动中 --UP--> onStopTrackingTouch:
         level = 档位（progress/1000，余数>500 进位）
         level != 起始档 → 立即显示文案（volume_value / music_volume_value / voice_call_volume / volume_slient 或通话系）+ 切图标
         延迟 300ms → 真正写系统音量（b()/c() 通道，见上表）+ 边界提示（system_volume_low / call_still_ring）
         上报埋点（复刻删除）
空闲 --长按1s--> 若不支持编辑 → toast 模式文案；否则 震动 + 启动音量设置页 + 隐藏 fl_volume_tip
空闲 --收到 VOLUME_CHANGED_ACTION(stream=3/0/SCO)--> 静默刷新进度（不弹提示）
空闲 --蓝牙连接状态变化--> 3s 后刷新（决定是否走 SCO 通道）
```
证据：`VolumeFuncSetting.java:165-179`（enter/exit）、`:351-361`（refreshUI 去重）、`:466-479`（边界提示）、`:481-494`（长按进设置）、`:583-679`（广播）、`:695-702`（埋点）。

**公开 API 复刻**：
```
AudioManager am = ctx.getSystemService(AudioManager.class);
int maxMusic = am.getStreamMaxVolume(STREAM_MUSIC);      // 3
int maxVoice = am.getStreamMaxVolume(STREAM_VOICE_CALL);// 0
int cur      = am.getStreamVolume(stream);
am.setStreamVolume(stream, value, 0);
am.getRingerMode();  am.setRingerMode(...)               // 铃声态
registerReceiver(VOLUME_CHANGED_ACTION)                  // 外部音量变化
```
SCO 音量走 `AudioManagerEx`（厂商类）→ 复刻可用 `am.setStreamVolume(STREAM_BLUETOOTH_SCO=6, …)` + `setMode(MODE_IN_CALL)` 组合，或直接省略 SCO 分支（手表无蓝牙耳机时）。

### D.2 亮度条（`sb_brightness` + `iv_brightness`）

| 项 | 逻辑 | 证据 |
|---|---|---|
| 分档 | `setLevelMode(100)`，即 0..100 共 100 档 + 1 | `tiles\c.java:94` |
| 读 | `DisplayManagerEx.getLevelByBrightness(0,100)`：`round(Settings.System.getInt("screen_brightness")/255*100)` | `<J>\...\hardware\DisplayManagerEx.java:120-126` |
| 写 | `setBrightness(level,0,100)` → `Settings.System.putInt("screen_brightness", round(level/100*255))`；节流 **50ms**（`PublishSubject.debounce(50ms)`） | `DisplayManagerEx.java:112-118`、`tiles\c.java:126-136` |
| 自动亮度开关 | `iv_brightness` 点击（仅 `Settings.System("config_screen_auto_brightness_available")==1` 时绑定）：切 `screen_brightness_mode` 0/1 + 换图标 + toast `auto_brightness_open/close` | `XTCTileLayout.java:151-158`、`:187-208`、`util\d.java:182-191` |
| 外部变化 | ContentObserver `screen_brightness` → 动画过渡（`ValueAnimator`，时长由 `getAutoBrightnessAnimateDuration` 算） | `tiles\c.java:248-260`、`:273-295` |
| 边界提示 | 拖到 0 / 100 → toast `level_min` / `level_max` | `tiles\c.java:98-113`、`:178-189` |
| 长按 | 震动 + `Intent("android.settings.BRIGHTNESS_SETTINGS")` | `tiles\c.java:242-245`、`:191-200` |

**公开 API**：`Settings.System.getInt/putInt("screen_brightness")`（0..255）+ `("screen_brightness_mode")`（0 手动 /1 自动）→ 完全公开，不依赖 `DisplayManagerEx`。
RISK：R-85（判"保留无害"）；注意原包在 API30+ 用 `screen_brightness_float`，**本机 API27 走 int 分支**。

### D.3 音量提示条（`fl_volume_tip`，`layout_pull_up_volume_tip.xml`）

状态机（`VolumeFuncSetting.e()` + `d(boolean)`）：
```
onAttach → e(): 可见性 = AudioUtils.a(ctx) && !ShareUtil.f(ctx)     // 支持分离音量 且 用户未点过
           可见 → 显示（"长按进入音量调节 / 可调节来电或媒体音量 / 去看看>"）
点击 tv_see_click 或长按 fl_volume_tip → d(false):
           if (!ModeManager.supportEditMusicVolume) → toast ModeManager.m1269b() 文案
           否则 → q.m1350a(ctx,true)（落盘"已点过"）→ fl_volume_tip.setVisibility(8)
配置变化(d_()) → 重设 3 个文案 + 重建 2 个气泡提示（TileTipView.a()）
```
证据：`VolumeFuncSetting.java:261-287`、`:249-259`、`:449-463`；`XTCTileLayout.java:472-479`。

> ⚠ 产品坑（复刻需注意）：文案写"去看看>"但**点击并不跳转设置页**，只是隐藏提示并记"已点过"。若产品要"真跳转"，改为 `h()`（启动音量设置）。

### D.4 省电 / 长续航面板

**两个弹窗共用同一 layout**（`layout_watch_long_endurance_tip.xml`），仅文案与按钮文字不同：

| | 长续航确认（开启） | 低电量劝阻（关闭） |
|---|---|---|
| 类 | `WatchLongEnduranceTipLayout`（`<J>\com\xtc\statusbar\WatchLongEnduranceTipLayout.java:12-62`） | `LowPowerTipLayout`（`...\LowPowerTipLayout.java:12-62`） |
| 标题 | `watch_long_battery`「省电模式」 | `low_power`「电量不足」 |
| 正文 | `watch_long_battery_tip` | `low_power_tip` |
| 左键 | `cancel`「取消」 | `cancel`「取消」 |
| 右键 | `open`「开启」→ `powerSaveController.a(true)` | `exit`「退出」→ `powerSaveController.a(false)` |
| 触发 | 点击省电磁贴（电量>10） | 点击省电磁贴（电量≤10，且当前已开） |
| 装载 | `PopWindowManager.a(view, "watchLongEnduranceTipLayout")` | `PopWindowManager.a(view)` |

**状态流转**：
```
off --click(电量>10 且 funSwitch 开)--> 弹长续航确认
    --确认(左键取消)--> 关闭弹窗 + 发 updateWatchLongEnduranceStatus
    --确认(右键开启)--> powerSaveController.a(true) + 关闭 + 发广播
on  --click(电量>10)--> powerSaveController.a(false)（直接关，无弹窗）
on  --click(电量≤10)--> 弹低电量劝阻
    --左键取消--> 关闭 + 发广播
    --右键退出--> powerSaveController.a(false) + 关闭 + 发广播
```
证据：`tiles\e.java:62-95`、`:154-255`。

**公开 API**：见 §B.4（`PowerManager.isPowerSaveMode()` 读 + `persist.sys.wleswitch` 写 + 广播）。

### D.5 音乐快捷卡片（`music_short_cut`，可选但结构完整）

| 项 | 逻辑 | 证据 |
|---|---|---|
| 数据源 | 通知监听（`NotificationListenerService`）中带 `IS_MUSIC` extra 的通知；`StatusBarNotification.getNotification().extras` | `<J>\...\music\a.java:90-128` |
| extras 键 | `IS_MUSIC`/`PLAY_STATE`/`SONG_NAME`/`SINGER_NAME`/`ALBUM_COVER_URL`/`PLAY_PAUSE_INTENT`/`PREVIOUS_INTENT`/`NEXT_INTENT`/`SONG_NAME_INTENT` | `music\b.java:5-21` |
| 播放态 | `getInt("PLAY_STATE",0)`；常量 16/32（`MP_STATE_PLAYING=32`） | `music\a.java:90-96` |
| 控制 | 取对应 key 的 `PendingIntent.send()`；`SONG_NAME_INTENT` 用 `ActivityStarter.postStartActivityDismissingKeyguard(pendingIntent)` | `music\a.java:195-220` |
| 无下一首 | `PREVIOUS/NEXT_INTENT` 非 PendingIntent → toast `notification_no_next_media` | `:215-219` |
| 两态 | 折叠：`ll_state_fold`（"继续播放: 歌名"）；展开：`ll_state_unfold`（歌名+3 键） | `MusicShortCutView.java:80-95`、`:158-174` |
| 歌手名 | `SINGER_NAME`，缺省 `unknown_singer` | `music\a.java:222-226` |

### D.6 色彩校正提示条（`ll_color_crrector_root`）

可见性 = `Settings.Secure.getInt("accessibility_display_daltonizer_enabled") == 1`（`XTCTileLayout.java:444-452`，常量 `<J>\com\xtc\statusbar\b.java:9`）。
点击 → 普通模式时 `startActivity(Intent("com.xtc.launcher.color.rectify"))`；否则 toast 当前模式文案（`XTCTileLayout.java:168-175`）。**注意该 click 里的埋点调用（`:174`）删除。**

---

## E. 复刻实现建议

### E.1 新类清单（包 / 类 / 职责 / 预计 LOC）

> 目标：用**公开 API + 自研**替换私有框架（`com.xtc.ultraframework.*`、`com.xtc.baseservice.*`、`com.xtc.ui.widget.*`）。
> 估算基于 jadx 行数 × 裁剪系数（去埋点/去云控/去插件/合并匿名类）。

| 包 | 类 | 职责 | 依据（原始行数） | 预计 LOC |
|---|---|---|---|---|
| `com.android.systemui.qs` | `QSPanel` | 面板容器：建 `XTCTileLayout`、绑 Host、tile 列表增删、expanded/listening 状态、`showDetail`（可空实现） | `QSPanel.java` 507 | 190 |
| | `QSHost`（interface） | `createTile/createTileView/getTiles/collapsePanels/expandPanels/showTip/hideTip/isExpanded` 8 方法 | `c.java` 45 | 40 |
| | `QSTileHost` | spec 列表解析（`quick_settings_tiles_default`）、`Settings.Secure.sysui_qs_tiles` 读、tile 创建缓存、`showTip/hideTip` 转发 | `d.java` 326 | 220 |
| | `XTCTileLayout` | 2+2+3 分栏装配；音量/亮度/音乐/色彩校正/气泡提示；`setScale` 同步 | `XTCTileLayout.java` 488 | 340 |
| | `TileGridLayout` | 2 列固定单元格网格 | `TileGridLayout.java` 91 | 80 |
| | `QsOutCanScrollView` | 外层滚动：仅当"已到底 + 继续同向滚动"时吞事件 | `QsOutCanScrollView.java` 32 + `OutCanScrollView` 行为 | 70 |
| | `BaseTileTipView` / `TileTipView` | 气泡提示（inflate `layout_tile_tip_view`、重建） | 53 + 89 | 130 |
| `...qs.tileimpl` | `QSTileImpl<T>` | Handler 消息机（1/2/4/5/10/11/12/13/14）、回调分发、`click/longClick/refreshState`、埋点方法留空 | `d.java` 633 | 280 |
| | `QSFactoryImpl` | spec→Tile 映射 | `c.java` 63 | 55 |
| | `XTCQSTileBaseView` | 卡片视图：背景、按压、点击/长按 | `XTCQSTileBaseView.java` 127 | 120 |
| | `XTCQSIconView` | Lottie 播放（见 §C.4） | `XTCQSIconView.java` 65 | 70 |
| | `FuncSetting` / `IQSTileBehavior` | 长按型设置项基类 / 空埋点接口 | 65 + 10 | 50 |
| | `BaseShortCutView` | 音乐卡片三态基类（可只做折叠/展开两态） | 308 | 160 |
| `...qs.tiles` | `WifiTile` | WLAN | `h.java` 223 | 130 |
| | `BluetoothTile` | 蓝牙 | `b.java` 176 | 110 |
| | `RingModeTile` | 4 态铃音 | `f.java` 279 | 190 |
| | `PowerSaveTile` | 省电 + 2 弹窗 | `e.java` 262 | 160 |
| | `AirplaneTile` | 飞行模式 | `a.java` 151 | 90 |
| | `SettingSeekBar` | 分档滑条（核心） | 332 | 230 |
| | `BrightnessSettingSeekBar` | 亮度条（点图标区判定） | 61 | 60 |
| | `VolumeFuncSetting` | 音量逻辑 | 703 | 320 |
| | `BrightnessFuncSetting` | 亮度逻辑 | 305 | 200 |
| `...qs.tiles.music` | `MusicController` / `MusicShortCutView` / `MusicNotificationConstants` | 音乐卡片（可选） | 349+181+21 | 200+130+15 |
| `...statusbar.policy` | `WifiControllerImpl` / `BluetoothControllerImpl` / `RingModeControllerImpl` / `PowerSaveModeControllerImpl` / `AirplaneModeControllerImpl` | 5 个控制器 | 106+271+139+44+116 | 90+150+120+45+70 |
| `com.xtc.baseservice`（本地化） | `ModeManager` | 手表模式枚举 + 门禁文案（**数据源改本地**） | 367 | 130 |
| | `AudioHelper` | 音量读写（去 SoundPool/提示音） | 489 | 120 |
| | `RingModeHelper` | 4 态映射（可并入 RingModeControllerImpl） | 120 | 90 |
| `com.xtc.ultraframework.hardware`（本地化） | `DisplayManagerEx` | 亮度映射 + 动画时长 | 145 | 70 |
| `com.xtc.statusbar` | `LowPowerTipLayout` / `WatchLongEnduranceTipLayout` | 2 个确认弹窗 | 62+62 | 60+60 |

合计约 **3,300~3,600 行**（不含移交的 flashlight/screenrecord、不含 W3 控件库）。

### E.2 依赖顺序（先容器还是先 tile？**先接口层，再容器，再 tile，最后滑条**）

理由：`QSPanel` 依赖 `QSHost`/`QSTileView` 接口；`XTCTileLayout` 依赖 `QSPanel.d` 内部类。若先写 tile 会因接口未定而反复改。

1. **接口层**：`plugins.qs.QSTile/State/BooleanState/LottieBooleanState/QSIconView/QSTileView/QSFactory/DetailAdapter`（本包已有同名接口定义，可直接照抄 `<J>\...\plugins\qs\*.java`）+ `QSHost`。→ 可编译，无 UI。
2. **容器壳**：`QsOutCanScrollView` + `QSPanel`（先只做 `setHost/setTiles/setExpanded/setListening`）。→ `layout_pull_down.xml` 能 inflate、下拉面板不崩（当前 `<S>` 就是这一步的雏形）。
3. **tile 基类**：`QSTileImpl` + `QSFactoryImpl` + `XTCQSTileBaseView` + `XTCQSIconView`（Lottie）。→ 用假 tile（固定 spec）验证卡片与动画。
4. **布局装配**：`XTCTileLayout` + `TileGridLayout` + `TileTipView`。→ 7 张卡片上屏、2+2+3 正确。
5. **4 个开关 + 控制器**：`WifiTile`/`BluetoothTile`/`RingModeTile`/`PowerSaveTile` + 5 个 controller。→ 开关可点、状态同步。
6. **滑条**：`SettingSeekBar` → `VolumeFuncSetting` → `BrightnessFuncSetting` + `BrightnessSettingSeekBar`。→ 音量/亮度可调。
7. **提示与弹窗**：`fl_volume_tip` 交互 + `LowPowerTipLayout`/`WatchLongEnduranceTipLayout`（依赖 `PopWindowManager`）。
8. **可选**：音乐卡片 + `MusicController`（依赖通知监听服务）。
9. **清理**：删 `external/*`、`AutoAddTracker`、`TileQueryHelper`、`SecureSetting`、`TouchAnimator`、`PathInterpolatorBuilder`、`TileLayout`、`AutoSizingList`、`MusicSessionRecorder`。

### E.3 风险

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | **Lottie 依赖**：7 个开关图标全靠 `com.airbnb.lottie.LottieAnimationView`（`XTCQSIconView.java:15,26`） | 不引入 lottie 则 7 个开关全部无图标 | 引入 `com.airbnb.lottie:lottie:3.4.0`（支持 bodymovin v5.x，含 5.12）；或退化为"静态矢量图 + progress 状态"（视觉损失大） |
| R2 | `com.xtc.baseservice.ModeManager` 数据源是 `content://com.xtc.watch.status.data/…`（跨进程 provider）+ `persist.sys.mode` | provider 不在时模式恒为 `normal`，门禁文案永不出现 | 复刻版**本地化**：默认 `normal` + 预留 setter；不要 crash |
| R3 | `SystemProperties("persist.sys.wleswitch")` / `("persist.sys.ringmode.*")` 为厂商属性 | 属性不存在 → 省电开关恒 false；铃音静音态语义缺失 | 写侧用 `SystemProperties.set` 保留（platform 签名），读侧加默认值兜底 |
| R4 | 省电开关**写操作无公开 API**（`PowerSaveModeControllerImpl.a(z)` → `StatusBar.mo495a(z)` → 私有服务） | 无法纯粹用 framework 实现"开启长续航" | 保留 `persist.sys.wleswitch` + 广播约定；与设备侧服务对齐（RISK R-72 判"保留无害"） |
| R5 | 音量条与亮度条依赖 W3 控件（`PressAnimHelper`/`MarqueeTextView`/`DoubleFlatButton`/`DoubleFlatBtnWithTitleDialog`） | 缺控件则弹窗与按压动画不可用 | 先做最小实现（普通 View + 系统 AlertDialog 替代），后替换 |
| R6 | 埋点/上报路径密布（`IQSTileBehavior.mo401a/g`、`StatusBarBehavior`、`DataPoint`、`BehaviorUtil`） | 误保留即恢复上报 | 按 RISK-MAP R-78-02 / R-78-24 / R-72 / R-77 / R-85 逐点删除；`IQSTileBehavior` 空实现 |
| R7 | `SettingSeekBar.getImpliedLevel` 反编译呈整型除法（中段恒 1） | 图标/文案档位显示异常 | 按浮点语义实现，实测 0..max 连续 |
| R8 | `QSPanel` 的 `MetricsLogger`（`com.android.internal.logging`）与 `DetailAdapter` 全链路 | 直接照抄会 NPE（`Dependency.get(MetricsLogger.class)` 无绑定） | 复刻版去掉 MetricsLogger 与 detail 路径（原版全部 tile 的 `getDetailAdapter()` 均返回 null，属死路径） |
| R9 | `qs_divider` 在 `QSPanel.b()` 中 inflate 但**从不调用** | 若"补齐调用"会多出一条 1dp 分隔线，与真机不符 | 不实现 `b()`（保持与真机一致） |

---

## F. 风险与死代码（可完全不实现的部分）

### F.1 可完全不实现（dead code）清单

| # | 类/资源 | 为什么可删（证据） | RISK-MAP 关联 |
|---|---|---|---|
| 1 | `qs/external/*`（CustomTile、PackageManagerAdapter、QSTileServiceWrapper、TileServiceManager、TileServices、TileLifecycleManager，共 6 类，约 1,300 行） | 第三方 `TileService` 磁贴框架：spec 必须以 `custom(` 开头（`CustomTile.java:34`），而磁贴列表是固定的 7 个（`strings.xml:163`），无 `custom(` 项；`QSTileHost` 唯一的第三方位是 `TileServices`（`d.java:68`），已随 `CustomTile` 一起死。**无云控依赖，纯插件框架** | 与云控无关；但 `TileServices` 是 Binder 暴露面，删掉可减少攻击面 |
| 2 | `AutoAddTracker` | `Prefs.Key.QS_*_ADDED` + `Settings.Secure.qs_auto_tiles`；全仓无引用 | — |
| 3 | `TileQueryHelper` | 磁贴选择器用；全仓无引用 | — |
| 4 | `SecureSetting` | 全仓无引用 | — |
| 5 | `TouchAnimator` | 全仓无引用（旧 AOSP 遗留） | — |
| 6 | `PathInterpolatorBuilder` | 全仓无引用（已被 `android.view.animation.PathInterpolator` 取代） | — |
| 7 | `TileLayout` | `QSPanel.c()` 硬编码 `new XTCTileLayout(...)`（`QSPanel.java:144`），此 AOSP 网格已无调用点 | — |
| 8 | `AutoSizingList` | 无引用 | — |
| 9 | `MusicSessionRecorder` | 仅在 `music/a` 里有 `implements` 关系，主流程不依赖 | — |
| 10 | `QSPanel.b()`（divider）/`getDivider()`/`setGridContentVisibility()` | `b()` 全文件无调用点（grep 验证）；另两个方法也无调用点 | — |
| 11 | 埋点/上报调用（约 30 处，散落在 tile 与 panel 内） | `IQSTileBehavior.mo401a(int)/g()`（`tileimpl/b.java:5-10`）、`StatusBarBehavior`（`<J>\com\xtc\statusbar\a\c.java:9-47`）、`AirplaneModeControllerImpl` 的 `DataPoint` 上报（`policy\b.java:103-115`）、`WifiTile`/`PowerSaveTile` 的 `funSwitch` 云控读取 | **R-78-02**（AirplaneTile clickEvent）、**R-78-24**（StatusBarBehavior）、**R-78-22/23**、**R-72**（省电属性）、**R-77**（长续航上报）、**R-67/R-68**（FunSwitch/模块开关）、**R-80/R-81**（日志落盘） |
| 12 | `QsOutCanScrollView` 的父类 `com.android.common.library.OutCanScrollView` | 厂商滚动库；复刻用 `ScrollView` + 20 行判定替代 | — |

### F.2 必须保留但要去毒（业务必需 + 有风险调用）的项

| 项 | 保留原因 | 要去掉的毒 | RISK-MAP |
|---|---|---|---|
| `PowerSaveModeControllerImpl` | 省电是核心业务 | （仅 `SystemProperties` 读写，本身无害） | R-72 判"保留无害" |
| `ModeManager` | 门禁文案/限制逻辑是核心业务 | `BehaviorUtil.customEvent` 2 处 | R-77 |
| `WifiControllerImpl` | WLAN 开关 | 热点/256qam 私有分支可留 | — |
| `WifiTile` / `PowerSaveTile` | 开关本体 | `com.xtc.system.account.h`（云控开关）→ 本地常量 | R-67/R-68 |
| `DisplayManagerEx` | 亮度映射 | `DbModuleSwitch` 引用可去 | R-85 判"保留无害" |
| `XtcStatusBarView`（提示通道） | 磁贴 toast 依赖 | 无 | — |
| `BluetoothTile` 的首次提示条件 | 气泡提示（一次性引导） | `account.h.m1843a(...)` 云控开关 → **视为 true 删掉该判断**；保留 `sp_enter_blue_setting_from_qs` 一次性标记逻辑 | R-68 |

### F.3 与其它模块的接口约定（避免重复实现）

| 交互 | 提供方 | 本模块用法 |
|---|---|---|
| `QSPanel.setHost(QSHost)` | `StatusBar.n()`（`<J>\...\phone\StatusBar.java:578-581`） | 面板挂载点 |
| `QSPanel.a(String spec, String title, String content)` / `b(String spec)` | `StatusBar.a(String,String,String)`（`:683-695`） | 磁贴气泡提示显示/隐藏 |
| `QSPanel.setListening(boolean)` | `XTCPanelView.s()`（`XTCPanelView.java:459-472`） | 面板展开/收起时启停监听 |
| `QSPanel.a(float)` | `XTCPanelView` SpringAnimation（`:318-326`） | 面板缩放同步 |
| `QSFactoryImpl` 的 flashlight/screenRecord 分支 | `mod-tools` | 本模块只保留分支占位 |
| `PopWindowManager.a(View[,String])` | popup 模块 | 省电/长续航弹窗装载 |
| `Lottie` 依赖 | 工程级 | 7 个开关动画 |

---

## 附：本 SPEC 关键数字（供 Lead 汇总）

| 指标 | 数值 |
|---|---|
| `qs` 包类总数 | **43**（+2 lambda 文件） |
| 复刻必需 / 可选 / dead / 移交 | **27 / 3 / 11 / 2** |
| UI 元素（含控件、卡片、弹窗） | **51**（7 张磁贴卡片、2 类运行时弹窗、19 个无 id 节点） |
| UI 文案（本模块可达） | **67 条** string 资源 |
| 开关动画 asset | **7** 个（wifi / bluetooth / vibration / bell / bell_vibration / silence / power_save） |
| 磁贴 spec | **7** 个（wifi / bt / flashlight / ringMode / powerSave / screenRecord / airplane） |
| 新增/重写类估算 | ~**30** 个类，~**3,300-3,600** LOC |
