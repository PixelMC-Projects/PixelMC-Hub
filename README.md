# PixelMC-Hub · 旅人手册

Minecraft 玩家个人面板：查看旅程信息、佩戴称号、翻阅收藏的签笺、整合包更新日志和玩家排行。浅深色玻璃界面，包含公开名片、旅程纪念、立体头像、手写开屏与页面动效。

项目包含网页设计参考，以及 `minecraft/` 中的 **Forge 1.20.1 / Java 17 原生 Skija 玩家面板与可选服务端连接**。游戏内模式读取 Manager 的当前存档统计、MemorialTitles 的称号和 DailyFortunes 的归档签笺；模块缺失时显示原因。抽签、解签与存签仍由游戏内物品和方块完成，个人手册展示选择不改变藏签匣方块。

在 `minecraft/` 运行 `gradlew build`，使用 `build/libs/pixelmc-hub-forge-1.20.1-0.5.0-dev.jar`（不要使用 `thin-dev.jar`）。真实数据需要在客户端和服务器安装 Hub，并配合同批构建、协议 v1 的 Manager／MemorialTitles／DailyFortunes 对应版本；Hub 不要求三个模块全部安装，也不向客户端提供数据库或管理凭据。进入世界后按 **H** 打开、**Esc** 返回；偏好按玩家与服务器／存档隔离，保存在客户端的 `config/pixelmc-hub-client.json`。当前客户端渲染库仅含 Windows x64；0.5.0-dev 本地候选加入连续滚动的更新日志、持久缓存与增量同步，以及点赞和仅本人/管理员可见的留言回复；此功能需要配套 Manager 0.9.0-dev 游戏桥接和独立服务。本地候选另含三项排行榜、可选食物书适配、变化通知和动画，含客户端启动预热及通用绘制资源复用；已有登录预载、会话同步及签历缓存继续保留，实际游戏体验待用户验收，见[接入契约](docs/player-integration-contract.md)与[效果边界](docs/native-visual-parity.md)。

网页参考与标题画面的“本地预览”保留明确的演示数据。网页需要 Node.js 22.12+：在项目根目录执行 `npm ci`、`npm run dev`，打开 <http://127.0.0.1:4176>；`npm run build` 生成静态页面。

## English

A Minecraft player panel for journey information, equipped titles, collected fortunes, modpack changelogs, and player leaderboards, with light and dark glass surfaces, public profiles, journey memories, layered skin heads, a handwritten greeting, and animated pages.

This repository contains the web design reference and a **Forge 1.20.1 / Java 17 native Skija player panel with an optional server bridge** under `minecraft/`. In-world panels read current-save statistics from Manager, titles from MemorialTitles, and archived fortunes from DailyFortunes. Missing modules show an unavailable state. Drawing, interpreting, and archiving fortunes remain in-game interactions; personal handbook selections do not change physical chests.

Run `gradlew build` in `minecraft/` and use `build/libs/pixelmc-hub-forge-1.20.1-0.5.0-dev.jar`, excluding the `thin-dev.jar`. Live data requires Hub on both client and server, with matching protocol-v1 Manager, MemorialTitles and DailyFortunes builds from this integration set. Providers are optional; client JVMs receive no database or administrator credentials. Press **H** in a world to open and **Esc** to return. Preferences are stored per player and server/save in `config/pixelmc-hub-client.json`. Client rendering currently bundles Windows x64 libraries only. The local 0.5.0-dev candidate adds continuously scrolling changelogs with durable differential caches, likes and private player/admin conversations, requiring the matching Manager 0.9.0-dev game bridge and independent service. The candidate also includes three leaderboards, optional food-book compatibility, committed change hints and animations, plus startup graphics warmup and reusable rendering resources. Existing preloading, session synchronization and fortune caches are preserved; gameplay acceptance remains pending; see the [integration contract](docs/player-integration-contract.md) and [visual limits](docs/native-visual-parity.md).

The web reference and explicit title-screen local preview retain clearly labelled demo data. For the web reference, use Node.js 22.12+, run `npm ci` and `npm run dev` from the repository root, and visit <http://127.0.0.1:4176>. `npm run build` creates a static build.

## License / 许可

自有代码 / Original application code: Copyright © 2026 zhan_cpu / PixelMC Projects, [Apache-2.0](LICENSE)。允许闭源修改和商用，按许可保留版权、许可和适用声明。Closed-source modifications and commercial use are permitted, subject to the license and notice requirements.

React Bits 组件保留其独立的 [MIT + Commons Clause](src/vendor/react-bits/LICENSE.md) 条款，不能当作 Apache 或标准 MIT 组件单独再分发。已有美术素材与签文仍使用原授权。React Bits components and reused artwork retain their separate terms; this is an application, not a component redistribution package. [完整声明 / Notices](NOTICE.md).
