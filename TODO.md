# TODO

Known bugs and planned work, in place of GitHub issues. Finished items are deleted, not ticked; the commit that finishes one names its ID. How to keep this file: [AGENTS.md](AGENTS.md#keeping-todomd-and-agentsmd).

Each item says what blocks it and how it is known:

- **ready**: can be done now. **after-ui**: waits for the UI direction (U1). **after-F4**: waits for media columns.
- *verified*: reproduced or measured. *from reading*: seen in the code, not exercised in the running app.

Items are in priority order within each section.

## Measures

Updated 2026-10-14. Re-measure with the commands in [BUILDING.md › Analysis Reports](BUILDING.md#analysis-reports).

| Measure | Before cleanup (2026-10-14) | Now |
|---|---|---|
| Java source (main) | 242 files, 42.5k lines | 184 files, 31.4k lines |
| Tests / line coverage | 113 / 9.9% | 289 / 17.2%; still 0% in most views, the timeline and undo |
| Runtime dependencies / with known vulnerabilities | 28 / 8 | 21 / 4 |
| Error Prone + javac lint warnings (`-PallWarnings`) | 1,227 | 837 |
| PMD / SpotBugs findings; PMD god classes | 626 / 113; 16 | 463 / 97; 15 |
| Never-invoked methods (`dead-code.py`) | 109+ | 10, all intended: B3's events and test-only accessors |
| Static global access (`Datavyu.getView()` and friends) | about 330 call sites | 272 |

## Security

**S3. Other vulnerable libraries.** *verified with OSV.dev*. SnakeYAML 1.18 (8 advisories; opening `.opf` files now uses its `SafeConstructor`, which refuses tags naming Java classes, but the parser's denial-of-service advisories remain), commons-io 2.5 (2), Guava 27.1 (2), commons-lang3 3.6 (1). Fixed by D1.

## Bugs

**B2. The app changes a global macOS setting.** ready, *from reading*. At startup `Datavyu` runs `defaults write -g ApplePressAndHoldEnabled -bool false` for every application on the Mac, and restores it only on a clean shutdown; a crash or force-quit leaves it changed. (It used to run when the class loaded, so every test run turned the setting off and never restored it.) The intent is key repeat instead of the accent popup while holding keys. Scope it to the app (a per-app defaults domain once there is an app bundle, or in-app key repeat), then delete the global write.

**B3. Two data-store events are never fired.** ready, *verified with the dead-code scan*. `DataStoreListener.variableNameChange` and `variableOrderChanged` are implemented by `SpreadSheetPanel` and `VariableListV`, but no data store calls them, so the Column List window probably goes stale when a column is renamed or reordered.

**B4. Thread safety.** ready, *SpotBugs*. 25 thread-safety findings, including inconsistently synchronized fields in `ClockTimer` and `NeedleModelImpl`. Do with R7.

**B5. Arrow keys may not move the caret in a cell.** ready, *from reading*. `SpreadSheetPanel` registers its own global key dispatcher for ⌘←/⌘→ column selection. When at most one column is visible, it consumes every left and right arrow press in every window.

**B6. Backslash in a cell is handled as Backspace.** ready, *from reading*. A `\` typed without ⌘ falls through to the Backspace handling in `EditorTracker.keyPressed`.

**B9. Typing in a cell of a column with no codes shows text that is not kept.** ready, *verified by the owner*. In a column whose codes have all been deleted, typed text appears outside the parentheses but is not in the cell's value, so it is not saved. With no codes the cell has no value editor (`DataValueEditorFactory`), so `MatrixRootView`'s text area takes the keys itself (`EditorTracker`). It should refuse typing, or the column should not lose its last code.

**B7. Playback edges at time 0.** ready, *from reading*. `ClockTimer` boundary checks pause playback at time 0, and shuttle does nothing at exactly 0 (`VideoController.shuttle` requires a time above 0). Both predate the cleanup; check whether they are intended.

## Features

**F4. Media columns.** ready. A media column is one media window, and its cells are clips that replace each other in it: gaps show blank frames, the later clip wins where clips overlap, and clips slide without trimming (rules in [AGENTS.md › Decisions](AGENTS.md#decisions)). Today each video is its own window and track (`VideoController`, `MixerController`), and opening a project file with more than one clip in a media column is refused (`OpenController.openProjectFile`). How media columns appear in the spreadsheet depends on U1. Audio files are to be supported as media too.

**F5. One kind of annotation column in the app.** needs the owner (migration, see AGENTS.md › Undecided), *verified by `SpreadsheetColumnsTest`*. New Column already makes only matrix columns (`NewVariableV.getVariableType`); text and nominal columns now come only from Datavyu's `.opf`/`.csv` files and JSON import. Such a column is saved as an annotation column with one code and reopens as a matrix column, shown as `(value)`. Converting them when they open would let the text and nominal editors go.

**F2. Snapping when dragging a track.** after-F4. Wanted for easy alignment; removed with bookmarks and the region of interest. The old version (`TracksEditorController.snapOffset` on `main` before the cleanup) snapped a track's start, end and bookmarks to time zero, the needle, the region of interest and the other tracks' starts, ends and bookmarks, within 5 seconds. Rebuild it around media columns and their onsets.

## Research

**U1. UI direction.** ready, needs the owner. A research document comparing options for a modern UI, kept separate from this file. Questions it should answer include whether to dock the video controller in the main window, using the space the removed drawers freed, and what this means for MigLayout (D1). The owner intends to phase out the separate controller window; when it goes, revisit media clips starting before time zero (a hack today, see AGENTS.md › Decisions).

**U2. Project structure.** needs the owner. Spreadsheets are usually instances of a project template, and coding passes (columns and their codes) are reused across experiments. Datavyu handled this with Ruby scripts that created a spreadsheet, or migrated one when the template changed; that worked imperfectly and is not to be recreated. Explore how projects, templates, spreadsheets and portable coding passes relate, likely as a folder hierarchy, and where large videos live (often copied locally from a network share for speed). The project file holds everything until this is settled.

**U3. Write-ahead log and event sourcing.** Consider recording every edit as an event in an append-only log beside the project file, for data integrity and disaster recovery. Volume is small (tens of thousands of events, perhaps millions). It would likely replace R9's autosave and could feed undo.

## Dependencies

**D1. Upgrade or drop old libraries.**

| Library | Now → latest stable | Used by | Action |
|---|---|---|---|
| SnakeYAML | 1.18 → 2.7 | opening `.opf` files, citation | drop with `.opf` opening (a migration question, see AGENTS.md › Decisions); the citation can parse its few keys without it. Until then, upgrade |
| commons-io, commons-lang3, Guava | 2.5 / 3.6 / 27.1 → 2.22 / 3.21 / 33.7 | a few files each | ready: upgrade, or replace the remaining uses with the JDK |
| TestNG | 6.11 → 7.12.0 | tests | ready: upgrade; JUnit 5 is optional |
| MigLayout | 3.7.4 → `miglayout-swing` 11.x | 6 files | after-ui |

**D2. Version catalog and dependency verification.** ready. Move versions into `gradle/libs.versions.toml` and enable Gradle dependency verification (checksums).

## Refactors

**R2. Split the god classes.** PMD flags 15. Split along responsibilities (menu model, document and file service, spreadsheet view, transport) rather than by line count. The project file itself is in `org.datavyu.projectfile`; saving and opening in `DatavyuView` and `ProjectController` are the next document-service piece to pull out.
- `DatavyuView` (1,666 lines): menus and shortcuts, file open/save/export, tabs, zoom.
- `VideoController` (1,539): transport, cell timing, step size and the track panel.
- `SpreadSheetPanel`, `SpreadsheetColumn`, `SpreadsheetCell`, `EditorComponent`: the spreadsheet editing core.
- `MixerController`, `TrackController`: the timeline.

**R3. Replace static singletons with explicit wiring.** 271 call sites reach the app through `Datavyu.getView()`, `getApplication()`, `getVideoController()` and `getProjectController()`, and `getProjectController()` returns different objects depending on whether a tab exists. Introduce a per-document object (data store, project, undo manager, playback) and pass it explicitly. Global state is what keeps the views untestable: `ProjectController.loadProject`, which opens a project file's videos and keeps missing ones for saving, is checked only by hand because its dialogs reach the app through `Datavyu.getApplication()`.

**R4. Per-editor key handling.** `EditorTracker` and the value editors handle keys per editor, with 22 uses of the deprecated modifier API (`getModifiers`, `*_MASK`, `getMenuShortcutKeyMask`). Move what can go into `Shortcuts`; fix B5 and B6 on the way.

**R7. Threading.** Make the boundary between the clock thread, the JavaFX thread and the EDT explicit, as `JavaFxPlayback` already does for the player. Start with B4.

**R8. Mechanical language modernization.** Low priority. Diamonds, switch expressions, pattern-matching `instanceof`, `java.time`, missing `@Override`. Error Prone can apply most of it (`-XepPatchChecks:MissingOverride,... -XepPatchLocation:IN_PLACE`), in one pass or per file as files change.

**R9. Autosave and crash recovery.** ready. `AutoSaveController` saves a `~<name>_*.watavyu.json` project file to the temporary folder every minute; at launch, `DatavyuView.checkForAutosavedFile` offers to recover any left in the temporary folder (Datavyu's `~*.opf`/`~*.csv` too) and opens one as an unsaved project; that flow is checked only by reading, as its dialogs need clicks. The temporary folder is shared with other apps and cleared by macOS. Settle this together with U3.

**R10. Hand-ordered dialog buttons.** ready. `Datavyu.overwriteExisting`, `VideoController` and `ExportDatabaseFileController` order dialog buttons with `MacOS.getOptions` and then compare the chosen index by hand. Read the answer by the chosen button's label, as `UnsavedWork.choice` does for the unsaved-changes dialog, or use `JOptionPane`'s standard options.
