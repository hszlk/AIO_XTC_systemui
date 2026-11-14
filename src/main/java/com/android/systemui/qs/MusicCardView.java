package com.android.systemui.qs;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Now-playing card of the pull-down, top-left rounded block (user UI spec).
 *
 * Layout (per the user's sketch):
 *   +------------------+
 *   |    album art     |
 *   |  marquee title   |
 *   |  <    (||)   >   |
 *   +------------------+
 *
 * The previous version only had two text lines; this one is the "mini player"
 * segment: rounded album-art image, scrolling song title, previous / play-pause /
 * next controls.  Data is pushed by {@link NowPlayingController} (media
 * notification / MediaSession token), so it shows whatever app is playing in the
 * background - NetEase Music, Kugou, system music, etc.
 *
 * Cheap by design: one cached shader for the rounded artwork, all controls are
 * drawn shapes (no bitmaps / no View hierarchy), marquee scrolls only while the
 * panel is expanded.
 */
public class MusicCardView extends View {

    public interface Callback {
        void onPrevious();

        void onPlayPause();

        void onNext();

        void onLyrics();
    }

    private final Paint mCardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArtPaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG);
    private final Paint mButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mButtonFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mArtistPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mCardRect = new RectF();
    private final RectF mArtRect = new RectF();
    private float mRecordCx;
    private float mRecordCy;
    private float mRecordR;
    private final android.graphics.RectF mLyricsRect = new android.graphics.RectF();
    private final Rect mArtClip = new Rect();
    private final RectF mPrevRect = new RectF();
    private final RectF mNextRect = new RectF();
    private final RectF mPlayRect = new RectF();
    private final Rect mTitleClip = new Rect();
    private final Matrix mShaderMatrix = new Matrix();

    private Bitmap mArt;
    private BitmapShader mArtShader;
    private String mTitle = "";
    private String mArtist = "";
    private boolean mPlaying;
    private boolean mHasMedia;
    private boolean mActive;
    private Callback mCallback;

    private float mCardRadius;
    private float mArtSize;
    private float mScroll;
    private long mLastFrameTime;

    public MusicCardView(Context context) {
        this(context, null);
    }

    public MusicCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mCardPaint.setColor(0x26FFFFFF);
        mButtonPaint.setColor(0xFF333333);
        mButtonPaint.setStyle(Paint.Style.STROKE);
        mButtonFill.setColor(0xFF333333);
        mButtonFill.setStyle(Paint.Style.FILL);
        mTitlePaint.setColor(0xFF222222);
        mTitlePaint.setFakeBoldText(true);
        mArtistPaint.setColor(0x99000000);
        setClickable(true);
        // no music -> no card: hidden until setMedia() is told otherwise
        setVisibility(View.GONE);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    /** Pushes the current media state; pass null artwork when unavailable. */
    public void setMedia(String title, String artist, Bitmap art, boolean playing) {
        mTitle = title == null ? "" : title;
        mArtist = artist == null ? "" : artist;
        mPlaying = playing;
        mHasMedia = !mTitle.isEmpty() || art != null;
        if (mArt != art) {
            mArt = art;
            mArtShader = null;
            if (art != null) {
                mArtShader = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            }
        }
        mScroll = 0f;
        setVisibility(mHasMedia ? View.VISIBLE : View.GONE);
        invalidate();
    }

    /** Marquee animation is only run while the panel is open. */
    public void setActive(boolean active) {
        mActive = active;
        if (active) {
            mLastFrameTime = 0;
            postInvalidateOnAnimation();
        } else {
            mScroll = 0f;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // vinyl centred; prev/next vertically centred on the flanks;
        // title bottom-left, artist bottom-right (user layout)
        mCardRadius = Math.min(w, h) * 0.12f;
        mCardRect.set(0f, 0f, w, h);
        float pad = h * 0.06f;
        float recordD = h - pad * 2f;
        float recordR = recordD * 0.5f;
        float recordCx = w * 0.5f;
        float recordCy = h * 0.5f;
        mRecordCx = recordCx;
        mRecordCy = recordCy;
        mRecordR = recordR;
        mArtSize = recordD * 0.60f;
        float artLeft = recordCx - mArtSize * 0.5f;
        float artTop = recordCy - mArtSize * 0.5f;
        mArtRect.set(artLeft, artTop, artLeft + mArtSize, artTop + mArtSize);
        mArtClip.set((int) mArtRect.left, (int) mArtRect.top,
                (int) mArtRect.right, (int) mArtRect.bottom);
        float playBtn = recordD * 0.36f;
        mPlayRect.set(recordCx - playBtn * 0.5f, recordCy - playBtn * 0.5f,
                recordCx + playBtn * 0.5f, recordCy + playBtn * 0.5f);
        float sideBtn = h * 0.18f;
        float flankCx = pad * 0.6f + sideBtn * 0.5f;
        mPrevRect.set(flankCx - sideBtn * 0.5f, recordCy - sideBtn * 0.5f,
                flankCx + sideBtn * 0.5f, recordCy + sideBtn * 0.5f);
        float nextCx = w - flankCx;
        mNextRect.set(nextCx - sideBtn * 0.5f, recordCy - sideBtn * 0.5f,
                nextCx + sideBtn * 0.5f, recordCy + sideBtn * 0.5f);
        // lyrics toggle: small pill at the top-right corner
        float lyr = h * 0.13f;
        mLyricsRect.set(w - pad * 0.6f - lyr, pad * 0.35f,
                w - pad * 0.6f, pad * 0.35f + lyr);
        // bottom corners: title left, artist right
        float textBottom = h - pad * 0.7f;
        mTitleClip.set((int) (pad * 0.6f), (int) (h * 0.80f),
                (int) (w * 0.58f), (int) textBottom);
        mTitlePaint.setTextSize(h * 0.085f);
        mArtistPaint.setTextSize(h * 0.075f);
        mButtonPaint.setStrokeWidth(Math.max(1.5f, h * 0.016f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mCardRect.isEmpty()) {
            return;
        }
        PillBackground.drawBoard(canvas, mCardRect, mCardRadius);
        drawLyricsButton(canvas);

        // vinyl record: dark disc + grooves + album art + centre play button
        float artCx = mArtRect.centerX();
        float artCy = mArtRect.centerY();
        float artR = mArtSize * 0.5f;
        float spin = (System.currentTimeMillis() % 60000L) / 60000f * 360f;
        canvas.save();
        canvas.rotate(spin, mRecordCx, mRecordCy);
        mArtPaint.setStyle(Paint.Style.FILL);
        mArtPaint.setColor(0xE6202020);
        canvas.drawCircle(mRecordCx, mRecordCy, mRecordR, mArtPaint);
        mArtPaint.setStyle(Paint.Style.STROKE);
        mArtPaint.setStrokeWidth(Math.max(1f, mRecordR * 0.02f));
        mArtPaint.setColor(0x2EFFFFFF);
        canvas.drawCircle(mRecordCx, mRecordCy, mRecordR * 0.86f, mArtPaint);
        canvas.drawCircle(mRecordCx, mRecordCy, mRecordR * 0.74f, mArtPaint);
        mArtPaint.setStyle(Paint.Style.FILL);
        if (mArtShader != null && mArt != null && !mArt.isRecycled()) {
            float scale = Math.max(mArtSize / mArt.getWidth(), mArtSize / mArt.getHeight());
            mShaderMatrix.reset();
            mShaderMatrix.setScale(scale, scale);
            mShaderMatrix.postTranslate(
                    mArtRect.left + (mArtSize - mArt.getWidth() * scale) * 0.5f,
                    mArtRect.top + (mArtSize - mArt.getHeight() * scale) * 0.5f);
            mArtShader.setLocalMatrix(mShaderMatrix);
            // the paint alpha survived from the disc/groove passes (0x2E) and was
            // veiling the cover with a dark film - reset it to fully opaque
            mArtPaint.setAlpha(255);
            mArtPaint.setShader(mArtShader);
            mArtPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(artCx, artCy, artR, mArtPaint);
            mArtPaint.setShader(null);
        } else {
            mArtPaint.setStyle(Paint.Style.FILL);
            mArtPaint.setColor(0x1F000000);
            canvas.drawCircle(artCx, artCy, artR, mArtPaint);
            mArtPaint.setColor(0x66000000);
            float noteR = mArtSize * 0.10f;
            canvas.drawCircle(artCx - noteR * 0.8f, artCy + noteR * 0.8f, noteR, mArtPaint);
            mArtPaint.setStyle(Paint.Style.STROKE);
            mArtPaint.setStrokeWidth(mArtSize * 0.035f);
            canvas.drawLine(artCx - noteR * 0.1f,
                    artCy + noteR * 1.8f,
                    artCx + noteR * 1.2f,
                    artCy - noteR * 1.4f, mArtPaint);
        }
        canvas.restore(); // end of the spinning disc

        // theme-aware text colour: white on the dark board, black on white
        int textColor = PillBackground.isDarkMode() ? 0xFFFFFFFF : 0xFF222222;
        mTitlePaint.setColor(textColor);
        mArtistPaint.setColor(PillBackground.isDarkMode()
                ? 0xCCFFFFFF : 0x99000000);
        // scroll title (marquee) inside the clip band
        if (!mTitle.isEmpty()) {
            drawMarqueeTitle(canvas);
        }
        if (!mArtist.isEmpty()) {
            android.graphics.Paint.Align oldAlign = mArtistPaint.getTextAlign();
            mArtistPaint.setTextAlign(android.graphics.Paint.Align.RIGHT);
            float baseline = mTitleClip.bottom - mArtistPaint.getTextSize() * 0.12f;
            canvas.drawText(mArtist, mCardRect.width() - mCardRect.height() * 0.036f,
                    baseline, mArtistPaint);
            mArtistPaint.setTextAlign(oldAlign);
        }

        // controls
        boolean enabled = mHasMedia;
        int alpha = enabled ? 255 : 70;
        drawPrevious(canvas, alpha);
        drawPlayPause(canvas, alpha);
        drawNext(canvas, alpha);

        // keep the marquee running only while active and needed
        boolean spinning = mPlaying && mHasMedia;
        if ((mActive && needsScroll()) || spinning) {
            long now = System.currentTimeMillis();
            if (mLastFrameTime != 0) {
                float dt = (now - mLastFrameTime) / 1000f;
                mScroll += getWidth() * 0.22f * dt;
                if (mScroll > mTitlePaint.measureText(mTitle) + getWidth() * 0.25f) {
                    mScroll = 0f;
                }
            }
            mLastFrameTime = now;
            postInvalidateOnAnimation();
        }
    }

    private boolean needsScroll() {
        return !mTitle.isEmpty() && mTitlePaint.measureText(mTitle) > mTitleClip.width();
    }

    private void drawLyricsButton(Canvas canvas) {
        boolean on = LyricsFloat.isShowing();
        mArtPaint.setStyle(Paint.Style.FILL);
        mArtPaint.setColor(on ? 0xE6108CFF : 0x66FFFFFF);
        canvas.drawRoundRect(mLyricsRect, mLyricsRect.height() * 0.5f,
                mLyricsRect.height() * 0.5f, mArtPaint);
        mArtPaint.setColor(on ? 0xFFFFFFFF : 0xFF333333);
        mArtPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
        float size = mLyricsRect.height() * 0.52f;
        mArtPaint.setTextSize(size);
        android.graphics.Paint.FontMetrics fm = mArtPaint.getFontMetrics();
        float baseline = mLyricsRect.centerY() - (fm.ascent + fm.descent) * 0.5f;
        canvas.drawText("词", mLyricsRect.centerX(), baseline, mArtPaint);
        mArtPaint.setTextAlign(android.graphics.Paint.Align.LEFT);
    }

    private void drawMarqueeTitle(Canvas canvas) {
        canvas.save();
        canvas.clipRect(mTitleClip);
        float baseline = mTitleClip.top + (mTitleClip.height() - mTitlePaint.getTextSize()) * 0.5f
                + mTitlePaint.getTextSize() * 0.85f;
        float tw = mTitlePaint.measureText(mTitle);
        if (tw <= mTitleClip.width()) {
            canvas.drawText(mTitle, mTitleClip.left, baseline, mTitlePaint);
        } else {
            float x = mTitleClip.left - (mScroll % (tw + mTitleClip.width()));
            canvas.drawText(mTitle, x, baseline, mTitlePaint);
            canvas.drawText(mTitle, x + tw + mTitleClip.width(), baseline, mTitlePaint);
        }
        canvas.restore();
    }

    private void drawPrevious(Canvas canvas, int alpha) {
        mButtonPaint.setColor(Color.WHITE);
        mButtonPaint.setAlpha(alpha);
        float cx = mPrevRect.centerX();
        float cy = mPrevRect.centerY();
        float h = mPrevRect.height() * 0.55f;
        float w = mPrevRect.width() * 0.45f;
        mPath.reset();
        mPath.moveTo(cx + w * 0.5f, cy - h * 0.5f);
        mPath.lineTo(cx - w * 0.5f, cy);
        mPath.lineTo(cx + w * 0.5f, cy + h * 0.5f);
        mPath.close();
        canvas.drawPath(mPath, mButtonFill);
        canvas.drawLine(cx - w * 0.5f, cy - h * 0.5f, cx - w * 0.5f, cy + h * 0.5f, mButtonPaint);
    }

    private void drawNext(Canvas canvas, int alpha) {
        mButtonPaint.setColor(Color.WHITE);
        mButtonPaint.setAlpha(alpha);
        float cx = mNextRect.centerX();
        float cy = mNextRect.centerY();
        float h = mNextRect.height() * 0.55f;
        float w = mNextRect.width() * 0.45f;
        mPath.reset();
        mPath.moveTo(cx - w * 0.5f, cy - h * 0.5f);
        mPath.lineTo(cx + w * 0.5f, cy);
        mPath.lineTo(cx - w * 0.5f, cy + h * 0.5f);
        mPath.close();
        canvas.drawPath(mPath, mButtonFill);
        canvas.drawLine(cx + w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f, mButtonPaint);
    }

    private void drawPlayPause(Canvas canvas, int alpha) {
        float cx = mPlayRect.centerX();
        float cy = mPlayRect.centerY();
        float r = Math.min(mPlayRect.width(), mPlayRect.height()) * 0.5f;
        // floating: no disc under the icon (it greys the album art out)
        if (mPlaying) {
            float barW = r * 0.14f;
            float barH = r * 0.50f;
            mButtonFill.setColor(Color.WHITE);
            mButtonFill.setAlpha(alpha);
            canvas.drawRect(cx - barW * 1.6f, cy - barH * 0.5f, cx - barW * 0.6f,
                    cy + barH * 0.5f, mButtonFill);
            canvas.drawRect(cx + barW * 0.6f, cy - barH * 0.5f, cx + barW * 1.6f,
                    cy + barH * 0.5f, mButtonFill);
        } else {
            mPath.reset();
            mPath.moveTo(cx - r * 0.22f, cy - r * 0.40f);
            mPath.lineTo(cx + r * 0.42f, cy);
            mPath.lineTo(cx - r * 0.22f, cy + r * 0.40f);
            mPath.close();
            mButtonFill.setColor(Color.WHITE);
            mButtonFill.setAlpha(alpha);
            canvas.drawPath(mPath, mButtonFill);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP || mCallback == null || !mHasMedia) {
            return true;
        }
        float x = event.getX();
        float y = event.getY();
        if (mLyricsRect.contains(x, y)) {
            mCallback.onLyrics();
        } else if (mPrevRect.contains(x, y)) {
            mCallback.onPrevious();
        } else if (mNextRect.contains(x, y)) {
            mCallback.onNext();
        } else if (mPlayRect.contains(x, y)) {
            mCallback.onPlayPause();
        }
        return true;
    }
}
