# 数据来源与采集方案

## 1. 数据类型

| 数据 | 用途 | 首选来源 | 更新方式 |
| --- | --- | --- | --- |
| M3U/M3U8 | 频道与播放地址 | 用户粘贴的公开列表、获得授权的官方公开流、开发阶段的 iptv-org 列表 | 启动时加载，可手动刷新 |
| 频道元数据 | 名称、国家、分类、Logo | `iptv-org/api` 或播放列表内的 `tvg-*` 属性 | 与播放列表一起刷新；以后做缓存 |
| EPG/XMLTV | 节目单和当前节目 | 频道提供方或明确授权的 XMLTV 源 | 按来源配置的周期拉取 |
| 播放状态 | 延迟、HTTP 错误、HLS 错误 | 应用本地探测结果 | 仅用于当前用户，不上传播放内容 |

## 2. 推荐入口

开发阶段先使用小规模列表，避免每次加载数万条记录：

- 中国频道：`https://iptv-org.github.io/iptv/countries/cn.m3u`
- 新闻频道：`https://iptv-org.github.io/iptv/categories/news.m3u`
- 完整列表（仅用于离线解析和压力测试）：`https://iptv-org.github.io/iptv/index.m3u`
- 频道元数据：`https://iptv-org.github.io/api/channels.json`
- 流信息：`https://iptv-org.github.io/api/streams.json`

上述入口来自 [iptv-org/iptv](https://github.com/iptv-org/iptv) 和 [iptv-org/api](https://github.com/iptv-org/api)。它们是公开数据入口，不等于每条流都在所有地区可用，也不等于本项目取得了节目转播权。

## 3. 采集和更新原则

1. 应用优先读取用户主动添加的 URL 或本地文件。
2. 内置示例源只用于研发和演示，保留来源链接、更新时间和移除入口。
3. 不抓取需要登录、绕过鉴权、绕过地区限制或明显来自盗播站点的地址。
4. 解析 M3U 时保存 `tvg-id`、`tvg-name`、`tvg-logo`、`group-title`、语言和原始 URL，便于后续接入 EPG。
5. 对每个源记录最后成功时间、失败次数和错误类型；连续失败时从“推荐”中降级，但不替用户修改其自有列表。
6. 播放地址只在内存或本地配置中使用，不通过项目服务器中转视频流。

## 4. 预期数据模型

```text
PlaylistSource
  id, name, url_or_file, kind, last_loaded_at, enabled

Channel
  id, playlist_id, name, group, logo_url, language, country
  stream_url, stream_type, tvg_id, last_checked_at, health_status

Favorite
  channel_id, created_at

Program (第二阶段)
  channel_id, start_at, end_at, title, description, source_url
```

## 5. 质量检查

- 当前 PC 实现只接受不含凭据的 `https` URL；阻止 `http`、`file:`、脚本协议和非预期内网地址。
- M3U 解析失败时显示具体行号或来源错误，不把空列表误报成“没有频道”。
- HLS 播放失败要区分网络错误、跨域错误、地区限制和已失效链接。
- 频道 Logo 和 EPG 属于外部资源，加载失败不影响播放。
