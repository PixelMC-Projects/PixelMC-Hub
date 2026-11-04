# Native visual parity / 原生效果对应记录

本文件比较仓库**当前实际使用的网页界面**与 `minecraft/` 中的 Forge 1.20.1 / Skija 移植。网页源码是依据；原生实现通过复用路径、公式、参数和着色计算还原效果，不包含浏览器。这里的“已移植”表示代码对应成立，不代表已通过用户的游戏内视觉验收，也不表示两种排版/合成环境已经逐像素一致。

## 范围与对应文件

| 网页来源 | 原生对应 |
| --- | --- |
| `src/App.jsx`、`src/data.js`、`src/fortunes.json` | `HubView`、`HubData`；本地三页和完整 60 签 |
| `src/styles.css`、`src/glass-theme.css` | `HubView` 的布局、绘制顺序、状态和材质调用 |
| `src/vendor/react-bits/GlassIcons.jsx` / `.css` | `HubMotion.Navigation`、`HubDraw.iconPose`、`HubGlass` 导航 RuntimeEffect |
| `src/vendor/react-bits/TiltedCard.jsx` / `.css` | `HubMotion.Tilt`、`HubTransforms`、`HubDraw.tiltGlare` |
| `src/vendor/react-bits/GlassSurface.jsx` / `.css` | `GlassStyle`、`HubGlass`；当前两个实例均为 `refraction=false` |
| `src/WelcomeSplash.jsx`、`src/welcome-splash.css` | `HubView.welcome` / `strokeSvg`；直接使用两条原始 SVG cubic 路径 |

代码保留原有许可边界：自有 UI 代码使用 Apache-2.0；`HubMotion` 中移植的 React Bits 动力学，以及 renderer 的相关算法/着色器保留 David Haz 的 **MIT + Commons Clause** 条款。完整副本位于 `src/vendor/react-bits/LICENSE.md`，并随模组打包到 `assets/pixelmc_hub/licenses/React-Bits-LICENSE.md`。不能把移植部分改称普通 MIT 或 Apache。

## 当前网页实际使用的材质

| 项目 | 网页参数与层叠顺序 | 原生对应 / 状态 |
| --- | --- | --- |
| 整体窗口 | 28px 圆角；`#f4f6ff99`；backdrop blur 24px | `blurBackdrop(24)` 后绘制同一 ARGB；不再以不透明浅色填满母面板 |
| 窗口阴影 | `0 28px 80px -28px #43588260`、`0 5px 16px #53658e12`，inset `0 1px 0 white` | 在外层 clip 之前绘制两条真实 Gaussian 阴影；高光在窗口内部 |
| 主内容背景 | 135° `#ffffffad → #f5f6ff9c`；顶栏 `#ffffff36` | 同色值、角度和 alpha；母面板透明度只作用背景，不降低文字 alpha |
| 侧栏 GlassSurface | `borderWidth=.08, brightness=65, opacity=.9, blur=12, displace=.5, backgroundOpacity=.32, saturation=1.15, distortionScale=-12, RGB offsets=0, refraction=false` | `GlassStyle.SIDEBAR`；按 fallback 实际路径采样、blur12、saturate1.61、brightness1.04。`opacity` 是位移图参数，**不是整栏透明度** |
| 侧栏 CSS 覆盖 | surface background：145° `#ffffff40 → #e6edff28 @52% → #e9e3ff35`；before：145° `#f8fcff3d → #eaf1ff2b @50% → #eee9ff38`；after：118° `#ffffff24 → transparent @32–72% → #bcc0e314` | 分别绘制三层；CSS 的 surface background 覆盖 fallback 的 `.28` 白底，因此原生没有再重复添加这层白底 |
| 侧栏接缝 | 只有窗口负责外侧圆角；靠内容区域两角为直角 | 先 clip 整窗，侧栏自身 radius0；保留白边及 `inset 2px 1px 3px white / inset -3px 0 7px #abb9e142 / 6px 0 20px #65719e0c` |
| 身份卡 | 110° `#d9f0ff → #e4e8ff @45% → #dbc9f9`，叠加 78%/34% 紫色径向环境光 | 原生 CSS 角度多 stop linear/radial helper；在身份卡渐变画完后捕获同一 GPU 图层，供玻璃采样 |
| 身份卡 GlassSurface | `.04,65,.7,8,backgroundOpacity=.09,saturation=1.25,distortionScale=-45,RGB=0,refraction=false` | `GlassStyle.IDENTITY`；fallback 实际 backdrop blur 为12，不把 prop `blur=8` 误当作该 fallback 的 CSS blur；白底 alpha `.12+.09*.5=.165` |
| 身份卡阴影与边缘 | 外层 `0 10px 30px #8980c528`；内层 `inset 0 1px 2px white / inset 0 0 5px #8c9ac329 / 0 8px 22px #6c64aa13` | 按层级分别绘制；真实 Gaussian / inset helper，取代原先偏移圆角矩形近似 |
| 导航 GlassIcons | size46、radius15、bevel7、refraction11.04px、dispersion0、frost.1 → soft1.69px、shine.9、shade.22、白 tint.16 | 由原网页 fragment 计算移植为 SkSL；使用同一 plate、roundBox/normal、bend、shade、shine 数学。glyph 为 tile40% =18.4px |
| 导航颜色 | HSL 精确转色：蓝 `#569dfb → #1949e6`，紫 `#bf72f3 → #6f34d5`，橙 `#ffb83d → #f8651b` | 已使用对应 8bit 颜色；不是先前低饱和颜色加大面积白片的替代方案 |
| 称号徽章 | 60px / right21 / top43；外层 -9°；back 子层 rotate12° + translate(4,-5)，front 145° 白 `.651 → .278` + backdrop blur4 | 原生分别变换两层，并捕获徽章 ROI 后真实模糊；未获得卡整体 alpha.8，back saturation.35 |
| 佩戴预览底座 | `::before` 140×42 / bottom32 / `border-radius:50%` / `#f6f8ff9c` / white1px，shadow `0 5px 12px #7184b827`、inset `0 -5px 7px #b0a8d017`；ground 111×26 / bottom46 / `#a3b4d54d` | 使用真正椭圆及各自水平/垂直半径，补齐双层台面、外阴影、底部内阴影与白边；替换此前缺少 ground 的扁平胶囊近似，头像投影底缘对齐台面中心 |
| 佩戴预览文字 | 内容 top26；称号21px/600、line-height31.5、margin-bottom5；玩家名13px/500、tracking.5、normal行高17 | 在本机隐藏浏览器读取实际网页行框，三行 baseline 相对预览顶部为50 /76.5 /194.5；原生分别用275 /301.5 /419.5，补回原先漏掉的行高与间距，玩家名字重和字距也按源设置 |
| 普通卡片 | 135° `#ffffffec → #ffffffab`，22px，`0 6px 22px #4b5d8520` | 通过共享卡片 helper 绘制；特殊纸面、徽章、展示卡继续使用各自参数 |
| 阅读弹窗背景 | `#34405c36` + backdrop blur9；modal 135° `#fcfdfff5 → #f5f3ffef`、r25、shadow `0 28px 90px #35416c42` | 先捕获当前已合成 viewport（包含 UI 和面板外背景），再分组应用 fade、blur、遮罩；普通 modal 宽440、reading 宽720 |

GPU 图层使用实际组合 alpha。进入动画不是逐个 primitive 乘 alpha，因此头像、阴影与半透明交叠区域跟随同一个组淡入。renderer 在必要时为 alpha 图层使用 GPU offscreen Surface，组内捕获能够看见刚画好的卡片渐变；alpha1 时直接 save/restore，不创建额外 copy。

采样范围受控：侧栏 `0,0,208,850`；身份卡 `236,174,996,276`；徽章使用其本地矩形；modal 的全屏遮罩按 `setViewportBounds` 捕获完整当前合成 viewport（只有此效果需要全屏）；toast 使用自身 ROI。称号卡的组合层也限定为单卡 ROI，不为每张卡复制整张 4K framebuffer。viewport 由 Minecraft screen 以 `(-panelX/scale,-panelY/scale,framebufferWidth/scale,framebufferHeight/scale)` 传入。

## 实际使用的动画与输入

| 效果 / 网页来源 | 原始参数 | 原生对应与行为 |
| --- | --- | --- |
| shell-enter / styles.css | 360ms，cubic-bezier(.2,.8,.2,1)，opacity0、Y12、scale.992 → 1/0/1 | `render` 外层真实 alpha + 同步 logical transform；阴影也在该动画组内 |
| 非主页 page-enter | 240ms，CSS ease(.25,.1,.25,1)，opacity0、Y5 → 1/0 | heading/body 使用同一进度；无额外 invented stagger |
| 主页分组 / welcome-splash.css | heading340ms ease-out；identity440ms delay50；lower440ms delay110；footer340ms delay170。中间两组 bezier(.2,.7,.2,1)，均由 Y12/opacity0 开始 | `group` 逐项使用对应曲线/时长/延迟；问候结束3060ms后才从主页重新开始计时 |
| 导航 intro / GlassIcons | 70ms 按项错开；enter spring k60/d15.5；pose 的 fade、swing、glyphRise、settle 直接依原公式 | `HubMotion.Navigation.enter` 与 renderer `iconPose`；开屏覆盖期间导航可在其后正常完成 intro |
| 导航 hover/press/aim | h k170/d24；p420/40；aim x/y90/19；240Hz子步、dt≤1/30 | 原数值积分；离开时 h、aim、press 均回零；不是鼠标移出立即复位 |
| 导航 lift/plate/parallax/light | liftY=-3.68h；angle=-16+10h；offset=(-3.68h,-3.68h)；aim shift=-2.093×pointer；light 随 aim 改变；glow=1+.25h | 同一 `IconPose` 驱动 shader 与 DOM 等价的 glyph 位移/scale/alpha |
| TiltedCard spring | perspective800；smoothness.6 → omega=20×.2^.6、zeta=.82；子步≤1/240、dt≤.05；lift zeta≥.85；press omega26/zeta.8 | `HubMotion.Tilt`；rx=-ny×amplitude、ry=nx×amplitude；使用 CSS 透视矩阵，未以 rotateZ 冒充双轴倾斜 |
| 首页 medallion props | amplitude10、scale1.04、glare.75、shadow.16、introtrue；figure 固定 -5° | 同一透视机制；初始 rx16°、ty22、opacity0，450ms smoother(6x⁵-15x⁴+10x³) + 对应 spring；每次重新进入主页重新初始化 |
| 称号卡 props | amplitude5、scale1.018、glare.5、shadow.12、introfalse；紧凑视图同机制 | 每个稳定称号 ID 保存 spring；press scale额外 -.02×press；selection 只负责预览 |
| TiltedCard glare / shadow | gx=50+ry/amplitude×46%，gy=50-rx/amplitude×46%；glare×max(lift,energy)；8个glare stop SCREEN混合、6个edge stop；shadow blur20、偏移/scale依源公式 | `tiltGlare` 接收归一化 gx/gy 后转实际坐标；阴影 sigma20 由 Gaussian helper 的 blur40 实现 |
| 手写 hello / WelcomeSplash.jsx | 完整20段主 cubic、2段下划线 cubic；stroke6.8/2.1、round cap/join；下划线alpha.58 | `Path.makeFromSVGString` + `PathMeasure.getSegment`，绘制真实 cubic；两个 path 各自使用完整 bounding box 的三 stop 渐变，没有按点画直线替代 |
| hello 节奏 | main delay120/duration1900/bezier(.32,.08,.35,1)；flourish delay1810/duration350/ease-out | 曲线反解 x 后获得真实 CSS easing 进度，再按路径实际长度截取 |
| hello 签名/退场 | PIXELMC 10px、tracking5；1550ms后500ms CSS ease，由Y5/opacity0进入；overlay2600ms后420ms ease-in-out退场；App在3060ms结束 opening | 签名为原字符串 + glyph tracking，未插空格；整张 welcome layer 一次 alpha 退场，然后主页卡片开始入场 |
| modal 进入 | backdrop180ms CSS ease；modal220ms bezier(.2,.8,.2,1)，Y9/scale.985/opacity0 | 遮罩与内容分别组合 alpha；modal hit 使用当前 matrix inverse |
| modal 关闭 | 网页直接 unmount，没有退场 keyframe | 原生立即关闭，不额外编造退出动画；Esc 优先关闭 modal，焦点归还触发控件 |
| toast | 240ms CSS ease，Y-12/opacity0进入；top25、r18、blur16；2800ms后直接 unmount | `toast` 使用对应进入参数；已移除旧版额外的300ms fade-out |
| 月份 body / heading | body260ms bezier(.18,.75,.22,1)，按方向±28px + fade；heading260ms ease-out、±28/3px | 控件不移动；只变换月份标题和 body，列表/月历共用；历史月优先选择最近收藏 |
| 按钮与图标按钮 | 源基础 transition140ms；primary hover Y-1、active Y1；subtle hover白；背景/边缘/阴影依 CSS 状态 | hover/leave 保留状态通道与 CSS reversing-shortening factor；gradient-image 按浏览器规则离散切换，不额外编造渐变插值；鼠标按下保留press，抬起才激活；禁用控件不注册 hit |
| toggle | background180ms ease；knob translate15px/180ms；33×18、knob14 | 数值与尺寸按源实现，保留当前开/关状态，关闭称号展示不摘下 |
| 签筒 hover | 350ms CSS ease；rotate-8° → -3°、Y-4 | 真实 transform transition；减少动态效果时固定 -8° |
| 日期/分段控件 | 日期 border/shadow160ms；普通按钮140ms；频率文字180ms | 独立 hover/selection 通道；未来日期不注册 hit，也不绘制淡签图 |
| 减少动态效果 | 跳过 hello、入场/翻月、tilt/press 与持续动态；保留静态导航造型与选中反馈 | springs snap静止值，CSS式状态反馈即时更新；恢复动效时按网页组件重挂行为重置 navigation/tilt；不会把“无动画”当成“控件不可用” |

`HubView` 保存纯 logical 变换栈，不把 Minecraft 物理缩放混入命中矩阵。每个 hit 保存当前 2D/透视矩阵并 inverse 鼠标位置；称号列表还检查**父坐标中的滚动 viewport**，被裁切部分不吞点击。频率 slider 在按下时保存 modal 的 hit matrix，拖动数值使用该矩阵反向映射。输入桥区分按下/抬起；Enter 按下激活，Space 抬起激活。`pointerCursor()` 依据变换后的 hit 返回 default / pointer / text。

## 字体、图标与状态

- 正文依 `Segoe UI Variable Text → Segoe UI → Microsoft YaHei UI`，CJK glyph 回退单独分 run；诗文依 `Noto Serif SC → Source Han Serif SC → SimSun → serif`。选择本机字体，不下载远程字体，不把本机专有字体打入 JAR；禁用伪粗体以对应 `font-synthesis:none`。
- 已接入真实 weight 与 glyph cluster tracking：主标题25/720/1px；名字35/700/-1px；导航14/550或700/.25px；称号卡20/600/1px；预览21/600/2px；纸面标题25/600/3px；纸面诗句13/1.6px/30.55px行高；reading标题29/600/4px，诗句16/2px/35.2px，解签15/1px/31.5px。
- 朝露行者使用95° `#168878 → #69a648`；拾星之人使用100° `#a97929 → #bf854a → #8a69bb`，通过字形 shader 实现 background-clip:text 等价效果，首页/卡片/佩戴预览/资料共用。其他称号保留原单色。
- lucide图标默认 stroke1.65；称号徽章使用1.5变体，preview-symbol使用1.0变体、118px、alpha.14。首页头部额外 roll=-2°；皮肤六面与独立透明帽层由共享 `HubHead` 提供。
- 统计、称号、签笺均明确为本地演示。关闭展示保留佩戴；选中不等于佩戴；未获得不能佩戴；未解不露结果；已解未归档不计收藏；手册展示不冒充游戏方块设置。

## 未启用的上游变体

当前网页没有使用 GlassSurface 任意 shape/blob、refraction=true 的 sidebar/identity、TiltedCard flip/backContent、floating、gyroscope、trackWindow 或 tooltip。导航只使用 tilt plate + lift hover；其他 plate/hover 组合不作为本轮验收缺项，也不扩展成额外产品功能。

## 验证与尚存边界

- 已完成 Java17 编译检查；第一轮 CPU raster 仅证明布局/输入样例可运行，不能验证本轮 GPU backdrop、RuntimeEffect 与真实游戏 framebuffer。
- 本轮由 renderer 在一个隔离的隐藏 GPU context 中检查 home、titles hover、fortunes、reading、settings；结果见下方记录。该检查不启动/控制 Minecraft，不替代用户游戏验收。
- 仍需用户确认实际游戏中的尺寸、真实世界/主菜单背景穿透、GUI/DPI缩放、鼠标光标、动画观感、兼容性与帧耗。没有执行真实游玩客户端验收。
- 原生布局仍是固定1260×850的直接实现，不运行 CSS layout/media-query 引擎；浏览器滚动条、line-box baseline、系统字体匹配/字重和 GPU 像素栅格化可能产生差异。本轮按实际源参数移植效果，**没有证据支持“全分辨率逐像素完全一致”**。
- 原生没有完整 CSS 引擎；本轮已显式实现过渡中途反转的 reversing-shortening factor、gradient-image 的离散状态切换，以及 color/shadow/transform 的同源 easing。尚未由真实浏览器与游戏逐帧做动画轨迹差分，不能把公式移植或静止截图称作动态逐像素相同。
- 普通 modal 宽度与reading列宽已按源修正；原生内容高度/文字基线通过显式布局与文本测量确定，仍需与用户观察到的最终网页排版核对。

### GPU 离屏记录

2026-11-04：在一个隔离的隐藏 OpenGL context 中执行本轮源代码检查，未启动或操控 Minecraft / 浏览器。使用1440×1000离屏画面、1260×850逻辑面板；spring 逐帧推进至稳定后采样，不能以一次时间跳跃代替积分。

| 检查 | 实际结果与证据 |
| --- | --- |
| 初轮五页 | `.runtime/native-previews/fidelity-pass2/{home,titles-hover,fortunes,reading,settings}-gpu.png`；无 GL 错误和 capture 预算告警；440px settings /720px reading 内容完整；透明面板能看见底纹；称号 hover 包括双轴透视与父 viewport 裁切 |
| 导航视觉审查后的修复 | 首图出现尖角导航，不能算玻璃对应完成。原因是 RuntimeEffectBuilder 的 int/float 重载误把整数 bit 写给 float radius/bevel uniform；改为显式 float 后，`fidelity-pass2-rounded-fix/home-gpu.png` 已恢复 source 的 radius15、bevel7、前层玻璃与高光，已实际查看回归图 |
| 小图标缩小采样 | 原五页中的12px calendar 图标曾损失水平线。GPU mipmaps + FAST full-image sampling 修正后，`fidelity-pass2-targeted-fixes/titles-calendar-mipmap-gpu.png` 日历轮廓/横线已恢复；只有 fortune-barrel / collection-chest 保持 nearest，fortune-slip / Logo / 其他普通图像使用 mipmap 缩小采样，头像由 HubHead 保持像素采样；已实际查看回归图 |
| 佩戴预览底座补齐 | 用户反馈旧底座仍是胶囊形，并提供网页佩戴预览作为参照。本次替换为双层椭圆、白边、外阴影与底部内阴影，头像底缘落在台面，说明文字依参照置于台沿下方。`.runtime/native-previews/pedestal-fix/titles-pedestal-gpu.png` 已自动生成并查看，无 GL 错误；这只确认隔离 GPU 画面，本次新包的游戏观感仍由用户复核 |
| 佩戴预览文字基线 | 用户已确认底座在游戏里正确，随后指出顶部文字偏高。隐藏浏览器测得同一网页226px预览的三行实际 baseline 为50 /76.5 /194.5px，原生据此修正称号、玩家名和说明文字；`.runtime/native-previews/preview-text-baseline-fix/titles-pedestal-gpu.png` 已查看，无 GL 错误。此次文字修正的游戏内验收仍交给用户 |
| 完整 viewport 遮罩 | probe传入 `setViewportBounds(-90,-75,1440,1000)`；`fidelity-pass2-targeted-fixes/reading-full-viewport-gpu.png` 确认面板外背景也被模糊及遮罩，modal 本体保持中央，未以原始 game snapshot 替代已绘制 UI；已实际查看回归图 |
| 字体匹配 | 当前主机实际匹配 Segoe UI Variable Text（400/700）、Microsoft YaHei UI（400）、Noto Serif SC（400/600）；这仅是该主机的匹配证据，不保证其他电脑一定安装相同字体 |
| 源码收口 | `HubView` /`HubMotion` 的最新 viewport、reverse-transition、gradient-image离散状态代码已编译；owned文件 `git diff --check` 无空白错误 |

以上证明限定于本轮源代码、这些 GPU 画面及该主机。静止画面不能验证完整 hover/leave 轨迹、真实 Minecraft GL 状态恢复、4K/DPI全组合和帧耗；游戏内验收由用户完成，不能把本记录改写为“游戏内通过”或“完全一致”。

### 重开面板的渲染状态修正

2026-11-04：用户在关闭游戏音乐后重新打开预览时发生原生进程崩溃。系统转储显示异常位于 NVIDIA OpenGL 工作线程，当时渲染线程在 Skija 的路径绘制中；没有 Java 异常，不能仅凭故障模块就判定为驱动自身缺陷，也未证实音乐设置是直接原因。

排查确认并修正了三处问题：补齐 `GL_PROGRAM_POINT_SIZE` 和受支持上下文的间接绘制缓冲绑定恢复；先展开嵌套透明图层再恢复根画布，确保异常时仍提交和恢复状态；关闭渲染器前重置 Skia 的状态缓存并显式同步 GPU，随后按依赖顺序释放资源。同步只发生在关闭时。Skia 自身析构也有同步，不能把此前缺少显式等待宣称为已证实的闪退根因。

新增 `NativeLifecycleChecks` 在修改前明确失败于 `Hub changed GL_PROGRAM_POINT_SIZE`，修正后 OpenGL 4.6 /3840×2054 的8次重开、嵌套图层异常恢复、游戏附件和状态保持、关闭清理均通过。原始闪退未在隐藏环境稳定复现；以上是已确认缺陷的修正与加固，原操作路径仍待用户在游戏内复核。界面布局、样式和动画参数未改动。
