package com.android.systemui.qs;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.android.systemui.flashlight.FlashLightActivity;
import com.android.systemui.screenrecord.RecordingService;

import java.lang.reflect.Method;

/**
 * Back-end for the 12 pull-down shortcuts (user's latest UI spec).
 *
 * Layout:
 *   - the "quick cube" (white pill block) holds the row-1 tiles:
 *       Wi-Fi / mobile data / Bluetooth / hotspot            (indices 0-3)
 *   - the two round tile rows below hold:
 *       flashlight / screen record / eye comfort / keep awake
 *       airplane / power save / do not disturb / rotation lock  (indices 4-11)
 *
 * Hotspot uses the same layered strategy as the reference Xposed module
 * (DC_xpos / Xposed Edge): hidden ConnectivityManager.startTethering() first,
 * then WifiManager.setWifiApEnabled(null, enabled) as the fallback.
 *
 * Framework-only (reflection where the API is hidden on 8.1); no vendor SDK,
 * no reporting, no network I/O.
 */
public class QsToggles {

    private static final String TAG = "QsToggles";

    public static final int TILE_WIFI = 0;
    public static final int TILE_MOBILE_DATA = 1;
    public static final int TILE_BLUETOOTH = 2;
    public static final int TILE_HOTSPOT = 3;
    public static final int TILE_FLASHLIGHT = 4;
    public static final int TILE_SCREEN_RECORD = 5;
    public static final int TILE_EYE_COMFORT = 6;
    public static final int TILE_KEEP_AWAKE = 7;
    public static final int TILE_AIRPLANE = 8;
    /** Multi-touch visualisation (touch points + pointer coords), replaces power save. */
    public static final int TILE_MULTI_TOUCH = 9;
    public static final int TILE_DND = 10;
    public static final int TILE_ROTATION = 11;
    public static final int TILE_COUNT = 12;

    /** Function names shown under the balls while the drag-edit mode is active. */
    private static final String[] TILE_LABELS = {
            "WiFi", "移动数据", "蓝牙", "热点",
            "手电筒", "录屏", "护眼", "保持亮屏",
            "飞行", "多点触控", "勿扰", "旋转",
    };

    /** Display name of a shortcut tile (used by the edit-mode captions). */
    public static String label(int tileIndex) {
        if (tileIndex < 0 || tileIndex >= TILE_LABELS.length) {
            return "";
        }
        return TILE_LABELS[tileIndex];
    }

    /** Eye-comfort overlay tint (warm amber, ~20% alpha). */
    /** Pure warm amber; the strength is the View alpha (clear, not hazy). */
    private static final int EYE_COMFORT_COLOR = 0xFFFFC979;
    private static final float EYE_COMFORT_STRENGTH = 0.20f;

    private static final String PREF = "qs_toggles";
    private static final String KEY_EYE = "eye_comfort";
    private static final String KEY_AWAKE = "keep_awake";
    /** Saved SCREEN_OFF_TIMEOUT while keep-awake is on (restored on disable). */
    private static final String KEY_TIMEOUT_BACKUP = "screen_timeout_backup";
    /** Keep-awake screen timeout: 7 days (adb "settings put system" trick). */
    private static final int KEEP_AWAKE_TIMEOUT_MS = 7 * 24 * 60 * 60 * 1000;

    public interface Listener {
        void onToggleStateChanged(int index, boolean on);
    }

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Context mAppContext;
    private final SharedPreferences mPrefs;

    private WifiManager mWifiManager;
    private BluetoothAdapter mBluetoothAdapter;
    private TelephonyManager mTelephony;
    private PowerManager mPowerManager;
    private AudioManager mAudioManager;
    private View mEyeOverlay;
    private Listener mListener;
    private boolean mRegistered;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshAll();
        }
    };

    private final Runnable mPollTask = new Runnable() {
        @Override
        public void run() {
            refreshAll();
            mHandler.postDelayed(this, 10_000L);
        }
    };

    public QsToggles(Context context) {
        mAppContext = context.getApplicationContext();
        mContext = context;
        mPrefs = mAppContext.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        mWifiManager = (WifiManager) mAppContext.getSystemService(Context.WIFI_SERVICE);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mTelephony = (TelephonyManager) mAppContext.getSystemService(Context.TELEPHONY_SERVICE);
        mPowerManager = (PowerManager) mAppContext.getSystemService(Context.POWER_SERVICE);
        mAudioManager = (AudioManager) mAppContext.getSystemService(Context.AUDIO_SERVICE);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void register() {
        if (mRegistered) {
            return;
        }
        mRegistered = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction("android.app.action.INTERRUPTION_FILTER_CHANGED");
        try {
            mAppContext.registerReceiver(mReceiver, filter);
        } catch (Throwable t) {
            Log.w(TAG, "register receiver failed: " + t);
        }
        if (mPrefs.getBoolean(KEY_EYE, false)) {
            setEyeComfortInternal(true);
        }
        if (mPrefs.getBoolean(KEY_AWAKE, false)) {
            setKeepAwakeInternal(true);
        }
        refreshAll();
        mHandler.postDelayed(mPollTask, 10_000L);
    }

    public void unregister() {
        mRegistered = false;
        mHandler.removeCallbacksAndMessages(null);
        try {
            mAppContext.unregisterReceiver(mReceiver);
        } catch (Throwable ignored) {
        }
        removeEyeOverlay();
        // keep-awake survives QS panel rebuilds: re-apply the long timeout
        if (mPrefs.getBoolean(KEY_AWAKE, false)) {
            setKeepAwakeInternal(true);
        }
    }

    public int getTileCount() {
        return TILE_COUNT;
    }

    public boolean isOn(int index) {
        switch (index) {
            case TILE_WIFI:
                return mWifiManager != null && mWifiManager.isWifiEnabled();
            case TILE_MOBILE_DATA:
                return isMobileDataEnabled();
            case TILE_BLUETOOTH:
                return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
            case TILE_HOTSPOT:
                return isHotspotEnabled();
            case TILE_FLASHLIGHT:
                try {
                    return FlashLightActivity.isOn();
                } catch (Throwable t) {
                    return false;
                }
            case TILE_SCREEN_RECORD:
                return RecordingService.isRecording();
            case TILE_EYE_COMFORT:
                return mPrefs.getBoolean(KEY_EYE, false);
            case TILE_KEEP_AWAKE:
                return mPrefs.getBoolean(KEY_AWAKE, false);
            case TILE_AIRPLANE:
                return Settings.Global.getInt(mAppContext.getContentResolver(),
                        Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
            case TILE_MULTI_TOUCH:
                return isMultiTouchOn();
            case TILE_DND:
                return isDndEnabled();
            case TILE_ROTATION:
                return isRotationAuto();
            default:
                return false;
        }
    }

    public void toggle(int index) {
        boolean target = !isOn(index);
        switch (index) {
            case TILE_WIFI:
                if (mWifiManager != null) {
                    mWifiManager.setWifiEnabled(target);
                }
                break;
            case TILE_MOBILE_DATA:
                setMobileDataEnabled(target);
                break;
            case TILE_BLUETOOTH:
                if (mBluetoothAdapter != null) {
                    if (target) {
                        mBluetoothAdapter.enable();
                    } else {
                        mBluetoothAdapter.disable();
                    }
                }
                break;
            case TILE_HOTSPOT:
                setHotspotEnabled(target);
                break;
            case TILE_FLASHLIGHT:
                try {
                    FlashLightActivity.toggle(mContext);
                } catch (Throwable t) {
                    Log.w(TAG, "flashlight toggle failed: " + t);
                }
                break;
            case TILE_SCREEN_RECORD:
                try {
                    RecordingService.toggle(mContext);
                } catch (Throwable t) {
                    Log.w(TAG, "screen record toggle failed: " + t);
                }
                break;
            case TILE_EYE_COMFORT:
                mPrefs.edit().putBoolean(KEY_EYE, target).apply();
                setEyeComfortInternal(target);
                notifyChanged(index, target);
                return;
            case TILE_KEEP_AWAKE:
                mPrefs.edit().putBoolean(KEY_AWAKE, target).apply();
                setKeepAwakeInternal(target);
                notifyChanged(index, target);
                return;
            case TILE_AIRPLANE:
                setAirplaneMode(target);
                break;
            case TILE_MULTI_TOUCH:
                setMultiTouch(target);
                break;
            case TILE_DND:
                setDndEnabled(target);
                break;
            case TILE_ROTATION:
                setRotationAuto(target);
                break;
            default:
                return;
        }
        notifyChanged(index, target);
    }

    public void refreshAll() {
        if (mListener != null) {
            for (int i = 0; i < TILE_COUNT; i++) {
                mListener.onToggleStateChanged(i, isOn(i));
            }
        }
    }

    private void notifyChanged(int index, boolean on) {
        if (mListener != null) {
            mListener.onToggleStateChanged(index, on);
        }
    }

    // ------------------------------------------------------------- airplane

    private void setAirplaneMode(boolean on) {
        try {
            Settings.Global.putInt(mAppContext.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, on ? 1 : 0);
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", on);
            mAppContext.sendBroadcast(intent);
        } catch (Throwable t) {
            Log.w(TAG, "airplane toggle failed: " + t);
        }
    }

    // ----------------------------------------------------------- multi touch

    /**
     * The ROM's two-finger screenshot gesture is driven by a system property:
     *   persist.sys.xtcscreencut = 0  -> multi-touch ON  (screenshot gesture off)
     *   persist.sys.xtcscreencut = 1  -> multi-touch OFF (screenshot gesture on)
     * SystemUI runs as the system uid, so it can flip the property directly.
     */
    private static final String PROP_SCREENCUT = "persist.sys.xtcscreencut";

    private String getProp(String key, String defValue) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get =
                    sp.getMethod("get", String.class, String.class);
            Object v = get.invoke(null, key, defValue);
            return v instanceof String ? (String) v : defValue;
        } catch (Throwable t) {
            Log.w(TAG, "getprop " + key + " failed: " + t);
            return defValue;
        }
    }

    private void setProp(String key, String value) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method set =
                    sp.getMethod("set", String.class, String.class);
            set.invoke(null, key, value);
            Log.i(TAG, "setprop " + key + " = " + value);
        } catch (Throwable t) {
            Log.w(TAG, "setprop " + key + " failed: " + t);
        }
    }

    private boolean isMultiTouchOn() {
        // 0 means the screenshot gesture is disabled => multi-touch works
        return "0".equals(getProp(PROP_SCREENCUT, "1"));
    }

    private void setMultiTouch(boolean enabled) {
        setProp(PROP_SCREENCUT, enabled ? "0" : "1");
        // keep the local mirror so the tile shows the last known state instantly
        mPrefs.edit().putBoolean("mt_state", enabled).apply();
    }

    // ------------------------------------------------------------ power save

    private void setPowerSaveMode(boolean enabled) {
        try {
            Method m = PowerManager.class.getDeclaredMethod("setPowerSaveMode", boolean.class);
            m.setAccessible(true);
            m.invoke(mPowerManager, enabled);
            return;
        } catch (Throwable t) {
            Log.w(TAG, "setPowerSaveMode unavailable: " + t);
        }
        try {
            Settings.Global.putInt(mAppContext.getContentResolver(), "low_power", enabled ? 1 : 0);
            mAppContext.sendBroadcast(new Intent("android.os.action.POWER_SAVE_MODE_CHANGED"));
        } catch (Throwable t2) {
            Log.w(TAG, "low_power fallback failed: " + t2);
        }
    }

    // ------------------------------------------------------- do not disturb

    /**
     * Real system DND (user request: new messages must NOT pop the top-left
     * banner). INTERRUPTION_FILTER_ALARMS suppresses notification banners and
     * sounds while alarms keep ringing (kids-watch requirement). The vendor
     * launcher honours zen mode, so its message banner stays hidden too.
     */
    private boolean isDndEnabled() {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager)
                    mAppContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                int filter = nm.getCurrentInterruptionFilter();
                return filter == android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
                        || filter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE;
            }
        } catch (Throwable ignored) {
        }
        if (mAudioManager != null) {
            try {
                return mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private void setDndEnabled(boolean enabled) {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager)
                    mAppContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                // user spec: DND ONLY suppresses notifications (banners etc.),
                // sounds/ringer stay untouched -> FILTER_ALARMS keeps alarms
                // ringing and is the lightest system zen level that hides
                // notification popups
                nm.setInterruptionFilter(enabled
                        ? android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
                        : android.app.NotificationManager.INTERRUPTION_FILTER_ALL);
            }
        } catch (Throwable t) {
            Log.w(TAG, "dnd toggle failed: " + t);
        }
    }

    // --------------------------------------------------------- rotation lock

    private boolean isRotationAuto() {
        try {
            return Settings.System.getInt(mAppContext.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
        } catch (Throwable t) {
            return false;
        }
    }

    private void setRotationAuto(boolean auto) {
        ContentResolver cr = mAppContext.getContentResolver();
        try {
            if (auto) {
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1);
            } else {
                int rotation = 0;
                try {
                    WindowManager wm = (WindowManager) mAppContext
                            .getSystemService(Context.WINDOW_SERVICE);
                    if (wm != null) {
                        rotation = wm.getDefaultDisplay().getRotation();
                    }
                } catch (Throwable ignored) {
                }
                Settings.System.putInt(cr, "user_rotation", rotation);
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0);
            }
            Log.i(TAG, "rotation auto=" + auto);
        } catch (Throwable t) {
            Log.w(TAG, "rotation toggle failed: " + t);
        }
    }

    // ------------------------------------------------------------ mobile data

    private boolean isMobileDataEnabled() {
        try {
            return mTelephony != null && mTelephony.isDataEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    private void setMobileDataEnabled(boolean enabled) {
        try {
            Method m = TelephonyManager.class.getDeclaredMethod("setDataEnabled", boolean.class);
            m.setAccessible(true);
            m.invoke(mTelephony, enabled);
        } catch (Throwable t) {
            Log.w(TAG, "mobile data toggle failed: " + t);
        }
    }

    // ---------------------------------------------------------------- hotspot

    private boolean isHotspotEnabled() {
        try {
            Method m = WifiManager.class.getDeclaredMethod("getWifiApState");
            m.setAccessible(true);
            Object result = m.invoke(mWifiManager);
            if (result instanceof Integer) {
                int state = (Integer) result;
                // WIFI_AP_STATE_ENABLING = 12, WIFI_AP_STATE_ENABLED = 13
                return state == 12 || state == 13;
            }
        } catch (Throwable ignored) {
        }
        try {
            Method m = WifiManager.class.getDeclaredMethod("isWifiApEnabled");
            m.setAccessible(true);
            Object result = m.invoke(mWifiManager);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Calls ConnectivityManager.startTethering through the raw binder interface.
     * The framework's Java wrapper refuses this ROM's callback class (it ships
     * {@code OnStartTetheringCallback} as an abstract class, not an interface),
     * but the binder AIDL interface itself can be proxied - and SystemUI runs as
     * the system uid, which ConnectivityService accepts for tethering.
     */
    private boolean tetherViaBinder(boolean enabled) {
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method getService = serviceManager.getDeclaredMethod("getService", String.class);
            getService.setAccessible(true);
            Object binder = getService.invoke(null, "connectivity");
            if (binder == null) {
                return false;
            }
            Class<?> stubClass = Class.forName("android.net.IConnectivityManager$Stub");
            Method asInterface = stubClass.getDeclaredMethod("asInterface",
                    Class.forName("android.os.IBinder"));
            asInterface.setAccessible(true);
            Object service = asInterface.invoke(null, binder);
            Class<?> ifaceClass = Class.forName("android.net.IConnectivityManager");
            if (!enabled) {
                Method stop = ifaceClass.getMethod("stopTethering", int.class);
                stop.invoke(service, 0);
                Log.i(TAG, "binder stopTethering(0) ok");
                return true;
            }
            Class<?> callbackClass = null;
            String[] names = {
                    "android.net.IConnectivityManager$IStartTetheringCallback",
                    "android.net.IStartTetheringCallback"};
            for (String name : names) {
                try {
                    callbackClass = Class.forName(name);
                    break;
                } catch (Throwable ignored) {
                }
            }
            if (callbackClass == null) {
                Log.w(TAG, "no tethering callback interface in framework");
                return false;
            }
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    callbackClass.getClassLoader(), new Class<?>[]{callbackClass},
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            return null;
                        }
                    });
            try {
                Method start = ifaceClass.getMethod("startTethering", int.class,
                        callbackClass, boolean.class, String.class);
                start.invoke(service, 0, callback, false, "com.android.systemui");
            } catch (Throwable four) {
                try {
                    Method start = ifaceClass.getMethod("startTethering", int.class,
                            callbackClass, boolean.class);
                    start.invoke(service, 0, callback, false);
                } catch (Throwable three) {
                    Method start = ifaceClass.getMethod("startTethering", int.class,
                            callbackClass);
                    start.invoke(service, 0, callback);
                }
            }
            Log.i(TAG, "binder startTethering(0) ok");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "binder tether failed: " + t);
            return false;
        }
    }

    /** Calls IWifiManager.setWifiApEnabled directly through the wifi-service binder. */
    private boolean hotspotViaWifiBinder(boolean enabled) {
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method getService = serviceManager.getDeclaredMethod("getService", String.class);
            getService.setAccessible(true);
            Object binder = getService.invoke(null, "wifi");
            if (binder == null) {
                return false;
            }
            Class<?> stubClass = Class.forName("android.net.wifi.IWifiManager$Stub");
            Method asInterface = stubClass.getDeclaredMethod("asInterface",
                    Class.forName("android.os.IBinder"));
            asInterface.setAccessible(true);
            Object service = asInterface.invoke(null, binder);
            Class<?> ifaceClass = Class.forName("android.net.wifi.IWifiManager");
            WifiConfiguration config = null;
            try {
                Method get = WifiManager.class.getDeclaredMethod("getWifiApConfiguration");
                get.setAccessible(true);
                config = (WifiConfiguration) get.invoke(mWifiManager);
            } catch (Throwable ignored) {
            }
            if (config == null) {
                config = new WifiConfiguration();
                config.SSID = "XTC-Hotspot";
                config.preSharedKey = "12345678";
                try {
                    config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                } catch (Throwable ignored) {
                }
            }
            Method set = ifaceClass.getMethod("setWifiApEnabled",
                    WifiConfiguration.class, boolean.class);
            Object result = set.invoke(service, enabled ? config : null, enabled);
            Log.i(TAG, "wifiBinder setWifiApEnabled(" + enabled + ") -> " + result);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            Log.w(TAG, "wifiBinder hotspot failed: " + t);
            return false;
        }
    }

    private void setHotspotEnabled(boolean enabled) {
        // 1) raw binder startTethering (bypasses the vendor callback class issue)
        if (tetherViaBinder(enabled)) {
            return;
        }
        // 2) raw wifi-service binder (bypasses the Java wrapper)
        if (hotspotViaWifiBinder(enabled)) {
            return;
        }
        // 3) WifiManager.setWifiApEnabled with a REAL configuration (many vendor
        // ROMs reject a null config when enabling, which is why the toggle did
        // nothing before).
        try {
            WifiConfiguration config = null;
            try {
                Method get = WifiManager.class.getDeclaredMethod("getWifiApConfiguration");
                get.setAccessible(true);
                config = (WifiConfiguration) get.invoke(mWifiManager);
            } catch (Throwable ignored) {
            }
            if (config == null) {
                config = new WifiConfiguration();
                config.SSID = "XTC-Hotspot";
                config.preSharedKey = "12345678";
                try {
                    config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                } catch (Throwable ignored) {
                }
            }
            Method set = WifiManager.class.getDeclaredMethod("setWifiApEnabled",
                    WifiConfiguration.class, boolean.class);
            set.setAccessible(true);
            Object result = set.invoke(mWifiManager, enabled ? config : null, enabled);
            Log.i(TAG, "setWifiApEnabled(" + enabled + ") -> " + result
                    + " ssid=" + config.SSID);
            if (result instanceof Boolean && (Boolean) result) {
                return;
            }
        } catch (Throwable t) {
            Log.w(TAG, "setWifiApEnabled(config) failed: " + t);
        }
        // 2) hidden ConnectivityManager.startTethering (only if the callback is an interface)
        if (tetherViaConnectivityManager(enabled)) {
            return;
        }
        // 3) "tethering" system service as last resort
        try {
            Object tethering = mAppContext.getSystemService("tethering");
            if (tethering != null) {
                Method start = tethering.getClass().getDeclaredMethod("startTethering",
                        int.class, boolean.class);
                start.setAccessible(true);
                start.invoke(tethering, 0, false);
            }
        } catch (Throwable t) {
            Log.w(TAG, "tethering service fallback failed: " + t);
        }
    }

    private boolean tetherViaConnectivityManager(boolean enabled) {
        try {
            ConnectivityManager cm = (ConnectivityManager) mAppContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            if (!enabled) {
                Method stop = ConnectivityManager.class.getDeclaredMethod("stopTethering", int.class);
                stop.setAccessible(true);
                stop.invoke(cm, 0);
                return true;
            }
            Class<?> callbackClass;
            try {
                callbackClass = Class.forName("android.net.ConnectivityManager$OnStartTetheringCallback");
            } catch (Throwable t) {
                return false;
            }
            if (!callbackClass.isInterface()) {
                return false; // this ROM ships it as an abstract class
            }
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    callbackClass.getClassLoader(), new Class<?>[]{callbackClass},
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            Class<?> returnType = method.getReturnType();
                            if (returnType == boolean.class) {
                                return Boolean.FALSE;
                            }
                            if (returnType == int.class) {
                                return 0;
                            }
                            return null;
                        }
                    });
            try {
                Method start = ConnectivityManager.class.getDeclaredMethod("startTethering",
                        int.class, callbackClass, boolean.class);
                start.setAccessible(true);
                start.invoke(cm, 0, callback, false);
                return true;
            } catch (NoSuchMethodException nsme) {
                Method start = ConnectivityManager.class.getDeclaredMethod("startTethering",
                        int.class, callbackClass, boolean.class, String.class);
                start.setAccessible(true);
                start.invoke(cm, 0, callback, false, mAppContext.getPackageName());
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "startTethering unavailable: " + t);
            return false;
        }
    }

    // ---------------------------------------------------- eye comfort overlay

    private void setEyeComfortInternal(boolean enabled) {
        if (enabled) {
            addEyeOverlay();
        } else {
            removeEyeOverlay();
        }
    }

    private void addEyeOverlay() {
        if (mEyeOverlay != null) {
            return;
        }
        try {
            WindowManager wm = (WindowManager) mAppContext.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return;
            }
            android.util.DisplayMetrics dm = mAppContext.getResources().getDisplayMetrics();
            int oversize = Math.max(dm.widthPixels, dm.heightPixels) + 200;
            View view = new View(mAppContext);
            // night-mask technique: a SOLID colour whose View alpha carries the
            // strength - stays clear instead of turning hazy grey
            view.setBackgroundColor(EYE_COMFORT_COLOR);
            view.setAlpha(EYE_COMFORT_STRENGTH);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    oversize, oversize,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = android.view.Gravity.CENTER;
            lp.setTitle("EyeComfortFilter");
            lp.packageName = mAppContext.getPackageName();
            wm.addView(view, lp);
            mEyeOverlay = view;
        } catch (Throwable t) {
            Log.w(TAG, "eye comfort overlay failed: " + t);
        }
    }

    private void removeEyeOverlay() {
        if (mEyeOverlay == null) {
            return;
        }
        final View view = mEyeOverlay;
        mEyeOverlay = null;
        try {
            view.animate().alpha(0f).setDuration(150L).start();
            WindowManager wm = (WindowManager) mAppContext.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                wm.removeView(view);
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------ keep screen awake

    /**
     * Wake locks proved unreliable on this ROM (user report), so the screen is
     * held awake the same way "adb shell settings put system
     * screen_off_timeout" does it: SCREEN_OFF_TIMEOUT is raised to a very long
     * value while the toggle is on; the original value is stored once and
     * restored when the toggle goes off (or unregister).
     */
    @SuppressWarnings("deprecation")
    private void setKeepAwakeInternal(boolean enabled) {
        if (enabled) {
            try {
                ContentResolver cr = mAppContext.getContentResolver();
                if (!mPrefs.contains(KEY_TIMEOUT_BACKUP)) {
                    int original = Settings.System.getInt(cr,
                            Settings.System.SCREEN_OFF_TIMEOUT, 30000);
                    mPrefs.edit().putInt(KEY_TIMEOUT_BACKUP, original).apply();
                }
                Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT,
                        KEEP_AWAKE_TIMEOUT_MS);
            } catch (Throwable t) {
                Log.w(TAG, "keep awake (timeout) failed: " + t);
            }
        } else {
            try {
                ContentResolver cr = mAppContext.getContentResolver();
                int original = mPrefs.getInt(KEY_TIMEOUT_BACKUP, -1);
                if (original >= 0) {
                    Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, original);
                }
                mPrefs.edit().remove(KEY_TIMEOUT_BACKUP).apply();
            } catch (Throwable t) {
                Log.w(TAG, "restore timeout failed: " + t);
            }
        }
    }
}
