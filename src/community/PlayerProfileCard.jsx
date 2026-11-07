// SPDX-License-Identifier: Apache-2.0
import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { ArrowUpRight, Check, ChevronLeft, ChevronRight, Feather, Pencil, CalendarDays, Leaf } from 'lucide-react';
import PlayerHead from '../PlayerHead.jsx';
import GlassSurface from '../vendor/react-bits/GlassSurface.jsx';
import { titleIcons } from '../title-icons.js';
import { getProfileMemories, PROFILE_BACKGROUNDS, PROFILE_TITLES, SELF_PROFILE, readProfilePreferences, saveProfilePreferences, readCommunityProfile, subscribeCommunity, profileStorageKey, HUB_PREVIEW_STORAGE_KEY } from './demo.js';
import './community.css';
import './profile-customization.css';
import ProfileBackdrop from './ProfileBackdrop.jsx';
import { PROFILE_PATTERNS, profilePatternId } from './profile-patterns.js';

function collectionDate(value) {
  return typeof value === 'string' && /^\d{4}[-/.]\d{2}[-/.]\d{2}$/.test(value)
    ? value.replaceAll('-', '/').replaceAll('.', '/')
    : '获得日期未记录';
}

function ProfileModeRegion({ editing, reduced, readContent, children, className = '' }) {
  const readRef = useRef(null);
  const editRef = useRef(null);
  const [height, setHeight] = useState(null);

  useLayoutEffect(() => {
    const content = editing ? editRef.current : readRef.current;
    setHeight(content?.offsetHeight ?? 0);
  }, [editing, readContent, children]);

  useEffect(() => {
    const observer = new ResizeObserver(() => {
      const content = editing ? editRef.current : readRef.current;
      setHeight(content?.offsetHeight ?? 0);
    });
    if (readRef.current) observer.observe(readRef.current);
    if (editRef.current) observer.observe(editRef.current);
    return () => observer.disconnect();
  }, [editing]);

  return <motion.div className={`profile-mode-region ${height === null ? '' : 'is-measured'} ${className}`}
    initial={false} animate={{ height: height ?? 'auto' }}
    transition={{ duration: reduced ? 0 : 0.28, ease: [0.22, 1, 0.36, 1] }}>
    <motion.div className="profile-mode-read" ref={readRef} inert={editing} aria-hidden={editing}
      initial={false} animate={{ opacity: editing ? 0 : 1 }} transition={{ duration: reduced ? 0 : 0.16 }}>
      {readContent}
    </motion.div>
    <motion.div className="profile-mode-edit" ref={editRef} inert={!editing} aria-hidden={!editing}
      initial={false} animate={{ opacity: editing ? 1 : 0 }} transition={{ duration: reduced ? 0 : 0.16 }}>
      {children}
    </motion.div>
  </motion.div>;
}

export function PlayerProfileCard({ player = SELF_PROFILE, onOpenMemories, fortuneOptions = [], onSaveFortune, onSave, reduceMotion = false, notify }) {
  const [profile, setProfile] = useState(() => readProfilePreferences(player));
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [draft, setDraft] = useState(() => readProfilePreferences(player));
  const [draftFortuneDate, setDraftFortuneDate] = useState(() => player.featuredFortune?.date ?? null);
  const [biographyHeight, setBiographyHeight] = useState(null);
  const prefersReducedMotion = useReducedMotion();
  const reduced = reduceMotion || prefersReducedMotion;
  const modeTransition = { duration: reduced ? 0 : 0.16, ease: [0.22, 1, 0.36, 1] };
  const heightTransition = { duration: reduced ? 0 : 0.28, ease: [0.22, 1, 0.36, 1] };
  const [memoryScroll, setMemoryScroll] = useState({ overflow: false, atStart: true, atEnd: true });
  const bioId = useId();
  const textareaRef = useRef(null);
  const biographyTextRef = useRef(null);
  const editButtonRef = useRef(null);
  const memoryTrackRef = useRef(null);

  useEffect(() => {
    const next = readProfilePreferences(player);
    setProfile(next);
    setDraft(next);
    setDraftFortuneDate(player.featuredFortune?.date ?? null);
    setEditing(false);
    // Same-player fortune updates must retain drafts and previews when browser writes fail.
  }, [player.playerId, player.isSelf, player.demo]);

  useEffect(() => {
    if (editing) textareaRef.current?.focus({ preventScroll: true });
  }, [editing]);

  useEffect(() => subscribeCommunity(event => {
    if (event.source === 'self') return;
    if (event.kind === 'storage') {
      const relevant = event.key === null || event.key === profileStorageKey(player.playerId) ||
        (player.isSelf && event.key === HUB_PREVIEW_STORAGE_KEY);
      if (!relevant) return;
    } else if (event.kind !== 'profile' || event.playerId !== player.playerId) return;
    try {
      const next = { ...readCommunityProfile(player.playerId), title: player.title };
      if (next.revision === profile.revision) return;
      if (editing) {
        notify?.('资料已在另一页面更新，当前草稿已保留。取消编辑可载入最新资料。', 'error');
        return;
      }
      setProfile(next);
      setDraft(next);
    } catch (error) { notify?.(error.message, 'error'); }
  }), [player.playerId, player.isSelf, player.title, profile.revision, editing]);

  useEffect(() => {
    if (profile.profileReadError) notify?.(profile.profileReadError, 'error');
  }, [profile.profileReadError]);


  const visibleProfile = editing ? draft : profile;
  const availableMemories = getProfileMemories(profile);
  const memory = availableMemories.find(item => item.id === visibleProfile.featuredMemoryId);
  const featuredTitleId = visibleProfile.featuredTitleId === undefined ? visibleProfile.featuredTitle?.id ?? null : visibleProfile.featuredTitleId;
  const featuredTitle = editing ? PROFILE_TITLES.find(title => title.id === featuredTitleId) ?? null : profile.featuredTitle;
  // Dates identify acquired instances, including repeated draws of the same fortune number.
  const availableFortunes = fortuneOptions.filter(fortune =>
    /^\d{4}-\d{2}-\d{2}$/.test(fortune?.date) && (!fortune.status || fortune.status === 'archived'))
    .sort((left, right) => right.date.localeCompare(left.date));
  const featuredFortune = editing
    ? availableFortunes.find(fortune => fortune.date === draftFortuneDate) ?? null
    : player.featuredFortune ?? null;
  const Badge = titleIcons[featuredTitle?.icon] || Leaf;
  const canEdit = profile.isSelf && profile.demo;
  const hasBiography = Boolean(profile.bio?.trim());

  function resizeBiography() {
    const textarea = textareaRef.current;
    if (!textarea) return;
    textarea.style.height = 'auto';
    textarea.style.height = `${textarea.scrollHeight}px`;
  }

  function measureBiography() {
    const content = editing ? textareaRef.current : biographyTextRef.current;
    if (content) setBiographyHeight(content.offsetHeight);
  }

  useLayoutEffect(() => {
    resizeBiography();
    measureBiography();
  }, [editing, draft.bio, profile.bio]);

  useEffect(() => {
    const text = biographyTextRef.current;
    const textarea = textareaRef.current;
    if (!text) return;
    let width = textarea?.clientWidth;
    const observer = new ResizeObserver(() => {
      if (textarea && textarea.clientWidth !== width) {
        width = textarea.clientWidth;
        resizeBiography();
      }
      measureBiography();
    });
    observer.observe(text);
    if (textarea) observer.observe(textarea);
    return () => observer.disconnect();
  }, [editing, canEdit]);

  function updateMemoryScroll() {
    const track = memoryTrackRef.current;
    if (!track) return;
    const maxScroll = track.scrollWidth - track.clientWidth;
    setMemoryScroll({ overflow: maxScroll > 2, atStart: track.scrollLeft <= 2, atEnd: track.scrollLeft >= maxScroll - 2 });
  }

  useLayoutEffect(() => {
    const track = memoryTrackRef.current;
    if (!editing || !track) return;
    track.scrollLeft = 0;
    updateMemoryScroll();
    const observer = new ResizeObserver(updateMemoryScroll);
    observer.observe(track);
    return () => observer.disconnect();
  }, [editing, availableMemories.length]);

  function scrollMemories(direction) {
    const track = memoryTrackRef.current;
    if (!track) return;
    track.scrollBy({ left: direction * track.clientWidth, behavior: reduced ? 'instant' : 'smooth' });
  }

  function beginEditing() {
    setDraft(profile);
    setDraftFortuneDate(player.featuredFortune?.date ?? null);
    setEditing(true);
  }

  function cancelEditing() {
    let next = profile;
    try {
      const latest = readCommunityProfile(player.playerId);
      if (latest.revision !== profile.revision) {
        next = { ...latest, title: player.title };
        setProfile(next);
      }
    } catch (error) { notify?.(error.message, 'error'); }
    setDraft(next);
    setDraftFortuneDate((next === profile ? player : next).featuredFortune?.date ?? null);
    finishEditing();
  }

  function finishEditing() {
    setEditing(false);
    requestAnimationFrame(() => editButtonRef.current?.focus({ preventScroll: true }));
  }

  async function save(event) {
    event.preventDefault();
    if (!editing || !canEdit || saving) return;
    if (onSaveFortune && draftFortuneDate !== null && !availableFortunes.some(fortune => fortune.date === draftFortuneDate)) {
      notify?.('所选签笺已不可用，请重新选择。', 'error');
      return;
    }
    setSaving(true);
    try {
      const patch = { bio: draft.bio.trim(), background: draft.background, profilePattern: draft.profilePattern ?? 'classic',
        featuredMemoryId: draft.featuredMemoryId, featuredTitleId: draft.featuredTitleId };
      if (onSaveFortune) patch.featuredFortuneDate = draftFortuneDate;
      const result = await saveProfilePreferences(profile, patch);
      if (result.blocked) {
        notify?.(result.error?.message || '保存未完成，请重新读取。', 'error');
        return;
      }
      setProfile(result.profile);
      setDraft(result.profile);
      notify?.(result.persisted ? '资料已保存' : '当前预览已更新，浏览器存储不可用', result.persisted ? 'success' : 'error');
      finishEditing();
      onSave?.(result.profile, { persisted: result.persisted });
      onSaveFortune?.(draftFortuneDate);
    } finally { setSaving(false); }
  }

  return (
    <article className={`community profile-card ${reduced ? 'community-reduced' : ''}`} data-profile-theme={visibleProfile.background} data-profile-pattern={profilePatternId(visibleProfile.profilePattern)}>
      <GlassSurface width="100%" height="auto" borderRadius={0} refraction={false}
        backgroundOpacity={0.04} className="profile-cover">
        <ProfileBackdrop pattern={visibleProfile.profilePattern} />
        <div className="profile-cover-copy">
          <span className="profile-context">{profile.isSelf ? '我的公开资料' : '玩家公开资料'}<span>Web 演示</span></span>
          <h3>{profile.name}</h3>
          {profile.title && <span className={`title-text ${profile.title.style} profile-equipped`}>{profile.title.name}</span>}
          <div className="profile-joined"><CalendarDays size={13} /><span>{profile.joined} 加入旅程</span></div>
        </div>
        <div className="profile-head-stage"><PlayerHead name={profile.name} skin={profile.isSelf ? undefined : profile.skin ?? null} /></div>
      </GlassSurface>

      <div className="profile-body">
        <form id={`${bioId}-form`} className="profile-editor" onSubmit={save} inert={saving} aria-busy={saving}>
          <div className={`profile-biography ${editing ? 'profile-biography-editing' : ''} ${!editing && !hasBiography ? 'is-empty' : ''}`}>
            <div className="profile-biography-statement">
              <motion.span className="profile-biography-mark" aria-hidden="true" initial={false}
                animate={{ opacity: editing || hasBiography ? 1 : 0 }} transition={modeTransition}>“</motion.span>
              <motion.div className={`profile-biography-textframe ${biographyHeight === null ? '' : 'is-measured'}`}
                initial={false} animate={{ height: biographyHeight ?? 'auto' }} transition={heightTransition}>
                <motion.p ref={biographyTextRef} className="profile-biography-read" initial={false}
                  inert={editing} aria-hidden={editing} animate={{ opacity: editing ? 0 : 1 }} transition={modeTransition}>
                  {hasBiography ? profile.bio : '还没有写下简介。'}
                </motion.p>
                {canEdit && <motion.div className="profile-biography-write" initial={false}
                  inert={!editing} aria-hidden={!editing} animate={{ opacity: editing ? 1 : 0 }} transition={modeTransition}>
                  <label className="profile-visually-hidden" htmlFor={bioId}>个人简介</label>
                  <textarea id={bioId} className="profile-biography-input" ref={textareaRef} value={draft.bio}
                    form={`${bioId}-form`} maxLength={120} rows={1} aria-describedby={`${bioId}-count`} placeholder="写下一点关于自己……"
                    onChange={event => setDraft({ ...draft, bio: event.target.value })} />
                </motion.div>}
              </motion.div>
            </div>
            {canEdit && <div className="profile-biography-actions">
              <motion.div className="profile-biography-action" initial={false} inert={editing} aria-hidden={editing}
                animate={{ opacity: editing ? 0 : 1, y: editing ? -3 : 0 }} transition={modeTransition}>
                <button ref={editButtonRef} type="button" className="community-button subtle profile-edit-button"
                  onClick={beginEditing}><Pencil size={13} />编辑资料</button>
              </motion.div>
              <motion.span id={`${bioId}-count`} className="profile-biography-count" initial={false}
                inert={!editing} aria-hidden={!editing} animate={{ opacity: editing ? 1 : 0, y: editing ? 0 : 3 }} transition={modeTransition}>
                {draft.bio.length} / 120
              </motion.span>
            </div>}
          </div>
          {canEdit && <motion.div className="profile-editor-expansion profile-background-expansion"
            inert={!editing} aria-hidden={!editing} initial={false}
            animate={{ height: editing ? 'auto' : 0 }} transition={heightTransition}>
            <motion.div className="profile-background-fields" initial={false}
              animate={{ opacity: editing ? 1 : 0, y: editing ? 0 : -4 }} transition={modeTransition}>
              <fieldset className="profile-backgrounds">
                <legend>资料背景</legend>
                {PROFILE_BACKGROUNDS.map(background => (
                  <label className="profile-background-choice" key={background.id}>
                    <input type="radio" name={`${bioId}-background`} value={background.id}
                      checked={draft.background === background.id}
                      onChange={() => setDraft({ ...draft, background: background.id })} />
                    <span className="profile-swatch" data-profile-theme={background.id}>
                      {draft.background === background.id && <Check size={14} />}
                    </span>
                    <span>{background.name}</span>
                  </label>
                ))}
              </fieldset>
              <fieldset className="profile-patterns">
                <legend>背景图案</legend>
                <div className="profile-pattern-options">
                  {PROFILE_PATTERNS.map(pattern => <label className="profile-pattern-choice" key={pattern.id}>
                    <input type="radio" name={`${bioId}-pattern`} value={pattern.id}
                      aria-label={`${pattern.category} · ${pattern.name}`}
                      checked={profilePatternId(draft.profilePattern) === pattern.id}
                      onChange={() => setDraft({ ...draft, profilePattern: pattern.id })} />
                    <span className="profile-pattern-preview"><ProfileBackdrop pattern={pattern.id} compact /></span>
                    <span className="profile-pattern-label">{pattern.name}</span>
                  </label>)}
                </div>
              </fieldset>
            </motion.div>
          </motion.div>}

          <section className="profile-curation" aria-label="精选收藏">
            <h4>精选收藏</h4>
            <div className="profile-curation-items">
              <ProfileModeRegion editing={editing && canEdit} reduced={reduced} className="profile-title-mode" readContent={
            <div className="profile-featured-title">
              {featuredTitle ? <>
                <span className={`profile-collection-emblem ${featuredTitle.style || ''}`}><Badge size={27} strokeWidth={1.55} /></span>
                <div><span className="community-caption">称号 · {featuredTitle.rarity}</span><strong className={`title-text ${featuredTitle.style || ''}`}>{featuredTitle.name}</strong><span className="community-caption profile-acquired-date">{collectionDate(featuredTitle.date)}</span></div>
              </> : <div className="profile-curation-empty">
                <span className="community-caption">精选称号</span>
                <strong>尚未展示称号</strong>
                {canEdit && <span className="community-caption">在编辑资料中选择</span>}
              </div>}
            </div>
              }>
                {canEdit && <>
              <fieldset className="profile-title-choices">
                <legend>精选称号</legend>
                <div className="profile-title-options">
                  <label className="profile-title-choice">
                    <input type="radio" name={`${bioId}-title`} value="" checked={featuredTitleId === null}
                      onChange={() => setDraft({ ...draft, featuredTitleId: null })} />
                    <span className="profile-title-choice-content"><strong>不展示</strong></span>
                  </label>
                  {PROFILE_TITLES.map(title => (
                    <label className="profile-title-choice" key={title.id}>
                      <input type="radio" name={`${bioId}-title`} value={title.id} checked={featuredTitleId === title.id}
                        onFocus={event => event.currentTarget.closest('label').scrollIntoView({ block: 'nearest', inline: 'nearest' })}
                        onChange={() => setDraft({ ...draft, featuredTitleId: title.id })} />
                      <span className="profile-title-choice-content">
                        <strong className={`title-text ${title.style}`}>{title.name}</strong>
                        <span>{collectionDate(title.date)} 获得</span>
                      </span>
                    </label>
                  ))}
                </div>
              </fieldset>
                </>}
              </ProfileModeRegion>
              <ProfileModeRegion editing={editing && canEdit && Boolean(onSaveFortune)} reduced={reduced} className="profile-fortune-mode" readContent={
            <div className="profile-featured-fortune">
              {featuredFortune ? <>
                <img src="/assets/fortune-slip.png" alt="" />
                <div><span className="community-caption">{featuredFortune.grade} · 第 {featuredFortune.id} 签</span><strong>{featuredFortune.title}</strong><p>{featuredFortune.poem?.[1]}</p><span className="community-caption profile-acquired-date">{collectionDate(featuredFortune.date)}</span></div>
              </> : <div className="profile-curation-empty">
                <span className="community-caption">精选签笺</span>
                <strong>尚未展示签笺</strong>
                {canEdit && <span className="community-caption">在编辑资料中选择</span>}
              </div>}
            </div>
              }>
                {canEdit && onSaveFortune && <>
              <fieldset className="profile-fortune-choices">
                <legend>精选签笺</legend>
                <div className="profile-fortune-options">
                  <label className="profile-fortune-choice">
                    <input type="radio" name={`${bioId}-fortune`} value="" checked={draftFortuneDate === null}
                      onChange={() => setDraftFortuneDate(null)} />
                    <span className="profile-fortune-choice-content"><strong>不展示</strong></span>
                  </label>
                  {availableFortunes.map(fortune => (
                    <label className="profile-fortune-choice" key={fortune.date}>
                      <input type="radio" name={`${bioId}-fortune`} value={fortune.date} checked={draftFortuneDate === fortune.date}
                        onFocus={event => event.currentTarget.closest('label').scrollIntoView({ block: 'nearest', inline: 'nearest' })}
                        onChange={() => setDraftFortuneDate(fortune.date)} />
                      <span className="profile-fortune-choice-content">
                        <span><strong>{fortune.title}</strong><small>{fortune.grade} · 第 {fortune.id} 签</small></span>
                        <span>{collectionDate(fortune.date)} 获得</span>
                      </span>
                    </label>
                  ))}
                </div>
                {availableFortunes.length === 0 && <p className="community-caption">还没有可展示的已归档签笺。</p>}
              </fieldset>
                </>}
              </ProfileModeRegion>
            </div>
          </section>

          {(memory || availableMemories.length > 0) && <ProfileModeRegion editing={editing && canEdit} reduced={reduced}
            className="profile-memory-mode" readContent={
        memory && <section className="profile-memory-section" aria-label="精选纪念">
          <div className="profile-memory-copy"><span className="community-caption">精选纪念</span><h4>{memory.name} <span>{memory.version}</span></h4><p>{memory.description}</p></div>
          <div className="profile-memory-period">
            <span>{memory.start} — {memory.end || '至今'}</span>
            <span>停留 <strong>{memory.hours}</strong> 小时</span>
          </div>
          {onOpenMemories && <button type="button" className="community-button profile-memory-open" onClick={() => onOpenMemories(profile, memory.id)}>
            翻开纪念<ArrowUpRight size={15} />
          </button>}
        </section>
          }>
            {canEdit && <>
              {availableMemories.length > 0 && <fieldset className="profile-memory-choices">
                <legend>精选纪念</legend>
                <div className="profile-memory-carousel">
                  {memoryScroll.overflow && <div className="profile-memory-controls" role="group" aria-label="浏览整合包纪念">
                    <button type="button" aria-label="上一页整合包" aria-controls={`${bioId}-memories`}
                      disabled={memoryScroll.atStart} onClick={() => scrollMemories(-1)}><ChevronLeft size={14} /></button>
                    <button type="button" aria-label="下一页整合包" aria-controls={`${bioId}-memories`}
                      disabled={memoryScroll.atEnd} onClick={() => scrollMemories(1)}><ChevronRight size={14} /></button>
                  </div>}
                  <div id={`${bioId}-memories`} className="profile-memory-options" ref={memoryTrackRef} onScroll={updateMemoryScroll}>
                    {availableMemories.map(item => (
                      <label className="profile-memory-choice" key={item.id}>
                        <input type="radio" name={`${bioId}-memory`} value={item.id}
                          checked={draft.featuredMemoryId === item.id}
                          onFocus={event => event.currentTarget.closest('label').scrollIntoView({ block: 'nearest', inline: 'nearest' })}
                          onChange={() => setDraft({ ...draft, featuredMemoryId: item.id })} />
                        <span className="profile-memory-choice-content">
                          <strong>{item.name}<small>{item.version}</small></strong>
                          <span>{item.start} — {item.end || '至今'}</span>
                        </span>
                      </label>
                    ))}
                  </div>
                </div>
              </fieldset>}
            </>}
          </ProfileModeRegion>}

          {canEdit && <motion.div className="profile-editor-expansion profile-save-expansion"
            inert={!editing} aria-hidden={!editing} initial={false}
            animate={{ height: editing ? 'auto' : 0 }} transition={heightTransition}>
            <motion.div className="profile-save-fields" initial={false}
              animate={{ opacity: editing ? 1 : 0 }} transition={modeTransition}>
              <div className="profile-editor-footer">
                <span>仅保存到此浏览器的 Web 演示</span>
                <button type="button" className="community-button subtle" data-profile-cancel disabled={saving} onClick={cancelEditing}>取消</button>
                <button type="submit" disabled={saving} className="community-button primary"><Check size={14} />保存</button>
              </div>
            </motion.div>
          </motion.div>}
        </form>

        <div className="profile-card-footer"><Feather size={12} /><span>公开展示的资料与收藏均为演示样例</span></div>
      </div>
    </article>
  );
}

export default PlayerProfileCard;
