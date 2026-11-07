// SPDX-License-Identifier: Apache-2.0
// Fixed fictional records for explicit Web previews. No production client may fall back to these.
import { BOARD_DEFINITIONS, MAX_PAGE_SIZE, STATUSES, validateProjection } from './contract.js';

export const DEMO_VIEWER = Object.freeze({ playerId: '7f1a0000-0000-4000-8000-000000000001', name: 'zhan_cpu' });
const UPDATED_AT = Date.parse('2026-11-06T00:00:00Z');
const STORAGE_KEY = 'pixelmc-hub.leaderboards.preview-controls.v1';
const names = ['风栖竹下', 'MossWalker', '云间渡客', '纸灯与晚风', 'BambooEcho', '山川寄远', '拾光旅人', '落日长街里慢慢散步的旅人', 'RiverLantern', '青石小巷', '木叶听雨', '橙色方块', '星河拾贝', 'CloudHarbor', '雪落松间', '白昼与夜航', '小麦田守望者', 'QuietSpruce', '月下归舟', '风铃轻响', '温热南瓜汤', '石径微光', '柳岸飞萤'];
const food = [168, 156, 156, 142, 137, 130, 124, 119, 113, 107, 102, 98, 91, 86, 81, 76, 72, 67, 62, 58, 53, 48, 43];
const fortunes = [58, 56, 56, 54, 51, 48, 46, 43, 41, 38, 35, 33, 31, 29, 27, 25, 23, 21, 20, 18, 16, 14, 12];
const hours = [384, 351, 351, 327, 298, 273, 251, 228, 207, 188, 171, 155, 140, 126, 113, 101, 90, 80, 71, 63, 56, 49, 43];
const avatar = Object.freeze({ url: '/assets/avatar.png', version: 'bundled-default-v1' });
const valueFor = (board, index) => board === 'food' ? food[index] : board === 'fortunes' ? fortunes[index] : hours[index] * 3_600_000;
function selfValue(board, scenario, previous) {
  if (scenario === 'outside') return previous ? (board === 'food' ? 65 : board === 'fortunes' ? 22 : 75 * 3_600_000) : (board === 'food' ? 41 : board === 'fortunes' ? 11 : 39 * 3_600_000);
  return previous ? (board === 'food' ? 150 : board === 'fortunes' ? 55 : 340 * 3_600_000) : (board === 'food' ? 176 : board === 'fortunes' ? 60 : 400 * 3_600_000);
}
const previewBonus = (board, step, index) => {
  const amount = step * (index === -1 ? 3 : index === 0 ? 8 : index === 3 ? 12 : index % 4 + 1);
  return board === 'online' ? amount * 1_800_000 : board === 'fortunes' ? Math.floor(amount / 3) : amount;
};
function snapshot(board, scenario, previous, step = 0) {
  // The final fictional participant is new in the current published snapshot.
  const rows = names.flatMap((name, index) => previous && index === names.length - 1 ? [] : [{ playerId: '7f1b0000-0000-4000-8000-' + String(index + 1).padStart(12, '0'), name, value: valueFor(board, index) + previewBonus(board, step, index), avatar }]);
  if (scenario !== 'absent') rows.push({ ...DEMO_VIEWER, value: selfValue(board, scenario, previous) + previewBonus(board, step, -1), avatar });
  rows.sort((a, b) => b.value - a.value || (a.playerId < b.playerId ? -1 : a.playerId > b.playerId ? 1 : 0));
  return Object.freeze(rows.map((item, index) => Object.freeze({ ...item, rank: index > 0 && rows[index - 1].value === item.value ? rows.findIndex(prior => prior.value === item.value) + 1 : index + 1 })));
}
function fixture(board, scenario, step = 0) {
  const previous = step > 0 ? snapshot(board, scenario, false, step - 1) : snapshot(board, scenario, true);
  const current = snapshot(board, scenario, false, step);
  const previousRanks = new Map(previous.map(item => [item.playerId, item.rank]));
  return Object.freeze({ previous, current: Object.freeze(current.map(item => Object.freeze({ ...item, rankDelta: previousRanks.has(item.playerId) ? previousRanks.get(item.playerId) - item.rank : null }))) });
}
// Rank full fixed snapshots once; deltas are competition-rank differences before any paging.
const fixtures = Object.fromEntries(BOARD_DEFINITIONS.map(board => [board.id, Object.fromEntries(['top', 'outside', 'absent'].map(scenario => [scenario, fixture(board.id, scenario)]))]));
function controls(value = {}) { return Object.freeze({ status: STATUSES.includes(value.status) ? value.status : 'ready', selfAbsent: value.selfAbsent === true, updateStep: Number.isSafeInteger(value.updateStep) && value.updateStep >= 0 && value.updateStep <= 1000 ? value.updateStep : 0 }); }
function readControls() { try { return controls(JSON.parse(globalThis.localStorage?.getItem(STORAGE_KEY) || '{}')); } catch { return controls(); } }
let state = readControls();
const listeners = new Set();
function accept(next) { if (state.status === next.status && state.selfAbsent === next.selfAbsent && state.updateStep === next.updateStep) return; state = next; for (const listener of listeners) listener(state); }
function storage(event) { if (event.key === STORAGE_KEY || event.key === null) accept(readControls()); }
/** Stable reference until an actual control change, suitable for useSyncExternalStore. */
export function getDemoState() { return state; }
export function setDemoState(patch = {}) {
  const next = controls({ ...state, ...patch }); accept(next);
  try { globalThis.localStorage?.setItem(STORAGE_KEY, JSON.stringify(next)); } catch { /* Preview works in memory when storage is unavailable. */ }
  return state;
}
// A manual preview event, never a timer or a production update source.
export function simulateDemoUpdate() {
  return setDemoState({status:'ready', updateStep:Math.min(1000, state.updateStep + 1)});
}
export function subscribeDemo(listener) {
  if (listeners.size === 0) globalThis.addEventListener?.('storage', storage);
  listeners.add(listener);
  return () => { listeners.delete(listener); if (listeners.size === 0) globalThis.removeEventListener?.('storage', storage); };
}
export function getDemoProjection(board = 'food', options = {}) {
  const definition = BOARD_DEFINITIONS.find(item => item.id === board);
  if (!definition) throw new TypeError('Unknown leaderboard');
  const status = options.status ?? state.status;
  if (!STATUSES.includes(status)) throw new TypeError('Unknown leaderboard status');
  const scenario = (options.selfAbsent ?? state.selfAbsent) ? 'absent' : options.selfOutside === true ? 'outside' : 'top';
  const withBaseline = options.withBaseline !== false;
  const page = options.page ?? 0, size = options.size ?? 20;
  if (!Number.isSafeInteger(page) || page < 0 || !Number.isSafeInteger(size) || size < 1 || size > MAX_PAGE_SIZE) throw new TypeError('Invalid leaderboard page/size');
  const step = options.updateStep ?? state.updateStep;
  if (!Number.isSafeInteger(step) || step < 0 || step > 1000) throw new TypeError('Invalid preview update step');
  const all = step === 0 ? fixtures[board][scenario].current : fixture(board, scenario, step).current;
  const confirmed = status === 'ready' || status === 'empty';
  const copy = item => ({ ...item, rankDelta: withBaseline ? item.rankDelta : null, avatar: { ...avatar } });
  const prefix = 'demo-leaderboards-v2:' + board + ':' + scenario;
  const currentRevision = step === 0 ? ':current' : ':event:' + step;
  const previousRevision = step === 0 ? ':previous' : step === 1 ? ':current' : ':event:' + (step - 1);
  const dto = {
    schemaVersion: 1, board, status, supported: status !== 'unsupported', period: 'all', ranking: 'competition',
    scope: { kind: definition.scope, id: definition.scope === 'archive' ? 'demo-server/demo-pack/demo-archive' : 'demo-fortunes-global', label: definition.scope === 'archive' ? '本存档' : '全局' },
    unit: definition.unit, rows: status === 'ready' ? all.slice(page * size, (page + 1) * size).map(copy) : [],
    total: status === 'ready' ? all.length : status === 'empty' ? 0 : null,
    page, size, updatedAt: confirmed ? UPDATED_AT + step * 1000 : null,
    revision: confirmed ? prefix + (status === 'empty' ? ':empty' : withBaseline ? currentRevision : ':initial:' + step) : null,
    comparisonRevision: status === 'ready' && withBaseline ? prefix + previousRevision : null,
    viewer: { ...DEMO_VIEWER },
    self: status === 'ready' ? copy(all.find(item => item.playerId === DEMO_VIEWER.playerId) || { ...DEMO_VIEWER, rank: null, value: null, rankDelta: null }) : null,
  };
  return validateProjection(dto);
}
