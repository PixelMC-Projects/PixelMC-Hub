// Browser-only data shared by the Hub and Manager previews on the same origin.
import { defaultText, defaultVersion } from './agreementText.js';
import { validAgreementField } from './agreementDocument.js';

export const prefix = 'pixelmc-wenzhou-manager-preview:';
export const receiptKey = 'pixelmc-wenzhou-preview-agreement';
export const historyKey = 'pixelmc-wenzhou-preview-v1';
export const policyKey = prefix + 'policy';
export const effortKey = prefix + 'reasoning-effort';
export const visibilityKey = prefix + 'show-agreement';
export function showsPlayerAgreement() {
  return readValue(visibilityKey, true) !== false;
}
export const grantKey = id => prefix + 'grant:' + id;
export const archivePrefix = prefix + 'session:';
const eventName = 'pixelmc-wenzhou-preview-change';
export const terraPreviewCredentialKey = prefix + 'terra-credential';
export const terraPreviewBillingHoldKey = prefix + 'terra-billing-hold';

export function terraPreviewBillingHold() {
  const credential = readValue(terraPreviewCredentialKey);
  const hold = readValue(terraPreviewBillingHoldKey);
  return credential?.configured === true && typeof hold?.id === 'string' && hold.id
    && hold.revision === credential.revision ? hold : null;
}

export function terraPreviewProjectAvailable() {
  return readValue(terraPreviewCredentialKey)?.configured === true && !terraPreviewBillingHold();
}

export function readValue(key, fallback = null) {
  try { return JSON.parse(localStorage.getItem(key)) ?? fallback; }
  catch { return fallback; }
}

export function writeValue(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
  window.dispatchEvent(new CustomEvent(eventName, {detail:key}));
}

export function subscribePreview(listener) {
  function storage(event) {
    if (event.storageArea === localStorage && (!event.key || event.key.startsWith(prefix) || [receiptKey, historyKey].includes(event.key))) listener(event.key);
  }
  const local = event => listener(event.detail);
  window.addEventListener('storage', storage);
  window.addEventListener(eventName, local);
  return () => {
    window.removeEventListener('storage', storage);
    window.removeEventListener(eventName, local);
  };
}

const initialPolicy = {version:defaultVersion, text:defaultText, draft:null, revision:0};
export function currentPolicy() {
  const saved = readValue(policyKey, initialPolicy);
  if (!['2026-11-18.3', '2026-11-18.4'].includes(saved.version)) return saved;
  // This version identifies the previous bundled text. Published custom versions use a revision suffix.
  return { ...saved, version:defaultVersion, text:defaultText };
}

export async function savePolicy(text, revision, publish) {
  return navigator.locks.request(policyKey, () => {
    const previous = currentPolicy();
    if (revision !== previous.revision) throw new Error('协议已在另一窗口更新，请重新载入后再编辑。');
    if (!Object.keys(defaultText).every(key => validAgreementField(text[key]))) throw new Error('请填写三个正文区域，每个区域最多 20000 字。');
    if (publish && (!previous.draft || JSON.stringify(text) !== JSON.stringify(previous.draft))) throw new Error('请先保存当前草稿。');
    const nextRevision = previous.revision + 1;
    const date = new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date());
    const next = publish
      ? {version:date + '-r' + nextRevision, text, draft:null, revision:nextRevision}
      : {...previous, draft:text, revision:nextRevision};
    writeValue(policyKey,next);
    return next;
  });
}

export function rememberedGrant(playerId) {
  const value = readValue(grantKey(playerId));
  if (typeof value === 'boolean') return value;
  try { return sessionStorage.getItem('pixelmc-wenzhou-demo-grant') === 'granted'; }
  catch { return false; }
}

export function archiveSessions(sessions, previous) {
  const seen = new Map(previous.map(row => [row.id,row]));
  for (const row of sessions) {
    if (seen.get(row.id) !== row) writeValue(archivePrefix + row.id, row);
  }
}
