package com.android.systemui.statusbar.phone;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side bridge to the vendor keyguard service that lives in
 * {@code com.xtc.i3launcher} ({@code com.xtc.systemui.keyguard.KeyguardService}).
 *
 * <p><b>This package contains no AOSP Keyguard.</b> In XTC 1.8.00 the lock screen
 * is owned by the launcher; SystemUI is only an AIDL client. Reproduces the
 * decompiled {@code com.android.systemui.statusbar.f}
 * ({@code StatusBarKeyguardViewManagerImpl}) behavior exactly, including the
 * two-track pending-action design that governs "unlock, then do X":</p>
 *
 * <pre>
 * new KeyguardController(ctx) -&gt; ensureConnected(): binder dead/null -&gt; bindService()
 * onServiceConnected        -&gt; IKeyguardInterface.asInterface(binder)
 *                           -&gt; registerCallback(this)
 * onServiceDisconnected     -&gt; unregisterCallback(this) (null-guarded) -&gt; service = null
 * onKeyguardShown           -&gt; CLEAR the single pending slot AND the queue
 *                                   (user re-locked: the action is gone)
 * onKeyguardDismissed       -&gt; RUN the single slot, then RUN the queue, then clear both
 * dismissKeyguard(action)   -&gt; slot = action; if locked -&gt; requestDismissKeyguard();
 *                                   else run slot + queue immediately
 * postAfterUnlock(action)   -&gt; queue.add(action) (does NOT request an unlock)
 * </pre>
 *
 * <p>Differences from the vendor implementation that do not change wire behavior:
 * the pending actions and state listeners are dispatched on the main looper (the
 * vendor ran them on the binder thread, which is a
 * {@code CalledFromWrongThreadException} hazard for window/UI work), and
 * {@code unregisterCallback} is null-guarded (the vendor called it on an
 * already-cleared service reference, which raised an NPE that was swallowed).</p>
 *
 * <p>Wiring (done by the Lead, not by this file): register the singleton in
 * {@code Dependency}, call {@link #ensureConnected()} from the "launcher
 * completed"/"panel expanded" hooks (the vendor did the same to recover from a
 * launcher restart), and replace direct {@code ActivityStarter} /
 * {@code postQSRunnableDismissingKeyguard} bodies with
 * {@link #dismissKeyguard(Runnable)}. Everything degrades conservatively when
 * the launcher is absent: {@link #isKeyguardLocked()} returns {@code true},
 * matching the vendor behavior.</p>
 */
public class KeyguardController extends IKeyguardCallbackInterface.Stub {

    private static final String TAG = "KeyguardController";

    /** Vendor service action (the Intent action is the service component name). */
    public static final String ACTION_KEYGUARD_SERVICE =
            "com.xtc.systemui.keyguard.KeyguardService";
    /** Vendor package that hosts the keyguard service. */
    public static final String KEYGUARD_PACKAGE = "com.xtc.i3launcher";
    /**
     * Permission the launcher-side service enforces; declared in the manifest as
     * a uses-permission ({@code com.xtc.systemui.keyguard.permission.KEYGUARD_SERVICE}).
     */
    public static final String PERMISSION_KEYGUARD_SERVICE =
            "com.xtc.systemui.keyguard.permission.KEYGUARD_SERVICE";

    /** Listener for people who want "did the keyguard go away / come back". */
    public interface KeyguardStateListener {
        /**
         * @param locked {@code true} after {@code onKeyguardShown} (screen locked),
         *               {@code false} after {@code onKeyguardDismissed} (unlocked).
         */
        void onKeyguardStateChanged(boolean locked);
    }

    private static volatile KeyguardController sInstance;

    private final Context mContext;
    private final Handler mMainHandler;
    private final Object mPendingLock = new Object();
    private final ArrayList<Runnable> mAfterUnlockQueue = new ArrayList<>(1);
    private final ArrayList<KeyguardStateListener> mStateListeners = new ArrayList<>(1);

    /** {@code f980a} in the vendor code - the remote keyguard service. */
    private volatile IKeyguardInterface mKeyguardService;
    /** {@code f981a} in the vendor code - the single "run after unlock" slot. */
    private Runnable mPendingAction;
    /** Bound-state bookkeeping (vendor ignores this and may double-bind; harmless). */
    private volatile boolean mBound;
    private Boolean mLastReportedLocked;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "onServiceConnected: " + name);
            IKeyguardInterface service = IKeyguardInterface.Stub.asInterface(binder);
            mKeyguardService = service;
            if (service == null) {
                Log.e(TAG, "onServiceConnected: asInterface returned null");
                return;
            }
            try {
                service.registerCallback(KeyguardController.this);
            } catch (Exception e) {
                Log.e(TAG, "onServiceConnected#registerCallback error: " + e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected: " + name);
            IKeyguardInterface service = mKeyguardService;
            if (service != null) {
                try {
                    service.unregisterCallback(KeyguardController.this);
                } catch (Exception e) {
                    Log.e(TAG, "onServiceDisconnected#unregisterCallback error: " + e);
                }
            }
            mKeyguardService = null;
            mBound = false;
        }
    };

    private KeyguardController(Context context) {
        Context app = context.getApplicationContext();
        mContext = app != null ? app : context;
        mMainHandler = new Handler(Looper.getMainLooper());
        // Vendor constructor immediately tries to connect.
        ensureConnected();
    }

    /**
     * Returns the process-wide controller, creating + connecting it on first use.
     * Safe to call from any thread.
     */
    public static KeyguardController get(Context context) {
        KeyguardController instance = sInstance;
        if (instance == null) {
            synchronized (KeyguardController.class) {
                instance = sInstance;
                if (instance == null) {
                    instance = new KeyguardController(context);
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    /** Returns the singleton if it was already created, otherwise {@code null}. */
    public static KeyguardController peek() {
        return sInstance;
    }

    // ------------------------------------------------------------- connection

    /**
     * Vendor {@code a()} / {@code c()}: ping the binder and re-bind when it is
     * dead (launcher restart recovery). Call it from the same hooks the vendor
     * used (launcher-launched broadcast, panel expansion).
     */
    public void ensureConnected() {
        IKeyguardInterface service = mKeyguardService;
        boolean alive = false;
        if (service != null) {
            try {
                IBinder binder = service.asBinder();
                alive = binder != null && binder.pingBinder();
            } catch (Throwable t) {
                Log.e(TAG, "pingRemoteService error: " + t);
            }
        }
        if (!alive) {
            bindToKeyguardService();
        }
    }

    private void bindToKeyguardService() {
        try {
            Intent intent = new Intent(ACTION_KEYGUARD_SERVICE);
            intent.setPackage(KEYGUARD_PACKAGE);
            boolean bound = mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            mBound = bound;
            if (!bound) {
                Log.d(TAG, "bindService returned false (keyguard service unavailable)");
            }
        } catch (Exception e) {
            Log.e(TAG, "bindService error: " + e);
        }
    }

    /** True when the remote binder is currently connected. */
    public boolean isServiceConnected() {
        IKeyguardInterface service = mKeyguardService;
        if (service == null) {
            return false;
        }
        try {
            IBinder binder = service.asBinder();
            return binder != null && binder.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Drops the connection and clears pending state (process teardown). */
    public void release() {
        synchronized (mPendingLock) {
            mPendingAction = null;
            mAfterUnlockQueue.clear();
        }
        synchronized (mStateListeners) {
            mStateListeners.clear();
        }
        if (mBound) {
            try {
                mContext.unbindService(mConnection);
            } catch (Exception e) {
                Log.e(TAG, "unbindService error: " + e);
            }
            mBound = false;
        }
        mKeyguardService = null;
        sInstance = null;
    }

    // ---------------------------------------------------------------- queries

    /**
     * Vendor {@code mo507a()}: is the screen locked?
     * <b>Conservative fallback</b>: when the service is not connected, or the
     * transaction fails, returns {@code true} (treat as locked) - exactly like
     * the vendor code. Callers that "dismiss then run" therefore never skip the
     * unlock step; the cost is that on a device without the launcher those
     * actions stay pending forever.
     */
    public boolean isKeyguardLocked() {
        IKeyguardInterface service = mKeyguardService;
        try {
            if (service != null) {
                return service.isKeyguardLocked();
            }
            Log.d(TAG, "isLocked: keyguardService is null");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "isLocked: error: " + e);
            return true;
        }
    }

    /**
     * Vendor {@code mo508b()}: is the lock screen secure (PIN/pattern)?
     * Same {@code true} fallback as {@link #isKeyguardLocked()}.
     */
    public boolean isKeyguardSecure() {
        IKeyguardInterface service = mKeyguardService;
        try {
            if (service != null) {
                return service.isKeyguardSecure();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "isSecure: error: " + e);
            return true;
        }
    }

    // --------------------------------------------------------- pending actions

    /**
     * Vendor {@code a(Runnable)}: "execute this after the keyguard is dismissed".
     *
     * <p>Stores the action in the <b>single slot</b> {@code f981a} (a second call
     * replaces the first, exactly like the vendor code), then either requests an
     * unlock - the action runs from {@code onKeyguardDismissed} - or, when
     * already unlocked, runs the slot + queue immediately.</p>
     */
    public void dismissKeyguard(Runnable afterUnlock) {
        synchronized (mPendingLock) {
            mPendingAction = afterUnlock;
        }
        boolean locked = isKeyguardLocked();
        Log.d(TAG, "dismissKeyguard: isLocked " + locked);
        if (!locked) {
            Log.d(TAG, "dismissKeyguard: already unlocked, running pending actions");
            runPendingActions();
            return;
        }
        IKeyguardInterface service = mKeyguardService;
        if (service == null) {
            Log.d(TAG, "dismissKeyguard: keyguardService is null, action kept pending");
            return;
        }
        try {
            service.requestDismissKeyguard();
        } catch (Exception e) {
            Log.e(TAG, "dismissKeyguard requestDismissKeyguard error: " + e);
        }
    }

    /**
     * Vendor {@code b(Runnable)}: append to the <b>queue</b> {@code f982a} that is
     * drained only after a keyguard dismissal. Does not request an unlock.
     */
    public void postAfterUnlock(Runnable action) {
        if (action == null) {
            return;
        }
        synchronized (mPendingLock) {
            mAfterUnlockQueue.add(action);
        }
    }

    /** True when an action is waiting in the single slot or in the queue. */
    public boolean hasPendingActions() {
        synchronized (mPendingLock) {
            return mPendingAction != null || !mAfterUnlockQueue.isEmpty();
        }
    }

    // --------------------------------------------------- callback from service

    @Override
    public void onKeyguardDismissed() throws RemoteException {
        Log.d(TAG, "onKeyguardDismissed");
        runPendingActions();
        notifyKeyguardStateChanged(false);
    }

    @Override
    public void onKeyguardShown() throws RemoteException {
        Log.d(TAG, "onKeyguardShown");
        synchronized (mPendingLock) {
            mPendingAction = null;
            mAfterUnlockQueue.clear();
        }
        notifyKeyguardStateChanged(true);
    }

    /**
     * Vendor {@code b()}: run the slot first, then the queue; each action runs at
     * most once and both containers are cleared before dispatching so a throwing
     * action cannot leave the queue half-drained.
     */
    private void runPendingActions() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runPendingActionsOnMain();
        } else {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    runPendingActionsOnMain();
                }
            });
        }
    }

    private void runPendingActionsOnMain() {
        Runnable slot;
        List<Runnable> queued;
        synchronized (mPendingLock) {
            slot = mPendingAction;
            mPendingAction = null;
            queued = new ArrayList<>(mAfterUnlockQueue);
            mAfterUnlockQueue.clear();
        }
        runSafely(slot);
        for (int i = 0; i < queued.size(); i++) {
            runSafely(queued.get(i));
        }
    }

    private static void runSafely(Runnable action) {
        if (action == null) {
            return;
        }
        try {
            action.run();
        } catch (Throwable t) {
            Log.e(TAG, "pending keyguard action failed", t);
        }
    }

    // ------------------------------------------------------------- listeners

    /** Registers a state listener (called with {@code false}=unlocked / {@code true}=locked). */
    public void registerKeyguardStateListener(KeyguardStateListener listener) {
        if (listener == null) {
            return;
        }
        synchronized (mStateListeners) {
            if (!mStateListeners.contains(listener)) {
                mStateListeners.add(listener);
            }
        }
    }

    public void unregisterKeyguardStateListener(KeyguardStateListener listener) {
        synchronized (mStateListeners) {
            mStateListeners.remove(listener);
        }
    }

    private void notifyKeyguardStateChanged(final boolean locked) {
        // Dispatch on the main thread: listeners touch views/windows.
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                Boolean last = mLastReportedLocked;
                mLastReportedLocked = locked;
                if (last != null && last == locked) {
                    return;
                }
                KeyguardStateListener[] listeners;
                synchronized (mStateListeners) {
                    listeners = mStateListeners.toArray(new KeyguardStateListener[0]);
                }
                for (KeyguardStateListener listener : listeners) {
                    try {
                        listener.onKeyguardStateChanged(locked);
                    } catch (Throwable t) {
                        Log.e(TAG, "keyguard state listener failed", t);
                    }
                }
            }
        });
    }
}
