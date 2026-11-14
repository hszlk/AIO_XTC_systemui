# SPEC — 录屏 / 手电筒+SOS / 助理服务 / 蓝牙接口（复刻实现规格）

> 目标包：`com.android.systemui` 1.8.00（XTC 儿童手表 SystemUI，Android 8.1 / API 27 / `minSdk=27`、`targetSdk=30`、platform 签名、`sharedUserId=android.uid.system`、`persistent=true`）。
> 唯一可信代码源（只读）：`work\jadx18b\sources`（1.8.00 unquicken 精确版）。交叉校验：`work\jadx17\sources`。
> 资源：`nx\resources`。Manifest 证据：`analysis\cloud-control\out\xmltree-vendor.txt`（aapt2 xmltree，行号可靠）。
> 风险清单：`analysis\risk\RISK-MAP.md`。
> **重要前提（决定大量结论）**：本机是 **Android 8.1（API 27）**。原包大量逻辑走 `Build.VERSION.SDK_INT >= 30`（Android 11）分支，这些分支**在目标设备上永远不可达**（静态可达、运行时死代码）。本 SPEC 对每个类都标注了"本机可达性"。

---

## 0. 结论速览（先看这里）

| 指标 | 数值 | 备注 |
|---|---|---|
| 本任务范围内类 | **58** | 录屏 15 + 手电/SOS 9 + 助理 22 + 蓝牙 12 |
| 复刻**必需** | **38** | 含 assistantapi SDK 骨架 20 类 |
| **可选**（本机不可达 / 需 API≥29、30） | **16** | 全部与「内部音频录制」「SOS 硬件手电」「Lottie 引导」有关 |
| **dead code** | **3** | `BluetoothA2dpEx`/`BluetoothHeadsetEx`/`BluetoothOfflineEx` 零调用者（另有库级死代码，见 §A-5） |
| 归属其它 task（仅登记映射） | 1 | `qs.tiles.b`（BluetoothTile，归 task-5） |
| UI 元素 | **33** | 布局内控件 + QS tile 图标/标签 + 通知 + 悬浮计时窗 |
| UI 文案（本任务负责） | **22** key | 另有 4 条蓝牙文案归 task-5（已附登记） |
| **mp4 输出结论** | **不需要自写 muxer** | `MediaRecorder(MPEG_4)` 直出 `.mp4`；mp4parser 在包内但 **reachable=0（全死）**，screenrecord 根本没用它 |
| **BLE 是否死代码** | **是（客户端协议栈全死）** | `com.xtc.client.*`(34 类) + `BleClientManager` 无调用者；录屏用的 `com.xtc.baseservice.c` 是 **CameraInUseMonitor**，不是蓝牙 |
| 复刻预估 LOC | **≈3,660 行 Java** | 录屏 ~1,650 / 手电 ~880 / 助理 ~950 / 蓝牙 ~180（不含资源） |

**两个最容易踩的坑（必读）**：
1. `com.xtc.baseservice.c` 与 `com.xtc.baseservice.c.a` **不是同一个东西**：前者 = `CameraInUseMonitor`（相机占用监控，`camera2.CameraManager.registerAvailabilityCallback`），后者 = `BleClientManager`（蓝牙客户端管理器）。录屏只用了前者。**不要因为看到 `baseservice.c` 就认为录屏依赖 BLE。**
2. 本机 API 27 上「手电筒」**不是** `CameraManager.setTorchMode`，而是**屏幕手电**：全屏纯白高亮页面 + 屏幕亮度动画（`FlashLightActivity`）。`setTorchMode` 只出现在 `statusbar.policy.q`（`Build.VERSION.SDK_INT >= 30` 才选用，见 `FlashlightControllerDelegate:28-32`）。

---

## A. 类清单表（全类名 / 文件:行 / 职责 / 判定 / 本机可达）

> 路径前缀统一为 `work\jadx18b\sources\`。`可达` 列含义：**是** = 本机 API27 会执行；**否(SDK30)** = 只走 `SDK_INT>=30` 分支；**否(SDK29)** = 只走 `SDK_INT>=29/30` 分支；**否(无调用者)** = 无人调用。

### A-1 录屏模块（15 类）

| ID | 类全名 | 文件:行 | 职责 | 判定 | 可达 |
|---|---|---|---|---|---|
| C-01 | `com.android.systemui.screenrecord.a`（RecordingController） | `com\android\systemui\screenrecord\a.java:26` | 录屏总控：前置检查/倒计时/启动停止/状态机/窗口调度/QS 观察者 | **必需** | 是 |
| C-02 | `screenrecord.b`（RecordingFloatController） | `screenrecord\b.java:28` | 悬浮窗控制器：窗口参数、显隐、动画、点击、计时器副窗 | **必需** | 是 |
| C-03 | `screenrecord.c`（RecordingInterruptListener） | `screenrecord\c.java:17` | 中断监听：熄屏/关机/充电/视频通话/模式切换/相机占用/功能开关 | **必需**（可裁剪到 3 个监听） | 是 |
| C-04 | `screenrecord.d`（RecordingStorageHelper） | `screenrecord\d.java:6` | 缓存目录 `externalCacheDir/ScreenRecord`、清理、剩余空间判断 | **必需** | 是 |
| C-05 | `screenrecord.e`（ScreenInternalAudioRecorder） | `screenrecord\e.java:19` | `AudioPlaybackCapture` 录系统内部声 + AAC 编码 + MediaMuxer | 可选 | 否(SDK29) |
| C-06 | `screenrecord.f`（ScreenMediaRecorder） | `screenrecord\f.java:28` | **核心**：MediaProjection+VirtualDisplay+MediaRecorder，mp4 落盘、保存相册 | **必需** | 是 |
| C-07 | `screenrecord.g`（ScreenRecordingMuxer） | `screenrecord\g.java:16` | 用 `MediaExtractor+MediaMuxer` 把 视频/音频 两个 mp4 合成一个 | 可选 | 否(SDK30) |
| C-08 | `screenrecord.RecordingService` | `screenrecord\RecordingService.java:22` | 前台服务：START/STOP action、通知、最大时长定时器、结果回调 | **必需** | 是 |
| C-09 | `screenrecord.RecordingFloatView` | `screenrecord\RecordingFloatView.java:23` | 悬浮窗自绘：红点 + Chronometer + 呼吸动画 + 文案切换动画 | **必需** | 是 |
| C-10 | `screenrecord.ScreenRecordAudioTipsLayout` | `screenrecord\ScreenRecordAudioTipsLayout.java:18` | 「准备录制/录制系统声音/开始录制」整屏引导 | 可选 | 部分（需 XTC 内部音频特性） |
| C-11 | `screenrecord.ScreenRecordingAudioSource` | `screenrecord\ScreenRecordingAudioSource.java:4` | 枚举 `NONE/INTERNAL/MIC/MIC_AND_INTERNAL` | **必需** | 是 |
| C-12 | `screenrecord.config.a`（RecordingConfigHelper） | `screenrecord\config\a.java:10` | 从云控 JSON 反序列化录制参数（单例缓存） | 可选 | 是（但数据源是云控） |
| C-13 | `screenrecord.config.RemoteRecordingConfig` | `screenrecord\config\RemoteRecordingConfig.java:3` | 参数模型：`durationS`(默认 60s)/`storageMB`(默认 100MB) | **必需**（改本地常量） | 是 |
| C-14 | `com.android.systemui.media.MediaProjectionPermissionActivity` | `media\MediaProjectionPermissionActivity.java:11` | 给**第三方** `com.xtc.*` 应用免弹窗发投屏凭据（校验 `com.android.phone` 签名） | 可选 | 是（但第三方入口，非系统 UI 自身） |
| C-15 | `com.android.systemui.qs.tiles.g`（ScreenRecordTile） | `qs\tiles\g.java:17` | 录屏 QS 磁贴：点击开始/停止、状态图标、错误 Toast、助理 directive 处理 | **必需** | 是 |

### A-2 手电筒 / SOS 模块（9 类）

| ID | 类全名 | 文件:行 | 职责 | 判定 | 可达 |
|---|---|---|---|---|---|
| C-16 | `com.android.systemui.flashlight.FlashLightActivity` | `flashlight\FlashLightActivity.java:47` | **屏幕手电**：全屏白/黑双页 ViewPager（照明模式/闪烁模式）、亮度 100→255 动画、180s 自动退出、音量键退出 | **必需** | 是 |
| C-17 | `flashlight.FlashLightSOSDialog` | `flashlight\FlashLightSOSDialog.java:15` | SOS 弹窗：开关 SOS、跳「切换成手电」；`XtcStayFront` 常驻 | 可选 | 否(SDK30 逻辑) |
| C-18 | `flashlight.FlashLightGuideDialog` | `flashlight\FlashLightGuideDialog.java:13` | Lottie「请先翻起摄像头」引导浮层 | 可选 | 否(SDK30) |
| C-19 | `flashlight.a`（TimerTaskUtil） | `flashlight\a.java:6` | 按 delay 存 TimerTask，统一以 8180ms 周期循环（SOS 节律驱动器） | 可选 | 否(SDK30) |
| C-20 | `com.android.systemui.statusbar.policy.g`（FlashlightController 接口） | `statusbar\policy\g.java:6` | 手电筒控制器接口 + `FlashlightListener` | **必需** | 是 |
| C-21 | `statusbar.policy.j`（LegacyFlashlightControllerImpl） | `statusbar\policy\j.java:14` | **API27 实际实现**：仅维护 boolean + 打开 `FlashLightActivity` | **必需** | 是 |
| C-22 | `statusbar.policy.q`（TorchFlashlightControllerImpl） | `statusbar\policy\q.java:38` | `CameraManager.setTorchMode` + SOS 节律 + 引导动画 + WakeLock | 可选 | 否(SDK30) |
| C-23 | `statusbar.policy.FlashlightControllerDelegate` | `statusbar\policy\FlashlightControllerDelegate.java:10` | 按 SDK/相机能力选实现；提供 `getTorchCameraId()`（FLASH_INFO_AVAILABLE+LENS_FACING=1） | **必需** | 是（但本机选 j） |
| C-24 | `qs\tiles\d`（FlashlightTile） | `qs\tiles\d.java:18` | 手电 QS 磁贴：开关、SOS 弹窗拦截、助理 directive（含 `query` 回包） | **必需** | 是 |

### A-3 助理服务模块（22 类：vassistant 5 + assistantapi SDK 17）

| ID | 类全名 | 文件:行 | 职责 | 判定 |
|---|---|---|---|---|
| C-25 | `com.android.systemui.vassistant.a`（ApiConstants） | `vassistant\a.java:5` | namespace `ai.xtc.device_interface.extensions.system_ui_control`；directive 名 `FlashLight`/`VideoRecord` | **必需** |
| C-26 | `vassistant.b`（SwitchControlHelper） | `vassistant\b.java:8` | 单例注册表：directive 名 → 处理器（QS tile 注册自己） | **必需** |
| C-27 | `vassistant.c`（SystemUiControlDeviceModule） | `vassistant\c.java:15` | 设备模块：解析 Directive→找 handler→回 `DirectiveResult`；注册 payload 类型表 | **必需** |
| C-28 | `vassistant.SwitchPayload` | `vassistant\SwitchPayload.java:6` | payload：`isOpen:boolean` + `target:String`（`"query"` 为查询语义） | **必需** |
| C-29 | `vassistant.SystemControlService` | `vassistant\SystemControlService.java:9` | 前台服务，绑定 `com.xtc.assistantapi.directive` 意图入口 | **必需** |
| C-30 | `com.xtc.assistantapi.a`（DeviceModuleManager） | `com\xtc\assistantapi\a.java:19` | 单例：注册模块、JSON→Directive、主线程分发、选 MessageSender | **必需** |
| C-31 | `assistantapi.a.a`（BaseDeviceModule） | `assistantapi\a\a.java:16` | 抽象模块基类 + 回包 `a(Context, DirectiiveResult/String)` | **必需** |
| C-32 | `assistantapi.d.a`（AssistantIntentUtil） | `assistantapi\d\a.java:11` | 从 Intent 取 `assistantapi-directive` 并分发 | **必需** |
| C-33 | `assistantapi.c.h`（IntentAction） | `assistantapi\c\h.java:9` | action/extras/URI 常量（`com.xtc.assistantapi.directive` 等） | **必需** |
| C-34 | `assistantapi.c.d`（DirectiveCode） | `assistantapi\c\d.java:4` | 200/300/400/403/404/500 返回码 | 可选 |
| C-35 | `assistantapi.c.g`（IMessageSender） | `assistantapi\c\g.java:8` | 回包接口（默认实现 `b.a` 是空实现） | **必需** |
| C-36 | `assistantapi.message.Directive` | `assistantapi\message\Directive.java:11` | `{header, payload}`，含 rawMessage/rawPayload | **必需** |
| C-37 | `assistantapi.message.Header` | `assistantapi\message\Header.java:9` | `namespace/name/directiveId` | **必需** |
| C-38 | `assistantapi.message.Payload` | `assistantapi\message\Payload.java:7` | 空 payload 基类（Parcelable） | **必需** |
| C-39 | `assistantapi.message.PayloadConfig` | `assistantapi\message\PayloadConfig.java:9` | `namespace+name → Payload 子类` 注册表 | **必需** |
| C-40 | `assistantapi.message.DirectiveResult` | `assistantapi\message\DirectiveResult.java:6` | 回包模型（errorCode/dec/params/directiveId…） | **必需** |
| C-41 | `assistantapi.message.MessageCode` | `assistantapi\message\MessageCode.java:5` | `000001 成功 / 000003 错误 / 000006 功能禁用 / 000008 不支持` … | **必需** |
| C-42 | `assistantapi.message.DirectiveConstants` | `assistantapi\message\DirectiveConstants.java:4` | `ai.xtc` / `ai.duer` 前缀 | **必需** |
| C-43 | `assistantapi.message.DirectiveDeserializer` | `assistantapi\message\DirectiveDeserializer.java:14` | Gson 自定义反序列化（header/payload 分离） | **必需** |
| C-44 | `assistantapi.launch.AssistantForegroundService` | `assistantapi\launch\AssistantForegroundService.java:9` | 前台服务基类：`assistant_service` 通知渠道 + `onStartCommand` 分发 | **必需** |
| C-45 | `assistantapi.launch.b`（DefaultComponentLaunch） | `assistantapi\launch\b.java:7` | startActivity/startService 默认实现 | 可选 |
| C-46 | `assistantapi.exception.HandleDirectiveException` | `assistantapi\exception\HandleDirectiveException.java` | directive 处理异常 | **必需** |

### A-4 蓝牙接口（12 类 / 使用点）

| ID | 类全名 | 文件:行 | 功能 | 判定 | 可达 |
|---|---|---|---|---|---|
| C-47 | `com.xtc.ultraframework.bluetooth.BluetoothDeviceEx` | `com\xtc\ultraframework\bluetooth\BluetoothDeviceEx.java:7` | 隐藏 API 包装：`isConnect/removeBound/cancelPairing/BondProcess/getAliasName/setAliasName/convertPinToBytes` | **必需**（反射实现） | 是（`isConnect` 3 处调用） |
| C-48 | `BluetoothAdapterEx` | `...\BluetoothAdapterEx.java:8` | `setDiscoverableTimeout/setScanMode/setblecontimeout` | 可选 | 是 |
| C-49 | `BluetoothA2dpEx` | `...\BluetoothA2dpEx.java:7` | A2DP connect/disconnect 包装 | **dead** | 否(无调用者) |
| C-50 | `BluetoothHeadsetEx` | `...\BluetoothHeadsetEx.java:7` | HFP connect/disconnect 包装 | **dead** | 否(无调用者) |
| C-51 | `BluetoothOfflineEx` | `...\BluetoothOfflineEx.java:16` | `setOutPower`（私有） | **dead** | 否(无调用者) |
| C-52 | `BluetoothAutoCloseManager` | `...\BluetoothAutoCloseManager.java` | 蓝牙自动关闭策略（120s） | 可选 | 是（`xtc.statusbar.b.b:135-139`） |
| C-53 | `qs.tiles.VolumeFuncSetting`（蓝牙段落） | `qs\tiles\VolumeFuncSetting.java:546-563,619-694` | 媒体音量是否走蓝牙：遍历已绑定设备 + `isConnect` + `BluetoothClass` 大类过滤（7936 音频/1024 可穿戴） | **必需** | 是 |
| C-54 | `com.xtc.statusbar.b.b`（蓝牙状态模型） | `com\xtc\statusbar\b\b.java:264-303` | 已连接经典蓝牙检测、半开模式、延迟关闭 | 可选 | 是 |
| C-55 | `xtc.statusbar.headset.HeadsetConnectManager` | `...\headset\HeadsetConnectManager.java:216-217,231-260` | 自研耳机 HFP+A2DP+BLE 连接管理（另一模块，仅映射） | 可选 | 是 |
| C-56 | `statusbar.policy.d`（BluetoothControllerImpl） | `statusbar\policy\d.java:15` | **蓝牙开关状态**：`isEnabled/disable/getScanMode` + `STATE_CHANGED/SCAN_MODE_CHANGED/CONNECTION_STATE_CHANGED` 广播 | **必需** | 是 |
| C-57 | `com.xtc.statusbar.BluetoothHelper` | `com\xtc\statusbar\BluetoothHelper.java:9` | 状态栏用蓝牙开关/连接状态（`xtc.matemode.action.MATE_ON/OFF`） | 可选 | 是 |
| C-58 | `qs.tiles.b`（BluetoothTile） | `qs\tiles\b.java:10` | 蓝牙 QS 磁贴（lottie `pull_up_setting/bluetooth.json`，点击跳 `BLUETOOTH_SETTINGS`） | 归属 **task-5** | 是 |

### A-5 判定汇总 + 库级死代码

| 判定 | 数量 | 类 ID |
|---|---|---|
| 复刻必需 | **38** | C-01,02,03,04,06,08,09,11,13,15,16,20,21,23,24,25,26,27,28,29,30,31,32,33,35,36,37,38,39,40,41,42,43,44,46,47,53,56 |
| 可选 | **16** | C-05,07,10,12,14,17,18,19,22,34,45,48,52,54,55,57 |
| dead code | **3** | C-49, C-50, C-51 |
| 归属 task-5 | 1 | C-58 |
| 合计 | **58** | |

**库级死代码（不计入 58，但必须一起判定）**：

| 目标 | 证据 | 结论 |
|---|---|---|
| `com.googlecode.mp4parser.*` + `com.coremedia.iso.*`（mp4parser 全库） | `analysis\modules\_reachable.csv` 中 grep `mp4parser|coremedia|isoparser` = **0 行**；`screenrecord` 全部 13 类源码 grep = 0 引用 | **整库死代码，复刻不需要**（详见 §B-1 mp4 结论） |
| `com.xtc.client.*`（34 类 BLE 客户端：`a/b/entity/listener/request/response`） | 全包无 BLE 服务端调用者；`com.xtc.baseservice.c.a`(BleClientManager) 无外部调用 | **死代码，完全不实现** |
| `com.android.systemui.screenrecord.e/g`、`policy.q`、`FlashLightSOSDialog`、`GuideDialog`、`flashlight.a` | 均只在 `SDK_INT>=29/30` 分支被引用 | **本机运行时死代码**（建议直接不移植，节省 ~700 行） |
| `layout_record.xml` / `dialog_record_sending.xml` / `layout_tile_tip_view.xml` | 全包 grep 只有 `R` 常量定义，**无任何 `inflate(...)` 调用点** | **与本模块无关**（`layout_record` 属语音留言 UI），不要误移植 |

---

## B. 各模块：原逻辑 → 复刻方案 → 预计 LOC

### B-1 录屏模块

#### B-1.1 原调用链（精确到行）

```
[入口1] QS 磁贴 ScreenRecordTile.handleClick()            qs\tiles\g.java:93-122
    → RecordingController.m464a(delay=300+qs_panel_collapse_duration_ms, tick=100, startPI, stopPI)  a.java:215-230
        ├ 前置检查 a()                        a.java:232-247   → 0..7 错误码
        ├ 首次使用提示判断 share.b.h(ctx)      a.java:220-226 → 弹「温馨提示」窗（d() :358-379）
        ├ g() 内部音频支持判断                 a.java:309-314
        └ b() CountDownTimer 倒计时后 send()  a.java:255-290
    → PendingIntent(startForegroundService, RecordingService, action=START, extra_useAudio)  a.java:470-478
[入口2] 助理 directive "VideoRecord"                      qs\tiles\g.java:260-273
[入口3] 系统/其他 stop: RecordingService.a(context)       RecordingService.java:281-283

RecordingService.onStartCommand(action)                   RecordingService.java:93-155
  START  → c()                                             :201-214
      ├ b() startForeground(NOTIF_ID=4274, channel="screen_record")  :192-199
      ├ a(1, true) → 回调 onRecordingStart                    :296-337
      ├ f = new f(ctx, audioSource, this)                     :161
      ├ f.m483a() 开始录                                     f.java:147-152
      └ d() 最大时长定时器 Timer                             :216-232
  STOP / STOP_FROM_NOTIF → f(); stopSelf()                 :166-173, 242-266
  onInfo(MAX_DURATION_REACHED / MAX_FILESIZE_REACHED)      :285-293

f.m483a()  → b() 准备                                     f.java:91-144
  ├ MediaProjection: ServiceManager("media_projection") → IMediaProjectionManager.createProjection(uid, pkg, 0, false)  f.java:92
  ├ temp 文件: externalCacheDir/ScreenRecord/tempXXXX.mp4  f.java:93-97 + d.java:9-17
  ├ MediaRecorder: setVideoSource(2=SURFACE), setOutputFormat(2=MPEG_4), setVideoEncoder(2=H264)
  │   size=realMetrics(w,h), 30fps, captureRate=30, bitrate=w*h*30*0.5, maxDuration=config, maxFileSize=100MB  f.java:110-126
  ├ 音频: 仅当 audioSource==MIC → setAudioSource(0), AAC(4), 1ch, 196000bps, 44100Hz  f.java:107-109,127-132
  ├ setOutputFile(temp.mp4); prepare(); getSurface()        f.java:133-135
  └ MediaProjection.createVirtualDisplay("Recording Display", w, h, dpi, 16, surface, null, null)  f.java:136
f.m484a() 停止：MediaRecorder.stop(); projection.stop(); release(); surface/display release  f.java:155-190
f.a() 保存：                                                  f.java:211-226
  ├ (SDK>=30 且 内部音频) g 合成 → 本机不走
  ├ duration = MediaMetadataRetriever(9)  l.a(path)          baseservice\util\l.a
  ├ duration<=0 → 返回 null（视为失败）
  └ 相册入库 com.xtc.data.b.b.a(ctx).m1450a(DbAlbumData…)   f.java:242-257
      name = "screenrecord" + currentTimeMillis + ".mp4"
      标题 by_screen_record / 来源 from_screen_record
[回调闭合] RecordingService.a(1|2|3, ok, msg) → RecordingController 回调  a.java:68-174
  onRecordingStart   → 记开始时间、FloatController 计时
  onRecordingEnd     → 停止计时、FloatController.stopLoading
  onGeneratingVideo  → 导出中
  onSavingVideoStart → 保存中
  onSavingVideoEnd(str) → duration/大小校验 → Toast「录屏已保存到相册」/「保存失败」+ FloatView 文本
  onError(code,msg)  → Toast / FloatView 收起
```

#### B-1.2 状态机（复刻必须实现，4 个布尔 + 2 个瞬时态）

| 状态 | 字段（`screenrecord.a`） | 语义 | 触发 |
|---|---|---|---|
| 倒计时中 | `f824a`（`m471b()`） | 已点击、尚未真正录 | CountDownTimer 启动 → :256 |
| 录制中 | `f825b`（`d()`） | MediaRecorder 已 start | onRecordingStart → :76 |
| 导出中 | `f826c`（`e()`） | 停止后保存/合成 | onGenerating/onSavingStart → :104,121 |
| 已保存 | `f827d`（`c()`） | 本次保存完成（用于禁止重复保存） | onSavingVideoEnd → :129 |

> 磁贴可用态 = `倒计时中 || 录制中 || 导出中 || 功能开关开启`（`qs\tiles\g.java:171-183`）。
> 磁贴点击分支顺序：倒计时中→取消；录制中→停止；导出中→忽略；否则→开始（`g.java:93-122`）。

#### B-1.3 前置检查错误码（必须照抄语义，文案见 §C）

| code | 触发条件（`a.java:232-247`） | 用户提示（QS 层 `g.java:128-145`） |
|---|---|---|
| 0 | 可录制 | — |
| 1 | 手表模式非「normal」（`baseservice.f.a().h()` 为 false） | 走 `xtc.statusbar.c.a` 提示模式名 |
| 2 | 通话中（`TelephonyManager.getCallState() != 0`） | 静默返回 |
| 3 | `SystemProperties.getBoolean(z.au)` 为 true（锁屏/常亮键） | 静默返回 |
| 4 | 剩余存储 < `storageMB`（`d.a(int)`，默认 100MB） | Toast「相册存储空间不足无法录屏」 |
| 5 | 相机被占用（`baseservice.c.a(ctx).m1261a()`） | Toast「已启用摄像头 无法录屏」 |
| 6 | 首次使用（需要弹提示窗） | 弹「温馨提示」窗 |
| 7 | 支持内部音频（需要弹音频设置页） | 弹「准备录制」页 |

#### B-1.4 复刻方案（逐点）

| 环节 | 原实现 | 复刻方案（公开 API） | 备注 |
|---|---|---|---|
| 投屏授权 | `IMediaProjectionManager.createProjection(uid,pkg,0,false)`（`f.java:92`，隐藏 API + `MANAGE_MEDIA_PROJECTION`） | **方案A（推荐，system uid 内）**：同样通过 `ServiceManager.getService("media_projection")` 反射调用 `createProjection`；`MediaProjection` 构造需隐藏 API（平台签名可反射）。**方案B（回退）**：`MediaProjectionManager.createScreenCaptureIntent()` + `startActivityForResult`（会弹系统确认框，SystemUI 内嵌需 `FLAG_ACTIVITY_NEW_TASK`） | 风险见 §E-2 |
| 录屏编码 | `MediaRecorder`（H264/MPEG_4/30fps/bitrate=w*h*30/2） | **原样保留**，参数照抄 | 无需 MediaCodec |
| 音频 | 仅 `MIC`，AAC 196kbps | 保留 MIC 分支；`INTERNAL` 不实现 | API27 无内部音频 |
| 虚拟屏 | `createVirtualDisplay(name,w,h,dpi,16,surface,null,null)` | 原样；flags 16=`VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR` | |
| **mp4 输出** | MediaRecorder 直出 `.mp4` | **直出（结论见下）** | 不自写 muxer |
| 保存 | XTC 相册 DB `com.xtc.data.b.b`（私有库） | **改 `MediaStore.Video` insert**（`RELATIVE_PATH=Movies/ScreenRecord`，8.1 用 `DATA`/`DISPLAY_NAME`）或直接 `File` 移动到 `Environment.getExternalStoragePublicDirectory(DIRECTORY_MOVIES)`；时长/大小用 `MediaMetadataRetriever` 复检 | 去掉云控/相册 UI 文案常量 |
| 最大时长 | `Timer` + `RemoteRecordingConfig.getDurationMS()`（默认 60s，云控可改） | `Handler.postDelayed` + 本地常量 60_000 | 云控去掉（R-76） |
| 线程 | `com.xtc.baseservice.j.a(Supplier)` = RxJava 泵 | 改 `HandlerThread`/`ExecutorService`；UI 回调统一 `Handler(Looper.getMainLooper())` | 去掉 rxjava 依赖 |
| 中断监听 | 6 类监听（熄屏/关机/充电/视频通话/模式/相机/功能开关） | 保留 **熄屏、视频通话(`com.xtc.videochat.start`)、相机占用** 3 个；其余（关机/充电/云控开关）可删 | `screenrecord\c.java:119-180,243-266` |
| 相机占用 | `com.xtc.baseservice.c`（CameraInUseMonitor） | **`CameraManager.registerAvailabilityCallback`** 自实现（8 行） | 见 §0 坑 1 |
| 功能开关 | `com.xtc.system.account.d` FunSwitch + `baseservice.f` 模式 | 改本地 `SharedPreferences`/常量；`funSwitch` 语义保留为「录屏功能开关」 | R-67 |

#### B-1.5 **mp4 输出方案 — 明确结论**

| 候选方案 | 证据 | 评价 |
|---|---|---|
| **A. `MediaRecorder` 直出 MP4（原版做法）** | `f.java:111` `setOutputFormat(2)` + `f.java:133` `setOutputFile(temp.mp4)`；`com.xtc.data.d.a.k = ".mp4"` | ✅ **推荐**。0 额外代码，容器/封装由 framework 完成；码率 6 级联调一致；API27 稳定 |
| B. 自写 MP4 muxer（mp4parser 替身） | mp4parser 在包内（`com.googlecode.mp4parser.*`），但 `_reachable.csv` **0 命中**，且 screenrecord 无引用 | ❌ **不需要**。原版并没有用 mp4parser 做录制 |
| C. `MediaExtractor`+`MediaMuxer` 二次封装（原 `g.java`） | `g.java:42-85`，仅 `f.java:212-222`（`SDK>=30` 且内部音频分支）调用 | ⚠️ 仅当要支持「系统内部音频」时才需要（API 29+），本机不实现 |
| D. `MediaCodec` 自编码 + `MediaMuxer` | 无对应原实现 | ❌ 不推荐，纯增加复杂度 |

> **结论：复刻用 A（`MediaRecorder` 直出 `.mp4`）。若未来移植到 API 29+ 且要内部音频，再补 C（`g.java` 的 80 行公开 API 逻辑，直接照抄即可，不需要 mp4parser）。**

#### B-1.6 预计 LOC

| 文件 | 原行数 | 复刻 LOC |
|---|---|---|
| `RecordingService.java` | 362 | 280 |
| `screenrecord/f.java`（ScreenMediaRecorder） | 273 | 220（去掉 SDK30 分支/云控） |
| `screenrecord/a.java`（RecordingController） | 507 | 300（去掉埋点/FunSwitch/首次提示分支细化） |
| `screenrecord/b.java`（RecordingFloatController） | 365 | 260 |
| `screenrecord/c.java`（录制中可控中断） | 317 | 110（裁到 3 个监听） |
| `screenrecord/d.java` | 35 | 30 |
| `RecordingFloatView.java` | 225 | 180 |
| `ScreenRecordAudioTipsLayout.java` | 95 | 60（可选） |
| `config/*`（2 类） | 83 | 20（本地常量） |
| `qs/tiles/g.java`（ScreenRecordTile） | 274 | 180 |
| `ScreenRecordingAudioSource` | 9 | 9 |
| **小计** | **2,545**（= screenrecord 必需 11 文件 2,271 + tiles.g 274，已剔除 e/g 的 356 行） | **≈1,650** |

---

### B-2 手电筒 / SOS 模块

#### B-2.1 原逻辑：API27 与 API30 两套完全不同的实现

`FlashlightControllerDelegate` 择路（`statusbar\policy\FlashlightControllerDelegate.java:26-33`）：
```java
if (Build.VERSION.SDK_INT >= 30 && a(context)) this.f1193a = new q(context); // Torch 实现
else                                        this.f1193a = new j(context); // Legacy 实现
// a(Context) = getTorchCameraId(CameraManager) != null（FLASH_INFO_AVAILABLE && LENS_FACING==1）:70-113
```

**本机（API27）走 `j`（LegacyFlashlightControllerImpl）** —— `statusbar\policy\j.java`：
```
g.mo596a(true)  → ActivityStarter.startActivity(new Intent(ctx, FlashLightActivity.class))  j.java:73-78
               + this.f1213a = true, 通知所有 listener a(z,false)
g.mo595a()      → 返回 f1213a（只有"逻辑开"）
g.mo617b()      → false（无 SOS）
g.mo618d()      → false（无 SOS）
g.c()           → true（功能开关恒可用）
```
> 也就是说：**API27 上手电筒 = 打开一个全屏高亮 Activity**，没有 `setTorchMode`、没有 SOS。

**`FlashLightActivity`（真正的「屏幕手电」，`flashlight\FlashLightActivity.java`）**：
| 项 | 实现 | 行 |
|---|---|---|
| 窗口 | `addFlags(128=KEEP_SCREEN_ON)`、`addFlags(524288=FLAG_SHOW_WHEN_LOCKED)` | :213-214 |
| 布局 | `activity_flash_light`：`fl_root` + `vp_flash_light`(ViewPager 160dp×180dp) + 两个指示点 | :215,228-231 |
| 双页 | page0=照明模式(item_flash_light 白底 `-1`)，page1=闪烁模式(黑底 `-16777216`)，标题分别 `string_light_title`/`string_flash_title` | :153-165,233-255 |
| 页面间距 | `setPageMargin(25)` + `setPageMarginDrawable(2131099905)` | :257-258 |
| 触摸 | `ACTION_DOWN`→`k()`：显示全部 UI + 1s 后再次隐藏；`ACTION_MOVE`→`d()`：显示 UI | :236-250,466-475,299-305 |
| 屏亮度 | `ValueAnimator(0→1, 2000ms)`：`screenBrightness = (int)(f*155+100)/255`（即 **100→255**）| :410-429 |
| 自动退出 | `rx.e.b(exitInterval=180s)` → `finish()`；间隔可由 SP `exitTime` 覆盖（0 则 180） | :316-332,437-464 |
| 退出 | `onPause` → `finish()`（任何遮挡即退） | :526-539 |
| 闪烁模式亮灭 | 对 `f498c`(v_back) 做 `alpha` 循环动画：序列 `[alpha=1,dur=100ms,delay=funFlag]、[alpha=0,dur=100ms,delay=30ms]`，每个动画 `onAnimationEnd` → `f()` 递归取下一个（`list` 索引自增循环） | :318,383-393,470-475 |
| 音量键 | `onStart` 记 `baseservice.util.d.a()`（音量），页面内不再改 | :403-408 |
| 生命期清理 | `onDestroy` 取消 animator/timer、`g.mo596a(false)` | :555-564 |

**API30 路径（本机不实现，仅为完整性记录）**：
- `q.java:435-470`：`CameraManager.setTorchMode(torchId, on)`；相机占用时 Toast「已启用摄像头 无法打开手电筒」；`baseservice.l.c(ctx,1800L)` 自动关。
- **SOS 节律（如果移植到 API30+，必须照抄这组数字）**：`q.java:437-460`
  ```
  亮灭时间点(ms, 相对 SOS 开始)：1000,1230,1330,1560,1660,1890,2390,3190,3690,4490,4990,5790,6290,6520,6620,6850,6950,7180
  周期 = 8180ms（每 8180ms 重复一轮）
  等效：3 短(230ms 亮) → 3 长(800ms 亮) → 3 短(230ms 亮) = 标准 SOS
  WakeLock：newWakeLock(PARTIAL, ...).acquire(1805000ms)；30min 后自动关闭 SOS
  驱动：flashlight.a（TimerTaskUtil）把所有 TimerTask 以 8180ms 周期统一调度（a.java:20-31,41）
  ```
- `FlashLightSOSDialog`（`flashlight\FlashLightSOSDialog.java`）：`FLAG_SHOW_WHEN_LOCKED|FLAG_KEEP_SCREEN_ON`（524289）；`ib_sos_switch` 点按切 SOS；`btn_sos_title` 点按（SDK>=30 且非翻起态）→ 弹引导动画；`v_switch_instead`（80dp 热区）→ 切回普通手电；`onFlashlightChanged(z)`/`onFlashlightSOSChanged(z)` 更新 `setSelected` 与文字颜色（选中 `color_sos_title_selected`，未选 `color/white`）；`XtcStayFront=true` 常驻（manifest）。
- `FlashLightGuideDialog`：Lottie 引导（见 §C）。

#### B-2.2 复刻方案

| 环节 | 原实现 | 复刻方案 | 说明 |
|---|---|---|---|
| 控制器接口 | `statusbar.policy.g` | 保留同名接口（`isOn/setOn/listener/mo617b(SOS)/mo618d(SOS弹窗显示中)`） | 磁贴与助理都依赖它 |
| API27 实现 | `j`（Legacy） | **照抄**：boolean + 启动 `FlashLightActivity` | 90 行 |
| 手电 UI | `FlashLightActivity`（双页 ViewPager + 亮度动画 + 180s 退出） | **照抄**，但 `com.android.common.library.widget.viewpager.ViewPager` 私有 ViewPager 需换 `androidx.viewpager.widget.ViewPager`；`FastOutLinearInInterpolator/LinearOutSlowInInterpolator` 用 androidx 同名类 | 见 §E-4 |
| 窗口 flag | `addFlags(524288)` | 8.1 用 `FLAG_SHOW_WHEN_LOCKED`；`setShowWhenLocked()` 是 API27+ API（可用） | |
| 亮度 | `WindowManager.LayoutParams.screenBrightness` | 公开 API，照抄 | |
| SOS / 硬件手电 / Lottie 引导 | 全部 `SDK>=30` | **不实现**（本机不可达）；仅把 SOS 节律表与 `XtcStayFront` 元数据留在文档里备移植 | 省 ~450 行 |
| 磁贴 | `qs.tiles.d` FlashlightTile | 保留 `handleClick`（SOS 弹窗拦截→开关），删掉埋点 `com.xtc.statusbar.a.c`、FunSwitch 监听改本地开关 | 见 §B-4 |

#### B-2.3 预计 LOC

| 文件 | 原行数 | 复刻 LOC |
|---|---|---|
| `FlashLightActivity.java` | 570 | 470 |
| `statusbar/policy/g.java`（接口） | 30 | 30 |
| `statusbar/policy/j.java`（Legacy 实现） | 95 | 90 |
| `statusbar/policy/FlashlightControllerDelegate.java` | 120 | 90（保留 `getTorchCameraId` 供未来） |
| `qs/tiles/d.java`（FlashlightTile） | 276 | 200 |
| （不实现）`q.java`+`SOSDialog`+`GuideDialog`+`flashlight.a` | 1,036 | 0 |
| **小计** | 2,147 | **≈880** |

---

### B-3 助理服务模块（vassistant + assistantapi）

#### B-3.1 协议（directive JSON 结构）

**入口 1：Binder/Intent**（manifest 证据 `xmltree-vendor.txt:149-161`）
```xml
<service android:name="com.android.systemui.vassistant.SystemControlService" android:exported="true">
  <intent-filter>
    <action android:name="com.xtc.assistantapi.directive"/>   <!-- c.h.f1975a -->
    <category android:name="android.intent.category.DEFAULT"/>
  </intent-filter>
  <meta-data android:name="assistantapi-directive" android:value="[FlashLight,VideoRecord]"/>
</service>
```
- `SystemControlService extends AssistantForegroundService`；`onBind()` 返回 **null**（不是 binder 服务，走 **startService + Intent extra**）。
- 分发链：`AssistantForegroundService.onStartCommand` → `assistantapi.d.a.a(intent, modules)` → 读 `intent.getStringExtra("assistantapi-directive")`（`assistantapi\d\a.java:22-23`，常量定义见 `c\h.java`）→ 每个 module `DeviceModuleManager.a(module)` 注册 → `m1224a(json)` 反序列化 → 主线程 `module.a(directive)`。
- 返回码：200 成功分发 / 400 JSON 解析失败 / 404 无对应 module / 500 异常（`c\d.java`、`assistantapi\a.java:84-120`）。

**directive JSON 形态**（`message\DirectiveDeserializer.java:19-38` + `Header`/`Payload`）：
```json
{
  "header": { "namespace": "ai.xtc.device_interface.extensions.system_ui_control",
              "name": "FlashLight" | "VideoRecord",
              "directiveId": "<id>" },
  "payload": { "isOpen": true, "target": "query" }
}
```
- payload 类型由 `PayloadConfig` 按 `namespace+name` 注册：`SwitchPayload`（`vassistant\c.java:50-54`）。
- `SwitchPayload` 字段：`isOpen:boolean`、`target:String`；`TARGET_QUERY="query"`（`SwitchPayload.java:7`）。`target=="query"` 表示「查询状态」，不回开关动作。

**回包（DirectiveResult）**（`message\DirectiveResult.java` + `a\a.java:39-56`）
```java
DirectiveResult r = new DirectiveResult();
r.setErrorCode("000001");                 // MessageCode.CODE_COMPLETE
r.setParams(Map.of("flashlightEnabled","true"));  // 仅 FlashLight+query 时带 params
module.a(context, r);                     // → DeviceModuleManager.m1225a().a(ctx,r) → IMessageSender
```
默认 `IMessageSender` 实现 `assistantapi.b.a` 是**空实现**（只打 log，`b\a.java:20-38`）→ 复刻时回包可只保留接口 + 日志/回调，真正回包人由 assistant app 侧处理（原版本就无实际发送）。

#### B-3.2 两个 action 的完整语义

| action | 处理器 | 逻辑（行号） | 回包 |
|---|---|---|---|
| `FlashLight` | `qs\tiles\d.java:241-275` | `target=="query"` → 回 `{"flashlightEnabled": "true/false"}` + `000001`；否则 `isOpen` 与当前态不同 → 走点击流程；SOS 弹窗显示中则按 SOS 规则处理 | `000001` 成功 / `CODE_ERROR(000003)` 异常 |
| `VideoRecord` | `qs\tiles\g.java:260-273` | `isOpen != (倒计时中‖录制中‖导出中)` → 执行 `e()`（开始/停止）；**无 query 支持** | `000001` 成功 / `000003` 异常 |

前置判定（模块级，`vassistant\c.java:30-42`）：handler 未注册 → `CODE_FUNC_UNSUPPORTED(000008)`；handler 存在但 `mo409e()`（功能开关关闭）→ `CODE_FUNC_DISABLE(000006)`。

#### B-3.3 复刻方案

| 层 | 原实现 | 复刻方案 | LOC |
|---|---|---|---|
| 服务 | `SystemControlService` + `AssistantForegroundService` | 保留（`assistant_service` 通知渠道 id/名称照抄；`startForeground(100001)`） | 60 |
| 模块/分发 | `DeviceModuleManager`(Gson+主线程 Handler) + `BaseDeviceModule` + `PayloadConfig` | 保留；Gson 依赖可换 `org.json` 手工解析（若不想引 gson） | 260 |
| 消息模型 | Directive/Header/Payload/DirectiveResult/MessageCode 等 8 类 | 保留（Parcelable 可省略，只需 Serializable/POJO） | 180 |
| 内容 | `vassistant.a/b/c/SwitchPayload` | 保留原样（`SwitchControlHelper` 就是 HashMap 注册表，30 行） | 150 |
| 处理器 | QS 磁贴里实现（`tiles.d` / `tiles.g`） | **保持这个设计**：助理不新增类，directive 直接调磁贴控制器 | 0（已在磁贴内） |
| 回包发送 | `IMessageSender`（默认空实现） | 保留接口 + 空实现；如需真实回包，用 `com.xtc.assistantapi.c.h` 的 URI（`content://com.xtc.vassistant.provider`）——**属可选** | 40（可选） |
| manifest | service + intent-filter + meta-data | **原样保留**（`assistantapi-directive=[FlashLight,VideoRecord]` 是外部读取的契约） | — |
| **小计** | | | **≈950（含可选回包）** |

#### B-3.4 关键约束
- `SystemControlService.onBind` 必须返回 null（否则 assistant 侧 binder 语义变化）。
- 分发必须在**主线程**（`DeviceModuleManager` 内部 `Handler(Looper.getMainLooper())`，`assistantapi\a.java:31-33,105-112`）。
- `Header.namespace == null` 会抛 `IllegalArgumentException`（`Header.java:60-64`）→ 解析前必须校验。
- `payload` 未注册类型时降级为空 `Payload`（`Directive.java:60-70`），不能 NPE。

---

### B-4 蓝牙接口（`BluetoothDeviceEx` → 公开 API 映射）

#### B-4.1 `BluetoothDeviceEx` 全部 7 个方法映射

| 方法 | 原实现（`BluetoothDeviceEx.java`） | 公开替代 | 复刻建议 |
|---|---|---|---|
| `isConnect(BluetoothDevice)` | `device.isConnected()` :8-10 | ✅ `BluetoothDevice.isConnected()`（API 14+ 公开） | **唯一被使用的 3 处都属于它 → 直接调用，无需反射** |
| `removeBound` | `device.removeBond()` :12-14 | ❌ 隐藏 API | 反射 `removeBond()`（平台签名可直调） |
| `cancelPairingUserInput` | `SDK>=30 ? cancelPairing() : cancelPairingUserInput()` :16-21 | ❌ 隐藏 | 反射；API27 用 `cancelPairingUserInput` |
| `cancelBondProcess` | `device.cancelBondProcess()` :23-25 | ❌ 隐藏 | 反射 |
| `getAliasName` | `SDK>=30? getAlias() : getAliasName()` :27-33 | ❌ 隐藏（`getAlias` 是 API30 公开） | 反射；本包**无调用者**（grep `getAliasName(` 仅定义处） |
| `setAliasName` | `device.setAlias(str)` :35-37 | ❌ 隐藏 | 反射；本包无调用者 |
| `convertPinTyBytes` | `BluetoothDevice.convertPinToBytes(str)` :39-41 | ❌ 隐藏 | 反射；本包无调用者 |

> **结论**：`BluetoothDeviceEx` 在 systemui 里 **只用到 `isConnect`**（3 处），其余 6 个方法是「备用包装」，复刻时**只需实现 `isConnect`（一行）**，其余可按需反射或直接不实现。这也是公共 API 映射的最省方案。

#### B-4.2 使用点（全包 3 处 + 开关状态 2 处 + 名称/RSSI）

| 使用点 | 文件:行 | 做什么 | 公开 API 复刻 |
|---|---|---|---|
| `VolumeFuncSetting.k()` | `qs\tiles\VolumeFuncSetting.java:561-566` | 媒体音量「是否输出到蓝牙」：遍历 `getBondedDevices()`，`isConnect` + `BluetoothClass.getMajorDeviceClass() ∈ {7936(0x1F00 音频), 1024(0x0400 可穿戴)}` | `BluetoothAdapter.getDefaultAdapter().getBondedDevices()` + `BluetoothDevice.isConnected()` + `getBluetoothClass().getMajorDeviceClass()`（全公开） |
| `xtc.statusbar.b.b.f()` | `com\xtc\statusbar\b\b.java:262-281` | 已连接经典蓝牙检测（决定是否切「半开」模式） | 同上 |
| `HeadsetConnectManager.b()` | `headset\HeadsetConnectManager.java:214-228` | 自研耳机连接状态恢复 | 同上（另有 A2DP/HFP profile 监听，见下） |
| 蓝牙**开关状态** | `statusbar\policy\d.java:46,92-131,196-244` | `BluetoothAdapter.isEnabled()/disable()/getScanMode()` + 广播 `STATE_CHANGED`(12 开 /10 关)、`SCAN_MODE_CHANGED`(mode 23=可发现 /20 可连接)、`CONNECTION_STATE_CHANGED`(2 已连 /0 断开) | 全公开 API + `BroadcastReceiver`，照抄 |
| 蓝牙**已连接设备/跳转设置** | `qs\tiles\b.java:74-87,146-152` | 磁贴点击 → `startActivity(Intent("android.settings.BLUETOOTH_SETTINGS"))`；`getScanMode()==23` 判开 | 公开 API（属 task-5，此处登记） |
| **名称** | `headset\a.java:326`、`BluetoothDeviceEx.getAliasName` | 耳机名 `BluetoothDevice.getName()` / alias | `getName()` 公开；alias 需反射 |
| **RSSI** | `headset\e.java:179-262` | BLE 扫描：`ScanResult.getRssi()`，**≥ -60 才算合格**，累加均值，过滤未绑定设备 | `BluetoothLeScanner.startScan(callback)` + `ScanResult.getRssi()`（公开 API 21+）；本机 8.1 可用 |
| `BluetoothAdapterEx` 使用 | `xtc\statusbar\b\a.java:33-49`、`xtc\statusbar\b\b.java:121-197` | `setDiscoverableTimeout`/`setScanMode(23|20, 60000)`（可发现 60s） | `setScanMode` 是隐藏 API → 反射；或改 `ACTION_REQUEST_DISCOVERABLE`（会弹窗，手表场景不合适） |
| `BluetoothAutoCloseManager` | `xtc\statusbar\b\b.java:135-139` | 自动关蓝牙 120s 策略 | XTC 框架能力，无公开等价 → **不实现**（属厂商特性） |

#### B-4.3 预计 LOC
`BluetoothDeviceEx` 精简版（仅 isConnect + 反射工具）**≈40 行**；`VolumeFuncSetting` 的蓝牙判断段 ≈30 行；蓝牙状态监听若已在别模块实现则 0。合计 **≈180 行**（含反射与 `BluetoothClass` 过滤）。

---

## C. UI 元素与文案全清单（为「改 UI 话术」准备）

> 文案源：`nx\resources\res\values\strings.xml`（**默认 values 即简体中文**，`values-zh-rCN` 无覆盖 → 直接改默认 values 即可）。
> `m.C0016m` = `com.android.systemui.m.C0016m`（string id 常量表，`m.java`）；`m.i/m.l/m.g/m.h/m.f/m.j` 分别是 id/layout/dimen/drawable/color/integer。

### C-1 录屏 UI（15 个元素）

| # | 布局/容器 | 元素 id | 类（使用方法） | 类型 | 文案 key | 中文原文 | 交互 / 动画 |
|---|---|---|---|---|---|---|---|
| 1 | `layout_recording_float_view` | `iv_tips_icon` | `RecordingFloatView` :68 | ImageView 12dp | — | （drawable `screen_recording_red` 红点） | 呼吸动画：alpha 1→0.35→1，1500ms，`repeatMode=REVERSE`，无限（`RecordingFloatView.java:109-119`） |
| 2 | `layout_recording_float_view` | `tv_tips` | `RecordingFloatView` :69 | Chronometer 12sp | — | 计时 `mm:ss`；可加前缀 `前缀 %s`（:154-164） | 切换文案时先 alpha 1→0（200ms）→ `ChangeBounds` 300ms（:82-119,205-212）；停止/结果时 `c()` 恢复 alpha 1 |
| 3 | 悬浮窗参数（代码） | — | `RecordingFloatController` :323-334 | WindowManager 窗 | — | `width/height = wrap_content(-2)` | `type=2003`、`flags=136`(KEEP_SCREEN_ON\|NOT_FOCUSABLE)、`gravity=49`(TOP\|CENTER_HORIZONTAL)、`y=recording_float_view_margin_vertical(0dp)`、`flagsEx bit16`(XTC 私有)；入场动画 alpha 0→1 + scale 0.5→1，300ms `PathInterpolator(0.42,0,0.58,1)`（:359-364） |
| 4 | 悬浮窗包装 | — | `RecordingFloatController` :132-137 | FrameLayout | — | padding 4dp、`clipToPadding=false` | 点击：倒计时中→取消；录制中→停止；导出中→无响应；已保存→(电量允许时)打开相册（:107-131） |
| 5 | 悬浮窗背景 | — | `RecordingFloatView` :70-79 | drawable | — | `recording_float_view_background` | padding start 4dp/end 6dp/vertical 2dp、elevation 4dp、clipToPadding=false |
| 6 | 计时器副窗（API<30） | `AlphaTextView` | `RecordingFloatController` :233-250 | Chronometer | — | 半透明 `mm:ss` 屏上计时 | **alpha=0.1**、`flags\|=16`(NOT_TOUCHABLE)、随录制开始/结束添加/移除 |
| 7 | `layout_screen_record_audio_tips` | `swipe_layout` | `ScreenRecordAudioTipsLayout` :44-49 | SwipeDismissLayout | — | 下滑/上滑关闭 | `setOnFinishScroll` → 回调 `b()`（取消） |
| 8 | 同上 | `tv_title` | :53 | TextView 20sp bold #55dd7b | `ready_for_screen_record` | **准备录制** | 静态（xml 里已写死） |
| 9 | 同上 | `rsi_switch` | :54-62 | `RightSwitchItem`+`SwitchButton` | `record_internal_audio` | **录制系统声音** | 开关；`setSwitchChecked()` 同步 SP；切换回调 `a(z)` 写 `Settings.Global["xtc_systemui_screen_record_audio_enabled"]` |
| 10 | 同上 | `tv_content` | :53 | TextView 12sp #737373 | `screen_record_setting_tips`（**HTML**） | 本次设置后，后续录制不再提示，可前往**手表设置-辅助功能-屏幕录制**中调整哦（`<font color=#FFFFFF>` 白字强调） | `Html.fromHtml` 解析（:53） |
| 11 | 同上 | `btn_start_record` | :63-72 | `LongHollowButton` 36dp | `start_screen_recording` | **开始录制** | 点击 → 回调 `a()`：关闭本页 → 直接开始录制；描边色 `color_55DD7B` |
| 12 | `layout_hint_window`（首次使用） | `swipe_layout`/`tv_title`/`tv_content`/`btn_i_know` | `RecordingController` :358-379、`HintWindowLayout` | 全屏窗 | `init_wx_notice` / `screen_record_using_tips` / `i_know` | **温馨提示** / 使用录屏会录制手表屏幕上显示的内容，并将其保存为视频。在录制过程中，要注意保护隐私信息，避免分享导致隐私信息泄露 / **我知道了** | 仅首次（SP `sp_first_time_use_screen_record`）弹；点「我知道了」→ 继续录制；滑掉 → 取消；`flags\|=8192`(FLAG_SECURE，防录屏自拍) |
| 13 | QS 磁贴 | `ic_quick_screen_record` | `qs.tiles.g` :155 | 图标 | `screen_record` | **录屏**（= 磁贴 label，`getTileLabel()` :243-245） | 状态：`value = 倒计时中‖录制中‖导出中`；点击见 §B-1.2；长按 → `com.xtc.setting.SCREEN_RECORD` 设置页（:208-210，`supportScreenRecordSetting=1` 时才返回 Intent） |
| 14 | 通知 | `screen_record` 渠道 | `RecordingService` :192-199 | Notification | `screen_record` | **录屏**（渠道名 + 描述） | channel id `screen_record`，importance 3(LOW→无横幅)，`setOngoing(true)`，`startForeground(4274)` |
| 15 | Toast（运行时） | — | `qs.tiles.g` :128-145 | Toast | 见 C-3 | — | 图标 `ic_quick_screen_record` 同上 |

**录屏文案 15 条**（key → 原文，可直接替换话术）：

| # | key | 中文原文 | 使用位置 |
|---|---|---|---|
| S1 | `screen_record` | 录屏 | 磁贴 label + 通知渠道名/描述 |
| S2 | `screen_record_using_tips` | 使用录屏会录制手表屏幕上显示的内容，并将其保存为视频。在录制过程中，要注意保护隐私信息，避免分享导致隐私信息泄露 | 首次提示窗正文 |
| S3 | `init_wx_notice` | 温馨提示 | 首次提示窗标题 |
| S4 | `i_know` | 我知道了 | 首次提示窗按钮 |
| S5 | `screen_record_setting_tips` | 本次设置后，后续录制不再提示，可前往`<font color=#FFFFFF>`手表设置-辅助功能-屏幕录制`</font>`中调整哦 | 音频设置页正文（HTML） |
| S6 | `record_internal_audio` | 录制系统声音 | 音频设置页开关标题 |
| S7 | `start_screen_recording` | 开始录制 | 音频设置页主按钮 |
| S8 | `ready_for_screen_record` | 准备录制 | 音频设置页标题 |
| S9 | `by_screen_record` | 录屏保存 | 相册条目标题（入库字段） |
| S10 | `from_screen_record` | 来源于手表录屏 | 相册条目来源（入库字段） |
| S11 | `screen_record_save_video_failed` | 保存失败 | 失败 Toast / 悬浮窗文案 |
| S12 | `screen_record_has_saved_to_album` | 录屏已保存到相册 | 成功 Toast / 悬浮窗文案 |
| S13 | `interrupt_screen_record_when_open_camera` | 录屏已停止 | 相机占用中断（录制中） |
| S14 | `disable_screen_record_when_open_camera` | 已启用摄像头 无法录屏 | 相机占用（开始前） |
| S15 | `can_not_record_screen_cause_storage` | 相册存储空间不足无法录屏 | 存储不足 |

### C-2 手电筒 / SOS UI（17 个元素）

| # | 布局 | 元素 id | 类 | 类型 | 文案 key | 中文原文 | 交互 / 动画 |
|---|---|---|---|---|---|---|---|
| 1 | `activity_flash_light` | `fl_root` | `FlashLightActivity` :228 | FrameLayout | — | — | 根容器 |
| 2 | 同上 | `vp_flash_light` | :229 | ViewPager 160dp×180dp | — | — | 双页切换；`background=flash_shape`；`pageMargin=25` + drawable |
| 3 | 同上 | `iv_select_light_mode` | :230 | ImageView 6dp | — | `ic_point_selects`（选中点） | 页 0 指示：选中→`ic_point_selects` |
| 4 | 同上 | `iv_select_flash_mode` | :231 | ImageView 6dp | — | `ic_point_unselects` | 页 1 指示；切换时互换图标（:345-355） |
| 5 | `item_flash_light`(页0) | `v_back` | :233（layout 共用） | View 160dp×180dp | — | `flash_shape` | 页 0 仅用整页白底（`setBackgroundColor(-1)` :234）；页 0 的 `v_back` 代码未单独引用 |
| 6 | `item_flash_light`(页0) | `ll_tip` | :254 | LinearLayout | — | — | 顶部提示容器；显示/隐藏 + 300ms `AlphaAnimation`（0→1 显示，1→0 隐藏） |
| 7 | `item_flash_light`(页0) | `tv_title`(2131296793) | :153 | TextView 16sp 黑字 | `string_light_title` | **照明模式** | 页 0 标题 |
| 8 | `item_flash_light`(页1) | `v_back` | :252 | View 160dp×180dp | — | `flash_shape` | **闪烁模式靠它整块亮灭**：`f498c.animate().alpha(0→1，130ms)` 交替（`g()` :370-393、`e()` :307-312 复位 alpha=1）。无点击监听 |
| 9 | `item_flash_light`(页1) | `ll_tip` | :255 | LinearLayout | — | — | 同上 |
| 10 | `item_flash_light`(页1) | `tv_title` | :162 | TextView | `string_flash_title` | **闪烁模式** | 页 1 标题 |
| 11 | `dialog_flash_light_sos` | `sdl_root` | `FlashLightSOSDialog` :42 | ConstraintLayout | — | `shape_rectangle_sos_gradient_bg` | SOS 页根（仅 API30+） |
| 12 | 同上 | `ib_sos_switch` | :52 | ImageButton 全屏 | — | `selector_sos_switch`（`sos_switch_on/off.png`） | 点按切 SOS；`setSelected` 反映状态 |
| 13 | 同上 | `v_switch_instead` | :54 | View 80dp 热区 | — | — | 顶部 40dp 位置：点它「切换成普通手电」 |
| 14 | 同上 | `TextView`(flash_sos_tip) | xml 内 | TextView 10sp 白 70% | `flash_sos_tip` | **请勿照射自己或他人眼睛** | 静态 |
| 15 | 同上 | `btn_sos_title` | :53 | Button 15sp | `flash_sos_title` | **SOS灯光信号** | 点按开 SOS（SDK>=30）/ 点按弹引导（未翻起相机）；选中色 `color_sos_title_selected`，未选 `white` |
| 16 | `layout_switch_flashlight` | `fl_flash` + `lav_tip` + TextView | `FlashLightGuideDialog` :31-33 | FrameLayout + **Lottie** | `guide_layout_anim_text9` | **使用手电筒\n请先翻起摄像头** | `lav_tip`：`lottie_fileName="guide/switch_camera/switch_camera_tip.json"`、`lottie_imageAssetsFolder="guide/switch_camera_tip"`、`background=switch_camera_tip_bg`；`onAttachedToWindow` → `playAnimation()`；失焦/点击 `fl_flash` → `cancelAnimation()` + dismiss |
| 17 | QS 磁贴 | `ic_quick_flashlight` | `qs.tiles.d` :144 | 图标 | `fun_switch_name_flashlight` | **手电筒**（磁贴 label，:216-218） | `value = 手电开 ‖ SOS 开 ‖ SOS 弹窗显示中`；点击：SOS 弹窗中→关闭弹窗；否则开/关手电 |

**手电文案 7 条**：

| # | key | 中文原文 | 使用位置 |
|---|---|---|---|
| F1 | `fun_switch_name_flashlight` | 手电筒 | 磁贴 label；功能开关关闭时 Toast 标题（`FlashLightSOSDialog:105`） |
| F2 | `string_light_title` | 照明模式 | 手电页 0 标题 |
| F3 | `string_flash_title` | 闪烁模式 | 手电页 1 标题 |
| F4 | `flash_sos_tip` | 请勿照射自己或他人眼睛 | SOS 页提示 |
| F5 | `flash_sos_title` | SOS灯光信号 | SOS 页按钮 |
| F6 | `guide_layout_anim_text9` | 使用手电筒\n请先翻起摄像头 | Lottie 引导页文案 |
| F7 | `no_flashlight_when_use_camera` | 已启用摄像头 无法打开手电筒 | 相机占用时硬件手电 Toast（API30+） |

### C-3 运行时动态提示（非资源、硬编码/拼接）

| 文案来源 | 原文 | 触发 |
|---|---|---|
| `RecordingService` 内部标记 | `"ignore"`（常量 `f804a`） | 表示"没在录"，**不展示给用户**，仅作状态哨兵 |
| `RecordingController.onError(1, msg)` | 各异常 message（`e2.getMessage()`） | 录制启动/停止失败时透传系统异常文本 |
| 手电默认退出时长 | 180s（常量 `c=180`） | 无 UI 文案，仅行为 |

### C-4 蓝牙/其它模块文案（**归 task-5，此处仅登记，勿重复改**）

> UI 元素（归 task-5）：**1 个** —— 蓝牙磁贴图标，`qs.tiles.b:107` 使用 Lottie `pull_up_setting/bluetooth.json`，`maxAnimationProgress=0.9`，`getTileLabel()` 返回 null（无文字标签）。
> 因此本任务 UI 元素合计 = 15（录屏）+ 17（手电）+ 1（蓝牙磁贴，登记）= **33**。

| key | 中文 | 出处 |
|---|---|---|
| `blue_opened` | 蓝牙已打开 | `qs.tiles.b` :78 |
| `blue_half_open` | 蓝牙连接已断开 | 同上 |
| `connect_ble_device` | 连接蓝牙设备 | `qs.tiles.b` :127 |
| `like_ble_earphones` | 如蓝牙耳机等 | 同上 |

### C-5 资源清单一览（复刻需保留的资源）

| 类型 | 名字 | 用途 |
|---|---|---|
| layout | `layout_recording_float_view` / `layout_screen_record_audio_tips` / `layout_hint_window` / `activity_flash_light` / `item_flash_light` / `dialog_flash_light_sos` / `layout_switch_flashlight` | UI |
| drawable | `screen_recording_red` / `recording_float_view_background` / `ic_quick_screen_record` / `ic_quick_flashlight` / `flash_shape` / `ic_point_selects` / `ic_point_unselects` / `selector_sos_switch`(`sos_switch_on/off.png`) / `shape_rectangle_sos_gradient_bg` / `selector_flash_sos_title_bg` / `switch_camera_tip_bg` | 外观 |
| dimen | `recording_float_view_padding_start`(4dp) / `_padding_end`(6dp) / `_padding_vertical`(2dp) / `recording_float_view_margin_vertical`(0dp) / `_margin_horizontal`(4dp) / `recording_float_wrapper_view_padding`(4dp) / `recording_float_text_margin_start`(4dp) / `recording_float_view_icon_size`(12dp) / `recording_float_view_text_size`(12sp) / `recording_float_view_elevation`(4dp) | 尺寸 |
| color | `color_55DD7B`(主色 #55DD7B) / `color_sos_title_selected` / `color_white_percent_70` / `color_000000` / `color_black` / `white` / `color_tip_gray` | 颜色 |
| integer | `qs_panel_collapse_duration_ms` | 倒计时总时长 = 300 + 该值 |
| assets | `guide/switch_camera/switch_camera_tip.json` + `guide/switch_camera_tip/img_0..4.png` | Lottie（可选） |

---

## D. 异步 / 线程 / 文件路径 / 权限（复刻落点）

### D-1 线程与异步

| 位置 | 原实现 | 复刻落点 |
|---|---|---|
| `RecordingService.onStartCommand` START/STOP | RxJava：`com.xtc.baseservice.j.a(Supplier).c(rx.d.c.e()).a(onNext,onError)`（`RecordingService.java:117-151`） | `new Thread(...)`（或 `ExecutorService` 单线程）+ 回调切主线程；**不要**再引 rxjava |
| 回调 → UI | `j.m1281a(Runnable)`（`RecordingService.java:300,343`）→ 主线程 | `Handler(Looper.getMainLooper()).post(...)` |
| 最大时长 | `java.util.Timer` + `TimerTask`（`RecordingService.java:216-232`） | `Handler.postDelayed`（更好取消） |
| 倒计时 | `CountDownTimer(300+qs_panel_collapse_ms, 100ms)`（`screenrecord\a.java:260-289`） | 照抄 `CountDownTimer` |
| 悬浮窗动画 | `Handler` + `android.animation.*`（`screenrecord\b.java:92,183-187`） | 照抄 |
| 手电亮度 | `ValueAnimator(2000ms)`（`FlashLightActivity.java:415-422`） | 照抄 |
| 手电自动退出 | RxJava `rx.e.b(180,SECONDS)`（`FlashLightActivity.java:457-463`） | `Handler.postDelayed` |
| 手电时间订阅 | RxJava `rx.e.a(0,1,SECONDS)`（`FlashLightActivity.java:440-451`）——**结果没人消费（空 anonymous）** → 死逻辑，可不实现 | 直接删 |
| SOS 节律 | `Timer`（`flashlight\a.java:20-31`）+ `WakeLock(1805000ms)`（`q.java:519-527`） | 不实现（本机不可达） |
| 助理分发 | `DeviceModuleManager.Handler(mainLooper).post`（`assistantapi\a.java:31,105-112`） | 照抄 |

### D-2 文件路径与保存

| 项 | 原值 | 证据 |
|---|---|---|
| 临时目录 | `context.getExternalCacheDir()/ScreenRecord/`（不存在则 `mkdirs`） | `screenrecord\d.java:9-17` |
| 临时文件 | `File.createTempFile("temp", ".mp4", cacheDir)` → 形如 `temp1234567890.mp4` | `f.java:97` + `com.xtc.data.d.a.k=".mp4"` |
| 启动前清理 | 记录开始时清理缓存目录旧文件（`d.m482a(ctx)`，在 `RecordingController.a()` 调） | `a.java:245`、`d.java:19-30` |
| 最终文件名 | `"screenrecord" + System.currentTimeMillis() + ".mp4"`（常量 `f869b`） | `f.java:34,234` |
| 最终存储 | XTC 相册库入库（`com.xtc.data.b.b`，`DbAlbumData`，`builder.a(13)` 类型码），成功后**删除临时文件** | `f.java:236-262` |
| 空间判断 | `com.xtc.utils.storage.m.a()`（可用 MB）≥ `RemoteRecordingConfig.getStorageMB()`（默认 100） | `d.java:32-35`、`a.java:246` |
| **FileProvider** | 声明存在：authority `com.android.systemui.fileprovider`、paths 资源仅 `<cache-path name="leak" path="leak/"/>`；**本模块代码 0 处使用**（全包 grep `getUriForFile` = 定义处） | `xmltree-vendor.txt:167-174`、`res\xml\fileprovider.xml` |
| 复刻落点 | 临时=`getExternalCacheDir()/ScreenRecord`（不变）；最终=`MediaStore.Video` insert（`DISPLAY_NAME=screenrecord<ts>.mp4`，目录 `Movies/ScreenRecord`，`MIME=video/mp4`），API27 用 `MediaStore.Video.Media.DATA` 绝对路径；或用 `Environment.getExternalStoragePublicDirectory(DIRECTORY_MOVIES)` + `MediaScannerConnection.scanFile` | — |

### D-3 权限 / 组件声明（必须一起复刻）

| 声明 | 值 | 证据 | 是否必需 |
|---|---|---|---|
| `uses-permission MANAGE_MEDIA_PROJECTION` | — | `xmltree-vendor.txt` permission 列表 | ✅ 必需（免确认投屏） |
| `uses-permission RECORD_AUDIO` | — | 同上 | ✅ 必需（MIC 录制） |
| `uses-permission READ_FRAME_BUFFER` | — | 同上 | ✅（平台签名） |
| `uses-permission WRITE_MEDIA_STORAGE` / `WRITE_EXTERNAL_STORAGE` | — | 同上 | ✅（写相册） |
| `uses-permission FOREGROUND_SERVICE` | — | 同上 | ✅ |
| `uses-permission BLUETOOTH` / `BLUETOOTH_ADMIN` | — | 同上 | ✅（蓝牙状态/开关） |
| `<service RecordingService exported=false foregroundServiceType=0x20>` | `0x20 = mediaProjection` | `xmltree-vendor.txt:162-166` | ✅ |
| `<service SystemControlService exported=true>` + intent-filter + meta-data | 见 §B-3.1 | `:149-161` | ✅ |
| `<activity FlashLightActivity launchMode=2 showWhenLocked=true theme=@0x7f0e0006>` | — | `:126-130` | ✅ |
| `<activity FlashLightSOSDialog ... > <meta-data XtcStayFront=true>` | — | `:131-141` | ⚠️ 保留无害（R-70） |
| `<activity MediaProjectionPermissionActivity exported=true>` | — | `:142-145` | 可选 |
| `<meta-data moduleSwitch / funSwitch / supportScreenRecordAudio>` | `[{"module":"3547","funSwitch":"com.android.systemui.screenrecord","notBind":3}]` / `[{"appPackage":"com.android.systemui.screenrecord","funSwitch":"1"}]` / `1` | `:182-190` | ✅ **不可删**（launcher/设置读取，R-69） |
| `<provider androidx.core.content.FileProvider authorities="com.android.systemui.fileprovider">` | paths=`@xml/fileprovider` | `:167-174` | 本模块不需要（保留无害） |

---

## E. 风险点关联（RISK-MAP 编号）与不可行/取舍点

| # | 风险 / 取舍 | 关联 RISK-MAP | 结论 |
|---|---|---|---|
| E-1 | **投屏免确认能力依赖系统私有 API**（`IMediaProjectionManager.createProjection`，`f.java:92`；`MediaProjectionManagerEx`） | RISK-MAP 无对应行（新增） | 平台签名 + `MANAGE_MEDIA_PROJECTION` 下可直连 `ServiceManager`；若移植到非 system 应用必须退化为 `createScreenCaptureIntent()` + 用户确认。**这是本模块最大不可行点** |
| E-2 | **XTC 私有窗口扩展**：`layoutParams.flagsEx`（`com.xtc.a.a.b`，`b.java:245,332` 调用 `q.a(params,true/false)` 设 `flagsEx` bit16/bit8） | 无 | 非 AOSP 字段，**复刻必须删除或改为条件编译**，否则编译失败。删除影响：悬浮窗"外部触摸穿透/防误触"行为略变 |
| E-3 | 悬浮窗类型 `2002/2003`（TYPE_SYSTEM_ALERT/TYPE_PHONE）+ `INTERNAL_SYSTEM_WINDOW` | 无 | 8.1 需平台签名（本包满足）；`flags=8192(FLAG_SECURE)` 用于首次提示窗保证不被自己录制 |
| E-4 | 私有控件依赖：`com.android.common.library.widget.viewpager.ViewPager`（`FlashLightActivity:229`）、`com.xtc.statusbar.SwipeDismissLayout`、`com.xtc.ui.widget.button.LongHollowButton`、`com.xtc.ui.widget.item.clickScale.RightSwitchItem`、`com.xtc.ui.widget.toggleswitch.SwitchButton`、`com.airbnb.lottie.LottieAnimationView` | 无 | 换 `androidx.viewpager` + 自绘等价控件；Lottie 仅在可选 SOS 引导页使用（可不引依赖） |
| E-5 | **云控功能开关**：`com.xtc.system.account.d`（FunSwitch，R-67）+ `screenrecord.config.a` 远程参数（R-76，`h.b(ctx, g.b.g, "")` 读云控 JSON） | **R-67 / R-76** | 降级本地：`RemoteRecordingConfig` 用默认 60s/100MB；录屏功能开关改本地常量/SP |
| E-6 | **埋点上报**：`com.xtc.statusbar.a.b`（ScreenRecordBehavior）、`com.xtc.statusbar.a.c`、`com.xtc.baseservice.b.c`（手电时长/SOS 使用） | **R-78-09 / R-78-10 / R-78-23 / R-78-24** | **全部删除**（`f819a` 回调里 10+ 处 `a.b.*` 调用、磁贴 `mo401a/g()` 埋点） |
| E-7 | **相册入库用私有库** `com.xtc.data.b.b`（`f.java:242-257`） | 无 | 换 `MediaStore`；`by_screen_record`/`from_screen_record` 两条文案可保留为标题/描述 |
| E-8 | manifest 功能元数据被外部读取 | **R-69** | `moduleSwitch`/`funSwitch`/`supportScreenRecordAudio` 必须原样保留 |
| E-9 | `FlashLightSOSDialog` 的 `XtcStayFront` 由 framework 读取（Java 侧 0 命中） | **R-70** | 保留；若移植到非 XTC 框架则无效（需 `setShowWhenLocked`+常驻前台 Activity 变通） |
| E-10 | 助理服务 meta-data `assistantapi-directive=[FlashLight,VideoRecord]` 是外部契约 | 无 | 必须保留 metadata 与 service `exported=true`+action，否则助理侧找不到入口 |
| E-11 | BLE / 蓝牙框架能力（`BluetoothAutoCloseManager`、`setScanMode`、`cancelPairingUserInput`） | **R-86**（保留无害，业务必需） | BLE 客户端整栈不复刻；`BluetoothDeviceEx` 只留 `isConnect`，其余反射或不实现 |
| E-12 | API 27 上「SOS 闪烁」「硬件手电」「Lottie 引导」全部不可达 | 无 | **不实现**（省 ~700 行）；若目标机型升到 Android 11，按 §B-2.1 的节律表与 `q.java` 逻辑补回 |
| E-13 | `MediaProjectionPermissionActivity` 校验 `com.android.phone` 签名 | 无 | 可选；若保留，注意 `checkSignatures` 结果 `>=0` 才算同签名 |
| E-14 | 录屏期间的"防自拍"与隐私：`FLAG_SECURE`(8192) 首次提示窗 | 无 | 建议保留（防提示窗被录进视频） |

---

## F. 建议实施顺序（前 5 步）

| 步 | 目标 | 交付物 | 依赖 | 预估 LOC | 验收标准 |
|---|---|---|---|---|---|
| **1** | **录屏内核跑通（无 UI）** | `ScreenMediaRecorder`(f) + `RecordingService` + `RecordingStorageHelper`(d) + `ScreenRecordingAudioSource` + 本地 `RemoteRecordingConfig` | 平台签名/投屏 API 可用（E-1 先验证） | ~600 | `adb shell am startservice -a com.android.systemui.screenrecord.START --ei extra_useAudio 0` → 60s 后 `externalCacheDir/ScreenRecord/` 出现可播放 mp4；`MediaMetadataRetriever` 时长>0 |
| **2** | **悬浮窗 + 状态机** | `RecordingFloatView` + `RecordingFloatController`(b) + `RecordingController`(a 精简版) | 步 1 的回调接口 | ~480 | 点磁贴→倒计时 0.3s+面板收起→悬浮红点+计时呼吸动画→点悬浮窗停止→2s 后文案「录屏已保存到相册」并淡出；熄屏/来电自动停止 |
| **3** | **QS 磁贴联动 + 中断监听** | `ScreenRecordTile`(g) + `RecordingInterruptListener`(c 裁剪 3 监听) + 前置检查错误码与 Toast | 步 2 | ~320 | 磁贴 4 态正确（可点/录制中/导出中/不可用）；相机占用与存储不足给出正确中文 Toast；长按进设置页 |
| **4** | **手电（API27 屏幕手电）** | `statusbar.policy.g` 接口 + `j` Legacy 实现 + `FlashlightControllerDelegate`(精简) + `FlashLightActivity` + `FlashlightTile` | 无（与录屏独立，可并行） | ~780 | 磁贴点击 → 全屏 160×180 双页手电，屏幕亮度 2s 内 100→255；左右滑切换「照明模式/闪烁模式」；触摸显隐 UI；180s 或 onPause 自动退出；再点磁贴关闭 |
| **5** | **助理服务 directive** | assistantapi 骨架(17 类精简) + `SystemControlService` + `SwitchControlHelper` + `SystemUiControlDeviceModule` + `SwitchPayload` + 两个 directive handler（已在磁贴内） | 步 3、步 4 的控制器必须已就绪 | ~950 | `am startservice -a com.xtc.assistantapi.directive --es assistantapi-directive '{"header":{"namespace":"ai.xtc.device_interface.extensions.system_ui_control","name":"FlashLight","directiveId":"1"},"payload":{"isOpen":true}}'` → 手电开；`payload.target="query"` → 回包 params `flashlightEnabled=true`；`VideoRecord` 同构；未注册 name → `000008` |

**后续（第 6 步起，非核心）**：蓝牙接口映射（`BluetoothDeviceEx.isConnect` 反射封装 + `BluetoothControllerImpl` 状态监听，~180 LOC）→ 若能升 Android 11 再补 SOS/lottie 引导（~450 LOC）。
**并行建议**：步 1-3（录屏）与步 4（手电）互不依赖 → 可两个 subagent 并行；步 5 必须等 3/4 完成（directive handler 就写在磁贴里）。
**LOC 口径说明**：步 1-3 合计 1,400 为核心必需；与 §B-1.6 的 1,650 差额 ~250 = 可选页（`ScreenRecordAudioTipsLayout`/首次提示窗）与联调余量。手电步 4 为 780，与 §B-2.3 的 880 差额 100 = `FlashlightControllerDelegate` 的 `getTorchCameraId` 保留部分。

---

## 附：证据索引（按需复查）

| 结论 | 证据位置 |
|---|---|
| 投屏免确认 | `screenrecord\f.java:92`；`com\xtc\ultraframework\media\projection\MediaProjectionManagerEx.java` |
| MediaRecorder 参数 | `screenrecord\f.java:107-136` |
| mp4 直出 + 后缀 | `screenrecord\f.java:97,111,133`；`com\xtc\data\d\a.java`（`k=".mp4"`） |
| mp4parser 死代码 | `analysis\modules\_reachable.csv`（0 命中）；`screenrecord\*` 全 0 引用 |
| 悬浮窗参数 | `screenrecord\b.java:323-334`；`com\xtc\statusbar\j.java:93-106`（`a(Context,String)` type=2002 全屏） |
| 录屏状态机 | `screenrecord\a.java:50-59,68-174,232-247` |
| 手电 API27 实现选择 | `statusbar\policy\FlashlightControllerDelegate.java:26-33` |
| 手电 Legacy 行为 | `statusbar\policy\j.java:73-78` |
| 屏幕手电 UI/亮度/超时 | `flashlight\FlashLightActivity.java:213-215,236-258,335-357,396-429,437-464,489-539` |
| SOS 节律（8180ms 周期） | `statusbar\policy\q.java:437-460,499-522`；`flashlight\a.java:20-31` |
| 助理协议/入口 | `assistantapi\message\Directive*.java`；`assistantapi\a.java:84-120`；`assistantapi\d\a.java:13-30`；`c\h.java:9-31` |
| 助理 directive 处理 | `qs\tiles\d.java:241-275`（FlashLight）；`qs\tiles\g.java:260-273`（VideoRecord） |
| manifest 组件 | `analysis\cloud-control\out\xmltree-vendor.txt:126-190` |
| 文案原文 | `nx\resources\res\values\strings.xml`（默认即为简体中文） |
| 布局 xml | `nx\resources\res\layout\layout_recording_float_view.xml` / `layout_screen_record_audio_tips.xml` / `layout_hint_window.xml` / `item_flash_light.xml` / `dialog_flash_light_sos.xml` / `layout_switch_flashlight.xml`；`layout-xhdpi\activity_flash_light.xml` |
| 蓝牙映射 | `com\xtc\ultraframework\bluetooth\BluetoothDeviceEx.java`；使用点 `qs\tiles\VolumeFuncSetting.java:561-566`、`xtc\statusbar\b\b.java:262-281`、`headset\HeadsetConnectManager.java:214-228`；状态 `statusbar\policy\d.java:46,196-244` |
| BLE 死代码 | 全包 grep `com.xtc.client` 无 UI 侧调用；`baseservice\c.a`(BleClientManager) 无外部调用；`baseservice\c`(CameraInUseMonitor) 才是录屏所用 |
