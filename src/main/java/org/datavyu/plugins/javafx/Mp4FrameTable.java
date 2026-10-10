/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.datavyu.plugins.javafx;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads video frame presentation times from an MP4/QuickTime sample table without
 * decoding. Times follow the same rules a player applies: decode times from
 * {@code stts}, B-frame reordering from {@code ctts}, and the track's edit list
 * ({@code elst}) for start delay and trimmed leading samples.
 *
 * Fragmented MP4 (samples described in {@code moof} boxes) is not supported.
 */
public final class Mp4FrameTable {

    private Mp4FrameTable() {
    }

    /**
     * @return Frame times of the first enabled video track.
     * @throws IOException If the file is not readable or has no usable video sample table.
     */
    public static FrameIndex read(final File file) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            ByteBuffer moov = findTopLevelBox(in, "moov");
            if (moov == null) {
                throw new IOException("No moov box in " + file.getName());
            }
            return readMovie(moov);
        }
    }

    static FrameIndex readMovie(final ByteBuffer moov) throws IOException {
        long movieTimescale = 0;
        for (Box box : children(moov)) {
            if (box.type.equals("mvhd")) {
                movieTimescale = readTimescale(box.payload);
            }
        }
        for (Box box : children(moov)) {
            if (box.type.equals("trak")) {
                FrameIndex index = readVideoTrack(box.payload, movieTimescale);
                if (index != null) {
                    return index;
                }
            }
        }
        throw new IOException("No video track with a sample table");
    }

    private static FrameIndex readVideoTrack(final ByteBuffer trak, final long movieTimescale) throws IOException {
        Box mdia = child(trak, "mdia");
        Box hdlr = mdia == null ? null : child(mdia.payload, "hdlr");
        if (hdlr == null || !fourCc(hdlr.payload, 8).equals("vide")) {
            return null;
        }
        Box mdhd = child(mdia.payload, "mdhd");
        Box minf = child(mdia.payload, "minf");
        Box stbl = minf == null ? null : child(minf.payload, "stbl");
        Box stts = stbl == null ? null : child(stbl.payload, "stts");
        if (mdhd == null || stts == null) {
            return null;
        }
        long timescale = readTimescale(mdhd.payload);
        if (timescale <= 0) {
            throw new IOException("Invalid media timescale " + timescale);
        }

        long[] decodeTimes = readDecodeTimes(stts.payload);
        int samples = decodeTimes.length - 1;
        if (samples <= 0) {
            throw new IOException("Video track has no samples");
        }
        Box ctts = child(stbl.payload, "ctts");
        long[] compositionOffsets = ctts == null ? new long[samples] : readCompositionOffsets(ctts.payload, samples);

        // Presentation times in media units, sorted into display order.
        long[] presentation = new long[samples];
        long mediaEnd = Long.MIN_VALUE;
        for (int i = 0; i < samples; i++) {
            presentation[i] = decodeTimes[i] + compositionOffsets[i];
            mediaEnd = Math.max(mediaEnd, decodeTimes[i + 1] + compositionOffsets[i]);
        }
        Arrays.sort(presentation);

        Edit edit = readEdit(trak, movieTimescale);
        double delayMillis = edit.emptyMovieUnits * 1000.0 / Math.max(movieTimescale, 1);
        long mediaStart = edit.mediaTime;

        List<Double> starts = new ArrayList<>(samples);
        for (long time : presentation) {
            if (time >= mediaStart) {
                starts.add(delayMillis + (time - mediaStart) * 1000.0 / timescale);
            }
        }
        double end = delayMillis + (mediaEnd - mediaStart) * 1000.0 / timescale;
        if (edit.mediaMovieUnits > 0) {
            end = Math.min(end, delayMillis + edit.mediaMovieUnits * 1000.0 / movieTimescale);
            while (!starts.isEmpty() && starts.get(starts.size() - 1) >= end) {
                starts.remove(starts.size() - 1);
            }
        }
        if (starts.isEmpty()) {
            throw new IOException("The edit list hides every video frame");
        }
        double[] result = new double[starts.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = starts.get(i);
        }
        return FrameIndex.measured(result, end);
    }

    /** @return Decode time of each sample plus one trailing entry for the end of the last sample. */
    private static long[] readDecodeTimes(final ByteBuffer stts) throws IOException {
        ByteBuffer b = stts.duplicate();
        b.position(4);
        long entries = Integer.toUnsignedLong(b.getInt());
        List<long[]> runs = new ArrayList<>();
        long total = 0;
        for (long e = 0; e < entries; e++) {
            long count = Integer.toUnsignedLong(b.getInt());
            long delta = Integer.toUnsignedLong(b.getInt());
            runs.add(new long[]{count, delta});
            total += count;
        }
        if (total > Integer.MAX_VALUE - 1) {
            throw new IOException("Too many samples: " + total);
        }
        long[] times = new long[(int) total + 1];
        int i = 0;
        long time = 0;
        for (long[] run : runs) {
            for (long c = 0; c < run[0]; c++) {
                times[i++] = time;
                time += run[1];
            }
        }
        times[i] = time;
        return times;
    }

    private static long[] readCompositionOffsets(final ByteBuffer ctts, final int samples) {
        ByteBuffer b = ctts.duplicate();
        b.position(4);
        long entries = Integer.toUnsignedLong(b.getInt());
        long[] offsets = new long[samples];
        int i = 0;
        for (long e = 0; e < entries && i < samples; e++) {
            long count = Integer.toUnsignedLong(b.getInt());
            // Version 0 offsets are nominally unsigned, but writers store negative values
            // in them too; reading them as signed is what players do.
            int offset = b.getInt();
            for (long c = 0; c < count && i < samples; c++) {
                offsets[i++] = offset;
            }
        }
        return offsets;
    }

    /** The parts of an edit list that affect where frames appear. */
    private static final class Edit {
        long emptyMovieUnits;
        long mediaTime;
        long mediaMovieUnits;
    }

    private static Edit readEdit(final ByteBuffer trak, final long movieTimescale) {
        Edit edit = new Edit();
        Box edts = child(trak, "edts");
        Box elst = edts == null ? null : child(edts.payload, "elst");
        if (elst == null || movieTimescale <= 0) {
            return edit;
        }
        ByteBuffer b = elst.payload.duplicate();
        int version = b.get(0);
        b.position(4);
        long entries = Integer.toUnsignedLong(b.getInt());
        for (long e = 0; e < entries; e++) {
            long segmentDuration = version == 1 ? b.getLong() : Integer.toUnsignedLong(b.getInt());
            long mediaTime = version == 1 ? b.getLong() : b.getInt();
            b.getInt(); // media rate
            if (mediaTime == -1) {
                edit.emptyMovieUnits += segmentDuration;
            } else {
                // Only the first media segment is used; multi-segment edits are rare in
                // recordings and would need a full timeline remap.
                edit.mediaTime = mediaTime;
                edit.mediaMovieUnits = segmentDuration;
                break;
            }
        }
        return edit;
    }

    private static long readTimescale(final ByteBuffer fullBox) {
        int version = fullBox.get(0);
        return Integer.toUnsignedLong(fullBox.getInt(version == 1 ? 20 : 12));
    }

    private static final class Box {
        final String type;
        final ByteBuffer payload;

        Box(final String type, final ByteBuffer payload) {
            this.type = type;
            this.payload = payload;
        }
    }

    private static Box child(final ByteBuffer parent, final String type) {
        for (Box box : children(parent)) {
            if (box.type.equals(type)) {
                return box;
            }
        }
        return null;
    }

    private static List<Box> children(final ByteBuffer parent) {
        List<Box> boxes = new ArrayList<>();
        ByteBuffer b = parent.duplicate();
        b.position(0);
        while (b.remaining() >= 8) {
            int start = b.position();
            long size = Integer.toUnsignedLong(b.getInt());
            String type = fourCc(b, b.position());
            b.position(b.position() + 4);
            int header = 8;
            if (size == 1) {
                size = b.getLong();
                header = 16;
            } else if (size == 0) {
                size = b.limit() - start;
            }
            if (size < header || start + size > b.limit()) {
                break;
            }
            ByteBuffer payload = b.duplicate();
            payload.position(start + header).limit((int) (start + size));
            boxes.add(new Box(type, payload.slice()));
            b.position((int) (start + size));
        }
        return boxes;
    }

    private static String fourCc(final ByteBuffer b, final int offset) {
        char[] chars = new char[4];
        for (int i = 0; i < 4; i++) {
            chars[i] = (char) (b.get(offset + i) & 0xff);
        }
        return new String(chars);
    }

    /** Scans top-level boxes, skipping media data, and reads the named box into memory. */
    private static ByteBuffer findTopLevelBox(final RandomAccessFile in, final String type) throws IOException {
        long position = 0;
        long length = in.length();
        byte[] header = new byte[16];
        while (position + 8 <= length) {
            in.seek(position);
            in.readFully(header, 0, 8);
            ByteBuffer h = ByteBuffer.wrap(header);
            long size = Integer.toUnsignedLong(h.getInt(0));
            String boxType = fourCc(h, 4);
            int headerSize = 8;
            if (size == 1) {
                in.readFully(header, 8, 8);
                size = h.getLong(8);
                headerSize = 16;
            } else if (size == 0) {
                size = length - position;
            }
            if (size < headerSize) {
                throw new IOException("Malformed box " + boxType + " at " + position);
            }
            if (boxType.equals(type)) {
                long payloadSize = size - headerSize;
                if (payloadSize > Integer.MAX_VALUE) {
                    throw new IOException(type + " box is too large");
                }
                byte[] payload = new byte[(int) payloadSize];
                in.readFully(payload);
                return ByteBuffer.wrap(payload);
            }
            position += size;
        }
        return null;
    }
}
