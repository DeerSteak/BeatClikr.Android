# Data and backup policy

| Data | Location | Android backup | Policy |
| --- | --- | --- | --- |
| Songs and playlists | Room database | Included | User-authored data; preserve on restore. |
| Practice history | Room database | Included | Preserve stable civil days and qualified history. |
| Preferences | SharedPreferences | Included | Restore through versioned codecs, legacy aliases, bounds, and safe defaults. |
| Generated PCM | Cache directory | Excluded | Regenerable from packaged resources. |
| Diagnostics | Generated in memory or transient share data | Excluded | User-triggered, bounded, and not retained by default. |
| Proprietary WAV resources | Signed APK resources | Not app-data backup | Reinstalled with the authorized application package. |

Both backup rule formats use an inclusion allowlist containing only the Room database, its journaling files, and the application preference file. Cache, files, diagnostics, and generated PCM are therefore excluded. A restored version 4 database migrates through the tested 4→5 migration before use.
