package com.sh0sh1n.watavyu.dist;

import com.sh0sh1n.watavyu.views.AppIcon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Writes the app icon ({@link AppIcon}) as a macOS iconset, the folder of PNG images that {@code iconutil} turns into
 * the app bundle's {@code .icns} file (build.gradle.kts › Distribution).
 */
public final class IconsetWriter {

    /** The sizes, in points, an iconset holds, each at 1x and 2x. */
    private static final int[] POINTS = {16, 32, 128, 256, 512};

    private IconsetWriter() {
    }

    /**
     * Writes the iconset.
     *
     * @param args the iconset folder, ending in {@code .iconset}
     * @throws IOException if an image cannot be written
     */
    public static void main(final String[] args) throws IOException {
        Path iconset = Path.of(args[0]);
        Files.createDirectories(iconset);
        for (int points : POINTS) {
            write(iconset.resolve("icon_" + points + "x" + points + ".png"), points);
            write(iconset.resolve("icon_" + points + "x" + points + "@2x.png"), points * 2);
        }
    }

    private static void write(final Path file, final int pixels) throws IOException {
        if (!ImageIO.write(AppIcon.image(pixels), "png", file.toFile())) {
            throw new IOException("No PNG writer for " + file);
        }
    }
}
