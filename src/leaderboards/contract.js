// SPDX-License-Identifier: Apache-2.0
// Public read projection only. This Web contract does not enable a production provider.
export const BOARD_DEFINITIONS = Object.freeze([
  Object.freeze({ id: 'food', label: '美食品鉴', scope: 'archive', unit: 'foods', metricLabel: '已品尝食物', description: '本存档内，记录你尝过的每一种风味' }),
  Object.freeze({ id: 'fortunes', label: '签笺收藏', scope: 'global', unit: 'fortunes', metricLabel: '已收藏签种', description: '在旅途中收进藏签匣的不同签种' }),
  Object.freeze({ id: 'online', label: '在线时长', scope: 'archive', unit: 'ms', metricLabel: '累计在线', description: '本存档内，记录相伴的时光' }),
]);
export const boardDefinitions = BOARD_DEFINITIONS;
export const BOARD_IDS = Object.freeze(BOARD_DEFINITIONS.map(board => board.id));
export const STATUSES = Object.freeze(['ready', 'loading', 'unavailable', 'unsupported', 'empty', 'error']);
export const SCHEMA_VERSION = 1;
export const MAX_PAGE_SIZE = 100;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const isCount = value => Number.isSafeInteger(value) && value >= 0;
const known = (value, allowed, at) => { for (const key of Object.keys(value)) if (!allowed.includes(key)) throw new TypeError(`${at}.${key} is not a public field`); };
const require = (condition, message) => { if (!condition) throw new TypeError(message); };
export function isSameOriginAvatarPath(value) {
  if (typeof value !== 'string' || value.length > 2048 || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u0020\u007f]|%5c|%(?:0[0-9a-f]|1[0-9a-f]|7f)/i.test(value)) return false;
  try { return new URL(value, 'https://pixelmc.invalid').origin === 'https://pixelmc.invalid'; } catch { return false; }
}
function row(value, at, nullable = false) {
  require(value && typeof value === 'object' && !Array.isArray(value), `${at} must be an object`);
  known(value, ['playerId', 'name', 'rank', 'value', 'rankDelta', 'avatar'], at);
  require(UUID.test(value.playerId), `${at}.playerId must be a UUID`);
  require(typeof value.name === 'string' && value.name.length > 0 && value.name.length <= 128, `${at}.name invalid`);
  require((nullable && value.rank === null) || (isCount(value.rank) && value.rank >= 1), `${at}.rank invalid`);
  require((nullable && value.value === null) || isCount(value.value), `${at}.value invalid`);
  if (value.rankDelta !== undefined && value.rankDelta !== null) {
    require(Number.isSafeInteger(value.rankDelta), `${at}.rankDelta must be a signed safe integer or null`);
    require(isCount(value.rank) && value.rank >= 1 && Number.isSafeInteger(value.rank + value.rankDelta) && value.rank + value.rankDelta >= 1, `${at}.previousRank is invalid`);
  }
  if (value.avatar !== undefined) {
    require(value.avatar && typeof value.avatar === 'object', `${at}.avatar invalid`);
    known(value.avatar, ['url', 'version'], `${at}.avatar`);
    require(isSameOriginAvatarPath(value.avatar.url), `${at}.avatar must use an approved same-origin path`);
    require(typeof value.avatar.version === 'string' && value.avatar.version.length <= 128, `${at}.avatar.version invalid`);
  }
}
/** Throws on malformed/private/contradictory DTOs. Does not sort, fill missing scores, or invent ranks. */
export function validateProjection(dto) {
  require(dto && typeof dto === 'object' && !Array.isArray(dto), 'projection must be an object');
  known(dto, ['schemaVersion', 'board', 'status', 'supported', 'period', 'ranking', 'scope', 'unit', 'rows', 'total', 'page', 'size', 'updatedAt', 'revision', 'comparisonRevision', 'viewer', 'self'], 'projection');
  require(dto.schemaVersion === SCHEMA_VERSION, 'unsupported schemaVersion');
  const board = BOARD_DEFINITIONS.find(item => item.id === dto.board);
  require(board && STATUSES.includes(dto.status), 'unknown board/status');
  require(dto.supported === (dto.status !== 'unsupported'), 'supported/status mismatch');
  require(dto.period === 'all' && dto.ranking === 'competition' && dto.unit === board.unit, 'period/ranking/unit mismatch');
  require(dto.scope && dto.scope.kind === board.scope && typeof dto.scope.id === 'string' && dto.scope.id.length > 0 && dto.scope.id.length <= 256 && typeof dto.scope.label === 'string', 'scope mismatch');
  known(dto.scope, ['kind', 'id', 'label'], 'scope');
  require(isCount(dto.page) && Number.isSafeInteger(dto.size) && dto.size >= 1 && dto.size <= MAX_PAGE_SIZE, 'invalid paging');
  require(dto.total === null || isCount(dto.total), 'invalid total');
  require(dto.updatedAt === null || isCount(dto.updatedAt), 'updatedAt must be epoch milliseconds or null');
  require(dto.revision === undefined || dto.revision === null || (typeof dto.revision === 'string' && dto.revision.length > 0 && dto.revision.length <= 128), 'invalid revision');
  require(dto.comparisonRevision === undefined || dto.comparisonRevision === null || (typeof dto.comparisonRevision === 'string' && dto.comparisonRevision.length > 0 && dto.comparisonRevision.length <= 128), 'invalid comparisonRevision');
  if (dto.comparisonRevision != null) require(typeof dto.revision === 'string' && dto.revision.length > 0 && dto.comparisonRevision !== dto.revision, 'comparison requires distinct current and previous revisions');
  const validateDelta = item => { if (item?.rankDelta != null) require(typeof dto.comparisonRevision === 'string' && typeof dto.revision === 'string', 'numeric rankDelta requires comparison and current revisions'); };
  require(dto.viewer && UUID.test(dto.viewer.playerId) && typeof dto.viewer.name === 'string' && dto.viewer.name.length > 0 && dto.viewer.name.length <= 128, 'invalid viewer');
  known(dto.viewer, ['playerId', 'name'], 'viewer');
  require(Array.isArray(dto.rows) && dto.rows.length <= dto.size, 'invalid rows');
  const ids = new Set();
  dto.rows.forEach((item, index) => {
    row(item, `rows[${index}]`); validateDelta(item); require(!ids.has(item.playerId), 'duplicate row playerId'); ids.add(item.playerId);
    const previous = dto.rows[index - 1];
    if (!previous) require(item.rank <= dto.page * dto.size + 1, 'first rank exceeds page offset');
    else if (previous.value === item.value) require(previous.rank === item.rank && previous.playerId < item.playerId, 'tie rank/order mismatch');
    else require(previous.value > item.value && item.rank === dto.page * dto.size + index + 1, 'score/rank order mismatch');
  });
  if (dto.self !== null) { row(dto.self, 'self', true); validateDelta(dto.self); require(dto.self.playerId === dto.viewer.playerId, 'self/viewer mismatch'); }
  const visibleSelf = dto.rows.find(item => item.playerId === dto.viewer.playerId);
  if (visibleSelf) require(dto.self && dto.self.rank === visibleSelf.rank && dto.self.value === visibleSelf.value && (dto.self.rankDelta ?? null) === (visibleSelf.rankDelta ?? null), 'visible row/self mismatch');
  if (dto.status === 'ready') require(dto.total !== null && dto.updatedAt !== null && dto.total > 0 && dto.rows.length <= Math.max(0, dto.total - dto.page * dto.size), 'ready metadata invalid');
  else require(dto.rows.length === 0, 'non-ready projection must not impersonate current rows');
  if (dto.status === 'empty') require(dto.total === 0 && dto.updatedAt !== null, 'empty must be confirmed');
  else if (dto.status !== 'ready') require(dto.total === null && dto.updatedAt === null && dto.self === null, 'unconfirmed state cannot invent totals/timestamps/self');
  if (!['ready', 'empty'].includes(dto.status)) require(dto.comparisonRevision == null, 'unconfirmed state cannot claim a comparison baseline');
  return dto;
}
