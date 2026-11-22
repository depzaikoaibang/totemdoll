package dev.totemdoll.client;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Reads only the alpha channel of a PNG (8-bit RGBA / gray+alpha / RGB / gray / palette) as an opacity mask. */
public final class PngMask {
    public final int width, height;
    private final boolean[] opaque;

    private PngMask(int w, int h, boolean[] o) { width = w; height = h; opaque = o; }

    public boolean opaque(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && opaque[y * width + x];
    }

    public static PngMask load(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        byte[] sig = new byte[8];
        d.readFully(sig);
        int w = 0, h = 0, depth = 8, type = 6;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        byte[] trns = null;
        while (true) {
            int len;
            try { len = d.readInt(); } catch (java.io.EOFException e) { break; }
            byte[] t = new byte[4];
            d.readFully(t);
            String name = new String(t, java.nio.charset.StandardCharsets.US_ASCII);
            byte[] data = new byte[len];
            d.readFully(data);
            d.readInt(); // crc
            if (name.equals("IHDR")) {
                w = ((data[0] & 255) << 24) | ((data[1] & 255) << 16) | ((data[2] & 255) << 8) | (data[3] & 255);
                h = ((data[4] & 255) << 24) | ((data[5] & 255) << 16) | ((data[6] & 255) << 8) | (data[7] & 255);
                depth = data[8] & 255;
                type = data[9] & 255;
            } else if (name.equals("tRNS")) trns = data;
            else if (name.equals("IDAT")) idat.write(data);
            else if (name.equals("IEND")) break;
        }
        if (depth != 8) throw new IOException("Only 8-bit PNG supported for the 3D layer mask");
        int channels = type == 6 ? 4 : type == 4 ? 2 : type == 2 ? 3 : 1;
        int stride = w * channels;
        byte[] raw = new byte[(stride + 1) * h];
        Inflater inf = new Inflater();
        inf.setInput(idat.toByteArray());
        try {
            int off = 0;
            while (off < raw.length && !inf.finished()) {
                int n = inf.inflate(raw, off, raw.length - off);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                off += n;
            }
        } catch (DataFormatException e) {
            throw new IOException(e);
        } finally {
            inf.end();
        }
        byte[] prev = new byte[stride], cur = new byte[stride];
        boolean[] op = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            int f = raw[y * (stride + 1)] & 255;
            System.arraycopy(raw, y * (stride + 1) + 1, cur, 0, stride);
            for (int i = 0; i < stride; i++) {
                int a = i >= channels ? cur[i - channels] & 255 : 0;
                int b = prev[i] & 255;
                int c = i >= channels ? prev[i - channels] & 255 : 0;
                int x = cur[i] & 255;
                switch (f) {
                    case 1: x += a; break;
                    case 2: x += b; break;
                    case 3: x += (a + b) / 2; break;
                    case 4: {
                        int p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
                        x += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c);
                        break;
                    }
                    default: break;
                }
                cur[i] = (byte) x;
            }
            for (int x = 0; x < w; x++) {
                int alpha = 255;
                if (type == 6) alpha = cur[x * 4 + 3] & 255;
                else if (type == 4) alpha = cur[x * 2 + 1] & 255;
                else if (type == 3 && trns != null) { int idx = cur[x] & 255; alpha = idx < trns.length ? trns[idx] & 255 : 255; }
                op[y * w + x] = alpha > 0;
            }
            byte[] tmp = prev; prev = cur; cur = tmp;
        }
        return new PngMask(w, h, op);
    }
}
