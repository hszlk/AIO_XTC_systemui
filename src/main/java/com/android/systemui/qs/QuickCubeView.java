package com.android.systemui.qs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * The "quick cube" block of the pull-down: a white pill card that holds the four
 * row-1 shortcut balls (Wi-Fi / mobile data / Bluetooth / hotspot) in a 2x2 grid.
 *
 * Background = the reference pill UI ({@link PillBackground}); children are the
 * regular {@link GlassTileView} balls, added by {@link QSPanel}.
 */
public class QuickCubeView extends LinearLayout {

    private final RectF mRect = new RectF();
    private float mRadius;

    public QuickCubeView(Context context) {
        this(context, null);
    }

    public QuickCubeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setWillNotDraw(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mRect.set(0f, 0f, w, h);
        mRadius = Math.min(w, h) * 0.26f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mRect.isEmpty()) {
            PillBackground.drawBoard(canvas, mRect, mRadius);
        }
    }
}
