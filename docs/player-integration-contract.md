# PixelMC-Hub player integration v1

Status: implementation contract, 2026-11-04. This is not a deployment or gameplay acceptance record.

## Scope and ownership

- Root coordinates contracts, review, packaging and documentation. Agents own implementation.
- Manager supplies authenticated player/session/archive context and existing unified storage.
- MemorialTitles owns title definitions, presentation metadata, ownership, equip and privacy.
- DailyFortunes owns today state, archived collections and the personal handbook feature selection.
- Hub is the optional client/server player channel and native UI. Manager stays server-only and never requires a client mod. Existing administration/mobile HTTP APIs keep their behavior.
- Work locally; no production service/database/client/sync changes, restarts or formal releases. The user performs gameplay acceptance. Build and isolated automated checks are allowed.
- Prioritize complete Forge 1.20.1 Hub integration. Propagate shared Manager/plugin schema and safe APIs to existing version branches without creating a new Hub renderer port.

## Optional Java boundary

Each server-side provider exposes `public static CompletableFuture<String> request(UUID player, String action, String payload)` and `public static int protocolVersion()` (returns 1):

| module | provider class |
| --- | --- |
| manager | `com.pixelmc.pixelmcmanager.api.PlayerHubApi` |
| titles | `cn.pixelmc.memorialtitles.api.PlayerHubApi` |
| fortunes | `cn.pixelmc.dailyfortunes.api.PlayerHubApi` |

Hub discovers these optional providers reflectively, with no compile-time hard dependency. The player UUID is taken only from the authenticated game connection. Payloads cannot choose another player. Providers validate player/runtime/admission as appropriate and reject unknown actions. Only explicitly listed player operations are forwarded; never general Manager RPC, administrative actions, raw snapshots or SQL.

Payload and response are UTF-8 JSON objects. Successful responses are the objects below; failures complete exceptionally with a safe user-facing message. Hub wraps responses with request ID, module, action, success/error and session generation. Responses must be discarded after logout, world/server change, stale request or a superseding write. IO uses bounded workers; the server tick/render thread never blocks on futures. Limits, throttling and paging must be explicit, with no silent truncation.

## Manager

`snapshot {}` returns:

```json
{
  "revision": "opaque",
  "player": {"uuid":"uuid", "name":"name", "firstJoinedAt":0, "previousLoginAt":0, "joinCount":0, "totalOnlineMillis":0},
  "scope": {"serverId":"non-secret-stable-id", "archiveId":"non-secret-stable-id", "packName":"name", "packVersion":"version", "timezone":"Asia/Shanghai"},
  "serverTime": 0
}
```

Stats are current archive/pack stats. Previous login excludes the current session. Unknown historical values remain unknown (0/null), never invented. Existing login history/context and in-memory statistics are reused. Scope IDs are identity, not credentials. For client cache isolation, `scope.archiveId` is the persistent game save/world UUID, consistently available even when the web bridge is disconnected; it need not equal the database archive row UUID. Cached authenticated bridge-hello data may supply pack name/version, with additive `metadataAvailable` and `metadataStatus` (current/cached/unavailable/standalone). Stats stay usable independently of missing pack metadata. No admin endpoint URL, password, network topology or all-player roster is exposed.

## Titles

Preserve existing integer `icon` (CustomModelData). Add a separate `presentation` object to definitions:

```json
{"badge":"leaf", "rarity":"none", "theme":"slate"}
```

- Badge IDs: leaf, mountain, star, house, moon, wheat, sun, cloud, personal. Personal is an icon, not an access rule.
- Rarity IDs: none, common, rare, treasured. Labels: no label, 普通, 稀有, 珍藏; stable gentle colors. Old definitions default to none, not an invented rarity.
- Theme IDs: rose, terracotta, amber, wheat, olive, sage, jade, teal, blue, indigo, lavender, mauve, cocoa, slate. Soft restrained glass-compatible colors, never neon/full-saturation primaries. Slate is the legacy default. Title glyph colors remain independent.
- Maintain one canonical preset palette in the Titles source and provide resolved colors with the player snapshot, so Hub does not guess or duplicate palette values.
- Web editor: named badge/rarity/theme preset controls, live card preview, themed dark/light controls; preserve existing editor structure, holder tab, text editor and CustomModelData. Theme choices show both the name and a color swatch. The preview reuses the approved Hub web card's original markup/display rules, bound to the current draft and scaled proportionally inside the existing drawer; do not substitute a simplified strip or refactor the native renderer solely for preview sharing. Persist definitions, backups/import/export, selective undo and compatibility constructors. Missing metadata gets defaults; invalid edits get validation errors.
- Current administrator entry is the Manager integrated plugin page and unified API. Visual acceptance/screenshots must use the current Manager app shell and Titles route. The old standalone MemorialTitles shell is not the user's current console and must not be presented as its result. If a browser fixture uses mock HTTP, label that evidence separately from actual authenticated service save/readback tests.

`snapshot {}` returns `{revision, equippedTitle, displayPreferences, globalDisplay, titles, nextCursor}`. `titles` contains only definitions visible to this player (public catalogue or owned), with definition fields and:

```json
{"owned":true,"acquiredAt":0,"publicCollection":true,"publicDisplay":true,
 "presentation":{"badge":"leaf","rarity":"none","theme":"slate"},
 "palette":{"accent":0,"light":0,"rarityColor":0,"rarityLabel":""}}
```

Colors are 24-bit RGB. `name` remains the existing RichText segments/gradients. If paging is needed, `catalogue {cursor,limit}` supplies remaining rows tied to a stable snapshot revision, and Hub finishes loading or shows an explicit incomplete/error state. Do not truncate or fetch other players' private data.

Player mutations use explicit target values (not retry-unsafe toggles), with an expected revision where meaningful:

- `equip {title, expectedRevision}`; empty title unequips; ownership checked server-side.
- `visibility {visible, expectedRevision}`; changes publicDisplay of the equipped title only, preserving collection privacy and equipped state.
- `displayPreferences {head,chat,tab,expectedRevision}`; preserve reminder setting and global administrator restrictions.

Mutations return committed state/snapshot. Selected card is only UI state. Existing rich text, hidden catalogue, retired-but-owned titles and permission semantics stay intact. No automatic achievement engine is added from example acquisition text.

## Fortunes

A safe record is `{id,date,at,fortune:{id,title,grade,poem,interpretation}}`, where `at` is the real draw timestamp and fortune is the stored historical snapshot. Never include validity tokens, weights, unpublished definitions, raw internal settings or full backend records.

`snapshot {}` returns:

```json
{"revision":"opaque","timezone":"Asia/Shanghai","today":"YYYY-MM-DD","todayState":"undrawn",
 "todayRecord":null,"archiveCount":0,"recent":[],"featuredDate":null,"featuredRecord":null}
```

Today states: undrawn, unread, read, archived. Undrawn/unread contain no result details. Read may show the already interpreted today's record but does not enter collections. `recent` contains only archived records (small bounded list).

- `calendar {month:"YYYY-MM"}` returns `{revision,month,today,timezone,records}`; archived records only.
- `read {date:"YYYY-MM-DD"}` returns safe archived record, or the player's already interpreted today's record; unread is rejected.
- `feature {date:"YYYY-MM-DD"|"",expectedRevision}` persists or clears personal handbook selection in existing database storage, requiring an archived owned record. Returns committed selection/state.

This personal selection never changes a physical chest. Hub does not remotely draw, resolve, archive, consume or recover items. Existing physical workflows and shared chest behavior are retained. In managed mode use existing unified storage namespace; standalone mode stays in the existing scoped fortune database, not a separate player database. Handle deleted/restored records and invalid featured references safely. Use server timezone and online day rollover.

## Hub rendering and lifecycle

- The approved React source and native visual implementation remain the design baseline. Preserve glass, fonts, motion, head hat layer, pedestal, spacing and input hit testing.
- Actual in-world panels show provider data/loading/unavailable states, never demo fallbacks. Explicit title-screen demo mode remains available for visual reference only.
- Initial server snapshot discovers optional provider availability/protocol. Missing modules cannot pretend empty ownership; disable relevant actions/pages with a clear concise reason. Failure in one provider does not block the others.
- Preload Manager statistics, the complete paged title catalogue and the small recent/today fortune snapshot once per login, before opening the panel. The connection owns the data session: closing the screen disposes GPU resources but retains data and processes confirmed change hints. Opening or navigating to a ready page does not clear or reload it. Explicit retry is allowed for missing data. Do not periodically poll or scan while open or closed. Defer change refresh until active writes/catalogue paging finish; cancel/ignore stale UI results. Online duration advances locally from its confirmed baseline; the next server midnight may cause one expiration/read, without a repeating scan. Event coalescing and request throttling never generate requests by themselves.
- Local reduced motion, density and welcome preferences are scoped by UUID and stable server/archive identity; once/login/always keep approved semantics. Persisted personal feature is server-owned.
- All title names use existing rich glyph formatting; never substitute card theme for text color. Long names clip/fit with readable full detail, never overlap controls. Rich text parity includes per-glyph color, bold, italic, underline and strike.
- Home current pack stats, exact previous-login time, titles counts/filter/equip/visibility, today states, archive month/list/reading/feature must use real responses and acknowledge only committed actions.
- Client JVM stays free of database/admin credentials. Dedicated server startup cannot load Skija/client classes.

## Session and historical cache extension (0.2.5-dev)

- Provider Java protocol stays v1; no Manager, Titles, Fortune backend schema or administration API change. Both Hub endpoints advertise `sessionSync:1` in `hub/discover`. The client opts in with `hub/subscribe {}` before reading providers. Older peers retain full-response compatibility.
- Subscribed snapshots and title pages use ordered connection-local envelopes: `_hubSync`, `stream`, `sequence`, `base`, and either `full` or a recursive field/array `patch`. Unchanged fields are omitted; deletions and explicit nulls are distinct. Every received envelope advances the wire baseline before obsolete UI replies are discarded. Baselines are bounded identically on both ends; an evicted stream restarts with a full response. Logout/stop removes server state and client session data. None of this state is stored in the database.
- Change notifications reuse the providers' existing title/fortune confirmed-change channels. They trigger a coalesced read whose Hub response contains a delta; opening a page is not a substitute for change notification. There is no periodic refresh. Cached UI stays visible while requests are pending, and the client still cannot commit actions optimistically.
- Archived history is the explicit exception to logout cleanup. Only archived month responses are persisted locally under `cache/pixelmc-hub/archives/<scope hash>/<YYYY-MM>.json`. Scope is derived from player UUID plus stable server/archive IDs; if that authoritative identity is unavailable, history remains session-only. Preferences remain separate. History files are disposable, size bounded, checksum validated and written atomically on one background IO worker. Late callbacks cannot populate a disconnected/new session.
- History is not downloaded wholesale on login. On viewing a month, show an available memory/disk copy first. `fortunes/calendarSync {month, known}` sends `known` as a JSON string mapping dates to canonical SHA-256 hashes of the records actually loaded on this client. The Hub server queries the existing authenticated `calendar` provider and compares those hashes; it does not trust them for ownership or request another player.
- The monthly response contains metadata plus `updated`, `removed`, `total`, `unchanged` and `recordsHash`. Only changed/new archived records carry content; deleted dates are removed locally. The client verifies the reconstructed complete month before displaying/persisting the authoritative result. A mismatch with an existing cache triggers one full-month recovery. Empty/missing/corrupt cache on another computer sends an empty summary and receives every current record, regardless of whether that account downloaded them elsewhere.
- Server-side history is authoritative. Client cache never changes visibility/ownership validation, never contains unread results, and logout never deletes persistent titles, fortune records or statistics.

## Verification and handoff

Each agent reports changed paths, contract deviations before implementation, appropriate build/test evidence, unresolved limitations and artifact paths. Do not commit/push unrelated pre-existing changes. Root coordinates Git and private change log. Do not claim successful local gameplay or production acceptance from builds/static checks.
