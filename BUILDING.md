# Building Watavyu Locally

## Scope

This is the Gradle build for the personal macOS fork. It targets local development on macOS 27 / Apple Silicon. It builds and launches the existing Swing application with an OpenJFX Media video player; it does not create an installer or sign an app bundle.

Gradle is the only build. The Maven build (`pom.xml`, the assembly descriptors, the `compile_*.sh`/`package-macos.sh` scripts and the javafx-maven-plugin/appbundle packaging resources) has been removed.

## Toolchain

The project pins these tools in `.tool-versions`:

- Java: Temurin 25.0.4, the JDK used for compilation and running.
- Gradle: 9.8.1. The checked-in Wrapper is authoritative for Gradle builds; a global Gradle install is not needed for normal use.

Install the `java` and `gradle` asdf plugins once if they are not already installed, then install this repository's pinned versions:

```sh
asdf plugin add java https://github.com/halcyon/asdf-java.git
asdf plugin add gradle
asdf install
```

If a plugin is already installed, skip its `asdf plugin add` command. In a shell where asdf has not configured `JAVA_HOME` and `PATH`, select the pinned JDK for Gradle like this:

```sh
export JAVA_HOME="$(asdf where java)"
export PATH="$JAVA_HOME/bin:$PATH"
```

The Gradle Wrapper downloads Gradle 9.8.1 on its first invocation and reuses it afterwards.

Optional: `brew install ffmpeg` provides `ffmpeg`/`ffprobe` for converting recordings, generating playback test fixtures and running the playback integration tests. The application itself does not need it.

## Common Commands

```sh
make build
make run
make test
make clean
```

The Makefile is a small convenience layer over the Gradle Wrapper; it does not introduce another build system. It reads the repo-pinned Java location from asdf for the build process. You can also invoke Gradle directly with `./gradlew <task>`.

`make run` starts the desktop UI in the foreground. The application keeps its settings in `~/Library/Application Support/Watavyu/settings.json` (see [Settings](#settings)) and its log in `~/Library/Logs/Watavyu/watavyu.log` (see [Log](#log)). `./gradlew run --args=/path/to/study.watavyu.json` opens a project at launch (see [Project Files](#project-files)).

Playback checks that drive the real player (see [Testing Playback](#testing-playback)):

```sh
caffeinate -u -d ./gradlew integrationTest
caffeinate -u -d ./gradlew playbackBenchmark -Pmedia=/path/to/video.mp4 [-Pseeks=200]
```

## How the Gradle Build Works

- `settings.gradle.kts` names the root project.
- `build.gradle.kts` applies Gradle's `application` plugin, selects `org.datavyu.Datavyu` as the main class, and sets the 1 GB application heap. The build stops early on anything other than Apple Silicon macOS.
- The Java toolchain requests JDK 25, so compiler selection is explicit rather than inherited from whichever JDK happens to be on `PATH`.
- Dependencies resolve from Maven Central only. OpenJFX 27 (`javafx-base`, `-graphics`, `-media`, `-swing`, `mac-aarch64` classifier) is the video stack. The x86_64-only `ffmpeg-plugin` and `libNativeOSXCanvas` artifacts and their dead legacy repositories are gone.
- Every dependency and plugin version is in `gradle/libs.versions.toml`. Gradle checks the SHA-256 of every downloaded artifact against `gradle/verification-metadata.xml` and fails the build on a mismatch or an unknown artifact; the wrapper checks the Gradle distribution against `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties`. To add or change a dependency, edit the catalog, then run `caffeinate -u -d ./gradlew --write-verification-metadata sha256 build analyze integrationTest` and review the new checksums in the diff before committing. `dependencyUpdates` turns verification off for its own lookups of newer versions.
- `javafx-media` is not used as published. The `patchedJavafxMedia` task repackages it with `libjfxmedia_avf.dylib` replaced by the patched build in `third_party/openjfx-media-avf` (see [The OpenJFX Patch](#the-openjfx-patch)); everything else in the jar, including `module-info`, is upstream.
- `run`, `test`, `integrationTest` and `playbackBenchmark` put the OpenJFX jars on the module path (`--add-modules javafx.swing,javafx.media`) and keep the rest of the application on the class path. OpenJFX is only supported from the module path.
- The `integrationTest` source set (`src/integrationTest`) holds tests and the benchmark that need the real player and a display; `./gradlew test` does not run them.
- `processResources` stamps the Gradle project version into `org/datavyu/version.properties` in the generated resource output. `DatavyuVersion.getVersion()` reads it; the About dialog and the citation use it. There is no other version string to keep in sync.
- `tasks.test` uses the repository's existing TestNG tests and supplies their `testPath` resource directory and the expected project version.
- Tests validate project files against the published JSON Schema with `com.networknt:json-schema-validator` 3.0.7, a test-only dependency. It uses Jackson 3 (`tools.jackson`), which does not clash with the app's Jackson 2; its YAML module is excluded because it would replace SnakeYAML 1.18, which opening `.opf` files still needs.
- Every test task loads Mockito 5 (`mockito-core` 5.24.0) as a `-javaagent` from the `mockitoAgent` configuration. Mockito 5 mocks through an inline agent; JDK 21+ warns when an agent attaches itself at runtime, and future JDKs will refuse it, so it is loaded at JVM start as Mockito documents.
- Gradle outputs are under `build/`; local Gradle caches are under `.gradle/`. Both are ignored by Git. JavaFX extracts its native libraries to `~/.openjfx/cache/27+30/aarch64` and replaces them there whenever the jar's copy differs.

The Gradle project version is currently `0.1.0-SNAPSHOT`. The window title (`Application.title` in `Datavyu.properties`) is `Watavyu`.

## Lint

[Error Prone](https://errorprone.info) runs inside `javac` on every compile (`net.ltgt.errorprone` Gradle plugin, `error_prone_core` 2.50.0) for all source sets, with its default severities: bug patterns marked ERROR fail the build, WARNING patterns are reported and do not. It replaced `checkstyle.xml`, which held only whitespace rules, was not wired into any build, and had every `value` attribute mangled to `cellValue`. Error Prone was chosen because it finds correctness bugs, which matter more than formatting in a codebase whose checks stand in for code review. Adopting it found two errors, both fixed: a redundant `Comparable<File>` on `FileSystemTreeModel.FileTreeNode`, and a discarded `values()` call in `DatavyuProjectConstructor`. The existing warnings are not yet addressed; promoting a check to ERROR is done with `options.errorprone.error("CheckName")` once its warnings are fixed. javac lists at most 100 warnings per compile task; `./gradlew compileJava -PallWarnings` lifts that limit and adds every javac `-Xlint` category (853 warnings in all on 2026-10-14, mostly Javadoc style and missing `@Override`).

### Analysis Reports

These fill the Measures table in [TODO.md](TODO.md). They are reports, not gates: they never fail the build, and Error Prone ERRORs and the tests remain the only checks that do. A rule moves into a gate once its count reaches zero ([AGENTS.md](AGENTS.md#how-to-work-here)).

- `./gradlew analyze` runs the tests with JaCoCo and writes `build/reports/jacoco/test/html`, PMD (`build/reports/pmd/main.html`) and SpotBugs (`build/reports/spotbugs/main.html`) for the main source set. `./gradlew check` and `build` also run PMD and SpotBugs.
  - PMD uses `config/pmd/ruleset.xml`: dead code, size and complexity, error handling, correctness and legacy-API rules, with no formatting or naming rules.
  - SpotBugs excludes the defensive-copy patterns (`EI_EXPOSE_REP*`, `MS_EXPOSE_REP`) through `config/spotbugs/exclude.xml`; they do not apply to a single-user desktop app and were 60% of its findings.
- `./gradlew dependencyUpdates` (Ben Manes versions plugin) lists stable updates for every dependency and plugin, ignoring alpha, beta, RC and early-access releases.
- `tools/analysis/dead-code.py` (after `./gradlew classes`, with `JAVA_HOME` set) lists classes unreachable from the entry points and methods nothing invokes, using `jdeps` and `javap`. Its docstring explains what it can and cannot see.
- `./gradlew -q dependencies --configuration runtimeClasspath | tools/analysis/osv-check.py` lists runtime dependencies with known vulnerabilities from OSV.dev. It sends only Maven coordinates and versions.

The SpotBugs Gradle plugin (6.5.13, the latest) itself calls `Configuration.setVisible`, which Gradle 11 removes. That is the build's only Gradle deprecation warning; it needs a plugin update, not a change here.

## Network Access

The application makes no network connections of its own. The Datavyu update check (which fetched `version.txt`/`pre_version.txt` from datavyu.org, sending the app, Java and OS versions in its User-Agent) and the Google Analytics telemetry with its first-start opt-in prompt have been removed. `NoNetworkAccessTest` fails the build if compiled application classes reference socket, `URLConnection`, `java.net.http` or analytics APIs. The Help menu's Support Forum, User Guide and Cite Datavyu items (Cite Datavyu is replaced by Cite Watavyu, which links nowhere), their datavyu.org settings, and the datavyu.org link in the unsupported-plugin message have also been removed. The application opens no external links.

## Citation

`CITATION.cff` (Citation File Format 1.2.0) at the repository root is the single source for how to cite Watavyu. GitHub reads it for "Cite this repository"; `processResources` bundles it and `Citation` formats it as APA 7 and BibLaTeX `@software` for Help > Cite Watavyu, always with the running build's version. The author is the group "The Watavyu Contributors" (double-braced in BibTeX so it is not split into first and last names); individual authors with `given-names`/`family-names` are formatted per style if added. There is no release yet, so APA shows `(n.d.)` and BibTeX has no year; at the first release, add `version` and `date-released` to the file. For a persistent identifier, connect the GitHub repository to Zenodo, which mints a DOI per release; then add it as `doi:` in the file.

## Settings

`Settings` keeps the user's settings in `~/Library/Application Support/Watavyu/settings.json`, a JSON file readable and editable by hand:

- `lastChosenDirectory`: where the last file dialog was left. File dialogs open there if the folder still exists and otherwise in the user's home (`Settings.resolveStartDirectory`). The fallback is never written back, so a missing, deleted or foreign path (for example one from another machine) is handled the same way.
- `recentProjects`: the five most recently opened or saved projects and data files, newest first, for File > Open Recent Files. It is saved as soon as it changes.
- `windows`: where the main window, the video controller and each video window were last left, keyed `main`, `videoController` and `JavaFxVideoViewer-<file name>`. A window is put back only if at least 50 × 50 points of it would be on a connected screen (`WindowPlacement`); otherwise it is placed as on first launch. Other dialogs open centred on the window that owns them.

Unknown keys are ignored and a missing or unreadable file gives the defaults, so the file never stops the application from starting. Everything except recent projects is saved when the application quits.

The `watavyu.settingsDir` system property moves the file to another directory. Every Gradle test task sets it to `build/test-settings/<task>`, so tests never read or write the user's settings; `SettingsTest` fails if that wiring is lost.

Spreadsheet colours and fonts are not settings: they are constants in `SpreadsheetTheme`. Zoom changes the cell font size until the application quits.

Before this file existed, settings lived in `~/Library/Application Support/Datavyu` as Java XMLEncoder files (`settings.xml`, `projectHistory.xml` and `*.session.xml`). Nothing reads them any more and they can be deleted.

## Log

Log4j 2.26 writes INFO and above to the console and to `~/Library/Logs/Watavyu/watavyu.log`, which Console.app also shows. Each launch starts a new file and the logs of the five launches before it are kept as `watavyu-1.log` (the last) to `watavyu-5.log`, so the log of a crash survives the next launch. Every line is written at once. Log4j 2.26 does not evaluate `${...}` lookups in messages, so a file path cannot make it act (`LoggingTest`). The `watavyu.logDir` system property moves the folder; every Gradle test task sets it to `build/test-logs/<task>`. The configuration is `src/main/resources/log4j2.xml`.

## Project Files

A project is one `<name>.watavyu.json` file: UTF-8 JSON that people and other tools can read. The format is decided in [AGENTS.md › Decisions](AGENTS.md#decisions); `src/main/resources/org/datavyu/projectfile/watavyu-project.schema.json` is its JSON Schema (draft 2020-12), and `src/test/resources/projectfile/example.watavyu.json` is a complete example.

- `org.datavyu.projectfile` holds the format: `ProjectDocument` (the project as records), `ProjectJson` (read and write), `ProjectFileReader` and `JsonFields` (keys and types), `ProjectRules` (rules across fields), `ProjectFileWriter` (layout), `ProjectPaths` (video paths) and `SpreadsheetColumns` (to and from the spreadsheet's columns).
- Reading is strict. An unknown or missing key, a duplicate key, a value of the wrong type, a time not written as `HH:MM:SS:mmm` or a broken rule is refused with a message naming the place, such as `columns[1].cells[0].offset: must be the onset plus the source's duration (00:00:11:500)`. A file written by a newer Watavyu says so.
- Writing checks the same rules first, so Watavyu never writes a file it would refuse. The layout is fixed (two-space indent, keys in schema order, cells in time order, one line per annotation cell), so saving an unchanged project rewrites the same bytes and diffs show only real changes. The new file is written next to the old one and renamed over it, so a failed save leaves the old file whole.
- The schema checks every field; three rules across fields are the reader's alone: column names are unique, an annotation cell has one value per code, and a clip's offset is its onset plus its source's duration. `ProjectJsonTest` keeps the schema and the reader in agreement on every refused file.
- Media paths are relative to the project file's folder when the file is inside it, and absolute otherwise. Media whose file is missing, or fails to open, keeps its column: it is saved back as it was read. Media added in the app gets a column named `media_1`, `media_2` and so on, skipping names already in use.
- File > Open offers project files and Datavyu's `.opf` files; `.csv` data files open from the command line or by dragging. Save on a project opened from `.opf` or `.csv` goes through Save As, which writes a project file.
- Autosave writes `~<name>_<number>.watavyu.json` to the temporary folder every minute and deletes it on a clean quit. At the next launch after a crash, Watavyu offers to recover it: it opens in a new tab as an unsaved project named after the original, Save As follows, and the autosave is deleted once it has opened (or if recovery is declined). See R9 in [TODO.md](TODO.md).

## Application Structure

- `Datavyu.main` starts the application on the Swing EDT: the system look-and-feel, the main window (`DatavyuView`) and the video controller, then the file named on the command line, if any. `Datavyu.exit()` asks about unsaved changes and then saves settings and quits; closing the main window and the macOS Quit command both go through it. Every spreadsheet with changes is asked about in turn (`UnsavedWork`); Cancel, closing the dialog, or a save that does not happen (a failed save, or a cancelled Save As) stops the quit or the closing of the tab.
- UI text and icons come from `resources/<ClassName>.properties` next to each view, read through `Resources.of(Class)`, which falls back to `org/datavyu/resources/Datavyu.properties`.
- `Shortcuts` is the one table of keyboard shortcuts. Menu items take their accelerators from it, Help > Keyboard Shortcuts List shows it, and its key dispatcher runs the shortcuts:
  - Menu shortcuts are handled by the menu bar while the main window has focus, and by the dispatcher from other windows, such as the video controller, if the menu item is enabled.
  - New Cell to Left/Right (⌘L, ⌘R) and Zoom In/Out (⌘=, ⌘-) are handled by the dispatcher first in every window.
  - Numeric keypad keys drive the video controller from every window. The character a keypad key would type is dropped, even for keys without a shortcut.
  - Column selection (⌘←, ⌘→, in `SpreadSheetPanel`) and the code editor's keys (in `VocabEditorV`) are handled by those components and listed for reference.

## Video Playback

### Priorities

The fork is a hard reset for macOS: no backward compatibility with old Datavyu versions or platforms is required, and modernizing is preferred over carrying legacy complexity. For playback, the typical workflow is seeking to a cell's onset, playing to its offset, and repeating, so random seeking and forward playback come first, frame stepping (both directions) second, and reverse playback last; it may be dropped. Optional local transcoding or caching is acceptable when measurements show it helps, and originals are never modified. The target material is OBS screen recordings: 1920x1080, 30 fps, H.264/AAC, roughly 20 minutes.

### Backend

OpenJFX Media (`org.datavyu.plugins.javafx`) is the only video player. Each video window is a Swing dialog that hosts a JavaFX `MediaView` through `JFXPanel`; the spreadsheet, timeline and annotation model stay Swing. On macOS, OpenJFX plays video through AVFoundation, so decoding is hardware-accelerated.

- `JavaFxVideoPlugin` opens a file on a background thread (it is no longer a plugin: there is no plugin system), waits up to 30 s for the player to become ready, and only then builds the window on the Swing EDT. Add Data and project loading both open videos this way, so neither UI thread blocks. A file that fails to open shows a message and adds no track, and its player is disposed.
- `JavaFxPlayback` is the thread-safe adapter between Datavyu's clock/Swing threads and the JavaFX Application Thread. Every player command is queued to the JavaFX thread; reads never block. Rapid seeks (scrubbing) are coalesced so only the latest target is decoded.
- `Mp4FrameTable` reads every frame's presentation time from the file's sample tables (`stts`, `ctts`, `elst`) without decoding. Seeks and frame steps target real frame boundaries, including variable-frame-rate files, and paused positions are reported as the displayed frame's start time rounded up to a whole millisecond. If the frame table cannot be read, 30 fps is assumed and frame stepping is disabled.
- The reported frame rate is the nominal one (inverse of the median frame duration). OBS recordings hold the second-to-last frame for two frame times; edge frames like that do not make a file count as variable frame rate.
- Reverse playback is not supported. Shuttling backward stops at 0x instead of entering negative rates, and a negative rate pauses. Frame stepping works in both directions.
- Forward rates above 8x are played at 8x (OpenJFX's maximum); the Datavyu clock keeps running at the requested rate and periodically re-syncs the video.

### Supported Input

MP4 (`.mp4`, `.m4v`) with H.264 video and AAC audio. This is what OpenJFX documents, and it is what the Add Data chooser offers.

QuickTime `.mov` is rejected by OpenJFX (`MEDIA_UNSUPPORTED`), even when it contains H.264/AAC. OBS's default `.mov` recordings are H.264/AAC, so converting them is a lossless stream copy that takes about a second for a 21-minute recording:

```sh
ffmpeg -i recording.mov -c copy -movflags +faststart recording.mp4
```

This changes only the container; the original file is untouched and the video/audio data are identical. Opening a `.mov` in Watavyu shows this command. Re-encoding with shorter keyframe intervals is **not** needed: with OBS's 1.9 s keyframe spacing, exact seeks measured below take p95 34 ms; 0.5 s spacing lowers that to 18 ms at the cost of a larger file.

### The OpenJFX Patch

`third_party/openjfx-media-avf/` contains `avf.patch`, `build.sh`, and the resulting arm64 `libjfxmedia_avf.dylib` (checked in so normal builds do not need Xcode). The patch changes only OpenJFX 27's AVFoundation player (`AVFMediaPlayer.mm`):

1. **Exact seeks.** Upstream seeks with `CMTimeMakeWithSeconds(time, 1)` and default tolerance, which truncates every seek to a whole second and then snaps to a keyframe. Measured with stock OpenJFX on this clip: 0 of 25 seeks landed on the requested frame (up to 36 frames off). The patch seeks with nanosecond precision and zero tolerance.
2. **No audio processing tap.** Upstream attaches an `MTAudioProcessingTap` for balance, equalizer and spectrum, none of which Datavyu uses. It delayed the first new frame after play by ~350 ms (median 475 ms vs ~135 ms). Volume is applied on the `AVPlayer` instead.
3. **Seek frame delivery.** After a seek, AVFoundation's video output could hand over a frame decoded before the seek, stamped with the new time; OpenJFX then skipped the correct frame because its timestamp was unchanged, leaving a stale picture while paused (about 1 in 40 seeks issued right after pausing). The patch delivers the frame at the seek target when the seek completes, with a timestamp nudged by under a microsecond, and restarts the display link.

To rebuild the library (needs Xcode command line tools and network access), then check the result:

```sh
third_party/openjfx-media-avf/build.sh
caffeinate -u -d ./gradlew integrationTest
```

The build is reproducible on this machine: rebuilding produces the same SHA-256 as the checked-in library. To move to a newer OpenJFX, update `javafxVersion` in `build.gradle.kts` and the tag in `build.sh`, re-apply the patch, rebuild, and run the integration tests; they fail against a library without these fixes.

## Testing Playback

- **Unit tests** (`./gradlew test`, `src/test/java/org/datavyu/plugins/javafx`): the adapter's time/rate/state conversions, seek coalescing, stepping, end-of-media, error and dispose paths against a fake engine and a queued executor (no window or display); MP4/MOV frame tables against tiny fixtures in `src/test/resources/video` (regenerate with `tools/playback/make-unit-fixtures.sh`); user-facing error messages; the reverse-free shuttle rates.
- **Integration tests** (`caffeinate -u -d ./gradlew integrationTest`): drive the real patched player through the production adapter on a 10 s, 30 fps, 1280x720 H.264/AAC MP4 that `ffmpeg` generates into `build/playback-fixtures` on first run. Each frame carries its own number as a 16-bit barcode in the top-left corner, read back from a snapshot of what the player displays. They cover paused seeks, seek after play/pause, seeks immediately after pausing, onset-to-play, prompt playback start, 2x playback, end-of-file seek and playing to the end, frame stepping both ways, rapid scrubbing, dispose, MOV rejection, refusing to open media on a UI thread, and what a video window reports for its project file (size, frame rate, duration, volume, visibility). They need `ffmpeg` and an awake display: JavaFX receives no video frames while the display sleeps. `caffeinate -d` only keeps an awake display from sleeping; `-u` also wakes one that is already asleep.
- **Benchmark** (`caffeinate -u -d ./gradlew playbackBenchmark -Pmedia=FILE`): cold/warm open, random paused seeks, repeated seeks, seek after play/pause, onset-to-play, rapid scrubbing, stepping, end-of-file seek, and playback CPU in a visible 960 px window. With a barcoded file every measurement also checks the displayed frame; with any other file, latency is the time until the picture changes. Latencies include a few milliseconds of snapshot polling.
- **Fixtures for the benchmark:** `tools/playback/make-fixtures.sh SOURCE OUT_DIR [SECONDS]` writes a lossless MP4 remux of SOURCE plus barcoded re-encodes (`libx264 -preset fast -crf 18`, AAC 192k, source resolution and frame rate) with keyframes every 1.9 s (like OBS), 1 s and 0.5 s. Media files stay outside the repository.

## Playback Measurements

Machine: Apple Silicon, macOS 27, Temurin 25.0.4, OpenJFX 27 with the patch above. Display awake, video muted, 200 random seeks per file.

Source recording (OBS `.mov`, 21:17): H.264 High 1920x1080, 30 fps constant (every frame 1/30 s except the held second-to-last frame), 427 kb/s video, keyframes every 1.9 s on average (max 1.97 s), AAC-LC stereo 48 kHz 192 kb/s, 100 MB. The lossless MP4 remux has identical streams. The barcoded re-encodes keep resolution, frame rate and audio; their video bitrates are 1.9 Mb/s (1.9 s keyframes), 2.6 Mb/s (1 s) and 3.7 Mb/s (0.5 s), so they decode somewhat more data per frame than the source.

| Measurement | Remux of source (1.9 s keyframes) | Barcoded, 1.9 s keyframes | Barcoded, 1 s | Barcoded, 0.5 s |
|---|---|---|---|---|
| Open: cold / warm / frame table | 244 / 12 / 14 ms | 221 / 13 / 13 ms | 270 / 15 / 12 ms | 219 / 12 / 13 ms |
| Random paused seek p50 / p95 / max | 18 / 35 / 39 ms (picture change) | 32 / 34 / 43 ms, 200/200 exact | 16 / 33 / 34 ms, 200/200 exact | 16 / 18 / 28 ms, 200/200 exact |
| Repeated seek to the same 20 targets p50 / p95 | 17 / 34 ms | 32 / 34 ms, 20/20 exact | 16 / 32 ms, 20/20 exact | 16 / 17 ms, 20/20 exact |
| Seek after play then pause p50 / p95 | 60 / 73 ms | 72 / 86 ms, 20/20 exact | 61 / 73 ms, 20/20 exact | 57 / 73 ms, 20/20 exact |
| Onset to play: first new frame p50 / p95 | not measurable | 132 / 134 ms | 133 / 148 ms | 132 / 135 ms |
| Rapid scrub (30 seeks 16 ms apart), last request to final frame p50 / p95 | not measurable | 4 / 13 ms, 10/10 exact | 6 / 14 ms, 10/10 exact | 5 / 12 ms, 10/10 exact |
| Step forward p50 / p95 | 31 / 35 ms | 17 / 18 ms, 30/30 exact | 17 / 18 ms, 30/30 exact | 17 / 18 ms, 30/30 exact |
| Step backward p50 / p95 | 17 / 34 ms | 30 / 34 ms, 30/30 exact | 16 / 33 ms, 30/30 exact | 16 / 17 ms, 30/30 exact |
| End-of-file seek | 33 ms | 32 ms, last frame | 17 ms, last frame | 17 ms, last frame |
| CPU playing 1x (paused) | 24% (0%) of one core | 24% (0%) | 23% (0%) | 24% (0%) |

Latencies are quantized by the 60 Hz display (frames arrive on refresh, roughly every 16.7 ms). The remux has no barcode, so its latencies are the time until the picture changed and its frame accuracy is not measured; the barcoded files carry the same GOP structure and are frame-checked. In 20 s of 1x playback the display advanced 596 frames against 600 expected from the wall clock (the ~130 ms start).

Frame accuracy: every paused seek, step, seek after pause and end-of-file seek on the barcoded files showed exactly the requested frame. Before the seek-frame fix, about 1 in 40 seeks issued immediately after pausing, and 2 in 10 scrub bursts, left an older frame on screen.

Playback starts about 130 ms after `play`, and the video then trails the wall clock by a steady 3 frames; Datavyu's clock remains authoritative and re-syncs the video when it drifts more than 1.5 s or on pause. With OpenJFX's audio tap still attached, the first new frame took about 470 ms.

### Caching and Proxies

Not needed for seeking with this backend. Native exact seeks already complete in tens of milliseconds on the original recording, with no difference between the first and a repeated visit to the same position; shorter keyframe intervals save at most about 16 ms (one display refresh). A precomputed proxy or frame cache would add disk use and invalidation logic for no measured benefit. If slower sources appear (for example 4K or long-GOP HEVC), measure them with `playbackBenchmark` first; a proxy would then be a separate transcode keyed by source path, size, modification time and transcode settings, stored under `~/Library/Caches/Watavyu`, never replacing the original or changing annotation timestamps.

## Verification On This Machine

- `make build` succeeds on Temurin 25.0.4 / macOS 27 arm64 with Error Prone enabled. Remaining compiler warnings are Error Prone warnings.
- `make run` reaches the application window. Opening a project file that lists the integration fixture loads its track (1280x720, 300 frames, 30 fps) at its onset, volume and lock, and the autosave written a minute later held that video, a second video whose file was missing (unchanged) and the annotation column. A `.mov` shows the conversion message and adds no track.
- `./gradlew integrationTest`: 15 tests pass. As controls, the same tests fail against stock OpenJFX (6 seek/step tests), against a library without the audio-tap change (start latency), and against a library without the seek-frame fix (stale frame after pause).
- `./gradlew test` runs 293 tests and all pass, with none skipped. Before the move from Mockito 2.10 to 5.24.0, the `setUp` methods of `CellTest`, `DataStoreTest` and `VariableTest` failed on JDK 25 ("Cannot define class using reflection") and TestNG skipped those classes' 27 tests. `OpenControllerTest` and `SaveControllerTest` pass since their Maven-era paths (a trailing slash on `testPath`, output under `target/`) were fixed. The suite is now a green verification gate.
- Not verified automatically: audible audio and the volume slider (the volume path changed with the audio-tap removal), and interactive playback driven by the Datavyu clock and keyboard shortcuts. Check these by hand after `make run`.

## Not Included

No installer/app image or code signing is configured.
