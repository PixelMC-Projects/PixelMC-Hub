import React, { useId, useState } from 'react';
import { usageDisplay } from './usage.js';

export default function UsageCost({ turns, terra = false, guideOpen = false }) {
  const [open, setOpen] = useState(false);
  const id = useId();
  const { cost, metrics, note } = usageDisplay(turns, terra);
  return <div className="wz-usage" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}
    onFocus={() => setOpen(true)} onBlur={() => setOpen(false)}
    onKeyDown={event => {
      if (event.key === 'Escape' && open) {
        event.preventDefault();
        event.stopPropagation();
        setOpen(false);
      }
    }}>
    <button className="wz-cost" type="button" aria-label={'本对话累计费用 ' + cost}
      aria-describedby={open || guideOpen ? id : undefined} onClick={() => setOpen(true)}>{cost}{note && ' *'}</button>
    {(open || guideOpen) && <div className="wz-usage-tip" role="tooltip" id={id}>
      <div className="wz-usage-paper">
        <header><strong>本对话用量</strong><small>{terra ? '演示数据' : '演示估算'}</small></header>
        <dl>{metrics.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
        {note && <small>{note}</small>}
      </div>
    </div>}
  </div>;
}
