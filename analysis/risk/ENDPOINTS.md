# XTCSystemUI 网络端点全量清单（ENDPOINTS）

> 代码根：`D:\DEBUG\apk\sysui\work\jadx17\sources\`（简写 `<SRC>`）；资源根：`D:\DEBUG\apk\sysui\nx\resources\`（简写 `<RES>`）。
> 提取方法：① 全量扫描 `https?://`、`"://"`、`.com`、`.cn`、`.net` 字符串常量；② 扫描 Retrofit 注解 `@GET/@POST/@Body`；③ 扫描 `okhttp3.HttpUrl`/`Uri.Builder`/`Uri.parse`；④ 人工确认域名来源（`Domain` 枚举 + 路由下发）。
> **重要结论：本包直连外网的端点极少**（2 个 Retrofit + 1 个明文 HTTP-DNS + 若干域名常量）。真正的“上报”走 `content://` 交给 launcher 进程落库上传，不在本包联网。

## 1. 汇总

| 类别 | 数量 | 说明 |
|---|---|---|
| 域名常量（`Domain` 枚举，含 6 区域变体） | **31 组 / 约 120 个域名+IP 字符串** | `com.xtc.domain.Domain`，见 §2 |
| 直连 HTTP(S) 路径 | **3** | `/route-service/route/getRouteInfo`、`/quickCharge/watchQuickCharge`、HTTP-DNS `/d`（另有 1 个 content:// 形式的 DNS 查询 E-04） |
| HTTP-DNS 服务器 | **1 个 IP（+可被 sdcard 配置覆盖）** | `http://119.29.29.98/d?dn=…&id=196&ttl=1` |
| WebSocket / IM 长连接端点 | **3 组** | `wss://ws-gw.okii.com`、`gw.im.okii.com:8000`、`106.75.31.151:1883/8000` |
| 跨进程 `content://` 端点（非网络，但属数据通道） | **24+** | 见 §6 |
| 证书锁定 / 双向认证 | **无** | 未使用 `CertificatePinner`/自定义 `X509TrustManager` |
| 传输加密 | 部分 | HTTPS 默认 + 自研 `Eebbk-Key/Eebbk-Sign` 头 + RSA/AES 报文加密；HTTP-DNS 为**明文 http** |

## 2. 域名常量表（`<SRC>\com\xtc\domain\Domain.java:5-35`）

字段顺序：`枚举名(配置键, CN, SA, US, DE, VN)`；由 `com.xtc.httplib.net.BaseUrlManager.getProtocolUrl()` 拼成 `https://<域名>/`（`<SRC>\com\xtc\httplib\net\BaseUrlManager.java:87-89`）。

| 行号 | 枚举 | 配置键 | CN 域名 | SA | US | DE | VN |
|---|---|---|---|---|---|---|---|
| :5 | `WATCH` | watchDomain | `watch.okii.com` | `watch-sa.imoo.com` | `watch-us.imoo.com` | `watch-oz.okii.com` | `watch-vn.imoo.com` |
| :6 | `CHAT` | chatDomain | `watch.okii.com` | `watch-sa.imoo.com` | `watch-us.imoo.com` | `watch-oz.okii.com` | `watch-vn.imoo.com` |
| :7 | `LOCATION` | locationDomain | `location.watch.okii.com` | `location-sa.imoo.com` | `location-us.imoo.com` | `location-oz.okii.com` | `location-vn.imoo.com` |
| :8 | `SENSOR_SERVICE` | sensorServiceDomain | `report-data-watch.okii.com` | `api-sa.imoo.com` | `api-us.imoo.com` | `api-oz.okii.com` | `api-vn.imoo.com` |
| :9 | `POINTS` | pointsDomain | `points.okii.com` | `points-sa.imoo.com` | `points-us.imoo.com` | `points-oz.okii.com` | `points-vn.imoo.com` |
| :10 | `TCX_POINTS` | pointsDomainTcx | `points.tiancaixing.com` | `points-sa.imoo.com` | `points-us.imoo.com` | `points-oz.okii.com` | `points-vn.imoo.com` |
| :11 | `ACTIVITY` | activityDomain | `act.okii.com` | `act.okii.com` | `act.okii.com` | `act.okii.com` | `act.okii.com` |
| :12 | `TCX_ACTIVITY` | activityDomainTcx | `act.tiancaixing.com` | `acttest.okii.com` | `acttest.okii.com` | `acttest.okii.com` | `acttest.okii.com` |
| :13 | `SSO` | ssoDomain | `account.okii.com` | `account-sa.imoo.com` | `account-us.imoo.com` | `account-oz.okii.com` | `account-vn.imoo.com` |
| :14 | `IM_JOIN` | imJoinDomain | `gw.im.okii.com:8000` | `gw-sa.im.imoo.com:8000` | `gw-us.im.imoo.com:8000` | `gw-oz.im.okii.com:8000` | `gw-vn.im.imoo.com:8000` |
| :15 | `SPORT` | sportDomain | `sport.watch.okii.com` | `sport-sa.imoo.com` | `sport-us.imoo.com` | `sport-oz.okii.com` | `sport-vn.imoo.com` |
| :16 | `WEATHER` | weatherDomain | `sport.watch.okii.com` | `sport-sa.imoo.com` | `sport-us.imoo.com` | `sport-oz.okii.com` | `sport-vn.imoo.com` |
| :17 | `H5` | h5Domain | `static.watch.okii.com` | `static-watch.imoo.com` | `static-watch.imoo.com` | `static-watch.imoo.com` | `static-watch.imoo.com` |
| :18 | `THIRD` | thirdDomain | `thirdparty.watch.okii.com` | 同 CN | 同 CN | 同 CN | 同 CN |
| :19 | `STORE` | storeDomain | `store.watch.okii.com` | `store-sa.imoo.com` | `store-us.imoo.com` | `store-oz.okii.com` | `store-vn.imoo.com` |
| :20 | `TCX_STORE` | storeDomainTcx | `store-watch.tiancaixing.com` | `store-sa.imoo.com` | `store-us.imoo.com` | `store-oz.okii.com` | `store-vn.imoo.com` |
| :21 | `IM_BACKUP` | imBackupDomainList | `106.75.31.151:1883,106.75.31.151:8000` | "" | "" | `106.75.86.52:1883,106.75.86.52:8000` | "" |
| :22 | `IM_JOIN_GREY` | imJoinGreyDomain | `gw.beta.im.okii.com:8000` | `gw-sa-beta.im.imoo.com:8000` | `gw-us-beta.im.imoo.com:8000` | `gw-oz.beta.im.okii.com:8000` | `gw-vn.im.imoo.com:8000` |
| :23 | `GAME` | gameDomain | `game.watch.okii.com` | `game-sa.imoo.com` | `game-us.imoo.com` | `game-oz.okii.com` | `game-vn.imoo.com` |
| :24 | `TCX_GAME` | gameDomainTcx | `game-watch.tiancaixing.com` | `game-sa.imoo.com` | `game-us.imoo.com` | `game-oz.okii.com` | `game-vn.imoo.com` |
| :25 | `THEME` | themeDomain | `theme.watch.okii.com` | `theme-sa.imoo.com` | `theme-us.imoo.com` | `theme-oz.okii.com` | `theme-vn.imoo.com` |
| :26 | `EMOJI` | emojiDomain | `smartwatch.qiniucdn.com` | 同 CN | 同 CN | 同 CN | 同 CN |
| :27 | `MOMENT` | momentDomain | `moment.watch.okii.com` | `moment-sa.imoo.com` | `moment-us.imoo.com` | `moment.watch.okii.com` | `moment-vn.imoo.com` |
| :28 | `GATEWAY` | gatewayDomain | `api.watch.okii.com` | `api-sa.imoo.com` | `api-us.imoo.com` | `api-oz.okii.com` | `api-vn.imoo.com` |
| :29 | `TCX_GATEWAY` | gatewayDomainTcx | `api-watch.tiancaixing.com` | `api-sa.imoo.com` | `api-us.imoo.com` | `api-oz.okii.com` | `api-vn.imoo.com` |
| :30 | `SHOP` | shopDomain | `oper.tiancaixing.com` | 同 CN | 同 CN | 同 CN | 同 CN |
| :31 | `ROUTE` | routeDomain | `route.okii.com` | `route-sa.imoo.com` | `route-us.imoo.com` | `route-oz.okii.com` | `route-vn.imoo.com` |
| :32 | `BIGDATA` | bigdataDomain | **`watchda.eebbk.net`** | `watchda.eebbk.net` | `watchda.eebbk.net` | `watchda-eu.imoo.com` | `watchda.eebbk.net` |
| :33 | `QINIU_PRE_QUERY` | qiniuPreQueryDomain | `uc.qbox.me` | `kodo-config.qiniuapi.com` | `kodo-config.qiniuapi.com` | "" | `kodo-config.qiniuapi.com` |
| :34 | `WEB_SOCKET` | webSocketDomain | `ws-gw.okii.com` | `ws-gw.okii.com` | "" | "" | "" |
| :35 | `QIUNIU_UPLOAD` | qiniuUploadDomain | "" | "" | `upload-as0.qiniup.com` | "" | `upload-as0.qiniup.com` |

协议：`ConfigOptions.ProtocolType`（`<SRC>\com\xtc\httplib\ConfigOptions.java:51-55`）`HTTP="http://"`、`HTTPS="https://"`、`WebSocket="wss://"`；`BaseUrlManager.PROTOCOL_TYPE` 默认 **HTTPS**（`<SRC>\com\xtc\httplib\net\BaseUrlManager.java:12`），可由 `HttpManager.setUseHttps()`（`<SRC>\com\xtc\httplib\HttpManager.java:146-153`）改为 HTTP。

域名解析来源：`com.xtc.domain.b.a(ctx).a(domain.getName())`（`<SRC>\com\xtc\domain\b.java:63-73`）——值来自 launcher 共享的 **domain 配置**（本地存储 / `content://`），**可被服务端路由下发覆盖**（见 §3）。

## 3. 直连 HTTP(S) 端点

| 编号 | 端点/路径 | 文件:行号 | 所属类/方法 | 用途分类 | 协议 | 加密/证书 | 备注 |
|---|---|---|---|---|---|---|---|
| E-01 | `POST /route-service/route/getRouteInfo` | `<SRC>\com\xtc\domain\config\http\HttpConfigApi.java:12` | `HttpConfigApi.getRouteInfo(@Body d)` | **云控配置下发**（机房/域名路由表） | https（`BaseUrlManager.getRouteUrl()` 拼 `https://route.okii.com/`） | 无 pin；`@NoNeedToken` | 请求体 `com.xtc.domain.config.http.d`（envId/机型/地区，`<SRC>\com\xtc\domain\config\http\d.java`）；响应 `NetBaseResult<e>` 含域名映射 |
| E-02 | `POST /quickCharge/watchQuickCharge` | `<SRC>\com\android\common\library\IWatchQuickChargeHttp.java:9` | `IWatchQuickChargeHttp`（接口） | 其他（快充状态同步） | https | 无 pin | **本包内无调用点**（随 common library 打包） |
| E-03 | `http://119.29.29.98/d?dn=<host>&id=196&ttl=1` | `<SRC>\com\xtc\dns\b\a\c.java:24-25`（`Uri.Builder().scheme("http").authority(a()).path("d")...`）；IP 默认值 `<SRC>\com\xtc\dns\b\a.java:19`（`f2504a="119.29.29.98"`），id 默认 `<SRC>\com\xtc\dns\b\a.java:22`（`f2506b="196"`） | `com.xtc.dns.b.a.c.mo1523a(String)` | **DNS 查询（旁路解析）** | **明文 http**（DNS 劫持面） | 无 | 腾讯 HTTP-DNS 协议；返回 `"ip1;ip2,ttl"` 解析于 `c.java:36-57`；可被 §4 的 sdcard 文件改 IP |
| E-04 | `http://<provider-authority>/DomainModel/<host>`（**非网络**，content://） | `<SRC>\com\xtc\dns\client\b\b.java:34-48`、`:58-90` | `com.xtc.dns.client.b.b`（DnsResolver） | DNS 缓存跨进程查询 | content | — | authority = `"<pkg>.com.xtc.httpdns.provider"`（`<SRC>\com\xtc\dns\provider\a.java:6`） |
| E-05 | 配置中的自定义域名（外部注入） | `<SRC>\com\xtc\httplib\net\BaseUrlManager.java:87-93` | `getProtocolUrl/getWSProtocolUrl` | 云控（所有业务请求的基础 URL） | https / wss | 无 pin | 域名来自 `com.xtc.domain.b`，**非硬编码**，可在复刻版中固定为常量或整体去掉 |

## 4. 外部文件 / 系统属性中的端点（可被劫持的配置注入点）

| 编号 | 位置 | 文件:行号 | 键/格式 | 用途 | 风险 |
|---|---|---|---|---|---|
| E-06 | `/mnt/sdcard/xtc/ibwatch/common/httpdnsinfo.properties` | `<SRC>\com\xtc\dns\d\b.java:13`（写入 `:41-68`，读取 `:15-39`） | `enableHttpDns=0/1`、`httpDnsIp=<ip>`、`cacheTime=<sec>`、`dnsTtl=<sec>` | HTTP-DNS 服务器地址与开关 | **外部存储明文配置，任意有 sdcard 写权限的应用可篡改 DNS 服务器 IP** → 建议复刻版直接删除该文件读取逻辑 |
| E-07 | 大数据配置文件 `/mnt/sdcard/.../BigData/config.txt` + `BaseAttrInfo.txt` | `<SRC>\com\xtc\bigdata\collector\config\DeviceInfo.java:5-14`（`BIG_DATA_DIR="/BigData"`、`WATCH_CONFIG_PATH="/config.txt"`） | 采样率/上报开关等 | 采集库配置 | 外部可写 → 随采集库删除 |
| E-08 | `SystemProperties` 网络/CTA 开关 | `<SRC>\com\xtc\httplib\okhttp\MonitorInterceptor.java:40`（`persist.sys.cta.permission`）、`:307`（`persist.sys.bindstatus`） | boolean | 请求准入 | 系统属性，root/系统应用可改 |
| E-09 | `SystemProperties` 省电开关 | `<SRC>\com\android\systemui\statusbar\policy\m.java:11`（`persist.sys.wleswitch`） | boolean | 省电模式状态 | 系统内部，保留 |
| E-10 | `SystemProperties` odex 调试开关 | `<SRC>\com\xtc\bigdata\collector\exception\CrashHandler.java:503-504`（`persist.sys.opt.xtc_debug`、`persist.sys.debugopt.pkgname`） | int/string | 触发 odex 重优化 | 随 R-15 删除 |

## 5. WebSocket / IM 长连接

| 编号 | 端点 | 文件:行号 | 用途 | 备注 |
|---|---|---|---|---|
| E-11 | `wss://ws-gw.okii.com/` | `<SRC>\com\xtc\domain\Domain.java:34` + `BaseUrlManager.java:91-93` | IM/推送长连接 | 由 `com.xtc.im.*` 使用；SystemUI 侧仅打包，无直接调用 |
| E-12 | `gw.im.okii.com:8000`（及各区域），`gw.beta.im.okii.com:8000`（灰度） | `<SRC>\com\xtc\domain\Domain.java:14`、`:22` | IM 接入网关 | 同上 |
| E-13 | `106.75.31.151:1883/8000`、`106.75.86.52:1883/8000` | `<SRC>\com\xtc\domain\Domain.java:21` | IM 备份（MQTT 1883 + 8000） | 裸 IP |

## 6. 跨进程 `content://` 端点（非外网，但属“上报/配置”数据通道，复刻版必须知道）

| 编号 | URI / authority | 文件:行号 | 方向 | 用途 |
|---|---|---|---|---|
| P-01 | `content://<HOST_APP_ID>.provider/...`，HOST_APP_ID=`com.xtc.i3launcher`（SystemUI 注入）→ **`com.xtc.i3launcher.provider`** | `<SRC>\com\xtc\bigdata\common\utils\UriUtils.java:11-17`；注入点 `<SRC>\com\android\systemui\c.java:81`（`setHostAppId("com.xtc.i3launcher")`） | SystemUI → launcher | **行为/崩溃/设备数据落库（真正的“上报”出口）** |
| P-02 | `content://com.xtc.http.bigdata.provider.HttpBigdataProvider/query`、`/insert` | `<SRC>\com\xtc\httplib\bigdata\BigdataClientManager.java:13`（jadx18 交叉核对：`BigdataClientManager.java` 中 `Uri.parse("content://com.xtc.http.bigdata.provider.HttpBigdataProvider/insert")`） | SystemUI → launcher | HTTP 请求大数据（每次网络请求的 url/耗时/流量）上报 + 采样开关查询 |
| P-03 | `content://com.xtc.provider/BaseDataProvider/{watchId/1, icon/2, name/3, tempmap/4, number/5, iconpath/6, geniusaccount/7, openID/8, simType/9, countryCode/10, realName/11}` | `<SRC>\com\xtc\system\account\h.java:17-20`、`:59-66` | 读 | 设备账号/机型信息（供上报头使用） |
| P-04 | `content://com.xtc.provider/low_authorities` | `<SRC>\com\xtc\system\account\h.java:20`、`:85-87` | 读 | 低权限列表 |
| P-05 | `content://com.xtc.moduledata/query`（`module=<int>`） | `<SRC>\com\xtc\system\account\h.java:135`、`:178`、`:194` | 读 | **模块开关/提示/extra（云控下发落库）** |
| P-06 | `content://com.xtc.fundata/FunSwitch`、`/FunItem`、`/FunExtra` | `<SRC>\com\xtc\system\account\d.java:27-28`；`<SRC>\com\xtc\system\account\h.java:222`、`:256`、`:276` | 读 + ContentObserver | **功能开关（funSwitch）**：录屏/省电等是否可用 |
| P-07 | `content://com.xtc.gdprdata/query`（`switchId=<int>`） | `<SRC>\com\xtc\system\account\h.java:297` | 读 | GDPR/隐私开关 |
| P-08 | `content://com.xtc.launcher.self.start`（`call("METHOD_GET_PACKAGE", pkg)`） | `<SRC>\com\xtc\ui\widget\selfstart\SelfStartUtils.java:87`、`:95`；`<SRC>\com\xtc\ui\widget\util\CtaPermissionUtil.java:27`、`:221`、`:264` | 读/调用 | 自启动白名单（权限检测） |
| P-09 | `content://com.xtc.setting/setting_bluetooth`、`content://com.xtc.setting/HAS_BLE_CONNECTED` | `<SRC>\com\xtc\statusbar\b\a.java:22`、`:27` | 读 | 蓝牙已连接状态 |
| P-10 | `content://com.xtc.motion/{friend_list, goal_steps, refresh_data, read_data, motion_status}` | `<SRC>\com\xtc\system\account\MotionTotalData.java:70`、`:84`、`:97`、`:99`；`<SRC>\com\xtc\system\account\f.java:31` | 读 | 运动数据 |
| P-11 | `content://com.xtc.initservice/item`、`content://com.xtc.initservice.watchaccount/item` | `<SRC>\com\xtc\system\account\bean\AppInfoBase.java:10`；`<SRC>\com\xtc\system\account\h.java:70` | 读 + 广播 | 初始化服务/RSA key/账号 |
| P-12 | `content://com.xtc.datacenter.SensitiveProvider/data/sensitiveData` | `<SRC>\com\xtc\log\util\ProviderUtil.java:8-17` | 读 | 敏感数据字段白名单（日志脱敏用） |
| P-13 | `content://com.xtc.contact/item`、`content://com.xtc.contact.provider.CallLogProvider/{allCallLog,clearUnreadNumber,clearCallLog,callLogLimit,callLogReadState}` | `<SRC>\com\xtc\contactapi\contact\a\a.java:34-37`；`<SRC>\com\xtc\contactapi\contact\e\a.java:45-49` | 读/写 | 联系人/通话记录 |
| P-14 | `content://com.xtc.album.provider`、`content://com.xtc.camera.app.provider.album` | `<SRC>\com\xtc\data\d\a.java:6-7`；`<SRC>\com\xtc\data\f\a.java:23-24` | 写 | 录屏文件写入相册 |
| P-15 | `content://com.xtc.i3launcher.assistant`、`content://com.xtc.openapi.assistant`、`content://com.xtc.vassistant.provider`、`content://com.baidu.voicesearch.assistant` | `<SRC>\com\xtc\assistantapi\c\h.java:21-23`；`<SRC>\com\xtc\assistantapi\b\d.java:22` | 调用 | 语音助手（SystemControlService directive 底座） |
| P-16 | `content://<authority>`（MMKVContentProvider 动态 authority） | `<SRC>\com\tencent\mmkv\MMKVContentProvider.java:40`、`:82` | 读/写 | MMKV 跨进程（`com.tencent.mmkv.MMKVContentProvider`） |
| P-17 | **本包对外暴露的 Provider**：`content://com.android.systemui.fileprovider`（FileProvider）、`content://com.xtc.systemui.statusbar.proxy`（StatusBarProxyProvider，仅允许 `com.xtc.i3launcher`）、`content://com.android.systemui.com.xtc.httpdns.provider`（HttpDnsContentProvider，`readPermission=com.android.systemui.permission.READ_DNS`） | `<RES>\AndroidManifest.xml:124-137`、`:164-172`；代码 `<SRC>\com\android\systemui\statusbar\provider\StatusBarProxyProvider.java:18`、`<SRC>\com\xtc\dns\provider\a.java:6` | 被外部调用 | 复刻版必须逐项决定：保留（FileProvider/代理 Provider 业务必需）或删除（HTTPDNS Provider） |

## 7. 加密 / 证书 / 混淆结论

| 检查 | 结论 | 证据 |
|---|---|---|
| 证书锁定 | **无** | 业务代码未调用 `OkHttpClient.Builder.certificatePinner()`；`okhttp3\CertificatePinner.java` 仅为库 |
| 自定义 TrustManager / 忽略证书 | **无**（仅 okhttp 标准 `Util.platformTrustManager()`） | `<SRC>\okhttp3\OkHttpClient.java:188-190`、`okhttp3\internal\Util.java:593-598` |
| 报文加密 | 有：`Eebbk-Key/Eebbk-Key-Id/Eebbk-Sign/encrypted/dataCenterCode` 请求头 | `<SRC>\com\xtc\httplib\ConfigOptions.java:9-26`；`<SRC>\com\xtc\httplib\okhttp\HttpHelper.java:1-257` |
| RSA/AES 密钥 | RSA 公钥由 launcher 通过 `com.xtc.initservice.action.NET_PARAM_UPDATE` 广播下发 | `<SRC>\com\xtc\httplib\HttpManager.java:242-274`；`<SRC>\com\xtc\httplib\bigdata\RsaKeyBigData.java:22-27` |
| 敏感数据脱敏 | `com.xtc.log.util.ProviderUtil` 从 `com.xtc.datacenter.SensitiveProvider` 拉“敏感字段名”列表 | `<SRC>\com\xtc\log\util\ProviderUtil.java:8-17` |
| 明文 HTTP 端点 | **1 处**：HTTP-DNS `http://119.29.29.98/d` | `<SRC>\com\xtc\dns\b\a\c.java:24-25` |
| 明文配置注入点 | **2 处**：`/mnt/sdcard/xtc/ibwatch/common/httpdnsinfo.properties`、`/BigData/config.txt` | `<SRC>\com\xtc\dns\d\b.java:13`、`<SRC>\com\xtc\bigdata\collector\config\DeviceInfo.java:5-14` |

## 8. 复刻版建议（端点处置结论）

| 处置 | 端点 | 说明 |
|---|---|---|
| **直接删除** | E-01（路由下发）、E-03（HTTP-DNS）、E-06/E-07（sdcard 配置） | 均属云控/旁路，删除后业务不受影响（DNS 回落系统解析） |
| **删除（无调用点）** | E-02（快充接口） | 本包无调用，删除不影响编译（若保留 library，保留接口定义不加载即可） |
| **保留但不再使用** | E-11~E-13（IM/WS 端点常量） | 属 `Domain` 枚举，业务代码若仍引用 `Domain` 常量，保留枚举即可；复刻版不初始化 IM 通道 |
| **必须保留** | P-01~P-17 中的本地/系统 provider（P-03/P-06/P-07/P-09/P-11 等） | 状态栏/快捷开关/省电/录屏依赖本机 provider，**不联网、不上报**，删除会破坏功能（详见 IMPACT） |
| **替换实现** | P-01/P-02（上报出口） | 若复刻版要保留“本地统计”，用本地 SQLite/内存替代，**不再写 launcher provider** |
