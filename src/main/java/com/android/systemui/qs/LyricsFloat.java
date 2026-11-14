package com.android.systemui.qs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * "Desktop lyrics" style floating caption: white text with a black outline
 * (karaoke style), no background. Long-press (~450ms) starts a drag; a plain
 * tap hides it. The text is clamped to the screen.
 */
public final class LyricsFloat {

    private static WindowManager sWm;
    private static LyricView sView;
    private static boolean sShowing;

    private LyricsFloat() {
    }

    public static boolean isShowing() {
        return sShowing;
    }

    public static void toggle(Context context) {
        if (sShowing) {
            hide();
        } else {
            show(context);
        }
    }

    public static void show(Context context) {
        if (sShowing) {
            return;
        }
        Context app = context.getApplicationContext();
        sWm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (sWm == null) {
            return;
        }
        float sizePx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP, 7f,
                app.getResources().getDisplayMetrics());
        LyricView text = new LyricView(app, sizePx);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 40;
        lp.y = 120;
        lp.setTitle("XTCLyrics");
        text.setOnTouchListener(new View.OnTouchListener() {
            private float mDownRawX, mDownRawY;
            private int mStartX, mStartY;
            private boolean mMoved;
            private Runnable mDragStart = new Runnable() {
                @Override
                public void run() {
                    mMoved = true; // long-press arms the drag
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        mDownRawX = event.getRawX();
                        mDownRawY = event.getRawY();
                        mStartX = lp.x;
                        mStartY = lp.y;
                        mMoved = false;
                        v.postDelayed(mDragStart, 450L);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (!mMoved) {
                            return true; // not armed yet: plain scroll under it
                        }
                        lp.x = clampX(v, mStartX + Math.round(event.getRawX() - mDownRawX));
                        lp.y = clampY(v, mStartY + Math.round(event.getRawY() - mDownRawY));
                        try {
                            sWm.updateViewLayout(v, lp);
                        } catch (Throwable ignored) {
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        v.removeCallbacks(mDragStart);
                        if (!mMoved) {
                            hide(); // a plain tap hides the caption
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.removeCallbacks(mDragStart);
                        return true;
                    default:
                        return true;
                }
            }
        });
        try {
            sWm.addView(text, lp);
            sView = text;
            sShowing = true;
        } catch (Throwable ignored) {
        }
    }

    public static void hide() {
        if (sView != null && sWm != null) {
            try {
                sWm.removeView(sView);
            } catch (Throwable ignored) {
            }
        }
        sView = null;
        sShowing = false;
    }

    private static int clampX(View view, int x) {
        int screenW = view.getResources().getDisplayMetrics().widthPixels;
        int w = Math.min(view.getWidth(), screenW);
        return Math.max(0, Math.min(x, screenW - w));
    }

    private static int clampY(View view, int y) {
        int screenH = view.getResources().getDisplayMetrics().heightPixels;
        return Math.max(0, Math.min(y, screenH - view.getHeight()));
    }

    /** Live text: current lyric line (title fallback when no lrc exists). */
    public static void updateText(String line) {
        if (sView != null && line != null) {
            sView.setText(line);
        }
    }

    /** White fill + black outline caption. */
    private static final class LyricView extends View {
        private final Paint mFill;
        private final Paint mStroke;
        private final Paint.FontMetrics mFm;
        private String mText = "歌词";

        LyricView(Context c, float sizePx) {
            super(c);
            mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            mFill.setColor(Color.WHITE);
            mFill.setTypeface(Typeface.DEFAULT_BOLD);
            mFill.setTextSize(sizePx);
            mStroke = new Paint(mFill);
            mStroke.setColor(Color.BLACK);
            mStroke.setStyle(Paint.Style.STROKE);
            mStroke.setStrokeWidth(sizePx * 0.32f);
            mStroke.setStrokeJoin(Paint.Join.ROUND);
            mStroke.setStrokeCap(Paint.Cap.ROUND);
            mFm = mFill.getFontMetrics();
        }

        void setText(String t) {
            if (t == null || t.equals(mText)) {
                return;
            }
            mText = t;
            requestLayout();
            invalidate();
        }

        String text() {
            return mText;
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            float w = mStroke.measureText(mText) + mStroke.getStrokeWidth();
            int h = (int) Math.ceil(mFm.bottom - mFm.top + mStroke.getStrokeWidth());
            setMeasuredDimension((int) Math.ceil(w), h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float baseline = getHeight() * 0.5f - (mFm.ascent + mFm.descent) * 0.5f;
            float x = mStroke.getStrokeWidth() * 0.5f;
            canvas.drawText(mText, x, baseline, mStroke);
            canvas.drawText(mText, x, baseline, mFill);
        }
    }
}
