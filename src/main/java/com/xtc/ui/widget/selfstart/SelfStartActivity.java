package com.xtc.ui.widget.selfstart;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

/**
 * "Allow this app to start automatically" guidance screen.
 *
 * <p>Vendor original: a transparent activity showing a custom
 * {@code DoubleFlatBtnDialog} whose right button launched the vendor Settings
 * self-start page ({@code com.xtc.setting.action.SELF_START}) with the package
 * name, then finished; both window transitions were disabled.</p>
 *
 * <p>Rebuild: same flow with framework-only widgets (no vendor dialog library,
 * no new layouts/resources). The confirmation dialog is a plain
 * {@link AlertDialog}; its positive button fires the same implicit
 * {@link SelfStartConstant#ACTION_SETTINGS_SELF_START} intent and finishes. If
 * the Settings activity is missing (or dialog creation fails) the activity
 * finishes silently - launching a settings page the device does not have must
 * never crash SystemUI.</p>
 *
 * <p>Extras consumed: {@link SelfStartConstant#EXTRA_CONTENT_TEXT} (custom
 * wording shown as the dialog message) and
 * {@link SelfStartConstant#EXTRA_PACKAGE_NAME} (forwarded to Settings).</p>
 */
public class SelfStartActivity extends Activity {

    private static final String TAG = "SelfStartActivity";

    private static final String FALLBACK_TITLE = "自启动";
    private static final String FALLBACK_MESSAGE =
            "允许本应用自启动后，才能在开机或后台被清理后及时收到消息。";
    private static final String FALLBACK_CANCEL = "取消";
    private static final String FALLBACK_GO_TO_SET = "去设置";

    private AlertDialog mDialog;
    private String mPackageName;
    private String mContentText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        mContentText = intent.getStringExtra(SelfStartConstant.EXTRA_CONTENT_TEXT);
        mPackageName = intent.getStringExtra(SelfStartConstant.EXTRA_PACKAGE_NAME);
        showSelfStartDialog();
    }

    private void showSelfStartDialog() {
        try {
            final String message = TextUtils.isEmpty(mContentText)
                    ? FALLBACK_MESSAGE : mContentText;
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(FALLBACK_TITLE);
            builder.setMessage(message);
            builder.setCancelable(false);
            builder.setNegativeButton(stringOr(com.android.systemui.R.string.R_cancel,
                            FALLBACK_CANCEL),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
            builder.setPositiveButton(stringOr(com.android.systemui.R.string.self_start_go_to_set,
                            FALLBACK_GO_TO_SET),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            openSelfStartSettings();
                        }
                    });
            mDialog = builder.create();
            mDialog.show();
        } catch (Throwable t) {
            // No usable dialog theme / window on this device: do not crash, just leave.
            Log.e(TAG, "showSelfStartDialog failed", t);
            finish();
        }
    }

    /** Fires the vendor Settings self-start page; missing target is a no-op. */
    private void openSelfStartSettings() {
        Intent intent = new Intent(SelfStartConstant.ACTION_SETTINGS_SELF_START);
        if (!TextUtils.isEmpty(mPackageName)) {
            intent.putExtra(SelfStartConstant.EXTRA_PACKAGE_NAME, mPackageName);
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.d(TAG, "vendor self-start settings not installed: " + e);
        } catch (Throwable t) {
            Log.e(TAG, "openSelfStartSettings failed", t);
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDialog != null) {
            try {
                mDialog.dismiss();
            } catch (Throwable ignored) {
                // window already gone
            }
            mDialog = null;
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    private String stringOr(int resId, String fallback) {
        try {
            return getString(resId);
        } catch (Resources.NotFoundException e) {
            return fallback;
        }
    }
}
