package com.android.systemui;

/**
 * Build-time constants of the reimplemented XTCSystemUI.
 *
 * Value parity with the original 1.8.00 build is intentional (the original
 * BuildConfig is part of the APK's observable surface: log tags, module names
 * and version strings are read by sibling system apps).
 *
 * Reimplementation notes:
 *  - no analytics / crash-reporting / remote-config fields are kept;
 *  - the original file also carried Jacobi/blockcanary hooks - dropped on purpose.
 */
public final class BuildConfig {
    public static final boolean DEBUG = false;
    public static final int VERSION_CODE = 18000;
    public static final String APPLICATION_ID = "com.android.systemui";
    public static final boolean IS_RELEASE = true;
    public static final String BUILD_TYPE = "release";
    public static final String FLAVOR = "xhdpiApi25";
    public static final String VERSION_NAME = "1.8.00.Develop_AW_XtcSystemUI_J168_Gc23339b";
    public static final String DENSITY = "xhdpi";
    public static final String API_LEVEL = "api25";
    public static final String GIT_COMMIT = "14a9443";
    public static final String BUILD_ID = "c23339b";
    public static final String MODULE = "Develop_AW_XtcSystemUI";

    private BuildConfig() {
    }
}
