package com.android.systemui.qs;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * White "pill" glass background, matching the reference UI style from the
 * Automatic_brick_rescue_aio_apk project ({@code res/drawable/pill_bg.xml}):
 *
 * <pre>
 *   layer 1: fill #66FFFFFF + 1dp stroke #88FFFFFF, corner radius 24dp
 *   layer 2: diagonal gradient #33FFFFFF -> #00FFFFFF (inset 1dp)
 *   layer 3: top highlight gradient #22FFFFFF -> #00FFFFFF (inset 2dp)
 * </pre>
 *
 * Drew once per view into the cached RectF; the gradients are rebuilt only when
 * the bounds change, so onDraw stays allocation-free on the weak hardware.
 */
public final class PillBackground {

    private static int FILL = 0x24FFFFFF;
    private static int STROKE = 0xA6FFFFFF;
    /** Grey ("dark") variant of the white board, switched from the edit bar. */
    private static boolean sDark;
    private static int sVersion;
    private static final int HIGHLIGHT_START = 0x33FFFFFF;
    private static final int HIGHLIGHT_END = 0x00FFFFFF;
    private static final int TOP_START = 0x22FFFFFF;
    private static final int TOP_END = 0x00FFFFFF;

    private static final Paint FILL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint STROKE_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint HIGHLIGHT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TOP_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF TEMP = new RectF();
    private static final Paint RIM_GLOW_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static float sRadius;
    private static float sStrokeWidth;
    private static float sLeft = -1f, sTop = -1f, sRight = -1f, sBottom = -1f;

    static {
        FILL_PAINT.setColor(FILL);
        STROKE_PAINT.setStyle(Paint.Style.STROKE);
        STROKE_PAINT.setColor(STROKE);
    }

    private PillBackground() {
    }

    /** Switches every pill between the white board and the grey (dark) board. */
    public static void setDarkMode(boolean dark) {
        if (sDark == dark) {
            return;
        }
        sDark = dark;
        FILL = dark ? 0x4DA0A0A0 : 0x24FFFFFF;
        STROKE = dark ? 0xA6C8C8C8 : 0xA6FFFFFF;
        FILL_PAINT.setColor(FILL);
        STROKE_PAINT.setColor(STROKE);
        sVersion++;
    }

    public static boolean isDarkMode() {
        return sDark;
    }

    /** Bumped on every theme change so views can rebuild their gradients. */
    public static int getThemeVersion() {
        return sVersion;
    }

    private static final int BOARD_FILL = 0x552A2A2A;
    private static final int BOARD_STROKE = 0x99FFFFFF;
    private static final int BOARD_SHEEN = 0x33FFFFFF;
    private static final int BOARD_SHEEN_END = 0x00FFFFFF;
    private static final Paint BOARD_FILL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BOARD_STROKE_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BOARD_SHEEN_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF BOARD_TEMP = new RectF();

    /**
     * The board card for the quick cube and the music card:
     *   light mode (default) = WHITE board, opaque (#FFFFFF) with a hairline
     *   grey stroke - "不开暗色要白底";
     *   dark mode = the reference button_bg board (translucent dark #552A2A2A,
     *   white 1dp stroke, top sheen).
     */
    public static void drawBoard(Canvas canvas, RectF rect, float radius) {
        if (rect.isEmpty()) {
            return;
        }
        float strokeWidth = Math.max(1f, Math.round(1f
                * android.content.res.Resources.getSystem().getDisplayMetrics().density));
        if (!sDark) {
            // light mode: white glass - clearly white, yet still translucent
            BOARD_FILL_PAINT.setColor(0x73FFFFFF);
            BOARD_STROKE_PAINT.setStyle(Paint.Style.STROKE);
            BOARD_STROKE_PAINT.setStrokeWidth(strokeWidth);
            BOARD_STROKE_PAINT.setColor(0xA6FFFFFF);
            canvas.drawRoundRect(rect, radius, radius, BOARD_FILL_PAINT);
            canvas.drawRoundRect(rect, radius, radius, BOARD_STROKE_PAINT);
            BOARD_TEMP.set(rect.left + strokeWidth, rect.top + strokeWidth,
                    rect.right - strokeWidth, rect.bottom - strokeWidth);
            BOARD_SHEEN_PAINT.setShader(new LinearGradient(0f, BOARD_TEMP.top, 0f,
                    BOARD_TEMP.bottom, 0x40FFFFFF, 0x00FFFFFF,
                    Shader.TileMode.CLAMP));
            canvas.drawRoundRect(BOARD_TEMP, Math.max(0f, radius - strokeWidth),
                    Math.max(0f, radius - strokeWidth), BOARD_SHEEN_PAINT);
            BOARD_SHEEN_PAINT.setShader(null);
            return;
        }
        int fill = 0x552A2A2A;
        int strokeColor = 0x99FFFFFF;
        BOARD_FILL_PAINT.setColor(fill);
        BOARD_STROKE_PAINT.setStyle(Paint.Style.STROKE);
        BOARD_STROKE_PAINT.setStrokeWidth(strokeWidth);
        BOARD_STROKE_PAINT.setColor(strokeColor);
        canvas.drawRoundRect(rect, radius, radius, BOARD_FILL_PAINT);
        canvas.drawRoundRect(rect, radius, radius, BOARD_STROKE_PAINT);
        // top-to-bottom white sheen, exactly like button_bg.xml
            BOARD_TEMP.set(rect.left + strokeWidth, rect.top + strokeWidth,
                    rect.right - strokeWidth, rect.bottom - strokeWidth);
            BOARD_SHEEN_PAINT.setShader(new LinearGradient(0f, BOARD_TEMP.top, 0f,
                    BOARD_TEMP.bottom, BOARD_SHEEN, BOARD_SHEEN_END,
                    Shader.TileMode.CLAMP));
            canvas.drawRoundRect(BOARD_TEMP, Math.max(0f, radius - strokeWidth),
                    Math.max(0f, radius - strokeWidth), BOARD_SHEEN_PAINT);
        BOARD_SHEEN_PAINT.setShader(null);
    }

    /** Draws the pill style into {@code rect} (must be called from onDraw). */
    public static void draw(Canvas canvas, RectF rect, float radius) {
        if (rect.isEmpty()) {
            return;
        }
        if (radius < 0f) {
            radius = 0f;
        }
        float strokeWidth = Math.max(1f, rect.height() * 0.012f);
        if (rect.left != sLeft || rect.top != sTop || rect.right != sRight
                || rect.bottom != sBottom || radius != sRadius
                || strokeWidth != sStrokeWidth) {
            sLeft = rect.left;
            sTop = rect.top;
            sRight = rect.right;
            sBottom = rect.bottom;
            sRadius = radius;
            sStrokeWidth = strokeWidth;
            STROKE_PAINT.setStrokeWidth(strokeWidth);
        }
        // layer 1: frosted fill + rim
        canvas.drawRoundRect(rect, radius, radius, FILL_PAINT);
        canvas.drawRoundRect(rect, radius, radius, STROKE_PAINT);

        // layer 2: diagonal light - bright at the top-left edge, fading to the
        // bottom-right corner (user requirement: every glass surface)
        TEMP.set(rect.left + strokeWidth, rect.top + strokeWidth,
                rect.right - strokeWidth, rect.bottom - strokeWidth);
        HIGHLIGHT_PAINT.setShader(new LinearGradient(TEMP.left, TEMP.top,
                TEMP.right, TEMP.bottom, 0x66FFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(TEMP, Math.max(0f, radius - strokeWidth),
                Math.max(0f, radius - strokeWidth), HIGHLIGHT_PAINT);
        HIGHLIGHT_PAINT.setShader(null);

        // rim glow: the TOP-LEFT and BOTTOM-RIGHT edges are bright
        RIM_GLOW_PAINT.setStyle(Paint.Style.STROKE);
        RIM_GLOW_PAINT.setStrokeWidth(Math.max(1.5f, strokeWidth * 2.0f));
        // top-left rim brightest, fading out towards the bottom-right
        RIM_GLOW_PAINT.setShader(new LinearGradient(rect.left, rect.top,
                rect.right, rect.bottom,
                new int[]{0x9EFFFFFF, 0x3CFFFFFF, 0x12FFFFFF, 0x04000000},
                new float[]{0f, 0.24f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, radius, radius, RIM_GLOW_PAINT);
        RIM_GLOW_PAINT.setShader(null);

        // layer 3: bottom-right darkening (fully dark in the corner)
        TEMP.set(rect.left + strokeWidth * 2f, rect.top + strokeWidth * 2f,
                rect.right - strokeWidth * 2f, rect.bottom - strokeWidth * 2f);
        TOP_PAINT.setShader(new LinearGradient(TEMP.left, TEMP.top,
                TEMP.right, TEMP.bottom, 0x00000000, 0x44000000,
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(TEMP, Math.max(0f, radius - strokeWidth * 2f),
                Math.max(0f, radius - strokeWidth * 2f), TOP_PAINT);
        TOP_PAINT.setShader(null);
    }
}
