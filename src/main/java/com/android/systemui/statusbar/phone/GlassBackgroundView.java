package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Full-bleed "frosted glass" backdrop of the pull-down panel (W22 implementation
 * restored by user request): the blurred capture is drawn centre-cropped over
 * the whole panel, followed by a light dark tint.  No zoom, no mask, no sheen.
 */
public class GlassBackgroundView extends View {

    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint mImagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix mImageMatrix = new Matrix();
    private android.graphics.Bitmap mCustomImage;
    private final Matrix mMatrix = new Matrix();
    private Bitmap mBlurred;
    private final Paint mFallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RadialGradient mFallbackGradient;

    public GlassBackgroundView(Context context) {
        this(context, null);
    }

    public GlassBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.TRANSPARENT);
        mPaint.setAlpha(255);
    }

    /** Uses a user-picked picture as the backdrop (no blur, no tint). */
    public void setBackgroundImage(Bitmap bitmap) {
        if (mCustomImage == bitmap) {
            return;
        }
        if (mCustomImage != null && !mCustomImage.isRecycled() && mCustomImage != bitmap) {
            mCustomImage.recycle();
        }
        mCustomImage = bitmap;
        updateImageMatrix();
        invalidate();
    }

    public boolean hasCustomImage() {
        return mCustomImage != null && !mCustomImage.isRecycled();
    }

    private void updateImageMatrix() {
        mImageMatrix.reset();
        if (mCustomImage == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float bw = mCustomImage.getWidth();
        float bh = mCustomImage.getHeight();
        float scale = Math.max(getWidth() / bw, getHeight() / bh);
        float dx = (getWidth() - bw * scale) * 0.5f;
        float dy = (getHeight() - bh * scale) * 0.5f;
        mImageMatrix.setScale(scale, scale);
        mImageMatrix.postTranslate(dx, dy);
    }

    public void setBlurredBitmap(Bitmap bitmap) {
        if (mBlurred == bitmap) {
            return;
        }
        if (mBlurred != null && !mBlurred.isRecycled()) {
            mBlurred.recycle();
        }
        mBlurred = bitmap;
        updateMatrix();
        invalidate();
    }

    public boolean hasBlur() {
        return mBlurred != null && !mBlurred.isRecycled();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateMatrix();
        updateImageMatrix();
        if (w > 0 && h > 0) {
            float radius = (float) Math.sqrt((double) (w * w + h * h)) * 0.5f;
            mFallbackGradient = new RadialGradient(w * 0.5f, h * 0.5f, radius,
                    new int[]{0xE6262B31, 0xE61A1E22, 0xEE0B0D0F},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            mFallbackPaint.setShader(mFallbackGradient);
        }
    }

    private void updateMatrix() {
        mMatrix.reset();
        if (mBlurred == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float bw = mBlurred.getWidth();
        float bh = mBlurred.getHeight();
        float scale = Math.max(getWidth() / bw, getHeight() / bh);
        float dx = (getWidth() - bw * scale) * 0.5f;
        float dy = (getHeight() - bh * scale) * 0.5f;
        mMatrix.setScale(scale, scale);
        mMatrix.postTranslate(dx, dy);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mCustomImage != null && !mCustomImage.isRecycled()) {
            if (mImageMatrix.isIdentity()) {
                updateImageMatrix(); // self-heal: bitmap set before layout
            }
            canvas.drawBitmap(mCustomImage, mImageMatrix, mImagePaint);
            return;
        }
        if (mBlurred != null && !mBlurred.isRecycled() && !mMatrix.isIdentity()) {
            canvas.drawBitmap(mBlurred, mMatrix, mPaint);
            // slight dark tint => readable white text over bright frames
            canvas.drawColor(0x33000000);
        } else if (mFallbackPaint.getShader() != null) {
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), mFallbackPaint);
        }
    }
}
