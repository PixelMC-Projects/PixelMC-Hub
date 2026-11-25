import React, { useState, useEffect, useLayoutEffect, useRef } from "react";
import { AnimatePresence, motion } from "motion/react";
import GlassIcons from "./vendor/react-bits/GlassIcons.jsx";
import GlassSurface from "./vendor/react-bits/GlassSurface.jsx";
import TiltedCard from "./vendor/react-bits/TiltedCard.jsx";
import SwipeToast from "./vendor/react-bits/SwipeToast.jsx";
import ChangelogPage from "./changelog/ChangelogPage.jsx";
import LeaderboardPage from "./leaderboards/LeaderboardPage.jsx";
import PlayerProfileCard from "./community/PlayerProfileCard.jsx";
import JourneyMemories from "./community/JourneyMemories.jsx";
import {
  MEMORY_ARCHIVES, SELF_PROFILE, getPublicProfile, readCommunityProfile, readHubPreviewState, readSelfFortuneState,
  saveSelfFortuneSelection, persistHubPreviewState, subscribeCommunity, readProfilePreferences,
} from "./community/demo.js";
import { previewAppearance } from "./previewAppearance.js";
import { useLeaderboardPreview } from "./leaderboards/store.js";
import { dispatch as updateChangelog, useChangelog } from "./changelog/store.js";
import { publicationWatermark } from "./changelog/model.js";
import PlayerHead from "./PlayerHead.jsx";
import { FoxStamp, WinterNavigation, StationPaper, StationNews } from "./WinterStation.jsx";
import { grantKey, rememberedGrant, subscribePreview, writeValue } from "./wenzhou/managerPreview.js";
import Wenzhou from "./wenzhou/Wenzhou.jsx";
import {changeModelMode, modelMode, modelModes} from "./wenzhou/modelPreview.js";
import HubRouteSurface from "./wenzhou/HubRouteSurface.jsx";
import { Glyph } from "./wenzhou/Glyph.jsx";
import WinterTitleCase from "./WinterTitleCase.jsx";
import WinterFortuneCounter, { JournalSlip } from "./WinterFortuneCounter.jsx";
import "./winter-title-case.css";
import WelcomeSplash from "./WelcomeSplash.jsx";
import WelcomeFrequency, { shouldShowWelcome, welcomeModes } from "./WelcomeFrequency.jsx";
import { titleIcons } from "./title-icons.js";
import {
  Home,
  Newspaper,
  Trophy,
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
  Snowflake,
  BookUser,
  Footprints,
  Hourglass,
  CalendarCheck2,
  MailOpen,
  Map,
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

const handbookPages = ["home", "titles", "fortunes", "updates", "leaderboards", "memories", "wenzhou"];

function previewRoute(requested, granted) {
  if (requested === "rankings") return "leaderboards";
  if (requested === "wenzhou" && !granted) return "home";
  return handbookPages.includes(requested) ? requested : "home";
}

const currentArchive = MEMORY_ARCHIVES.find(archive => !archive.end && SELF_PROFILE.memoryIds.includes(archive.id));

function initial() {
  const defaults = {
    equipped: "dew",
    visible: true,
    fortuneState: "archived",
    displayDate: todayKey,
    reducedMotion: false,
    welcomeMode: "login",
    theme: "light",
    homeStyle: "classic",
    hasSeenWelcome: false,
    modpackSyncPlugin: true,
    modpackSyncEnabled: true,
    modpackSyncChannel: "stable",
    modpackSyncGranted: true,
  };
  try {
    const saved = { ...defaults, ...readHubPreviewState() };
    if (!welcomeModes.some((mode) => mode.id === saved.welcomeMode)) saved.welcomeMode = defaults.welcomeMode;
    if (!["stable", "preview"].includes(saved.modpackSyncChannel)) saved.modpackSyncChannel = defaults.modpackSyncChannel;
    saved.modpackSyncPlugin = saved.modpackSyncPlugin !== false;
    saved.modpackSyncEnabled = saved.modpackSyncEnabled !== false;
    saved.modpackSyncGranted = saved.modpackSyncGranted !== false;
    saved.hasSeenWelcome = saved.hasSeenWelcome === true;
    return { ...saved, ...previewAppearance(saved) };
  } catch {
    return { ...defaults, ...previewAppearance(defaults) };
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

// A replacement dialog can open while its animated predecessor is still mounted.
const modalReturnTargets = new WeakMap();

function Modal({ title, subtitle, onClose, children, wide = false, className = "", animated = false, reducedMotion = false }) {
  const Backdrop = animated ? motion.div : "div";
  const Surface = animated ? motion.section : "section";
  const backdropMotion = animated ? {
    initial: {opacity: 0}, animate: {opacity: 1}, exit: {opacity: 0},
    transition: {duration: reducedMotion ? 0 : .18},
  } : {};
  const surfaceMotion = animated ? {
    initial: {opacity: 0, y: reducedMotion ? 0 : 10, scale: reducedMotion ? 1 : .985},
    animate: {opacity: 1, y: 0, scale: 1},
    exit: {opacity: 0, y: reducedMotion ? 0 : 7, scale: reducedMotion ? 1 : .985},
    transition: {duration: reducedMotion ? 0 : .2, ease: [.22, 1, .36, 1]},
  } : {};
  const ref = useRef();
  useEffect(() => {
    const root = ref.current;
    const trigger = document.activeElement;
    const parent = trigger?.closest('[role="dialog"]');
    const returnTargets = [trigger, ...(modalReturnTargets.get(parent) ?? [])];
    modalReturnTargets.set(root, returnTargets);
    const focusable = () => [
      ...root.querySelectorAll(
        'button:not(:disabled),input:not(:disabled),textarea:not(:disabled),select:not(:disabled),a[href],[tabindex="0"]',
      ),
    ].filter(element => !element.closest('[inert]') && element.getClientRects().length > 0);
    focusable()[0]?.focus({ preventScroll: true });
    const onKey = (e) => {
      if (e.key === "Escape") {
        e.stopPropagation();
        if (e.repeat || e.isComposing) return;
        const cancel = root.querySelector("[data-profile-cancel]:not([inert] *)");
        if (cancel) cancel.click();
        else onClose();
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
      modalReturnTargets.delete(root);
      const activeDialog = document.activeElement?.closest('[role="dialog"]');
      if (activeDialog && activeDialog !== root) return;
      const target = returnTargets.find(el => el?.isConnected && !root.contains(el)
        && !el.closest('[inert]') && el.getClientRects().length > 0);
      target?.focus({ preventScroll: true });
    };
  }, []);
  return (
    <Backdrop {...backdropMotion}
      className={`modal-backdrop ${animated ? "modal-motion" : ""}`}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <Surface {...surfaceMotion}
        ref={ref}
        className={`modal ${wide ? "wide" : ""} ${className}`}
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
      </Surface>
    </Backdrop>
  );
}
function FortuneReading({ fortune, onClose, displayDate, onDisplay, winter }) {
  return (
    <Modal
      title="签笺详情"
      className="fortune-reading-dialog"
      subtitle={`${fortune.date.replaceAll("-", "/")} · ${player.name} 所得`}
      onClose={onClose}
      wide
    >
      <div className="reading">
        {winter ? <JournalSlip fortune={fortune} selected={fortune.date}/> : <div className="reading-paper">
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
        </div>}
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

const requestedDemo = new URLSearchParams(location.search).get("demo");
const inboxDemo = ["unread", "shares"].includes(requestedDemo) ? requestedDemo : null;

export default function App() {
  const [previewGrant, setPreviewGrant] = useState(() => Boolean(inboxDemo) || rememberedGrant(SELF_PROFILE.playerId));
  const [wenzhouModelMode, setWenzhouModelMode] = useState(() => modelMode(SELF_PROFILE.playerId));
  useEffect(() => subscribePreview(() => setWenzhouModelMode(modelMode(SELF_PROFILE.playerId))), []);
  useEffect(() => subscribePreview(key => {
    if (key && key !== grantKey(SELF_PROFILE.playerId)) return;
    const granted = Boolean(inboxDemo) || rememberedGrant(SELF_PROFILE.playerId);
    setPreviewGrant(granted);
    if (!granted && location.hash === '#/wenzhou') {
      setPage('home');
      history.replaceState(history.state, '', '#/home');
    }
  }), []);
  const [page, setPage] = useState(() => previewRoute(
    location.hash.slice(2) || new URLSearchParams(location.search).get("page"), previewGrant
  ));
  const updatePrototype = useChangelog();
  const [updateToOpen, setUpdateToOpen] = useState(null);
  const updatesUnread = publicationWatermark(updatePrototype) > (updatePrototype.updatesSeen || 0);
  const [leaderboardPreview, setLeaderboardPreview, simulateLeaderboardUpdate] = useLeaderboardPreview();
  useEffect(() => {
    const url = new URL(location.href);
    url.hash = "/" + page;
    url.searchParams.delete("winter");
    history.replaceState(history.state, "", url);
  }, []);
  const [state, setState] = useState(initial);
  const winterTheme = state.homeStyle === "winter";
  const winterHome = winterTheme && page === "home";
  const Navigation = winterTheme ? WinterNavigation : GlassIcons;
  const IdentitySurface = winterHome ? StationPaper : GlassSurface;
  const [systemReduced, setSystemReduced] = useState(() => window.matchMedia("(prefers-reduced-motion: reduce)").matches);
  useEffect(() => {
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const sync = () => setSystemReduced(media.matches);
    media.addEventListener("change", sync);
    return () => media.removeEventListener("change", sync);
  }, []);
  const reduceMotion = state.reducedMotion || systemReduced;
  const [modal, setModal] = useState(null);
  const [stationBio, setStationBio] = useState(() => readProfilePreferences(SELF_PROFILE).bio);
  const [publicProfile, setPublicProfile] = useState(null);
  const [memoryPlayer, setMemoryPlayer] = useState(SELF_PROFILE);
  const [memoryArchive, setMemoryArchive] = useState(null);
  const memoryReturnPage = useRef("home");
  useLayoutEffect(() => {
    document.documentElement.dataset.theme = state.theme;
  }, [state.theme]);
  useEffect(() => subscribeCommunity(() => {
    try {
      setStationBio(readProfilePreferences(SELF_PROFILE).bio);
      const selected = readSelfFortuneState();
      setState(current => current.displayDate === selected.displayDate && current.fortuneState === selected.fortuneState
        ? current : { ...current, ...selected });
      setPublicProfile(current => current ? { ...readCommunityProfile(current.playerId), title: current.title } : null);
      setMemoryPlayer(current => readCommunityProfile(current.playerId));
    } catch (error) {
      setToast({ text: error.message || "资料更新读取失败，请重新打开。", tone: "error", key: Date.now() });
    }
  }), []);
  const leaderboardPreviewTimer = useRef(null);
  useEffect(() => () => clearTimeout(leaderboardPreviewTimer.current), []);
  const playLeaderboardUpdate = () => {
    clearTimeout(leaderboardPreviewTimer.current);
    setModal(null); setPage('leaderboards');
    // Let the preview dialog close before issuing this single synthetic event.
    leaderboardPreviewTimer.current = setTimeout(simulateLeaderboardUpdate, 500);
  };
  const [reading, setReading] = useState(null);
  const [toast, setToast] = useState(null);
  const [closed, setClosed] = useState(false);
  // One page load represents one login in this browser-only prototype.
  const seenThisLogin = useRef(false);
  const [opening, setOpening] = useState(() => page === "home" && shouldShowWelcome(requestedDemo === "opening" && winterTheme ? "always" : state.welcomeMode, {
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
  const notify = (text, tone = "success") => setToast({ text, tone, key: Date.now() });
  const update = async (patch) => {
    try {
      if (Object.hasOwn(patch, "fortuneState")) {
        await persistHubPreviewState({ ...state, ...patch }, { writeFortuneState: true });
      }
      if (Object.hasOwn(patch, "displayDate")) {
        const selected = await saveSelfFortuneSelection(patch.displayDate);
        setState(current => ({ ...current, ...patch, ...selected }));
      } else {
        setState(current => ({ ...current, ...patch }));
      }
      return true;
    } catch (error) {
      notify(error.message || "保存未完成，请重试。", "error");
      return false;
    }
  };
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
  const navigate = (p, entry = null) => {
    const next = previewRoute(p, previewGrant);
    setUpdateToOpen(next === "updates" ? entry : null);
    setPage(next);
    location.hash = "/" + next;
    setClosed(false);
  };
  function changePreviewGrant(granted) {
    if (!granted && page === "wenzhou") {
      setPage("home");
      history.replaceState(history.state, "", "#/home");
    }
    try { writeValue(grantKey(SELF_PROFILE.playerId), granted); }
    catch { notify("浏览器未能保存模拟授权。", "error"); }
  }
  const openPublicProfile = (row = SELF_PROFILE) => {
    const base = row.memoryIds ? row : getPublicProfile(row);
    let profile;
    try { profile = readCommunityProfile(base.playerId); }
    catch (error) { notify(error.message || "资料读取失败，请重试。", "error"); return; }
    setModal(null);
    setPublicProfile(profile.isSelf ? {
      ...profile,
      title: state.visible ? equipped : null,
      featuredFortune: archivedFortunes[state.displayDate] ?? null,
    } : profile);
  };
  const saveProfileFortune = (date) => {
    if (!publicProfile?.isSelf || (date !== null && !archivedFortunes[date])) return;
    setState(current => ({ ...current, displayDate: date }));
    setPublicProfile(current => current?.isSelf ? {
      ...current, featuredFortune: archivedFortunes[date] ?? null,
    } : current);
  };
  const openMemories = (profile = SELF_PROFILE, archiveId = null) => {
    if (page !== "memories") memoryReturnPage.current = page;
    setPublicProfile(null); setModal(null);
    setMemoryPlayer(profile); setMemoryArchive(archiveId);
    navigate("memories");
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
    persistHubPreviewState(state).catch(error => notify(error.message || "界面设置保存未完成。", "error"));
    document.documentElement.dataset.motion = reduceMotion
      ? "reduced"
      : "full";
  }, [state, reduceMotion]);
  useEffect(() => {
    const f = () => {
      const requested = location.hash.slice(2);
      const next = previewRoute(requested, previewGrant);
      if (requested === "wenzhou" && next === "home") history.replaceState(history.state, "", "#/home");
      setPage(next);
    };
    window.addEventListener("hashchange", f);
    return () => window.removeEventListener("hashchange", f);
  }, [previewGrant]);
  useEffect(() => {
    const f = (e) => {
      if (e.key === "Escape" && !e.repeat && !modal && !reading && !publicProfile) {
        e.preventDefault();
        if (closed) openHandbook();
        else if (page === "wenzhou") navigate("home");
        else if (page === "memories") {
          const cancel = document.querySelector(".memories-page [data-memory-cancel]");
          if (cancel) cancel.click();
          else navigate(memoryReturnPage.current);
        }
        else setClosed(true);
      }
    };
    window.addEventListener("keydown", f);
    return () => window.removeEventListener("keydown", f);
  }, [page, modal, reading, publicProfile, closed, reduceMotion, state.welcomeMode, state.hasSeenWelcome]);
  function equip(title) {
    update({ equipped: title.id });
    notify(`已佩戴「${title.name}」`);
  }
  const NameHeading = winterHome ? "h1" : "h2";
  const playerIntro = page === "home" && <>
    {winterHome && <div className="avatar station-portrait"><PlayerHead name={player.name}/></div>}
    <div className="identity-copy">
      {!winterHome && <div className="identity-greeting">你好</div>}
      <NameHeading>{player.name}{!winterHome && <Pill>在线</Pill>}</NameHeading>
      <div className="equipped-line">
        <TitleText title={equipped} />
        {equipped && !state.visible && <span className="hidden-indicator"><EyeOff size={13}/>展示已隐藏</span>}
        {winterHome && <span className="station-joined">{player.joined} 加入</span>}
      </div>
      {winterHome && stationBio && <p className="station-bio">{stationBio}</p>}
    </div>
    <button className="btn subtle profile-btn" onClick={() => openPublicProfile(SELF_PROFILE)}>
      {winterHome && <BookUser size={15}/>}公开资料{!winterHome && <ArrowUpRight size={15}/>}
    </button>
    <button className="btn subtle profile-btn memories-btn" onClick={() => openMemories()}>
      {winterHome && <Map size={15}/>}旅程档案{!winterHome && <Bookmark size={14}/>}
    </button>
  </>;
  function readToday() {
    if (["read", "archived"].includes(state.fortuneState))
      setReading(todayFortune);
    else setModal("fortune-help");
  }
  return (
    <div className={`preview-stage${winterTheme ? " winter-theme" : ""}${winterHome ? " winter-home" : ""}`}>
      <div className="ambient ambient-one" />
      <div className="ambient ambient-two" />
      <div className="preview-caption">
        <span className="caption-brand">PIXELMC</span>
        <span>PLAYER EXPERIENCE</span>
      </div>
        <div style={closed ? {display:"none"} : undefined} className={`app-shell welcome-shell has-leaderboards-nav handbook-routes ${previewGrant ? "has-wenzhou-nav" : ""} ${opening ? "is-opening" : ""}`}>
          {opening && <WelcomeSplash winter={winterTheme} />}
          {previewGrant && <Wenzhou hubClosed={closed} demo={inboxDemo} blocked={Boolean(modal || opening)} playerId={SELF_PROFILE.playerId} active={!closed && page === "wenzhou"} reduceMotion={reduceMotion} notify={notify} onBack={() => navigate("home")} />}
          <AnimatePresence initial={false}>
          {!closed && page !== "wenzhou" && <HubRouteSurface key="handbook" side="handbook" reduceMotion={reduceMotion}>
          <aside className="sidebar" inert={opening}>
            {!winterTheme && <GlassSurface width="100%" height="100%" borderRadius={0}
              borderWidth={0.08} brightness={65} opacity={0.9} blur={12} displace={0.5}
              backgroundOpacity={0.32} saturation={1.15} distortionScale={-12}
              redOffset={0} greenOffset={0} blueOffset={0} refraction={false} className="sidebar-material" />}
            <button
              className="brand"
              onClick={() => navigate("home")}
              aria-label="旅人手册首页"
            >
              {winterTheme ? <img className="station-field-atlas" src="/assets/winter/directory-handbook.webp" alt=""/>
                : <img className="server-brand-logo" src="/assets/server-logo.png" alt="PixelMC 服务器 Logo"/>}
              <span>
                <strong>旅人手册</strong>
                <small>PixelMC-Hub</small>
              </span>
            </button>
            {!winterTheme && <div className="nav-label">我的空间</div>}
            <nav aria-label="主导航">
              <Navigation
                key={String(reduceMotion)}
                items={[
                  { id: "home", label: "个人主页", icon: <Home />, color: "blue" },
                  { id: "titles", label: "我的称号", icon: <Tag />, color: "purple" },
                  { id: "fortunes", label: "我的签笺", icon: <BookOpen />, color: "orange" },
                  ...(updatePrototype.settings.updatesEnabled ? [{ id: "updates", ariaLabel: updatesUnread ? "更新日志，有新发布" : "更新日志", label: <span className="wz-nav-label">更新日志{updatesUnread && <small className="wz-beta updates-new">NEW</small>}</span>, icon: <Newspaper />, color: "green" }] : []),
                  { id: "leaderboards", label: "玩家排行", icon: <Trophy />, color: "pink" },
                  ...(previewGrant ? [{
                    id: "wenzhou", color: "teal", icon: <Glyph name="boat" />,
                    label: <span className="wz-nav-label">问舟<small className="wz-beta">BETA</small></span>,
                    ariaLabel: "问舟，内测",
                  }] : []),
                ].map(item => ({ ...item, onClick: () => navigate(item.id), current: page === item.id || (page === "memories" && item.id === "home"),
                  className: page === item.id || (page === "memories" && item.id === "home") ? "is-current" : "" }))}
                size={46} gap={28} columns={1} labels="always" theme={state.theme}
                plate="tilt" tilt={-16} roundness={0.65} shine={0.9} frost={0.1}
                tint="#ffffff" tintOpacity={0.16}
                hover={reduceMotion ? "none" : "lift"} parallax={reduceMotion ? 0 : 0.65}
                intro={!reduceMotion} introReady={!opening} reducedMotion={reduceMotion} className="hub-navigation"
              />
            </nav>
            {winterTheme && <div className="station-directory-scene" aria-hidden="true"/>}
            <div className="sidebar-bottom">
              {state.modpackSyncPlugin ? (
                <button
                  type="button"
                  className="server-card"
                  aria-label="打开整合包同步设置"
                  onClick={() => setModal("modpack-sync")}
                >
                  <span className="server-icon">
                    {winterTheme ? <img src="/assets/server-logo.png" width="32" height="32" alt="PixelMC 服务器"/> : <Flower2 size={21} />}
                  </span>
                  <span className="server-caption">
                    <strong>{currentArchive?.name ?? "当前整合包"}{currentArchive?.version && <em>{currentArchive.version}</em>}</strong>
                    <small>
                      <i className={!state.modpackSyncEnabled || !state.modpackSyncGranted ? "is-paused" : state.modpackSyncChannel === "preview" ? "is-preview" : ""} />
                      {!state.modpackSyncGranted
                        ? "未授权 · 点击设置"
                        : !state.modpackSyncEnabled
                          ? "自动同步已关闭"
                          : state.modpackSyncChannel === "preview"
                            ? "预览版 · 自动同步"
                            : "正式版 · 自动同步"}
                    </small>
                  </span>
                  <ChevronRight className="server-card-chevron" size={13}/>
                </button>
              ) : (
                <div className="server-card is-static">
                  <span className="server-icon">
                    {winterTheme ? <img src="/assets/server-logo.png" width="32" height="32" alt="PixelMC 服务器"/> : <Flower2 size={21} />}
                  </span>
                  <span className="server-caption">
                    <strong>{currentArchive?.name ?? "当前整合包"}{currentArchive?.version && <em>{currentArchive.version}</em>}</strong>
                    <small>
                      <i /> 当前存档 · 演示
                    </small>
                  </span>
                </div>
              )}
              <button
                className="nav-item settings-nav"
                aria-label="界面设置"
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
                {winterTheme && ["updates","leaderboards"].includes(page) ? "服务器" : "我的空间"} <ChevronRight size={13} />{" "}
                <strong>
                  {
                    {
                      home: "个人主页",
                      titles: "我的称号",
                      fortunes: "我的签笺",
                      updates: "更新日志",
                      leaderboards: "玩家排行",
                      memories: "旅程档案",
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
                  {winterHome ? <header className="station-heading">{playerIntro}</header> : <div className="page-heading">
                    <div>
                      <h1>个人主页</h1>
                      <p>很高兴又见到你，今天是旅程的第 18 天。</p>
                    </div>
                    <div className="date-label">
                      <Sun size={18}/>
                      <span>11 月 3 日，星期二</span>
                    </div>
                  </div>}
                  <section className="identity-card">
                    <IdentitySurface width="100%" height="auto" borderRadius={22}
                      borderWidth={0.04} brightness={65} opacity={0.7} blur={8}
                      backgroundOpacity={0.09} saturation={1.25} distortionScale={-45}
                      redOffset={0} greenOffset={0} blueOffset={0} refraction={false} className="identity-glass">
                    {winterHome ? <div className="station-scenery">
                      <div className="station-scene-caption">
                        <span>{state.theme === "dark" ? <Moon size={15}/> : <Sun size={15}/>}11 月 3 日，星期二</span>
                        <strong>旅程第 18 天</strong>
                      </div>
                    </div> : <div className="identity-main">
                      {playerIntro}
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
                    </div>}
                    <div className="stats-row">
                      <div>
                        <span className="stat-icon">
                          {winterHome ? <Hourglass size={19}/> : <Clock size={19} />}
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
                          {winterHome ? <Footprints size={19}/> : <LogIn size={19} />}
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
                          {winterHome ? <CalendarCheck2 size={19}/> : <CalendarDays size={19} />}
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
                    </IdentitySurface>
                  </section>
                  <div className={winterHome ? "station-sections" : "home-lower"}>
                    <section className="card today-card">
                      <div className="card-title">
                        <h2>
                          {winterHome ? <MailOpen size={19}/> : <Leaf size={19} />}
                          今日签笺
                        </h2>
                        {!winterHome && <span className="quiet-label">每日一签</span>}
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
                            {winterHome ? <FoxStamp/> : <div className="fortune-art">
                              <div className="art-orbit" />
                              <img
                                src="/assets/fortune-barrel.png"
                                alt="繁茂枝叶环绕的木质签筒"
                              />
                              <span className="art-spark one">✦</span>
                              <span className="art-spark two">✧</span>
                            </div>}
                          </div>
                          <div className="card-bottom">
                            <span className="collected-status">
                              <CheckCheck size={15} />
                              {stateLabels[state.fortuneState]}
                            </span>
                            <button className="btn primary" onClick={readToday}>
                              查看签文
                              {winterHome ? <MailOpen size={16}/> : <ArrowRight size={16} />}
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
                            {winterHome ? <FoxStamp/> : <div className="fortune-art">
                              <img
                                src="/assets/fortune-barrel.png"
                                alt="签筒"
                              />
                            </div>}
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
                    {winterHome ? <StationNews changes={updatePrototype} onOpenEntry={entry => navigate("updates", entry)}
                      onOpenProfile={openPublicProfile}/> : <section className="card keepsakes">
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
                    </section>}
                  </div>
                  <div className="page-footnote">
                    {!winterHome && <span><Leaf size={13} /> 每一份收藏，都记得来时的路。</span>}
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
              {page === "updates" && <ChangelogPage notify={notify} reduceMotion={reduceMotion} initialEntry={updateToOpen} winter={winterTheme} />}
              {page === "leaderboards" && <LeaderboardPage notify={notify} reduceMotion={reduceMotion} onOpenProfile={openPublicProfile} winter={winterTheme} />}
              {page === "fortunes" && (
                <FortunesPage
                  archive={archivedFortunes}
                  state={state}
                  setReading={setReading}
                  update={update}
                  notify={notify}
                />
              )}
              {page === "memories" && <JourneyMemories player={memoryPlayer} initialArchiveId={memoryArchive} winter={winterTheme}
                onBack={() => navigate(memoryReturnPage.current)} onOpenProfile={openPublicProfile} reduceMotion={reduceMotion} notify={notify} />}
            </main>
          </div>
          </HubRouteSurface>}
          </AnimatePresence>
        </div>
      {closed && (
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
      {toast && (!closed || toast.tone === "wenzhou-complete") && <SwipeToast key={toast.key} title={toast.text}
        icon={winterTheme && toast.tone === "error" ? <span className="receipt-alert">!</span>
          : toast.tone === "error" || toast.tone === "info" ? <Info size={18}/> : <Check size={18}/>}
        className={`hub-swipe-toast is-${toast.tone}`}
        background="#ffffff" color="#505875" closeButton
        reducedMotion={reduceMotion} onClose={() => setToast(current => current?.key === toast.key ? null : current)} />}
      <div className="preview-footer">
        <span>
          <kbd>Esc</kbd> {closed ? "打开手册" : page === "wenzhou" ? "返回手册首页" : page === "memories" ? "返回上一级" : "收起面板"}
        </span>
        <span>本地交互预览 · 演示数据 <a className="updates-manager-link" href="/manager/" target="_blank" rel="noreferrer">打开 Manager 管理端 ↗</a></span>
        <button onClick={() => setModal("preview")}>
          <SlidersHorizontal size={13} />
          预览选项
        </button>
      </div>
      {reading && (
        <FortuneReading
          fortune={reading}
          winter={winterTheme}
          onClose={() => setReading(null)}
          displayDate={state.displayDate}
          onDisplay={async (date) => {
            if (await update({ displayDate: date })) notify("已更新手册展示的签笺");
          }}
        />
      )}
      <AnimatePresence>
      {publicProfile && (
        <Modal key={publicProfile.playerId} title="玩家资料" className="public-profile-modal" animated reducedMotion={reduceMotion} onClose={() => setPublicProfile(null)}>
          <div className="public-profile-scroll">
            <PlayerProfileCard player={publicProfile} onOpenMemories={openMemories} reduceMotion={reduceMotion} notify={notify} winter={winterTheme}
              fortuneOptions={publicProfile.isSelf ? Object.values(archivedFortunes).sort((a, b) => b.date.localeCompare(a.date)) : []}
              onSave={profile => setPublicProfile(current => current?.playerId === profile.playerId ? { ...current, ...profile } : current)}
              onSaveFortune={saveProfileFortune} />
            {publicProfile.isSelf && <div className="public-profile-account"><button className="text-button" onClick={() => {setPublicProfile(null);setModal("profile");}}>账户信息<ChevronRight size={13} /></button></div>}
          </div>
        </Modal>
      )}
      </AnimatePresence>
      {modal === "profile" && (
        <Modal
          title="个人资料"
          className="account-dialog"
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
                        notify("浏览器未允许复制，请手动选择 UUID", "error");
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
          className="settings-dialog"
          subtitle="让手册更符合你的习惯"
          onClose={() => setModal(null)}
        >
          <div className="settings-content">
            <WelcomeFrequency winter={winterTheme} value={state.welcomeMode} onChange={(welcomeMode) => update({ welcomeMode })}
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
                <p>{winterTheme ? (state.theme === "dark" ? "松绿夜色与暖纸灯光" : "松针绿与雪色纸面") : (state.theme === "dark" ? "深灰蓝紫玻璃" : "浅色液态玻璃")}</p>
              </div>
              <div className="theme-choices" role="group" aria-label="界面主题">
                <button aria-pressed={state.theme === "light"} onClick={() => update({theme:"light"})}><Sun size={15} />浅色</button>
                <button aria-pressed={state.theme === "dark"} onClick={() => update({theme:"dark"})}><Moon size={15} />深色</button>
              </div>
            </div>
            <div className="setting-row">
              <div>
                <strong>手册主题</strong>
                <p>{state.homeStyle === "winter" ? (state.theme === "dark" ? "驿站夜灯" : "雪后清晨") : "经典旅人手册"}</p>
              </div>
              <div className="theme-choices" role="group" aria-label="手册主题">
                <button aria-pressed={state.homeStyle !== "winter"} onClick={() => update({homeStyle:"classic"})}><BookOpen size={15}/>经典</button>
                <button aria-pressed={state.homeStyle === "winter"} onClick={() => update({homeStyle:"winter"})}><Snowflake size={15}/>冬日驿站</button>
              </div>
            </div>
            <div className="settings-note">
              <Info size={15} />
              <span>设置会保存在当前浏览器中。</span>
            </div>
          </div>
        </Modal>
      )}
      {modal === "modpack-sync" && (
        <Modal
          title="整合包同步"
          className="settings-dialog modpack-sync-dialog"
          subtitle={`${currentArchive?.name ?? "香草纪元"}${currentArchive?.version ? ` ${currentArchive.version}` : ""} · 管理自动同步与推送频道`}
          onClose={() => setModal(null)}
        >
          <div className="settings-content">
            <div className="setting-row">
              <div>
                <strong>自动同步整合包</strong>
                <p>
                  {state.modpackSyncEnabled && state.modpackSyncGranted
                    ? "连接服务器时自动从可信 OSS 校验并暂存更新"
                    : "已暂停自动同步，不会改动本地模组与配置"}
                </p>
              </div>
              <Toggle
                checked={state.modpackSyncEnabled && state.modpackSyncGranted}
                onChange={(v) => {
                  if (v && !state.modpackSyncGranted) {
                    update({ modpackSyncEnabled: true, modpackSyncGranted: true });
                    notify("已重新开启自动同步（游戏内首次连服会确认授权）");
                  } else {
                    update({ modpackSyncEnabled: v });
                    notify(v ? "已开启整合包自动同步" : "已关闭整合包自动同步");
                  }
                }}
                label="自动同步整合包"
              />
            </div>
            <div className={`setting-row${!state.modpackSyncEnabled || !state.modpackSyncGranted ? " is-dimmed" : ""}`}>
              <div>
                <strong>推送频道</strong>
                <p>
                  {state.modpackSyncChannel === "preview"
                    ? "优先接收最新预览版更新；切回正式版后下次连服自动回退"
                    : "仅接收稳定测试通过的正式版整合包更新（默认）"}
                </p>
              </div>
              <div className="theme-choices" role="group" aria-label="推送频道">
                <button
                  type="button"
                  aria-pressed={state.modpackSyncChannel !== "preview"}
                  onClick={() => {
                    const fromPreview = state.modpackSyncChannel === "preview";
                    update({ modpackSyncChannel: "stable" });
                    if (fromPreview) notify("已切回正式版，下次连服将自动安全回退");
                  }}
                >
                  正式版
                </button>
                <button
                  type="button"
                  aria-pressed={state.modpackSyncChannel === "preview"}
                  onClick={() => {
                    const fromStable = state.modpackSyncChannel !== "preview";
                    update({ modpackSyncChannel: "preview" });
                    if (fromStable) notify("已切换为预览版，下次连接服务器时生效");
                  }}
                >
                  预览版
                </button>
              </div>
            </div>
            <div className="setting-row">
              <div>
                <strong>本地同步授权</strong>
                <p>
                  {state.modpackSyncGranted
                    ? "已授权 · 仅处理签名清单内的受管目录，不触碰存档"
                    : "已撤销 · 下次连接服务器时将重新弹出首次授权确认"}
                </p>
              </div>
              {state.modpackSyncGranted ? (
                <button
                  type="button"
                  className="sync-revoke-btn"
                  onClick={() => {
                    update({ modpackSyncGranted: false, modpackSyncEnabled: false });
                    notify("已撤销本地同步授权并暂停自动同步");
                  }}
                >
                  撤销授权
                </button>
              ) : (
                <button
                  type="button"
                  className="btn subtle sync-restore-btn"
                  onClick={() => {
                    update({ modpackSyncGranted: true, modpackSyncEnabled: true });
                    notify("已恢复演示授权状态");
                  }}
                >
                  恢复授权
                </button>
              )}
            </div>
            <div className="settings-note">
              <Info size={15} />
              <span>修改会写入本地客户端同步配置，重新连接服务器后自动生效。</span>
            </div>
          </div>
        </Modal>
      )}
      {modal === "preview" && (
        <Modal
          title="预览选项"
          className="preview-options-dialog"
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
            <div className="lb-preview-field">
              <label htmlFor="leaderboard-preview-state">排行榜展示状态</label>
              <select id="leaderboard-preview-state" value={leaderboardPreview} onChange={(event) => setLeaderboardPreview(event.target.value)}>
                <option value="ready">演示排行</option>
                <option value="loading">读取中</option>
                <option value="unavailable">暂时不可用</option>
                <option value="unsupported">未安装对应模块</option>
                <option value="empty">空榜</option>
                <option value="unranked">自己未入榜</option>
              </select>
              <p>切换后进入玩家排行查看；仅影响本地预览。</p>
              <div className="lb-preview-actions"><button className="btn subtle" onClick={playLeaderboardUpdate}>播放排行变动</button><button className="btn subtle" onClick={() => setLeaderboardPreview('ready')}>还原排行</button></div>
            </div>
            <div className="lb-preview-field">
              <label htmlFor="wenzhou-preview-access">问舟内测身份（本页模拟）</label>
              <select id="wenzhou-preview-access" value={previewGrant ? "granted" : "denied"}
                onChange={event => changePreviewGrant(event.target.value === "granted")}>
                <option value="denied">未授权玩家</option>
                <option value="granted">已授权玩家</option>
              </select>
              <label htmlFor="wenzhou-preview-model">问舟模型权限（本页模拟）</label>
              <select id="wenzhou-preview-model" value={wenzhouModelMode}
                onChange={event => changeModelMode(SELF_PROFILE.playerId, event.target.value)}>
                {Object.entries(modelModes).map(([mode, label]) => <option key={mode} value={mode}>{label}</option>)}
              </select>
              <label htmlFor="modpack-sync-plugin-preview">AutoModpack-OSS 插件（本页模拟）</label>
              <select id="modpack-sync-plugin-preview" value={state.modpackSyncPlugin ? "installed" : "absent"}
                onChange={event => update({ modpackSyncPlugin: event.target.value === "installed" })}>
                <option value="installed">已安装（左下角卡片可点）</option>
                <option value="absent">未安装（左下角卡片不可点）</option>
              </select>
            </div>
            <button type="button" className="btn subtle" onClick={() => {
              if (page === "updates") navigate("home");
              updateChangelog({ type: "resetUpdateReads" });
              setModal(null);
              notify("已重置更新日志阅读状态");
            }}>
              <RotateCcw size={15} />重置更新日志阅读状态
            </button>
            <a className="btn subtle" href="?page=wenzhou&demo=unread#/wenzhou" target="_blank" rel="noreferrer">演示问舟未读对话</a>
            <a className="btn subtle" href="?page=wenzhou&demo=shares#/wenzhou" target="_blank" rel="noreferrer">演示收到的分享</a>
            <p className="preview-note">
              称号佩戴、显示设置和手册签笺展示都是本地模拟。抽签、解签、存签仍保留游戏内物品与方块交互。
            </p>
            <button
              className="btn subtle"
              onClick={async () => {
                const restored = await update({
                  equipped: "dew",
                  visible: true,
                  fortuneState: "archived",
                  displayDate: todayKey,
                });
                if (restored) notify("已还原演示状态");
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
          className="fortune-help-dialog"
          title={
            state.fortuneState === "undrawn"
              ? "用签筒开启今日一签"
              : "前往解签龛"
          }
          onClose={() => setModal(null)}
        >
          <div className="fortune-help">
            {winterTheme && state.fortuneState !== "undrawn"
              ? <img src="/assets/fortune-slip.png" alt="未解签" />
              : <img src="/assets/fortune-barrel.png" alt="签筒" />}
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
  const winter = state.homeStyle === "winter";
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
  const t = winter
    ? list.find(item => item.id === selected) ?? list[0]
    : titles.find(item => item.id === selected) ?? titles[0];

  function changeList(setValue, value) {
    if (winter) setSelected(null);
    setValue(value);
  }

  const toolbar = (
    <>
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
              onClick={() => { if (filter !== id) changeList(setFilter, id); }}
            >
              {label}
            </button>
          ))}
        </div>
        {!winter && <IconButton
          label={compact ? "切换卡片显示" : "切换紧凑显示"}
          onClick={() => setCompact(!compact)}
        >
          {compact ? <Grid2X2 size={18} /> : <List size={18} />}
        </IconButton>}
      </div>
      <div className="search-row">
        <label className="search">
          <Search size={16} />
          <input
            value={query}
            onChange={(e) => changeList(setQuery, e.target.value)}
            placeholder="搜索称号"
            aria-label="搜索称号"
          />
          {query && (
            <button aria-label="清空搜索" onClick={() => changeList(setQuery, "")}>
              <X size={14} />
            </button>
          )}
        </label>
        <select
          aria-label="称号排序"
          className={winter ? "album-sort" : undefined}
          onKeyDownCapture={winter ? e => {
            if (e.key === "Escape" && e.currentTarget.matches(":open")) e.stopPropagation();
          } : undefined}
          value={sort}
          onChange={(e) => changeList(setSort, e.target.value)}
        >
          <option value="recent">最近获得</option>
          <option value="name">名称排序</option>
        </select>
      </div>
    </>
  );
  const emptyState = (
    <div className="empty-state">
      <Search size={27} />
      <h3>没有找到这个称号</h3>
      <p>换个关键词，或查看全部称号。</p>
      <button
        className="btn subtle"
        onClick={() => {
          changeList(setQuery, "");
          setFilter("all");
        }}
      >
        查看全部
      </button>
    </div>
  );
  const equipButton = t?.owned ? (
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
  );
  function changeTitleVisibility(visible) {
    update({ visible });
    notify(visible ? "已开启称号展示" : "已隐藏称号展示，佩戴状态保留");
  }
  const visibility = winter ? (
    <button type="button" className="album-visibility" role="switch"
      aria-label="展示给其他玩家" aria-checked={state.visible}
      title={state.visible ? "其他玩家可见，点击隐藏" : "称号已隐藏，点击公开"}
      onClick={() => changeTitleVisibility(!state.visible)}>
      {state.visible ? <Eye size={17} /> : <EyeOff size={17} />}
    </button>
  ) : (
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
        onChange={changeTitleVisibility}
        label="展示给其他玩家"
      />
    </div>
  );
  if (winter) {
    return <WinterTitleCase title={t} items={list} onSelect={setSelected}
      equipped={state.equipped} visible={state.visible} toolbar={toolbar}
      listKey={JSON.stringify([filter, sort, query.trim()])} reduceMotion={reduceMotion}
      emptyState={emptyState} equipButton={equipButton} visibility={visibility} titleText={<TitleText title={t} />} />;
  }
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
          {toolbar}
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
          {!list.length && emptyState}
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
            {equipButton}
            {visibility}
          </div>
        </aside>
      </div>
    </>
  );
}

function FortunesPage({ archive, state, setReading, update, notify }) {
  const currentMonth = new Date(TODAY.getFullYear(), TODAY.getMonth(), 1);
  const [month, setMonth] = useState(currentMonth);
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
  const dates = Array.from({ length: cells }, (_, i) => {
    const day = i - start + 1;
    if (day < 1 || day > days) return null;
    const value = new Date(month.getFullYear(), month.getMonth(), day);
    const key = dateKey(value);
    return { day, key, f: archive[key], future: value > TODAY };
  });
  const shift = (amount) => {
    const d = new Date(month.getFullYear(), month.getMonth() + amount, 1);
    setMonthDirection(Math.sign(amount));
    setMonth(d);
    const prefix = dateKey(d).slice(0, 7);
    setSelected(Object.keys(archive).filter(date => date.startsWith(prefix)).sort().at(-1) || dateKey(d));
  };
  const display = archive[state.displayDate];
  const returnToToday = () => {
    setMonthDirection(1);
    setMonth(currentMonth);
    setSelected(todayKey);
  };
  const showSelected = async () => {
    if (!selectedFortune || state.displayDate === selected) return;
    if (await update({ displayDate: selected })) notify("已更新手册展示的签笺");
  };
  if (state.homeStyle === "winter") {
    return <WinterFortuneCounter month={month} dates={dates} stored={stored} total={Object.keys(archive).length}
      selected={selected} fortune={selectedFortune} display={display} mode={mode} monthDirection={monthDirection}
      atCurrentMonth={month >= currentMonth} onMonth={shift} onToday={returnToToday} onMode={setMode}
      onSelect={setSelected} onRead={setReading} onDisplay={showSelected} />;
  }
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
                onClick={returnToToday}
              >
                本月
              </button>
              <IconButton label="上个月" onClick={() => shift(-1)}>
                <ChevronLeft size={18} />
              </IconButton>
              <IconButton
                label="下个月"
                disabled={month >= currentMonth}
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
                {dates.map((entry, i) => {
                  if (!entry) return <span className="calendar-blank" key={i} />;
                  const { day, key, f, future } = entry;
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
          onClick={showSelected}
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
