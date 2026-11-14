package com.xtc.ui.widget.selfstart;

/**
 * Constants of the vendor self-start guidance module (decompiled
 * {@code com.xtc.ui.widget.selfstart.SelfStartConstant}). Package-private in the
 * vendor code; kept package-private here because only this package may need it.
 *
 * <p>Remote protocol recap (read-only, implemented by the launcher, not by us):</p>
 * <ul>
 *   <li>{@link #LAUNCHER_SELF_START_URI} - launcher provider; {@code call}
 *       {@link #METHOD_GET_PACKAGE} with a package name and read
 *       {@link #EXTRA_SELF_START_PACKAGE} from the bundle to test whether the app
 *       already has self-start permission;</li>
 *   <li>{@link #ACTION_SETTINGS_SELF_START} - vendor Settings activity that shows
 *       the self-start switch; the package is passed in
 *       {@link #EXTRA_PACKAGE_NAME}.</li>
 * </ul>
 */
interface SelfStartConstant {

    String ACTION_SETTINGS_SELF_START = "com.xtc.setting.action.SELF_START";
    String LAUNCHER_SELF_START_URI = "content://com.xtc.launcher.self.start";
    String METHOD_GET_PACKAGE = "METHOD_GET_PACKAGE";

    String EXTRA_CONTENT_TEXT = "EXTRA_CONTENT_TEXT";
    String EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME";
    String EXTRA_SELF_START_PACKAGE = "EXTRA_SELF_START_PACKAGE";
}
