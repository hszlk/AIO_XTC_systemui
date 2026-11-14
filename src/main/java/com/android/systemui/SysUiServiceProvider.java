package com.android.systemui;

import android.content.Context;

/**
 * Service-provider facade implemented by {@link SystemUIApplication}.
 *
 * Original APK logic (decompiled `n` / SysUiServiceProvider):
 *   interface with `getComponent(Class)` plus static helper that resolves the
 *   application and asks it for the component instance.
 *
 * Reimplementation logic: identical contract, clean names. Components are
 * registered by {@link SystemUI} instances into the application-wide map.
 */
public interface SysUiServiceProvider {

    <T> T getComponent(Class<T> type);

    static <T> T get(Context context, Class<T> type) {
        return ((SysUiServiceProvider) context.getApplicationContext()).getComponent(type);
    }
}
