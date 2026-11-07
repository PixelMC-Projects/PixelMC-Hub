import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { ArrowRight, Check, ChevronDown, Heart, LockKeyhole, MessageCircle, Pin, Search, Send, Trash2, FileText } from 'lucide-react';
import AnimatedList from '../vendor/react-bits/AnimatedList.jsx';
import ClickSpark from '../vendor/react-bits/ClickSpark.jsx';
import ChangelogArticle from './ChangelogArticle.jsx';
import PlayerAvatar2D from './PlayerAvatar2D.jsx';
import { useChangelog, dispatch } from './store.js';
import { DEMO_PLAYER, visiblePacks, publishedEntries, playerEntry, formatTime, formatVersion } from './model.js';
import './changelog.css';

function PackPicker({ packs, selected, change }) {
  const [open, setOpen] = useState(false), ref = useRef(null);
  useEffect(() => {
    if (!open) return;
    const outside = e => { if (!ref.current?.contains(e.target)) setOpen(false); };
    const key = e => { if (e.key === 'Escape') { e.stopPropagation(); setOpen(false); } };
    document.addEventListener('pointerdown', outside); ref.current?.addEventListener('keydown', key);
    const root = ref.current;
    return () => { document.removeEventListener('pointerdown', outside); root?.removeEventListener('keydown', key); };
  }, [open]);
  const current = packs.find(p => p.id === selected);
  if (packs.length < 2) return <span className="updates-pack-label"><span/>{current?.name}</span>;
  return <div className="updates-pack-picker" ref={ref}><button aria-expanded={open} aria-label="选择整合包" onClick={() => setOpen(!open)}>{current?.name}<ChevronDown size={14}/></button>{open && <div className="updates-pack-menu">{packs.map(p => <button key={p.id} onClick={() => { change(p.id); setOpen(false); }}><span>{p.name}<small>{p.current ? '当前整合包' : '历史整合包'}</small></span>{p.id === selected && <Check size={14}/>}</button>)}</div>}</div>;
}

function PrivateFeedback({ entry, settings, notify, reducedMotion }) {
  const [drafts, setDrafts] = useState({}), [deleting, setDeleting] = useState(null);
  const draft = drafts[entry.id] || '';
  const inputRef = useRef(null);
  const sizeInput = () => { const input = inputRef.current; if (!input) return; input.style.height = '0px'; const height = Math.min(168, Math.max(30, input.scrollHeight)); input.style.height = height + 'px'; input.style.overflowY = input.scrollHeight > 168 ? 'auto' : 'hidden'; };
  useLayoutEffect(sizeInput, [draft, entry.id]);
  useEffect(() => { const input = inputRef.current; if (!input) return; let width = 0; const observer = new ResizeObserver(entries => { const next = entries[0].contentRect.width; if (next !== width) { width = next; sizeInput(); } }); observer.observe(input); return () => observer.disconnect(); }, [settings.commentsEnabled]);
  const run = (action, message) => {
    try { dispatch(action); if (message) notify(message); return true; }
    catch (error) { notify(error.message); return false; }
  };
  const submit = e => { e.preventDefault(); if (run({ type: 'comment', entryId: entry.id, text: draft }, '留言已发送，仅你和管理员可见')) setDrafts(d => ({ ...d, [entry.id]: '' })); };
  return <>
    <div className="updates-reaction-row">
      <div><strong>这次更新怎么样？</strong><span>一个赞，就是一份支持。</span></div>
      <ClickSpark sparkColor={['#7699e6','#a884dd','#d3bbef']} sparkSize={5} sparkRadius={23} sparkCount={7} duration={480} shape="star" disabled={reducedMotion || entry.liked || !settings.likesEnabled} style={{width:'auto',height:'auto'}}>
        <button className={`updates-like ${entry.liked ? 'is-liked' : ''}`} aria-label={entry.liked ? '取消点赞' : '为这次更新点赞'} aria-pressed={entry.liked} disabled={!settings.likesEnabled} onClick={() => run({ type:'like', entryId:entry.id })}><Heart size={17} fill={entry.liked ? 'currentColor' : 'none'}/><span>{entry.liked ? '已点赞' : '点赞'}</span><span className="updates-like-count" key={entry.likeCount}>{entry.likeCount}</span></button>
      </ClickSpark>
    </div>
    <section className="updates-feedback">
      <div className="updates-feedback-heading"><h3><MessageCircle size={16}/>我的留言<small>{entry.comments.length}</small></h3><span><LockKeyhole size={12}/>仅你与管理员可见</span></div>
      {settings.commentsEnabled ? <form onSubmit={submit} className="updates-compose"><label className="sr-only" htmlFor={'update-comment-'+entry.id}>给管理员留言</label><textarea ref={inputRef} rows={1} id={'update-comment-'+entry.id} value={draft} maxLength={settings.commentLimit} placeholder="写下想法，或遇到的问题…" onChange={e => setDrafts(d => ({ ...d, [entry.id]: e.target.value }))}/><div><span>{draft.length} / {settings.commentLimit}</span><button className="btn primary" type="submit" disabled={!draft.trim()}><Send size={13}/>发送留言</button></div></form> : <p className="updates-comments-paused">管理员已暂停接收新留言，已有的对话仍然保留。</p>}
      <div className="updates-threads">
        {entry.comments.length ? entry.comments.toReversed().map(c => <div className="updates-thread" key={c.id}>
          <div className="updates-thread-meta"><PlayerAvatar2D name={DEMO_PLAYER.name}/><strong>{DEMO_PLAYER.name}<small>我</small></strong><time>{formatTime(c.createdAt)}</time><button aria-label="删除我的留言" onClick={() => setDeleting(c.id)}><Trash2 size={13}/></button></div>
          <p>{c.text}</p>
          {c.replies.map(r => <div className="updates-reply" key={r.id}><div><span>管理员回复</span><time>{formatTime(r.createdAt)}</time></div><p>{r.text}</p></div>)}
          {!c.replies.length && <span className="updates-awaiting">已送达，等待管理员回复</span>}
          {deleting === c.id && <div className="updates-delete-confirm"><span>删除这条留言及回复？</span><button onClick={() => setDeleting(null)}>取消</button><button onClick={() => { run({type:'deleteComment', commentId:c.id},'留言已删除'); setDeleting(null); }}>确认删除</button></div>}
        </div>) : <div className="updates-no-comments"><LockKeyhole size={15}/><span>你的留言和回复会留在这里。其他玩家看不到。</span></div>}
      </div>
    </section>
  </>;
}

export default function ChangelogPage({ notify, reduceMotion = false }) {
  const state = useChangelog(), packs = visiblePacks(state);
  const [packId, setPackId] = useState(() => packs.find(p => p.current)?.id), [search, setSearch] = useState('');
  const [selected, setSelected] = useState(null), scrollRef = useRef(null);
  const activePack = packs.find(p => p.id === packId) || packs.find(p => p.current) || packs[0];
  const all = publishedEntries(state, activePack?.id), rows = all.filter(e => `${e.title} ${formatVersion(e, activePack)} ${e.summary}`.toLowerCase().includes(search.trim().toLowerCase()));
  const chosen = rows.find(e => e.id === selected) || rows[0];
  const entry = playerEntry(state, chosen);
  useEffect(() => { scrollRef.current?.scrollTo({top:0}); }, [entry?.id]);
  const choose = item => setSelected(item.id);
  return <>
    <div className="page-heading"><div><h1>更新日志<span className="heading-count">{all.length}</span></h1><p>每一点变化，都在这里与你分享。</p></div><div className="updates-heading-actions">{packs.length>1&&<PackPicker packs={packs} selected={activePack?.id} change={id=>{setPackId(id);setSearch('');setSelected(null);}}/>}<span className="module-label">PixelMC-Hub</span></div></div>

    <div className="updates-layout">
      <aside className="updates-index">
        <label className="updates-search"><Search size={15}/><input aria-label="搜索更新日志" value={search} onChange={e=>setSearch(e.target.value)} placeholder="搜索标题或版本…"/></label>
        {rows.length ? <AnimatedList key={activePack?.id} className="updates-animated-list" items={rows} initialSelectedIndex={0} selectedIndex={Math.max(0,rows.findIndex(e=>e.id===entry?.id))} onItemSelect={choose} selectOnHover={false} animation={reduceMotion ? 'none' : 'slide'} animateOnce stagger={.045} frame={false} theme="light" accentColor="#9d8bcf" gap={10} width="100%" maxHeight="100%" showGradients={false} renderItem={(item) => <div className={`updates-index-item ${item.id === entry?.id ? 'is-active' : ''}`}><div className="updates-item-top"><span title={formatVersion(item, activePack)}>{formatVersion(item, activePack)}</span><time>{formatTime(item.publishedAt,true).slice(5)}</time></div><h3>{item.title}</h3><p>{item.summary}</p><div className="updates-item-foot"><span className={'update-category category-'+(item.category==='问题修复'?'fix':item.category==='体验优化'?'polish':'feature')}><i/>{item.category}</span>{item.pinned ? <span><Pin size={11}/>置顶</span> : item.id===all[0]?.id ? <span>最新</span> : <ArrowRight size={12}/>}</div></div>}/> : <div className="updates-list-empty"><FileText size={22}/><p>没有匹配的记录</p>{search && <button onClick={()=>setSearch('')}>清除搜索</button>}</div>}

      </aside>
      <div className="updates-reader-frame"><div className="updates-reader" ref={scrollRef}>
        <AnimatePresence mode="wait" initial={false}>
          {entry ? <motion.div key={entry.id} initial={reduceMotion ? false : {opacity:0,y:10}} animate={{opacity:1,y:0}} exit={{opacity:0,y:reduceMotion ? 0 : -5}} transition={{duration:reduceMotion ? 0 : .2}}>
            <ChangelogArticle entry={entry} packName={activePack.name}><PrivateFeedback entry={entry} settings={state.settings} notify={notify} reducedMotion={reduceMotion}/></ChangelogArticle>
          </motion.div> : <div className="updates-reader-empty"><FileText size={34}/><h2>{state.settings.updatesEnabled ? '这里还没有更新记录' : '更新日志暂未开放'}</h2><p>{search ? '换个关键词试试。' : '发布后的更新会出现在这里。'}</p></div>}
        </AnimatePresence>
      </div></div>
    </div>
  </>;
}
