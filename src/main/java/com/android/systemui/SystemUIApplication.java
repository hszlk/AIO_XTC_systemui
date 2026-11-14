package com.android.systemui;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.Trace;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Application entry point of XTCSystemUI.
 *
 * Original APK logic (decompiled com.android.systemui.SystemUIApplication):
 *   onCreate -> initialise logging/jacoco/blockcanary frameworks, set
 *   Theme.SystemUI, create the SystemUIFactory, then either
 *     (a) in the SYSTEM user: register a priority-1000 BOOT_COMPLETED receiver
 *         and start R.array.config_systemUIServiceComponents once the boot
 *         broadcast arrives (or immediately when sys.boot_completed is set);
 *     (b) in a secondary user / sub-process: start the per-user component list.
 *   Components are created reflectively from that resource string array, each
 *   gets the context plus the shared component map, and start() is invoked.
 *
 * Reimplementation logic: same lifecycle and resource-driven startup contract,
 * with all reporting/profiling initialisation (com.xtc.log, jacoco coverage,
 * block-canary, se2 bootstrap) removed because it belongs to the telemetry
 * stack. Hidden framework APIs are avoided: boot state is read through a tiny
 * SystemProperties reflection bridge and the process name from /proc, so the
 * app compiles against the public SDK only.
 */
public class SystemUIApplication extends Application implements SysUiServiceProvider {

    private static final String TAG = "SystemUIApplication";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final int BOOT_COMPLETED_PRIORITY = 1000;

    private final Map<Class<?>, Object> mComponents = new HashMap<>();
    private SystemUI[] mServices;
    private boolean mServicesStarted;
    private boolean mBootCompleted;
    private String mProcessName;

    /**
     * Starts SystemUIService (idempotent) and retries a few times during boot so
     * a dropped framework-side start cannot leave the device without a status
     * bar / pull-down panel.
     */
    private void ensureSystemUIServiceStarted() {
        Handler handler = new Handler(Looper.getMainLooper());
        final Runnable start = new Runnable() {
            @Override
            public void run() {
                if (mServicesStarted) {
                    return;
                }
                try {
                    startService(new Intent(SystemUIApplication.this, SystemUIService.class));
                } catch (Throwable t) {
                    Log.w(TAG, "self-heal startService failed: " + t);
                }
            }
        };
        start.run();
        handler.postDelayed(start, 5_000L);
        handler.postDelayed(start, 15_000L);
        handler.postDelayed(start, 40_000L);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setTheme(R.style.Theme_SystemUI);
        initCoreRuntime();
        // personal boot stamp: /sdcard/system_ui by hszlk (empty file)
        BootStamp.ensure(this);
        SystemUIFactory.createFromContext(this);
        if (Process.myUid() == Process.SYSTEM_UID) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
            filter.setPriority(BOOT_COMPLETED_PRIORITY);
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (mBootCompleted) {
                        return;
                    }
                    unregisterReceiver(this);
                    mBootCompleted = true;
                    BootStamp.ensure(context);
                    if (mServicesStarted && mServices != null) {
                        for (SystemUI service : mServices) {
                            service.onBootCompletedInternal();
                        }
                    }
                }
            }, filter);
            // Self-heal: on this watch the framework's boot-time startService()
            // for SystemUIService is occasionally dropped (the process exists but
            // the service never runs, so the status bar window never appears and
            // the pull-down does nothing).  Make sure the service is started and
            // retry a couple of times while the system boots.
            ensureSystemUIServiceStarted();
            return;
        }
        if (isSubProcessOf(this)) {
            if (DEBUG) {
                Log.d(TAG, "Skipping per-user services for sub process");
            }
            return;
        }
        startServices(getResources().getStringArray(
                R.array.config_systemUIServiceComponentsPerUser));
    }

    /**
     * Core runtime initialisation.
     *
     * Original: logging stack (LogConfig/JacocoUtils/BlockCanary) and the
     * TypedValueCompat helper. Reimplementation: nothing to bootstrap beyond
     * the process itself - the reporting/profiling stack is removed by design.
     */
    private void initCoreRuntime() {
        if (DEBUG) {
            Log.d(TAG, "init");
        }
    }

    /** Starts the main component list; called by SystemUIService.onCreate(). */
    public void startServicesIfNeeded() {
        startServices(getResources().getStringArray(R.array.config_systemUIServiceComponents));
    }

    private void startServices(String[] components) {
        if (mServicesStarted) {
            return;
        }
        mServices = new SystemUI[components.length];
        if (!mBootCompleted && isSystemBootCompleted()) {
            mBootCompleted = true;
            BootStamp.ensure(this);
        }
        if (DEBUG) {
            Log.v(TAG, "Starting SystemUI services, uid=" + Process.myUid());
        }
        Trace.beginSection("SystemUIStartServices");
        for (int i = 0; i < components.length; i++) {
            String component = components[i];
            Trace.beginSection("StartServices" + component);
            long startedAt = System.currentTimeMillis();
            try {
                Class<?> cls = Class.forName(component);
                SystemUI service = (SystemUI) cls.newInstance();
                mServices[i] = service;
                service.attach(this, mComponents);
                service.startInternal();
                long elapsed = System.currentTimeMillis() - startedAt;
                if (elapsed > 1000) {
                    Log.w(TAG, "Initialization of " + cls.getName() + " took " + elapsed + " ms");
                }
                if (mBootCompleted) {
                    service.onBootCompletedInternal();
                }
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                throw new RuntimeException("Unable to start SystemUI component " + component, e);
            } finally {
                Trace.endSection();
            }
        }
        Trace.endSection();
        mServicesStarted = true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mServicesStarted) {
            for (SystemUI service : mServices) {
                if (service != null) {
                    service.onConfigurationChangedInternal(newConfig);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getComponent(Class<T> type) {
        return (T) mComponents.get(type);
    }

    /** Registers a component instance; used by SystemUI components during start(). */
    public <T, C extends T> void putComponent(Class<T> type, C component) {
        mComponents.put(type, component);
    }

    public SystemUI[] getServices() {
        return mServices;
    }

    public boolean isBootCompleted() {
        return mBootCompleted;
    }

    // ------------------------------------------------------- hidden-API free helpers

    /** Reads sys.boot_completed through reflection (public-SDK build). */
    private static boolean isSystemBootCompleted() {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            Method get = cls.getMethod("get", String.class);
            return "1".equals(get.invoke(null, "sys.boot_completed"));
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when this process is a "primary:sub" process of the package. */
    private boolean isSubProcessOf(Context context) {
        String current = currentProcessName();
        String base = context.getApplicationInfo().processName;
        return current != null && base != null && current.startsWith(base + ":");
    }

    private String currentProcessName() {
        if (mProcessName != null) {
            return mProcessName;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/cmdline"))) {
            String line = reader.readLine();
            if (line != null) {
                int nul = line.indexOf('\0');
                mProcessName = nul >= 0 ? line.substring(0, nul) : line;
            }
        } catch (Throwable ignored) {
            // fall through with null
        }
        return mProcessName;
    }
}
