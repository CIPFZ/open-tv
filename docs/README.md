# 开源电视（Open TV）项目文档

这是项目的资料入口。所有调研结论、数据来源、技术决策和开发计划都以本目录为准，代码原型位于项目根目录。

## 文档导航

- [项目调研与结论](./01-research.md)：用户问题、目标、竞品和可行性判断
- [数据来源与采集方案](./02-data-sources.md)：M3U、频道元数据、节目单和数据更新策略
- [产品与实现方案](./03-solution.md)：MVP 范围、用户流程、技术选型和验收标准
- [系统架构](./04-architecture.md)：模块边界、数据模型、播放链路和异常处理
- [开发路线图](./05-roadmap.md)：从原型到 Android TV / 桌面版的分阶段计划
- [风险与合规边界](./06-risk-and-compliance.md)：直播源、版权、隐私和发布风险
- [技术决策记录](./DECISIONS.md)：可追溯的架构选择及后续变更规则
- [机器可读项目记录](./project-record.yaml)：供后续脚本、任务拆解或自动化读取
- [PC 端界面效果图](./design/README.md)：界面基线、功能分区和视觉规则
- [功能优先级](./design/02-function-priority.md)：主功能、辅助功能和设置功能的分层
- [开发前决策清单](./07-open-questions.md)：播放后端、换台、音量、EPG、数据保存和打包策略
- [Electron 轻量化方案](./08-electron-simplification.md)：进程边界、模块拆分和不引入的复杂度
- [工程实施计划](./09-engineering-plan.md)：当前交付、未实现项、阶段顺序和跨平台扩展点
- [设置与稳定性检查记录](./10-stability-checklist.md)：配置恢复、解析回退、关闭清理和桌面验证记录
- [跨平台数据契约](./11-cross-platform-contract.md)：PC、Android 和 Android TV 共享字段、恢复规则和迁移约束

## 当前状态

- 阶段：电视优先 TypeScript MVP / Electron 可运行桌面壳 / 已确定 PC 桌面端优先
- 项目目录：`/Volumes/workspace/project/open-tv`
- 当前界面：`index.html`、`src/renderer/app.ts`、`styles.css`
- 桌面工程：`src/main/main.ts`、`src/preload/preload.ts`、`package.json`
- 共享模型与服务：`src/shared/models.ts`、`src/core`、`src/renderer`
- 默认研发数据源：`https://iptv-org.github.io/iptv/countries/cn.m3u`
- 首个正式目标：PC 桌面端（Windows、macOS、Linux）
- 后续扩展：Android、Android TV / Google TV
- 文档基线日期：2026-10-09

当前版本支持 HTTPS 远程或本地 M3U/M3U8、HLS 播放、电视优先 OSD、上下换台、应用音量、静音、播放/暂停、全屏、频道搜索/分组、多个播放列表、本地配置、有限重试、来源健康状态和错误分类。设置面板采用居中模态浮层，内含来源管理、播放偏好、稳定性选项和主动导出的诊断信息；启动时优先恢复退出前的频道。EPG 数据模型和解析器保留用于后续高级能力，但当前 PC 版本不展示节目单设置，也不会自动加载 XMLTV。mpv/libmpv、系统音量、完整原生签名和 Android/Android TV 真机验收仍按工程计划推进。
