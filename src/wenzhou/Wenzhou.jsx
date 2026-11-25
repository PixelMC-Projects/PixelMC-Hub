import React, { useCallback, useEffect, useId, useLayoutEffect, useRef, useState, useSyncExternalStore } from "react";
import { AnimatePresence, MotionConfig, motion, useIsPresent } from "motion/react";
import HubRouteSurface from "./HubRouteSurface.jsx";
import { BoatMark, Glyph } from "./Glyph.jsx";
import { examples, previewEvents, previewMemory, previewSessionTitle } from "./guide.js";
import { ItemReference, ItemTooltipScope } from "./ItemReference.jsx";
import Answer from "./WzAnswer.jsx";
import { SharePanel, freezeConversation } from "./Shares.jsx";
import { Inbox, Share2 } from "lucide-react";
import InlineQuestion, { questionText } from "./InlineQuestion.jsx";
import { ItemPicker } from "./ItemPicker.jsx";
import ReferenceTools from "./ReferenceTools.jsx";
import BlockPicker from "./BlockPicker.jsx";
import FirstGuide from "./FirstGuide.jsx";
import { blockDetail, blockIcon, blockLabel } from "./blockSamples.js";
import { itemById } from "./itemCatalog.js";
import UsageCost from './UsageCost.jsx';
import ModelPicker from './ModelPicker.jsx';
import {DEEPSEEK, TERRA, effectiveEffort, modelLabel, modelMode, selectedModel,
  ownerModelView, terraAllowed} from './modelPreview.js';
import {demoTerraReceipt} from './usage.js';
import {player as previewOwner} from '../data.js';
import QuotaIndicator from './QuotaIndicator.jsx';
import { getPreviewQuota, getPreviewTerraQuota, previewQuotaSettings, startPreviewQuota, terraQuotaChanged } from './quotaPreview.js';
import { ReminderSettings, reminderEnabled } from "./ReminderSettings.jsx";
import Agreement from "./Agreement.jsx";

import { currentPolicy, policyKey, showsPlayerAgreement, visibilityKey, prefix, archivePrefix, readValue, receiptKey as previewEntryKey, historyKey as storageKey, subscribePreview, terraPreviewProjectAvailable, writeValue, archiveSessions } from './managerPreview.js';

function readPreviewReceipt() {
  try {
    const receipt = JSON.parse(localStorage.getItem(previewEntryKey));
    return receipt?.version === currentPolicy().version && receipt.terms === true && receipt.api === true
      && Number.isFinite(receipt.acceptedAt) ? receipt : null;
  }
  catch { return null; }
}

function exampleInbox() {
  const now = Date.now();
  return [
    ["酥皮什么时候放？", "酥皮留到取餐时用，放进厨锅的容器槽。"],
    ["蘑菇煲要用什么工具处理？", "把蘑菇煲放在切菜板上，再用配方指定的权杖处理。"],
    ["工作台怎么做？", "打开背包，在四个合成格里各放一块木板。"],
  ].map(([question, text], i) => {
    const updated = now - i * 60000;
    return {id: "demo-" + i, updated, unread: i < 2, turns: [
      {id: "example-" + i, createdAt: updated, question, reply: {text}, events: [], usageReceipts: []},
    ]};
  });
}

function loadHistory() {
  try {
    const data = JSON.parse(localStorage.getItem(storageKey) || "[]");
    return Array.isArray(data) ? data.filter(row => row?.id && Array.isArray(row.turns) && row.turns.length) : [];
  } catch { return []; }
}

// Include removed conversations because removing a history entry does not refund its usage.
const noQuota = {enabled:false, exhausted:false};
function playerQuotas(playerId, mode) {
  if (!previewQuotaSettings().quotaEnabled && !terraAllowed(mode)) return {deepSeek:getPreviewQuota(playerId), terra:noQuota};
  const receipts = [];
  for (let index = 0; index < localStorage.length; index++) {
    const key = localStorage.key(index);
    if (!key?.startsWith(archivePrefix)) continue;
    const thread = readValue(key);
    for (const turn of thread?.turns || []) {
      for (const receipt of turn.usageReceipts || []) {
        receipts.push({...receipt, at: receipt.at || turn.createdAt || thread.updated});
      }
    }
  }
  return {deepSeek:getPreviewQuota(playerId, receipts), terra:getPreviewTerraQuota(playerId, mode, receipts)};
}

function quotaFor(model, quotas) {
  if (model === TERRA) return quotas.terra;
  return !model || model === DEEPSEEK ? quotas.deepSeek : noQuota;
}

function applyEvent(turn, event) {
  switch (event.type) {
    case "usage":
      if (turn.usageReceipts.some(row => row.id === event.receipt.id)) return turn;
      return { ...turn, usageReceipts: [...turn.usageReceipts, event.receipt] };
    case "item.choice":
      return { ...turn, itemChoice: {prompt: event.prompt, options: event.options}, reply: {text: event.prompt} };
    case "topic.confirm":
      return { ...turn, topicPrompt: event.text };
    case "stage.report":
      if (!event.text?.trim() || turn.events.some(row => row.type === event.type && row.id === event.id)) return turn;
      return {...turn, events: [...turn.events, {type:event.type, id:event.id, text:event.text.trim()}]};
    case "progress": {
      const row = {type: "progress", id: event.id, text: event.text};
      if (row.id && turn.events.some(old => old.id === row.id))
        return {...turn, events: turn.events.map(old => old.id === row.id ? row : old)};
      return {...turn, events: [...turn.events, row]};
    }
    case "tool.start":
      return { ...turn, events: [...turn.events, { type: event.type, id: event.id, icon: event.icon, text: event.text }] };
    case "tool.finish":
      return { ...turn, events: turn.events.map(row =>
        row.type === "tool.start" && row.id === event.id ? { ...row, type: event.type, text: event.text, detail: event.detail } : row) };
    case "answer":
      return { ...turn, reply: event.reply };
    default:
      return turn;
  }
}

const ease = [0.22, 0.68, 0.18, 1];
const visible = { opacity: 1, y: 0 };
const enter = { opacity: 0, y: 9 };
const leave = { opacity: 0, y: -5 };

function Pane({ children, reduceMotion, nodeRef, ...props }) {
  const present = useIsPresent();
  return <motion.div {...props} ref={nodeRef} inert={!present}
    initial={reduceMotion ? false : enter} animate={visible} exit={reduceMotion ? visible : leave}>
    {children}
  </motion.div>;
}

function eventLabel(row, turn, running) {
  if (!row) return "";
  if (row.type === "progress") {
    if (row.text?.startsWith("思考 · ")) return row.text;
    if (turn.reply || row !== turn.events.at(-1)) return "已思考";
    return running ? "思考中" : "思考已中断";
  }
  return !running && row.type === "tool.start" ? "查询已中断" : row.text;
}

function Timeline({ turn, running, reduceMotion }) {
  const finished = Boolean(turn.reply);
  const [expanded, setExpanded] = useState(null);
  const open = expanded?.finished === finished ? expanded.open : !finished;
  if (!turn.events.length) return null;
  return <div className="wz-timeline">
    <button type="button" className="wz-disclosure" aria-expanded={open} onClick={() => setExpanded({finished, open:!open})}>
      <Glyph name="book" size={16} />{running ? "问舟正在查阅" : turn.reply ? "工作流程" : "未完成的查阅"}
      <Glyph name="chevron" size={13} className={open ? "wz-open-chevron" : ""} />
    </button>
    <AnimatePresence initial={false}>
    {open && <motion.div className="wz-event-clip" initial={{ height: 0, opacity: 0 }}
      animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }}>
      <ol className="wz-events">{turn.events.map((row, index) =>
      <motion.li key={row.id || index} initial={running && !reduceMotion ? enter : false} animate={visible}
        className={row.type === "stage.report" ? "wz-stage-report" : running && row.type === "tool.start" ? "wz-running" : ""}>
        {row.type === "stage.report" ? null : row.type === "progress" ? <i aria-hidden="true" /> : <Glyph name={row.icon} size={16} />}
        <span>{eventLabel(row, turn, running)}</span>
        {row.type === "tool.finish" && row.icon !== "command" && row.detail && <small>{row.detail}</small>}
      </motion.li>)}</ol></motion.div>}
    </AnimatePresence>
  </div>;
}

function ItemChoice({choice, busy, exhausted, submit, manual}) {
  const [selected, setSelected] = useState("");
  return <fieldset className="wz-item-choice" disabled={busy}>
    <legend className="wz-sr">{choice.prompt}</legend><strong>{choice.prompt}</strong>
    {choice.options.map(id => {
      const item = itemById.get(id);
      return <label key={id}><img src={item?.icon} alt=""/><span><b>{item?.name || id}</b><small>{item?.mod} · {id}</small></span>
        <input type="radio" name="wz-item-candidate" checked={selected === id} onChange={() => setSelected(id)}/></label>;
    })}
    <footer><button type="button" onClick={manual}>我来指定物品</button>
      <button type="button" disabled={!selected || exhausted} onClick={() => submit(selected)}>发送</button></footer>
  </fieldset>;
}

function TopicChoice({ prompt, onChoose, busy }) {
  return <div className="wz-topic-choice" role="group" tabIndex={-1} aria-label={prompt}>
    <p>{prompt}</p>
    <div>
      <button disabled={busy} onClick={() => onChoose(false)}>有关，接着聊</button>
      <button disabled={busy} onClick={() => onChoose(true)}>无关，新开对话</button>
    </div>
  </div>;
}

function ChatHistory({ sessions, running, showUnread, onChoose, onDelete }) {
  const [confirm, setConfirm] = useState(null);
  const heading = useRef(null);
  const cancelButton = useRef(null);
  const opener = useRef(null);
  useEffect(() => {
    if (confirm) cancelButton.current?.focus();
    else if (opener.current?.isConnected) opener.current.focus();
    else if (opener.current) heading.current?.focus({ preventScroll: true });
  }, [confirm]);
  return <div onKeyDown={e => {
    if (confirm && e.key === "Escape") { e.stopPropagation(); e.preventDefault(); setConfirm(null); }
  }}>
    <h1 ref={heading} tabIndex={-1}>历史对话</h1>
    {!sessions.length && <p>还没有历史对话。</p>}
    {sessions.map(row => {
      const title = previewSessionTitle(row);
      return <motion.div key={row.id} layout="position" className="wz-history-row">
        <div className="wz-history-line">
          <button className="wz-history-open" onClick={() => onChoose(row.id)}>
            <Glyph name="book" /><span className="wz-history-title">{showUnread && row.unread && <i className="wz-unread-dot" role="img" aria-label="未读"/>}{title}</span>
            {running && row.turns.at(-1)?.id === running && <small>生成中</small>}
            {row.turns.at(-1)?.topicPrompt && <small>待选择</small>}
            <time>{new Intl.DateTimeFormat("zh-CN", { month: "numeric", day: "numeric" }).format(row.updated)}</time>
            <Glyph name="chevron" size={14} />
          </button>
          <button className="wz-delete" aria-label={"删除对话「" + title + "」"} title="删除对话"
            aria-expanded={confirm === row.id} onClick={e => { opener.current = e.currentTarget; setConfirm(row.id); }}>
            <Glyph name="trash" size={16} />
          </button>
        </div>
        <AnimatePresence initial={false}>
          {confirm === row.id && <motion.div className="wz-confirm-clip" initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }}>
            <div className="wz-confirm" role="group" aria-label="确认删除对话">
              <span>删除这段对话？删除后无法恢复。</span>
              <button ref={cancelButton} onClick={() => setConfirm(null)}>取消</button>
              <button className="wz-confirm-delete" onClick={() => { onDelete(row.id); setConfirm(null); }}>确认删除</button>
            </div>
          </motion.div>}
        </AnimatePresence>
      </motion.div>;
    })}
  </div>;
}

const landingGreetings = [
  "哎，碰到你啦！",
  "今天的风刚刚好。",
  "晃悠，晃悠。",
  "刚跟一条鱼打了个照面。",
  "嘿，给你留了个位置。",
  "我刚才漂到哪儿来着？",
  "扑通！哦，是颗小石子。",
  "好巧，你也在这儿。",
];

function pickGreeting(previous) {
  const choices = landingGreetings.filter(line => line !== previous);
  return choices[Math.floor(Math.random() * choices.length)];
}

export default function Wenzhou({ playerId, active, hubClosed, blocked, demo = false, reduceMotion, onBack, notify }) {
  const [shareView, setShareView] = useState(() => demo === "shares" ? {page:"inbox"} : null);
  const shareOpener = useRef(null);
  function closeShares() {
    setShareView(null);
    requestAnimationFrame(() => document.getElementById(shareOpener.current)?.focus({preventScroll:true}));
  }
  const mode = useSyncExternalStore(subscribePreview, () => modelMode(playerId));
  const currentModel = useSyncExternalStore(subscribePreview, () => selectedModel(playerId, mode));
  const projectAvailable = useSyncExternalStore(subscribePreview, terraPreviewProjectAvailable);
  const showTerra = terraAllowed(mode, projectAvailable);
  const terraSelected = currentModel === TERRA;
  const chatGptSelected = currentModel !== DEEPSEEK && !terraSelected;
  const [policy, setPolicy] = useState(currentPolicy);
  const archived = useRef([]);
  const [quotas, setQuotas] = useState(() => playerQuotas(playerId, mode));
  const quota = quotaFor(currentModel, quotas);
  const quotaExhausted = quota.exhausted;
  useEffect(() => {
    const reload = () => setQuotas(playerQuotas(playerId, mode));
    reload();
    return subscribePreview(key => {
      if (!key || key.startsWith(prefix + 'quota') || key.startsWith(archivePrefix) || terraQuotaChanged(key, playerId)) reload();
    });
  }, [playerId, mode, projectAvailable]);
  useEffect(() => {
    if (!quota.enabled || !quota.resetAt) return;
    const timeout = setTimeout(() => setQuotas(playerQuotas(playerId, mode)), Math.max(0, quota.resetAt - Date.now()) + 1);
    return () => clearTimeout(timeout);
  }, [playerId, mode, quota.enabled, quota.resetAt]);
  const [receipt, setReceipt] = useState(readPreviewReceipt);
  const [agreementVisible, setAgreementVisible] = useState(() => !demo && showsPlayerAgreement());
  const enteredPreview = !agreementVisible || Boolean(receipt);
  const [reviewingTerms, setReviewingTerms] = useState(false);
  const agreementLink = useRef(null);
  const restoreAgreementFocus = useRef(false);
  const showAgreement = agreementVisible && (!receipt || reviewingTerms);
  const [guide, setGuide] = useState(null);
  const [guideLeaving, setGuideLeaving] = useState(false);
  const seenHere = useRef(null);
  const replayButton = useRef(null);
  const replaying = useRef(false);
  useLayoutEffect(() => {
    if (!active || showAgreement) { setGuideLeaving(false); setGuide(null); return; }
    const key = prefix + "intro-seen:" + playerId;
    if (demo || seenHere.current === key || readValue(key, false) === true) return;
    seenHere.current = key;
    replaying.current = false;
    setGuide(0);
  }, [active, showAgreement, playerId]);
  useEffect(() => {
    if (active && !showAgreement && guide === 0) {
      try { writeValue(prefix + "intro-seen:" + playerId, true); }
      catch { /* The current visit is still covered if browser storage is unavailable. */ }
    }
  }, [guide, active, showAgreement, playerId]);
  function closeGuide() {
    if (reduceMotion) finishGuide();
    else setGuideLeaving(true);
  }
  function finishGuide() {
    setGuideLeaving(false);
    setGuide(null);
    requestAnimationFrame(() => (replaying.current ? replayButton.current : input.current)?.focus({preventScroll:true}));
  }
  const guidanceId = useId();
  const [sessions, setSessions] = useState(() => demo ? exampleInbox() : loadHistory());
  const [selected, setSelected] = useState(null);
  const [viewHistory, setViewHistory] = useState(false);
  const [greeting, setGreeting] = useState(pickGreeting);
  const [draft, setDraft] = useState([]);
  const [running, setRunning] = useState(null);
  const request = useRef(null);
  const newest = useRef(null);
  const input = useRef(null);
  const composerForm = useRef(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [selectingBlock, setSelectingBlock] = useState(false);
  function openBlockPicker() {
    input.current?.remember();
    setPickerOpen(false);
    setSelectingBlock(true);
  }
  const finishBlockPicker = useCallback(block => {
    setSelectingBlock(false);
    if (block && input.current?.insert(block) === false) notify("输入框已达字数上限，未插入方块。");
    else if (!block) input.current?.restore();
  }, [notify]);
  function openPicker() {
    input.current?.remember();
    setPickerOpen(true);
  }
  const closePicker = useCallback(restoreFocus => {
    setPickerOpen(false);
    if (restoreFocus) input.current?.focus({preventScroll: true});
  }, []);
  useEffect(() => {
    if (!active || viewHistory || showAgreement) { setPickerOpen(false); setSelectingBlock(false); }
  }, [active, viewHistory, showAgreement]);
  function pickItem(item) {
    input.current?.insert(item);
    closePicker(true);
  }
  const scroll = useRef(null);
  const pinned = useRef(true);
  const [tabVisible, setTabVisible] = useState(() => !document.hidden);
  useEffect(() => {
    const update = () => setTabVisible(!document.hidden);
    document.addEventListener("visibilitychange", update);
    return () => document.removeEventListener("visibilitychange", update);
  }, []);
  const wasOpen = useRef(false);
  const showUnread = useSyncExternalStore(subscribePreview, () => reminderEnabled(playerId, "unread"));
  const closedRef = useRef(hubClosed);
  closedRef.current = hubClosed;
  const hasUnread = showUnread && sessions.some(row => row.unread);
  const session = sessions.find(row => row.id === selected);
  const turns = session?.turns || [];
  const lastTurn = turns.at(-1);
  const busy = Boolean(running);
  const showingRun = busy && lastTurn?.id === running;
  const waiting = Boolean(lastTurn?.topicPrompt);
  useLayoutEffect(() => {
    const entering = active && !wasOpen.current;
    wasOpen.current = active;
    if (!active) return;
    if (entering) {
      const ongoing = running && sessions.find(row => row.turns.at(-1)?.id === running);
      const latest = sessions.reduce((best, row) =>
        row.unread && (!best || row.updated > best.updated) ? row : best, null);
      const target = ongoing || latest;
      if ((target?.id ?? null) !== selected) {
        choose(target?.id ?? null);
        return;
      }
    }
    if (session?.unread && !shareView && tabVisible && !blocked && !viewHistory && !showAgreement && guide === null && !selectingBlock) {
      setSessions(rows => rows.map(row => row.id === selected ? {...row, unread:false} : row));
    }
  }, [active, selected, running, sessions, tabVisible, blocked, viewHistory, showAgreement, guide, selectingBlock, shareView]);
  const notifyRef = useRef(notify);
  notifyRef.current = notify;
  useEffect(() => () => request.current?.abort(), []);
  useEffect(() => {
    return subscribePreview(key => {
      if (key && key !== previewEntryKey && key !== policyKey && key !== visibilityKey) return;
      const visible = !demo && showsPlayerAgreement();
      setAgreementVisible(visible);
      setPolicy(currentPolicy());
      const next = readPreviewReceipt();
      setReceipt(next);
      if (!visible || !next) setReviewingTerms(false);
      if (visible && !next) {
        request.current?.abort();
        request.current = null;
        setRunning(null);
      }
    });
  }, []);
  useEffect(() => {
    try {
      if (demo) return;
      archiveSessions(sessions, archived.current);
      archived.current = sessions;
      if (sessions.length) localStorage.setItem(storageKey, JSON.stringify(sessions));
      else localStorage.removeItem(storageKey);
    }
    catch { notifyRef.current("浏览器未能保存对话。这次仍可在手册里继续聊。", "error"); }
  }, [sessions]);
  useEffect(() => {
    if (!active) { setShareView(null); newest.current = null; setViewHistory(false); setReviewingTerms(false); return; }
    if (showAgreement || viewHistory || shareView) return;
    if (restoreAgreementFocus.current) {
      restoreAgreementFocus.current = false;
      agreementLink.current?.focus({ preventScroll: true });
      return;
    }
    const target = waiting ? scroll.current?.querySelector(".wz-topic-choice") : input.current;
    target?.focus({ preventScroll: true });
  }, [active, showAgreement, selected, viewHistory, waiting]);
  const followAnswer = useCallback(() => {
    if (pinned.current && scroll.current) scroll.current.scrollTop = scroll.current.scrollHeight;
  }, []);
  useLayoutEffect(() => {
    if (active && !showAgreement && !viewHistory) followAnswer();
  }, [lastTurn, active, viewHistory, showAgreement, followAnswer]);

  function enterPreview(research) {
    const acceptedAt = Date.now();
    if (!showsPlayerAgreement() || currentPolicy().version !== policy.version) return;
    const next = { version: policy.version, acceptedAt, terms: true, api: true, research, researchUpdatedAt: acceptedAt };
    try {
      writeValue(previewEntryKey, next);
    }
    catch { notify("浏览器未能保存选择，本次预览仍可使用。", "error"); }
    setReceipt(next);
  }

  function changeResearchConsent(research) {
    if (!receipt || currentPolicy().version !== receipt.version) return;
    const next = { ...receipt, research, researchUpdatedAt: Date.now() };
    try {
      writeValue(previewEntryKey, next);
      setReceipt(next);
    } catch { notify("浏览器未能保存修改，请重试。", "error"); }
  }

  function closeReview() {
    restoreAgreementFocus.current = true;
    setReviewingTerms(false);
  }

  function choose(id) {
    if (id === null) setGreeting(pickGreeting);
    newest.current = null;
    setSelected(id);
    setPickerOpen(false);
    setDraft([]);
    setViewHistory(false);
    pinned.current = true;
  }
  function remove(id) {
    const row = sessions.find(item => item.id === id);
    if (row?.turns.some(turn => turn.id === running)) {
      request.current?.abort();
      request.current = null;
      setRunning(null);
    }
    if (id === selected) { setSelected(null); setPickerOpen(false); setDraft([]); }
    setSessions(prev => prev.filter(item => item.id !== id));
    notify("已删除这段对话");
  }
  function send(raw, choosing = false) {
    const parts = typeof raw === "string" ? [raw] : raw;
    const question = questionText(parts).trim();
    if (!question || showAgreement || request.current || waiting || (lastTurn?.itemChoice && !choosing)) return;
    const model = selectedModel(playerId);
    if (quotaFor(model, playerQuotas(playerId, modelMode(playerId))).exhausted) {
      notify(model === TERRA ? "Terra 美元额度已用完，可以切换到 DeepSeek。" : "本周额度已用完，可在输入栏左侧查看额度状态。");
      return;
    }
    const id = selected || crypto.randomUUID();
    const consent = readPreviewReceipt();
    if (!demo && showsPlayerAgreement() && !consent) return;
    if (!demo && model === DEEPSEEK) startPreviewQuota(playerId);
    const turn = {
      id: crypto.randomUUID(), createdAt: Date.now(), question, questionParts: parts, model, reasoningEffort: effectiveEffort(playerId, model), events: [], usageReceipts: [], reply: null,
      researchAuthorizedAt: consent?.research ? consent.researchUpdatedAt : null,
    };
    setDraft([]);
    setPickerOpen(false);
    setSelected(id);
    setSessions(prev => {
      const old = prev.find(row => row.id === id);
      return [{ id, updated: Date.now(), turns: [...(old?.turns || []), turn] }, ...prev.filter(row => row.id !== id)];
    });
    runTurn(id, turn, turns, false);
  }

  function resolveTopic(startNew) {
    if (!session || !lastTurn?.topicPrompt || request.current) return;
    if (lastTurn.model === TERRA && !terraAllowed(mode) || quotaFor(lastTurn.model, playerQuotas(playerId, mode)).exhausted) return;
    if (!demo && (!lastTurn.model || lastTurn.model === DEEPSEEK)) startPreviewQuota(playerId);
    const oldId = session.id;
    const id = startNew ? crypto.randomUUID() : oldId;
    const turn = { ...lastTurn, usageReceipts: lastTurn.usageReceipts ?? [], topicPrompt: null };
    const history = startNew ? [] : turns.slice(0, -1);
    setSessions(prev => {
      const others = prev.filter(row => row.id !== oldId);
      const original = prev.find(row => row.id === oldId);
      if (!original || original.turns.at(-1)?.id !== turn.id) return prev;
      const previous = original.turns.slice(0, -1);
      if (startNew) {
        return [{ id, updated: Date.now(), turns: [turn] }, { ...original, turns: previous }, ...others];
      }
      return [{ ...original, updated: Date.now(), turns: [...previous, turn] }, ...others];
    });
    setSelected(id);
    runTurn(id, turn, history, true);
  }

  function stop() {
    if (!showingRun || request.current?.turnId !== lastTurn.id) return;
    const controller = request.current;
    request.current = null;
    controller.abort();
    setRunning(null);
    setSessions(previous => previous.map(row => row.id !== selected ? row : {
      ...row, turns: row.turns.map(turn => turn.id === controller.turnId ? {...turn, stopped:true} : turn),
    }));
    input.current?.focus({preventScroll:true});
  }

  async function runTurn(id, turn, history, topicConfirmed) {
    const controller = new AbortController();
    controller.turnId = turn.id;
    request.current = controller;
    newest.current = turn.id;
    pinned.current = true;
    setRunning(turn.id);
    try {
      for await (const sample of previewEvents(turn.questionParts ? questionText(turn.questionParts, true) : turn.question,
        previewMemory(history), controller.signal, topicConfirmed, turn.id, turn.questionParts, turn.reasoningEffort)) {
        if (request.current !== controller || controller.signal.aborted) return;
        if (sample.type === "usage" && turn.model && turn.model !== DEEPSEEK && turn.model !== TERRA) continue;
        const event = sample.type === "usage" && turn.model === TERRA ? {...sample, receipt:demoTerraReceipt(sample.receipt)} : sample;
        setSessions(prev => prev.map(row => row.id !== id ? row : {
          ...row,
          ...(["answer", "item.choice", "topic.confirm"].includes(event.type) ? {unread: true, updated: Date.now()} : {}),
          turns: row.turns.map(t => t.id === turn.id && !t.stopped ? applyEvent(t, event) : t),
        }));
        if (event.type === "answer" && closedRef.current && reminderEnabled(playerId, "completion")) {
          const title = previewSessionTitle({turns:history.length ? history : [{...turn, reply:event.reply}]});
          notifyRef.current(`问舟已完成「${title}」`, "wenzhou-complete");
        }
        if (event.type === "topic.confirm") break;
      }
    } catch {
      if (!controller.signal.aborted) notifyRef.current("这次查阅中断了，可以重新提问。", "error");
    } finally {
      if (request.current === controller) { request.current = null; setRunning(null); }
    }
  }

  const composer = <div className="wz-compose-row">
    {(quota.enabled || quota.terra && (quota.unlimited || quota.exhausted)) &&
      <QuotaIndicator key={quota.terra ? TERRA : DEEPSEEK} quota={quota}/>}
    <form ref={composerForm} className="wz-compose" onSubmit={e => { e.preventDefault(); send(draft); }}>
    {pickerOpen && <ItemPicker anchor={composerForm} onSelect={pickItem} onClose={closePicker}/>}
    <ReferenceTools guideOpen={guide === 3} disabled={waiting} searching={pickerOpen} closeSearch={closePicker} remember={() => input.current?.remember()}
      onItem={openPicker} onBlock={openBlockPicker}/>
    <InlineQuestion editorRef={input} parts={draft} onChange={setDraft} disabled={waiting} description={guidanceId}
      placeholder={quotaExhausted ? (terraSelected ? "Terra 美元额度已用完" : "本周额度已用完") : waiting ? "请选择如何继续这次提问" : session ? "接着问问舟…" : "想在旅途中弄清什么？"}
      onPicker={openPicker} onSend={() => send(draft)}/>
    {(ownerModelView(mode) || showTerra) && <ModelPicker playerId={playerId} disabled={busy || waiting || guide !== null}/>}
    {!chatGptSelected && <UsageCost key={(selected || 'new') + ':' + currentModel} turns={turns} terra={terraSelected} guideOpen={guide === 2} />}
    {showingRun
      ? <button type="button" className="wz-stop" aria-label="停止回答" title="停止回答" onClick={stop}><span aria-hidden="true"/></button>
      : <button type="submit" aria-label="发送问题" disabled={!questionText(draft).trim() || busy || waiting || quotaExhausted}><Glyph name="send" size={21} /></button>}
  </form></div>;

  return <AnimatePresence initial={false}>
    {active && <HubRouteSurface key="guide" side="guide" reduceMotion={reduceMotion}>
    <MotionConfig reducedMotion={reduceMotion ? "always" : "never"} transition={{ duration: reduceMotion ? 0 : 0.2, ease }}>
    <section className="wz-page" aria-label="问舟" data-sharing={Boolean(shareView) && !showAgreement || undefined} data-guide={guide ?? undefined} data-guide-leaving={guideLeaving || undefined} onKeyDown={event => {
      if (reviewingTerms && event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        closeReview();
      }
    }}>
    <header className="topbar wz-bar" inert={guide !== null || Boolean(shareView) && !showAgreement}>
      <button className="wz-back" onClick={reviewingTerms ? closeReview : onBack}><Glyph name="back" size={18} />{reviewingTerms ? "返回问舟" : "返回手册"}</button>
      {!showAgreement && <div className="wz-actions">
        {!viewHistory && (guide === 6 || turns.some(t => t.reply && !t.itemChoice && !t.topicPrompt && !t.stopped)) &&
          <button id="wz-share-open-preview" onClick={event => { shareOpener.current = event.currentTarget.id;
            setShareView({page:"preview", snapshot:freezeConversation(session, previewOwner.name)}); }}>
            <Share2 size={16} aria-hidden="true"/>分享</button>}
        <button id="wz-share-open-inbox" onClick={event => { shareOpener.current = event.currentTarget.id; setShareView({page:"inbox"}); }}>
          <Inbox size={17} aria-hidden="true"/>收到的分享</button>
        {(session || guide === 5) && !viewHistory && <button data-guide-target="new" data-guide-preview-only={!session || undefined} onClick={() => choose(null)}><Glyph name="new" size={17} />新对话</button>}
        <button className="wz-history-toggle" data-guide-target="history" aria-expanded={viewHistory} onClick={() => { newest.current = null; setViewHistory(!viewHistory); }}><Glyph name="history" size={18} />{viewHistory ? "返回对话" : "历史"}{!viewHistory && (guide === 4 || hasUnread) && <i className="wz-unread-dot" role="img" aria-label="有未读对话"/>}</button>
      </div>}
    </header>
    {showAgreement && <Agreement key={policy.version} policy={policy} quotaEnabled={quotas.deepSeek.enabled} review={reviewingTerms} reduceMotion={reduceMotion} onAccept={enterPreview}
      onBack={onBack} researchAllowed={receipt?.research === true} onResearchChange={changeResearchConsent} />}
    {enteredPreview && <div hidden={reviewingTerms} inert={reviewingTerms || guide !== null || Boolean(shareView)}
      className={"wz-body" + (!session && !viewHistory ? " wz-empty" : "")}>
    <AnimatePresence initial={false}>
    {viewHistory ? <Pane key="history" reduceMotion={reduceMotion} className="wz-history wz-scroll">
      <ChatHistory sessions={sessions} running={running} showUnread={showUnread} onChoose={choose} onDelete={remove} />
    </Pane> : !session ? <Pane key="landing" reduceMotion={reduceMotion} className="wz-landing">
      <BoatMark /><h1>{greeting}</h1>
    </Pane> :
      <Pane key={selected} reduceMotion={reduceMotion} className="wz-chat wz-scroll" nodeRef={scroll} onScroll={e => {
        const box = e.currentTarget; pinned.current = box.scrollHeight - box.clientHeight - box.scrollTop < 52;
      }}>
        <ItemTooltipScope><div className="wz-thread">{turns.map(turn => <motion.article className="wz-turn" key={turn.id}
          initial={running === turn.id && !reduceMotion ? enter : false} animate={visible}>
          <div className="wz-question">
            {!turn.questionParts && turn.itemIds?.length > 0 && <div className="wz-question-refs">{turn.itemIds.map(id => <ItemReference key={id} stack={itemById.get(id)}/>)}</div>}
            {turn.questionParts ? turn.questionParts.map((part, i) => typeof part === "string" ? part : part.block ?
              <span key={i} className="wz-inline-item" title={blockDetail(part)}><img src={blockIcon(part)} alt=""/>{blockLabel(part)}</span> :
              <ItemReference key={i} stack={itemById.get(part.item)}/>) : turn.question}
          </div>
          <div className="wz-speaker"><BoatMark /><strong>问舟</strong>{turn.model && turn.model !== DEEPSEEK && <small>{modelLabel(turn.model)}</small>}</div>
          <Timeline turn={turn} running={running === turn.id} reduceMotion={reduceMotion} />
          {turn.topicPrompt ? <TopicChoice prompt={turn.topicPrompt} onChoose={resolveTopic}
            busy={busy || turn.model === TERRA && !terraAllowed(mode) || quotaFor(turn.model, quotas).exhausted} /> :
            turn.itemChoice && turn === lastTurn ? null : turn.reply ? <Answer reply={turn.reply} appear={newest.current === turn.id && !reduceMotion} onGrow={followAnswer} /> :
            running !== turn.id && <p>{turn.stopped ? "已停止回答" : "上次查阅已中断，可以重新提问。"}</p>}
        </motion.article>)}</div></ItemTooltipScope>
      </Pane>}
    </AnimatePresence>
    <AnimatePresence initial={false}>
      {!viewHistory && <motion.div key="composer" className="wz-dock" layout={reduceMotion ? false : "position"}
        layoutDependency={Boolean(session)} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
        transition={{ duration: reduceMotion ? 0 : 0.16, layout: { duration: reduceMotion ? 0 : 0.34, ease } }}>
        {lastTurn?.itemChoice && guide === null ? <ItemChoice key={lastTurn.id} choice={lastTurn.itemChoice} busy={busy} exhausted={quotaExhausted}
          submit={id => send(["我指的是 ", {item:id}, "，请继续回答上一个问题。"], true)}
          manual={() => {
            setSessions(prev => prev.map(row => row.id !== selected ? row : {...row,
              turns: row.turns.map(turn => turn.id !== lastTurn.id ? turn : {...turn, itemChoice:null})}));
            setPickerOpen(true);
          }}/> : composer}
        <p className="wz-compose-hint" id={guidanceId}><strong>换话题请新建对话。</strong>经提醒仍反复询问无关问题，可能被限制使用问舟。</p>
        <div className="wz-guide-links">
          {agreementVisible && <button ref={agreementLink} type="button" className="wz-terms-link" onClick={() => setReviewingTerms(true)}>《问舟内测使用协议》</button>}
          <button ref={replayButton} type="button" className="wz-terms-link" disabled={busy || waiting} onClick={() => {
            setPickerOpen(false); replaying.current = true; setGuide(0);
          }}>重看使用引导</button>
        </div>
      </motion.div>}
    </AnimatePresence>
    <AnimatePresence initial={false}>
      {!viewHistory && !session && <Pane key="examples" reduceMotion={reduceMotion} className="wz-examples">
        {examples.map(example => <button key={example.icon} disabled={busy || quotaExhausted} onClick={() => send(example.text)}>
          <Glyph name={example.icon} size={16} />{example.text}</button>)}
      </Pane>}
    </AnimatePresence>
    {(!session && guide === null || guide === 8) && !viewHistory && !shareView && !showAgreement && !selectingBlock &&
      <ReminderSettings guideOpen={guide === 8} playerId={playerId} notify={notify} reduceMotion={reduceMotion}/>}
    </div>}
    <span className="wz-sr" role="status">{active && !showAgreement && !viewHistory && (lastTurn?.topicPrompt || (running === lastTurn?.id ? eventLabel(lastTurn?.events.at(-1), lastTurn, true) : lastTurn?.reply ? "问舟已回复" : ""))}</span>
    {selectingBlock && active && !showAgreement && !viewHistory && <BlockPicker reduceMotion={reduceMotion} onReturn={finishBlockPicker}/>}
    {guide !== null && active && !showAgreement && <FirstGuide step={guide} setStep={setGuide} onClose={closeGuide} leaving={guideLeaving} onExited={finishGuide} reduceMotion={reduceMotion} chatGptSelected={chatGptSelected}/>}
    <AnimatePresence initial={false}>
      {shareView && !showAgreement && <SharePanel key={shareView.page} initial={shareView} playerId={playerId}
        demo={Boolean(demo)} onBack={closeShares} notify={notify} reduceMotion={reduceMotion}/>}
    </AnimatePresence>
    </section>
    </MotionConfig>
    </HubRouteSurface>}
  </AnimatePresence>;
}
