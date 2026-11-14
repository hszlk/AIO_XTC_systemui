package com.xtc.ui.widget.privacy;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.xtc.utils.text.RichText;

/**
 * Offline, built-in privacy statement / user agreement page.
 *
 * <p><b>Vendor original</b>: a QR-code page - it encoded an H5 URL (plus
 * fingerprint query parameters) and showed "手机扫码查看《隐私政策》". That was
 * really just a hand-off to the network.</p>
 *
 * <p><b>Rebuild decision (SPEC §E.4)</b>: no network, no H5, no device
 * fingerprint. The same activity now renders the full statement locally with the
 * product's own rich-text syntax ({@link RichText}: {@code ${XTC}} brand
 * placeholder + {@code $...$} white highlight) and keeps the "同意 / 不同意"
 * contract:</p>
 * <ul>
 *   <li>title from {@code @string/privacy_dialog_title} /
 *       {@code @string/privacy_dialog_title_empty_app_name} (vendor copy);</li>
 *   <li>consent is persisted locally in {@link #PREFS_NAME}
 *       ({@link #KEY_PRIVACY_AGREED}, plus the agreed package);</li>
 *   <li>agree -&gt; {@code RESULT_OK} + finish, refuse/back -&gt; {@code RESULT_CANCELED}
 *       + finish. No external side effects (the vendor wrote the launcher
 *       self-start whitelist; that write is intentionally not reproduced).</li>
 * </ul>
 *
 * <p>The vendor static {@code start(...)} / {@code startV2(...)} entry points are
 * preserved with the same signatures and extras ({@link PrivacyCommon}) so any
 * sibling caller keeps working; the {@code url} extra is accepted but never
 * loaded.</p>
 *
 * <p>UI is built in code on purpose: this rebuild adds no layouts/drawables.</p>
 */
public class PrivacyUriActivity extends Activity {

    private static final String TAG = "PrivacyUriActivity";

    /** Local consent store (vendor SystemUI did not persist consent itself). */
    public static final String PREFS_NAME = "xtc_privacy";
    public static final String KEY_PRIVACY_AGREED = "privacy_agreed";
    public static final String KEY_PRIVACY_AGREED_PACKAGE = "privacy_agreed_package";

    private static final int COLOR_PAGE_BG = 0xFF000000;
    private static final int COLOR_TITLE = 0xFFFAFAFA;
    private static final int COLOR_BODY = RichText.BASE_COLOR;
    private static final int COLOR_HINT = 0xFF888888;
    private static final int COLOR_ACCENT = 0xFF55DD7B;

    /**
     * Built-in statement body. Uses the vendor rich-text syntax so the brand
     * placeholder and the highlighted key phrases are rendered by
     * {@link RichText#format(Context, String)}.
     */
    private static final String BODY_TEXT =
            "欢迎使用${XTC}！我们非常重视你和孩子的个人信息保护，"
                    + "并会严格按照法律法规的要求处理个人信息。\n\n"
                    + "1. 我们遵循$最小必要$原则，仅在你使用具体功能时收集实现该功能"
                    + "所必需的信息，并在你使用前向你说明。\n\n"
                    + "2. 我们不会收集与所提供服务无关的个人信息，也不会将你的个人信息"
                    + "用于与服务无关的用途。\n\n"
                    + "3. 未经你的同意，我们不会向任何第三方共享、转让或公开披露你的"
                    + "个人信息；法律法规另有规定的除外。\n\n"
                    + "4. 你可以随时在手表「设置」中查看、更正或撤回授权，"
                    + "撤回同意不影响此前基于你的同意已开展的个人信息处理。\n\n"
                    + "5. 我们采用加密存储、访问控制等安全措施保护你的个人信息，"
                    + "防止信息被未经授权地访问、泄露或篡改。\n\n"
                    + "6. 若你未满十四周岁，请在父母或其他监护人的陪同下阅读本说明，"
                    + "并由监护人代为作出同意或不同意的决定。\n\n"
                    + "7. 本说明自你点击「同意」之日起生效。若你不同意本说明的内容，"
                    + "可以点击「不同意」退出本次使用。\n\n"
                    + "如需了解完整条款，请在手表「设置 - 关于」中查看隐私政策全文。";

    /** Fallback copy when a vendor string resource is unavailable at runtime. */
    private static final String FALLBACK_TITLE_EMPTY =
            "为保障你的个人权益，在使用应用前，请在父母或其他监护人陪同下阅读并同意用户服务协议及隐私政策：";
    private static final String FALLBACK_TITLE_WITH_APP =
            "为保障你的个人权益，在使用%1$s前，请在父母或其他监护人陪同下阅读并同意用户服务协议及隐私政策：";

    private String mPackageName;

    // ------------------------------------------------------------------ start

    public static void start(Context context, String url) {
        start(context, url, "");
    }

    public static void start(Context context, String url, String customString) {
        start(context, url, customString, PrivacyCommon.UrlType.PRIVACY_TYPE, "");
    }

    public static void start(Context context, String url, String customString, int urlType) {
        start(context, url, customString, urlType, "");
    }

    public static void start(Context context, String url, String customString, int urlType,
            String packageName) {
        launch(context, buildIntent(context, url, customString, urlType, packageName, false));
    }

    /** V2 entry point (vendor API): no URL re-joining in this offline rebuild. */
    public static void startV2(Context context, String url, String customString, int urlType,
            String packageName, boolean versionV2) {
        launch(context, buildIntent(context, url, customString, urlType, packageName, versionV2));
    }

    private static Intent buildIntent(Context context, String url, String customString,
            int urlType, String packageName, boolean versionV2) {
        Intent intent = new Intent(context, PrivacyUriActivity.class);
        intent.putExtra(PrivacyCommon.PrivacyExtras.EXTRA_URL, url);
        intent.putExtra(PrivacyCommon.PrivacyExtras.EXTRA_CUSTOM_STRING, customString);
        intent.putExtra(PrivacyCommon.PrivacyExtras.EXTRA_URL_TYPE, urlType);
        intent.putExtra(PrivacyCommon.PrivacyExtras.EXTRA_PACKAGE_NAME, packageName);
        if (versionV2) {
            intent.putExtra(PrivacyCommon.PrivacyExtras.EXTRA_VERSION,
                    PrivacyCommon.PrivacyComponentVersion.VERSION_V2);
        }
        return intent;
    }

    private static void launch(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "startActivity failed", e);
        }
    }

    // ------------------------------------------------------------------ consent

    /** True when the user already agreed to the locally shown statement. */
    public static boolean isAgreed(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PRIVACY_AGREED, false);
    }

    /** Records/clears the local consent flag (no network, no external process). */
    public static void setAgreed(Context context, String packageName, boolean agreed) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(KEY_PRIVACY_AGREED, agreed);
        if (agreed && !TextUtils.isEmpty(packageName)) {
            editor.putString(KEY_PRIVACY_AGREED_PACKAGE, packageName);
        } else {
            editor.remove(KEY_PRIVACY_AGREED_PACKAGE);
        }
        editor.apply();
    }

    public static String getAgreedPackage(Context context) {
        if (context == null) {
            return null;
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PRIVACY_AGREED_PACKAGE, null);
    }

    // --------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Full-page, no action bar - independent of the SystemUI app theme.
        setTheme(android.R.style.Theme_DeviceDefault_NoActionBar);
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mPackageName = intent != null
                ? intent.getStringExtra(PrivacyCommon.PrivacyExtras.EXTRA_PACKAGE_NAME)
                : null;
        String url = intent != null
                ? intent.getStringExtra(PrivacyCommon.PrivacyExtras.EXTRA_URL)
                : null;
        String customString = intent != null
                ? intent.getStringExtra(PrivacyCommon.PrivacyExtras.EXTRA_CUSTOM_STRING)
                : null;
        if (!TextUtils.isEmpty(url)) {
            // Deliberately never fetched: offline statement only.
            Log.d(TAG, "ignoring url extra (offline privacy page): " + url);
        }

        setContentView(buildContentView(customString));
    }

    // ---------------------------------------------------------------------- UI

    private View buildContentView(String customString) {
        final int padding = dp(12);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_PAGE_BG);
        root.setPadding(padding, padding, padding, padding);

        TextView titleView = new TextView(this);
        titleView.setText(buildTitle());
        titleView.setTextColor(COLOR_TITLE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        titleView.setGravity(Gravity.START);
        root.addView(titleView, matchWrap());

        if (!TextUtils.isEmpty(customString)) {
            TextView subtitleView = new TextView(this);
            subtitleView.setText("《" + customString + "》");
            subtitleView.setTextColor(COLOR_ACCENT);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            LinearLayout.LayoutParams subtitleParams = matchWrap();
            subtitleParams.topMargin = dp(6);
            root.addView(subtitleView, subtitleParams);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        TextView bodyView = new TextView(this);
        bodyView.setText(RichText.format(this, BODY_TEXT));
        bodyView.setTextColor(COLOR_BODY);
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        bodyView.setLineSpacing(dp(3), 1f);
        scroll.addView(bodyView, matchWrap());
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollParams);

        TextView hintView = new TextView(this);
        hintView.setText("本说明为离线内置内容，不会访问网络。");
        hintView.setTextColor(COLOR_HINT);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(6);
        root.addView(hintView, hintParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buttonsParams = matchWrap();
        buttonsParams.topMargin = dp(10);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        buttonParams.setMargins(dp(4), 0, dp(4), 0);

        TextView refuse = new TextView(this);
        refuse.setText(stringOr(R.string.disagree, "不同意"));
        refuse.setTextColor(COLOR_HINT);
        refuse.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        refuse.setGravity(Gravity.CENTER);
        refuse.setPadding(0, dp(9), 0, dp(9));
        refuse.setBackground(roundBackground(0x00000000, COLOR_HINT));
        refuse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRefused();
            }
        });
        buttons.addView(refuse, buttonParams);

        TextView agree = new TextView(this);
        agree.setText(stringOr(R.string.agree, "同意"));
        agree.setTextColor(Color.BLACK);
        agree.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        agree.setGravity(Gravity.CENTER);
        agree.setPadding(0, dp(9), 0, dp(9));
        agree.setBackground(roundBackground(COLOR_ACCENT, Color.TRANSPARENT));
        agree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAgreed();
            }
        });
        buttons.addView(agree, buttonParams);

        root.addView(buttons, buttonsParams);
        return root;
    }

    private String buildTitle() {
        String appName = resolveAppName();
        if (TextUtils.isEmpty(appName)) {
            return stringOr(R.string.privacy_dialog_title_empty_app_name, FALLBACK_TITLE_EMPTY);
        }
        try {
            return getString(R.string.privacy_dialog_title, appName);
        } catch (Resources.NotFoundException e) {
            return String.format(FALLBACK_TITLE_WITH_APP, appName);
        }
    }

    private String resolveAppName() {
        if (TextUtils.isEmpty(mPackageName)) {
            return "";
        }
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            CharSequence label = pm.getApplicationInfo(mPackageName, 0).loadLabel(pm);
            return label != null ? label.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    // --------------------------------------------------------------- actions

    private void onAgreed() {
        setAgreed(this, mPackageName, true);
        setResult(RESULT_OK);
        finish();
    }

    private void onRefused() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Back == refuse; never leave the consent flow in an undefined state.
        onRefused();
    }

    // --------------------------------------------------------------- helpers

    private String stringOr(int resId, String fallback) {
        try {
            return getString(resId);
        } catch (Resources.NotFoundException e) {
            return fallback;
        }
    }

    private GradientDrawable roundBackground(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(18));
        if (fillColor != Color.TRANSPARENT) {
            drawable.setColor(fillColor);
        }
        if (strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
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
