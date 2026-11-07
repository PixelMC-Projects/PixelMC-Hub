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

Hub discovers these optional providers reflectively, with no compile-time hard dependency. The player UUID is taken only from the authenticated game connection. Private operations use only the authenticated player. Explicit community public reads may select a known player UUID and return only approved public fields. Providers validate player/runtime/admission as appropriate and reject unknown actions. Only explicitly listed player operations are forwarded; never general Manager RPC, administrative actions, raw snapshots or SQL.

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
- Preload Manager statistics, the complete paged title catalogue and the small recent/today fortune snapshot once per login, before opening the panel. The connection owns the data session: closing the screen releases transient frame/target resources while retaining bounded application graphics resources and session data and processes confirmed change hints. Opening or navigating to a ready page does not clear or reload it. Explicit retry is allowed for missing data. Do not periodically poll or scan while open or closed. Defer change refresh until active writes/catalogue paging finish; cancel/ignore stale UI results. Online duration advances locally from its confirmed baseline; the next server midnight may cause one expiration/read, without a repeating scan. Event coalescing and request throttling never generate requests by themselves.
- Local reduced motion, density and welcome preferences are scoped by UUID and stable server/archive identity; once/login/always keep approved semantics. Persisted personal feature is server-owned.
- All title names use existing rich glyph formatting; never substitute card theme for text color. Long names clip/fit with readable full detail, never overlap controls. Rich text parity includes per-glyph color, bold, italic, underline and strike.
- Home current pack stats, exact previous-login time, titles counts/filter/equip/visibility, today states, archive month/list/reading/feature must use real responses and acknowledge only committed actions.
- Client JVM stays free of database/admin credentials. Dedicated server startup cannot load Skija/client classes.

## Session and historical cache extension (0.2.5-dev)

- Provider Java protocol stays v1; no Manager, Titles, Fortune backend schema or administration API change. Both Hub endpoints advertise `sessionSync:1` in `hub/discover`. The client opts in with `hub/subscribe {}` before reading providers. Older peers retain full-response compatibility.
- Subscribed snapshots and title pages use ordered connection-local envelopes: `_hubSync`, `stream`, `sequence`, `base`, and either `full` or a recursive field/array `patch`. Unchanged fields are omitted; deletions and explicit nulls are distinct. Every received envelope advances the wire baseline before obsolete UI replies are discarded. Baselines are bounded identically on both ends; an evicted stream restarts with a full response. Logout/stop removes server state and client session data. None of this state is stored in the database.
- Change notifications reuse the providers' existing title/fortune confirmed-change channels. They trigger a coalesced read whose Hub response contains a delta; opening a page is not a substitute for change notification. There is no periodic refresh. Cached UI stays visible while requests are pending, and the client still cannot commit actions optimistically.
- Archived history and the later changelog disk cache are explicit exceptions to logout cleanup. Only archived month responses are persisted locally under `cache/pixelmc-hub/archives/<scope hash>/<YYYY-MM>.json`. Scope is derived from player UUID plus stable server/archive IDs; if that authoritative identity is unavailable, history remains session-only. Preferences remain separate. History files are disposable, size bounded, checksum validated and written atomically on one background IO worker. Late callbacks cannot populate a disconnected/new session.
- History is not downloaded wholesale on login. On viewing a month, show an available memory/disk copy first. `fortunes/calendarSync {month, known}` sends `known` as a JSON string mapping dates to canonical SHA-256 hashes of the records actually loaded on this client. The Hub server queries the existing authenticated `calendar` provider and compares those hashes; it does not trust them for ownership or request another player.
- The monthly response contains metadata plus `updated`, `removed`, `total`, `unchanged` and `recordsHash`. Only changed/new archived records carry content; deleted dates are removed locally. The client verifies the reconstructed complete month before displaying/persisting the authoritative result. A mismatch with an existing cache triggers one full-month recovery. Empty/missing/corrupt cache on another computer sends an empty summary and receives every current record, regardless of whether that account downloaded them elsewhere.
- Server-side history is authoritative. Client cache never changes visibility/ownership validation, never contains unread results, and logout never deletes persistent titles, fortune records or statistics.

## Changelog extension (0.3.0-dev, local candidate)

Manager Forge 1.20.1 and its independent service provide `com.pixelmc.pixelmcmanager.api.PlayerHubUpdatesApi`, Java protocol 1, exposed to Hub as `updates`. Manager remains server-only. This feature requires the matching 0.7.0-dev Manager mod/service plus Hub 0.3.0-dev on client and server; it is not a port to other loaders. Existing Titles/Fortunes providers and the mobile administration routes retain their contracts.

- Player identity comes only from the admitted Minecraft connection. The bridge RPC is `playerhub/playerHub` with `[authenticated UUID, action, payload]` and the same admission proof. No administrator operation or arbitrary identity is exposed. Both game bridge and service enforce admission and scope.
- `snapshot {}` preloads settings, the first bounded pack page and 12 initial entry summaries once per login. Fields: `settings`, `packs`, `packsTotal`, `packsPage`, `packsHasMore`, `selectedPackId`, `list`, `revision`. The article and the player's own conversations load on demand. Cached content stays visible during ordinary refreshes; authoritative withdrawal/hiding/disable removes unavailable content. Logout clears connection data, drafts and pending responses, while the bounded disk-cache extension retains already-read data.
- `packs {page}` returns `{rows,total,page,size:100,hasMore,revision}`. Extra pack pages are explicitly reachable; there is no silent first-page-only catalogue. `list {packId,page,search}` returns at most 12 published visible summaries, pinned first. Only packs from the authenticated instance can be read.
- `read {id,commentsPage,replyPages?}` returns `{entry,comments,revision}`. Comments are paged by 12 and limited to this player's UUID; each thread returns 20 replies with total/page/hasMore. `replyPages` contains at most 12 comment IDs. An ID in this map does not grant access to someone else's thread.
- `like {id,liked}` uses an explicit desired boolean. `comment {id,text,operationId}` is idempotent, bounded to the configured 50–2000 character limit and cooldown (defaults 500 and 10 seconds). `deleteComment {id,commentId}` deletes only the player's own thread. State is shown as committed only after a successful server response.
- Service hello advertises `playerHubApi:1`; hello/snapshot/bootstrap carry an epoch-qualified `playerHubRevision`. A changed token rides the existing bridge/SSE connection and invalidates the connection-owned cache. Refreshes are coalesced, never created by a new periodic scanner. Updates use bounded page responses and the content-unit cache negotiation rather than the existing recursive snapshot-delta stream.
- Plain request cap is 8192 UTF-8 bytes; the updates-only cache negotiation has its own bounded manifest cap. The updates response cap is 2 MiB; larger native replies use bounded connection/request-scoped packet fragments because Minecraft's single clientbound custom packet limit is 1 MiB. Other modules retain their 512 KiB cap. Oversized/malformed/stale responses fail clearly and never become a different session's data.
- Restricted rich content supports paragraphs, heading level 3, bold text, bullet lists and line breaks. Arbitrary HTML, images and embeds are not accepted. Legacy Markdown is display-compatible. Native Skija performs the equivalent text layout without a browser or remote fonts.
- Pack groups use stable instance plus full pack name, independent of ordinary world/archive replacement. A version change updates group metadata; entries keep their own short pack-name/version snapshots. A new full pack name starts a new group, with the preceding group hidden by default. Administrators can hide or restore any entire pack, including current, without deleting entries or interactions.
- Manager owns `ph_settings`, `ph_packs`, `ph_instances`, `ph_entries`, `ph_likes`, `ph_comments`, `ph_replies`, `ph_operations` in existing unified storage. Existing global profiles and avatar cache are reused. Native-table manifests participate in complete backup/restore and global player deletion/recovery; no database credentials enter Hub.

The production Manager entry uses `/api/playerhub/*` with the existing authenticated session and CSRF boundary. Its Word-style editor reuses the player article presentation; prototype browser storage stays isolated. Entry deletion requires the exact persisted title and revision and removes its interactions. Archived pack history remains accessible to administrators regardless of player visibility.

This is a local integration candidate, not a deployment or gameplay acceptance record. Build artifacts and isolated verification evidence are retained in the private integration handoff; do not treat automated rendering as full client compatibility acceptance.

## Changelog persistent cache and scrolling

This user-approved extension supersedes the earlier updates-only session-cache decision. UI lists, comments and replies scroll continuously while the existing backend keeps bounded pages. A next-page read is driven by actual scroll/viewport demand, not an idle polling timer. Authority refreshes replace/reconcile their page; appending another page must not resurrect deleted rows or duplicate IDs. Preserve the visible reading anchor.

The client retains bounded, checksum-validated atomic cache files keyed by player UUID, stable Manager server ID and canonical action/query. Archive/world ID is intentionally excluded from the changelog scope so an ordinary world replacement can reuse the same pack history. No trusted identity means session-only storage. Logout invalidates in-flight callbacks and connection state while keeping disposable disk data.

Cache negotiation is an optional Hub transport capability, not a new administrative or SQL API. Every request still invokes the same admitted own-player provider before comparing client-held hashes. Content-addressed semantic units separate entry metadata/body/document, each summary/pack, comment metadata and individual replies. Missing units are sent, changed root/order metadata reconciles deletions, and unchanged content is reused. Missing/damaged or different-computer caches supply an empty digest; reconstruction is verified and may fall back once to a full response. The client must never claim possession merely because this account downloaded content on another machine.

Disk data is used for negotiation/reconstruction on a new connection, then injected only after current authoritative confirmation. Current-session confirmed UI stays visible during ordinary refresh. Visibility denial clears affected visible content; historical data never grants permission. Stable Manager identity is established before initial updates cache preload, with a bounded session-only fallback on identity failure. Other providers keep their existing behavior.

Both peers explicitly negotiate support. Old peers continue with full DTOs. Plain requests and all other modules retain their limits; updates-only digest envelopes are bounded separately. Replies still fit the updates-specific fragmented transport. Mutations retain explicit target values, request generation, operation-id idempotency and server confirmation.


### Implemented transport and resource bounds

`updatesCache:1` negotiates snapshotSync/packsSync/listSync/readSync/commentSync/deleteCommentSync; like retains its small ordinary response. Plain requests remain 8 KiB; these allowlisted cache requests are capped at 64 KiB, with a flat literal-hash array capped at 800 units and 60000 characters. Missing units are never silently dropped. Response and pre-copy reconstruction budgets are 2 MiB; reconstruction also caps nodes at 40000. Encoding runs on the existing bounded provider worker pool, not the Minecraft server completion thread.

The persistent LRU spans at most 64 MiB and 256 query files. Connection memory retains at most 24 DTO query keys. An entry-scoped articleBase permits body reuse across comment/reply queries. Failed write reconstruction retries the identical action and operationId once; it never creates a second comment to repair a cache. Every accepted write and change hint invalidates pending read IO intents; purge also clears queued/in-flight update tickets. These checks are transport/cache safeguards, not an authorization substitute.


## Client graphics warmup

Graphics preparation is independent of authenticated player-data preloading. Client render ticks perform a bounded sequence of startup stages after loading is ready: native library, isolated shared GL context, Skija, local fonts, representative GPU work and bundled image batches. Warmup uses its own small offscreen target, not the live Minecraft color attachment or a real HubView. It must not advance welcome settings, submit player requests or display preview data in a live session. An early open can complete the required resources through the normal guarded path.

Application-owned common graphics remain reusable between screens. Closing a screen detaches its game target and releases frame snapshots, large scratch surfaces, runtime server icons and temporary text caches. Resource reload invalidates the generation and the active screen reacquires its renderer; final shutdown releases graphics while the original render thread/context remains available. Logout clears player skin/session state. Never move OpenGL calls to a generic worker or remove the existing isolated-context/fence boundary.

Player skins are unavailable at application startup. The later player-session path uses Minecraft's existing skin mechanism and an already registered texture; it does not add an HTTP downloader. Readback executes with an explicit isolated pixel-pack boundary and passes raw RGBA to Skija instead of PNG compression/decompression. A missing texture does not block startup or wait synchronously for the network; player changes and reload invalidate old skin content. A finite retry window plus pixel hashes handles fallback textures later updated under the same resource/GL identity. There is no indefinite GPU polling. An unusually slow skin upload beyond that window can wait for the next explicit open/reload opportunity.

Stage and first-visible-frame timings use a monotonic nanosecond clock and log milliseconds without player identifiers or credentials. They measure CPU-side work/submission, not the entire GPU pipeline. Staged warmup moves work earlier; a single native driver/library call is not preemptible. The real current scene/viewport still needs preparation on open. Hidden tests are not real-game latency or compatibility acceptance.

## Public leaderboards, local candidates 0.4.0-dev / Manager 0.8.0-dev

The optional `rankings` provider maps to Manager's `PlayerHubRankingsApi`, protocol 1. Discovery advertises `pixelmcmanager / leaderboards.read v1 / public-summary`. The allowlist contains `snapshot`, `page`, `aroundMe` and `window`. Connection identity supplies the viewer UUID. Rows expose only UUID, current name, approved avatar path, aggregate value, competition rank and optional rank delta.

The shared contract remains `src/leaderboards/contract.js`. Food and online time use the active archive. Archived distinct fortune kinds use the existing DailyFortunes business-global scope. Positive values rank, known zero remains unranked. Food is optionally read through the installed SOL Carrot capability and stored in a rebuildable projection. The adapter uses the same effective-food filtering as its book.

Login preloads three first pages. Session caches retain at most five pages per board, across panel closes, and are cleared on disconnect. The server sends board-specific committed change hints. Hidden or inactive boards defer refresh. A visible board refreshes its header and bounded page window under a single revision, retaining the old display and scroll until the response arrives. One hint-driven retry can repair a failed initial admission preload. Failures have bounded lifetime and await new events or explicit retry.

Manager coalesces simultaneous viewer requests into authenticated batches of up to 32 after 160ms, with a queue of 128 and one active batch. Each item retains independent identity and pre/post-response session checks. The internal batch RPC is outside the player's action allowlist. Ordinary per-player requests stay within 8KiB/512KiB transport limits.

Manager 0.8.0-dev migrates three `ph_ranking_*` projection tables and advertises an 11-table PlayerHub storage manifest v2. Complete backup, empty-database restore and global player deletion/recovery include these tables. Deploy the matching Manager service and game bridge with the Hub candidate on both sides. The new FortuneBackend aggregate is bundled into Manager service, so this feature needs no extra standalone DailyFortunes installation step.



## Community profiles and journey memories

Hub 0.5.0-dev uses the optional `community` provider `com.pixelmc.pixelmcmanager.api.PlayerHubCommunityApi`, protocol 1, capability `communityApi=1`. Manager 0.9.0-dev game bridge and independent service provide the implementation. Native live mode displays confirmed server records and explicit unavailable states. The formal Manager application uses authenticated administrator HTTP, with its preview adapters isolated from the production dependency graph.

Player actions are `snapshot`, `profile`, `profileOptions`, `memories`, `memory`, `memoryOptions`, `saveProfile`, `saveNote`, `appendSupplement`, `deleteSupplement` and `saveCollections`. Public reads accept a known `playerId`. Options and all writes derive the owner from the game connection. Public profiles expose selected objects only. They never include another player's private option catalogues.

Profile patches allow `bio`, `background`, `profilePattern`, `featuredTitleId`, `featuredFortuneDate` and `featuredMemoryId`. The fortune selection updates the existing DailyFortunes `featured_date` in the same database transaction as the profile. Explicit null clears the selection and is preserved through the bridge. Title and fortune candidates come from actual source ownership and acquisition events. Missing historical timestamps stay unavailable. Archive collections must fall within the archive's real time interval.

Current original notes are owner-editable. Archived originals remain owner-read-only. Supplements use a UUID `operationId` for idempotent append and tombstones prevent retry resurrection. Owners may delete their supplements strictly within 24 hours using server time. Administrator routes permit editing and deletion of originals and supplements of any age. Revisions are opaque CAS tokens and conflicts retain the local draft.

Bounded pages contain 100 archive/options rows or 20 supplements. Native windows retain continuous nearby pages, scroll anchors and selected full objects. Per-query connection caches are limited to 48 entries and 8 MiB. Logout clears private cache and drafts. Server `communityRevision` changes generate subscribed connection-generation hints. The client refreshes open targets after committed hints, with deferred reads while hidden. No periodic full scan is added. Wire deltas remain ordered even when a UI response is stale.

Client-only `_hubViewRequestId` identifies each view request. It is retained in local queue metadata and removed before transmission. It is excluded from transport stream hashes. It never becomes a provider field. Failures retain the original query so expired or superseded targets cannot overwrite newer editors. Timeout, malformed response and queue/transport rejection terminate pending UI state.

Manager registers PlayerHub native manifest v3 with 15 tables, adding four `ph_community_*` tables. Full registered backup/restore and player deletion/recovery include community data. Downgrading the v3 manifest through an old binary is rejected. Rollback requires the verified pre-upgrade database and matched old binaries, with newer writes retained separately for review.

The detailed administrator routes, DTOs and migration contract are maintained in the Manager repository at `manager-service/docs/playerhub-community-api.md`. Archive cover metadata currently returns explicit default artwork, `arcs` with current `sage` or historical `mist`. Administrator pack-cover selection remains deferred.
