# Views

The UI is implemented with Jetpack Compose and Navigation Compose.

## Main views

| View | Purpose |
| --- | --- |
| `BeatClikrScreen` | Root scaffold, navigation graph, dialogs, and adaptive layout |
| `MetronomeContainerView` | Compact switcher between instant and polyrhythm modes |
| `MetronomeView` | Tempo, groove, pattern, ramp, sound, and transport controls |
| `PolyrhythmView` | M-against-N controls, timelines, playhead, and transport |
| `SongLibraryView` | Song list, selection, editing, and transport |
| `PlaylistListView` | Playlist creation, rename, deletion, and navigation |
| `PlaylistDetailView` | Ordered entries, song picker, editing, and transport |
| `PlaylistFocusView` | Full-screen distraction-reduced playlist transport |
| `PracticeHistoryView` | Calendar, daily activity, streaks, and sharing |
| `SettingsView` | Sound, behavior, appearance, flashlight, and reminder settings |

## Navigation and adaptation

Top-level routes cover Instant, Polyrhythm, Library, Playlist, History, and Settings, with a parameterized playlist-detail route. Compact layouts use a bottom navigation bar and place polyrhythm inside the metronome container. Expanded layouts use a navigation rail, expose polyrhythm directly, and limit content width to 840 dp.

Every top-level destination change issues one explicit global playback stop. Switching directly between instant and polyrhythm also stops the interface being hidden. Navigation inside Library and Playlist—including details, editors, pickers, sheets, and focus mode—preserves playback; explicitly playing another song replaces the session at tick zero.

## Shared components

Reusable components include BPM and groove controls, odd-meter selection, sound pickers, calendar cells, song rows and forms, playlist transport, the pulsing metronome indicator, section cards, and the shareable streak card.

The theme uses custom light and dark palettes; dynamic color is intentionally disabled to preserve BeatClikr branding.

## UI validation

Automated Compose coverage exists, but TalkBack, large fonts, RTL, landscape, tablet/foldable behavior, contrast, and reduced-motion behavior still require a formal acceptance matrix.
