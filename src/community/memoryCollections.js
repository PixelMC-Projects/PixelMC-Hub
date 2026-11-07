// SPDX-License-Identifier: Apache-2.0
// Explicit fictional acquisition events, independent of title-definition dates.
import { titles, fortuneById } from '../data.js';
import { MEMORY_ARCHIVES, SELF_PROFILE, COMMUNITY_PLAYERS, getBaseCommunityProfile, requireArchiveMember, readCommunityValue, subscribeCommunity } from './demo.js';
import { createMemorySubmissionId, memoryScopeKey, withMemoryWriteLock, writeMemoryPreview } from './memoryStore.js';

const events = [
  ['create-mountain', 'title', 'mountain', '2026-04-03T19:20:00+08:00'],
  ['create-star', 'title', 'star', '2026-06-12T21:05:00+08:00'],
  ['create-fortune-4', 'fortune', 4, '2026-05-10T18:30:00+08:00'],
  ['create-fortune-2', 'fortune', 2, '2026-06-15T20:16:00+08:00'],
  ['maple-mountain', 'title', 'mountain', '2026-07-12T18:45:00+08:00'],
  ['maple-harvest', 'title', 'harvest', '2026-08-08T20:30:00+08:00'],
  ['maple-wood', 'title', 'wood', '2026-09-18T21:12:00+08:00'],
  ['maple-fortune-4', 'fortune', 4, '2026-08-18T19:02:00+08:00'],
  ['maple-fortune-3', 'fortune', 3, '2026-09-20T20:40:00+08:00'],
  ['vanilla-harvest', 'title', 'harvest', '2026-10-21T20:15:00+08:00'],
  ['vanilla-wood', 'title', 'wood', '2026-10-26T21:10:00+08:00'],
  ['vanilla-star', 'title', 'star', '2026-10-30T21:25:00+08:00'],
  ['vanilla-dew', 'title', 'dew', '2026-11-02T20:26:00+08:00'],
  ['vanilla-fortune-3', 'fortune', 3, '2026-10-30T20:26:00+08:00'],
  ['vanilla-fortune-17', 'fortune', 17, '2026-11-02T20:26:00+08:00'],
  ['vanilla-fortune-1', 'fortune', 1, '2026-11-03T20:26:00+08:00'],
];

const otherPlayerEvents = COMMUNITY_PLAYERS.filter(player => !player.isSelf).flatMap(player => {
  const profile = getBaseCommunityProfile(player.playerId);
  return player.memoryIds.flatMap(archiveId => {
    const archive = MEMORY_ARCHIVES.find(item => item.id === archiveId);
    const start = Date.parse(`${archive.start.replaceAll('.', '-')}T18:30:00+08:00`);
    return [['title', 'mountain', 0], ['title', profile.featuredTitleId, 5], ['fortune', 4, 8], ['fortune', profile.featuredFortune.id, 12]]
      .map(([kind, itemId, day], index) => ({ eventId: `${player.playerId}/${archiveId}/${index}`, playerId: player.playerId,
        kind, itemId, obtainedAt: new Date(start + day * 86_400_000).toISOString(), demo: true }));
  });
});
export const DEMO_MEMORY_ACQUISITIONS = Object.freeze([
  ...events.map(([eventId, kind, itemId, obtainedAt]) => ({ eventId, playerId: SELF_PROFILE.playerId, kind, itemId, obtainedAt, demo: true })),
  ...otherPlayerEvents,
].map(event => Object.freeze(event)));

function failure(code, message) {
  return Object.assign(new Error(message), { code });
}

function findArchive(archiveId) {
  const archive = MEMORY_ARCHIVES.find(item => item.id === archiveId);
  if (!archive) throw failure('unknown-archive', '找不到这份整合包纪念。');
  return archive;
}

export function memoryCollectionInterval(archiveId) {
  const archive = findArchive(archiveId);
  return {
    start: Date.parse(`${archive.start.replaceAll('.', '-')}T00:00:00+08:00`),
    end: archive.end ? Date.parse(`${archive.end.replaceAll('.', '-')}T23:59:59.999+08:00`) : Date.now(),
  };
}

export function getMemoryCollectionCandidates(playerId, archiveId) {
  requireArchiveMember(playerId, archiveId);
  const interval = memoryCollectionInterval(archiveId);
  return DEMO_MEMORY_ACQUISITIONS.filter(event => {
    const obtainedAt = Date.parse(event.obtainedAt);
    return event.playerId === playerId && obtainedAt >= interval.start && obtainedAt <= interval.end;
  }).flatMap(event => {
    const item = event.kind === 'title' ? titles.find(title => title.id === event.itemId) : fortuneById(event.itemId);
    return item?.id === event.itemId ? [{ ...event, item }] : [];
  }).sort((a, b) => Date.parse(b.obtainedAt) - Date.parse(a.obtainedAt));
}

export function filterMemoryCollectionSelection(playerId, archiveId, selection = {}) {
  const candidates = getMemoryCollectionCandidates(playerId, archiveId);
  return {
    titleEventId: candidates.find(event => event.kind === 'title' && event.eventId === selection?.titleEventId)?.eventId ?? null,
    fortuneEventId: candidates.find(event => event.kind === 'fortune' && event.eventId === selection?.fortuneEventId)?.eventId ?? null,
  };
}

export function initialMemoryCollections(playerId, archiveId) {
  const archive = findArchive(archiveId);
  const candidates = getMemoryCollectionCandidates(playerId, archiveId);
  return {
    playerId, archiveId, revision: null, updatedAt: null,
    selection: {
      titleEventId: (candidates.find(event => event.kind === 'title' && event.itemId === archive.titleId) ?? (playerId === SELF_PROFILE.playerId ? null : candidates.find(event => event.kind === 'title')))?.eventId ?? null,
      fortuneEventId: (candidates.find(event => event.kind === 'fortune' && event.itemId === archive.fortuneId) ?? (playerId === SELF_PROFILE.playerId ? null : candidates.find(event => event.kind === 'fortune')))?.eventId ?? null,
    },
  };
}

function storageKey(playerId, archiveId) {
  return `${memoryScopeKey(playerId, archiveId)}:collections`;
}

export function readMemoryCollections(playerId, archiveId) {
  const initial = initialMemoryCollections(playerId, archiveId);
  try {
    if (!globalThis.localStorage) throw failure('storage-unavailable', '浏览器存储暂不可用，请恢复存储权限后重试。');
    const raw = readCommunityValue(storageKey(playerId, archiveId));
    if (raw === null) return initial;
    let saved;
    try { saved = JSON.parse(raw); } catch { throw failure('invalid-data', '已保存的纪念选择读取异常，请重新读取后重试。'); }
    if (saved?.schemaVersion !== 1) throw failure('unsupported-version', '纪念选择版本暂不支持，请保留数据并更新预览。');
    if (saved.playerId !== playerId || saved.archiveId !== archiveId ||
        typeof saved.revision !== 'string' || !saved.revision || (!Number.isSafeInteger(saved.updatedAt) || saved.updatedAt <= 0 || saved.updatedAt > 8_640_000_000_000_000) ||
        !saved.selection || typeof saved.selection !== 'object' || Array.isArray(saved.selection) ||
        ['titleEventId', 'fortuneEventId'].some(key => saved.selection[key] !== undefined && saved.selection[key] !== null && typeof saved.selection[key] !== 'string')) {
      throw failure('invalid-data', '已保存的纪念选择读取异常，请重新读取后重试。');
    }
    return { ...initial, revision: saved.revision, updatedAt: saved.updatedAt,
      selection: filterMemoryCollectionSelection(playerId, archiveId, saved.selection) };
  } catch (error) {
    if (typeof error.code === 'string') throw error;
    throw failure('storage-unavailable', '浏览器存储暂不可用，请恢复存储权限后重试。');
  }
}

export async function saveMemoryCollections({ playerId, archiveId, selection, expectedRevision }) {
  if (playerId !== SELF_PROFILE.playerId) throw failure('read-only', '仅能选择自己的旅途纪念。');
  findArchive(archiveId);
  return withMemoryWriteLock(playerId, archiveId, () => {
    const current = readMemoryCollections(playerId, archiveId);
    if (current.revision !== expectedRevision) throw failure('revision-conflict', '纪念选择已在另一页面更新，请重新打开编辑。');
    // Resolve IDs against this player's dated acquisition events again at submission.
    const filtered = filterMemoryCollectionSelection(playerId, archiveId, selection);
    const filteredCount = ['titleEventId', 'fortuneEventId'].filter(key => selection?.[key] && selection[key] !== filtered[key]).length;
    const record = { playerId, archiveId, selection: filtered, revision: createMemorySubmissionId(), updatedAt: Date.now() };
    writeMemoryPreview(storageKey(playerId, archiveId), { schemaVersion: 1, ...record });
    return { ...record, filteredCount };
  });
}

export function subscribeMemoryCollections(playerId, archiveId, listener) {
  const key = storageKey(playerId, archiveId);
  return subscribeCommunity(event => {
    if (event.kind === 'storage' ? event.key === null || event.key === key : event.playerId === playerId && event.archiveId === archiveId) listener();
  });
}

const obtainedDateFormat = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
});

export function formatAcquisitionDate(timestamp) {
  const parts = Object.fromEntries(obtainedDateFormat.formatToParts(new Date(timestamp)).map(part => [part.type, part.value]));
  return `${parts.year}.${parts.month}.${parts.day}`;
}
