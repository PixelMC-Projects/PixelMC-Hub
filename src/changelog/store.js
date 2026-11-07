import { useSyncExternalStore } from 'react';
import { STORAGE_KEY, seed, reduceAction, normalizeState } from './model.js';
const EVENT = 'pixelmc-hub-changelog-preview-change';
let raw = null, cached = seed();
function snapshot() {
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved !== raw) {
    raw = saved;
    try { const next = JSON.parse(saved); cached = next?.schema === 1 && Array.isArray(next.entries) ? normalizeState(next) : seed(); }
    catch { cached = seed(); }
  }
  return cached;
}
function subscribe(listener) {
  const changed = e => { if (e.type === EVENT || e.key === STORAGE_KEY || e.key === null) listener(); };
  window.addEventListener('storage', changed); window.addEventListener(EVENT, changed);
  return () => { window.removeEventListener('storage', changed); window.removeEventListener(EVENT, changed); };
}
export function useChangelog() { return useSyncExternalStore(subscribe, snapshot, () => cached); }
export function dispatch(action) {
  const next = reduceAction(snapshot(), action);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  window.dispatchEvent(new Event(EVENT));
  return next;
}
