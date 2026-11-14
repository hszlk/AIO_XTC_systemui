package com.android.systemui.statusbar.phone;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Screen capture + fast blur helper for the "fake glass" pull-down background.
 *
 * Why this exists: the device runs Android 8.1 (API 27).  That platform has no
 * compositor blur (no RenderEffect / no background blur radius / FLAG_BLUR_BEHIND
 * is ignored since API 14), so a real-time blurred backdrop of the window below
 * can only be faked by capturing the screen and blurring it ourselves.
 *
 * Implementation notes for the weak hardware (1 GB RAM, Cortex-A53 class):
 *   - capture directly at a small target size (SurfaceControl.screenshot can
 *     scale), so no full-size bitmap ever exists in our heap;
 *   - blur with two separable box-blur passes on the small bitmap (integer
 *     pixels, no RenderScript init cost, no per-frame allocations);
 *   - the captured small bitmap is drawn stretched by the glass view, which
 *     hides the low resolution while looking like frosted glass.
 *
 * The screenshot call is a hidden framework API, reached through reflection so
 * the app keeps compiling against the public SDK.  SystemUI holds
 * READ_FRAME_BUFFER in the manifest, which is what the platform checks.
 */
public final class ScreenBlur {

    private static final String TAG = "ScreenBlur";

    private ScreenBlur() {
    }

    /**
     * Captures the current screen into a blurred bitmap of roughly
     * {@code width x height} pixels.  Returns null when capture is unavailable
     * (e.g. secure window on top) - callers must keep a fallback background.
     */
    public static Bitmap captureAndBlur(int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        Bitmap shot = capture(width, height);
        if (shot == null) {
            return null;
        }
        // SurfaceControl.screenshot() returns an IMMUTABLE bitmap (and its
        // config may be RGB_565 on some panels).  Bitmap.setPixels() throws
        // IllegalStateException on such bitmaps, which used to crash SystemUI
        // on the first pull-down - so always repaint the capture into a fresh
        // mutable ARGB_8888 bitmap of exactly the wanted size.
        Bitmap argb = null;
        try {
            argb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(argb);
            android.graphics.Paint paint = new android.graphics.Paint(
                    android.graphics.Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(shot, null,
                    new android.graphics.Rect(0, 0, width, height), paint);
        } catch (Throwable t) {
            Log.w(TAG, "cannot copy capture into a mutable bitmap: " + t);
            argb = null;
        } finally {
            shot.recycle();
        }
        if (argb == null) {
            return null;
        }
        try {
            // two separable box blur passes approximate a gaussian closely enough;
            // radius 3 keeps the frame readable while the glass zoom shows the edges
            boxBlur(argb, 2);
            boxBlur(argb, 2);
        } catch (Throwable t) {
            Log.w(TAG, "blur failed: " + t);
            argb.recycle();
            return null;
        }
        return argb;
    }

    private static Bitmap capture(int width, int height) {
        // SurfaceControl.screenshot overloads across releases:
        //   screenshot(Rect, int, int, int), screenshot(int, int, int), screenshot(int, int)
        try {
            Class<?> sc = Class.forName("android.view.SurfaceControl");
            try {
                Method m = sc.getMethod("screenshot", Rect.class, int.class, int.class, int.class);
                Object bmp = m.invoke(null, null, width, height, 0);
                if (bmp instanceof Bitmap) {
                    return (Bitmap) bmp;
                }
            } catch (NoSuchMethodException ignored) {
            }
            try {
                Method m = sc.getMethod("screenshot", int.class, int.class, int.class);
                Object bmp = m.invoke(null, width, height, 0);
                if (bmp instanceof Bitmap) {
                    return (Bitmap) bmp;
                }
            } catch (NoSuchMethodException ignored) {
            }
            try {
                Method m = sc.getMethod("screenshot", int.class, int.class);
                Object bmp = m.invoke(null, width, height);
                if (bmp instanceof Bitmap) {
                    return (Bitmap) bmp;
                }
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Throwable t) {
            Log.w(TAG, "screen capture unavailable: " + t);
        }
        return null;
    }

    /** In-place separable box blur (two passes: horizontal then vertical). */
    private static void boxBlur(Bitmap bitmap, int radius) {
        if (radius <= 0 || bitmap == null || bitmap.isRecycled() || !bitmap.isMutable()) {
            return;
        }
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        final int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        final int[] tmp = new int[w * h];
        // horizontal
        int div = radius * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int rSum = 0, gSum = 0, bSum = 0;
            for (int i = -radius; i <= radius; i++) {
                int x = clamp(i, w);
                int c = pixels[row + x];
                rSum += (c >> 16) & 0xFF;
                gSum += (c >> 8) & 0xFF;
                bSum += c & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                tmp[row + x] = 0xFF000000 | ((rSum / div) << 16) | ((gSum / div) << 8) | (bSum / div);
                int addX = clamp(x + radius + 1, w);
                int subX = clamp(x - radius, w);
                int add = pixels[row + addX];
                int sub = pixels[row + subX];
                rSum += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                gSum += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                bSum += (add & 0xFF) - (sub & 0xFF);
            }
        }
        // vertical
        for (int x = 0; x < w; x++) {
            int rSum = 0, gSum = 0, bSum = 0;
            for (int i = -radius; i <= radius; i++) {
                int y = clamp(i, h);
                int c = tmp[y * w + x];
                rSum += (c >> 16) & 0xFF;
                gSum += (c >> 8) & 0xFF;
                bSum += c & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                pixels[y * w + x] = 0xFF000000 | ((rSum / div) << 16) | ((gSum / div) << 8) | (bSum / div);
                int addY = clamp(y + radius + 1, h);
                int subY = clamp(y - radius, h);
                int add = tmp[addY * w + x];
                int sub = tmp[subY * w + x];
                rSum += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                gSum += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                bSum += (add & 0xFF) - (sub & 0xFF);
            }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    private static int clamp(int v, int size) {
        if (v < 0) {
            return 0;
        }
        return v >= size ? size - 1 : v;
    }
}
