import React, { useEffect, useLayoutEffect, useRef, useState } from "react";
import { ArrowLeft, Check, ChevronRight, Inbox, LockKeyhole, Pin, Send, Trash2 } from "lucide-react";
import { motion, useIsPresent } from "motion/react";
import { player } from "../data.js";
import { HUB_PREVIEW_STORAGE_KEY, readHubPreviewState } from "../community/demo.js";
import { previewAppearance } from "../previewAppearance.js";
import { BoatMark, Glyph } from "./Glyph.jsx";
import { ItemReference, ItemTooltipScope } from "./ItemReference.jsx";
import { itemById } from "./itemCatalog.js";
import { blockDetail, blockLabel } from "./blockSamples.js";
import { lookupPreview, previewSessionTitle } from "./guide.js";
import Answer from "./WzAnswer.jsx";
import {modelLabel} from "./modelPreview.js";
import "./shares.css";

const base = "pixelmc-wenzhou-web-shares:v1:";
const dateFormat = new Intl.DateTimeFormat("zh-CN", {year:"numeric",month:"2-digit",day:"2-digit"});
const timeFormat = new Intl.DateTimeFormat("zh-CN", {hour:"2-digit",minute:"2-digit"});
const scopeKey = demo => base + (demo ? "demo:" : "local:");
const entryPrefix = (scope, playerId) => scope + "inbox:" + playerId + ":";
const get = key => JSON.parse(localStorage.getItem(key) || "null");
function saveSnapshot(scope, snap) {
  const key = scope + "snapshot:" + snap.id;
  if (!localStorage.getItem(key)) localStorage.setItem(key, JSON.stringify(snap));
}
function receive(scope, playerId, snap, receivedAt = Date.now(), pinned = false) {
  const key = entryPrefix(scope, playerId) + snap.id;
  if (!localStorage.getItem(key)) localStorage.setItem(key, JSON.stringify({id:snap.id, receivedAt, pinned}));
}
function list(scope, playerId) {
  const prefix = entryPrefix(scope, playerId);
  return Object.keys(localStorage).filter(k => k.startsWith(prefix)).map(k => {
    const entry = get(k);
    const snapshot = entry && get(scope + "snapshot:" + entry.id);
    return snapshot && {...entry, snapshot};
  }).filter(Boolean).sort((a,b) => Number(b.pinned)-Number(a.pinned) || b.receivedAt-a.receivedAt);
}
export function freezeConversation(session, sender) {
  const turns = session.turns.filter(t => t.reply && !t.stopped && !t.itemChoice && !t.topicPrompt).map(t => ({
    question:t.question, model:modelLabel(t.model), reply:structuredClone(t.reply),
    parts:t.questionParts?.map(p => typeof p === "string" ? p : p.block ?
      {label:blockLabel(p), detail:blockDetail(p)} : {item:p.item}),
  }));
  return {id:crypto.randomUUID(), title:previewSessionTitle(session), sender, turns};
}
const models = snap => [...new Set(snap.turns.map(t => t.model))].join(" / ");
function seed(scope, playerId) {
  // Keep this marker outside the entry namespace.
  const marker = scope + "seeded:" + playerId;
  if (localStorage.getItem(marker)) return;
  const now = Date.now();
  const examples = [
    ["anvil","两本效率 III 怎么合成效率 IV？","小满","Terra","铁砧",true],
    ["ae2","压印器的管道该接哪一面？","阿舟","DeepSeek","AE2 压印器",false],
    ["torch","木炭也能做火把吗？","星野","Sol","火把",false],
  ];
  examples.forEach(([id, title, sender, model, topic, pinned], i) => {
    const snap = {id:"example-" + id, title, sender, turns:[{question:title, model, reply:lookupPreview(topic)}]};
    saveSnapshot(scope,snap);
    receive(scope,playerId,snap,now-i*86400000,pinned);
  });
  localStorage.setItem(marker,"1");
}
function Snapshot({snap, preview}) {
  return <article className="wzs-content">
    <div className="wzs-summary">
      <span className="wzs-type"><LockKeyhole size={13}/>{preview ? "分享预览" : "只读快照"}</span>
      <h1>{snap.title}</h1>
      <p>{snap.sender} 分享<span>·</span>{models(snap)}<span>·</span>{snap.turns.length} 轮问答</p>
    </div>
    <ItemTooltipScope>{snap.turns.map((turn,i) => <section className="wz-turn wzs-turn" key={i}>
      <div className="wz-question">{turn.parts?.length ? turn.parts.map((p,j) => typeof p === "string" ? p :
        p.item && itemById.has(p.item) ? <ItemReference key={j} stack={itemById.get(p.item)}/> :
        <span key={j} title={p.detail}>{p.label || p.item}</span>) : turn.question}</div>
      <div className="wz-speaker"><BoatMark/><strong>问舟</strong><small>{turn.model}</small></div>
      <Answer reply={turn.reply} appear={false}/>
    </section>)}</ItemTooltipScope>
  </article>;
}
export function SharePanel({initial, playerId, demo, onBack, notify, reduceMotion}) {
  const present = useIsPresent();
  const scope = scopeKey(demo);
  const [rows,setRows] = useState([]);
  const [detail,setDetail] = useState(initial.snapshot || null);
  const [remove,setRemove] = useState(null);
  const [published,setPublished] = useState(false);
  const heading = useRef(null);
  const listOpener = useRef(null);
  const preview = initial.page === "preview";
  const url = detail && "?snapshot=" + encodeURIComponent(detail.id) + (demo ? "&demo=shares" : "");
  useEffect(() => {
    function refresh() { try { setRows(list(scope,playerId)); } catch { notify("未能读取收到的分享","error"); } }
    if (demo) seed(scope,playerId);
    refresh();
    const update = e => { if (!e.key || e.key.startsWith(scope)) refresh(); };
    window.addEventListener("storage",update);
    return () => window.removeEventListener("storage",update);
  },[scope,playerId,demo,notify]);
  function back() {
    if (remove) { setRemove(null); return; }
    if (detail && !preview) { setDetail(null); return; }
    onBack();
  }
  useEffect(() => {
    if (!present) return;
    const esc = e => { if (e.key === "Escape" && !e.defaultPrevented) { e.preventDefault(); e.stopPropagation(); back(); } };
    document.addEventListener("keydown",esc);
    return () => document.removeEventListener("keydown",esc);
  });
  useEffect(() => {
    if (!detail && listOpener.current) document.getElementById(listOpener.current)?.focus();
    else heading.current?.focus({preventScroll:true});
  },[detail]);
  function change(entry, erase) {
    try {
      const key = entryPrefix(scope,playerId) + entry.id;
      if (erase) { localStorage.removeItem(key); setRemove(null); notify("已从收到的分享中移除"); }
      else { const saved = get(key); if (saved) localStorage.setItem(key,JSON.stringify({...saved,pinned:!saved.pinned})); }
      setRows(list(scope,playerId));
    } catch { notify("未能保存修改，请重试","error"); }
  }
  function publish() {
    try { saveSnapshot(scope,detail); setPublished(true); }
    catch { notify("未能保存快照，请重试","error"); }
  }
  return <motion.div className="wzs-panel" inert={!present}
    initial={reduceMotion ? false : {opacity:0, x:14}} animate={{opacity:1, x:0}}
    exit={reduceMotion ? {opacity:0} : {opacity:0, x:10}}>
    <header className="topbar wz-bar"><button ref={heading} className="wz-back" onClick={back}><ArrowLeft size={17}/>
      {detail && !preview ? "收到的分享" : "返回问舟"}</button></header>
    <motion.div key={detail?.id || "inbox"} className="wzs-scroll wz-scroll"
      initial={!reduceMotion && listOpener.current ? {opacity:0, x:detail ? 10 : -10} : false}
      animate={{opacity:1, x:0}}>
      {detail ? <Snapshot snap={detail} preview={preview}/> : <div className="wzs-content">
        <div className="wzs-list-heading"><h1>收到的分享</h1><span>{rows.length} 条</span></div>
        {!rows.length && <div className="wzs-empty"><Inbox size={29}/><p>还没有收到的分享</p><small>点开玩家在聊天里分享的对话，就会保存在这里。</small></div>}
        {rows.map(row => <div key={row.id} className="wzs-row">
          <div className="wzs-line"><button className="wzs-open" id={"wzs-open-"+row.id} onClick={() => {
            listOpener.current = "wzs-open-"+row.id; setRemove(null); setDetail(row.snapshot);
          }}>
            <span className="wzs-cover"><Glyph name="book" size={21}/></span>
            <span className="wzs-row-copy"><strong>{row.snapshot.title}</strong><small>{row.snapshot.sender} 分享 · {models(row.snapshot)}</small></span>
            <time dateTime={new Date(row.receivedAt).toISOString()} title={dateFormat.format(row.receivedAt)+" "+timeFormat.format(row.receivedAt)+" 接收"}>{dateFormat.format(row.receivedAt)}</time>
          </button>
          <button className="wzs-icon" aria-label={(row.pinned ? "取消置顶" : "置顶")+row.snapshot.title} title={row.pinned ? "取消置顶" : "置顶"} aria-pressed={row.pinned} onClick={() => change(row,false)}><Pin size={15}/></button>
          <button className="wzs-icon" aria-label={"删除"+row.snapshot.title} title="删除" onClick={() => setRemove(row.id)}><Trash2 size={15}/></button></div>
          {remove===row.id && <div className="wz-confirm"><span>从你的列表移除这条分享？</span><button onClick={() => setRemove(null)}>取消</button><button className="wz-confirm-delete" onClick={() => change(row,true)}>确认删除</button></div>}
        </div>)}
      </div>}
    </motion.div>
    {preview && <footer className="wzs-footer"><div>{published ? <>
      <span role="status"><Check size={16}/> 快照已生成 · 本地预览</span>
      <a className="wzs-primary" href={url} target="_blank" rel="noreferrer">查看接收效果<ChevronRight size={15}/></a>
    </> : <>
      <span>公开当前已完成的问答，后续追问不会加入快照。</span>
      <button className="wzs-primary" onClick={publish}><Send size={15}/>分享至聊天区</button>
    </>}</div></footer>}
  </motion.div>;
}
export function ExternalShare() {
  const params = new URLSearchParams(location.search);
  const scope = scopeKey(params.get("demo")==="shares");
  const [snap] = useState(() => get(scope+"snapshot:"+params.get("snapshot")));
  const [saveFailed,setSaveFailed] = useState(false);
  const readAppearance = () => {
    try { return previewAppearance(readHubPreviewState()); }
    catch { return previewAppearance({}); }
  };
  const [appearance, setAppearance] = useState(readAppearance);
  useLayoutEffect(() => { document.documentElement.dataset.theme = appearance.theme; }, [appearance.theme]);
  useEffect(() => {
    const refresh = event => {
      if (!event.key || event.key === HUB_PREVIEW_STORAGE_KEY) setAppearance(readAppearance());
    };
    window.addEventListener("storage", refresh);
    return () => window.removeEventListener("storage", refresh);
  }, []);
  useEffect(() => { if (snap) try { receive(scope,player.uuid,snap); } catch { setSaveFailed(true); } },[scope,snap]);
  return <div className={`preview-stage${appearance.homeStyle === "winter" ? " winter-theme" : ""}`}><div className="app-shell handbook-routes"><section className="wz-page">
    <header className="topbar wz-bar"><a className="wzs-close" href="/#/home"><ArrowLeft size={17}/>返回手册</a></header>
    <div className="wzs-scroll wz-scroll">{snap ? <Snapshot snap={snap}/> : <div className="wzs-content"><h1>这条分享无法打开</h1><p>预览快照只能在生成它的浏览器中查看。</p></div>}
    {saveFailed && <p role="status">浏览器未能保存这条接收记录。</p>}</div>
  </section></div></div>;
}
