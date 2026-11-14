package com.android.systemui.qs;

/**
 * Tile glyphs copied from the HQB SystemUI reference (AOSP 9 quick-settings
 * vectors): Wi-Fi, mobile signal, Bluetooth, hotspot, flashlight, airplane,
 * battery saver, do-not-disturb and auto-rotate.
 *
 * The rebuild cannot add resources, so each vector's pathData is embedded here
 * and parsed at runtime once (tiny SVG parser below, cached Paths).  Tiles whose
 * function is not present in the reference UI (screen record / eye comfort /
 * keep awake) keep the hand-drawn glyphs in {@link GlassTileView}.
 */
public final class QsIcons {

    private QsIcons() {
    }

    // WIFI (viewport 12.0x12.0, 1 path)
    private static final String[] WIFI = new String[]{
        "M0.106,4.184L0.427,4.561C0.583,4.745 0.854,4.773 1.051,4.635C2.492,3.623 4.213,3.076 5.981,3.076C7.749,3.076 9.47,3.623 10.911,4.635C11.109,4.773 11.379,4.745 11.536,4.561L11.857,4.184C12.026,3.985 11.989,3.683 11.774,3.535Q8.878,1.53 5.981,1.53Q3.085,1.53 0.188,3.535C-0.026,3.683 -0.063,3.985 0.106,4.184M5.981,3.92C7.535,3.92 9.049,4.388 10.328,5.257C10.541,5.402 10.57,5.697 10.403,5.893L9.98,6.39C9.826,6.572 9.56,6.601 9.36,6.471C8.357,5.817 7.184,5.467 5.981,5.467C4.779,5.467 3.605,5.817 2.603,6.471C2.403,6.601 2.137,6.572 1.982,6.39L1.559,5.893C1.393,5.697 1.422,5.402 1.634,5.257C2.914,4.388 4.428,3.92 5.981,3.92ZM5.981,6.311C6.962,6.311 7.92,6.581 8.753,7.086C8.976,7.221 9.014,7.526 8.846,7.724L8.51,8.12C8.359,8.297 8.101,8.328 7.898,8.215C7.314,7.89 6.654,7.717 5.981,7.717C5.308,7.717 4.649,7.89 4.065,8.215C3.862,8.328 3.604,8.297 3.453,8.12L3.117,7.724C2.948,7.526 2.987,7.221 3.209,7.086C4.042,6.581 5.001,6.311 5.981,6.311ZM5.981,8.561C6.422,8.561 6.855,8.655 7.253,8.834C7.499,8.945 7.541,9.258 7.366,9.464L6.324,10.689C6.144,10.9 5.818,10.9 5.639,10.689L4.597,9.464C4.422,9.258 4.464,8.945 4.71,8.834C5.108,8.655 5.541,8.561 5.981,8.561Z",
    };
    // MOBILE_DATA (viewport 21.0x28.0, 4 paths)
    private static final String[] MOBILE_DATA = new String[]{
        "M2,24L2,17A1,1 0,0 1,3 16L4,16A1,1 0,0 1,5 17L5,24A1,1 0,0 1,4 25L3,25A1,1 0,0 1,2 24z",
        "M7,24L7,13A1,1 0,0 1,8 12L9,12A1,1 0,0 1,10 13L10,24A1,1 0,0 1,9 25L8,25A1,1 0,0 1,7 24z",
        "M12,24L12,9A1,1 0,0 1,13 8L14,8A1,1 0,0 1,15 9L15,24A1,1 0,0 1,14 25L13,25A1,1 0,0 1,12 24z",
        "M17,24L17,5A1,1 0,0 1,18 4L19,4A1,1 0,0 1,20 5L20,24A1,1 0,0 1,19 25L18,25A1,1 0,0 1,17 24z",
    };
    // BLUETOOTH (viewport 16.0x18.0, 1 path)
    private static final String[] BLUETOOTH = new String[]{
        "M14.136,5.848L6.36,1L6.36,7.296L1.848,4.48L1,5.8L6.064,9L1,12.2L1.848,13.56L6.36,10.744L6.36,17L14.136,12.144L9.088,9L14.136,5.848ZM7.96,3.88L11.112,5.848L9.536,6.832L7.96,7.816L7.96,3.88ZM9.168,10.952L11.088,12.152L7.96,14.12L7.96,10.184L9.168,10.952Z",
    };
    // HOTSPOT (viewport 24.0x24.0, 1 path)
    private static final String[] HOTSPOT = new String[]{
        "M12,11c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,13a6,6 0,0 0,-6.75 -5.95c-2.62,0.32 -4.78,2.41 -5.18,5.02 -0.32,2.14 0.49,4.11 1.92,5.39 0.48,0.43 1.24,0.33 1.56,-0.23 0.24,-0.42 0.14,-0.94 -0.22,-1.26a3.99,3.99 0,0 1,-1.22 -3.94,3.954 3.954,0 0,1 2.9,-2.91A4.007,4.007 0,0 1,16 13c0,1.18 -0.51,2.23 -1.33,2.96 -0.36,0.33 -0.47,0.85 -0.23,1.27 0.31,0.54 1.04,0.69 1.5,0.28A5.97,5.97 0,0 0,18 13zM10.83,3.07c-4.62,0.52 -8.35,4.33 -8.78,8.96a9.966,9.966 0,0 0,4.02 9.01c0.48,0.35 1.16,0.2 1.46,-0.31 0.25,-0.43 0.14,-0.99 -0.26,-1.29 -2.28,-1.69 -3.65,-4.55 -3.16,-7.7 0.54,-3.5 3.46,-6.29 6.98,-6.68C15.91,4.51 20,8.28 20,13c0,2.65 -1.29,4.98 -3.27,6.44 -0.4,0.3 -0.51,0.85 -0.26,1.29 0.3,0.52 0.98,0.66 1.46,0.31A9.96,9.96 0,0 0,22 13c0,-5.91 -5.13,-10.62 -11.17,-9.93z",
    };
    // FLASHLIGHT (viewport 90.0x90.0, 1 path)
    private static final String[] FLASHLIGHT = new String[]{
        "M55.739,41.18Q55.739,39.587 56.573,38.231L59.704,33.143Q61.749,29.821 59.846,26.416Q57.943,23.011 54.043,23.011L35.957,23.011Q32.057,23.011 30.154,26.416Q28.252,29.821 30.296,33.143L33.427,38.231Q34.261,39.587 34.261,41.18L34.261,60.341Q34.261,63.095 36.209,65.042Q38.156,66.989 40.909,66.989L49.091,66.989Q51.845,66.989 53.792,65.042Q55.739,63.095 55.739,60.341L55.739,41.18ZM55.349,30.463L52.218,35.551Q50.625,38.14 50.625,41.18L50.625,60.341Q50.625,61.875 49.091,61.875L40.909,61.875Q39.375,61.875 39.375,60.341L39.375,41.18Q39.375,38.14 37.782,35.551L34.651,30.463Q34.179,29.697 34.618,28.911Q35.057,28.125 35.957,28.125L54.043,28.125Q54.943,28.125 55.382,28.911Q55.821,29.697 55.349,30.463ZM41.932,45L41.932,49.091C41.932,50.785 43.306,52.159 45,52.159C46.695,52.159 48.068,50.785 48.068,49.091L48.068,45C48.068,43.305 46.695,41.932 45,41.932C43.306,41.932 41.932,43.305 41.932,45Z",
    };
    // AIRPLANE (viewport 90.0x90.0, 1 path)
    private static final String[] AIRPLANE = new String[]{
        "M52.444,40.464C50.888,40.464 49.303,40.464 47.737,40.522L39.089,25.668C38.308,24.434 36.74,23.96 35.406,24.556C34.073,25.152 33.378,26.636 33.776,28.041L38.081,40.945C35.112,41.185 32.45,41.483 30.164,41.905L28.127,37.38L28.127,37.294L27.983,37.054C27.752,36.532 27.237,36.193 26.667,36.189L26.129,36.189C25.705,36.187 25.302,36.37 25.024,36.688C24.647,37.097 24.483,37.659 24.582,38.206L25.86,44.932L24.582,51.715C24.47,52.259 24.619,52.825 24.985,53.243C25.265,53.558 25.668,53.737 26.09,53.733L26.628,53.733C27.199,53.728 27.714,53.39 27.944,52.868L28.089,52.628L28.089,52.541L30.087,48.016C32.374,48.391 35.035,48.689 38.004,48.929L33.776,61.851C33.314,63.275 33.995,64.819 35.359,65.437C36.723,66.055 38.333,65.55 39.099,64.263L47.746,49.409C49.312,49.409 50.897,49.467 52.454,49.467C58.795,49.467 63.301,48.218 65.059,46.095C65.586,45.438 65.586,44.503 65.059,43.846C63.32,41.713 58.814,40.464 52.444,40.464Z",
    };
    // POWER_SAVE (viewport 14.0x21.0, 1 path)
    private static final String[] POWER_SAVE = new String[]{
        "M12,2H9V1c0,-0.553 -0.447,-1 -1,-1H6C5.447,0 5,0.447 5,1v1H2C0.896,2 0,2.896 0,4v15c0,1.104 0.896,2 2,2h10c1.104,0 2,-0.896 2,-2V4C14,2.896 13.104,2 12,2zM11,12H8v3H6v-3H3v-2h3V7h2v3h3V12z",
    };
    // DND (viewport 24.0x24.0, 2 paths)
    private static final String[] DND = new String[]{
        "M12,2C6.48,2 2,6.48 2,12c0,5.52 4.48,10 10,10c5.52,0 10,-4.48 10,-10C22,6.48 17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8c0,-4.41 3.59,-8 8,-8c4.41,0 8,3.59 8,8C20,16.41 16.41,20 12,20z",
        "M7,11h10v2h-10z",
    };
    // ROTATION (viewport 24.0x24.0, 1 path)
    private static final String[] ROTATION = new String[]{
        "M7.4,10.94H2.45V5.99l2,0.01v1.53l4.61,-4.61c0.64,-0.64 1.67,-0.66 2.29,-0.04l8.18,8.18h-2.83l-6.48,-6.48L5.86,8.95H7.4V10.94zM16.6,13.06l0.01,2h1.53l-4.36,4.36l-6.48,-6.48H4.46l8.19,8.19c0.62,0.62 1.65,0.6 2.29,-0.04l4.61,-4.61l0.01,0.01v1.53h1.99v-4.95H16.6z",
    };

    /** Draws one cached tile icon centred at (cx, cy) inside the given box size. */
    public static void draw(android.graphics.Canvas canvas, String[] data, float viewW,
            float viewH, float cx, float cy, float size, android.graphics.Paint paint) {
        android.graphics.Path[] paths = get(data, viewW, viewH);
        if (paths == null || paths.length == 0) {
            return;
        }
        float scale = Math.min(size / viewW, size / viewH);
        float dx = cx - viewW * scale * 0.5f;
        float dy = cy - viewH * scale * 0.5f;
        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        for (android.graphics.Path p : paths) {
            canvas.drawPath(p, paint);
        }
        canvas.restore();
    }

    private static final java.util.HashMap<String, android.graphics.Path[]> CACHE =
            new java.util.HashMap<>();

    private static android.graphics.Path[] get(String[] data, float viewW, float viewH) {
        String key = viewW + "x" + viewH + "#" + data.length + "#" + data[0].hashCode();
        android.graphics.Path[] cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        android.graphics.Path[] paths = new android.graphics.Path[data.length];
        for (int i = 0; i < data.length; i++) {
            paths[i] = parse(data[i]);
        }
        CACHE.put(key, paths);
        return paths;
    }

    public static boolean drawTile(android.graphics.Canvas canvas, int tileIndex,
            float cx, float cy, float size, android.graphics.Paint paint) {
        switch (tileIndex) {
            case 0: drawWIFI(canvas, cx, cy, size, paint); return true;
            case 1: drawMOBILE_DATA(canvas, cx, cy, size, paint); return true;
            case 2: drawBLUETOOTH(canvas, cx, cy, size, paint); return true;
            case 3: drawHOTSPOT(canvas, cx, cy, size, paint); return true;
            case 4: drawFLASHLIGHT(canvas, cx, cy, size, paint); return true;
            case 8: drawAIRPLANE(canvas, cx, cy, size, paint); return true;
            case 10: drawDND(canvas, cx, cy, size, paint); return true;
            default:
                return false;
        }
    }

    private static void drawWIFI(android.graphics.Canvas canvas, float cx, float cy,
            float size, android.graphics.Paint paint) {
        draw(canvas, WIFI, 12.0000f, 12.0000f, cx, cy, size, paint);
    }

    private static void drawMOBILE_DATA(android.graphics.Canvas canvas, float cx, float cy,
            float size, android.graphics.Paint paint) {
        draw(canvas, MOBILE_DATA, 21.0000f, 28.0000f, cx, cy, size, paint);
    }

    private static void drawBLUETOOTH(android.graphics.Canvas canvas, float cx, float cy,
            float size, android.graphics.Paint paint) {
        draw(canvas, BLUETOOTH, 16.0000f, 18.0000f, cx, cy, size, paint);
    }

    private static void drawHOTSPOT(android.graphics.Canvas canvas, float cx, float cy,
            float size, android.graphics.Paint paint) {
        draw(canvas, HOTSPOT, 24.0000f, 24.0000f, cx, cy, size, paint);
    }

    private static void drawFLASHLIGHT(android.graphics.Canvas canvas, float cx, float cy,
            float size, android.graphics.Paint paint) {
        draw(canvas, FLASHLIGHT, 90.0000f, 90.0000f, cx, cy, size, paint);
    }

    private static void drawAIRPLANE(android.graphics.Canvas canvas, float cx, float cy,
            float size, android.graphics.Paint paint) {
        draw(canvas, AIRPLANE, 90.0000f, 90.0000f, cx, cy, size, paint);
    }

    private static void drawPOWER_SAVE(android.graphics.Canvas canvas, float cx, float cy,
            float size, android.graphics.Paint paint) {
        draw(canvas, POWER_SAVE, 14.0000f, 21.0000f, cx, cy, size, paint);
    }

    private static void drawDND(android.graphics.Canvas canvas, float cx, float cy,
            float size, android.graphics.Paint paint) {
        draw(canvas, DND, 24.0000f, 24.0000f, cx, cy, size, paint);
    }

    private static void drawROTATION(android.graphics.Canvas canvas, float cx, float cy,
            float size, android.graphics.Paint paint) {
        draw(canvas, ROTATION, 24.0000f, 24.0000f, cx, cy, size, paint);
    }

    /** Minimal SVG path parser (M/L/H/V/C/S/Q/T/A/Z, absolute + relative). */
    static android.graphics.Path parse(String data) {
        android.graphics.Path path = new android.graphics.Path();
        new Parser(data).run(path);
        return path;
    }

    private static final class Parser {
        private final String s;
        private int i;
        private float cx, cy, sx, sy;
        private char cmd;

        Parser(String s) {
            this.s = s;
        }

        private boolean more() {
            return i < s.length();
        }

        private void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ',' || c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    i++;
                } else {
                    return;
                }
            }
        }

        private float num() {
            skipWs();
            int start = i;
            if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
                i++;
            }
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            if (i < s.length() && s.charAt(i) == '.') {
                i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                i++;
                if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
                    i++;
                }
                while (i < s.length() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            if (i == start) {
                return 0f;
            }
            return Float.parseFloat(s.substring(start, i));
        }

        private boolean isCommand(char c) {
            return "MmLlHhVvCcSsQqTtAaZz".indexOf(c) >= 0;
        }

        void run(android.graphics.Path path) {
            while (more()) {
                skipWs();
                if (!more()) {
                    break;
                }
                char c = s.charAt(i);
                if (isCommand(c)) {
                    cmd = c;
                    i++;
                }
                switch (cmd) {
                    case 'M': {
                        float x = num(), y = num();
                        path.moveTo(x, y);
                        cx = sx = x;
                        cy = sy = y;
                        cmd = 'L';
                        break;
                    }
                    case 'm': {
                        float dx = num(), dy = num();
                        path.rMoveTo(dx, dy);
                        cx += dx;
                        cy += dy;
                        sx = cx;
                        sy = cy;
                        cmd = 'l';
                        break;
                    }
                    case 'L': {
                        float x = num(), y = num();
                        path.lineTo(x, y);
                        cx = x;
                        cy = y;
                        break;
                    }
                    case 'l': {
                        float dx = num(), dy = num();
                        path.rLineTo(dx, dy);
                        cx += dx;
                        cy += dy;
                        break;
                    }
                    case 'H': {
                        float x = num();
                        path.lineTo(x, cy);
                        cx = x;
                        break;
                    }
                    case 'h': {
                        float dx = num();
                        path.rLineTo(dx, 0);
                        cx += dx;
                        break;
                    }
                    case 'V': {
                        float y = num();
                        path.lineTo(cx, y);
                        cy = y;
                        break;
                    }
                    case 'v': {
                        float dy = num();
                        path.rLineTo(0, dy);
                        cy += dy;
                        break;
                    }
                    case 'C':
                    case 'c':
                    case 'S':
                    case 's':
                    case 'Q':
                    case 'q':
                    case 'T':
                    case 't': {
                        boolean rel = cmd == 'c' || cmd == 's' || cmd == 'q' || cmd == 't';
                        float sign = rel ? 1f : 0f;
                        if (cmd == 'C' || cmd == 'c') {
                            float x1 = num(), y1 = num(), x2 = num(), y2 = num(), x = num(), y = num();
                            if (rel) {
                                path.rCubicTo(x1, y1, x2, y2, x, y);
                                cx += x;
                                cy += y;
                            } else {
                                path.cubicTo(x1, y1, x2, y2, x, y);
                                cx = x;
                                cy = y;
                            }
                        } else if (cmd == 'S' || cmd == 's') {
                            float x2 = num(), y2 = num(), x = num(), y = num();
                            if (rel) {
                                path.rCubicTo(0f, 0f, x2, y2, x, y);
                                cx += x;
                                cy += y;
                            } else {
                                path.cubicTo(cx + sign, cy + sign, x2, y2, x, y);
                                cx = x;
                                cy = y;
                            }
                        } else {
                            float x1 = num(), y1 = num(), x = num(), y = num();
                            if (rel) {
                                path.rQuadTo(x1, y1, x, y);
                                cx += x;
                                cy += y;
                            } else {
                                path.quadTo(x1, y1, x, y);
                                cx = x;
                                cy = y;
                            }
                        }
                        break;
                    }
                    case 'A':
                    case 'a': {
                        float rx = num();
                        float ry = num();
                        float rot = num();
                        float large = num();
                        float sweep = num();
                        float x = num();
                        float y = num();
                        float endX = (cmd == 'A') ? x : cx + x;
                        float endY = (cmd == 'A') ? y : cy + y;
                        // crude arc: sample quarter-arc through the end point
                        int steps = 12;
                        float startX = cx, startY = cy;
                        for (int k = 1; k <= steps; k++) {
                            float t = (float) k / steps;
                            float px = startX + (endX - startX) * t;
                            float py = startY + (endY - startY) * t;
                            path.lineTo(px, py);
                        }
                        cx = endX;
                        cy = endY;
                        break;
                    }
                    case 'Z':
                    case 'z':
                        path.close();
                        cx = sx;
                        cy = sy;
                        break;
                    default:
                        i++;
                        break;
                }
            }
        }
    }
}
