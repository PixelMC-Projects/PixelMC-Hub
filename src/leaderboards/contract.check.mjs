// SPDX-License-Identifier: Apache-2.0
// node src/leaderboards/contract.check.mjs; synthetic records only, no network or backend.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { BOARD_IDS, BOARD_DEFINITIONS, STATUSES, isSameOriginAvatarPath, validateProjection } from './contract.js';
import { getDemoProjection, getDemoState, setDemoState, subscribeDemo } from './demo.js';

const clone = value => JSON.parse(JSON.stringify(value));
const reject = (dto, mutate) => { const bad = clone(dto); mutate(bad); assert.throws(() => validateProjection(bad), TypeError); };
for (const board of BOARD_IDS) {
  const all = getDemoProjection(board, { size: 100, status: 'ready', selfAbsent: false });
  assert.equal(all.rows.length, 24);
  assert.deepEqual(all.rows.slice(0, 5).map(row => row.rank), [1, 2, 3, 3, 5]);
  assert.equal(all.self.rank, 1);
  assert.equal(all.rows[0].playerId, all.viewer.playerId);
  assert.equal(all.self.rankDelta, 3);
  assert.deepEqual(all.rows.slice(0, 5).map(row => row.rankDelta), [3, -1, -1, -1, 0]);
  assert.equal(all.rows.at(-1).rankDelta, null);
  assert.notEqual(all.comparisonRevision, all.revision);
  const top = getDemoProjection(board, { status: 'ready', page: 0, size: 20, selfOutside: true });
  assert.equal(top.self.rank, 24);
  assert(top.self.rankDelta < 0);
  assert(!top.rows.some(row => row.playerId === top.viewer.playerId));
  assert.equal(top.total, 24);
  const tieAcrossPage = getDemoProjection(board, { status: 'ready', page: 1, size: 3 });
  assert.deepEqual(tieAcrossPage.rows.map(row => row.rank), [3, 5, 6]);
  assert.deepEqual(tieAcrossPage.rows, all.rows.slice(3, 6));
  assert.equal(tieAcrossPage.revision, all.revision);
  const absent = getDemoProjection(board, { status: 'ready', selfAbsent: true });
  assert.equal(absent.self.value, null); assert.equal(absent.self.rank, null); assert.equal(absent.total, 23);
  for (const status of STATUSES) {
    const dto = getDemoProjection(board, { status }); validateProjection(dto);
    if (status !== 'ready') assert.equal(dto.rows.length, 0);
    if (!['ready', 'empty'].includes(status)) { assert.equal(dto.total, null); assert.equal(dto.updatedAt, null); }
  }
  reject(all, dto => { dto.rows[0].privatePayload = { foods: ['hidden'] }; });
  reject(all, dto => { dto.rows[0].value = null; });
  reject(all, dto => { dto.rows[0].value = NaN; });
  reject(all, dto => { dto.rows[0].rank = 2; });
  reject(all, dto => { dto.rows[3].rank = 4; });
  reject(all, dto => { [dto.rows[2], dto.rows[3]] = [dto.rows[3], dto.rows[2]]; });
  reject(all, dto => { dto.self.value += 1; });
  reject(all, dto => { dto.self.rankDelta += 1; });
  reject(all, dto => { dto.rows[0].rankDelta = 1.5; });
  reject(all, dto => { dto.rows[0].rankDelta = Number.MAX_SAFE_INTEGER; });
  reject(all, dto => { dto.rows[0].rankDelta = -3; });
  reject(all, dto => { dto.comparisonRevision = null; });
  reject(all, dto => { delete dto.comparisonRevision; });
  reject(all, dto => { delete dto.revision; });
  reject(all, dto => { dto.comparisonRevision = dto.revision; });
  reject(absent, dto => { dto.self.rankDelta = 0; });
  const initialSnapshot = getDemoProjection(board, { status: 'ready', withBaseline: false });
  assert.equal(initialSnapshot.comparisonRevision, null);
  assert(initialSnapshot.rows.every(row => row.rankDelta === null));
  assert.equal(initialSnapshot.self.rankDelta, null);
  const legacy = clone(all); delete legacy.comparisonRevision; delete legacy.revision;
  for (const row of legacy.rows) delete row.rankDelta; delete legacy.self.rankDelta;
  validateProjection(legacy);
  reject(all, dto => { dto.scope.kind = dto.scope.kind === 'archive' ? 'global' : 'archive'; });
  reject(all, dto => { dto.size = 101; });
  reject(all, dto => { dto.rows[1].playerId = dto.rows[0].playerId; });
  reject(all, dto => { dto.viewer.playerId = 'client-picked-name'; });
  const zero = clone(absent); zero.self.value = 0; validateProjection(zero); assert.equal(zero.self.value, 0);
  const mutable = getDemoProjection(board, { status: 'ready' }); mutable.rows[0].name = 'changed'; mutable.rows[0].avatar.url = '/changed';
  assert.notEqual(getDemoProjection(board, { status: 'ready' }).rows[0].name, 'changed');
  assert.equal(getDemoProjection(board, { status: 'ready' }).rows[0].avatar.url, '/assets/avatar.png');
}
for (const value of ['/assets/avatar.png', '/api/avatars/demo?v=2']) assert(isSameOriginAvatarPath(value));
for (const value of ['https://external.invalid/a', '//external.invalid/a', '/\\external.invalid/a', '/%5cexternal.invalid/a', '/\n/external.invalid', '/%0a/a', 'data:image/png;base64,aaa']) {
  assert.equal(isSameOriginAvatarPath(value), false);
  reject(getDemoProjection(), dto => { dto.rows[0].avatar.url = value; });
}
const schema = JSON.parse(fs.readFileSync(new URL('./schemas/projection.schema.json', import.meta.url), 'utf8'));
assert.deepEqual(schema.properties.board.enum, BOARD_IDS);
assert.deepEqual(schema.properties.status.enum, STATUSES);
assert.equal(schema.properties.size.maximum, 100);
const avatarPattern = new RegExp(schema.$defs.avatar.properties.url.pattern);
assert(avatarPattern.test('/assets/avatar.png')); assert(!avatarPattern.test('/\\external.invalid/a'));
let notifications = 0;
const initial = getDemoState(); assert.equal(getDemoState(), initial);
const unsubscribe = subscribeDemo(() => { notifications++; });
setDemoState({ status: 'loading' }); const changed = getDemoState(); assert.notEqual(changed, initial);
setDemoState({ status: 'loading' }); assert.equal(getDemoState(), changed); assert.equal(notifications, 1);
assert.equal(getDemoProjection().status, 'loading');
setDemoState({ status: 'ready', selfAbsent: false }); unsubscribe();
assert.equal(BOARD_DEFINITIONS.find(board => board.id === 'online').unit, 'ms');
assert.equal(BOARD_DEFINITIONS.find(board => board.id === 'fortunes').scope, 'global');
console.log('PASS shared leaderboard contract: fixed three-board fixtures, cross-page competition ties, default self first and delta +3, explicit self outside Top-N/unknown-vs-zero, full-snapshot movement/first snapshot/legacy compatibility, all statuses, scope/unit/privacy/paging rejection, local avatar boundary, immutable fixture and event-driven stable preview state.');
console.log('JSON Schema parsed and key boundaries checked; semantic validation uses contract.js. No real provider, HTTP, SQL, loader or Minecraft acceptance performed.');
