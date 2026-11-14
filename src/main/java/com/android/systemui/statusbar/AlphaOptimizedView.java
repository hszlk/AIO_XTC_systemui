package com.android.systemui.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * FrameLayout that declares it has no overlapping rendering, letting the
 * framework skip the expensive alpha layer path.
 *
 * Original APK: {@code com.android.systemui.statusbar.AlphaOptimizedView} -
 * "An optimized FrameLayout that returns false from
 * hasOverlappingRendering()", used as the heads-up scrim host in
 * super_status_bar.xml.
 *
 * Reimplementation: same contract, framework-only.
 */
public class AlphaOptimizedView extends FrameLayout {

    public AlphaOptimizedView(Context context) {
        super(context);
    }

    public AlphaOptimizedView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AlphaOptimizedView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
