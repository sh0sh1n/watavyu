# Playback Backend Options

This note preserves alternatives for the macOS playback work. Current target: local 1080p H.264/AAC research video, responsive random seeking and forward playback, accurate frame stepping, offline/local media, Swing desktop UI, Apple Silicon.

## 1. OpenJFX Media: First Prototype

**Fit:** Smallest integration experiment for the existing Java application. OpenJFX is an actively maintained, separately versioned Java component and provides `Media`, `MediaPlayer.seek`, playback state/rate/volume controls, and `MediaView`. It can be embedded into Swing with `JFXPanel` while leaving the app's broader UI in Swing.

**Format fit:** OpenJFX's current media documentation explicitly lists MP4 with H.264/AAC. It does not list QuickTime `.mov` in its documented container table, so a local H.264/AAC MP4 transcode may be necessary. Validate the user's exact OBS MOV rather than inferring codec support from `.mov` or `.mp4` alone.

**Risks:** JavaFX has its own UI thread and lifecycle; all cross-thread calls must be marshalled correctly. `MediaPlayer` is asynchronous. Its documented seek API does not promise frame-accurate, low-latency seeking. Reverse playback and exact frame stepping need empirical validation. Don't assume the JavaFX runtime uses a particular macOS decoder path without profiling.

**Use when:** Rapidly establishing a supported, arm64-capable native playback baseline is more important than broad codec coverage. Benchmark it before committing to the architecture.

References: [OpenJFX getting started](https://openjfx.io/openjfx-docs/), [OpenJFX Media supported formats](https://openjfx.io/javadoc/25/javafx.media/javafx/scene/media/package-summary.html), [MediaPlayer API](https://openjfx.io/javadoc/25/javafx.media/javafx/scene/media/MediaPlayer.html).

## 2. JavaCV / JavaCPP FFmpeg: Precision and Control

**Fit:** JavaCV wraps FFmpeg and provides frame-grabbing APIs; JavaCPP Presets publish platform-specific native artifacts, including `macosx-arm64`. This offers low-level control over decode, presentation timestamps, seeking, frame stepping, pixel buffers, and derived caches. It is the stronger fallback if OpenJFX misses the seek accuracy/latency target.

**Risks:** Watavyu would own the rendering surface, audio output/synchronization, threading, seek coalescing, frame presentation, and player lifecycle. `javacv-platform` can pull large native artifacts for many platforms; select only required modules and the macOS ARM64 classifier/platform. Hardware acceleration availability and useful FFmpeg configuration must be verified on this Mac. This is a larger implementation than replacing one library call.

**Use when:** Exact timestamps, frame-level behavior, or decoder-level seek/cache strategies are product requirements and the team is ready to own the media pipeline.

References: [JavaCV](https://github.com/bytedeco/javacv), [JavaCPP Presets](https://github.com/bytedeco/javacpp-presets). JavaCPP documents `macosx-arm64` platform builds and FFmpeg presets; confirm current artifact coordinates/version and license configuration at implementation time.

## 3. VLCJ / LibVLC: Broad Codec Coverage

**Fit:** VLCJ embeds LibVLC in Java and supports Swing/AWT video surfaces. VLC for macOS is distributed as a Universal build supporting Apple Silicon. This is attractive if the main priority becomes compatibility with many researchers' input formats rather than minimizing runtime size.

**Version choice:** VLCJ 4.12.x with VLC 3 is the stable branch documented by the project. VLCJ 5 requires VLC 4, which is still a development/nightly dependency as of this review; avoid basing a local annotation tool on it unless willing to track unstable native APIs.

**Risks:** LibVLC is a sizable native runtime and adds its own distribution/packaging and native thread lifecycle. VLCJ documents macOS embedding limitations, callback threading constraints, and the need to retain player objects to avoid native callbacks into collected Java objects. Metadata parsing can make network requests for artwork unless explicitly disabled; configure local/offline behavior and test privacy. Benchmark seek latency/frame accuracy instead of assuming the desktop VLC application's behavior carries over to embedding.

**Use when:** Broad codec support and a ready-made native media engine outweigh runtime size and integration complexity.

References: [VLCJ stable 4.x](https://github.com/caprica/vlcj/tree/vlcj-4.x), [VLC for macOS](https://www.videolan.org/vlc/download-macosx.html), [VLCJ current development branch](https://github.com/caprica/vlcj).

## Existing AVFoundation / NativeOSXCanvas Bridge

The current wrapper comes from [`databrary/datavyu-ffmpegplugin`](https://github.com/databrary/datavyu-ffmpegplugin): Objective-C `NativeOSXCanvas.m` implements JNI methods and attaches an AVPlayer-backed Core Animation layer to an AWT `Canvas`. It is published as `org.datavyu:libNativeOSXCanvas:0.92`.

The original report was a missing runtime artifact; Gradle now declares the dependency. The artifact's `libNativeOSXCanvas.jnilib` is x86_64-only, so it is not usable in a native arm64 JVM. The upstream Xcode project has JDK 7 header/library paths, an old deployment target, and a `JavaNativeFoundation.framework` dependency. Rebuilding is theoretically possible but requires a JNF/JAWT/Core Animation port and a new arm64 artifact, not merely changing `ARCHS`. This is not the recommended first route.

## Current Recommendation

1. Prototype OpenJFX Media on a representative local MP4 and the original MOV; benchmark before deciding.
2. If seek latency or frame accuracy is insufficient, compare JavaCV/FFmpeg against VLCJ 4 using the same test clips and measurement harness.
3. Choose based on measured p50/p95 seek completion, requested-vs-displayed PTS error, playback CPU, memory/runtime footprint, multi-track stability, and privacy behavior.
4. Only then define a default transcode profile/cache. Preserve source files and annotation timestamps; generated media must be local, reproducible, and clearly invalidated when its source/settings change.
