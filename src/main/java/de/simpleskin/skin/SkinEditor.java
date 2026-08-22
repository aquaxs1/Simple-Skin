package de.simpleskin.skin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Pixel operations on a skin PNG.
 *
 * <p>Deliberately built on {@link BufferedImage} rather than Minecraft's {@code NativeImage}:
 * everything here is pure byte-in/byte-out, so it runs off the render thread and is covered by
 * the regression suite without a game instance.
 */
public final class SkinEditor {
    /** Every skin is normalised to the modern 64x64 layout before editing. */
    public static final int SIZE = 64;

    private SkinEditor() {
    }

    /** The second-layer regions of a 64x64 skin, in texture coordinates. */
    public enum Layer {
        HAT("Hat", 32, 0, 32, 16),
        JACKET("Jacket", 16, 32, 24, 16),
        RIGHT_SLEEVE("Right sleeve", 40, 32, 16, 16),
        LEFT_SLEEVE("Left sleeve", 48, 48, 16, 16),
        RIGHT_LEG_LAYER("Right trouser", 0, 32, 16, 16),
        LEFT_LEG_LAYER("Left trouser", 0, 48, 16, 16);

        private final String label;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        Layer(String label, int x, int y, int width, int height) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public String label() {
            return label;
        }
    }

    /** The head region, including its hat overlay. */
    private static final int HEAD_X = 0;
    private static final int HEAD_Y = 0;
    private static final int HEAD_WIDTH = 64;
    private static final int HEAD_HEIGHT = 16;

    /** Clears a second-layer region, so the overlay stops being drawn. */
    public static byte[] clearLayer(byte[] png, Layer layer) throws IOException {
        BufferedImage image = read(png);
        for (int x = layer.x; x < layer.x + layer.width; x++) {
            for (int y = layer.y; y < layer.y + layer.height; y++) {
                image.setRGB(x, y, 0);
            }
        }
        return write(image);
    }

    /** True when a second-layer region has any non-transparent pixel left. */
    public static boolean hasLayer(byte[] png, Layer layer) throws IOException {
        BufferedImage image = read(png);
        for (int x = layer.x; x < layer.x + layer.width; x++) {
            for (int y = layer.y; y < layer.y + layer.height; y++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Copies the head (and its hat layer) of {@code donor} onto {@code base}. */
    public static byte[] swapHead(byte[] base, byte[] donor) throws IOException {
        BufferedImage target = read(base);
        BufferedImage source = read(donor);
        for (int x = HEAD_X; x < HEAD_X + HEAD_WIDTH; x++) {
            for (int y = HEAD_Y; y < HEAD_Y + HEAD_HEIGHT; y++) {
                target.setRGB(x, y, source.getRGB(x, y));
            }
        }
        return write(target);
    }

    /**
     * Replaces every pixel matching {@code from} with {@code to}.
     *
     * @param tolerance how far each colour channel may differ and still count as a match, 0 for
     *     an exact match. Alpha is compared exactly so transparent pixels are never painted in.
     */
    public static byte[] replaceColour(byte[] png, int from, int to, int tolerance) throws IOException {
        BufferedImage image = read(png);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int pixel = image.getRGB(x, y);
                if (matches(pixel, from, tolerance)) {
                    // Keep the pixel's own alpha: only the colour is being replaced.
                    image.setRGB(x, y, (pixel & 0xFF000000) | (to & 0x00FFFFFF));
                }
            }
        }
        return write(image);
    }

    private static boolean matches(int pixel, int target, int tolerance) {
        if ((pixel >>> 24) == 0) {
            return false;
        }
        int dr = Math.abs(((pixel >> 16) & 0xFF) - ((target >> 16) & 0xFF));
        int dg = Math.abs(((pixel >> 8) & 0xFF) - ((target >> 8) & 0xFF));
        int db = Math.abs((pixel & 0xFF) - (target & 0xFF));
        return dr <= tolerance && dg <= tolerance && db <= tolerance;
    }

    /** Reads the colour of one pixel, for the "pick a colour from the preview" flow. */
    public static int pixel(byte[] png, int x, int y) throws IOException {
        BufferedImage image = read(png);
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            throw new IOException("That pixel is outside the skin");
        }
        return image.getRGB(x, y);
    }

    /**
     * Decodes a skin, expanding the legacy 64x32 layout to 64x64 so every edit works on one
     * coordinate system.
     */
    static BufferedImage read(byte[] png) throws IOException {
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png));
        if (decoded == null) {
            throw new IOException("That file is not a readable PNG");
        }
        if (decoded.getWidth() != SIZE || (decoded.getHeight() != SIZE && decoded.getHeight() != SIZE / 2)) {
            throw new IOException("Minecraft skins must be 64x64 or legacy 64x32 PNG files");
        }
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < decoded.getWidth(); x++) {
            for (int y = 0; y < decoded.getHeight(); y++) {
                image.setRGB(x, y, decoded.getRGB(x, y));
            }
        }
        if (decoded.getHeight() == SIZE / 2) {
            expandLegacy(image);
        }
        return image;
    }

    /**
     * Mirrors the right arm and leg of a 64x32 skin into the 64x64 left-limb slots.
     *
     * <p>The offsets are Minecraft's own, copied from {@code SkinTextureDownloader#processLegacySkin}
     * so an imported legacy skin ends up byte-identical to one the game converts itself.
     */
    private static void expandLegacy(BufferedImage image) {
        copyMirrored(image, 4, 16, 16, 32, 4, 4);
        copyMirrored(image, 8, 16, 16, 32, 4, 4);
        copyMirrored(image, 0, 20, 24, 32, 4, 12);
        copyMirrored(image, 4, 20, 16, 32, 4, 12);
        copyMirrored(image, 8, 20, 8, 32, 4, 12);
        copyMirrored(image, 12, 20, 16, 32, 4, 12);
        copyMirrored(image, 44, 16, -8, 32, 4, 4);
        copyMirrored(image, 48, 16, -8, 32, 4, 4);
        copyMirrored(image, 40, 20, 0, 32, 4, 12);
        copyMirrored(image, 44, 20, -8, 32, 4, 12);
        copyMirrored(image, 48, 20, -16, 32, 4, 12);
        copyMirrored(image, 52, 20, -8, 32, 4, 12);
    }

    /** Copies a rect by {@code (dx, dy)}, flipping it horizontally, matching vanilla's copyRect. */
    private static void copyMirrored(BufferedImage image, int fromX, int fromY, int dx, int dy,
            int width, int height) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(fromX + dx + width - 1 - x, fromY + dy + y, image.getRGB(fromX + x, fromY + y));
            }
        }
    }

    public static byte[] write(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", output)) {
            throw new IOException("The edited skin could not be encoded as PNG");
        }
        return output.toByteArray();
    }
}
