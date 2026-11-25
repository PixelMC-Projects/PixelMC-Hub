import React, {useEffect, useState} from "react";
import { ChevronLeft, ChevronRight, Newspaper, Users, ArrowUpRight } from "lucide-react";
import { formatTime, publishedEntries, visiblePacks } from "./changelog/model.js";
import { COMMUNITY_PLAYERS, SELF_PROFILE } from "./community/demo.js";
import PlayerAvatar2D from "./changelog/PlayerAvatar2D.jsx";

export function StationPaper({children}) {
  return <div className="station-paper">{children}</div>;
}

const directoryEmblems = ["home", "titles", "fortunes", "updates", "leaderboards", "wenzhou"];

export function WinterNavigation({ items, intro, introReady = true }) {
  const [started, setStarted] = useState(false);
  useEffect(() => {
    if (!intro || !introReady) { setStarted(false); return; }
    // Paint the waiting state before the entrance clock starts, including after the splash.
    let frame = requestAnimationFrame(() => { frame = requestAnimationFrame(() => setStarted(true)); });
    return () => cancelAnimationFrame(frame);
  }, [intro, introReady]);
  const personal = ["home", "titles", "fortunes"];
  const groups = [items.filter(item => personal.includes(item.id)), items.filter(item => !personal.includes(item.id))];
  return <div className="winter-navigation" data-intro={intro ? (started && introReady ? "running" : "waiting") : undefined}>{groups.map((group, index) => group.length > 0 &&
    <div className="station-nav-group" key={index} role="group" aria-label={index === 0 ? "我的空间" : "服务器"}>
      <span className="station-nav-caption" aria-hidden="true">{index === 0 ? "我的空间" : "服务器"}</span>
      {group.map((item, row) => {
        const cell = directoryEmblems.indexOf(item.id);
        return <button type="button" key={item.id} aria-label={item.ariaLabel || item.label}
          style={{"--entry-order": (index === 0 ? 0 : groups[0].length) + row}}
          aria-current={item.current ? "page" : undefined} onClick={item.onClick}>
          {cell >= 0 ? <span className="station-nav-symbol" aria-hidden="true"
            style={{backgroundPosition:`${cell % 3 * 50}% ${Math.floor(cell / 3) * 100}%`}}/>
            : <span aria-hidden="true">{item.icon}</span>}
          <span className="station-nav-title">{item.label}</span>
        </button>;
      })}
    </div>
  )}</div>;
}

export function FoxStamp() {
  return <div className="fox-postage" aria-hidden="true">
    <div className="fox-stamp"><img src="/assets/winter/fox-stamp.webp" alt=""/></div>
  </div>;
}

const onlinePlayers = COMMUNITY_PLAYERS.filter(player => player.isOnline && !player.isSelf);
const playersPerPage = 4;

export function StationNews({changes, onOpenEntry, onOpenProfile}) {
  const [page, setPage] = useState(0);
  const pack = visiblePacks(changes).find(pack => pack.current);
  const entries = publishedEntries(changes, pack?.id)
    .sort((a,b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt)).slice(0,2);
  const offset = page * playersPerPage;
  const pages = Math.ceil(onlinePlayers.length / playersPerPage);
  return <div className="station-news">
    {changes.settings.updatesEnabled && <section className="station-bulletin" aria-label="最近更新">
      <header><h2><Newspaper size={17}/>最近更新</h2><button className="station-all" onClick={()=>onOpenEntry(null)}>全部更新<ArrowUpRight size={13}/></button></header>
      {entries.length ? entries.map(entry =>
        <button className="station-article" key={entry.id} onClick={() => onOpenEntry({id:entry.id, packId:entry.packId})}>
          <span className="station-article-copy">
            <span className="station-article-title"><strong>{entry.title}</strong>
              <time dateTime={entry.publishedAt}>{formatTime(entry.publishedAt,true).slice(5)}</time></span>
            {entry.summary && <small>{entry.summary}</small>}
            <span className="station-article-meta">{entry.category}{entry.updateVersion && <span> · v{entry.updateVersion}</span>}</span>
          </span><ChevronRight size={14}/>
        </button>
      ) : <p>当前存档暂无公开更新</p>}
    </section>}
    <section className="station-company" aria-label="在线玩家">
      <header><h2><Users size={17}/>在线玩家 <small>{onlinePlayers.length}</small></h2>
        {pages > 1 && <div className="station-pager">
          <button aria-label="上一页在线玩家" disabled={page===0} onClick={()=>setPage(page-1)}><ChevronLeft size={14}/></button>
          <span>{page+1} / {pages}</span>
          <button aria-label="下一页在线玩家" disabled={page+1>=pages} onClick={()=>setPage(page+1)}><ChevronRight size={14}/></button>
        </div>}
      </header>
      {onlinePlayers.length ? <div className="station-roster">
        {onlinePlayers.slice(offset,offset+playersPerPage).map(player =>
          <button key={player.playerId} aria-label={`查看 ${player.name} 的公开资料`} title={player.name} onClick={()=>onOpenProfile(player)}>
            <span className="station-peer-avatar" aria-hidden="true">
              <PlayerAvatar2D name={player.name} skin={player.skin ?? null}/>
            </span>
            <span className="station-player-copy"><strong>{player.name}</strong><small>共同存档 · {player.memoryIds.filter(id=>SELF_PROFILE.memoryIds.includes(id)).length} 个</small></span>
          </button>
        )}
      </div> : <p>暂时没有其他玩家在线</p>}
    </section>
  </div>;
}
