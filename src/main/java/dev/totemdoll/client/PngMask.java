package dev.totemdoll.client;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Reads PNG pixel opacity, including indexed/palette PNGs with tRNS transparency. */
public final class PngMask {
    public final int width;
    public final int height;
    private final boolean[] opaque;

    private PngMask(int width, int height, boolean[] opaque) {
        this.width = width;
        this.height = height;
        this.opaque = opaque;
    }

    public boolean opaque(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && opaque[y * width + x];
    }

    public static PngMask load(InputStream in) throws IOException {
        BufferedImage image = ImageIO.read(in);
        if (image == null) throw new IOException("Unsupported or invalid PNG image");
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0 || (long) width * height > 16_777_216L) {
            throw new IOException("Invalid PNG dimensions: " + width + "x" + height);
        }
        boolean[] opaque = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xFF;
                opaque[y * width + x] = alpha >= 128;
            }
        }
        return new PngMask(width, height, opaque);
    }
}
