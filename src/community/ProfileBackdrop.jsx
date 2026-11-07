// SPDX-License-Identifier: Apache-2.0
import MemoryCoverArtwork from './MemoryCoverArtwork.jsx';
import { profilePatternId } from './profile-patterns.js';
import './MemoryCover.css';
import './profile-patterns.css';

const sharedPatterns = { nature: 'arcs', architecture: 'grid', cosmos: 'stars' };

function DoodleArt() {
  return <svg className="profile-pattern-illustration" viewBox="0 0 480 240" focusable="false">
    <ellipse cx="329" cy="215" rx="133" ry="15" fill="var(--memory-art-deep)" opacity=".12" />
    <path d="M156 208C215 160 259 174 303 190C359 162 405 174 469 205" fill="var(--memory-art-mid)" opacity=".35" />
    <g stroke="var(--memory-art-deep)" strokeWidth="2.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M262 205L325 118L396 205Z" fill="var(--memory-art-light)" />
      <path d="M325 118L338 204H396Z" fill="var(--memory-art-mid)" />
      <path d="M287 204L315 158L325 204" fill="var(--memory-art-highlight)" />
      <path d="M325 118L371 181M252 210H406M316 120L328 106M322 121L340 108" fill="none" />
      <path d="M187 97C174 98 167 92 169 82C170 73 178 68 190 70C196 49 224 46 237 61C252 57 266 65 266 78C282 78 284 96 269 99Z" fill="var(--memory-art-highlight)" />
      <path d="M208 79V82M234 79V82M218 85Q222 90 227 85" fill="none" />
      <path d="M407 178V213M410 134L391 166H427ZM407 159L384 192H436Z" fill="var(--memory-art-mid)" />
      <path d="M225 177V206M227 147L211 177H243Z" fill="var(--memory-art-light)" />
      <path d="M307 54L310 62L318 65L310 68L307 76L304 68L296 65L304 62Z" fill="var(--memory-art-highlight)" />
      <circle cx="389" cy="70" r="21" fill="var(--memory-art-highlight)" />
      <path d="M389 37V30M421 70H428M367 47L362 42M411 48L417 42" fill="none" />
    </g>
    <g fill="var(--memory-art-deep)" opacity=".5"><circle cx="292" cy="93" r="2" /><circle cx="449" cy="126" r="2" /><circle cx="175" cy="153" r="2.5" /></g>
  </svg>;
}

function TechnologyArt() {
  return <svg className="profile-pattern-illustration" viewBox="0 0 480 240" focusable="false">
    <g fill="none" stroke="var(--memory-art-deep)" strokeLinecap="round" strokeLinejoin="round">
      <path d="M166 65H226L250 89H278M139 179H221L249 151H277M343 87L371 59H443M343 150L375 182H466M305 53V21M307 190V223" strokeWidth="1.7" opacity=".65" />
      <path d="M181 45H234L261 72M161 196H229L253 172M361 104H407L424 87H466M359 132H404L426 154H466M289 53V30M326 188V214" strokeWidth="1" opacity=".35" />
      <rect x="267" y="66" width="85" height="109" rx="15" fill="var(--memory-art-light)" strokeWidth="1.5" />
      <rect x="279" y="79" width="61" height="83" rx="9" fill="var(--memory-art-mid)" opacity=".65" />
      <path d="M310 91L291 119L308 119L301 148L327 112H309Z" fill="var(--memory-art-highlight)" stroke="none" />
      <path d="M281 57V65M297 57V65M313 57V65M329 57V65M281 176V184M297 176V184M313 176V184M329 176V184M258 84H266M258 101H266M258 118H266M258 135H266M258 152H266M353 84H361M353 101H361M353 118H361M353 135H361M353 152H361" strokeWidth="2" />
      <g fill="var(--memory-art-highlight)" strokeWidth="1.5"><circle cx="166" cy="65" r="4" /><circle cx="139" cy="179" r="4" /><circle cx="443" cy="59" r="4" /><circle cx="466" cy="182" r="4" /><circle cx="305" cy="21" r="4" /><circle cx="307" cy="223" r="4" /></g>
      <circle cx="307" cy="120" r="93" strokeDasharray="2 9" opacity=".2" />
      <path d="M395 124A88 88 0 0 0 354 42M224 155A88 88 0 0 0 260 194" strokeWidth="4" opacity=".15" />
    </g>
  </svg>;
}

function ClassicArt() {
  return <svg className="profile-pattern-illustration" viewBox="0 0 480 240" focusable="false">
    <g fill="none" stroke="var(--memory-art-highlight)">
      <ellipse cx="329" cy="41" rx="150" ry="107" transform="rotate(-18 329 41)" strokeWidth="29" opacity=".22" />
      <ellipse cx="329" cy="41" rx="150" ry="107" transform="rotate(-18 329 41)" opacity=".7" />
      <path d="M188 197C276 171 320 220 457 144" opacity=".25" />
      <rect x="273" y="141" width="8" height="8" rx="1.5" transform="rotate(25 277 145)" opacity=".85" />
    </g>
    <g fill="var(--memory-art-highlight)"><circle cx="425" cy="99" r="2.5" /><circle cx="231" cy="183" r="2" /></g>
  </svg>;
}

export default function ProfileBackdrop({ pattern = 'classic', compact = false }) {
  const id = profilePatternId(pattern);
  const shared = sharedPatterns[id];
  return <div className={`profile-backdrop ${compact ? 'profile-backdrop--compact' : ''}`} data-pattern={id} aria-hidden="true">
    {shared ? <MemoryCoverArtwork pattern={shared} /> : id === 'doodle' ? <DoodleArt /> : id === 'tech' ? <TechnologyArt /> : <ClassicArt />}
  </div>;
}
