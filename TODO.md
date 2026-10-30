# TODO

Known bugs and planned work, in place of GitHub issues. Finished items are deleted, not ticked; the commit that finishes one names its ID. How to keep this file: [AGENTS.md](AGENTS.md#keeping-todomd-and-agentsmd).

Each item says what blocks it and how it is known:

- **ready**: can be done now. **needs the owner**: waits on a product decision. **future**: kept for later consideration, not planned.
- *verified*: reproduced or measured. *from reading*: seen in the code, not exercised in the running app.

Items are in priority order within each section.

## Measures

Updated 2026-10-29. Re-measure with the commands in [BUILDING.md › Analysis Reports](BUILDING.md#analysis-reports).

| Measure | Before cleanup (2026-10-14) | Now |
|---|---|---|
| Java source (main) | 242 files, 42.5k lines | 157 files, 27.5k lines |
| Tests / line coverage | 113 / 9.9% | 457 / 35.8%; still 0% in most views and undo |
| Runtime dependencies / with known vulnerabilities | 28 / 8 | 12 / 0 at the last check; FlatLaf, added for U5, not yet checked |
| Error Prone + javac lint warnings (`-PallWarnings`) | 1,227 | 608 |
| PMD / SpotBugs findings; PMD god classes | 626 / 113; 16 | 426 / 74 (none on thread safety, now gated); 16 |
| Never-invoked methods (`dead-code.py`) | 109+ | 20: test-only accessors, and Swing overrides the script cannot see |
| Static global access (`grep -rohP 'Datavyu\.(get\|set)\w*\(' src/main/java \| wc -l`) | about 330 call sites | 250 (259 before F6; earlier figures counted differently) |

## Bugs

**B2. The app changes a global macOS setting.** ready (owner: scope it to the app), *from reading*. At startup `Datavyu` runs `defaults write -g ApplePressAndHoldEnabled -bool false` for every application on the Mac, and restores it only on a clean shutdown; a crash or force-quit leaves it changed. (It used to run when the class loaded, so every test run turned the setting off and never restored it.) The intent is key repeat instead of the accent popup while holding keys. Scope it to the app: in-app key repeat, or a per-app defaults domain once there is an app bundle; then delete the global write.

**B11. Crash recovery is lost on an ordinary exit, and recovering needs a dialog.** ready, *verified*. `AutoSaveController` marks each autosave `deleteOnExit`, so the JVM deletes it on every orderly exit: quitting, Ctrl+C in the terminal running `make run`, or a plain `kill`. On 2026-10-30 an autosave written after the app's first minute was gone 2 seconds after SIGINT. Only exits that skip the JVM's shutdown hooks (SIGKILL, a JVM crash, a power cut) leave one behind, which is why the recovery dialog no longer appears. When one is left, `DatavyuView.checkForAutosavedFile` asks about every `~*.watavyu.json` in the temporary folder at launch: the dialog itself is a chore (owner), and it includes the autosave a running Watavyu is writing, which answering No deletes. Owner's direction: open unsaved autosaves without asking, and only those no running Watavyu owns (a lock file, or the owning process recorded in the autosave's name). An autosave should go only when its work was saved or knowingly discarded (through `UnsavedWork`'s prompt), not on any exit. With that, add a safeguard against saving over a newer file: if the project file changed on disk since it was opened or last saved (by another Watavyu, a recovered copy or another tool), say so before overwriting it. Settle with R9.

**B14. Adding media cannot be undone.** ready, *from reading*. Spreadsheet > Add Media and dropping files post no undoable edit (`MediaPlayers.addMediaFiles`), while deleting the clip or column afterwards can be undone. Undo should take an added clip or column out again, closing its player.

**B16. A Datavyu file with an unreadable time fails to open without saying why.** ready, *from reading*. Opening a `.csv` (`OpenDataStoreFileController.openAsCsv`) counts an unreadable time in a matrix column as a cell it could not read, but in a text or nominal column the exception ends the whole read, which returns null, and `OpenController.openDataStore` then fails on it. JSON import (`DatavyuView.importJSON`) catches only `UserWarningException`, `JsonParseException` and `IOException`, so an unreadable time leaves the columns read so far and no message. Since B13, negative times, which Datavyu could write, are unreadable too. Each should refuse the file and say which time is wrong.

## Features

**F7. Configurable key mapping for playback.** ready. The numpad shortcuts (`Shortcuts`, the keypad section) are condensed into a smaller set of playback and coding keys that the user can map, so coding works on laptops without a numpad. Settings would hold the mapping. Goes with F16 and with finding, by trial, which buttons the controls drawer keeps.

**F12. Edit history in the controls drawer.** ready, medium priority (owner). Below the controls, a list of the edits that can be undone and redone, newest first, from the spreadsheet's undo history (`SpreadsheetUndoManager`; each edit has a presentation name, and `SpreadSheetEdit.toString` already formats one with its time). Hiding the drawer hides both, so the space can be reclaimed with one toggle. Datavyu's separate Undo History window was removed in the cleanup; this replaces it. B14 (adding media is not undoable) shows up here.

**F10. Offsets below onsets, and point cells without offsets.** ready, medium to low priority (owner). A cell shows its onset and offset side by side, which sets a column's minimum width. Put the offset at the bottom of the cell, so columns can shrink thinner, and add a setting to leave the offset out entirely for point cells (offset = onset), which new cells are by default (`SpreadsheetCell`, `TimeStampDataValueEditor`, the column width limits in `SpreadsheetColumn`).

**F11. Shorter cell times.** ready, low priority (owner). Where a cell's onset and offset both start with zero fields, leave those fields out of both: `00:00:04:028` and `00:08:04:028` show as `00:04:028` and `08:04:028`. The playhead keeps every field. Editing a shortened time needs a rule for what is typed.

**F2. Dragging clips in the spreadsheet, with snapping.** future (owner: low priority; may be dropped). Clips move only by typing their onset. Dragging a clip cell would move it, snapping for easy alignment as Datavyu's track dragging did: `TracksEditorController.snapOffset` on `main` before the cleanup snapped a track's start, end and bookmarks to time zero, the needle, the region of interest and the other tracks' starts, ends and bookmarks, within 5 seconds. A locked column's clips stay put.

**F13. Light or dark while running: the Mac's, or the user's choice.** ready, low priority, *from reading*. The palette is chosen once at startup (`SpreadsheetTheme.PALETTE`, from `Appearance`, which the `watavyu.appearance` property can force), so a change of macOS appearance shows only after a restart, and there is no choice in the app. Wanted (owner): follow the Mac by default, and let the user switch between light and dark at any time while running. Scope, a day or two, mostly mechanical:
- Setting and menu: `appearance` in `Settings` (`system`, `light` or `dark`, default `system`) and a three-way choice in a menu; which menu is the owner's. This revises the 2026-10-14 decision that colours follow the Mac rather than a setting.
- A palette that can change: `PALETTE` is a `static final` read at 60 places in 17 files. It becomes an accessor, and the four constants built from it when their classes load (`SpreadsheetEmptyCell.NORMAL_BORDER`, `SpreadsheetView.PLAYHEAD_LINE`, `VocabElementV.FOCUSED` and `WARNING_MARK`) are made when used.
- Repainting: a switch gives FlatLaf the new palette's defaults, installs `FlatMacLightLaf` or `FlatMacDarkLaf` and calls `FlatLaf.updateUI()`, which walks every window. Components that take palette colours once in their constructors (cells, headers, the drawer, the playhead, the code editor) re-read them in `updateUI`. Missed spots keep the old colours until a restart; they are found by switching back and forth in the running app.
- Title bars already follow the Mac (`apple.awt.application.appearance=system`). A choice that differs from the Mac needs each window's `apple.awt.windowAppearance` client property (`NSAppearanceNameAqua` or `NSAppearanceNameDarkAqua`), to be confirmed on this JDK.
- Noticing the Mac change: Java has no event for it. The cheapest way is to re-read `AppleInterfaceStyle` (`Appearance`, which runs `defaults`, off the EDT) whenever Watavyu becomes the active app, since the change is made in System Settings. A timer or a native notification listener are the alternatives.
- Tests: how a setting and the Mac's appearance resolve to a palette, and that a switch re-colours a sample component tree (headless). The rest is checked in the running app.

**F14. A clearer selected column header in Paper.** ready, low priority, *verified* (contrast measured). A selected column's header has a bar in the selection colour along its bottom (`SpreadsheetColumn.headerBorder`), at 2.7:1 against Paper's chrome, under the 3:1 the other marks keep (`PaletteTest`). Use the accent, a thicker bar, or a selection colour tuned for chrome.

**F15. A clip's tail after the last annotation is a sliver.** ready, *verified* (owner, in `~/Movies/test.watavyu.json`). In temporal layout a stretch of time gets height only from the cells that start in it (`SheetLayoutWeakTemporal`: each distinct onset gets its cells' height plus a 15-pixel gap), so a clip that runs on past every annotation shows that stretch as a few pixels. In the test project the second clip of `media_1` runs from 00:09:27:876 to 01:00:23:909, and the half hour after the fifth `bookmarks` cell (00:31:06:516) is a thin strip at the clip's bottom, which the playhead's needle crosses in a few pixels. Give such a tail enough height to see and to scrub through, as a minimum or growing with its duration; how much is the owner's.

**F16. One play/pause key, and stop becomes reset to 1×.** ready (owner to confirm the details below), *from reading*. Owner's proposal, evaluated: it is small (about half a day with tests) and loses nothing. Today play (keypad 8) sets 1× and plays; pause (2) already toggles, resuming at the rate it paused at; stop (5) sets the rate to 0, after which only play resumes (`VideoController.startAction`, `pauseAction`, `stopAction`, `ClockTimer`). Stop differs from pause only in forgetting the rate, and the end of media and of the timeline already pause rather than stop. Merged: 8 plays or pauses, resuming at the paused rate; 5 sets the rate back to 1×; 2 is freed. The play key shows the pause glyph while playing, so it listens to the clock. The clock's two meanings of stopped (paused, and a rate of 0) could then become one. To settle: whether reset also starts playback when paused (suggested: no, it only sets the rate), and what 2 becomes, if anything. Do before or with F7, which maps these keys.

## Research

**U2. Project structure.** needs the owner. Spreadsheets are usually instances of a project template, and coding passes (columns and their codes) are reused across experiments. Datavyu handled this with Ruby scripts that created a spreadsheet, or migrated one when the template changed; that worked imperfectly and is not to be recreated. Explore how projects, templates, spreadsheets and portable coding passes relate, likely as a folder hierarchy, and where large videos live (often copied locally from a network share for speed). The project file holds everything until this is settled.

**U4. Media identity and cached details.** Whether a clip's media is the file it was coded against, and what to trust when opening it goes wrong. Today a clip's cached details (length, size, frame rate) are replaced by whatever its file reports when it opens (`MediaPlayers.open`), so a file that opens but reports wrongly, or a different file at the same path, overwrites details that were right. Revisit with fingerprinting the media (owner: later), so a clip knows its file. A future idea (owner): filler cells with no media, as placeholders in a media column.

## Dependencies

**D1. Upgrade or drop old libraries.**

| Library | Now → latest stable | Used by | Action |
|---|---|---|---|
| MigLayout | 3.7.4 → `miglayout-swing` 11.x | the controls drawer (`VideoController`) and the Column List (`VariableListV`) | ready: upgrade |

## Refactors

The app is to be rewritten in C++ (AGENTS.md › North Star), so refactor only where a cut or a behaviour change needs it.

**R3. Replace static singletons with explicit wiring.** 255 call sites reach the app through `Datavyu.getView()`, `getApplication()`, `getVideoController()` and `getProjectController()`, and `getProjectController()` returns different objects depending on whether a tab exists. Introduce a per-document object (data store, project, undo manager, playback) and pass it explicitly. Global state is what keeps the views untestable: `ProjectController.loadProject`, which opens a project file's videos and keeps missing ones for saving, is checked only by hand because its dialogs reach the app through `Datavyu.getApplication()`.

**R4. Per-editor key handling.** `EditorTracker` and the value editors handle keys per editor, with 21 uses of the deprecated modifier API (`getModifiers`, `*_MASK`, `getMenuShortcutKeyMask`). Move what can go into `Shortcuts`; do with F7.

**R7. Threading.** Make the boundary between the clock thread, the JavaFX thread and the EDT explicit, as `JavaFxPlayback` already does for the player. `ClockTimer` notifies its listeners on its own pool threads, most of them while holding its lock, so a listener that waits on another thread can deadlock it; the playhead (`PlayheadCarriage.show`) and `MediaPlayers` hand their work to the EDT. Cells still restyle for the needle on the clock thread (`SpreadsheetCell`'s clock listener, through `CellLook`), as they did before U5. SpotBugs's thread-safety findings are at zero and gated.

**R9. Autosave and crash recovery.** ready. `AutoSaveController` saves a `~<name>_*.watavyu.json` project file to the temporary folder every minute; at launch, `DatavyuView.checkForAutosavedFile` offers to recover any left in the temporary folder (Datavyu's `~*.csv` too) and opens one as an unsaved project; that flow is checked only by reading, as its dialogs need clicks. The temporary folder is shared with other apps and cleared by macOS. Settle this together with B11.
