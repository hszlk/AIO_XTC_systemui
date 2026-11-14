package com.android.systemui.flashlight;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Process-wide flashlight state shared by the flashlight activity, the SOS
 * activity and the assistant directive handler.
 *
 * Original APK logic: this state lived in {@code statusbar.policy.g}
 * (FlashlightController) with a listener list; on API 27 the implementation
 * ({@code statusbar.policy.j}, LegacyFlashlightControllerImpl) only keeps a
 * boolean and opens {@code FlashLightActivity}. The rebuild keeps the same
 * single-source-of-truth idea but hosts the state next to the UI it describes,
 * so the flashlight/assistant packages do not need the (not yet rebuilt)
 * statusbar.policy controller.
 *
 * Two independent flags are tracked, matching the original controller:
 *  - {@code illuminated}: the screen-flashlight activity is showing
 *  - {@code sos}: the SOS flashing mode is running
 * "isOn" for the assistant/tile query means either of them.
 */
public final class FlashlightStatus {

    /** Receives flashlight/sos state changes on the calling thread. */
    public interface Listener {
        void onFlashlightChanged(boolean illuminated, boolean sos);
    }

    private static final CopyOnWriteArrayList<Listener> sListeners = new CopyOnWriteArrayList<>();
    private static volatile boolean sIlluminated;
    private static volatile boolean sSos;

    private FlashlightStatus() {
    }

    public static boolean isIlluminated() {
        return sIlluminated;
    }

    public static boolean isSos() {
        return sSos;
    }

    public static boolean isOn() {
        return sIlluminated || sSos;
    }

    public static void setIlluminated(boolean on) {
        if (sIlluminated == on) {
            return;
        }
        sIlluminated = on;
        notifyListeners();
    }

    public static void setSos(boolean on) {
        if (sSos == on) {
            return;
        }
        sSos = on;
        notifyListeners();
    }

    public static void addListener(Listener listener) {
        if (listener != null) {
            sListeners.addIfAbsent(listener);
        }
    }

    public static void removeListener(Listener listener) {
        if (listener != null) {
            sListeners.remove(listener);
        }
    }

    private static void notifyListeners() {
        boolean illuminated = sIlluminated;
        boolean sos = sSos;
        for (Listener listener : sListeners) {
            listener.onFlashlightChanged(illuminated, sos);
        }
    }
}
