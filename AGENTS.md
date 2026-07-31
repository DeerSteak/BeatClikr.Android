<<<<<<< Updated upstream
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.bfunkstudios.beatclikr.music.PureCoreQualificationTest'
=======
# Agent Instructions

- Keep inline comments and docstrings concise, with no more than two lines.
- Do not hard-wrap Markdown or other documentation prose.
- Prefer durable documentation over large inline comment blocks, and trim redundant comments after documenting the design context.
- Keep proprietary acoustic WAV files untracked. Preserve the resource directory with `.gitkeep` and ignore its WAV files.
- Treat the sibling iOS app as the behavioral parity reference unless an approved Android contract explicitly differs.
- Update individual action-plan checkboxes as their work is completed.
- Use unit tests and targeted short tests for ordinary changes. Use a 30–60-second smoke test for timing or audio changes, a five-minute stress test for major timing milestones, and matched long resource or battery runs only for release or TB-018 qualification.
- Before physical-device testing, verify that each device has only one active ADB transport.
- Use the `benchmark` build with `-Pbeatclikr.testBuildType=benchmark` for release-equivalent instrumentation. Keep normal CI on debug tests.
- Preserve unrelated user changes in a dirty working tree.
- When the checked-out local branch is the pull request branch, review the code on disk instead of fetching the PR contents from GitHub.
- Do not stage, commit, push, or create a pull request unless the user explicitly requests it.
- Record benchmark source commit, device build, route, settings, commands, and raw artifacts.
- Restore device brightness, volume, timeout, connectivity, and other changed settings after a benchmark.
- For long-running tests or commands, write console output to a timestamped file and return control to the user instead of continuously polling.
- Leave the process running when practical. Check its status and report progress only when the user asks.
- Tell the user where the output file is and how to ask for a status check.

## Local verification

This machine uses Android Studio's bundled JDK and the SDK under the user's Library directory. Supply both paths on the Gradle command so verification does not depend on the shell's current environment.

Run the permanent Phase 2 musical-contract qualification suite with:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.bfunkstudios.beatclikr.music.PureCoreQualificationTest'
```

Run the normal full local verification with:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleBenchmark
```

The qualification suite is a JVM test and requires no emulator, physical device, proprietary WAV files, or timing sleeps. >>>>>>> Stashed changes
