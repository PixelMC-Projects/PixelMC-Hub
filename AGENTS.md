# PixelMC-Hub

- 本项目位于 `E:\minecraft-pixelmc\mods\PixelMCPlayerHub`，产品名与远程仓库为 PixelMC-Hub。包含网页设计参考及 `minecraft/` 下的 Forge 1.20.1 原生 Skija 玩家面板和可选服务端连接，不是 Manager 管理后台。
- 使用场景是 Minecraft Java 版游戏内横屏面板；验收优先横屏分辨率、GUI 缩放和鼠标键盘交互，不开展手机竖屏专项设计或验收。
- 使用浅色液态玻璃方向和实际 React Bits 组件；网页保留获准的五页：个人主页、我的称号、我的签笺、更新日志、玩家排行；玩家排行已有确认后的 Web 参考及本地原生/真实数据接入候选；更新日志已有本地 Web 参考及 Forge 1.20.1 原生接入候选，真实游戏验收仍由用户完成。没有玩法指南页面。详见 `docs/design-spec.md`。
- 原生移植以完整前端源码为准：逐项对应组件参数、透明层叠、背景采样、字体选择、阴影、透视、动画曲线/时长/延迟、hover/press/focus。不能把静态渐变或单轴旋转当作已还原的玻璃/三维效果。映射与尚存差异记录在 `docs/native-visual-parity.md`，自动截图不等于用户的游戏验收。
- 本项目自有代码使用 Apache-2.0，不影响其他模组许可证。React Bits 保留 `src/vendor/react-bits/LICENSE.md` 的独立条款，不得改标为 Apache 或标准 MIT；已有素材保留独立授权，见 `NOTICE.md`。构建产物须保留许可证副本。
- 演示数据和真实接口必须清楚区分。网页和标题画面预览为样例；实际游戏内面板只显示真实服务端数据或明确的加载／不可用状态。接入以 `docs/player-integration-contract.md` 为准，`docs/native-prototype-contract.md` 的客户端实验范围与旧并行分工仅是历史记录。
- 本地开发、构建和隔离检查不应擅自启动或控制游戏、修改生产服务／数据库／现有游玩实例／同步目录。真实游戏验收由用户完成；工程完成后的 GitHub 提交和推送遵循上级 GitHub 源码范围规则，发行和部署按用户要求进行。
- 不引入远程字体，不提交真实玩家记录、连接凭据或管理后台密钥。运行时只接收自己的白名单字段及非秘密的服务器／存档标识。素材来源见 `NOTICE.md`。
- 关闭称号展示必须保留已佩戴状态；未解签不泄露签文；未归档的签不能出现在收藏历史。佩戴、展示和个人签笺选择只在服务端确认后更新，禁止管理员 RPC 隧道。
- README 保持简洁、中英双语，先说作用和运行方式；勿添加无关技术或测试章节。
- 不将上级目录的服务器私有魔改记录纳入 Git。远程仓库为 https://github.com/PixelMC-Projects/PixelMC-Hub，不要推送到其他项目。

## 模组接口准则

- 新增或变更联动、玩家数据、provider/capability、缓存与通知前，必须阅读 `../pixelmcmanager/forge/pixelmcmanager-forge-1.20.1/docs/模组接口规范.md`，并与本项目 `docs/player-integration-contract.md` 一起执行。
- 保留连接 UUID、作用域、action/字段白名单和会话失效边界；公开排行摘要是显式获准的新能力，不构成任意读取其他玩家私有数据的通道。
- 接口规范和迁移状态必须随实现更新。先用 Web 确认排行榜设计，再实施原生移植；不要把演示 fixture 引入真实运行或以文档代替实际接入。

## Java 格式

- `minecraft/src/main/java` 遵循 `docs/java-formatting.md`；使用 `tools/format-java.ps1` 检查。格式整理与行为修改分开。
