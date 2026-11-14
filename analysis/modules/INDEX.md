# 模块分析总索引（ANALYSIS INDEX）— 剩余文件分析完成

> 状态：**4/4 模块 SPEC 已交付**（2026-11-01）。代码源统一为 `work\jadx18b\sources`（1.8.00 unquicken 后精确反编译）。
> 本索引汇总：分析产出、规模统计、改变计划的重大结论、待用户拍板事项、下一步实施顺序。

## 0. 产出清单（4 份 SPEC + 本索引）

| 模块 | SPEC | 类数（必需/可选/dead/其它） | UI 元素 | 文案 | 预计 LOC |
|---|---|---|---|---|---|
| 状态栏主链路 + 通知 + 图标/控制器 | [statusbar-notification/SPEC.md](statusbar-notification/SPEC.md)（75KB/526 行 + `_strings_zh.txt` 201 条 + `_layout_ids.txt`） | 95（50/33/12） | 61 个 view id | 30 key + 6 运行时 | 3,600–4,600 |
| QS 快捷开关 + 音量 + 省电/长续航 | [qs-volume/SPEC.md](qs-volume/SPEC.md)（73KB/758 行） | 43（27/3/11/移交2） | 51 | 67 key | ≈3,300–3,600 |
| 录屏 + 手电/SOS + 助理 + 蓝牙 | [media-tools/SPEC.md](media-tools/SPEC.md)（66KB/653 行） | 58（38/16/3） | 33 | 22 key（+4 归状态栏） | ≈3,660 |
| 锁屏/Keyguard + 隐私/自启动/权限说明 + 富文本 + Provider | [misc-local/SPEC.md](misc-local/SPEC.md)（74KB/621 行） | 49 条（17/24/dead4/移除4） | 16 布局/31 id | 65 key（含 20 权限条目、2 条富文本） | 见 SPEC §D |
| 类清单/可达性（机器统计） | [CLASS-INVENTORY.md](CLASS-INVENTORY.md) + `_inventory.csv`(3908) / `_reachable.csv` / `_dead_sdk.csv` | 应用层 245、SDK 依赖 281（132 云控上报不实现）、死代码 798 | — | — | — |

合计需手写 ≈ **11,000–13,000 行 Java**（不含资源；资源 100% 复用原包）。

## 1. 改变计划的 7 个重大结论（先看这里）

1. **原包没有通知列表/通知中心 UI**：`cancelNotification/getActiveNotifications/getRanking/isClearable/HeadsUp` 全仓 0 命中；通知唯一消费方是 `MusicController`（音乐卡片）。→ 原 W3「通知列表」不是复刻而是**新增功能**，需产品决策是否做。
2. **`status_bar.xml` 是空布局**：折叠状态栏原本**没有任何可见内容**；可见的信息行是下拉面板 header 的 `XtcStatusBarView`。→ W2 已有自绘时钟/电量属新增，需决定保留与否（保留则与面板 header 重复显示）。
3. **`XTCPanelView extends PanelView`（1,110 行手势/动画基类）**：现 W2 的 FrameLayout+alpha 只覆盖约 5%；拖拽跟随/fling/falsing/长按 peek/表冠/缩放同步都要补。状态栏模块 LOC 的大头在这里。
4. **录屏不需要自写 mp4 muxer**：原包 `MediaRecorder(MPEG_4)` 直出 mp4；`mp4parser` 在包内但 reachable=0（全死代码）。→ 计划中的"自写 muxer"取消，省 ~800 行。
5. **API 27 上"手电筒"是屏幕手电**：`FlashLightActivity` = 全屏双页 ViewPager（照明/闪烁）+ 亮度 100→255 动画 2s + 180s 自动退出 + onPause 即退；`CameraManager.setTorchMode` 与 SOS 节律（18 个时间点、周期 8180ms=3短3长3短、WakeLock 1805s）只在 SDK≥30 的 `statusbar.policy.q` 分支，本机不可达。→ 手电实现比预想简单，但需处理亮度恢复与常亮。
   - 另：`layout_record.xml`/`dialog_record_sending.xml`/`layout_tile_tip_view.xml` 与本模块无关（全包只有 R 常量、无 inflate 调用点，`layout_record` 属语音留言 UI）；`assets/guide/switch_camera*` 只被 SDK30 Lottie 引导使用 → 均可不移植。
6. **BLE 客户端协议栈（34 类 proto/request/response）是死代码**：无 BLE 服务端调用者；但状态栏蓝牙图标消费 `XtcBleConnectStateListener/BleConnectResponse`，复刻用公开 `BluetoothAdapter` API 近似（`getProfileConnectionState`/`ACTION_ACL_*`/`getBondedDevices`）。另注意 `com.xtc.baseservice.c`=CameraInUseMonitor（相机占用）≠ `c.a`=BleClientManager。
7. **三处"看起来该有其实没有"**：`qs_divider` 代码从不调用 → 分隔线真机不显示（勿补）；音量提示条"去看看>"点击实际**不跳转**（只隐藏+落盘）；隐私/自启动/权限说明整块是**库残留死链**（无活调用、未 exported），但文案表仍须覆盖。

## 2. 必须由你拍板的 10 件事（决定实施口径）

| # | 事项 | 选项 | 建议 |
|---|---|---|---|
| 1 | **UI 话术规则**（原句→新句，或批量替换规则/品牌词/语气） | 你提供 | 待你给；已备好全量文案清单（4 份 SPEC 的 C 节 + `_strings_zh.txt`） |
| 2 | Lottie 依赖（7 个开关图标动画） | A 引入 lottie 3.4.0（+约 300KB）；B 用静态图/自绘 | 若体积优先选 B；要还原动画选 A |
| 3 | 省电开关"写"操作 | A 保留 `persist.sys.wleswitch`（厂商语义）；B 本地开关+广播 | A（与原机行为一致，非云控） |
| 4 | 音量提示"去看看>" | A 原样不跳转；B 改成真跳转 | 改话术阶段一起定 |
| 5 | 隐私协议页形态 | A 内置富文本页（离线）；B H5 加载 | A（去网络/去指纹） |
| 6 | 同意隐私后写 launcher 自启白名单（`content://com.xtc.launcher.self.start`） | A 保留；B 去掉副作用 | A（本地 IPC，非云控） |
| 7 | Keyguard 锁屏 | A 桥接外部 launcher AIDL（与原机一致，但依赖 launcher）；B 自建最小锁屏（独立可跑） | 先 A（保一致），B 作为可选 |
| 8 | 通知列表/通知中心 | A 不做（原包没有）；B 新增最小通知列表 | A（先复刻，不新增功能） |
| 9 | 折叠状态栏时钟/电量（原包为空） | A 保留（已实现）；B 去掉，只在面板 header 显示 | 需产品口径 |
| 10 | IStatusBar 系统服务注册 | A 自写 AOSP 8.1 等价 AIDL/Stub（与原机一致）；B 不注册，只本地手势+Provider | A（否则系统侧状态栏事件收不到） |

## 3. 各模块实施顺序（SPEC 已给，汇总）

- **状态栏/通知**：① NLS+NotificationDataCenter+NotificationUtils（isMusic/category）→ ② MusicController 裁剪 + MusicNotificationConstants → ③ MusicShortCutView 音乐卡片 → ④ PopWindowManager/HintWindow/DisabledForbid/AntiAddictionExit 提示浮层 → ⑤ PanelView 手势基类补全（拖拽/fling/peek）+ CommandQueue/IStatusBar 注册。
- **QS/音量/省电**：① 接口层（QSTile/State/QSTileView/QSFactory/QSHost）→ ② QsOutCanScrollView+QSPanel 容器 → ③ QSTileImpl+QSFactoryImpl+XTCQSTileBaseView/XTCQSIconView（Lottie 取决于决策 2）→ ④ XTCTileLayout/TileGridLayout/TileTipView 布局 → ⑤ 4 开关+5 controller→滑条→提示条/弹窗→音乐卡片。
- **录屏/手电/助理/蓝牙**：① 录屏服务骨架（MediaProjection+MediaRecorder）→ ② 悬浮录制窗+提示 → ③ 手电全屏页+SOS 节律（亮度动画/恢复）→ ④ 助理 directive 协议（FlashLight/VideoRecord）→ ⑤ 蓝牙状态公开 API 封装。
- **锁屏/隐私/富文本/Provider**：① Keyguard AIDL×2 + KeyguardController → ② `${XTC}`+`$…$` 文本基础设施 → ③ StatusBarProxyProvider+IMStateManager → ④ 提示体系（PopWindow/Hint/InnerToast）→ ⑤ 权限/隐私保留面+文案表落地。

## 4. 下一步（等 UI 规则即可开工）

1. 你把 UI 话术规则/文案替换表给我（决策 1），我按 4 份 SPEC 的 C 节清单批量替换并复写。
2. 同时按决策 2-10 的答复锁定实施口径。
3. 之后按 §3 顺序逐模块写代码：每完成一模块 → 双逻辑登记（DUAL-LOGIC）+ 构建（build.ps1 -Variant both）+ verify + 云控门禁。

---

## 5. 实现完成度（2026-11-02 更新）

4 份 SPEC 中的**必需项已全部实现**（手写 195 个 class，dex 370,840 B，APK 2.33 MB）：

| SPEC | 已实现 | 未做/简化 | 原因 |
|---|---|---|---|
| statusbar-notification | 状态栏行全部图标、面板/玻璃/手势、媒体卡（封面/走马灯/三键）、8 开关、InfoToast、Provider 联动 | PanelView 的 peek/falsing/表冠细节；逐行歌词 | 手表未观察到依赖；歌词需 hook 音乐 App |
| qs-volume | 亮度/音量条 + 8 个玻璃开关（WiFi/数据/蓝牙/热点/飞行/省电/护眼/保持亮屏） | QS 录屏/手电磁贴、亮度自动模式、音量提示条 | UI 定型为用户指定 8 开关；其余非必需 |
| media-tools | 录屏全链路（授权/录制/悬浮窗/保存/中断）、屏幕手电/SOS、助理 directive 协议栈、蓝牙状态公开 API | 内部音频录制（API29+）、硬件 torch SOS（API30） | 本机 API27 不可达/无公开等价 |
| misc-local | Keyguard Binder 桥、隐私页（离线）、自启动页、权限说明页、富文本、StatusBarProxyProvider、FileProvider | 同意后写 launcher 自启白名单；自建锁屏 | SPEC E.2 决策去外部副作用；自建锁屏需另立项 |

风险清单（RISK-MAP 86 条）对应的上报/云控/权限检测逻辑**均未实现**；每次构建由 `no-cloud` 门禁强制校验（strict 变体 0 残留）。