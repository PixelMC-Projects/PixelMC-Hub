// SPDX-License-Identifier: Apache-2.0
// Fictional Web-preview profiles and pack memories. Never use as a production fallback.
import { player as previewPlayer, titles, fortuneById, archive, todayKey } from '../data.js';
import { getDemoProjection } from '../leaderboards/demo.js';
import { PROFILE_PATTERNS } from './profile-patterns.js';

export const PROFILE_TITLES = titles.filter(title => title.owned);

export const PROFILE_BACKGROUNDS = [
  { id: 'mist', name: '雾蓝', color: '#c8d6ec' },
  { id: 'lilac', name: '浅藤', color: '#d9cfe9' },
  { id: 'sage', name: '青苔', color: '#ccdcd2' },
  { id: 'sand', name: '麦穗', color: '#e2d7c3' },
  { id: 'blush', name: '蔷薇', color: '#e6ced5' },
  { id: 'apricot', name: '杏光', color: '#e7d1bd' },
  { id: 'tide', name: '浅汐', color: '#c2dcd9' },
  { id: 'twilight', name: '暮云', color: '#d0cfe1' },
  { id: 'frost', name: '霜青', color: '#cedee4' },
  { id: 'stone', name: '雨石', color: '#d5d6d2' },
];

export const MEMORY_ARCHIVES = [
  {
    id: 'vanilla-271', name: '香草纪元', version: '2.7.1', theme: 'sage',
    coverPattern: 'arcs', coverColor: 'sage',
    start: '2026.10.17', end: null, status: '旅程继续中',
    description: '从第一间木屋，到有人等你回来的地方。',
    hours: '128.7', visits: 36, collections: 23,
    titleId: 'dew', fortuneId: 1, item: '/assets/collection-chest.png',
    note: '把小屋修在河边，是这个存档里最早做的决定。后来有了菜园、木桥，还有夜里总亮着的那盏灯。\n\n最喜欢的是下线前沿着河岸走一段，看看今天又多了谁的建筑。',
    moment: '2026.11.02', momentText: '收下「朝露行者」，记住这一百小时。',
  },
  {
    id: 'maple-16', name: '枫林物语', version: '1.6', theme: 'sand',
    coverPattern: 'ridges', coverColor: 'sand',
    start: '2026.07.12', end: '2026.10.06', status: '已归档',
    description: '那年秋天，我们在枫林里留了一座小镇。',
    hours: '216.5', visits: 68, collections: 34,
    titleId: 'wood', fortuneId: 3, item: '/assets/fortune-barrel.png',
    note: '小镇的第一条路铺了很久，因为每个人都想在路边留一点自己的东西。有人种花，有人修路灯，我在桥头放了一张长椅。\n\n最后一次离开时，站在那里看了一整场日落。',
    moment: '2026.09.18', momentText: '和朋友一起完成河畔长桥。',
  },
  {
    id: 'create-60', name: '机械动力', version: '6.0', theme: 'mist',
    coverPattern: 'grid', coverColor: 'mist',
    start: '2026.04.03', end: '2026.06.29', status: '已归档',
    description: '第一台蒸汽机开始转动的声音，还记得。',
    hours: '163.2', visits: 52, collections: 19,
    titleId: 'star', fortuneId: 2, item: '/assets/collection-chest.png',
    note: '从手摇曲柄开始，一点一点把整条生产线接起来。第一次看见列车准点进站时，大家都停下手里的事，跟着它跑了一段。\n\n工厂很大，最舍不得的却是站台旁那间小小的休息室。',
    moment: '2026.06.12', momentText: '环线列车第一次完整跑过所有站点。',
  },
  // Older fictional archives exercise browsing beyond the three newest packs.
  {
    id: 'spring-14', name: '春日田园', version: '1.4', theme: 'sage',
    coverPattern: 'arcs', coverColor: 'rose',
    start: '2026.02.08', end: '2026.03.22', status: '已归档',
    description: '在山脚下种一片田，等第一场春雨。',
    hours: '82.4', visits: 27, collections: 12,
    titleId: 'harvest', fortuneId: 4, item: '/assets/fortune-barrel.png',
    note: '每天上线先去田边转一圈。等到麦田变成金黄色，我们在小路尽头搭了一间风车房。\n\n那段时间最常说的话，是明天再一起收菜。',
    moment: '2026.03.09', momentText: '风车房落成，收下第一季的麦子。',
  },
  {
    id: 'winter-12', name: '雪境漫游', version: '1.2', theme: 'mist',
    coverPattern: 'stars', coverColor: 'lilac',
    start: '2025.12.06', end: '2026.01.25', status: '已归档',
    description: '雪地里的脚印，连起了彼此的灯火。',
    hours: '96.8', visits: 31, collections: 15,
    titleId: 'moon', fortuneId: 2, item: '/assets/collection-chest.png',
    note: '沿着结冰的河流走到山脚，我们找到一片可以搭营地的空地。后来每个人都在门前点了一盏灯。\n\n最后一次夜游时，回头还能看见整条山路上的光。',
    moment: '2026.01.11', momentText: '点亮雪山营地的最后一盏路灯。',
  },
];

export function getProfileMemories(profile) {
  return MEMORY_ARCHIVES.filter(memory => profile.memoryIds?.includes(memory.id))
    .sort((left, right) => (right.end ?? '9999.12.31').localeCompare(left.end ?? '9999.12.31') || right.start.localeCompare(left.start));
}

export const SELF_PROFILE = Object.freeze({
  playerId: previewPlayer.uuid,
  name: previewPlayer.name,
  isSelf: true,
  demo: true,
  joined: previewPlayer.joined,
  title: titles.find(title => title.id === 'dew'),
  bio: '喜欢沿着河岸慢慢走，也喜欢把偶然的相遇收进旅途。',
  background: 'mist',
  profilePattern: 'classic',
  featuredTitleId: 'star',
  featuredTitle: titles.find(title => title.id === 'star'),
  // Use this acquired archive instance so its date stays distinct from the fortune definition.
  featuredFortune: archive['2026-11-03'],
  featuredMemoryId: 'maple-16',
  memoryIds: MEMORY_ARCHIVES.map(memory => memory.id),
});

const otherProfiles = [
  { bio: '在竹林里搭了一间小屋。路过的话，可以来喝杯茶。', background: 'sage', titleId: 'wood', fortuneId: 3, fortuneDate: '2026-10-30' },
  { bio: '把看过的风景记下来，把每一条岔路都走一遍。', background: 'lilac', titleId: 'star', fortuneId: 2, fortuneDate: '2026-10-25' },
  { bio: '喜欢种田、钓鱼，还有黄昏时分的村庄。', background: 'sand', titleId: 'harvest', fortuneId: 4, fortuneDate: '2026-11-01' },
];

// Explicit fictional membership, sharing the UUIDs already used by the leaderboard.
const archiveMembers = {
  'vanilla-271': [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15],
  'maple-16': [1, 2, 4, 5, 7, 8, 10, 11, 14, 16, 18, 20],
  'create-60': [2, 3, 5, 6, 9, 12, 15, 17, 21],
  'spring-14': [1, 4, 6, 8, 13, 18, 22],
  'winter-12': [2, 5, 7, 11, 16, 19, 23],
};
const onlineMembers = [1, 2, 5, 8, 11, 14, 17, 23];
export const COMMUNITY_PLAYERS = getDemoProjection('food', { size: 100, status: 'ready', selfAbsent: false, updateStep: 0 }).rows.map(row => {
  const isSelf = row.playerId === SELF_PROFILE.playerId;
  const number = Number(row.playerId.slice(-12));
  const isOnline = isSelf || onlineMembers.includes(number);
  const lastLoginAt = isOnline ? '2026-11-06T17:42:00+08:00' : `2026-11-${String(1 + number % 5).padStart(2, '0')}T19:25:00+08:00`;
  return Object.freeze({ playerId: row.playerId, name: row.name, isSelf, demo: true, skin: null,
    isOnline, lastLoginAt, lastSeen: isOnline ? '2026-11-06T18:10:00+08:00' : lastLoginAt,
    memoryIds: Object.keys(archiveMembers).filter(id => isSelf || archiveMembers[id].includes(number)) });
});

export function communityFailure(code, message) { return Object.assign(new Error(message), { code }); }

export function getCommunityPlayer(playerId) {
  const player = COMMUNITY_PLAYERS.find(item => item.playerId === playerId);
  if (!player) throw communityFailure('unknown-player', '找不到这位演示玩家。');
  return { ...player, memoryIds: [...player.memoryIds] };
}

export function getBaseCommunityProfile(playerId) {
  const player = getCommunityPlayer(playerId);
  if (player.isSelf) return { ...SELF_PROFILE, ...player };
  const seed = Array.from(playerId).reduce((sum, character) => sum + character.charCodeAt(0), 0);
  const sample = otherProfiles[seed % otherProfiles.length];
  return { ...player, joined: '2026/10/17', bio: sample.bio, background: sample.background, profilePattern: 'classic',
    title: titles.find(title => title.id === sample.titleId), featuredTitleId: sample.titleId,
    featuredTitle: titles.find(title => title.id === sample.titleId),
    featuredFortune: { ...fortuneById(sample.fortuneId), date: sample.fortuneDate, status: 'archived', playerId },
    featuredMemoryId: player.memoryIds.includes('maple-16') ? 'maple-16' : player.memoryIds[0] ?? null };
}

export function requireArchiveMember(playerId, archiveId) {
  const player = getCommunityPlayer(playerId);
  const archive = MEMORY_ARCHIVES.find(item => item.id === archiveId);
  if (!archive) throw communityFailure('unknown-archive', '找不到这份整合包纪念。');
  if (!player.memoryIds.includes(archiveId)) throw communityFailure('not-an-archive-member', '这位玩家没有这段整合包旅程。');
  return { player, archive };
}

export function getPublicProfile(row = {}) {
  if (COMMUNITY_PLAYERS.some(player => player.playerId === row.playerId)) return readCommunityProfile(row.playerId);
  const name = row.name || '旅人';
  const seed = Array.from(row.playerId || name).reduce((sum, character) => sum + character.charCodeAt(0), 0);
  const sample = otherProfiles[seed % otherProfiles.length];
  return { playerId: row.playerId || `demo-profile-${seed}`, name, isSelf: false, demo: true, joined: '2026/10/17',
    bio: sample.bio, background: sample.background, profilePattern: 'classic', skin: null,
    title: titles.find(title => title.id === sample.titleId), featuredTitleId: sample.titleId,
    featuredTitle: titles.find(title => title.id === sample.titleId),
    featuredFortune: { ...fortuneById(sample.fortuneId), date: sample.fortuneDate, status: 'archived' },
    featuredMemoryId: null, memoryIds: [] };
}

export const HUB_PREVIEW_STORAGE_KEY = 'pixelmc-player-hub-preview-v1';
const legacyStorageKey = 'pixelmc-hub.community.demo-profile.v1';
const storagePrefix = 'pixelmc-hub.community.demo-profile.v2.';
const transactionKey = 'pixelmc-hub.community.pending-profile-transaction.v1';
const eventName = 'pixelmc-hub.community.changed';
export const profileStorageKey = playerId => storagePrefix + encodeURIComponent(playerId);

export function communityStorage() {
  try { if (globalThis.localStorage) return globalThis.localStorage; } catch { /* Report below. */ }
  throw communityFailure('storage-unavailable', '浏览器存储暂不可用，请恢复存储权限后重试。');
}

export function parseCommunityJSON(raw, label = '社区资料') {
  let value;
  try { value = JSON.parse(raw); } catch { throw communityFailure('invalid-data', `${label}读取异常，请重新读取后重试。`); }
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw communityFailure('invalid-data', `${label}格式异常，请重新读取后重试。`);
  return value;
}

function readPendingTransaction(storage) {
  const raw = storage.getItem(transactionKey);
  if (raw === null) return null;
  const transaction = parseCommunityJSON(raw, '未完成的社区保存记录');
  if (transaction.schemaVersion !== 1 || !Array.isArray(transaction.changes) || transaction.changes.length !== 2 ||
      new Set(transaction.changes.map(change => change.key)).size !== 2 ||
      transaction.changes.some(change => !change || !(typeof change.key === 'string' && (change.key === HUB_PREVIEW_STORAGE_KEY || change.key.startsWith(storagePrefix))) ||
        !(change.before === null || typeof change.before === 'string') || typeof change.after !== 'string')) {
    throw communityFailure('invalid-data', '未完成的社区保存记录损坏，请保留存储数据并重新读取。');
  }
  return transaction;
}

// A pending multi-key write reads as its previous snapshot until every key commits.
export function readCommunityValue(key) {
  try {
    const storage = communityStorage();
    const pending = readPendingTransaction(storage);
    const change = pending?.changes.find(item => item.key === key);
    return change ? change.before : storage.getItem(key);
  } catch (error) {
    if (typeof error.code === 'string') throw error;
    throw communityFailure('storage-unavailable', '浏览器存储读取失败，请恢复后重试。');
  }
}

function restorePendingTransaction(storage, pending) {
  for (const change of pending.changes) {
    if (change.before === null) storage.removeItem(change.key);
    else storage.setItem(change.key, change.before);
  }
  storage.removeItem(transactionKey);
}

export function commitCommunityValues(values) {
  const storage = communityStorage();
  const changes = values.map(({ key, value }) => ({ key, before: storage.getItem(key), after: JSON.stringify(value) }));
  let pendingWritten = false;
  try {
    if (changes.length > 1) {
      storage.setItem(transactionKey, JSON.stringify({ schemaVersion: 1, changes }));
      pendingWritten = true;
    }
    for (const change of changes) storage.setItem(change.key, change.after);
    if (pendingWritten) storage.removeItem(transactionKey);
  } catch {
    if (pendingWritten) {
      try { restorePendingTransaction(storage, { changes }); } catch { /* Reads retain the old snapshot until recovery succeeds. */ }
    }
    throw communityFailure('save-failed', '保存未完成，浏览器存储可能已满。请保留草稿后重试。');
  }
}

export async function withCommunityWriteLock(name, write) {
  if (!globalThis.navigator?.locks?.request) throw communityFailure('locking-unavailable', '当前浏览器无法安全保存，请使用支持 Web Locks 的浏览器。');
  try {
    return await globalThis.navigator.locks.request(name, () => {
      try {
        const storage = communityStorage();
        const pending = readPendingTransaction(storage);
        if (pending) restorePendingTransaction(storage, pending);
        return write();
      } catch (error) {
        if (typeof error.code === 'string') throw error;
        throw communityFailure('save-failed', '保存未完成，请保留草稿后重试。');
      }
    });
  } catch (error) {
    if (typeof error.code === 'string') throw error;
    throw communityFailure('locking-unavailable', '当前浏览器无法取得写锁，请保留草稿后重试。');
  }
}

export function emitCommunityChange(detail) {
  if (globalThis.CustomEvent && globalThis.dispatchEvent) globalThis.dispatchEvent(new CustomEvent(eventName, { detail }));
}

export function subscribeCommunity(listener) {
  const sameTab = event => listener(event.detail);
  const crossTab = event => {
    if (event.key === null || event.key === HUB_PREVIEW_STORAGE_KEY || event.key?.startsWith('pixelmc-hub.community.')) {
      listener({ kind: 'storage', key: event.key });
    }
  };
  globalThis.addEventListener?.(eventName, sameTab);
  globalThis.addEventListener?.('storage', crossTab);
  return () => { globalThis.removeEventListener?.(eventName, sameTab); globalThis.removeEventListener?.('storage', crossTab); };
}

export function readHubPreviewState() {
  const raw = readCommunityValue(HUB_PREVIEW_STORAGE_KEY);
  const value = raw === null ? {} : parseCommunityJSON(raw, '手册预览设置');
  if (value.displayDate !== undefined && value.displayDate !== null && (typeof value.displayDate !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value.displayDate))) {
    throw communityFailure('invalid-data', '展示签日期读取异常，请重新读取。');
  }
  if (value.fortuneState !== undefined && !['undrawn', 'unread', 'read', 'archived'].includes(value.fortuneState)) throw communityFailure('invalid-data', '签笺状态读取异常，请重新读取。');
  return { ...value, displayDate: value.displayDate === undefined ? todayKey : value.displayDate, fortuneState: value.fortuneState ?? 'archived' };
}

export function readSelfFortuneState() {
  const state = readHubPreviewState();
  return { displayDate: state.displayDate, fortuneState: state.fortuneState };
}

export function getProfileFortuneChoices(playerId) {
  const profile = getBaseCommunityProfile(playerId);
  if (profile.isSelf) {
    const state = readHubPreviewState();
    return Object.values(archive).filter(item => item.date !== todayKey || state.fortuneState === 'archived');
  }
  return [profile.featuredFortune, { ...fortuneById(4), date: '2026-10-21', status: 'archived', playerId },
    { ...fortuneById(2), date: '2026-10-24', status: 'archived', playerId }];
}

export function getProfileTitleChoices(playerId) {
  const profile = getBaseCommunityProfile(playerId);
  return profile.isSelf ? PROFILE_TITLES : PROFILE_TITLES.filter(title => [profile.featuredTitleId, 'mountain', 'harvest'].includes(title.id));
}

export async function saveSelfFortuneSelection(date) {
  return withCommunityWriteLock('pixelmc-hub.community.write', () => {
    if (date !== null && !getProfileFortuneChoices(SELF_PROFILE.playerId).some(item => item.date === date)) throw communityFailure('invalid-selection', '请选择本人已归档的签笺。');
    const state = { ...readHubPreviewState(), displayDate: date };
    commitCommunityValues([{ key: HUB_PREVIEW_STORAGE_KEY, value: state }]);
    emitCommunityChange({ kind: 'profile', playerId: SELF_PROFILE.playerId, source: 'self' });
    return { displayDate: state.displayDate, fortuneState: state.fortuneState };
  });
}

export async function persistHubPreviewState(state, { writeFortuneState = false } = {}) {
  return withCommunityWriteLock('pixelmc-hub.community.write', () => {
    const current = readHubPreviewState();
    const fortuneState = writeFortuneState ? state.fortuneState ?? current.fortuneState : current.fortuneState;
    if (!['undrawn', 'unread', 'read', 'archived'].includes(fortuneState)) throw communityFailure('invalid-selection', '签笺状态无效，请重新选择。');
    const next = { ...current, ...state, displayDate: current.displayDate, fortuneState };
    commitCommunityValues([{ key: HUB_PREVIEW_STORAGE_KEY, value: next }]);
    if (next.fortuneState !== current.fortuneState) emitCommunityChange({ kind: 'profile', playerId: SELF_PROFILE.playerId, source: 'self' });
    return next;
  });
}

function profilePreferences(value, profile) {
  const titleId = profile.featuredTitleId === undefined ? profile.featuredTitle?.id ?? null : profile.featuredTitleId;
  const memoryIds = profile.memoryIds ?? [];
  return {
    bio: typeof value?.bio === 'string' ? value.bio.slice(0, 120) : profile.bio ?? '',
    background: PROFILE_BACKGROUNDS.some(item => item.id === value?.background) ? value.background : profile.background ?? 'mist',
    profilePattern: PROFILE_PATTERNS.some(item => item.id === value?.profilePattern) ? value.profilePattern : profile.profilePattern ?? 'classic',
    featuredTitleId: value?.featuredTitleId === null || getProfileTitleChoices(profile.playerId).some(item => item.id === value?.featuredTitleId) ? value.featuredTitleId : titleId,
    featuredMemoryId: value?.featuredMemoryId === null || memoryIds.includes(value?.featuredMemoryId) ? value.featuredMemoryId : profile.featuredMemoryId ?? null,
  };
}

function readProfileRecord(profile) {
  let raw = readCommunityValue(profileStorageKey(profile.playerId));
  if (raw === null && profile.playerId === SELF_PROFILE.playerId) raw = readCommunityValue(legacyStorageKey);
  if (raw === null) return { values: {}, revision: null, updatedAt: null };
  const value = parseCommunityJSON(raw, '玩家名片');
  if (value.schemaVersion !== undefined && value.schemaVersion !== 2) throw communityFailure('unsupported-version', '玩家名片版本暂不支持，请保留数据并更新预览。');
  if (value.schemaVersion === 2 && (value.playerId !== profile.playerId || typeof value.revision !== 'string' || !value.revision || !Number.isSafeInteger(value.updatedAt) || value.updatedAt <= 0 || value.updatedAt > 8_640_000_000_000_000)) throw communityFailure('invalid-data', '玩家名片修订信息异常，请重新读取。');
  if (value.bio !== undefined && (typeof value.bio !== 'string' || value.bio.length > 120)) throw communityFailure('invalid-data', '玩家简介读取异常，请重新读取。');
  for (const field of ['background', 'profilePattern', 'featuredTitleId', 'featuredMemoryId', 'featuredFortuneDate']) {
    if (value[field] !== undefined && value[field] !== null && typeof value[field] !== 'string') throw communityFailure('invalid-data', '玩家展示选项读取异常，请重新读取。');
  }
  return { values: value, revision: value.revision ?? null, updatedAt: value.updatedAt ?? null };
}

function profileRevision(profile, record, fortuneState) {
  return profile.isSelf ? JSON.stringify([record.revision, fortuneState.displayDate, fortuneState.fortuneState]) : record.revision;
}

export function readCommunityProfile(playerId) {
  const profile = getBaseCommunityProfile(playerId);
  const record = readProfileRecord(profile);
  const choices = getProfileFortuneChoices(playerId);
  const state = profile.isSelf ? readSelfFortuneState() : null;
  const date = profile.isSelf ? state.displayDate : record.values.featuredFortuneDate === undefined ? profile.featuredFortune?.date ?? null : record.values.featuredFortuneDate;
  const prefs = profilePreferences(record.values, profile);
  return { ...profile, ...prefs,
    featuredTitle: getProfileTitleChoices(playerId).find(title => title.id === prefs.featuredTitleId) ?? null,
    featuredFortuneDate: date, featuredFortune: choices.find(fortune => fortune.date === date) ?? null,
    revision: profileRevision(profile, record, state), updatedAt: record.updatedAt };
}

export function readProfilePreferences(profile) {
  if (!profile.demo || !COMMUNITY_PLAYERS.some(player => player.playerId === profile.playerId)) return profile;
  try {
    const saved = readCommunityProfile(profile.playerId);
    return { ...profile, bio: saved.bio, background: saved.background, profilePattern: saved.profilePattern,
      featuredTitleId: saved.featuredTitleId, featuredTitle: saved.featuredTitle, featuredMemoryId: saved.featuredMemoryId,
      revision: saved.revision, updatedAt: saved.updatedAt };
  } catch (error) {
    return { ...profile, profileReadError: error.message, profileReadErrorCode: error.code };
  }
}

function validatedProfilePatch(profile, patch) {
  const allowed = ['bio', 'background', 'profilePattern', 'featuredTitleId', 'featuredFortuneDate', 'featuredMemoryId'];
  if (!patch || typeof patch !== 'object' || Array.isArray(patch) || Object.keys(patch).some(key => !allowed.includes(key))) throw communityFailure('invalid-patch', '玩家名片包含不支持的编辑字段。');
  if (patch.bio !== undefined && (typeof patch.bio !== 'string' || patch.bio.length > 120)) throw communityFailure('invalid-text', '个人简介最多 120 字。');
  const options = { background: PROFILE_BACKGROUNDS.map(item => item.id), profilePattern: PROFILE_PATTERNS.map(item => item.id),
    featuredTitleId: [null, ...getProfileTitleChoices(profile.playerId).map(item => item.id)],
    featuredMemoryId: [null, ...profile.memoryIds], featuredFortuneDate: [null, ...getProfileFortuneChoices(profile.playerId).map(item => item.date)] };
  for (const [key, values] of Object.entries(options)) if (patch[key] !== undefined && !values.includes(patch[key])) throw communityFailure('invalid-selection', '所选展示项不属于这位玩家或已不可用，请重新选择。');
  return { ...patch, ...(patch.bio === undefined ? {} : { bio: patch.bio.trim() }) };
}

export function commitProfilePreview(playerId, patch, expectedRevision, source) {
  const current = readCommunityProfile(playerId);
  if (current.revision !== expectedRevision) throw communityFailure('revision-conflict', '玩家名片已被更新，请保留草稿并重新读取。');
  const changes = validatedProfilePatch(current, patch);
  const next = { ...current, ...changes };
  const stored = { schemaVersion: 2, playerId, ...profilePreferences(next, current), revision: globalThis.crypto.randomUUID(), updatedAt: Date.now() };
  if (!current.isSelf) stored.featuredFortuneDate = next.featuredFortuneDate;
  const writes = [{ key: profileStorageKey(playerId), value: stored }];
  if (current.isSelf && Object.hasOwn(changes, 'featuredFortuneDate')) writes.push({ key: HUB_PREVIEW_STORAGE_KEY, value: { ...readHubPreviewState(), displayDate: changes.featuredFortuneDate } });
  commitCommunityValues(writes);
  emitCommunityChange({ kind: 'profile', playerId, source });
  return readCommunityProfile(playerId);
}

export async function saveProfilePreferences(profile, patch) {
  if (!profile.isSelf || !profile.demo || profile.playerId !== SELF_PROFILE.playerId) return { profile, persisted: false, blocked: true, error: communityFailure('read-only', '仅能修改自己的资料。') };
  try {
    const next = await withCommunityWriteLock('pixelmc-hub.community.write', () => commitProfilePreview(profile.playerId, patch, profile.revision ?? readCommunityProfile(profile.playerId).revision, 'self'));
    return { profile: { ...next, title: profile.title }, persisted: true };
  } catch (error) {
    if (!['save-failed', 'storage-unavailable', 'locking-unavailable'].includes(error.code)) return { profile, persisted: false, blocked: true, error };
    const prefs = profilePreferences(patch, profile);
    return { profile: { ...profile, ...prefs, featuredTitle: PROFILE_TITLES.find(title => title.id === prefs.featuredTitleId) ?? null }, persisted: false, error };
  }
}
