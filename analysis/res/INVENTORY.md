# XTCSystemUI 1.8.00 资源与组件信息基线（INVENTORY）

> 采集者：res-inventory（Team 成员）｜ 采集时间：2026-11-01
> 数据源：`D:\DEBUG\apk\sysui\XTCSystemUI.apk`（只读）+ 解码资源树 `D:\DEBUG\apk\sysui\nx\resources`（只读，未做任何修改）
> 本文所有条目均可用「路径 + 行号」复核；下文简写：
> - `R\` = `D:\DEBUG\apk\sysui\nx\resources\`
> - `RES\` = `D:\DEBUG\apk\sysui\nx\resources\res\`
> - `M` = `R\AndroidManifest.xml`（明文，共 192 行）
> 采集脚本与原始输出（可复跑）：`analysis\res\_scan.py`、`_scan_report.txt`、`_badging.txt`、`_dump_resources.txt`

---

## 0. 关键数字速览（验收基准）

| 指标 | 数值 | 证据 / 采集方式 |
|---|---|---|
| package / versionCode / versionName | `com.android.systemui` / `18000` / `1.8.00.Develop_AW_XtcSystemUI_J168_Gc23339b` | `_badging.txt:1`；`M:3-8` |
| minSdk / targetSdk / compileSdk | 27 / 30 / 30 (codename 11) | `_badging.txt:2-3`；`M:6,11-13` |
| classes.dex | **不存在**（APK zip 根目录无 classes.dex） | zip 目录枚举（1131 条目） |
| 组件总数 | **16**（activity 6 + service 5 + receiver 2 + provider 3） | `M` 全量统计 |
| `<permission>` 声明 | **6** | `M:14,48,52,58,61,71` |
| `<uses-permission>` | **42** | `_badging.txt:4-45`；`M` |
| `<meta-data>` | **8**（application 级 4 + 组件级 4） | `M:99,116,129,138,141,144,169,188` |
| `<intent-filter>` / `<action>` | 5 / 5 | `M` |
| `<uses-feature>` | 1 显式（watch）+ 4 隐式 | `_badging.txt:159-168` |
| `res\` 文件数 / 字节 | **883** / **2,027,701 B** | `RES` 递归统计 |
| res 类型数（APK 资源表 type） | **16**（typeId 0x01–0x0e、0x10，**0x0f 缺号**） | `_dump_resources.txt`（`aapt2 dump resources`） |
| 资源表 entry 总数 | **3738**（= `values\public.xml` 3738 条 `<public>`） | `RES\values\public.xml:3+` |
| strings.xml 条目（base） | **238** | `RES\values\strings.xml:3-240` |
| locale 数 | **104**（badging `locales:` 共 105 项，含默认 `--_--`） | `_badging.txt:175`；`values-*` 目录 104 个含 strings.xml |
| values 目录数 | **127**（`values` + `values-*` 126，其中 104 含 strings.xml、22 为密度/尺寸/版本限定符） | `RES` 目录枚举 |
| assets 条目 | **31 个文件**（`guide` 24 + `pull_up_setting` 7），无其它文件 | `R\assets` 递归统计 |
| APK zip 条目 / 解压总字节 | **1131 / 3,074,863** | py `zipfile` |
| APK 签名 | **仅 v2 有效**（v1=false, v3=false）；证书 `CN=ywm, OU=XTC, O=BBKEDU, C=CN, emailAddress=yangwm@dw.gdbbk.com`；SHA-256 `c7a460a0d3b8b79c43a06d74eb3609c1f0f1d2fd8cd77b57570cf6ea3ee77c24` | `apksigner verify -v --print-certs` |
| native ABI | `armeabi`（2 个 .so） | `_badging.txt:177`；`R\lib\armeabi\` |

---

## 1. Manifest 全量组件清单

### 1.1 清单根属性与 `<application>` 属性

| 位置 | 属性 | 值 | 用途/影响 |
|---|---|---|---|
| `M:2-10` | `package` | `com.android.systemui` | 与原包一致，改动即失败 |
| `M:3` | `sharedUserId` | `android.uid.system` | **系统进程身份**，签名/权限校验链核心 |
| `M:4` | `versionCode` | `18000` | — |
| `M:5` | `versionName` | `1.8.00.Develop_AW_XtcSystemUI_J168_Gc23339b` | 尾段 `Gc23339b` 疑为 git commit 短哈希 |
| `M:6-7` | `compileSdkVersion` / `Codename` | 30 / 11 | 编译期信息（Android 11 编译） |
| `M:9-10` | `platformBuildVersionCode/Name` | 30 / 11 | 由 aapt2/平台写入 |
| `M:74` | `uses-feature` | `android.hardware.type.watch` | 手表设备限定 |
| `M:76` | `application android:theme` | `@style/Theme.SystemUI` | 见 `RES\values\styles.xml`（parent=`@android:style/TextAppearance.Watch.AppErrorDialog`） |
| `M:77` | `android:label` | `@string/app_name` = `SystemUI` | 所有 locale 的 `application-label` 均为 `SystemUI`（无本地化覆盖） |
| `M:78` | `android:icon` | `@drawable/icon` | 向量图（`RES\drawable\icon.xml`，48dp，黑底+橙黄同心圆） |
| `M:79` | `android:name` | `com.android.systemui.SystemUIApplication` | 无 dex，类由系统侧提供 |
| `M:80` | `allowClearUserData` | `false` | 系统应用 |
| `M:81` | **`persistent`** | `true` | 常驻进程，不可被回收 |
| `M:82` | `process` | `com.android.systemui` | 独立进程名 |
| `M:83` | `allowBackup` | `false` | — |
| `M:84` | `hardwareAccelerated` | `true` | — |
| `M:85` | `supportsRtl` | `false` | 与 `drawable-ldrtl-*` 资源并存（残留） |
| `M:86` | `defaultToDeviceProtectedStorage` | `true` | **直接启动（Direct Boot）必需** |
| `M:87` | **`directBootAware`** | `true` | 锁屏前可用（锁屏/密码相关） |
| `M:88` | `appComponentFactory` | `androidx.core.app.CoreComponentFactory` | androidx core 兼容层 |

### 1.2 `<permission>` 声明（6 条，全部为自定义权限）

| # | 权限名 | protectionLevel | 行号 | 用途推测 / 风险 |
|---|---|---|---|---|
| 1 | `android.permission.systemui.IDENTITY` | signature | `M:14-16` | 占用系统命名空间的「身份」权限，供签名级校验（权限检测链） |
| 2 | `com.android.systemui.permission.SELF` | signature | `M:48-50` | 本包内部自我保护权限（同时自用，`M:51`） |
| 3 | `com.android.systemui.permission.PLUGIN` | signature | `M:52-54` | 插件机制入口保护 |
| 4 | `com.xtc.systemui.proxy.permission.STATUS_BAR` | signature | `M:58-60` | XTC 状态栏代理保护（声明后本包未 uses） |
| 5 | `com.xtc.systemui.statusbar.permission.StatusBarProxyProvider` | signature | `M:61-63` | 保护 `StatusBarProxyProvider`（`M:135`） |
| 6 | `com.android.systemui.permission.READ_DNS` | **normal** | `M:71-73` | 保护 HTTPDNS provider 的读权限（`M:166`），normal 级=同签名无关 |

### 1.3 `<uses-permission>`（42 条，按用途分组）

| 分组 | 权限（manifest 行号） |
|---|---|
| 状态栏/窗口管理（SystemUI 核心，需平台签名，**不可删**） | INJECT_EVENTS(22), DUMP(23), WRITE_SETTINGS(24), STATUS_BAR_SERVICE(25), STATUS_BAR(26), EXPAND_STATUS_BAR(27), WRITE_SECURE_SETTINGS(28), START_ANY_ACTIVITY(37), GET_TOP_ACTIVITY_INFO(38), INTERNAL_SYSTEM_WINDOW(39), SYSTEM_ALERT_WINDOW(40), READ_FRAME_BUFFER(41), MANAGE_APP_TOKENS(42), REGISTER_WINDOW_MANAGER_LISTENERS(43), MANAGE_MEDIA_PROJECTION(45) |
| 存储 / 媒体 | READ_EXTERNAL_STORAGE(18), WRITE_EXTERNAL_STORAGE(19), WRITE_MEDIA_STORAGE(20), **`android.perminssion.WRITE_MEDIA_STORAGE`(70)** ← 拼写错误（perminssion），实际为无效权限名 |
| 网络 / 蓝牙 / WLAN | BLUETOOTH(29), BLUETOOTH_ADMIN(30), ACCESS_NETWORK_STATE(31), CHANGE_NETWORK_STATE(32), ACCESS_WIFI_STATE(33), CHANGE_WIFI_STATE(34), INTERNET(67) |
| 音频 / 电源 / 前台服务 | WAKE_LOCK(21), VIBRATE(35), FOREGROUND_SERVICE(46), RECORD_AUDIO(47) |
| 其它系统能力 | RECEIVE_BOOT_COMPLETED(17), REORDER_TASKS(36), `com.android.alarm.permission.SET_ALARM`(44), BIND_QUICK_SETTINGS_TILE(55) |
| XTC 厂商自定义 | `com.android.systemui.permission.SELF`(51), `com.xtc.systemui.keyguard.permission.KEYGUARD_SERVICE`(56), `com.xtc.i3launcher.permission.LAUNCHER_PROXY_SERVICE`(57), **`android.Manifest.permission#WRITE_SECURE_SETTINGS`(64)** ← 名称含 `#`，非法/无效权限，`com.xtc.http.bigdata.provider.permission.READ_CONTENT`(65), `...WRITE_CONTENT`(66), `com.xtc.i3launcher.permission.READ_DNS`(68), `com.android.systemui.permission.READ_DNS`(69) |

> ⚠️ 两条「错拼/非法」权限名（L64、L70）是**信息一致性陷阱**：必须逐字节原样保留，删除或"修正"都会导致 badging/清单与原包不一致。

### 1.4 Activity（6 个）

| # | 类名 | 行号 | 关键属性 | intent-filter | exported（推断） | 用途推测 |
|---|---|---|---|---|---|---|
| 1 | `com.android.systemui.flashlight.FlashLightActivity` | 89-93 | theme=`@style/AppTheme`, launchMode=singleTask, showWhenLocked=true | 无 | false（无 filter） | 手电筒主界面，锁屏可显示 |
| 2 | `com.android.systemui.flashlight.FlashLightSOSDialog` | 94-102 | 同上 + meta-data `XtcStayFront=true`(99-101) | 无 | false | SOS 灯光信号弹窗；`XtcStayFront` 语义=保持前置/不被覆盖（保活线索） |
| 3 | `com.android.systemui.media.MediaProjectionPermissionActivity` | 103-105 | exported=true | 无 | **true** | 录屏/投屏授权界面（系统级），对外暴露 |
| 4 | `com.xtc.architecture.mvp.PermissionListActivity` | 161-163 | launchMode=singleTask | 无 | false | **权限申请列表页**（配合 `privacy_list_*` 字符串、`permission_label` 数组） |
| 5 | `com.xtc.ui.widget.privacy.PrivacyUriActivity` | 184-186 | launchMode=singleTop | 无 | false | **隐私协议/用户协议 H5 或扫码页**（`privacy_url`、`activity_privacy_url.xml`） |
| 6 | `com.xtc.ui.widget.selfstart.SelfStartActivity` | 187 | — | 无 | false | **自启动/后台保活引导页**（`self_start_go_to_set`） |

### 1.5 Service（5 个）

| # | 类名 | 行号 | 属性 | intent-filter actions | exported | 用途推测 |
|---|---|---|---|---|---|---|
| 1 | `com.android.systemui.SystemUIService` | 106-108 | exported=true | 无 | true | SystemUI 常驻服务（源码级标准组件） |
| 2 | `com.android.systemui.vassistant.SystemControlService` | 109-119 | exported=true；meta-data `assistantapi-directive="[FlashLight,VideoRecord]"`(116-118) | `com.xtc.assistantapi.directive` + category DEFAULT（112-115） | true | **语音助手指令服务**，支持手电筒/录制两类指令 |
| 3 | `com.android.systemui.screenrecord.RecordingService` | 120-123 | exported=false, foregroundServiceType=`mediaProjection` | 无 | false | 录屏前台服务 |
| 4 | `com.xtc.statusbar.XtcNotificationListenerService` | 147-153 | permission=`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | `android.service.notification.NotificationListenerService`（151） | true（有 filter 且未写 exported，targetSdk30 默认 true） | XTC 通知监听（状态栏通知展示/样式） |
| 5 | `com.xtc.bigdata.collector.exception.CrashIntentService` | 173-175 | exported=false | 无 | false | **大数据 SDK 崩溃采集上报服务**（`com.xtc.bigdata.*`） |

### 1.6 BroadcastReceiver（2 个）

| # | 类名 | 行号 | 属性 | intent-filter actions | exported | 用途推测 |
|---|---|---|---|---|---|---|
| 1 | `com.xtc.xtcoco.CodeCoverageReceiver` | 154-160 | exported=true | `com.xtc.report.generate.ec`（158） | **true** | **覆盖率/上报触发器**（xtcoco = XTC code coverage），任意应用可发该广播触发 |
| 2 | `com.xtc.bigdata.monitor.receiver.MonitorStartReceiver` | 176-183 | 无 permission，priority=100000（177） | ①`com.xtc.watchdogs`（178-179）②`com.xtc.launcher.ACTION_CHANGE_REGION`（180-182） | true | **大数据监控/看门狗启动器**，桌面（launcher）可唤活；`ACTION_CHANGE_REGION`=地区变更（影响云控/上报策略） |

> `MonitorStartReceiver` 未加 `android:permission`，且 `exported` 未显式声明 → 任何应用可发送这两个 action 唤起采集链路，属**上报攻击面**。

### 1.7 ContentProvider（3 个）

| # | 类名 | 行号 | authorities | exported | read/write permission | 用途推测 |
|---|---|---|---|---|---|---|
| 1 | `androidx.core.content.FileProvider` | 124-132 | `com.android.systemui.fileprovider` | false | — | 录屏/截图文件共享；`meta-data FILE_PROVIDER_PATHS=@xml/fileprovider`(129-131) → `RES\xml\fileprovider.xml`（`cache-path name="leak" path="leak/"`） |
| 2 | `com.android.systemui.statusbar.provider.StatusBarProxyProvider` | 133-137 | `com.xtc.systemui.statusbar.proxy` | **true** | `permission=com.xtc.systemui.statusbar.permission.StatusBarProxyProvider`(135) | **状态栏代理（跨进程）**，供 XTC 外部应用操作状态栏 |
| 3 | `com.xtc.dns.provider.HttpDnsContentProvider` | 164-172 | `com.android.systemui.com.xtc.httpdns.provider` | **true** | readPermission=`com.android.systemui.permission.READ_DNS`(166)；meta-data `allowLaunchOnPowerSaveMode=true`(169-171) | **HTTPDNS（域名解析）provider = 云控域名解析通道**，省电模式仍允许拉起 |

### 1.8 `<meta-data>`（8 条，全量）

| # | 位置 | key | value | 判定 |
|---|---|---|---|---|
| 1 | 组件级 `M:99-101`（FlashLightSOSDialog） | `XtcStayFront` | `true` | 弹窗常驻/保持前置（保活） |
| 2 | 组件级 `M:116-118`（SystemControlService） | `assistantapi-directive` | `[FlashLight,VideoRecord]` | 语音助手指令白名单 |
| 3 | 组件级 `M:129-131`（FileProvider） | `android.support.FILE_PROVIDER_PATHS` | `@xml/fileprovider` | 无害 |
| 4 | 组件级 `M:169-171`（HttpDnsContentProvider） | `allowLaunchOnPowerSaveMode` | `true` | 省电模式仍拉起（云控解析保活） |
| 5 | application `M:138-140` | **`moduleSwitch`** | `[{"module":"3547","funSwitch":"com.android.systemui.screenrecord","notBind":3}]` | **平台模块号开关**（3547=录屏模块），`notBind` 疑为"未绑定/未开通"策略位 → 云控/模块下发口径 |
| 6 | application `M:141-143` | **`funSwitch`** | `[{"appPackage":"com.android.systemui.screenrecord","funSwitch":"1"}]` | **功能开关**（1=开启），与 moduleSwitch 配套 |
| 7 | application `M:144-146` | `supportScreenRecordAudio` | `1` | 能力开关：支持录制系统声音 |
| 8 | application `M:188-190` | `useXtcPublicApi` | `true` | 接入 XTC 公共 API（跨应用能力开放） |

---

## 2. 资源统计与分类

### 2.1 按资源类型聚合（`RES` 递归统计，883 文件 / 2,027,701 B）

| 类型 | 文件数 | 字节 | 目录数 | 说明 |
|---|---|---|---|---|
| values | 173 | 1,022,948 | 127 | 编译进 `resources.arsc`，APK 内无独立文件 |
| drawable | 513 | 763,066 | 15 | 含 9-patch / png / vector xml / selector |
| layout | 126 | 195,018 | 4 | base 118 + xhdpi 5 + watch 2 + en-xhdpi 1 |
| anim | 27 | 29,050 | 1 | — |
| color | 30 | 11,143 | 1 | selector |
| animator | 6 | 5,012 | 1 | — |
| interpolator | 7 | 1,284 | 1 | — |
| xml | 1 | 180 | 1 | 仅 `fileprovider.xml` |
| **合计** | **883** | **2,027,701** | — | — |

drawable 目录分布：`drawable`=257、`drawable-xhdpi`=120、`drawable-hdpi`=39、`drawable-mdpi`=39、`drawable-xxhdpi`=34、`drawable-xxxhdpi`=14、`drawable-anydpi`=2、`drawable-ldpi`/`drawable-watch`/`drawable-en-xhdpi`=1、`drawable-ldrtl-*`=5（5 个各 1 个文件）。
layout 目录分布：`layout`=118、`layout-xhdpi`=5、`layout-watch`=2、`layout-en-xhdpi`=1。

### 2.2 APK 资源表（`resources.arsc`）entry 统计 — 16 个 type，3738 entry

| type | typeId | entryCount | type | typeId | entryCount |
|---|---|---|---|---|---|
| anim | 0x01 | 27 | id | 0x09 | 569 |
| animator | 0x02 | 6 | integer | 0x0a | 13 |
| array | 0x03 | 3 | interpolator | 0x0b | 7 |
| attr | 0x04 | 769 | layout | 0x0c | 122 |
| bool | 0x05 | 12 | string | 0x0d | 238 |
| color | 0x06 | 485 | style | 0x0e | 386 |
| dimen | 0x07 | 728 | xml | 0x10 | 1 |
| drawable | 0x08 | 372 | **（0x0f 缺号）** | — | — |

- 合计 entry = **3738**，与 `RES\values\public.xml` 的 3738 条 `<public>` 完全一致；唯一 name 数 3718（20 个同名跨类型）。
- `public.xml` 为显式 ID 表（`typeId+entryId` 固定），**是复刻「资源 ID 完全一致」的天然基准**（例：`RES\values\public.xml:3` = `anim/abc_fade_in = 0x7f010000`）。
- 校验关系：`res\` 下非 values 文件 883-173=**710** 个 = APK 内 `res/*` 条目 710 个（其中 **275 stored / 435 deflated**）。→ 复刻后 APK 必须是 710 个 res 条目 + 173 个 values XML 合入 arsc。

### 2.3 values 目录构成（127 个）

| 类别 | 数量 | 说明 |
|---|---|---|
| `values`（base，默认=中文） | 1 | 10 个文件：arrays / attrs / bools / colors / dimens / drawables / integers / public / strings / styles |
| `values-<locale>`（含 strings.xml） | **104** | 见 2.4 |
| 非语言限定符目录（无 strings.xml） | 22 | `anydpi, en-xhdpi, h720dp, hdpi, land, large, ldpi, ldrtl-hdpi, ldrtl-mdpi, ldrtl-xhdpi, ldrtl-xxhdpi, ldrtl-xxxhdpi, mdpi, night, port, sw600dp, v28, watch, xhdpi, xlarge, xxhdpi, xxxhdpi`（仅 dimens/drawables/bools/integers/styles） |

base `values` 条目数：attrs 769、dimens 662、colors 455、styles 384、strings 238、public 3738（public 为 ID 映射，非独立资源）。
小文件内容：`arrays.xml`（3 个 array）、`bools.xml`（12 个，含 `abc_action_bar_embed_tabs` 等 2 个 abc_ 残留）、`integers.xml`（10 个）、`drawables.xml`（3 个颜色 drawable，均 notification_template 残留）。

### 2.4 locale 清单与每个 locale 的 strings.xml 条数（104 个 locale）

| 条数 | locale 数 | locale 目录（`RES\values-*`） |
|---|---|---|
| 29 | 82 | af, am, ar, as, az, **b+sr+Latn**, be, bg, bn, bs, ca, cs, da, de, el, en-rAU, en-rCA, en-rGB, en-rIN, en-rXC, es, es-rUS, et, eu, fa, fi, fr, fr-rCA, gl, gu, hi, hr, hu, hy, in, is, it, iw, ja, ka, kk, km, kn, ko, ky, lo, lt, lv, mk, ml, mn, mr, ms, my, nb, ne, nl, or, pa, pl, pt, pt-rBR, pt-rPT, ro, ru, si, sk, sl, sq, sr, sv, sw, ta, te, tl, tr, uk, ur, uz, vi, **zh-rCN**, zu |
| 87 | 1 | th-rTH |
| 98 | 14 | ar-rBH, bn-rIN, de-rDE, en-rXH, en-rXL, es-rES, fr-rFR, hi-rIN, it-rIT, pl-rPL, ru-rRU, tl-rPH, tr-rTR, vi-rVN |
| 170 | 2 | in-rID, ms-rMY |
| 175 | 1 | en-rUS |
| 199 | 1 | th |
| 200 | 1 | en |
| 224 | 1 | zh-rTW |
| 229 | 1 | zh-rHK |
| 238 | 1 | **values（base）** |

- 全 locale 字符串条目合计 **5442**；`arrays.xml` 额外存在于 ar-rBH/bn-rIN/de-rDE/en/en-rUS/en-rXH/en-rXL/es-rES/fr-rFR/hi-rIN/in-rID/it-rIT/ms-rMY/pl-rPL/ru-rRU/th/th-rTH/tl-rPH/tr-rTR/vi-rVN/zh-rHK/zh-rTW（22 个 locale）。
- **重点结论（与任务假设不同，务必按此执行）**：`values-zh-rCN\strings.xml` **只有 29 条，且全部是 appcompat 内置中文**（`abc_*`、`search_menu_title`、`status_bar_notification_info_overflow`），**不含任何业务话术**。全部中文业务话术都写在 **base `RES\values\strings.xml`（无 locale 限定符，默认即中文）**，共 238 条。→ 复刻时若把中文话术搬到 `values-zh-rCN`，会造成资源表 config 集合与原包不一致。

### 2.5 appcompat / androidx 残留资源：可删 vs 必须保留

**残留规模**：`public.xml` 中 `abc_*` 名字 **256 个**（跨 8 类型：dimen 81 / drawable 79 / layout 28 / string 27 / color 25 / anim 12 / bool 2 / integer 2）；在 `res\` 中对应**文件 139 个**。另有 support 系残留 12 个：`compatible_scroll_bar, notification_action, notification_action_background, notification_action_tombstone, notification_template_custom_big, notification_template_icon_group, notification_template_part_chronometer, notification_template_part_time, select_dialog_item_material, select_dialog_multichoice_material, select_dialog_singlechoice_material, support_simple_spinner_dropdown_item`。`androidx_*` 前缀仅 2 条（均为 color）：`androidx_core_ripple_material_light`(0x7f06001b)、`androidx_core_secondary_text_default_material_light`(0x7f06001c)。

**必须保留（被非 abc_ 文件引用，删除会导致 aapt2 编译失败）**：以下 57 个被 `RES\values\styles.xml`（appcompat 的 `Theme.*/Base.*` 样式集，384 个 style）或 layout 文本引用：
`abc_ab_share_pack_mtrl_alpha, abc_action_bar_item_background_material, abc_action_mode_close_item_material, abc_alert_dialog_material, abc_background_cache_hint_selector_material_dark/_light, abc_cab_background_top_material, abc_control_background_material, abc_dialog_material_background, abc_edit_text_material, abc_grow_fade_in_from_bottom, abc_hint_foreground_material_dark/_light, abc_ic_ab_back_material, abc_ic_arrow_drop_right_black_24dp, abc_ic_clear_material, abc_ic_commit_search_api_mtrl_alpha, abc_ic_go_search_api_material, abc_ic_menu_copy_mtrl_am_alpha, abc_ic_menu_cut_mtrl_alpha, abc_ic_menu_paste_mtrl_am_alpha, abc_ic_menu_selectall_mtrl_alpha, abc_ic_menu_share_mtrl_alpha, abc_ic_search_api_material, abc_ic_voice_search_api_material, abc_item_background_holo_dark/_light, abc_list_divider_mtrl_alpha, abc_list_selector_holo_dark/_light, abc_menu_hardkey_panel_mtrl_mult, abc_popup_background_mtrl_mult, abc_popup_enter/_exit, abc_primary_text_disable_only_material_dark/_light, abc_primary_text_material_dark/_light, abc_search_dropdown_item_icons_2line, abc_search_url_text, abc_search_view, abc_secondary_text_material_dark/_light, abc_seekbar_tick_mark_material, abc_select_dialog_material, abc_shrink_fade_out_from_bottom, abc_switch_thumb_material, abc_switch_track_mtrl_alpha, abc_text_cursor_material, abc_textfield_search_material, abc_tooltip_enter/_exit`（证据：各名字在 `RES\values\styles.xml` 中出现，例 `abc_ic_ab_back_material` 见 styles.xml 的 `Base.V21.Theme.AppCompat` 等）
+ 仅被 abc_ 内部互相引用的 5 个（`abc_action_mode_bar, abc_alert_dialog_button_bar_material, abc_alert_dialog_title_material, abc_list_divider_material, abc_screen_content_include`）——保留成本低，**建议整体保留**。

**零引用（可删候选，82 个）**：`abc_action_bar_title_item, abc_action_bar_up_container, abc_action_menu_item_layout, abc_action_menu_layout, abc_activity_chooser_view, abc_activity_chooser_view_list_item, abc_btn_*（18 个：borderless_material, check_material, check_material_anim, check_to_on_mtrl_000/015, colored_borderless_text_material, colored_material, colored_text_material, default_mtrl_shape, radio_material, radio_material_anim, radio_to_on_mtrl_000/015, switch_to_on_mtrl_00001/00012）, abc_cab_background_internal_bg, abc_cab_background_top_mtrl_alpha, abc_cascading_menu_item_layout, abc_color_highlight_material, abc_dialog_title_material, abc_expanded_menu_layout, abc_fade_in/_out, abc_ic_menu_overflow_material, abc_list_*（focused/longpressed/pressed/selector_* 等 10 个 holo 态）, abc_popup_menu_header_item_layout, abc_popup_menu_item_layout, abc_ratingbar_* (3), abc_screen_simple*, abc_screen_toolbar, abc_scrubber_* (5), abc_seekbar_thumb/track_material, abc_slide_in/out_* (4), abc_spinner_* (2), abc_star_* (2), abc_tab_indicator_* (2), abc_text_select_handle_* (3)…`

> ⚠️ **重要前提**：本 APK **无 classes.dex**，代码侧（含 appcompat 库代码）的资源引用在文本层不可见，因此上表「零引用」仅代表 **res 文本引用为零**，不等于运行时不用。**结论：复刻一期不建议删任何 abc_/androidx_ 资源**（删了既无法证明等价，又会破坏 3738 entry 与 public.xml ID 一致性）；若后续要做精简，须作为独立任务并以「运行时无引用」为前提重新评估。

### 2.6 其它值得记录的资源事实

- `RES\values\attrs.xml` 769 条 attr（大量 `android:^attr-private.*`、`darkIconTheme/lightIconTheme/wallpaperTextColor` 等**平台私有属性**，说明是系统内部资源包）。
- `RES\xml\fileprovider.xml` 仅 1 条路径：`cache-path name="leak" path="leak/"`（命名 `leak` 值得注意，但仅为录屏临时文件目录）。
- `RES\drawable\icon.xml` 为向量图，但 badging 对 120–65534 所有密度都指向 `res/drawable/icon.xml`（`_badging.txt:151-157`）。
- 布局中引用 **21 个 `com.xtc.*` 自定义 View**（45/126 个布局文件含 `com.xtc`），如 `com.xtc.statusbar.XtcStatusBarView / SwipeDismissLayout / StatusTipView`、`com.xtc.ui.widget.permission.cta.CtaPermissionView`、`com.xtc.ui.widget.toggleswitch.SwitchButton`、`com.xtc.ui.widget.scalablecontainer.AppScrollView`；另有 `com.airbnb.lottie.LottieAnimationView`、`com.android.common.library.widget.viewpager.ViewPager`、`com.android.systemui.qs.*`。**这些类不在本包（无 dex），必须由系统镜像/其余 dex 提供**——这是"资源能编过、运行才算成功"的关键依赖。

---

## 3. 字符串盘点

### 3.1 base `RES\values\strings.xml`（238 条）分组

| 组 | 条数 | 说明 / 代表条目 |
|---|---|---|
| A. appcompat 内置（英文） | 29 | `abc_*` 27 + `search_menu_title` + `status_bar_notification_info_overflow` |
| B. 隐私协议 / 权限弹窗 | 30 | `privacy*`(24)、`cta_allow_permission*`(3)、`agree/disagree/allow`、`about_settings*` |
| C. 禁用 / 限额（家长管控话术） | 27 | `app_forbid*`、`tip_class_*`(4)、`tip_sleep_*`(4)、`tip_official_disable_*`(3)、`tip_high_temp_*`(2)、`watch_long_endurance_unsupport_*`(3)、`long_battery_unsupport_*`(3)、`remind_mode_forbid_by_guardian`、`how_to_exit_app_forbid*`、`how_to_exit_duration`、`tip_temporarily_inoperable` |
| D. 录屏 / 录音 | 17 | `screen_record*`(8)、`recording/string_recording`、`by/from_screen_record`、`record_internal_audio`、`start_screen_recording`、`ready_for_screen_record`、`able_record/second_to_record`、`can_not_record_screen_cause_storage`、`disable_screen_record_when_open_camera`、`interrupt_screen_record_when_open_camera` |
| E. 手电筒 / 摄像头 | 6 | `flash_sos_title/tip`、`string_flash_title`、`string_light_title`、`no_flashlight_when_use_camera`、`interrupt_flashlight_when_use_camera` |
| F. 下拉刷新 / 加载更多 | 37 | `cube_ptr_*`(12)、`ptr_*`(3)、`pull_down*`(4)、`load*`(6)、`refresh*`(3)、`last_update`、`*_ago`、`noMore/no_more/no_mores`、`sina_load_more_*`、`swipe_up_load_more`、`click_to_load_more` |
| G. 快捷开关 / 音量 / 运营商状态 | 32 | `wifi_*`、`blue_*`、`airplane_*`、`auto_brightness_*`、`brightness_value`、`*_volume*`、`remind_mode_*`(3)、`watch_long_battery*`、`low_power*`、`operator_*`、`string_sim_*`(3)、`string_no_sim_card`、`string_open_color_corrector`、`system_volume_low`、`volume_slient`、`steps`、`shut_down` |
| H. 其它 / 通用 | 60 | `app_name`(=SystemUI)、`config_statusBarComponent`、`config_systemUIFactoryComponent`、`velocity_tracker_impl`、`quick_settings_tiles*`(3)、`guide_layout_anim_text9`、`fun_switch_name_flashlight`、`assistant_start_app`、`contact_head_*_tag`、`get_contact_info`、`get_location_info`、`ipc_information`、`init_wx_notice`、按钮类（`cancel/sure/open/exit/copy/delete/send/...`） |

### 3.2 `values-zh-rCN\strings.xml` 全量（29 条，键名→原文；均为 appcompat 内置）

| 键 | 原文 | 键 | 原文 |
|---|---|---|---|
| abc_action_bar_home_description | 转到首页 | abc_menu_sym_shortcut_label | Sym+ |
| abc_action_bar_up_description | 转到上一层级 | abc_prepend_shortcut_label | Menu+ |
| abc_action_menu_overflow_description | 更多选项 | abc_search_hint | 搜索… |
| abc_action_mode_done | 完成 | abc_searchview_description_clear | 清除查询 |
| abc_activity_chooser_view_see_all | 查看全部 | abc_searchview_description_query | 搜索查询 |
| abc_activitychooserview_choose_application | 选择应用 | abc_searchview_description_search | 搜索 |
| abc_capital_off | 关闭 | abc_searchview_description_submit | 提交查询 |
| abc_capital_on | 开启 | abc_searchview_description_voice | 语音搜索 |
| abc_menu_alt_shortcut_label | Alt+ | abc_shareactionprovider_share_with | 分享对象 |
| abc_menu_ctrl_shortcut_label | Ctrl+ | abc_shareactionprovider_share_with_application | 与%s分享 |
| abc_menu_delete_shortcut_label | Delete 键 | abc_toolbar_collapse_description | 收起 |
| abc_menu_enter_shortcut_label | Enter 键 | search_menu_title | 搜索 |
| abc_menu_function_shortcut_label | Fn+ | status_bar_notification_info_overflow | 999+ |
| abc_menu_meta_shortcut_label | Meta+ | — | — |
| abc_menu_shift_shortcut_label | Shift+ | — | — |
| abc_menu_space_shortcut_label | 空格键 | — | — |

> 证据：`RES\values-zh-rCN\strings.xml:3-31`（29 条全部为 appcompat 内置中文本地化，**无任何业务话术**）。

### 3.3 含 `$${...}$` 富文本标记的条目（全库仅 2 个键）

| 键 | 原文（base 中文） | 证据 | locale 差异 |
|---|---|---|---|
| `app_durtaion_tip_content` | `请在最新版$${XTC}APP-更多-应用禁用$中调整$本应用的每日可用时长$或修改$应用禁用设置$，即可正常使用` | `RES\values\strings.xml:37` | en 作 `${XTC}`（单 `$`，`values-en\strings.xml:10`）、th 作 `${XTC}`（`values-th:37`）、zh-rHK/zh-rTW 保持 `$${XTC}`（各 `:37`） |
| `how_to_exit_app_forbid_content` | `请在最新版$${XTC}APP-更多-应用禁用$中，修改$本应用的禁用设置$，即可正常使用` | `RES\values\strings.xml:95` | en `$${XTC}`（`values-en:63`）、in-rID `$Aplikasi $${XTC}`（`values-in-rID:51`）、th `$${XTC}`（`values-th:78`）、zh-rHK/TW `$${XTC}`（各 `:90`） |

> `$${XTC}` 是该 App 富文本占位符（渲染成"小天才"高亮文本），`$...$` 为强调片段。**locale 间写法不统一（单 `$` / 双 `$`）是原包既有状态，必须原样复刻**，否则字符串校验会失败。

### 3.4 与 XTC / 家长 / 隐私协议 / 应用禁用 / 上报 / 云控 相关的关键条目（base 中文原文）

| 键 | 原文 | 证据行 |
|---|---|---|
| privacy | 隐私政策 | 131 |
| privacy_agreement | 用户服务协议 | 132 |
| privacy_custom | 手机扫码查看\n《%s》 | 133 |
| privacy_dialog_title | 为保障你的个人权益，在使用%1$s前，请在父母或其他监护人陪同下阅读并同意用户服务协议及隐私政策： | 136 |
| privacy_dialog_title_empty_app_name | 为保障你的个人权益，在使用应用前，请在父母或其他监护人陪同下阅读并同意用户服务协议及隐私政策： | 137 |
| privacy_disclaimer | 免责声明 | 138 |
| privacy_link / _agreement / _disclaimer | 详情查看隐私政策 / 详情查看用户服务协议 / 查看免责声明 | 139-141 |
| privacy_dangerous_permissions_title | 本应用需要联网，下列权限仅在你使用对应功能时申请授权 | 134 |
| privacy_dangerous_permissions_title_only_net | 使用本应用时需要联网 | 135 |
| privacy_list_titile1-4 | 为确保正常使用,使用该应用基础功能需同意以下权限 / 下列权限仅在你使用对应功能时申请授权 / 应用使用过程中将会使用以下权限 / 同意应用使用以下权限 | 143-146 |
| privacy_title1 / 2 / complete / no_permission / no_network_no_permission | 为保障你的个人权益，在使用{0}前请先阅读并同意隐私政策，所需权限如下： / …需你同意政策及获取下列权限满足基础功能使用： / …并在征得父母或其他监护人同意后，点击同意并接受隐私协议： / …请在父母或其他监护人陪同下阅读并同意隐私政策： / …阅读并同意隐私政策。 | 147-151 |
| privacy_url | 手机扫码查看\n《隐私政策》 | 152 |
| cta_allow_permission | 使用{0}需向你申请以下权限： | 62 |
| cta_allow_permission_multi_entry_app | 使用{0}及其他同类应用需向你申请以下权限： | 63 |
| cta_allow_permission_pre_icon | 使用{0}需向你申请应用中心权限： | 64 |
| app_forbid | 应用禁用 | 38 |
| app_forbid_remind | 目前"%s"处于禁用状态，无法使用 | 39 |
| app_durtaion_tip_content | 见 3.3 | 37 |
| how_to_exit_app_forbid | 如何退出应用禁用？ | 94 |
| how_to_exit_app_forbid_content | 见 3.3 | 95 |
| how_to_exit_duration | 如何退出应用限额？ | 96 |
| remind_mode_forbid_by_guardian | 已禁用，去家长APP设置 | 171 |
| low_power_tip | 电量不足10%啦！为了\n家长能够及时联系你，… | 115 |
| tip_class_* / tip_sleep_* / tip_official_disable_* / tip_high_temp_* | 上课禁用中 / 睡眠模式中 / 智能禁用中 / 高温禁用中 + （仅支持振动或静音调节 / 退出后可正常使用 / 暂无法更改设置 / 暂时无法操作） | 213-226 |
| watch_long_battery* / long_battery_unsupport_* / watch_long_endurance_unsupport_* | 省电模式（长续航）相关受限话术 | 234-238, 111-113, 236-238 |
| screen_record_using_tips | 使用录屏会录制手表屏幕上显示的内容…要注意保护隐私信息，避免分享导致隐私信息泄露 | 182 |
| get_location_info / get_contact_info | 获取位置信息 / 读取联系人信息 | 89 / 88 |

### 3.5 `permission_label` 数组（隐私/权限弹窗用的中文权限名，20 项）

`RES\values\arrays.xml:11-32`：拨打电话｜接收短信、发送短信｜访问网络｜进行本地录音｜读写短信内容｜读取联系人信息｜写入或删除联系人｜读取通话记录｜修改通话记录｜获取位置信息｜拍摄照片和录制视频｜修改网络状态｜读取WLAN连接状态｜开启或关闭WLAN｜开启或关闭蓝牙｜读取设备通话状态｜获取位置信息｜访问媒体影音数据｜访问媒体影音数据｜智能卡权限
（该数组与 `privacy_*` 文案、`CtaPermissionView`/`item_cta_permission*.xml`/`layout_cta_permission_dialog.xml` 共同构成**权限申请/隐私合规 UI**，是本包最显眼的"权限检测"资源面）

### 3.6 其它字符串资源

- `config_systemUIServiceComponents` = `[Dependency, CommandQueue$CommandQueueStart, SystemBars]`、`config_systemUIServiceComponentsPerUser` = `[Dependency]`（`RES\values\arrays.xml:3-10`）→ SystemUI 启动组件清单，**改动会直接影响启动行为**。
- `quick_settings_tiles_default` / `_stock` = `wifi,bt,flashlight,ringMode,powerSave,screenRecord,airplane`、`quick_settings_tiles`=`default`（`strings.xml:162-164`）→ 下拉快捷开关默认 7 项。

---

## 4. assets 盘点（共 31 个文件，全部为 Lottie 动画 json + 位图）

### 4.1 总览

| 目录 | 文件数 | 字节合计 | 内容 |
|---|---|---|---|
| `assets\guide\`（13 个子目录） | 24 | 289,629 | 13 个引导动画 json（其中 `long_press_swipe_hide`、`rotate_device`、`rotate_device_I32` 各带 `images\`，`switch_camera_tip` 平铺 5 张 png） |
| `assets\pull_up_setting\` | 7 | 60,075 | 7 个下拉快捷开关图标动画 json |
| 合计 | **31** | **349,704** | — |

压缩方式（APK 内）：**png/jpg 全部 stored(0)**，**全部 json 均 deflate(8)**（证据：zip `method` 字段）。复刻必须保持一致。

### 4.2 `assets\guide\*` 引导动画（Lottie/bodymovin json）

| 目录 / 文件 | 字节 | v | fr | w×h | 帧数(op-ip) | layers | assets | nm（合成名） | 用途推测 |
|---|---|---|---|---|---|---|---|---|---|
| `guide\down_notifi\data.json` | 16,907 | 5.1.20 | 25 | 150×168 | 111 | 8 | 2 | upanddown | 下拉通知栏引导 |
| `guide\down_notifi\data1.json` | 27,733 | 5.6.3 | 25 | 150×168 | 253 | 13 | 3 | 下拉快捷方式 | 下拉快捷方式/开关引导 |
| `guide\flipup_camera\data.json` | 17,690 | 5.1.20 | 25 | 220×198 | 72 | 6 | 3 | 预合成 15 | 翻起摄像头引导（手电筒前提） |
| `guide\hori_slide\data.json` | 31,895 | 5.2.0 | 25 | 150×168 | 172 | 9 | 4 | leftandright | 左右滑动（切卡片/通知） |
| `guide\icon_merge\data.json` | 16,295 | 5.6.6 | 25 | 150×168 | 219 | 10 | 0 | 预合成 15 | 图标合并（通知堆叠） |
| `guide\icon_move\data.json` | 16,147 | 5.6.6 | 25 | 150×168 | 138 | 10 | 0 | 预合成 15 | 图标移动 |
| `guide\longpre_sysvoice\data.json` | 12,508 | 5.6.6 | 25 | 220×198 | 114 | 5 | 2 | 预合成 15 | 长按唤起语音助手 |
| `guide\longpre_sysvoice_right_up\data.json` | 12,524 | 5.6.6 | 25 | 220×168 | 84 | 5 | 2 | 预合成 15 | 长按+右上滑语音助手 |
| `guide\long_press_swipe_hide\data.json` | 8,093 | 5.9.0 | 25 | 320×360 | 76 | 1 | 7 | 合成 2 | 长按上滑隐藏；**位图** `images\zujianxiala_0/1/2.png`(149×154, 287×92, 76×40) + `zujianxiala_3.jpg`(320×360) |
| `guide\rotate_device\data.json` | 30,745 | 5.6.6 | 25 | 220×198 | 166 | 3 | 7 | 透明度显示效果 | 旋转手表引导；**位图** `images\img_route_rotate.png`(192×42) |
| `guide\rotate_device_I32\data.json` | 36,672 | 5.9.0 | 25 | 220×198 | 226 | 3 | 9 | 透明度显示效果 | I32 机型专用旋转引导；**位图** `images\img_route_i32_rotate.png`(192×42) |
| `guide\swipe_exit\data.json` | 16,238 | 5.1.20 | 25 | 150×168 | 114 | 8 | 1 | back | 右滑退出引导（配 `slide_right_to_exit`） |
| `guide\switch_camera\switch_camera_tip.json` | 11,866 | 5.2.0 | 25 | 320×360 | 336 | 3 | 7 | 预合成 2 | 手电筒"先翻起摄像头"提示；**位图** 5 张，见 4.5 |
| `guide\switch_camera_tip\img_0..4.png` | 178/2,992/156/3,594/2,949 | — | — | 320×360 / 92×127 / 10×10 / 174×46 / 226×33 | — | — | — | — | 上条 json 的 `lottie_imageAssetsFolder` 图片资源（平铺在本目录，不在 `images\` 子目录） |

### 4.3 `assets\pull_up_setting\*` 下拉快捷开关动画（7 个）

| 文件 | 字节 | v | w×h | 帧数 | layers | assets | nm | 对应开关 |
|---|---|---|---|---|---|---|---|---|
| `bell.json` | 6,098 | 5.6.10 | 90×90 | 27 | 1 | 2 | 铃铛 | 铃声模式-铃声 |
| `bell_vibration.json` | 9,618 | 5.6.10 | 90×90 | 27 | 3 | 2 | 响铃加震动 | 铃声模式-响铃+振动 |
| `vibration.json` | 6,439 | 5.6.10 | 90×90 | 18 | 3 | 1 | 震动 | 铃声模式-振动 |
| `silence.json` | 7,669 | 5.12.1 | 92×92 | 11 | 5 | 2 | 合成 1 | 铃声模式-静音（**顶层多 `props` 字段**） |
| `bluetooth.json` | 10,515 | 5.9.0 | 90×90 | 22 | 3 | 0 | 蓝牙 | 蓝牙开关 |
| `wifi.json` | 6,054 | 5.12.1 | 90×90 | 17 | 4 | 0 | wifi 4 | WLAN 开关（**顶层多 `props`**） |
| `power_save.json` | 13,682 | 5.9.0 | 90×90 | 16 | 4 | 0 | 合成 1 | 省电模式开关 |

> 注意：任务描述中的"9 个快捷开关 json"与实际不符，**实际为 7 个**。与 `quick_settings_tiles_default`（wifi,bt,flashlight,ringMode,powerSave,screenRecord,airplane，共 7 tile）对照：本目录覆盖 `wifi/bt/ringMode(4 态)/powerSave`，**没有 flashlight 与 screenRecord 的动画**（手电筒用 `guide/switch_camera` 动画，录屏无 Lottie 资源）。

### 4.4 json 顶层字段含义（全部 20 个 json 结构一致，Lottie/Bodymovin 导出）

| 字段 | 含义 | 实测取值 |
|---|---|---|
| `v` | Lottie(bodymovin) 导出器版本 | 5.1.20 / 5.2.0 / 5.6.3 / 5.6.6 / 5.9.0 / 5.6.10 / 5.12.1 |
| `fr` | 帧率 | 全部 25 fps |
| `ip` / `op` | 入点/出点帧号 → 时长 =(op-ip)/fr 秒 | guide 72–336 帧（2.9–13.4s）；开关 11–27 帧（0.4–1.1s） |
| `w` / `h` | 合成画布宽高（px） | guide：150×168 / 220×198 / 220×168 / 320×360；开关：90×90 / 92×92 |
| `nm` | 合成名（设计稿中文名） | upanddown / leftandright / back / 铃铛 / 蓝牙 / wifi 4 … |
| `ddd` | 是否 3D | 全部 0（2D） |
| `assets` | 预合成（`comp_N`）+ 位图资源 | 位图项含 `id,w,h,u,p`（例 `u="images/", p="zujianxiala_0.png"`） |
| `layers` | 图层数组 | 图层含 `ind`(层序) `ty`(类型：0=预合成,2=图片,4=形状) `ks`(变换) `refId`(引用预合成) `sr`(时间伸缩) `ip/op/st`(层区间) |
| `markers` | 时间线标记 | 全部空数组 |
| `props` | 新版附加字段 | 仅 `silence.json` / `wifi.json`（5.12.1）出现 |

结构示例（`pull_up_setting\bell.json`，截断）：
```json
{"v":"5.6.10","fr":25,"ip":0,"op":27,"w":90,"h":90,"nm":"铃铛","ddd":0,
 "assets":[{"id":"comp_0","layers":[ ... ]},{"id":"comp_1", ... }],
 "layers":[
   {"ddd":0,"ind":1,"ty":0,"nm":"预合成 1","refId":"comp_1","sr":1,
    "ks":{"o":{"a":0,"k":100},"r":{...},"p":{"a":0,"k":[19,0,0]},"a":{...},"s":{...}},
    "ao":0,"w":38,"h":40,"ip":0,"op":750,"st":0,"bm":0},
   {"ddd":0,"ind":2,"ty":4,"nm":"3","sr":1,
    "ks":{"p":{"a":1,"k":[{"t":0,"s":[19,0,0],"e":[11,0,0]}, ... ]}},   // 关键帧位移
    "shapes":[{"ty":"gr","it":[{"ty":"sh","ks":{"a":0,"k":{"v":[[...]]}}}, ... ]}]}
 ],
 "markers":[]}
```
（`guide\down_notifi\data.json` 同构，`assets` 为 `comp_0/comp_1` 两个预合成，`layers` 8 层，`nm="upanddown"`）

### 4.5 Lottie 与布局的绑定证据（复刻必读：assets 路径写死在布局属性里）

`RES\layout\layout_switch_flashlight.xml:13-19`：
```
<com.airbnb.lottie.LottieAnimationView
    android:id="@+id/lav_tip"
    android:background="@drawable/switch_camera_tip_bg"
    custom:lottie_fileName="guide/switch_camera/switch_camera_tip.json"
    custom:lottie_imageAssetsFolder="guide/switch_camera_tip"/>
```
→ 这解释了 `guide\switch_camera\`（只放 json）与 `guide\switch_camera_tip\`（只放 5 张 png）的**目录拆分是设计如此**，不是缺文件。**assets 路径必须逐字符保留**。

---

## 5. 资源层风险线索（供「去云控 / 去上报 / 去权限检测」）

> 前置结论：本包**无 classes.dex**，云控/上报的**实现逻辑不可见**；资源层能提供的证据 = 权限声明、组件（尤其 exported+broadcast action+authority）、meta-data 开关、残留文件。**资源层内不存在任何域名/IP/URL 字符串**（已全量扫描 `RES` 文本，`http(s)` 命中全部是 `xmlns` 命名空间）。

### 5.1 权限声明类（清单级，最高优先级）

| # | 权限 / 声明 | 位置 | 风险判定 |
|---|---|---|---|
| 1 | `com.xtc.http.bigdata.provider.permission.READ_CONTENT` | `M:65` | **上报**（XTC 大数据 provider 读写权限） |
| 2 | `com.xtc.http.bigdata.provider.permission.WRITE_CONTENT` | `M:66` | **上报** |
| 3 | `com.xtc.dns.provider.HttpDnsContentProvider` + `com.android.systemui.permission.READ_DNS`（normal）+ `com.xtc.i3launcher.permission.READ_DNS` | `M:164-172, 71-73, 68, 69` | **云控**（HTTPDNS 域名解析通道，可改变后端域名指向） |
| 4 | `android.permission.systemui.IDENTITY`（signature，自定义） | `M:14-16` | **权限/签名检测**（占用系统命名空间的身份权限） |
| 5 | `com.android.systemui.permission.SELF` / `PLUGIN`（signature） | `M:48-54` | **权限检测**（自我保护/插件校验） |
| 6 | `com.xtc.systemui.keyguard.permission.KEYGUARD_SERVICE` | `M:56` | 功能（锁屏服务）+ 跨包依赖 |
| 7 | `com.xtc.i3launcher.permission.LAUNCHER_PROXY_SERVICE` | `M:57` | 功能（桌面代理），跨包依赖 |
| 8 | `com.xtc.systemui.proxy.permission.STATUS_BAR` / `...statusbar.permission.StatusBarProxyProvider`（均 signature） | `M:58-63` | 功能（状态栏代理），对外暴露状态栏控制 |
| 9 | `android.Manifest.permission#WRITE_SECURE_SETTINGS` | `M:64` | **无害但异常**：名称含 `#` → 无效权限名（原包缺陷），**必须原样保留** |
| 10 | `android.perminssion.WRITE_MEDIA_STORAGE` | `M:70` | **无害但异常**：`perminssion` 拼写错误 → 无效权限名，**必须原样保留** |
| 11 | `android.permission.RECORD_AUDIO` + `supportScreenRecordAudio=1` | `M:47, 144-146` | 功能（录系统声），隐私相关 |
| 12 | `android.permission.DUMP` / `INJECT_EVENTS` / `READ_FRAME_BUFFER` / `MANAGE_APP_TOKENS` | `M:23,22,41,42` | 系统特权（SystemUI 必需） |

### 5.2 组件级（上报/云控/保活的入口点）

| 组件 | 位置 | 证据 | 判定 |
|---|---|---|---|
| `com.xtc.bigdata.collector.exception.CrashIntentService` | service | `M:173-175` | **上报**：崩溃采集上报服务 |
| `com.xtc.bigdata.monitor.receiver.MonitorStartReceiver`（actions `com.xtc.watchdogs`、`com.xtc.launcher.ACTION_CHANGE_REGION`，priority=100000，无 permission） | receiver | `M:176-183` | **上报/云控**：看门狗唤活 + 地区变更触发，任意应用可广播唤起 |
| `com.xtc.xtcoco.CodeCoverageReceiver`（action `com.xtc.report.generate.ec`，exported=true） | receiver | `M:154-160` | **上报**（覆盖率/数据触发），任意应用可触发 |
| `com.xtc.dns.provider.HttpDnsContentProvider`（exported=true, allowLaunchOnPowerSaveMode=true） | provider | `M:164-172` | **云控**：HTTPDNS |
| `com.android.systemui.statusbar.provider.StatusBarProxyProvider`（exported=true） | provider | `M:133-137` | 功能（跨进程状态栏控制），攻击面 |
| `com.xtc.architecture.mvp.PermissionListActivity` + `com.xtc.ui.widget.privacy.PrivacyUriActivity` + `CtaPermissionView`/`privacy_*` 文案 + `permission_label` 数组 | activity/资源 | `M:161-163,184-186`；`RES\values\arrays.xml:11-32`；`RES\values\strings.xml:131-152` | **权限检测/隐私合规 UI**（改造时的重点 UI 面） |
| `com.xtc.ui.widget.selfstart.SelfStartActivity` + `XtcStayFront=true` | activity/meta | `M:187, 99-101` | 保活/自启动引导 |

### 5.3 application / 组件 meta-data 开关

| key | 位置 | 判定 |
|---|---|---|
| `moduleSwitch` = `[{module:3547, funSwitch:com.android.systemui.screenrecord, notBind:3}]` | `M:138-140` | **云控口径**（平台模块号 3547=录屏；`notBind:3` 疑为分档绑定策略） |
| `funSwitch` = `[{appPackage:com.android.systemui.screenrecord, funSwitch:1}]` | `M:141-143` | **云控口径**（功能开关=开） |
| `supportScreenRecordAudio` = `1` | `M:144-146` | 能力开关 |
| `useXtcPublicApi` = `true` | `M:188-190` | **XTC 公共 API 接入**（跨应用能力开放） |
| `allowLaunchOnPowerSaveMode` = `true` | `M:169-171` | 省电模式仍可拉起（DNS provider 常驻） |
| `assistantapi-directive` = `[FlashLight,VideoRecord]` | `M:116-118` | 语音指令白名单 |
| `XtcStayFront` = `true` | `M:99-101` | 保持前置（保活） |

### 5.4 残留文件类

| 文件 | 大小 / 行号 | 内容摘要 | 判定 |
|---|---|---|---|
| `R\jacoco-agent.properties` | 11 B / L1 | `output=none` | 无害（JaCoCo 覆盖率残留；无 dex，无实际作用） |
| `R\version.txt` | 16 B / L1 | `isoparser-1.1.21` | 无害（第三方库版本残留） |
| `R\builddef.lst` | 44,769 B / 390 行（L1-9 为 ajc 参数） | AspectJ 构建定义清单，含构建机绝对路径 `C:\Users\sannies\.m2\repository\...`、`C:\dev\mp4parser\target\checkout\isoparser\...` | **无害但泄露构建路径**（第三方 mp4parser/isoparser 的构建残留） |
| `R\isoparser-default.properties` | 12,965 B | isoparser 默认配置 | 无害（功能相关，录屏 MP4 封装） |
| `R\DebugProbesKt.bin` | 1,738 B | kotlinx-coroutines 调试探针 | 无害 |
| `R\lib\armeabi\libxtcSecurity.so` | 144,956 B | XTC 安全库（签名/加密/校验） | **中风险：权限/签名检测能力载体**；复刻若去掉，替换包可能无法通过 XTC 侧校验 |
| `R\lib\armeabi\libmmkv.so` | 219,292 B | 腾讯 MMKV 本地 KV 存储（可能存设备/账号标识） | 低风险（本地存储，非上报） |
| `*.proto` ×11（根目录，BLE 协议：`BleBindStateRequest/BleClearDataRequest/BleCommunicateRead/BleCommunicateRequest/BleCommunicateResponse/BleConnectRequest/BleConnectResponse/BleDiscoveryScanRequest/BleScanRequest/BleScanResponse/BleServerVersionResponse`）+ `google\protobuf\*.proto` ×10 | 413–669 B / 2,341–8,185 B | protobuf 描述符残留 | 无害（BLE 通信协议定义 + protobuf 运行时） |
| `META-INF\*.version` ×32 | 各 5–6 B | androidx/kotlinx 版本标记 | 无害（复刻时按同名同值重建） |
| `META-INF\CERT.RSA`(1,524) / `CERT.SF`(131,349) / `MANIFEST.MF`(131,279) | 三件套 | v1 签名文件 | **需注意**：`apksigner` 判定 **v1 校验失败(false)、仅 v2 通过** → 属失效残留。复刻时**不要直接拷贝这三件**，应由 apksigner 重新签名 |
| `okhttp3\internal\publicsuffix\publicsuffixes.gz` | 34,000 B | OkHttp 公共后缀数据 | 无害（网络库自带，说明存在 HTTP 客户端能力） |
| `com\j256\ormlite\{android,core}\LICENSE.txt/README.txt` | 4 文件 | ORMLite 许可 | 无害 |
| `META-INF\kotlin*.kotlin_module` ×8、`com.android.tools\r8*` ×3、`META-INF\services\*` ×2、`META-INF\rxjava.properties` | — | Kotlin/R8/RxJava 元数据 | 无害 |

### 5.5 「去云控/去上报/去权限检测」的资源层切除点（结论）

1. **去上报**：`com.xtc.bigdata.*` 两个组件（`M:173-175`、`M:176-183`）+ `CodeCoverageReceiver`（`M:154-160`）+ 两条 `com.xtc.http.bigdata.provider.permission.*`（`M:65-66`）。资源层无对应资源，切除后 `res`/`assets` 无变化 → **不影响资源一致**。
2. **去云控**：`HttpDnsContentProvider`（`M:164-172`）+ `READ_DNS` 两权限（`M:68-69`）+ `moduleSwitch/funSwitch` meta-data（`M:138-143`）。注意 `moduleSwitch/funSwitch` 只体现在 AndroidManifest.xml 文本里（badging 不输出 meta-data），删改虽不影响 badging，但会改变清单文本 → 需 Lead 决策是否属于「信息一致」范围。
3. **去权限检测**：`PermissionListActivity`/`PrivacyUriActivity`/`CtaPermissionView`/`privacy_*` 30 条文案/`permission_label` 20 项/`item_cta_permission*.xml`/`layout_cta_permission_dialog.xml`/`view_cta_permission.xml`。**这一条会大范围改动 res**（至少 30+ 字符串 + 5+ 布局 + 1 数组），与"APK 全部信息一致"目标直接冲突 → 建议仅做记录，不改。
4. 权限声明（`M:14-73`）**全部是清单文本**：任何增删都会使 badging 对齐清单（第 6 节）失败。

### 5.6 最高风险 5 条（TL;DR）

1. **上报链**：`com.xtc.bigdata.*`（CrashIntentService / MonitorStartReceiver，后者 exported+无 permission+priority=100000） + `CodeCoverageReceiver`(action `com.xtc.report.generate.ec`, exported=true) + 2 条 `com.xtc.http.bigdata.provider.permission.*`。
2. **云控链**：`HttpDnsContentProvider`（authority `com.android.systemui.com.xtc.httpdns.provider`，exported=true，`allowLaunchOnPowerSaveMode=true`）+ `READ_DNS` 权限（一个 normal 自声明）+ `moduleSwitch/funSwitch` 平台开关。
3. **权限/签名检测链**：`android.permission.systemui.IDENTITY` + `com.android.systemui.permission.SELF/PLUGIN` + `libxtcSecurity.so` + `sharedUserId=android.uid.system` + `persistent/directBootAware`；另有失效 v1 签名残留（`META-INF/CERT.*`）。
4. **清单一致性地雷 2 处**：`android.Manifest.permission#WRITE_SECURE_SETTINGS`（L64）、`android.perminssion.WRITE_MEDIA_STORAGE`（L70）——错拼/非法名必须原样保留。
5. **中文话术位置陷阱**：中文业务文案全在 **base `values\strings.xml`**（238 条），`values-zh-rCN` 只有 29 条 appcompat 中文；另 `$${XTC}` 富文本写法在多个 locale 间不统一（16 个出现处中 **11 处为 `$${XTC}`、5 处为 `${XTC}`**，无缺失）——**必须原样复刻**。

---

## 6. badging 对齐清单（`aapt2 dump badging` 原样，用于验收比对）

原始文件：`analysis\res\_badging.txt`（177 行）

```
package: name='com.android.systemui' versionCode='18000' versionName='1.8.00.Develop_AW_XtcSystemUI_J168_Gc23339b' platformBuildVersionName='11' platformBuildVersionCode='30' compileSdkVersion='30' compileSdkVersionCodename='11'
sdkVersion:'27'
targetSdkVersion:'30'
```

### 6.1 uses-permission（42 条，**保持原始顺序**）

```
RECEIVE_BOOT_COMPLETED, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, WRITE_MEDIA_STORAGE, WAKE_LOCK,
INJECT_EVENTS, DUMP, WRITE_SETTINGS, STATUS_BAR_SERVICE, STATUS_BAR, EXPAND_STATUS_BAR, WRITE_SECURE_SETTINGS,
BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_NETWORK_STATE, CHANGE_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE,
VIBRATE, REORDER_TASKS, START_ANY_ACTIVITY, GET_TOP_ACTIVITY_INFO, INTERNAL_SYSTEM_WINDOW, SYSTEM_ALERT_WINDOW,
READ_FRAME_BUFFER, MANAGE_APP_TOKENS, REGISTER_WINDOW_MANAGER_LISTENERS, com.android.alarm.permission.SET_ALARM,
MANAGE_MEDIA_PROJECTION, FOREGROUND_SERVICE, RECORD_AUDIO, com.android.systemui.permission.SELF,
BIND_QUICK_SETTINGS_TILE, com.xtc.systemui.keyguard.permission.KEYGUARD_SERVICE,
com.xtc.i3launcher.permission.LAUNCHER_PROXY_SERVICE, android.Manifest.permission#WRITE_SECURE_SETTINGS,
com.xtc.http.bigdata.provider.permission.READ_CONTENT, com.xtc.http.bigdata.provider.permission.WRITE_CONTENT,
INTERNET, com.xtc.i3launcher.permission.READ_DNS, com.android.systemui.permission.READ_DNS,
android.perminssion.WRITE_MEDIA_STORAGE
```
（前 31 条除 `com.android.alarm.permission.SET_ALARM` 外均带 `android.permission.` 前缀，`_badging.txt:4-45`）

### 6.2 uses-feature（1 显式 + 4 隐式，`_badging.txt:159-168`）

| 来源 | feature | 原因 |
|---|---|---|
| 显式（`M:74`） | `android.hardware.type.watch` | manifest 声明 |
| 隐式 | `android.hardware.bluetooth` | 申请 BLUETOOTH + BLUETOOTH_ADMIN 且 targetSdk>4 |
| 隐式 | `android.hardware.faketouch` | 所有应用默认 |
| 隐式 | `android.hardware.microphone` | 申请 RECORD_AUDIO |
| 隐式 | `android.hardware.wifi` | 申请 ACCESS_WIFI_STATE + CHANGE_WIFI_STATE |

### 6.3 其它 badging 字段

| 字段 | 值 |
|---|---|
| application-label / 所有 locale label | `SystemUI`（`_badging.txt:46-150`） |
| application-icon（120/160/240/320/480/640/65534） | 全部 `res/drawable/icon.xml` |
| `provides-component` | `notification-listener`（`_badging.txt:169`） |
| `other-activities / other-receivers / other-services` | 均存在（因无 launcher activity，故不列 launchable-activity） |
| supports-screens | `small normal large xlarge` |
| supports-any-density | true |
| densities | `120 160 240 320 480 640 65534` |
| native-code | `armeabi` |
| locales | 105 项（`--_--` + 104 locale，见 2.4） |

### 6.4 APK 结构基线（重建后逐项比对）

| 项 | 原包值 |
|---|---|
| zip 条目总数 | 1131（根 18、META-INF 50、assets 31、lib 2、res 710、com 4、google 10、kotlin 305、okhttp3 1） |
| `resources.arsc` | 663,920 B，**stored(0)** |
| `AndroidManifest.xml` | 17,628 B，deflate |
| `res/*` | 710 条目（275 stored / 435 deflated） |
| `assets/*` | 31 条目（png/jpg=stored；json=deflate） |
| `lib/armeabi/*.so` | 2 条目，**stored 且带对齐 padding**（extra 1527/1833 B，4K 页对齐） |
| `classes.dex` | **不存在** |
| 签名 | v2 only；证书 DN `EMAILADDRESS=yangwm@dw.gdbbk.com, CN=ywm, OU=XTC, O="BBKEDU ", L=DongGuan View, ST=CuangDong, C=CN`；SHA-256 `c7a460a0d3b8b79c43a06d74eb3609c1f0f1d2fd8cd77b57570cf6ea3ee77c24` |

---

## 7. 复刻验收 Checklist（建议 Lead 直接引用）

- [ ] package=`com.android.systemui`, versionCode=`18000`, versionName=`1.8.00.Develop_AW_XtcSystemUI_J168_Gc23339b`, minSdk 27, targetSdk 30
- [ ] `_badging.txt` 逐行 diff（42 uses-permission 顺序、5 uses-feature、locales 105 项=104 locale+默认、densities、native-code `armeabi`）
- [ ] 资源表：16 个 type（0x01–0x0e + 0x10，**0x0f 仍缺号**）、**3738 entry**、与 `public.xml` ID 一一对应
- [ ] `res/` APK 条目 **710**、assets **31**、lib **2**、META-INF 50、zip 总条目 1131
- [ ] `resources.arsc` stored、`AndroidManifest.xml` deflate、json deflate / png stored
- [ ] base strings **238** 条、locale 104 个（`values-en` 200 / `values-zh-rHK` 229 / `values-zh-rTW` 224 / `values-zh-rCN` 29）
- [ ] 组件数 16（6/5/2/3）+ `<permission>` 6 + `<meta-data>` 8 + `<intent-filter>` 5
- [ ] 两处错拼权限名（`M:64`, `M:70`）原样保留
- [ ] `guide/` 13 组 Lottie、`pull_up_setting/` 7 个 Lottie，`layout_switch_flashlight.xml` 中的 assets 路径不变
- [ ] 签名：v2 有效（可用绝对路径 APK 复签，但证书指纹不可能与原包相同 → 与 Lead 确认是否纳入"信息一致"范围）

---

## 附录 A：采集命令（可复跑）

```powershell
# badging（第 6 节数据源）
& 'C:\Users\10413\AppData\Local\Android\Sdk\build-tools\30.0.3\aapt2.exe' dump badging D:\DEBUG\apk\sysui\XTCSystemUI.apk
# 资源表（第 2.2 节）
& '...\aapt2.exe' dump resources D:\DEBUG\apk\sysui\XTCSystemUI.apk
# 签名（第 6.4 节）
& '...\build-tools\30.0.3\apksigner.bat' verify -v --print-certs D:\DEBUG\apk\sysui\XTCSystemUI.apk
# 资源/字符串/assets 统计（第 2-4 节全部数字）
py D:\DEBUG\apk\sysui\analysis\res\_scan.py     # 输出 _scan_report.txt
```

## 附录 B：本次产出文件（均在 `D:\DEBUG\apk\sysui\analysis\res\`）

| 文件 | 说明 |
|---|---|
| `INVENTORY.md` | 本文件（主交付物） |
| `_badging.txt` | `aapt2 dump badging` 原始输出（177 行） |
| `_dump_resources.txt` | `aapt2 dump resources` 原始输出（677 KB，资源表全量） |
| `_scan_report.txt` | 扫描脚本报告（类型统计/字符串分组/locale 表/abc 引用分析/Lottie 结构） |
| `_scan.py` | 扫描脚本（可复跑，只读输入） |

> 未修改 `nx\`、`work\`、`rebuild\` 下任何文件；仅写入 `analysis\res\`。
