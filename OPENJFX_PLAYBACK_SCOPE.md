# OpenJFX Playback Integration: Agent Scope

## Objective

Implement the first usable macOS video backend for the personal Watavyu fork using OpenJFX Media, compatible with the existing Swing application and Gradle build. The initial target is local 1920x1080 H.264/AAC footage from OBS, with responsive random seeking and forward playback as the top priorities, frame stepping next, and reverse playback optional or omitted.

Do not create an installer, sign the app, migrate/clean up Maven, or redesign the annotation UI in this task.

## User Workflow and Constraints

- The application is a local desktop annotation tool. Video stays on local storage; do not upload or proxy it through a service.
- Typical workflow: seek to a cell onset, play through a short interval, stop, and repeat.
- Random seeking and forward playback are the key acceptance criteria. Forward/backward frame stepping is next. Reverse playback can be deferred.
- User accepts a documented optional transcode workflow and broadly supports precomputation/caching if it demonstrably improves future use.
- First supported machine: macOS 27, Apple Silicon, Temurin JDK 25, Gradle Wrapper 9.8.1.
- Keep the change focused on local playback. No Windows/Linux compatibility requirement for this first backend.

## Existing Integration Surface

- Player abstraction: `StreamViewer` in `src/main/java/org/datavyu/plugins/StreamViewer.java`.
- Shared Swing dialog/control behavior: `StreamViewerDialog` in `src/main/java/org/datavyu/plugins/StreamViewerDialog.java`.
- Plugin selection/registration: `Plugin`, `PluginManager`, and `VideoController` under `src/main/java/org/datavyu/plugins/` and `src/main/java/org/datavyu/views/`.
- The last working source snapshot has AVFoundation registered on macOS, but its Java bridge is supplied by the historical `org.datavyu:ffmpeg-plugin:0.24` artifact and `org.datavyu:libNativeOSXCanvas:0.92`. The JNI bridge is x86_64-only and unusable in the native arm64 JDK.
- `build.gradle.kts` is the active local build. Maven is intentionally retained but not in scope.
- The Swing app now uses the system look and feel; a macOS Add Data chooser regression was fixed by using `JFileChooser.setAccessory()`.

## Implementation Requirements

1. Trace the current `StreamViewer` lifecycle and `ClockTimer` callback/thread model before choosing the JavaFX/Swing boundary. Keep Swing component updates on the Swing EDT and JavaFX scene/player access on the JavaFX Application Thread. Do not block either UI thread waiting for media readiness or a seek.
2. Add a macOS OpenJFX Media-backed `StreamViewer` implementation. Embed its `MediaView` in Swing through `JFXPanel`, with deterministic initialization, teardown, listener cleanup, and error propagation. Keep the project UI and annotation model Swing-based.
3. Register this backend as the sole default macOS video player. Remove the obsolete AVFoundation/NativeOSXCanvas runtime dependencies from the active Gradle macOS runtime once no longer needed. Remove FFmpeg source/dependencies only where they are demonstrably no longer required to compile or run this macOS-only fork; avoid unrelated Maven cleanup.
4. Preserve the existing timeline contract: duration/current position, rate, mute/volume, start/pause/stop, seek, frame rate, and cleanup. Implement frame stepping using actual decoded presentation/frame behavior where possible; do not assume every source is constant-frame-rate without checking.
5. Handle asynchronous `Media`, `MediaPlayer`, and player readiness/errors without hanging the UI. Ensure an invalid codec/container or unsupported file produces a useful user-facing error and does not leave a phantom track or leak a player.
6. Scope accepted formats to what the selected OpenJFX runtime actually supports. Its documented media table lists MP4 containing H.264/AAC but does not list MOV; do not advertise MOV support based only on its extension. Test the user's OBS MOV directly, then test a local H.264/AAC MP4 transcode. If the MOV is unsupported, make MP4 input clear and document an optional local FFmpeg transcode command/profile without silently altering the original.
7. Do not implement reverse playback by issuing continuous decoder seeks unless measurements show it is acceptable. It is okay to disable reverse playback for this backend with a clear capability signal; retain forward/backward stepping.
8. Do not claim a cache improves behavior until measured. First characterize native seeking. If repeated seeks are slow, propose a separately testable derived proxy/index/frame cache with explicit disk location, invalidation key (source identity/size/mtime plus transcode settings), cleanup policy, and preservation of annotation timestamps. Do not modify originals.

## Required Tests and Measurements

- Add unit tests for the JavaFX player adapter’s time/rate/state conversions and disposal/error paths using a test seam that does not require opening a visible desktop window.
- Add an integration test or reproducible manual test harness for a short local H.264/AAC MP4 fixture. Avoid committing large media files; document how the fixture is generated or selected.
- Verify direct seek while paused, seek after play/pause, onset-to-play, end-of-file seek, step forward/backward, duration/frame-rate reporting, and closing/removing a viewer.
- Measure random seeks at multiple points in the target 21:17 clip: p50 and p95 completion latency, frame accuracy (requested timestamp vs displayed frame PTS if accessible), playback CPU, and behavior when scrubbing rapidly. Include cold-open and repeated-seek measurements.
- Compare source MOV and a local MP4 transcode with a short keyframe interval. Record the exact video/audio codecs, frame rate (including whether variable), resolution, bitrate, and GOP. Keep the measurement recipe reproducible.
- Run `make build`, the focused test target(s), and `make run`. Report full-suite status without disabling existing failures.

## Suggested Local Transcode Candidate

Treat this as a benchmark fixture, not a permanent requirement. A reasonable first candidate is MP4 with H.264 video, AAC audio, source resolution/frame rate retained, and keyframes about every 0.5-1 second. For example, an FFmpeg command could use `-c:v libx264 -preset fast -crf 18 -g <fps> -keyint_min <fps> -sc_threshold 0 -c:a aac -b:a 192k -movflags +faststart`; adjust GOP frame count for the actual FPS and compare resulting quality, size, and seek latency. Do not bake this command into UI until benchmarks support it.

## Deliverables

- Working OpenJFX `StreamViewer` integration and macOS plugin registration.
- Focused automated coverage and the manual/integration verification recipe.
- Updates to `BUILDING.md` describing prerequisites, supported input profile, transcode workflow, and playback test results.
- A concise implementation summary with decisions and unresolved risks. Leave Maven files and distribution/signing untouched.
