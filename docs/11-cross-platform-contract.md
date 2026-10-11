# 跨平台数据契约

Open TV 的 PC、Android 和 Android TV 客户端共享同一份本地配置语义。当前契约版本是 `1`，机器可读定义位于 [`schemas/open-tv-config.v1.schema.json`](../schemas/open-tv-config.v1.schema.json)，TypeScript 运行时模型位于 [`src/shared/models.ts`](../src/shared/models.ts)。

Android/Android TV 的 Kotlin 共享层位于 [`platforms/android/src/main/kotlin/org/opentv/shared`](../platforms/android/src/main/kotlin/org/opentv/shared)，其中 `M3uParser.kt` 与 `XmltvParser.kt` 与 PC 保持 HTTPS 校验、HLS 清单识别、元数据、XMLTV 时区和 URL 去重语义；遥控器动作映射位于 [`TvNavigation.kt`](../platforms/android/src/main/kotlin/org/opentv/tv/TvNavigation.kt)。`platforms/android/app` 已接入 Compose、Media3 播放边界、可聚焦 TV 控制条和 TV 启动入口；本机和 Android CI 均可执行 debug 编译，目标设备安装与遥控器验收仍待完成。

契约检查可运行 `npm run check:contract`；CI 会在桌面端构建前同时检查 Schema、Kotlin 模型、播放端口和 TV 按键语义。

## 启动恢复

- `activePlaylistId` 记录上次使用的来源。
- `lastChannelId` 记录退出前正在播放的频道。
- 客户端启动时优先寻找包含 `lastChannelId` 的启用来源，再恢复该频道；找不到时回退到启用来源的第一频道。
- `defaultPlaylistId` 和 `defaultChannelId` 只作为旧配置兼容字段保留，当前 TV 界面不要求用户维护它们。

## 共享实体

| 实体 | PC 当前用途 | Android / Android TV 复用方式 |
| --- | --- | --- |
| `PlaylistSource` | 来源添加、刷新、启用/停用、健康状态 | Android 用于本地来源列表和刷新任务；TV 使用同一来源选择逻辑 |
| `Channel` | 频道搜索、分组、上下换台和播放 | Media3/ExoPlayer 使用同一频道 URL、编号和分组字段 |
| `PlaybackPreferences` | 自动播放、音量记忆、自动全屏、超时和重试 | Android 映射到前后台恢复、播放器重试和音量状态 |
| `lastChannelId` | 关闭后恢复上次频道 | TV 启动器进入应用后恢复同一频道 |
| `SourceError` | 来源失败、超时、限制和格式错误 | Android/TV 显示同一错误类别，不绕过授权或地区限制 |
| `EpgSource` / `Program` | 已支持保存的 XMLTV 地址启动时自动加载；无节目时隐藏 | Android/TV 后续按同一来源字段展示当前/下一节目 |

## 平台边界

- PC 使用 Electron 主进程保存 JSON、选择本地文件和执行远程文本读取；渲染层不直接访问 Node.js。
- Android 使用 Kotlin 数据类和 Media3/ExoPlayer；`AndroidConfigCodec`/`AndroidConfigStore` 已按 v1 字段保存播放列表、频道、来源状态、上次频道、音量、静音和播放偏好，`AndroidPlaylistLoader` 负责 HTTPS、重定向、大小和超时边界，`AndroidEpgLoader` 对 XMLTV 复用同一 HTTPS/重定向/大小限制并由 `XmltvParser` 解析当前/下一节目，系统文件选择器导入的本地 M3U/M3U8 复用同一解析器，导入或迁移时继续遵循同一字段约束。
- Android TV 与 Android 共用播放器和配置层，只替换为 D-pad 焦点导航、遥控器返回和 TV 启动器入口；频道侧栏和居中设置弹层与桌面端保持同一空间关系。
- `PlaybackAdapter` 是 PC 播放器边界；Android 不直接复用 Electron 实现，但保持 `load / play / pause / volume / mute / destroy` 语义一致。

## 迁移规则

1. 读取 `schemaVersion`；缺失版本和当前支持的 legacy `0` 会先迁移到 `1`，更高未知版本不得静默覆盖。Android 配置导入使用严格解码，格式错误或未知版本会拒绝导入并保留现有配置。
2. 未知版本不得静默覆盖，客户端应保留原文件并使用默认配置启动。
3. 迁移必须保留 `lastChannelId`、来源名称、启用状态、音量和播放偏好。
4. 本地来源的 `text` 只在本地配置迁移时使用，不写入诊断导出和普通日志。
5. 播放列表 URL 仍由各平台执行 HTTPS、无凭据和长度校验。
