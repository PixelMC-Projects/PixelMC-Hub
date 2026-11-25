import React from 'react';
import { Pin } from 'lucide-react';
import { formatTime, formatVersion } from './model.js';
import './winter-gazette.css';

export function GazetteIndexItem({ item, pack, unread, current, latest }) {
  const fullVersion = formatVersion(item, pack);
  const edition = [item.packVersion, item.updateVersion].filter(Boolean).join('-') || fullVersion;
  return <div className="gazette-entry" data-current={current || undefined}>
    <div className="gazette-entry-meta">
      <span title={fullVersion}>{edition}</span>
      <time dateTime={item.publishedAt}>{formatTime(item.publishedAt, true).slice(5)}</time>
    </div>
    <h3>{item.title}</h3>
    <div className="gazette-entry-flags">
      <span>{item.category}</span>
      {item.pinned && <span><Pin size={10} />置顶</span>}
      {latest && <span>最新</span>}
      {unread && <span className="gazette-unread">未读</span>}
    </div>
  </div>;
}

export function GazetteCover({ entry, pack }) {
  return <header className="gazette-cover">
    <div className="gazette-mast">
      <span>驿站公报</span>
      <time dateTime={entry.publishedAt}>{formatTime(entry.publishedAt)}</time>
    </div>
    <div className="gazette-byline">
      <span>{entry.category}</span>
      <span className="gazette-version">{formatVersion(entry, pack)}</span>
      {entry.pinned && <span className="gazette-pin"><Pin size={11} />置顶</span>}
    </div>
    <h2>{entry.title}</h2>
    {entry.summary && <p>{entry.summary}</p>}
  </header>;
}
