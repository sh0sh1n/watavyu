# AGENTS.md

Guidance for agents working on Watavyu. Read this first, then [TODO.md](TODO.md) for open work and [BUILDING.md](BUILDING.md) for how to build, test, measure and play video.

## North Star

Watavyu is a personal macOS fork of Datavyu, a tool for coding behaviour in video: researchers watch recordings and record timed events as cells in a spreadsheet. It has one user and stakeholder, its owner.

The aim is a smaller app whose every feature was chosen on purpose, with a modern UI and project files a person can read:

- **Cut, then keep on purpose.** Unused features go. What stays is kept deliberately and pinned by tests, because each kept behaviour is something the app is then committed to.
- **Transparent data.** A project is one readable JSON file holding the spreadsheet and the project. Videos are data columns like any other, with explicit onsets (F1).
- **Fewer windows.** The main window, video controller and video windows are hard to manage; the UI direction (U1) should reduce them.
- **One platform.** macOS on Apple Silicon, Java 25, Swing with OpenJFX video through a patched media library. The app makes no network connections.

## How to Work Here

- **The owner reviews goals, architecture and design, not diffs.** Take code-level choices (patterns, libraries, structure) yourself and record structural ones under [Decisions](#decisions). Rebuilding something from scratch is an acceptable price when it has gone badly; say so plainly.
- **Stop and ask about product decisions**: what the app does, its rules and feel, and anything under [Undecided](#undecided).
- **Tests are the specification.** A behaviour without a test that pins it is unfinished. Do not write characterization tests for behaviour that may be cut.
- **Never weaken a check to get green.** No disabled tests, relaxed rules or silenced warnings. Checks only ratchet up: when an Error Prone check, PMD rule or SpotBugs pattern reaches zero findings, make it fail the build.
- **Keep the docs true.** README.md, BUILDING.md, TODO.md and this file are the owner's window into the code.
- **Commit or push only when asked.** A commit that finishes a TODO item names its ID and title, such as "Fix B1: quitting can discard unsaved work".
- **Playback tests need an awake display**: run `caffeinate -d ./gradlew integrationTest`; JavaFX receives no video frames while the display sleeps.

## Decisions

Newest first. (owner) marks product decisions; the rest are engineering choices that can be revisited with reason.

- **2026-10-14 (owner): work is tracked in TODO.md and AGENTS.md**, not GitHub issues or separate documents. Small bugs found while iterating are logged rather than fixed when the code may be redesigned or dropped.
- **2026-10-14 (owner): JSON replaces the `.opf` project file** (F1). Security and data-loss fixes that touch persistence wait for it.
- **2026-10-14 (owner): removed features**: Ruby and R scripting and the script console; the file and favorites drawers; the `.shapa` and `.odb` formats; the Undo History window; the playback region of interest; track bookmarks; snapping while dragging a track. Old `.opf` files with bookmarks still open; the bookmarks are dropped.
- **2026-10-14 (owner): kept features**: JSON import/export; highlight-and-focus mode, on by default and not announced in the window title; quick-key mode, for now.
- **2026-10-14 (owner): snapping comes back** after F1 (F2).
- **2026-10-14 (owner): no characterization tests.** Tests are written for behaviour that is deliberately kept or rebuilt.
- **2026-10-14: no Swing Application Framework.** A plain `main`, Swing `Action`s, `Resources` over `ResourceBundle`, and `WindowPlacement` replaced it. Settings are JSON (`Settings`, see BUILDING.md › Settings); spreadsheet colours and fonts are constants in `SpreadsheetTheme`, not settings.
- **2026-10-14: one shortcut table.** `Shortcuts` drives menu accelerators, the keypad and the Keyboard Shortcuts dialog; new shortcuts go there.
- **2026-10-14: OpenJFX is the only video player.** The plugin system is gone. Project files still record the OpenJFX player's UUID, namespace and viewer class; a track recorded with another Datavyu video player opens with OpenJFX after a notice.
- **Error Prone is the linter; analysis reports are not gates** (BUILDING.md › Lint). PMD, SpotBugs, JaCoCo and the scripts in `tools/analysis` measure TODO.md.

## Undecided

Ask the owner before acting on these.

- The JSON project format's design (F1).
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
