package com.android.systemui.statusbar.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

/**
 * Cross-process status-bar proxy exposed to the vendor launcher.
 *
 * <p>Manifest contract (vendor manifest, unchanged):</p>
 * <pre>
 * &lt;provider android:name="com.android.systemui.statusbar.provider.StatusBarProxyProvider"
 *           android:authorities="com.xtc.systemui.statusbar.proxy"
 *           android:exported="true"
 *           android:permission="com.xtc.systemui.statusbar.permission.StatusBarProxyProvider"/&gt;
 * </pre>
 *
 * <p>Only {@code call()} is implemented (vendor had empty
 * insert/delete/getType/query/update as well). Two access gates, exactly like
 * the vendor implementation: the signature permission above (enforced by the
 * system) plus a code check that {@link #getCallingPackage()} is
 * {@value #CALLER_PACKAGE}; anything else falls through to
 * {@code super.call(...)} which returns {@code null}.</p>
 *
 * <h3>Method table (byte-for-byte vendor behavior)</h3>
 * <table>
 *   <tr><th>call(method, arg, extras)</th><th>return</th></tr>
 *   <tr><td>{@code isStatusBarExpanded}</td>
 *       <td>Bundle{@code "expanded"=boolean}</td></tr>
 *   <tr><td>{@code collapsePanel}</td>
 *       <td>{@code null} (falls through to {@code super.call}, like the vendor code)</td></tr>
 *   <tr><td>{@code isStatusBarFullyExpanded}</td>
 *       <td>Bundle{@code "fullyExpanded"=boolean}</td></tr>
 *   <tr><td>{@code onImStateChanged} (extras: {@code "imState"=boolean})</td>
 *       <td>Also publishes the IM state, then returns
 *       Bundle{@code "fullyExpanded"=boolean} (vendor quirk: the same block runs
 *       after the IM dispatch)</td></tr>
 *   <tr><td>anything else / null</td>
 *       <td><b>Bundle{@code "fullyExpanded"=boolean}, never null</b> - vendor
 *       switch fell through to the same fully-expanded block; the launcher
 *       depends on this odd fallback</td></tr>
 * </table>
 *
 * <p>Panel state comes from {@link PanelStateHolder}, which the status-bar/panel
 * code publishes to (see that class for the wiring). The original read
 * package-private {@code StatusBar} getters whose de-obfuscated names are
 * ambiguous; the holder encodes the same {@code PanelView} semantics
 * ({@code isPanelExpanded} / {@code isFullyExpanded}).</p>
 */
public class StatusBarProxyProvider extends ContentProvider {

    private static final String TAG = "StatusBarProxyProvider";

    public static final String AUTHORITY = "com.xtc.systemui.statusbar.proxy";
    public static final String PERMISSION =
            "com.xtc.systemui.statusbar.permission.StatusBarProxyProvider";
    public static final String CALLER_PACKAGE = "com.xtc.i3launcher";

    public static final String METHOD_IS_STATUS_BAR_EXPANDED = "isStatusBarExpanded";
    public static final String METHOD_IS_STATUS_BAR_FULLY_EXPANDED = "isStatusBarFullyExpanded";
    public static final String METHOD_COLLAPSE_PANEL = "collapsePanel";
    public static final String METHOD_ON_IM_STATE_CHANGED = "onImStateChanged";

    public static final String KEY_EXPANDED = "expanded";
    public static final String KEY_FULLY_EXPANDED = "fullyExpanded";
    public static final String KEY_IM_STATE = "imState";

    private Context mContext;

    @Override
    public boolean onCreate() {
        mContext = getContext();
        return mContext != null;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!isTrustedCaller()) {
            return super.call(method, arg, extras);
        }

        if (METHOD_IS_STATUS_BAR_EXPANDED.equals(method)) {
            Bundle result = new Bundle();
            result.putBoolean(KEY_EXPANDED, PanelStateHolder.isExpanded());
            return result;
        }

        if (METHOD_COLLAPSE_PANEL.equals(method)) {
            PanelStateHolder.requestCollapse();
            // Vendor code fell through to `return super.call(...)` for this branch.
            return super.call(method, arg, extras);
        }

        if (METHOD_ON_IM_STATE_CHANGED.equals(method)) {
            boolean online = extras != null && extras.getBoolean(KEY_IM_STATE, false);
            PanelStateHolder.setImState(online);
        }

        // isStatusBarFullyExpanded AND every unknown method land here (vendor quirk:
        // the fully-expanded bundle is the catch-all return value).
        Bundle result = new Bundle();
        result.putBoolean(KEY_FULLY_EXPANDED, PanelStateHolder.isFullyExpanded());
        return result;
    }

    /**
     * Vendor {@code a()}: only the vendor launcher may use this provider. The
     * signature permission is the primary gate; this is the defense-in-depth
     * package check (same as the vendor implementation).
     */
    private boolean isTrustedCaller() {
        String callingPackage;
        try {
            callingPackage = getCallingPackage();
        } catch (Throwable t) {
            Log.e(TAG, "getCallingPackage failed", t);
            return false;
        }
        if (TextUtils.isEmpty(callingPackage)) {
            return false;
        }
        if (CALLER_PACKAGE.equals(callingPackage)) {
            return true;
        }
        Log.d(TAG, "checkAccessPermission: not allowed to access: " + callingPackage);
        return false;
    }

    // ---------------------------------------------- unused content-provider API

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
