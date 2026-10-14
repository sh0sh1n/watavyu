# TODO

Known bugs and planned work, in place of GitHub issues. Finished items are deleted, not ticked; the commit that finishes one names its ID. How to keep this file: [AGENTS.md](AGENTS.md#keeping-todomd-and-agentsmd).

Each item says what blocks it and how it is known:

- **ready**: can be done now. **after-format**: waits for the JSON project format (F1). **after-ui**: waits for the UI direction (U1).
- *verified*: reproduced or measured. *from reading*: seen in the code, not exercised in the running app.

Items are in priority order within each section.

## Measures

Updated 2026-10-14. Re-measure with the commands in [BUILDING.md › Analysis Reports](BUILDING.md#analysis-reports).

| Measure | Before cleanup (2026-10-14) | Now |
|---|---|---|
| Java source (main) | 242 files, 42.5k lines | 175 files, 30.8k lines |
| Tests / line coverage | 113 / 9.9% | 154 / 14.7%; still 0% in most views, the timeline and undo |
| Runtime dependencies / with known vulnerabilities | 28 / 8 | 20 / 7 |
| Error Prone + javac lint warnings (`-PallWarnings`) | 1,227 | 853 |
| PMD / SpotBugs findings; PMD god classes | 626 / 113; 16 | 469 / 104; 15 |
| Never-invoked methods (`dead-code.py`) | 109+ | 10, all intended: B3's events and test-only accessors |
| Static global access (`Datavyu.getView()` and friends) | about 330 call sites | 271 |

## Security

**S1. Opening a project file can instantiate arbitrary Java classes.** after-format, *verified*. `DatavyuProjectConstructor` extends SnakeYAML 1.18's full `Constructor`, which resolves any `!!fully.qualified.Class` tag: a `project` entry of `!!java.io.File ["/tmp/x"]` returned a `java.io.File`. Published gadget chains turn this into code execution, and `.opf` files are what researchers exchange. F1 removes YAML from project files; until then, do not open `.opf` files from untrusted sources. (`Citation` also parses YAML, but only the bundled `CITATION.cff`.)

**S2. Log4j 2.11.2 evaluates lookups inside log messages.** ready, *verified*. Logging `"${java:version}"` printed `Java version 25.0.4.1`. This is the Log4Shell precondition: a file path containing `${jndi:ldap://…}` makes Log4j open a network connection, which also breaks the no-network property (`NoNetworkAccessTest` only inspects the application's own classes). Upgrade to Log4j 2.26.x, where message lookups no longer exist. While there, move the log from `all.log` in the working directory (DEBUG, overwritten each launch, starts with the full class path) to `~/Library/Logs/Watavyu/` at INFO.

**S3. Other vulnerable libraries.** *verified with OSV.dev*. jackson-databind 2.9.9.2 (54 advisories; most need polymorphic typing, which this code does not enable), jackson-core 2.9.9 (4), SnakeYAML 1.18 (8, including S1), Log4j 2.11.2 (7, including S2), commons-io 2.5 (2), Guava 27.1 (2), commons-lang3 3.6 (1). Fixed by D1.

## Bugs

**B1. Quitting can discard unsaved work.** ready, *from reading*. `Datavyu.safeQuit()` loops over the open tabs but `return`s after prompting for the first one with changes, so other changed tabs are never asked about. Closing the Save / Don't Save / Cancel dialog with Esc or its close button returns `CLOSED_OPTION`, which the code treats as "go ahead", so the app quits, or the tab closes, without saving.

**B2. The app changes a global macOS setting.** ready, *from reading*. At class-load time `Datavyu` runs `defaults write -g ApplePressAndHoldEnabled -bool false` for every application on the Mac, and restores it only on a clean shutdown; a crash or force-quit leaves it changed. The intent is key repeat instead of the accent popup while holding keys. Scope it to the app (a per-app defaults domain once there is an app bundle, or in-app key repeat), then delete the global write.

**B3. Two data-store events are never fired.** ready, *verified with the dead-code scan*. `DataStoreListener.variableNameChange` and `variableOrderChanged` are implemented by `SpreadSheetPanel` and `VariableListV`, but no data store calls them, so the Column List window probably goes stale when a column is renamed or reordered.

**B4. Thread safety.** ready, *SpotBugs*. 31 thread-safety findings, including inconsistently synchronized fields in `ClockTimer` and `NeedleModelImpl`, and a static `SimpleDateFormat` in `VideoController` used from more than one thread. Do with R7.

**B5. Arrow keys may not move the caret in a cell.** ready, *from reading*. `SpreadSheetPanel` registers its own global key dispatcher for ⌘←/⌘→ column selection. When at most one column is visible, it consumes every left and right arrow press in every window.

**B6. Backslash in a cell is handled as Backspace.** ready, *from reading*. A `\` typed without ⌘ falls through to the Backspace handling in `EditorTracker.keyPressed`.

**B7. Playback edges at time 0.** ready, *from reading*. `ClockTimer` boundary checks pause playback at time 0, and shuttle does nothing at exactly 0 (`VideoController.shuttle` requires a time above 0). Both predate the cleanup; check whether they are intended.

## Features

**F1. JSON project format.** Direction decided, design open: ask the owner before designing (see [AGENTS.md › Undecided](AGENTS.md#undecided)). One transparent JSON file replaces the `.opf` zip and holds the whole spreadsheet and project. Videos become data columns, and each video's start offset becomes an explicit onset. Specify it with round-trip and schema tests from the start. It removes S1, needs R5, and unblocks F2 and R9.

**F2. Snapping when dragging a track.** after-format. Wanted for easy alignment; removed with bookmarks and the region of interest. The old version (`TracksEditorController.snapOffset` on `main` before the cleanup) snapped a track's start, end and bookmarks to time zero, the needle, the region of interest and the other tracks' starts, ends and bookmarks, within 5 seconds. Rebuild it around video columns and their onsets.

## Research

**U1. UI direction.** ready, needs the owner. A research document comparing options for a modern UI, kept separate from this file. Questions it should answer include whether to dock the video controller in the main window, using the space the removed drawers freed, and what this means for MigLayout (D1).

## Dependencies

**D1. Upgrade or drop old libraries.**

| Library | Now → latest stable | Used by | Action |
|---|---|---|---|
| Log4j | 2.11.2 → 2.26.1 | most classes | ready: upgrade (S2) |
| Jackson | 2.9.9 → 2.22.3 | JSON import/export, settings | ready: upgrade; F1 will rely on it |
| SnakeYAML | 1.18 → 2.7 | `.opf` project entry, citation | after-format: drop with F1; the citation can parse its few keys without it |
| commons-io, commons-lang3, Guava | 2.5 / 3.6 / 27.1 → 2.22 / 3.21 / 33.7 | a few files each | ready: upgrade, or replace the remaining uses with the JDK |
| TestNG | 6.11 → 7.12.0 | tests | ready: upgrade; JUnit 5 is optional |
| MigLayout | 3.7.4 → `miglayout-swing` 11.x | 6 files | after-ui |

**D2. Version catalog and dependency verification.** ready. Move versions into `gradle/libs.versions.toml` and enable Gradle dependency verification (checksums).

## Refactors

**R2. Split the god classes.** PMD flags 15. Split along responsibilities (menu model, document and file service, spreadsheet view, transport) rather than by line count; F1 is a natural moment to pull out the document service.
- `DatavyuView` (1,666 lines): menus and shortcuts, file open/save/export, tabs, zoom.
- `VideoController` (1,539): transport, cell timing, step size and the track panel.
- `SpreadSheetPanel`, `SpreadsheetColumn`, `SpreadsheetCell`, `EditorComponent`: the spreadsheet editing core.
- `MixerController`, `TrackController`: the timeline.

**R3. Replace static singletons with explicit wiring.** 271 call sites reach the app through `Datavyu.getView()`, `getApplication()`, `getVideoController()` and `getProjectController()`, and `getProjectController()` returns different objects depending on whether a tab exists. Introduce a per-document object (data store, project, undo manager, playback) and pass it explicitly. Global state is what keeps the views untestable.

**R4. Per-editor key handling.** `EditorTracker` and the value editors handle keys per editor, with 22 uses of the deprecated modifier API (`getModifiers`, `*_MASK`, `getMenuShortcutKeyMask`). Move what can go into `Shortcuts`; fix B5 and B6 on the way.

**R5. One timestamp type.** `HH:MM:SS:mmm` is parsed and formatted separately in `DatavyuCell`, `VideoController` (two static `SimpleDateFormat`s over `java.util.Date`), `SpreadSheetEdit`, `TimeStampTextField` and the timescale painter. Replace them with one tested utility on `long` milliseconds or `java.time.Duration`. F1 needs it.

**R7. Threading.** Make the boundary between the clock thread, the JavaFX thread and the EDT explicit, as `JavaFxPlayback` already does for the player. Start with B4.

**R8. Mechanical language modernization.** Low priority. Diamonds, switch expressions, pattern-matching `instanceof`, `java.time`, missing `@Override`. Error Prone can apply most of it (`-XepPatchChecks:MissingOverride,... -XepPatchLocation:IN_PLACE`), in one pass or per file as files change.

**R9. Autosave and crash recovery.** after-format. `AutoSaveController` and the recovery prompt in `DatavyuView` still say "Datavyu" and recover `~*.opf`/`~*.csv` files from the temp directory.

**R10. Hand-ordered dialog buttons.** ready. `Datavyu`, `VideoController` and `ExportDatabaseFileController` order dialog buttons with `MacOS.getOptions` and then compare the chosen index by hand. `JOptionPane` with standard options does it, and removes the index confusion behind part of B1.
