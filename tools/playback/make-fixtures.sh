#!/usr/bin/env bash
# Builds derived playback fixtures from a source video without modifying it.
#
# Usage: tools/playback/make-fixtures.sh SOURCE OUT_DIR [SECONDS]
#
# Outputs (all H.264/AAC MP4, source resolution and frame rate retained):
#   remux.mp4          lossless stream copy of SOURCE into an MP4 container
#   barcode-gop<N>.mp4 re-encode with the frame number burned into the top-left
#                      corner as a 16-bit barcode, keyframe every N frames
#
# The barcode is 16 cells of 64x64 px starting at (0,0); cell i is white when
# bit i of the zero-based frame number is set. PlaybackBenchmark reads it back to
# identify the displayed frame. SECONDS limits the output length (default: all).
set -euo pipefail

if [[ $# -lt 2 ]]; then
    sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
fi

source="$1"
out="$2"
limit=()
if [[ $# -ge 3 ]]; then
    limit=(-t "$3")
fi
mkdir -p "$out"

fps=$(ffprobe -v error -select_streams v:0 -show_entries stream=avg_frame_rate -of csv=p=0 "$source")
gop_source=$(python3 -c "from fractions import Fraction; print(round(Fraction('$fps') * 1.9))")
gop_1s=$(python3 -c "from fractions import Fraction; print(round(Fraction('$fps')))")
gop_half=$(python3 -c "from fractions import Fraction; print(round(Fraction('$fps') / 2))")

ffmpeg -v error -y "${limit[@]}" -i "$source" -map 0:v:0 -map 0:a:0? -c copy -movflags +faststart "$out/remux.mp4"

barcode="color=c=black:s=1024x64:r=$fps,format=gray,geq=lum='if(bitand(N\,pow(2\,floor(X/64)))\,255\,0)'"

for gop in "$gop_source" "$gop_1s" "$gop_half"; do
    ffmpeg -v error -y "${limit[@]}" -i "$source" -f lavfi -i "$barcode" \
        -filter_complex "[1:v]format=yuv420p[bar];[0:v][bar]overlay=0:0:shortest=1[v]" \
        -map "[v]" -map 0:a:0? \
        -c:v libx264 -preset fast -crf 18 -pix_fmt yuv420p \
        -g "$gop" -keyint_min "$gop" -sc_threshold 0 \
        -c:a aac -b:a 192k -movflags +faststart \
        "$out/barcode-gop$gop.mp4"
done

ls -l "$out"
