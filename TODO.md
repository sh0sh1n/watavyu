# TODO

Known bugs and planned work, in place of GitHub issues. Finished items are deleted, not ticked; the commit that finishes one names its ID. How to keep this file: [AGENTS.md](AGENTS.md#keeping-todomd-and-agentsmd).

Each item says what blocks it and how it is known:

- **ready**: can be done now. **after-ui**: waits for the UI direction (U1). **future**: kept for later consideration, not planned.
- *verified*: reproduced or measured. *from reading*: seen in the code, not exercised in the running app.

Items are in priority order within each section.

## Measures

Updated 2026-10-21. Re-measure with the commands in [BUILDING.md › Analysis Reports](BUILDING.md#analysis-reports).

| Measure | Before cleanup (2026-10-14) | Now |
|---|---|---|
| Java source (main) | 242 files, 42.5k lines | 173 files, 29.2k lines |
| Tests / line coverage | 113 / 9.9% | 362 / 25.9%; still 0% in most views, the timeline and undo |
| Runtime dependencies / with known vulnerabilities | 28 / 8 | 12 / 0 |
| Error Prone + javac lint warnings (`-PallWarnings`) | 1,227 | 695 |
| PMD / SpotBugs findings; PMD god classes | 626 / 113; 16 | 445 / 71; 16 |
| Never-invoked methods (`dead-code.py`) | 109+ | 13: test-only accessors, and a Swing override the script cannot see |
| Static global access (`Datavyu.getView()` and friends) | about 330 call sites | 272 |

## Bugs

**B2. The app changes a global macOS setting.** ready (owner: scope it to the app), *from reading*. At startup `Datavyu` runs `defaults write -g ApplePressAndHoldEnabled -bool false` for every application on the Mac, and restores it only on a clean shutdown; a crash or force-quit leaves it changed. (It used to run when the class loaded, so every test run turned the setting off and never restored it.) The intent is key repeat instead of the accent popup while holding keys. Scope it to the app: in-app key repeat, or a per-app defaults domain once there is an app bundle; then delete the global write.

**B11. Recovering autosaves needs a dialog, and offers those of a Watavyu still running.** ready, *verified*. At launch, `DatavyuView.checkForAutosavedFile` asks about every `~*.watavyu.json` in the temporary folder: the dialog itself is a chore (owner), and it includes the autosave a running Watavyu is writing, which answering No deletes. Owner's direction: open unsaved autosaves without asking, and only those no running Watavyu owns (a lock file, or the owning process recorded in the autosave's name). With that, add a safeguard against saving over a newer file: if the project file changed on disk since it was opened or last saved (by another Watavyu, a recovered copy or another tool), say so before overwriting it. Settle with R9 and U3.

**B14. Adding media cannot be undone.** ready, *from reading*. Spreadsheet > Add Media and dropping files post no undoable edit (`MediaPlayers.addMediaFiles`), while deleting the clip or column afterwards can be undone. Undo should take an added clip or column out again, closing its player.

## Features

**F6. Remove the video controller window.** ready. The next big UI change (AGENTS.md › North Star). Media tracks are already in the spreadsheet as media columns (F4), and the controller's tracks are gone; what is left is its transport buttons, time display, jump field, Add Data button, and a ruler with a needle (`VideoController`, `MixerController`). They find a place in the main window or go. One idea (owner): a timeline with a draggable needle down the left edge of the spreadsheet. Media clips starting before time zero (a hack today) are discussed again here.

**F7. Configurable key mapping for playback.** ready. The numpad shortcuts (`Shortcuts`, the keypad section) are condensed into a smaller set of playback and coding keys that the user can map, so coding works on laptops without a numpad. Settings would hold the mapping.

**F12. Edit history in the controls drawer.** ready, medium priority (owner). Below the controls, a list of the edits that can be undone and redone, newest first, from the spreadsheet's undo history (`SpreadsheetUndoManager`; each edit has a presentation name, and `SpreadSheetEdit.toString` already formats one with its time). Hiding the drawer hides both, so the space can be reclaimed with one toggle. Datavyu's separate Undo History window was removed in the cleanup; this replaces it. B14 (adding media is not undoable) shows up here.

**F10. Offsets below onsets, and point cells without offsets.** ready, medium to low priority (owner). A cell shows its onset and offset side by side, which sets a column's minimum width. Put the offset at the bottom of the cell, so columns can shrink thinner, and add a setting to leave the offset out entirely for point cells (offset = onset), which new cells are by default (`SpreadsheetCell`, `TimeStampDataValueEditor`, the column width limits in `SpreadsheetColumn`).

**F11. Shorter cell times.** ready, low priority (owner). Where a cell's onset and offset both start with zero fields, leave those fields out of both: `00:00:04:028` and `00:08:04:028` show as `00:04:028` and `08:04:028`. The playhead keeps every field. Editing a shortened time needs a rule for what is typed.

**F2. Dragging clips in the spreadsheet, with snapping.** future (owner: low priority; may be dropped). Clips move only by typing their onset. Dragging a clip cell would move it, snapping for easy alignment as Datavyu's track dragging did: `TracksEditorController.snapOffset` on `main` before the cleanup snapped a track's start, end and bookmarks to time zero, the needle, the region of interest and the other tracks' starts, ends and bookmarks, within 5 seconds. A locked column's clips stay put.

## Research

**U5. A UI for long sessions.** needs the owner; next after the open PRs. Researchers code for hours, so the first aim is little eye strain, then performance (AGENTS.md › North Star). Research what reduces strain over long sessions (contrast, colour temperature, dark and light themes, font and size, motion such as the moving playhead) and propose a design. Owner's taste: minimalist, like a black and green terminal, with simple lines and shapes instead of icons. That would empty `src/main/resources/icons`: the keypad buttons (`DataController/eng`, where `back*.png` is already unused), the column lock and volume marks, the Code Editor's arrows and change mark, and the About splash; the app icon `datavyu.icns` is a separate question. Colours and fonts are constants in `SpreadsheetTheme`; the rest of the UI uses the system look and feel.

**U1. UI direction.** needs the owner. Decided so far: remove the video controller window (F6) and replace the numpad shortcuts with a configurable key mapping (F7). The rest of the UI, and what it means for MigLayout (D1), is open.

**U2. Project structure.** needs the owner. Spreadsheets are usually instances of a project template, and coding passes (columns and their codes) are reused across experiments. Datavyu handled this with Ruby scripts that created a spreadsheet, or migrated one when the template changed; that worked imperfectly and is not to be recreated. Explore how projects, templates, spreadsheets and portable coding passes relate, likely as a folder hierarchy, and where large videos live (often copied locally from a network share for speed). The project file holds everything until this is settled.

**U3. Write-ahead log and event sourcing.** Consider recording every edit as an event in an append-only log beside the project file, for data integrity and disaster recovery. Volume is small (tens of thousands of events, perhaps millions). It would likely replace R9's autosave and could feed undo.

**U4. Media identity and cached details.** Whether a clip's media is the file it was coded against, and what to trust when opening it goes wrong. Today a clip's cached details (length, size, frame rate) are replaced by whatever its file reports when it opens (`MediaPlayers.open`), so a file that opens but reports wrongly, or a different file at the same path, overwrites details that were right. Revisit with fingerprinting the media (owner: later), so a clip knows its file. A future idea (owner): filler cells with no media, as placeholders in a media column.

## Dependencies

**D1. Upgrade or drop old libraries.**

| Library | Now → latest stable | Used by | Action |
|---|---|---|---|
| SnakeYAML | 2.7 (latest) | opening `.opf` files, citation | drop with `.opf` opening (a migration question, see AGENTS.md › Undecided); the citation can parse its few keys without it |
| MigLayout | 3.7.4 → `miglayout-swing` 11.x | 6 files | after-ui |

## Refactors

The app is to be rewritten in C++ (AGENTS.md › North Star), so refactor only where a cut or a behaviour change needs it.

**R3. Replace static singletons with explicit wiring.** 272 call sites reach the app through `Datavyu.getView()`, `getApplication()`, `getVideoController()` and `getProjectController()`, and `getProjectController()` returns different objects depending on whether a tab exists. Introduce a per-document object (data store, project, undo manager, playback) and pass it explicitly. Global state is what keeps the views untestable: `ProjectController.loadProject`, which opens a project file's videos and keeps missing ones for saving, is checked only by hand because its dialogs reach the app through `Datavyu.getApplication()`.

**R4. Per-editor key handling.** `EditorTracker` and the value editors handle keys per editor, with 21 uses of the deprecated modifier API (`getModifiers`, `*_MASK`, `getMenuShortcutKeyMask`). Move what can go into `Shortcuts`; do with F7.

**R7. Threading.** Make the boundary between the clock thread, the JavaFX thread and the EDT explicit, as `JavaFxPlayback` already does for the player. `ClockTimer` notifies its listeners on its own pool threads, most of them while holding its lock, so a listener that waits on another thread can deadlock it. SpotBugs reports 14 thread-safety findings, all in `MixerController`, `TimescaleController` and `TrackController` fields that by reading are only touched on the EDT (listeners and `runInEDT`); making that confinement explicit should clear them.

**R9. Autosave and crash recovery.** ready. `AutoSaveController` saves a `~<name>_*.watavyu.json` project file to the temporary folder every minute; at launch, `DatavyuView.checkForAutosavedFile` offers to recover any left in the temporary folder (Datavyu's `~*.opf`/`~*.csv` too) and opens one as an unsaved project; that flow is checked only by reading, as its dialogs need clicks. The temporary folder is shared with other apps and cleared by macOS. Settle this together with U3.
