# BeatClikr Android
BeatClikr's Android implementation with Jetpack Compose, Hilt, MVVM architecture, and SoundPool audio. Mirrors the iOS app's architecture and feature set.

Note: The 15 `.wav` drum/percussion samples are bundled in `res/raw/` and tracked in git. No additional setup is needed to build and run the app.

## Architecture Overview

BeatClikr follows an MVVM architecture with a clean separation of concerns:

### Models
- **Song** - Core domain model (title, artist, BPM, beats per measure, groove, live/rehearsal sequence index)
- **Subdivisions** - Enum defining subdivision types (quarter notes, eighth notes, triplets, sixteenths)
- **ClickerType** - Enum distinguishing instant vs. live vs. rehearsal metronome modes
- **SoundFile** - Enum mapping the 15 bundled `.wav` samples to their raw resource IDs; provides filtered lists `beatSounds` and `rhythmSounds`
- **SongLibraryUiState** - Immutable UI state snapshot for the song library screen

### ViewModels
- **MetronomeViewModel** - Orchestrates metronome playback, coordinates with `IAudioPlayerService`, handles UI state (beat pulse animation, isPlaying, BPM, tap tempo)
- **SongLibraryViewModel** - Handles song library CRUD operations and exposes `StateFlow<SongListUiState>` to the UI

### Screens
- **BeatClikrApp** - Root composable; hosts the `NavHost` and `TopAppBar` inside a `Scaffold`
- **InstantMetronomeView** - Standalone metronome with live BPM/groove controls and tap tempo
- **SongList** - Browsable song list; tap a song to navigate to its details, + to add a new one
- **SongDetail** - Add or edit a song's metadata (title, artist, BPM, beats per measure, groove); presented as a `ModalBottomSheet` over the song library rather than a separate navigation destination

### Components
- **MetronomePlayerView** - Animated circle that pulses with each beat; scale driven by `iconScale` from `MetronomeViewModel`
- **SectionCard** - Shared card surface (`RoundedCornerShape(16.dp)`, surface color) used across all form screens
- **BpmSliderControl** - `OutlinedIconButton(−)` / `Slider` / `OutlinedIconButton(+)` row with secondary-color styling and built-in `coerceIn` clamping
- **GrooveSelector** - 2×2 grid of `GrooveButton`s for picking a subdivision type
- **SoundPickerRow** - Label + dropdown row for selecting a beat or rhythm sound; owns its own expanded state

### Services Layer
- **MetronomeAudioEngine** - Low-latency metronome engine using `SoundPool` and `SystemClock.elapsedRealtimeNanos()` for precise timing; supports mute (suppresses audio but still fires delegate callbacks for animation and haptics)
- **IAudioPlayerService** - Interface for the audio service, enabling dependency injection and unit testing without a real audio session
- **AudioPlayerService** - Singleton implementation of `IAudioPlayerService`; facade over `MetronomeAudioEngine`. Implements `MetronomeAudioEngineDelegate` and re-exposes a `delegate` property for ViewModel callbacks
- **MetronomeTimer** - Alternative timer implementation retained for reference; not used in the active playback path

### Data Layer
- **IAppPreferences** - Interface for user preferences, enabling dependency injection and unit testing without SharedPreferences
- **AppPreferences** - Implementation of `IAppPreferences` backed by `SharedPreferences` via the AndroidX KTX `edit { }` API

### Dependency Injection
- **AppModule** - Hilt `@Module` providing `IAudioPlayerService` and `IAppPreferences` as `@Singleton` bindings

### Constants
- **MetronomeConstants** - Timing parameters, BPM ranges, animation scale values, and tolerance thresholds

## About Keeping Time

Sample-accurate timing is critical for a metronome. The current implementation uses **Android `SoundPool`** with **`SystemClock.elapsedRealtimeNanos()`** for high-precision beat scheduling.

### How it works:

The `MetronomeAudioEngine` uses a polling approach with extremely tight tolerances:

```
Check Interval:      1ms  (Handler loop on dedicated HandlerThread "MetronomeThread")
First Beat Delay:   67ms  (ensures timer is running before first beat)
Lookahead Tolerance: 2ms  (fires beat slightly early to account for processing)
```

**Example with 100 BPM, 8th note subdivisions:**
- Subdivision duration: 60,000 / (100 BPM × 2 subdivisions) = 300 milliseconds
- Handler fires every 1ms to check if it's within 2ms of the next scheduled beat
- When threshold is met, plays the appropriate sound (beat or rhythm) and notifies the delegate

This approach provides:
- **<5ms jitter** - beats stay locked to the tempo
- **No drift** - uses monotonic `elapsedRealtimeNanos()` rather than cumulative intervals (note: Android's monotonic clock is superior to iOS's `CFAbsoluteTimeGetCurrent()` — it does not jump if the system wall clock changes)
- **Real-time tempo changes** - BPM and subdivisions can be updated while playing

### Delegate Pattern

`MetronomeViewModel` implements `MetronomeAudioEngineDelegate` to receive beat callbacks:
- Triggers visual beat-pulse animation (`iconScale` snaps to MAX, decays to MIN over one beat duration)
- `AudioPlayerService` sits between the engine and the ViewModel, relaying callbacks via its own `delegate` property
- Callbacks always fire even when muted, so animation and haptics remain active

## About Audio Playback

BeatClikr Android relies on **Android `SoundPool`** for sound playback, configured with `USAGE_GAME` and `CONTENT_TYPE_SONIFICATION` to route through Android's low-latency audio path.

### Sound Architecture:
- WAV files are loaded into `SoundPool` at startup via `loadSounds()`
- Beat vs. rhythm (subdivision) sounds are selected based on a subdivision counter
- Two sound IDs are active at any time: `beatSoundId` and `rhythmSoundId`
- Supports instant sound switching by calling `setupAudioPlayer()` with new resource IDs
- When muted, `SoundPool.play()` is skipped but the delegate callback still fires — identical behaviour to the iOS implementation

The `AudioPlayerService` manages:
- Loading audio files from `res/raw/`
- Delegating start/stop/update/mute calls to the engine
- Providing a single shared instance to all ViewModels via Hilt's `@Singleton` binding

## Tap Tempo

The Instant Metronome includes a **Tap Tempo** button displayed as a circle to the right of the BPM display. Tapping it calculates BPM from the average interval of the last 8 taps. The result is clamped to the app's min/max BPM range (30–240). If more than 2 seconds pass between taps, the tap history is cleared so a new tempo can be set.

## About the Song Library

Songs are persisted in a **Room database** and survive app restart. Songs are matched for update by UUID.

Songs include:
- Title and artist metadata
- BPM and beats per measure
- Groove/subdivision settings
- Optional live and rehearsal sequence indices

## Dependency Injection

BeatClikr uses **Hilt** for dependency injection. ViewModels are annotated with `@HiltViewModel` and receive their dependencies via `@Inject constructor`. Composables obtain ViewModels via `hiltViewModel()` rather than the plain `viewModel()` helper.

```kotlin
@HiltViewModel
class MetronomeViewModel @Inject constructor(
    private val audio: IAudioPlayerService,
    private val prefs: IAppPreferences
) : ViewModel()
```

`AppModule` provides the singleton bindings:
```kotlin
@Provides @Singleton
fun provideAudioPlayerService(@ApplicationContext context: Context): IAudioPlayerService =
    AudioPlayerService.getInstance(context)

@Provides @Singleton
fun provideAppPreferences(@ApplicationContext context: Context): IAppPreferences =
    AppPreferences(context)
```

This ensures:
- A single shared `AudioPlayerService` instance across the app
- No background metronome instances after the ViewModel is cleared
- Song list state is not lost when navigating into and out of song details
- Full testability — unit tests construct ViewModels directly with mock/fake dependencies

## Testing

### Unit Tests (`src/test/`)

`MetronomeViewModelTest` covers the full ViewModel surface using **MockK** and `kotlinx-coroutines-test`. No Android runtime is required — tests run on the JVM.

Coverage includes:
- Preference loading on init (BPM, subdivisions, sound selection)
- BPM clamping and prefs persistence
- Subdivision changes and `updateTempo` forwarding while playing
- Play/pause toggle, `startMetronome`/`stopMetronome` delegation
- Mute propagation — verifies `isMuted` is set on `IAudioPlayerService` before `startMetronome` is called
- Sound selection and prefs persistence
- `loadSong` state updates and `updateTempo` forwarding
- Beat/subdivision `iconScale` transitions
- Tap tempo accumulation and clamping

### UI Tests (`src/androidTest/`)

`InstantMetronomeViewTest` uses **Compose UI testing** with `createAndroidComposeRule<MainActivity>()` and Hilt's `@HiltAndroidTest`. The real `AppModule` is replaced via `@UninstallModules` with a `TestModule` that binds `FakeAudioPlayerService` and `FakeAppPreferences` — no real audio session or SharedPreferences involved.

Coverage includes:
- BPM display and label visible on launch
- Play/pause button toggle (text and audio service call counts)
- All four subdivision buttons visible
- Tap Tempo button visible

Run unit tests: `./gradlew test`
Run UI tests (requires device or emulator): `./gradlew connectedAndroidTest`

## Feature Parity Roadmap

Items remaining to match the iOS app, roughly in dependency order:

### Song Library Polish
- Add `android:autoBackup` backup rules to include the Room database, mirroring iOS's automatic CloudKit/iCloud sync

### Playlist Mode
- Add a `PlaylistEntry` model (ordered link between a `Song` and its sequence index)
- Add `PlaylistModeViewModel` — manages next/previous/play sequencing, edit, reorder, and delete
- Add `PlaylistModeView` — ordered `LazyColumn` with drag-to-reorder (`ReorderableLazyColumn`) and swipe-to-delete, inline edit mode
- Add `PlaylistTransportView` — floating Previous / Stop / Next bar shown while a song is active; pulses with the beat via `MetronomeAudioEngineDelegate`
- Add `SongPickerView` — bottom sheet for picking a library song to add to the playlist
- Wire up `liveSequence` and `rehearsalSequence` fields on `Song`, which are already modeled but unused

### Settings
- Add `SettingsViewModel` and `SettingsView` covering: default beat/rhythm sounds, haptics on/off, flashlight on/off, keep-awake on/off
- Persist settings with **Jetpack DataStore** (Android equivalent of iOS `UserDefaultsService`)
- Apply saved sound preferences as defaults when `MetronomeViewModel` initializes, instead of always defaulting to Click Hi / Click Lo
- ~~Mute support~~ — `MetronomeAudioEngine` now respects `isMuted`; suppress audio while keeping delegate callbacks active for animation and haptics ✓

### Feedback Options
- **Haptics** — Add a `VibrationService` using `VibrationEffect` / `HapticFeedbackManager` to pulse on each beat (mirrors iOS `VibrationService` using `UIImpactFeedbackGenerator`)
- **Flashlight** — Add a `FlashlightService` using `CameraManager.setTorchMode()` to flash the torch on each beat
- **Keep-awake** — Acquire a `WindowManager` `FLAG_KEEP_SCREEN_ON` (or `WakeLock`) while the metronome is playing so the screen doesn't turn off during practice
