// SPDX-License-Identifier: Apache-2.0
import GlassSurface from '../vendor/react-bits/GlassSurface.jsx';
import { resolveMemoryCover } from './MemoryCoverPresets.js';
import MemoryCoverArtwork from './MemoryCoverArtwork.jsx';
import './MemoryCover.css';

export default function MemoryCover({ archive, compact = false, children }) {
  const { coverPattern, coverColor } = resolveMemoryCover(archive);
  return <div className={`memory-cover ${compact ? 'memory-cover--compact memory-archive-cover' : 'memory-cover--detail'}`}
    data-cover-pattern={coverPattern} data-cover-color={coverColor}>
    {compact ? <><MemoryCoverArtwork pattern={coverPattern} />{children}</> : (
      <GlassSurface width="100%" height="auto" borderRadius={0} refraction={false}
        backgroundOpacity={0.03} className="memory-detail-cover">
        <MemoryCoverArtwork pattern={coverPattern} />
        {children}
      </GlassSurface>
    )}
  </div>;
}
