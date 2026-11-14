package com.android.systemui.qs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Pull-down content panel, laid out following the user's latest mock:
 *
 * <pre>
 *   +----------------------------------------+
 *   |              now playing               |   <- full-width white pill card
 *   +----------------------------------------+
 *   +---------+  +----+  +----+
 *   |  quick  |  | 亮 |  | 音 |                <- quick cube (2x2 balls)
 *   |  cube   |  | 度 |  | 量 |
 *   +---------+  +----+  +----+
 *     (O)   (O)   (O)   (O)      <- flashlight / record / eye / awake
 *     (O)   (O)   (O)   (O)      <- airplane / power save / DND / rotation
 * </pre>
 *
 * The first-row shortcuts (Wi-Fi / mobile data / Bluetooth / hotspot, tile
 * indices 0-3) live inside the {@link QuickCubeView}; the 8 round tiles below
 * use indices 4-11.  Music card + cube + seek bars all share the white pill
 * background from the reference UI.
 */
public class QSPanel extends LinearLayout {

    private static final int TILES_PER_ROW = 4;
    /** Side inset in dp: matches the header row's layout_marginStart (5dp). */
    private static final int SIDE_INSET_DP = 3;
    private static final int TOP_INSET_DP = 2;
    private static final int BOTTOM_INSET_DP = 4;
    private static final int GAP_DP = 2;

    private MusicCardView mMusicCard;
    private NowPlayingController mNowPlaying;
    private VerticalSeekBar mBrightnessBar;
    private VerticalSeekBar mVolumeBar;
    private BrightnessController mBrightnessController;
    private VolumeController mVolumeController;
    private QsToggles mToggles;
    /** slot -> tile function index (slot 0-3 = quick cube, 4-11 = the two rows). */
    private final int[] mSlotTile = new int[QsToggles.TILE_COUNT];
    private final GlassTileView[] mTiles = new GlassTileView[QsToggles.TILE_COUNT];
    private static final String PREFS = "qs_slot_order";
    private static final String KEY_ORDER = "order";
    private int mDragFromSlot = -1;
    private boolean mEditMode;
    private boolean mSignaturesPending;
    private VerticalSeekBar mMagnifiedBar;
    /** Volume magnify shows THREE streams: media | system | alarm (user spec). */
    private android.widget.LinearLayout mMagnifyVolumeTrio;
    private View mMagnifyScrim;
    private BoostBallView mBrightBoostBall;
    private BoostBallView mDarkBoostBall;
    private BoostBallView mMagnifyLeftBall;
    private BoostBallView mMagnifyRightBall;
    private android.view.WindowManager mOverlayWm;
    private View mOverlayView;
    private boolean mOverlayIsBoost;
    private int mSavedBrightness;
    private android.view.ViewGroup mMagnifyTarget;
    private LinearLayout mMagnifiedParent;
    private int mMagnifiedIndex;
    private boolean mDragVisualPaused;
    private boolean mDragWobbleStopped;
    private int mHoverSlot = -1;
    private int mHoverFromSlot = -1;
    private android.widget.TextView mSaveButton;
    private LinearLayout mExtraRow;
    private android.widget.TextView mDarkButton;
    private android.widget.TextView mBgButton;
    private boolean mDarkMode;
    private boolean mExpanded;
    private boolean mSetup;
    /** Bottom two shortcut rows + their geometry, needed by the edit-mode captions. */
    private LinearLayout mTileRowTop;
    private LinearLayout mTileRowBottom;
    private int mTileRowH;
    private int mTileDiameter;
    private int mTileRowGap;

    public QSPanel(Context context) {
        this(context, null);
    }

    public QSPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setBackgroundColor(Color.TRANSPARENT);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    public void setExpanded(boolean expanded) {
        mExpanded = expanded;
        if (mMusicCard != null) {
            mMusicCard.setActive(expanded);
        }
        // media discovery only runs while the panel is actually open
        if (mNowPlaying != null) {
            if (expanded) {
                mNowPlaying.resume();
            } else {
                mNowPlaying.pause();
            }
        }
    }

    /** Builds the panel content once; safe to call multiple times. */
    public void setup() {
        if (mSetup) {
            return;
        }
        mSetup = true;
        for (int i = 0; i < mSlotTile.length; i++) {
            mSlotTile[i] = i;
        }
        loadSlotOrder();
        final Context ctx = getContext();
        final float density = getResources().getDisplayMetrics().density;
        Point screen = screenSize(ctx);
        final int sideInset = Math.round(SIDE_INSET_DP * density);
        final int topInset = Math.round(TOP_INSET_DP * density);
        final int bottomInset = Math.round(BOTTOM_INSET_DP * density);
        final int gap = Math.round(GAP_DP * density);

        // The panel is intentionally TALLER than one screen: the outer
        // QsOutCanScrollView scrolls, so blocks keep comfortable sizes instead of
        // being squeezed to fit (user requirement).
        final int contentW = screen.x - sideInset * 2;
        final int musicH = Math.round(104 * density);
        final int midRowH = Math.round(80 * density);
        final int cellGapPx = Math.round(1.5f * density);
        final int cellWpx = contentW / TILES_PER_ROW;
        final int ballDiameter = Math.round(cellWpx * 0.92f) - cellGapPx;
        // rows hug the balls; the row gap equals the horizontal gap between balls
        final int tileRowH = ballDiameter;

        // reserve the collapsed status-row height at the top of the page (the row
        // stays above the page and slides away on scroll) - without this the row
        // overlaps the music card.
        int headerH = 0;
        try {
            headerH = getResources().getDimensionPixelSize(
                    com.android.systemui.R.dimen.xtc_status_bar_height);
        } catch (Throwable ignored) {
        }
        if (headerH <= 0) {
            headerH = Math.round(14 * density);
        }
        setPadding(sideInset, headerH + topInset, sideInset, bottomInset);
        android.util.Log.i("QSPanel", "page padding top=" + (headerH + topInset));

        // ---- row 1: full-width music card
        mMusicCard = new MusicCardView(ctx);
        addView(mMusicCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, musicH));
        mNowPlaying = new NowPlayingController(ctx, mMusicCard);

        // ---- row 2: quick cube (4 balls) + brightness + volume
        LinearLayout mid = new LinearLayout(ctx);
        mid.setOrientation(HORIZONTAL);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, midRowH);
        midLp.topMargin = gap;
        addView(mid, midLp);

        // quick cube must stay a perfect 1:1 square (user requirement)
        final int cubeW = midRowH;
        QuickCubeView cube = new QuickCubeView(ctx);
        cube.setPadding(gap, gap, gap, gap);
        LinearLayout.LayoutParams cubeLp = new LinearLayout.LayoutParams(cubeW, midRowH);
        cubeLp.rightMargin = gap;
        mid.addView(cube, cubeLp);
        cube.setClipChildren(false);
        cube.setClipToPadding(false);
        addCubeRow(ctx, cube, 0);
        addCubeRow(ctx, cube, 2);

        mBrightnessBar = new VerticalSeekBar(ctx);
        mBrightnessBar.setLabel("亮度");
        mBrightnessBar.setIcon(VerticalSeekBar.ICON_BRIGHTNESS);
        mBrightnessBar.setOnMagnifyListener(new VerticalSeekBar.OnMagnifyListener() {
            @Override
            public void onMagnifyRequested(VerticalSeekBar bar) {
                toggleMagnify(bar);
            }
        });
        LinearLayout.LayoutParams brightnessLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        brightnessLp.rightMargin = gap;
        mid.addView(mBrightnessBar, brightnessLp);

        mVolumeBar = new VerticalSeekBar(ctx);
        mVolumeBar.setLabel("音量");
        mVolumeBar.setIcon(VerticalSeekBar.ICON_VOLUME);
        // the bottom icon cycles mute / vibrate / ring / ring+vibrate (tappable)
        mVolumeBar.setRingerIconEnabled(true);
        mVolumeBar.setOnMagnifyListener(new VerticalSeekBar.OnMagnifyListener() {
            @Override
            public void onMagnifyRequested(VerticalSeekBar bar) {
                toggleMagnify(bar);
            }
        });
        mid.addView(mVolumeBar, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // ---- rows 3/4: 8 round tiles
        mToggles = new QsToggles(ctx);
        // vertical gap between the two rows == horizontal gap between balls (6px)
        final int rowGap = cellGapPx * 2;
        mTileRowH = tileRowH;
        mTileDiameter = ballDiameter;
        mTileRowGap = rowGap;
        mTileRowTop = buildTileRow(ctx, 4, contentW, tileRowH);
        mTileRowBottom = buildTileRow(ctx, 8, contentW, tileRowH);
        addView(mTileRowTop, rowParams(rowGap, tileRowH));
        addView(mTileRowBottom, rowParams(rowGap, tileRowH));

        // ---- controllers
        mBrightnessController = new BrightnessController(ctx);
        mVolumeController = new VolumeController(ctx);
        mBrightnessBar.setMax(mBrightnessController.getMax());
        mBrightnessBar.setOnChangeListener(new VerticalSeekBar.OnChangeListener() {
            @Override
            public void onProgressChanged(VerticalSeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    mBrightnessController.setBrightness(progress);
                }
            }
        });
        // yellow SAVE button shown only in the drag-edit mode
        mSaveButton = new android.widget.TextView(ctx);
        mSaveButton.setText("保存");
        mSaveButton.setTextColor(0xFF2B2B2B);
        mSaveButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
        mSaveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        mSaveButton.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable saveBg =
                new android.graphics.drawable.GradientDrawable();
        saveBg.setColor(0xFFFFD54F);
        saveBg.setCornerRadius(Math.round(16 * density));
        mSaveButton.setBackground(saveBg);
        mSaveButton.setVisibility(android.view.View.GONE);
        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitEditMode();
            }
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(30 * density));
        saveLp.topMargin = Math.round(8 * density);
        saveLp.leftMargin = Math.round(36 * density);
        saveLp.rightMargin = Math.round(36 * density);
        addView(mSaveButton, saveLp);

        // edit-mode extra row: [ dark ] [ background ]
        mExtraRow = new LinearLayout(ctx);
        mExtraRow.setOrientation(HORIZONTAL);
        mExtraRow.setVisibility(android.view.View.GONE);
        mDarkButton = makeBarButton(ctx, "暗色", 0xFF444444);
        mBgButton = makeBarButton(ctx, "背景", 0xFF9E9E9E);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        half.rightMargin = Math.round(4 * density);
        mExtraRow.addView(mDarkButton, half);
        mExtraRow.addView(mBgButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams extraLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(32 * density));
        extraLp.topMargin = Math.round(6 * density);
        extraLp.leftMargin = Math.round(36 * density);
        extraLp.rightMargin = Math.round(36 * density);
        addView(mExtraRow, extraLp);

        mDarkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDarkMode();
            }
        });
        mBgButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickCustomBackground();
            }
        });
        mBgButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                clearCustomBackground();
                return true;
            }
        });
        mDarkMode = getContext().getSharedPreferences("panel_theme", Context.MODE_PRIVATE)
                .getBoolean("dark", false);
        PillBackground.setDarkMode(mDarkMode);
        mDarkButton.setTextColor(mDarkMode ? 0xFF222222 : 0xFFFFFFFF);
        setButtonColor(mDarkButton, mDarkMode ? 0xFFE0E0E0 : 0xFF444444);

        mVolumeBar.setMax(mVolumeController.getMax());
        mVolumeBar.setOnChangeListener(new VerticalSeekBar.OnChangeListener() {
            @Override
            public void onProgressChanged(VerticalSeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    mVolumeController.setVolume(progress);
                }
            }
        });
    }

    /** Adds a 2-tile horizontal row (2x2 grid) into the quick cube. */
    private void addCubeRow(Context ctx, QuickCubeView cube, int firstSlot) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        cube.addView(row, rowLp);
        for (int i = 0; i < 2; i++) {
            final int slot = firstSlot + i;
            GlassTileView tile = createTile(ctx, slot);
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            row.addView(tile, cellLp);
            mTiles[slot] = tile;
        }
    }

    /** Creates a tile bound to a slot (function index comes from mSlotTile). */
    private GlassTileView createTile(Context ctx, final int slot) {
        GlassTileView tile = new GlassTileView(ctx);
        tile.setTileIndex(mSlotTile[slot]);
        tile.setOnTileClickListener(new GlassTileView.OnTileClickListener() {
            @Override
            public void onTileClick(int tileIndex) {
                handleTileClick(tileIndex);
            }
        });
        tile.setOnTileDragListener(new GlassTileView.OnTileDragListener() {
            @Override
            public void onTileLongPress(GlassTileView t) {
                if (mEditMode) {
                    // already editing: this long-press just grabs the ball
                    mDragFromSlot = slot;
                    startShakeAll(t);
                    return;
                }
                int tile = mSlotTile[slot];
                if (tile == QsToggles.TILE_WIFI) {
                    openSettings(android.provider.Settings.ACTION_WIFI_SETTINGS);
                    return;
                }
                if (tile == QsToggles.TILE_BLUETOOTH) {
                    openSettings(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                    return;
                }
                if (tile == QsToggles.TILE_SCREEN_RECORD) {
                    // user spec: long-press toggles the recording on/off
                    if (mToggles != null) {
                        mToggles.toggle(QsToggles.TILE_SCREEN_RECORD);
                    }
                    collapsePanel();
                    return;
                }
                mDragFromSlot = slot;
                enterEditMode(t);
            }

            @Override
            public void onTileDragMove(GlassTileView t, float totalDx, float totalDy) {
                if (!mDragWobbleStopped) {
                    mDragWobbleStopped = true;
                    for (GlassTileView view : mTiles) {
                        if (view != null && view != t) {
                            view.stopShake();
                            view.setTranslationX(0f);
                            view.setTranslationY(0f);
                        }
                    }
                }
                // a quick-cube ball only grows its caption once it is dragged
                // down into the shortcut rows - the label follows the ball
                if (slot < 4) {
                    t.setCaptionVisible(draggedIntoBottomRows(t));
                }
                updateShiftPreview(t, slot);
            }

            @Override
            public void onTileDragEnd(GlassTileView t, boolean cancelled) {
                mDragVisualPaused = false;
                mDragWobbleStopped = false;
                commitShift(slot, cancelled);
                if (mEditMode) {
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (mEditMode) {
                                for (GlassTileView view : mTiles) {
                                    if (view != null) {
                                        view.startShake();
                                    }
                                }
                            }
                        }
                    }, 160L);
                }
            }
        });
        return tile;
    }

    private LinearLayout buildTileRow(Context ctx, int firstSlot, int contentW, int rowH) {
        final float density = getResources().getDisplayMetrics().density;
        final int cellGap = Math.round(1.5f * density);
        final int cellW = contentW / TILES_PER_ROW;
        // keep the balls the same size (width-driven) even though the rows are shorter now
        final int diameter = Math.round(cellW * 0.92f) - cellGap;
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        for (int i = 0; i < TILES_PER_ROW; i++) {
            final int slot = firstSlot + i;
            FrameLayout cell = new FrameLayout(ctx);
            cell.setClipChildren(false);
            GlassTileView tile = createTile(ctx, slot);
            FrameLayout.LayoutParams tileLp = new FrameLayout.LayoutParams(diameter, diameter);
            tileLp.gravity = Gravity.CENTER;
            cell.addView(tile, tileLp);
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            cellLp.leftMargin = cellGap;
            cellLp.rightMargin = cellGap;
            row.addView(cell, cellLp);
            mTiles[slot] = tile;
        }
        return row;
    }

    private void handleTileClick(int tileIndex) {
        if (mEditMode) {
            return; // editing: shortcuts are not clickable, only draggable
        }
        if (mToggles != null) {
            mToggles.toggle(tileIndex);
        }
        // Flashlight and screen recording open their own UI/activity; collapse the
        // panel first so the overlay window does not cover them.
        if (tileIndex == QsToggles.TILE_FLASHLIGHT
                || tileIndex == QsToggles.TILE_SCREEN_RECORD) {
            collapsePanel();
        }
    }

    private LinearLayout.LayoutParams rowParams(int topGap, int rowH) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowH);
        lp.topMargin = topGap;
        return lp;
    }

    /** Walks up to the pull-down host and collapses it. */
    private void collapsePanel() {
        android.view.ViewParent parent = getParent();
        while (parent != null) {
            if (parent instanceof com.android.systemui.statusbar.phone.XTCPanelView) {
                ((com.android.systemui.statusbar.phone.XTCPanelView) parent).collapse();
                return;
            }
            parent = parent.getParent();
        }
    }

    private static Point screenSize(Context ctx) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        Point p = new Point();
        if (wm != null) {
            wm.getDefaultDisplay().getSize(p);
        }
        if (p.x == 0 || p.y == 0) {
            p.x = ctx.getResources().getDisplayMetrics().widthPixels;
            p.y = ctx.getResources().getDisplayMetrics().heightPixels;
        }
        return p;
    }

    /** Returns the QS scroll container (or null) so edit mode can freeze the page. */
    private static com.android.systemui.qs.QsOutCanScrollView findScrollContainer(android.view.View view) {
        android.view.ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof com.android.systemui.qs.QsOutCanScrollView) {
                return (com.android.systemui.qs.QsOutCanScrollView) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    /** Enters drag-edit mode: all tiles wobble, clicks are disabled. */
    private void enterEditMode(GlassTileView dragged) {
        mEditMode = true;
        // the page keeps scrolling while editing; only collapsing the whole
        // panel is blocked (so a stray swipe cannot drop edit mode)
        com.android.systemui.statusbar.phone.XTCPanelView.setCollapseLocked(true);
        for (GlassTileView view : mTiles) {
            if (view == null) {
                continue;
            }
            view.setEditMode(true);
            if (view != dragged) {
                view.startShake();
            }
        }
        setEditSpacing(true);
        updateCaptions();
        if (mSaveButton != null) {
            mSaveButton.setVisibility(android.view.View.VISIBLE);
        }
        if (mExtraRow != null) {
            mExtraRow.setVisibility(android.view.View.VISIBLE);
        }
        try {
            java.io.File bg = new java.io.File(getContext().getFilesDir(), "panel_bg.png");
            setButtonColor(mBgButton, bg.exists() ? 0xFFFFFFFF : 0xFF9E9E9E);
            if (mBgButton != null) {
                mBgButton.setTextColor(bg.exists() ? 0xFF222222 : 0xFFFFFFFF);
            }
        } catch (Throwable ignored) {
        }
        // while editing, brightness/volume must not react to touches
        if (mBrightnessBar != null) {
            mBrightnessBar.setEnabled(false);
            mBrightnessBar.setAlpha(0.45f);
        }
        if (mVolumeBar != null) {
            mVolumeBar.setEnabled(false);
            mVolumeBar.setAlpha(0.45f);
        }
        android.util.Log.i("QSPanel", "edit mode on");
    }

    /** Leaves drag-edit mode and persists the new order. */
    private void exitEditMode() {
        mEditMode = false;
        com.android.systemui.statusbar.phone.XTCPanelView.setCollapseLocked(false);
        for (GlassTileView view : mTiles) {
            if (view != null) {
                view.setEditMode(false);
                view.stopShake();
            }
        }
        setEditSpacing(false);
        updateCaptions();
        if (mSaveButton != null) {
            mSaveButton.setVisibility(android.view.View.GONE);
        }
        if (mExtraRow != null) {
            mExtraRow.setVisibility(android.view.View.GONE);
        }
        if (mBrightnessBar != null) {
            mBrightnessBar.setEnabled(true);
            mBrightnessBar.setAlpha(1f);
        }
        if (mVolumeBar != null) {
            mVolumeBar.setEnabled(true);
            mVolumeBar.setAlpha(1f);
        }
        saveSlotOrder();
        refreshTileViews();
        if (mToggles != null) {
            mToggles.refreshAll();
        }
        android.util.Log.i("QSPanel", "edit mode off, order saved");
    }

    private android.widget.TextView makeBarButton(android.content.Context ctx,
            String text, int color) {
        android.widget.TextView button = new android.widget.TextView(ctx);
        button.setText(text);
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(Math.round(14 * getResources().getDisplayMetrics().density));
        button.setBackground(bg);
        return button;
    }

    private void setButtonColor(android.widget.TextView button, int color) {
        if (button == null) {
            return;
        }
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(Math.round(14 * getResources().getDisplayMetrics().density));
        button.setBackground(bg);
    }

    /** Grey board <-> white board, without touching the layout. */
    private void toggleDarkMode() {
        mDarkMode = !mDarkMode;
        PillBackground.setDarkMode(mDarkMode);
        getContext().getSharedPreferences("panel_theme", Context.MODE_PRIVATE)
                .edit().putBoolean("dark", mDarkMode).apply();
        setButtonColor(mDarkButton, mDarkMode ? 0xFFE0E0E0 : 0xFF444444);
        mDarkButton.setTextColor(mDarkMode ? 0xFF222222 : 0xFFFFFFFF);
        refreshAllViews();
        android.util.Log.i("QSPanel", "dark mode " + mDarkMode);
    }

    private void refreshAllViews() {
        invalidateDeep(this);
        invalidate();
    }

    private void invalidateDeep(View view) {
        view.invalidate();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                invalidateDeep(group.getChildAt(i));
            }
        }
    }

    /** Starts the wobble on every tile except the one being dragged. */
    private void startShakeAll(GlassTileView dragged) {
        for (GlassTileView view : mTiles) {
            if (view != null && view != dragged) {
                view.startShake();
            }
        }
    }

    /**
     * True when a dragged quick-cube ball has been carried down onto the two
     * shortcut rows (it then shows its function name like the row balls).
     */
    private boolean draggedIntoBottomRows(GlassTileView dragged) {
        if (!mAnchorsReady) {
            computeSlotAnchors();
        }
        if (!mAnchorsReady || mTileRowTop == null) {
            return false;
        }
        int[] loc = new int[2];
        mTileRowTop.getLocationInWindow(loc);
        int rowTop = loc[1];
        int[] dloc = new int[2];
        dragged.getLocationInWindow(dloc);
        return dloc[1] + dragged.getHeight() * 0.5f > rowTop;
    }

    /**
     * Edit-mode layout: the two shortcut rows spread apart so the function name
     * under each ball has room, and the bottom row keeps clear of the save bar.
     */
    private void setEditSpacing(boolean editing) {
        float density = getResources().getDisplayMetrics().density;
        int extra = Math.round(12 * density);
        int captionBand = Math.round(14 * density);
        int rowH = mTileRowH + (editing ? captionBand : 0);
        if (mTileRowTop != null && mTileRowTop.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mTileRowTop.getLayoutParams();
            lp.topMargin = mTileRowGap + (editing ? extra : 0);
            lp.height = rowH;
            mTileRowTop.setLayoutParams(lp);
        }
        if (mTileRowBottom != null
                && mTileRowBottom.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) mTileRowBottom.getLayoutParams();
            lp.topMargin = mTileRowGap + (editing ? extra : 0);
            lp.height = rowH;
            mTileRowBottom.setLayoutParams(lp);
        }
        // rows 4-11 get a taller tile view so the caption band lives inside it
        for (int slot = 4; slot < mTiles.length; slot++) {
            GlassTileView view = mTiles[slot];
            if (view == null || !(view.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
                continue;
            }
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) view.getLayoutParams();
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.width = mTileDiameter;
            lp.height = mTileDiameter + (editing ? captionBand : 0);
            view.setLayoutParams(lp);
        }
        requestLayout();
    }

    /**
     * Edit mode: every ball carries its function name. The quick-cube balls
     * (slots 0-3) only show theirs once they are dragged down to the bottom
     * rows - the cube is too tight for a caption and its own glyph is enough.
     */
    private void updateCaptions() {
        for (int slot = 0; slot < mSlotTile.length; slot++) {
            GlassTileView view = mTiles[slot];
            if (view == null) {
                continue;
            }
            view.setCaption(QsToggles.label(mSlotTile[slot]));
            boolean inBottomRows = slot >= 4;
            view.setCaptionVisible(mEditMode && inBottomRows);
        }
    }

    private void stopShakeAll() {
        for (GlassTileView view : mTiles) {
            if (view != null) {
                view.stopShake();
            }
        }
    }

    /** Opens a system settings page from the overlay window. */
    private void openSettings(String action) {
        try {
            android.content.Intent intent = new android.content.Intent(action);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
            getContext().startActivity(intent);
            collapsePanel();
        } catch (Throwable t) {
            android.util.Log.w("QSPanel", "cannot open settings " + action + ": " + t);
        }
    }

    // ------------------------------------------------------------------
    // Two-layer positioning: the slot anchors never move, the balls do.
    // Dropping a ball onto an occupied anchor pushes that ball into the
    // anchor the dragged ball came from.
    // ------------------------------------------------------------------

    private final float[][] mSlotAnchors = new float[QsToggles.TILE_COUNT][2];
    private boolean mAnchorsReady;
    private int mShiftedSlot = -1;

    /** Caches the fixed anchor (layout centre in window coordinates) of every slot. */
    private void computeSlotAnchors() {
        for (int slot = 0; slot < mTiles.length; slot++) {
            GlassTileView view = mTiles[slot];
            if (view == null || view.getWidth() == 0) {
                mAnchorsReady = false;
                return;
            }
            int[] loc = new int[2];
            view.getLocationInWindow(loc);
            mSlotAnchors[slot][0] = loc[0] + view.getWidth() * 0.5f
                    - view.getTranslationX();
            mSlotAnchors[slot][1] = loc[1] + view.getHeight() * 0.5f
                    - view.getTranslationY();
        }
        mAnchorsReady = true;
    }

    /** Anchor under the dragged ball (window coordinates), or -1. */
    private int anchorUnder(GlassTileView dragged, int fromSlot) {
        if (!mAnchorsReady) {
            computeSlotAnchors();
        }
        if (!mAnchorsReady) {
            return -1;
        }
        int[] loc = new int[2];
        dragged.getLocationInWindow(loc);
        float cx = loc[0] + dragged.getWidth() * 0.5f;
        float cy = loc[1] + dragged.getHeight() * 0.5f;
        float maxDist = dragged.getWidth() * 0.85f;
        int best = -1;
        float bestDist = maxDist * maxDist;
        for (int slot = 0; slot < mTiles.length; slot++) {
            if (slot == fromSlot || mTiles[slot] == null) {
                continue;
            }
            float dx = mSlotAnchors[slot][0] - cx;
            float dy = mSlotAnchors[slot][1] - cy;
            float dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                best = slot;
            }
        }
        return best;
    }

    /** Live preview: the ball of the overlapped anchor slides into the source anchor. */
    private void updateShiftPreview(GlassTileView dragged, int fromSlot) {
        int hit = anchorUnder(dragged, fromSlot);
        if (hit == mShiftedSlot) {
            return;
        }
        if (mShiftedSlot >= 0 && mShiftedSlot < mTiles.length
                && mTiles[mShiftedSlot] != null) {
            mTiles[mShiftedSlot].animate().translationX(0f).translationY(0f)
                    .setDuration(90L).start();
        }
        mShiftedSlot = hit;
        if (hit >= 0 && mTiles[hit] != null) {
            float dx = mSlotAnchors[fromSlot][0] - mSlotAnchors[hit][0];
            float dy = mSlotAnchors[fromSlot][1] - mSlotAnchors[hit][1];
            mTiles[hit].animate().translationX(dx).translationY(dy)
                    .setDuration(110L).start();
        }
    }

    /** Commits the push: swap the two slots, settle every ball home, refresh glyphs. */
    private void commitShift(int fromSlot, boolean cancelled) {
        int hit = mShiftedSlot;
        mShiftedSlot = -1;
        boolean swapped = false;
        if (!cancelled && hit >= 0 && hit != fromSlot && hit < mSlotTile.length) {
            int tmp = mSlotTile[fromSlot];
            mSlotTile[fromSlot] = mSlotTile[hit];
            mSlotTile[hit] = tmp;
            swapped = true;
            saveSlotOrder();
            android.util.Log.i("QSPanel", "slot swapped: " + fromSlot + " <-> " + hit
                    + " tiles now " + mSlotTile[fromSlot] + "/" + mSlotTile[hit]);
        } else {
            android.util.Log.i("QSPanel", "no swap: from=" + fromSlot + " hit=" + hit
                    + " cancelled=" + cancelled);
        }
        // settle instantly - no cross-flying animation on drop
        for (GlassTileView view : mTiles) {
            if (view != null) {
                view.animate().cancel();
                view.setTranslationX(0f);
                view.setTranslationY(0f);
            }
        }
        if (swapped) {
            refreshTileViews();
        }
        // hard refresh again once the settle animation is over: glyphs must always
        // match the final slot mapping (this also covers late-layout cases)
        postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshTileViews();
                for (GlassTileView view : mTiles) {
                    if (view != null) {
                        view.invalidate();
                    }
                }
            }
        }, 220L);
        if (mToggles != null) {
            mToggles.refreshAll();
        }
        mAnchorsReady = false;
    }

    /** Re-applies slot -> tile mapping to every view and its on-state. */
    private void refreshTileViews() {
        for (int slot = 0; slot < mSlotTile.length; slot++) {
            GlassTileView view = mTiles[slot];
            if (view == null) {
                continue;
            }
            view.setTileIndex(mSlotTile[slot]);
            if (mToggles != null) {
                view.setOn(mToggles.isOn(mSlotTile[slot]));
            }
        }
        if (mEditMode) {
            updateCaptions();
        }
    }

    /** The view that carries the given tile identity. */
    private GlassTileView viewForTile(int tileIndex) {
        for (GlassTileView view : mTiles) {
            if (view != null && view.getTileIndex() == tileIndex) {
                return view;
            }
        }
        return null;
    }

    /**
     * Moves every ball to the visual position of the slot it currently occupies
     * (layout positions never change, only the translation does).
     */
    private void applySlotTranslations(boolean animate) {
        if (!mAnchorsReady) {
            computeSlotAnchors();
        }
        if (!mAnchorsReady) {
            return;
        }
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        for (int slot = 0; slot < mSlotTile.length; slot++) {
            GlassTileView view = viewForTile(mSlotTile[slot]);
            if (view == null || view.getWidth() == 0 || view.getHeight() == 0) {
                continue;
            }
            int[] loc = new int[2];
            view.getLocationInWindow(loc);
            float layoutX = loc[0] + view.getWidth() * 0.5f;
            float layoutY = loc[1] + view.getHeight() * 0.5f;
            float dx = mSlotAnchors[slot][0] - layoutX;
            float dy = mSlotAnchors[slot][1] - layoutY;
            // never throw a ball off the screen (guards against stale anchors)
            if (Math.abs(dx) > screenW || Math.abs(dy) > screenH * 1.5f) {
                dx = 0f;
                dy = 0f;
            }
            view.animate().cancel();
            if (animate) {
                view.animate().translationX(dx).translationY(dy)
                        .setDuration(160L).start();
            } else {
                view.setTranslationX(dx);
                view.setTranslationY(dy);
            }
        }
    }

    private void loadSlotOrder() {
        mSignaturesPending = true;
        android.content.SharedPreferences prefs = getContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_ORDER, null);
        if (stored == null || stored.isEmpty()) {
            return;
        }
        String[] parts = stored.split(",");
        boolean[] used = new boolean[mSlotTile.length];
        for (int i = 0; i < mSlotTile.length && i < parts.length; i++) {
            try {
                int v = Integer.parseInt(parts[i].trim());
                if (v >= 0 && v < mSlotTile.length && !used[v]) {
                    mSlotTile[i] = v;
                    used[v] = true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /** Called after the first layout: applies the persisted slot order. */
    private void applyLoadedOrder() {
        if (!mSignaturesPending) {
            return;
        }
        mSignaturesPending = false;
        post(new Runnable() {
            @Override
            public void run() {
                mAnchorsReady = false;
            }
        });
    }

    /** The persisted order must be a permutation of 0..11. */
    private boolean isValidOrder() {
        boolean[] seen = new boolean[mSlotTile.length];
        for (int value : mSlotTile) {
            if (value < 0 || value >= seen.length || seen[value]) {
                return false;
            }
            seen[value] = true;
        }
        return true;
    }

    private void saveSlotOrder() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mSlotTile.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(mSlotTile[i]);
        }
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ORDER, sb.toString()).apply();
    }

    /**
     * "Darker" mask, modelled on the reference night-screen app
     * (DC_夜间屏幕 / BlackBulb): a PURE BLACK oversized view whose View alpha is
     * the dimming amount - no grey tint, no blur, not touchable.
     */
    private void toggleScreenOverlay(boolean boost) {
        if (mOverlayView != null) {
            removeScreenOverlay();
            return;
        }
        android.content.Context ctx = getContext();
        if (mOverlayWm == null) {
            mOverlayWm = (android.view.WindowManager) ctx
                    .getSystemService(android.content.Context.WINDOW_SERVICE);
        }
        if (mOverlayWm == null) {
            return;
        }
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int oversize = Math.max(dm.widthPixels, dm.heightPixels) + 200;
        View overlay = new View(ctx);
        overlay.setBackgroundColor(0xFF000000);   // pure black, like the night screen
        overlay.setAlpha(0.80f);                   // dimming amount (View alpha)
        android.view.WindowManager.LayoutParams lp =
                new android.view.WindowManager.LayoutParams(
                        oversize, oversize,
                        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = android.view.Gravity.CENTER;
        lp.setTitle("XTCNightMask");
        try {
            mOverlayWm.addView(overlay, lp);
        } catch (Throwable t1) {
            android.util.Log.w("QSPanel", "night mask overlay failed: " + t1);
            return;
        }
        mOverlayView = overlay;
        mOverlayIsBoost = false;
        if (mMagnifyRightBall != null) {
            mMagnifyRightBall.setActive(true);
        }
        android.util.Log.i("QSPanel", "night mask on");
    }

    private void removeScreenOverlay() {
        if (mOverlayView != null && mOverlayWm != null) {
            final View view = mOverlayView;
            mOverlayView = null;
            view.animate().alpha(0f).setDuration(160L).start();
            mOverlayWm.removeView(view);
        }
        mOverlayIsBoost = false;
        if (mMagnifyRightBall != null) {
            mMagnifyRightBall.setActive(false);
        }
        android.util.Log.i("QSPanel", "night mask off");
    }

    /** Enlarges one brightness/volume bar over the whole panel, or restores it. */
    private void toggleMagnify(VerticalSeekBar source) {
        if (mMagnifiedBar != null || mMagnifyVolumeTrio != null) {
            restoreMagnify();
            return;
        }
        com.android.systemui.statusbar.phone.XTCPanelView panelHost =
                com.android.systemui.statusbar.phone.XTCPanelView.getActive();
        if (panelHost == null) {
            return;
        }
        final android.view.ViewGroup target = panelHost;
        mMagnifyTarget = panelHost;
        // The scrim must go in BEFORE the bars: ViewGroup dispatch gives the
        // topmost child the touch, so a full-screen scrim added afterwards would
        // swallow every drag and close the magnifier on the first move.
        View scrim = new View(getContext());
        scrim.setBackgroundColor(0x01000000);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restoreMagnify();
            }
        });
        target.addView(scrim, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        mMagnifyScrim = scrim;
        final boolean brightness = (source == mBrightnessBar);
        float density = getResources().getDisplayMetrics().density;
        if (brightness) {
            VerticalSeekBar bar = new VerticalSeekBar(getContext());
            bar.setIcon(VerticalSeekBar.ICON_BRIGHTNESS);
            bar.setMax(mBrightnessBar.getMax());
            bar.setProgress(mBrightnessBar.getProgress());
            bar.setOnChangeListener(new VerticalSeekBar.OnChangeListener() {
                @Override
                public void onProgressChanged(VerticalSeekBar b, int progress,
                        boolean fromUser) {
                    if (fromUser) {
                        mBrightnessController.setBrightness(progress);
                    }
                }
            });
            android.widget.FrameLayout.LayoutParams lp =
                    new android.widget.FrameLayout.LayoutParams(
                            Math.round(72 * density),
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
            lp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
            target.addView(bar, lp);
            mMagnifiedBar = bar;
            mBrightnessBar.setVisibility(android.view.View.INVISIBLE);

            // the dark ball only belongs to the brightness magnifier
            int ballSize = Math.round(26 * density);
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            int barW = Math.round(72 * density);
            int ballGap = Math.round(10 * density);
            int ballTop = (screenH - ballSize) / 2;
            mMagnifyRightBall = new BoostBallView(getContext());
            mMagnifyRightBall.setMode(BoostBallView.MODE_DARK);
            mMagnifyRightBall.setOnBoostClickListener(new BoostBallView.OnBoostClickListener() {
                @Override
                public void onBoostClick(BoostBallView v, int mode) {
                    toggleScreenOverlay(false);
                }
            });
            android.widget.FrameLayout.LayoutParams rightLp =
                    new android.widget.FrameLayout.LayoutParams(ballSize, ballSize);
            rightLp.gravity = android.view.Gravity.TOP | android.view.Gravity.RIGHT;
            rightLp.rightMargin = screenW / 2 - barW / 2 - ballGap - ballSize;
            rightLp.topMargin = ballTop;
            target.addView(mMagnifyRightBall, rightLp);
        } else {
            // volume magnify: media | system | alarm, three slim bars in a row,
            // each captioned so the user knows which stream it is
            android.media.AudioManager am = (android.media.AudioManager)
                    getContext().getSystemService(Context.AUDIO_SERVICE);
            android.widget.LinearLayout trio = new android.widget.LinearLayout(getContext());
            trio.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            trio.setGravity(android.view.Gravity.CENTER);
            int[] streams = {android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.STREAM_SYSTEM,
                    android.media.AudioManager.STREAM_ALARM};
            String[] captions = {"媒体", "系统", "闹钟"};
            int barW = Math.round(46 * density);
            int captionH = Math.round(16 * density);
            for (int i = 0; i < streams.length; i++) {
                final int stream = streams[i];
                // one column = the bar plus the caption underneath it
                android.widget.LinearLayout column = new android.widget.LinearLayout(getContext());
                column.setOrientation(android.widget.LinearLayout.VERTICAL);
                column.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                VerticalSeekBar b = new VerticalSeekBar(getContext());
                b.setIcon(VerticalSeekBar.ICON_VOLUME);
                try {
                    b.setMax(am.getStreamMaxVolume(stream));
                    b.setProgress(am.getStreamVolume(stream));
                } catch (Throwable t) {
                    b.setMax(15);
                }
                b.setOnChangeListener(new VerticalSeekBar.OnChangeListener() {
                    @Override
                    public void onProgressChanged(VerticalSeekBar sb, int progress,
                            boolean fromUser) {
                        if (!fromUser) {
                            return;
                        }
                        try {
                            am.setStreamVolume(stream, progress, 0);
                        } catch (Throwable ignored) {
                        }
                    }
                });
                column.addView(b, new android.widget.LinearLayout.LayoutParams(barW, 0, 1f));
                android.widget.TextView caption = new android.widget.TextView(getContext());
                caption.setText(captions[i]);
                caption.setTextColor(0xFFFFFFFF);
                caption.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                        Math.round(11 * density));
                caption.setGravity(android.view.Gravity.CENTER);
                caption.setIncludeFontPadding(false);
                caption.setShadowLayer(3f, 0f, 1f, 0xCC000000);
                column.addView(caption, new android.widget.LinearLayout.LayoutParams(
                        barW, captionH));
                android.widget.LinearLayout.LayoutParams clp =
                        new android.widget.LinearLayout.LayoutParams(
                                barW, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
                if (i > 0) {
                    clp.leftMargin = Math.round(8 * density);
                }
                trio.addView(column, clp);
            }
            android.widget.FrameLayout.LayoutParams tlp =
                    new android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.Gravity.CENTER);
            tlp.bottomMargin = captionH / 2;
            target.addView(trio, tlp);
            mMagnifyVolumeTrio = trio;
            mVolumeBar.setVisibility(android.view.View.INVISIBLE);
        }
        // capture the current screen and use it as the blurred backdrop
        android.view.ViewParent host = target;
        while (host != null
                && !(host instanceof com.android.systemui.statusbar.phone.XTCPanelView)) {
            host = host.getParent();
        }
        if (host instanceof com.android.systemui.statusbar.phone.XTCPanelView) {
            ((com.android.systemui.statusbar.phone.XTCPanelView) host).refreshGlassBackground();
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != null) {
                child.setAlpha(0.22f);
            }
        }
        setAlpha(0.35f);
        android.util.Log.i("QSPanel", "magnify on");
    }

    private void restoreMagnify() {
        if (mMagnifiedBar == null && mMagnifyVolumeTrio == null) {
            return;
        }
        VerticalSeekBar bar = mMagnifiedBar;
        mMagnifiedBar = null;
        if (mMagnifyScrim != null && mMagnifyTarget != null) {
            mMagnifyTarget.removeView(mMagnifyScrim);
            mMagnifyScrim = null;
        }
        if (mMagnifyTarget != null) {
            if (mMagnifyRightBall != null) {
                mMagnifyTarget.removeView(mMagnifyRightBall);
                mMagnifyRightBall = null;
            }
            if (mMagnifyVolumeTrio != null) {
                mMagnifyTarget.removeView(mMagnifyVolumeTrio);
                mMagnifyVolumeTrio = null;
            }
            if (bar != null) {
                mMagnifyTarget.removeView(bar);
            }
            mMagnifyTarget = null;
        }
        if (mBrightnessBar != null) {
            mBrightnessBar.setVisibility(android.view.View.VISIBLE);
        }
        if (mVolumeBar != null) {
            mVolumeBar.setVisibility(android.view.View.VISIBLE);
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != null) {
                child.setAlpha(1f);
            }
        }
        setAlpha(1f);
        android.util.Log.i("QSPanel", "magnify off");
    }

    /** The panel host (XTCPanelView FrameLayout) that overlays the whole screen. */
    private android.view.ViewGroup findPanelContainer() {
        android.view.ViewParent parent = getParent();
        while (parent != null) {
            if (parent instanceof android.widget.FrameLayout) {
                return (android.view.ViewGroup) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    /** Launches the picker activity (reuses an existing registered activity). */
    private void pickCustomBackground() {
        try {
            // the pull-down overlay would cover the picker - collapse first
            collapsePanel();
            android.content.Intent intent = new android.content.Intent(getContext(),
                    com.android.systemui.media.MediaProjectionPermissionActivity.class);
            intent.putExtra("pick_panel_bg", true);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            android.util.Log.i("QSPanel", "background picker started");
        } catch (Throwable t) {
            android.util.Log.w("QSPanel", "cannot start background picker: " + t);
        }
    }

    /** Removing the custom image falls back to the live blurred screenshot. */
    private void clearCustomBackground() {
        try {
            java.io.File file = new java.io.File(getContext().getFilesDir(), "panel_bg.png");
            if (file.exists()) {
                file.delete();
            }
            com.android.systemui.statusbar.phone.XTCPanelView panel =
                    com.android.systemui.statusbar.phone.XTCPanelView.getActive();
            if (panel != null) {
                panel.setCustomBackground(null);
            }
            setButtonColor(mBgButton, 0xFF9E9E9E);
            android.util.Log.i("QSPanel", "custom background cleared");
        } catch (Throwable t) {
            android.util.Log.w("QSPanel", "cannot clear background: " + t);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setup();
        applyLoadedOrder();
        if (mBrightnessController != null) {
            mBrightnessController.register(new BrightnessController.OnBrightnessChangedListener() {
                @Override
                public void onBrightnessChanged(int value, int max) {
                    mBrightnessBar.setMax(max);
                    mBrightnessBar.setProgress(value);
                }
            });
        }
        if (mVolumeController != null) {
            mVolumeController.register(new VolumeController.OnVolumeChangedListener() {
                @Override
                public void onVolumeChanged(int value, int max) {
                    mVolumeBar.setMax(max);
                    mVolumeBar.setProgress(value);
                }
            });
        }
        if (mToggles != null) {
            mToggles.setListener(new QsToggles.Listener() {
                @Override
                public void onToggleStateChanged(int index, boolean on) {
                    for (int slot = 0; slot < mSlotTile.length; slot++) {
                        if (mSlotTile[slot] == index && mTiles[slot] != null) {
                            mTiles[slot].setOn(on);
                        }
                    }
                }
            });
            mToggles.register();
        }
        if (mNowPlaying != null) {
            mNowPlaying.attach();
            mNowPlaying.replayLast();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mBrightnessController != null) {
            mBrightnessController.unregister();
        }
        if (mVolumeController != null) {
            mVolumeController.unregister();
        }
        if (mToggles != null) {
            mToggles.unregister();
        }
        if (mNowPlaying != null) {
            mNowPlaying.detach();
        }
        super.onDetachedFromWindow();
    }
}
