import React, { useLayoutEffect, useRef } from "react";
import { BookmarkCheck, Check, ChevronLeft, ChevronRight, EyeOff, Lock, Tag } from "lucide-react";
import { player, titles } from "./data.js";
import { titleIcons } from "./title-icons.js";
import PlayerHead from "./PlayerHead.jsx";

// Sprite cell and optical offset, measured from the opaque artwork in each cell.
const pinCells = {
  dew: [0, -3.26, -1.09], star: [1, -1.09, -0.27], wood: [2, 1.63, -1.36], moon: [3, 3.26, -1.90],
  harvest: [4, -0.82, 0.54], mountain: [5, 1.09, 0], dawn: [6, 1.90, 1.36], cloud: [7, 2.45, 0],
};

function atlasPosition(cell) {
  return `${cell % 4 * 100 / 3}% ${Math.floor(cell / 4) * 100}%`;
}

export function WinterTitlePin({ title }) {
  const sprite = Object.hasOwn(pinCells, title.style) && pinCells[title.style];
  if (!sprite) {
    const Icon = titleIcons[title.icon] || Tag;
    return <span className="case-pin case-pin-fallback" aria-hidden="true"><Icon /></span>;
  }
  const [cell, offsetX, offsetY] = sprite;
  return <span aria-hidden="true" className="case-pin"><span className="case-pin-art" style={{
    backgroundPosition: atlasPosition(cell),
    translate: `${offsetX}% ${offsetY}%`,
  }} /></span>;
}

function AcquisitionTicket({ title }) {
  const date = title.owned && title.date;
  const parts = date && /^(\d{4})[/-](\d{2})[/-](\d{2})$/.exec(date);
  return <div className="album-acquisition">
    <div className="album-ticket-copy">
      <h3>{title.owned ? "获得方式" : "解锁条件"}</h3>
      <p className="case-condition">{title.condition}</p>
    </div>
    {date ? <div className="album-ticket-stub">
      <time dateTime={date.replaceAll("/", "-")}>
        {parts ? <><small>{parts[1]}</small><strong>{parts[2]}.{parts[3]}</strong></> : date}
      </time>
      <span>入藏</span>
    </div> : !title.owned && <div className="album-ticket-stub"><Lock size={16}/><span>未解锁</span></div>}
  </div>;
}

const pageSize = 6;

export default function WinterTitleCase({ title, items, onSelect, equipped, visible, toolbar, listKey, reduceMotion,
  emptyState, equipButton, visibility, titleText }) {
  const page = Math.floor(Math.max(0, items.indexOf(title)) / pageSize);
  const pages = Math.ceil(items.length / pageSize);
  const start = page * pageSize;
  const tray = useRef(null);
  const previousView = useRef(null);
  useLayoutEffect(() => {
    const previous = previousView.current;
    previousView.current = { listKey, page };
    if (!previous || !tray.current || reduceMotion) return;
    let from;
    if (previous.listKey !== listKey) {
      from = { opacity:.45 };
    } else if (previous.page !== page) {
      from = { opacity:.55, transform:`translateX(${page > previous.page ? 8 : -8}px)` };
    } else {
      return;
    }
    const motion = tray.current.animate([from, { opacity:1, transform:"none" }], { duration:200, easing:"ease-out" });
    return () => motion.cancel();
  }, [listKey, page, reduceMotion]);
  function turnPage(step) {
    const next = items[(page + step) * pageSize];
    if (next) {
      onSelect(next.id);
    }
  }
  const wearing = title && equipped === title.id;
  const etching = title && Object.hasOwn(pinCells, title.style) ? pinCells[title.style][0] : 5;
  const StatusIcon = wearing ? visible ? Check : EyeOff : Tag;
  return <section className="winter-title-case" aria-label="我的称号">
    <header className="case-heading">
      <h1>我的称号</h1>
      <span>已收藏 <b>{titles.filter(item => item.owned).length}</b> / {titles.length}</span>
    </header>
    <div className="album-binding">
      <div className="album-cover">
        <div className="case-lid">
          <div className="album-inscription">
            <div className="case-player"><PlayerHead name={player.name}/></div>
            <div className="album-identity">
              <div className="album-signature">{player.name}</div>
              {title && <strong key={title.id} className="album-preview-title">{titleText}</strong>}
              <span className="album-preview-label" key={`${title?.id}-${wearing}-${visible}`}>
                <StatusIcon size={13} aria-hidden="true"/>
                {title ? wearing ? visible ? "正在佩戴" : "已佩戴 · 已隐藏" : "试戴预览" : "称号收藏匣"}
              </span>
            </div>
          </div>
        </div>
      </div>
      <div className="case-bar" role="group" aria-label="筛选与显示">{toolbar}</div>
      <div className="album-book">
        {title ? <div className="album-spread">
          <section className="album-display" aria-label="称号陈列">
            <div className="case-pins" ref={tray}>
              {items.slice(start, start + pageSize).map(item => <button key={item.id} type="button"
                className={`case-slot${item.owned ? "" : " is-locked"}`}
                aria-pressed={title.id === item.id}
                aria-label={item.name + (equipped === item.id ? "，已佩戴" : item.owned ? "" : "，未获得")}
                onClick={() => onSelect(item.id)}>
                <span className="album-pin-mount"><WinterTitlePin title={item}/></span>
                <span className="case-caption">
                  <strong className="case-title">{item.name}</strong>
                  <small>{item.category} · {item.rarity}</small>
                  {equipped === item.id ? <em><Check size={12}/>已佩戴</em>
                    : !item.owned ? <em><Lock size={11}/>未获得</em> : null}
                </span>
              </button>)}
            </div>
            <nav className="album-index" aria-label="称号翻页">
              <span>{start + 1}–{Math.min(start + pageSize, items.length)} / {items.length} 枚</span>
              {pages > 1 && <div>
                <button type="button" aria-label="上一页称号" disabled={page === 0} onClick={() => turnPage(-1)}><ChevronLeft size={16}/></button>
                <span aria-live="polite">第 {page + 1} / {pages} 页</span>
                <button type="button" aria-label="下一页称号" disabled={page + 1 >= pages} onClick={() => turnPage(1)}><ChevronRight size={16}/></button>
              </div>}
            </nav>
          </section>
          <article className="case-record" aria-label="称号详情">
            <div className="album-story" key={title.id}>
              <div className="album-entry-heading">
                <div className="album-entry-name">
                  <div className="album-provenance"><BookmarkCheck size={14}/>{title.category}<i>·</i>{title.rarity}</div>
                  <h2>{title.name}</h2>
                </div>
                <span className="album-entry-pin"><WinterTitlePin title={title}/></span>
              </div>
              <p className="album-description">{title.description}</p>
              <AcquisitionTicket title={title}/>
            </div>
            <div className="album-endpiece">
              <div className="album-settings">
                <div className="case-action">{equipButton}</div>
                {visibility}
              </div>
              <span key={title.id} className="album-etching" aria-hidden="true" style={{maskPosition:atlasPosition(etching)}} />
            </div>
          </article>
        </div> : emptyState}
      </div>
    </div>
    <div className="album-foot-rule" aria-hidden="true"><i/></div>
  </section>;
}
