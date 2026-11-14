package com.android.systemui;

import android.content.Context;
import android.util.Log;

/**
 * Factory loaded from {@code @string/config_systemUIFactoryComponent}.
 *
 * Original APK logic: a singleton created from the configured class name; the
 * factory object is the extension point used by SystemUI to build the
 * status-bar related controllers (asynchronous inflate thread, status bar
 * window controller, QS panel).
 *
 * Reimplementation logic: same static load path and fatal-on-failure behaviour;
 * the concrete controller factory methods are added wave by wave together with
 * the controllers they produce.
 */
public class SystemUIFactory {

    private static final String TAG = "SystemUIFactory";

    private static SystemUIFactory sInstance;

    public static SystemUIFactory getInstance() {
        return sInstance;
    }

    public static void createFromContext(Context context) {
        String component = context.getString(R.string.config_systemUIFactoryComponent);
        if (component == null || component.isEmpty()) {
            throw new RuntimeException("No SystemUIFactory component configured");
        }
        try {
            sInstance = (SystemUIFactory) context.getClassLoader()
                    .loadClass(component).newInstance();
        } catch (Throwable t) {
            Log.w(TAG, "Error creating SystemUIFactory component: " + component, t);
            throw new RuntimeException(t);
        }
    }
}
