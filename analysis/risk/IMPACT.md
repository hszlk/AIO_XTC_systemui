# 移除影响评估（IMPACT）

> 配套文档：`RISK-MAP.md`（编号 R-xx）、`ENDPOINTS.md`、`MODULES.md`。
> 评估维度：① **业务影响**（移除后产品功能是否坏）；② **系统影响**（framework/其它应用是否会因缺少组件而报错）；③ **最小侵入替代方案**（不改业务代码的前提下最省事的做法）。
> 代码根：`D:\DEBUG\apk\sysui\work\jadx17\sources\`（`<SRC>`）；资源根：`D:\DEBUG\apk\sysui\nx\resources\`（`<RES>`）。

## 0. 总览：三档处置

| 档 | 含义 | 适用 |
|---|---|---|
| **A 档：整包不打包** | 直接删除源码树与依赖，成本最低、最彻底 | `com.xtc.bigdata.*`、`com.xtc.xtcoco.*`、`com.xtc.dns.*`、`com.xtc.httplib.*`、`com.xtc.domain.*`（可保留 `Domain` 常量类） |
| **B 档：保留签名、空实现** | 类名/方法签名保留，方法体改为 `return null/""/false` 或空 | `com.xtc.common.bigdata.BehaviorUtil`（25 处调用点）、`com.xtc.log.*`、`com.xtc.baseservice.*` 的上报方法 |
| **C 档：原样保留** | 与上报无关的业务/系统组件，**禁止改动** | `IKeyguard*`、`StatusBarProxyProvider`、`SelfStart*`、隐私 UI、BLE、录屏、手电筒、音量、省电、FileProvider、所有 manifest 业务 meta-data |

**总判断：本包移除上报/云控对业务功能几乎零影响**，因为：
1. 上报链路是“采集→写 `content://com.xtc.i3launcher.provider`”，由 **launcher 进程**负责上传；SystemUI 侧删除后，系统只是少了一个数据源，**不会有崩溃/异常**（`ShareHelper` 本身已 try/catch 所有 provider 异常：`<SRC>\com\xtc\bigdata\collector\ShareHelper.java:46-53`）。
2. 云控只是**读取**开关（FunSwitch/moduleSwitch），读到默认值时业务按默认分支走；唯一需要关注的是“默认值是否等于产品期望”。
3. 真正需要小心的是**Manifest 里的业务声明**（meta-data / exported 组件），删错了才会坏事——见 §9。

---

## 1. 初始化与“总开关”（R-01 ~ R-04、R-21、R-24、R-80）

| 风险 | 移除后业务影响 | 移除后系统影响 | 最小侵入替代方案 |
|---|---|---|---|
| R-01 `BigDataInitHelper`（`<SRC>\com\android\systemui\c.java`） | **无**。它只初始化上报 SDK，不参与 UI | 无。被 `SystemUIApplication.f()` 直接调用，删调用即可 | 删除 `SystemUIApplication.java:100-104` 三行 + `:91/:92` 两行；不实现 `c`/`JacocoUtils`/BlockCanary 初始化。**注意** `SystemUIApplication.a()` 里 `:86-93` 其余行要保留 |
| R-02 120s Timer | 无 | 无 | 随 R-01 删 |
| R-03 崩溃监听 | 无（崩溃时只是不再写上报库；系统默认 handler 仍在） | 无 | 随 R-01 删；若产品需要“崩溃后自恢复”，AOSP 行为已由 system_server 处理（persistent 进程重启） |
| R-04 `JacocoUtils` / BlockCanary | 无 | 无 | 删 `SystemUIApplication.java:91-92` |
| R-80 `LogConfig.saveLog(true)` | **有轻微影响**：排障时看不到 `/data/.../logs` 文件 | 无。`LogUtil` 全仓 500+ 调用点仍可用（只是不落盘） | 保留 `LogUtil`，把 `SystemUIApplication.java:42-44` 的 `e()` 改为 `saveLog(false).isPrintConsole(false)`；或整段删除（`LogConfig.builder()` 不再 build 时，`Log` 内部有默认 logger，见 `<SRC>\com\xtc\log\Log.java:1-80`） |

> **验证点**：删掉 R-01 后必须 grep 确认没有其它 `c.a().m290a()`/`BehaviorUtil.init` 调用；`BehaviorUtil.init` 只在 `<SRC>\com\android\systemui\c.java:81` 一处。

---

## 2. 行为采集库（R-05 ~ R-14、R-78、R-79、R-83、R-84）

| 风险 | 移除后业务影响 | 系统影响 | 最小侵入替代 |
|---|---|---|---|
| R-05~R-12、R-14 采集库主体 | **无**。采集库不驱动 UI | 无。`ShareHelper` 对 provider 异常全 catch（`<SRC>\com\xtc\bigdata\collector\ShareHelper.java:46-53`、`:65-72`），不会抛到业务 | **A 档**：`com.xtc.bigdata.*` 整包不打包。若构建系统按类引用，保留 `BehaviorCollector`/`BehaviorUtil` 空壳 |
| R-13 `BehaviorUtil` 门面（25 处调用点） | **无**（调用点都是为了上报，函数无返回值参与业务） | 无 | **B 档（推荐）**：保留类与静态方法签名，方法体空。这样 25 个调用点**一行都不用改**；后续再逐步清理。**注意** `BehaviorUtil.init(BehaviorConfig)` 若为空，`BbkBehavior` 不会被创建，后续 `getBbkBehavior()` 返回 null 的分支要能在空实现里避免 NPE（返回 `null` 时 `BehaviorUtil.clickEvent` 先判空即可） |
| R-78-01 `BehaviorUtil.init` | 无 | 无 | 直接删调用 |
| R-78-02~25 各埋点调用点 | 无 | 无 | 若 R-13 用空壳，全部保留；若删门面，则逐个删除（25 处，见 RISK-MAP §2.9） |
| R-79 HomePressReceiver / PressHomeKey | **基本无**：原逻辑是“记录用户按 Home 键” | 无。它是**动态注册**的 receiver（不是 manifest 组件），删了不会缺组件 | 不实现；`PressHomeKey.getInstance().registerHomePress()` 若无调用点则随类删 |
| R-83 `AppLifeCycleCallback` | 无 | 无 | 不注册 `registerActivityLifecycleCallbacks` |
| R-84 appLaunch/appExit notify | 无 | 无 | 随 ShareHelper 删 |

> **风险提示（易踩坑）**：不要因为“删上报”而删掉 `<SRC>\com\xtc\baseservice\ScreenReceiver.java` 的**广播注册**（`:139/:148` 只是其中两行埋点调用）；该 receiver 同时承担亮灭屏业务通知（见 §8）。

---

## 3. 崩溃 / ANR / 性能 / 卡顿（R-15 ~ R-25）

| 风险 | 移除后业务影响 | 系统影响 | 最小侵入替代 |
|---|---|---|---|
| R-15 `CrashHandler`（`Thread.setDefaultUncaughtExceptionHandler`） | **无功能影响**；但会改变“崩溃时行为”：原版崩溃会走 `handleException → 写 provider → 交给默认 handler`。移除后直接走 AOSP 默认（弹“系统界面已停止/进程被杀由 system_server 重启”） | 无。`persistent=true` + `sharedUserId=system` 的进程由 system_server 负责重启 | 删除 `InitManager.java:57` 的 `registerCrashHandler()` 调用（随 R-05 一起）；**不要**在复刻版注册自己的 handler |
| R-16 `CrashIntentService`（manifest :173-175） | 无 | **无**：`exported=false` 且**无 intent-filter**；全仓 grep 除自身外 0 命中（jadx17/18 均如此），即无 Java 发起方；删除 manifest 不会让外部/系统报“找不到组件” | 删 manifest `<service>` + 不实现类。若原版 `CrashHandler` 的不可读方法体里有 `startService`，本方案已把 `CrashHandler` 一并删除，链路同断 |
| R-17/R-18 `WatchDogs`/`ANRMonitor` | 无（卡顿检测不参与业务） | 无 | A 档不打包 |
| R-19 `MonitorStartReceiver`（manifest :176-183） | 无 | **低风险**：它是 `exported` 隐式广播 receiver（`com.xtc.watchdogs`、`com.xtc.launcher.ACTION_CHANGE_REGION`）。删除后 launcher 发的广播**无接收者，不会报错**（Android 广播语义：无接收者即丢弃）。但 launcher 侧“看门狗健康检查”会失去这个观测点 → 若产品依赖“launcher 检测 SystemUI 卡死”，需评估 | 删 manifest `<receiver>`；**保留** `com.xtc.launcher.ACTION_CHANGE_REGION` 的其它业务处理（若有）。复刻版若需要被 launcher 感知存活，另建轻量 receiver（不要恢复 WatchDogs） |
| R-20/R-21 性能/FPS/BlockCanary | 无 | 无 | 删 `SystemUIApplication.java:92` |
| R-22 `DropboxScanner` | 无 | 无（动态注册 `DROPBOX_ENTRY_ADDED` 广播） | A 档 |
| R-23 Odex 自优化（`odexOpt` 写 `persist.sys.opt.xtc_debug` / `persist.sys.debugopt.pkgname`） | 无（只影响“是否重新触发 dexopt”） | **有轻微影响**：这两个属性由 `com.xtc.baseservice`/系统 daemon 读取触发优化；不写 = 不触发优化，不会报错 | 不实现；**不要**去删 `persist.sys.*` 的读取方（属系统侧） |
| R-24/R-25 `JacocoUtils` + `CodeCoverageReceiver`（manifest :154-160） | 无 | **安全收益**：`CodeCoverageReceiver` 是 `exported=true` 的广播，任意应用发 `com.xtc.report.generate.ec` 即可触发写 `/sdcard/ec/...`。删除后无影响 | 删 manifest `<receiver>` + 不实现 `JacocoUtils`；`SystemUIApplication.java:91` 删 |

---

## 4. 设备信息采集（R-26 ~ R-29）

| 风险 | 移除后业务影响 | 系统影响 | 最小侵入替代 |
|---|---|---|---|
| R-26 `DeviceUtils`（IMEI/MAC/机型） | **可能影响**：`BaseAttr` 用它生成上报属性；但业务层（隐私弹窗展示机型）改用 `Build.MODEL` 即可。`ro.product.innermodel` 在隐私文案中用到（`PrivacyUtilities.java:124-128`），属**本地读取**，不属上报 | 无 | 保留 `ro.product.innermodel` 的本地读取（隐私文案），删除 `getIMEI/getMac/getMachineId`（或整包不打包，业务改调 `Build.MODEL`） |
| R-27 `MachineAttr` | 无 | 无 | A 档 |
| R-28 `BaseAttr/BaseAttrManager` | 无 | 无 | A 档 |
| R-29 `UserAttr` | 无 | 无 | A 档 |

> 若产品仍需要“设备型号显示/客服排障”，建议在复刻版用 `Build.MODEL + Build.VERSION.SDK_INT` 本地拼接，**不读 IMEI/MAC**。

---

## 5. 网络层 / 域名 / 路由下发（R-30 ~ R-42）

| 风险 | 移除后业务影响 | 系统影响 | 最小侵入替代 |
|---|---|---|---|
| R-30 `Domain` 枚举 | **无**（仅常量） | 无 | 保留类（很多库引用 `Domain` 常量），但不做路由下发；或替换为 2~3 个本地常量 |
| R-31/R-32 路由配置 `POST /route-service/route/getRouteInfo` | **无**（SystemUI 只是“读域名”，没有依赖该请求的业务；请求失败时代码已有 fallback：`<SRC>\com\xtc\domain\config\http\c.java:86-92` 只打日志） | 无 | 删除 `m1547a()` 调用；`Domain` 用固定值 |
| R-33 机房切换 `powerManager.reboot()` | **无**（只对 `com.xtc.i3launcher` 包名生效，SystemUI 上是死分支） | 删除**更安全**（否则一旦复刻版包名/条件被满足会远程重启） | 不实现 |
| R-34 `HttpManager`（watchId/广播 RSA key） | **无**（SystemUI 不用这些头） | 无 | 不实现；若复刻版保留 httplib，至少删 `registerHttpBridge` 与 provider watchId 读取 |
| R-35 `DefaultOkHttpClient` 拦截器栈 | **无**（本包只有路由请求会用到 client） | 无 | A 档：httplib 不打包。**若保留**：必须删 `MonitorInterceptor`（R-36），否则**它会凭本地阈值把正常请求伪造成错误响应**（`<SRC>\com\xtc\httplib\okhttp\MonitorInterceptor.java:87-98`） |
| R-36 `MonitorInterceptor` | **无** | 无 | 见上；**这是“删上报”里最容易被忽略的“功能破坏器”**：留在包里会在 5 次/分钟、10KB/请求、500MB/日 阈值时直接返回错误 |
| R-37 `PreprocessorInterceptor`（IM 透传） | 无 | 无 | 不实现 |
| R-38 `LogInterceptor` | 无 | 无 | 不实现 |
| R-39 请求头加密 | 无 | 无 | 不实现 |
| R-40 `BigdataClientManager`（`content://com.xtc.http.bigdata.provider...`） | 无 | 无 | 不实现 |
| R-41 `SwitchNetworkManager`（网络调度云控） | **可能有影响**：它参与“弱网/后台限流”，但 SystemUI 无业务请求，实际不生效 | 无 | 不实现；`VolumeFuncSetting.java:21` 只是 import，删库时同步删 import 即可 |
| R-42 `ConfUpdatesInfoManager` | 无 | 无 | A 档 |

> **结论**：httplib 整套可以不打包。若为了保留某些工具类（如 `f.a()` gson 封装）而保留，务必确认**不初始化 `DefaultOkHttpClient`**（否则拦截器链生效）。

---

## 6. HTTP-DNS（R-43 ~ R-50）

| 风险 | 移除后业务影响 | 系统影响 | 最小侵入替代 |
|---|---|---|---|
| R-43 `HttpDnsContentProvider`（manifest :164-172） | **无**：它只提供“域名→IP 缓存”查询，删掉后 OkHttp 用系统 DNS | **有“被查询”风险**：manifest 是 `exported=true` + `readPermission=com.android.systemui.permission.READ_DNS`（`protectionLevel=normal`，`<RES>\AndroidManifest.xml:71-73`）。**其它应用（launcher 等）会 `resolveContentProvider("<pkg>.com.xtc.httpdns.provider")` 探测**（`<SRC>\com\xtc\dns\client\b.java:52-62`）。如果**删除该 provider**，外部应用探测会得到 `null`，它们会回落系统 DNS（代码已支持：`resolveContentProvider(...) != null` 才用 HTTP-DNS），**不会崩溃** | 方案 A（推荐）：删 provider。方案 B（保守）：**保留 provider**（authority/权限不变），`query/getType` 直接 `return null` → 外部探测仍成功但拿不到数据，效果等价系统 DNS。**注意**：如果整机 launcher 的 HttpDnsManager 把“provider 存在但没有数据”当异常重试，可能产生日志噪音但无害 |
| R-44/R-49 `DnsStrategy`/`HttpDnsManager` | **无**（回退 `Dns.SYSTEM.lookup`，代码 `<SRC>\com\xtc\dns\api\a.java:110-118`） | 无 | 复刻版 OkHttp 不设置 `builder.dns(...)`，用默认系统 DNS |
| R-45 腾讯 HTTP-DNS `http://119.29.29.98/d` | **无**（同上回退） | 无 | 删除；不打包 `com.xtc.dns` |
| R-46 `/mnt/sdcard/.../httpdnsinfo.properties` | 无 | 无 | 删除（**顺带消除一个外部存储注入点**） |
| R-47 DNS 埋点 `apm_http_dns_req` | 无 | 无 | 删除 |
| R-48 DNS Provider 查询方 | 无 | 无 | 删除 |
| R-50 快充 Retrofit 接口 | **可能影响快充状态显示**（若 UI 依赖）：本包**无调用点**，可删 | 无 | 保留接口定义（不初始化 client）或删除 |

> **DNS 回退验证**：`DnsStrategy.lookup()` 在 HTTP-DNS 为空/异常时已经走 `Dns.SYSTEM.lookup(str)`（`<SRC>\com\xtc\dns\api\a.java:112`），所以“删 DNS 模块”是**协议层安全**的。

---

## 7. JNI / 安全校验 / 权限检测（R-51 ~ R-58、R-59 ~ R-66、R-75）

| 风险 | 移除后业务影响 | 系统影响 | 最小侵入替代 |
|---|---|---|---|
| R-51 `libxtcSecurity.so` + `XtcSecurity` | **需确认**：本包内**未发现调用点**（`XtcSecurity` 在 SystemUI 里只有类定义）。若复刻版其它模块（如隐私/加密）引用，需保留。安全建议：`System.loadLibrary("xtcSecurity")` 在类静态块（`:18`），**只要不加载类就不会 dlopen** | 无 | 类不加载即可；**保留 `.so` 文件**（`<RES>\lib\armeabi\libxtcSecurity.so`），防止其它系统应用/框架按路径 dlopen。若确认无外部使用可删文件 |
| R-52 `FaceImageEncrypt` | 本包无调用点 | 无 | 同 R-51 |
| R-53 MMKV | **有影响**：`com.xtc.utils.storage.l` 用 MMKV 存业务设置（`<SRC>\com\xtc\bigdata\collector\BehaviorCollector.java:76-83` 里 bigdata 也用它，但业务侧同样在用）。**不要删 `libmmkv.so`** | 无（`MMKVContentProvider` 若已在 manifest 注册需检查） | **仅删 bigdata 的 `initMMKV` 调用**；保留 MMKV 本体 |
| R-54 `AppSignatureManager`（`XTCUtilsService.verifyInstalledAppSignature`） | 无（本包无调用点） | 无 | 保留（框架能力，删了也可能被反射调用）；如要裁剪，确认全仓无引用即可删 |
| R-55 `SecurityKeyManager` | 无（构造即抛 `UnsupportedException`，死代码） | 无 | 保留；不影响 |
| R-56 `com.xtc.core.se2.c.a()`（SecureCheck） | 无（**空方法**） | 无 | 保留空调用（最省事）或删调用 |
| R-57 `XtcAuthManagerEx` | **可能有影响**：`xtcauth` 系统服务代理，业务 UI 可能用它申请权限/自启动白名单。本包内属于系统能力封装 | 无 | **保留** |
| R-58 `RsaKeyBigData` | 无 | 无 | 删（随 httplib） |
| R-59 `PermissionListActivity` | **有影响（潜在）**：它是权限申请引导页。**谁拉起它**：① 本包 `PermissionUtil`（`<SRC>\com\xtc\architecture\mvp\b\a.java:56-62`）；② **外部应用**可能按类名显式 `startActivity(new Intent().setClassName("com.android.systemui","com.xtc.architecture.mvp.PermissionListActivity"))`（Manifest 未 `exported=false`，默认 exported=true 因为无 intent-filter → **实际上未声明 exported，Android 8 默认 false（无 filter 时）**；但 sdk 30 下需显式。原包能工作说明有调用方）| **有“组件被拉起”风险**：删 Activity 后外部显式启动会 `ActivityNotFoundException`（对方进程崩溃，不是本包崩溃）。**最稳做法**：保留 Activity + 类名，`onCreate()` 直接 `finish()` | **B 档（推荐）**：保留组件，内容改为空页/直接 `finish()`。见 §9 组件表 |
| R-60 `PermissionUtil` | 无（系统运行时权限申请是 Android 标配） | 无 | 保留；仅把跳 `PermissionListActivity` 改为直接 `ActivityCompat.requestPermissions` |
| R-61 `CtaPermissionUtil` | **有影响**：隐私弹窗要展示“本应用申请的权限及理由”，删了隐私弹窗内容为空 | 无 | **保留**（纯本地读取，不联网、不上报） |
| R-62/R-63 自启动 | **有影响**：引导用户开自启动是业务。删了体验下降，但不会崩 | 无（provider 不存在时代码已 `return true`：`<SRC>\com\xtc\ui\widget\selfstart\SelfStartUtils.java:88-93`） | **保留** |
| R-64 隐私 UI | **有影响**（合规必需） | 无 | **保留** |
| R-65 `PrivacyUriActivity` | **有影响**（隐私协议二维码） | **有“组件被拉起”风险**：外部（launcher 设置页）可能显式启动它。删了对方会 `ActivityNotFoundException` | **保留**（本页不联网）；如要删，先确认全机无 `setClassName(...PrivacyUriActivity)` 调用 |
| R-66 `StatusBarProxyProvider` | **有影响（大）**：launcher 靠它查询下拉面板状态、收起面板、同步 IM 状态（见 MODULES M-13）。删了 launcher 的“下拉/通知中心联动”会失效 | **有“provider 被调用”风险**：`content://com.xtc.systemui.statusbar.proxy` 被 launcher `call()`；删除后对方 `IllegalArgumentException: Unknown provider`（**可能被 catch，也可能崩溃，取决于 launcher 实现**） | **保留**（业务必需）。它只做本机状态查询，无上报 |
| R-75 `persist.sys.cta.permission` 校验 | 无（属于请求准入，随 R-36 删） | 无 | 删 |

---

## 8. 远程配置 / 系统开关 / 后台广播（R-67 ~ R-77、R-81、R-82、R-85、R-86）

| 风险 | 移除后业务影响 | 系统影响 | 最小侵入替代 |
|---|---|---|---|
| R-67 FunSwitch（`content://com.xtc.fundata/FunSwitch`） | **有影响（中）**：快捷开关/录屏/省电等功能“是否可用”由它控制。**删掉数据源后必须给默认值**，否则功能可能全关 | 无（provider 由 launcher 提供；查询失败已有默认值：`h.a(ctx, str, false)` 返回 `switchStatus` 默认） | **降级本地**：保留 `h.a(ctx,pkg,default)` 的**调用点与默认值**，把 `query(...)` 改为直接返回默认 `FunSwitch(switchStatus=1)`。即“功能默认全开” |
| R-68 moduleSwitch（`content://com.xtc.moduledata/query`）+ gdprdata | **有影响（中）**：`queryModuleSwitchByInt` 控制长续航/提示等。删了要默认 | 无 | 同 R-67：`queryModuleSwitchByInt(ctx,module,default)` 直接 `return default`；`queryModuleSwitchTipByInt` 返回 `defaultTip` |
| R-69 manifest `moduleSwitch`/`funSwitch`/`supportScreenRecordAudio` | **有影响（大）**：这些 meta-data 由**外部应用**（launcher/设置）读取，用来决定“手表是否显示录屏入口/录屏是否带声音”。本包 Java 代码**不解析**它们（grep 零命中），**删除会静默丢功能** | 无（只是数据） | **原样保留**（`<RES>\AndroidManifest.xml:138-146`）。这是“最容易误删”的项之一 |
| R-70 `XtcStayFront` | **有影响**：让 `FlashLightSOSDialog` 常驻前台（框架读取）。删了 SOS 弹窗可能被系统回收/不可见 | 无 | **原样保留**（`<RES>\AndroidManifest.xml:99-101`）；Java 侧无需任何代码 |
| R-71 `useXtcPublicApi` | 未知外部依赖，成本为零 | 无 | **保留**（`<RES>\AndroidManifest.xml:188-190`） |
| R-72 `persist.sys.wleswitch` 省电开关 | **有影响**：省电模式状态来源 | 无（属性由系统服务写） | **保留**（`<SRC>\com\android\systemui\statusbar\policy\m.java:11`） |
| R-73 `isHttpDnsEnable` | 无 | 无 | 随 DNS 删 |
| R-74 `com.xtc.launcher.moduleswitch.INIT_ACTION` | 无（本包未注册监听） | 无 | 保留常量类 |
| R-76 `RemoteRecordingConfig` + FunSwitch 录屏开关 | **有影响**：录屏是否开放。默认值 `duration=60s` / `storage=100MB` 是产品默认 | 无 | 保留类；FunSwitch 无数据时按“开放”处理（默认 status=1） |
| R-77 长续航上报 | 无 | 无 | 保留 `com.xtc.baseservice.f` 的长续航逻辑，只删 2 行 `BehaviorUtil` 调用 |
| R-81 崩溃文件落盘（`/mnt/sdcard/.crash`） | 无 | 无 | 随 R-15 删 |
| R-82 `ScreenReceiver` 广播 | **有影响**：亮灭屏/解锁事件驱动状态栏刷新。删了状态栏可能不随亮屏更新 | 无 | **保留广播注册**，只删 `:139/:148` 两行埋点 |
| R-85 `Settings.System` 亮度/超时 | **有影响（核心）**：亮度条 | 无 | **保留** |
| R-86 BLE/健康协议 | **有影响（核心）**：BLE 是产品功能 | 无 | **保留**（含 `*.proto` 与 `libmmkv`） |

---

## 9. 组件级“牵一发”清单（删之前必须逐项确认）

> 本表回答“系统/外部应用是否会因为缺组件而报错”。原则：**被外部显式启动/绑定的组件，宁可保留空实现，也不要直接删**。

| 组件 | 类型 | 外部是否会拉起/绑定 | 删除后果 | 建议 |
|---|---|---|---|---|
| `com.android.systemui.SystemUIService` | service | **是**（system_server 起 SystemUI） | 系统无状态栏（灾难） | **绝对保留** |
| `com.android.systemui.statusbar.phone.StatusBar`（由 `config_statusBarComponent` 反射加载） | 类（string 资源） | 框架通过 `Class.forName` | 启动即 `RuntimeException`（`<SRC>\com\android\systemui\SystemBars.java:45-55`） | **绝对保留** |
| `com.android.systemui.statusbar.provider.StatusBarProxyProvider` | provider | **是**（launcher `call()`） | launcher 侧 `Unknown provider` 异常/功能失效 | **保留**（R-66） |
| `com.android.systemui.statusbar.proxy` authority + `com.xtc.systemui.statusbar.permission.StatusBarProxyProvider` 权限 | manifest | 是（同上） | 同 | **保留** |
| `com.xtc.systemui.keyguard.permission.KEYGUARD_SERVICE` + `IKeyguardInterface`/`IKeyguardCallbackInterface` | permission/AIDL | **是**（外部绑定锁屏服务） | 绑定失败 | **保留**（M-3） |
| `com.android.systemui.fileprovider`（FileProvider） | provider | 是（录屏保存/分享） | 录屏导出崩溃 | **保留**（M-16） |
| `com.android.systemui.media.MediaProjectionPermissionActivity` | activity (exported=true) | **是**（外部可拉起请求录屏权限） | 对方 `ActivityNotFoundException` | **保留**（M-5） |
| `com.android.systemui.flashlight.FlashLightActivity` / `FlashLightSOSDialog` | activity | 是（action `android.settings.FLASH_LIGHT`/launcher） | 手电入口失效 | **保留**（M-6） |
| `com.android.systemui.screenrecord.RecordingService` | service (exported=false) | 否（内部 startService） | 无外部影响 | 可保留（业务必需） |
| `com.xtc.statusbar.XtcNotificationListenerService` | service | **是**（system_server 绑定，靠 intent-filter） | 通知监听功能失效 | **保留**（M-4） |
| `com.xtc.bigdata.collector.exception.CrashIntentService` | service (exported=false, 无 filter) | **否** | 无 | **可删**（R-16） |
| `com.xtc.bigdata.monitor.receiver.MonitorStartReceiver` | receiver (exported, 隐式广播) | 广播发送方不感知 | 无异常（无接收者） | **可删**（R-19）；注意 launcher 健康检查语义 |
| `com.xtc.xtcoco.CodeCoverageReceiver` | receiver (exported, `com.xtc.report.generate.ec`) | 广播发送方不感知 | 无异常 | **可删**（R-25，安全收益） |
| `com.xtc.dns.provider.HttpDnsContentProvider` | provider (exported, readPermission normal) | **是**（`resolveContentProvider` 探测） | 探测失败→对方回落系统 DNS（无害） | 可删；保守用“空 provider”（R-43） |
| `com.android.systemui.com.xtc.httpdns.provider` authority | manifest | 同上 | 同上 | 同上 |
| `com.xtc.architecture.mvp.PermissionListActivity` | activity | **可能**（外部按类名启动） | 对方 `ActivityNotFoundException` | **保留空壳**（R-59） |
| `com.xtc.ui.widget.privacy.PrivacyUriActivity` | activity | **可能**（外部启动查看隐私 URL） | 对方 `ActivityNotFoundException` | **保留**（R-65） |
| `com.xtc.ui.widget.selfstart.SelfStartActivity` | activity | 否（内部启动） | 无 | 保留（R-62） |
| `com.android.systemui.vassistant.SystemControlService` | service (exported, directive) | **是**（语音助手） | 语音控制手电/录屏失效 | **保留**（M-14） |
| manifest meta-data `moduleSwitch`/`funSwitch`/`supportScreenRecordAudio` | meta-data | **是**（外部读） | 静默丢功能 | **保留**（R-69） |
| manifest meta-data `XtcStayFront` | meta-data | 是（framework） | SOS 弹窗行为变化 | **保留**（R-70） |
| manifest meta-data `useXtcPublicApi` | meta-data | 未知外部 | 未知 | **保留**（R-71） |
| `com.xtc.bigdata.common.utils.UriUtils` 指向的 `com.xtc.i3launcher.provider` | 外部 provider（不在本包） | 本包是**调用方** | 不删本包也不会崩（已 catch） | 删调用方即可 |

---

## 10. “必须保留但最容易被误删”Top 10（与 RISK-MAP/ENDPOINTS 呼应）

1. **`com.android.systemui.statusbar.provider.StatusBarProxyProvider` + `com.xtc.systemui.statusbar.proxy`**：名字里带“Provider/权限”，看起来像权限检测，实际是 launcher 联动业务（MODULES M-13、R-66）。
2. **Manifest `moduleSwitch` / `funSwitch` / `supportScreenRecordAudio` meta-data**：看起来像“云控开关”，实际被**外部应用**读取来决定录屏入口/录音（R-69）。本包代码不解析它们，删了不会报错、只会**静默丢功能**——最危险的一类。
3. **`XtcStayFront` meta-data**：只有一行，删除后 SOS 弹窗可能不再常驻（R-70）。
4. **`IKeyguardInterface` / `IKeyguardCallbackInterface` + `com.xtc.systemui.keyguard.permission.KEYGUARD_SERVICE`**：本包没有 AOSP Keyguard，锁屏靠这套 AIDL 桥接（M-3）。
5. **`com.android.systemui.vassistant.SystemControlService` + `assistantapi-directive=[FlashLight,VideoRecord]`**：语音控制手电/录屏的唯一入口（M-14）。
6. **`com.xtc.statusbar.XtcNotificationListenerService`**：通知/音乐卡片/未读依赖它，且由 system_server 绑定（M-4）。
7. **`com.xtc.ui.widget.privacy.*` 全套**：隐私合规必需，且 `PrivacyUriActivity` 可能被外部启动（M-11、R-64/65）。
8. **`com.xtc.baseservice.*`（尤其 `ScreenReceiver`、`f` 长续航、`k` 网络状态）**：里面夹着上报调用，但类本身是**业务状态机**；只能删 `BehaviorUtil.*` 那几行（R-77、R-82）。
9. **`libmmkv.so` + `com.tencent.mmkv.MMKV`**：业务设置存储在用；bigdata 只是“顺便用了 MMKV”，删 bigdata 不能删 MMKV（R-53）。
10. **`assets/pull_up_setting/*.json` + `assets/guide/*`**：快捷开关/引导动画资源，与上报无关，删了 UI 会空白（M-10）。

---

## 11. 复刻版“最小改动”实施顺序（建议）

| 步骤 | 动作 | 涉及 RISK | 风险 |
|---|---|---|---|
| 1 | 删 `SystemUIApplication` 的 4 行初始化（`:91`、`:92`、`:100-104`），并把 `LogConfig` 改 `saveLog(false)` | R-01~R-04、R-21、R-24、R-80 | 低（无外部依赖） |
| 2 | `BehaviorUtil` 保留类名 → 所有方法空实现（含 `init`） | R-13、R-78 | 极低（25 处调用点零改动） |
| 3 | 不打包 `com.xtc.bigdata.*`、`com.xtc.xtcoco.*`、`com.xtc.dns.*`、`com.xtc.httplib.*`、`com.xtc.domain.*`（`Domain` 可留常量） | R-05~R-12、R-14~R-50、R-58 | 低（需同步删 import） |
| 4 | 删 Manifest 中 3 个上报组件（`CrashIntentService`、`MonitorStartReceiver`、`CodeCoverageReceiver`）+ HTTPDNS provider | R-16、R-19、R-25、R-43 | 低（见 §9） |
| 5 | FunSwitch/moduleSwitch 降级为本地默认值（status=1 / 传入 default） | R-67、R-68 | 中（要确保默认值=产品期望“全开”） |
| 6 | 保留全部业务组件/manifest meta-data/权限声明不动 | R-59、R-62~R-72、R-86 等 | 低（但**别手抖**） |
| 7 | 全量回归：状态栏/下拉/录屏/手电/SOS/音量/省电/BLE/通知/快捷开关/隐私/自启动/代理 Provider/系统控制服务 | — | 每项按 MODULES.md 的 ①~⑧ 调用链走一遍 |

## 12. 结论

- **上报链路是“只写不读”的单向旁路**，SystemUI 内部没有任何业务逻辑依赖上报结果 → 移除**不会破坏业务流程**。
- **云控读取是“只读开关”**，代码普遍带默认值/异常兜底（`h.a(ctx,pkg,default)`、`FunSwitch` 默认 status、`RemoteRecordingConfig` 默认 60s/100MB）→ 把数据源换成常量即可，**不需要改业务分支**。
- **会真正“报错”的情况只有两类**：① 被外部显式启动/绑定的组件被删除（`PermissionListActivity`、`PrivacyUriActivity`、`StatusBarProxyProvider`、`IKeyguardInterface`、`SystemControlService`）；② Manifest 业务 meta-data 被删导致外部静默丢功能（`moduleSwitch`/`funSwitch`/`supportScreenRecordAudio`/`XtcStayFront`）。这两类都已在 §9/§10 给出“保留空实现/原样保留”的最小侵入方案。
- 最值得优先处理的三项安全收益：**删 `CodeCoverageReceiver`（exported 广播写 sdcard）**、**删 `httpdnsinfo.properties`（sdcard 注入 DNS）**、**删 `MonitorInterceptor`（会破坏正常请求）**。
