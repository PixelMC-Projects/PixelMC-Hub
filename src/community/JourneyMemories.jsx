// SPDX-License-Identifier: Apache-2.0
import { useEffect, useId, useRef, useState } from 'react';
import { ArrowLeft, ArrowUpRight, Check, MapPin } from 'lucide-react';
import { MEMORY_ARCHIVES, SELF_PROFILE } from './demo.js';
import MemoryNote from './MemoryNote.jsx';
import MemoryCollections from './MemoryCollections.jsx';
import './community.css';
import MemoryCover from './MemoryCover.jsx';

export function JourneyMemories({ player = SELF_PROFILE, initialArchiveId, onBack, onOpenProfile, reduceMotion = false, notify }) {
  const archives = MEMORY_ARCHIVES.filter(item => player.memoryIds?.includes(item.id));
  const [selectedId, setSelectedId] = useState(() => initialArchiveId || archives[0]?.id);
  const detailRef = useRef(null);
  const detailId = useId();
  const selected = archives.find(item => item.id === selectedId) || archives[0];

  useEffect(() => { setSelectedId(initialArchiveId || player.memoryIds?.[0]); }, [player.playerId, initialArchiveId]);
  useEffect(() => { detailRef.current?.scrollTo({ top: 0 }); }, [selected?.id]);

  return (
    <div className={`community memories-page ${reduceMotion ? 'community-reduced' : ''}`}>
      <div className="page-heading memories-heading">
        <div className="memories-heading-main">
          {onBack && <button type="button" className="community-back" aria-label="返回个人主页" onClick={onBack}><ArrowLeft size={20} /></button>}
          <div><h1>整合包纪念</h1><p>{player.isSelf ? '我的旅途' : `${player.name} 的旅途`} · Web 演示</p></div>
        </div>
        {onOpenProfile && <button type="button" className="community-button subtle" onClick={() => onOpenProfile(player)}>查看资料<ArrowUpRight size={14} /></button>}
      </div>

      <div className="memories-layout">
        <nav className="memories-archive-list" aria-label="选择整合包纪念">
          {archives.map(archive => (
            <button key={archive.id} type="button" className={`memory-archive-option ${selected?.id === archive.id ? 'is-selected' : ''}`}
              data-profile-theme={archive.theme} aria-current={selected?.id === archive.id ? 'true' : undefined}
              aria-controls={detailId} onClick={() => setSelectedId(archive.id)}>
              <MemoryCover archive={archive} compact><span>{archive.status}</span>{selected?.id === archive.id && <Check size={14} />}</MemoryCover>
              <div className="memory-archive-label"><strong>{archive.name}<small>{archive.version}</small></strong><span>{archive.start} — {archive.end || '至今'}</span></div>
            </button>
          ))}
        </nav>

        <section className="memory-reader-frame" id={detailId} aria-label={selected ? `${selected.name}纪念详情` : '纪念详情'}>
          {selected ? <div ref={detailRef} className="memory-reader-scroll">
            <article key={selected.id} className="memory-detail" data-profile-theme={selected.theme}>
              <MemoryCover archive={selected}>
                <div className="memory-detail-intro"><span className="community-caption">{selected.status}</span><h2>{selected.name}<span>{selected.version}</span></h2><p>{selected.description}</p><span className="memory-detail-dates">{selected.start} — {selected.end || '至今'}</span></div>
              </MemoryCover>

              <div className="memory-detail-body">
                <dl className="memory-stats"><div><dt>在线时长</dt><dd>{selected.hours}<span>小时</span></dd></div><div><dt>登录次数</dt><dd>{selected.visits}<span>次</span></dd></div><div><dt>留下收藏</dt><dd>{selected.collections}<span>件</span></dd></div></dl>

                <MemoryNote key={`note:${player.playerId}/${selected.id}`} player={player} archive={selected} reduceMotion={reduceMotion} notify={notify} />

                <MemoryCollections key={`collections:${player.playerId}/${selected.id}`} player={player} archive={selected} reduceMotion={reduceMotion} notify={notify} />

                <div className="memory-milestone"><MapPin size={14} /><time>{selected.moment}</time><span>{selected.momentText}</span></div>
                <p className="memory-demo-note">此页展示虚构整合包归档与回忆样例</p>
              </div>
            </article>
          </div> : <p className="memory-empty">这位旅人还没有公开的整合包纪念。</p>}
        </section>
      </div>
    </div>
  );
}

export default JourneyMemories;
