package com.android.systemui.vassistant;

import com.xtc.assistantapi.BaseDeviceModule;

import java.util.HashMap;

/**
 * Registry of directive handlers: {@code directive name -> handler}.
 *
 * Original APK logic: {@code com.android.systemui.vassistant.b} (SwitchControlHelper)
 * - a process singleton HashMap that the QS tiles populated with themselves and
 * the device module looked up on dispatch. In the rebuild no vendor QS tile is
 * rebuilt, so the handlers are registered by {@link SystemControlService} itself
 * and delegate to the flashlight/recording modules.
 */
public final class SwitchControlHelper {

    /** A directive handler for the switch-like payload protocol. */
    public interface SwitchHandler {
        /** Executes the directive; runs on the main thread. */
        void handle(SwitchPayload payload, BaseDeviceModule module);

        /** False when the feature is disabled by a (local) feature switch. */
        boolean isEnabled();
    }

    private static final SwitchControlHelper INSTANCE = new SwitchControlHelper();

    private final HashMap<String, SwitchHandler> mHandlers = new HashMap<>();

    private SwitchControlHelper() {
    }

    public static SwitchControlHelper getInstance() {
        return INSTANCE;
    }

    public synchronized void register(String directiveName, SwitchHandler handler) {
        if (directiveName == null || handler == null) {
            return;
        }
        mHandlers.put(directiveName, handler);
    }

    public synchronized void unregister(String directiveName) {
        if (directiveName != null) {
            mHandlers.remove(directiveName);
        }
    }

    public synchronized SwitchHandler find(String directiveName) {
        return directiveName == null ? null : mHandlers.get(directiveName);
    }
}
