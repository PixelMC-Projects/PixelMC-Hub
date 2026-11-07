// SPDX-License-Identifier: Apache-2.0
export const PROFILE_PATTERNS = Object.freeze([
  { id: 'classic', name: '流光', category: '简约' },
  { id: 'doodle', name: '云间露营', category: '简笔画卡通' },
  { id: 'tech', name: '星际电路', category: '科技' },
  { id: 'architecture', name: '方块小城', category: '建筑' },
  { id: 'nature', name: '风过叶梢', category: '自然' },
  { id: 'cosmos', name: '星河漫游', category: '星空' },
]);

export function profilePatternId(value) {
  return PROFILE_PATTERNS.some(pattern => pattern.id === value) ? value : 'classic';
}
