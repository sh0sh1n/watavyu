# Modernization Backlog

Status as of 2026-10-14, after the first cleanup pass (see [Done](#done)). This is the work left before new feature code. Each finding says how it was established: **verified** means reproduced or measured, **from reading** means found in the code but not exercised in the running app.

Every number here can be re-measured with the commands in [Re-measuring](#re-measuring). Delete items when they are done; this file lists remaining work and the decisions that shape it.

## Decisions

Made on 2026-10-14:

- **Project files will be redesigned.** A transparent JSON file will replace the `.opf` zip, holding the whole spreadsheet and project. Videos become data columns, and each video's start offset becomes an explicit onset. The security and data-loss fixes in section 1, which mostly touch persistence, wait for that redesign.
- **No characterization tests for the old behaviour.** Features are being cut, so tests get written for behaviour that is deliberately kept or rebuilt, not to pin what exists today.
- **Scripting is gone** (Ruby, R and the script console). A scripting shell may be reintroduced later on a clean slate.
- **Removed features:** R and Ruby scripts, the file and favorites drawers, the OpenSHAPA `.shapa` and MacSHAPA `.odb` formats, the Undo History window, the playback region of interest, track bookmarks, and snapping while dragging a track.
- **Kept features:** JSON import/export, highlight-and-focus mode (now on by default, so the window title no longer announces it), and quick-key mode (may be reconsidered).
- **Snapping comes back with the redesign.** Snapping a dragged track into alignment with other tracks is wanted. It snapped a track's start, end and bookmarks to time zero, the needle, the region of interest and the other tracks' starts, ends and bookmarks, within 5 seconds. It was removed together with bookmarks and the region, and will be rebuilt once videos are spreadsheet columns with explicit onsets. The old algorithm is `TracksEditorController.snapOffset` on `main`.
- **The UI direction** needs its own research document and is out of scope here. Removing the drawers freed space that might be used to dock the video controller in the main window.

## At a Glance

| Measure | Before (morning of 2026-10-14) | Now |
|---|---|---|
| Java source (main) | 242 files, 42.5k lines | 175 files, 30.8k lines; `DatavyuView` 1,670 lines, `VideoController` 1,539 |
| Tests (`./gradlew test`) / line coverage | 113 / 9.9% | 154 / 14.7%; still 0% in most views, the timeline and undo |
| Runtime dependencies / with known vulnerabilities (OSV.dev) | 28 / 8 | 20 / 7 |
| Error Prone + javac lint warnings (`-PallWarnings`) | 1,227 | 853 |
| PMD / SpotBugs findings; PMD god classes | 626 / 113; 16 | 469 / 104; 15 |
| Dead code (`tools/analysis/dead-code.py`) | 9 classes, 109+ methods | 0 classes; 10 methods, all intended (B3's events and test-only accessors) |
| Static global access (`Datavyu.getView()`, `getApplication()`, `getVideoController()`, `getProjectController()`) | about 330 call sites | 273 |

## 1. Security and Data Loss (deferred to the project-file redesign)

Each fix needs a test that fails without it.

**S1. Opening a project file can instantiate arbitrary Java classes (verified).** `DatavyuProjectConstructor` extends SnakeYAML 1.18's full `Constructor`, which resolves any `!!fully.qualified.Class` tag. A `project` entry of `!!java.io.File ["/tmp/x"]` returned a `java.io.File`. Published gadget chains turn this into code execution, and `.opf` files are exactly what researchers exchange. The JSON redesign removes YAML from project files; until then, do not open `.opf` files from untrusted sources. (`Citation` also parses YAML, but only the bundled `CITATION.cff`.)

**S2. Log4j 2.11.2 evaluates lookups inside log messages (verified).** Logging `"${java:version}"` printed `Java version 25.0.4.1`. This is the Log4Shell precondition: a file path containing `${jndi:ldap://…}` makes Log4j open a network connection, which also breaks the fork's "no network access" property (`NoNetworkAccessTest` only inspects the application's own classes). Fix: upgrade to Log4j 2.26.x, where message lookups no longer exist. While there, move the log from `all.log` in the working directory (DEBUG, overwritten each launch, starting with the full class path) to `~/Library/Logs/Watavyu/` at INFO. This does not touch persistence and could be done now.

**S3. Other vulnerable libraries (verified with OSV.dev).** jackson-databind 2.9.9.2 (54 advisories; most need polymorphic typing, which this code does not enable), jackson-core 2.9.9 (4), SnakeYAML 1.18 (8, including S1), Log4j 2.11.2 (7, including S2), commons-io 2.5 (2), Guava 27.1 (2), commons-lang3 3.6 (1). See [section 3](#3-dependencies).

**B1. Quitting can discard unsaved work (from reading).** `Datavyu.safeQuit()` loops over the open tabs but `return`s after prompting for the first one with changes, so other modified tabs are never asked about. Separately, closing the Save / Don't Save / Cancel dialog with Esc or its close button returns `CLOSED_OPTION`, which the code treats as "go ahead", so the app quits, or the tab closes, without saving.

**B2. The app changes a global macOS setting (from reading).** At class-load time `Datavyu` runs `defaults write -g ApplePressAndHoldEnabled -bool false` for every application on the Mac, and only restores it on a clean shutdown; a crash or force-quit leaves it changed. The intent is key repeat instead of the accent popup while holding keys. Scope it to the app (a per-app defaults domain once there is an app bundle, or in-app key repeat), then delete the global write. Not a persistence change; could be done now.

**B3. Two data-store events are never fired (verified with the dead-code scan).** `DataStoreListener.variableNameChange` and `variableOrderChanged` are implemented by `SpreadSheetPanel` and `VariableListV`, but no data store calls them, so the Variable List window probably goes stale when a column is renamed or reordered (not checked in the running app).

**B4. Thread safety.** SpotBugs reports 31 thread-safety findings, including inconsistently synchronized fields in `ClockTimer` and `NeedleModelImpl` and a static `SimpleDateFormat` in `VideoController` used from more than one thread. Review them with R7.

## 2. Tests

No characterization suite (see [Decisions](#decisions)). New and rebuilt behaviour gets tests as it lands. The JSON project format, in particular, should be specified by round-trip and schema tests from the start.

## 3. Dependencies

Removed in the first pass: the Swing Application Framework (`appframework`), `swing-layout`, `fest-swing`, JNA, commons-text, JRuby and RCaller.

| Library | Now → latest stable | Used by | Action |
|---|---|---|---|
| `org.apache.logging.log4j` | 2.11.2 → 2.26.1 | most classes | Upgrade (S2) |
| `org.yaml:snakeyaml` | 1.18 → 2.7 | `.opf` project entry, citation | Goes with the JSON redesign; the citation can parse its few keys without it or use 2.x |
| `com.fasterxml.jackson.core` | 2.9.9 → 2.22.3 | JSON import/export, settings | Upgrade; the JSON project format will rely on it |
| `com.miglayout:miglayout` | 3.7.4 → `miglayout-swing` 11.x | 6 files | Depends on the UI direction |
| `commons-io`, `commons-lang3`, Guava | 2.5 / 3.6 / 27.1 → 2.22 / 3.21 / 33.7 | a few files each | Upgrade, or replace the remaining uses with the JDK |
| `org.testng:testng` | 6.11 → 7.12.0 | tests | Upgrade. Moving to JUnit 5 is optional. |
| OpenJFX | 27 | video | Current. 28 is early access. |

Also move the versions into a Gradle version catalog (`gradle/libs.versions.toml`) and enable dependency verification (checksums).

## 4. Structural Refactors

**R2. Split the god classes.** PMD flags 15. The ones that matter most:
- `DatavyuView` (1,670 lines): menus and shortcuts, file open/save/export, tab management, zoom.
- `VideoController` (1,539): transport, cell timing, step size and the track panel.
- `SpreadSheetPanel`, `SpreadsheetColumn`, `SpreadsheetCell`, `EditorComponent`: the spreadsheet editing core.
- `MixerController` / `TrackController`: the timeline.

Split along responsibilities (menu model, document/file service, spreadsheet view, transport) rather than by line count. The project-file redesign is a natural moment to pull out the document/file service.

**R3. Replace static singletons with explicit wiring.** 273 call sites reach the app through `Datavyu.getView()`, `getApplication()`, `getVideoController()` and `getProjectController()`. `getProjectController()` returns different objects depending on whether a tab exists (`// TODO: Why do we have these two project controllers?`). Introduce a per-document object (data store, project, undo manager, playback) and pass it explicitly. Global state is what makes the views untestable.

**R4 (remainder). Keyboard.** App-level shortcuts are now one table (`Shortcuts`). Left over:
- `SpreadSheetPanel` registers its own global key dispatcher for ⌘←/⌘→ column selection. When at most one column is visible, it consumes every left and right arrow press in every window, which probably stops the caret moving inside a cell (from reading).
- `EditorTracker` and the value editors handle keys per editor, with 22 uses of the deprecated modifier API (`getModifiers`, `*_MASK`, `getMenuShortcutKeyMask`).
- A backslash typed without ⌘ in a cell falls through to Backspace handling in `EditorTracker.keyPressed` (from reading).

**R5. One timestamp type.** `HH:MM:SS:mmm` is parsed and formatted separately in `DatavyuCell`, `VideoController` (two static `SimpleDateFormat`s over `java.util.Date`), `SpreadSheetEdit`, `TimeStampTextField` and the timescale painter. Replace them with one small tested utility built on `long` milliseconds or `java.time.Duration`; the JSON format will need it too.

**R7. Threading.** Make the boundary between the clock thread, the JavaFX thread and the EDT explicit, as `JavaFxPlayback` already does for the player. Start with B4.

**R8. Mechanical language modernization (low priority).** Diamond operators, switch expressions, pattern-matching `instanceof`, `java.time`, missing `@Override`. Error Prone can apply most of these itself (`-XepPatchChecks:MissingOverride,... -XepPatchLocation:IN_PLACE`). Best done in one automated pass, or per file as files are changed anyway.

**Smaller leftovers.**
- `ClockTimer` boundary checks pause playback at time 0, and shuttle does nothing at exactly time 0 (`VideoController.shuttle` requires a time greater than 0). Both predate this pass; check whether they are intended.
- `AutoSaveController` and the crash-recovery prompt in `DatavyuView` still say "Datavyu" and recover `~*.opf`/`~*.csv` files from the temp directory. This belongs to the persistence redesign.
- `VideoController` and the export controllers still use `MacOS.getOptions` to order dialog buttons by hand. `JOptionPane` with standard options would do it.

## Done

The first cleanup pass on 2026-10-14:

- **Deleted features:** everything listed under Decisions. Old `.opf` files with bookmarks still open; the bookmarks are ignored and no longer written. Without the region of interest, playback always spans the whole timeline, and the timeline's magnifier button zooms to fit all tracks.
- **Deleted platform and dead code:**
  - Windows and Linux code (`WindowsOS`, platform file choosers, the `Platform` enum and its branches, the Windows DLL and EXE).
  - The plugin system around the single OpenJFX player. Project files still record the same player UUID, namespace and viewer class.
  - 9 unreachable classes and more than 100 never-invoked methods.
  - Unused resources, test fixtures and FEST helpers, and the `BugzID` comments.
  - The hidden Push/Pull menu items.
- **R1:** the Swing Application Framework is replaced by a plain `main`, Swing `Action`s, `Resources` over `ResourceBundle`, and `WindowPlacement`. The main window, the video controller and each video window come back where they were left; other dialogs open centred.
- **R4:** one `Shortcuts` table drives menu accelerators, the keypad, and Help > Keyboard Shortcuts List, which is generated from it. Menu shortcuts now also work from the video controller and video windows. The video controller no longer takes over the main window's menu bar. Each video window no longer registers its own copy of the key dispatcher.
- **R6 (settings part):** `Settings` writes `~/Library/Application Support/Watavyu/settings.json` (last folder, five recent projects, window positions). Spreadsheet colours and fonts are constants in `SpreadsheetTheme`. Tests use their own settings directory. The `.opf` part is left to the redesign.
- **Fixed in passing:**
  - Save As offered MacSHAPA `.odb` but wrote nothing (gone with the format).
  - The recent-files list kept four entries instead of the intended five.
  - The column-name warning recommended a favorites script that no longer exists.
  - ⌘W from the video controller asked about saving but did not close the tab.

## Tooling

See BUILDING.md, under Lint and Analysis:

- `./gradlew analyze` writes JaCoCo coverage, PMD (`config/pmd/ruleset.xml`) and SpotBugs (`config/spotbugs/exclude.xml`) reports under `build/reports/`. They do not fail the build yet.
- `./gradlew compileJava -PallWarnings` lists every javac lint and Error Prone warning.
- `./gradlew dependencyUpdates` lists stable updates.
- `tools/analysis/dead-code.py` lists unreachable classes and never-invoked methods. It now also sees method references.
- `tools/analysis/osv-check.py` lists runtime dependencies with known vulnerabilities.

Ratchet plan: when an Error Prone check, PMD rule or SpotBugs pattern reaches zero, promote it to a build failure so it cannot regress.

## Re-measuring

```sh
./gradlew analyze                          # coverage, PMD, SpotBugs under build/reports
./gradlew compileJava -PallWarnings        # every compiler warning
./gradlew dependencyUpdates                # outdated dependencies
./gradlew classes && tools/analysis/dead-code.py
./gradlew -q dependencies --configuration runtimeClasspath | tools/analysis/osv-check.py
```
