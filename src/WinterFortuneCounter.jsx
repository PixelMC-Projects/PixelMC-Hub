import React from "react";
import { ArrowRight, Bookmark, CalendarDays, Check, ChevronLeft, ChevronRight, Inbox, List, ScrollText } from "lucide-react";
import { gradeColor, player, todayKey } from "./data.js";
import "./winter-fortune-counter.css";

const monthName = new Intl.DateTimeFormat("zh-CN-u-nu-hanidec", { month: "long" });

function DateGrid({ dates, selected, onSelect }) {
  return <div className="counter-grid" style={{ "--week-count": dates.length / 7 }}>
    {"日一二三四五六".split("").map(day => <span key={day} className="counter-weekday">{day}</span>)}
    {dates.map((entry, i) => {
      if (!entry) return <span key={i} className="counter-blank" aria-hidden="true" />;
      const { key, day, f: fortune, future } = entry;
      return <button key={key} className="counter-day" data-stored={fortune ? "" : undefined}
        style={fortune ? { "--seal-color": gradeColor(fortune.grade) } : undefined}
        disabled={future} title={fortune ? fortune.title + " · " + fortune.grade : undefined}
        aria-label={key + (fortune ? " " + fortune.grade + " " + fortune.title : future ? " 尚未到来" : " 未收藏")}
        aria-current={key === todayKey ? "date" : undefined} aria-pressed={key === selected} onClick={() => onSelect(key)}>
        <span>{day}</span>{fortune && <b className="counter-day-entry" aria-hidden="true">{fortune.title}</b>}
        {key === todayKey && <small>今</small>}
      </button>;
    })}
  </div>;
}

function SlipList({ stored, selected, onSelect }) {
  if (!stored.length) return <div className="counter-empty-list">
    <Inbox size={26} /><strong>这个月还没有签笺</strong><span>已收藏的签会按抽签日期出现在这里。</span>
  </div>;
  return <div className="counter-list" role="group" aria-label="本月收藏的签笺">
    {stored.toSorted((a, b) => b.date.localeCompare(a.date)).map(fortune =>
      <button key={fortune.date} aria-pressed={fortune.date === selected} onClick={() => onSelect(fortune.date)}
        style={{ "--seal-color": gradeColor(fortune.grade) }}>
        <time dateTime={fortune.date}>{fortune.date.slice(5).replace("-", " / ")}</time>
        <strong>{fortune.title}</strong><span>{fortune.grade}</span><ChevronRight size={15} />
      </button>)}
  </div>;
}

export function JournalSlip({ fortune, selected }) {
  return <div className="counter-mount">
    <article className="counter-letter" key={selected} aria-label="签文" data-empty={!fortune || undefined}>
      <header className="counter-imprint">
        <span>{fortune ? fortune.grade : "未收藏"}</span>
        {fortune && <small>第 {fortune.id} 签</small>}
      </header>
      {fortune ? <>
        <div className="counter-script">
          <h2>{fortune.title}</h2>
          <div className="counter-verse">{fortune.poem.map(line => <p key={line}>{line}</p>)}</div>
        </div>
        <footer><span>{player.name} 所得</span><time>{fortune.time}</time>{fortune.status === "archived" && <b aria-label="已入藏">藏</b>}</footer>
      </> : <div className="counter-no-slip">
        <ScrollText size={28} strokeWidth={1.25} /><h2>这一天未留签</h2>
        <p>在游戏中打开藏签匣，可将已解的签按日期归档。</p>
      </div>}
    </article>
  </div>;
}

export default function WinterFortuneCounter({ month, dates, stored, total, selected, fortune, display, mode,
  monthDirection, atCurrentMonth, onMonth, onToday, onMode, onSelect, onRead, onDisplay }) {
  const calendar = mode === "calendar";
  const displayed = Boolean(fortune && display?.date === selected);
  return <>
    <header className="case-heading counter-page-heading">
      <h1 id="counter-title">我的签笺</h1><span>已收藏 <b>{total}</b> 张</span>
    </header>
    <section className="fortune-counter" aria-labelledby="counter-title">
      <header className="counter-scene">
        <div className="counter-scene-copy">
          <span className="counter-feature-label"><Bookmark size={14} />手册展示</span>
          {display ? <>
            <button className="counter-feature" aria-label={"阅读手册展示的签笺：" + display.title} onClick={() => onRead(display)}>
              {display.title}<ChevronRight size={18} />
            </button>
            <div className="counter-feature-meta"><b>{display.grade}</b><time dateTime={display.date}>{display.date.replaceAll("-", " / ")}</time></div>
          </> : <>
            <strong className="counter-feature-empty">尚未选择展示签笺</strong>
            <span className="counter-feature-meta">{total ? "在下方选中一张收藏，设为展示。" : "收藏签笺后，可在这里展示。"}</span>
          </>}
        </div>
      </header>
      <div className="counter-leafbar">
        <div className="counter-index" role="group" aria-label="翻阅签笺">
          <div className="counter-modes">
            <button aria-label="切换月历显示" aria-pressed={calendar} onClick={() => onMode("calendar")}><CalendarDays size={14} />月历</button>
            <button aria-label="切换签笺列表" aria-pressed={!calendar} onClick={() => onMode("list")}><List size={14} />列表</button>
          </div>
          <div className="counter-controls">
            <button aria-label="上个月" onClick={() => onMonth(-1)}><ChevronLeft size={17} /></button>
            <button onClick={onToday} title="回到本月">本月</button>
            <button aria-label="下个月" onClick={() => onMonth(1)} disabled={atCurrentMonth}><ChevronRight size={17} /></button>
          </div>
        </div>
        <div className="counter-dateline">
          <time dateTime={selected}>{selected.replaceAll("-", " / ")}</time>
          {selected === todayKey && <span>今天</span>}
        </div>
      </div>
      <div className="counter-desk">
        <section className="counter-calendar" aria-label="签笺月历">
          <header className="counter-month">
            <div className="counter-month-copy">
              <div className="counter-year">{month.getFullYear()} <span>年</span></div>
              <h2>{monthName.format(month)}</h2>
              <span>本月收签 <b>{stored.length}</b> 张</span>
            </div>
            <img className="counter-woodcut" src="/assets/winter/calendar-woodcut.webp" alt="" />
          </header>
          <div className="counter-month-body" key={month.toISOString() + mode} style={{ "--leaf-turn": monthDirection * 8 + "px" }}>
            {calendar ? <DateGrid dates={dates} selected={selected} onSelect={onSelect} /> :
              <SlipList stored={stored} selected={selected} onSelect={onSelect} />}
          </div>
        </section>
        <aside className="counter-reader" aria-label="选中的签笺">
          <JournalSlip fortune={fortune} selected={selected} />
          <footer className="counter-actions">
            <button disabled={!fortune || displayed} onClick={onDisplay} title="仅在旅人手册中展示">
              {displayed ? <Check size={14} /> : <Bookmark size={14} />}{displayed ? "当前选择已展示" : "展示选中的签"}
            </button>
            <button disabled={!fortune} onClick={() => onRead(fortune)}>阅读解签<ArrowRight size={15} /></button>
          </footer>
        </aside>
      </div>
    </section>
    <div className="counter-tailpiece" aria-hidden="true" />
  </>;
}
