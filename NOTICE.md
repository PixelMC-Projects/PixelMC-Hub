# 素材与依赖 / Credits

Copyright © 2026 zhan_cpu / PixelMC Projects. 本项目自有应用代码使用 Apache-2.0，见 `LICENSE`。本次改许可只涉及本玩家面板自有代码，不修改其他模组或下列独立素材、组件的授权。

本原型使用 PixelMC Projects 已有素材，不包含服务器凭据或生产玩家记录。

- 冬日手册页边插画由 OpenAI ImageGen 为本项目生成。雪松路标、冬日来信、睡狐和松枝提灯以透明 WebP 存放在 `public/assets/winter/`。 / The winter grove, letter, sleeping fox, and lantern illustrations were generated for this project with OpenAI ImageGen.

- `public/assets/wenzhou-items/`：问舟 Web 配方示例中的原版物品纹理与方块模型缩略图取自本机 Minecraft Java Edition 1.20.1 客户端。原版美术归 Mojang / Microsoft 所有，项目的 Apache-2.0 许可不涵盖这些素材。

- `public/assets/fortune-slip.png`、`fortune-barrel.png`、`collection-chest.png`：来自本地 DailyFortunes 项目的签笺及默认像素模型物品图。Copyright © 2026 zhan_cpu / PixelMC Projects，Apache-2.0。
- `src/fortunes.json`：DailyFortunes 六十签文案，同上。
- `public/assets/server-logo.png`：用户提供的 PixelMC 服务器 Logo，原文件用于品牌展示；该品牌标识不因应用代码使用 Apache-2.0 而被重新授权。 / User-provided PixelMC server logo; the application license does not relicense this brand asset.
- `public/assets/avatar.png`：MemorialTitles 附带的默认演示头像。同项目来源；仅作为无完整皮肤时的占位。完整皮肤可通过本地 `VITE_PREVIEW_SKIN` 配置，`public/assets/local-preview/` 中的测试皮肤不纳入仓库，也不受本项目许可证重新授权。
- Lucide 图标：ISC license，版权见安装包中的 LICENSE。
- React：MIT license。Vite：MIT license。依赖版权声明保留于各依赖的许可证文件。
- Swipe Toast 网页动效依赖 Motion / Framer Motion / motion-dom / motion-utils（MIT，Copyright Motion B.V.）及 tslib（0BSD，Copyright Microsoft）；分发副本在构建产物 `licenses/` 中。
- 字体调用系统 Segoe UI、Microsoft YaHei UI 与可用宋体，不随项目分发字体文件。
- `src/vendor/react-bits/` 的 GlassIcons、GlassSurface、TiltedCard、AnimatedList、ClickSpark：Copyright © 2026 David Haz；使用该目录 `LICENSE.md` 中的 **MIT + Commons Clause License Condition v1.0**，不是标准 MIT 或 Apache 授权。来源：https://github.com/DavidHDev/react-bits 。官方注册表来源与获取时的文件哈希见 `SOURCES.json`。2026/11/03 修改 GlassIcons 和 TiltedCard，加入应用的减少动态效果选项、当前页面和选中状态的可访问性属性；2026/11/05 AnimatedList 适配应用现有图标、受控选中状态与读屏标签，ClickSpark 保留上游实现。组件只作为本玩家应用的一部分使用，不作为组件包独立再分发。

DailyFortunes 素材及签文采用 Apache-2.0，许可全文见 `LICENSE`；来源为 https://github.com/PixelMC-Projects/DailyFortunes 。默认头像来源为 https://github.com/PixelMC-Projects/MemorialTitles 。本项目保留这些素材的原始署名与许可声明。

Artwork and fortune text are reused from the local PixelMC projects under their original license. The avatar is a bundled demo placeholder. Third-party dependencies retain their own licenses. System fonts are referenced, not redistributed.

## Native client / 原生客户端

- Swipe Toast：2026-11-04 获取官方 `src/content/Micro/SwipeToast/SwipeToast.jsx` / `.css`，来自 https://reactbits.dev/micro/swipe-toast 。Copyright © 2026 David Haz，沿用上述 MIT + Commons Clause；网页改用 Lucide 关闭图标、白色玻璃与左蓝右紫进度条，原生 `HubToast.java` 移植其 400ms 进入、280ms 退出、倒计时、悬停暂停、下滑与回弹交互。该原生适配仍保留原授权，不是 Apache，也不作为独立组件分发。Swipe Toast is adapted as part of this application; upstream license and copyright are retained.

- `minecraft/` 中自有的原生 UI 与绘制桥接使用 Apache-2.0。直接移植自 React Bits 的弹簧动画、透视及玻璃着色算法保留 Copyright © 2026 David Haz / MIT + Commons Clause，具体文件在文件头标明；完整条款随 JAR 保存于 `assets/pixelmc_hub/licenses/React-Bits-LICENSE.md`。Native adaptations of React Bits motion, perspective, and glass algorithms retain the original copyright and MIT + Commons Clause terms; they are not relicensed as Apache-2.0. File headers identify those adaptations. No browser runtime is embedded.
- Skija `0.143.18` / HumbleUI types `0.2.0`：Apache-2.0；Windows x64 本地库包含 Skia，使用其 BSD 许可及上游声明。依赖以原始 Jar-in-Jar 分发，未重定位或改写 JNI 包。The original dependency jars are bundled without JNI relocation.
- 上游许可副本及 Skija NOTICE 保存于 `minecraft/src/main/resources/assets/pixelmc_hub/licenses/`，并包含在客户端 JAR 中。Native dependency license and notice copies are included in the client JAR.
- `minecraft/src/main/resources/assets/pixelmc_hub/` 下复用的图像、签文和 Lucide 图标沿用上文对应许可；素材清单记录来源与文件摘要。运行时玩家皮肤只读取游戏内已加载的纹理，不保存或发布玩家皮肤文件。Reused native assets keep their original licenses; runtime skin textures are not published with the project.

Original application code is Apache-2.0. React Bits is Copyright © 2026 David Haz, under its separate MIT + Commons Clause terms; it is not relicensed here. GlassIcons and TiltedCard were adapted on 2026-11-03 for application motion preferences and accessibility state. These components form part of the player application and are not offered as a standalone component library. The static build includes notices and license copies under `licenses/`.

冬日称号收藏匣的日景、夜景与八枚徽章图集使用内置 ImageGen 为本项目生成。
冬日签笺页的雪窗签案日景和夜景由 OpenAI ImageGen 为本项目绘制。 / The day and night fortune-desk illustrations were generated for this project with OpenAI ImageGen.
签笺页的线装日记底图、雪路驿亭木刻、寒梅挂轴和页尾的红绳折签亦由 OpenAI ImageGen 为本项目绘制。 / The bound journal spread, snowy post house woodcut, plum-blossom scroll, and red-thread folded fortune slip were also generated for this project with OpenAI ImageGen.
夹在手记中的芦岸长签纸由 OpenAI ImageGen 为本项目绘制。 / The loose fortune slip with the winter reed-bank vignette was generated for this project with OpenAI ImageGen.
冬日更新日志的雪窗印刷间插画由 OpenAI ImageGen 为本项目绘制。 / The snowy print workshop illustration for the winter changelog was generated for this project with OpenAI ImageGen.
冬日排行榜的雪地日景与夜景由 OpenAI ImageGen 为本项目绘制。预览缺少皮肤时使用本站的像素头像与原创旅行冬装。 / The day and night awards grounds were generated for this project with OpenAI ImageGen. The preview fallback pairs the bundled avatar with an original winter travel outfit.
旅程档案使用的折叠地图与指南针插画由 OpenAI ImageGen 为本项目绘制。 / The folded travel map and compass illustration was generated for this project with OpenAI ImageGen.
