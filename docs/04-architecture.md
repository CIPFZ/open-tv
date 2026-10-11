# 系统架构

## 1. 分层

```text
数据源层
  URL / 本地 M3U / API / XMLTV
        ↓
适配与解析层
  SourceAdapter → M3UParser → Normalizer → HealthChecker
        ↓
应用状态层
  playlists / channels / recentChannelIds / activeChannel / playbackState / playbackError
        ↓
展示层
  频道导航、搜索、播放器、节目单、设置
        ↓
播放后端
  HTML5 Video + HLS.js（Web）
  Media3 或 mpv/libmpv（原生端）
```

## 2. 播放链路

1. SourceAdapter 获取文本并限制响应大小与超时。
2. M3UParser 读取 `#EXTINF` 与下一条媒体 URL。
3. Normalizer 统一频道 ID、名称、分组和 Logo 字段。
4. 用户点击频道后，PlaybackService 判断浏览器原生 HLS 或 HLS.js。
5. 播放器监听 manifest、网络和解码错误，映射成用户可理解的状态。
6. 退出或切换频道时销毁旧 HLS 实例，避免后台继续占用连接。

## 3. 组件边界

- `SourceService`：导入、刷新、删除和保存播放列表来源。
- `CatalogService`：频道解析、去重、分组、搜索和排序。
- `PlaybackService`：播放器生命周期、重试、全屏和音量。
- `LastChannelService`：保存退出前频道并在启动时恢复；旧 `recent` 字段仅为配置兼容保留，首版不设置最近频道导航。
- `EpgService`（第二阶段）：XMLTV 获取、时区转换、当前节目匹配。
- `HealthService`（第二阶段）：仅对用户配置的源做按需检测，不批量扫描第三方站点。

## 4. 安全和稳定性约束

- 对用户输入的 URL 做协议、长度和主机校验。
- 不将 URL 直接拼接进 HTML；频道名称、Logo 和分组都需转义。
- 不保存 M3U 中可能出现的账号密码到日志。
- 网络请求设置超时、取消和最大响应大小。
- 外部图片、EPG 和播放流失败时，界面保持可操作。
- 原生端将播放进程与 UI 解耦，防止播放器崩溃带走主窗口。

## 5. 跨平台共享契约

`src/shared/models.ts` 定义 `PlaylistSource`、`Channel`、`Program`、`PlaybackPreferences`、`PlaybackError` 和 `PlaybackAdapter`。PC 渲染层先使用 `HtmlHlsAdapter`，Android 使用 Media3，桌面原生增强阶段再实现 `MpvAdapter`；这些播放器只负责播放生命周期，换台、音量、OSD 和来源管理由上层状态机复用。
