// SPDX-License-Identifier: Apache-2.0
// Browser-only Web preview. Self and administrator commands stay separate.
import { SELF_PROFILE, communityFailure as failure, communityStorage, readCommunityValue, parseCommunityJSON,
  commitCommunityValues, emitCommunityChange, subscribeCommunity, withCommunityWriteLock, requireArchiveMember } from './demo.js';

export const MEMORY_NOTE_LIMIT = 1200;
export const MEMORY_SUPPLEMENT_LIMIT = 600;
export const MEMORY_SUPPLEMENT_DELETE_WINDOW = 24 * 60 * 60 * 1000;
const storageRoot = 'pixelmc-hub.community.memories.v1';

export function memoryScopeKey(playerId, archiveId) {
  if (typeof playerId !== 'string' || !playerId) throw failure('invalid-player', '缺少玩家标识。');
  return `${storageRoot}:${encodeURIComponent(playerId)}:${encodeURIComponent(archiveId)}`;
}

export function validateMemoryText(text, limit) {
  if (typeof text !== 'string' || !text.trim()) throw failure('empty-text', '请先写下内容。');
  const next = text.trim();
  if (next.length > limit) throw failure('text-too-long', `内容最多 ${limit} 字。`);
  return next;
}

const validTimestamp = value => Number.isSafeInteger(value) && value > 0 && value <= 8_640_000_000_000_000;
const validRevision = value => typeof value === 'string' && value.length > 0 && value.length <= 160;
function parseSaved(raw, playerId, archiveId) {
  const value = parseCommunityJSON(raw, '整合包回忆');
  if (![1, 2].includes(value.schemaVersion)) throw failure('unsupported-version', '回忆记录版本暂不支持，请保留数据并更新预览。');
  if (value.playerId !== playerId || value.archiveId !== archiveId) throw failure('invalid-data', '回忆记录归属异常，请重新读取。');
  return value;
}

function parseNote(raw, playerId, archiveId) {
  const note = parseSaved(raw, playerId, archiveId);
  if (!validRevision(note.revision) || !validTimestamp(note.updatedAt)) throw failure('invalid-data', '回忆修订信息异常，请重新读取。');
  if (note.deleted === true) {
    if (note.schemaVersion !== 2 || note.deletedBy !== 'admin' || !validTimestamp(note.deletedAt) || note.text !== undefined) throw failure('invalid-data', '回忆删除记录异常，请重新读取。');
    return { text: '', revision: note.revision, updatedAt: note.updatedAt, deleted: true };
  }
  if (note.deleted !== undefined || typeof note.text !== 'string' || !note.text.trim() || note.text.length > MEMORY_NOTE_LIMIT) throw failure('invalid-data', '回忆正文读取异常，请重新读取。');
  return { text: note.text, revision: note.revision, updatedAt: note.updatedAt, deleted: false };
}

function parseSupplement(raw, key, playerId, archiveId) {
  const saved = parseSaved(raw, playerId, archiveId);
  if (typeof saved.id !== 'string' || !saved.id || saved.id.length > 80 || key !== `${memoryScopeKey(playerId, archiveId)}:supplement:${saved.id}` || !validTimestamp(saved.createdAt)) throw failure('invalid-data', '补记记录归属或时间异常，请重新读取。');
  if (saved.schemaVersion === 2 && (!validRevision(saved.revision) || !validTimestamp(saved.updatedAt))) throw failure('invalid-data', '补记修订信息异常，请重新读取。');
  const common = { id: saved.id, playerId, createdAt: saved.createdAt, revision: saved.revision ?? null, updatedAt: saved.updatedAt ?? null };
  if (saved.deleted === true) {
    const age = saved.deletedAt - saved.createdAt;
    const isAdmin = saved.schemaVersion === 2 && saved.deletedBy === 'admin';
    const allowed = saved.schemaVersion === 1
      ? ['schemaVersion', 'playerId', 'archiveId', 'id', 'createdAt', 'deleted', 'deletedAt']
      : ['schemaVersion', 'playerId', 'archiveId', 'id', 'createdAt', 'deleted', 'deletedAt', 'deletedBy', 'revision', 'updatedAt'];
    if (!validTimestamp(saved.deletedAt) || age < 0 || (!isAdmin && age >= MEMORY_SUPPLEMENT_DELETE_WINDOW) ||
        (saved.schemaVersion === 2 && !['self', 'admin'].includes(saved.deletedBy)) || Object.keys(saved).some(field => !allowed.includes(field))) {
      throw failure('invalid-data', '补记删除记录读取异常，请重新读取。');
    }
    return { ...common, deleted: true, deletedAt: saved.deletedAt };
  }
  if (saved.deleted !== undefined || typeof saved.text !== 'string' || !saved.text.trim() || saved.text.length > MEMORY_SUPPLEMENT_LIMIT) throw failure('invalid-data', '补记正文读取异常，请重新读取。');
  return { ...common, text: saved.text };
}

// Fixed preview supplements remain addressable by their normal UUID/archive storage key.
function fixtureSupplements(playerId, archiveId) {
  if (playerId === SELF_PROFILE.playerId && archiveId === 'maple-16') return [
    { id: 'demo-maple-return-note', playerId, text: '后来再看小镇的截图，还是最喜欢桥边那张长椅。', createdAt: Date.parse('2026-11-01T20:15:00+08:00'), revision: null, updatedAt: null },
  ];
  if (playerId === '7f1b0000-0000-4000-8000-000000000001' && archiveId === 'maple-16') return [
    { id: 'demo-bamboo-maple-note', playerId, text: '整理旧地图时发现，大家修的小路最后连成了一个圆。', createdAt: Date.parse('2026-10-20T18:40:00+08:00'), revision: null, updatedAt: null },
  ];
  return [];
}

export function initialMemoryNotes(playerId, archiveId) {
  const { player, archive } = requireArchiveMember(playerId, archiveId);
  const text = player.isSelf ? archive.note : `在${archive.name}的这段旅程里，${player.name}留下了自己的脚印。\n\n最想记住的是和朋友一起完成小小计划的那一天。`;
  return { playerId, archiveId, note: { text, revision: null, updatedAt: null, deleted: false }, supplements: fixtureSupplements(playerId, archiveId) };
}

export function readMemorySupplement(playerId, archiveId, supplementId) {
  requireArchiveMember(playerId, archiveId);
  if (typeof supplementId !== 'string' || !supplementId || supplementId.length > 80) throw failure('invalid-submission', '补记标识无效。');
  const key = `${memoryScopeKey(playerId, archiveId)}:supplement:${supplementId}`;
  const raw = readCommunityValue(key);
  return raw === null ? fixtureSupplements(playerId, archiveId).find(item => item.id === supplementId) ?? null : parseSupplement(raw, key, playerId, archiveId);
}

export function readMemoryNotes(playerId, archiveId) {
  try {
    const record = initialMemoryNotes(playerId, archiveId);
    const prefix = memoryScopeKey(playerId, archiveId);
    const rawNote = readCommunityValue(`${prefix}:note`);
    if (rawNote !== null) record.note = parseNote(rawNote, playerId, archiveId);
    const storage = communityStorage();
    const supplementPrefix = `${prefix}:supplement:`;
    const keys = Array.from({ length: storage.length }, (_, index) => storage.key(index)).filter(key => key?.startsWith(supplementPrefix));
    const records = new Map(record.supplements.map(item => [item.id, item]));
    for (const key of keys) {
      const supplement = parseSupplement(readCommunityValue(key), key, playerId, archiveId);
      records.set(supplement.id, supplement);
    }
    record.supplements = [...records.values()].filter(item => !item.deleted).sort((left, right) => left.createdAt - right.createdAt || left.id.localeCompare(right.id));
    return record;
  } catch (error) {
    if (typeof error.code === 'string') throw error;
    throw failure('storage-unavailable', '回忆存储读取失败，请恢复浏览器存储权限后重试。');
  }
}

export function createMemorySubmissionId() { return globalThis.crypto.randomUUID(); }
export async function withMemoryWriteLock(playerId, archiveId, write) {
  requireArchiveMember(playerId, archiveId);
  return withCommunityWriteLock('pixelmc-hub.community.write', write);
}
export function writeMemoryPreview(key, value, source = 'self') {
  commitCommunityValues([{ key, value }]);
  emitCommunityChange({ kind: 'memory', playerId: value.playerId, archiveId: value.archiveId, source });
}
function checkOwner(playerId) {
  if (playerId !== SELF_PROFILE.playerId) throw failure('read-only', '仅能书写自己的整合包纪念。');
}
export function canDeleteMemorySupplement(playerId, supplement, now = Date.now()) {
  return playerId === SELF_PROFILE.playerId && supplement?.playerId === playerId && !supplement.deleted && validTimestamp(supplement.createdAt) && validTimestamp(now) && now >= supplement.createdAt && now - supplement.createdAt < MEMORY_SUPPLEMENT_DELETE_WINDOW;
}

export async function saveMemoryNote({ playerId, archiveId, text, expectedRevision }) {
  checkOwner(playerId);
  const { archive } = requireArchiveMember(playerId, archiveId);
  if (archive.end) throw failure('archived', '这段旅途已归档，请在原文下追加补记。');
  const nextText = validateMemoryText(text, MEMORY_NOTE_LIMIT);
  return withMemoryWriteLock(playerId, archiveId, () => {
    const current = readMemoryNotes(playerId, archiveId);
    if (current.note.revision !== expectedRevision) throw failure('revision-conflict', '这段回忆已被更新，请保留草稿并重新读取。');
    writeMemoryPreview(`${memoryScopeKey(playerId, archiveId)}:note`, { schemaVersion: 2, playerId, archiveId, text: nextText, revision: createMemorySubmissionId(), updatedAt: Date.now() });
    return readMemoryNotes(playerId, archiveId);
  });
}

export async function appendMemorySupplement({ playerId, archiveId, text, submissionId }) {
  checkOwner(playerId);
  const { archive } = requireArchiveMember(playerId, archiveId);
  if (!archive.end) throw failure('in-progress', '这段旅途仍在进行中，可以直接编辑回忆。');
  const nextText = validateMemoryText(text, MEMORY_SUPPLEMENT_LIMIT);
  if (typeof submissionId !== 'string' || !/^[a-zA-Z0-9-]{12,80}$/.test(submissionId)) throw failure('invalid-submission', '补记提交标识无效，请重新打开补记编辑。');
  return withMemoryWriteLock(playerId, archiveId, () => {
    const current = readMemoryNotes(playerId, archiveId);
    const existing = readMemorySupplement(playerId, archiveId, submissionId);
    if (existing) {
      if (existing.deleted) throw failure('deleted-supplement', '这条补记已删除，请另写一条新补记。');
      if (existing.text !== nextText) throw failure('immutable-supplement', '补记正文已定稿，请另写一条补记。');
      return current;
    }
    const now = Date.now();
    writeMemoryPreview(`${memoryScopeKey(playerId, archiveId)}:supplement:${submissionId}`, { schemaVersion: 2, playerId, archiveId, id: submissionId, text: nextText, createdAt: now, updatedAt: now, revision: createMemorySubmissionId() });
    return readMemoryNotes(playerId, archiveId);
  });
}

export async function deleteMemorySupplement({ playerId, archiveId, supplementId }) {
  checkOwner(playerId);
  return withMemoryWriteLock(playerId, archiveId, () => {
    const current = readMemoryNotes(playerId, archiveId);
    const supplement = readMemorySupplement(playerId, archiveId, supplementId);
    if (!supplement) throw failure('missing-supplement', '这条补记已不可用，请重新读取。');
    if (supplement.deleted) return current;
    const now = Date.now();
    if (!canDeleteMemorySupplement(playerId, supplement, now)) {
      if (supplement.createdAt > now) throw failure('future-created-at', '补记创建时间晚于当前时间，请检查系统时间。');
      throw failure('delete-window-expired', '这条补记已到 24 小时删除期限。');
    }
    writeMemoryPreview(`${memoryScopeKey(playerId, archiveId)}:supplement:${supplementId}`, { schemaVersion: 2, playerId, archiveId, id: supplementId, createdAt: supplement.createdAt,
      deleted: true, deletedBy: 'self', deletedAt: now, updatedAt: now, revision: createMemorySubmissionId() });
    return readMemoryNotes(playerId, archiveId);
  });
}

export function subscribeMemoryNotes(playerId, archiveId, listener) {
  const prefix = `${memoryScopeKey(playerId, archiveId)}:`;
  return subscribeCommunity(event => {
    if (event.kind === 'storage' ? event.key === null || event.key?.startsWith(prefix) : event.playerId === playerId && event.archiveId === archiveId) listener();
  });
}

const noteTimeFormat = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
export function formatMemoryTime(timestamp) {
  const parts = Object.fromEntries(noteTimeFormat.formatToParts(new Date(timestamp)).map(part => [part.type, part.value]));
  return `${parts.year}年${parts.month}月${parts.day}日 ${parts.hour}:${parts.minute}`;
}
