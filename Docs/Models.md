# Models

BeatClikr stores structured user data with Room and represents musical choices with Kotlin enums and value objects.

## Room entities

| Model | Purpose |
| --- | --- |
| `Song` | Title, artist, tempo, meter, groove, optional odd-meter pattern, and legacy sequence fields |
| `Playlist` | Named ordered collection with creation time |
| `PlaylistEntry` | Song membership and sequence within a playlist |
| `PracticeSession` | One local calendar day of practice |
| `PracticedSong` | Aggregated practice count and a snapshot of the practiced item |

`PlaylistEntry` cascades deletion from its playlist or song. Practice items cascade from their session. UUIDs are persisted through Room converters. Relation projections (`PlaylistWithEntries`, `PlaylistEntryWithSong`, and `PracticeSessionWithSongs`) assemble screen-ready graphs without making them independent entities.

## Practice-day identity

Practice sessions are grouped by the device's local calendar day. Repeated practice of the same `songId` increments `timesPracticed`; metronome and polyrhythm activity use stable synthetic IDs. This policy intentionally matches the day shown in the history calendar rather than UTC.

Room version 4 is the supported migration baseline. Unknown versions 1–3 are destructively recreated; migrations after version 4 must preserve user data and ship with schema fixtures.

## Musical models

- `Groove` defines quarter, eighth, triplet, sixteenth, and odd-meter modes.
- `BeatPattern` converts additive groupings such as 3+2+2 into accent arrays.
- `PolyrhythmGrid` maps two counts onto their least-common-multiple grid.
- `SoundFile` maps each selectable instrument to acoustic and synthetic Android resources.
- `SoundBank` selects acoustic or synthetic mappings.
- `ClickerType` distinguishes instant and song playback contexts.

Several enums currently contain display text. Moving presentation strings to localized resources remains remediation work.
