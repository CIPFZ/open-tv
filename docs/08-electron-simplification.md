# Electron 轻量化方案

## 总体原则

Electron 只负责桌面能力，电视业务保持为普通 TypeScript 模块。主界面是单窗口、单播放页，不需要为它引入复杂的应用平台。

## 建议技术栈

| 部分 | 选择 | 不引入 |
| --- | --- | --- |
| 桌面壳 | Electron | 多窗口壳、远程网页容器 |
| 界面 | HTML + CSS + TypeScript | React、Redux、路由框架、CSS 大型组件库 |
| 打包构建 | 轻量 TypeScript/Vite 构建 | 多套构建系统 |
| 播放 | 原生 `<video>` + HLS.js | 第一阶段不内置完整转码器 |
| 配置 | `app.getPath('userData')` 下的 JSON | 第一阶段不引入数据库 |
| 原生播放 | `PlaybackAdapter` 预留 mpv/libmpv | 不在第一阶段绑定原生播放器 |

现有 Web 原型已经迁移为 `src/renderer`、`src/core` 和 `src/shared` 的 TypeScript 模块，保留 HTML/CSS 电视优先界面，不需要引入 React 或大型状态框架。

## 进程边界

```text
主进程
  窗口、菜单、快捷键、全屏、配置文件、来源文本下载
        ↕ 受限 IPC
预加载脚本
  只暴露明确的 appAPI 方法
        ↕
渲染进程
  播放画面、OSD、换台、音量、频道列表、错误状态
```

主进程只提供少量能力：

- `window.toggleFullscreen()`
- `settings.load()` / `settings.save()`
- `playlist.importFile()`
- `playlist.fetchText(url)`
- `app.getVersion()`

不要把整个 `ipcRenderer`、Node.js 或 Electron API 暴露给页面。启用 `contextIsolation`、渲染进程沙箱和严格的 IPC 参数校验。Electron 官方文档将这些列为安全基础：[Context Isolation](https://www.electronjs.org/docs/latest/tutorial/context-isolation)、[Security](https://www.electronjs.org/docs/latest/tutorial/security/)。

## 模块拆分

```text
src/
  main/                 Electron 主进程
    main.ts
    ipc.ts
    settings-store.ts
  preload/
    preload.ts
  renderer/
    app.ts              页面启动和状态协调
    playback/
      playback-service.ts
      hls-adapter.ts
    channels/
      m3u-parser.ts
      channel-store.ts
      channel-zapper.ts
    controls/
      volume-control.ts
      keyboard-control.ts
      fullscreen-control.ts
    ui/
      osd.ts
      playlist-drawer.ts
      settings-drawer.ts
```

## 播放适配器

界面只调用统一接口，不直接依赖 HLS.js：

```text
PlaybackAdapter
  load(url)
  play()
  pause()
  setVolume(value)
  setMuted(value)
  destroy()

第一阶段：HtmlHlsAdapter
后续：MpvAdapter
```

这样第一阶段可以快速验证 M3U8 播放，后续切换 mpv 时不需要重做换台和音量界面。

## 哪些东西暂时不要做

- 不做账号、云同步和远程后台。
- 不做服务端视频代理、转码或缓存。
- 不做多个窗口和复杂路由。
- 不做后台批量扫描全部频道。
- 不把远程播放列表当作可执行页面加载。
- 不为了未来 Android 过早引入跨端状态框架。

## 轻量化的边界

可以简化 Electron 的结构，但不能关闭安全隔离。渲染层可以很轻，远程来源可以很多，但远程来源只能作为数据和媒体地址处理，不能获得 Node.js 或 Electron 权限。Electron 官方的多进程模型仍然保留：主进程负责桌面能力，渲染进程负责页面和播放。[Process Model](https://www.electronjs.org/docs/latest/tutorial/process-model)
