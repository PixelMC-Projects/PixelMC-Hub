import React, { createContext, useContext, useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

const TooltipContext = createContext(null);
const gutter = 8;

function ItemTooltip({ id, anchor, stack, keepOpen, dismissSoon, close }) {
  const tip = useRef(null);
  useLayoutEffect(() => {
    function place() {
      const el = tip.current;
      const rect = anchor.getBoundingClientRect();
      const width = el.offsetWidth;
      const height = el.offsetHeight;
      const fitsBelow = rect.bottom + gutter + height <= window.innerHeight - gutter;
      const y = fitsBelow ? rect.bottom + gutter : rect.top - height - gutter;
      const clamp = (v, max) => Math.max(gutter, Math.min(v, max - gutter));
      el.style.left = clamp(rect.left, window.innerWidth - width) + "px";
      el.style.top = clamp(y, window.innerHeight - height) + "px";
    }
    place();
    const resize = new ResizeObserver(place);
    resize.observe(tip.current);
    window.addEventListener("resize", place);
    return () => {
      resize.disconnect();
      window.removeEventListener("resize", place);
    };
  }, [anchor, stack]);

  useEffect(() => {
    const dismissOutside = event => {
      if (!tip.current?.contains(event.target) && !anchor.contains(event.target)) close();
    };
    const escape = event => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      event.stopPropagation();
      close();
    };
    document.addEventListener("scroll", dismissOutside, true);
    document.addEventListener("pointerdown", dismissOutside, true);
    window.addEventListener("keydown", escape, true);
    return () => {
      document.removeEventListener("scroll", dismissOutside, true);
      document.removeEventListener("pointerdown", dismissOutside, true);
      window.removeEventListener("keydown", escape, true);
    };
  }, [anchor, close]);

  return createPortal(<div className="wz-item-tooltip" role="tooltip" id={id} ref={tip}
    onPointerEnter={keepOpen} onPointerLeave={dismissSoon}>
    <strong data-rarity={stack.rarity}>{stack.name}</strong>
    {stack.tooltip?.map((line, i) => <div key={i} data-tone={line.tone}>{line.text}</div>)}
  </div>, document.body);
}

export function ItemTooltipScope({ children }) {
  const [active, setActive] = useState(null);
  const timeout = useRef(null);
  const id = useId();
  const keepOpen = () => clearTimeout(timeout.current);
  useEffect(() => () => clearTimeout(timeout.current), []);
  const close = () => { keepOpen(); setActive(null); };
  function show(anchor, stack) {
    keepOpen();
    setActive(last => last?.anchor === anchor && last.stack === stack ? last : { anchor, stack });
  }
  function dismissSoon() {
    keepOpen();
    timeout.current = setTimeout(() => setActive(null), 120);
  }
  return <TooltipContext.Provider value={{ active, id, show, dismissSoon }}>
    {children}
    {active && <ItemTooltip {...active} id={id} keepOpen={keepOpen} dismissSoon={dismissSoon} close={close} />}
  </TooltipContext.Provider>;
}

export function ItemReference({ stack, iconOnly = false, count }) {
  const tooltip = useContext(TooltipContext);
  const ref = useRef(null);
  const [failedIcon, setFailedIcon] = useState(null);
  if (!stack?.name) return null;
  const hasIcon = stack.icon?.startsWith("/assets/") && stack.icon !== failedIcon;
  const show = event => tooltip.show(event.currentTarget, stack);
  return <button type="button" ref={ref} className="wz-item-ref" data-slot={iconOnly || undefined}
    aria-label={iconOnly ? stack.name : undefined}
    aria-describedby={tooltip.active?.anchor === ref.current && tooltip.active ? tooltip.id : undefined}
    onPointerEnter={e => { if (e.pointerType !== "touch") show(e); }} onPointerLeave={tooltip.dismissSoon}
    onFocus={show} onBlur={tooltip.dismissSoon} onClick={show}>
    {hasIcon && <span className="wz-item-image" data-glint={stack.glint || undefined}
      style={stack.glint ? { maskImage: 'url("' + stack.icon + '")' } : undefined}>
      <img src={stack.icon} alt="" draggable="false" onError={() => setFailedIcon(stack.icon)} />
    </span>}
    {(!iconOnly || !hasIcon) && <span className="wz-item-name">{stack.name}</span>}
    {count != null && <span className="wz-stack-count">×{count}</span>}
  </button>;
}

export function ItemText({ text, items }) {
  if (!Array.isArray(text)) return text;
  return text.map((part, i) => {
    if (typeof part === "string") return part;
    const stack = items?.[part.item];
    return <React.Fragment key={i}>{stack
      ? <ItemReference stack={stack} />
      : (part.label || part.item)}
      {part.explanation && <span className="wz-item-explanation">（{part.explanation}）</span>}
    </React.Fragment>;
  });
}
