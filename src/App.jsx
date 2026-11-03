import React, { useState, useEffect, useRef } from "react";
import GlassIcons from "./vendor/react-bits/GlassIcons.jsx";
import GlassSurface from "./vendor/react-bits/GlassSurface.jsx";
import TiltedCard from "./vendor/react-bits/TiltedCard.jsx";
import PlayerHead from "./PlayerHead.jsx";
import WelcomeSplash from "./WelcomeSplash.jsx";
import WelcomeFrequency, { shouldShowWelcome, welcomeModes } from "./WelcomeFrequency.jsx";
import { titleIcons } from "./title-icons.js";
import {
  Home,
  Tag,
  BookOpen,
  Settings,
  ChevronRight,
  ChevronLeft,
  ArrowUpRight,
  ArrowRight,
  Check,
  X,
  Leaf,
  Clock,
  LogIn,
  CalendarDays,
  Search,
  SlidersHorizontal,
  Grid2X2,
  List,
  Eye,
  EyeOff,
  MoreHorizontal,
  Star,
  Mountain,
  Moon,
  Sun,
  Wheat,
  Cloud,
  House,
  Lock,
  Sparkles,
  PanelLeftClose,
  Volume2,
  RotateCcw,
  Maximize2,
  Info,
  Copy,
  CheckCheck,
  ChevronsUpDown,
  Bookmark,
  Gift,
  Flower2,
  Wind,
} from "lucide-react";
import {
  player,
  titles,
  archive,
  todayKey,
  TODAY,
  fortuneById,
  dateKey,
  gradeColor,
  stateLabels,
} from "./data.js";

const storageKey = "pixelmc-player-hub-preview-v1";
function initial() {
  const defaults = {
    equipped: "dew",
    visible: true,
    fortuneState: "archived",
    displayDate: todayKey,
    reducedMotion: false,
    welcomeMode: "always",
    hasSeenWelcome: false,
  };
  try {
    const saved = { ...defaults, ...JSON.parse(localStorage.getItem(storageKey) || "{}") };
    if (!welcomeModes.some((mode) => mode.id === saved.welcomeMode)) saved.welcomeMode = defaults.welcomeMode;
    saved.hasSeenWelcome = saved.hasSeenWelcome === true;
    return saved;
  } catch {
    return defaults;
  }
}
function Symbol({ name, ...props }) {
  const C = titleIcons[name] || Leaf;
  return <C {...props} />;
}
function Avatar({ large = false }) {
  return (
    <div className={`avatar ${large ? "large" : ""}`}>
      <PlayerHead name={player.name} />
      <span className="avatar-status" />
    </div>
  );
}
function TitleText({ title, small = false }) {
  return (
    <span
      className={`title-text ${title?.style || ""} ${small ? "small" : ""}`}
    >
      {title?.name || "暂未佩戴称号"}
    </span>
  );
}
function IconButton({ label, children, onClick, className = "", ...props }) {
  return (
    <button
      className={`icon-btn ${className}`}
      aria-label={label}
      title={label}
      onClick={onClick}
      {...props}
    >
      {children}
    </button>
  );
}
function Toggle({ checked, onChange, label }) {
  return (
    <button
      type="button"
      className={`toggle ${checked ? "on" : ""}`}
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
    >
      <span />
    </button>
  );
}
function Pill({ children, color = "green" }) {
  return (
    <span className={`pill ${color}`}>
      <span className="pill-dot" />
      {children}
    </span>
  );
}

function Modal({ title, subtitle, onClose, children, wide = false }) {
  const ref = useRef();
  useEffect(() => {
    const prev = document.activeElement;
    const root = ref.current;
    const focusable = () => [
      ...root.querySelectorAll(
        'button:not(:disabled),input,select,[tabindex="0"]',
      ),
    ];
    focusable()[0]?.focus({ preventScroll: true });
    const onKey = (e) => {
      if (e.key === "Escape") {
        e.stopPropagation();
        onClose();
      }
      if (e.key === "Tab") {
        const all = focusable();
        const first = all[0],
          last = all.at(-1);
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last?.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first?.focus();
        }
      }
    };
    root.addEventListener("keydown", onKey);
    return () => {
      root.removeEventListener("keydown", onKey);
      prev?.focus({ preventScroll: true });
    };
  }, []);
  return (
    <div
      className="modal-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <section
        ref={ref}
        className={`modal ${wide ? "wide" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <header className="modal-head">
          <div>
            <h2>{title}</h2>
            {subtitle && <p>{subtitle}</p>}
          </div>
          <IconButton label="关闭弹窗" onClick={onClose}>
            <X size={19} />
          </IconButton>
        </header>
        {children}
      </section>
    </div>
  );
}
function FortuneReading({ fortune, onClose, displayDate, onDisplay }) {
  return (
    <Modal
      title="签笺详情"
      subtitle={`${fortune.date.replaceAll("-", "/")} · ${player.name} 所得`}
      onClose={onClose}
      wide
    >
      <div className="reading">
        <div className="reading-paper">
          <div className="paper-corner tl" />
          <div className="paper-corner br" />
          <span
            className="fortune-grade"
            style={{ color: gradeColor(fortune.grade) }}
          >
            第{fortune.id}签 · {fortune.grade}
          </span>
          <h3>{fortune.title}</h3>
          <div className="poem">
            {fortune.poem.map((s) => (
              <p key={s}>{s}</p>
            ))}
          </div>
          <div className="paper-stamp">签</div>
          <span className="paper-foot">DAILY FORTUNES</span>
        </div>
        <div className="reading-interpretation">
          <span className="section-eyebrow">
            <Leaf size={15} /> 解签
          </span>
          <p>{fortune.interpretation}</p>
          <div className="reader-note">
            <Bookmark size={16} />
            <span>
              {fortune.status === "archived"
                ? "已收藏于你的藏签匣"
                : "已解签，尚未收藏"}
              <br />
              <small>
                {fortune.status === "archived"
                  ? "签笺一经收藏，便成为旅途的一部分。"
                  : "在游戏中打开藏签匣，即可收存这张签。"}
              </small>
            </span>
          </div>
          <button
            className="btn primary"
            disabled={fortune.status !== "archived"}
            onClick={() => onDisplay(fortune.date)}
          >
            <Sparkles size={16} />
            {fortune.status !== "archived"
              ? "收藏后可设为展示"
              : displayDate === fortune.date
                ? "当前展示的签笺"
                : "设为手册展示"}
          </button>
        </div>
      </div>
    </Modal>
  );
}

export default function App() {
  const [page, setPage] = useState("home");
  useEffect(() => { location.hash = "/home"; }, []);
  const [state, setState] = useState(initial);
  const [systemReduced, setSystemReduced] = useState(() => window.matchMedia("(prefers-reduced-motion: reduce)").matches);
  useEffect(() => {
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const sync = () => setSystemReduced(media.matches);
    media.addEventListener("change", sync);
    return () => media.removeEventListener("change", sync);
  }, []);
  const reduceMotion = state.reducedMotion || systemReduced;
  const [modal, setModal] = useState(null);
  const [reading, setReading] = useState(null);
  const [toast, setToast] = useState(null);
  const [closed, setClosed] = useState(false);
  // One page load represents one login in this browser-only prototype.
  const seenThisLogin = useRef(false);
  const [opening, setOpening] = useState(() => shouldShowWelcome(state.welcomeMode, {
    reducedMotion: reduceMotion, seenThisLogin: false, seenBefore: state.hasSeenWelcome,
  }));
  useEffect(() => {
    if (closed || reduceMotion) {
      setOpening(false);
      return;
    }
    if (!opening) return;
    seenThisLogin.current = true;
    setState((current) => current.hasSeenWelcome ? current : { ...current, hasSeenWelcome: true });
    const timer = setTimeout(() => setOpening(false), 3060);
    return () => clearTimeout(timer);
  }, [closed, reduceMotion, opening]);
  const notify = (text) => setToast({ text, key: Date.now() });
  const update = (patch) => setState((s) => ({ ...s, ...patch }));
  const equipped = titles.find((t) => t.id === state.equipped);
  const todayFortune = {
    ...fortuneById(1),
    date: todayKey,
    time: "20:26",
    status: state.fortuneState,
  };
  const archivedFortunes = Object.fromEntries(
    Object.entries(archive).filter(
      ([date]) => date !== todayKey || state.fortuneState === "archived",
    ),
  );
  const latestFortune = Object.values(archivedFortunes).at(-1);
  const navigate = (p) => {
    setPage(p);
    location.hash = "/" + p;
    setClosed(false);
  };
  const openHandbook = () => {
    setPage("home");
    location.hash = "/home";
    setOpening(shouldShowWelcome(state.welcomeMode, {
      reducedMotion: reduceMotion, seenThisLogin: seenThisLogin.current, seenBefore: state.hasSeenWelcome,
    }));
    setClosed(false);
  };
  useEffect(() => {
    try { localStorage.setItem(storageKey, JSON.stringify(state)); } catch { /* Keep in-memory preferences when storage is unavailable. */ }
    document.documentElement.dataset.motion = reduceMotion
      ? "reduced"
      : "full";
  }, [state, reduceMotion]);
  useEffect(() => {
    if (toast) {
      const timer = setTimeout(() => setToast(null), 2800);
      return () => clearTimeout(timer);
    }
  }, [toast]);
  useEffect(() => {
    const f = () =>
      setPage(
        ["titles", "fortunes"].includes(location.hash.slice(2))
          ? location.hash.slice(2)
          : "home",
      );
    window.addEventListener("hashchange", f);
    return () => window.removeEventListener("hashchange", f);
  }, []);
  useEffect(() => {
    const f = (e) => {
      if (e.key === "Escape" && !e.repeat && !modal && !reading) {
        e.preventDefault();
        if (closed) openHandbook();
        else setClosed(true);
      }
    };
    window.addEventListener("keydown", f);
    return () => window.removeEventListener("keydown", f);
  }, [modal, reading, closed, reduceMotion, state.welcomeMode, state.hasSeenWelcome]);
  function equip(title) {
    update({ equipped: title.id });
    notify(`已佩戴「${title.name}」`);
  }
  function readToday() {
    if (["read", "archived"].includes(state.fortuneState))
      setReading(todayFortune);
    else setModal("fortune-help");
  }
  return (
    <div className="preview-stage">
      <div className="ambient ambient-one" />
      <div className="ambient ambient-two" />
      <div className="preview-caption">
        <span className="caption-brand">PIXELMC</span>
        <span>PLAYER EXPERIENCE</span>
      </div>
      {!closed ? (
        <div className={`app-shell welcome-shell ${opening ? "is-opening" : ""}`}>
          {opening && <WelcomeSplash />}
          <aside className="sidebar" inert={opening}>
            <GlassSurface width="100%" height="100%" borderRadius={0}
              borderWidth={0.08} brightness={65} opacity={0.9} blur={12} displace={0.5}
              backgroundOpacity={0.32} saturation={1.15} distortionScale={-12}
              redOffset={0} greenOffset={0} blueOffset={0} refraction={false} className="sidebar-material" />
            <button
              className="brand"
              onClick={() => navigate("home")}
              aria-label="旅人手册首页"
            >
              <img className="server-brand-logo" src="/assets/server-logo.png" alt="PixelMC 服务器 Logo" />
              <span>
                <strong>旅人手册</strong>
                <small>PixelMC Player Hub</small>
              </span>
            </button>
            <div className="nav-label">我的空间</div>
            <nav aria-label="主导航">
              <GlassIcons
                key={String(reduceMotion)}
                items={[
                  { id: "home", label: "个人主页", icon: <Home />, color: "blue" },
                  { id: "titles", label: "我的称号", icon: <Tag />, color: "purple" },
                  { id: "fortunes", label: "我的签笺", icon: <BookOpen />, color: "orange" },
                ].map(item => ({ ...item, onClick: () => navigate(item.id), current: page === item.id,
                  className: page === item.id ? "is-current" : "" }))}
                size={46} gap={28} columns={1} labels="always" theme="light"
                plate="tilt" tilt={-16} roundness={0.65} shine={0.9} frost={0.1}
                tint="#ffffff" tintOpacity={0.16}
                hover={reduceMotion ? "none" : "lift"} parallax={reduceMotion ? 0 : 0.65}
                intro={!reduceMotion} reducedMotion={reduceMotion} className="hub-navigation"
              />
            </nav>
            <div className="sidebar-bottom">
              <div className="server-card">
                <span className="server-icon">
                  <Flower2 size={21} />
                </span>
                <div>
                  <strong>香草纪元</strong>
                  <small>
                    <i /> 当前存档 · 演示
                  </small>
                </div>
              </div>
              <button
                className="nav-item settings-nav"
                onClick={() => setModal("settings")}
              >
                <Settings size={19} />
                <span>界面设置</span>
              </button>
            </div>
          </aside>
          <div className="workspace" inert={opening}>
            <header className="topbar">
              <span className="topbar-path">
                我的空间 <ChevronRight size={13} />{" "}
                <strong>
                  {
                    {
                      home: "个人主页",
                      titles: "我的称号",
                      fortunes: "我的签笺",
                    }[page]
                  }
                </strong>
              </span>
              <div className="top-actions">
                <span className="connected">
                  <span /> 本地预览
                </span>
                <span className="top-divider" />
                <IconButton className="mobile-settings" label="界面设置" onClick={() => setModal("settings")}>
                  <Settings size={18} />
                </IconButton>
                <IconButton label="收起手册" onClick={() => setClosed(true)}>
                  <X size={19} />
                </IconButton>
              </div>
            </header>
            <main className={`page-content page-${page}`} key={`${page}-${opening ? "opening" : "ready"}`}>
              {page === "home" && (
                <>
                  <div className="page-heading">
                    <div>
                      <h1>个人主页</h1>
                      <p>很高兴又见到你，今天是旅程的第 18 天。</p>
                    </div>
                    <div className="date-label">
                      <Sun size={18} />
                      <span>11 月 3 日，星期二</span>
                    </div>
                  </div>
                  <section className="identity-card">
                    <GlassSurface width="100%" height="auto" borderRadius={22}
                      borderWidth={0.04} brightness={65} opacity={0.7} blur={8}
                      backgroundOpacity={0.09} saturation={1.25} distortionScale={-45}
                      redOffset={0} greenOffset={0} blueOffset={0} refraction={false} className="identity-glass">
                    <div className="identity-main">
                      <div className="identity-copy">
                        <div className="identity-greeting">你好</div>
                        <h2>
                          {player.name}
                          <Pill>在线</Pill>
                        </h2>
                        <div className="equipped-line">
                          <TitleText title={equipped} />
                          {equipped && !state.visible && (
                            <span className="hidden-indicator">
                              <EyeOff size={13} />
                              展示已隐藏
                            </span>
                          )}
                        </div>
                      </div>
                      <button
                        className="btn subtle profile-btn"
                        onClick={() => setModal("profile")}
                      >
                        个人资料
                        <ArrowUpRight size={15} />
                      </button>
                      <div className="identity-decoration">
                        <span className="medallion-ring" />
                        <TiltedCard containerHeight="160px" imageHeight="160px" imageWidth="190px"
                          containerWidth="190px" rotateAmplitude={reduceMotion ? 0 : 10}
                          scaleOnHover={reduceMotion ? 1 : 1.04} glare={0.75} shadow={0.16}
                          gyroscope={false} intro={!reduceMotion} reducedMotion={reduceMotion}
                          showTooltip={false} radius={26} className="player-medallion">
                          <Avatar large />
                          <span className="journey-badge"><Sparkles size={13} /> 旅程第 18 天</span>
                        </TiltedCard>
                        <span className="identity-spark spark-a">✦</span>
                        <span className="identity-spark spark-b">✧</span>
                      </div>
                    </div>
                    <div className="stats-row">
                      <div>
                        <span className="stat-icon">
                          <Clock size={19} />
                        </span>
                        <div>
                          <small>累计在线</small>
                          <strong>
                            {player.hours}
                            <em>小时</em>
                            {player.minutes}
                            <em>分钟</em>
                          </strong>
                        </div>
                      </div>
                      <div>
                        <span className="stat-icon">
                          <LogIn size={19} />
                        </span>
                        <div>
                          <small>登录次数</small>
                          <strong>
                            {player.logins}
                            <em>次</em>
                          </strong>
                        </div>
                      </div>
                      <div>
                        <span className="stat-icon">
                          <CalendarDays size={19} />
                        </span>
                        <div>
                          <small>上次登录</small>
                          <strong className="date-value">
                            <span>{player.lastLogin.split(" ")[0]}</span>
                            <span className="last-login-time">{player.lastLogin.split(" ")[1]}</span>
                          </strong>
                        </div>
                      </div>
                    </div>
                    </GlassSurface>
                  </section>
                  <div className="home-lower">
                    <section className="card today-card">
                      <div className="card-title">
                        <h2>
                          <Leaf size={19} />
                          今日签笺
                        </h2>
                        <span className="quiet-label">每日一签</span>
                      </div>
                      {["read", "archived"].includes(state.fortuneState) ? (
                        <>
                          <div className="today-body">
                            <div className="today-words">
                              <span className="fortune-grade">
                                {todayFortune.grade}
                                <span> · </span>第{todayFortune.id}签
                              </span>
                              <h3>{todayFortune.title}</h3>
                              <div className="today-poem">
                                {todayFortune.poem.slice(0, 2).map((s) => (
                                  <p key={s}>{s}</p>
                                ))}
                              </div>
                            </div>
                            <div className="fortune-art">
                              <div className="art-orbit" />
                              <img
                                src="/assets/fortune-barrel.png"
                                alt="繁茂枝叶环绕的木质签筒"
                              />
                              <span className="art-spark one">✦</span>
                              <span className="art-spark two">✧</span>
                            </div>
                          </div>
                          <div className="card-bottom">
                            <span className="collected-status">
                              <CheckCheck size={15} />
                              {stateLabels[state.fortuneState]}
                            </span>
                            <button className="btn primary" onClick={readToday}>
                              查看签文
                              <ArrowRight size={16} />
                            </button>
                          </div>
                        </>
                      ) : (
                        <>
                          <div className="today-body">
                            <div className="today-words">
                              <span className="fortune-grade">今日机缘</span>
                              <h3>
                                {state.fortuneState === "undrawn"
                                  ? "有一签，等你开启"
                                  : "签已入手，静候解读"}
                              </h3>
                              <p className="pending-description">
                                {state.fortuneState === "undrawn"
                                  ? "拿起签筒，为今天留下一份期许。"
                                  : "带上未解的签，前往解签龛。"}
                              </p>
                            </div>
                            <div className="fortune-art">
                              <img
                                src="/assets/fortune-barrel.png"
                                alt="签筒"
                              />
                            </div>
                          </div>
                          <div className="card-bottom">
                            <span className="collected-status">
                              {stateLabels[state.fortuneState]}
                            </span>
                            <button className="btn primary" onClick={readToday}>
                              {state.fortuneState === "undrawn"
                                ? "如何抽签"
                                : "如何解签"}
                              <ArrowRight size={16} />
                            </button>
                          </div>
                        </>
                      )}
                    </section>
                    <section className="card keepsakes">
                      <div className="card-title">
                        <h2>
                          <Bookmark size={18} />
                          最近收藏
                        </h2>
                        <span className="quiet-label">旅途的印记</span>
                      </div>
                      <button
                        className="keepsake-row"
                        onClick={() => setReading(latestFortune)}
                      >
                        <span className="keepsake-icon fortune-mini">
                          <img src="/assets/fortune-slip.png" alt="" />
                        </span>
                        <span className="keepsake-text">
                          <strong>
                            {latestFortune.title}
                            <em
                              style={{ color: gradeColor(latestFortune.grade) }}
                            >
                              {latestFortune.grade}
                            </em>
                          </strong>
                          <small>
                            收藏签笺 ·{" "}
                            {latestFortune.date.slice(5).replace("-", "/")}{" "}
                            {latestFortune.time}
                          </small>
                        </span>
                        <ChevronRight size={16} />
                      </button>
                      <button
                        className="keepsake-row"
                        onClick={() => navigate("titles")}
                      >
                        <span className="keepsake-icon green">
                          <Leaf size={23} />
                        </span>
                        <span className="keepsake-text">
                          <strong>朝露行者</strong>
                          <small>获得新称号 · 11/02 18:42</small>
                        </span>
                        <ChevronRight size={16} />
                      </button>
                      <button
                        className="keepsake-row"
                        onClick={() => navigate("titles")}
                      >
                        <span className="keepsake-icon lavender">
                          <Star size={22} />
                        </span>
                        <span className="keepsake-text">
                          <strong>拾星之人</strong>
                          <small>获得新称号 · 10/30 21:16</small>
                        </span>
                        <ChevronRight size={16} />
                      </button>
                      <div className="collection-totals">
                        <span>
                          <strong>6</strong> 枚称号
                        </span>
                        <span className="vline" />
                        <span>
                          <strong>
                            {Object.keys(archivedFortunes).length}
                          </strong>{" "}
                          张签笺
                        </span>
                        <button
                          onClick={() => navigate("fortunes")}
                          aria-label="打开签笺月历"
                        >
                          <ArrowUpRight size={16} />
                        </button>
                      </div>
                    </section>
                  </div>
                  <div className="page-footnote">
                    <span>
                      <Leaf size={13} /> 每一份收藏，都记得来时的路。
                    </span>
                    <span>统计范围：当前存档</span>
                  </div>
                </>
              )}
              {page === "titles" && (
                <TitlesPage
                  state={state}
                  update={update}
                  equip={equip}
                  notify={notify}
                  reduceMotion={reduceMotion}
                />
              )}
              {page === "fortunes" && (
                <FortunesPage
                  archive={archivedFortunes}
                  state={state}
                  setReading={setReading}
                  update={update}
                  notify={notify}
                />
              )}
            </main>
          </div>
        </div>
      ) : (
        <div className="closed-state">
          <span className="brand-mark">
            <span />
          </span>
          <h1>旅途仍在继续</h1>
          <p>这里是旅人手册的本地交互预览。</p>
          <button className="btn primary" onClick={openHandbook}>
            打开旅人手册
            <ArrowRight size={16} />
          </button>
          <span>
            <kbd>Esc</kbd> 也可以重新打开
          </span>
        </div>
      )}
      <div className="preview-footer">
        <span>
          <kbd>Esc</kbd> {closed ? "打开手册" : "收起面板"}
        </span>
        <span>本地交互预览 · 演示数据</span>
        <button onClick={() => setModal("preview")}>
          <SlidersHorizontal size={13} />
          预览选项
        </button>
      </div>
      {toast && (
        <div key={toast.key} className="toast" role="status">
          <span>
            <Check size={15} />
          </span>
          {toast.text}
        </div>
      )}
      {reading && (
        <FortuneReading
          fortune={reading}
          onClose={() => setReading(null)}
          displayDate={state.displayDate}
          onDisplay={(date) => {
            update({ displayDate: date });
            notify("已更新手册展示的签笺");
          }}
        />
      )}
      {modal === "profile" && (
        <Modal
          title="个人资料"
          subtitle="你在这段旅程中留下的足迹"
          onClose={() => setModal(null)}
        >
          <div className="profile-modal">
            <Avatar large />
            <h3>{player.name}</h3>
            <TitleText title={equipped} />
            <dl>
              <div>
                <dt>首次加入</dt>
                <dd>{player.joined}</dd>
              </div>
              <div>
                <dt>累计在线</dt>
                <dd>128 小时 42 分钟</dd>
              </div>
              <div>
                <dt>累计登录</dt>
                <dd>36 次</dd>
              </div>
              <div className="uuid-row">
                <dt>
                  玩家 UUID <small>演示身份</small>
                </dt>
                <dd>
                  {player.uuid}
                  <IconButton
                    label="复制 UUID"
                    onClick={async () => {
                      try {
                        await navigator.clipboard.writeText(player.uuid);
                        notify("UUID 已复制");
                      } catch {
                        notify("浏览器未允许复制，请手动选择 UUID");
                      }
                    }}
                  >
                    <Copy size={14} />
                  </IconButton>
                </dd>
              </div>
            </dl>
          </div>
        </Modal>
      )}
      {modal === "settings" && (
        <Modal
          title="界面设置"
          subtitle="让手册更符合你的习惯"
          onClose={() => setModal(null)}
        >
          <div className="settings-content">
            <WelcomeFrequency value={state.welcomeMode} onChange={(welcomeMode) => update({ welcomeMode })}
              reducedMotion={reduceMotion} />
            <div className="setting-row">
              <div>
                <strong>减少动态效果</strong>
                <p>关闭开屏动画、卡片倾斜与页面过渡</p>
              </div>
              <Toggle
                checked={state.reducedMotion}
                onChange={(v) => update({ reducedMotion: v })}
                label="减少动态效果"
              />
            </div>
            <div className="setting-row">
              <div>
                <strong>界面主题</strong>
                <p>Liquid Glass · 浅色</p>
              </div>
              <span className="theme-swatch">
                <Check size={14} />
              </span>
            </div>
            <div className="settings-note">
              <Info size={15} />
              <span>设置会保存在当前浏览器中。</span>
            </div>
          </div>
        </Modal>
      )}
      {modal === "preview" && (
        <Modal
          title="预览选项"
          subtitle="仅用于体验不同状态，不会修改游戏数据"
          onClose={() => setModal(null)}
        >
          <div className="settings-content">
            <label className="field-label" htmlFor="fortune-state">
              今日抽签状态
            </label>
            <select
              id="fortune-state"
              value={state.fortuneState}
              onChange={(e) => update({ fortuneState: e.target.value })}
            >
              {Object.entries(stateLabels).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
            <p className="preview-note">
              称号佩戴、显示设置和手册签笺展示都是本地模拟。抽签、解签、存签仍保留游戏内物品与方块交互。
            </p>
            <button
              className="btn subtle"
              onClick={() => {
                update({
                  equipped: "dew",
                  visible: true,
                  fortuneState: "archived",
                  displayDate: todayKey,
                });
                notify("已还原演示状态");
              }}
            >
              <RotateCcw size={15} />
              还原演示状态
            </button>
          </div>
        </Modal>
      )}
      {modal === "fortune-help" && (
        <Modal
          title={
            state.fortuneState === "undrawn"
              ? "用签筒开启今日一签"
              : "前往解签龛"
          }
          onClose={() => setModal(null)}
        >
          <div className="fortune-help">
            <img src="/assets/fortune-barrel.png" alt="签筒" />
            <p>
              {state.fortuneState === "undrawn"
                ? "在游戏中手持签筒，右键抽取今天的签。"
                : "在游戏中拿着未解签，对解签龛使用，即可读到签文。"}
            </p>
            <small>手册会在操作完成后更新你的签笺状态。</small>
            <button className="btn primary" onClick={() => setModal(null)}>
              知道了
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}

function TitlesPage({ state, update, equip, notify, reduceMotion }) {
  const [selected, setSelected] = useState(state.equipped || "dew");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("owned");
  const [sort, setSort] = useState("recent");
  const [compact, setCompact] = useState(false);
  const t = titles.find((t) => t.id === selected) || titles[0];
  const list = titles
    .filter(
      (t) =>
        (filter === "all" || (filter === "owned" ? t.owned : !t.owned)) &&
        `${t.name}${t.category}`.includes(query.trim()),
    )
    .sort((a, b) =>
      sort === "name"
        ? a.name.localeCompare(b.name, "zh-Hans-CN-u-co-pinyin")
        : String(b.date || "").localeCompare(a.date || ""),
    );
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>
            我的称号<span className="heading-count">{titles.filter(item => item.owned).length}</span>
          </h1>
          <p>选一个喜欢的称号，让名字多一点自己的颜色。</p>
        </div>
        <span className="module-label">MemorialTitles</span>
      </div>
      <div className="titles-layout">
        <section className="titles-browser">
          <div className="browser-toolbar">
            <div className="segmented">
              {[
                ["owned", "已拥有"],
                ["all", "全部"],
                ["locked", "未获得"],
              ].map(([id, label]) => (
                <button
                  className={filter === id ? "selected" : ""}
                  key={id}
                  aria-pressed={filter === id}
                  onClick={() => setFilter(id)}
                >
                  {label}
                </button>
              ))}
            </div>
            <IconButton
              label={compact ? "切换卡片显示" : "切换紧凑显示"}
              onClick={() => setCompact(!compact)}
            >
              {compact ? <Grid2X2 size={18} /> : <List size={18} />}
            </IconButton>
          </div>
          <div className="search-row">
            <label className="search">
              <Search size={16} />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="搜索称号"
                aria-label="搜索称号"
              />
              {query && (
                <button aria-label="清空搜索" onClick={() => setQuery("")}>
                  <X size={14} />
                </button>
              )}
            </label>
            <select
              aria-label="称号排序"
              value={sort}
              onChange={(e) => setSort(e.target.value)}
            >
              <option value="recent">最近获得</option>
              <option value="name">名称排序</option>
            </select>
          </div>
          <div className={`title-grid ${compact ? "compact" : ""}`}>
            {list.map((item) => (
              <TiltedCard
                key={item.id}
                onClick={() => setSelected(item.id)}
                className={`title-tile ${item.style} ${selected === item.id ? "selected" : ""} ${!item.owned ? "locked" : ""}`}
                pressed={selected === item.id} captionText={`${item.name}${state.equipped === item.id ? "，已佩戴" : ""}${!item.owned ? "，未获得" : ""}`}
                containerHeight={compact ? "68px" : "158px"} imageHeight={compact ? "68px" : "158px"}
                imageWidth="100%" radius={18} rotateAmplitude={reduceMotion ? 0 : 5}
                scaleOnHover={reduceMotion ? 1 : 1.018} glare={0.5} shadow={0.12}
                gyroscope={false} intro={false} reducedMotion={reduceMotion} showTooltip={false}
              >
                <span className="title-emblem" aria-hidden="true">
                  <span className="emblem-back" />
                  <span className="emblem-front"><Symbol name={item.icon} size={33} /></span>
                </span>
                <span className="tile-copy">
                  <span className="tile-category">{item.category}</span>
                  <TitleText title={item} />
                  <span className="tile-rarity">{item.rarity}</span>
                </span>
                {state.equipped === item.id ? (
                  <span className="equipped-tag">
                    <Check size={12} />
                    已佩戴
                  </span>
                ) : !item.owned ? (
                  <Lock className="locked-icon" size={14} />
                ) : null}
                <span className="tile-footer">
                  <span className="tile-date"><CalendarDays size={12} />{item.owned ? item.date : "尚未解锁"}</span>
                  <span className="tile-quality">{selected === item.id ? <><Check size={12} />已选中</> : <ArrowUpRight size={14} />}</span>
                </span>
              </TiltedCard>
            ))}
          </div>
          {!list.length && (
            <div className="empty-state">
              <Search size={27} />
              <h3>没有找到这个称号</h3>
              <p>换个关键词，或查看全部称号。</p>
              <button
                className="btn subtle"
                onClick={() => {
                  setQuery("");
                  setFilter("all");
                }}
              >
                查看全部
              </button>
            </div>
          )}
          <p className="list-summary">
            {list.length} 枚称号 ·{" "}
            {filter === "locked"
              ? "还有新的旅程等你开启"
              : "每一个名字，都有它的故事"}
          </p>
        </section>
        <aside className={`title-detail card ${t.style}`}>
          <div className="card-title">
            <h2>佩戴预览</h2>
            <span className={`preview-status ${state.equipped === t.id ? "is-equipped" : ""}`}>
              {state.equipped === t.id ? <Check size={12} /> : <Eye size={12} />}
              {state.equipped === t.id ? "已佩戴" : "仅预览"}
            </span>
          </div>
          <div className="nameplate-preview">
            <span className="preview-orbit" aria-hidden="true" />
            <Symbol name={t.icon} className="preview-symbol" aria-hidden="true" />
            <div className="nameplate">
              <TitleText title={t} />
              <strong>{player.name}</strong>
            </div>
            <Avatar large />
            <div className="preview-ground" />
            <small>游戏内效果示意</small>
          </div>
          <div className="title-description">
            <h3>
              <Symbol name={t.icon} size={18} />
              {t.name}
            </h3>
            <p>{t.description}</p>
            <div className="acquisition">
              <small><Gift size={13} />{t.owned ? "获得方式" : "解锁条件"}</small>
              <span>{t.condition}</span>
            </div>
            {t.owned && <div className="acquired-date"><CalendarDays size={13} /><span>{t.date}</span><span>获得</span></div>}
          </div>
          <div className="equip-controls">
            {t.owned ? (
              <button
                className={`btn ${state.equipped === t.id ? "equipped-btn" : "primary"}`}
                onClick={() =>
                  state.equipped === t.id
                    ? (update({ equipped: null }), notify("已摘下称号"))
                    : equip(t)
                }
              >
                {state.equipped === t.id ? (
                  <>
                    <Check size={16} />
                    已佩戴 · 点击摘下
                  </>
                ) : (
                  <>
                    佩戴这个称号
                    <ArrowRight size={16} />
                  </>
                )}
              </button>
            ) : (
              <button className="btn" disabled>
                <Lock size={15} />
                尚未获得
              </button>
            )}
            <div className="visibility-control">
              <div>
                <span>展示给其他玩家</span>
                <small>
                  {state.visible
                    ? "佩戴后在游戏中展示"
                    : "已隐藏展示，佩戴状态保留"}
                </small>
              </div>
              <Toggle
                checked={state.visible}
                onChange={(v) => {
                  update({ visible: v });
                  notify(v ? "已开启称号展示" : "已隐藏称号展示，佩戴状态保留");
                }}
                label="展示给其他玩家"
              />
            </div>
          </div>
        </aside>
      </div>
    </>
  );
}

function FortunesPage({ archive, state, setReading, update, notify }) {
  const [month, setMonth] = useState(new Date(2026, 10, 1));
  const [monthDirection, setMonthDirection] = useState(1);
  const [selected, setSelected] = useState(todayKey);
  const [mode, setMode] = useState("calendar");
  const monthPrefix = `${month.getFullYear()}-${String(month.getMonth() + 1).padStart(2, "0")}`;
  const stored = Object.values(archive).filter((x) =>
    x.date.startsWith(monthPrefix),
  );
  const selectedFortune = archive[selected];
  const start = month.getDay();
  const days = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  const cells = Math.ceil((start + days) / 7) * 7;
  const shift = (amount) => {
    const d = new Date(month.getFullYear(), month.getMonth() + amount, 1);
    setMonthDirection(Math.sign(amount));
    setMonth(d);
    const prefix = dateKey(d).slice(0, 7);
    setSelected(Object.keys(archive).filter(date => date.startsWith(prefix)).sort().at(-1) || dateKey(d));
  };
  const display = archive[state.displayDate];
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>
            我的签笺
            <span className="heading-count">{Object.keys(archive).length}</span>
          </h1>
          <p>一日一签，把沿途的机缘慢慢收藏。</p>
        </div>
        <span className="module-label">DailyFortunes</span>
      </div>
      <div className="fortunes-layout">
        <section className="card calendar-card" style={{ "--month-enter": `${monthDirection * 28}px` }}>
          <div className="calendar-toolbar">
            <div className="month-label" key={monthPrefix}>
              <span className="month-numeral" aria-hidden="true">{String(month.getMonth() + 1).padStart(2, "0")}</span>
              <div><h2>{month.getFullYear()} 年 {month.getMonth() + 1} 月</h2>
                <span className="month-collection"><Bookmark size={12} />本月收藏 <strong>{stored.length}</strong> 张</span>
              </div>
            </div>
            <div className="calendar-controls">
              <button
                className="text-button"
                onClick={() => {
                  setMonthDirection(1);
                  setMonth(new Date(2026, 10, 1));
                  setSelected(todayKey);
                }}
              >
                本月
              </button>
              <IconButton label="上个月" onClick={() => shift(-1)}>
                <ChevronLeft size={18} />
              </IconButton>
              <IconButton
                label="下个月"
                disabled={month.getTime() >= new Date(2026, 10, 1).getTime()}
                onClick={() => shift(1)}
              >
                <ChevronRight size={18} />
              </IconButton>
              <span className="toolbar-divider" />
              <IconButton
                label={mode === "calendar" ? "切换签笺列表" : "切换月历显示"}
                onClick={() =>
                  setMode(mode === "calendar" ? "list" : "calendar")
                }
              >
                {mode === "calendar" ? (
                  <List size={18} />
                ) : (
                  <Grid2X2 size={18} />
                )}
              </IconButton>
            </div>
          </div>
          <div className="calendar-month-viewport">
          <div className="calendar-month-body" key={`${monthPrefix}-${mode}`}>
          {mode === "calendar" ? (
            <>
              <div className="weekdays">
                {"日一二三四五六".split("").map((x) => (
                  <span key={x}>{x}</span>
                ))}
              </div>
              <div className="calendar-grid">
                {Array.from({ length: cells }, (_, i) => {
                  const day = i - start + 1;
                  if (day < 1 || day > days)
                    return <span className="calendar-blank" key={i} />;
                  const d = new Date(
                      month.getFullYear(),
                      month.getMonth(),
                      day,
                    ),
                    key = dateKey(d),
                    f = archive[key],
                    future = d > TODAY;
                  return (
                    <button
                      key={key}
                      className={`calendar-day ${selected === key ? "selected" : ""} ${key === todayKey ? "today" : ""} ${future ? "future" : ""} ${f ? "has-fortune" : ""}`}
                      style={{
                        "--grade": f ? gradeColor(f.grade) : "transparent",
                      }}
                      disabled={future}
                      onClick={() => setSelected(key)}
                      aria-label={`${key}${f ? " " + f.grade + " " + f.title : future ? " 尚未到来" : " 未收藏"}`}
                      aria-pressed={selected === key}
                    >
                      <span className="day-number">{day}</span>
                      {key === todayKey && (
                        <span className="today-mark">今天</span>
                      )}
                      <img src="/assets/fortune-slip.png" alt="" />
                      {f && <span className="day-grade">{f.grade}</span>}
                    </button>
                  );
                })}
              </div>
            </>
          ) : (
            <div className="fortune-history">
              {stored.length ? (
                stored.toReversed().map((f) => (
                  <button
                    key={f.date}
                    onClick={() => setSelected(f.date)}
                    className={selected === f.date ? "selected" : ""}
                  >
                    <img src="/assets/fortune-slip.png" alt="" />
                    <div>
                      <strong>{f.title}</strong>
                      <small>
                        {f.date.replaceAll("-", "/")} {f.time}
                      </small>
                    </div>
                    <span style={{ color: gradeColor(f.grade) }}>
                      {f.grade}
                    </span>
                    <ChevronRight size={16} />
                  </button>
                ))
              ) : (
                <div className="empty-state">
                  <BookOpen size={28} />
                  <h3>这个月还没有签笺</h3>
                  <p>已收藏的签会按抽签日期出现在这里。</p>
                </div>
              )}
            </div>
          )}
          </div>
          </div>
          <div className="calendar-legend">
            <span>
              <i style={{ background: "#b48a41" }} />
              上上签
            </span>
            <span>
              <i style={{ background: "#578c68" }} />
              上吉签
            </span>
            <span>
              <i style={{ background: "#6a90a8" }} />
              中平签
            </span>
            <span>
              <i style={{ background: "#9188a1" }} />
              小凶签
            </span>
            <span>
              <i style={{ background: "#a27b7e" }} />
              下下签
            </span>
          </div>
        </section>
        <div className="fortune-aside">
        <aside className="card calendar-selection">
          <div className="card-title">
            <h2>{selected.replaceAll("-", " / ")}</h2>
            {selected === todayKey && <Pill>今天</Pill>}
          </div>
          {selectedFortune ? (
            <>
              <div className="fortune-preview-paper" style={{ "--grade": gradeColor(selectedFortune.grade) }}>
              <div className="fortune-seal" aria-hidden="true"><img src="/assets/fortune-slip.png" alt="" /></div>
              <span
                className="fortune-grade"
                style={{ color: gradeColor(selectedFortune.grade) }}
              >
                {selectedFortune.grade} · 第{selectedFortune.id}签
              </span>
              <h3 className="selected-fortune-title">
                {selectedFortune.title}
              </h3>
              <div className="poem compact-poem">
                {selectedFortune.poem.map((l) => (
                  <p key={l}>{l}</p>
                ))}
              </div>
              <div className="fortune-provenance"><span>{player.name} 所得</span><time>{selectedFortune.time}</time></div>
              </div>
              <div className="fortune-reading-actions"><span className="collected-status">
                <CheckCheck size={14} />
                已收藏
              </span>
              <button
                className="btn primary"
                onClick={() => setReading(selectedFortune)}
              >
                阅读解签
                <ArrowRight size={16} />
              </button>
              </div>
            </>
          ) : (
            <div className="empty-day">
              <img src="/assets/fortune-slip.png" alt="" />
              <h3>这一天，没有留下签笺</h3>
              <p>打开藏签匣时，背包中属于你的已解签会按对应日期归档。</p>
            </div>
          )}
        </aside>
      <section className="display-card">
        <div className="display-current">
        <span className="display-object">
          <img src="/assets/collection-chest.png" alt="藏签匣" />
        </span>
        <div>
          <span className="quiet-label">手册展示</span>
          <h3>
            {display?.title || "还没有选择签笺"}
            <span style={{ color: gradeColor(display?.grade) }}>
              {display?.grade}
            </span>
          </h3>
          <p>{display ? `${display.date.replaceAll("-", "/")} · 已收藏` : "从月历中选择一张已收藏的签"}</p>
        </div>
        </div>
        <button
          className="btn subtle"
          disabled={!selectedFortune || state.displayDate === selected}
          onClick={() => {
            if (selectedFortune) {
              update({ displayDate: selected });
              notify("已更新手册展示的签笺");
            } else notify("请先选择一张已收藏的签笺");
          }}
        >
          {state.displayDate === selected ? "当前选择已展示" : "展示选中的签"}
          {state.displayDate === selected ? <Check size={15} /> : <ArrowUpRight size={15} />}
        </button>
        <small className="display-note">仅用于手册，方块展示在游戏内设置</small>
      </section>
        </div>
      </div>
    </>
  );
}
