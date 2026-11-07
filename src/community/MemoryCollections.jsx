// SPDX-License-Identifier: Apache-2.0
import { useEffect, useId, useRef, useState } from 'react';
import { Check, Leaf, Pencil } from 'lucide-react';
import { AnimatePresence, motion, useIsPresent, useReducedMotion } from 'motion/react';
import { titleIcons } from '../title-icons.js';
import { SELF_PROFILE } from './demo.js';
import {
  filterMemoryCollectionSelection, formatAcquisitionDate, getMemoryCollectionCandidates,
  initialMemoryCollections, readMemoryCollections, saveMemoryCollections, subscribeMemoryCollections,
} from './memoryCollections.js';

function CollectionItem({ event, showDate = false }) {
  const Badge = titleIcons[event.item.icon] || Leaf;
  return <>
    {event.kind === 'title'
      ? <span className={`profile-collection-emblem ${event.item.style}`}><Badge size={23} strokeWidth={1.55} /></span>
      : <img className="memory-collection-slip" src="/assets/fortune-slip.png" alt="" />}
    <div className="memory-collection-item-copy">
      <span className="community-caption">{event.kind === 'title' ? '称号' : event.item.grade}</span>
      <strong className={event.kind === 'title' ? `title-text ${event.item.style}` : 'memory-collection-poem'}>{event.kind === 'title' ? event.item.name : event.item.title}</strong>
      {showDate && <time dateTime={event.obtainedAt}>{formatAcquisitionDate(event.obtainedAt)} 获得</time>}
    </div>
  </>;
}

function CollectionEditorTransition({ reduceMotion, children }) {
  const present = useIsPresent();
  return <motion.div className="memory-collections-expansion" inert={!present ? true : undefined} aria-hidden={!present ? true : undefined}
    initial={{ height: reduceMotion ? 'auto' : 0, opacity: reduceMotion ? 1 : 0 }}
    animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }}
    transition={{ height: { duration: reduceMotion ? 0 : .32, ease: [.22, 1, .36, 1] },
      opacity: { duration: reduceMotion ? 0 : .18, delay: reduceMotion || !present ? 0 : .06 } }}>
    {children}
  </motion.div>;
}

export default function MemoryCollections({ player, archive, reduceMotion = false, notify }) {
  const systemReducedMotion = useReducedMotion();
  const noMotion = reduceMotion || systemReducedMotion;
  const [stored, setStored] = useState(() => {
    try { return { record: readMemoryCollections(player.playerId, archive.id), error: '' }; }
    catch (error) {
      const record = initialMemoryCollections(player.playerId, archive.id);
      return { record: { ...record, selection: { titleEventId: null, fortuneEventId: null } }, error: error.message };
    }
  });
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(stored.record.selection);
  const [expectedRevision, setExpectedRevision] = useState(null);
  const [saving, setSaving] = useState(false);
  const editorId = useId();
  const actionRef = useRef(null);
  const editorRef = useRef(null);
  const canWrite = player.isSelf && player.demo && player.playerId === SELF_PROFILE.playerId;
  const candidates = getMemoryCollectionCandidates(player.playerId, archive.id);
  const visibleSelection = filterMemoryCollectionSelection(player.playerId, archive.id, editing ? draft : stored.record.selection);
  const selected = [visibleSelection.titleEventId, visibleSelection.fortuneEventId]
    .map(id => candidates.find(event => event.eventId === id)).filter(Boolean);

  function reload() {
    try { setStored({ record: readMemoryCollections(player.playerId, archive.id), error: '' }); }
    catch (error) { setStored(previous => ({ ...previous, error: error.message })); }
  }

  useEffect(() => subscribeMemoryCollections(player.playerId, archive.id, reload), [player.playerId, archive.id]);
  useEffect(() => { if (editing) editorRef.current?.querySelector('input:checked')?.focus({ preventScroll: true }); }, [editing]);

  function beginEditing() {
    setDraft(stored.record.selection);
    setExpectedRevision(stored.record.revision);
    setEditing(true);
  }

  function closeEditor() {
    setEditing(false);
    requestAnimationFrame(() => actionRef.current?.focus({ preventScroll: true }));
  }

  async function save(event) {
    event.preventDefault();
    if (saving) return;
    setSaving(true);
    try {
      const record = await saveMemoryCollections({ playerId: player.playerId, archiveId: archive.id, selection: draft, expectedRevision });
      setStored({ record, error: '' });
      closeEditor();
      notify?.(record.filteredCount ? '纪念已保存，范围外的选择已移除' : '纪念选择已保存');
    } catch (error) { notify?.(error.message || '选择保存未完成，请稍后重试。', 'error'); }
    finally { setSaving(false); }
  }

  return (
    <section className="memory-collections">
      <div className="memory-collections-heading"><h3>带走的纪念</h3>
        {editing && <span className="community-caption">选择预览</span>}
        {canWrite && !editing && <button ref={actionRef} type="button" className="community-button subtle memory-note-action" disabled={Boolean(stored.error)} onClick={beginEditing}><Pencil size={13} />编辑纪念</button>}
      </div>

      <div className="memory-collection-row">
        {selected.map(event => <div className="memory-selected-collection" key={event.eventId}><CollectionItem event={event} showDate /></div>)}
        {selected.length === 0 && <p className="memory-collection-empty">还没有选择这段旅途的纪念。</p>}
      </div>

      <AnimatePresence initial={false}>
      {editing && <CollectionEditorTransition key="collection-editor" reduceMotion={noMotion}>
      <form ref={editorRef} className="memory-collections-editor" onSubmit={save}>
        <div className="memory-collection-period"><span>{archive.start} — {archive.end || '至今'}</span><span>演示获得记录</span></div>
        <div className="memory-collection-groups">
          {[['title', '称号', 'titleEventId'], ['fortune', '签笺', 'fortuneEventId']].map(([kind, label, field]) => {
            const available = candidates.filter(event => event.kind === kind);
            return <fieldset key={kind} disabled={saving}><legend>{label}<span>最多 1 项</span></legend>
              <div className="memory-collection-candidates">
                {available.map(event => <label key={event.eventId} className={`memory-collection-choice ${draft[field] === event.eventId ? 'is-selected' : ''}`}>
                  <input type="radio" name={`${editorId}-${kind}`} value={event.eventId} checked={draft[field] === event.eventId} onChange={() => setDraft({ ...draft, [field]: event.eventId })} />
                  <CollectionItem event={event} showDate />
                  <span className="memory-collection-check" aria-hidden="true">{draft[field] === event.eventId && <Check size={12} />}</span>
                </label>)}
                {available.length === 0 && <p className="memory-collection-empty">此区间暂无获得记录。</p>}
                <label className={`memory-collection-choice is-empty-choice ${draft[field] === null ? 'is-selected' : ''}`}>
                  <input type="radio" name={`${editorId}-${kind}`} value="" checked={draft[field] === null} onChange={() => setDraft({ ...draft, [field]: null })} />
                  <span>留空</span><span className="memory-collection-check" aria-hidden="true">{draft[field] === null && <Check size={12} />}</span>
                </label>
              </div>
            </fieldset>;
          })}
        </div>
        <div className="memory-editor-footer"><span>保存到此浏览器的 Web 演示</span><div>
          <button type="button" className="community-button subtle" disabled={saving} onClick={closeEditor}>取消</button>
          <button type="submit" className="community-button primary" disabled={saving || Boolean(stored.error)}><Check size={13} />{saving ? '保存中…' : '保存选择'}</button>
        </div></div>
      </form>
      </CollectionEditorTransition>}
      </AnimatePresence>

      {stored.error && <div className="memory-note-error-row"><p className="memory-collection-error" role="alert">{stored.error}</p><button type="button" className="community-button subtle" onClick={reload}>重新读取</button></div>}

    </section>
  );
}
