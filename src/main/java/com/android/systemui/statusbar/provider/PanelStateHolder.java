package com.android.systemui.statusbar.provider;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Process-local snapshot of the status-bar panel state, published for
 * {@link StatusBarProxyProvider} and refreshed by the status-bar/panel code.
 *
 * <p>Why this class exists: the vendor provider reads the live {@code StatusBar}
 * instance and calls its package-private obfuscated getters
 * ({@code m559k()}/{@code m558j()}/{@code l()}/{@code m557i()}). Those getters are
 * derived from the pull-down {@code PanelView} state, are not public, and the
 * original names are ambiguous after JADX de-obfuscation. This holder is the
 * stable, public contract instead: the status-bar/panel code publishes the two
 * booleans, and the provider only reads them.</p>
 *
 * <h3>Semantics (mirror of the vendor mapping, see SPEC §B.4.1)</h3>
 * <ul>
 *   <li>{@link #setExpanded(boolean) expanded} - {@code StatusBar.m559k()} inverse:
 *       {@code true} while the pull-down is open or opening / "not fully
 *       collapsed". Provider method {@code isStatusBarExpanded} returns this.</li>
 *   <li>{@link #setFullyExpanded(boolean) fullyExpanded} -
 *       {@code StatusBar.l() && StatusBar.m557i()}: the panel is fully expanded
 *       <b>and</b> the status-bar window is visible. Provider method
 *       {@code isStatusBarFullyExpanded} (and the unknown-method fallback)
 *       returns this.</li>
 *   <li>{@link #setImState(boolean) imState} - IM online state delivered by the
 *       launcher through {@code onImStateChanged}; the vendor code fed it to
 *       {@code com.xtc.baseservice.e.a} (IMStateManager). No IM manager is kept
 *       in this rebuild (it was vendor business logic); the value is retained
 *       here so the wiring can fan it out later.</li>
 * </ul>
 *
 * <h3>Registration points for the Lead</h3>
 * <pre>
 * // once, when the panel view is created (e.g. StatusBar.inflateStatusBarWindow)
 * PanelStateHolder.setCollapseCallback(() -&gt; mPanelView.collapse());
 *
 * // every time the panel expansion changes (XTCPanelView.PanelStateListener)
 * PanelStateHolder.setExpanded(expanded);
 * PanelStateHolder.setFullyExpanded(expanded);
 * </pre>
 *
 * <p>All accessors are thread-safe/volatile: the provider's {@code call()} runs on
 * a binder thread while the panel publishes from the UI thread.</p>
 */
public final class PanelStateHolder {

    private static final String TAG = "PanelStateHolder";

    /** Collapse handler registered by the panel host. */
    public interface CollapseCallback {
        /** Collapse the pull-down panel; called on the main thread. */
        void onCollapseRequested();
    }

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static volatile boolean sExpanded;
    private static volatile boolean sFullyExpanded;
    private static volatile boolean sImOnline;
    private static volatile CollapseCallback sCollapseCallback;

    private PanelStateHolder() {
    }

    // ------------------------------------------------------------------ panel

    /**
     * Publishes "panel is not fully collapsed". Setting {@code false} also clears
     * {@link #isFullyExpanded()} (a collapsed panel cannot be fully expanded).
     */
    public static void setExpanded(boolean expanded) {
        sExpanded = expanded;
        if (!expanded) {
            sFullyExpanded = false;
        }
    }

    /** Publishes "panel is fully expanded and its window is visible". */
    public static void setFullyExpanded(boolean fullyExpanded) {
        sFullyExpanded = fullyExpanded;
        if (fullyExpanded) {
            sExpanded = true;
        }
    }

    /**
     * Convenience for a single call from the panel listener:
     * {@code setPanelState(expanded, expanded)}.
     */
    public static void setPanelState(boolean expanded, boolean fullyExpanded) {
        sExpanded = expanded;
        sFullyExpanded = expanded && fullyExpanded;
    }

    public static boolean isExpanded() {
        return sExpanded;
    }

    public static boolean isFullyExpanded() {
        return sFullyExpanded;
    }

    // ---------------------------------------------------------------- im state

    /** IM online state from the launcher's {@code onImStateChanged} call. */
    public static void setImState(boolean online) {
        sImOnline = online;
    }

    /** Alias kept close to the provider's wire name. */
    public static void setImOnline(boolean online) {
        sImOnline = online;
    }

    public static boolean isImOnline() {
        return sImOnline;
    }

    // --------------------------------------------------------------- collapse

    /** Registers the panel-collapse entry point used by {@code collapsePanel}. */
    public static void setCollapseCallback(CollapseCallback callback) {
        sCollapseCallback = callback;
    }

    /** Convenience overload for a plain {@link Runnable}. */
    public static void setCollapseCallback(final Runnable collapseAction) {
        sCollapseCallback = collapseAction == null ? null : new CollapseCallback() {
            @Override
            public void onCollapseRequested() {
                collapseAction.run();
            }
        };
    }

    /**
     * Asks the panel to collapse. Posts to the main thread (the vendor
     * {@code StatusBar.r()} was also asynchronous) and is a no-op when no host
     * registered yet.
     */
    public static void requestCollapse() {
        final CollapseCallback callback = sCollapseCallback;
        if (callback == null) {
            Log.d(TAG, "collapsePanel ignored: no panel host registered");
            return;
        }
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                CollapseCallback current = sCollapseCallback;
                if (current == null) {
                    return;
                }
                try {
                    current.onCollapseRequested();
                } catch (Throwable t) {
                    Log.e(TAG, "collapse callback failed", t);
                }
            }
        });
    }

    /** Clears every published flag and the callback (process teardown / tests). */
    public static void reset() {
        sExpanded = false;
        sFullyExpanded = false;
        sImOnline = false;
        sCollapseCallback = null;
    }
}
