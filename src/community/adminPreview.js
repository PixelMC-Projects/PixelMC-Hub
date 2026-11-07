// SPDX-License-Identifier: Apache-2.0
// Explicit Web-preview administrator commands. Never expose these through a player provider.
import { COMMUNITY_PLAYERS, MEMORY_ARCHIVES, PROFILE_BACKGROUNDS, getCommunityPlayer, getProfileTitleChoices,
  getProfileFortuneChoices, getProfileMemories, readCommunityProfile, commitProfilePreview, withCommunityWriteLock,
  requireArchiveMember, communityFailure as failure } from './demo.js';
import { PROFILE_PATTERNS } from './profile-patterns.js';
import { MEMORY_NOTE_LIMIT, MEMORY_SUPPLEMENT_LIMIT, readMemoryNotes, readMemorySupplement, memoryScopeKey,
  validateMemoryText, createMemorySubmissionId, withMemoryWriteLock, writeMemoryPreview } from './memoryStore.js';
import { getMemoryCollectionCandidates, filterMemoryCollectionSelection, readMemoryCollections } from './memoryCollections.js';

export { subscribeCommunity, readCommunityProfile, readSelfFortuneState, saveSelfFortuneSelection, persistHubPreviewState } from './demo.js';
export { getMemoryCollectionCandidates } from './memoryCollections.js';

export function getCommunityPlayers() { return COMMUNITY_PLAYERS.map(player => getCommunityPlayer(player.playerId)); }
export function readAdminProfile(playerId) { return readCommunityProfile(playerId); }
export function getProfileOptions(playerId) {
  const player = getCommunityPlayer(playerId);
  return { backgrounds: PROFILE_BACKGROUNDS, patterns: PROFILE_PATTERNS, titles: getProfileTitleChoices(playerId),
    fortunes: getProfileFortuneChoices(playerId), memories: getProfileMemories(player) };
}
export async function saveAdminProfile({ playerId, patch, expectedRevision }) {
  getCommunityPlayer(playerId);
  return withCommunityWriteLock('pixelmc-hub.community.write', () => commitProfilePreview(playerId, patch, expectedRevision, 'admin'));
}
export function getCommunityArchives() {
  return [...MEMORY_ARCHIVES].sort((left, right) => (right.end ?? '9999.12.31').localeCompare(left.end ?? '9999.12.31') || right.start.localeCompare(left.start))
    .map(archive => ({ ...archive, isCurrent: archive.end === null }));
}
export function getArchivePlayers(archiveId) {
  const archive = MEMORY_ARCHIVES.find(item => item.id === archiveId);
  if (!archive) throw failure('unknown-archive', '找不到这份整合包纪念。');
  return getCommunityPlayers().filter(player => player.memoryIds.includes(archiveId)).map(player => ({ ...player,
    lastLoginAt: archive.end ? `${archive.end.replaceAll('.', '-')}T20:15:00+08:00` : player.lastLoginAt }));
}
export function readAdminMemory(playerId, archiveId) {
  requireArchiveMember(playerId, archiveId);
  return { ...readMemoryNotes(playerId, archiveId), collections: readMemoryCollections(playerId, archiveId) };
}
function checkRevision(actual, expected) {
  if (actual !== expected) throw failure('revision-conflict', '这项内容已被更新，请保留草稿并重新读取。');
}
export async function adminSaveMemoryNote({ playerId, archiveId, text, expectedRevision }) {
  const nextText = validateMemoryText(text, MEMORY_NOTE_LIMIT);
  return withMemoryWriteLock(playerId, archiveId, () => {
    const current = readAdminMemory(playerId, archiveId);
    checkRevision(current.note.revision, expectedRevision);
    writeMemoryPreview(`${memoryScopeKey(playerId, archiveId)}:note`, { schemaVersion: 2, playerId, archiveId,
      text: nextText, revision: createMemorySubmissionId(), updatedAt: Date.now() }, 'admin');
    return readAdminMemory(playerId, archiveId);
  });
}
export async function adminDeleteMemoryNote({ playerId, archiveId, expectedRevision }) {
  return withMemoryWriteLock(playerId, archiveId, () => {
    const current = readAdminMemory(playerId, archiveId);
    checkRevision(current.note.revision, expectedRevision);
    const now = Date.now();
    writeMemoryPreview(`${memoryScopeKey(playerId, archiveId)}:note`, { schemaVersion: 2, playerId, archiveId,
      deleted: true, deletedBy: 'admin', deletedAt: now, revision: createMemorySubmissionId(), updatedAt: now }, 'admin');
    return readAdminMemory(playerId, archiveId);
  });
}
export async function adminSaveMemorySupplement({ playerId, archiveId, supplementId, text, expectedRevision }) {
  const nextText = validateMemoryText(text, MEMORY_SUPPLEMENT_LIMIT);
  return withMemoryWriteLock(playerId, archiveId, () => {
    readAdminMemory(playerId, archiveId);
    const current = readMemorySupplement(playerId, archiveId, supplementId);
    if (!current || current.deleted) throw failure('missing-supplement', '这条补记已不可用，请重新读取。');
    checkRevision(current.revision, expectedRevision);
    writeMemoryPreview(`${memoryScopeKey(playerId, archiveId)}:supplement:${supplementId}`, { schemaVersion: 2, playerId, archiveId,
      id: supplementId, text: nextText, createdAt: current.createdAt, updatedAt: Date.now(), revision: createMemorySubmissionId() }, 'admin');
    return readAdminMemory(playerId, archiveId);
  });
}
export async function adminDeleteMemorySupplement({ playerId, archiveId, supplementId, expectedRevision }) {
  return withMemoryWriteLock(playerId, archiveId, () => {
    const record = readAdminMemory(playerId, archiveId);
    const current = readMemorySupplement(playerId, archiveId, supplementId);
    if (!current) throw failure('missing-supplement', '这条补记已不可用，请重新读取。');
    if (current.deleted) return record;
    checkRevision(current.revision, expectedRevision);
    const now = Date.now();
    if (current.createdAt > now) throw failure('future-created-at', '补记创建时间晚于当前时间，请检查系统时间。');
    writeMemoryPreview(`${memoryScopeKey(playerId, archiveId)}:supplement:${supplementId}`, { schemaVersion: 2, playerId, archiveId,
      id: supplementId, createdAt: current.createdAt, deleted: true, deletedBy: 'admin', deletedAt: now, updatedAt: now, revision: createMemorySubmissionId() }, 'admin');
    return readAdminMemory(playerId, archiveId);
  });
}
export async function adminSaveMemoryCollections({ playerId, archiveId, selection, expectedRevision }) {
  return withMemoryWriteLock(playerId, archiveId, () => {
    const current = readAdminMemory(playerId, archiveId);
    checkRevision(current.collections.revision, expectedRevision);
    if (!selection || typeof selection !== 'object' || Array.isArray(selection) || Object.keys(selection).some(key => !['titleEventId', 'fortuneEventId'].includes(key))) throw failure('invalid-selection', '纪念选择格式无效。');
    const filtered = filterMemoryCollectionSelection(playerId, archiveId, selection);
    if (['titleEventId', 'fortuneEventId'].some(key => selection[key] !== undefined && selection[key] !== null && filtered[key] !== selection[key])) throw failure('invalid-selection', '所选纪念不属于这位玩家在该整合包期间的获得记录。');
    writeMemoryPreview(`${memoryScopeKey(playerId, archiveId)}:collections`, { schemaVersion: 1, playerId, archiveId,
      selection: filtered, revision: createMemorySubmissionId(), updatedAt: Date.now() }, 'admin');
    return readAdminMemory(playerId, archiveId);
  });
}
