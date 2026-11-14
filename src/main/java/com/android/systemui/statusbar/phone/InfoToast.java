package com.android.systemui.statusbar.phone;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.TextView;

/**
 * Lightweight in-window transient message ("inner toast") used by system UI
 * features such as screen recording start/stop, flashlight, etc.
 *
 * The vendor layout already includes {@code layout_inner_toast} at the bottom of
 * status_bar.xml (a TextView with id {@code tv_toast_inner}, style
 * TextAppearance_Toast, background inner_toast_background).  This helper binds
 * to that view once and fades messages in/out - no Toast window, no extra
 * layout, no allocations per show.
 *
 * Original APK logic: com.xtc.ui.widget.toast.ToastUtil / ToastView +
 * {@code layout_inner_toast}; vendor code showed these through the status bar
 * window.  Reimplementation: same visual host, framework-only, no queue.
 */
public final class InfoToast {

    private static final long SHOW_MS = 2400L;
    private static final long FADE_MS = 180L;

    private static Handler sHandler;
    private static TextView sView;
    private static final Runnable sHideTask = new Runnable() {
        @Override
        public void run() {
            if (sView == null) {
                return;
            }
            AlphaAnimation out = new AlphaAnimation(1f, 0f);
            out.setDuration(FADE_MS);
            out.setFillAfter(true);
            sView.startAnimation(out);
            sView.setVisibility(View.GONE);
        }
    };

    private InfoToast() {
    }

    /** Called once by StatusBar after inflating super_status_bar. */
    public static void bind(View root) {
        if (root == null) {
            return;
        }
        View v = root.findViewById(com.android.systemui.R.id.tv_toast_inner);
        if (v instanceof TextView) {
            sView = (TextView) v;
            if (sHandler == null) {
                sHandler = new Handler(Looper.getMainLooper());
            }
        }
    }

    /** Shows a transient message; safe to call from any thread. */
    public static void show(final CharSequence message) {
        Handler handler = sHandler;
        if (handler == null) {
            return;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (sView == null) {
                    return;
                }
                sView.clearAnimation();
                sView.setText(message);
                sView.setVisibility(View.VISIBLE);
                AlphaAnimation in = new AlphaAnimation(0f, 1f);
                in.setDuration(FADE_MS);
                sView.startAnimation(in);
                sHandler.removeCallbacks(sHideTask);
                sHandler.postDelayed(sHideTask, SHOW_MS);
            }
        });
    }

    public static void show(int resId) {
        if (sView != null) {
            show(sView.getContext().getText(resId));
        }
    }
}
