import React, { useId, useLayoutEffect, useRef, useState } from "react";
import { findItems } from "./itemCatalog.js";
import "./itemPicker.css";

export function CubeIcon() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" aria-hidden="true">
    <path d="m12 2.8 8.4 4.7v9L12 21.2l-8.4-4.7v-9zM3.6 7.5 12 12l8.4-4.5M12 12v9.2"/>
  </svg>;
}

export function ItemPicker({ onSelect, onClose, anchor }) {
  const [query, setQuery] = useState("");
  const [index, setIndex] = useState(0);
  const options = findItems(query);
  const listId = useId();
  const panel = useRef(null);
  const field = useRef(null);
  useLayoutEffect(() => {
    const element = panel.current;
    const form = anchor.current ?? element.closest("form");
    const page = form.closest(".wz-page");
    function size() {
      const rect = form.getBoundingClientRect();
      const scale = rect.width / form.offsetWidth || 1;
      const available = (rect.top - page.querySelector("header").getBoundingClientRect().bottom) / scale - 12;
      element.style.maxHeight = Math.min(305, Math.max(85, available)) + "px";
    }
    size();
    const observer = new ResizeObserver(size);
    observer.observe(form);
    observer.observe(page);
    field.current.focus({ preventScroll: true });
    function outside(event) {
      if (!element.contains(event.target) && !form.querySelector(".wz-pick-button")?.contains(event.target)) onClose(false);
    }
    document.addEventListener("pointerdown", outside);
    return () => {
      observer.disconnect();
      document.removeEventListener("pointerdown", outside);
    };
  }, [anchor, onClose]);
  useLayoutEffect(() => {
    panel.current.querySelector('[aria-selected="true"]')?.scrollIntoView({ block: "nearest", inline: "nearest" });
  }, [query, index]);
  return <div className="wz-picker" ref={panel} onKeyDown={event => {
    if (event.nativeEvent.isComposing || event.keyCode === 229) return;
    if (event.key === "Tab") { onClose(true); return; }
    if (!["Escape", "ArrowDown", "ArrowUp", "Enter"].includes(event.key)) return;
    event.preventDefault();
    event.stopPropagation();
    if (event.key === "Escape") onClose(true);
    if (event.key === "Enter" && options[index]) onSelect(options[index]);
    if (event.key === "ArrowDown") setIndex(Math.min(index + 1, Math.max(0, options.length - 1)));
    if (event.key === "ArrowUp") setIndex(Math.max(index - 1, 0));
  }}>
    <label className="wz-picker-search">
      <svg width="17" height="17" fill="none" stroke="currentColor" strokeWidth="1.7" viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m15.5 15.5 5 5"/></svg>
      <input ref={field} role="combobox" aria-label="搜索物品" aria-autocomplete="list" aria-expanded="true" aria-controls={listId}
        aria-activedescendant={options[index] ? listId + index : undefined} placeholder="搜索物品名称、拼音或 ID" value={query}
        onChange={event => { setQuery(event.target.value); setIndex(0); }}/>
    </label>
    <div className="wz-picker-list" role="listbox" aria-label="物品搜索结果" id={listId}>
      {options.map((item, i) => <button key={item.id} type="button" tabIndex={-1} id={listId + i} role="option" aria-selected={index === i}
        onMouseDown={event => event.preventDefault()} onClick={() => onSelect(item)}>
        <img src={item.icon} className={item.glint ? "wz-pick-glint" : undefined} alt=""/>
        <span><strong>{item.name}</strong><small>{item.id}</small></span><span className="wz-picker-mod">{item.mod}</span>
      </button>)}
    </div>
    {!options.length && <p className="wz-picker-empty" role="status">预览物品中没有匹配项</p>}
  </div>;
}
