package com.xtc.dns.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * HTTP-DNS provider (authority
 * com.android.systemui.com.xtc.httpdns.provider, read-permission
 * READ_DNS, allowLaunchOnPowerSaveMode).
 *
 * Original APK logic: local caching front-end for the XTC HTTP-DNS service; it
 * answered "resolve(host)" calls from sibling apps and, on miss, queried the
 * vendor DNS endpoint over the network (cloud dependency).
 *
 * Reimplementation decision: REMOVED (cloud DNS / remote config channel). The
 * provider is kept as an empty, offline implementation so that:
 *   - the declared authority still resolves (APK information parity),
 *   - callers receive "no answer" and fall back to the platform resolver,
 *   - no network traffic or cloud lookup can be initiated.
 */
public class HttpDnsContentProvider extends ContentProvider {

    private static final String TAG = "HttpDnsProvider";

    @Override
    public boolean onCreate() {
        Log.i(TAG, "httpdns provider disabled (cloud DNS removed); callers fall back to system resolver");
        return true;
    }

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

    @Override
    public android.os.Bundle call(String method, String arg, android.os.Bundle extras) {
        // No resolution service available in the de-clouded build.
        return null;
    }
}
