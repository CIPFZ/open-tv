# 开源电视 · Open TV

一个面向客厅观看场景的电视优先 IPTV 播放器。它读取 M3U/M3U8 播放列表，以直播画面、上下换台、音量调节、静音、播放/暂停和全屏为核心；频道列表、来源管理和设置通过短暂 OSD 与抽屉访问。

项目调研、数据来源、技术选型、架构和路线图集中记录在 [`docs/README.md`](./docs/README.md)。后续编码以该目录中的方案文档和 `project-record.yaml` 为基线。

## 运行

开发预览执行 `npm install` 后运行 `npm run dev`；桌面壳执行 `npm start`。生产渲染文件由 Vite 构建，直接双击 HTML 不会启用 TypeScript 模块和解析线程。远程播放列表是否可读仍取决于源站 CORS 和网络状况。

首次打开会加载 `https://iptv-org.github.io/iptv/countries/cn.m3u` 研发样本。也可以打开主界面的设置图标，粘贴自己的 HTTPS M3U URL 或选择本地文件。

## 数据与使用边界

- 默认数据源来自 [iptv-org/iptv](https://github.com/iptv-org/iptv) 的公开播放列表；该项目只维护公开流媒体链接，不存储视频文件。
- 频道是否可播放取决于源站的地区限制、CORS、直播状态和版权授权。
- 发布产品时应让用户自行添加其有权观看的来源，并增加来源有效性检查、错误提示和下架机制。

## 当前实现与后续路线

当前已完成电视优先 Web MVP、HLS 播放适配器、换台/音量/静音/播放/全屏、OSD、频道抽屉、播放列表管理、设置和本地状态保存，并加入 Electron 安全主进程、preload 受限 API、共享数据模型以及 Android/Android TV 的 Compose + Media3 TV-first 入口骨架。

完整工程顺序、已实现边界、未实现项和 Android/Android TV 扩展点见 [`docs/09-engineering-plan.md`](./docs/09-engineering-plan.md)。
