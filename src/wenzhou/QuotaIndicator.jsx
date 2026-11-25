import React, { useId, useState } from 'react';

const timestamp = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });

export default function QuotaIndicator({quota}) {
  const [open, setOpen] = useState(false);
  const id = useId();
  const unlimited = quota.unlimited && !quota.exhausted;
  const percent = quota.exhausted ? 0 : Math.min(100, Math.max(0, quota.remainingPercent ?? 0));
  const value = percent > 0 && percent < 1 ? '不足 1%' : Math.round(percent) + '%';
  const label = quota.terra
    ? unlimited ? 'Terra 美元额度不限' : 'Terra 美元剩余额度 ' + value
    : unlimited ? '额度不限' : '本周剩余额度 ' + value;
  const reset = quota.resetAt ? '重置时间 ' + timestamp.format(quota.resetAt)
    : quota.exhausted ? '当前没有可用额度' : quota.terra ? '' : '首次提问后开始计时';
  return <div className="wz-quota" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}
    onFocus={() => setOpen(true)} onBlur={() => setOpen(false)} onKeyDown={event => {
      if (event.key === 'Escape' && open) { event.stopPropagation(); setOpen(false); }
    }}>
    <button type="button" className="wz-quota-button" aria-label={label} aria-describedby={open ? id : undefined}
      onClick={() => setOpen(true)}>
      {unlimited ? <span className="wz-quota-unlimited" aria-hidden="true">∞</span> :
        <svg viewBox="0 0 32 32" width="32" height="32" aria-hidden="true">
          <circle cx="16" cy="16" r="13" fill="none" stroke="var(--wz-rule)" strokeWidth="2.5"/>
          {percent > 0 && <circle cx="16" cy="16" r="13" pathLength="100" fill="none" stroke="currentColor"
            strokeWidth="2.5" strokeLinecap="round" strokeDasharray={percent + ' 100'} transform="rotate(-90 16 16)"/>}
          <rect x="12.4" y="12.4" width="7.2" height="7.2" rx="1.4" fill="var(--wz-rule)" transform="rotate(45 16 16)"/>
        </svg>}
    </button>
    {open && <div id={id} role="tooltip" className="wz-quota-tip"><div className="wz-usage-paper">
      <strong>{label}</strong>
      {!unlimited && reset && <small>{reset}</small>}
    </div></div>}
  </div>;
}
