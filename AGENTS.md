# AGENTS.md

Guidance for agents working on Watavyu. Read this first, then [TODO.md](TODO.md) for open work and [BUILDING.md](BUILDING.md) for how to build, test, measure and play video.

## North Star

Watavyu is a personal macOS fork of Datavyu, a tool for coding behaviour in video: researchers watch recordings and record timed events as cells in a spreadsheet. It has one user and stakeholder, its owner.

The aim is a smaller app whose every feature was chosen on purpose, with a modern UI and project files a person can read:

- **A C++ rewrite follows.** The app will be rewritten in C++ for interoperability with computer vision and ML/AI libraries. Until then, effort goes into cutting features and fleshing out the behaviour of what stays, pinned by tests that become the rewrite's specification, not into refactoring code that will be replaced. Refactor only where a cut or a behaviour change needs it.
- **Cut, then keep on purpose.** Unused features go. What stays is kept deliberately and pinned by tests, because each kept behaviour is something the app is then committed to.
- **Transparent data.** A project is one readable JSON file holding the spreadsheet and the project. Videos are data columns like any other, with explicit onsets (F1).
- **Fewer windows.** The main window, video controller and video windows were hard to manage. The video controller window is gone: media tracks are media columns in the spreadsheet, and its buttons and playhead sit beside the columns (F4, F6). Next, the numpad shortcuts become a smaller, configurable key mapping, so coding works on laptops without a numpad (F7).
- **Comfortable for hours, then fast.** Researchers code for hours at a stretch, so the UI's first priority is little eye strain; performance comes second (U5).
- **One platform.** macOS on Apple Silicon, Java 25, Swing with OpenJFX video through a patched media library. The app makes no network connections of its own; the rule keeps traffic off the public internet, and project and video files on mounted or local-network volumes are fine.

## How to Work Here

- **The owner reviews goals, architecture and design, not diffs.** Take code-level choices (patterns, libraries, structure) yourself and record structural ones under [Decisions](#decisions). Rebuilding something from scratch is an acceptable price when it has gone badly; say so plainly.
- **Stop and ask about product decisions**: what the app does, its rules and feel, and anything under [Undecided](#undecided).
- **Tests are the specification.** A behaviour without a test that pins it is unfinished. Do not write characterization tests for behaviour that may be cut.
- **Never weaken a check to get green.** No disabled tests, relaxed rules or silenced warnings. Checks only ratchet up: when an Error Prone check, PMD rule or SpotBugs pattern reaches zero findings, make it fail the build.
- **Keep the docs true.** README.md, BUILDING.md, TODO.md and this file are the owner's window into the code.
- **Commit or push only when asked.** A commit that finishes a TODO item names its ID and title, such as "Fix B1: quitting can discard unsaved work".
- **Pull requests.** Related items go in one pull request, one commit per item, merged by rebase so each item's commit lands on `main`. Items that should be reviewed or merged apart go in a stack: one branch and squash-merged PR per item, each based on the one below, managed with the `gh stack` extension (`github/gh-stack`).
  - Build it with `gh stack init <branch>`, then `gh stack add <branch>` for each later item, and open the PRs with `gh stack submit --auto --open`. After any PR in it merges, `gh stack sync --prune` rebases the rest onto `main` and pushes them; no manual `rebase --onto`.
  - The repository accepts at most 10 branch and tag updates per push, and `gh stack` pushes every open branch of a stack in one push, so a stack holds at most 10 open PRs.
  - Agents pass the non-interactive forms (`view --json`, `submit --auto`, `merge <pr> --yes`, a branch name for `init` and `add`) and never run `modify` or `switch`, which only open menus. `sync` exits 0 without doing anything when the local and GitHub stacks have diverged; check its output for "Sync aborted".
- **Playback tests need an awake display**: run `caffeinate -u -d ./gradlew integrationTest`; JavaFX receives no video frames while the display sleeps, and `-u` wakes a display that is already asleep.

## Decisions

Newest first. (owner) marks product decisions; the rest are engineering choices that can be revisited with reason.

- **2026-10-29 (owner): no video controller window** (F6). Its window became two parts of each spreadsheet tab, left of the columns:
  - A controls drawer holding its buttons and fields as they were, shown or hidden from the Playback menu or the keypad's `*` (a setting, on by default). Which buttons stay is to be found by trial and error.
  - A playhead: a vertical strip with a needle and the current time and rate, and no ruler or scale. The needle follows the spreadsheet's rows: it sits where its time falls among the cells in temporal layout, scrolls with them, and is dragged to scrub. A visible media column spans its media, so with one the strip covers the whole recording; with all media columns hidden it follows the annotations. Its line can run across the columns, faint enough to read cells through (a setting, on by default).
  - The ruler, zoom and the old timestamp display went with the window. Add Data went too: Spreadsheet > Add Media does the same.
- **2026-10-20 (owner): the video controller has no tracks.** A clip's volume is set in its media window, for the clip in play; a media column is locked from its header's right-click menu, and locking is an edit in the undo history, so undoing past it unlocks the column. Lock all, the highlighting buttons and the Frame Rate box are gone (Highlight and Focus stays in the menu; plain highlighting without focus went with its button). Playback stops at the end of the last media clip, a setting (`stopAtEndOfMedia`) that is on by default; off, it runs on to the end of the timeline, which is at least a minute, as in Datavyu. Closing a media window hides its column, which silences it.
- **2026-10-17 (owner): media columns in the spreadsheet** (F4). Rules for clips are under the JSON project format below. Done to unblock other work, and expected to be refined: do not build in assumptions that would make the details below hard to change.
  - A media column's clips are cells laid out like annotation cells, showing the file's name, its frame rate when it has video, and whether it is missing (owner). The spreadsheet opens in temporal layout by default.
  - The spreadsheet is a view on the media system. Adding a clip may later mean processing a large media file, so a clip cell shows missing or not-yet-available media gracefully.
  - Clips are added by dropping a file on the column and by a menu command: two entry points to one action, the simpler built first.
  - Clips move by editing their onset. Dragging them in the spreadsheet is low priority, a possibility for later (F2).
  - One floating media window per media column, for now. Media columns and their clips are separate concerns (2026-10-29): Spreadsheet > New Media Column manages columns, each a window once a clip in it opens, and a new spreadsheet starts with an empty one, a setting (`newSpreadsheetMediaColumns`) that is a first step toward a new-spreadsheet template. Adding clips is unchanged until a better way is found: Spreadsheet > Add Media and dropping files on the spreadsheet put each file in the one selected media column as a clip at the current time, or otherwise in a new media column at time zero.
  - Column names `media_1`, `media_2`, … are provisional (2026-10-29). Alternatives to explore: a prefix such as `_media_1`, or namespaces such as `media::1` and `annotation::bookmarks`.
  - A clip is one media container: video without audio and audio without video are the same to a media column. Audio alone shows a black window; splitting audio and video playback is a later discussion. Gaps between clips are black.
- **2026-10-29 (owner): playback rates.** Shuttle steps through 0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 3 and 4× instead of Datavyu's powers of 2 from 1/32 to 32×; beyond 4×, people jump or scrub instead. (2× alone, decided 2026-10-14, felt a low ceiling.)
- **2026-10-14 (owner): annotation columns have no subtypes.** The app has one kind of column, a list of codes. Datavyu's text and nominal columns, from `.opf`, `.csv` and JSON import, open as annotation columns with one code named `var`, as Datavyu named their value; JSON export writes every column as Datavyu's matrix of nominal codes.
- **2026-10-14 (owner): work is tracked in TODO.md and AGENTS.md**, not GitHub issues or separate documents. Small bugs found while iterating are logged rather than fixed when the code may be redesigned or dropped.
- **2026-10-14 (owner): the JSON project format** (F1). One `<name>.watavyu.json` file holds the whole project for now; folders, templates and reusable coding passes come later (U2). Backwards compatibility with `.opf` does not constrain the design: existing users keep Datavyu, and migration is a later question. For now `.opf` and `.csv` files still open, and saving always writes a project file (Save on one of them goes through Save As).
  - Shape: `{"format": "watavyu", "version": 1, "columns": [...]}`. Columns are in spreadsheet order; each has `name`, `type`, `settings` (view state as key-value pairs, every key always written) and `cells` in time order. Times are `HH:MM:SS:mmm`.
  - `annotation` columns replace text, nominal and matrix columns: an ordered `codes` list, and each cell has `onset`, `offset` and `values` aligned with `codes`, an empty value being `""`. A column with no codes holds timing only, since the app lets the last code be deleted. Column settings: `hidden`.
  - A `media` column is one media window (audio is to be supported too), and each cell is a clip in it: `onset`, `offset`, `settings` (`volume`) and `source` (`file`, `duration`, `frameRate`, `width`, `height`). `source` caches what the media file says, and the file wins when they differ: a detail the file has never given (it has not opened, or it has no video) is left out, and a clip has an `offset` exactly when its duration is known. Saving never waits for media to open (owner). Paths are relative to the project file, or absolute outside its folder. Media added in the app gets a column named `media_1`, `media_2` and so on. Column settings: `hidden`, which hides the column and its window, and `locked`, which stops clips moving: a missed accidental move would misalign every cell annotated after it.
  - Clips slide but are not trimmed: a clip's offset is its onset plus its source duration, written for readers and never set by the user. Gaps between clips are black; where clips overlap, the one that starts later wins.
  - No time is negative (owner, 2026-10-29): no use is known for a cell or clip before time zero, and this may break older files. Until B13 lands, a clip may still start before zero, as dragging a track in the old video controller allowed.
  - Every offset is a real time: new cells are point cells (offset = onset), with no "unset" offset. An offset before its onset is allowed, saved as it is, and flagged in the spreadsheet with a red border as in Datavyu (which exempts an offset of `00:00:00:000`, Datavyu's "unset").
  - Not carried over: the project name, original folder and database file name, plugin UUID, namespace and class, per-object version numbers, and the frame-rate override. Video window size and position wait for U1.
- **2026-10-14 (owner): removed features**: Ruby and R scripting and the script console; the file and favorites drawers; the `.shapa` and `.odb` formats; the Undo History window (an edit list in the controls drawer is planned instead, F12); the playback region of interest; track bookmarks; snapping while dragging a track. Old `.opf` files with bookmarks still open; the bookmarks are dropped.
- **2026-10-14 (owner): kept features**: JSON import/export; highlight-and-focus mode, on by default and not announced in the window title; quick-key mode, for now.
- **2026-10-14 (owner): snapping comes back** after F1 (F2).
- **2026-10-14 (owner): no characterization tests.** Tests are written for behaviour that is deliberately kept or rebuilt.
- **2026-10-29: the playhead reads the layout** (BUILDING.md › Application Structure). The temporal layout publishes where times fall down the sheet (`TimeAxis`) after each pass, and the playhead strip sits beside the scroll pane, following its scrolling, rather than inside it as a row header whose height would have to track the sheet's. Each tab keeps its own `VideoController`, now a plain object rather than a window.
- **2026-10-17: media columns are spreadsheet columns** (BUILDING.md › Application Structure). The data model holds the clip rules; `MediaPlayers` keeps the players in step with the spreadsheet, so undo, opening a project and the video controller all go through one path.
- **2026-10-14: project files are read strictly and written in a fixed layout** (`org.datavyu.projectfile`, BUILDING.md › Project Files). The reader refuses unknown keys and names the place of any error; the writer checks the same rules first. The published JSON Schema is tested to agree with the reader. Times are `long` milliseconds in code, formatted and parsed only by `Timestamp`.
- **2026-10-14: no Swing Application Framework.** A plain `main`, Swing `Action`s, `Resources` over `ResourceBundle`, and `WindowPlacement` replaced it. Settings are JSON (`Settings`, see BUILDING.md › Settings); spreadsheet colours and fonts are constants in `SpreadsheetTheme`, not settings.
- **2026-10-14: one shortcut table.** `Shortcuts` drives menu accelerators, the keypad and the Keyboard Shortcuts dialog; new shortcuts go there.
- **2026-10-14: OpenJFX is the only video player.** The plugin system is gone. Project files do not name a player; in older `.opf` files, a track recorded with another Datavyu video player opens with OpenJFX after a notice.
- **Error Prone is the linter; analysis reports are not gates** (BUILDING.md › Lint). PMD, SpotBugs, JaCoCo and the scripts in `tools/analysis` measure TODO.md. A SpotBugs category at zero joins `spotbugsGate` and fails the build; thread safety is the first.

## Undecided

Ask the owner before acting on these.

- Project structure beyond one file: folders, templates, and coding passes reused across experiments (U2).
- Migration from Datavyu: whether `.opf` files keep opening, or a one-off converter replaces that. Dropping it would also drop SnakeYAML (D1).
- The rest of the UI direction (U1) beyond the controls drawer and playhead (F6), including which controls stay, and the visual design for long sessions (U5).
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
