# SS Task Loader

A small Android app largely based on [XComps](https://github.com/DanielDe8/xcomps) 
that loads task files from [SoaringScoring](https://soaringscoring.com)'s Public API 
straight into XCSoar (and XCSoar Jet), so you don't have to download and copy a 
`.tsk` file by hand each contest day.

Same idea as XComps, just built native (Kotlin + Jetpack Compose) against SoaringScoring
instead of SoaringSpot/GlideAndSeek.

## What it does

1. Lists contests from `GET /api/v1/public/contests` (no key needed).
2. You pick a contest, and it lists that contest's published tasks via
   `GET /api/v1/public/contests/:id/tasks` (needs an API key with the
   `tasks:read` scope).
3. You grant the app access to your `Android/media` folder once (system
   folder picker) — it finds any subfolder with "soar" in the name
   (`org.xcsoar`, `com.zinuzoid.xcsoar_jet`, future forks, etc.) and lets you
   tick which ones to write to.
4. Tapping the download icon on a task fetches the XCSoar `.tsk` file
   (`files.xcsoarTsk` from the tasks response) and writes it to
   `<that folder>/Tasks/soaringscoring_task.tsk`, overwriting each time —
   same filename-per-overwrite pattern as xcomps.

## Status
Functional.

## Project layout

```
app/src/main/java/com/soaringscoring/taskloader/
  api/                  OkHttp client + data models for the Public API
  data/SettingsRepository.kt   DataStore: API key, last contest, saved folder tree URI
  storage/XcsoarFolderStore.kt SAF folder scan + file write
  ui/AppViewModel.kt    All app state + orchestration
  ui/screens/           Compose screens (contests, tasks, settings)
  MainActivity.kt       NavHost + SAF picker launcher
```

## API key management (app-wide key, not per-user)

Since SoaringScoring is issuing one key to the app rather than one per user,
the key is baked in at build time rather than typed in by each person:

1. `local.properties` (already gitignored) holds the real key in `ss.apiKey=...`.
2. `app/build.gradle.kts` reads that into `BuildConfig.SS_API_KEY`.
3. `AppViewModel` uses that as the default; the Settings screen only holds a
   *personal override*, for testing with your own key later — most users
   will never open it.

## Building

Open the project root in Android Studio (Koala or newer). 
Targets Gradle 8.7

Minimum SDK 26, target/compile SDK 34. No special permissions beyond
`INTERNET` — folder access goes through Storage Access Framework, not
`WRITE_EXTERNAL_STORAGE`, so it keeps working under scoped storage.

## Why SAF and not a plain file path

Since Android 11, apps can't touch `Android/data/**` or (on some versions)
`Android/obb/**` via SAF at all — but `Android/media/**` is *not* on that
blocklist, which is presumably why XCSoar stores its files there in the first
place. So: pick `Android/media` once via `ACTION_OPEN_DOCUMENT_TREE`, persist
the permission, and read/write through `DocumentFile` from then on.

## Known limitations / good next steps

- **DHT (Distance-Handicap) days** aren't handled specially yet — the API
  returns one row per glider handicap for those, with `dhtHandicap` /
  `dhtDistanceKm` set. Right now they just show up as extra rows; it'd be
  worth grouping them or letting the user filter to their own handicap.
- **No offline cache** — every screen re-fetches. Fine for contest use, but
  a local cache of the last-loaded task would help on bad campsite wifi.
- **No waypoint/airspace download** — xcomps also ships `.cup` waypoints and
  airspace files; SoaringScoring's public API doesn't expose airspace, and
  waypoints would come from the `seeyouCup` file if wanted later.
- **Class filtering** — right now all classes' tasks show in one list; for a
  multi-class contest you'll likely want a class picker so you're not
  scrolling past tasks that aren't yours.
- **Folder permission can be revoked by the OS** on reinstall/storage
  changes — worth adding a "recheck access" step on launch that silently
  re-prompts if the persisted URI permission is gone.
- **App icon** — manifest currently omits `android:icon` so it builds without
  extra asset work; add a real launcher icon before you publish anywhere.
