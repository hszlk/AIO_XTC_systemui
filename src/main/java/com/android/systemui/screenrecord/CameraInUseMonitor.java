package com.android.systemui.screenrecord;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Camera occupancy monitor for screen recording.
 *
 * Original APK logic: {@code com.xtc.baseservice.c} (CameraInUseMonitor) wrapped
 * {@code CameraManager.registerAvailabilityCallback} and was used twice -
 * a pre-start check (camera in use -> "已启用摄像头 无法录屏") and an
 * interruption check while recording ("录屏已停止"). This is the public-API
 * equivalent, re-implemented from scratch (see SPEC B-1.4 and the "two easiest
 * traps" note: {@code baseservice.c} here is the camera monitor, NOT a BLE
 * client manager).
 */
public final class CameraInUseMonitor {

    private static final String TAG = "CameraInUseMonitor";
    private static final long PROBE_TIMEOUT_MS = 600L;

    private CameraInUseMonitor() {
    }

    /**
     * One-shot probe used before a recording starts. Returns true when any
     * camera is currently unavailable (i.e. opened by another client).
     */
    public static boolean isCameraInUse(Context context) {
        final CameraManager cameraManager = getCameraManager(context);
        if (cameraManager == null) {
            return false;
        }
        final int cameraCount = countCameras(context);
        if (cameraCount <= 0) {
            return false;
        }
        HandlerThread thread = new HandlerThread("camera-in-use-probe");
        thread.start();
        // registerAvailabilityCallback delivers one state per camera right away;
        // wait for all of them so an available camera cannot mask an in-use one.
        final CountDownLatch latch = new CountDownLatch(cameraCount);
        final boolean[] inUse = new boolean[]{false};
        CameraManager.AvailabilityCallback callback = new CameraManager.AvailabilityCallback() {
            @Override
            public void onCameraAvailable(String cameraId) {
                latch.countDown();
            }

            @Override
            public void onCameraUnavailable(String cameraId) {
                inUse[0] = true;
                latch.countDown();
            }
        };
        try {
            cameraManager.registerAvailabilityCallback(callback, new Handler(thread.getLooper()));
            latch.await(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            Log.w(TAG, "isCameraInUse: probe failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                cameraManager.unregisterAvailabilityCallback(callback);
            } catch (Throwable ignored) {
                // nothing to do
            }
            thread.quitSafely();
        }
        return inUse[0];
    }

    /** Live camera watcher used while a recording is in progress. */
    public interface Listener {
        /** A camera was opened by another client; the recording should stop. */
        void onCameraUnavailable();
    }

    /** Starts watching camera availability; call {@link Watch#stop()} when done. */
    public static Watch watch(Context context, final Listener listener) {
        return new Watch(context, listener);
    }

    /** Active camera availability registration. */
    public static final class Watch {
        private final CameraManager mCameraManager;
        private final HandlerThread mThread;
        private final Handler mHandler;
        private final CameraManager.AvailabilityCallback mCallback;
        private volatile boolean mStopped;

        Watch(Context context, final Listener listener) {
            mCameraManager = getCameraManager(context);
            mThread = new HandlerThread("camera-in-use-watch");
            mThread.start();
            mHandler = new Handler(mThread.getLooper());
            mCallback = new CameraManager.AvailabilityCallback() {
                @Override
                public void onCameraUnavailable(String cameraId) {
                    Log.d(TAG, "camera unavailable: " + cameraId);
                    if (!mStopped && listener != null) {
                        listener.onCameraUnavailable();
                    }
                }
            };
            if (mCameraManager != null) {
                try {
                    mCameraManager.registerAvailabilityCallback(mCallback, mHandler);
                } catch (RuntimeException e) {
                    Log.w(TAG, "watch: unable to register callback", e);
                }
            }
        }

        public void stop() {
            mStopped = true;
            if (mCameraManager != null) {
                try {
                    mCameraManager.unregisterAvailabilityCallback(mCallback);
                } catch (Throwable ignored) {
                    // nothing to do
                }
            }
            mThread.quitSafely();
        }
    }

    private static CameraManager getCameraManager(Context context) {
        try {
            Object service = context.getSystemService(Context.CAMERA_SERVICE);
            return service instanceof CameraManager ? (CameraManager) service : null;
        } catch (Throwable t) {
            Log.w(TAG, "getCameraManager failed", t);
            return null;
        }
    }

    /** Unused helper kept for diagnostics: lists the camera ids available. */
    static int countCameras(Context context) {
        CameraManager cameraManager = getCameraManager(context);
        if (cameraManager == null) {
            return 0;
        }
        try {
            String[] ids = cameraManager.getCameraIdList();
            return ids == null ? 0 : ids.length;
        } catch (CameraAccessException | RuntimeException e) {
            Log.w(TAG, "countCameras failed", e);
            return 0;
        }
    }

    /** True when the device reports at least one camera with a flash unit. */
    static boolean hasFlash(Context context) {
        CameraManager cameraManager = getCameraManager(context);
        if (cameraManager == null) {
            return false;
        }
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Boolean flash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) {
                    return true;
                }
            }
        } catch (CameraAccessException | RuntimeException e) {
            Log.w(TAG, "hasFlash failed", e);
        }
        return false;
    }
}
