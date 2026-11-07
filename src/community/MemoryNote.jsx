// SPDX-License-Identifier: Apache-2.0
import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { Check, Feather, Pencil, Plus, Trash2 } from 'lucide-react';
import { motion, useReducedMotion } from 'motion/react';
import { SELF_PROFILE } from './demo.js';
import {
  MEMORY_NOTE_LIMIT, MEMORY_SUPPLEMENT_LIMIT, MEMORY_SUPPLEMENT_DELETE_WINDOW,
  appendMemorySupplement, canDeleteMemorySupplement, createMemorySubmissionId, deleteMemorySupplement,
  formatMemoryTime, initialMemoryNotes, readMemoryNotes, saveMemoryNote, subscribeMemoryNotes,
} from './memoryStore.js';

function fitTextarea(element, measure) {
  if (!element || !measure) return;
  // Measure the separate, out-of-flow copy so the live reader never collapses to zero.
  element.style.height = getComputedStyle(measure).height;
}

export default function MemoryNote({ player, archive, reduceMotion = false, notify }) {
  const systemReducedMotion = useReducedMotion();
  const noMotion = reduceMotion || systemReducedMotion;
  const [stored, setStored] = useState(() => {
    try { return { record: readMemoryNotes(player.playerId, archive.id), error: '' }; }
    catch (error) {
      const record = initialMemoryNotes(player.playerId, archive.id);
      return { record: { ...record, note: { text: '', revision: null, updatedAt: null, deleted: false }, supplements: [] }, error: error.message };
    }
  });
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');
  const [expectedRevision, setExpectedRevision] = useState(null);
  const [submissionId, setSubmissionId] = useState(null);
  const [saving, setSaving] = useState(false);
  const [clockNow, setClockNow] = useState(() => Date.now());
  const [confirmingId, setConfirmingId] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [bodyHeight, setBodyHeight] = useState(null);
  const noteRef = useRef(null);
  const bodyRef = useRef(null);
  const textareaRef = useRef(null);
  const measureRef = useRef(null);
  const readerSnapshotRef = useRef(null);
  const actionRef = useRef(null);
  const deleteButtonsRef = useRef(new Map());
  const confirmCancelRef = useRef(null);
  const editorId = useId();
  const archived = Boolean(archive.end);
  const canWrite = player.isSelf && player.demo && player.playerId === SELF_PROFILE.playerId;
  const limit = archived ? MEMORY_SUPPLEMENT_LIMIT : MEMORY_NOTE_LIMIT;

  function reload() {
    try { setStored({ record: readMemoryNotes(player.playerId, archive.id), error: '' }); }
    catch (error) { setStored(previous => ({ ...previous, error: error.message })); }
  }

  useEffect(() => subscribeMemoryNotes(player.playerId, archive.id, reload), [player.playerId, archive.id]);
  useEffect(() => { setClockNow(Date.now()); }, [stored.record.supplements]);

  useLayoutEffect(() => { if (editing) fitTextarea(textareaRef.current, measureRef.current); }, [editing, draft]);
  useLayoutEffect(() => {
    const body = bodyRef.current;
    if (!body) return;
    const measureBody = () => setBodyHeight(Number.parseFloat(getComputedStyle(body).height));
    measureBody();
    const observer = new ResizeObserver(measureBody);
    observer.observe(body);
    return () => observer.disconnect();
  }, []);
  useLayoutEffect(() => {
    const snapshot = readerSnapshotRef.current;
    if (editing) textareaRef.current?.focus({ preventScroll: true });
    else if (snapshot) actionRef.current?.focus({ preventScroll: true });
    if (snapshot) {
      snapshot.reader.scrollTop = Math.min(snapshot.top, Math.max(0, snapshot.reader.scrollHeight - snapshot.reader.clientHeight));
      readerSnapshotRef.current = null;
    }
  }, [editing]);
  useEffect(() => {
    if (!editing || !measureRef.current) return;
    const observer = new ResizeObserver(() => fitTextarea(textareaRef.current, measureRef.current));
    observer.observe(measureRef.current);
    return () => observer.disconnect();
  }, [editing]);

  useEffect(() => {
    if (!canWrite) return;
    const now = Date.now();
    const boundaries = stored.record.supplements.map(supplement => supplement.createdAt > now
      ? supplement.createdAt : supplement.createdAt + MEMORY_SUPPLEMENT_DELETE_WINDOW).filter(deadline => deadline > now);
    const refreshClock = () => setClockNow(Date.now());
    const timer = boundaries.length ? window.setTimeout(refreshClock, Math.min(Math.min(...boundaries) - now + 1, 2_147_483_647)) : null;
    window.addEventListener('focus', refreshClock);
    document.addEventListener('visibilitychange', refreshClock);
    return () => {
      if (timer !== null) window.clearTimeout(timer);
      window.removeEventListener('focus', refreshClock);
      document.removeEventListener('visibilitychange', refreshClock);
    };
  }, [canWrite, stored.record.supplements, clockNow]);

  useEffect(() => {
    if (!confirmingId) return;
    const supplement = stored.record.supplements.find(item => item.id === confirmingId);
    if (!canDeleteMemorySupplement(player.playerId, supplement, clockNow)) {
      setConfirmingId(null);
      requestAnimationFrame(() => actionRef.current?.focus({ preventScroll: true }));
    }
  }, [confirmingId, clockNow, player.playerId, stored.record.supplements]);
  useEffect(() => { if (confirmingId) confirmCancelRef.current?.focus({ preventScroll: true }); }, [confirmingId]);

  function rememberReaderPosition() {
    const reader = noteRef.current?.closest('.memory-reader-scroll');
    if (reader) readerSnapshotRef.current = { reader, top: reader.scrollTop };
  }

  function beginEditing() {
    rememberReaderPosition();
    setDraft(archived ? '' : stored.record.note.text);
    setExpectedRevision(stored.record.note.revision);
    setSubmissionId(createMemorySubmissionId());
    setConfirmingId(null);
    setEditing(true);
  }

  function closeEditor() {
    rememberReaderPosition();
    setEditing(false);
    setDraft('');
  }

  async function save(event) {
    event.preventDefault();
    if (saving || deletingId) return;
    setSaving(true);
    try {
      const request = { playerId: player.playerId, archiveId: archive.id, text: draft };
      const record = archived
        ? await appendMemorySupplement({ ...request, submissionId })
        : await saveMemoryNote({ ...request, expectedRevision });
      setStored({ record, error: '' });
      closeEditor();
      notify?.(archived ? '补记已保存' : '回忆已保存');
    } catch (error) {
      notify?.(error.message || '保存未完成，请稍后重试。', 'error');
    } finally {
      setSaving(false);
    }
  }

  function askDelete(supplement) {
    const now = Date.now();
    setClockNow(now);
    if (!canDeleteMemorySupplement(player.playerId, supplement, now)) {
      notify?.('这条补记当前已无法删除，请重新读取。', 'error');
      return;
    }
    setConfirmingId(supplement.id);
  }

  function cancelDelete(id) {
    setConfirmingId(null);
    requestAnimationFrame(() => deleteButtonsRef.current.get(id)?.focus({ preventScroll: true }));
  }

  async function removeSupplement(supplement) {
    if (saving || deletingId) return;
    setDeletingId(supplement.id);
    try {
      const record = await deleteMemorySupplement({ playerId: player.playerId, archiveId: archive.id, supplementId: supplement.id });
      setStored({ record, error: '' });
      setConfirmingId(null);
      notify?.('补记已删除');
      requestAnimationFrame(() => actionRef.current?.focus({ preventScroll: true }));
    } catch (error) {
      notify?.(error.message || '删除未完成，请稍后重试。', 'error');
    } finally {
      setDeletingId(null);
      setClockNow(Date.now());
    }
  }

  return (
    <section ref={noteRef} className="memory-note">
      <div className="memory-note-heading">
        <h3><Feather size={15} />写给这段旅途</h3>
        {canWrite && !editing && <button ref={actionRef} type="button" className="community-button subtle memory-note-action"
          disabled={Boolean(stored.error) || Boolean(deletingId)} onClick={beginEditing}>
          {archived ? <Plus size={13} /> : <Pencil size={13} />}{archived ? '追加补记' : '编辑回忆'}
        </button>}
      </div>

      <motion.div className="memory-note-body-frame" initial={false} animate={{ height: bodyHeight ?? 'auto' }}
        transition={{ duration: noMotion ? 0 : .28, ease: [.22, 1, .36, 1] }}>
      <div ref={bodyRef} className="memory-note-body">
      {(!editing || archived) && <motion.div className="memory-note-original" initial={{ opacity: noMotion ? 1 : 0 }}
        animate={{ opacity: 1 }} transition={{ duration: noMotion ? 0 : .14 }}>
        <p>{stored.record.note.deleted ? '这段回忆已移除。' : stored.record.note.text}</p>
        {stored.record.note.updatedAt && <time className="memory-note-updated" dateTime={new Date(stored.record.note.updatedAt).toISOString()}>
          {stored.record.note.deleted ? '移除于' : '编辑于'} {formatMemoryTime(stored.record.note.updatedAt)}
        </time>}
      </motion.div>}

      {stored.record.supplements.length > 0 && <div className="memory-supplements" aria-label="旅途补记">
        {stored.record.supplements.map(supplement => <article className="memory-supplement" key={supplement.id}>
          <header><h4>补记</h4><time dateTime={new Date(supplement.createdAt).toISOString()}>{formatMemoryTime(supplement.createdAt)}</time>
            {canWrite && canDeleteMemorySupplement(player.playerId, supplement, clockNow) && <button type="button"
              ref={element => { if (element) deleteButtonsRef.current.set(supplement.id, element); else deleteButtonsRef.current.delete(supplement.id); }}
              className="memory-supplement-delete" aria-label={`删除 ${formatMemoryTime(supplement.createdAt)} 的补记`}
              aria-expanded={confirmingId === supplement.id} disabled={editing || saving || Boolean(deletingId) || Boolean(stored.error)} onClick={() => askDelete(supplement)}><Trash2 size={11} />删除</button>}
          </header>
          <p>{supplement.text}</p>
          {confirmingId === supplement.id && <div className="memory-delete-confirm" role="group" aria-label="确认删除补记">
            <span>删除这条补记？</span><div>
              <button ref={confirmCancelRef} type="button" className="community-button subtle" disabled={Boolean(deletingId)} onClick={() => cancelDelete(supplement.id)}>取消</button>
              <button type="button" className="community-button memory-delete-confirm-action" disabled={Boolean(deletingId)} onClick={() => removeSupplement(supplement)}>{deletingId === supplement.id ? '删除中…' : '删除'}</button>
            </div>
          </div>}
        </article>)}
      </div>}

      {editing && <motion.form className={`memory-note-editor ${archived ? 'is-supplement' : ''}`} onSubmit={save}
        initial={{ opacity: noMotion ? 1 : 0 }} animate={{ opacity: 1 }} transition={{ duration: noMotion ? 0 : .14 }}>
        <label className={archived ? 'memory-editor-label' : 'community-sr-only'} htmlFor={editorId}>{archived ? '新的补记' : '编辑这段回忆'}</label>
        <div className="memory-note-input">
        <div ref={measureRef} className="memory-note-measure" aria-hidden="true">{`${draft}\u200b`}</div>
        <textarea ref={textareaRef} id={editorId} rows={1} maxLength={limit} value={draft} aria-describedby={`${editorId}-hint`}
          disabled={saving} onChange={event => setDraft(event.target.value)}
          placeholder={archived ? '此刻再回想这段旅途，还有什么想记下来…' : '写下你想留住的回忆…'} />
        </div>
        <div className="memory-editor-footer">
          <span id={`${editorId}-hint`}>{draft.length} / {limit}{archived ? ' · 创建后24小时内可删除' : ' 字'}</span>
          <div><button type="button" className="community-button subtle" disabled={saving} onClick={closeEditor}>取消</button>
            <button type="submit" className="community-button primary" disabled={saving || !draft.trim() || Boolean(stored.error)}><Check size={13} />{saving ? '保存中…' : archived ? '保存补记' : '保存回忆'}</button></div>
        </div>
      </motion.form>}

      {stored.error && <div className="memory-note-error-row"><p className="memory-note-error" role="alert">{stored.error}</p>
        <button type="button" className="community-button subtle" onClick={reload}>重新读取</button></div>}
      </div>
      </motion.div>
    </section>
  );
}
