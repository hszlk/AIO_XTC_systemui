package com.android.systemui.vassistant;

/**
 * Assistant namespace / directive names handled by the system UI.
 *
 * (Original: {@code com.android.systemui.vassistant.a}, ApiConstants.)
 * The namespace and the directive names are matched against the
 * {@code assistantapi-directive} manifest metadata
 * {@code [FlashLight,VideoRecord]}, so they must stay verbatim.
 */
public final class ApiConstants {

    public static final String NAMESPACE =
            "ai.xtc.device_interface.extensions.system_ui_control";

    /** Directive name: turn the (screen) flashlight on/off, {@code target=query} supported. */
    public static final String NAME_FLASHLIGHT = "FlashLight";
    /** Directive name: start/stop screen recording. */
    public static final String NAME_VIDEO_RECORD = "VideoRecord";

    /** Reply param of the FlashLight query (original tile constant). */
    public static final String PARAM_FLASHLIGHT_ENABLED = "flashlightEnabled";

    private ApiConstants() {
    }
}
