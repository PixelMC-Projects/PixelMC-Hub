# PixelMC-Hub · 旅人手册

Minecraft 玩家个人面板：查看旅程信息、佩戴称号、翻阅收藏的签笺。浅色玻璃界面，包含立体头像、手写开屏与页面动效。

项目包含网页设计原型，以及 `minecraft/` 中的 **Forge 1.20.1 / Java 17 原生 Skija 客户端原型**。当前打包 Windows x64 渲染库；其余平台尚未提供。称号、签笺和旅程信息使用本地演示数据，尚未连接 Manager 或插件接口。抽签、解签与存签仍由游戏内物品和方块完成。

在 `minecraft/` 运行 `gradlew build`，将 `build/libs/pixelmc-hub-forge-1.20.1-0.1.0-native-prototype.jar` 放入独立客户端的 `mods/`（不要使用 `thin-dev.jar`）。进入世界后按 **H** 打开、**Esc** 返回；设置保存在该客户端的 `config/pixelmc-hub-client.json`。本原型只安装客户端，无需数据库或网页服务。原生材质、字体与动效按当前网页源码逐项移植；[效果对应与边界](docs/native-visual-parity.md)记录实现细节，实际游戏体验仍待验收。

网页原型需要 Node.js 22.12+：在项目根目录执行 `npm ci`、`npm run dev`，打开 <http://127.0.0.1:4176>；`npm run build` 生成静态页面。

## English

A Minecraft player panel for journey information, equipped titles, and collected fortunes, with light glass surfaces, layered skin heads, a handwritten greeting, and animated pages.

This repository contains the web design reference and a **Forge 1.20.1 / Java 17 native Skija client prototype** under `minecraft/`. The current package includes Windows x64 native libraries only. Titles, fortunes, and journey information use local demo data; Manager and plugin integrations are not connected. Drawing, interpreting, and archiving fortunes remain in-game interactions.

Run `gradlew build` in `minecraft/`, then put `build/libs/pixelmc-hub-forge-1.20.1-0.1.0-native-prototype.jar` in an isolated client's `mods/` directory, excluding the `thin-dev.jar`. Press **H** in a world to open the panel and **Esc** to return. Preferences are saved to that client's `config/pixelmc-hub-client.json`. No server installation, database, or web service is required. Native materials, typography, and motion follow the current web source; [implementation mapping and limits](docs/native-visual-parity.md) distinguish the port from pending in-game acceptance.

For the web reference, use Node.js 22.12+, run `npm ci` and `npm run dev` from the repository root, and visit <http://127.0.0.1:4176>. `npm run build` creates a static build.

## License / 许可

自有代码 / Original application code: Copyright © 2026 zhan_cpu / PixelMC Projects, [Apache-2.0](LICENSE)。允许闭源修改和商用，按许可保留版权、许可和适用声明。Closed-source modifications and commercial use are permitted, subject to the license and notice requirements.

React Bits 组件保留其独立的 [MIT + Commons Clause](src/vendor/react-bits/LICENSE.md) 条款，不能当作 Apache 或标准 MIT 组件单独再分发。已有美术素材与签文仍使用原授权。React Bits components and reused artwork retain their separate terms; this is an application, not a component redistribution package. [完整声明 / Notices](NOTICE.md).
