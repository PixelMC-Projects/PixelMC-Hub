import React, {useEffect, useId, useRef, useState, useSyncExternalStore} from 'react';
import {Check, ChevronDown, LockKeyhole} from 'lucide-react';
import {subscribePreview, terraPreviewProjectAvailable} from './managerPreview.js';
import {TERRA, modelLabel, modelMode, previewModels, selectedModel, selectPreviewModel, effortLabels, effortOptions,
  personalEffort, effectiveEffort, selectEffort, terraAllowed} from './modelPreview.js';
import './modelPicker.css';

export default function ModelPicker({disabled,playerId}) {
  const mode = useSyncExternalStore(subscribePreview, () => modelMode(playerId));
  const model = useSyncExternalStore(subscribePreview, () => selectedModel(playerId, mode));
  const effort = useSyncExternalStore(subscribePreview, () => personalEffort(playerId, model, mode));
  const resolved = useSyncExternalStore(subscribePreview, () => effectiveEffort(playerId, model, mode));
  const options = effortOptions(mode, model);
  const projectAvailable = useSyncExternalStore(subscribePreview, terraPreviewProjectAvailable);
  const showTerra = terraAllowed(mode, projectAvailable);
  const label = modelLabel(model, showTerra);
  const [placement,setPlacement] = useState({up:true,height:320});
  const [open, setOpen] = useState(false);
  const root = useRef(null), trigger = useRef(null);
  const id = useId();
  useEffect(() => {
    if (!open || disabled) return;
    const closeOutside = e => { if (!root.current?.contains(e.target)) setOpen(false); };
    root.current.querySelector('[aria-checked="true"]')?.focus({preventScroll:true});
    document.addEventListener('pointerdown', closeOutside);
    document.addEventListener('focusin', closeOutside);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
      document.removeEventListener('focusin', closeOutside);
    };
  }, [open, disabled]);
  function show() {
    const rect=trigger.current.getBoundingClientRect();
    const above=rect.top-16, below=window.innerHeight-rect.bottom-16;
    const up=above>=285 || above>=below;
    setPlacement({up,height:up?above:below});
    setOpen(true);
  }
  function close() { setOpen(false); trigger.current?.focus({preventScroll:true}); }
  function keys(e) {
    if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); close(); return; }
    if (!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Home','End'].includes(e.key)) return;
    e.preventDefault();
    const rows = [...root.current.querySelectorAll('[role="menuitemradio"]:not(:disabled)')];
    const next = e.key === 'Home' ? 0 : e.key === 'End' ? rows.length-1 :
      (rows.indexOf(document.activeElement) + (['ArrowDown','ArrowRight'].includes(e.key) ? 1 : -1) + rows.length) % rows.length;
    rows[next]?.focus();
  }
  return <div ref={root} className="wz-model-picker">
    <button type="button" ref={trigger} className="wz-model-trigger" aria-label={'选择模型，当前 '+label} aria-haspopup="menu"
      aria-expanded={open && !disabled} aria-controls={open ? id : undefined} disabled={disabled}
      title={resolved ? label+' · 思考强度：'+effortLabels[resolved] : label}
      onClick={() => open ? close() : show()} onKeyDown={e => { if (e.key === 'ArrowDown') {e.preventDefault(); show();} }}>
      <span>{label}</span>{resolved && <span className="wz-effort-current">{effortLabels[resolved]}</span>}<ChevronDown size={13} aria-hidden="true"/>
    </button>
    {open && !disabled && <div className="wz-model-menu" id={id} role="menu" aria-label="问舟模型" onKeyDown={keys}
      data-below={!placement.up || undefined} style={{maxHeight:placement.height}}>
      <div className="wz-model-heading">选择模型</div>
      {previewModels(mode, projectAvailable).map(name => <button type="button" role="menuitemradio" key={name} aria-checked={name === model}
        aria-describedby={name === TERRA ? id + '-terra-notice' : undefined}
        onClick={() => {
          selectPreviewModel(playerId, name);
          if (effortOptions(mode, name).length === 0) close();
        }}>
        {modelLabel(name, showTerra)}{name === model && <Check size={15}/>}
      </button>)}
      {mode === 'owner-locked' && <>
        <button type="button" role="menuitemradio" aria-checked={false} disabled>ChatGPT<LockKeyhole size={13}/></button>
        <small>在 Manager 登录 ChatGPT 后可用</small>
      </>}
      {showTerra && <small id={id + '-terra-notice'}>Terra 由 OpenAI 处理问题、相关前文和回答所需的游戏查询资料。</small>}
      {options.length > 0 && <div className="wz-effort-section" role="group" aria-label="思考强度">
        <div className="wz-model-heading">思考强度</div>
        <div className="wz-efforts">{options.map(value =>
          <button key={value} role="menuitemradio" type="button" aria-checked={effort === value}
            onClick={() => {selectEffort(playerId, model, value);close();}}>{effortLabels[value]}</button>)}</div>
      </div>}
    </div>}
  </div>;
}
