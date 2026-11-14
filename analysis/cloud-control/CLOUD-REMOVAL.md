# 云控移除专项报告（CLOUD-CONTROL REMOVAL）

> 目标包：`com.android.systemui` 1.8.00（XTC 小天才儿童手表 SystemUI，Android 8.1 / API 27，platform 签名，sharedUserId=android.uid.system）
> 本节对应「先把云控去除」指令；上报/权限检测的完整清单见 `analysis\risk\RISK-MAP.md`（86 条），本报告只处理**云控（远程配置/远程开关/远程下发）**面。
> 交付日期：2026-11-01；执行：Lead（+ risk-scan 逆向证据、build-eng 构建基座）。

---

## 0. 结论（先看这里）

| 项 | 结论 |
|---|---|
| 云控代码是否存在于复刻包 | **不存在**。复刻工程是 framework-only 手写代码，未实现 `com.xtc.domain` / `com.xtc.httplib` / `com.xtc.dns`（除空实现） / `com.xtc.bigdata` / `com.xtc.funlist` 任何云控类 |
| 云控声明是否还在包里 | **默认变体**：保留 4 个云控/上报组件声明（空实现，能力为 0，信息与原包 100% 一致）；**strict 变体**：用 AXML 二进制手术**物理删除**这 4 个声明 + 2 条 bigdata 权限（见 §3） |
| 静态扫描 | strict 变体：类名前缀黑名单 **0 命中**、dex 字符串黑名单 **0 命中**、`Ljava/net/*` **0 个** → PASS（`NO-CLOUD-REPORT.md`） |
| 资源/信息一致性 | `resources.arsc` 与原包 **sha256 完全一致**；strict 变体的 manifest 除被删的 6 个元素（30 行 xmltree）外**无任何差异** |
| 是否还能被远程控制 | 不能：无域名/无端点/无 HTTP DNS/无远程开关读取/无 route 下发解析；`moduleSwitch`/`funSwitch` 元数据保留但本包代码从不解析（由 launcher 读取，属静态功能声明） |

两个可用产物：
- **默认（信息 100% 一致）**：`rebuild\build\XTCSystemUI-signed.apk` 2,364,413 B，sha256 `60c9848e0e80b037…`
- **strict（云控声明物理移除）**：`analysis\cloud-control\out\XTCSystemUI-strict-cloud.apk` 2,367,101 B，sha256 `7a0fb6142b0f7f18…`

---

## 1. 原包云控面全量清单（证据 → 处置）

| 编号 | 云控元素 | 证据（文件:行号） | 原行为 | 复刻包处置 |
|---|---|---|---|---|
| C-01 | 云控/上报总入口 `com.android.systemui.c`（BigDataInitHelper） | `<SRC>\com\android\systemui\c.java:67-87`；`SystemUIApplication.java:100-104` | 开机初始化 bigdata 采集器（hostAppId=`com.xtc.i3launcher`） | **不实现该类**；Application 无此调用 |
| C-02 | 延迟 120s 静默启动 | `<SRC>\com\android\systemui\c.java:49-65` | `Timer` + `elapsedRealtime` 判断后启动上报 | **不实现** |
| C-03 | 域名硬编码表 `com.xtc.domain.Domain` | `<SRC>\com\xtc\domain\Domain.java:5-35` | 31 组域名（CN/SA/US/DE/VN 变体，约 120 个） | **不打包**（R-30） |
| C-04 | 路由/机房配置下发 `com.xtc.domain.b` + `HttpConfigApi` | `<SRC>\com\xtc\domain\b.java:90-111`；`config/http/HttpConfigApi.java:12` | `@POST("/route-service/route/getRouteInfo")` 拉取并覆盖本地域名 | **不打包**（R-31/R-32） |
| C-05 | 机房变更远程重启 | `<SRC>\com\xtc\domain\config\http\c.java:97-106` | 机房变化时 `powerManager.reboot("reboot in datacenter change by launcher")` | **不打包**（R-33，最危险项） |
| C-06 | 网络管理器/OkHttp 拦截器栈 | `<SRC>\com\xtc\httplib\HttpManager.java`、`okhttp\DefaultOkHttpClient.java:146-169` | 统一 HTTP 出口 + 监控/限流/伪造响应/IM 透传 | **不打包**（R-34~R-40） |
| C-07 | 配置热更新 `ConfUpdatesInfoManager` | `<SRC>\com\xtc\httplib\confupdate\ConfUpdatesInfoManager.java:1-148` | 服务端配置落盘并按周期刷新 | **不打包**（R-42） |
| C-08 | 网络开关下发 `SwitchNetworkManager` | `<SRC>\com\xtc\httplib\netstate\SwitchNetworkManager.java:29,49-51,101` | `setModuleSwitch(boolean, SwitchConfigBean)` 服务端下发网络策略 | **界面改用 `ConnectivityManager` 公开 API**，不打包该 SDK（R-41） |
| C-09 | HTTPDNS Provider `com.xtc.dns.provider.HttpDnsContentProvider` | `<RES>\AndroidManifest.xml:164-172`；`<SRC>\...\HttpDnsContentProvider.java:12,110-126` | exported provider（READ_DNS，protectionLevel=normal）+ 缓存/查询，旁路系统 DNS | 默认：空实现（call→null，零网络）；strict：**声明物理删除**（R-43） |
| C-10 | DNS 策略注入 `com.xtc.dns.api.a` | `<SRC>\com\xtc\dns\api\a.java:21,43-52,77-118` | 实现 okhttp Dns/EventListener，失败回落系统 DNS | **不打包**（R-44） |
| C-11 | 腾讯 HTTP-DNS `dns.b.a.c` | `<SRC>\com\xtc\dns\b\a\c.java:24-25,63-69` | 明文 `http://119.29.29.98/d?...` 查询 | **不打包**（R-45） |
| C-12 | DNS 配置落盘（可被 sdcard 篡改） | `<SRC>\com\xtc\dns\b\a.java:57-115`；`dns\d\b.java:13` | 读写 `/mnt/sdcard/xtc/ibwatch/common/httpdnsinfo.properties` | **不打包**（R-46） |
| C-13 | DNS 客户端探测/查询 | `<SRC>\com\xtc\dns\client\b.java:31-49,52-62`；`client\b\b.java:34-48,58-90` | `resolveContentProvider(...,httpdns.provider)` 命中即走自研 DNS | **不打包**（R-48/R-49） |
| C-14 | 远程配置读取通道（provider/Settings/Prefs） | `<SRC>` 多处：`ShareHelper`、`dataservice`、`baseservice` | 从 launcher/服务端读取模块开关与配置 | **不实现**（复刻包只读本地 Resources/SharedPreferences） |
| C-15 | manifest 云控/监控组件声明 | `<RES>\AndroidManifest.xml:154-183` | CrashIntentService / MonitorStartReceiver / CodeCoverageReceiver | 默认：空实现；strict：**声明物理删除** |
| C-16 | 云控/上报权限申请 | `<RES>\AndroidManifest.xml:65-66` | `com.xtc.http.bigdata.provider.permission.READ_CONTENT/WRITE_CONTENT` | 默认：保留（信息一致）；strict：**删除** |
| C-17 | `moduleSwitch` / `funSwitch` 元数据 | `<RES>\AndroidManifest.xml:138-143` | 由 **launcher/设置** 读取决定录屏等模块入口；本包 Java 从不解析（grep 0 命中） | **保留不动**：属静态功能声明，删除会静默丢功能（risk-scan IMPACT §9） |
| C-18 | libmmkv / libxtcSecurity | `<RES>\lib\armeabi\*.so` | MMKV 本地 KV；xtcSecurity 签名/安全 SDK | MMKV：不打包，改 SharedPreferences；xtcSecurity：**不打包**（安全/检测链） |

---

## 2. 云控移除的实现方式

1. **代码层（防止重新引入）**
   - 复刻源码 `src\main\java` 为 framework-only：不含 `okhttp/retrofit/rx/kotlin/protobuf`，不含 XTC 网络/域名/DNS/bigdata 类。
   - `HttpDnsContentProvider` 在默认变体中为离线空实现：`call()/query()` 返回 null，调用方按原设计回退系统 DNS，零网络。
   - `SystemUIApplication` 不初始化任何采集器；`Dependency` 不注册上报相关 controller。
   - 静态门禁 `analysis\cloud-control\tools\check_nocloud.py`：类包前缀黑名单 18 条 + dex 字符串黑名单 34 条 + `Ljava/net/*` 扫描，任何云控/网络残留直接 FAIL。

2. **声明层（strict 变体，AXML 二进制手术）**
   - 工具 `tools\axml_strip.py`：解析二进制 `AndroidManifest.xml`（StringPool / ResourceMap / START_ELEMENT / END_ELEMENT），按 `android:name` 删除整棵元素子树，**StringPool 与 ResourceMap 原样保留**，仅重算外层 chunk size。
   - 目标表 `tools\cloud-targets.json`（6 项）：

| 类型 | android:name | 理由 |
|---|---|---|
| service | `com.xtc.bigdata.collector.exception.CrashIntentService` | 崩溃上报服务（exported=false，无 filter，安全删除） |
| receiver | `com.xtc.bigdata.monitor.receiver.MonitorStartReceiver` | watchdog/监控广播入口（priority 100000） |
| receiver | `com.xtc.xtcoco.CodeCoverageReceiver` | **exported=true** 覆盖率上报入口（攻击面） |
| provider | `com.xtc.dns.provider.HttpDnsContentProvider` | 云 DNS/旁路解析 |
| uses-permission | `com.xtc.http.bigdata.provider.permission.READ_CONTENT` / `WRITE_CONTENT` | 上报 provider 权限 |

   - 同时把上述占位类从 strict 变体的 javac 输入中剔除（`-ExcludedSources`），strict dex 内**不存在任何 com.xtc.bigdata / com.xtc.dns / com.xtc.xtcoco 类**。

3. **保留不删（明确决策，防误删功能）**
   `StatusBarProxyProvider`、`IKeyguardInterface`、`SystemControlService`、`XtcNotificationListenerService`、`PermissionListActivity`、`PrivacyUriActivity`、`SelfStartActivity`、`moduleSwitch/funSwitch/supportScreenRecordAudio/XtcStayFront` 元数据 —— 均为**本地业务**或外部应用读取的功能声明，非云控通道。

---

## 3. 验证证据（可复跑）

strict 产物：`analysis\cloud-control\out\XTCSystemUI-strict-cloud.apk`

| 校验项 | 结果 | 证据文件 |
|---|---|---|
| 资源表与原包一致 | `resources.arsc` sha256 `fc35556c…6e6f` **逐字节相同** | 本次校验输出 |
| 组件删除范围可控 | `aapt2 dump xmltree` 对比：**移除 30 行 / 新增 0 行**，且全部属于上表 6 个元素（含其 intent-filter/action/meta-data 子节点） | `out\xmltree-vendor.txt` / `out\xmltree-strict.txt` |
| badging 差异 | 仅 2 条 uses-permission + `other-receivers`（receiver 归零导致） | `out\badging-*.txt` |
| 云控/网络残留静态扫描 | **PASS**：类黑名单 0、字符串黑名单 0、`Ljava/net/*` 0、HTTP/SSL 类 0 | `NO-CLOUD-REPORT.md` |
| APK 条目 | 原包 1131 → strict 1132（`+classes.dex`，manifest 原位替换，META-INF 重签名） | 校验输出 |
| 签名/对齐 | apksigner verify 通过（v2/v3，v1 兜底）；`zipalign -c -v 4` 通过 | 校验输出 |
| 体积 | **2,367,101 B = 2.26 MB（≤3MB）**；classes.dex 190,636 B | — |
| 默认变体（对照） | 2,364,413 B；`verify.ps1` 7/7 PASS；16/16 manifest 组件类齐全；资源/清单 sha256 与原包一致 | `rebuild\build\verify-report.txt` |

复现命令：
```powershell
# strict 云控移除变体（约 20s，无网络/无 gradle）
powershell -NoProfile -ExecutionPolicy Bypass -File D:\DEBUG\apk\sysui\analysis\cloud-control\tools\build_strict_cloud.ps1 -Clean

# 默认（信息 100% 一致）变体
powershell -NoProfile -ExecutionPolicy Bypass -File D:\DEBUG\apk\sysui\rebuild\tools\build.ps1 -Clean
```

---

## 4. 残余风险与后续

| 项 | 说明 | 处置 |
|---|---|---|
| 默认变体仍声明 4 个云控组件 | 类为空实现，能力为 0；为保证「APK 信息与原包一致」 | 若要求物理删除，使用 strict 变体 |
| 上报出口在 launcher | 原包上报是"写 `content://com.xtc.i3launcher.provider` 由 launcher 落库上传"，launcher 不在本工程范围 | 本包内已断链（无该类、无该调用） |
| `moduleSwitch` 等元数据 | 保留；如误删会静默丢失录屏入口/录音能力 | 已列入「禁止删除」清单 |
| 平台签名 | sharedUserId=android.uid.system 需平台签名 + system 分区部署 | 设备端 corepatch/签名豁免（用户环境） |
| 后续波次回归 | W2+ 每次构建必须跑 `check_nocloud.py`，防止 UI 复刻时把网络/云控代码带回来 | 已纳入验收门禁 |
