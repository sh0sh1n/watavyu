# AGENTS.md

Guidance for agents working on Watavyu. Read this first, then [TODO.md](TODO.md) for open work and [BUILDING.md](BUILDING.md) for how to build, test, measure and play video.

## North Star

Watavyu is a personal macOS fork of Datavyu, a tool for coding behaviour in video: researchers watch recordings and record timed events as cells in a spreadsheet. It has one user and stakeholder, its owner.

The aim is a smaller app whose every feature was chosen on purpose, with a modern UI and project files a person can read:

- **Cut, then keep on purpose.** Unused features go. What stays is kept deliberately and pinned by tests, because each kept behaviour is something the app is then committed to.
- **Transparent data.** A project is one readable JSON file holding the spreadsheet and the project. Videos are data columns like any other, with explicit onsets (F1).
- **Fewer windows.** The main window, video controller and video windows are hard to manage; the UI direction (U1) should reduce them.
- **One platform.** macOS on Apple Silicon, Java 25, Swing with OpenJFX video through a patched media library. The app makes no network connections of its own; the rule keeps traffic off the public internet, and project and video files on mounted or local-network volumes are fine.

## How to Work Here

- **The owner reviews goals, architecture and design, not diffs.** Take code-level choices (patterns, libraries, structure) yourself and record structural ones under [Decisions](#decisions). Rebuilding something from scratch is an acceptable price when it has gone badly; say so plainly.
- **Stop and ask about product decisions**: what the app does, its rules and feel, and anything under [Undecided](#undecided).
- **Tests are the specification.** A behaviour without a test that pins it is unfinished. Do not write characterization tests for behaviour that may be cut.
- **Never weaken a check to get green.** No disabled tests, relaxed rules or silenced warnings. Checks only ratchet up: when an Error Prone check, PMD rule or SpotBugs pattern reaches zero findings, make it fail the build.
- **Keep the docs true.** README.md, BUILDING.md, TODO.md and this file are the owner's window into the code.
- **Commit or push only when asked.** A commit that finishes a TODO item names its ID and title, such as "Fix B1: quitting can discard unsaved work".
- **Playback tests need an awake display**: run `caffeinate -u -d ./gradlew integrationTest`; JavaFX receives no video frames while the display sleeps, and `-u` wakes a display that is already asleep.

## Decisions

Newest first. (owner) marks product decisions; the rest are engineering choices that can be revisited with reason.

- **2026-10-14 (owner): work is tracked in TODO.md and AGENTS.md**, not GitHub issues or separate documents. Small bugs found while iterating are logged rather than fixed when the code may be redesigned or dropped.
- **2026-10-14 (owner): the JSON project format** (F1). One `<name>.watavyu.json` file holds the whole project for now; folders, templates and reusable coding passes come later (U2). Backwards compatibility with `.opf` does not constrain the design: existing users keep Datavyu, and migration is a later question. For now `.opf` and `.csv` files still open, and saving always writes a project file (Save on one of them goes through Save As).
  - Shape: `{"format": "watavyu", "version": 1, "columns": [...]}`. Columns are in spreadsheet order; each has `name`, `type`, `settings` (view state as key-value pairs, every key always written) and `cells` in time order. Times are `HH:MM:SS:mmm`.
  - `annotation` columns replace text, nominal and matrix columns (F5): an ordered `codes` list, and each cell has `onset`, `offset` and `values` aligned with `codes`, an empty value being `""`. A column with no codes holds timing only, since the app lets the last code be deleted. Column settings: `hidden`.
  - A `media` column is one media window (audio is to be supported too), and each cell is a clip in it: `onset`, `offset`, `settings` (`volume`) and `source` (`file`, `duration`, `frameRate`, `width`, `height`). `source` caches what the media file says; the file wins when they differ. Paths are relative to the project file, or absolute outside its folder. Media added in the app gets a column named `media_1`, `media_2` and so on. Column settings: `hidden`, and `locked`, which stops clips being dragged: a missed accidental move would misalign every cell annotated after it.
  - Clips slide but are not trimmed: a clip's offset is its onset plus its source duration, written for readers and never set by the user. Gaps between clips show blank frames; where clips overlap, the later one wins. Until F4, a media column holds one clip.
  - **Hack:** a clip may start before time zero, because dragging a track in the video controller window already allows it (for a recording that began before the annotated session). The separate controller window is to be phased out (U1); discuss negative times again then.
  - Every offset is a real time: new cells are point cells (offset = onset), with no "unset" offset. An offset before its onset is allowed, saved as it is, and flagged in the spreadsheet with a red border as in Datavyu (which exempts an offset of `00:00:00:000`, Datavyu's "unset").
  - Not carried over: the project name, original folder and database file name, plugin UUID, namespace and class, per-object version numbers, and the frame-rate override. Video window size and position wait for U1.
- **2026-10-14 (owner): removed features**: Ruby and R scripting and the script console; the file and favorites drawers; the `.shapa` and `.odb` formats; the Undo History window; the playback region of interest; track bookmarks; snapping while dragging a track. Old `.opf` files with bookmarks still open; the bookmarks are dropped.
- **2026-10-14 (owner): kept features**: JSON import/export; highlight-and-focus mode, on by default and not announced in the window title; quick-key mode, for now.
- **2026-10-14 (owner): snapping comes back** after F1 (F2).
- **2026-10-14 (owner): no characterization tests.** Tests are written for behaviour that is deliberately kept or rebuilt.
- **2026-10-14: project files are read strictly and written in a fixed layout** (`org.datavyu.projectfile`, BUILDING.md › Project Files). The reader refuses unknown keys and names the place of any error; the writer checks the same rules first. The published JSON Schema is tested to agree with the reader. Times are `long` milliseconds in code, formatted and parsed only by `Timestamp`.
- **2026-10-14: no Swing Application Framework.** A plain `main`, Swing `Action`s, `Resources` over `ResourceBundle`, and `WindowPlacement` replaced it. Settings are JSON (`Settings`, see BUILDING.md › Settings); spreadsheet colours and fonts are constants in `SpreadsheetTheme`, not settings.
- **2026-10-14: one shortcut table.** `Shortcuts` drives menu accelerators, the keypad and the Keyboard Shortcuts dialog; new shortcuts go there.
- **2026-10-14: OpenJFX is the only video player.** The plugin system is gone. Project files do not name a player; in older `.opf` files, a track recorded with another Datavyu video player opens with OpenJFX after a notice.
- **Error Prone is the linter; analysis reports are not gates** (BUILDING.md › Lint). PMD, SpotBugs, JaCoCo and the scripts in `tools/analysis` measure TODO.md.

## Undecided

Ask the owner before acting on these.

- Project structure beyond one file: folders, templates, and coding passes reused across experiments (U2).
- Migration from Datavyu: whether `.opf` files keep opening, or a one-off converter replaces that. Dropping it would also drop SnakeYAML (D1).
- The UI direction (U1), including docking the video controller in the main window.
- Whether quick-key mode stays.
- Whether a scripting shell returns, and in what form. It would start from a clean slate.

## Keeping TODO.md and AGENTS.md

- Update them in the same commit as the work that changes them.
- **TODO.md** holds what is left: bugs, security issues, features, research, dependencies and refactors.
  - Delete an item when it is finished; git history keeps the record.
  - Log a bug or deferred task as a new item. Give it the next number after the highest in its section, a bold one-line title, what blocks it (ready, after-format, after-ui) and how it is known (*verified* or *from reading*), and say where in the code it is.
  - Refresh the Measures table when the numbers move.
- **AGENTS.md** holds direction, decisions and how to work. Rewrite a decision when it changes instead of appending a new one, and delete it once it no longer guides anything. Move an Undecided question to Decisions when the owner answers it.
- **BUILDING.md** holds how to build, test, run and measure. Do not repeat it here.
- Trim aggressively. Remove finished work, stale numbers, superseded context and anything a reader would no longer act on. Both files should stay short enough to read at the start of every session.
