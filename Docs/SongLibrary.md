# Song Library, Playlists, and Practice History

## Song library

Songs persist title, artist, tempo, beats per measure, groove, and optional odd-meter pattern. The library supports creation, editing, deletion, selection, and previous/next transport. Playing a song loads its musical settings into the standard metronome.

Draft validation currently requires nonblank title and artist, clamps tempo to 30–240 BPM, and clamps beats per measure to 1–16.

## Playlists

Playlists contain ordered `PlaylistEntry` rows referencing songs. Users can create, rename, and delete playlists; add or remove songs; reorder entries; and play through previous/next controls or Focus Mode.

Ordering mutations are currently coordinated in repository/ViewModel code. Moving add, delete, resequence, and reorder invariants into Room transactions is required before concurrent mutation is considered safe.

## Practice history

Practice activity is aggregated per local calendar day and stable practiced-item ID. History displays practiced dates, daily items, current and longest streaks, and a shareable streak card.

The accepted normative behavior is in [`Decisions/0003-Practice-History.md`](Decisions/0003-Practice-History.md). It defines confirmed playback, the cumulative 30-second daily threshold, repeated-period accounting, stable identity, iOS-parity local-day checkpoint attribution, travel, timezone changes, and daylight-saving transitions.

The current transport records practice from ViewModel behavior and does not yet implement that contract. The target playback state machine must confirm meaningful audible playback before history is written, preventing focus-denied or immediately interrupted sessions from counting.

## Reminders

Users can choose a daily reminder time. Android notification permission is handled explicitly, boot completion reschedules enabled reminders, and settings surface denied or deferred states.

Android currently stores this data locally; it has no iCloud-equivalent cross-device song, playlist, history, or reminder synchronization.

## Database policy

Room version 4 is the supported baseline. Exported schemas live under `app/schemas/`. Every future production version requires a data-preserving migration and instrumentation fixture covering songs, playlists, entries, practice sessions, and relationships.
