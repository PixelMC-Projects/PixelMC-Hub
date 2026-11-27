import React, { useEffect, useId, useRef, useState, useSyncExternalStore } from "react";
import { AnimatePresence, motion, useIsPresent } from "motion/react";
import { Settings } from "lucide-react";
import { prefix, readValue, subscribePreview, writeValue } from "./managerPreview.js";
import "./reminders.css";

const choices = [
  ["completion", "处理完成提醒", "离开手册后，回答完成时提醒我。"],
  ["unread", "历史未读提醒", "在历史入口和未读对话旁显示圆点。"],
];

export const QUICK_ASK_MODES = [
  ["global", "全局"],
  ["jei", "JEI"],
  ["off", "关闭"],
];

const storageKey = (playerId, kind) => prefix + "reminder:" + playerId + ":" + kind;
const quickAskModeKey = playerId => prefix + "quick-ask-mode:" + playerId;
const quickAskBindKey = playerId => prefix + "quick-ask-key:" + playerId;

export const reminderEnabled = (playerId, kind) => readValue(storageKey(playerId, kind), true) !== false;
export function quickAskMode(playerId) {
  const saved = readValue(quickAskModeKey(playerId), "global");
  return QUICK_ASK_MODES.some(([id]) => id === saved) ? saved : "global";
}
export function setQuickAskMode(playerId, mode) {
  if (!QUICK_ASK_MODES.some(([id]) => id === mode)) return;
  writeValue(quickAskModeKey(playerId), mode);
}
export function quickAskKey(playerId) {
  const saved = readValue(quickAskBindKey(playerId), "G");
  return typeof saved === "string" && /^[A-Z0-9]$/.test(saved) ? saved : "G";
}
export function setQuickAskKey(playerId, key) {
  const normalized = String(key || "G").toUpperCase();
  if (!/^[A-Z0-9]$/.test(normalized)) return;
  writeValue(quickAskBindKey(playerId), normalized);
}

function ReminderPanel({id, playerId, notify, reduceMotion, guideOpen}) {
  const completion = useSyncExternalStore(subscribePreview, () => reminderEnabled(playerId, "completion"));
  const unread = useSyncExternalStore(subscribePreview, () => reminderEnabled(playerId, "unread"));
  const askMode = useSyncExternalStore(subscribePreview, () => quickAskMode(playerId));
  const present = useIsPresent();
  const first = useRef(null);
  useEffect(() => { if (!guideOpen) first.current?.focus({preventScroll:true}); }, [guideOpen]);
  return <motion.section id={id} aria-label="问舟设置" className="wz-reminder-panel" inert={!present}
    initial={reduceMotion ? false : {opacity:0, y:6}} animate={{opacity:1, y:0}}
    exit={reduceMotion ? {opacity:0} : {opacity:0, y:4}}>
    <h2>问舟设置</h2>
    {choices.map(([key, label, description], i) => {
      const checked = key === "completion" ? completion : unread;
      return <button key={key} type="button" className="wz-reminder-switch" ref={i === 0 ? first : undefined} role="switch"
        aria-checked={checked} aria-label={label} aria-describedby={id + key}
        onClick={() => {
          try { writeValue(storageKey(playerId, key), !checked); }
          catch { notify("未能保存提醒设置，请重试。", "error"); }
        }}>
        <span><strong>{label}</strong><small id={id + key}>{description}</small></span>
        <span className={"toggle" + (checked ? " on" : "")} aria-hidden="true"><span/></span>
      </button>;
    })}
    <div className="wz-reminder-choice">
      <div className="wz-reminder-choice-head">
        <strong>物品快捷提问</strong>
        <small id={id + "quick-ask"}>悬停物品时显示快捷提问提示。</small>
      </div>
      <div className="wz-reminder-segment" role="radiogroup" aria-label="物品快捷提问范围" aria-describedby={id + "quick-ask"}>
        {QUICK_ASK_MODES.map(([mode, label]) => (
          <button key={mode} type="button" role="radio" aria-checked={askMode === mode}
            className={askMode === mode ? "is-active" : undefined}
            onClick={() => {
              try { setQuickAskMode(playerId, mode); }
              catch { notify("未能保存快捷提问设置，请重试。", "error"); }
            }}>
            {label}
          </button>
        ))}
      </div>
    </div>
  </motion.section>;
}

export function ReminderSettings({playerId, notify, reduceMotion, guideOpen = false}) {
  const [open, setOpen] = useState(false);
  const container = useRef(null);
  const trigger = useRef(null);
  const id = useId();
  useEffect(() => {
    if (!open) return;
    const outside = event => { if (!container.current?.contains(event.target)) setOpen(false); };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("focusin", outside);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("focusin", outside);
    };
  }, [open]);
  return <div ref={container} className="wz-reminders" onKeyDown={e => {
    if (open && e.key === "Escape") {
      e.preventDefault(); e.stopPropagation(); setOpen(false); trigger.current?.focus();
    }
  }}>
    <button ref={trigger} type="button" className="wz-reminder-trigger" title="问舟设置" aria-label="问舟设置"
      aria-expanded={open || guideOpen} aria-controls={open || guideOpen ? id : undefined} onClick={() => setOpen(value => !value)}>
      <Settings size={19} strokeWidth={1.75} aria-hidden="true"/>
    </button>
    <AnimatePresence>{(guideOpen || open) && <ReminderPanel guideOpen={guideOpen} id={id} playerId={playerId} notify={notify} reduceMotion={reduceMotion}/>}</AnimatePresence>
  </div>;
}
