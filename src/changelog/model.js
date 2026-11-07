// Apache-2.0 · Local Web prototype only. Never a production authorization layer.
import { cleanDocument, documentText } from './richDocument.js';
export const STORAGE_KEY = 'pixelmc-playerhub-changelog-preview-v1';
export const DEMO_PLAYER = { uuid: '7f1a0000-0000-4000-8000-000000000001', name: 'zhan_cpu' };
export const PEOPLE = {
  [DEMO_PLAYER.uuid]: DEMO_PLAYER.name,
  'demo-lin': 'LinMu', 'demo-moon': 'Moonrise', 'demo-north': 'North',
};
export function seed() {
  const state = normalizeState({
    schema: 1,
    packs: [
      { id: 'vanilla-food', name: '香草纪元 · 食旅纪行', current: true, visible: true },
      { id: 'vanilla-origins', name: '香草纪元 · 初始之旅', current: false, visible: false },
      { id: 'summer', name: '夏日小镇', current: false, visible: false },
    ],
    settings: { updatesEnabled: true, likesEnabled: true, commentsEnabled: true, commentLimit: 500, commentCooldown: 10 },
    entries: [
      { id: 'handbook', packId: 'vanilla-food', title: '旅人手册，与你见面', version: '2.7.2', category: '功能更新', status: 'published', pinned: true, publishedAt: '2026-11-05T12:00:00+08:00', updatedAt: '2026-11-05T12:00:00+08:00', summary: '个人统计、称号与签笺，现在收进同一本手册。', body: '欢迎打开旅人手册。从今天起，旅途中的收藏与近况，有了一个随时可以翻看的地方。\n\n## 一个按键，打开你的空间\n按下 **H**，即可查看当前整合包的游玩统计、管理称号，或翻阅已经收藏的签笺。若你修改过按键，请使用自己绑定的菜单键。\n\n## 把收藏带在身边\n- **我的称号**：查看获得方式，预览并更换佩戴。\n- **我的签笺**：按月份回看收藏，挑一张展示在手册里。\n- **更新日志**：在这里了解最近的调整，也可以给我们留下建议。\n\n## 关于你的反馈\n觉得这次更新不错，可以点个赞。遇到问题或有新的想法，请在下方留言；留言和管理员回复只对你们双方可见。', likes: [DEMO_PLAYER.uuid, 'demo-lin', 'demo-north'] },
      { id: 'fortunes', packId: 'vanilla-food', title: '签笺收藏，翻阅更顺手', version: '2.7.1', category: '体验优化', status: 'published', pinned: false, publishedAt: '2026-11-03T18:30:00+08:00', updatedAt: '2026-11-03T18:30:00+08:00', summary: '保留已读月份，切换展示时不再让日历闪一下。', body: '这次整理了签笺页面的交互，让收藏更方便翻阅。\n\n## 翻阅体验\n- 切换展示签笺时，保留当前月份的内容。\n- 最近抽签记录显示完整时间。\n- 月份切换加入柔和的过渡动画。\n\n## 收藏与展示\n签笺要先收进藏签匣，才会出现在收藏中。你仍然可以独立选择手册里展示的那一张。', likes: ['demo-lin', 'demo-moon'] },
      { id: 'titles', packId: 'vanilla-food', title: '给称号一个新的展示方式', version: '2.7.0', category: '功能更新', status: 'published', pinned: false, publishedAt: '2026-10-30T20:00:00+08:00', updatedAt: '2026-10-30T20:00:00+08:00', summary: '新的主题颜色、收藏徽章与佩戴预览。', body: '每一枚称号，都记录着一段不同的经历。\n\n## 这次新增\n- 称号卡片拥有独立的主题颜色和徽章。\n- 收藏页面可查看获得时间与获得方式。\n- 佩戴预览显示皮肤的帽子层。\n\n**隐藏展示不会摘下称号**，你可以按自己的喜好选择是否公开。', likes: ['demo-moon', 'demo-north'] },
      { id: 'fixes', packId: 'vanilla-food', title: '一些小问题，慢慢变好', version: '2.6.9', category: '问题修复', status: 'published', pinned: false, publishedAt: '2026-10-27T16:15:00+08:00', updatedAt: '2026-10-27T16:15:00+08:00', summary: '修正交互边界，补齐细节反馈。', body: '## 已调整\n- 修正藏签匣交互时的展示选择。\n- 调整方块碰撞范围，使它更接近实际外形。\n- 完善操作完成后的提示。\n\n如仍遇到异常，请描述发生场景并留言。', likes: ['demo-lin'] },
      { id: 'arrival', packId: 'vanilla-food', title: '食旅纪行，新的旅程开始了', version: '2.6.0', category: '整合包更新', status: 'published', pinned: false, publishedAt: '2026-10-17T12:00:00+08:00', updatedAt: '2026-10-17T12:00:00+08:00', summary: '带上行囊，在新的世界里相见。', body: '新整合包的第一份更新记录。\n\n## 旅途从这里开始\n新的探索、食物和建造内容已经准备就绪。感谢每一位一起参与测试和反馈的玩家。\n\n你可以通过这份记录回顾当前整合包的变化。', likes: ['demo-north'] },
      { id: 'draft', packId: 'vanilla-food', title: '下一次更新的准备', version: '2.7.3', category: '体验优化', status: 'draft', pinned: false, publishedAt: null, updatedAt: '2026-11-05T15:00:00+08:00', summary: '尚未发布的编辑示例。', body: '## 准备中的调整\n- 整理近期收到的建议。\n- 补充下一次更新说明。', likes: [] },
      { id: 'withdrawn', packId: 'vanilla-food', title: '临时活动时间调整', version: '', category: '通知', status: 'withdrawn', pinned: false, publishedAt: '2026-10-25T15:00:00+08:00', updatedAt: '2026-10-26T09:00:00+08:00', summary: '已撤下的公告示例，玩家不可见。', body: '活动时间需要重新确认，本条公告已撤下，原有互动仍然保留。', likes: ['demo-lin'] },
      { id: 'old-goodbye', packId: 'vanilla-origins', title: '初始之旅，感谢同行', version: '1.9.0', category: '整合包更新', status: 'published', pinned: false, publishedAt: '2026-09-30T20:00:00+08:00', updatedAt: '2026-09-30T20:00:00+08:00', summary: '上一整合包的更新记录，默认只在管理端保留。', body: '## 这一程的收藏\n感谢大家一同完成这段旅程。旧整合包的公告与互动会继续保留。\n\n新存档不覆盖旧的更新记录。', likes: ['demo-lin', 'demo-moon'] },
      { id: 'summer-end', packId: 'summer', title: '夏日小镇 · 最后一次更新', version: '1.4.0', category: '整合包更新', status: 'published', pinned: false, publishedAt: '2026-08-20T20:00:00+08:00', updatedAt: '2026-08-20T20:00:00+08:00', summary: '归档示例。', body: '小镇的最后一份更新记录。历史内容由管理员决定是否重新向玩家开放。', likes: [] },
    ],
    comments: [
      { id: 'c1', entryId: 'handbook', playerId: DEMO_PLAYER.uuid, text: '终于可以在一个地方看称号和签笺了！希望以后也能看到活动纪念。', createdAt: '2026-11-05T12:16:00+08:00', replies: [{ id: 'r1', text: '收到，活动纪念的建议记下了。谢谢你来体验！', createdAt: '2026-11-05T12:35:00+08:00' }] },
      { id: 'c2', entryId: 'handbook', playerId: 'demo-lin', text: '如果修改了 H 键，进服提示也会跟着变吗？', createdAt: '2026-11-05T13:08:00+08:00', replies: [] },
      { id: 'c3', entryId: 'fortunes', playerId: 'demo-moon', text: '月份切换比之前顺了很多。', createdAt: '2026-11-04T19:20:00+08:00', replies: [] },
    ],
  });
  // Times below are fixture data only. Existing browser data without timestamps stays unknown.
  for (const entry of state.entries) entry.likeTimes=Object.fromEntries(entry.likes.map((id,i)=>[id,new Date(new Date(entry.publishedAt).getTime()+(i+1)*420000).toISOString()]));
  return state;
}
export function orderedPacks(state) {
  const started = pack => new Date(pack.startedAt || Math.min(...state.entries.filter(e=>e.packId===pack.id&&e.publishedAt).map(e=>new Date(e.publishedAt).getTime()))).getTime() || 0;
  return [...state.packs].sort((a,b)=>Number(b.current)-Number(a.current)||started(b)-started(a)||a.id.localeCompare(b.id));
}
export function packLabel(pack) { return [pack?.shortName || pack?.name?.split(' · ')[0],pack?.version].filter(Boolean).join(' '); }
export function visiblePacks(state) { return orderedPacks(state).filter(p => p.visible); }
export function publishedEntries(state, packId) {
  if (!state.settings.updatesEnabled || !visiblePacks(state).some(p => p.id === packId)) return [];
  return state.entries.filter(e => e.packId === packId && e.status === 'published').sort((a,b) => Number(b.pinned)-Number(a.pinned) || new Date(b.publishedAt)-new Date(a.publishedAt));
}
// Player projection intentionally omits other players' comments and liker identities.
export function playerEntry(state, entry, uuid = DEMO_PLAYER.uuid) {
  if (!entry || !publishedEntries(state, entry.packId).some(e => e.id === entry.id)) return null;
  const { likes, likeTimes, ...publicEntry } = entry;
  return { ...publicEntry, likeCount: likes.length, liked: likes.includes(uuid), comments: state.comments.filter(c => c.entryId === entry.id && c.playerId === uuid) };
}
export function formatTime(value, short = false) {
  if (!value) return '尚未发布';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '—';
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', ...(short ? {} : {hour: '2-digit', minute: '2-digit', hour12: false}) }).format(date).replaceAll('/', '.');
}
const uuid = () => globalThis.crypto.randomUUID();
export function reduceAction(input, action, now = new Date().toISOString()) {
  const state = structuredClone(input);
  const entry = state.entries.find(e => e.id === action.entryId);
  const requireEntry = () => { if (!entry) throw new Error('这条更新记录已不存在。'); };
  const requireVisible = () => { requireEntry(); if (!playerEntry(state, entry)) throw new Error('这条更新记录目前不可见。'); };
  switch (action.type) {
    case 'saveEntry': {
      const draft = {...action.value};
      if (draft.bodyDocument) { draft.bodyDocument=cleanDocument(draft.bodyDocument); draft.body=documentText(draft.bodyDocument); }
      if (!draft.title?.trim() || !draft.body?.trim()) throw new Error('请填写标题和更新内容。');
      if (draft.title.length > 80 || draft.body.length > 20000) throw new Error('标题最多 80 字，正文最多 20000 字。');
      if (!state.packs.some(p => p.id === draft.packId)) throw new Error('请选择整合包。');
      const existing = state.entries.find(e => e.id === draft.id);
      const status = action.publish ? 'published' : existing?.status || 'draft';
      const value = { id: existing?.id || uuid(), title: draft.title.trim(), body: draft.body.trim(), ...(draft.bodyDocument?{bodyDocument:draft.bodyDocument}:{}), summary: draft.summary?.trim().slice(0,160) || '', version: draft.packVersion?.trim().slice(0,32) || '', packName: draft.packName?.trim().slice(0,40) || '', packVersion: draft.packVersion?.trim().slice(0,32) || '', updateVersion: draft.updateVersion?.trim().slice(0,32) || '', category: draft.category || '功能更新', packId: draft.packId, pinned: !!draft.pinned, status, updatedAt: now, publishedAt: existing?.publishedAt || (status === 'published' ? now : null), likes: existing?.likes || [], likeTimes: existing?.likeTimes || {} };
      if (existing) state.entries[state.entries.indexOf(existing)] = value; else state.entries.unshift(value);
      break;
    }
    case 'deleteEntry': {
      requireEntry();
      if (action.confirmTitle !== entry.title) throw new Error('输入的标题不一致，未删除日志。');
      state.entries=state.entries.filter(e=>e.id!==entry.id);
      state.comments=state.comments.filter(c=>c.entryId!==entry.id);
      break;
    }
    case 'status': requireEntry(); entry.status = action.value; if (action.value === 'published') entry.publishedAt ||= now; break;
    case 'pin': requireEntry(); entry.pinned = !entry.pinned; break;
    case 'packVisibility': { const pack = state.packs.find(p => p.id === action.packId); if (pack) pack.visible = !!action.value; break; }
    case 'settings': {
      const s = action.value;
      if (!Number.isInteger(Number(s.commentLimit)) || s.commentLimit < 50 || s.commentLimit > 2000 || !Number.isInteger(Number(s.commentCooldown)) || s.commentCooldown < 0 || s.commentCooldown > 300) throw new Error('留言上限为 50–2000 字，间隔为 0–300 秒。');
      state.settings = { updatesEnabled: !!s.updatesEnabled, likesEnabled: !!s.likesEnabled, commentsEnabled: !!s.commentsEnabled, commentLimit: Number(s.commentLimit), commentCooldown: Number(s.commentCooldown) }; break;
    }
    case 'like': {
      requireVisible(); if (!state.settings.likesEnabled) throw new Error('点赞功能已关闭。');
      entry.likeTimes ||= {};
      if (entry.likes.includes(DEMO_PLAYER.uuid)) { entry.likes=entry.likes.filter(id=>id!==DEMO_PLAYER.uuid); delete entry.likeTimes[DEMO_PLAYER.uuid]; }
      else { entry.likes.push(DEMO_PLAYER.uuid); entry.likeTimes[DEMO_PLAYER.uuid]=now; }
      break;
    }
    case 'comment': {
      requireVisible(); if (!state.settings.commentsEnabled) throw new Error('管理员已暂停接收新留言。');
      const text = action.text.trim(); if (!text || text.length > state.settings.commentLimit) throw new Error('请检查留言字数。');
      const last = state.comments.filter(c => c.playerId === DEMO_PLAYER.uuid).sort((a,b)=>new Date(b.createdAt)-new Date(a.createdAt))[0];
      if (last && new Date(now)-new Date(last.createdAt) < state.settings.commentCooldown*1000) throw new Error('留言发送得有些频繁，请稍后再试。');
      state.comments.push({ id: uuid(), entryId: entry.id, playerId: DEMO_PLAYER.uuid, text, createdAt: now, replies: [] }); break;
    }
    case 'deleteComment': {
      const comment = state.comments.find(c => c.id === action.commentId);
      if (comment && (action.admin || comment.playerId === DEMO_PLAYER.uuid)) state.comments = state.comments.filter(c => c.id !== comment.id);
      break;
    }
    case 'reply': {
      const comment = state.comments.find(c => c.id === action.commentId), text = action.text.trim();
      if (!comment || !text || text.length > 2000) throw new Error('请填写有效的回复（最多 2000 字）。');
      comment.replies.push({ id: uuid(), text, createdAt: now }); break;
    }
    default: throw new Error('不支持的预览操作。');
  }
  return state;
}

// Per-entry snapshots preserve the historical label when the active pack changes.
export function normalizeState(state) {
  const labels = { 'vanilla-food':['香草纪元','2.7.1'], 'vanilla-origins':['香草纪元','1.9.0'], summer:['夏日小镇','1.4.0'] };
  state.packs = state.packs.map(p => ({ ...p, visible:p.visible ?? !!p.current, shortName:p.shortName ?? labels[p.id]?.[0] ?? p.name, version:p.version ?? labels[p.id]?.[1] ?? '' }));
  const versions = {handbook:['2.7.1','1.0'],fortunes:['2.7.1','0.9'],titles:['2.7.1','0.8'],fixes:['2.7.0','0.2'],arrival:['2.7.0','0.1'],draft:['2.7.1','1.1'],withdrawn:['2.7.0','0.3']};
  state.entries = state.entries.map(e => { const p=state.packs.find(p=>p.id===e.packId); return {...e,packName:e.packName ?? p?.shortName ?? '',packVersion:e.packVersion ?? versions[e.id]?.[0] ?? e.version ?? p?.version ?? '',updateVersion:e.updateVersion ?? versions[e.id]?.[1] ?? '1.0'}; });
  return state;
}
export function formatVersion(entry, pack) {
  const name=entry.packName || pack?.shortName || pack?.name?.split(' · ')[0] || '';
  const version=entry.packVersion || '';
  return name+version+(entry.updateVersion ? '-'+entry.updateVersion : '') || '更新记录';
}
