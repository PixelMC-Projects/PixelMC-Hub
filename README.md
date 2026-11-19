# PixelMC-Hub · 旅人手册

Minecraft 玩家个人面板：查看旅程信息、佩戴称号、翻阅收藏的签笺、整合包更新日志和玩家排行。浅深色玻璃界面，包含公开名片、旅程纪念、立体头像、手写开屏与页面动效。

项目包含网页设计参考，以及 `minecraft/` 中的 **Forge 1.20.1 / Java 17 原生 Skija 玩家面板与可选服务端连接**。游戏内模式读取 Manager 的当前存档统计、MemorialTitles 的称号和 DailyFortunes 的归档签笺；模块缺失时显示原因。抽签、解签与存签仍由游戏内物品和方块完成，个人手册展示选择不改变藏签匣方块。

在 `minecraft/` 运行 `gradlew build`，将 `pixelmc-hub-forge-1.20.1-0.5.5-dev.jar` 安装在两端。游戏中按 H 打开手册、Esc 返回。目前原生客户端渲染支持 Windows x64。选装[问舟 0.1.9-dev](https://github.com/PixelMC-Projects/WenZhou-Agent) 后，获得授权的玩家可以从手册侧栏进入玩法助手。

网页参考与标题画面的“本地预览”保留明确的演示数据。网页需要 Node.js 22.12+：在项目根目录执行 `npm ci`、`npm run dev`，打开 <http://127.0.0.1:4176>；`npm run build` 生成静态页面。

## English

A Minecraft player panel for journey information, equipped titles, collected fortunes, modpack changelogs, and player leaderboards, with light and dark glass surfaces, public profiles, journey memories, layered skin heads, a handwritten greeting, and animated pages.

This repository contains the web design reference and a **Forge 1.20.1 / Java 17 native Skija player panel with an optional server bridge** under `minecraft/`. In-world panels read current-save statistics from Manager, titles from MemorialTitles, and archived fortunes from DailyFortunes. Missing modules show an unavailable state. Drawing, interpreting, and archiving fortunes remain in-game interactions; personal handbook selections do not change physical chests.

Run `gradlew build` in `minecraft/` and install `pixelmc-hub-forge-1.20.1-0.5.5-dev.jar` on both sides. Press H to open the handbook. Native rendering currently supports Windows x64 clients. The optional WenZhou 0.1.9-dev extension adds the gameplay assistant for authorized players.

The web reference and explicit title-screen local preview retain clearly labelled demo data. For the web reference, use Node.js 22.12+, run `npm ci` and `npm run dev` from the repository root, and visit <http://127.0.0.1:4176>. `npm run build` creates a static build.

## License / 许可

自有代码 / Original application code: Copyright © 2026 zhan_cpu / PixelMC Projects, [Apache-2.0](LICENSE)。允许闭源修改和商用，按许可保留版权、许可和适用声明。Closed-source modifications and commercial use are permitted, subject to the license and notice requirements.

React Bits 组件保留其独立的 [MIT + Commons Clause](src/vendor/react-bits/LICENSE.md) 条款，不能当作 Apache 或标准 MIT 组件单独再分发。已有美术素材与签文仍使用原授权。React Bits components and reused artwork retain their separate terms; this is an application, not a component redistribution package. [完整声明 / Notices](NOTICE.md).
