# SPEC — 锁屏/Keyguard + 隐私/自启动/权限说明 + 富文本话术 + Provider/代理 + Toast/提示

> 目标包：`com.android.systemui` 1.8.00（XTC 儿童手表 SystemUI，minSdk 27 / targetSdk 30，`sharedUserId=android.uid.system`，`persistent=true`，`process=com.android.systemui`）
> 代码证据（唯一权威）：`D:\DEBUG\apk\sysui\work\jadx18b\sources`（1.8.00 unquicken 精确版）。交叉校验：`work\jadx17\sources`（对同一文件做「去 JADX 头 + 去空白」归一化后比对，逻辑一致，仅合成字段/方法重命名编号整体偏移 −1/+2，例 `statusbar\f.java`：18b `mo507a/mo508b` ↔ 17 `mo506a/mo507b`；`AntiAddictionExitLayout` 字段 `f2762a/f2763a` ↔ `f2760a/f2761a`。**结论：18b 可信，17 可作逻辑复核**）。
> 资源证据：`D:\DEBUG\apk\sysui\nx\resources`（已解码文本 XML）。风险清单：`analysis\risk\RISK-MAP.md`（引用编号即可）。
> 本文档只描述「复刻实现规格」，不改任何代码。

---

## 0. 一句话结论（先给 Lead 的判断依据）

1. **本包没有 AOSP Keyguard**。锁屏是**外部 launcher（`com.xtc.i3launcher`）的 `KeyguardService`**，SystemUI 只是 **AIDL 客户端**：`com.android.systemui.statusbar.f`（`StatusBarKeyguardViewManagerImpl`）bind 外部服务、注册回调，用 `isKeyguardLocked()` 决定「要不要先解锁再执行动作」，用 `requestDismissKeyguard()` 请求解锁，用 `onKeyguardDismissed/onKeyguardShown` 收状态。
2. **隐私/自启动/权限说明这一大坨在 1.8.00 里全部是「库残留」**：`PrivacyFirstDialog`、`XTCPrivacy`、`CtaPermissionDialog/View`、`PrivacyUriActivity`、`SelfStartActivity`、`PermissionListActivity` 在本包内**没有任何活调用方**（唯一入口 `PermissionUtil.a(Activity,Intent,core.b)` 只在 `BaseActivity.d()` 里以 `mo1212a()==false` 调用 → 不会启动 `PermissionListActivity`），且 manifest 里这些 Activity 都没有 `android:exported="true"` + 无 intent-filter（API27+ 默认 `exported=false`），外部也起不来。→ **复刻优先级最低，但「文案必须逐条保留可改」**（用户明确要按规则改话术）。
3. **富文本标记是两段式**：`${XTC}` 是**品牌占位符**（`com.xtc.utils.system.w.a()` 替换为「小天才」/「imoo」），`$…$` 是**高亮定界符**（`com.xtc.statusbar.AntiAddictionExitLayout.a(String)` 把 `$` 之间的文字加 `ForegroundColorSpan(-1)`=纯白，再把 `$` 删除；正文基色 `#b3ffffff`）。
4. **对外 Provider 两个真在用**：`StatusBarProxyProvider`（仅允许 `com.xtc.i3launcher`，4 个 call 方法）、`FileProvider`（仅 `cache/leak/` 一个路径）；`HttpDnsContentProvider` 按结论**移除**（原协议已完整记录，见 §B.4.3）。
5. **AIDL 方案结论**：**写 `.aidl`（2 个文件：`IKeyguardInterface.aidl` + `IKeyguardCallbackInterface.aidl`），不要手写 Parcelable/Binder**；若改为自建锁屏，再补服务端 `IKeyguardInterface.Stub` 实现（不需要第 3 个 aidl）。理由与文件内容见 §D.2。

---

## A. 类清单表（类全名 / 文件:行 / 职责 / 判定）

判定口径：
- **必需** = 本包内有活调用链（Trace 到 `SystemUIApplication`/`StatusBar`/真正会跑的 UI）；
- **可选** = 代码完整但本包内无活调用方（或仅被其它「可选」类调用），外部方（launcher/兄弟进程）可能按约定调用 → **保留声明/保留实现二选一**；
- **dead** = 既无活调用链、也无外部可达路径（未 exported、无跨进程协议），复刻可删；
- **移除** = 云控/上报/权限探测类（引 RISK-MAP），复刻必须不打包。

### A.1 Keyguard（锁屏 AIDL 桥）

| # | 类全名 | 文件:行 | 职责 | 判定 |
|---|---|---|---|---|
| 1 | `com.android.systemui.IKeyguardInterface` | `com\android\systemui\IKeyguardInterface.java:10`（接口方法 `:41-49`，事务码 `:52-59`，`onTransact` `:82-120`，本地代理 `:122-227`，`setDefaultImpl` `:229-242`） | 客户端 AIDL：锁屏查询 / 请求解锁 / 注册回调解锁状态 | **必需** |
| 2 | `com.android.systemui.IKeyguardCallbackInterface` | `com\android\systemui\IKeyguardCallbackInterface.java:10`（方法 `:27-29`，事务码 `:32-36`，`onTransact` `:59-77`，代理 `:79-131`） | 服务端回调 AIDL：`onKeyguardDismissed()` / `onKeyguardShown()` | **必需** |
| 3 | `com.android.systemui.statusbar.e`（`StatusBarKeyguardViewManager`） | `com\android\systemui\statusbar\e.java:5`（`a()` `:6`；`a(Runnable)` `:8`；`mo507a()` `:11`；`b(Runnable)` `:13`；`mo508b()` `:16`） | 锁屏视图管理抽象（本包只有这一个实现） | **必需** |
| 4 | `com.android.systemui.statusbar.f`（`StatusBarKeyguardViewManagerImpl`） | `f.java:17`（动作常量 `:19`，ServiceConnection `:34-56`，ctor `:58-61`，`a()`→`c()` `:64-66`，`isLocked` `:70-81`，`isSecure` `:85-95`，`executeRunnableDismissingKeyguard` `:98-117`，`onKeyguardDismissed` `:120-123`，`b()` 执行队列 `:125-136`，`onKeyguardShown` `:139-145`，`b(Runnable)` `:148-150`，`c()` pingBinder 重连 `:152-160`，`a(Context)` bindService `:162-166`） | 唯一实现；bind `com.xtc.i3launcher` 的 `com.xtc.systemui.keyguard.KeyguardService` | **必需** |
| 5 | `com.android.systemui.Dependency`（仅 2 行与本模块相关） | `Dependency.java:193`（`put(statusbar.e.class, lambda)`）、`:320`（`return new com.android.systemui.statusbar.f(this.a)`） | DI 绑定；`Dependency.b(statusbar.e.class)` 是所有调用方拿实例的唯一方式 | **必需** |
| 6 | `com.xtc.ultraframework.keyguard.KeyguardEventManager` | `com\xtc\ultraframework\keyguard\KeyguardEventManager.java:14`（`Listener` `:20-41`，`registerListener` `:54-61`，`unregisterListener` `:63-70`，`setLockScreenShown` `:72-87`，`CallbackStub` `:89-148`） | **对外 SDK**：走 `IWindowManager.registerKeyguardListener` + `com.android.internal.policy.IKeyguardEventCallback` + `ActivityManagerNative.setLockScreenShown`，给「外部 App 监听锁屏/休眠/唤醒/抬手（onLaunchFromHall）」用 | **可选**（本包内 0 引用；纯 SDK 面，复刻可整体丢弃） |
| 7 | `com.xtc.utils.ui.k`（一行相关） | `com\xtc\utils\ui\k.java:99` | `KeyguardManager.inKeyguardRestrictedInputMode()` 工具方法（与上面的 AIDL 无关，别混淆） | 可选（属 utils 模块） |

> ⚠️ **命名陷阱**：`com.xtc.statusbar.f` 不是锁屏类，而是 `NotificationDataCenter`（`com\xtc\statusbar\f.java:15`）。锁屏实现只指 `com.android.systemui.statusbar.f`。

**调用链（谁在用 keyguard 桥）**：

| 调用点 | 证据 | 语义 |
|---|---|---|
| `StatusBar` 收到 `com.android.common.library.b.m`（`ACTION_LAUNCH_COMPLETED` 类）广播 | `statusbar\phone\StatusBar.java:375` | 桌面启动完成后 `e.a()` → 重新 ping 并 bind（服务重启后自愈） |
| `StatusBar.f(true)`（面板展开） | `StatusBar.java:845` | 展开面板时同样 `e.a()` |
| `ActivityStarter.postStartActivityDismissingKeyguard(Intent,int,cb)` | `StatusBar.java:907-921`（`eVar.a(runnable)`） | **核心语义**：先判断是否锁屏；已解锁立即执行，锁屏则 `requestDismissKeyguard()` 并把 runnable 挂到 `f981a`，等 `onKeyguardDismissed()` 再执行 |
| `postStartActivityDismissingKeyguard(PendingIntent,cb)` | `StatusBar.java:952-977` | 同上 |
| `postQSRunnableDismissingKeyguard(Runnable)` | `StatusBar.java:1000-1024` | 同上（QS 磁贴动作先解锁） |
| 面板状态判断 | `StatusBar.java:1559`（`k()`）、`:1563`（`l()`） | `StatusBarProxyProvider` 用的 `isStatusBarExpanded/FullyExpanded` 依赖它们（见 §B.4.1） |
| QS 磁贴点击前判断锁屏 | `qs\tiles\g.java:189`、`qs\tileimpl\d.java:213` | `Dependency.b(statusbar.e.class).mo507a()`，锁屏时磁贴点击走解锁流程 |
| `StatusBar` 字段/工具 | `StatusBar.java:1548-1550`（`i()`=`w==0`）、`:1553-1555`（`j()`=`w==2`）、`:1558-1560`（`k()`） | windowState 语义：0=VISIBLE，2=HIDDEN |

### A.2 隐私 / 自启动 / 权限说明

| # | 类全名 | 文件:行 | 职责 | 判定 |
|---|---|---|---|---|
| 8 | `com.xtc.ui.widget.privacy.PrivacyUriActivity` | `com\xtc\ui\widget\privacy\PrivacyUriActivity.java:29`（`onCreate` `:38-53`，`buildQrCode` `:55-78`，`createQRImage` `:80-120`，`start/startV2` `:122-153`） | **二维码协议页**（不是协议弹窗！）：把 H5 URL 生成 130dp 二维码 + 一行提示文字 | **可选**（仅被 `PrivacyFirstDialog` 调；`singleTop`，未 exported） |
| 9 | `com.xtc.ui.widget.privacy.PrivacyFirstDialog` | `PrivacyFirstDialog.java:25`（ctor `:36-46`，`init(PrivacyBean)` `:52-116`，`onClick` `:118-146`，`show()` `:148-161`，`dismissDelay` `:163-170`，`dismiss` `:172-184`，`fullList` `:186-194`） | **首启隐私/权限协议弹窗**（全屏黑色、双按钮「不同意 / 同意」） | **可选**（唯一入口 `DialogUtil.makePrivacyDialog*`，本包 0 调用） |
| 10 | `com.xtc.ui.widget.privacy.PrivacyBean` | `PrivacyBean.java:7`（字段 `:8-19`，`toString` `:117-119`） | 弹窗数据模型：3 个文案列表 + 3 个 URL + appName/firstTitle/secondTitle + packageName(Alias) + internetAccess | **可选** |
| 11 | `com.xtc.ui.widget.privacy.PrivacyCommon` | `PrivacyCommon.java:4`（`PrivacyExtras` `:11-17`，`UrlType` `:19-23`，`PrivacyComponentVersion` `:6-9`） | Intent extra key 常量与 URL 类型常量 | **可选** |
| 12 | `com.xtc.ui.widget.privacy.PrivacyUtilities` | `PrivacyUtilities.java:20`（`jointUrl` `:143-188`，`jointUrlV2` `:190-231`，`getAppVersion` `:233-244`，`getExtra/generateRandomString` `:246-257`，`getUrlTypeString` `:259-264`，`dealH5Domain` `:266-296`；机型/区域探测 `:66-141`） | URL 拼参（机型/包名/语言/品牌/type）+ 按 139 字符阈值补随机 `extra`（常量 `MIN_LENGTH=139`/`MAX_LENGTH=156`，实际只用 139）+ H5 域名替换 | **可选** |
| 13 | `com.xtc.ui.widget.privacy.XTCPrivacy` | `XTCPrivacy.java:9`（`PrivacyResponse` `:12-22`，`requestPrivacy` `:24-75`，`onDestroy` `:77-86`） | 对外的「申请隐私授权」门面（6 个重载） | **可选** |
| 14 | `com.xtc.ui.widget.privacy.IPrivacyDialogListener` | `IPrivacyDialogListener.java:5` | 回调：`allow()` / `refuse()` / `getH5Domain()` | **可选** |
| 15 | `com.xtc.ui.widget.privacy.CustomAppInfo` | `CustomAppInfo.java:8`（Gson `@SerializedName`） | V2 弹窗入参（可覆写文案/权限理由） | **可选** |
| 16 | `com.xtc.ui.widget.privacy.AboutEntranceView` | `AboutEntranceView.java:13` | 「关于」入口 View | **dead**（0 引用、无布局引用） |
| 17 | `com.xtc.ui.widget.util.DialogUtil`（仅隐私/CTA 工厂方法） | `DialogUtil.java:174-215`（7 个 `makePrivacyDialog` 重载）、`:218-246`（`makePrivacyDialogV2`）、`:248-254`（`makeCtaPermissionDialog`）、`:163-172` | 弹窗工厂 | **可选** |
| 18 | `com.xtc.ui.widget.util.CtaPermissionUtil` | `CtaPermissionUtil.java:25`（常量 `:26-45`：`LAUNCHER_SELF_START_URI` `:27`、meta-data key `:28-31`、`METHOD_SELF_START` `:32`、`ANDROID_PERM_NAME` 20 条 `:42`、`DANGEROUS_NAME` 14 条 `:43`）；`getMessageBody` `:51-60`；`isRuntime/isDangerous` `:62-74`；`getAuthorityInfos` `:76-113`；`getPrivacy` `:115-207`；`showPermissionReminder` `:209-211`；`setPackageWhiteListByLauncher` `:216-297`；`getApplicationName` `:299-306` | **本模块核心数据源**：权限名→中文标签映射（`@array/permission_label` 下标对齐 `ANDROID_PERM_NAME`）、危险权限分组、meta-data 读隐私 URL、同意后写 launcher 自启白名单 | **可选**（但 `@array/permission_label` 与文案表 **必须保留**） |
| 19 | `com.xtc.ui.widget.permission.PermissionCompat` | `com\xtc\ui\widget\permission\PermissionCompat.java:9` | CTA 权限兼容工具 | **dead**（全包 0 引用，仅自身文件；与 `androidx` 的 `PermissionCompatDelegate` 无关） |
| 20 | `com.xtc.ui.widget.permission.cta.CtaPermissionView` | `CtaPermissionView.java:21`（`initView` `:40-59`，`setCallBack` `:61-80`，`initData` `:82-91`） | 权限说明列表 View（RecyclerView + 双按钮） | **可选** |
| 21 | `com.xtc.ui.widget.permission.cta.CtaPermissionDialog` | `CtaPermissionDialog.java:16`（ctor `:22-32`，`initData` `:38-40`，`initListener` `:42-73`，`show` `:79-92`，`dismiss` `:94-98`） | 上述 View 的 Dialog 包装 | **可选** |
| 22 | `com.xtc.ui.widget.permission.cta.adapter.CtaPermissionAdapter` | `CtaPermissionAdapter.java:15`（3 种 item：head `:48-50` / tip `:51-53` / item `:54`；`onBind` `:57-70`，head 字号随 `hasTip` 16sp/20sp `:66`） | 列表适配器 | **可选** |
| 23 | `com.xtc.ui.widget.dialog.bean.noIcon.CtaPermissionBean` | `CtaPermissionBean.java:6`（4 参数 ctor `:20-24`） | `(data, title, tip, isAppStore)` | **可选** |
| 24 | `com.xtc.ui.widget.permission.cta.interfaces.ICtaPermissionCallback` | `ICtaPermissionCallback.java:5` | `allow()`/`refuse()` | **可选** |
| 25 | `com.xtc.architecture.mvp.PermissionListActivity` | `PermissionListActivity.java:13`（`onNewIntent` `:14-18`，`onCreate` `:20-24`，`a(Intent)` `:26-62`） | 「权限说明整页」（`CtaPermissionView` 直接 `setContentView`），同意后把原 Intent 带上 `EXTRA_CTA_PERMISSION_ALLOWED=true` 继续 startActivity | **可选**（近死：仅 `PermissionUtil` 可启动，而无 `mo1212a()==true` 的子类） |
| 26 | `com.xtc.architecture.mvp.b.a`（`PermissionUtil`） | `mvp\b\a.java:14`（extra key `:15-18`，运行时权限结果分发 `:20-40`，申请权限 `:42-54`，**启动 PermissionListActivity** `:56-62`，`MvpActivity` 钩子 `:64-72`） | 权限工具 | **可选** |
| 27 | `com.xtc.architecture.mvp.BaseActivity` | `mvp\BaseActivity.java:58`（`d()` 里 `mvp.b.a.a(this,getIntent(),this)`） | 唯一可能触发 #26 的地方（`FlashLightActivity`/`FlashLightSOSDialog` 继承它，但 `mo1212a()` 恒 false） | 可选 |

### A.3 自启动

| # | 类全名 | 文件:行 | 职责 | 判定 |
|---|---|---|---|---|
| 28 | `com.xtc.ui.widget.selfstart.SelfStartActivity` | `SelfStartActivity.java:13`（`onCreate` `:18-24`，`initData` `:26-33`，`showOpenSelfStartPermissionDialog` `:35-60`，`onDestroy` `:62-69`，`finish` `:71-75`） | 「去设置开启自启动」提示页（透明 Activity + `DoubleFlatBtnDialog`，`overridePendingTransition(0,0)`） | **可选**（仅 `SelfStartUtils` 可启动；未 exported） |
| 29 | `com.xtc.ui.widget.selfstart.SelfStartConstant` | `SelfStartConstant.java:4`（常量 `:5-10`） | `ACTION_SETTINGS_SELF_START="com.xtc.setting.action.SELF_START"`、`LAUNCHER_SELF_START_URI="content://com.xtc.launcher.self.start"`、`METHOD_GET_PACKAGE`、extra key | **可选** |
| 30 | `com.xtc.ui.widget.selfstart.SelfStartUtils` | `SelfStartUtils.java:16`（`checkSelfStartReminded` 3 重载 `:20-65`，`isHadSelfStartPermission` `:79-127`，`isTargetActivityExist` `:129-138`） | 查 launcher provider 判断是否已有自启权限；没有且设置页存在 → 起 `SelfStartActivity` | **可选** |
| 31 | `com.xtc.ultraframework.os.SelfStartManager` | `SelfStartManager.java:17`（`@Deprecated` `:16`；SP 名 `:22`；广播 `:18`；`createPackageContext("com.xtc.i3launcher")` `:38`；`getWhiteList` `:58-72`；`updateWhiteListNew` `:102-142`） | **对外 SDK**：直接读写 launcher 的 SharedPreferences `watch_boot_start` 实现自启白名单 | **可选**（本包内仅被 `ActivityManagerEx` 用，`@Deprecated`） |

### A.4 富文本话术

| # | 类全名 | 文件:行 | 职责 | 判定 |
|---|---|---|---|---|
| 32 | `com.xtc.utils.system.w`（`StringResourceUtils`） | `w.java:7`（`a(Context,int)` `:8-10`，`m2043a(Context,int)` 数组版 `:13-19`，**`a(String)` 品牌替换 `:21-26`**） | `${XTC}` → 「小天才」（CN/HK/TW）或「imoo」（其它区域） | **必需** |
| 33 | `com.xtc.statusbar.AntiAddictionExitLayout` | `AntiAddictionExitLayout.java:14`（定界符 `"$"`）、`:38-43`（`a(title,content)` 绑定）、**:67-104（`$…$` 解析+高亮+去标记）** | 「如何退出应用禁用/限额」全屏提示，是 `$…$` 的唯一渲染器 | **必需** |
| 34 | `com.xtc.statusbar.DisabledForbidLayout` | `DisabledForbidLayout.java:57`（点 `tv_hint` → `j.a(ctx).a(getString(how_to_exit_app_forbid), w.a(ctx, how_to_exit_app_forbid_content))`）、`:71`（`app_forbid_remind` 带 `%s`） | 「应用禁用」全屏页（复用链上的人） | **必需** |

### A.5 Provider / 代理 / 文件

| # | 类全名 | 文件:行 | 职责 | 判定 |
|---|---|---|---|---|
| 35 | `com.android.systemui.statusbar.provider.StatusBarProxyProvider` | `StatusBarProxyProvider.java:16`（常量 `:17-26`），**`call()` `:71-125`**，**调用方校验 `a()` `:127-137`** | 跨进程状态栏代理（launcher 用）：查询展开态 / 收起面板 / 转发 IM 在线态 | **必需** |
| 36 | `com.android.systemui.n`（`SysUiServiceProvider`） | `n.java:7`（`c(Class)` `:8`，静态 `a(Context,Class)` `:10-12`） | Provider 里取 `StatusBar` 实例的唯一入口 | **必需** |
| 37 | `com.android.systemui.SystemUIApplication`（`implements n`） | `SystemUIApplication.java`（`c()` 为 `this.f443a.get(cls)` 的 HashMap 查表） | 组件注册表；`StatusBar` 未启动时返回 null（provider 需要判空） | **必需** |
| 38 | `com.xtc.baseservice.e.a`（`IMStateManager`，单例） | `com\xtc\baseservice\e\a.java:12`（`InterfaceC0051a` `:22-24`，`a()` 单例 `:26-34`，`a(boolean online)` `:44-46`，弱引用分发 `:48-64`） | IM 在线态分发中心（provider 直接喂它） | **必需** |
| 39 | `androidx.core.content.FileProvider` + `res/xml/fileprovider.xml` | 类：`androidx\core\content\FileProvider.java:25`；路径：`nx\resources\res\xml\fileprovider.xml:1-6`；清单：`AndroidManifest.xml:124-132` | 对外文件 URI 授权（`grantUriPermissions=true`, `exported=false`） | **必需**（零自研代码，保留声明即可） |
| 40 | `com.xtc.dns.provider.HttpDnsContentProvider` | `HttpDnsContentProvider.java:19`（authority 注册 `:38`，`query` `:43-63`，**`getType` 当命令通道 `:67-111`**） | 自研 HTTP-DNS 跨进程缓存 | **移除**（R-43/R-73） |
| 41 | `com.xtc.dns.provider.a`（`CourseProviders`） | `com\xtc\dns\provider\a.java:6`（authority 模板 `%s.com.xtc.httpdns.provider`）、`:7`（`DomainModel/*`）、`:8-11`（4 个命令）、`:15-17`（`isHttpDnsEnable`）、`:20-29`（列名） | 协议常量 | **移除**（R-73） |
| 42 | `com.xtc.dns.client.b`（`HttpDnsManager`） | `client\b.java:52-62`（`resolveContentProvider("<pkg>.com.xtc.httpdns.provider")` 探测 → 命中才用 HTTP-DNS，否则回落） | 客户端探测 | **移除**（R-48/R-49）；**注意回落语义**：探测返回 null 即回落系统 DNS，不崩 |
| 43 | `com.xtc.dns.client.b.b`（`DnsResolver`） | `client\b\b.java:34-49`（4 个命令全走 `ContentResolver.getType(...)`）、`:58-122`（`query` 读 `ip` 列，`InetAddressValidator` 判 IP） | 命令+查询协议实现 | **移除**（R-48） |

### A.6 Toast / 提示窗

| # | 类全名 | 文件:行 | 职责 | 判定 |
|---|---|---|---|---|
| 44 | `com.xtc.ui.widget.toast.view.ToastUtil` | `ToastUtil.java:14`（去重窗口 `:15-16`+`:105-108`；TN handler hook 吞异常 `:36-55`；`showShortCover/showLongCover` `:79-98`；`showCoverImpl` `:101-127`；`ComponentCallbacks` 换语言清缓存 `:25-34`） | **系统 Toast 封装**：2s/3.5s 内同文去重、反射 hook `Toast$TN.mHandler` 防 `BadTokenException` | **必需** |
| 45 | `com.xtc.ui.widget.toast.view.ToastView` | `ToastView.java:15`（`initView` `:38-44`，`showToast` `:46-72`，`cancelToast` `:74-81`） | 带 `HorizontalProgressBar` 的自定义 Toast | **dead**（全包 0 引用，`layout_toast` 只被它引用） |
| 46 | `com.xtc.baseservice.util.i`（`InnerToastUtils`） | `i.java:12`（`a(TextView)` 绑定 `:47-49`，`a(String)` 显示 2s `:51-63`，`m1338a()` 立即隐藏 `:76-79`） | **状态栏内嵌 Toast**（`tv_toast_inner`，非系统 Toast，在状态栏窗口内） | **必需** |
| 47 | `com.xtc.statusbar.j`（`PopWindowManager`） | `j.java:19`（单例 `:78-88`；`WindowManager.LayoutParams` 工厂 **type=2002**、`gravity=TOP|LEFT`、全屏、`softInputMode=3`、`setTitle()` `:105-118`；`a(View,String)` 加窗 `:125-137`；`a(View)` 移除 `:139-156`；`a(title,content, lp, cb)` HintWindow `:185-198`；`a(title,content,cb)` `:205-208`；`a(appName)` DisabledForbid `:219-231`；`a(title,content)` AntiAddiction `:234-246`；`d()` 清空 `:275-289`；广播/来电自动收起 `:46-70`） | **全屏提示窗宿主**（加在 WindowManager，不走 Activity） | **必需** |
| 48 | `com.xtc.statusbar.HintWindowLayout` | `HintWindowLayout.java:12`（`a(ctx)` inflate `:36-54`，`a(title,content)` `:61-64`） | 「温馨提示」窗（绿标题 + 白正文 + 「我知道了」+ 下滑关闭） | **必需** |
| 49 | `res/layout/layout_read_remind.xml` | `layout_read_remind.xml:1-9`（`tv_read_remind`） | 阅读提醒占位布局 | **dead**（0 引用） |

**A 节统计：共 49 条记录（48 个类/接口 + 1 个纯资源布局 `layout_read_remind`；`androidx.FileProvider` 与 `res/xml/fileprovider.xml` 合并为 #39 一条）= 必需 17 / 可选 24 / dead 4 / 移除 4。**

- 必需（17）：#1 #2 #3 #4 #5（keyguard）+ #32 #33 #34（富文本）+ #35 #36 #37 #38 #39（provider/代理/文件）+ #44 #46 #47 #48（toast/提示窗）
- 可选（24）：#6 #7（SDK 面）+ #8~#15 #17 #18 #20~#27（隐私/CTA/权限页 18 条）+ #28~#31（自启动 4 条）
- dead（4）：#16 `AboutEntranceView`、#19 `PermissionCompat`、#45 `ToastView`、#49 `layout_read_remind`
- 移除（4）：#40 `HttpDnsContentProvider`、#41 `dns.provider.a`、#42 `dns.client.b`、#43 `dns.client.b.b`

---

## B. 原逻辑（入口 / 调用链 / 状态机 / 落盘 key / AIDL 签名与语义）

### B.1 Keyguard AIDL（签名 + 语义 + 权限 + 调用方推断）

#### B.1.1 `IKeyguardInterface`（客户端接口，descriptor = `com.android.systemui.IKeyguardInterface`）

| 事务码 | 方法签名 | 语义（本包怎么用） |
|---|---|---|
| 1 | `boolean isKeyguardLocked()` | 是否处于锁屏态。**异常/服务未连 → 返回 true（保守当锁屏）**（`statusbar\f.java:76`、`:79`） |
| 2 | `boolean isKeyguardSecure()` | 是否安全锁屏（有密码/PIN）。同样异常返回 true（`f.java:90`、`:93`） |
| 3 | `void requestDismissKeyguard()` | 请求外部解锁；**解锁成功后由服务回调 `onKeyguardDismissed()` 续跑挂起动作**（`f.java:105`） |
| 4 | `void registerCallback(IKeyguardCallbackInterface cb)` | 服务连接成功后立刻注册（`f.java:38-43`） |
| 5 | `void unregisterCallback(IKeyguardCallbackInterface cb)` | 断连时反注册（`f.java:50-53`）——注意原实现**先**反注册、**后**置 `f980a=null`；若连接从未建立（`f980a==null`，如 bind 失败后立即 disconnect）会 NPE 并被 catch 吞掉，属原实现瑕疵 |

- **权限校验**：AIDL 桩里**没有任何** `enforceCallingPermission`/`checkCallingPermission`。约束全部靠**系统 Binder 权限模型**：
  - SystemUI 侧声明 `uses-permission com.xtc.systemui.keyguard.permission.KEYGUARD_SERVICE`（`AndroidManifest.xml:56`）——该权限由 launcher 定义（`protectionLevel` 不在本包，推断 signature|privileged），所以 **SystemUI 必须系统签名才能 bind**；
  - 绑定用**显式动作 + `setPackage("com.xtc.i3launcher")`**（`statusbar\f.java:163-165`），`bindService` flag=`1`（`BIND_AUTO_CREATE`）；
  - 也就是说：**服务端（launcher）在 `onBind` 里做权限校验**，客户端无校验。
- **调用方推断（外部谁在用）**：本包没有任何 `IKeyguardInterface` 的**服务端实现**（grep 全包仅客户端桩 + 默认实现 `a`）。该接口的**实现方是 `com.xtc.i3launcher` 的 `com.xtc.systemui.keyguard.KeyguardService`**。反过来的 `IKeyguardCallbackInterface` 的**实现方是 SystemUI**（`statusbar\f`）。→ 复刻若继续用外部 launcher 的锁屏，保持 AIDL 二进制兼容即可；若自建锁屏，则**本包要开始实现 `IKeyguardInterface` 的 Stub** 并自己 `addService`/`publishBinderService`。
- **重连状态机**（`statusbar\f.java`）：
  ```
  new f(ctx) → a() → c() → 若无 binder 或 pingBinder 失败 → a(ctx) bindService(ACTION, pkg=i3launcher, BIND_AUTO_CREATE)
  onServiceConnected → asInterface(binder) → registerCallback(this)
  onServiceDisconnected → unregisterCallback(this)（忽略异常）→ f980a=null
  onKeyguardShown  → 清空 f981a（待执行动作）+ 清空 f982a（延后队列）
  onKeyguardDismissed → b()：先跑 f981a，再依次跑 f982a（各只跑一次，随后 clear）
  ```
  - `a(Runnable)`：锁屏→记到 `f981a` + `requestDismissKeyguard()`；未锁屏→立即 `b()`。
  - `b(Runnable)`：把 runnable **放入 `f982a` 队列**（用于 `onKeyguardDismissed` 后执行），注意它不触发解锁。
  - 复刻要点：`f981a` 单槽 + `f982a` 队列的双轨设计必须照抄，否则「解锁后动作丢失/重复」都会出现。

#### B.1.2 `IKeyguardCallbackInterface`（服务端回调，descriptor = `com.android.systemui.IKeyguardCallbackInterface`）

| 事务码 | 方法签名 | SystemUI 侧行为 |
|---|---|---|
| 1 | `void onKeyguardDismissed()` | 解锁完成 → 执行挂起动作（`f.java:120-123`） |
| 2 | `void onKeyguardShown()` | 上锁 → **丢弃**所有挂起/排队动作（`f.java:139-145`） |

#### B.1.3 `com.xtc.ultraframework.keyguard.KeyguardEventManager`（SDK 面，与 AIDL 桥无关）

- 通道：`IWindowManager.registerKeyguardListener(IKeyguardEventCallback)` / `unregisterKeyguardListener`（`KeyguardEventManager.java:57`、`:66`）；回调方法 7 个：`onStartedGoingToSleep/onFinishedGoingToSleep/onStartedWakingUp/onFinishedWakingUp/showKeyguard(String)/hideKeyguard(String)/onLaunchFromHall`（`:100-147`）。
- `setLockScreenShown(boolean)`：API≥30 走 `ActivityTaskManager`，>25 走 `ActivityManagerNative.setLockScreenShown(z,-1)`，>23 走 `(z,false)`，更老走 1 参版（`:72-87`）。本包 minSdk 27 → 实际走 `(z,-1)` 分支。
- 本包内 **0 调用**；它属于 `com.xtc.ultraframework` 对外 SDK（`NoClassDefFoundError` 时抛 `NoSuchServiceException("miss IKeyguardEventCallback")`，`:43-52`）。

### B.2 隐私协议弹窗流程（PrivacyFirstDialog）

```
调用方（外部 App/兄弟模块）
  → XTCPrivacy.requestPrivacy(ctx, pkg, alias, firstTitle, map, response)   [XTCPrivacy.java:36-75]
      ├ response.shouldRequest()==false → response.onHadAllowed()   // 同意态由调用方自己持久化！
      └ 是 → DialogUtil.makePrivacyDialog(...) → PrivacyFirstDialog.init(PrivacyBean) → show()
  → 用户点「同意」:
      dismissDelay() 100ms 关闭
      CtaPermissionUtil.setPackageWhiteListByLauncher(ctx, bean.getPackageName())   // 写 launcher 自启白名单
      listener.allow() → response.onAllow()
  → 用户点「不同意」:
      dismissDelay() 100ms 关闭
      listener.refuse() → response.onRefuse()
```
- **同意态落盘 key**：**SystemUI 自己不落盘任何同意态**（`com.xtc.ui.widget.privacy.*` 里没有任何 `SharedPreferences`）。落盘发生在两处外部：
  1. `shouldRequest()/onAllow()/onHadAllowed()` 由**调用方**实现并自行持久化（`XTCPrivacy.PrivacyResponse`，`XTCPrivacy.java:12-22`）；
  2. 「同意」时通过 **`content://com.xtc.launcher.self.start`** 的 `call("METHOD_SELF_START", packageName, null)` 让 launcher 写入自启白名单（`CtaPermissionUtil.java:216-297`，返回值 `Bundle.getString("SELF_START_STR")=="SUCCESS"`）；launcher 侧落盘位置（由 `SelfStartManager` 反推）：**`com.xtc.i3launcher` 的 SharedPreferences 文件 `watch_boot_start`，key=应用包名，value=boolean true / int 1**，写入后发广播 `com.xtc.selfstart.PREF_CHANGE_ACTION`（`SelfStartManager.java:18-24`、`:38`、`:134-142`）。
- **二次弹窗逻辑**：`PrivacyFirstDialog.version` —— `PrivacyFirstDialog(ctx)` = version 1；`PrivacyFirstDialog(ctx, 2)` = V2（`DialogUtil.makePrivacyDialogV2`，`DialogUtil.java:235`）。差异只体现在**点协议链接时是否用 URL V2**：`PrivacyUriActivity.startV2(..., z=version>=2)`（`PrivacyFirstDialog.java:127`、`:131`），`startV2` 会多传 `EXTRA_VERSION=2`（`PrivacyUriActivity.java:149-151`），`createQRImage` 里 `if (version < 2) str = jointUrl(...)`（`PrivacyUriActivity.java:86-88`）→ **V1 会二次拼参（`wModel/pkg/lang/appBrand/type` + `extra` 随机补齐），V2 直接拿原 URL**。注意：`PrivacyUtilities.jointUrlV2()`（`:190-231`）**在本包内无调用方**（死方法）。
- 弹窗内容分支（`PrivacyFirstDialog.java:76-110`）：
  | 条件 | `tv_title1` | `ll_list1` |
  |---|---|---|
  | 有危险权限 && `internetAccess` | 显示，文案 `privacy_dangerous_permissions_title` | 显示，逐条 `item_privacy` |
  | 无危险权限 && `internetAccess` | 显示，文案 `privacy_dangerous_permissions_title_only_net`，`gravity=center` | 隐藏 |
  | 有危险权限 && !`internetAccess` | 显示，文案 `privacy_list_titile2` | 显示 |
  | 都没有 | `GONE` | `GONE` |
  `tv_privacy/tv_agreement/tv_disclaimer` 仅当对应 URL 非空才 `VISIBLE`（`:96-110`）；三个链接分别用 `PRIVACY_TYPE=1 / AGREEMENT_TYPE=2 / OTHER_TYPE=0`、`custom_string` 分别 `""`/`privacy_agreement`/`privacy_disclaimer`。
- 防连点：`onClick` 里 `SystemClock.elapsedRealtime()` 500ms 去抖（`:120-124`）。
- 全屏：`show()` 里 `width=height=-1` + `setPadding(0,0,0,0)`（`:148-161`）；主题 `dialog_default_style_forbidSwipe`（禁下滑关闭）。
- 权限清单数据来源（`CtaPermissionUtil.getPrivacy`，`:119-207`）：
  - `PackageManager.getPackageInfo(pkg, 4224)` = `GET_PERMISSIONS|GET_META_DATA`；
  - meta-data：`privacy_url` / `agreement_url` / `disclaimer_url` / `required_permissions`(Gson `List<String>`)
  - `requestedPermissions` 逐个 `getPermissionInfo`；`isRuntime()`（`PERM_LABELS.contains(name) || protectionLevel&15==1`）且（在 20 条表内 或 系统特性 `android.software.grant_single_permission`）；
  - 分三桶：`list2`（危险且不在 `required_permissions`）、`list3`（危险且在 required）、`list1`（非危险）；
  - 文案 = `privacy_list_format_permission_reason`（`"%s，%s"`）格式化成「标签，理由」；理由来自 `CustomAppInfo.permissionReasons`；标签可用 `permissionLabels` 覆写，`"permission_no_use"` 表示不用该标签。

### B.3 自启动流程（SelfStart）

```
SelfStartUtils.checkSelfStartReminded(ctx, pkg, contentText, cb)     [SelfStartUtils.java:28-65]  // RxJava: subscribeOn(io), observeOn(main)
  → isHadSelfStartPermission(ctx, pkg)                               [:79-127]
       ContentResolver.acquireUnstableContentProviderClient(content://com.xtc.launcher.self.start)
       provider==null → return true（视为已有权限，不打扰用户）
       Bundle b = client.call("METHOD_GET_PACKAGE", pkg, null)
       return b.getBoolean("EXTRA_SELF_START_PACKAGE")   // 无 bundle → true
  → 若无权限 → isTargetActivityExist(ctx): PackageManager.resolveActivity(action=com.xtc.setting.action.SELF_START, MATCH_ALL=65536) != null（结果缓存静态变量）
  → 需要提醒 → startActivity(new Intent(ctx, SelfStartActivity).putExtra(EXTRA_CONTENT_TEXT, text).putExtra(EXTRA_PACKAGE_NAME, pkg))
SelfStartActivity.onCreate → DoubleFlatBtnDialog（禁滑动关闭）
   标题 = EXTRA_CONTENT_TEXT（自定义话术，左对齐）
   背景 = R.color.color_ff000000
   按钮左 = R.string.cancel（取消 → finish）
   按钮右 = R.string.self_start_go_to_set（去设置）
        → new Intent().setAction("com.xtc.setting.action.SELF_START").putExtra("EXTRA_PACKAGE_NAME", pkg)
        → startActivity（**隐式 Intent，未加 FLAG_ACTIVITY_NEW_TASK 的版本靠 Activity context 也没问题；复刻注意 ctx 类型**）
        → finish
finish/onCreate 均 overridePendingTransition(0,0)（无动画）
```
- 落盘 key（自启白名单）：见 §B.2 的 launcher `watch_boot_start` SP。

### B.4 Provider / 代理

#### B.4.1 `StatusBarProxyProvider`

- 清单：`AndroidManifest.xml:133-137` → `authorities="com.xtc.systemui.statusbar.proxy"`、`exported="true"`、`android:permission="com.xtc.systemui.statusbar.permission.StatusBarProxyProvider"`（后者 `protectionLevel=signature`，`:61-63`）。
- 只实现 `onCreate/call`；`delete/getType/insert/query/update` 全空（`StatusBarProxyProvider.java:31-54`）。
- **调用方校验（双层）**：
  1. 系统层：`android:permission` 要求调用方持有 signature 权限；
  2. 代码层：`getCallingPackage()` 必须 `equals("com.xtc.i3launcher")`，否则返回 false → 走 `super.call()`（默认 null）（`:127-137`）。
- **`call` 方法清单**（`str`=method，`str2`=arg，`bundle`=extras）：

| method | extras 入参 | 行为 | 返回 |
|---|---|---|---|
| `isStatusBarExpanded` | — | `StatusBar sb = n.a(ctx, StatusBar.class)`；`expanded = (sb!=null && !sb.m559k() && !sb.m558j())`（`:97-105`） | `Bundle{ "expanded": boolean }` |
| `collapsePanel` | — | `sb.r()`（`StatusBar.r()`=`this.f1070a.post(this.f1085b)`，即异步收起面板，`StatusBar.java:1149-1151`）（`:106-110`） | **落到方法末尾 `return super.call(...)`（实际 null）** |
| `isStatusBarFullyExpanded` | — | `fullyExpanded = (sb!=null && sb.l() && sb.m557i())`（`:115-121`） | `Bundle{ "fullyExpanded": boolean }` |
| `onImStateChanged` | `{ "imState": boolean }`（缺省 false） | `com.xtc.baseservice.e.a.a().a(online)` → `IMStateManager` 分发 `mo1758a(boolean)` 给所有弱引用监听者（`:112-114`）**并且**继续走下面的 fullyExpanded 返回块 | `Bundle{ "fullyExpanded": boolean }` |
| **其它任意 method（含未知命令）** | — | `switch` 未命中时 `b2=-1`，**既不等于 0 也不等于 1 → 落入 `else` 分支**，执行 `onImStateChanged` 同款 fullyExpanded 计算（`:111-122`） | `Bundle{ "fullyExpanded": boolean }`（**不是 null！排障/复刻时必须照抄这个「兜底返回 fullyExpanded」的怪行为**） |

- **状态语义映射（复刻必读）**：
  - `sb.m559k()` = `this.f1072a.f()`，`f1072a` 是 `XTCPanelView`；
  - `sb.m558j()` = `this.w == 2`（`w` = `StatusBarManager` 的 windowState，2=`WINDOW_STATE_HIDDEN`；0=`VISIBLE`，`:1279-1298`）；
  - `sb.l()` = `this.f1072a.e()`；`sb.m557i()` = `this.w == 0`。
  - ⚠️ **JADX 重命名冲突（证据可信度声明）**：`PanelView` 内同时存在 `protected void e()`/`protected void f()`（生命周期回调，`:179`、`:535`）与 `public boolean m521f()/m522g()/m523h()/m524i()`（状态 getter，`:856-892`，注释 `renamed from: f/g/h/i`）。因此 `f1072a.f()` / `f1072a.e()` 的**字面方法名不可直接采信**，语义应取：
    - `m559k()` ≈ **面板「是否未完全收回」判断**（对应 `PanelView.m521f()` = `this.f<=0`（完全收起）的取反，等价 AOSP `PanelView.isPanelExpanded()` 的反面）；
    - `l()` ≈ **面板「是否完全展开」判断**（对应 `PanelView.m523h()` = `f1008b`，即 AOSP `PanelView.isFullyExpanded()`/`isPanelExpanded()` 的 fully 标志）。
    → **复刻建议**：不要照抄 obfuscated 名，直接用 AOSP `PanelView` 的 `isPanelExpanded()`（= `mExpandedFraction>0`/`f1008b` 语义）与 `isFullyCollapsed()`，并在 REVIEW 里注明「原实现经 JADX 冲突重命名，语义按 PanelView 状态位反推」。

#### B.4.2 `FileProvider`

- 清单：`AndroidManifest.xml:124-132` → `androidx.core.content.FileProvider`，`authorities="com.android.systemui.fileprovider"`，`exported="false"`，`grantUriPermissions="true"`，meta-data `android.support.FILE_PROVIDER_PATHS=@xml/fileprovider`。
- 路径策略（`res/xml/fileprovider.xml`）：**只有一条** `<cache-path name="leak" path="leak/"/>` → 可共享目录 = `/data/user/<user>/com.android.systemui/cache/leak/`，URI 形如 `content://com.android.systemui.fileprovider/leak/<file>`。
- 本包内 **0 个 `FileProvider.getUriForFile()` 调用**（全包 grep 仅 androidx 库自身）。→ 保留声明即可（**不要改 authority**，外部/历史调用方按此名授权）。

#### B.4.3 `HttpDnsContentProvider` 原 call 协议（仅备案，复刻移除）

- authority：`String.format("%s.com.xtc.httpdns.provider", context.getPackageName())` → 本包 = `com.android.systemui.com.xtc.httpdns.provider`（`HttpDnsContentProvider.java:38`、`dns\provider\a.java:6`）；manifest `:164-172`（`exported=true` + `readPermission=com.android.systemui.permission.READ_DNS`（`protectionLevel=normal`，`:71-73`）+ meta-data `allowLaunchOnPowerSaveMode=true`）。
- 协议（**注意：命令走 `getType()`，数据走 `query()`**）：

| 通道 | URI | 语义 |
|---|---|---|
| `query` | `content://<auth>/DomainModel/<host>` | 返回 `MatrixCursor`，列 `id,createTime,domain,domainTTL,ip,ipTTL,priority,sp`（`:54-59`）；开关 `com.xtc.dns.b.a.f2507a` 为 false 时返回 null（`:50-53`） |
| `getType` | `.../checkIpExpire/<host>/<ip>` | `c.a(host, ip)`：校验该 host-ip 是否过期（`b\b.java:34-36` → `/checkIpExpire/…`） |
| `getType` | `.../checkIpOverTime` | 单段：检查批量超时（`:98-99`）；两段：`.../checkIpOverTime/<host>` 检查单 host（`:100-101`） |
| `getType` | `.../decreaseIpPriority/<host>` | 降低该 host 的 IP 优先级（`:106-107`） |
| `getType` | `.../clear` | 清空缓存（`:95-96`） |
| 其它 | — | 返回 null |
- 调用方探测（回退行为，**复刻删 Provider 的关键依据**）：`HttpDnsManager.m1533a(pkg)` 用 `PackageManager.resolveContentProvider("<pkg>.com.xtc.httpdns.provider",0)`，**null 才回落本包/系统 DNS**（`dns\client\b.java:52-62`）。→ 删除 Provider 后外部探测得 null → 静默回落系统 DNS，**不会崩**（与 `RISK-MAP R-43` / `IMPACT I-3` 结论一致）。若选择「保留空壳」：authority/权限不变、`query/getType` 直接 `return null`，效果等价。

### B.5 Toast / 提示窗

| 类型 | 实现 | 显示位置/时机 | 触发点 |
|---|---|---|---|
| **内嵌 Toast**（状态栏内） | `com.xtc.baseservice.util.i` + `layout_inner_toast.xml`（`tv_toast_inner`，`super_status_bar.xml:21` `<include>`） | 状态栏窗口内、`gravity=start|center_vertical`、`paddingVertical=8dp`、显示 **2000ms** 自动隐藏、`visibility` 初始 `gone` | 绑定：`StatusBar.java:570`（`findViewById(m.i.tv_toast_inner)`）；显示：`statusbar\policy\q.java:439`（`no_flashlight_when_use_camera`）、`qs\tiles\f.java:169`（`com.xtc.baseservice.f.a().e()` 当前模式名）；立即隐藏：`StatusBar.java:865`（面板完全展开 `m1338a()`） |
| **系统 Toast 封装** | `ToastUtil.showShortCover/showLongCover` | 系统 Toast，`Toast.makeText(appCtx,…)`；同文去重：short 2000ms / long 3500ms 内不重复弹（`ToastUtil.java:105-108`）；反射 hook `Toast$TN.mHandler` 包一层 `SafelyHandlerWrapper.dispatchMessage` 吞异常（`:47-55`、`:57-77`）；语言切换时丢弃缓存 toast（`:30-33`） | `statusbar\policy\q.java:135`（`interrupt_flashlight_when_use_camera`）、`qs\tiles\g.java:58`（`interrupt_screen_record_when_open_camera` / `disable_screen_record_when_open_camera`）、`CopyTextDialog.java:111`（`copy_success`） |
| **全屏提示窗（HintWindow）** | `com.xtc.statusbar.j` + `HintWindowLayout` + `layout_hint_window.xml` | `WindowManager.addView`，`type=2002`（`TYPE_SYSTEM_ALERT`）、全屏、`gravity TOP|LEFT`、标题 `#55dd7b` 加粗 20sp、正文 `#ffffff` 13sp、底部 `LongHollowButton`「我知道了」（描边 `#55dd7b`）、`SwipeDismissLayout` 下滑关闭（`onDismissed(false)`，点按钮是 `onDismissed(true)`） | `screenrecord\a.java:359-361`：标题取 `R.string.init_wx_notice`（=「温馨提示」），正文 `R.string.screen_record_using_tips`（=`screen_record_using_tips`）；`j.a(ctx, title, content, lp, cb)` 通用入口 |
| **应用禁用全屏页** | `DisabledForbidLayout` + `view_disabled_forbid_tip.xml` | 同宿主 `type=2002`；标题 `@string/app_forbid`（应用禁用）、`tv_time`=`app_forbid_remind`(带 `%s`)、`tv_hint`=`how_to_exit_app_forbid`（可点，跳富文本页）、按钮 `i_know` | `j.a(ctx).a(String appName)`：`flashlight\FlashLightSOSDialog.java:105`（手电筒）、`qs\tiles\g.java:202` / `qs\tiles\d.java:183,189` / `qs\tiles\e.java:140`（磁贴名） |
| **退出提示（富文本页）** | `AntiAddictionExitLayout` + `activity_anti_exit_tip.xml` | 同宿主；标题 `#55dd7b` 17sp、正文 `#b3ffffff` 13sp（高亮段纯白） | `DisabledForbidLayout.java:57` → `j.a(ctx).a(how_to_exit_app_forbid, w.a(how_to_exit_app_forbid_content))` |
| **自定义 Toast（带进度条）** | `ToastView` + `layout_toast.xml` | 未使用 | **dead** |

---

## C. UI 元素与文案全清单（供按规则改话术）

### C.1 布局文件 × View id（16 个布局 + 1 个 xml）

| # | 布局文件 | View id → 类型 | 归属类 | 说明/样式 |
|---|---|---|---|---|
| 1 | `activity_privacy_url.xml` | `iv_url` ImageView（130dp×130dp，`bg_privacy_url`，padding 6dp）；`tv_string` TextView（14sp `#888888` 居中） | PrivacyUriActivity | 二维码页 |
| 2 | `dialog_privacy_first.xml` | `tv_title` FixedSizeTextView 15sp `#fafafa`；`tv_title1` 13sp `#808080`（`gone`）；`ll_list1` LinearLayout（`gone`）；`tv_agreement`/`tv_privacy`/`tv_disclaimer` 12sp `#3aec81` + `ic_enter_green`（均 `gone`）；`dfb_operate` DoubleFlatButton | PrivacyFirstDialog | 根 RelativeLayout `#000000` 全屏；外层 `AppScrollView`；边距 `privacy_dialog_margin_edge`（xhdpi 4dp / xxhdpi 8dp） |
| 3 | `item_privacy.xml` | `v_dot` View（5dp，`bg_dot`）；`tv_desc` 15sp `#ffffff` | PrivacyFirstDialog.fullList | 列表项 |
| 4 | `layout_cta_permission_dialog.xml` | `view_cta` CtaPermissionView | CtaPermissionDialog | 仅一层壳 |
| 5 | `view_cta_permission.xml` | `lv_permission` RecyclerView；`dfb_operate` DoubleFlatButton | CtaPermissionView | 根 `#000000` |
| 6 | `item_cta_permission.xml` | `v_dot` View（5dp，`rectangle_radius_3dp_80ffffff`）；`tv_desc` 13sp（`@dimen/dp_13`）`#ccfafafa` | CtaPermissionAdapter | 权限条目 |
| 7 | `item_cta_permission_head.xml` | `tv_title` 20sp `#ffffff`（有 tip 时 16sp，代码 `:66` 动态改） | CtaPermissionAdapter | 头 |
| 8 | `item_cta_permission_tip.xml` | `tv_title` 14sp `#ff7a7a` | CtaPermissionAdapter | 提示（红） |
| 9 | `layout_double_flat_button_dialog.xml` | `rl_root_normal_dialog`（`@style/NormalDialogRoot`，`bg_gauss`）；`tv_title_normal_dialog`（`@style/NormalDialogTitle`）；`tv_bottom` 15sp `#55dd7b`（`gone`）；`btn_bottom` DoubleFlatButton | SelfStartActivity（`DoubleFlatBtnDialog`） | 灰高斯模糊背景 |
| 10 | `layout_double_flat_button.xml`（`<merge>`） | `btn_left` FixedSizeTextView 16sp `#ffffff`；`ll_right` LinearLayout；`btn_right` FixedSizeTextView 16sp；`loading_right` View（`gone`） | DoubleFlatButton | 双按钮 |
| 11 | `layout_hint_window.xml` | `swipe_layout` SwipeDismissLayout；`tv_title` 20sp bold `#55dd7b`（默认文案 `init_wx_notice`）；`tv_content` 13sp `#ffffff`；`btn_i_know` LongHollowButton（高 36dp，描边 `#55dd7b`） | HintWindowLayout | 全屏提示窗 |
| 12 | `activity_anti_exit_tip.xml` | `swipe_layout`；`tv_title` 17sp `#55dd7b`；`tv_content` 13sp **`#b3ffffff`**（`$…$` 高亮变纯白） | AntiAddictionExitLayout | 富文本页 |
| 13 | `view_disabled_forbid_tip.xml` | `swipe_layout`；`tv_title` 20sp bold `#55dd7b`（`@string/app_forbid`）；`tv_time` 13sp `#ffffff`；`tv_hint` 13sp `#ff667379`（`how_to_exit_app_forbid`）；`btn_i_know` | DisabledForbidLayout | 应用禁用页 |
| 14 | `layout_toast.xml` | `layout_toast` RelativeLayout（`toast_bg`）；`tv_toast` 12sp `#ffffff` 居中 `maxWidth=130dp`；`pb_toast` HorizontalProgressBar（280px） | ToastView | **dead** |
| 15 | `layout_inner_toast.xml` | `tv_toast_inner` TextView（`TextAppearance_Toast`、`#ffffffff`、`inner_toast_background`、shadow `#bb000000` 2.75、`visibility=gone`、`marginHorizontal/paddingHorizontal 4dp`、`paddingVertical 8dp`） | InnerToastUtils（宿主 `super_status_bar.xml:21` include） | 状态栏内嵌 |
| 16 | `layout_read_remind.xml` | `tv_read_remind` TextView（无样式） | 无 | **dead** |
| 17 | `xml/fileprovider.xml`（路径声明） | `<cache-path name="leak" path="leak/"/>` | FileProvider | 仅 1 条路径 |

**UI 元素统计：16 个布局文件 + 1 个 fileprovider paths；31 个唯一 View id。**

### C.2 文案全清单（65 条）

> 「改话术影响面」列：🔴=一定会被用户看到（优先改）；🟡=条件出现；⚪=死链/预留（改了也不影响 1.8.00 运行时）。

#### C.2.1 隐私协议弹窗（14 条）

| # | key | 中文原文 | 用在哪 | 影响面 |
|---|---|---|---|---|
| 1 | `privacy_dialog_title` | 为保障你的个人权益，在使用%1$s前，请在父母或其他监护人陪同下阅读并同意用户服务协议及隐私政策： | `tv_title`（appName 非空） | 🟡 |
| 2 | `privacy_dialog_title_empty_app_name` | 为保障你的个人权益，在使用应用前，请在父母或其他监护人陪同下阅读并同意用户服务协议及隐私政策： | `tv_title`（appName 空）/ 布局默认 | 🟡 |
| 3 | `privacy_dangerous_permissions_title` | 本应用需要联网，下列权限仅在你使用对应功能时申请授权 | `tv_title1`（有危险权限 + 联网） | 🟡 |
| 4 | `privacy_dangerous_permissions_title_only_net` | 使用本应用时需要联网 | `tv_title1`（无危险权限 + 联网） | 🟡 |
| 5 | `privacy_list_titile2` | 下列权限仅在你使用对应功能时申请授权 | `tv_title1`（有危险权限 + 不联网） | 🟡 |
| 6 | `privacy_link` | 详情查看隐私政策␠ | `tv_privacy`（布局默认 + 代码设置） | 🟡 |
| 7 | `privacy_link_agreement` | 详情查看用户服务协议␠ | `tv_agreement` | 🟡 |
| 8 | `privacy_link_disclaimer` | 查看免责声明␠ | `tv_disclaimer` | 🟡 |
| 9 | `privacy_agreement` | 用户服务协议 | 点协议链接时作为 `custom_string`（`privacy_custom` 的 `%s`） | 🟡 |
| 10 | `privacy_disclaimer` | 免责声明 | 点免责链接时 `custom_string` | 🟡 |
| 11 | `privacy` | 隐私政策 | 备用（代码未用；`privacy_url` 才用于二维码页默认文案） | ⚪ |
| 12 | `privacy_custom` | 手机扫码查看\n《%s》 | 二维码页 `tv_string`（有 custom_string 时） | 🟡 |
| 13 | `privacy_url` | 手机扫码查看\n《隐私政策》 | 二维码页 `tv_string` 默认 | 🟡 |
| 14 | `privacy_list_titile1/3/4`、`privacy_title1/2/_complete/_no_network_no_permission/_no_permission` | （见 strings.xml:143-151，共 7 条 V2/预留话术） | **无引用**（`getPrivacy` 不用它们） | ⚪ |

> 补充：`privacy_list_format_permission_reason`（`%s，%s`，`formatted="false"`，strings.xml:142）是**权限条目的拼接模板**，属 #15（见下）。

#### C.2.2 权限说明（CTA）（6 条 + 20 条权限标签）

| # | key | 中文原文 | 用在哪 | 影响面 |
|---|---|---|---|---|
| 15 | `privacy_list_format_permission_reason` | %s，%s | 权限条目「标签，理由」 | 🟡 |
| 16 | `cta_allow_permission` | 使用{0}需向你申请以下权限： | `CtaPermissionView.initData` 头（非 appStore） | 🟡 |
| 17 | `cta_allow_permission_pre_icon` | 使用{0}需向你申请应用中心权限： | 同上（`isAppStore=true`） | 🟡 |
| 18 | `cta_allow_permission_multi_entry_app` | 使用{0}及其他同类应用需向你申请以下权限： | 无引用（`MULTI_ENTRY_APP_LIST` 只在 `getPrivacy` 里没用到它） | ⚪ |
| 19 | `get_location_info` | 获取位置信息 | `getAuthorityInfos` 对 `com.xtc.weichat`/`com.xtc.moment` 追加 | 🟡 |
| 20 | `get_contact_info` | 读取联系人信息 | `getAuthorityInfos` 对 `com.xtc.message` 追加 | 🟡 |
| 21 | `agree` / `disagree` / `cancel` / `i_know` / `common_i_know` | 同意 / 不同意 / 取消 / 我知道了 / 我知道了 | 弹窗按钮（`DoubleFlatButton.btn_left` / `ll_right`；`LongHollowButton`） | 🔴 |
| 22 | `self_start_go_to_set` | 去设置 | SelfStartActivity 右按钮 | 🟡 |

#### C.2.3 `@array/permission_label`（20 条，下标与 `ANDROID_PERM_NAME` 一一对应）

| idx | `ANDROID_PERM_NAME[idx]` | `permission_label[idx]` 中文 |
|---|---|---|
| 0 | `android.permission.CALL_PHONE` | 拨打电话 |
| 1 | `android.permission.SEND_SMS` | 接收短信、发送短信 |
| 2 | `android.permission.INTERNET` | 访问网络 |
| 3 | `android.permission.RECORD_AUDIO` | 进行本地录音 |
| 4 | `android.permission.READ_SMS` | 读写短信内容 |
| 5 | `android.permission.READ_CONTACTS` | 读取联系人信息 |
| 6 | `android.permission.WRITE_CONTACTS` | 写入或删除联系人 |
| 7 | `android.permission.READ_CALL_LOG` | 读取通话记录 |
| 8 | `android.permission.WRITE_CALL_LOG` | 修改通话记录 |
| 9 | `android.permission.ACCESS_FINE_LOCATION` | 获取位置信息 |
| 10 | `android.permission.CAMERA` | 拍摄照片和录制视频 |
| 11 | `android.permission.CHANGE_NETWORK_STATE` | 修改网络状态 |
| 12 | `android.permission.ACCESS_WIFI_STATE` | 读取WLAN连接状态 |
| 13 | `android.permission.CHANGE_WIFI_STATE` | 开启或关闭WLAN |
| 14 | `android.permission.BLUETOOTH_ADMIN` | 开启或关闭蓝牙 |
| 15 | `android.permission.READ_PHONE_STATE` | 读取设备通话状态 |
| 16 | `android.permission.ACCESS_COARSE_LOCATION` | 获取位置信息 |
| 17 | `android.permission.READ_EXTERNAL_STORAGE` | 访问媒体影音数据 |
| 18 | `android.permission.WRITE_EXTERNAL_STORAGE` | 访问媒体影音数据 |
| 19 | `org.simalliance.openmobileapi.SMARTCARD` | 智能卡权限 |

> 数据来源：`res/values/arrays.xml:11-32`；消费方 `CtaPermissionUtil.getMessageBody()`（`CtaPermissionUtil.java:51-60`，`PERM_LABELS.indexOf(permName)` 取同下标）。`DANGEROUS_NAME`（14 条，`CtaPermissionUtil.java:43`）用于危险分组。**数组顺序不能动**，否则权限名与文案错配。

#### C.2.4 富文本话术（2 条含标记 + 3 条配套）

| # | key | 原文（default=zh） | 标记位置 | 影响面 |
|---|---|---|---|---|
| 23 | `app_durtaion_tip_content` | `请在最新版$${XTC}APP-更多-应用禁用$中调整$本应用的每日可用时长$或修改$应用禁用设置$，即可正常使用` | 见 §C.2.5 | ⚪（1.8.00 无代码引用，预留给「应用限额」） |
| 24 | `how_to_exit_app_forbid_content` | `请在最新版$${XTC}APP-更多-应用禁用$中，修改$本应用的禁用设置$，即可正常使用` | 见 §C.2.5 | 🔴（`DisabledForbidLayout` 点 `tv_hint` 必现） |
| 25 | `how_to_exit_app_forbid` | 如何退出应用禁用？ | `view_disabled_forbid_tip.tv_hint` | 🔴 |
| 26 | `app_forbid` | 应用禁用 | `view_disabled_forbid_tip.tv_title` | 🔴 |
| 27 | `app_forbid_remind` | 目前“%s”处于禁用状态，无法使用 | `view_disabled_forbid_tip.tv_time`（`%s`=应用/磁贴名） | 🔴 |

#### C.2.5 富文本标记逐条位置表（渲染后高亮段）

| key | 标记 token 序列（`$` = 定界符） | 渲染后被高亮的文本段 | 渲染后纯文本 |
|---|---|---|---|
| `how_to_exit_app_forbid_content` | `$` `$` `{XTC}` `APP-更多-应用禁用` `$` … `$` `本应用的禁用设置` `$` | ①「小天才APP-更多-应用禁用」②「本应用的禁用设置」 | `请在最新版小天才APP-更多-应用禁用中，修改本应用的禁用设置，即可正常使用` |
| `app_durtaion_tip_content` | `$` `$` `{XTC}` `APP-更多-应用禁用` `$` … `$` `本应用的每日可用时长` `$` … `$` `应用禁用设置` `$` | ①「小天才APP-更多-应用禁用」②「本应用的每日可用时长」③「应用禁用设置」 | `请在最新版小天才APP-更多-应用禁用中调整本应用的每日可用时长或修改应用禁用设置，即可正常使用` |

**全部语言变体（16 条资源行 = 2 key × 8 locale）**：

| locale | `app_durtaion_tip_content` | `how_to_exit_app_forbid_content` | 备注 |
|---|---|---|---|
| `values`(zh) | `:37` | `:95` | 基准（配对正确） |
| `values-zh-rHK` | `:37` | `:90` | 繁体（配对正确） |
| `values-zh-rTW` | `:37` | `:90` | 繁体（配对正确） |
| `values-en` | `:10` | `:63` | ⚠️ 英文 `app_durtaion_tip_content` **`$` 为奇数（3 个）→ 末尾 `$` 会原样显示**（实测：`…under More - App Disable$ to use…`） |
| `values-en-rUS` | `:10` | `:53` | 同上 |
| `values-in-rID` | `:10` | `:51` | 同上 irregularities |
| `values-ms-rMY` | `:10` | `:51` | 同上 |
| `values-th` | `:37` | `:78` | 泰文 `${XTC}` 前后配对亦不规范 |

> 英文既有 bug 是**改话术时的天然收益点**：重写时保证每条文案里 `$` 出现次数为偶数、且 `${XTC}` 前后不要紧贴 `$`（推荐写法：`$…${XTC}…$` 之外不要出现落单 `$`）。

#### C.2.6 自启动 / 提示窗 / Toast（8 条）

| # | key | 中文原文 | 用在哪 | 影响面 |
|---|---|---|---|---|
| 28 | `init_wx_notice` | 温馨提示 | `layout_hint_window.tv_title` 默认 + 录屏提示标题 | 🔴 |
| 29 | `screen_record_using_tips` | 使用录屏会录制手表屏幕上显示的内容，并将其保存为视频。在录制过程中，要注意保护隐私信息，避免分享导致隐私信息泄露 | HintWindow 录屏正文 | 🔴 |
| 30 | `no_flashlight_when_use_camera` | 已启用摄像头 无法打开手电筒 | **内嵌 Toast**（状态栏内） | 🔴 |
| 31 | `interrupt_flashlight_when_use_camera` | 启用摄像头，手电筒已关闭 | **系统 Toast 封装** | 🔴 |
| 32 | `disable_screen_record_when_open_camera` | 已启用摄像头 无法录屏 | 系统 Toast 封装 | 🔴 |
| 33 | `interrupt_screen_record_when_open_camera` | 录屏已停止 | 系统 Toast 封装 | 🔴 |
| 34 | `copy_success` | 复制成功 | 系统 Toast 封装（`CopyTextDialog`） | 🟡 |
| 35 | `self_start_go_to_set` | 去设置 | SelfStart 弹窗右按钮（已计入 #22，此处仅备忘） | 🟡 |

**文案统计：65 条唯一 key** = 隐私协议/链接/预留 22（13 常规 + 8 V2 预留 + 1 拼接模板）+ CTA 权限说明 5 + 按钮 5（`agree`/`disagree`/`cancel`/`i_know`/`common_i_know`）+ 自启动 1（`self_start_go_to_set`）+ 权限标签 20 + 富文本及配套 5 + 提示窗/Toast 7。其中**含 `$…$` 富文本标记的 key = 2 条**（`app_durtaion_tip_content`、`how_to_exit_app_forbid_content`），在 8 个 locale 下共 **16 条资源行**。

---

## D. 复刻实现建议

### D.1 新类清单（包 / 类 / 职责 / 预计 LOC）

| # | 包 | 类 | 职责 | 预计 LOC |
|---|---|---|---|---|
| 1 | `com.android.systemui` | `IKeyguardInterface.aidl` | 客户端 AIDL（5 方法） | 8（aidl） |
| 2 | `com.android.systemui` | `IKeyguardCallbackInterface.aidl` | 回调 AIDL（2 方法） | 6（aidl） |
| 3 | `com.android.systemui.keyguard` | `KeyguardController` | 替代 `statusbar.f`：bind / 重连 / 单槽+队列 / 回调 | 150 |
| 4 | `com.android.systemui.keyguard` | `KeyguardServiceBridge`（可选，自建锁屏时） | 实现 `IKeyguardInterface.Stub`，接管注册表与状态广播 | 120 |
| 5 | `com.android.systemui.statusbar` | `Dependency` 绑定 2 处 | 注册 `KeyguardController` | 5 |
| 6 | `com.android.systemui.statusbar.provider` | `StatusBarProxyProvider` | 4 个 call + 调用方校验 | 130 |
| 7 | `com.xtc.baseservice.im` | `ImStateManager` | IM 在线态（单例 + 弱引用） | 70 |
| 8 | `com.xtc.text` | `StringResourceUtils`（原 `com.xtc.utils.system.w`） | `${XTC}` 品牌替换（CN/HK/TW→小天才，其它→imoo） | 30 |
| 9 | `com.xtc.text` | `RichTextParser` | `$…$` → Spannable（高亮 + 删标记），移植 `AntiAddictionExitLayout.a(String)` | 90（含边界用例） |
| 10 | `com.android.systemui.statusbar.tip` | `PopWindowManager`（原 `j`） | 2002 全屏窗宿主、4 类提示窗生命周期、广播/来电自动收起 | 260 |
| 11 | `com.android.systemui.statusbar.tip` | `HintWindowLayout` | 温馨提示窗 | 80 |
| 12 | `com.android.systemui.statusbar.tip` | `DisabledForbidLayout` | 应用禁用页 | 90 |
| 13 | `com.android.systemui.statusbar.tip` | `AntiAddictionExitLayout` | 富文本页（调 `RichTextParser`） | 80 |
| 14 | `com.xtc.toast` | `InnerToast` | 内嵌 Toast（TextView + 2s 定时） | 60 |
| 15 | `com.xtc.toast` | `ToastCompat`（原 `ToastUtil`） | 去重 + 反射 hook（Kotlin/Java 均可；API28+ 建议仅 try/catch，不必 hook） | 110 |
| 16 | `com.xtc.ui.privacy` | `PrivacyBean` / `PrivacyCommon` / `IPrivacyDialogListener` | 数据模型/常量 | 130 |
| 17 | `com.xtc.ui.privacy` | `PrivacyFirstDialog` | 首启协议弹窗 | 200 |
| 18 | `com.xtc.ui.privacy` | `PrivacyUriActivity` | 二维码协议页（zxing 可换 QREncode/自绘） | 160 |
| 19 | `com.xtc.ui.privacy` | `PrivacyUtilities` | URL 拼参/H5 域替换（如保留 V1） | 220 |
| 20 | `com.xtc.ui.privacy` | `XTCPrivacy` / `DialogUtil`(隐私部分) | 门面/工厂 | 120 |
| 21 | `com.xtc.ui.cta` | `CtaPermissionUtil` | 权限名→文案映射、meta-data 解析、白名单 call | 240 |
| 22 | `com.xtc.ui.cta` | `CtaPermissionView/Dialog/Adapter/Bean/ICtaPermissionCallback` | 权限说明 UI | 300 |
| 23 | `com.xtc.ui.selfstart` | `SelfStartActivity/SelfStartUtils/SelfStartConstant` | 自启动引导 | 180 |
| 24 | `com.android.systemui.statusbar.provider` | `FileProvider`（用 androidx 现成） | 0（只加 manifest + res/xml） | 0 |
| 25 | — | **不实现**：`HttpDnsContentProvider`/`dns.*`、`ToastView`、`layout_toast`、`layout_read_remind`、`AboutEntranceView`、`KeyguardEventManager`、`SelfStartManager` | — | 0 |

**合计预计：约 2 400 ~ 2 800 行（不含布局 XML 与资源）；其中「活链必需」约 900~1 100 行，隐私/CTA/自启动「保留面」约 1 300~1 700 行。**

### D.2 AIDL 自实现方案结论：**写 `.aidl`**

**结论：写 2 个 `.aidl` 文件（`IKeyguardInterface.aidl` 5 方法 + `IKeyguardCallbackInterface.aidl` 2 方法），不要手写 Parcelable/Binder。**

理由（证据驱动）：
1. **descriptor 必须逐字一致**：`"com.android.systemui.IKeyguardInterface"` / `"com.android.systemui.IKeyguardCallbackInterface"`（`IKeyguardInterface.java:55`、`IKeyguardCallbackInterface.java:35`）。调用方（launcher）用 `queryLocalInterface(descriptor)` 做同进程优化，字符串一改就退化甚至失败。
2. **事务码必须一致**：`1..5` / `1..2`（`IKeyguardInterface.java:52-59`、`IKeyguardCallbackInterface.java:32-36`）。手写极易与外部服务错位（尤其是 `1658968902` = `INTERFACE_TRANSACTION` 的 `writeString(descriptor)` 返回，`:110-113`、`:67-70`）。用 aidl 编译产物 100% 一致。
3. **参数只有 `boolean/void/IBinder(强引用)`**，没有 Parcelable 自定义类型 → **完全没有手写 Parcelable 的必要**（任务书里「写 AIDL 还是手写 Parcelable」的答案：**AIDL**，因为本协议不需要任何自定义 Parcelable）。
4. 反编译形状即 aidl 生成物（`Stub`/`asInterface`/`Proxy`/`setDefaultImpl`），复刻后可直接编译对齐；`setDefaultImpl/getDefaultImpl`（`:229-242`、`:133-146`）是 aidl 版本特性，手写可省但保留更稳。
5. **aidl 文件内容（照抄即可）**：

```aidl
// com/android/systemui/IKeyguardCallbackInterface.aidl
package com.android.systemui;
interface IKeyguardCallbackInterface {
    void onKeyguardDismissed();
    void onKeyguardShown();
}
```
```aidl
// com/android/systemui/IKeyguardInterface.aidl
package com.android.systemui;
import com.android.systemui.IKeyguardCallbackInterface;
interface IKeyguardInterface {
    boolean isKeyguardLocked();
    boolean isKeyguardSecure();
    void requestDismissKeyguard();
    void registerCallback(IKeyguardCallbackInterface callback);
    void unregisterCallback(IKeyguardCallbackInterface callback);
}
```

- **落地注意**：
  - 若**继续用外部 launcher 的锁屏**：SystemUI 只做客户端，manifest 保留 `uses-permission com.xtc.systemui.keyguard.permission.KEYGUARD_SERVICE`（`AndroidManifest.xml:56`），bind 时 `setPackage("com.xtc.i3launcher")` + `BIND_AUTO_CREATE`；
  - 若**自建锁屏**：还需要「服务端」：实现 `IKeyguardInterface.Stub`，用 `ServiceManager.addService("keyguard")` 或 `publishBinderService` 注册（本包是 `android.uid.system`，有权限），并在 `onKeyguardDismissed/onKeyguardShown` 时刻广播状态；
  - **不要**把这两个 aidl 放进 `com.xtc.*` 包名，descriptor 依赖包名。
  - ⚠️ 注意 `unregisterCallback` 在 `onServiceDisconnected` 里对已置空引用调用（原实现 bug，`statusbar\f.java:49-53`）——复刻时先判空再反注册，行为更正确且不影响兼容。

### D.3 依赖顺序（拓扑）

```
(1) AIDL 两个文件 + KeyguardController  ← 无依赖，先做
(2) ImStateManager（provider 依赖）
(3) StatusBarProxyProvider  ← 依赖 (2) + SysUiServiceProvider(n) + StatusBar 状态 getter
(4) StringResourceUtils + RichTextParser  ← 无依赖，但被 (5)(6)(7) 共用
(5) PopWindowManager + HintWindowLayout / DisabledForbidLayout / AntiAddictionExitLayout ← 依赖 (4)
(6) InnerToast + ToastCompat ← 无依赖
(7) FileProvider 清单 + res/xml ← 无依赖
--- 以下为「保留面」，最后做 ---
(8) CtaPermissionUtil + @array/permission_label + CtaPermission* UI  ← 依赖资源
(9) PrivacyBean/Common/FirstDialog/UriActivity/Utilities/XTCPrivacy/DialogUtil ← 依赖 (8)
(10) SelfStart* ← 依赖 launcher provider 约定（可先只做弹窗）
```

### D.4 资源落地清单（必须新建/保留）

| 资源 | 类型 | 归属 |
|---|---|---|
| `activity_privacy_url.xml` / `dialog_privacy_first.xml` / `item_privacy.xml` | layout | 隐私 |
| `layout_cta_permission_dialog.xml` / `view_cta_permission.xml` / `item_cta_permission{,_head,_tip}.xml` | layout | CTA |
| `layout_double_flat_button_dialog.xml` / `layout_double_flat_button.xml` | layout | 通用对话框 |
| `layout_hint_window.xml` / `activity_anti_exit_tip.xml` / `view_disabled_forbid_tip.xml` | layout | 提示窗 |
| `layout_inner_toast.xml` + `super_status_bar.xml:21` include | layout | 提示 |
| `xml/fileprovider.xml` | xml | Provider |
| `arrays.xml` → `permission_label`（20 条，**顺序不可变**） | values | CTA |
| `strings.xml` → §C.2 的 65 条 | values | 全部 |
| `styles.xml` → `DoubleFlatButton`、`DoubleFlatButton_Btn`、`NormalDialogRoot`、`NormalDialogTitle`、`dialog_default_style(_forbidSwipe)`、`TextAppearance_Toast` | values | UI 基线 |
| `dimens.xml` → `privacy_dialog_margin_edge`(xhdpi 4dp/xxhdpi 8dp)、`sp_13`、`dp_1~dp_20`、`dp_130`、`btn_left_text_size`(16sp)、`double_flat_btn_*` | values | UI 基线 |
| `colors.xml` → `color_black(#ff000000)`、`color_55dd7b`、`color_808080`、`color_FFFFFF`、`color_ff000000` | values | UI 基线 |
| `drawable` → `bg_privacy_url`、`bg_dot`、`bg_gauss`、`inner_toast_background`、`toast_bg`、`ic_enter_green`、`rectangle_radius_3dp_80ffffff` | drawable | UI 基线 |

---

## E. 风险与取舍

### E.1 必须移除（引 RISK-MAP）

| 项 | 依据 | 处理 |
|---|---|---|
| `HttpDnsContentProvider` + `com.xtc.dns.provider.a` + `com.xtc.dns.client.b` + `client.b.b` | **R-43 / R-48 / R-49 / R-73**（云控 DNS 旁路；manifest `:164-172` 暴露面） | 删除 Provider 与客户端探测；外部探测得 null → 系统 DNS 兜底，不崩（`IMPACT I-3`）。若希望「零行为差异」可保留空壳 Provider 返回 null，**authority 保持 `com.android.systemui.com.xtc.httpdns.provider`** |
| `READ_DNS` 权限声明 | R-43 关联（manifest `:69-73`，`protectionLevel=normal` 的自声明） | 随 Provider 一起删 |
| `meta-data moduleSwitch/funSwitch`（`AndroidManifest.xml:138-146`） | 云控开关（不属本模块，但与 provider 同区） | 交由 risk-scan 结论统一处理 |

### E.2 属于「权限检测/上报」需注意的边界

| 项 | 说明 | 取舍 |
|---|---|---|
| `CtaPermissionUtil.getAuthorityInfos()` / `getPrivacy()` 读 `PackageManager` 里**其它应用**的权限清单 | 这是**读取他人权限做 UI 展示**，不是自我权限上报；但会暴露「本机装了哪些应用及其权限」给本 UI | 保留（纯本地 UI，无网络、无落盘）。若合规从严，可改为只展示**本应用**权限（`context.getPackageName()`） |
| `CtaPermissionUtil.setPackageWhiteListByLauncher()` → `content://com.xtc.launcher.self.start` | **写外部应用（launcher）状态**，会改变其它应用自启行为 | **保留但需 Lead 决策**：这是「同意隐私→自动放行自启」的联动，属于产品语义；若不做，必须让「同意」按钮不再产生副作用（否则权限语义不一致） |
| `SelfStartUtils.isHadSelfStartPermission()` → 读 launcher provider | 仅查询，不写 | 保留（可选面） |
| `PrivacyUtilities` 里的机型/区域探测（`SystemProperties: ro.hardware/ro.product.innermodel/ro.product.locale.region/persist.sys.serverinner`）+ URL 拼参 `wModel/pkg/lang/appBrand` | 属**设备指纹拼进 H5 URL**（会出网） | 若走「H5 协议页」路线，**去掉随机 `extra` 与机型参数**，只保留 `pkg/lang/type`；或直接改用内置富文本协议页（推荐，见 E.4） |
| `PrivacyUriActivity` 用 zxing 在本机生成二维码 | 纯本地 | 保留（可换实现） |

### E.3 必须保留（纯本地 UI，不可删）

1. 内嵌 Toast（`InnerToastUtils` + `layout_inner_toast`）+ `ToastUtil` 去重/hook —— 用户可见反馈。
2. `PopWindowManager` + 3 个全屏提示页（Hint / DisabledForbid / AntiAddictionExit）—— 禁用/限额/录屏提示的产品核心。
3. `${XTC}` 品牌替换 + `$…$` 富文本渲染 —— **改话术的基础设施**，与文案表强耦合。
4. `StatusBarProxyProvider` + `FileProvider` 声明 —— 跨进程契约（launcher 依赖）。
5. `@array/permission_label` 20 条 + 全部 65 条文案 —— **用户要求按规则改话术的对象**。
6. Keyguard AIDL 客户端 —— 决定「点通知/磁贴先解锁」的正确性。

### E.4 关键取舍建议（给 Lead 决策）

| 议题 | 建议 | 理由 |
|---|---|---|
| 隐私协议页：H5 二维码 or 内置富文本？ | **内置富文本页（复用 `$…$` 渲染）** | ①去网络依赖与指纹拼参（E.2）；②用户要改话术，内置文案改起来直接；③原 `PrivacyUriActivity` 只显示二维码+一行字，信息量低 |
| 「同意」后的自启白名单联动 | **保留 call，但失败静默** | 原实现已 try/catch 吞异常（`CtaPermissionUtil.java:228-234`），launcher 不存在时无感 |
| Keyguard：继续桥接外部 launcher or 自建？ | 若复刻目标是「独立可跑」→ **自建最小锁屏**（`IKeyguardInterface.Stub` + 简单 KeyguardView）；若目标是「与原机一致」→ **照抄客户端桥** | 本包无 AOSP Keyguard，纯客户端方案离开 launcher 就是「永远返回 true（永远当锁屏）」→ 所有 `DismissingKeyguard` 动作会卡住（`f.java:76`） |
| `PermissionListActivity` / `PrivacyFirstDialog` 等死链 | **保留代码+布局，不改行为** | 成本低；未来兄弟模块/外部需要时可复用；**但话术表要一并覆盖**，否则将来复用时文案不一致 |

---

## F. 交付摘要（给 Lead 的回报口径）

- **SPEC 路径**：`D:\DEBUG\apk\sysui\analysis\modules\misc-local\SPEC.md`
- **类数**：A 节共 **49 条记录**（48 个类/接口 + 1 个纯资源布局）→ **必需 17 / 可选 24 / dead 4 / 移除 4**
- **UI 元素数**：**16 个布局文件 + 1 个 `fileprovider.xml`**；**31 个唯一 View id**
- **文案数**：**65 条**（其中含 `$…$` 富文本标记的 key **2 条**，共 16 条本地化资源行；`@array/permission_label` 20 条按序锁定）
- **AIDL 方案结论**：**写 `.aidl`（2 个必须：`IKeyguardInterface.aidl` 5 方法、`IKeyguardCallbackInterface.aidl` 2 方法；如需自建锁屏再加服务端 Stub）。不写自定义 Parcelable —— 协议里没有 Parcelable 参数。**
- **富文本语法规则（一段话）**：`${XTC}` 是品牌占位符，渲染前先由 `StringResourceUtils` 用正则 `\$\{XTC\}` 替换为「小天才」（区域 CN/HK/TW）或「imoo」（其它区域）；`$…$` 是**成对定界符**，`AntiAddictionExitLayout.a(String)` 顺序扫描：第 1 个 `$` 记起点、第 2 个 `$` 记终点，对 `[起点,终点)` 加 `ForegroundColorSpan(0xFFFFFFFF)`（flag=18，`SPAN_MARK_POINT`），然后第二轮把成对的 `$` 字符删除（`delete(i,i+1)` + `delete(i2-1,i2)`，用 `cnt*2` 做偏移补偿）；因此**每条文案里 `$` 总数必须为偶数**，`${XTC}` 前后不能出现落单 `$`；高亮色=纯白，正文基色 `#b3ffffff`（`activity_anti_exit_tip.tv_content`），标题 `#55dd7b`；不支持换行/字号/多色，只有「白色高亮」一种语义。（实测渲染：`请在最新版$${XTC}APP-更多-应用禁用$中，修改$本应用的禁用设置$，即可正常使用` → `请在最新版小天才APP-更多-应用禁用中，修改本应用的禁用设置，即可正常使用`，两段高亮。）
- **建议实施顺序前 5 步**：
  1. **AIDL + `KeyguardController`**（2 个 aidl 文件 + 150 行控制器，照抄单槽 `f981a` + 队列 `f982a` + pingBinder 重连；`Dependency` 注册）；
  2. **`${XTC}` + `$…$` 文本基础设施**（`StringResourceUtils` 30 行 + `RichTextParser` 90 行，含英文奇数 `$` 的兼容策略：遇落单 `$` 原样保留、不抛异常）；
  3. **`StatusBarProxyProvider` + `ImStateManager` + `SysUiServiceProvider` 取 `StatusBar`**（4 个 call + callingPackage 白名单 `com.xtc.i3launcher` + manifest signature 权限）；
  4. **提示体系**：`PopWindowManager`(2002 窗) → `HintWindowLayout` / `DisabledForbidLayout` / `AntiAddictionExitLayout` → `InnerToast` + `ToastUtil`（含 `layout_inner_toast` 挂到 `super_status_bar.xml`）；
  5. **权限/隐私「保留面」+ 文案表**：`CtaPermissionUtil` + `@array/permission_label` + `CtaPermissionView/Dialog` + `PrivacyFirstDialog`/`PrivacyUriActivity`/`SelfStart*`，并同步落地 §C.2 的 65 条文案（此时按用户规则统一改写话术，特别注意英文变体的 `$` 配对 bug）。

---

## G. 证据索引（文件:行，便于 Lead 抽查）

| 主题 | 证据 |
|---|---|
| Keyguard AIDL 客户端 | `work\jadx18b\sources\com\android\systemui\statusbar\f.java:17,19,34-56,70-81,85-95,98-117,120-123,139-145,152-166` |
| Keyguard AIDL 接口 | `...\com\android\systemui\IKeyguardInterface.java:41-49,52-59,82-120`；`IKeyguardCallbackInterface.java:27-29,32-36,59-77` |
| Keyguard DI/调用方 | `...\Dependency.java:193,320`；`...\statusbar\phone\StatusBar.java:375,845,907-921,952-977,1000-1024,1149-1151,1548-1564`；`qs\tiles\g.java:189`；`qs\tileimpl\d.java:213` |
| Keyguard 权限 | `nx\resources\AndroidManifest.xml:56,57` |
| SDK 面锁屏 | `...\com\xtc\ultraframework\keyguard\KeyguardEventManager.java:20-41,54-87,89-148` |
| 隐私弹窗 | `...\com\xtc\ui\widget\privacy\PrivacyFirstDialog.java:52-194`；`DialogUtil.java:174-246`；`XTCPrivacy.java:40-86` |
| 隐私页/工具 | `...\privacy\PrivacyUriActivity.java:38-153`；`PrivacyUtilities.java:143-296`；`PrivacyCommon.java:11-23`；`PrivacyBean.java:8-19` |
| 同意态/白名单 | `...\CtaPermissionUtil.java:216-297`；`...\ultraframework\os\SelfStartManager.java:22,38,102-142` |
| 权限文案源 | `nx\resources\res\values\arrays.xml:11-32`；`...\CtaPermissionUtil.java:42,43,51-60`；`nx\resources\res\values\strings.xml:142` |
| CTA UI | `...\ui\widget\permission\cta\{CtaPermissionView,CtaPermissionDialog,adapter\CtaPermissionAdapter}.java`；`nx\resources\res\layout\{layout_cta_permission_dialog,view_cta_permission,item_cta_permission,item_cta_permission_head,item_cta_permission_tip}.xml` |
| 权限页 Activity | `...\architecture\mvp\PermissionListActivity.java:20-62`；`mvp\b\a.java:56-72`；`mvp\BaseActivity.java:58` |
| 自启动 | `...\ui\widget\selfstart\{SelfStartActivity,SelfStartConstant,SelfStartUtils}.java` |
| 富文本 | `...\com\xtc\utils\system\w.java:21-26`；`...\com\xtc\statusbar\AntiAddictionExitLayout.java:14,38-43,67-104`；`...\DisabledForbidLayout.java:57,71`；`nx\resources\res\values\strings.xml:37,95`（+ 7 个 locale 变体） |
| Provider 代理 | `...\statusbar\provider\StatusBarProxyProvider.java:16-26,71-125,127-137`；`nx\resources\AndroidManifest.xml:58-63,133-137`；`...\com\android\systemui\n.java:7-12`；`...\baseservice\e\a.java:22-46` |
| FileProvider | `nx\resources\AndroidManifest.xml:124-132`；`nx\resources\res\xml\fileprovider.xml:1-6`；`...\androidx\core\content\FileProvider.java:25` |
| HttpDns 协议 | `...\com\xtc\dns\provider\HttpDnsContentProvider.java:38,43-63,67-111`；`dns\provider\a.java:6-29`；`dns\client\b.java:52-62`；`dns\client\b\b.java:34-49,58-122` |
| Toast/提示窗 | `...\ui\widget\toast\view\{ToastUtil,ToastView}.java`；`...\baseservice\util\i.java:47-79`；`...\statusbar\j.java:105-118,125-156,185-246,275-289`；`...\statusbar\HintWindowLayout.java:36-64`；`nx\resources\res\layout\{layout_hint_window,layout_inner_toast,layout_toast,layout_read_remind}.xml`；`nx\resources\res\layout\super_status_bar.xml:21` |
| 提示触发点 | `...\statusbar\policy\q.java:135,439`；`...\qs\tiles\g.java:58`；`...\qs\tiles\f.java:169`；`...\screenrecord\a.java:359-361`；`...\flashlight\FlashLightSOSDialog.java:105`；`...\statusbar\phone\StatusBar.java:570,865` |
