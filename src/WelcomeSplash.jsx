import { useState } from "react";
import "./welcome-splash.css";

export default function WelcomeSplash() {
  const [phase, setPhase] = useState("writing");
  return (
    <div className="welcome-splash" data-phase={phase} aria-hidden="true">
      <div className="welcome-ink">
        <svg viewBox="0 0 330 160" className="hello-script" role="presentation">
          <defs>
            <linearGradient id="hello-ink" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0" stopColor="#5d84d6" />
              <stop offset="0.55" stopColor="#8970d8" />
              <stop offset="1" stopColor="#b184bf" />
            </linearGradient>
          </defs>
          {/* A continuous cursive stroke: h ascender, arch, e, two l loops, then o. */}
          <path className="hello-stroke" pathLength="1"
            d="M 24 111 C 42 99 63 77 72 48 C 83 14 67 11 58 29 C 48 49 49 86 41 121 C 48 94 60 77 73 81 C 92 88 64 127 88 124 C 101 122 111 111 120 101 C 139 80 143 95 128 104 C 99 122 112 139 139 123 C 160 111 176 80 185 48 C 194 13 177 12 170 33 C 162 57 159 100 157 113 C 154 140 178 126 194 105 C 211 82 222 51 224 32 C 228 11 210 14 206 38 C 201 65 196 102 199 116 C 201 136 220 125 235 108 C 253 85 268 91 262 112 C 256 136 231 136 229 116 C 227 98 243 87 255 96 C 270 107 284 109 303 93" />
          <path className="hello-flourish" pathLength="1" onAnimationEnd={() => setPhase("holding")}
            d="M 97 145 C 153 133 229 134 283 142 C 302 145 316 143 329 136" />
        </svg>
        <span className="welcome-signature">PIXELMC</span>
      </div>
    </div>
  );
}
