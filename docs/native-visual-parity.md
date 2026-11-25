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
- 网页与显式本地预览的统计、称号、签笺均为演示；原生游戏内模式通过服务端玩家 API 读取真实数据，失败时不回退到演示。关闭展示保留佩戴；选中不等于佩戴；未获得不能佩戴；未解不露结果；已解未归档不计收藏；手册展示不冒充游戏方块设置。

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


### 0.2.4-dev：独立上下文兼容候选（2026-11-05）

0.2.3-dev 在实际多人整合包打开面板后出现原生访问异常；已知调用链是 `HubDraw.insetBoxShadow → Canvas.drawPath → nvoglv64.dll`。这只能定位出错阶段，尚不能确定是驱动缺陷或某个第三方模组导致。Java 异常捕获无法拦住这类原生进程崩溃。

新的 `HubGlContext` 在同一渲染线程创建隐藏、共享纹理的独立 OpenGL 上下文。Skija 的状态、VAO/FBO 与 LWJGL capability 表单独管理；游戏附件只读查询，Hub 仅绘制共享颜色纹理，另建自己的 stencil。两个方向都使用 GPU fence 和 flush；正常逐帧路径没有 CPU 画面回读，也不使用逐帧 glFinish。关闭时仍等待完成后释放资源。未退回在游戏上下文直接绘制的旧路径。

逐项全局 GL 状态恢复已退出生产绘制路径，因为它会覆盖其他渲染器的 indexed blend 状态。实际附件格式按 RGBA8、sRGB8-alpha8、RGBA16F、RGB10-A2 映射；未知格式会报告初始化错误并允许 Esc 返回。布局、字体、玻璃效果和动画参数未修改。

本机隐藏 GPU 检查通过：四种格式共 360 帧、双向共享纹理同步、独立 UBO/混合设置、实际深度写入、中文/衬线字体、GPU 背景模糊、异常恢复、4K 下八次重开和资源关闭。刷新保留缓存的像素比较与白色 Toast 动画检查通过。测试中发现本机布尔深度掩码查询在切换上下文后与整数查询不一致；回归检查改用整数查询，并用真实深度清除和回读独立确认写入掩码，未据查询结果推断实际写入。

这些检查不启动或控制 Minecraft，未重现完整整合包的原始闪退。0.2.4-dev 仍为修复候选，不能据此宣布生产兼容问题已解决；本轮只推送源码，不发布发行版或重新部署生产。

### 0.3.0-dev：更新日志原生接入与 Terra 对照（2026-11-05）

以下为本地源码对应修正；不是生产部署或真实 Minecraft 验收。绿色 Newspaper 导航、分类底板配色与填充点赞心形已先行修正，以下 23 项来自后续静态审查。

| 对照项 | 当前实现 |
| --- | --- |
| 1. 列表 hover / press | 未选中项 hover 使用 brightness .97，按下使用 .92；绘制隔离图层只改变该项 RGB，保留 alpha。 |
| 2. 独立弹簧选中框 | 选中框单独追踪位置/高度，omega30、zeta.78、最大1/240秒积分步，普通数据刷新不重置已有运动。 |
| 3. 列表方向键选择与滚入视野 | 上下键更改记录选择；Home/End 选中窗口首末项；按行测量位置滚入当前视口。 |
| 4. 搜索长文本与点击光标 | 接入独立单行编辑器，光标水平跟随、点击定位和拖选，选区复制/剪切由 Screen 路由。 |
| 5. 留言文本编辑 | 接入多行编辑器，支持局部选区、拖选、Shift/Control移动、上下行定位、剪贴板以及成对UTF16输入。 |
| 6. 长留言跟随光标 | 最大168逻辑像素输入区以 caret 确保可见；Ctrl+Home 可回首行；保留玩家手动滚动。 |
| 7. 文章退出后入场与数据身份 | selected 和 displayedId 分离；目标数据未就绪时保留旧记录；目标就绪后旧文章200ms上移5px淡出，再新文章200ms下方10px淡入；互动绑定显示ID且过渡时禁用。 |
| 8. 3px滚动条拖动及hover | 列表/正文独立拖动；轨道透明；hover 对齐 Web 的整个 viewport 命中与 #9aa7c273，常态 #9aa7c23d。 |
| 9. 菜单滚轮命中 | 只有鼠标位于整合包菜单边界时滚动菜单；文章区域仍由文章滚动处理。 |
| 10. 菜单外点击关闭 | pointerDown 在菜单和触发按钮以外关闭菜单，Escape也可关闭。 |
| 11. 点号日期 | 列表 MM.dd，正文/留言/回复 yyyy.MM.dd HH:mm，Asia/Shanghai，无秒。 |
| 12. 空摘要不预留行 | 仅非空摘要参与封面高度和绘制；空文本 normal measure为0。 |
| 13. 长版本换行与封面重排 | 版本标签保留全部文本并换行；category/pin空间不足时换到下一行，标题与时间随 kicker/摘要高度重排。 |
| 14. 最新标记身份 | 按 provider list.latestId / snapshot.list.latestId 标记，不再使用搜索结果第一行。 |
| 15. 标题数量不受搜索影响 | 分页total只控制查询结果，标题使用未过滤 counts.published；显式demo也返回未过滤counts。 |
| 16. 英文单词换行 | 使用Unicode行边界优先；无法容纳整词时才按码点拆；每行按实际富文本字重测量，保留粗体段。 |
| 17. 多段li只画一次bullet | listItem首段为bullet，后续段为listParagraph并保留缩进；嵌套列表继续按层级处理。 |
| 18. 留言锁与发送图标 | 隐私标签前绘制LockKeyhole，发送按钮绘制Send；Lucide/ISC素材沿同源生成。 |
| 19. 25px圆角头像底框 | 25x25、5px圆角裁切、#e9edf6底色、#a9b2cd22细框；内部复用自己的2D皮肤和独立透明帽子层。 |
| 20. ClickSpark即时点击坐标 | 点击未点赞按钮即在实际点击坐标产生7个四色星形粒子：radius23、duration480ms、source randomness；仅动画即时，点赞状态仍等权威回执。 |
| 21. 点赞数量独立动画 | 回执计数变化触发200ms从opacity.4/y3进入；同值回执不重播。 |
| 22. 删除hover及确认细节 | trash hover #b86f81，确认区域 #fcf3f5、8px圆角，取消/确认使用文本操作。 |
| 23. 发送按钮状态 | 发送含图标；normal/hover渐变、阴影、边线与inset高光按source；hover上移、press下移、禁用.45 alpha且不注册提交hit。 |

玩家侧更新日志、留言、回复及整合包菜单改为连续滚动按需加载，后端保持有界分页；Manager 管理端分页不变。窗口对账使用稳定 ID，刷新替换而非反复追加；较早回复前插保持阅读锚点，被逐出窗口可重新读取。普通同连接刷新保留旧画面，权威隐藏/撤回/停用才清除。新连接的磁盘缓存先经服务端确认，按 UUID 与稳定 Manager serverId 隔离；详细协议见 player-integration-contract.md。

Java 17 编译与完整 build 通过。输入检查 54 项及 1500 次混合操作、缓存检查 295 项、现有 nativeUpdatesChecks，以及 NativeTerraChecks 的 66 项聚焦断言通过。最后仅调整滚动条 viewport hover 范围与透明度，受影响的 66 项检查及完整 build 再次通过；没有重新宣称全部旧检查都在此微调后执行。输入接线中发现并修复 render 外编辑访问 frame-local HubDraw=null 的生命周期问题，现绑定 renderer-lifetime draw，并在 renderer 更换时失效布局。

聚焦检查覆盖双批列表删除/去重/锚点、103 个整合包续载及隐藏/更名、回复前插和重访、光标与选区、文章退出/进入/迟回标识、拖动细滚动条、菜单边界、日期/长版本/英文和多段列表、点击粒子与确认后计数动效。没有运行真实游戏。实际字体/DPI/GUI 缩放、当前玩家皮肤与帽子捕获、输入法候选框、完整整合包兼容性及动画观感仍由用户验收；1260×850 逻辑布局不等于 CSS 引擎，不保证跨机器逐像素一致。


### 0.3.1-dev：客户端启动预热与通用资源复用（2026-11-05）

本轮调整初始化时机及资源生命周期，保留 0.3.0-dev 的页面、字体选择规则、素材、配色、动画参数和服务器协议。启动后在正确的渲染线程上分阶段准备通用资源，使用独立的小离屏目标；不预绘真实 HubView、不改变 hello 展示记录、不写进游戏画面。关闭面板保留可复用的通用资源，释放当前游戏画面引用、临时文本和大绘制池；资源重载失效并安全重建，退出时完整释放。

玩家皮肤在会话可用后准备，沿用原版皮肤流程；使用独立 GL 状态中的完整像素读取边界，并以原始 RGBA 交给 Skija，避免打开面板时重复 PNG 编解码。跨玩家和资源重载清理旧皮肤；头像与帽层绘制规则不变。

分阶段与首个显示帧增加毫秒计时。构建及隔离 GPU/生命周期检查以本地 0.3.1-dev 交付记录为准；未启动或控制 Minecraft，未替换用户正在使用的 0.2.5-dev。隐藏冷/热路径数据只描述该测试设备与夹具，不证明实际整合包首次打开已经无卡顿，也不证明全尺寸逐像素相同。


### 0.3.1-dev 源码补充：复用菜单背景（2026-11-06）

有世界时先执行标准 `Screen.renderBackground(GuiGraphics)`，flush 后由 Hub 采样并合成；真实 HubScreen 不再调用低分辨率全屏 sceneBlur。当前 1.20.1 整合包由 Modern UI 的标准 Screen hook 处理菜单虚化和进入过渡，Hub 不反射调用其私有类，也不强依赖该模组。无此类效果模组时是原版暗化。标题预览仍绘制全景；面板玻璃及内部弹窗的局部模糊保持各自参数。

依据：本地 Forge 1.20.1 Screen 的 renderBackground 调用 fillGradient；本地 Modern UI 3.11.1.6 的 MixinScreen 将该调用转入 BlurHandler.drawScreenBackground。现有配置启用 blurEffect/blurWithBackground、blurRadius=7；未修改配置。此前 Hub 自有缩小采样可能导致边缘台阶，但未通过实际游戏测量确认，不能将此静态核对称为抗锯齿问题已验证修复。

全部 35 个主 Java 源码经 Java 17 和本地缓存依赖直接编译通过；标准 Gradle build 被本机 loopback 通信错误阻断，未完成新 JAR 打包。前次预热候选 JAR 不包含本次修改。未运行 Minecraft；菜单动画、外部模组兼容与实际虚化观感待用户验收。旧隐藏 GPU 夹具中的显式 sceneBlur 仅验证独立渲染器能力，不是本次游戏标准背景链的运行证明。

### 0.4.0-dev 排行榜原生接入与审查修复（2026-11-06）

逐项映射当前 LeaderboardPage.jsx 与 leaderboards.css。三榜标签、前三玩家玻璃卡、独立帽层头像、普通行数据条及本人位置均接入原生 Skija。普通行保留涨跌位数和并列标签，前三及本人栏突出名次。

数值过渡 460ms、条长 320ms、UUID 位置 420ms、名次文本 220ms。前三玩家新增 320ms 淡入/淡出和上下偏移，退出完成后释放旧位置，连续变化从当前插值继续。普通行首次进入视口淡入，初次展示按 25ms 错开，移除行有短退出。刷新只对账当前有界窗口并保留滚动。

Terra 审查的 1–7 项已落入本地修复。补入前三不足三人时的列表提示、本人已知未入榜数值、平滑定位和 1.5/1.6s 消退高亮、榜首提示、加载旋转及断线图标。皮肤图片 LRU 淘汰后，玩家重新进入可视区域可重新读取已注册纹理，保留有限尝试。

审查第 8 项经用户决定本轮跳过。原生继续使用全页统一缩放的 1260×850 逻辑画布，小窗口 CSS 重排属于明确保留的差异。头像面投影仍沿既有原生实现，实际皮肤、玻璃取样、DPI/GUI 缩放和游戏兼容性由用户实游验收。

本候选同时包含先前的启动预热和标准 Screen 背景接入。Java 17 完整构建及隐藏 GPU 检查已通过。独立 GPU 夹具与合成背景只用于自动回归。

## 2026-11-07 community native candidate

The Forge 1.20.1 candidate ports the approved public-profile modal and journey-memory reader. It retains ten background colors and six profile patterns, in-place biography editing, original-position title/fortune selection, a three-card memory carousel, bounded modal scroll, independent candidate rails, entrance/exit and expansion animations. Home and leaderboard identity links open profiles. Memory navigation uses the existing home entry and five-item sidebar.

The shared large-surface fill, border and clipping use the same continuous-corner path. Dark materials are mapped by the approved CSS roles, including low-alpha blue/violet glass highlights and warm dark fortune paper. The native light-theme 42-percent white frosted decoration is replaced with dark-specific sheen and a bounded edge rim. Themes are stored per player and server/save. Rich title glyph colors and source images remain literal.

3D head geometry uses one device-space 2x supersampled layer for base and hat. Skin texels retain nearest sampling. The pooled surface count is bounded and resources are released with the panel. Hidden checks cover 1080p/1440p and DPI 1/1.25/1.5, with internal seam and alpha-edge checks.

Verification uses actual compiled Gradle classes, real isolated-service DTOs, normal HubView mouse/keyboard dispatch and hidden GPU surfaces. Build receipts, targeted session checks, screenshots and source/artifact hashes are stored in the local `outputs/playerhub-native-community-20261107` handoff directory. These establish local integration evidence. Actual Minecraft/client rendering, resource packs, drivers and production deployment await user acceptance. The previously waived whole-app small-window responsive reflow stays outside this candidate.

Persisted archive cover presets are currently limited to the explicit defaults from Manager. The renderer supports the full four-pattern/six-palette artwork set for future metadata. Pack art selection in the administrator UI remains deferred.

## 2026-11-07 0.5.1-dev 视觉差异修复

本地 Forge 候选对齐本轮确认的十四项差异，并补齐主页旅程标签、两个链接按钮与最近签图的深色呈现。共享颜色根因已由回归确认：连续圆角在局部路径绘制前平移画布，而渐变已定义在调用者坐标中，导致着色器再次偏移。HubCorners 现直接绘制有界缓存的绝对坐标路径；fill、clip、阴影、glare 及玻璃位移图共用连续轮廓。旧版同坐标普通／连续圆角渐变检查失败，新版通过，不通过整体提高饱和度补偿。

页面逐项对应 CSS farthest-corner 径向渐变、背景层叠与深色覆盖。称号各色卡面、佩戴预览、排行金／蓝紫／青卡面和光晕、主页身份与今日签恢复层次；日历、纸面装饰、按钮高光和图标按当前 Web 调整。设置采用右侧紧凑日／月分段，小时与分钟数字采用同一层级。真实最近收藏保持实际类型与排序，不拿 Web 样例图标替换真实记录。

资料卡白色／近白富文本在浅色下增加细轮廓，保留原始填色、逐字渐变及粗斜／划线，不加底块。此为真实白字 DTO 的可读性处理，Web 彩色样例未覆盖该数据情形。

Windows 通过后台只读 SPI 获取系统动画偏好。首次或过期查询完成后才决定打开面板与欢迎动画，请求按会话和屏幕身份失效。本地开关独立保存，有效值为本地或系统；正常系统保留原欢迎频率。其他平台当前使用本地开关，未宣称跨平台系统偏好均已实现。

完整构建、偏好／渲染隔离／生命周期／排行回归、五页浅深截图、资料与实际隔离服务 DTO 输入检查已执行。完整回执和源码摘要在私有本地 outputs/hub-parity-fixes-20261107 交接目录。没有启动游戏、部署或发布；截图来自隐藏 GPU，不代替实际整合包场景、字体、DPI 和输入验收。全局小窗口重排仍按用户豁免保留。


## 问舟原生导航动效

Forge 1.20.1 的 Hub 0.5.4-dev 移出原生导航的共用绘制函数。问舟 0.1.6-dev 在最后一个可见导航项之后注册入口，使用原生的悬停缓动、指针视差、玻璃图标入场、键盘焦点和点击区域。手册打开期间收到确认的授权时，问舟入口从自身的起始状态出现。禁用「更新日志」时沿用实际可见的顺序。减少动态效果时入口直接显示。

问舟页面调用 Hub 的按钮悬停与位移时序。从手册进入问舟时，动画中的页面点击区域跟随画面。客户端和专用端 Mixin 检查及浅色、深色离屏 GPU 检查已通过，实际游戏体验待验证。

## 冬日驿站

冬日驿站和深浅色独立选择，偏好按玩家及服务器／存档隔离。旧偏好沿用经典主题。原生 HUB 使用与 Web 定稿相同的驿站原画、徽章、纹饰与纸张。首页按 1260 × 850 逻辑画布排版，跟随游戏面板缩放。玩家头像、称号、签文、公告、在线同伴和点击区域均由原生界面绘制。管理器提供的当前存档公告、共同存档数为可选增强摘要，未提供时保留已知数据。

冬季开屏以原画的拱窗、关灯／亮灯图层、14 片雪、4 组足印、标题和织纹分别合成。760 ms 展开拱窗，570 ms 开始亮灯，920 ms 淡入标题，2600 ms 开始向主页淡出。开启减少动态效果时跳过开屏。问舟的季节外观由配套客户端扩展渲染。

### 冬日收藏与社区页面

本轮使用 Web 定稿的称号徽章、旅行签纸、雪窗印刷间、排行榜雪景和旅程地图。目录以 186 × 848 的逻辑尺寸绘制，高分辨率底图保存封面、布纹和桥景。六个入口从同一图集取样，选中丝带、存档信息、服务器图标及命中区域由客户端绘制。开屏结束后，各入口间隔 82 毫秒开始 360 毫秒的淡入和平移。减少动态效果时立即显示。

称号页使用六枚一页的收藏匣，签笺页使用月历和阅读页。排行榜按皮肤纹理及手臂模型计算领奖姿势。界面设置的频率拖动使用绘制滑轨的同一组坐标。公开资料和旅程数据沿用已确认的玩家响应。游戏标题菜单没有服务器连接，合成的社区数据由独立回归程序提供。问舟延续原有页面结构，冬日样式作用于发送、工具、提醒和浮层。
