package com.android.systemui;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Central dependency registry (a SystemUI component, started first).
 *
 * Original APK logic (decompiled `Dependency`): a SystemUI component holding an
 * eagerly-populated instance map plus a lazy factory map, with static accessors
 * get(Class) / getDependency(Class). Lazy entries included the background
 * looper, main and time-tick handlers, a leak-report e-mail address and several
 * controllers (each controller creation itself pulled in native/plugin code).
 *
 * Reimplementation logic: same registry semantics, only the runtime
 * dependencies that the retained (non-telemetry) business logic needs are
 * pre-registered - main looper/handler, background looper/handler, context.
 * The crash/leak-report dependencies are intentionally not present; network /
 * reporting oriented controllers are not registered at all.
 */
public class Dependency extends SystemUI {

    private static Dependency sInstance;

    private final Map<Object, Object> mInstances = new HashMap<>();
    private final Map<Object, Supplier<?>> mFactories = new HashMap<>();

    public static final DependencyKey<Looper> MAIN_LOOPER = new DependencyKey<>();
    public static final DependencyKey<Handler> MAIN_HANDLER = new DependencyKey<>();
    public static final DependencyKey<Looper> BACKGROUND_LOOPER = new DependencyKey<>();
    public static final DependencyKey<Handler> BACKGROUND_HANDLER = new DependencyKey<>();

    private HandlerThread mBackgroundThread;

    @Override
    protected void start() {
        sInstance = this;
        provide(MAIN_LOOPER, Looper::getMainLooper);
        provide(MAIN_HANDLER, () -> new Handler(Looper.getMainLooper()));
        provide(BACKGROUND_LOOPER, () -> {
            mBackgroundThread = new HandlerThread("sysui-bg",
                    android.os.Process.THREAD_PRIORITY_BACKGROUND);
            mBackgroundThread.start();
            return mBackgroundThread.getLooper();
        });
        provide(BACKGROUND_HANDLER, () -> new Handler((Looper) resolve(BACKGROUND_LOOPER)));
    }

    public static void setInstance(Object type, Object instance) {
        instance().mInstances.put(type, instance);
    }

    public static void provide(DependencyKey<?> key, Supplier<?> factory) {
        instance().mFactories.put(key, factory);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> type) {
        return (T) resolve(type);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(DependencyKey<T> key) {
        return (T) resolve(key);
    }

    public static boolean has(Class<?> type) {
        Dependency d = sInstance;
        return d != null && (d.mInstances.containsKey(type) || d.mFactories.containsKey(type));
    }

    public static void destroy() {
        Dependency d = sInstance;
        if (d != null) {
            d.mInstances.clear();
            d.mFactories.clear();
            if (d.mBackgroundThread != null) {
                d.mBackgroundThread.quitSafely();
                d.mBackgroundThread = null;
            }
            sInstance = null;
        }
    }

    private static Object resolve(Object key) {
        Dependency d = instance();
        Object o = d.mInstances.get(key);
        if (o == null) {
            Supplier<?> factory = d.mFactories.get(key);
            if (factory != null) {
                o = factory.get();
                d.mInstances.put(key, o);
            }
        }
        return o;
    }

    private static Dependency instance() {
        Dependency d = sInstance;
        if (d == null) {
            throw new IllegalStateException("Dependency component not started");
        }
        return d;
    }

    /** Unique, strongly typed dependency key for dependencies without a class token. */
    public static final class DependencyKey<T> {
        private final String mName;

        DependencyKey() {
            this(null);
        }

        DependencyKey(String name) {
            mName = name;
        }

        @Override
        public String toString() {
            return mName == null ? "DependencyKey" : mName;
        }
    }
}
