#!/usr/bin/env bash
# Regenerates the small video files used by Mp4FrameTableTest.
#
# Usage: tools/playback/make-unit-fixtures.sh
# Output: src/test/resources/video/*.mp4|.mov, a few kilobytes each.
# The expected frame times in Mp4FrameTableTest come from:
#   ffprobe -v error -select_streams v:0 -show_entries frame=pts_time,duration_time -of csv=p=0 FILE
set -euo pipefail

out="$(cd "$(dirname "$0")/../.." && pwd)/src/test/resources/video"
mkdir -p "$out"
x264=(-c:v libx264 -preset ultrafast -pix_fmt yuv420p -bsf:v filter_units=remove_types=6)

# 30 fps, 12 frames, B-frames so presentation order differs from decode order (ctts + elst).
ffmpeg -v error -y -f lavfi -i "testsrc2=size=64x48:rate=30" -frames:v 12 \
    "${x264[@]}" -preset medium -bf 2 -g 12 -map_metadata -1 "$out/cfr-bframes.mp4"

# Variable frame rate: 4 frames at 30 fps, then 4 frames at 10 fps.
ffmpeg -v error -y -f lavfi -i "testsrc2=size=64x48:rate=30" -frames:v 8 \
    -vf "setpts='if(lt(N,4),N/30,4/30+(N-4)/10)/TB'" -fps_mode vfr -video_track_timescale 90000 \
    "${x264[@]}" -bf 0 -map_metadata -1 "$out/vfr.mp4"

# Track starting 0.5 s into the movie: an empty edit before the media.
ffmpeg -v error -y -f lavfi -i "testsrc2=size=64x48:rate=25" -frames:v 5 \
    -vf "setpts=PTS+0.5/TB" -fps_mode passthrough "${x264[@]}" -bf 0 -map_metadata -1 "$out/delayed.mp4"

# QuickTime container with the same layout as cfr-bframes.
ffmpeg -v error -y -i "$out/cfr-bframes.mp4" -c copy "$out/cfr-bframes.mov"

ls -l "$out"
