package com.android.systemui.statusbar;

import com.android.systemui.SystemUI;

/**
 * Status-bar service callback funnel.
 *
 * Original APK logic: a component registered by the nested CommandQueueStart
 * (listed in config_systemUIServiceComponents) that implements the IStatusBar
 * callback surface (disable flags, panel expand/collapse, notification
 * add/remove, IME state, navigation keys, screenshot requests, ...) and routes
 * them to the status bar/panel.
 *
 * Wave-1 reimplementation: the component and registration are in place so the
 * resource-driven component list starts correctly. The callback set is
 * implemented in the status bar wave against our own panel controller.
 */
public class CommandQueue extends SystemUI {

    public static class CommandQueueStart extends SystemUI {
        @Override
        protected void start() {
            putComponent(CommandQueue.class, new CommandQueue());
        }
    }

    @Override
    protected void start() {
        // Wired in the status-bar wave.
    }
}
