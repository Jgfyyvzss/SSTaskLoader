# Development notes

Narrative context that doesn't fit CLAUDE.md's terse gotcha list - background,
open decisions, and a roadmap. Update this alongside CLAUDE.md when something
here gets resolved or superseded.

## Origin

Built as a native Kotlin/Compose reimplementation of the idea behind
[xcomps](https://github.com/DanielDe8/xcomps) (a Capacitor/Svelte app doing the
same job for SoaringSpot/GlideAndSeek), targeting SoaringScoring's own Public
API instead. Chose native Kotlin over Capacitor because the whole app is
"HTTP → JSON → SAF file write," which doesn't benefit from a web-view layer,
and scoped-storage file access maps more directly onto Kotlin/SAF APIs than
through a Capacitor plugin bridge.

## Distribution model & the API key tension

The SoaringScoring team issues **one API key to the app**, not one per user -
most of this app's users never need to touch an API key at all. That key is
baked in via `local.properties` → `BuildConfig.SS_API_KEY` at build time, and
is deliberately never committed to git.

This creates a real tension with F-Droid distribution: F-Droid builds strictly
from public source with no access to private secrets, so an F-Droid-built copy
would always compile with an empty key, and every F-Droid user would need to
supply their own personal key via Settings' override field - undermining the
"just works" experience that GitHub-distributed builds get. Two ways to
resolve this if F-Droid distribution becomes a priority:

1. Accept it - F-Droid users self-serve a key via the existing override.
2. Build a small server-side proxy that holds the key, so *no* distributed
   copy (GitHub or F-Droid) ever needs to contain it. Bigger lift, cleanly
   solves the problem.

Not yet decided. Dependency-wise, the app is otherwise a clean F-Droid fit -
no ads, no trackers, no Google Play Services, every dependency is Apache 2.0
(AndroxX, OkHttp, kotlinx.serialization).

## Feature history (roughly chronological)

- **Task download** - fetch a contest's tasks, download the XCSoar `.tsk` for
  a selected task, write to the correct XCSoar folder(s).
- **Multi-folder support** - tick XCSoar and/or XCSoar Jet; writes go to every
  ticked folder.
- **Contest categorization & drill-down** - Current/Future/Past tabs
  (`ContestGrouping.categorize`), month-grouped lists matching SoaringScoring's
  own site, card-based UI, class-selection chips, timeframe-aware task
  filtering (Current shows only today's/most-recent task; Future/Past show
  everything for the selected class).
- **Waypoint download** - one action per contest (not per task, since the
  turnpoint set doesn't change day to day), writes `soaringscoring_waypoint.cup`
  to the `waypoints`/`Waypoints` subfolder (falls back to root on older
  XCSoar versions with no such folder).
- **IGC flight upload (v1, manual)** - separate top-level screen (not folded
  into the contest drill-down, since a pilot's entry address has no
  discoverable link to a specific contest via the API). Requires the pilot's
  *own* personal `flights:write`-scoped key plus their
  `{competitionNumber}-{contestKey}` entry address, both entered once in
  Settings. Browses `.igc` files from the `logs`/`Logs` subfolder across every
  selected XCSoar folder, confirms before sending, shows a dedicated result
  dialog (not a snackbar) given the real stakes of a flight-scoring upload.
  **Likely to be replaced by DustDevil.cloud sign-in - see below** - this
  manual version was always understood as a stopgap pending that
  conversation with the SoaringScoring dev.

## DustDevil.cloud sign-in (proposed)

**Status: proposed, discussed with the SoaringScoring dev, not yet
implemented.** This would replace IGC upload's current manual key/address
entry (above) with a real sign-in flow. Read `SoaringScoring
DustDevil.cloud_sign-in_for_your_app` in the repo's doc files for the full
protocol before implementing any of this - what follows is a summary and the
reasoning behind the proposed design, not a substitute for that doc.

**Context**: DustDevil.cloud is the pilot-facing side of the SoaringScoring
platform (event discovery, entry, payment) - a separate surface from the
Public API this app otherwise talks to.

**Why this is a better fit than generic OAuth**: DustDevil's OAuth server
doesn't support PKCE, so a native app can't run the standard authorization
code flow directly against it. Instead, SoaringScoring proxies the whole
exchange through their own already-registered OAuth client - our app never
talks to DustDevil directly, only to SoaringScoring's own API. This is
meaningfully less work on our side than implementing `AppAuth`/PKCE ourselves
against DustDevil.

**The two insights that shape the design**:

1. The exchange response hands back a pilot's contest entries with `localPart`
   **ready to use** directly with the Task Distribution and Flight Upload
   APIs - "no need to ask the pilot for it." This eliminates the
   hand-typed-entry-address problem entirely, not just the key problem.
2. The exchange call itself authenticates with **our app's own key**
   (`Authorization: Bearer ssk_live_...`), not a pilot-specific secret.
   Cross-referenced against the original Flight Upload API doc's trust model
   ("a partner integration ... uses one `flights:write`-scoped key across all
   of them, same as the Task Distribution API") - this suggests we may not
   need per-pilot upload keys at all. If `flights:write` gets added to our
   existing app-wide key, uploads could work exactly like task/waypoint
   downloads already do: one shared key baked into the app, with pilot
   *identity* established by sign-in rather than by a personal secret. This
   would remove the personal-key field from Settings entirely, not just the
   address field.

**Proposed flow**:

1. One-time setup with the SoaringScoring dev (not code): add
   `contests:read` + `flights:write` to our existing app key (or a second
   key with those scopes), register our app's redirect URI against it, get
   the key's public `client_key_id`.
2. "Sign in with SoaringScoring" button in Settings, replacing the manual
   entry-address field (and possibly the personal key field too, per the
   insight above).
3. Opens a **Chrome Custom Tab** (not a WebView - login credentials should
   never pass through a view our app code could inspect) at
   `/api/auth/dustdevil/mobile-start?client_key_id=...`.
4. A manifest intent-filter on our custom scheme (e.g.
   `xcsoaringscoring://oauth-callback`) catches the redirect with the
   short-lived (2 minute), single-use code.
5. Redeem immediately - POST to `/auth/dustdevil-mobile/exchange` using our
   app's own key. Must happen right away; the doc is explicit the code
   expires fast and can't be redeemed by a different key than the one that
   started the flow.
6. Store the result (pilot name/email + full entries list: contest, class,
   competition number, `localPart`) in DataStore. Upload flow becomes: signed
   in? → pick an entry from the list (or remember the last one) → upload
   using the app's key + that entry's `localPart`.
7. **Keep the manual address field as a fallback**, not a full replacement -
   the doc notes a contest DustDevil knows about but hasn't synced to
   SoaringScoring yet just won't appear in the entries list ("not an error"),
   so there's a real scenario where sign-in won't surface an entry a pilot
   actually needs.

**Open questions for the SoaringScoring dev** (answer these before writing
code):

1. Can `contests:read` + `flights:write` be added to our existing key, with
   a redirect URI registered against it?
2. What's that key's public `client_key_id`?
3. Shared-key-vs-per-pilot tradeoff, explicitly - a shared key means a leak's
   blast radius is "every pilot using this app," same exposure our existing
   `tasks:read` key already has. Per-pilot keys isolate a compromise to one
   pilot but reintroduce the manual-copying friction this whole proposal is
   trying to remove. Their call, not ours to assume.
4. What custom URI scheme to register - depends on the app's final
   package/display name, which is itself still an open item below.

## Known incidents worth remembering

- **Case-sensitive folder names caused silent data loss.** One XCSoar install
  used `Tasks`, another used `tasks`; the app's exact-case lookup only ever
  matched one of them, so overwrites silently succeeded on one device while
  the other quietly accumulated duplicate `Tasks (1)`, `Tasks (2)` ... folders
  that XCSoar never read from. Root-caused by testing on two real devices with
  two different XCSoar versions - not something reproducible from a single
  test device. Take device-specific folder/file behavior seriously; don't
  assume one confirmed-working device generalizes.
- **A scoped update once shipped without all its dependent files.** The
  waypoint "get waypoints" button was added across three files in one update;
  a follow-up fix touched two *different* files. If a branch was created from
  a point in history before the first update merged, applying just the second
  update's files left the UI referencing state the ViewModel didn't have
  wired up (or vice versa). Lesson: when re-applying a partial update onto an
  uncertain branch state, prefer re-shipping the *complete* set of files a
  feature touches, not just the newest diff.
- **A zip overlay once nested the project inside itself** (`SSTaskLoader/`
  containing another `SSTaskLoader/`), because extracting a zip whose root
  folder matches the destination folder's name merges incorrectly by default
  in some extraction tools. Silently caused Android Studio to keep building a
  stale copy for a while. When overlaying a scoped update: drag the *contents*
  of the top-level folder when it should merge into an existing folder, or
  drag the folder *itself* when its contents are meant to land at a new,
  correctly-named path one level down (e.g. a package folder like `app/`
  landing on top of an existing `app/`). Getting this backwards is the
  single most common failure mode in this project's update process.

## Release process

1. Bump `versionCode` (+1, never reuse) and `versionName` in
   `app/build.gradle.kts` (skip both for a genuine first release).
2. Build → Generate Signed Bundle/APK → release variant. Output filename is
   auto-versioned (`SSTaskLoader-<versionName>-release.apk`) via a Gradle
   `applicationVariants.all` hook.
3. GitHub → Releases → Draft a new release → tag `vX.Y.Z` against `main` →
   attach the signed APK → publish.
4. Large/risky changes go through a feature branch + PR review before landing
   on `main`, not straight commits - see branching workflow discussed in
   project history if this needs re-explaining to a new contributor.

## Open items / roadmap

- **In-app help** - planned: a brief help entry in Settings (dialog, not a
  full screen) covering first-run setup and where files land. Not yet built.
- **DustDevil.cloud sign-in** - see the dedicated section above. Would
  replace IGC upload's manual key/address entry with real sign-in and a
  proper multi-entry picker. Pending confirmation from the SoaringScoring
  dev on the open questions listed there before any code gets written.
- **F-Droid submission** - blocked on the API-key distribution decision above,
  plus the usual F-Droid prerequisites (LICENSE file, committed Gradle
  wrapper, `fdroiddata` metadata PR).
- **Custom launcher icon** - currently a hand-drawn top-down glider silhouette
  vector (banked, gull-wing style), replaceable via Android Studio's Vector
  Asset tool or by generating new VectorDrawable XML directly.
- Project/display name: app label is currently a simple string resource
  (`app_name`); a full package/applicationId rename was considered but
  deliberately deferred as higher-risk than it's worth for a solo project at
  this stage.
