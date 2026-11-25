import {prefix, readValue, writeValue} from './managerPreview.js';
import {terraAllowed} from './modelPreview.js';

const week = 7 * 86400000;
const key = prefix+'quota';
const playerKey = id => key+':'+id;
const initial = {mode:'inherit', limitNanoYuan:0, periodStart:0, revision:0};
export const previewQuotaSettings = () => readValue(key, {quotaEnabled:false, weeklyQuotaNanoYuan:2000000000});
export const savePreviewQuotaSettings = settings => writeValue(key, {...previewQuotaSettings(), ...settings});
const minute = now => Math.floor(now / 60000) * 60000;
function cycleStart(anchor, now) {
  return anchor && anchor <= now && now < anchor+week ? anchor : 0;
}

export function getPreviewQuota(playerId, receipts = [], now = Date.now()) {
  const {quotaEnabled:enabled, weeklyQuotaNanoYuan} = previewQuotaSettings();
  const saved = readValue(playerKey(playerId), initial);
  const periodStart = cycleStart(saved.periodStart, now);
  const unlimited = saved.mode === 'unlimited';
  const limitedMode = saved.limitedMode ?? (saved.mode === 'custom' ? 'custom' : 'inherit');
  const customLimitNanoYuan = saved.customLimitNanoYuan ?? (limitedMode === 'custom' ? saved.limitNanoYuan : null);
  const limitNanoYuan = unlimited ? 0 : saved.mode === 'custom' ? saved.limitNanoYuan : weeklyQuotaNanoYuan;
  let spentNanoYuan = 0;
  let lifetimeNanoYuan = 0;
  const seen = new Set();
  for (const receipt of receipts) {
    if (!receipt || !receipt.id || seen.has(receipt.id) || !Number.isSafeInteger(receipt.nanoYuan) || receipt.nanoYuan < 0) continue;
    seen.add(receipt.id);
    lifetimeNanoYuan += receipt.nanoYuan;
    if (periodStart && receipt.at >= Math.max(periodStart,saved.clearedAt ?? 0) && receipt.at < periodStart+week) spentNanoYuan += receipt.nanoYuan;
  }
  const remainingNanoYuan = unlimited ? 0 : Math.max(0,limitNanoYuan-spentNanoYuan);
  return {enabled, mode:saved.mode, unlimited, limitedMode, customLimitNanoYuan, defaultLimitNanoYuan:weeklyQuotaNanoYuan,
    limitNanoYuan, remainingNanoYuan, spentNanoYuan, periodStart,
    resetAt:periodStart ? periodStart+week : 0, lifetimeNanoYuan, revision:saved.revision,
    exhausted:enabled && !unlimited && remainingNanoYuan === 0,
    remainingPercent:limitNanoYuan ? remainingNanoYuan*100/limitNanoYuan : 0};
}

const terraSettingsKey = prefix + 'terra-settings';
const terraAccountKey = playerId => prefix + 'terra-account:' + playerId;
const day = 86400000, chinaOffset = 8 * 3600000;
export const terraQuotaChanged = (key, playerId) => key === terraSettingsKey || key === terraAccountKey(playerId);

export function getPreviewTerraQuota(playerId, mode, receipts, now = Date.now()) {
  if (!terraAllowed(mode)) return {enabled:false, unlimited:false, exhausted:false};
  const settings = readValue(terraSettingsKey, {});
  const account = readValue(terraAccountKey(playerId), {});
  const windows = [];
  const shanghai = new Date(now + chinaOffset);
  const year = shanghai.getUTCFullYear(), month = shanghai.getUTCMonth();
  const today = Math.floor((now + chinaOffset) / day) * day - chinaOffset;
  const weekStart = today - (shanghai.getUTCDay() + 6) % 7 * day;
  const monthStart = Date.UTC(year, month, 1) - chinaOffset;
  const monthEnd = Date.UTC(year, month + 1, 1) - chinaOffset;
  if (settings.quotaEnabled !== false && account.mode !== 'unlimited') {
    const weekly = settings.weeklyLimitNanoUsd ?? 2e9, monthly = settings.monthlyLimitNanoUsd ?? 8e9;
    windows.push(
      {limit:account.mode === 'custom' ? account.customWeeklyLimitNanoUsd ?? weekly : weekly,
        start:weekStart, end:weekStart + week, seed:700_000_000},
      {limit:account.mode === 'custom' ? account.customMonthlyLimitNanoUsd ?? monthly : monthly,
        start:monthStart, end:monthEnd, seed:1_150_000_000},
    );
  }
  if (settings.serverQuotaEnabled !== false) windows.push({
    limit:settings.serverMonthlyLimitNanoUsd ?? 160e9, start:monthStart, end:monthEnd, seed:20e9,
  });
  if (!windows.length) return {terra:true, enabled:false, unlimited:true, exhausted:false, resetAt:0};
  // The preview uses fixed sample spending before adding completed browser-only receipts.
  if (mode === 'player-terra-exhausted') windows.at(-1).seed = windows.at(-1).limit;
  const seen = new Set();
  for (const row of receipts) {
    if (row.billing !== 'openai_api' || !row.id || seen.has(row.id)
      || !Number.isSafeInteger(row.nanoUsd) || row.nanoUsd < 0) continue;
    seen.add(row.id);
    for (const window of windows) if (row.at >= window.start && row.at < window.end) window.seed += row.nanoUsd;
  }
  let remainingPercent = 100, resetAt = windows[0].end;
  for (const {limit, seed, end} of windows) {
    const percent = limit > 0 ? Math.max(0, Math.ceil(100 * (limit - seed) / limit)) : 0;
    if (percent < remainingPercent) {
      remainingPercent = percent;
      resetAt = end;
    } else if (percent === 0) resetAt = Math.max(resetAt, end);
  }
  return {terra:true, enabled:true, unlimited:false, exhausted:remainingPercent === 0, resetAt, remainingPercent};
}

export function startPreviewQuota(playerId, now = Date.now()) {
  const saved = readValue(playerKey(playerId), initial);
  if (!cycleStart(saved.periodStart, now) && !getPreviewQuota(playerId, [], now).exhausted) {
    writeValue(playerKey(playerId), {...saved, periodStart:minute(now), clearedAt:now});
  }
}

export function resetAllPreviewQuotas({resetRevision}) {
  return navigator.locks.request(key, () => {
    const config = previewQuotaSettings();
    if ((config.resetRevision ?? 0) !== resetRevision) throw new Error('已有新的重置操作，请刷新后重试。');
    const now = Date.now();
    let affected = 0;
    for (const accountKey of Object.keys(localStorage).filter(id=>id.startsWith(key+':'))) {
      const saved = readValue(accountKey, initial);
      if (!cycleStart(saved.periodStart, now)) continue;
      writeValue(accountKey, {...saved, periodStart:minute(now), clearedAt:now, revision:saved.revision+1});
      affected++;
    }
    savePreviewQuotaSettings({...config,resetRevision:resetRevision+1});
    return {affected,resetRevision:resetRevision+1};
  });
}

export function updatePreviewQuota(playerId, operation, body) {
  const saved = readValue(playerKey(playerId), initial);
  if (body.revision !== saved.revision) throw new Error('额度已修改，请重新打开玩家详情。');
  const now = Date.now();
  let changes;
  if (operation === 'quota.reset') {
    changes = {periodStart:minute(now), clearedAt:now};
  } else {
    const previous = getPreviewQuota(playerId);
    const limitedMode = body.mode === 'unlimited' ? body.limitedMode ?? previous.limitedMode : body.mode;
    const customLimitNanoYuan = limitedMode === 'custom' ? body.limitNanoYuan ?? previous.customLimitNanoYuan : previous.customLimitNanoYuan;
    changes = {mode:body.mode, limitedMode, customLimitNanoYuan, limitNanoYuan:customLimitNanoYuan ?? 0};
  }
  writeValue(playerKey(playerId), {...saved, ...changes, revision:saved.revision+1});
}
