#!/usr/bin/env bash
# Rebuilds libjfxmedia_avf.dylib from OpenJFX 27-ga with avf.patch applied.
#
# The patch changes OpenJFX's macOS (AVFoundation) player in two ways:
# - Seeks are exact. Upstream truncates every seek to whole seconds and lets
#   AVFoundation snap to a keyframe.
# - The audio processing tap is not attached and volume is set on the player.
#   The tap (balance, equalizer, spectrum) delays the start of playback by about
#   350 ms.
# Only this one library is rebuilt; it links against the stock libjfxmedia,
# libglib-lite and libgstreamer-lite from the published javafx-media jar.
#
# Requirements: Xcode command line tools, git, curl, JDK 25 (JAVA_HOME or asdf).
# Usage: third_party/openjfx-media-avf/build.sh
# Output: third_party/openjfx-media-avf/libjfxmedia_avf.dylib (arm64)
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
tag="27-ga"
javafx_version="27"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

java_home="${JAVA_HOME:-$(asdf where java)}"
maven="https://repo1.maven.org/maven2/org/openjfx"

git clone -q --depth 1 --branch "$tag" --filter=blob:none --sparse https://github.com/openjdk/jfx.git "$work/jfx"
git -C "$work/jfx" sparse-checkout set \
    modules/javafx.media/src/main/native \
    modules/javafx.media/src/main/java \
    modules/javafx.media/src/tools
git -C "$work/jfx" apply "$here/avf.patch"

for module in base graphics media; do
    curl -sSfL -o "$work/javafx-$module.jar" \
        "$maven/javafx-$module/$javafx_version/javafx-$module-$javafx_version-mac-aarch64.jar"
done
module_path="$work/javafx-base.jar:$work/javafx-graphics.jar:$work/javafx-media.jar"
media_src="$work/jfx/modules/javafx.media/src"

# Generated headers: JNI headers from the Java sources and the media error table.
gen="$work/gen"
find "$media_src/main/java" -name '*.java' > "$work/sources.txt"
"$java_home/bin/javac" -nowarn -h "$gen" -d "$work/classes" \
    --module-path "$work/javafx-base.jar:$work/javafx-graphics.jar" \
    --patch-module "javafx.media=$media_src/main/java" @"$work/sources.txt"
"$java_home/bin/javac" -d "$work/tools" --module-path "$module_path" --add-modules javafx.media \
    --add-exports javafx.media/com.sun.media.jfxmedia=ALL-UNNAMED "$media_src/tools/java/headergen/HeaderGen.java"
"$java_home/bin/java" --module-path "$module_path" --add-modules javafx.media \
    --add-exports javafx.media/com.sun.media.jfxmedia=ALL-UNNAMED -cp "$work/tools" \
    headergen.HeaderGen "$gen/jfxmedia_errors.h" "$media_src/main/java"

# Link inputs come from the stock jar. libjfxmedia is marked up to date so make
# builds only the AVFoundation library.
out="$work/out/Release"
mkdir -p "$out"
(cd "$out" && unzip -oq "$work/javafx-media.jar" libjfxmedia.dylib libglib-lite.dylib libgstreamer-lite.dylib)
(
    cd "$media_src/main/native/jfxmedia/projects/mac"
    CFLAGS="-arch arm64 -mmacosx-version-min=11.0" \
    LDFLAGS="-arch arm64 -mmacosx-version-min=11.0 -framework Cocoa -framework CoreVideo" \
    make -o "$out/libjfxmedia.dylib" OUTPUT_DIR="$work/out" BUILD_TYPE=Release BASE_NAME=jfxmedia \
        ARCH=arm64 JAVA_HOME="$java_home" GENERATED_HEADERS_DIR="$gen" CC=clang LINKER=clang++ \
        "$out/libjfxmedia_avf.dylib"
)

cp "$out/libjfxmedia_avf.dylib" "$here/libjfxmedia_avf.dylib"
shasum -a 256 "$here/libjfxmedia_avf.dylib"
