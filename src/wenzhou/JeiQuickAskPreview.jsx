import React, { useEffect, useLayoutEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { ArrowLeft, Search, Keyboard, Sparkles } from "lucide-react";
import { catalogItems } from "./itemCatalog.js";
import { subscribePreview } from "./managerPreview.js";
import { QUICK_ASK_MODES, quickAskKey, quickAskMode, setQuickAskKey, setQuickAskMode } from "./ReminderSettings.jsx";
import "./jeiQuickAsk.css";

const INVENTORY_IDS = [
  "ultramarine:chisel_table",
  "minecraft:crafting_table",
  "minecraft:torch",
  "minecraft:anvil",
  "minecraft:enchanted_golden_apple",
  "farm_and_charm:strawberry",
  "crockpot:milk_bottle",
  "minecraft:enchanted_book",
  "minecraft:golden_apple",
  "minecraft:coal",
  "minecraft:oak_planks",
  "minecraft:stick",
];

const KEY_CHOICES = ["G", "V", "K"];

function isTypingTarget(target) {
  if (!target) return false;
  const tag = target.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || target.isContentEditable;
}

function JeiTooltipOverlay({ hovered, anchorPos, showQuickAsk, bindKey, typingInSearch, onAsk }) {
  const wrapRef = useRef(null);
  const mainRef = useRef(null);
  const subRef = useRef(null);
  const [placed, setPlaced] = useState({ left: 0, top: 0, flipped: false, ready: false });

  useLayoutEffect(() => {
    if (!hovered || !mainRef.current) return;
    const gap = 5;
    const pad = 12;
    const mainRect = mainRef.current.getBoundingClientRect();
    const subHeight = showQuickAsk && subRef.current ? subRef.current.getBoundingClientRect().height : 0;
    const width = Math.max(mainRect.width, showQuickAsk && subRef.current ? subRef.current.getBoundingClientRect().width : 0);
    const totalHeight = mainRect.height + (showQuickAsk ? gap + subHeight : 0);

    let left = anchorPos.x + 16;
    if (left + width > window.innerWidth - pad) {
      left = Math.max(pad, anchorPos.x - width - 16);
    }

    let top = anchorPos.y - 12;
    let flipped = false;
    if (showQuickAsk && top + totalHeight > window.innerHeight - pad) {
      flipped = true;
      top = Math.max(pad, Math.min(anchorPos.y - 12 - subHeight - gap, window.innerHeight - pad - totalHeight));
    } else {
      top = Math.max(pad, Math.min(top, window.innerHeight - pad - totalHeight));
    }
    setPlaced({ left, top, flipped, ready: true });
  }, [hovered, anchorPos.x, anchorPos.y, showQuickAsk, bindKey]);

  if (!hovered) return null;
  const { item } = hovered;

  return (
    <div
      ref={wrapRef}
      className={`wz-jei-tip-stack${placed.flipped ? " is-flipped" : ""}`}
      style={{
        left: `${placed.left}px`,
        top: `${placed.top}px`,
        opacity: placed.ready ? 1 : 0,
      }}
    >
      <div ref={mainRef} className="wz-jei-tooltip-box" role="tooltip">
        <div className="wz-jei-tooltip-title">{item.name}</div>
        <div className="wz-jei-tooltip-divider" aria-hidden="true" />
        <div className="wz-jei-tooltip-id">{item.id} (alt)</div>
        <div className="wz-jei-tooltip-mod">{item.mod}</div>
      </div>

      {showQuickAsk && (
        <button
          ref={subRef}
          type="button"
          className={`wz-jei-ask-box${typingInSearch ? " is-muted" : ""}`}
          onClick={() => onAsk(item, hovered.scope)}
        >
          <span className="wz-jei-ask-gradient">
            按 <span className="wz-jei-ask-key">[{bindKey}]</span> 向问舟提问...
          </span>
          {typingInSearch && <span className="wz-jei-ask-typing-note">搜索输入中已暂停热键</span>}
        </button>
      )}
    </div>
  );
}

export default function JeiQuickAskPreview({ playerId, onClose, onAskItem }) {
  const mode = useSyncExternalStore(subscribePreview, () => quickAskMode(playerId));
  const bindKey = useSyncExternalStore(subscribePreview, () => quickAskKey(playerId));
  const [search, setSearch] = useState("");
  const [searchFocused, setSearchFocused] = useState(false);
  const [hovered, setHovered] = useState(null);
  const [pointer, setPointer] = useState({ x: 320, y: 260 });
  const [pinnedSample, setPinnedSample] = useState(false);

  const inventoryItems = useMemo(
    () => INVENTORY_IDS.map(id => catalogItems.find(item => item.id === id)).filter(Boolean),
    [],
  );

  const jeiItems = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return catalogItems;
    return catalogItems.filter(item => item.keywords.some(k => k.includes(q)));
  }, [search]);

  const showQuickAsk = Boolean(
    hovered && (mode === "global" || (mode === "jei" && hovered.scope === "jei")),
  );

  useEffect(() => {
    const onKeyDown = event => {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        onClose();
        return;
      }
      if (event.repeat || event.ctrlKey || event.metaKey || event.altKey) return;
      if (isTypingTarget(event.target)) return;
      if (event.key.toUpperCase() !== bindKey) return;
      if (!hovered) return;
      const allowed = mode === "global" || (mode === "jei" && hovered.scope === "jei");
      if (!allowed) return;
      event.preventDefault();
      event.stopPropagation();
      const targetItem = hovered.item;
      const targetScope = hovered.scope;
      requestAnimationFrame(() => onAskItem(targetItem, targetScope));
    };
    window.addEventListener("keydown", onKeyDown, true);
    return () => window.removeEventListener("keydown", onKeyDown, true);
  }, [hovered, mode, bindKey, onClose, onAskItem]);

  function enterSlot(item, scope, event) {
    setPinnedSample(false);
    setHovered({ item, scope });
    setPointer({ x: event.clientX, y: event.clientY });
  }

  function moveSlot(event) {
    if (pinnedSample) return;
    setPointer({ x: event.clientX, y: event.clientY });
  }

  function leaveSlot() {
    if (pinnedSample) return;
    setHovered(null);
  }

  function focusSlot(item, scope, event) {
    const rect = event.currentTarget.getBoundingClientRect();
    setPinnedSample(false);
    setHovered({ item, scope });
    setPointer({ x: rect.right, y: rect.top + rect.height / 2 });
  }

  function triggerChiselPin() {
    const chisel = catalogItems.find(item => item.id === "ultramarine:chisel_table") || catalogItems[0];
    setPinnedSample(true);
    setHovered({ item: chisel, scope: "jei" });
    setPointer({ x: Math.min(window.innerWidth * 0.56, window.innerWidth - 360), y: 240 });
  }

  return (
    <div className="wz-jei-stage" role="dialog" aria-modal="true" aria-label="JEI 与背包物品快捷提问演示">
      <header className="wz-jei-topbar">
        <button type="button" className="wz-jei-back" onClick={onClose}>
          <ArrowLeft size={15} aria-hidden="true" />
          返回旅人手册
        </button>

        <div className="wz-jei-controls">
          <div className="wz-jei-control-group" aria-label="快捷提问范围（与问舟设置同步）">
            <span className="wz-jei-control-label">问舟设置</span>
            <div className="wz-jei-seg" role="radiogroup" aria-label="快捷提问生效范围">
              {QUICK_ASK_MODES.map(([id, label]) => (
                <button
                  key={id}
                  type="button"
                  role="radio"
                  aria-checked={mode === id}
                  className={mode === id ? "is-active" : undefined}
                  onClick={() => setQuickAskMode(playerId, id)}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div className="wz-jei-control-group" aria-label="模拟按键绑定">
            <Keyboard size={14} aria-hidden="true" />
            <div className="wz-jei-seg" role="radiogroup" aria-label="模拟快捷提问键位">
              {KEY_CHOICES.map(k => (
                <button
                  key={k}
                  type="button"
                  role="radio"
                  aria-checked={bindKey === k}
                  className={bindKey === k ? "is-active" : undefined}
                  onClick={() => setQuickAskKey(playerId, k)}
                >
                  {k}
                </button>
              ))}
            </div>
          </div>

          <button type="button" className="wz-jei-pin-btn" onClick={triggerChiselPin}>
            <Sparkles size={13} aria-hidden="true" />
            固定雕刻桌提示预览
          </button>
        </div>
      </header>

      <div className="wz-jei-canvas">
        <section className="wz-mc-panel" aria-label="背包与容器模拟区域">
          <div className="wz-mc-panel-head">
            <strong>背包 / 容器</strong>
            <span className={mode === "global" ? "is-on" : "is-off"}>
              {mode === "global" ? `已启用 [${bindKey}] 快捷提问` : mode === "jei" ? "仅 JEI 模式 · 此处不显示" : "已关闭"}
            </span>
          </div>
          <div className="wz-mc-grid">
            {Array.from({ length: 18 }).map((_, index) => {
              const item = inventoryItems[index];
              return item ? (
                <button
                  key={item.id + "-inv"}
                  type="button"
                  className={`wz-mc-slot${hovered?.item.id === item.id && hovered?.scope === "inventory" ? " is-hovered" : ""}`}
                  aria-label={`背包物品：${item.name}`}
                  onPointerEnter={e => enterSlot(item, "inventory", e)}
                  onPointerMove={moveSlot}
                  onPointerLeave={leaveSlot}
                  onFocus={e => focusSlot(item, "inventory", e)}
                  onClick={() => {
                    if (mode === "global") onAskItem(item, "inventory");
                  }}
                >
                  <img src={item.icon} alt="" draggable="false" />
                </button>
              ) : (
                <div key={"empty-inv-" + index} className="wz-mc-slot is-empty" aria-hidden="true" />
              );
            })}
          </div>
          <p className="wz-mc-panel-tip">
            把鼠标移到物品格上悬停，直接按键盘 <kbd>{bindKey}</kbd> 即可跳转问舟专属承接页。切到「JEI」模式可对比此处隐藏效果。
          </p>
        </section>

        <aside className="wz-jei-sidebar" aria-label="JEI 物品列表模拟区域">
          <div className="wz-jei-side-head">
            <span className="wz-jei-side-title">JEI 物品栏</span>
            <span className="wz-jei-side-page">1 / 1</span>
          </div>

          <div className="wz-jei-grid">
            {jeiItems.map(item => (
              <button
                key={item.id + "-jei"}
                type="button"
                className={`wz-mc-slot wz-jei-slot${hovered?.item.id === item.id && hovered?.scope === "jei" ? " is-hovered" : ""}${item.hasJeiRecipe === false ? " is-no-recipe" : ""}`}
                aria-label={`JEI 物品：${item.name}`}
                onPointerEnter={e => enterSlot(item, "jei", e)}
                onPointerMove={moveSlot}
                onPointerLeave={leaveSlot}
                onFocus={e => focusSlot(item, "jei", e)}
                onClick={() => {
                  if (mode !== "off") onAskItem(item, "jei");
                }}
              >
                <img src={item.icon} alt="" draggable="false" />
              </button>
            ))}
          </div>

          <div className="wz-jei-bottom-row">
            <div className="wz-jei-search-wrap">
              <Search size={13} aria-hidden="true" />
              <input
                type="text"
                value={search}
                placeholder={`JEI 搜索（输入 ${bindKey.toLowerCase()} 不会误触发）`}
                aria-label="模拟 JEI 搜索框"
                onFocus={() => setSearchFocused(true)}
                onBlur={() => setSearchFocused(false)}
                onChange={e => setSearch(e.target.value)}
              />
            </div>
          </div>
        </aside>
      </div>

      <footer className="wz-jei-foot">
        <span><kbd>{bindKey}</kbd> 悬停物品时一键向问舟提问</span>
        <span><kbd>Esc</kbd> 返回旅人手册</span>
        <span>悬停最底排物品可查看副框触底自动翻至上方</span>
      </footer>

      <JeiTooltipOverlay
        hovered={hovered}
        anchorPos={pointer}
        showQuickAsk={showQuickAsk}
        bindKey={bindKey}
        typingInSearch={searchFocused}
        onAsk={onAskItem}
      />
    </div>
  );
}
