import React, { useLayoutEffect, useRef } from 'react';

// Measure after wrapping. Item references keep their native size, and a recipe grid is one step.
function lineBottoms(content) {
  const stops = [];
  for (const block of content.children) {
    const height = block.offsetHeight;
    const lineHeight = parseFloat(getComputedStyle(block).lineHeight);
    const count = block.tagName === 'P' && lineHeight > 0 ? Math.max(1, Math.round(height / lineHeight)) : 1;
    for (let line = 1; line <= count; line++) {
      stops.push(block.offsetTop + Math.ceil(height * line / count));
    }
  }
  if (stops.length) stops[stops.length - 1] = content.offsetHeight;
  return stops;
}

export default function AnswerReveal({ play, onGrow, children }) {
  const frame = useRef(null);
  const content = useRef(null);
  const played = useRef(false);

  useLayoutEffect(() => {
    if (!play || played.current) { played.current = true; return; }
    const box = frame.current;
    const body = content.current;
    const stops = lineBottoms(body);
    if (!stops.length) return;

    let timer;
    let next = 0;
    const width = body.clientWidth;
    const height = body.offsetHeight;
    function clear() {
      clearTimeout(timer);
      observer.disconnect();
      box.removeEventListener('focusin', finish);
      box.removeAttribute('data-revealing');
      box.style.removeProperty('max-height');
    }
    function finish() {
      played.current = true;
      clear();
      onGrow();
    }
    function showLine() {
      box.style.maxHeight = stops[next] + 'px';
      next += Math.max(1, Math.ceil(stops.length / 24));
      timer = setTimeout(next < stops.length ? showLine : finish, 180);
    }
    const observer = new ResizeObserver(() => {
      if (body.clientWidth !== width || body.offsetHeight !== height) finish();
      else onGrow();
    });
    box.dataset.revealing = '';
    box.addEventListener('focusin', finish);
    observer.observe(box);
    observer.observe(body);
    showLine();
    return clear;
  }, [play, onGrow]);

  return <div className="wz-answer" ref={frame}>
    <div className="wz-answer-content" ref={content}>{children}</div>
  </div>;
}
