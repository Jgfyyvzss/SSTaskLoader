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
- **IGC flight upload** - separate top-level screen (not folded into the
  contest drill-down, since a pilot's entry address has no discoverable link
  to a specific contest via the API). Requires the pilot's *own* personal
  `flights:write`-scoped key plus their `{competitionNumber}-{contestKey}`
  entry address, both entered once in Settings. Browses `.igc` files from the
  `logs`/`Logs` subfolder across every selected XCSoar folder, confirms before
  sending, shows a dedicated result dialog (not a snackbar) given the real
  stakes of a flight-scoring upload.

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
- **IGC upload - single entry address only.** Settings stores one default
  address; a pilot flying multiple contests in a season would need to update
  it each time. Extending to multiple stored addresses (keyed per contest) is
  a plausible v2 if this friction turns out to matter in practice.
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
