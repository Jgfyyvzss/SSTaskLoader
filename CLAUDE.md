# CLAUDE.md

Context for Claude Code (or any coding agent) working in this repo. Keep this
file current when architecture or conventions change - it's read automatically
at the start of a session.

## What this is

XCSoaringScoring (originally "SS Task Loader") - an Android app that fetches
contest tasks, waypoints, and flight-upload access from the SoaringScoring
Public API and loads them straight into XCSoar / XCSoar Jet, replacing a manual
download-and-copy routine during gliding competitions.

## Stack

- Kotlin + Jetpack Compose (Material 3), single-Activity + Navigation Compose
- OkHttp + kotlinx.serialization for the API client (no Retrofit)
- DataStore Preferences for settings (not SharedPreferences)
- Storage Access Framework (SAF) for reading/writing XCSoar's folders - **not**
  plain file APIs, and **not** `MANAGE_EXTERNAL_STORAGE`
- minSdk 26, targetSdk/compileSdk 34
- AGP **8.5.2** + Gradle **8.7** + Kotlin **1.9.24** - this combination is
  load-bearing, see "Gotchas" below before touching any of these versions

## Where things live

```
app/src/main/java/com/soaringscoring/taskloader/
  api/              SoaringScoringApi.kt (OkHttp client), Models.kt (all @Serializable data classes)
  data/             SettingsRepository.kt - DataStore-backed settings
  storage/          XcsoarFolderStore.kt - all SAF folder/file resolution logic
  ui/               AppViewModel.kt (single ViewModel, single AppUiState) +
                     ContestGrouping.kt (date categorization/grouping/filtering, pure functions)
  ui/screens/       Compose screens - one file per screen, plus FolderPicker.kt
                     (two composables: MediaFolderAccessSetting for Settings,
                     TargetFolderCheckboxes for the home screen)
  MainActivity.kt   NavHost + SAF folder-picker launcher
  util/             DateFormat.kt - dateOnly() strips time-of-day from API dates
```

Single `AppViewModel` + single `AppUiState` data class for the whole app - not
one ViewModel per screen. All screens read from and call into this one state
holder.

## Build

Open the project root in Android Studio and use Run/Build - this is the
primary, tested workflow. There's no guarantee `./gradlew` works standalone
from a terminal in this environment; if you need CLI builds, verify the
wrapper actually invokes Gradle 8.7 first (see Gotchas).

## Gotchas - read before changing these

1. **AGP/Gradle version pairing is fragile.** AGP 8.5.2 requires Gradle 8.x;
   jumping to Gradle 9.x (which Android Studio may substitute silently if the
   wrapper isn't properly bootstrapped) causes an internal R8/Kotlin-compiler
   crash that looks nothing like a version-mismatch error. If a build fails
   with an obscure internal tooling exception (ArrayIndexOutOfBounds,
   NoSuchMethodError inside `com.android.tools.r8.internal.*`, etc.), check
   the actual Gradle version in use before debugging anything else.

2. **XCSoar's subfolder names vary by install - always case-insensitive, never
   hardcode a single case.** Different XCSoar versions/forks use `Tasks` vs
   `tasks`, `Waypoints` vs `waypoints`, `Logs` vs `logs`. All folder resolution
   goes through `XcsoarFolderStore`'s shared `resolveSubfolderOrRoot()` -
   never add a new hardcoded-case folder lookup elsewhere.

3. **Never create XCSoar's own folders.** `findXcsoarFolders()` and every
   subfolder resolver only ever *find* existing folders (`tasks`, `waypoints`,
   `logs`) - they never call `createDirectory()` for these. Those folders are
   always created by XCSoar itself; if a subfolder genuinely doesn't exist
   (older XCSoar version), the code falls back to the XCSoar folder's root
   rather than creating a new one.

4. **`DocumentFile.findFile()` can silently miss existing files/folders** on
   some devices/providers. Once a target file/folder is resolved, its URI gets
   cached (`fileUriCache` in `XcsoarFolderStore`) and reused directly rather
   than re-searching on every write - re-searching every time previously
   caused duplicate `Tasks (1)`, `Tasks (2)` ... folders to appear.

5. **The live SoaringScoring API requires a key on every endpoint**, including
   `/contests` and `/classes`, despite the docs describing those as needing no
   key. Send the effective key everywhere.

6. **`dhtHandicap` is a `Double`, not an `Int`** - real DHT handicap values are
   fractional (e.g. 0.86). Getting this wrong causes a JSON parse crash on any
   contest with non-integer handicaps.

7. **Contest/task dates from the API sometimes carry a bogus time-of-day**
   (`T00:00:00.000Z` on everything, even genuinely time-sensitive contests) -
   `util/dateOnly()` strips it for display, but be aware same-day contests can
   be mis-bucketed into Current vs Past because the API just doesn't expose
   real end times. Not fixable client-side.

8. **The app-wide API key (`BuildConfig.SS_API_KEY`) only exists on locally
   built APKs** - it comes from a gitignored `local.properties` (`ss.apiKey=`)
   that never reaches F-Droid's build server or a fresh clone. Don't assume
   it's present; the Settings screen's "personal override" field is the
   fallback path for any build that doesn't have it baked in.

9. **When editing multiple files for one change, keep them together.** This
   project's update workflow has been: edit files here, zip just the changed
   ones, drag into the GitHub repo. A partial set (e.g. a UI file added in one
   update, its ViewModel wiring in another, applied out of order across
   branches) has caused real regressions - see DEVELOPMENT.md's "Known
   incidents" for a concrete example.

10. **IGC upload's current manual key/address entry is likely to be replaced,
    not extended.** The implemented version (personal `flights:write` key +
    hand-typed `{competitionNumber}-{contestKey}` address in Settings) is a v1.
    A DustDevil.cloud OAuth sign-in flow is proposed to replace both fields
    entirely - see DEVELOPMENT.md's "DustDevil.cloud sign-in" section before
    building anything further on top of the manual approach. Check whether
    that proposal has since been implemented, agreed, or dropped before
    assuming the current Settings fields are the long-term design.

## Conventions

- No Retrofit - plain OkHttp with manual `Request`/`Response` handling in
  `SoaringScoringApi`, wrapped in a small `ApiResult<T>` sealed class
  (`Success`/`Failure`), not exceptions.
- Errors from the API get mapped to specific documented error codes
  (`MISSING_API_KEY`, `INVALID_API_KEY`, `INSUFFICIENT_SCOPE`, etc.) into
  human-readable messages via `describeError()`/`describeUploadError()` in
  `AppViewModel` - extend these when adding new endpoints rather than showing
  raw API error text.
- Card-based UI (Material 3 `Card`), not `ListItem` rows, for anything
  representing a distinct item (contests, tasks, IGC files).
- Read the API docs in the repo (`SoaringSCoring_API.md`,
  `SoaringScoringUpload_API.txt`) before assuming endpoint behavior - the live
  API has diverged from the docs at least twice already (auth requirements,
  and Current/Past categorization for same-day contests).
