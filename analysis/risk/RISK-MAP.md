# XTCSystemUI 复刻工程 —— 云控 / 上报 / 权限检测 风险点总表（RISK-MAP）

> 目标包：`com.android.systemui`（XTC 小天才儿童手表 SystemUI）
> versionName `1.8.00.Develop_AW_XtcSystemUI_J168_Gc23339b`；Android 8.1；platform 签名；`sharedUserId=android.uid.system`；`persistent=true`。
> 分析方式：只读逆向。**未修改任何 `nx\`、`work\`、`rebuild\` 下的文件**，本文档与 ENDPOINTS/MODULES/IMPACT 均只写在 `analysis\risk\`。

## 0. 证据来源与版本口径（重要）

| 项 | 值 |
|---|---|
| 代码根（本文档全部 `文件:行号` 均相对此根） | `D:\DEBUG\apk\sysui\work\jadx17\sources\`（简写 `<SRC>`） |
| 反编译来源 | `work\ref17\classes.dex / classes2.dex`（jadx 头注释：`JADX INFO: loaded from: ...\work\ref17\classes.dex`；5136 个 .java，与目标 1.8.00 同源、差异极小） |
| 资源 / Manifest 根 | `D:\DEBUG\apk\sysui\nx\resources\`（简写 `<RES>`；`AndroidManifest.xml` 为明文） |
| `work\jadx18\sources` 的实际状态 | 已存在（5144 .java，来自今日 `work\dex\classes.dex`），但**大量方法带 `JADX ERROR: Failed to decode insn: UNKNOWN(0x20E9/0x10E9…)`**（ART 快速化指令未还原），类体不可读。本文档仅在**常量层面**用它交叉核对（见各条“交叉核对”），关键类以 `jadx17` 为准。**给 Lead 的提示：`jadx18` 要等 `work\dex` 完成 unquicken 后才可用于复查。** |
| 混淆说明 | jadx17 中类名/字段名保留了大量原义（如 `com.xtc.bigdata.collector.BehaviorCollector`），仅少数字段被重命名为 `a/b/f442a`；本文档一律给「全类名 + 文件:行号 + 方法签名/常量」，避免只写混淆标识符 |

证据强度标记：**A** = 方法体可读，API/常量可见；**B** = 方法体部分不可读，但字符串常量/导入/注解可读；**C** = 仅类名+调用点可读（jadx 解码失败）。

## 1. 结论摘要

| 指标 | 数值 |
|---|---|
| 风险点主条目总数（R-01…R-86） | **86** |
| 其中 R-78 为聚合条目，覆盖 **25 个埋点调用点**（若按证据行拆分 = 111 条） | — |
| 云控·远程配置 | 27 |
| 埋点上报 | 19 |
| 崩溃上报 | 6 |
| 日志上报/日志落盘 | 5 |
| 设备信息采集 | 4 |
| 权限检测·安全校验（含 JNI 安全 SDK） | 12 |
| 热修复·自优化（odex） | 1 |
| 自更新 | 0（本包无自更新代码，见 §2.11 负向结论） |
| 后台任务（Timer/线程/广播） | 2 |
| 其他（Provider 暴露面、代码覆盖率、本地 UI/设置） | 10 |
| 判定=必须移除 | **50** |
| 判定=降级本地 | 19 |
| 判定=保留无害（须保留业务） | 17 |

一句话结论：**本包的云控/上报并不直接联网发送**，而是“收集→写入 `content://com.xtc.i3launcher.provider/…`，由 launcher 落库并上传”。因此 SystemUI 侧移除是**低风险、可彻底断链**的；真正会“自说自话”联网的只有两处：① 域名/路由配置下发（`/route-service/route/getRouteInfo`）；② 腾讯 HTTP-DNS 查询（`http://119.29.29.98/d`）。这两处都可以安全降级为系统 DNS / 使用内置域名。

---

## 2. 风险点总表

> 风险类型：`云控`=远程配置/远程开关；`埋点`=行为/事件上报；`崩溃`=crash 上报；`日志`=日志落盘/上报；`设备`=设备信息采集；`权限`=权限检测/越权检测/签名校验；`热修`=odex 自优化/热修类；`自更新`；`后台`=后台任务；`其他`。

### 2.1 初始化编排（云控总开关）

| 编号 | 模块 | 类全名（jadx 混淆名） | 文件:行号 | 证据（API/常量/签名） | 风险类型 | 处置判定 | 移除方案 |
|---|---|---|---|---|---|---|---|
| R-01 | 云控总入口 | `com.android.systemui.c`（BigDataInitHelper，混淆名 `c`） | `<SRC>\com\android\systemui\c.java:67-87`（`public synchronized void b()`） | `CrashHandler.setCrashListener(...)`；`BehaviorUtil.init(new BehaviorCollector.Builder(this.f462a).setHostAppId("com.xtc.i3launcher").setDeviceType("watch").openActivityDurationTrack(true).setAutoCollectEvent(false).setDebugMode(false).build())` | 云控/埋点总开关 | **必须移除** | 复刻版不实现该类；删除 `SystemUIApplication.f()` 的 3 行调用（见 R-04），其余调用点全部消失 |
| R-02 | 云控延迟启动 | 同上 `c.m290a()` | `<SRC>\com\android\systemui\c.java:49-65` | `new Timer()` + `SystemClock.elapsedRealtime() > 120000` 判断，否则 `schedule(...,120000L)`：开机 2 分钟后静默启动 bigdata | 后台 | **必须移除** | 随 R-01 删类，不必单独处理 |
| R-03 | 崩溃监听注入 | 同上 `c.b()` 内匿名 `CrashListener` | `<SRC>\com\android\systemui\c.java:75-80` | `onUncaughtException(...) → BehaviorCollector.getInstance().onCrash(th)` | 崩溃 | **必须移除** | 不注册 `Thread.setDefaultUncaughtExceptionHandler`（见 R-22） |
| R-04 | Application 挂载点 | `com.android.systemui.SystemUIApplication` | `<SRC>\com\android\systemui\SystemUIApplication.java:84-94`（`a()`）、`:91`（`JacocoUtils.getInstance().init(BuildConfig.h, BuildConfig.g)`）、`:100-104`（`f()` → `c.a().a(this); c.m290a()`） | `SystemUIApplication.a()` 同时初始化 `com.xtc.baseservice.util.b`、`com.github.moduth.blockcanary`、`JacocoUtils`、`TypedValueCompat` | 云控/埋点/代码覆盖率 | **必须移除**（仅删 4 类初始化，其余保留） | 删 `:91` Jacoco 初始化、删 `f()`/`c.java` 调用、删 `com.github.moduth.blockcanary.a.a(this,new b())`（`:92`，BlockCanary 卡顿采集，见 R-20/R-21）；`com.xtc.baseservice.util.b.a(this)`（`:87`）为 baseservice 本地初始化，保留（见 IMPACT M-1） |

### 2.2 行为采集库 `com.xtc.bigdata.*`（collector）

| 编号 | 模块 | 类全名（jadx 混淆名） | 文件:行号 | 证据 | 风险类型 | 处置判定 | 移除方案 |
|---|---|---|---|---|---|---|---|
| R-05 | 采集器 Builder | `com.xtc.bigdata.collector.BehaviorCollector` | `<SRC>\com\xtc\bigdata\collector\BehaviorCollector.java:66-83`（`Builder(Application)`、`initMMKV`）、`:140-161`（`build()` → `InitManager.init()`） | `initMMKV()` 用 `com.xtc.utils.storage.l` 把 MMKV 目录指到 `getFilesDir()+"/mmkv"`；`login` 等 20+ Builder 方法 | 埋点/设备 | **必须移除** | 不实现该类；`BehaviorUtil.init(...)` 删除后无人构造 |
| R-06 | 采集器主体 | 同上 `BehaviorCollector` | `<SRC>\com\xtc\bigdata\collector\BehaviorCollector.java:42-57`（单例/`getBehaviorVersion`/`initAnrMonitor`/`initMemoryMonitor`）、`:161`（`InitManager.init()`）、`:358`（`CrashHandler.getInstance().handleExec(th)`） | `ICollector` 实现；`onCrash`、`pageBegin/pageEnd`、`clickEvent` 等 | 埋点 | **必须移除** | 不实现；若复刻版保留 `BehaviorUtil` 门面，则其内部一律空实现 |
| R-07 | 采集调度 | `com.xtc.bigdata.collector.CollectionManager` | `<SRC>\com\xtc\bigdata\collector\CollectionManager.java:155-170`（`event/insert`） | `insert(IEvent) → QueueUtils.getInstance().add(iEvent)`；`pageEnd` 生成 `PageEvent`（`:102-119`） | 埋点 | **必须移除** | 不实现 |
| R-08 | 跨进程落库 | `com.xtc.bigdata.collector.ShareHelper` | `<SRC>\com\xtc\bigdata\collector\ShareHelper.java:33-45`、`:56-73`、`:75-111`、`:132-152`、`:173-190`、`:192-232` | `context.getContentResolver().acquireUnstableContentProviderClient(uri)` + `insert/bulkInsert/delete/getType`；`appLaunchNotify()`、`appExitNotify()`、`pressHomeKeyNotify()`、`reportData(String)` | 埋点（跨应用 Provider 写） | **必须移除** | 不实现；调用方（CrashHandler/BehaviorUtil/AppLifeCycle）一并删 |
| R-09 | 上报 URI 生成 | `com.xtc.bigdata.common.utils.UriUtils` | `<SRC>\com\xtc\bigdata\common\utils\UriUtils.java:11-17`、`:19-45` | `getAuthority() = Constants.HOST_APP_ID + ".provider"`（HOST_APP_ID 由 `setHostAppId("com.xtc.i3launcher")` 注入 = `com.xtc.i3launcher.provider`）；`content://<authority>/<Columns.DIR_PATH><suffix>` | 埋点（跨应用 Provider） | **必须移除** | 不实现 |
| R-10 | 采集队列线程 | `com.xtc.bigdata.collector.utils.QueueUtils` | `<SRC>\com\xtc\bigdata\collector\utils\QueueUtils.java:38-42`、`:52-56` | `new HandlerThread("bigdata_task", 10)`；`LinkedBlockingQueue<IEvent>` | 后台 | **必须移除** | 不实现（避免后台线程常驻） |
| R-11 | 生命周期埋点 | `com.xtc.bigdata.collector.AppLifeCycleCallback` | `<SRC>\com\xtc\bigdata\collector\AppLifeCycleCallback.java:24-62`、`:64-79` | `registerActivityLifecycleCallbacks` 语义：`onActivityResumed → pageBegin(name)`、`onActivityPaused → pageEnd(name)`、`onActivityDestroyed → ShareHelper.appExitNotify()` | 埋点 | **必须移除** | 不注册 `Application.ActivityLifecycleCallbacks` |
| R-12 | 事件封装 | `com.xtc.common.bigdata.BbkBehavior` | `<SRC>\com\xtc\common\bigdata\BbkBehavior.java:60-149` | `clickEvent/countEvent/searchEvent/customEvent/customEventByCustomTime/userSingIn` → `BehaviorCollector.getInstance().xxxEvent(...)` | 埋点 | **必须移除** | 不实现；`BehaviorUtil` 若保留必须去掉 `getBbkBehavior()` 分支 |
| R-13 | 埋点门面 | `com.xtc.common.bigdata.BehaviorUtil` | `<SRC>\com\xtc\common\bigdata\BehaviorUtil.java:83-90`（`customEvent`）、`:107-111`（`init`）、`:182-183`（`uplaodSystemCrashInfo`）、`:92-93`（`followDropbox`） | **jadx17 中这些方法体为空（旧版 jadx 解码失败静默丢体）**；`jadx18` 同方法可读：`changePerfMonitorInterval → PerfMonitor.setCollectInterval(r0)` | 埋点门面 | **必须移除/空实现** | 复刻版保留类名与静态签名（避免全仓 25 处调用点编译报错），**方法体全部空实现**；或直接删除类并同步删除 25 处调用（推荐：调用点多为 UI 代码，删除更干净） |
| R-14 | Session 保活 | `com.xtc.bigdata.collector.SessionAgent` | `<SRC>\com\xtc\bigdata\collector\SessionAgent.java:1-94` | `refreshSessionId()/clean()`；被 `AppLifeCycleCallback.java:42,76` 调用 | 埋点 | **必须移除** | 不实现 |

### 2.3 崩溃 / ANR / 性能 / 卡顿（`com.xtc.bigdata.monitor.*`）

| 编号 | 模块 | 类全名 | 文件:行号 | 证据 | 风险类型 | 处置判定 | 移除方案 |
|---|---|---|---|---|---|---|---|
| R-15 | 崩溃捕获与上报 | `com.xtc.bigdata.collector.exception.CrashHandler` | `<SRC>\com\xtc\bigdata\collector\exception\CrashHandler.java:83-87`（`registerCrashHandler` → `Thread.setDefaultUncaughtExceptionHandler(this)`）、`:235-284`（`collectCrash` 组装 `ExceptionEvent` → `ShareHelper.insert`）、`:301-323`（`handleOdexCrash`）、`:501-513`（`odexOpt`） | `CrashHandler.java:281` `ShareHelper.getInstance().insert(exceptionEvent.getContentValues())`；`:255` `LogUtil.e("CrashHandler_Print", stackTrace)`；`:503-504` `aa.m1968a("persist.sys.opt.xtc_debug", 0)`、`aa.m1970a("persist.sys.debugopt.pkgname", packageName)` | 崩溃 | **必须移除** | 不注册全局 handler；不实现该类。若需保留“崩溃不弹窗/不重启”的产品行为，用 10 行空实现替换（见 IMPACT M-2） |
| R-16 | 崩溃上报 Service | `com.xtc.bigdata.collector.exception.CrashIntentService` | `<SRC>\com\xtc\bigdata\collector\exception\CrashIntentService.java:87-94`（`onHandleIntent`）；`<RES>\AndroidManifest.xml:173-175` | Manifest 注册 `<service android:name="com.xtc.bigdata.collector.exception.CrashIntentService" android:exported="false"/>`（**无 intent-filter**）。全仓 grep `CrashIntentService` 除自身外 **0 命中**（jadx17/jadx18 均如此）→ 无 Java 侧引用/无外部可拉起；若原版 `CrashHandler` 的不可读方法体里存在 `startService`，本方案已将 `CrashHandler` 一并移除，链路一起断 | 崩溃 | **必须移除** | 删 Manifest 该 `<service>`；复刻版不实现类。**注意**：`exported=false` + 无 filter ⇒ 外部无法拉起，删除不会让系统或其它应用报「组件不存在」（我方代码是唯一潜在发起方，已一并删除） |
| R-17 | ANR 看门狗 | `com.xtc.bigdata.monitor.anr.WatchDogs` | `<SRC>\com\xtc\bigdata\monitor\anr\WatchDogs.java:17-42`、`:55-120`（HandlerChecker） | `extends Thread`；`DEFAULT_TIMEOUT=4000`；`mHandler.postAtFrontOfQueue(this)` 检主线程卡顿；配合 `ANRWatchDog/LooperMonitor/StackSampler` | 崩溃/性能 | **必须移除** | 不实现；同时删 `MonitorStartReceiver`（R-19）与 `BehaviorUtil.followDropbox` 关联 |
| R-18 | ANR 文件落盘 | `com.xtc.bigdata.monitor.anr.ANRMonitor` | `<SRC>\com\xtc\bigdata\monitor\anr\ANRMonitor.java:67`、`:86` | `new File(Environment.getExternalStorageDirectory().getPath()+"/bigData/anr/")`；`FileUtils.saveFile(...)` | 日志（外部存储落盘） | **必须移除** | 不实现 |
| R-19 | Watchdog 拉起广播 | `com.xtc.bigdata.monitor.receiver.MonitorStartReceiver` | `<SRC>\com\xtc\bigdata\monitor\receiver\MonitorStartReceiver.java:35-70`、`:217-229`；`<RES>\AndroidManifest.xml:176-183` | Manifest 两个 `<intent-filter>`：`com.xtc.watchdogs`（priority=100000）、`com.xtc.launcher.ACTION_CHANGE_REGION`；代码 `WATCHDOGS="com.xtc.watchdogs"`、`WatchDogs.getInstance(i).start()` | 崩溃/后台 | **必须移除** | 删 Manifest `<receiver>`；**注意**：`com.xtc.watchdogs` 由 launcher 广播，缺失 receiver 不会使系统报错（广播无接收者），但会导致 launcher 侧“健康检查”失去对该进程的监测，属预期效果 |
| R-20 | 性能采集 | `com.xtc.bigdata.monitor.performance.PerfMonitor` | `<SRC>\com\xtc\bigdata\monitor\performance\PerfMonitor.java:23`（`collectInterval=20000`）、`:32-55`（normal/screenOn/screenOff Runnable）、`:65-92`（`startLooper`）、`:94-112`（`collectScreenOn/Off`） | `HandlerThread("PerfMonitor")` 每 20s `collectInfo("normal")`，上报 `CustomEvent`（CPU/内存/FPS） | 性能/埋点 | **必须移除** | 不实现；`BehaviorUtil.startPerfMonitorLooper/quitPerfMonitor` 不实现 |
| R-21 | FPS / 卡顿 | `com.xtc.bigdata.monitor.frame.FPSFrameCallback`、`com.github.moduth.blockcanary.*` | `<SRC>\com\xtc\bigdata\monitor\frame\FPSFrameCallback.java:1-66`；`<SRC>\com\android\systemui\SystemUIApplication.java:92` | `SystemUIApplication.a()` 中 `com.github.moduth.blockcanary.a.a(this, new b()).m645a()` | 性能/日志 | **必须移除** | 删 `SystemUIApplication.java:92` 一行；不实现 BlockCanary 初始化入口 |
| R-22 | Dropbox 扫描 | `com.xtc.bigdata.monitor.crash.DropboxScanner` | `<SRC>\com\xtc\bigdata\monitor\crash\DropboxScanner.java:147`、`:168-192` | `intentFilter.addAction("android.intent.action.DROPBOX_ENTRY_ADDED")`（另加 `testAction`）；`DATA_APP_CRASH/DATA_APP_ANR/SYSTEM_APP_CRASH/SYSTEM_APP_ANR` 四类标签，取 `tag/time` 后上报 | 崩溃 | **必须移除** | 不实现；`BehaviorUtil.followDropbox/unfollowDropbox` 空实现 |
| R-23 | 崩溃去重缓存 | `com.xtc.bigdata.collector.exception.CrashInfo` / `OdexInfo` / `OdexConfig` | `<SRC>\com\xtc\bigdata\collector\exception\OdexInfo.java:40-42`；`<SRC>\com\xtc\bigdata\collector\config\OdexConfig.java:25-31` | `{"OdexInfo":{...}}` JSON；`OdexConfig.verify()` 要求 interval≥4/triggerCount≥5 | 热修 | **必须移除** | 不实现 |
| R-24 | 覆盖率生成 | `com.xtc.xtcoco.JacocoUtils` | `<SRC>\com\xtc\xtcoco\JacocoUtils.java:46-72`（`generateEcFile`）、`:114-121`（`getPath`） | `Class.forName("org.jacoco.agent.rt.RT").getMethod("getAgent")...getExecutionData(false)` 反射；落盘 `Environment.getExternalStorageDirectory()+"/ec/"+pkg+"/"+gitSHA+"_"+baseline+"/coverage-<ts>.exec"` | 其他（代码覆盖率/源码指纹） | **必须移除** | 删 `SystemUIApplication.java:91` 初始化 + `CodeCoverageReceiver`（R-25）；不实现类 |
| R-25 | 覆盖率广播 | `com.xtc.xtcoco.CodeCoverageReceiver` | `<SRC>\com\xtc\xtcoco\CodeCoverageReceiver.java:19`；`<RES>\AndroidManifest.xml:154-160` | Manifest：`exported=true`，`<action android:name="com.xtc.report.generate.ec"/>`；代码 `JacocoUtils.getInstance().generateEcFile(context)` | 其他（可被任意应用触发写 SD 卡） | **必须移除** | 删 Manifest `<receiver>`。**这是 exported 广播，任何第三方应用可发 `com.xtc.report.generate.ec` 触发外部存储写入**，属实际攻击面 |
| R-26 | 设备信息采集 | `com.xtc.bigdata.common.utils.DeviceUtils` | `<SRC>\com\xtc\bigdata\common\utils\DeviceUtils.java:36-44`（`getIMEI`）、`:46-48`（`ro.product.innermodel`）、`:60-93`（MAC，含 `NetworkInterface.getByName("wlan0")`）、`:154-160`（`getMachineId` → bbkSn/IMEI/MAC） | `TelephonyManager.getDeviceId()`、`WifiInfo.getMacAddress()`、`Build.MODEL/MANUFACTURER`、`/proc/meminfo` | 设备指纹 | **必须移除** | 不实现该类；若隐私弹窗需展示机型，改用 `Build.MODEL` 本地值（见 IMPACT M-4） |
| R-27 | 机型属性上报 | `com.xtc.bigdata.collector.encapsulation.entity.attr.MachineAttr` | `<SRC>\com\xtc\bigdata\collector\encapsulation\entity\attr\MachineAttr.java:8-24` | 字段 `mId/devName/innerModel/osVer/brand/rooted`，`ContentValues.put(Columns.COLUMN_ROOTED, ...)` | 设备指纹 | **必须移除** | 不实现 |
| R-28 | 通用属性 | `com.xtc.bigdata.collector.encapsulation.entity.BaseAttr` / `BaseAttrManager` | `<SRC>\com\xtc\bigdata\collector\encapsulation\entity\BaseAttr.java:1-824`、`<SRC>\com\xtc\bigdata\collector\encapsulation\BaseAttrManager.java:1-311` | `genBaseAttr()` 组装机器/用户/应用属性；`BaseAttrInfo.txt`（`DeviceInfo.java:5`） | 设备指纹 | **必须移除** | 不实现 |
| R-29 | 用户属性 | `com.xtc.bigdata.collector.encapsulation.entity.attr.UserAttr` | `<SRC>\com\xtc\bigdata\collector\encapsulation\entity\attr\UserAttr.java:1-266`（调用点 `BbkBehavior.java:145-149`） | `initUserInfo(UserAttr)`：绑定号/openId/watchId 等 | 设备指纹 | **必须移除** | 不实现 |

### 2.4 网络层：域名体系 / 路由下发 / 拦截器（自联网的两处之一）

| 编号 | 模块 | 类全名 | 文件:行号 | 证据 | 风险类型 | 处置判定 | 移除方案 |
|---|---|---|---|---|---|---|---|
| R-30 | 域名硬编码表 | `com.xtc.domain.Domain` | `<SRC>\com\xtc\domain\Domain.java:5-35` | 31 个枚举项，含 `watch.okii.com`、`api.watch.okii.com`、`route.okii.com`、`watchda.eebbk.net`、`api-watch.tiancaixing.com`、`119.29.*`（见 ENDPOINTS） | 云控（域名） | **降级本地** | 不实现“域名下发”，把用到的 2~3 个域名写成 `BuildConfig` 常量（或干脆不联网） |
| R-31 | 路由配置拉取 | `com.xtc.domain.b`（DomainManager） | `<SRC>\com\xtc\domain\b.java:90-111`（`m1547a()`） | `HttpManager.getInstance(ctx).getHttpClient().request(BaseUrlManager.getRouteUrl(ctx), HttpConfigApi.class)).getRouteInfo(dVar)`，成功后 `b.this.a(eVar)` 解析机房/域名映射 | 云控（远程配置下发） | **必须移除** | 不调用 `m1547a()`；`BaseUrlManager`/`Domain` 只保留本地常量 |
| R-32 | 路由 API 定义 | `com.xtc.domain.config.http.HttpConfigApi` | `<SRC>\com\xtc\domain\config\http\HttpConfigApi.java:11-13` | `@NoNeedToken @POST("/route-service/route/getRouteInfo") i<NetBaseResult<e>> getRouteInfo(@Body d dVar)` | 云控 | **必须移除** | 不实现该 Retrofit 接口（**本包仅 2 个 Retrofit 接口之一**） |
| R-33 | 机房切换重启 | `com.xtc.domain.config.http.c` | `<SRC>\com\xtc\domain\config\http\c.java:97-106` | `if (pkg.equals("com.xtc.i3launcher") && !TextUtils.equals(old,new)) powerManager.reboot("reboot in datacenter change by launcher")` | 云控（**可远程重启整机**） | **必须移除** | 不实现；该逻辑只对 launcher 包名生效，SystemUI 上为死分支，但类仍随 httplib 打包，建议整体删 |
| R-34 | 网络管理器 | `com.xtc.httplib.HttpManager` | `<SRC>\com\xtc\httplib\HttpManager.java:34`、`:58-66`、`:242-274`、`:309-311` | `CONTENT_WATCHID_TYPE="content://com.xtc.provider/BaseDataProvider/watchId/1"`；注册 `com.xtc.initservice.action.NET_PARAM_UPDATE` 广播更新 RSA key；`getWatchIdByProvider()` 读设备 watchId | 云控/设备 | **必须移除**（本包仅路由配置用） | 不实现；`BaseUrlManager` 保留纯字符串拼接即可 |
| R-35 | OkHttp 客户端与拦截器栈 | `com.xtc.httplib.okhttp.DefaultOkHttpClient` | `<SRC>\com\xtc\httplib\okhttp\DefaultOkHttpClient.java:146-169` | 依次挂载 `LogInterceptor`、`ShutdownLocationInterceptor`、`TimeoutInterceptor`、`MonitorInterceptor`、`PreprocessorInterceptor`、`HttpRequestInterceptor`、`ConfUpdateInterceptor`、`IMRefreshDomainInterceptor`(仅 launcher)、`HttpResponseInterceptor`、`ImBridgeInterceptor`、`RetryInterceptor`；`:132-134` `initDns()` 注入自研 `DnsStrategy` | 云控/上报 | **必须移除** | 复刻版不初始化该 client；**注意**：一旦删干净，R-31/R-36/R-37 全部失效 |
| R-36 | 流量监控·伪造响应 | `com.xtc.httplib.okhttp.MonitorInterceptor` | `<SRC>\com\xtc\httplib\okhttp\MonitorInterceptor.java:87-98`（命中限制时 `HttpUtil.createErrorResponse(...)` **直接伪造错误响应返回给调用方**）、`:120-122`（单请求 10KB 阈值）、`:264-285`（日流量 500MB 阈值）、`:293`（上报 `Http_Monitor_Total_Traffic_Over`）、`:305-309` | `openMonitor=true`；`NECESSARY_BOOT_URLS`；`ContextCompat`… `BehaviorUtil.customEvent(this.context,"Http_Monitor_Total_Traffic_Over",…)` | 上报/云控 | **必须移除** | 不实现；风险点：该拦截器会**凭本地阈值拒绝正常请求**，复刻版若保留其它联网须一并去掉 |
| R-37 | IM 通道转发 | `com.xtc.httplib.okhttp.PreprocessorInterceptor` | `<SRC>\com\xtc\httplib\okhttp\PreprocessorInterceptor.java:103-128`、`:160-182`、`:250-257` | 把 HTTP 请求经 `com.xtc.im.b.c.a(url, method, header, body, listener)` **转成 IM 长连接透传**；`AlarmManager.setExact(2, elapsed+21000, pendingIntent)` 做超时 | 云控/上报（旁路通道） | **必须移除** | 不实现 |
| R-38 | 全量请求日志 | `com.xtc.httplib.okhttp.LogInterceptor` | `<SRC>\com\xtc\httplib\okhttp\LogInterceptor.java:51-60`、`:96-120` | `UUID.randomUUID()` 生成请求 id，构造 `HttpRequestEvent`，记录 url/header/body（`MAX_PRINT_BODY_LENGTH=1000`） | 日志/埋点 | **降级本地** | 若复刻版仍用 okhttp，换回 AOSP 自带 client 或空拦截器 |
| R-39 | 请求头加密 | `com.xtc.httplib.okhttp.HttpRequestInterceptor` / `HttpResponseInterceptor` / `ImBridgeInterceptor` / `RetryInterceptor` / `ConfUpdateInterceptor` / `ShutdownLocationInterceptor` / `TimeoutInterceptor` | `<SRC>\com\xtc\httplib\okhttp\HttpRequestInterceptor.java:1-179`、`HttpResponseInterceptor.java:1-65`、`ImBridgeInterceptor.java:1-161`、`RetryInterceptor.java:1-66`、`ConfUpdateInterceptor.java:1-58` | 注入 `Base-Request-Param`/`Eebbk-Key`/`Eebbk-Sign`/`encrypted` 头（`com\xtc\httplib\ConfigOptions.java:13-16`）；`ConfUpdateInterceptor` 处理配置更新响应 | 云控/安全 | **降级本地** | 不实现（复刻版无服务端，整套 httplib 可整体裁掉） |
| R-40 | 大数据 Provider 客户端 | `com.xtc.httplib.bigdata.BigdataClientManager` | `<SRC>\com\xtc\httplib\bigdata\BigdataClientManager.java:13`、`:26-28`、`:53-58`、`:73-79` | `AUTHORITY="com.xtc.http.bigdata.provider.HttpBigdataProvider"`；`jadx18` 交叉核对可读常量：`content://com.xtc.http.bigdata.provider.HttpBigdataProvider/query` 与 `/insert`；`bury()/collectRawData()/querySwitchOpen()` | 埋点 | **必须移除** | 不实现 |
| R-41 | 网络状态开关 | `com.xtc.httplib.netstate.SwitchNetworkManager` / `NetStateDataManager` | `<SRC>\com\xtc\httplib\netstate\SwitchNetworkManager.java:29`、`:49-51`、`:101`；`NetStateDataManager.java:1-157` | `setModuleSwitch(boolean, SwitchConfigBean)`（**服务端下发网络调度开关**）；`moduleSwitch` 为 false 时降级 | 云控 | **必须移除** | 不实现 |
| R-42 | 配置热更新 | `com.xtc.httplib.confupdate.ConfUpdatesInfoManager` | `<SRC>\com\xtc\httplib\confupdate\ConfUpdatesInfoManager.java:1-148` | 服务端返回配置后落盘并按周期刷新（与 `ConfUpdateInterceptor` 配合） | 云控/热更新 | **必须移除** | 不实现 |

### 2.5 HTTP-DNS（自联网的第二处）

| 编号 | 模块 | 类全名 | 文件:行号 | 证据 | 风险类型 | 处置判定 | 移除方案 |
|---|---|---|---|---|---|---|---|
| R-43 | HTTPDNS Provider | `com.xtc.dns.provider.HttpDnsContentProvider` | `<SRC>\com\xtc\dns\provider\HttpDnsContentProvider.java:12`、`:110-126`；`<RES>\AndroidManifest.xml:164-172` | `<provider android:name="com.xtc.dns.provider.HttpDnsContentProvider" android:readPermission="com.android.systemui.permission.READ_DNS" android:exported="true" android:authorities="com.android.systemui.com.xtc.httpdns.provider">`，meta-data `allowLaunchOnPowerSaveMode=true` | 云控（DNS 旁路）/Provider 暴露面 | **降级本地** | 复刻版：删除该 Provider（系统 DNS 兜底）或保留空 Provider 返回 `null`（见 IMPACT I-3）。**必须保留 authority 名不变**，否则 `DnsStrategy` 会误判 provider 不存在 |
| R-44 | DNS 策略注入 | `com.xtc.dns.api.a`（DnsStrategy） | `<SRC>\com\xtc\dns\api\a.java:21`、`:43-52`（`connectFailed` 回调反查 DNS）、`:77-118`（`lookup`：先自研 HTTP-DNS，失败回落 `Dns.SYSTEM.lookup`） | `implements okhttp3.Dns, EventListener.Factory`；硬编码 `"com.xtc.i3launcher"` 包名（`:29`、`:74`） | 云控/网络 | **降级本地** | 复刻版 OkHttp 不 `builder.dns(...)`，直接用默认 `Dns.SYSTEM` |
| R-45 | 腾讯 HTTP-DNS 请求 | `com.xtc.dns.b.a.c`（TencentHttpDnsProvider） | `<SRC>\com\xtc\dns\b\a\c.java:24-25`、`:63-69` | `new Uri.Builder().scheme("http").authority(a()).path("d").appendQueryParameter("dn",…).appendQueryParameter("id", "196").appendQueryParameter("ttl","1")`；硬编码明文 **http** | 云控/DNS 劫持面 | **必须移除** | 不实现；`DnsConfig.f2504a="119.29.29.98"`（`<SRC>\com\xtc\dns\b\a.java:19`）一并去掉 |
| R-46 | DNS 配置落盘 | `com.xtc.dns.b.a`（DnsConfig）/ `com.xtc.dns.d.b`（Tools） | `<SRC>\com\xtc\dns\b\a.java:57-115`、`<SRC>\com\xtc\dns\d\b.java:13` | 读写 `/mnt/sdcard/xtc/ibwatch/common/httpdnsinfo.properties`（键 `enableHttpDns/httpDnsIp/cacheTime/dnsTtl`）；**外部存储可写的配置注入点** | 云控/安全 | **必须移除** | 不实现（任何能写 sdcard 的应用都能改 DNS 服务器 IP，属实际攻击面） |
| R-47 | DNS 埋点 | `com.xtc.dns.a.a`（DnsBeh） | `<SRC>\com\xtc\dns\a\a.java:13`、`:20-28`、`:30-36` | `BehaviorUtil.customEvent(context, "apm_http_dns_req", "", null, map)`，字段 `success/host/ips/time_consumed/source` | 埋点 | **必须移除** | 不实现；`DnsBeh` 两个重载直接删 |
| R-48 | DNS Provider 查询 | `com.xtc.dns.client.b.b`（DnsResolver） | `<SRC>\com\xtc\dns\client\b\b.java:34-48`、`:58-90` | `contentResolver.getType(content://<pkg>.com.xtc.httpdns.provider/checkIpExpire/…)`、`query(content://…/DomainModel/<host>)` 读列 `ip`；`InetAddressValidator` 判 IP | 云控（跨进程） | **降级本地** | 不实现；`com.xtc.dns.provider.a` 的 authority 模板（`:6` `"%s.com.xtc.httpdns.provider"`）保留仅作文档 |
| R-49 | DNS 客户端管理 | `com.xtc.dns.client.b`（HttpDnsManager） | `<SRC>\com\xtc\dns\client\b.java:31-49`、`:52-62` | 通过 `PackageManager.resolveContentProvider("<pkg>.com.xtc.httpdns.provider",0)` 探测 provider，命中即走 HTTP-DNS，否则回落本包 | 云控 | **降级本地** | 不实现 |
| R-50 | 第三处 Retrofit | `com.android.common.library.IWatchQuickChargeHttp` | `<SRC>\com\android\common\library\IWatchQuickChargeHttp.java:9` | `@POST("/quickCharge/watchQuickCharge")`（快充相关，本包内未找到调用点，属随库打包） | 其他 | **降级本地** | 不实现（如快充 UI 需要，改为读本地电池状态） |

### 2.6 JNI / 加密 / 签名与安全校验

| 编号 | 模块 | 类全名 | 文件:行号 | 证据 | 风险类型 | 处置判定 | 移除方案 |
|---|---|---|---|---|---|---|---|
| R-51 | native 库加载（安全） | `com.xtc.utils.security.XtcSecurity` | `<SRC>\com\xtc\utils\security\XtcSecurity.java:11-15`（3 个 native）、`:18`（`System.loadLibrary("xtcSecurity")`） | `getDefaultRsaKey()`、`secretKeyDecrypt(String)`、`secretKeyEncrypt(String)` | 安全 SDK | **降级本地** | 复刻版：**不加载** `libxtcSecurity.so`，方法返回 `null`/原串；但 `nx\resources\lib\armeabi\libxtcSecurity.so` 建议保留文件（若其它系统应用按路径 dlopen，见 IMPACT I-5） |
| R-52 | 人脸图加解密 | `com.xtc.utils.security.FaceImageEncrypt` | `<SRC>\com\xtc\utils\security\FaceImageEncrypt.java:7-12` | `native byte[] encrypt(byte[])`、`decrypt(byte[])`、`loadLibrary("xtcSecurity")` | 安全 SDK | **降级本地** | 不实现（本包无调用点；属随库打包） |
| R-53 | KV 存储 JNI | `com.tencent.mmkv.MMKV` / `MMKVContentProvider` | `<SRC>\com\tencent\mmkv\MMKV.java:203`（`System.loadLibrary("mmkv")`）；`<SRC>\com\tencent\mmkv\MMKVContentProvider.java:40`、`:82` | 47 个 native 方法（`encodeString/decodeString/getMMKVWithID…`）；`MMKVContentProvider` 暴露 `content://<authority>` | 其他（存储） | **保留无害**（仅删上报相关使用） | 保留 MMKV 本体（业务设置项在用：`com.xtc.utils.storage.l`）；**只删** bigdata 侧 `initMMKV` 调用（R-05） |
| R-54 | 签名校验（跨进程） | `com.xtc.ultraframework.security.AppSignatureManager` | `<SRC>\com\xtc\ultraframework\security\AppSignatureManager.java:11-55` | `ServiceManager.getService("XTCUtilsService")` → `IXTCUtilsManager.verifyInstalledAppSignature(pkg)` / `verifyApkFileSignature(path)` | 权限/安全校验 | **保留无害**（框架能力，本包内无调用点） | 保留类即可；**若复刻版要极致裁剪**可整类删除（无调用点，删了不影响编译） |
| R-55 | 安全密钥管理 | `com.xtc.ultraframework.security.SecurityKeyManager` | `<SRC>\com\xtc\ultraframework\security\SecurityKeyManager.java:15-34`、`:45-121` | 构造时校验 `context.getPackageName()` 必须是 `com.xtc.i3launcher`/`com.xtc.datacenter`，否则抛 `UnsupportedException`；`getSecurityKey/setSecurityKey/setChipId/setPermissionPolicy` | 权限/安全校验 | **保留无害**（SystemUI 上必抛异常，等于死代码） | 保留；无需改动 |
| R-56 | “安全自检” | `com.xtc.core.se2.c`（SecureCheck） | `<SRC>\com\xtc\core\se2\c.java:7-9`；调用点 `<SRC>\com\android\systemui\SystemUIApplication.java:48` | `public static void a(Context context) { }` —— **方法体为空**（jadx17/jadx18 一致） | 其他（安全校验占位） | **保留无害** | 保留空调用即可（删了也无影响，但保留更贴近原版） |
| R-57 | XTC 权限框架 | `com.xtc.ultraframework.xtcauth.XtcAuthManagerEx` | `<SRC>\com\xtc\ultraframework\xtcauth\XtcAuthManagerEx.java:93-133`、`:76-91` | `context.getSystemService("xtcauth")` → `IMobileManager`；`setPermissionRecord/setSelfstartWhiteList/initPackagePermission`；30 个 `PERM_*` 码 | 权限检测 | **保留无害**（系统服务代理，本包仅 UI 使用） | 保留 |
| R-58 | 私钥错误上报 | `com.xtc.httplib.bigdata.RsaKeyBigData` | `<SRC>\com\xtc\httplib\bigdata\RsaKeyBigData.java:22-27` | `BehaviorUtil.customEvent(context,"account_auth_secret_key_error", map)`；类型常量 `DB_KEY_INVALID=5/LOCAL_DECRYPT=3/RSA_ENCRYPT=2/SERVER_CODE=1/SYSTEM_KEY_NULL=4` | 埋点/安全 | **必须移除** | 不实现（调用点无，随 httplib 裁掉） |

### 2.7 权限检测界面 / 隐私协议 / 自启动

| 编号 | 模块 | 类全名 | 文件:行号 | 证据 | 风险类型 | 处置判定 | 移除方案 |
|---|---|---|---|---|---|---|---|
| R-59 | 权限列表页 | `com.xtc.architecture.mvp.PermissionListActivity` | `<SRC>\com\xtc\architecture\mvp\PermissionListActivity.java:13`、`:147-153`（`onCreate`）；`<RES>\AndroidManifest.xml:161-163` | Manifest `<activity android:name="com.xtc.architecture.mvp.PermissionListActivity" android:launchMode="singleTask"/>`；打开后展示 `CtaPermissionView` 并回调 `ICtaPermissionCallback.allow()/refuse()` | 权限检测 | **降级本地**（保留组件名，内容本地化） | 复刻版保留 Activity 与类名（可能被 launcher/设置页按类名拉起），`onCreate` 直接 `finish()`；见 IMPACT I-6 |
| R-60 | 权限申请工具 | `com.xtc.architecture.mvp.b.a`（PermissionUtil） | `<SRC>\com\xtc\architecture\mvp\b\a.java:56-62`、`:42-54` | `new Intent(context, PermissionListActivity.class)` + `EXTRA_TIP/EXTRA_INTENT/EXTRA_CTA_TITLE`；`ActivityCompat.requestPermissions(...)` | 权限检测 | **降级本地** | 保留（系统运行时权限申请是合法必需）；只把跳转 `PermissionListActivity` 改为直接 `requestPermissions` |
| R-61 | 权限文案/隐私元数据读取 | `com.xtc.ui.widget.util.CtaPermissionUtil` | `<SRC>\com\xtc\ui\widget\util\CtaPermissionUtil.java:26-45`、`:76-113`、`:119-150` | 读目标应用 `requestedPermissions` 与 meta-data：`privacy_url`/`agreement_url`/`disclaimer_url`/`required_permissions`；`PACKAGE_WEICHAT/PACKAGE_MOMENT/PACKAGE_MESSAGE` 特判 | 权限检测（读全机已装应用权限） | **降级本地** | 保留（隐私弹窗必需）；**不联网、不上报**，只是本机读取，无需移除 |
| R-62 | 自启动引导页 | `com.xtc.ui.widget.selfstart.SelfStartActivity` | `<SRC>\com\xtc\ui\widget\selfstart\SelfStartActivity.java:13`、`:35-60`；`<RES>\AndroidManifest.xml:187` | Manifest `<activity android:name="com.xtc.ui.widget.selfstart.SelfStartActivity"/>`（无 exported）；点击“去设置”发 `SelfStartConstant.ACTION_SETTINGS_SELF_START` | 权限检测（自启动白名单） | **保留无害**（业务必需） | 保留 |
| R-63 | 自启动白名单查询 | `com.xtc.ui.widget.selfstart.SelfStartUtils` | `<SRC>\com\xtc\ui\widget\selfstart\SelfStartUtils.java:79-127`、`:130-138` | `acquireUnstableContentProviderClient(Uri.parse("content://com.xtc.launcher.self.start")).call("METHOD_GET_PACKAGE", pkg, null)`，读 `SELF_START_STR` 布尔；`PackageManager.resolveActivity(ACTION_SETTINGS_SELF_START)` | 权限检测（跨进程） | **保留无害** | 保留；provider 不存在时现有代码已 `return true`（视为有权限），行为安全 |
| R-64 | 隐私协议 UI | `com.xtc.ui.widget.privacy.XTCPrivacy` / `PrivacyFirstDialog` / `PrivacyBean` / `PrivacyUtilities` | `<SRC>\com\xtc\ui\widget\privacy\XTCPrivacy.java:1-301`、`PrivacyFirstDialog.java:1-238`、`PrivacyUtilities.java:33-34`、`:124-128` | 读取 `ro.product.innermodel(.ex)` 拼接隐私文案；`jointUrl()` 拼隐私协议 URL | 其他（本地 UI） | **保留无害**（业务必需） | 保留 |
| R-65 | 隐私二维码页 | `com.xtc.ui.widget.privacy.PrivacyUriActivity` | `<SRC>\com\xtc\ui\widget\privacy\PrivacyUriActivity.java:29`、`:55-78`、`:84-120`；`<RES>\AndroidManifest.xml:184-186` | `QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, …)`；取 Intent extra `"url"`/`package_name`/`EXTRA_URL_TYPE`，`PrivacyUtilities.jointUrl(...)`；文案 `R.string.privacy_url`（`<RES>\res\values\strings.xml:152` “手机扫码查看《隐私政策》”） | 其他（展示型，**本身不联网**） | **保留无害** | 保留；URL 由调用方传入（launcher 传入），复刻版可保留本页 |
| R-66 | 代理 Provider 访问校验 | `com.android.systemui.statusbar.provider.StatusBarProxyProvider` | `<SRC>\com\android\systemui\statusbar\provider\StatusBarProxyProvider.java:127-136`、`:71-125`；`<RES>\AndroidManifest.xml:133-137` | `getCallingPackage()` 必须等于 `com.xtc.i3launcher` 才允许 `call()`；支持 `isStatusBarExpanded/collapsePanel/onImStateChanged`；`/`authorities="com.xtc.systemui.statusbar.proxy"`，`permission=com.xtc.systemui.statusbar.permission.StatusBarProxyProvider` | 权限检测 | **保留无害**（业务必需：launcher 控制下拉面板） | 保留；若复刻版不接 launcher，可删 Provider + 对应 manifest 项（见 IMPACT I-7） |

### 2.8 远程配置读取 / 系统开关

| 编号 | 模块 | 类全名 | 文件:行号 | 证据 | 风险类型 | 处置判定 | 移除方案 |
|---|---|---|---|---|---|---|---|
| R-67 | 功能开关（FunSwitch） | `com.xtc.system.account.d`（FunManager） | `<SRC>\com\xtc\system\account\d.java:27-28`、`:62-84`（注册 ContentObserver）、`:100-199` | `content://com.xtc.fundata/FunSwitch`、`content://com.xtc.fundata/FunExtra/`；`ContentObserver` 监听 + `FunSwitchInitReceiver`（`:158-179`）；回调 `a/b/c(FunSwitch)` | 云控（功能开关下发） | **降级本地** | 复刻版：**保留 FunSwitch 读取接口**（业务功能开关，如省电/录屏是否可用），但**把数据源改为本地常量/本地 SharedPreferences**；`d.java` 的 `ContentObserver` 与 receiver 可保留（只读本机 provider，不联网），也可空实现 |
| R-68 | 模块开关/机型开关 | `com.xtc.system.account.h`（WatchAccountBase） | `<SRC>\com\xtc\system\account\h.java:17-20`、`:59-66`、`:134-140`、`:219-251`、`:297` | `content://com.xtc.moduledata/query`（`module=<int>` 取 `display/tips/extra`）、`content://com.xtc.provider/BaseDataProvider/{watchId,simType,countryCode,realName,…}`、`content://com.xtc.gdprdata/query`（`switchId=`） | 云控/设备 | **降级本地** | 复刻版：`queryFunSwitchByPackageName()` 返回固定默认值（`switchStatus=1`）；`queryModuleSwitchByInt()` 返回传入的 `default`；不查 `gdprdata` |
| R-69 | manifest 功能开关元数据 | `AndroidManifest.xml` | `<RES>\AndroidManifest.xml:138-146` | `moduleSwitch=[{"module":"3547","funSwitch":"com.android.systemui.screenrecord","notBind":3}]`；`funSwitch=[{"appPackage":"com.android.systemui.screenrecord","funSwitch":"1"}]`；`supportScreenRecordAudio=1` | 云控（被外部应用读取） | **保留无害**（但**不可误删**） | **保留原样**。这 3 个 meta-data 由 **launcher/设置读取**（本包代码内 grep `moduleSwitch|notBind` 零命中，见“已确认无解析代码”），删掉会导致手表端“录屏”入口消失/录屏无声音 |
| R-70 | 常驻前台元数据 | `AndroidManifest.xml` | `<RES>\AndroidManifest.xml:99-101` | `FlashLightSOSDialog` 上 `<meta-data android:name="XtcStayFront" android:value="true"/>` | 其他（框架行为） | **保留无害**（**不可误删**） | **保留**。本包 Java 代码 grep `XtcStayFront` **0 命中** → 由 framework/ActivityManager 读取，删除会改变“SOS 弹窗常驻”行为 |
| R-71 | 公共 API 开关 | `AndroidManifest.xml` | `<RES>\AndroidManifest.xml:188-190` | `<meta-data android:name="useXtcPublicApi" android:value="true"/>` | 云控 | **保留无害** | 保留（被外部读取；本包内无解析代码） |
| R-72 | 省电模式开关 | `com.android.systemui.statusbar.policy.m`（PowerSaveModeControllerImpl） | `<SRC>\com\android\systemui\statusbar\policy\m.java:11`、`:31-33`、`:35-43` | 读写 `SystemProperties.getBoolean("persist.sys.wleswitch", false)`；`com.xtc.baseservice.a.b.a(ctx).a() <= 10`（电量≤10 才允许开） | 云控（系统属性开关） | **保留无害**（业务必需） | 保留；属性由系统/long-endurance 服务写入，不是云控下发 |
| R-73 | HTTPDNS 总开关键 | `com.xtc.dns.provider.a` | `<SRC>\com\xtc\dns\provider\a.java:6-17`、`<SRC>\com\xtc\dns\client\b\a.java:6-16` | authority 模板 `"%s.com.xtc.httpdns.provider"`；键 `isHttpDnsEnable`；路径 `DomainModel/*`、`checkIpExpire/checkIpOverTime/decreaseIpPriority/clear` | 云控 | **必须移除** | 随 R-43~R-49 一起删 |
| R-74 | 模块开关广播 | `com.android.common.library.b` | `<SRC>\com\android\common\library\b.java:20`、`:25`、`:44` | `l = "com.xtc.launcher.moduleswitch.INIT_ACTION"`；`q="persist.sys.mobile.hand.close"`；`d="persist.sys.hall.fun"` | 云控 | **降级本地** | 常量类保留（被大量 UI 引用）；只取消对该广播的监听/处理（本包内未发现注册点，属外部约定） |
| R-75 | CTA 权限系统属性 | `com.xtc.httplib.okhttp.MonitorInterceptor` | `<SRC>\com\xtc\httplib\okhttp\MonitorInterceptor.java:40`、`:305-309` | `PROPERTY_CTA_PERMISSION="persist.sys.cta.permission"`；`getHadAllowedLauncherPermission()`：`aa.m1973b("persist.sys.bindstatus", false) \|\| aa.m1973b("persist.sys.cta.permission", false)` | 权限检测 | **必须移除** | 随 R-36 一并删除 |
| R-76 | 录屏远程参数 | `com.android.systemui.screenrecord.config.RemoteRecordingConfig` + `com.android.systemui.screenrecord.c` | `<SRC>\com\android\systemui\screenrecord\config\RemoteRecordingConfig.java:4-24`、`<SRC>\com\android\systemui\screenrecord\c.java:272-281` | `DEFAULT_RECORDING_DURATION_MS=60000`、`DEFAULT_STORAGE_SPACE_MB=100`；`d.a(funSwitch.getSwitchStatus())` 决定录屏功能是否开放 | 云控（功能开关） | **保留无害**（本地默认值） | 保留类，去掉 FunSwitch 数据源（改用默认 60s/100MB） |
| R-77 | 长续航/省电策略上报 | `com.xtc.baseservice.f.a` / `com.xtc.baseservice.f` | `<SRC>\com\xtc\baseservice\f\a.java:17-35`、`<SRC>\com\xtc\baseservice\f.java:29`、`:143-262` | `BehaviorUtil.customEvent(context,"launcher_watch_long_endurance_status", map)`、`"launcher_long_battery_available_time"`；`"watch_long_endurance"` 模式枚举与限制文案 | 埋点 | **降级本地** | 保留 `watch_long_endurance` 业务逻辑（省电/长续航是核心功能），仅删 `BehaviorUtil.*` 上报调用（2 行） |

### 2.9 埋点调用点聚合（25 处，必须逐个处理）

> `BehaviorUtil.*` 在复刻版中若保留空实现，则这 25 处可原样保留（零风险）；若删除门面类，则需同步删除这些调用。**建议**：先做空实现，编译通过后再批量清理，避免破坏 UI 逻辑。

| 子编号 | 调用点 文件:行号 | 事件名 / 语义 |
|---|---|---|
| R-78-01 | `<SRC>\com\android\systemui\c.java:81` | `BehaviorUtil.init(BehaviorCollector.Builder...)` —— **总开关，必须删** |
| R-78-02 | `<SRC>\com\android\systemui\qs\tiles\a.java:94` | `clickEvent(ctx, C0049b.e)` 快捷开关点击 |
| R-78-03 | `<SRC>\com\xtc\baseservice\k.java:539` | `customEvent("WATCH_NETWORK_SERVICE", map)` |
| R-78-04 | `<SRC>\com\xtc\baseservice\k.java:689` | `customEvent("WatchSignalLevel", map)` 信号强度 |
| R-78-05 | `<SRC>\com\xtc\baseservice\k.java:700` | `customEvent("WatchSignalNetworkType", map)` 网络制式 |
| R-78-06 | `<SRC>\com\xtc\baseservice\k.java:709` | `customEvent("WatchAirplaneMode", map)` 飞行模式 |
| R-78-07 | `<SRC>\com\xtc\baseservice\ScreenReceiver.java:139` | `collectScreenOnInfo()` 亮屏采集 |
| R-78-08 | `<SRC>\com\xtc\baseservice\ScreenReceiver.java:148` | `collectScreenOffInfo()` 灭屏采集 |
| R-78-09 | `<SRC>\com\xtc\baseservice\b\c.java:15` | `customEvent("launcher_operation_flashLight", map)` 手电筒时长 |
| R-78-10 | `<SRC>\com\xtc\baseservice\b\c.java:19` | `customEvent("launcher_flashlight_sos", null)` SOS 使用 |
| R-78-11 | `<SRC>\com\xtc\baseservice\f\a.java:17` | `customEvent(b.a, map)` 长续航状态 |
| R-78-12 | `<SRC>\com\xtc\baseservice\f\a.java:21` | `clickEvent(C0049b.e)` |
| R-78-13 | `<SRC>\com\xtc\baseservice\f\a.java:27` | `customEvent("launcher_long_battery_available_time", map)` |
| R-78-14 | `<SRC>\com\xtc\baseservice\f\a.java:35` | `customEvent("launcher_watch_long_endurance_status", map)` |
| R-78-15 | `<SRC>\com\xtc\bigdata\collector\exception\CrashHandler.java:512` | `customEvent("odex_opt", map, "C","C")` |
| R-78-16 | `<SRC>\com\xtc\dns\a\a.java:27` | `customEvent("apm_http_dns_req", …)` 成功 |
| R-78-17 | `<SRC>\com\xtc\dns\a\a.java:35` | `customEvent("apm_http_dns_req", …)` 失败 |
| R-78-18 | `<SRC>\com\xtc\dns\client\a\a.java:27` | 同 DNS 事件（client 侧副本） |
| R-78-19 | `<SRC>\com\xtc\dns\client\a\a.java:35` | 同上（失败） |
| R-78-20 | `<SRC>\com\xtc\httplib\bigdata\RsaKeyBigData.java:26` | `customEvent("account_auth_secret_key_error", map)` |
| R-78-21 | `<SRC>\com\xtc\httplib\okhttp\MonitorInterceptor.java:293` | `customEvent("Http_Monitor_Total_Traffic_Over", …)` |
| R-78-22 | `<SRC>\com\xtc\statusbar\a\a.java:61` | `customEvent("WatchScreenOn", map)` |
| R-78-23 | `<SRC>\com\xtc\statusbar\a\b.java:119` | `customEvent(b.a.f2834a, …)` 录屏行为（键 `f2831a` 打包） |
| R-78-24 | `<SRC>\com\xtc\statusbar\a\c.java:45` | `customEvent(InterfaceC0103b.f2837b, …)` 录屏行为 |
| R-78-25 | `<SRC>\com\xtc\common\bigdata\BehaviorUtil.java:201` | `BehaviorUtil.access$000().start(context)` → `DropboxScanner.start(ctx)`（间接） |

### 2.10 其他后台任务 / 广播 / 日志

| 编号 | 模块 | 类全名 | 文件:行号 | 证据 | 风险类型 | 处置判定 | 移除方案 |
|---|---|---|---|---|---|---|---|
| R-79 | 按键采集 | `com.xtc.bigdata.collector.receiver.HomePressReceiver` / `PressHomeKey` | `<SRC>\com\xtc\bigdata\collector\receiver\HomePressReceiver.java:27-33`、`PressHomeKey.java:29-40` | 监听 `android.intent.action.CLOSE_SYSTEM_DIALOGS` + `reason=homekey`，回调 `onHomePressed` → `ShareHelper.pressHomeKeyNotify()` | 埋点 | **必须移除** | 不实现（`PressHomeKey.getInstance().registerHomePress()` 无调用点时随类删除） |
| R-80 | 日志框架落盘 | `com.xtc.log.LogConfig` / `Log` | `<SRC>\com\xtc\log\LogConfig.java:125`、`:132`；`<SRC>\com\xtc\log\Log.java:1-80` | `Log.setLogger(new FilterLogger(new AsyncLogger(new AndroidLogger(new InterceptLogger(logConfig), logConfig)), logConfig))`；`isSaveLog(true)`（`SystemUIApplication.java:43` `e()` 里 `saveLog(true).module("ibwatch").appName(BuildConfig.a)`） | 日志 | **降级本地** | 保留 `LogUtil`（全仓 500+ 调用点，删不动），但把 `LogConfig.builder().saveLog(false).isPrintConsole(false)`，禁止落盘；不实现 InterceptLogger 的敏感数据拦截上报 |
| R-81 | 崩溃文件落盘 | `com.xtc.bigdata.collector.exception.CrashHandler` | `<SRC>\com\xtc\bigdata\collector\exception\CrashHandler.java:55-58`（`SDCARD_PATH="/mnt/sdcard"`、`DEFAULT_CRASH_LOG_PATH="/mnt/sdcard/.crash/"`）、`:365-396` | `FileUtils.writeFile(new File(path), stackTrace, true)` 写 `.cr` 文件；`:423-428` 超 1MB 清目录 | 日志 | **必须移除** | 随 R-15 删除 |
| R-82 | 亮屏/解锁广播 | `com.xtc.baseservice.ScreenReceiver` | `<SRC>\com\xtc\baseservice\ScreenReceiver.java:22`（`com.xtc.i3launcher.action.UNLOCK`）、`:139/148`（BehaviorUtil） | 注册亮灭屏 + launcher 解锁广播，触发埋点/业务 | 埋点/业务 | **降级本地** | 保留广播接收（业务需要：亮屏刷新状态栏），只删 2 处 `BehaviorUtil` 调用 |
| R-83 | 应用前后台 | `com.xtc.bigdata.collector.AppLifeCycleCallback` | 同 R-11 | — | 埋点 | **必须移除** | 同 R-11 |
| R-84 | 单次启动/退出通知 | `com.xtc.bigdata.collector.ShareHelper` | `<SRC>\com\xtc\bigdata\collector\ShareHelper.java:132-171` | `appLaunchNotify()`、`appExitNotify()` 通过 `getType(uri)` 触发 launcher 侧落库 | 埋点 | **必须移除** | 同 R-08 |
| R-85 | 显示/亮度系统设置 | `com.android.systemui.qs.XTCTileLayout` / `com.xtc.baseservice.util.d` | `<SRC>\com\android\systemui\qs\XTCTileLayout.java:154`、`:189-192`、`:219`；`<SRC>\com\xtc\baseservice\util\d.java:52-73`、`:137-160` | `Settings.System.getInt/putInt("screen_brightness_mode"/"screen_brightness"/"screen_off_timeout")` | 其他（本地设置） | **保留无害** | 保留（状态栏亮度/超时是核心功能） |
| R-86 | 车机/健康协议 | `com.xtc.ultraframework.health.protocol.ProtocolManagerEx`、`com.xtc.ultraframework.bluetooth.*` | `<SRC>\com\xtc\ultraframework\health\protocol\ProtocolManagerEx.java:1-135`、`<SRC>\com\xtc\ultraframework\bluetooth\BluetoothAdapterEx.java:1-53` | BLE 通信/设备状态（`BluetoothAdapterEx.setDiscoverableTimeout/setScanMode`） | 其他（业务） | **保留无害**（业务必需） | 保留（BLE 是产品核心，**不可误删**） |

### 2.11 已确认“无风险 / 无代码”的项（负向结论，避免误伤）

| 检查项 | 结论 | 证据 |
|---|---|---|
| `androidx.work` / WorkManager | **本包未使用**（仅 androidx 普通类；`AlarmManagerCompat` 为转调） | `<SRC>\androidx\core\app\AlarmManagerCompat.java:8-27`；`_scratch_work.txt` 中无 `androidx.work` 导入 |
| `XtcStayFront` 的 Java 解析 | **不存在**（纯 framework 行为） | 全仓 grep `XtcStayFront` = 0 命中；仅 `<RES>\AndroidManifest.xml:99-101` |
| `moduleSwitch`/`funSwitch` meta-data 的 Java 解析 | **本包不解析**（由外部 launcher/设置读取） | 全仓 grep `"moduleSwitch"`/`"notBind"` = 0（`DbModuleSwitch` 是数据库实体，非 meta-data）；`funSwitch` 命中均为 `content://com.xtc.fundata` 读取 |
| Retrofit 接口总数 | **仅 2 个** | `<SRC>\com\xtc\domain\config\http\HttpConfigApi.java:12`、`<SRC>\com\android\common\library\IWatchQuickChargeHttp.java:9` |
| 证书锁定（CertificatePinner） | **业务代码未使用**（仅 okhttp 库自带类）；HTTPS 为默认，无自定义 pin | `<SRC>\com\xtc\httplib\okhttp\DefaultOkHttpClient.java:146-169` 未 `certificatePinner(...)`；`okhttp3\CertificatePinner.java` 为库 |
| 自更新（下载/安装 APK） | **本包无**（`com.xtc.appupdate`/`com.xtc.systemupdate_*` 仅出现在白名单字符串里） | `<SRC>\com\xtc\httplib\okhttp\MonitorInterceptor.java:44` `INTERCEPT_REQUEST_PACKAGE_NAME` |
| 热修复（Tinker/Sophix 类） | **无**；仅有 odex 自优化（R-23/R-78-15） | 全仓无 `Tinker`/`Sophix` 导入 |
| `libxtcSecurity.so` / `libmmkv.so` 文件 | 存在于 APK 资源树 | `<RES>\lib\armeabi\libxtcSecurity.so`（144,956 B）、`<RES>\lib\armeabi\libmmkv.so`（219,292 B） |

---

## 3. 统计口径（可复核）

- 主条目：§2.1~§2.10 的 **R-01…R-86** 共 86 条（连续无跳号；§2.9 的 R-78 为 1 条聚合，其下 25 个调用点见表格）。
- 按类型分布：云控 27、埋点 19、崩溃 6、日志 5、设备 4、权限/安全 12、热修 1、后台 2、其他 10 = 86。
- 按判定：必须移除 50、降级本地 19、保留无害 17 = 86。
- 判定为“保留无害”的 17 条 = 业务组件/本地 UI/框架能力（R-53~R-57、R-62~R-66、R-69~R-72、R-76、R-85、R-86），**这些不是“没问题”，而是“问题不在上报且不能删”**，详见 IMPACT.md §9/§10。

> 处置优先级：先做 **R-01/R-04 断根**（删 `BigDataInitHelper` 调用），再做 **R-43~R-46 DNS 降级** 与 **R-31~R-32 路由配置移除**，其余均为随库裁剪（`com.xtc.bigdata`、`com.xtc.httplib`、`com.xtc.dns`、`com.xtc.xtcoco` 可整体不打包）。**注意**：`com.xtc.common.bigdata.BehaviorUtil`、`com.xtc.log.*`、`com.xtc.ultraframework.*`、`com.xtc.baseservice.*` 有大量业务调用点，**不能整包删**，只能空实现/降级（见 MODULES.md / IMPACT.md）。
