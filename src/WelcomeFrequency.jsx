export const welcomeModes = [
  { id: "always", label: "每次打开", description: "每次打开手册时播放" },
  { id: "login", label: "每次登录", description: "每次进服后，仅首次打开手册时播放" },
  { id: "once", label: "仅初次", description: "仅第一次打开手册时播放，以后不再显示" },
];

export function shouldShowWelcome(mode, { reducedMotion, seenThisLogin, seenBefore }) {
  if (reducedMotion) return false;
  if (mode === "login") return !seenThisLogin;
  if (mode === "once") return !seenBefore;
  return true;
}

export default function WelcomeFrequency({ value, onChange, reducedMotion }) {
  const index = Math.max(0, welcomeModes.findIndex((mode) => mode.id === value));
  return (
    <div className="setting-row welcome-setting">
      <div className="welcome-setting-heading">
        <strong id="welcome-frequency-label">开屏动画</strong>
        <span>手写 hello</span>
      </div>
      <div className="welcome-frequency" style={{ "--welcome-progress": `${index * 50}%` }}>
        <div className="welcome-range-track">
          <span className="welcome-range-line" aria-hidden="true" />
          <span className="welcome-range-stops" aria-hidden="true"><i /><i /><i /></span>
          <input
            type="range"
            min="0"
            max="2"
            step="1"
            value={index}
            aria-labelledby="welcome-frequency-label"
            aria-describedby="welcome-frequency-description"
            aria-valuetext={welcomeModes[index].label}
            onChange={(event) => onChange(welcomeModes[Number(event.target.value)].id)}
          />
        </div>
        <div className="welcome-range-labels">
          {welcomeModes.map((mode, i) => (
            <button key={mode.id} type="button" aria-pressed={i === index}
              className={i === index ? "selected" : ""} onClick={() => onChange(mode.id)}>
              {mode.label}
            </button>
          ))}
        </div>
      </div>
      <p id="welcome-frequency-description">
        {reducedMotion ? "已开启减少动态效果，暂不播放开屏动画" : welcomeModes[index].description}
      </p>
    </div>
  );
}
