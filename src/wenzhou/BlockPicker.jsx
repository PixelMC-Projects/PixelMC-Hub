import React, {useLayoutEffect, useRef, useState} from "react";
import {blockKey, blockLabel, blockSamples} from "./blockSamples.js";
import {Glyph} from "./Glyph.jsx";
import "./blockPicker.css";

export default function BlockPicker({reduceMotion, onReturn}) {
  const dialog = useRef(null);
  const result = useRef(undefined);
  const [closing, setClosing] = useState(false);
  const [target, setTarget] = useState(null);
  useLayoutEffect(() => {
    const node = dialog.current;
    node.showModal();
    return () => node.close();
  }, []);
  useLayoutEffect(() => {
    if (!closing) return;
    const timer = setTimeout(() => {
      dialog.current.close();
      onReturn(result.current);
    }, reduceMotion ? 0 : 170);
    return () => clearTimeout(timer);
  }, [closing, reduceMotion, onReturn]);
  function finish(sample) {
    if (result.current !== undefined) return;
    result.current = sample;
    setClosing(true);
  }
  return <dialog className="wz-block-world" ref={dialog} aria-label="方块选取预览"
    data-closing={closing} data-reduced={reduceMotion} onCancel={event => {event.preventDefault(); finish(null);}}
    onKeyDown={event => {
      if (event.key === "Escape") {event.preventDefault(); event.stopPropagation(); finish(null);}
    }}>
    <header><button type="button" autoFocus onClick={() => finish(null)}><Glyph name="back" size={17}/>返回问舟</button>
      <span>方块选取预览</span></header>
    <div className="wz-block-field">
      {blockSamples.map(sample => <button type="button" className="wz-world-target" key={blockKey(sample)}
        aria-label={"引用" + blockLabel(sample)} data-aimed={target === sample}
        onPointerEnter={() => setTarget(sample)} onPointerLeave={() => setTarget(null)}
        onFocus={() => setTarget(sample)} onBlur={() => setTarget(null)}
        onClick={() => finish(sample)} onContextMenu={event => {event.preventDefault(); finish(sample);}}>
        <img src={sample.icon} alt="" draggable="false"/>
      </button>)}
    </div>
    <div className="wz-world-readout" aria-live="polite">{target ? <>
      <img src="/assets/wenzhou-boat.png" alt=""/><span><strong>{target.name}</strong><small>主世界 · {target.pos.join(", ")}</small></span>
    </> : <><Glyph name="selectBlock"/><strong>瞄准要引用的方块</strong></>}</div>
    <footer><kbd>点击</kbd> 或右键引用一个方块 <kbd>Esc</kbd> 取消</footer>
  </dialog>;
}
