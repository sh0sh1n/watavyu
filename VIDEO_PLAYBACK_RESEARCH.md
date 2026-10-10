# Video Playback Performance Research

## Goal

Improve local video playback and seeking for personal annotation work. Keep video files local; consider narrowing supported input formats only if measurements show that doing so materially improves the workflow.

This is an investigation note, not a decision to replace the player or require transcoding.

## Current Findings

- The application targets Java 8 and uses a plugin-based player integration. On macOS, the native AVFoundation plugin is ordered first; on Windows, the FFmpeg plugin is the available built-in backend. See `pom.xml` and `src/main/java/org/datavyu/plugins/PluginManager.java`.
- The FFmpeg integration depends on `org.datavyu:ffmpeg-plugin:0.24` with platform-specific native artifacts. `FFmpegPlayer` passes the selected local file as a URI to `FfmpegSdlMediaPlayer`. See `pom.xml` and `src/main/java/org/datavyu/plugins/ffmpegplayer/FFmpegPlayer.java`.
- Both built-in plugins advertise `.avi`, `.mov`, `.mpg`, and `.mp4`. These are container extensions, not a single codec/profile guarantee. See `src/main/java/org/datavyu/plugins/ffmpegplayer/FFmpegPlugin.java` and `src/main/java/org/datavyu/plugins/nativeosx/AvFoundationPlugin.java`.
- `ClockTimer` schedules four tasks at 100 ms intervals. Its seek-playback callback can make the viewer seek on every tick during reverse/seek playback. The FFmpeg viewer ignores new requests while `isSeeking` is true; if a seek throws, that flag is not reset. See `src/main/java/org/datavyu/util/ClockTimer.java`, `src/main/java/org/datavyu/plugins/StreamViewerDialog.java`, and `src/main/java/org/datavyu/plugins/ffmpegplayer/FFmpegStreamViewer.java`.
- Normal periodic synchronization seeks a stream only after it differs from the shared clock by at least 1.5 seconds (`ClockTimer.SYNC_THRESHOLD`). Whether that threshold contributes to perceived clunkiness needs measurement. See `src/main/java/org/datavyu/plugins/StreamViewerDialog.java`.
- Clock callbacks include updates to UI-facing state. Confirm which thread owns Swing updates and native player calls before changing scheduling or adding asynchronous seeks.
- No playback-specific tests were found under `src/test/java`. The README's packaging example skips tests.

## Privacy Boundary

The inspected playback path opens a local file URI; no video upload path was found in that path. The app's share-data preference defaults to off. When enabled, visible analytics report app/OS/Java versions and video format/plugin names, not video contents or file paths. The app also checks an online version endpoint, so it is not strictly offline by default. This is a source-level review of the inspected code, not an audit of all dependencies or network traffic.

## Scope Work In This Order

1. Establish a repeatable local baseline before changing the decoder. Record open time, random-seek latency (median and tail), displayed-frame accuracy, playback smoothness, CPU use, resolution, frame rate, codec, bitrate, and number of simultaneous videos.
2. Use representative videos from the intended projects. Include frequent scrubbing, frame stepping, reverse playback, long-GOP and short-GOP encodes, and any multi-track workflow that matters.
3. Add focused tests for seek request ordering/coalescing, seek failure recovery, and frame stepping. Confirm Swing thread ownership and characterize the current 100 ms scheduler and 1.5 second resynchronization threshold.
4. Based on the baseline, compare a minimal fix to the current FFmpeg integration with a maintained player integration such as libmpv. Include native packaging, platform support, rendering integration, and reproducible seek accuracy in that comparison.
5. Decide on a recommended input profile only after benchmarking. Specify codec, container, resolution, frame rate, and keyframe interval, not just a filename extension. Keep general-format support if it is adequate for the real workload.

## GOP Terminology

GOP means **Group of Pictures**: a keyframe (also called an I-frame) plus the dependent frames encoded around it. A player generally needs to start decoding at a keyframe and decode forward to display a requested frame, so shorter keyframe spacing can reduce random-seek work. More frequent keyframes can increase file size or bitrate for comparable visual quality. The right interval is a measured tradeoff, not a universal setting; a roughly one-second interval is a reasonable benchmark candidate, not yet a project requirement.

## Decisions Still Open

- Which operating systems must the personal fork support?
  - i'm on osx 27 so need to support that. treat this fork as a hard reset with a new version counter; no backwards compatibility requirement and we should aggressivley modernize instead of choosing complexity to support legacy decision/features.
  
- What video codecs, resolutions, frame rates, and number of simultaneous tracks occur in the intended projects?
  - dimensions: 1920 x 1080
  - codecs: mpeg-4 aac, h.264
  - color profile: HD (1-1-1)
  - duration: 21:17
  - audio channels: Stereo
  - above information is from a .mov recorded using OBS studio, read from the Finder Info window for the file.
- Is smooth reverse playback important, or are accurate frame stepping and responsive forward seeking the priority?
- I care most about random seeking because I want to use this for bookmarking video locations. Different researchers will prefer different things so a ordering I choose may not be suitable for someone else; in an ideal world we would encapsulate different performance profiles and allow users to select and change between profiles for their needs, but that is probably too much upfront complexity. Treat random seeking and playback as top priority (the spreadsheet pane of the UI creates "cells" which are time regions of the video and researchers will often seek to the start of a cell and play to the end of the cell and repeat with trying to evaluate that segment of the video). After that frame stepping (forward and back). Lastly, reverse playback. reverse playback can be dropped completely if it too unwieldy; use cases for reverse playback are limited and we can support it with a duplicate video file transcoded backwards if our forward playback is optimized.
- Would a documented optional transcode workflow be acceptable if it substantially improves seeking?
  - absolutely; we should definitely make use of caches whereever possible, where cache is used broadly to refer to any precomputation that speeds up future use of the software