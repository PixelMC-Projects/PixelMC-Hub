import React, { useEffect, useMemo, useRef, useState } from 'react';
import { animate, AnimatePresence, motion, useReducedMotion } from 'motion/react';
import { ArrowDown, ArrowUp, BookOpen, Clock3, LoaderCircle, RefreshCw, Trophy, Utensils, WifiOff } from 'lucide-react';
import GlassSurface from '../vendor/react-bits/GlassSurface.jsx';
import TiltedCard from '../vendor/react-bits/TiltedCard.jsx';
import AnimatedList from '../vendor/react-bits/AnimatedList.jsx';
import PlayerHead from '../PlayerHead.jsx';
import PlayerAvatar2D from '../changelog/PlayerAvatar2D.jsx';
import { useLeaderboards, boardDefinitions } from './store.js';
import './leaderboards.css';

const icons = { food: Utensils, fortunes: BookOpen, online: Clock3 };
const number = new Intl.NumberFormat('zh-CN');
const compactNumber = new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 });
const scoreNumber = value => value >= 1_000_000 ? compactNumber.format(value) : number.format(value);
const skinFor = row => row?.name === 'zhan_cpu' ? import.meta.env.VITE_PREVIEW_SKIN : null;

export function formatScore(boardId, score) {
  if (!Number.isFinite(score)) return { value: '—', unit: '' };
  if (boardId === 'online') {
    const minutes = Math.floor(Math.max(0, score) / 60000);
    return { value: scoreNumber(Math.floor(minutes / 60)), unit: '小时', detail: `${minutes % 60} 分钟` };
  }
  return { value: scoreNumber(score), unit: '种' };
}

function Score({ boardId, score, small = false, reduceMotion = false }) {
  const [displayed, setDisplayed] = useState(score), current = useRef(score);
  useEffect(() => {
    if (reduceMotion || !Number.isFinite(score) || !Number.isFinite(current.current)) {
      current.current = score; setDisplayed(score); return;
    }
    if (current.current === score) return;
    const playback = animate(current.current, score, {
      duration: .46, ease: [.22, 1, .36, 1],
      onUpdate: value => { current.current = value; setDisplayed(Math.round(value)); },
      onComplete: () => { current.current = score; setDisplayed(score); },
    });
    return () => playback.stop();
  }, [score, reduceMotion]);
  const value = formatScore(boardId, displayed);
  const exact = Number.isFinite(score) ? boardId === 'online' ? `${number.format(Math.floor(score / 60000))} 分钟` : `${number.format(score)} 种` : '暂无记录';
  return <span className={`lb-score ${small ? 'is-small' : ''}`} aria-label={exact}><strong>{value.value}</strong><span>{value.unit}</span>{value.detail && <small>{value.detail}</small>}</span>;
}

function RelativeScore({ score, leaderScore }) {
  const available = Number.isFinite(leaderScore) && leaderScore > 0 && Number.isFinite(score) && score >= 0;
  const percentage = available ? Math.min(100, score / leaderScore * 100) : 0;
  return <span className={`lb-row-meter ${available ? '' : 'is-unavailable'}`} aria-hidden="true"><span style={{ width: `${percentage}%` }}/></span>;
}

function RankMovement({ row, comparisonRevision }) {
  if (!comparisonRevision || !Number.isSafeInteger(row?.rankDelta) || row.rankDelta === 0) return null;
  const up = row.rankDelta > 0, Icon = up ? ArrowUp : ArrowDown;
  return <span className={`lb-rank-movement ${up ? 'is-up' : 'is-down'}`} aria-label={`${up ? '上升' : '下降'} ${Math.abs(row.rankDelta)} 位`}><Icon size={10}/><span>{Math.abs(row.rankDelta)}</span></span>;
}

function RankPosition({ row, tied, comparisonRevision, variant, reduceMotion }) {
  const label = row?.rank ? `${tied ? '并列第' : '第'}${row.rank}名` : '未入榜';
  const showMovement = variant === 'list' && comparisonRevision && Number.isSafeInteger(row?.rankDelta) && row.rankDelta !== 0;
  const legacyClass = variant === 'list' ? 'lb-row-rank' : variant === 'self' ? 'lb-self-rank' : 'lb-podium-place';
  return <span className={`lb-rank-position lb-rank-position-${variant} ${legacyClass} ${tied ? 'is-tied' : ''} ${showMovement ? 'has-movement' : ''}`}>
    <span className="lb-rank-value" aria-label={label} title={label}>{row?.rank ? <>{variant !== 'list' && <small>{tied ? '并列第' : '第'}</small>}<AnimatePresence initial={false} mode="popLayout"><motion.strong key={row.rank} initial={{opacity:0,y:reduceMotion ? 0 : 4}} animate={{opacity:1,y:0}} exit={{opacity:0,y:reduceMotion ? 0 : -4}} transition={{duration:reduceMotion ? 0 : .22}}>{row.rank}</motion.strong></AnimatePresence>{variant !== 'list' && <small>名</small>}</> : <small>未入榜</small>}</span>
    {variant === 'list' && tied && <small className="lb-rank-tie">并列</small>}
    {showMovement && <RankMovement row={row} comparisonRevision={comparisonRevision}/>}
  </span>;
}

function Podium({ rows, boardId, comparisonRevision, tieRanks, reduceMotion, revision, onOpenProfile }) {
  const arranged = rows.length === 3 ? [1, 0, 2] : rows.map((_, i) => i);
  return <section className="lb-podium" aria-label="榜单前三位">
    <AnimatePresence initial={false} mode="popLayout">{arranged.map(index => {
      const row = rows[index], first = index === 0, tied = tieRanks.has(row.rank), rankLabel = `${tied ? '并列第' : '第'}${row.rank}名`;
      return <motion.article layout={reduceMotion ? false : "position"} layoutDependency={revision} initial={{opacity:0,y:reduceMotion ? 0 : 8}} animate={{opacity:1,y:0}} exit={{opacity:0,y:reduceMotion ? 0 : -8}} transition={{duration:reduceMotion ? 0 : .32,layout:{duration:reduceMotion ? 0 : .42,ease:[.22,1,.36,1]}}} key={row.playerId} data-player-id={row.playerId} tabIndex={-1} aria-label={`${row.name}，${rankLabel}`} className={`lb-podium-slot rank-${row.rank} ${first ? 'is-leading' : ''}`}>
        <TiltedCard containerHeight="100%" imageHeight="100%" containerWidth="100%" imageWidth="100%"
          rotateAmplitude={reduceMotion ? 0 : 3} scaleOnHover={reduceMotion ? 1 : 1.015} glare={.42} shadow={0}
          showTooltip={false} gyroscope={false} intro={false} radius={19} reducedMotion={reduceMotion} className="lb-podium-tilt">
          <div className="lb-podium-content">
            <div className="lb-podium-scene">
              <span className="lb-podium-aura" aria-hidden="true"/>
              <button className="lb-podium-avatar lb-profile-link" aria-label={`查看${row.name}的公开资料`} onClick={() => onOpenProfile?.(row)}><div className="avatar"><PlayerHead name={row.name} skin={skinFor(row)}/></div></button>
            </div>
            <GlassSurface width="100%" height="auto" borderRadius={17} brightness={72} opacity={.65} blur={8}
              backgroundOpacity={.13} saturation={1.15} distortionScale={-15} refraction={false} className="lb-podium-glass">
              <RankPosition row={row} tied={tied} comparisonRevision={comparisonRevision} variant="podium" reduceMotion={reduceMotion}/>
              <div className="lb-podium-copy"><strong title={row.name}><button className="lb-profile-name" onClick={() => onOpenProfile?.(row)}>{row.name}</button>{row.isSelf && <em>我</em>}</strong><div className="lb-podium-scoreline"><Score boardId={boardId} score={row.score} reduceMotion={reduceMotion}/></div></div>
            </GlassSurface>
          </div>
        </TiltedCard>
      </motion.article>;
    })}</AnimatePresence>
  </section>;
}

function RankingList({ snapshot, boardId, tieRanks, reduceMotion, listRef, loadMore, onOpenProfile }) {
  const rows = useMemo(() => snapshot.rows.slice(3).map(row => ({...row, id:row.playerId})), [snapshot.rows]);
  const [selectedPlayer, setSelectedPlayer] = useState(null);
  const selected = rows.findIndex(row => row.playerId === selectedPlayer);
  const leaderScore = snapshot.rows.find(row => row.rank === 1)?.score;
  useEffect(() => {
    const viewport = listRef.current?.querySelector('[role="listbox"]');
    if (!viewport) return;
    viewport.setAttribute('aria-label', '玩家排名列表');
    const scroll = () => { if (viewport.scrollTop + viewport.clientHeight >= viewport.scrollHeight - 100) loadMore(); };
    viewport.addEventListener('scroll', scroll, { passive: true });
    return () => viewport.removeEventListener('scroll', scroll);
  }, [snapshot.rows, listRef, loadMore]);
  return <section className="lb-ranking-frame" aria-label="完整排名" ref={listRef}>
    <div className="lb-table-heading"><span>排名</span><span>玩家</span><span aria-hidden="true"/><span>{boardDefinitions.find(b => b.id === boardId)?.metricLabel}</span></div>
    {rows.length ? <AnimatedList className="lb-animated-list" items={rows} onItemSelect={row => setSelectedPlayer(row.playerId)}
      selectedIndex={selected} selectOnHover={false} animation={reduceMotion ? 'none' : 'fade'} animateOnce stagger={.025}
      frame={false} theme="light" accentColor="#a494d2" gap={2} radius={10} width="100%" maxHeight="100%" showGradients={false}
      renderItem={row => <motion.div layout={reduceMotion ? false : "position"} layoutDependency={snapshot.revision} initial={false} transition={{layout:{duration:reduceMotion ? 0 : .42,ease:[.22,1,.36,1]}}} className={`lb-rank-row ${row.isSelf ? 'is-self' : ''}`} data-player-id={row.playerId}>
        <RankPosition row={row} tied={tieRanks.has(row.rank)} comparisonRevision={snapshot.comparisonRevision} variant="list" reduceMotion={reduceMotion}/><button className="lb-row-player lb-profile-link" aria-label={`查看${row.name}的公开资料`} onClick={event => {event.stopPropagation();onOpenProfile?.(row);}}><PlayerAvatar2D name={row.name} skin={skinFor(row)}/><strong title={row.name}>{row.name}</strong>{row.isSelf && <em>我</em>}</button><RelativeScore score={row.score} leaderScore={leaderScore}/><div className="lb-row-result"><Score boardId={boardId} score={row.score} small reduceMotion={reduceMotion}/></div>
      </motion.div>}/>
      : <div className="lb-rest-empty">本榜已有 {snapshot.rows.length} 位旅人留下记录</div>}
  </section>;
}

function SelfPosition({ snapshot, boardId, tieRanks, locate, reduceMotion, onOpenProfile }) {
  const row = snapshot.self;
  return <div className={`lb-self-position ${row?.rank ? '' : 'is-unranked'}`}>
    <span className="lb-self-label">我的位置</span><RankPosition row={row} tied={tieRanks.has(row?.rank)} comparisonRevision={snapshot.comparisonRevision} variant="self" reduceMotion={reduceMotion}/>
    <button className="lb-self-player lb-profile-link" aria-label="查看我的公开资料" onClick={() => onOpenProfile?.(row || snapshot.viewer)}><PlayerAvatar2D name={row?.name || 'zhan_cpu'} skin={skinFor(row)}/><strong title={row?.name}>{row?.name || 'zhan_cpu'}</strong></button>
    {row && <Score boardId={boardId} score={row.score} small reduceMotion={reduceMotion}/>}
    {row?.rank ? <button onClick={locate} aria-label="定位我的排名"><span>定位</span><ArrowDown size={13}/></button> : <span className="lb-self-hint">{boardId === 'food' ? '品尝食物后，留下你的记录' : boardId === 'fortunes' ? '收藏签笺后，留下你的记录' : '下次更新后再来看看'}</span>}
  </div>;
}

function BoardState({ status, reason, retry }) {
  const loading = status === 'loading';
  const content = {
    loading: ['正在读取排行', '稍等片刻，旅途的记录马上就到。'],
    missing: ['这个存档还未开启此排行', reason || '对应模块安装并开启后，就能看到大家的记录。'],
    unavailable: ['暂时无法读取排行', reason || '连接恢复后，可以重新试试。'],
    empty: ['榜上还没有记录', reason || '第一份记录，会从一位旅人开始。'],
    disabled: ['此排行暂未开放', reason || '开放后，大家的记录会在这里相见。'],
  }[status] || ['暂时无法读取排行', reason || '请稍后再试。'];
  const Icon = loading ? LoaderCircle : status === 'unavailable' ? WifiOff : Trophy;
  return <div className={`lb-state lb-state-${status}`} role="status"><span className="lb-state-icon"><Icon size={29}/></span><h2>{content[0]}</h2><p>{content[1]}</p>{status === 'unavailable' && <button className="btn subtle" onClick={retry}><RefreshCw size={13}/>重新读取</button>}</div>;
}

export default function LeaderboardPage({ notify, reduceMotion: reduceMotionSetting = false, onOpenProfile }) {
  const systemReducedMotion = useReducedMotion(), reduceMotion = reduceMotionSetting || systemReducedMotion;
  const [boardId, setBoardId] = useState('food'), [pendingLocate, setPendingLocate] = useState(false), listRef = useRef(null), tabRefs = useRef([]);
  const { snapshot, refreshing, refreshFailed, retry, loadMore, revealSelf } = useLeaderboards(boardId);
  // Only mark ties already proven by loaded rows. The future provider can supply cross-page tie metadata.
  const tieRanks = useMemo(() => { const counts = new Map(); snapshot.rows.forEach(row => counts.set(row.rank, (counts.get(row.rank) || 0) + 1)); return new Set([...counts].filter(([, count]) => count > 1).map(([rank]) => rank)); }, [snapshot.rows]);
  const board = boardDefinitions.find(b => b.id === boardId);
  const changeBoard = id => { if (id !== boardId) setBoardId(id); };
  const tabKey = (event, index) => {
    let next;
    if (event.key === 'ArrowRight') next = (index + 1) % boardDefinitions.length;
    if (event.key === 'ArrowLeft') next = (index + boardDefinitions.length - 1) % boardDefinitions.length;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = boardDefinitions.length - 1;
    if (next === undefined) return;
    event.preventDefault(); changeBoard(boardDefinitions[next].id); tabRefs.current[next]?.focus();
  };
  const locate = () => {
    const index = snapshot.rows.findIndex(row => row.playerId === snapshot.self?.playerId);
    if (index < 0) { revealSelf(); setPendingLocate(true); return; }
    if (index < 3) {
      const target = [...(listRef.current?.previousElementSibling?.querySelectorAll('[data-player-id]') || [])].find(row => row.dataset.playerId === snapshot.self?.playerId);
      target?.scrollIntoView({block:'nearest', behavior:reduceMotion ? 'instant' : 'smooth'}); target?.focus({preventScroll:true});
      if (target) { target.classList.remove('lb-podium-located'); void target.offsetWidth; target.classList.add('lb-podium-located'); }
      notify(snapshot.self?.rank === 1 ? '你位于榜首' : `你位于第 ${snapshot.self?.rank} 名`); return;
    }
    const target = [...(listRef.current?.querySelectorAll('[data-player-id]') || [])].find(row => row.dataset.playerId === snapshot.self?.playerId);
    target?.scrollIntoView({ block:'center', behavior:reduceMotion ? 'instant' : 'smooth' });
    if (target) { target.classList.remove('lb-located'); void target.offsetWidth; target.classList.add('lb-located'); }
  };
  useEffect(() => { if (pendingLocate && snapshot.rows.some(row => row.playerId === snapshot.self?.playerId)) { setPendingLocate(false); requestAnimationFrame(locate); } }, [pendingLocate, snapshot.rows]);
  const ready = snapshot.status === 'ready';
  return <>
    <div className="page-heading lb-heading"><div><h1>玩家排行</h1><p>{board.description}</p></div></div>
    <div className="lb-toolbar"><div className="lb-tabs" role="tablist" aria-label="选择排行榜">{boardDefinitions.map((item, index) => {
      const TabIcon = icons[item.id];
      return <button ref={node => {tabRefs.current[index] = node;}} id={`lb-tab-${item.id}`} aria-controls={`lb-panel-${item.id}`} role="tab" aria-selected={boardId === item.id} tabIndex={boardId === item.id ? 0 : -1} key={item.id} onClick={() => changeBoard(item.id)} onKeyDown={event => tabKey(event,index)} className={boardId === item.id ? 'is-active' : ''}><TabIcon size={15}/>{item.label}</button>;
    })}</div><div className="lb-toolbar-meta">{refreshFailed && <button className="lb-refresh-error" onClick={retry}>读取失败，重试</button>}{refreshing && <LoaderCircle size={12} className="lb-refreshing" aria-label="正在更新"/>}<div className="lb-scope"><span/>{board.scope === 'global' ? '全局收藏' : '当前存档'}</div></div></div>
    <AnimatePresence mode="wait" initial={false}>
      <motion.div className={`lb-board board-${boardId}`} id={`lb-panel-${boardId}`} role="tabpanel" aria-labelledby={`lb-tab-${boardId}`} key={boardId}
        initial={reduceMotion ? false : {opacity:0,y:9}} animate={{opacity:1,y:0}} exit={{opacity:0,y:reduceMotion ? 0 : -6}} transition={{duration:reduceMotion ? 0 : .18}}>
        {ready ? <><Podium onOpenProfile={onOpenProfile} rows={snapshot.rows.slice(0,3)} revision={snapshot.revision} boardId={boardId} comparisonRevision={snapshot.comparisonRevision} tieRanks={tieRanks} reduceMotion={reduceMotion}/>
          <RankingList onOpenProfile={onOpenProfile} snapshot={snapshot} boardId={boardId} tieRanks={tieRanks} reduceMotion={reduceMotion} listRef={listRef} loadMore={loadMore}/>
          <SelfPosition onOpenProfile={onOpenProfile} snapshot={snapshot} boardId={boardId} tieRanks={tieRanks} locate={locate} reduceMotion={reduceMotion}/>
        </> : <BoardState status={snapshot.status} reason={snapshot.reason} retry={retry}/>}
      </motion.div>
    </AnimatePresence>
  </>;
}
