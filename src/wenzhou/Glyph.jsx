import React from "react";

// New single-ink drawings for Wenzhou. All the interiors stay transparent.
const shapes = {
  toolbox: <><path d="M8.5 7V5.7c0-.9.7-1.7 1.7-1.7h3.6c1 0 1.7.8 1.7 1.7V7"/><rect x="3" y="7" width="18" height="13" rx="2.6"/><path d="M3.2 12.2c5.2 1.8 12.4 1.8 17.6 0M10 12.7v2.5h4v-2.5"/></>,
  selectBlock: <><path d="M3 8V4h4m10 0h4v4M3 16v4h4m10 0h4v-4"/><path d="m7 9 5-2.7L17 9v5.7l-5 2.8-5-2.8Zm0 0 5 2.8L17 9m-5 2.8v5.7"/></>,
  "block-fields": <path d="M5 4h14v16H5ZM8.5 8h.1m2.9 0h4m-7 4h.1m2.9 0h4m-7 4h.1m2.9 0h4"/>,
  "block-field": <path d="M8 4H4v16h4m8-16h4v16h-4M8 12h8m-4-3v6"/>,
  boat: <>
    <path d="m2 12.5 9.7 1.9L22 8.9l-3.4 9.3-10.8 1.2Z" />
    <path d="m5.9 13.2 6.4-9.1 4.6 7.3m-4.6-7.3-.6 10.3" />
  </>,
  barrel: <>
    <path d="m6 10 1.2 9.7c2.8 1.7 6.8 1.7 9.6 0L18 10M6 10c3.1-1.4 8.9-1.4 12 0-2.8 2-9.2 2-12 0Zm3-.9L7.7 4.4l2.1-.6 1.3 5m1.2-.3.9-6 2.1.3-.8 5.8m.9.3 1.9-4 1.8.9-1.8 3.7" />
    <path d="m9.4 15 .4 2.8m4.7-2.8-.4 2.8" />
  </>,
  bench: <>
    <path d="m2.8 7.6 9.1-4.4L21 7.6v10.1l-9.1 4.1-9.1-4.1Zm0 0 9.1 4.4L21 7.6M11.9 12v9.8M6 16l2.5 1.3m6.6-3.3 2.4-1.2v3.4" />
    <path d="m6.1 6.1 8.9 4.4m-5.4-6 9 4.3m-12.7.3 9-4.5" />
  </>,
  torch: <><path d="m9.3 12.9-3.8 7.9 2.8 1.2 4.4-7.6m-4.3-2.1 6.6 3.1 1.5-3.1-6.6-3.1Z" /><path d="M11.5 9.5c-1.4-3 2.5-3.4 2.4-7.3 3.5 2.2 4.9 6.5 2.2 9.3" /></>,
  wood: <><path d="m2.5 8.4 12-4.9 7 3.2-12 5.3-7-3.6Zm0 0v7.3l7 3.6 12-5.3V6.7m-12 5.3v7.3m3.5-5.1 5.5-2.3" /></>,
  coal: <><path d="m3.4 13.1 2.4-5.5 6.4-3.3 6.7 3.4 2.1 7.5-6.2 4.2-7.2-.6Z" /><path d="m5.8 7.6 5.4 3.2 7.7-3.1m-7.7 3.1 3.6 8.6" /></>,
  stick: <><path d="m5 19.1 12.4-15 2.2 1.8L7.3 21Z" /></>,
  back: <path d="m10.2 5.5-6.4 6.4 6.4 6.6M4.5 12H21" />,
  history: <><path d="M4.1 8.3A8.5 8.5 0 1 1 3.5 15M3.5 3.9v5h5" /><path d="M12 7v5.4l3.6 2" /></>,
  trash: <><path d="M4.5 6.5h15m-12.8 0 .8 13h9l.8-13M9 6.5V4h6v2.5M10 10v6m4-6v6" /></>,
  send: <path d="M12 20V4.5m-6 6.1 6-6.1 6 6.1" />,
  book: <><path d="M3 5.8c3.4-.9 6.3-.3 9 1.4 2.7-1.7 5.6-2.3 9-1.4v13c-3.2-.7-6.1-.4-9 1.2-2.9-1.6-5.8-1.9-9-1.2Zm9 1.4v12.6" /></>,
  check: <path d="m4.5 12.5 4.7 4.8L19.7 6.7" />,
  "food-book": <path d="M3 5c3-1 6 0 9 2 3-2 6-3 9-2v13c-3-1-6 0-9 2-3-2-6-3-9-2Zm9 2v13M6 13v2m3-5v5m6-3h3m-3 3h2" />,
  "structure": <path d="M3 21h18M5 20V8h3V4h3v4h3V4h3v4h2v12m-9 0v-5h4v5" />,
  "structure-loot": <path d="M3 10h18v10H3zM3 14h18M10 13h4v3h-4zM6 9V6l6-3 6 3v3" />,
  "trade": <path d="M3 8h16l-4-4m6 12H5l4 4" />,
  "quest": <path d="M5 3h14v18H5zM8 8l1 1 2-3m2 2h3m-8 6 1 1 2-3m2 2h3" />,
  "reward": <path d="M3 9h18v4H3zM5 13v8h14v-8M12 9v12m0-12C4 9 5 2 9 4l3 5c7 0 8-6 4-5z" />,
  "wallet": <path d="M3 7l13-4 1 4M3 7h18v13H3zM16 11h5v5h-5z" />,
  "store": <path d="M3 10l2-6h14l2 6M3 10c0 3 4 3 5 0 2 3 5 3 7 0 2 3 5 3 6 0M5 13v8h14v-8m-10 8v-6h4v6" />,
  "store-search": <path d="M2 9l2-5h12l2 5H2zm2 0v10h6m9-3 3 5m-3-7a3 3 0 1 1-6 0 3 3 0 1 1 6 0" />,
  "quote": <path d="M5 2h14v20l-3-2-4 2-4-2-3 2zM8 7h8m-8 4h3m2 0h3m-8 4h8" />,
  worldgen: <path d="M3 20h18M8 20v-4H4l4-5H5l5-7 5 7h-3l4 5h-4v4m4-9 2-3 4 6h-3l3 4h-5" />,
  calculator: <path d="M6 3h12q1 0 1 1v16q0 1-1 1H6q-1 0-1-1V4q0-1 1-1ZM8 7h8M8 12h2m4 0h2m-8 4h2m4 0h2" />,
  command: <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m7 9 3 3-3 3m6 0h4" /></>,
  chevron: <path d="m8.5 5.5 6.2 6.5-6.2 6.5" />,
  new: <><path d="M11 5H6a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-5M10 14l1-4 7-7 3 3-7 7Z" /></>,
};

export function Glyph({ name, size = 20, ...props }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}>{shapes[name]}</svg>;
}

export function BoatMark() {
  return <span className="wz-mascot" aria-hidden="true">
    <img src="/assets/wenzhou-boat.png" alt="" draggable="false" />
  </span>;
}
