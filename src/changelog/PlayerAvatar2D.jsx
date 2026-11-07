import React, { useEffect, useState } from 'react';
import './avatar.css';
// Same skin source as the existing 3D head, with independent face and hat UV layers.
export default function PlayerAvatar2D({ name, skin = import.meta.env.VITE_PREVIEW_SKIN, fallback = '/assets/avatar.png' }) {
  const [texture, setTexture] = useState(null);
  useEffect(() => {
    setTexture(null); if (!skin) return;
    let active = true; const image = new Image();
    image.onload = () => { if (active && image.width >= 64 && [1,2].includes(image.width/image.height)) setTexture({url:skin, rows:8*image.height/image.width}); };
    image.src = skin; return () => { active = false; };
  }, [skin]);
  return <span className="updates-avatar-2d" role="img" aria-label={name+' 的头像，含帽子层'}>{texture ? ['face','hat'].map(layer=><span key={layer} className={'avatar-'+layer} aria-hidden="true" style={{backgroundImage:'url("'+texture.url+'")',backgroundSize:'800% '+texture.rows*100+'%',backgroundPosition:((layer==='hat'?40:8)/56*100)+'% '+(8/(texture.rows*8-8)*100)+'%'}}/>) : <img src={fallback} alt=""/>}</span>;
}
