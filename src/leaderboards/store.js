import { useCallback, useMemo, useState, useSyncExternalStore } from 'react';
import { getDemoProjection, getDemoState, setDemoState, simulateDemoUpdate, subscribeDemo } from './demo.js';
import { boardDefinitions } from './contract.js';

export { boardDefinitions };
const confirmed = new Map();
const pageSize = 20;
const adapt = row => row ? { ...row, score: row.value } : null;
const cacheKey = dto => `${dto.board}|${dto.scope.kind}|${dto.scope.id}|${dto.viewer.playerId}`;

export function useLeaderboardPreview() {
  const state = useSyncExternalStore(subscribeDemo, getDemoState, getDemoState);
  return [state.selfAbsent ? 'unranked' : state.status, value => setDemoState({ status: value === 'unranked' ? 'ready' : value, selfAbsent: value === 'unranked', updateStep:0 }), simulateDemoUpdate];
}

// Demo-only read adapter: values, ranks and statuses come from the shared projection.
// The future provider must supply bounded aroundMe data; this module must not call a production API.
export function useLeaderboards(boardId) {
  const state = useSyncExternalStore(subscribeDemo, getDemoState, getDemoState);
  const [pages, setPages] = useState({});
  const first = useMemo(() => getDemoProjection(boardId, { page: 0, size: pageSize }), [state, boardId]);
  const key = cacheKey(first), count = pages[key] || confirmed.get(key)?.pages || 1;
  const snapshot = useMemo(() => {
    const rows = [...first.rows];
    if (first.status === 'ready') for (let page = 1; page < count; page++) rows.push(...getDemoProjection(boardId, { page, size: pageSize }).rows);
    const status = { unsupported: 'missing', error: 'unavailable' }[first.status] || first.status;
    const result = {
      ...first, status: status === 'ready' && !rows.length ? 'empty' : status,
      rows: rows.map(row => ({ ...adapt(row), isSelf: row.playerId === first.viewer.playerId })),
      self: adapt(first.self), totalPlayers: first.total,
    };
    if (result.status === 'ready' || result.status === 'empty') confirmed.set(key, { snapshot: result, pages: result.status === 'empty' ? 1 : count });
    if (first.status === 'unsupported') confirmed.delete(key);
    return result;
  }, [first, boardId, count, key]);
  const keepConfirmed = ['loading', 'unavailable', 'error'].includes(first.status) && confirmed.has(key);
  // A retry performs a new successful local demo response. It does not impersonate a server request.
  const retry = useCallback(() => setDemoState({ status: 'ready' }), []);
  const loadMore = useCallback(() => {
    if (snapshot.status !== 'ready' || snapshot.rows.length >= (snapshot.totalPlayers ?? snapshot.rows.length)) return;
    setPages(current => ({ ...current, [key]: count + 1 }));
  }, [snapshot, key, count]);
  const revealSelf = useCallback(() => {
    if (!snapshot.self?.rank) return;
    // Fixture-only positioning by supplied rank. Real integration requires aroundMe/cursor, never an all-page scan.
    const targetPage = Math.floor((snapshot.self.rank - 1) / pageSize);
    const target = getDemoProjection(boardId, { page: targetPage, size: pageSize });
    if (target.rows.some(row => row.playerId === snapshot.self.playerId)) setPages(current => ({ ...current, [key]: Math.max(count, targetPage + 1) }));
  }, [snapshot, boardId, key, count]);
  return {
    snapshot: keepConfirmed ? confirmed.get(key).snapshot : snapshot,
    refreshing: keepConfirmed && first.status === 'loading',
    refreshFailed: keepConfirmed && first.status !== 'loading', retry, loadMore, revealSelf,
  };
}
