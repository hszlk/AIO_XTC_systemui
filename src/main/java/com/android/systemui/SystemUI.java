package com.android.systemui;

import android.content.Context;
import android.content.res.Configuration;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Base class of every SystemUI component started from
 * {@code R.array.config_systemUIServiceComponents}.
 *
 * Original APK logic (decompiled `o` / SystemUI): abstract class holding the
 * context and a component map, with lifecycle hooks start(),
 * onConfigurationChanged(), onBootCompleted(), dump() plus putComponent().
 *
 * Reimplementation: same lifecycle contract. The map is the one owned by
 * SystemUIApplication, so components can look each other up through
 * {@link SysUiServiceProvider}.
 */
public abstract class SystemUI implements SysUiServiceProvider {

    protected Context mContext;
    private Map<Class<?>, Object> mComponents;
    private boolean mStarted;

    /** Called once when the component is instantiated by the application. */
    protected abstract void start();

    /** Called on configuration changes after start(). */
    protected void onConfigurationChanged(Configuration newConfig) {
    }

    /** Called once after boot completed (or immediately if boot already completed). */
    protected void onBootCompleted() {
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
    }

    protected final Map<Class<?>, Object> getComponentMap() {
        return mComponents;
    }

    final void attach(Context context, Map<Class<?>, Object> components) {
        mContext = context;
        mComponents = components;
    }

    final void startInternal() {
        if (!mStarted) {
            mStarted = true;
            start();
        }
    }

    final void onConfigurationChangedInternal(Configuration newConfig) {
        if (mStarted) {
            onConfigurationChanged(newConfig);
        }
    }

    final void onBootCompletedInternal() {
        if (mStarted) {
            onBootCompleted();
        }
    }

    final boolean isStarted() {
        return mStarted;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getComponent(Class<T> type) {
        return mComponents == null ? null : (T) mComponents.get(type);
    }

    protected final <T, C extends T> void putComponent(Class<T> type, C component) {
        if (mComponents != null) {
            mComponents.put(type, component);
        }
    }
}

