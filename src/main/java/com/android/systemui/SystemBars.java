package com.android.systemui;

import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Owns and starts the status bar component configured by
 * {@code @string/config_statusBarComponent}.
 *
 * Original APK logic (decompiled SystemBars): a SystemUI component that loads
 * the configured class name from resources, instantiates it, attaches context
 * and component map, calls start(); dump() delegates to the status bar; any
 * failure is turned into a RuntimeException (SystemUI must not run without a
 * status bar).
 *
 * Reimplementation logic: identical contract. A missing/empty configuration is
 * still fatal (matches original).
 */
public class SystemBars extends SystemUI {

    private static final String TAG = "SystemBars";

    private SystemUI mStatusBar;

    @Override
    protected void start() {
        String component = mContext.getString(R.string.config_statusBarComponent);
        if (component == null || component.isEmpty()) {
            throw failure("No status bar component configured", null);
        }
        try {
            Class<?> cls = mContext.getClassLoader().loadClass(component);
            SystemUI statusBar = (SystemUI) cls.newInstance();
            mStatusBar = statusBar;
            statusBar.attach(mContext, getComponentMap());
            statusBar.startInternal();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            throw failure("Error creating status bar component: " + component, e);
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mStatusBar != null) {
            mStatusBar.dump(fd, pw, args);
        }
    }

    private RuntimeException failure(String message, Throwable cause) {
        Log.w(TAG, message, cause);
        return new RuntimeException(message, cause);
    }
}
