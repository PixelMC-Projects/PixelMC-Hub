import React, { useEffect, useRef } from 'react';
import { paintPlayer } from './summitMesh.js';
import './summit-player.css';

const winterOutfit = '/assets/winter/summit-traveler.png';
const textures = new Map();

async function readTexture(url) {
  const image = new Image();
  image.src = url;
  await image.decode();
  const height64 = 64 * image.height / image.width;
  if (image.width < 64 || ![32,64].includes(height64)) throw new Error('Unsupported skin dimensions');
  const canvas = document.createElement('canvas');
  canvas.width = image.width;
  canvas.height = image.height;
  const ctx = canvas.getContext('2d', {willReadFrequently:true});
  ctx.drawImage(image,0,0);
  const pixels = ctx.getImageData(0,0,image.width,image.height);
  let arm = 4;
  if (height64 === 64) {
    // The last two columns on the back of a slim arm are unused.
    const scale = image.width/64;
    const strip = ctx.getImageData(54*scale,20*scale,2*scale,12*scale);
    if (strip.data.every((v,i)=>i%4 !== 3 || v === 0)) arm = 3;
  }
  return {data:pixels.data, width:image.width, height:image.height, height64, arm};
}

function texture(url) {
  if (!textures.has(url)) textures.set(url,readTexture(url).catch(error=>{
    textures.delete(url);
    throw error;
  }));
  return textures.get(url);
}

export default function SummitPlayer({ name, skin, rank }) {
  const ref = useRef(null);
  useEffect(()=>{
    let current = true;
    async function render() {
      const pixels = await texture(skin || winterOutfit).catch(()=>texture(winterOutfit));
      if (current && ref.current) paintPlayer(ref.current,pixels,rank);
    }
    render().catch(()=>{}); // Keep the accessible name and profile control if local artwork fails.
    return ()=>{ current=false; };
  },[rank,skin]);
  return <canvas ref={ref} width={288} height={296} className="summit-player" role="img" aria-label={name+' 的全身像'}/>;
}
