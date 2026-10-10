package org.datavyu.plugins.javafx;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Measures OpenJFX playback on one video through the production {@link JavaFxPlayback}.
 *
 * Usage: caffeinate -d ./gradlew playbackBenchmark -Pmedia=/path/to/video.mp4 [-Pseeks=200]
 *
 * Videos made by tools/playback/make-fixtures.sh carry a frame-number barcode, so every
 * measurement checks the displayed frame. For other videos, latency is the time until the
 * picture changes and frame accuracy is not measured. Seek latency is from the request to
 * the target frame appearing in a snapshot; snapshot polling adds a few milliseconds.
 */
public final class PlaybackBenchmark {

    private static final long TIMEOUT_MILLIS = 3_000;

    private final File file;
    private final int seeks;
    private final Random random = new Random(42);

    private FxPlaybackEngine engine;
    private JavaFxPlayback playback;
    private FrameIndex frames;
    private FrameProbe barcode;
    private MediaView picture;
    private boolean barcodeMode;

    private PlaybackBenchmark(final File file, final int seeks) {
        this.file = file;
        this.seeks = seeks;
    }

    public static void main(final String[] args) throws Exception {
        if (args.length < 1 || args[0].isEmpty()) {
            System.err.println("Usage: ./gradlew playbackBenchmark -Pmedia=/path/to/video.mp4 [-Pseeks=200]");
            System.exit(2);
        }
        int seeks = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        try {
            new PlaybackBenchmark(new File(args[0]), seeks).run();
        } finally {
            Platform.exit();
        }
    }

    private void run() throws Exception {
        long t0 = System.nanoTime();
        JavaFxToolkit.ensureStarted();
        long toolkit = System.nanoTime() - t0;

        t0 = System.nanoTime();
        engine = FxPlaybackEngine.open(file, 30);
        long coldOpen = System.nanoTime() - t0;
        t0 = System.nanoTime();
        frames = Mp4FrameTable.read(file);
        long frameTable = System.nanoTime() - t0;
        JavaFxToolkit.onFx(() -> {
            engine.dispose();
            return null;
        }).get();
        t0 = System.nanoTime();
        engine = FxPlaybackEngine.open(file, 30);
        long warmOpen = System.nanoTime() - t0;

        playback = new JavaFxPlayback(engine, frames, Platform::runLater);
        playback.setVolume(0);
        barcode = FrameProbe.attach(engine.player());
        picture = JavaFxToolkit.onFx(() -> {
            MediaView view = new MediaView(engine.player());
            view.setFitWidth(192);
            view.setPreserveRatio(true);
            new Scene(new Group(view));
            return view;
        }).get();
        barcodeMode = hasBarcode();

        System.out.println("file: " + file.getName() + " (" + file.length() / 1_000_000 + " MB)");
        System.out.printf("video: %dx%d, %d frames, %.3f fps (%s), duration %d ms%n", engine.width(), engine.height(),
                frames.frameCount(), frames.framesPerSecond(), frames.isConstantRate() ? "constant" : "variable",
                playback.durationMillis());
        System.out.printf("startup: JavaFX toolkit %s, cold open %s, warm open %s, frame table %s%n",
                ms(toolkit), ms(coldOpen), ms(warmOpen), ms(frameTable));
        System.out.println("mode: " + (barcodeMode ? "barcode (frame accuracy checked)"
                : "plain (latency = time until the picture changes; accuracy not checked)"));

        List<Integer> targets = randomFrames(seeks);
        report("random paused seek", seekTo(targets));
        report("repeated seek (first 20 targets again)", seekTo(targets.subList(0, Math.min(20, targets.size()))));
        seekAfterPlayPause(20);
        onsetToPlay(20);
        rapidScrub(10, 30);
        steps(30);
        endOfFile();
        playbackCpu(20);
    }

    /** Latencies in nanoseconds (-1 for timeouts) and displayed-minus-requested frame errors. */
    private static final class Result {
        final List<Long> latencies = new ArrayList<>();
        final TreeMap<Integer, Integer> errors = new TreeMap<>();
        int unchecked;

        void add(final long latency, final Integer error) {
            latencies.add(latency);
            if (error == null) {
                unchecked++;
            } else {
                errors.merge(error, 1, Integer::sum);
            }
        }
    }

    private Result seekTo(final List<Integer> targets) throws Exception {
        Result result = new Result();
        for (int target : targets) {
            long start = System.nanoTime();
            long before = barcodeMode ? 0 : pictureHash();
            playback.seek(frames.reportedMillis(target));
            result.add(awaitShown(target, start, before), checkShown(target));
        }
        return result;
    }

    /** A dark corner reads as frame 0, so check frames whose numbers set several different bits. */
    private boolean hasBarcode() throws Exception {
        for (int frame : new int[]{0, 0b1011, 0b10110100}) {
            if (frame >= frames.frameCount()) {
                continue;
            }
            playback.seek(frames.reportedMillis(frame));
            if (barcode.awaitFrame(frame, System.nanoTime(), 2_000) < 0) {
                return false;
            }
        }
        playback.seek(0);
        return barcode.awaitFrame(0, System.nanoTime(), 2_000) >= 0;
    }

    private long awaitShown(final int target, final long start, final long hashBefore) throws Exception {
        if (barcodeMode) {
            return barcode.awaitFrame(target, start, TIMEOUT_MILLIS);
        }
        long deadline = start + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS);
        while (System.nanoTime() < deadline) {
            if (pictureHash() != hashBefore) {
                return System.nanoTime() - start;
            }
        }
        return -1;
    }

    /** @return Displayed minus target frame once settled, or null without a barcode. */
    private Integer checkShown(final int target) throws Exception {
        return barcodeMode ? barcode.settledFrame(100) - target : null;
    }

    private void seekAfterPlayPause(final int n) throws Exception {
        Result result = new Result();
        for (int target : randomFrames(n)) {
            playback.play();
            Thread.sleep(300);
            playback.pause();
            long start = System.nanoTime();
            long before = barcodeMode ? 0 : pictureHash();
            playback.seek(frames.reportedMillis(target));
            result.add(awaitShown(target, start, before), checkShown(target));
        }
        report("seek after play then pause", result);
    }

    private void onsetToPlay(final int n) throws Exception {
        List<Long> firstAdvance = new ArrayList<>();
        List<Integer> drift = new ArrayList<>();
        int playMillis = 2_000;
        for (int target : randomFrames(n)) {
            target = Math.min(target, frames.frameCount() - 1 - 3 * (int) frames.framesPerSecond());
            playback.seek(frames.reportedMillis(target));
            awaitShown(target, System.nanoTime(), 0);
            long start = System.nanoTime();
            playback.play();
            long advanced = -1;
            if (barcodeMode) {
                while (System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS)) {
                    if (barcode.read() > target) {
                        advanced = System.nanoTime() - start;
                        break;
                    }
                }
            }
            firstAdvance.add(advanced);
            Thread.sleep(Math.max(0, playMillis - (System.nanoTime() - start) / 1_000_000));
            long elapsed = System.nanoTime() - start;
            if (barcodeMode) {
                int expected = frames.frameAt(frames.startMillis(target) + elapsed / 1e6);
                drift.add(barcode.read() - expected);
            }
            playback.pause();
        }
        if (barcodeMode) {
            System.out.printf("onset to play (n=%d): first new frame %s; after %d s, shown minus wall-clock frame %s%n",
                    n, percentiles(firstAdvance), playMillis / 1000, intSummary(drift));
        } else {
            System.out.printf("onset to play (n=%d): not measured without a barcode%n", n);
        }
    }

    private void rapidScrub(final int bursts, final int seeksPerBurst) throws Exception {
        if (!barcodeMode) {
            // The final frame is often already on screen when the last request returns, so
            // waiting for the picture to change would measure nothing.
            System.out.println("rapid scrub: not measured without a barcode");
            return;
        }
        Result result = new Result();
        for (int b = 0; b < bursts; b++) {
            int frame = randomFrames(1).get(0);
            int step = Math.max(1, (int) frames.framesPerSecond() / 3);
            for (int i = 0; i < seeksPerBurst; i++) {
                frame = Math.max(0, Math.min(frames.frameCount() - 1, frame + (random.nextBoolean() ? step : -step)));
                playback.seek(frames.reportedMillis(frame));
                Thread.sleep(16);
            }
            long start = System.nanoTime();
            result.add(awaitShown(frame, start, barcodeMode ? 0 : pictureHash()), checkShown(frame));
        }
        report(String.format("rapid scrub (%d bursts of %d seeks 16 ms apart), from last request", bursts,
                seeksPerBurst), result);
    }

    private void steps(final int n) throws Exception {
        Result forward = new Result();
        Result backward = new Result();
        for (int i = 0; i < n; i++) {
            int frame = Math.max(1, Math.min(frames.frameCount() - 2, randomFrames(1).get(0)));
            playback.seek(frames.reportedMillis(frame));
            awaitShown(frame, System.nanoTime(), 0);

            long start = System.nanoTime();
            long before = barcodeMode ? 0 : pictureHash();
            playback.stepForward();
            forward.add(awaitShown(frame + 1, start, before), checkShown(frame + 1));

            start = System.nanoTime();
            before = barcodeMode ? 0 : pictureHash();
            playback.stepBackward();
            backward.add(awaitShown(frame, start, before), checkShown(frame));
        }
        report("step forward", forward);
        report("step backward", backward);
    }

    private void endOfFile() throws Exception {
        int last = frames.frameCount() - 1;
        long start = System.nanoTime();
        long before = barcodeMode ? 0 : pictureHash();
        playback.seek(playback.durationMillis());
        long latency = awaitShown(last, start, before);
        Integer error = checkShown(last);
        System.out.printf("end-of-file seek: %s%s%n", latency < 0 ? "timed out" : ms(latency),
                error == null ? "" : error == 0 ? ", shows the last frame" : ", off by " + error + " frames");
    }

    private void playbackCpu(final int seconds) throws Exception {
        com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        Stage stage = JavaFxToolkit.onFx(() -> {
            MediaView view = new MediaView(engine.player());
            view.setFitWidth(960);
            view.setPreserveRatio(true);
            Stage window = new Stage();
            window.setTitle("PlaybackBenchmark");
            window.setScene(new Scene(new Group(view)));
            window.show();
            return window;
        }).get();
        playback.seek(0);
        Thread.sleep(1_000);

        double pausedCpu = cpuShare(os, 5_000);
        int startFrame = barcodeMode ? barcode.read() : -1;
        playback.play();
        long start = System.nanoTime();
        double playingCpu = cpuShare(os, seconds * 1_000L);
        int endFrame = barcodeMode ? barcode.read() : -1;
        long elapsed = System.nanoTime() - start;
        playback.pause();
        JavaFxToolkit.onFx(() -> {
            stage.close();
            return null;
        }).get();

        System.out.printf("playback CPU at 1x for %d s in a 960 px window: %.0f%% of one core (paused: %.0f%%)%n",
                seconds, playingCpu * 100, pausedCpu * 100);
        if (barcodeMode) {
            double expected = elapsed / 1e9 * frames.framesPerSecond();
            System.out.printf("playback progress: %d frames in %.1f s (wall clock expects %.0f)%n",
                    endFrame - startFrame, elapsed / 1e9, expected);
        }
    }

    private static double cpuShare(final com.sun.management.OperatingSystemMXBean os, final long millis)
            throws InterruptedException {
        long cpu0 = os.getProcessCpuTime();
        long wall0 = System.nanoTime();
        Thread.sleep(millis);
        return (double) (os.getProcessCpuTime() - cpu0) / (System.nanoTime() - wall0);
    }

    private long pictureHash() throws Exception {
        return JavaFxToolkit.onFx(() -> {
            WritableImage image = picture.snapshot(null, null);
            PixelReader pixels = image.getPixelReader();
            long hash = 17;
            for (int y = 0; y < (int) image.getHeight(); y += 4) {
                for (int x = 0; x < (int) image.getWidth(); x += 4) {
                    hash = hash * 31 + (pixels.getArgb(x, y) & 0xf0f0f0);
                }
            }
            return hash;
        }).get(10, TimeUnit.SECONDS);
    }

    private List<Integer> randomFrames(final int n) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(random.nextInt(frames.frameCount()));
        }
        return result;
    }

    private static void report(final String name, final Result result) {
        StringBuilder line = new StringBuilder(String.format("%s (n=%d): %s", name, result.latencies.size(),
                percentiles(result.latencies)));
        if (result.unchecked == 0) {
            int exact = result.errors.getOrDefault(0, 0);
            line.append(String.format("; exact %d/%d", exact, result.latencies.size()));
            if (exact != result.latencies.size()) {
                line.append(", displayed-minus-requested frames ").append(result.errors);
            }
        }
        System.out.println(line);
    }

    private static String percentiles(final List<Long> nanos) {
        List<Long> done = new ArrayList<>();
        int timeouts = 0;
        for (long n : nanos) {
            if (n < 0) {
                timeouts++;
            } else {
                done.add(n);
            }
        }
        if (done.isEmpty()) {
            return "all " + timeouts + " timed out";
        }
        Collections.sort(done);
        String summary = String.format("p50 %s, p95 %s, max %s", ms(percentile(done, 50)), ms(percentile(done, 95)),
                ms(done.get(done.size() - 1)));
        return timeouts == 0 ? summary : summary + ", " + timeouts + " timed out (> " + TIMEOUT_MILLIS + " ms)";
    }

    private static long percentile(final List<Long> sorted, final int p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static String intSummary(final List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return String.format("median %+d, range %+d to %+d", sorted.get(sorted.size() / 2), sorted.get(0),
                sorted.get(sorted.size() - 1));
    }

    private static String ms(final long nanos) {
        return String.format("%.0f ms", nanos / 1e6);
    }
}
