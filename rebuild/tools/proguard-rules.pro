# ProGuard/R8 rules for the obfuscated (hardened) build of the hand-written
# XTCSystemUI 1.8.00 replica.
#
# Goal: rename every class / method / field that is NOT referenced by a string
# at runtime, while keeping the surface that the Android framework and the
# vendor resource table resolve BY NAME:
#   * AndroidManifest.xml component class names (16 components)
#   * @string/config_statusBarComponent, @string/config_systemUIFactoryComponent
#   * @array/config_systemUIServiceComponents (reflection-instantiated)
#   * custom View class names referenced from the vendor layout XMLs
#     (super_status_bar / status_bar / xtc_status_bar_expanded / layout_pull_down)
#   * the appComponentFactory / FileProvider classes from the manifest
#
# Shrinking and optimization are disabled on purpose: the code is hand-written
# and small, the only goal here is name obfuscation without behaviour changes.

-dontshrink
-dontoptimize
-dontpreverify
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable,Exceptions,InnerClasses,EnclosingMethod,Signature,Annotation,Deprecated

# --- manifest components (16) ------------------------------------------------
-keep class com.android.systemui.SystemUIApplication { *; }
-keep class com.android.systemui.SystemUIService { *; }
-keep class com.android.systemui.statusbar.phone.StatusBar { *; }
-keep class com.android.systemui.flashlight.FlashLightActivity { *; }
-keep class com.android.systemui.flashlight.FlashLightSOSDialog { *; }
-keep class com.android.systemui.media.MediaProjectionPermissionActivity { *; }
-keep class com.xtc.architecture.mvp.PermissionListActivity { *; }
-keep class com.xtc.ui.widget.privacy.PrivacyUriActivity { *; }
-keep class com.xtc.ui.widget.selfstart.SelfStartActivity { *; }
-keep class com.android.systemui.vassistant.SystemControlService { *; }
-keep class com.android.systemui.screenrecord.RecordingService { *; }
-keep class com.xtc.statusbar.XtcNotificationListenerService { *; }
-keep class com.xtc.xtcoco.CodeCoverageReceiver { *; }
-keep class com.xtc.bigdata.monitor.receiver.MonitorStartReceiver { *; }
-keep class com.xtc.bigdata.collector.exception.CrashIntentService { *; }
-keep class com.android.systemui.statusbar.provider.StatusBarProxyProvider { *; }
-keep class com.xtc.dns.provider.HttpDnsContentProvider { *; }
-keep class androidx.core.content.FileProvider { *; }

# --- component factory ------------------------------------------------------
-keep class androidx.core.app.CoreComponentFactory { *; }

# --- reflective service list (@array/config_systemUIServiceComponents) ------
-keep class com.android.systemui.Dependency { *; }
-keep class com.android.systemui.SystemBars { *; }
-keep class com.android.systemui.SystemUIFactory { *; }
-keep class com.android.systemui.statusbar.CommandQueue$CommandQueueStart { *; }

# --- custom views inflated from vendor layout XML (name-based inflation) ----
-keep class com.android.systemui.statusbar.phone.StatusBarWindowView { *; }
-keep class com.android.systemui.statusbar.phone.AlphaOptimizedView { *; }
-keep class com.android.systemui.statusbar.phone.XTCPanelView { *; }
-keep class com.android.systemui.statusbar.phone.PhoneStatusBarView { *; }
-keep class com.android.systemui.statusbar.AlphaOptimizedView { *; }
-keep class com.xtc.statusbar.XtcStatusBarView { *; }
-keep class com.android.systemui.qs.QsOutCanScrollView { *; }
-keep class com.android.systemui.qs.QSPanel { *; }

# --- assistant directive protocol (namespace / payload names are strings) ---
-keep class com.xtc.assistantapi.** { *; }

# --- keep the view constructors for anything inflated by the framework ------
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
