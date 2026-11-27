import React from "react";
import { ArrowUpRight, X } from "lucide-react";

export const QUICK_ASK_PRESETS = [
  { id: "obtain", label: "怎么获取？", prompt: "怎么获取？" },
  { id: "usage", label: "有什么用，怎么使用？", prompt: "有什么用，怎么使用？" },
  { id: "related", label: "有哪些主要用途或关联玩法？", prompt: "有哪些主要用途或关联玩法？" },
];

export function ItemQuickAskPanel({ item, busy, exhausted, onPreset, onDismiss }) {
  if (!item) return null;
  return (
    <div className="wz-quick-dock-top" aria-label={`关于 ${item.name} 的快捷提问`}>
      <div className="wz-quick-context-bar">
        <span
          className="wz-item-image wz-quick-context-icon"
          data-glint={item.glint || undefined}
          style={item.glint ? { maskImage: `url("${item.icon}")` } : undefined}
        >
          <img src={item.icon} alt="" draggable="false" />
        </span>
        <strong className="wz-quick-context-name">{item.name}</strong>
        <code className="wz-quick-context-id">{item.id}</code>
        <button
          type="button"
          className="wz-quick-dismiss"
          title="切回常规提问"
          aria-label="切回常规提问"
          onClick={onDismiss}
        >
          <X size={14} aria-hidden="true" />
        </button>
      </div>

      <div className="wz-quick-list" role="group" aria-label="可选快捷问题">
        {QUICK_ASK_PRESETS.map(preset => (
          <button
            key={preset.id}
            type="button"
            className="wz-quick-row"
            disabled={busy || exhausted}
            onClick={() => onPreset(preset.prompt)}
          >
            <span>{preset.label}</span>
            <ArrowUpRight size={14} aria-hidden="true" />
          </button>
        ))}
      </div>
    </div>
  );
}
