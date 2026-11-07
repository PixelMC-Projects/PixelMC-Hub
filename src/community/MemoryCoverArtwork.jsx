// SPDX-License-Identifier: Apache-2.0
import { useId } from 'react';

export default function MemoryCoverArtwork({ pattern }) {
  const prefix = `memory-art-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const paint = name => `url(#${prefix}-${name})`;
  const scenes = {
    arcs: <g>
      <circle cx="324" cy="92" r="92" fill={paint('sun')} opacity=".65" />
      <path d="M194 158C157 102 174 57 246 37C258 96 238 136 194 158Z" fill="var(--memory-art-mid)" opacity=".25" />
      <path d="M206 144C196 106 205 75 236 49" fill="none" stroke="var(--memory-art-deep)" strokeWidth="1.2" opacity=".2" />
      <path d="M-18 219C74 181 118 220 197 169C279 117 333 141 391 109C424 92 459 80 498 92V260H-18Z" fill={paint('ribbon')} opacity=".6" />
      <path d="M-24 249C58 199 129 243 224 192C318 142 345 167 420 138C452 125 480 123 507 134V265H-24Z" fill="var(--memory-art-mid)" opacity=".75" />
      <path d="M-4 265C87 239 137 271 235 226C334 181 378 208 493 166V268Z" fill={paint('shade')} opacity=".64" />
      <path d="M24 226C107 204 155 223 221 183C289 143 346 162 397 129" fill="none" stroke="var(--memory-art-highlight)" strokeWidth="1.3" opacity=".7" />
      <path d="M195 241C241 204 315 211 389 185" fill="none" stroke="var(--memory-art-highlight)" strokeWidth="1" opacity=".5" />
      <g transform="rotate(10 358 156)">
        <path d="M359 233C345 176 361 119 380 55" fill="none" stroke="var(--memory-art-deep)" strokeWidth="2" opacity=".7" />
        <path d="M368 112C339 112 325 93 326 68C353 72 369 87 368 112Z" fill="var(--memory-art-mid)" opacity=".9" />
        <path d="M374 92C375 68 394 49 415 48C412 76 394 91 374 92Z" fill="var(--memory-art-deep)" opacity=".63" />
        <path d="M358 150C329 151 308 133 308 108C336 112 354 126 358 150Z" fill="var(--memory-art-deep)" opacity=".55" />
        <path d="M361 132C375 107 398 101 419 109C405 132 383 140 361 132Z" fill="var(--memory-art-mid)" opacity=".86" />
        <path d="M352 187C322 190 303 172 298 147C330 149 348 164 352 187Z" fill="var(--memory-art-mid)" />
        <path d="M353 177C370 153 391 148 412 158C393 181 373 186 353 177Z" fill="var(--memory-art-deep)" opacity=".6" />
        <path d="M355 216C374 194 393 193 410 203C391 223 371 227 355 216Z" fill="var(--memory-art-mid)" opacity=".85" />
        <path d="M331 77L364 109M312 115L354 146M303 155L349 182" fill="none" stroke="var(--memory-art-highlight)" strokeWidth=".9" opacity=".55" />
      </g>
      <g fill="var(--memory-art-highlight)"><circle cx="274" cy="69" r="3" /><circle cx="442" cy="74" r="2" /><circle cx="250" cy="137" r="2" /></g>
      <ellipse cx="419" cy="223" rx="61" ry="9" fill="var(--memory-art-deep)" opacity=".09" />
    </g>,
    ridges: <g>
      <circle cx="340" cy="70" r="92" fill={paint('sun')} />
      <circle cx="340" cy="70" r="31" fill="var(--memory-art-highlight)" opacity=".78" />
      <path d="M292 76H402C398 65 387 62 378 68C367 51 346 53 337 66C321 57 302 62 292 76Z" fill="var(--memory-art-light)" opacity=".82" />
      <path d="M153 112H236C230 103 220 102 211 105C201 92 183 94 177 105C166 100 158 104 153 112Z" fill="var(--memory-art-light)" opacity=".72" />
      <path d="M-10 184L65 133L122 161L213 69L273 135L323 98L390 159L434 127L492 177V252H-10Z" fill="var(--memory-art-mid)" opacity=".48" />
      <path d="M179 104L213 69L244 103L226 97L212 104L201 94Z" fill="var(--memory-art-light)" opacity=".8" />
      <path d="M299 122L323 98L349 122L330 117L319 124L311 116Z" fill="var(--memory-art-light)" opacity=".6" />
      <path d="M-12 224L77 160L147 194L246 121L324 181L387 140L495 208V254H-12Z" fill={paint('ribbon')} opacity=".9" />
      <path d="M179 173L246 121L300 163L254 143L237 154L222 148Z" fill="var(--memory-art-light)" opacity=".22" />
      <path d="M-12 241Q76 192 159 223L260 178L335 215L408 179L493 219V259H-12Z" fill={paint('shade')} opacity=".82" />
      <path d="M261 179C246 195 279 201 263 214C249 226 210 226 205 252H238C237 231 278 230 282 216C287 201 255 194 261 179Z" fill="var(--memory-art-light)" opacity=".42" />
      <path d="M383 220L390 197L397 220ZM397 224L404 203L411 224ZM415 231L425 198L435 231Z" fill="var(--memory-art-deep)" opacity=".65" />
      <path d="M390 218V230M404 222V232M425 227V239" stroke="var(--memory-art-deep)" strokeWidth="1.5" opacity=".6" />
      <path d="M284 43H307M379 100H424" stroke="var(--memory-art-highlight)" strokeWidth="1.5" strokeLinecap="round" opacity=".5" />
    </g>,
    grid: <g strokeLinejoin="round">
      <ellipse cx="321" cy="207" rx="170" ry="27" fill={paint('sun')} opacity=".7" />
      <path d="M130 204L302 113L486 205L314 289Z" fill="var(--memory-art-mid)" opacity=".14" />
      <path d="M148 204L311 121M200 230L364 147M254 258L419 175M175 181L342 268M229 152L396 239M283 124L451 210" fill="none" stroke="var(--memory-art-deep)" strokeWidth=".8" opacity=".14" />
      <g opacity=".76">
        <path d="M355 121L403 94L451 121L403 149Z" fill="var(--memory-art-light)" />
        <path d="M355 121L403 149V212L355 185Z" fill="var(--memory-art-mid)" />
        <path d="M403 149L451 121V185L403 212Z" fill="var(--memory-art-deep)" opacity=".8" />
        <path d="M371 142L387 151V167L371 158ZM371 166L387 175V190L371 181Z" fill="var(--memory-art-highlight)" opacity=".52" />
      </g>
      <path d="M262 86L324 51L387 86L324 122Z" fill="var(--memory-art-light)" />
      <path d="M262 86L324 122V223L262 188Z" fill={paint('ribbon')} />
      <path d="M324 122L387 86V188L324 223Z" fill={paint('shade')} />
      <path d="M262 86L324 122L387 86M324 122V223" fill="none" stroke="var(--memory-art-highlight)" strokeWidth="1.2" opacity=".62" />
      <path d="M280 112L305 126V144L280 130ZM280 145L305 159V177L280 163ZM280 178L305 192V209L280 195Z" fill="var(--memory-art-highlight)" opacity=".48" />
      <path d="M343 126L368 112V130L343 144ZM343 159L368 145V163L343 177Z" fill="var(--memory-art-light)" opacity=".25" />
      <path d="M174 151L224 123L275 151L224 180Z" fill="var(--memory-art-light)" />
      <path d="M174 151L224 180V226L174 197Z" fill="var(--memory-art-mid)" />
      <path d="M224 180L275 151V197L224 226Z" fill="var(--memory-art-deep)" opacity=".82" />
      <path d="M183 156L224 180L265 156" fill="none" stroke="var(--memory-art-highlight)" strokeWidth="1.2" opacity=".7" />
      <path d="M196 174L210 182V199L196 191Z" fill="var(--memory-art-highlight)" opacity=".65" />
      <path d="M177 66L197 55L217 66L197 78Z" fill="var(--memory-art-light)" />
      <path d="M177 66L197 78V101L177 89Z" fill="var(--memory-art-mid)" />
      <path d="M197 78L217 66V89L197 101Z" fill="var(--memory-art-deep)" opacity=".7" />
      <path d="M198 101V120H154V158M388 87H426V61M324 51V30H290" fill="none" stroke="var(--memory-art-deep)" strokeWidth="1.2" opacity=".38" />
      <g fill="var(--memory-art-highlight)" stroke="var(--memory-art-deep)" strokeWidth="1" opacity=".8"><circle cx="154" cy="158" r="3" /><circle cx="426" cy="61" r="3" /><circle cx="290" cy="30" r="3" /></g>
    </g>,
    stars: <g>
      <ellipse cx="307" cy="120" rx="180" ry="98" transform="rotate(-18 307 120)" fill={paint('nebula')} opacity=".85" />
      <ellipse cx="372" cy="73" rx="105" ry="64" transform="rotate(-24 372 73)" fill={paint('sun')} opacity=".52" />
      <path d="M144 210C198 165 223 95 314 58C368 36 409 42 473 18C423 59 378 78 330 93C257 116 228 178 144 210Z" fill={paint('ribbon')} opacity=".22" />
      <ellipse cx="326" cy="127" rx="146" ry="66" transform="rotate(-22 326 127)" fill="none" stroke="var(--memory-art-deep)" strokeWidth="1.1" opacity=".35" />
      <ellipse cx="326" cy="127" rx="117" ry="51" transform="rotate(-22 326 127)" fill="none" stroke="var(--memory-art-highlight)" strokeWidth=".8" opacity=".48" />
      <circle cx="337" cy="109" r="44" fill={paint('planet')} />
      <path d="M304 89C320 80 348 84 371 98M298 111C321 101 352 108 378 122M307 133C327 125 350 135 365 141" fill="none" stroke="var(--memory-art-light)" strokeWidth="3" opacity=".18" />
      <path d="M270 143C283 165 350 150 392 118C412 103 419 90 408 85" fill="none" stroke="var(--memory-art-highlight)" strokeWidth="2.8" strokeLinecap="round" opacity=".72" />
      <circle cx="221" cy="171" r="13" fill={paint('planet')} opacity=".85" />
      <circle cx="428" cy="89" r="5" fill="var(--memory-art-highlight)" opacity=".9" />
      <path d="M163 65L204 44L248 71M384 195L423 174L449 194" fill="none" stroke="var(--memory-art-deep)" strokeWidth=".8" opacity=".35" />
      <g fill="var(--memory-art-highlight)">
        <circle cx="163" cy="65" r="2.2" /><circle cx="204" cy="44" r="2.8" /><circle cx="248" cy="71" r="2" />
        <circle cx="390" cy="38" r="2.4" /><circle cx="456" cy="57" r="1.5" /><circle cx="278" cy="29" r="1.7" />
        <circle cx="175" cy="129" r="1.4" /><circle cx="261" cy="205" r="2" /><circle cx="307" cy="188" r="1.5" />
        <circle cx="384" cy="195" r="2" /><circle cx="423" cy="174" r="2.5" /><circle cx="449" cy="194" r="1.7" />
        <circle cx="458" cy="129" r="1.8" /><circle cx="339" cy="221" r="1.2" /><circle cx="198" cy="215" r="1.5" />
      </g>
      <path d="M263 109L266 117L274 120L266 123L263 131L260 123L252 120L260 117ZM408 29L410 35L416 37L410 39L408 45L406 39L400 37L406 35Z" fill="var(--memory-art-highlight)" opacity=".92" />
    </g>,
  };

  return <div className="memory-cover-art" aria-hidden="true">
    <svg className="memory-cover-illustration" viewBox="0 0 480 240" preserveAspectRatio="xMaxYMid meet" focusable="false">
      <defs>
        <linearGradient id={`${prefix}-ribbon`} x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="var(--memory-art-light)" /><stop offset=".52" stopColor="var(--memory-art-mid)" /><stop offset="1" stopColor="var(--memory-art-deep)" />
        </linearGradient>
        <linearGradient id={`${prefix}-shade`} x1="0" y1="0" x2=".7" y2="1">
          <stop stopColor="var(--memory-art-mid)" /><stop offset="1" stopColor="var(--memory-art-deep)" />
        </linearGradient>
        <radialGradient id={`${prefix}-sun`}>
          <stop stopColor="var(--memory-art-highlight)" stopOpacity=".85" /><stop offset="1" stopColor="var(--memory-art-highlight)" stopOpacity="0" />
        </radialGradient>
        <radialGradient id={`${prefix}-nebula`}>
          <stop stopColor="var(--memory-art-mid)" stopOpacity=".8" /><stop offset=".6" stopColor="var(--memory-art-deep)" stopOpacity=".35" /><stop offset="1" stopColor="var(--memory-art-deep)" stopOpacity="0" />
        </radialGradient>
        <radialGradient id={`${prefix}-planet`} cx=".3" cy=".26" r=".8">
          <stop stopColor="var(--memory-art-highlight)" /><stop offset=".46" stopColor="var(--memory-art-mid)" /><stop offset="1" stopColor="var(--memory-art-deep)" />
        </radialGradient>
      </defs>
      {scenes[pattern]}
    </svg>
  </div>;
}
