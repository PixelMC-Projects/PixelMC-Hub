import {player as previewOwner} from '../data.js';
import {effortKey, prefix, readValue, terraPreviewProjectAvailable, writeValue} from './managerPreview.js';

const accountKey = prefix + 'chatgpt-demo-account';
const ownerSelection = prefix + 'model-selection';
const managedPrefix = prefix + 'managed-model:';
const managedSelection = player => managedPrefix + player;
const modeKey = player => prefix + 'model-mode:' + player;
const ownerModels = ['GPT-6.2 Sol', 'GPT-6.1 Terra'];
export const DEEPSEEK = 'DeepSeek';
export const TERRA = 'terra-api';
export const TERRA_LABEL = 'GPT-6.1 Terra';
export const effortLabels = {inherit:'跟随全局',off:'关闭',low:'低',medium:'中',high:'高',xhigh:'极高',max:'最高'};
export const modelModes = {
  player: '普通玩家 · 仅 DeepSeek',
  'player-terra': '普通玩家 · 已开通 Terra',
  'player-terra-exhausted': '普通玩家 · Terra 额度用完',
  'owner-locked': '服主 · 私人 ChatGPT 未连接',
  owner: '服主 · 已连接私人 ChatGPT',
  'owner-terra': '服主 · 私人 ChatGPT 和 Terra',
};

export const hasPreviewAccount = () => readValue(accountKey) === true;
export function connectPreviewAccount(connected) {
  if (!connected && ownerModels.includes(readValue(ownerSelection))) writeValue(ownerSelection, DEEPSEEK);
  writeValue(accountKey, connected);
}
export function modelMode(player) {
  const connected = player === previewOwner.uuid ? readValue(accountKey) : null;
  const terraGranted = readValue(prefix + 'terra-account:' + player)?.granted === true;
  const fallback = connected === true ? (terraGranted ? 'owner-terra' : 'owner')
    : terraGranted ? 'player-terra' : connected === false ? 'owner-locked' : 'player';
  return readValue(modeKey(player), fallback);
}
export function changeModelMode(player, value) {
  if (Object.hasOwn(modelModes, value)) writeValue(modeKey(player), value);
}
const hasPrivateModels = mode => mode === 'owner' || mode === 'owner-terra';
export const ownerModelView = mode => hasPrivateModels(mode) || mode === 'owner-locked';
export const terraAllowed = (mode, available = terraPreviewProjectAvailable()) =>
  available && (mode === 'player-terra' || mode === 'player-terra-exhausted' || mode === 'owner-terra');
export function previewModels(mode, available) {
  return [DEEPSEEK, ...(terraAllowed(mode, available) ? [TERRA] : []), ...(hasPrivateModels(mode) ? ownerModels : [])];
}
export function modelLabel(model, distinguishPlan = false) {
  if (model === TERRA) return TERRA_LABEL;
  if (distinguishPlan && model === TERRA_LABEL) return TERRA_LABEL + '（套餐）';
  return model || DEEPSEEK;
}
function selectionKey(player, mode) {
  return ownerModelView(mode) ? ownerSelection : managedSelection(player);
}
export function selectedModel(player, mode = modelMode(player)) {
  const saved = readValue(selectionKey(player, mode));
  return previewModels(mode).includes(saved) ? saved : DEEPSEEK;
}
export function resetPreviewProjectSelection() {
  for (const key of Object.keys(localStorage)) {
    if ((key === ownerSelection || key.startsWith(managedPrefix)) && readValue(key) === TERRA) writeValue(key, DEEPSEEK);
  }
}
export function selectPreviewModel(player, model) {
  const mode = modelMode(player);
  if (previewModels(mode).includes(model)) writeValue(selectionKey(player, mode), model);
}
export function effortOptions(mode, model) {
  if (!ownerModelView(mode) || model === TERRA) return [];
  return model === DEEPSEEK ? ['inherit','off','low','high','max'] : ['low','medium','high','xhigh','max'];
}
const personalKey = (player, model) => prefix + 'personal-effort:' + player + ':' + model;
export function personalEffort(player, model, mode = modelMode(player)) {
  const options = effortOptions(mode, model);
  const saved = readValue(personalKey(player, model));
  return options.includes(saved) ? saved : !options.length ? undefined : model === DEEPSEEK ? 'inherit' : 'medium';
}
export function effectiveEffort(player, model, mode = modelMode(player)) {
  const saved = personalEffort(player, model, mode);
  if (saved !== 'inherit') return saved;
  const global = readValue(effortKey,'low');
  return global === 'medium' ? 'high' : ['off','low','high','max'].includes(global) ? global : 'low';
}
export function selectEffort(player, model, value) {
  if (effortOptions(modelMode(player), model).includes(value)) writeValue(personalKey(player, model), value);
}
