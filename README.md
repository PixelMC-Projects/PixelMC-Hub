# PixelMC Player Hub · 旅人手册

面向玩家的个人面板：查看旅程记录、佩戴称号、翻阅收藏的签笺。浅色液态玻璃界面，配合彩色玻璃导航、立体收藏卡片与 Minecraft 头像和物品。

当前为**本地交互原型**，包含个人主页、我的称号、我的签笺和界面设置。数据均为演示；不会连接服务器或修改游戏。抽签、解签与存签仍由游戏内物品和方块完成。

运行：安装 Node.js 22.12+，在项目目录执行 `npm install`、`npm run dev`，打开 <http://127.0.0.1:4176>。`npm run build` 生成静态页面。页面下方的“预览选项”可切换今日签笺状态及还原演示。

## English

A personal player panel for PixelMC: view your journey, equip titles, and revisit collected daily fortunes. Light liquid-glass surfaces, colorful glass navigation, and dimensional collection cards retain Minecraft avatars and item artwork.

This is a **local interactive prototype**, with Home, Titles, Fortunes, and interface settings. All data is simulated; no game server is connected. Drawing, interpreting, and archiving fortunes remain in-game interactions.

With Node.js 22.12+, run `npm install` and `npm run dev`, then visit <http://127.0.0.1:4176>. Use `npm run build` for a static build. Preview controls switch fortune states and restore demo data.

## License / 许可

自有代码 / Original application code: Copyright © 2026 zhan_cpu / PixelMC Projects, [Apache-2.0](LICENSE)。允许闭源修改和商用，按许可保留版权、许可和适用声明。Closed-source modifications and commercial use are permitted, subject to the license and notice requirements.

React Bits 组件保留其独立的 [MIT + Commons Clause](src/vendor/react-bits/LICENSE.md) 条款，不能当作 Apache 或标准 MIT 组件单独再分发。已有美术素材与签文仍使用原授权。React Bits components and reused artwork retain their separate terms; this is an application, not a component redistribution package. [完整声明 / Notices](NOTICE.md).
