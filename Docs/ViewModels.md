# ViewModels

Hilt constructs Android ViewModels and supplies repository, preference, playback, secondary-output, flashlight-setting, and reminder interfaces. Compose observes a mixture of `StateFlow` and snapshot state.

## ViewModel responsibilities

| ViewModel | Responsibility |
| --- | --- |
| `MetronomeViewModel` | Standard/song playback controls, tempo, groove, accents, ramping, tap tempo, pulse state, and practice recording |
| `PolyrhythmViewModel` | M-against-N configuration, playback, visual indices, sound selection, and practice recording |
| `SongLibraryViewModel` | Song list, selection, transport navigation, and edit-draft validation |
| `PlaylistViewModel` | Playlist CRUD, membership, ordering, selection, and transport navigation |
| `PracticeHistoryViewModel` | Date selection, calendar aggregation, streak calculations, and share text |
| `SettingsViewModel` | Preferences, sound banks, output options, flashlight permission flow, and reminder permission/scheduling flow |

`RampController` is a plain state helper used by `MetronomeViewModel`; it is not an Android ViewModel.

## Dependency injection

`AppModule` binds interfaces to process-scoped implementations. ViewModels receive interfaces rather than constructing services, which supports JVM tests with fakes. `BeatClikrApplication` prewarms audio and observes process lifecycle; `MainActivity` supplies window-level theme and keep-awake behavior.

## State ownership limits

Playback ViewModels project the application-scoped coordinator's authoritative transport state. They also expose retained secondary-output failure diagnostics without allowing haptic or torch failures to alter healthy audio transport.

Timing callbacks also cross from audio scheduling into Choreographer-based visual state. See [PlaybackPerformance.md](PlaybackPerformance.md) for the clock and measurement constraints.
