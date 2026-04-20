# BeatClikr Android

A high-precision metronome app for Android built with Jetpack Compose and MVVM architecture.

## Architecture Overview

BeatClikr follows a clean MVVM architecture with a dedicated services layer for audio. The app is a single-module Android project organized into four main layers:

```
UI Layer (Compose)
    ↓
ViewModel Layer
    ↓
Services Layer (Audio Engine)
    ↓
Data Layer (In-memory)
```

---

## Project Structure

```
app/src/main/java/com/bfunkstudios/beatclikr/
├── MainActivity.kt
├── constants/
│   └── MetronomeConstants.kt
├── data/
│   ├── ClickerType.kt
│   ├── DataSource.kt
│   ├── Song.kt
│   ├── SongListUiState.kt
│   ├── SoundFile.kt
│   └── Subdivisions.kt
├── services/
│   ├── AudioPlayerService.kt
│   ├── MetronomeAudioEngine.kt
│   └── MetronomeTimer.kt
└── ui/
    ├── BeatClikrScreen.kt
    ├── InstantMetronomeView.kt
    ├── MetronomeViewModel.kt
    ├── SongListViewModel.kt
    ├── components/
    │   ├── MetronomePlayerView.kt
    │   ├── SongDetail.kt
    │   └── SongList.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

---

## Data Layer

### Models

**`Song`** — The core domain model.
```kotlin
data class Song(
    var title: String,
    var artist: String,
    var beatsPerMinute: Float,
    var beatsPerMeasure: Int,
    var subdivisions: Subdivisions,
    var liveSequence: Int?,
    var rehearsalSequence: Int?,
    val id: UUID
)
```

**`Subdivisions`** — Enum defining rhythm subdivision modes: `Quarter`, `Eighth`, `Triplet`, `Sixteenth`.

**`ClickerType`** — Enum for the three metronome modes: `LIVE`, `INSTANT`, `REHEARSAL`.

**`SoundFile`** — Enum mapping the 15 bundled `.wav` drum/percussion samples (kick, snare, hats, cymbals, etc.) to their raw resource IDs. Provides filtered lists: `beatSounds` (13 sounds) and `rhythmSounds` (10 sounds).

**`SongListUiState`** — Immutable UI state snapshot used by `SongListViewModel`.

### DataSource

`DataSource` is an in-memory singleton that stores a mutable list of `Song` objects. It ships with 5 pre-loaded songs. `saveSong(song)` inserts a new song or updates an existing one matched by UUID. There is no on-disk persistence.

---

## Services Layer

### `MetronomeAudioEngine`

The heart of the app. Responsible for low-latency audio playback and precise timing.

- Uses **Android `SoundPool`** for sub-5ms jitter audio.
- Timing is driven by `SystemClock.elapsedRealtimeNanos()` — monotonic and drift-free.
- A `Handler` loop fires every 1ms (configurable via `MetronomeConstants`) and checks whether the next beat is within a 2ms lookahead window before firing.
- Tracks a subdivision counter: `0` fires the **beat** sound, `>0` fires the **rhythm** sound.
- Beat duration is computed in nanoseconds from the BPM.
- Notifies callers via a `MetronomeAudioEngineDelegate` callback interface.

Key timing constants (from `MetronomeConstants`):
| Constant | Value | Purpose |
|---|---|---|
| `TIMER_CHECK_INTERVAL_MS` | 1ms | How often the timer loop wakes |
| `LOOKAHEAD_TOLERANCE_MS` | 2ms | Fire early if beat is this close |
| `FIRST_BEAT_DELAY_MS` | 67ms | Startup delay before first beat |

### `AudioPlayerService`

A singleton facade that wraps `MetronomeAudioEngine`. The ViewModels interact only with this service, not the engine directly. Implements `MetronomeAudioEngineDelegate` to relay beat callbacks upward.

Public API:
- `setupAudioPlayer(beatResourceId, rhythmResourceId)` — Load sound files.
- `startMetronome(bpm, subdivisions)` — Begin playback.
- `stopMetronome()` — Stop playback.
- `updateTempo(bpm, subdivisions)` — Update tempo while running.
- `release()` — Free audio resources.

### `MetronomeTimer`

An alternative timer implementation also using `elapsedRealtimeNanos()`. It calculates a dynamic check interval (`subdivisionDuration / 50`, minimum 1ms). Not used in the current main playback flow but retained as an alternate implementation.

---

## ViewModel Layer

### `MetronomeViewModel` (AndroidViewModel)

Manages all state for the Instant Metronome screen. State is held with the Compose `mutableStateOf` / `mutableFloatStateOf` APIs.

**State:**
| Property | Range/Type | Description |
|---|---|---|
| `isPlaying` | Boolean | Playback running |
| `beatsPerMinute` | 30–240 Float | Current tempo |
| `selectedSubdivisions` | Subdivisions enum | Current subdivision mode |
| `selectedBeatSound` | SoundFile | Beat click sound |
| `selectedRhythmSound` | SoundFile | Subdivision click sound |
| `iconScale` | 0.3–1.0 Float | Beat pulse animation scale |

**Tap Tempo** — `recordTap()` accumulates timestamps, averages the last 8 tap intervals, and resets if more than 2 seconds pass between taps.

**Beat Animation** — When a beat fires via the delegate callback, `iconScale` snaps instantly to `ICON_SCALE_MAX` (1.0), waits 16ms, then animates back to `ICON_SCALE_MIN` (0.3) over one beat duration (60,000 / BPM ms). This gives a sharp attack and smooth decay that tracks the current tempo.

**Lifecycle** — Stops audio in `onCleared()` to prevent playback leaking beyond the screen.

### `SongListViewModel` (ViewModel)

Manages song list state using `StateFlow<SongListUiState>`. Simpler than MetronomeViewModel — no audio concerns.

- `setSelectedSong(uuid)` — Sets the selected song for editing.
- `saveSong(song)` — Calls `DataSource.saveSong()` then refreshes the UI state.

---

## UI Layer

Navigation is handled by **Jetpack Navigation Compose**. Routes are defined as an enum in `BeatClikrScreen.kt`:

```
InstantMetronome  (default/home)
SongList
SongDetails
```

A `Scaffold` with a Material3 `TopAppBar` wraps the `NavHost`.

### Screens

**`InstantMetronomeView`** — The main screen. Contains:
- An animated **MetronomePlayerView** circle (BPM pulse indicator).
- Large BPM display with Tap Tempo button.
- BPM slider with ± buttons (bounded 30–240).
- Subdivision selector (Quarter / Eighth / Triplet / Sixteenth).
- Beat and Rhythm sound dropdown menus.
- Play/Pause toggle.

Uses `DisposableEffect` to call `setupMetronome()` on entry and `stopMetronome()` on exit.

**`SongList`** — A `LazyColumn` of songs with an "Add Song" button. Tapping a song navigates to `SongDetails`.

**`SongDetail`** — A form for editing a song's title, artist, BPM (slider), and beats per measure, with Cancel/Save actions.

### Components

**`MetronomePlayerView`** — A composable circle that animates its scale based on `iconScale` from the ViewModel. Uses `animateFloatAsState` with `LinearEasing` for the decay animation.

### Theme

The app uses a custom **Material3** color scheme with dynamic color disabled.

- **Accent:** Orange `#FF5722`
- **Light primary:** Blue `#408CC9`
- **Dark primary:** Darker blue `#0E6A96`
- **Surfaces:** Pure white (light) / `#1C1C1E` dark gray (dark) — iOS-style dark surface.
- Status bar styling adapts to light/dark mode.

---

## Build Configuration

| Setting | Value |
|---|---|
| Min SDK | 25 (Android 7.1) |
| Target/Compile SDK | 35 |
| Kotlin | 2.2.10 |
| Android Gradle Plugin | 9.1.0 |
| Compose BOM | 2023.08.00 |
| Navigation Compose | 2.7.7 |
| Lifecycle ViewModel Compose | 2.6.1 |

No network or device permissions are required.

---

## Audio Assets

15 `.wav` drum samples are bundled in `res/raw/`:

| Sound | File |
|---|---|
| Click Hi | `clickhi_e5.wav` |
| Click Lo | `clicklo_f5.wav` |
| Cowbell | `cowbell_gsharp3.wav` |
| Crash L/R | `crashl_csharp3.wav`, `crashr_a3.wav` |
| Hi-Hat Closed/Open | `hatclosed_fsharp2.wav`, `hatopen_asharp2.wav` |
| Kick | `kick_c2.wav` |
| Ride Bell/Edge | `ridebell_f3.wav`, `rideedge_dsharp3.wav` |
| Snare | `snare_d2.wav` |
| Tambourine | `tamb_fsharp3.wav` |
| Tom Hi/Mid/Low | `tomhi_d3.wav`, `tommid_b2.wav`, `tomlow_a2.wav` |
| Silence | `silence_d7.wav` |
