// Browser-only data shared by the Hub and Manager previews on the same origin.
export const prefix = 'pixelmc-wenzhou-manager-preview:';
export const historyKey = 'pixelmc-wenzhou-preview-v1';
export const effortKey = prefix + 'reasoning-effort';
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
    if (event.storageArea === localStorage && (!event.key || event.key.startsWith(prefix) || event.key === historyKey)) listener(event.key);
  }
  const local = event => listener(event.detail);
  window.addEventListener('storage', storage);
  window.addEventListener(eventName, local);
  return () => {
    window.removeEventListener('storage', storage);
    window.removeEventListener(eventName, local);
  };
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
