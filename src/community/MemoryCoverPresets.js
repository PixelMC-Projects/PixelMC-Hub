// SPDX-License-Identifier: Apache-2.0
// Finite IDs for the Web cover renderer and a future Manager preset picker.
export const MEMORY_COVER_PATTERNS = Object.freeze([
  { id: 'arcs', name: '流光花园' },
  { id: 'ridges', name: '山间晴岚' },
  { id: 'grid', name: '几何工坊' },
  { id: 'stars', name: '星河漫游' },
]);

export const MEMORY_COVER_COLORS = Object.freeze([
  { id: 'sage', name: '青苔' },
  { id: 'mist', name: '雾蓝' },
  { id: 'sand', name: '麦穗' },
  { id: 'lilac', name: '浅藤' },
  { id: 'tide', name: '浅汐' },
  { id: 'rose', name: '蔷薇' },
]);

export function resolveMemoryCover(archive = {}) {
  const coverPattern = MEMORY_COVER_PATTERNS.some(pattern => pattern.id === archive.coverPattern)
    ? archive.coverPattern : 'arcs';
  const legacyColor = MEMORY_COVER_COLORS.some(color => color.id === archive.theme) ? archive.theme : 'mist';
  const coverColor = MEMORY_COVER_COLORS.some(color => color.id === archive.coverColor)
    ? archive.coverColor : legacyColor;
  return { coverPattern, coverColor };
}
