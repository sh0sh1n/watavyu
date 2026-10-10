# Building Watavyu Locally

## Scope

This is the first Gradle build for the personal macOS fork. It targets local development on macOS 27 / Apple Silicon. It builds and launches the existing Swing application; it does not create an installer, sign an app bundle, or clean up the Maven build.

Gradle is the build path to use for this fork. `pom.xml` and the existing Maven scripts remain in the repository unchanged for now.

## Toolchain

The project pins these tools in `.tool-versions`:

- Java: Temurin 25.0.4, the JDK used for compilation and running.
- Gradle: 9.8.1. The checked-in Wrapper is authoritative for Gradle builds; a global Gradle install is not needed for normal use.

Install the `java` and `gradle` asdf plugins once if they are not already installed, then install this repository's pinned versions:

```sh
asdf plugin add java https://github.com/halcyon/asdf-java.git
asdf plugin add gradle
asdf install
```

If a plugin is already installed, skip its `asdf plugin add` command. In a shell where asdf has not configured `JAVA_HOME` and `PATH`, select the pinned JDK for Gradle like this:

```sh
export JAVA_HOME="$(asdf where java)"
export PATH="$JAVA_HOME/bin:$PATH"
```

The Gradle Wrapper downloads Gradle 9.8.1 on its first invocation and reuses it afterwards.

## Common Commands

```sh
make build
make run
make test
make clean
```

The Makefile is a small convenience layer over the Gradle Wrapper; it does not introduce another build system. It reads the repo-pinned Java location from asdf for the build process. You can also invoke Gradle directly with `./gradlew <task>`.

`make run` starts the desktop UI in the foreground. The first application launch creates or updates settings under `~/Library/Application Support/Datavyu`.

## How the Gradle Build Works

- `settings.gradle.kts` names the root project.
- `build.gradle.kts` applies Gradle's `application` plugin, selects `org.datavyu.Datavyu` as the main class, and sets the 1 GB application heap.
- The Java toolchain requests JDK 25, so compiler selection is explicit rather than inherited from whichever JDK happens to be on `PATH`.
- Dependencies are declared in Gradle and resolved from Maven Central plus the legacy Datavyu repositories needed by this source snapshot. The macOS build keeps the old `ffmpeg-plugin` artifact for its AVFoundation Java classes and explicitly declares `libNativeOSXCanvas:0.92`, which the published plugin metadata omitted.
- `processResources` copies `Build_Template.properties` into Gradle's generated resource output as `Build.properties`, substituting the Gradle project version. Unlike the old Maven Ant step, this does not rewrite files under `src/main/resources`.
- `tasks.test` uses the repository's existing TestNG tests and supplies their `testPath` resource directory.
- Gradle outputs are under `build/`; local Gradle caches are under `.gradle/`. Both are ignored by Git.

The Gradle project version is currently `0.1.0-SNAPSHOT`. The legacy UI resource still contains the old `1.5.2` product label/version; updating application identity consistently is separate from proving the build path.

## Verification On This Machine

- `./gradlew compileJava` succeeds on Temurin 25.0.4 / macOS 27 arm64. Remaining compiler warnings are legacy `Object.finalize()` removal.
- `./gradlew test --tests org.datavyu.util.MacOSTest` passes, covering version parsing for macOS `27.0` and legacy `10.15.7` formats.
- `./gradlew run` reaches the application window. Startup now uses the system Swing look and feel and Java's supported `java.awt.Desktop` handlers instead of the old Quaqua and internal `com.apple.eawt` integrations. Optional trackpad pinch/swipe gestures were removed; the on-screen zoom and scroll controls remain.
- `./gradlew test` runs 62 tests: 10 fail and 27 are skipped. Failures include the old Mockito/Byte Buddy stack not supporting the current Java class-file version and tests that cannot find expected resources. This suite is not a green verification gate yet; the failures were not hidden or disabled.
- Video playback has **not** been verified. The reported null-resource error was caused by a missing runtime dependency; Gradle now declares `libNativeOSXCanvas:0.92`. Its bundled JNI library is x86_64-only, however, so loading it in the native arm64 JDK will fail at the next step. The FFmpeg/SDL dylibs are also x86_64-only, but macOS currently selects AVFoundation rather than the FFmpeg plugin. The current milestone is UI startup and Java compilation, not usable local video annotation.

## Native Playback Decision

The AVFoundation bridge comes from [`databrary/datavyu-ffmpegplugin`](https://github.com/databrary/datavyu-ffmpegplugin), under `NativeOSXCanvas/src/native/NativeOSXCanvas.m`, and is published as `org.datavyu:libNativeOSXCanvas:0.92`. It uses JNI/JAWT to attach an AVPlayer-backed Core Animation layer to an AWT `Canvas`. Its Xcode project still has JDK 7 paths, a macOS 10.7 deployment target, and a link to Apple's `JavaNativeFoundation.framework` (JNF). Xcode 27 is installed on this machine, but rebuilding for arm64 is a port: replace the JNF helpers, modernize the JAWT/Core Animation integration, and build/package a matching arm64 library. Changing only the Xcode architecture setting is unlikely to be sufficient.

Options for a modern backend:

- **OpenJFX Media 27:** Lowest-risk prototype for this macOS-only fork. It documents H.264/AAC playback in MP4 and provides native platform builds and `MediaPlayer.seek`; the JavaFX view can be hosted in the current Swing UI. The documented format table does not list `.mov`, so first test a transcode to H.264/AAC MP4. Seek latency and frame accuracy need measurement before adopting it.
- **VLCJ 4.12.x with VLC 3:** A maintained, stable Java binding with Swing/AWT embedding and an Apple Silicon-capable VLC build. macOS embedding has limitations and this adds a larger native VLC runtime. VLCJ 5 currently requires VLC 4, which is still a development/nightly target, so it is not the conservative choice today.
- **JavaCV/FFmpeg 1.5.14:** Prebuilt JavaCPP native artifacts include `macosx-arm64`. This gives direct decoder/frame access and the most control for precise seeking and caching, at the cost of building the render/audio synchronization layer and carrying larger native dependencies.

Recommended order: prototype OpenJFX against the user's actual clip and a short-GOP H.264/AAC MP4, recording random-seek latency and displayed-frame accuracy. If it misses the annotation target, prototype JavaCV/FFmpeg next. Rebuild the AVFoundation bridge only if keeping AVPlayer is valuable enough to justify maintaining custom Objective-C/JNI code. VLCJ remains a credible alternative if broad codec support is more important than minimizing the native dependency footprint.

## Not Included

No installer/app image or code signing is configured. The Maven build remains untouched until the local Gradle path, especially native media playback, has been verified.
