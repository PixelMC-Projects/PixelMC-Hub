# Player integration acceptance ledger

Started 2026-11-04. Local implementation only. This ledger does not establish game acceptance or production deployment.

## Work ownership

| Area | Agent | Boundary |
| --- | --- | --- |
| Manager safe player API and compatible ports | hub_manager_api | Preserve pre-existing mobile work and existing HTTP contracts |
| Titles definitions, preset editor, safe player API and ports | hub_titles_metadata | Includes Manager's embedded Titles editor only |
| Hub optional network bridge and live native UI | hub_live_client | Keep the approved renderer and explicit demo preview |
| Fortune player projections and saved personal feature | hub_manager_api (second work item) | Preserve physical draw/resolve/archive/chest behavior |
| Contract, review, artifact inventory and private operations record | root | No production mutation |

## Required evidence

| Concern | Expected result | Status |
| --- | --- | --- |
| Identity | Network sender determines player; arbitrary UUID/action rejected | Protocol and authenticated isolated HTTP checks passed; actual game transport pending |
| Isolation | No admin credentials, hidden titles, unread results or validity tokens in player DTOs | Provider checks and actual managed response decoding passed |
| Manager stats | Current save totals, previous login excludes current, unknown metadata remains unknown | 30 Manager player API checks passed |
| Presets | Nine badge presets, optional rarity, muted theme families; numeric item icon preserved | Four loader builds, real admin save/readback and current Manager browser checks passed |
| Persistence | New metadata survives save/reload/export/import/backup; selective undo covers new fields | Titles isolated SQL/contract checks passed; no production migration |
| Title actions | Server-confirmed equip/unequip/hide; privacy and per-position restrictions preserved | Provider and Hub checks passed, including hidden equipped title on a later catalogue page |
| Fortune collection | Archived-only calendar/count/recent, real timestamps and server timezone | 42 player-projection checks and real managed DTO decode passed; profile and fortune zones isolated |
| Fortune feature | Database persistence, ownership validation, no changes to physical chest | Isolated standalone and managed storage checks passed; no physical world modified |
| Missing modules/errors | Explicit loading/unavailable/retry; no in-world demo fallback | Protocol/data checks and hidden GPU unavailable screenshot passed |
| Async lifecycle | Bounded event-driven refresh, stale-response rejection, no periodic polling | Hub 0.2.1 controller checks pass: 1000-second idle emits no requests; notifications/navigation merge; writes/paging retain changes; initial provider/discovery failures recover on explicit retry; closed hooks do no IO; one midnight expiration. Actual game recheck remains pending. Prior 17 same-JVM Manager lifecycle checks passed on all six targets |
| Settings | UUID/save isolation and once/login/always semantics remain intact | Preference/migration checks passed, including provisional identity before metadata |
| Dedicated-side loading | Common/server paths do not load client or Skija classes | Static source and provider checks only; actual dedicated Linux startup not performed |
| Compatibility | Existing Manager mobile/admin routes preserved; supported plugin versions build | Existing routes retained and six Manager/four Titles/four Fortune targets built; real phone reacceptance not performed |
| Visual rendering | Native renderer checks and source parity; actual game acceptance is user-owned | Current Manager dark/light screenshots reviewed; hidden GL checks passed; no Minecraft acceptance claimed |
| Packaging | Exact local artifacts + checksums; no release or automatic deployment | Final 15 mod builds and shared Manager service built; explicit collector verifies JAR metadata/classes and copied checksums |

## Scope exclusions

No automatic achievement engine is inferred from sample acquisition descriptions. No remote drawing, interpretation, archiving or physical chest edits from Hub. No new gameplay page or arbitrary remote HTML/code. No additional Hub Minecraft-version renderer port. No server restart, online database migration, client synchronizer publication or changes to the user's active game installation.

## Verified evidence and remaining boundaries

- Manager native mod builds passed on Forge 1.20.1, NeoForge 1.21.1 and Fabric 1.20.1 / 1.21.1 / 26.1.2 / 26.2. Final reruns include explicit-null preservation in the player bridge, 30 player API checks and 19 bridge-admission checks. Logs are private under the sibling `pixelmcmanager/validation-hub-manager-20261104` directory.
- Real isolated Manager HTTP bridge checks passed 39 assertions using a disposable local MariaDB. This caught and fixed missing hosted Titles admission binding and null fields lost during bridge JSON serialization. The extended checks include authenticated admin creation/save/readback, invalid-preset validation, stale revisions, unauthorized/cross-instance requests, and no raw administrative Snapshot. Evidence: `pixelmcmanager/forge/pixelmcmanager-forge-1.20.1/build/hub-tests/a04e9e16a8af/build.log`. Safe responses from that real fixture were decoded by Hub's own DTO checks. Ephemeral test credentials were removed when the owned service and database exited.
- DailyFortunes 0.4.0-hub.1 builds passed for all four existing loader targets, along with 31 existing isolated service checks and 42 new player-projection checks. The additional database preference schema is version 2. Rollback to old code requires the matching retained full backup; no production schema was touched.
- Native Hub automated protocol/queue, preference, DTO and hidden GPU screenshots/checks have been reported passing by its implementation agent. Root inspected `titles-rich.png` and `fortunes.png`; these use synthetic provider fixtures, not a running Minecraft session.
- The initial editor screenshots used the old standalone Titles shell and are superseded. The replacement reuses the approved Hub web card inside the actual current Manager AuthRoot/Titles route. Fifteen browser checks passed against real authenticated isolated Manager/MariaDB APIs, including save/readback, holder-tab draft retention, 14 theme swatches, 80-character name fitting, consistent light player material in both admin themes, and proportional fitting in the existing drawer. Root inspected the final dark and light screenshots at `MemorialTitles/web-console/screenshots/manager-player-card-20261104/`. No mock HTTP is used for this final evidence.
- Native Hub 0.2.0-dev protocol, preference, queue and DTO checks include a hidden equipped title on page two: visibility is unknown and noninteractive until the full catalogue resolves, then the explicit next value is computed from confirmed state.
- The Forge Titles login/admission ordering was reviewed. Manager marks local admission synchronously before the asynchronous remote login job, so the suspected normal-path missing-admission failure was ruled out; no speculative queue change was added.
- Manager's confirmed same-JVM bridge reuse defect was corrected across six targets: stopped executors are replaced only after bounded termination confirmation, per-save temporary state is cleared, identical extension registration is idempotent, and old callbacks/commands are gated by runtime generation. The original final synchronization order remains. Seventeen focused lifecycle checks passed on each target, alongside 19 admission and 30 player API checks. Logs: `pixelmcmanager/validation-hub-manager-20261104/manager-<target>-lifecycle-final.log`. These are direct Java checks without starting Minecraft and do not replace actual repeated world-entry acceptance.
- Titles 0.5.0-hub.1 passed 76 contract checks on each of four loader targets, 15 isolated SQL checks, and existing Sites/sorting checks. Each final JAR includes the same canonical palette and copied preview licenses. Manager's shared 0.6.2-hub.1 service JAR was rebuilt with current frontend resources and both updated backend providers.
- The user confirmed the prior phone app works. New code retains its HTTP contracts; that confirmation does not constitute real-phone testing of this new combined build.
- This integration remains local and uncommitted. No GitHub release, production deployment, client installation, live database access or game process launch is part of this handoff.

## 2026-11-04 local acceptance repair: Hub 0.2.1-dev

The earlier no-game-launch/client-installation statements describe the initial implementation handoff. The user subsequently performed local gameplay acceptance, recorded issues 01–05, and explicitly authorized their consolidated repair. This Hub follow-up implements issues 02–05; the repaired package still awaits the user's gameplay recheck. It does not establish production acceptance.

- Removed Hub's periodic refresh loop and automatic readiness retries. Existing Titles display/reset notifications and confirmed Fortune result notifications invalidate only the current open session. Explicit navigation/retry can refresh or rediscover unavailable modules. Request pacing/coalescing does not generate queries on its own.
- `acceptanceRefreshChecks` passed against the actual controller with a controlled clock and transport: 1000-second idle emits no requests; provider/discovery failures recover by explicit action; notification bursts merge; writes and catalogue paging retain pending changes; old replies and closed-session callbacks are ignored. A server-midnight deadline expires only once for a given day.
- `acceptanceCollectionChecks` passed for title-only, fortune-only, mixed and empty collections, exact timestamp ranking, new entries and confirmed online duration plus local elapsed time.
- `nativeAcceptanceChecks` passed in hidden OpenGL at 1440×1000 and 3840×2054: live background changes are blurred, opaque UI text pixels match the unblurred baseline, all captured GL state is restored, one pooled quarter-width/quarter-height filter target is used, and no additional full-screen backdrop is captured. All four recent-collection screenshots were generated and inspected. Grade labels measure 37 px for 27 px text plus equal 5 px padding on both sides. These checks do not measure real-game frame time.
- Synthetic screenshots: `.runtime/native-previews/acceptance-0.2.1/{scene-unblurred,scene-light-blur,recent-titles-only,recent-fortunes-only,recent-mixed,recent-empty}.png`.
- Final local Forge package: `minecraft/build/libs/pixelmc-hub-forge-1.20.1-0.2.1-dev.jar`, 12,236,268 bytes, SHA256 `BC4FCBA39A595BCFCA50434577B2B8D724D9C6A216A59D3E029A604486F63690`. The Hub implementation agent did not launch or control Minecraft, install the package, alter production, commit, push or release it. Root coordinates the separate authorized temporary-client update.

## 2026-11-04 second repair: Hub 0.2.2-dev

Issues 06–10 are implemented locally. Hello defaults to once per login without overwriting saved preferences. A connection-scoped, one-shot 11-second notice reads the actual key binding at display time. All in-panel feedback uses a white Swipe Toast adaptation with a blue-to-purple fuse and complete enter/exit animation. Titles stage a complete paged replacement before publishing; calendar data stays visible during feature mutations and refreshes. No periodic data requests were introduced.

Controlled checks passed for login timing/cancellation, saved preference retention, atomic paging, cached calendar and failed refreshes, stale-response handling, and no idle polling. Hidden GPU checks show pixel-identical title/count/button regions during unchanged refresh/paging and an unchanged calendar region during a feature reply. Toast entrance, fade-out, white surface, gradient and mouse input were checked; screenshots are under `.runtime/native-previews/polish-0.2.2`. This is not gameplay acceptance; the same owned temporary world is to be reopened for user review. No production deployment, database change or publication is part of this repair.

## 2026-11-04 login chat palette follow-up: Hub 0.2.3-dev

The user reviewed 0.2.2-dev in the temporary game and requested richer login-chat colors matching the existing Titles reminder. The notice now uses the exact gold `#f2c66d`, blue-gray `#b6c4d7`, cyan `#5dd4ed` and green `#80caa2` palette by semantic section. Wording, actual key lookup and the one-shot 11-second timing are unchanged. The local build passed; only Hub was replaced in the same owned temporary client after normal game exit, retaining its world, settings, other mods and services. User visual recheck is pending. This is not a production release.

## 2026-11-04 user acceptance completed

The user subsequently confirmed the final 0.2.3-dev local game build: “可以了 没问题了”. The preceding pending-recheck statements are superseded for this local acceptance round. The owned temporary game, Manager, database and supervisor are stopped, their ports released, and the temporary contents removed. One empty temporary root remains because automatic policy review rejected its final removal; no alternate deletion method was used. Source, candidate artifacts and acceptance records remain available. No production deployment, other-loader gameplay acceptance, commit, push or release is implied.
