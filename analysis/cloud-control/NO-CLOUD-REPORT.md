# 云控残留静态扫描报告

- APK：`D:\DEBUG\apk\sysui\analysis\cloud-control\out\XTCSystemUI-strict-cloud.apk`
- classes.dex：388532 字节，201 个类
- 规则：类包名前缀黑名单 18 条 + dex 字符串黑名单 34 条

## 1. 云控/上报/网络依赖类扫描

**PASS** — 0 命中（域名路由/HTTPlib/HTTPDNS/bigdata/okhttp/retrofit/rx/kotlin 均不存在）


## 2. 云控/端点/网络 API 字符串扫描

**PASS** — 0 命中（无 http(s)/ws 端点、无域名、无上报 provider、无网络 API）

## 3. 网络/加密类结构扫描

- `Ljava/net/*` 类：0
- HTTP/OKHttp/SSL 相关类：0

## 结论

**PASS — 云控/上报/网络能力静态残留为 0**
