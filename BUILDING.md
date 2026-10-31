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

[Error Prone](https://errorprone.info) runs inside `javac` on every compile (`net.ltgt.errorprone` Gradle plugin, `error_prone_core` 2.50.0) for all source sets, with its default severities: bug patterns marked ERROR fail the build, WARNING patterns are reported and do not. It replaced `checkstyle.xml`, which held only whitespace rules, was not wired into any build, and had every `value` attribute mangled to `cellValue`. Error Prone was chosen because it finds correctness bugs, which matter more than formatting in a codebase whose checks stand in for code review. Adopting it found two errors, both fixed: a redundant `Comparable<File>` on `FileSystemTreeModel.FileTreeNode`, and a discarded `values()` call in `DatavyuProjectConstructor`. Once a check has no findings it is made an error in `build.gradle.kts` (`options.errorprone.error("CheckName")`), so it stays at zero; `MissingOverride` is the first. `-PerrorpronePatch=CheckA,CheckB` rewrites the sources with Error Prone's suggested fixes for those checks. javac lists at most 100 warnings per compile task; `./gradlew compileJava -PallWarnings` lifts that limit and adds every javac `-Xlint` category (763 warnings in the main sources on 2026-10-14, mostly Javadoc style).

### Analysis Reports

These fill the Measures table in [TODO.md](TODO.md). They are reports, not gates: they do not fail the build, and Error Prone ERRORs, the tests and `spotbugsGate` are the only checks that do. A rule moves into a gate once its count reaches zero ([AGENTS.md](AGENTS.md#how-to-work-here)): `spotbugsGate` (run by `check` and `build`) fails on any SpotBugs finding in a gated category, so far only thread safety (`MT_CORRECTNESS`, at zero since 2026-10-29).

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

`CITATION.cff` (Citation File Format 1.2.0) at the repository root is the single source for how to cite Watavyu. GitHub reads it for "Cite this repository"; `processResources` bundles it, and `Citation` reads the part it needs (top-level `key: value` lines and the `authors` list, without a YAML library) and formats it as APA 7 and BibLaTeX `@software` for Help > Cite Watavyu, always with the running build's version. The author is the group "The Watavyu Contributors" (double-braced in BibTeX so it is not split into first and last names); individual authors with `given-names`/`family-names` are formatted per style if added. There is no release yet, so APA shows `(n.d.)` and BibTeX has no year; at the first release, add `version` and `date-released` to the file. For a persistent identifier, connect the GitHub repository to Zenodo, which mints a DOI per release; then add it as `doi:` in the file.

## Settings

`Settings` keeps the user's settings in `~/Library/Application Support/Watavyu/settings.json`, a JSON file readable and editable by hand:

- `lastChosenDirectory`: where the last file dialog was left. File dialogs open there if the folder still exists and otherwise in the user's home (`Settings.resolveStartDirectory`). The fallback is never written back, so a missing, deleted or foreign path (for example one from another machine) is handled the same way.
- `recentProjects`: the five most recently opened or saved projects and data files, newest first, for File > Open Recent Files. It is saved as soon as it changes.
- `stopAtEndOfMedia`: whether playback pauses at the end of the last media clip; true unless set to false. Set to false, playback runs on to the end of the timeline, which is at least one minute long, as it did in Datavyu.
- `showControls`: whether the controls drawer shows beside the spreadsheet; true unless set to false. Playback > Show Controls and the keypad's `*` toggle it.
- `playheadAcrossColumns`: whether the playhead's line runs on across the spreadsheet's columns; true unless set to false. Playback > Playhead Line Across Columns toggles it.
- `newSpreadsheetMediaColumns`: how many empty media columns a new spreadsheet starts with; 1 unless set otherwise. It applies to the spreadsheet open at launch and to File > New, not to opened projects.
- `spreadsheetFont`: the face of cell values, `sans` (IBM Plex Sans) unless set to `mono` (IBM Plex Mono); anything else reads as `sans`. Spreadsheet > Monospaced Values toggles it, and cells change at once.
- `pastCellDim`: how far the values, times and ordinal of a cell the needle has passed fade toward the cell colour, from 0 (unchanged) to 100 (gone); 40 unless set otherwise, a trial value until the owner picks a default. Values outside are clamped. Spreadsheet > Dim Past Cells offers steps of 20, and cells change at once. It applies while highlighting is on, as the in-play mark does.
- `windows`: where the main window and each video window were last left, keyed `main` and `MediaWindow-<column name>`. A window is put back only if at least 50 × 50 points of it would be on a connected screen (`WindowPlacement`); otherwise it is placed as on first launch. Other dialogs open centred on the window that owns them.

Unknown keys are ignored and a missing or unreadable file gives the defaults, so the file never stops the application from starting. Everything except recent projects is saved when the application quits.

The `watavyu.settingsDir` system property moves the file to another directory. Every Gradle test task sets it to `build/test-settings/<task>`, so tests never read or write the user's settings; `SettingsTest` fails if that wiring is lost.

The palette is not a setting: it follows the Mac's appearance when the application starts (`Appearance`), Paper when light and Graphite when dark, and stays for the run. The `watavyu.appearance` system property (`light` or `dark`) overrides it, for screenshots and for trying the other palette. Every Gradle test task sets it to `light`, so tests do not depend on the Mac's appearance. Zoom changes the cell font size until the application quits.

Before this file existed, settings lived in `~/Library/Application Support/Datavyu` as Java XMLEncoder files (`settings.xml`, `projectHistory.xml` and `*.session.xml`). Nothing reads them any more and they can be deleted.

## Log

Log4j 2.26 writes INFO and above to the console and to `~/Library/Logs/Watavyu/watavyu.log`, which Console.app also shows. Each launch starts a new file and the logs of the five launches before it are kept as `watavyu-1.log` (the last) to `watavyu-5.log`, so the log of a crash survives the next launch. Every line is written at once. Log4j 2.26 does not evaluate `${...}` lookups in messages, so a file path cannot make it act (`LoggingTest`). The `watavyu.logDir` system property moves the folder; every Gradle test task sets it to `build/test-logs/<task>`. The configuration is `src/main/resources/log4j2.xml`.

## Project Files

A project is one `<name>.watavyu.json` file: UTF-8 JSON that people and other tools can read. The format is decided in [AGENTS.md › Decisions](AGENTS.md#decisions); `src/main/resources/org/datavyu/projectfile/watavyu-project.schema.json` is its JSON Schema (draft 2020-12), and `src/test/resources/projectfile/example.watavyu.json` is a complete example.

- `org.datavyu.projectfile` holds the format: `ProjectDocument` (the project as records), `ProjectJson` (read and write), `ProjectFileReader` and `JsonFields` (keys and types), `ProjectRules` (rules across fields), `ProjectFileWriter` (layout), `ProjectPaths` (video paths) and `SpreadsheetColumns` (to and from the spreadsheet's columns).
- Reading is strict. An unknown or missing key (except an annotation column's `locked`, which files from before annotation columns could be locked lack, read as unlocked), a duplicate key, a value of the wrong type, a time not written as `HH:MM:SS:mmm`, a negative time or a broken rule is refused with a message naming the place, such as `columns[1].cells[0].offset: must be the onset plus the source's duration (00:00:11:500)`. A file written by a newer Watavyu says so.
- Writing checks the same rules first, so Watavyu never writes a file it would refuse. The layout is fixed (two-space indent, keys in schema order, cells in time order, one line per annotation cell), so saving an unchanged project rewrites the same bytes and diffs show only real changes. The new file is written next to the old one and renamed over it, so a failed save leaves the old file whole; it keeps the old file's permissions, and saving through a symbolic link keeps the link.
- The schema checks every field; three rules across fields are the reader's alone: column names are unique, an annotation cell has one value per code, and a clip has an offset exactly when its source has a duration, equal to its onset plus that duration. `ProjectJsonTest` keeps the schema and the reader in agreement on every refused file.
- Media paths are relative to the project file's folder when the file is inside it, and absolute otherwise; the spreadsheet holds them absolute, so Save As to another folder still finds the media. A clip whose file is missing keeps its cell, shown as `cam.mp4 · 30 fps (missing)`, and is saved back as it was read. A clip whose file has not opened yet is saved with what is known of it; details its file has never given are left out.
- File > Open opens project files, as do the command line and dragging a file onto the window. Datavyu's `.opf` and `.csv` files do not open; Spreadsheet > Import Passes From JSON adds the passes of Datavyu's JSON export as annotation columns.
- Autosave writes `~<name>_<number>.watavyu.json` to the temporary folder every minute and deletes it on a clean quit. At the next launch after a crash, Watavyu offers to recover it: it opens in a new tab as an unsaved project named after the original, Save As follows, and the autosave is deleted once it has opened (or if recovery is declined). See R9 in [TODO.md](TODO.md).

## Application Structure

- `Datavyu.main` starts the application on the Swing EDT: the look and feel and the main window (`DatavyuView`), then the file named on the command line, if any. `Datavyu.exit()` asks about unsaved changes and then saves settings and quits; closing the main window and the macOS Quit command both go through it. Every spreadsheet with changes is asked about in turn (`UnsavedWork`); Cancel, closing the dialog, or a save that does not happen (a failed save, or a cancelled Save As) stops the quit or the closing of the tab.
- Media columns are spreadsheet columns (`Variable.isMedia`) whose cells are clips (`Cell.getClip`, a `MediaClip`). The data model holds the clip rules, so every path obeys them: a clip's offset is its onset plus its length and is never set on its own, a media column refuses ordinary cells (as an annotation column refuses clips). `MediaPlayers`, one per spreadsheet, opens a player for each clip whose file exists and keeps players and spreadsheet in step: adding, moving or deleting a clip or its column in the spreadsheet (including undo and opening a project) opens, moves or closes its player. Each media column has one window (`MediaWindow`; a new one without a remembered place opens set off from those already open), into which its clips' players put their video; their own windows stay hidden. Under the picture, a volume control sets the volume of the clip in play (`MediaPlayers.setVolume`). A column's header menu (right-click) hides or deletes the column, or every selected column if it is one of them (`SpreadsheetColumn.hideItem`, `deleteItem`; deleting is one edit in the undo history, `RemoveVariableEdit`), and locks it (`lockItem`, also an edit, `LockColumnEdit`). Adding media while the selected media column is locked asks, through `MediaPlayers.addMediaFiles`, whether to use new columns instead. Closing a media window hides its column; a hidden column's clips are out of play, so they are paused and silent. On every clock tick `MediaPlayers` puts each column's clip in play (`StreamViewer.setInPlay`): of the clips whose span holds the time, the one that starts latest. The others are paused and silent, and the window shows black where no clip is in play or the clip in play has no player. Media columns and the clips in them are added apart (`MediaColumns`): Spreadsheet > New Media Column adds an empty one, selected and undoable, and a new spreadsheet starts with `newSpreadsheetMediaColumns` of them, the first selected. Adding media (Spreadsheet > Add Media, or dropping media files on the spreadsheet) puts each file in the one selected media column as a clip at the current time, or otherwise in a new column named `media_1`, `media_2` and so on at time zero; a file that does not open is taken out again. Media columns are left out of new cells, quick keys, the Code Editor and exports. Jogging steps by the highest frame rate of the open media with video, or 30 frames a second when only audio is open. Playback pauses at the end of the last clip of known length unless `stopAtEndOfMedia` is off. Leaving a spreadsheet's tab or minimising the main window hides its media windows; coming back shows them again, except those of hidden columns (`MediaPlayers.setShowing`).
- A locked column (`Variable.isLocked`) is enforced in the data model, so every path obeys it: `DatavyuVariable`, `DatavyuCell` and `DatavyuCellValue` refuse changes to a locked column's cells, codes and name, doing nothing or throwing `IllegalStateException` from a method that would return a new cell, clip or code. Places that start a change (New Cell, Delete Cell, set onset and offset, quick keys, the Code Editor's buttons, Add Media) ask `Variable.refusesEdits()` first, so they make no undo edit for it. Each refusal reaches the column's listeners as `VariableListener.editRefused`, and the header and the Code Editor shake their padlock. Typing is refused before it reaches the text: `EditorTracker`, which carries keys to a cell's values and to the Code Editor's names and codes (written to the model's `Argument`s directly), and `TimeStampDataValueEditor` for times.
- Playback has no window of its own. Each spreadsheet tab has its own `VideoController` (clock, players, and the playback and coding commands), and beside its columns:
  - The controls drawer: the controller's buttons laid out as a numeric keypad, with the Jump by, Onset and Offset fields. It shows while `showControls` is on.
  - The playhead (`PlayheadCarriage`): a strip with a needle at the clock's time, carrying the time and rate (`1.25×`, `paused 1×`, `stopped`). It has no scale of its own. In temporal layout the needle sits where its time falls among the cells: the layout gives each cell onset and offset a span down the sheet (`TimeAxis`), from the top of the cells that start then to the bottom of the cells that end then, and a time between two lies between the end of the first's span and the start of the next's in proportion. So the needle is inside a cell exactly while its time is, and passes a point cell at the instant of its time. The needle scrolls with the cells, shows as ▲ or ▼ at the edge when scrolled out of view, and is dragged to move the clock. While playback runs the sheet follows the needle, scrolling to keep it a third of the way down the visible rows and never past the sheet's ends; paused, the sheet stays where it was left; with `playheadAcrossColumns` on, a faint line of the same colour crosses the columns (`SpreadsheetView`). In ordinal layout the strip shows the time at its top and the needle is gone.
  - The layout (`SheetLayout`, set on the scroll pane) places the cells again only when something in the sheet has changed, which invalidates its view, or the scroll pane has changed size. Scrolling revalidates the viewport and so the scroll pane, but moves no cell, so it skips them; placing all the cells on every scroll step and playback tick made a sheet of 3,000 cells drop frames. A cell revalidates when its onset or offset changes, since its time fields are validate roots of their own.
  - Playback pauses where it is heading: at time zero going backward, or at the timeline's end going forward, so playback started at either edge and heading away carries on. The timeline ends at the end of the media, and is at least a minute long.
- The look (U5, decided in AGENTS.md › Decisions):
  - `Palette` holds the colours of each palette as named tokens (chrome, ground, cell, in play, rule, text, time, ordinal, accent, selection, warning, surround); `SpreadsheetTheme.PALETTE` is the one for this run, and views take their colours from it, never from literals. `PaletteTest` pins the contrast bands each pair keeps. The video and the gaps between clips stay black.
  - FlatLaf's macOS light or dark look and feel (`SpreadsheetTheme.installLookAndFeel`) draws menus, tabs, fields, scroll bars and dialogs, with its colours taken from the palette (`lookAndFeelDefaults`), and `apple.awt.application.appearance=system`, set as `Datavyu` loads and so before AWT starts, makes window title bars match.
  - Fonts are bundled IBM Plex (`src/main/resources/fonts`, SIL Open Font License): Mono for times and ordinals, Sans SemiBold for column headers, Sans or Mono for values (`spreadsheetFont`), and Sans for labels.
  - `CellLook` decides a cell's colours and border from where it stands against the needle and how it is selected. The cell in play is lifted a little and marked with the accent on its left edge; a past cell's text fades by `pastCellDim`; selection is a frame; an offset before its onset (or an overlap) is a warning line along the bottom. Every border has the same insets, so text never moves, and borders are made once per palette, so a clock tick sets no new objects.
  - Glyphs (`Glyph`) are line drawings in code, on an 18-unit square, in the colour of the component they are on: the keypad keys (`KeypadKey`, each with its label and keypad key), the padlock on a locked column's header and in the Code Editor (`LockMark`, which shakes when the column refuses a change), the volume mark in a media window, and the Code Editor's arrows and warning mark. The About window is set in type. The only image left is the app icon, `datavyu.icns`.
- UI text comes from `resources/<ClassName>.properties` next to each view, read through `Resources.of(Class)`, which falls back to `org/datavyu/resources/Datavyu.properties`.
- `Shortcuts` is the one table of keyboard shortcuts. Menu items take their accelerators from it, Help > Keyboard Shortcuts List shows it, and its key dispatcher runs the shortcuts:
  - Menu shortcuts are handled by the menu bar while the main window has focus, and by the dispatcher from other windows, such as a media window, if the menu item is enabled.
  - New Cell to Left/Right (⌘L, ⌘R) and Zoom In/Out (⌘=, ⌘-) are handled by the dispatcher first in every window.
  - Numeric keypad keys drive the playback and coding commands of the controls drawer from every window, whether or not the drawer shows; `*` shows or hides it. The character a keypad key would type is dropped, even for keys without a shortcut.
  - Reserved keys (`Shortcuts.RESERVED`: ⌃H, ⌃D, ⌃W, ⌥⌫, ⌥⌦), which macOS text fields delete with, are dropped with their character in every window until a shortcut uses one; they are kept for the key mapping (F7).
  - Column selection (⌘←, ⌘→, in `SpreadSheetPanel`) and the code editor's keys (in `VocabEditorV`) are handled by those components and listed for reference.

## Video Playback

### Priorities

The fork is a hard reset for macOS: no backward compatibility with old Datavyu versions or platforms is required, and modernizing is preferred over carrying legacy complexity. For playback, the typical workflow is seeking to a cell's onset, playing to its offset, and repeating, so random seeking and forward playback come first, frame stepping (both directions) second, and reverse playback last; it may be dropped. Optional local transcoding or caching is acceptable when measurements show it helps, and originals are never modified. The target material is OBS screen recordings: 1920x1080, 30 fps, H.264/AAC, roughly 20 minutes.

### Backend

OpenJFX Media (`org.datavyu.plugins.javafx`) is the only video player. Each video window is a Swing dialog that hosts a JavaFX `MediaView` through `JFXPanel`; the spreadsheet, timeline and annotation model stay Swing. On macOS, OpenJFX plays video through AVFoundation, so decoding is hardware-accelerated.

- `JavaFxVideoPlugin` opens a file on a background thread (it is no longer a plugin: there is no plugin system), waits up to 30 s for the player to become ready, and only then builds the window on the Swing EDT. Adding media and project loading both open videos this way, so neither UI thread blocks. A file that fails to open shows a message and adds no clip, and its player is disposed.
- `JavaFxPlayback` is the thread-safe adapter between Datavyu's clock/Swing threads and the JavaFX Application Thread. Every player command is queued to the JavaFX thread; reads never block. Rapid seeks (scrubbing) are coalesced so only the latest target is decoded.
- `Mp4FrameTable` reads every frame's presentation time from the file's sample tables (`stts`, `ctts`, `elst`) without decoding. Seeks and frame steps target real frame boundaries, including variable-frame-rate files, and paused positions are reported as the displayed frame's start time rounded up to a whole millisecond. If the frame table cannot be read, 30 fps is assumed and frame stepping is disabled.
- The reported frame rate is the nominal one (inverse of the median frame duration). OBS recordings hold the second-to-last frame for two frame times; edge frames like that do not make a file count as variable frame rate.
- Reverse playback is not supported. Shuttling backward stops at 0x instead of entering negative rates, and a negative rate pauses. Frame stepping works in both directions.
- Shuttle steps through 0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 3 and 4× (`RateController`). The adapter would play forward rates above 8x at 8x (OpenJFX's maximum), the clock running on at the requested rate and re-syncing the video, but no command asks for them.

### Supported Input

MP4 (`.mp4`, `.m4v`) with H.264 video and AAC audio. This is what OpenJFX documents. Audio files OpenJFX plays on macOS (`.m4a`, `.mp3`, `.wav`, `.aif`, `.aiff`) are media too: they show a black picture, have no frame stepping, and their clips record no size or frame rate. The Add Media chooser offers all of these, and dropping them on the spreadsheet adds them.

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

- **Unit tests** (`./gradlew test`, `src/test/java/org/datavyu/plugins/javafx`): the adapter's time/rate/state conversions, seek coalescing, stepping, end-of-media, error and dispose paths against a fake engine and a queued executor (no window or display); MP4/MOV frame tables against tiny fixtures in `src/test/resources/video` (regenerate with `tools/playback/make-unit-fixtures.sh`); user-facing error messages; the shuttle rates.
- **Integration tests** (`caffeinate -u -d ./gradlew integrationTest`): drive the real patched player through the production adapter on a 10 s, 30 fps, 1280x720 H.264/AAC MP4, and a 4 s AAC `.m4a`, that `ffmpeg` generates into `build/playback-fixtures` on first run. Each frame carries its own number as a 16-bit barcode in the top-left corner, read back from a snapshot of what the player displays. They cover paused seeks, seek after play/pause, seeks immediately after pausing, onset-to-play, prompt playback start, 2x playback, end-of-file seek and playing to the end, frame stepping both ways, rapid scrubbing, dispose, MOV rejection, refusing to open media on a UI thread, an audio file opening without a picture, two clips playing together at their own volumes, a clip out of play pausing, and what a video window reports for its project file (size, frame rate, duration, volume, visibility). They need `ffmpeg` and an awake display: JavaFX receives no video frames while the display sleeps. `caffeinate -d` only keeps an awake display from sleeping; `-u` also wakes one that is already asleep.
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
- `make run` reaches the application window. Opening a project file whose media column lists a 10 s, 1280x720, 30 fps MP4 (locked, onset 2 s, volume 0.5), a media column whose file was missing, and an annotation column: the autosave written a minute later held all three, unchanged and in order, with the clip's length, size and frame rate as the file reported them. A project file with three clips in one media column (two overlapping, written without their files' details, and one missing) autosaved them with each opened file's length, size and frame rate, and the missing clip as it was read. (Both checked 2026-10-17 without looking at the windows; the owner checked basic use by hand.) A `.mov` shows the conversion message and adds no clip. On 2026-10-29 `make run` with a project holding a 10 s clip and two annotation columns showed the controls drawer, the playhead strip reading `00:00:00:000 stopped` and its line across the columns at the top; driving it with clicks was not possible from the agent's session (no Accessibility permission), so dragging the playhead and playing were not checked in the running app.
- `./gradlew integrationTest`: 18 tests pass. As controls, the same tests fail against stock OpenJFX (6 seek/step tests), against a library without the audio-tap change (start latency), and against a library without the seek-frame fix (stale frame after pause).
- `./gradlew test` runs 475 tests and all pass, with none skipped. Before the move from Mockito 2.10 to 5.24.0, the `setUp` methods of `CellTest`, `DataStoreTest` and `VariableTest` failed on JDK 25 ("Cannot define class using reflection") and TestNG skipped those classes' 27 tests. `OpenControllerTest` and `SaveControllerTest` pass since their Maven-era paths (a trailing slash on `testPath`, output under `target/`) were fixed. The suite is now a green verification gate.
- On 2026-10-29, a generated project of 11 columns and 3,000 cells, driven in the running app on this machine with the EDT's event times recorded: scrolling in 6 or 40 px steps at 60 Hz kept the EDT about 21% busy with at most one event over 16 ms (33% on `main` before U5, and 44–56% with up to 18 such events before the layout stopped placing cells on scroll), and 10 s of playback at 1× or 4× with the sheet following the needle had at most two (45–67 before), with the playhead's line across the columns on or off. The harness was a throwaway; the rule it measured is pinned by `SheetLayoutTest`.
- Checked by the owner on 2026-10-21: clips in one column take over each other's picture and sound, and clips in different media columns play their sound together, each following its window's volume control.
- Not verified automatically: audible audio and the volume control (the volume path changed with the audio-tap removal), and interactive playback driven by the Datavyu clock and keyboard shortcuts. Check these by hand after `make run`.

## Not Included

No installer/app image or code signing is configured.
