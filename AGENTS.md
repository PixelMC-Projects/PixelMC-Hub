# PixelMC-Hub

- 本项目位于 `E:\minecraftserver\fabricmod\PixelMCPlayerHub`，产品名与远程仓库为 PixelMC-Hub。包含网页设计参考及 `minecraft/` 下的 Forge 1.20.1 原生 Skija 玩家面板和可选服务端连接，不是 Manager 管理后台。
- 使用场景是 Minecraft Java 版游戏内横屏面板；验收优先横屏分辨率、GUI 缩放和鼠标键盘交互，不开展手机竖屏专项设计或验收。
- 使用浅色液态玻璃方向和实际 React Bits 组件；保留获准的三页：个人主页、我的称号、我的签笺。没有玩法指南页面。详见 `docs/design-spec.md`。
- 原生移植以完整前端源码为准：逐项对应组件参数、透明层叠、背景采样、字体选择、阴影、透视、动画曲线/时长/延迟、hover/press/focus。不能把静态渐变或单轴旋转当作已还原的玻璃/三维效果。映射与尚存差异记录在 `docs/native-visual-parity.md`，自动截图不等于用户的游戏验收。
- 本项目自有代码使用 Apache-2.0，不影响其他模组许可证。React Bits 保留 `src/vendor/react-bits/LICENSE.md` 的独立条款，不得改标为 Apache 或标准 MIT；已有素材保留独立授权，见 `NOTICE.md`。构建产物须保留许可证副本。
- 演示数据和真实接口必须清楚区分。网页和标题画面预览为样例；实际游戏内面板只显示真实服务端数据或明确的加载／不可用状态。接入以 `docs/player-integration-contract.md` 为准，`docs/native-prototype-contract.md` 的客户端实验范围与旧并行分工仅是历史记录。
- 本轮仅执行本地开发、构建、隔离协议与隐藏 GPU 检查，不启动或控制游戏、修改生产服务／数据库／现有游玩实例／同步目录。真实游戏验收由用户完成；未明确授权前不提交、推送或发布。
- 不引入远程字体，不提交真实玩家记录、连接凭据或管理后台密钥。运行时只接收自己的白名单字段及非秘密的服务器／存档标识。素材来源见 `NOTICE.md`。
- 关闭称号展示必须保留已佩戴状态；未解签不泄露签文；未归档的签不能出现在收藏历史。佩戴、展示和个人签笺选择只在服务端确认后更新，禁止管理员 RPC 隧道。
- README 保持简洁、中英双语，先说作用和运行方式；勿添加无关技术或测试章节。
- 不将上级目录的服务器私有魔改记录纳入 Git。远程仓库为 https://github.com/PixelMC-Projects/PixelMC-Hub，不要推送到其他项目。
