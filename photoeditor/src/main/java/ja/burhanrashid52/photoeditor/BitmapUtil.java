package ja.burhanrashid52.photoeditor;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLException;
import android.opengl.GLSurfaceView;

import java.nio.IntBuffer;

import javax.microedition.khronos.opengles.GL10;

/**
 * <p>
 * Bitmap utility class to perform different transformation on bitmap
 * </p>
 *
 * @author <a href="https://github.com/burhanrashid52">Burhanuddin Rashid</a>
 * @version 0.1.2
 * @since 5/21/2018
 */
public class BitmapUtil {
    /**
     * Remove transparency in edited bitmap
     *
     * @param source edited image
     * @return bitmap without any transparency
     */
    static Bitmap removeTransparency(Bitmap source) {
        int firstX = 0, firstY = 0;
        int lastX = source.getWidth();
        int lastY = source.getHeight();
        int[] pixels = new int[source.getWidth() * source.getHeight()];
        source.getPixels(pixels, 0, source.getWidth(), 0, 0, source.getWidth(), source.getHeight());
        loop:
        for (int x = 0; x < source.getWidth(); x++) {
            for (int y = 0; y < source.getHeight(); y++) {
                if (pixels[x + (y * source.getWidth())] != Color.TRANSPARENT) {
                    firstX = x;
                    break loop;
                }
            }
        }
        loop:
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = firstX; x < source.getHeight(); x++) {
                if (pixels[x + (y * source.getWidth())] != Color.TRANSPARENT) {
                    firstY = y;
                    break loop;
                }
            }
        }
        loop:
        for (int x = source.getWidth() - 1; x >= firstX; x--) {
            for (int y = source.getHeight() - 1; y >= firstY; y--) {
                if (pixels[x + (y * source.getWidth())] != Color.TRANSPARENT) {
                    lastX = x;
                    break loop;
                }
            }
        }
        loop:
        for (int y = source.getHeight() - 1; y >= firstY; y--) {
            for (int x = source.getWidth() - 1; x >= firstX; x--) {
                if (pixels[x + (y * source.getWidth())] != Color.TRANSPARENT) {
                    lastY = y;
                    break loop;
                }
            }
        }
        return Bitmap.createBitmap(source, firstX, firstY, lastX - firstX, lastY - firstY);
    }

    /**
     * Save filter bitmap from {@link ImageFilterView}
     *
     * @param glSurfaceView surface view on which is image is drawn
     * @param gl            open gl source to read pixels from {@link GLSurfaceView}
     * @return save bitmap
     * @throws OutOfMemoryError error when system is out of memory to load and save bitmap
     */
    public static Bitmap createBitmapFromGLSurface(GLSurfaceView glSurfaceView, GL10 gl) throws OutOfMemoryError {
        int w = glSurfaceView.getWidth();
        int h = glSurfaceView.getHeight();
        int bitmapBuffer[] = new int[w * h];
        IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
        intBuffer.position(0);
        try {
            gl.glReadPixels(0, 0, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
            convertRgbaToArgbAndFlip(bitmapBuffer, w, h);
        } catch (GLException e) {
            return null;
        }
        return Bitmap.createBitmap(bitmapBuffer, w, h, Bitmap.Config.ARGB_8888);
    }

    public static Bitmap createBitmapFromGlFrameBuffer(int x, int y, int width, int height) throws OutOfMemoryError {
        int bitmapBuffer[] = new int[width * height];
        IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
        intBuffer.position(0);
        try {
            GLES20.glReadPixels(x, y, width, height, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
            convertRgbaToArgb(bitmapBuffer);
        } catch (GLException e) {
            return null;
        }
        return Bitmap.createBitmap(bitmapBuffer, width, height, Bitmap.Config.ARGB_8888);
    }

    private static void convertRgbaToArgbAndFlip(int []pixels, final int w, final int h) {
        int topHalfOffset;
        int bottomHalfOffset;
        for (int i = 0; i < (h / 2); ++i) {
            topHalfOffset = i * w;
            bottomHalfOffset = (h - i - 1) * w;
            for (int j = 0; j < w; ++j) {
                int topRgbaPixel = pixels[topHalfOffset + j];
                int bottomRgbaPixel = pixels[bottomHalfOffset + j];
                int topArgbPixel =
                        (topRgbaPixel & 0xff00ff00) // green
                                | ((topRgbaPixel << 16) & 0x00ff0000) // red
                                | ((topRgbaPixel >> 16) & 0xff); // blue
                int bottomArgbPixel =
                        (bottomRgbaPixel & 0xff00ff00) // green
                                | ((bottomRgbaPixel << 16) & 0x00ff0000) // red
                                | ((bottomRgbaPixel >> 16) & 0xff); // blue
                pixels[topHalfOffset + j] = bottomArgbPixel;
                pixels[bottomHalfOffset + j] = topArgbPixel;
            }
        }

        if ((h % 2) != 0) {
            int middleRowOffset = (h / 2) * w;
            for (int j = 0; j < w; ++j) {
                int rgbaPixel = pixels[middleRowOffset + j];
                int argbPixel =
                        (rgbaPixel & 0xff00ff00) // green
                                | ((rgbaPixel << 16) & 0x00ff0000) // red
                                | ((rgbaPixel >> 16) & 0xff); // blue
                pixels[middleRowOffset + j] = argbPixel;
            }
        }
    }

    private static void convertRgbaToArgb(int []pixels) {
        final int len = pixels.length;
        for (int i = 0; i < len; ++i) {
            int rgbaPixel = pixels[i];
            int topArgbPixel =
                    (rgbaPixel & 0xff00ff00) // green
                            | ((rgbaPixel << 16) & 0x00ff0000) // red
                            | ((rgbaPixel >> 16) & 0xff); // blue
            pixels[i] = topArgbPixel;
        }
    }

}
