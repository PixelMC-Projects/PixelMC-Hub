import React, {useId, useLayoutEffect, useRef, useState} from "react";
import {CubeIcon} from "./ItemPicker.jsx";
import {Glyph} from "./Glyph.jsx";

export default function ReferenceTools({disabled, searching, closeSearch, remember, onItem, onBlock, guideOpen = false}) {
  const [open, setOpen] = useState(false);
  const anchor = useRef(null);
  const trigger = useRef(null);
  const menu = useRef(null);
  const id = useId();
  useLayoutEffect(() => {
    if (!open) return;
    menu.current.querySelector("button")?.focus({preventScroll:true});
    const outside = event => { if (!anchor.current.contains(event.target)) setOpen(false); };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("focusin", outside);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("focusin", outside);
    };
  }, [open]);
  function pick(action) {
    setOpen(false);
    action();
  }
  return <div ref={anchor} className="wz-reference-tools">
    <button ref={trigger} type="button" className="wz-pick-button wz-tool-trigger" aria-label="工具箱" title="工具箱"
      aria-haspopup="menu" aria-expanded={open || guideOpen} aria-controls={open || guideOpen ? id : undefined} disabled={disabled}
      onMouseDown={e => e.preventDefault()} onClick={() => {
        remember();
        if (searching) closeSearch(false);
        setOpen(!open);
      }}><Glyph name="toolbox" size={21}/></button>
    {(open || guideOpen) && <div ref={menu} id={id} className="wz-reference-menu" role="menu" aria-label="引用工具"
      onKeyDown={event => {
        const rows = [...menu.current.querySelectorAll("button")];
        if (event.key === "Escape") {
          event.preventDefault(); event.stopPropagation(); setOpen(false); trigger.current.focus();
        } else if (["ArrowDown","ArrowUp","Home","End"].includes(event.key)) {
          event.preventDefault();
          const next = event.key === "Home" ? 0 : event.key === "End" ? rows.length-1 :
            (rows.indexOf(document.activeElement) + (event.key === "ArrowDown" ? 1 : -1) + rows.length) % rows.length;
          rows[next].focus();
        }
      }}>
      <button role="menuitem" type="button" onClick={() => pick(onItem)}><CubeIcon/>内联物品</button>
      <button role="menuitem" type="button" onClick={() => pick(onBlock)}><Glyph name="selectBlock" size={18}/>世界方块</button>
    </div>}
  </div>;
}
