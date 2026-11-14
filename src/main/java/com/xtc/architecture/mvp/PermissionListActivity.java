package com.xtc.architecture.mvp;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.systemui.R;

import java.text.MessageFormat;

/**
 * Full-page "permission statement" ("权限说明整页").
 *
 * <p>Vendor original: an MVP activity that hosted a
 * {@code CtaPermissionView} (RecyclerView of permission rows) over
 * {@code CtaPermissionUtil.getAuthorityInfos()} and, on "同意", re-launched the
 * caller's pending intent with {@code EXTRA_CTA_PERMISSION_ALLOWED=true}.</p>
 *
 * <p>Rebuild: framework-only, code-built UI (this rebuild adds no layouts), with
 * the data contract kept: the 20 vendor labels from
 * {@code @array/permission_label} are bound <b>by index</b> to
 * {@link #ANDROID_PERM_NAME} (the order is part of the vendor resource contract
 * and must not change), each rendered as one simple {@code TextView} row.
 * No permission detection / reporting is performed - the list is the static
 * declaration copied from the vendor resource.</p>
 *
 * <p>Extras honoured for compatibility (vendor {@code mvp.b.a} keys):</p>
 * <ul>
 *   <li>{@code EXTRA_CTA_TITLE} - override for the app name in the header;</li>
 *   <li>{@code EXTRA_TIP} - one-line red hint under the header;</li>
 *   <li>{@code EXTRA_INTENT} / {@code EXTRA_CTA_PERMISSION_ALLOWED} are accepted
 *       but intentionally not re-launched: in this rebuild no code path starts
 *       this activity with a pending intent flow.</li>
 * </ul>
 *
 * <p>Back handling is intentionally friendly: back / the "我知道了" button simply
 * finish the page.</p>
 */
public class PermissionListActivity extends Activity {

    private static final String TAG = "PermissionListActivity";

    // Vendor extra keys (com.xtc.architecture.mvp.b.a), kept verbatim.
    public static final String EXTRA_TIP = "EXTRA_TIP";
    public static final String EXTRA_INTENT = "EXTRA_INTENT";
    public static final String EXTRA_CTA_TITLE = "EXTRA_CTA_TITLE";
    public static final String EXTRA_CTA_PERMISSION_ALLOWED = "EXTRA_CTA_PERMISSION_ALLOWED";

    /**
     * Vendor {@code CtaPermissionUtil.ANDROID_PERM_NAME} - index-locked with
     * {@code @array/permission_label} (20 entries, do not reorder).
     */
    public static final String[] ANDROID_PERM_NAME = {
            "android.permission.CALL_PHONE",
            "android.permission.SEND_SMS",
            "android.permission.INTERNET",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_SMS",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.CAMERA",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.READ_PHONE_STATE",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "org.simalliance.openmobileapi.SMARTCARD",
    };

    /**
     * Literal copy of {@code @array/permission_label} used only when the resource
     * cannot be read at runtime (e.g. partial resource packaging). Same index
     * mapping as {@link #ANDROID_PERM_NAME}.
     */
    private static final String[] FALLBACK_PERMISSION_LABEL = {
            "拨打电话",
            "接收短信、发送短信",
            "访问网络",
            "进行本地录音",
            "读写短信内容",
            "读取联系人信息",
            "写入或删除联系人",
            "读取通话记录",
            "修改通话记录",
            "获取位置信息",
            "拍摄照片和录制视频",
            "修改网络状态",
            "读取WLAN连接状态",
            "开启或关闭WLAN",
            "开启或关闭蓝牙",
            "读取设备通话状态",
            "获取位置信息",
            "访问媒体影音数据",
            "访问媒体影音数据",
            "智能卡权限",
    };

    private static final String FALLBACK_HEADER = "使用{0}需向你申请以下权限：";
    private static final String FALLBACK_KNOW = "我知道了";

    private static final int COLOR_PAGE_BG = 0xFF000000;
    private static final int COLOR_TITLE = 0xFFFAFAFA;
    private static final int COLOR_TIP = 0xFFFF7A7A;
    private static final int COLOR_ROW = 0xCCFAFAFA;
    private static final int COLOR_ACCENT = 0xFF55DD7B;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Full-page, no action bar - independent of the SystemUI app theme.
        setTheme(android.R.style.Theme_DeviceDefault_NoActionBar);
        super.onCreate(savedInstanceState);
        setContentView(buildContentView(getIntent()));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        try {
            setContentView(buildContentView(intent));
        } catch (Throwable t) {
            Log.e(TAG, "onNewIntent render failed", t);
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    // ---------------------------------------------------------------------- UI

    private View buildContentView(Intent intent) {
        final int padding = dp(12);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_PAGE_BG);
        root.setPadding(padding, padding, padding, padding);

        String appName = resolveAppName(intent);

        TextView titleView = new TextView(this);
        titleView.setText(buildHeader(appName));
        titleView.setTextColor(COLOR_TITLE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        root.addView(titleView, matchWrap());

        String tip = intent != null ? intent.getStringExtra(EXTRA_TIP) : null;
        if (!TextUtils.isEmpty(tip)) {
            TextView tipView = new TextView(this);
            tipView.setText(tip);
            tipView.setTextColor(COLOR_TIP);
            tipView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            LinearLayout.LayoutParams tipParams = matchWrap();
            tipParams.topMargin = dp(6);
            root.addView(tipView, tipParams);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(6), 0, dp(6));

        String[] labels = readPermissionLabels();
        int count = Math.min(ANDROID_PERM_NAME.length, labels.length);
        for (int i = 0; i < count; i++) {
            TextView row = new TextView(this);
            // Index-locked binding: ANDROID_PERM_NAME[i] <-> permission_label[i].
            row.setText("• " + labels[i]);
            row.setTextColor(COLOR_ROW);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            row.setGravity(Gravity.START);
            row.setPadding(0, dp(4), 0, dp(4));
            row.setTag(ANDROID_PERM_NAME[i]);
            list.addView(row, matchWrap());
        }
        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("暂无可展示的权限说明。");
            empty.setTextColor(COLOR_ROW);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            list.addView(empty, matchWrap());
        }
        scroll.addView(list, matchWrap());
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView know = new TextView(this);
        know.setText(stringOr(R.string.i_know, FALLBACK_KNOW));
        know.setTextColor(Color.BLACK);
        know.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        know.setGravity(Gravity.CENTER);
        know.setPadding(0, dp(9), 0, dp(9));
        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setShape(GradientDrawable.RECTANGLE);
        buttonBackground.setCornerRadius(dp(18));
        buttonBackground.setColor(COLOR_ACCENT);
        know.setBackground(buttonBackground);
        know.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams knowParams = matchWrap();
        knowParams.topMargin = dp(10);
        root.addView(know, knowParams);

        return root;
    }

    private String buildHeader(String appName) {
        if (TextUtils.isEmpty(appName)) {
            appName = stringOr(R.string.app_name, "本应用");
        }
        String template;
        try {
            template = getString(R.string.cta_allow_permission);
        } catch (Resources.NotFoundException e) {
            template = FALLBACK_HEADER;
        }
        try {
            return MessageFormat.format(template, appName);
        } catch (IllegalArgumentException e) {
            return "使用" + appName + "需向你申请以下权限：";
        }
    }

    private String resolveAppName(Intent intent) {
        if (intent != null) {
            String override = intent.getStringExtra(EXTRA_CTA_TITLE);
            if (!TextUtils.isEmpty(override)) {
                return override;
            }
        }
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            return pm.getApplicationInfo(getPackageName(), 0).loadLabel(pm).toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Reads {@code @array/permission_label}. Falls back to the literal copy when
     * the resource is missing or shorter than the vendor's 20 entries, so the
     * page can never bind a label to the wrong permission.
     */
    private String[] readPermissionLabels() {
        String[] labels = null;
        try {
            labels = getResources().getStringArray(R.array.permission_label);
        } catch (Throwable t) {
            Log.e(TAG, "permission_label array unavailable, using literal copy", t);
        }
        if (labels == null || labels.length < ANDROID_PERM_NAME.length) {
            Log.w(TAG, "permission_label length mismatch, using literal copy");
            labels = FALLBACK_PERMISSION_LABEL.clone();
        }
        return labels;
    }

    // --------------------------------------------------------------- helpers

    private String stringOr(int resId, String fallback) {
        try {
            return getString(resId);
        } catch (Resources.NotFoundException e) {
            return fallback;
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
