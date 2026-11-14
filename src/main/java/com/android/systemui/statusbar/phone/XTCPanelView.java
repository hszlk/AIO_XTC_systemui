package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Pull-down panel host (notification_panel in super_status_bar.xml; content =
 * layout_pull_down: XtcStatusBarView header + QsOutCanScrollView(QSPanel)).
 *
 * Original APK logic (decompiled XTCPanelView, 26 KB): panel expansion state
 * machine (closed / opening / open / closing), window height hand-off to
 * StatusBarWindowManager, QS expansion (fling), back-gesture collapse, and the
 * scroll-view delegation.
 *
 * Current stage (user-driven UI step):
 *   - keeps the translucent look;
 *   - adds a "frosted glass" backdrop: before the panel becomes visible the
 *     screen underneath is captured and blurred (see {@link ScreenBlur}), so
 *     the panel looks like real glass; when a video plays below, the captured
 *     frame is a blurred video frame;
 *   - hosts the row layout built by {@link com.android.systemui.qs.QSPanel}
 *     (music card + brightness/volume + circular shortcuts).
 */
public class XTCPanelView extends FrameLayout {

    public interface PanelStateListener {
        /** @param expanded true when panel is fully shown */
        void onPanelExpandedChanged(boolean expanded);
    }

    private static java.lang.ref.WeakReference<XTCPanelView> sActive;
    private static final long ANIM_DURATION = 200L;
    /** Blur source resolution: 1/6 of the screen keeps the CPU cost tiny. */
    private static final int BLUR_DIVISOR = 6;

    private PanelStateListener mListener;
    private GlassBackgroundView mGlass;
    private boolean mHeaderPaddingReserved = true; // QSPanel.setup() already reserves the header padding
    private boolean mExpanded;
    private int mBarHeight;

    public XTCPanelView(Context context) {
        this(context, null);
    }

    public XTCPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.TRANSPARENT);
        setVisibility(INVISIBLE);
        setAlpha(0f);
        ensureGlassView();
    }

    private void ensureGlassView() {
        if (mGlass != null) {
            return;
        }
        mGlass = new GlassBackgroundView(getContext());
        addView(mGlass, 0, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setPanelStateListener(PanelStateListener listener) {
        mListener = listener;
        sActive = new java.lang.ref.WeakReference<>(this);
    }

    /** Collapses the live pull-down; used when the QS page is scrolled to the end. */
    /** The live pull-down host (FrameLayout) - used by the magnify overlay. */
    public static XTCPanelView getActive() {
        return sActive == null ? null : sActive.get();
    }

    public static boolean collapseActive() {
        XTCPanelView panel = sActive == null ? null : sActive.get();
        if (panel == null) {
            return false;
        }
        panel.collapse();
        return true;
    }

    /** Expands the live pull-down; used when the recording pill forwards a swipe. */
    public static boolean expandActive() {
        XTCPanelView panel = sActive == null ? null : sActive.get();
        if (panel == null) {
            return false;
        }
        panel.expand();
        return true;
    }

    public void setBarHeight(int barHeight) {
        mBarHeight = barHeight;
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    /** Captures + blurs the layer underneath; call while the panel is not visible. */
    /**
     * True when the capture came back as a black/empty frame (secure windows such
     * as the charging keyguard make SurfaceControl.screenshot return black);
     * the glass then falls back to its built-in dark gradient.
     */
    private static boolean isBlackFrame(android.graphics.Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            return true;
        }
        long sum = 0;
        int samples = 0;
        int stepX = Math.max(1, bitmap.getWidth() / 8);
        int stepY = Math.max(1, bitmap.getHeight() / 8);
        for (int y = 0; y < bitmap.getHeight(); y += stepY) {
            for (int x = 0; x < bitmap.getWidth(); x += stepX) {
                int c = bitmap.getPixel(x, y);
                sum += ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
                samples++;
            }
        }
        return samples == 0 || (sum / (samples * 3)) < 10;
    }

    /** Applies (or clears) the user-picked picture as the panel backdrop. */
    public void setCustomBackground(android.graphics.Bitmap bitmap) {
        ensureGlassView();
        if (mGlass != null) {
            mGlass.setBackgroundImage(bitmap);
        }
    }

    /** Restores the stored custom picture, if any. */
    public void loadCustomBackground() {
        try {
            java.io.File file = new java.io.File(getContext().getFilesDir(), "panel_bg.png");
            if (!file.exists()) {
                setCustomBackground(null);
                return;
            }
            android.graphics.Bitmap bitmap =
                    android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
            setCustomBackground(bitmap);
        } catch (Throwable t) {
            android.util.Log.w("XTCPanel", "load custom background failed: " + t);
        }
    }

    public void refreshGlassBackground() {
        ensureGlassView();
        Point screen = screenSize(getContext());
        int w = Math.max(32, screen.x / BLUR_DIVISOR);
        int h = Math.max(32, screen.y / BLUR_DIVISOR);
        android.graphics.Bitmap blurred = ScreenBlur.captureAndBlur(w, h);
        boolean black = isBlackFrame(blurred);
        if (black && blurred != null) {
            blurred.recycle();
            blurred = null;
        }
        android.util.Log.i("XTCPanel", "glass refresh " + w + "x" + h
                + " capture=" + (black ? "black" : "ok")
                + " frame=" + (blurred == null ? "fallback" : "blurred"));
        if (blurred != null) {
            mGlass.setBlurredBitmap(blurred);
        }
    }

    public void expand() {
        if (mExpanded) {
            return;
        }
        mExpanded = true;
        // capture before the panel is visible so the blurred frame really is
        // the content below the status bar window; a capture failure must never
        // keep the panel from opening (the glass view keeps its fallback tint)
        try {
            refreshGlassBackground();
            loadCustomBackground();
        } catch (Throwable ignored) {
        }
        try {
            setupContent();
        } catch (Throwable ignored) {
        }
        setContentExpanded(true);
        setVisibility(VISIBLE);
        animate().alpha(1f).setDuration(ANIM_DURATION)
                .setInterpolator(new DecelerateInterpolator()).start();
        if (mListener != null) {
            mListener.onPanelExpandedChanged(true);
        }
    }

    private static boolean sCollapseLocked;

    /** Locks/unlocks collapsing (used by the tile edit mode). */
    public static void setCollapseLocked(boolean locked) {
        sCollapseLocked = locked;
    }

    public static boolean isCollapseLocked() {
        return sCollapseLocked;
    }

    public void collapse() {
        if (sCollapseLocked) {
            return;
        }
        if (!mExpanded) {
            return;
        }
        mExpanded = false;
        setContentExpanded(false);
        animate().alpha(0f).setDuration(ANIM_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!mExpanded) {
                            setVisibility(INVISIBLE);
                        }
                    }
                }).start();
        if (mListener != null) {
            mListener.onPanelExpandedChanged(false);
        }
    }

    /** Enables/disables the marquee animation of the now-playing card. */
    private void setContentExpanded(boolean expanded) {
        View qs = findViewById(com.android.systemui.R.id.quick_settings_panel);
        if (qs instanceof com.android.systemui.qs.QSPanel) {
            ((com.android.systemui.qs.QSPanel) qs).setExpanded(expanded);
        }
    }

    /** Forwards a status update to the panel header view (XtcStatusBarView). */
    public void updateHeaderStatus(String time, int batteryLevel, boolean charging) {
        View header = findViewById(com.android.systemui.R.id.status_bar_view);
        if (header instanceof com.xtc.statusbar.XtcStatusBarView) {
            ((com.xtc.statusbar.XtcStatusBarView) header).updateStatus(time, batteryLevel, charging);
        }
    }

    /** Forwards connectivity state (Wi-Fi / airplane) to the header row. */
    public void updateHeaderConnectivity(boolean wifiEnabled, boolean airplaneMode) {
        View header = findViewById(com.android.systemui.R.id.status_bar_view);
        if (header instanceof com.xtc.statusbar.XtcStatusBarView) {
            com.xtc.statusbar.XtcStatusBarView bar = (com.xtc.statusbar.XtcStatusBarView) header;
            bar.setWifiEnabled(wifiEnabled);
            bar.setAirplaneMode(airplaneMode);
        }
    }

    /** Resets the QS page to the top so every pull-down starts from row 1. */
    private void scrollPageToTop(View scroll) {
        if (scroll instanceof android.widget.ScrollView) {
            ((android.widget.ScrollView) scroll).scrollTo(0, 0);
        }
    }

    /**
     * Makes the collapsed status-bar row slide away with the page content:
     * the scroll view is extended to the very top (marginTop 0) and the header
     * is translated up by the current scroll amount, so it rolls off the screen
     * as soon as the user scrolls the QS page.
     */
    private void attachStatusRowToPage() {
        View header = findViewById(com.android.systemui.R.id.status_bar_view);
        View scroll = findViewById(com.android.systemui.R.id.ocsv_root);
        if (header == null || !(scroll instanceof android.widget.ScrollView)) {
            return;
        }
        final android.widget.ScrollView sv = (android.widget.ScrollView) scroll;
        final View headerView = header;
        int screenH = screenSize(getContext()).y;
        // let the page start at the very top of the panel
        android.view.ViewGroup.LayoutParams lp = sv.getLayoutParams();
        if (lp != null) {
            boolean changed = false;
            if (lp.height != screenH) {
                lp.height = screenH;
                changed = true;
            }
            if (lp instanceof android.widget.FrameLayout.LayoutParams
                    && ((android.widget.FrameLayout.LayoutParams) lp).topMargin != 0) {
                ((android.widget.FrameLayout.LayoutParams) lp).topMargin = 0;
                changed = true;
            }
            if (changed) {
                sv.setLayoutParams(lp);
            }
        }
        // header must stay above the page while it is still visible
        headerView.bringToFront();
        headerView.setTranslationY(0f);
        // reserve the header height at the top of the page content so the page
        // starts below the row while it is still visible (it scrolls away with it)
        View qsPanel = findViewById(com.android.systemui.R.id.quick_settings_panel);
        if (qsPanel != null && !mHeaderPaddingReserved) {
            int headerH = Math.max(1, headerView.getHeight());
            if (headerH <= 1) {
                try {
                    headerH = getResources().getDimensionPixelSize(
                            com.android.systemui.R.dimen.xtc_status_bar_height);
                } catch (Throwable ignored) {
                    headerH = Math.round(14 * getResources().getDisplayMetrics().density);
                }
            }
            mHeaderPaddingReserved = true;
            qsPanel.setPadding(qsPanel.getPaddingLeft(),
                    headerH + qsPanel.getPaddingTop(),
                    qsPanel.getPaddingRight(), qsPanel.getPaddingBottom());
            android.util.Log.i("XTCPanel", "page top padding reserved headerH=" + headerH);
        }
        // slide it away proportionally to the scroll offset
        sv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY,
                    int oldScrollX, int oldScrollY) {
                float max = headerView.getHeight();
                float y = Math.max(-max, -scrollY);
                headerView.setTranslationY(y);
            }
        });
        android.util.Log.i("XTCPanel", "status row slides out with page, headerH="
                + headerView.getHeight());
    }

    /** Wires the QS content (brightness/volume/music card/circle grid). */
    public void setupContent() {
        // QSPanel.setup() re-applies its own padding, so it must run BEFORE the
        // header-height reservation; otherwise the status row overlaps the music
        // card (user report).
        View qs = findViewById(com.android.systemui.R.id.quick_settings_panel);
        if (qs instanceof com.android.systemui.qs.QSPanel) {
            ((com.android.systemui.qs.QSPanel) qs).setup();
        }
        attachStatusRowToPage();
        // The QS page is taller than the screen; give the outer scroll view a
        // fixed visible height (screen minus the header row) so it actually
        // scrolls instead of growing past the window and being clipped.
        View scroll = findViewById(com.android.systemui.R.id.ocsv_root);
        scrollPageToTop(scroll);
        if (!(scroll instanceof android.widget.ScrollView)
                || ((android.widget.ScrollView) scroll).getChildCount() != 1) {
            post(new Runnable() {
                @Override
                public void run() {
                    attachStatusRowToPage();
                }
            });
        }
        if (scroll != null) {
            Point screen = screenSize(getContext());
            int headerH = Math.round(14 * getResources().getDisplayMetrics().density);
            int visibleH = Math.max(headerH, screen.y - headerH);
            if (scroll.getLayoutParams() != null) {
                scroll.getLayoutParams().height = visibleH;
                scroll.requestLayout();
            }
        }
    }

    private static Point screenSize(Context ctx) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        Point p = new Point();
        if (wm != null) {
            wm.getDefaultDisplay().getSize(p);
        }
        return p;
    }
}
