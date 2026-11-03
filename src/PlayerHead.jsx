import { useEffect, useState } from "react";
import "./player-head.css";

// Minecraft head UVs: six 8x8 faces, with the hat net offset 32 pixels right.
// Keep the transparent hat on its own 9x9x9 cube around the 8x8x8 head.
const faces = [
  { side: "front", u: 8, v: 8 },
  { side: "back", u: 24, v: 8 },
  { side: "left", u: 0, v: 8 },
  { side: "right", u: 16, v: 8 },
  { side: "top", u: 8, v: 0 },
  { side: "bottom", u: 16, v: 0 },
];

export default function PlayerHead({ name, skin = import.meta.env.VITE_PREVIEW_SKIN }) {
  const [texture, setTexture] = useState(null);
  useEffect(() => {
    setTexture(null);
    if (!skin) return;
    let active = true;
    const image = new Image();
    image.onload = () => {
      // Both modern 64x64 and legacy 64x32 skins share this head UV layout.
      if (active && image.width >= 64 && [1, 2].includes(image.width / image.height)) {
        setTexture({ url: skin, rows: 8 * image.height / image.width });
      }
    };
    image.src = skin;
    return () => { active = false; };
  }, [skin]);

  return (
    <div className={`avatar-cube ${texture ? "has-skin" : "fallback-head"}`}
      role="img" aria-label={`${name} 的立体头像${texture ? "，含帽子外层" : "，默认占位"}`}>
      {["base", ...(texture ? ["hat"] : [])].map(layer => (
        <div className={`head-layer head-${layer}`} key={layer} aria-hidden="true">
          {faces.map(({ side, u, v }) => (
            <span className={`head-face head-${side}`} key={side}
              style={texture ? {
                backgroundImage: `url("${texture.url}")`,
                backgroundSize: `800% ${texture.rows * 100}%`,
                backgroundPosition: `${(u + (layer === "hat" ? 32 : 0)) / 56 * 100}% ${v / (texture.rows * 8 - 8) * 100}%`,
              } : undefined} />
          ))}
        </div>
      ))}
    </div>
  );
}
