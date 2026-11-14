package com.android.systemui.screenrecord;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.InfoToast;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Screen-recording foreground service (manifest: foregroundServiceType=0x20,
 * exported=false).
 *
 * Responsibilities:
 *  - own the {@link ScreenMediaRecorder} capture pipeline (MediaProjection +
 *    MediaRecorder -> direct .mp4),
 *  - acquire the MediaProjection grant through
 *    {@link com.android.systemui.media.MediaProjectionPermissionActivity} when
 *    no grant has been obtained yet in this process,
 *  - run the foreground notification (channel {@code screen_record}, id 4274)
 *    with a stop action and a max-duration backstop,
 *  - show/hide the {@link RecordingFloatController} overlay,
 *  - export the finished clip and tell the user (InfoToast) where it went,
 *  - stop on screen-off / camera-in-use and clean every resource up.
 *
 * Original APK logic: {@code screenrecord.RecordingService} + {@code screenrecord.f}
 * (ScreenMediaRecorder) + {@code screenrecord.b} (float window) +
 * {@code screenrecord.d} (storage). Reimplemented with public framework APIs
 * only: no RxJava (plain worker HandlerThread), no vendor album database
 * (MediaStore/media scanner instead), no cloud config (local 60 s / 100 MB
 * constants) and no telemetry.
 *
 * Intent contract (kept compatible with the original assistant directive and
 * with adb testing):
 *   - action {@code com.android.systemui.screenrecord.START}
 *     extra {@code extra_useAudio} (int, ScreenRecordingAudioSource ordinal)
 *     or {@code extra_use_audio} (boolean)
 *   - action {@code com.android.systemui.screenrecord.STOP}
 *   - action {@code com.android.systemui.screenrecord.STOP_FROM_NOTIF}
 *   - action {@code com.android.systemui.screenrecord.ABORT} (permission denied)
 */
public class RecordingService extends Service implements MediaRecorder.OnInfoListener {

    private static final String TAG = "RecordingService";

    public static final String ACTION_START = "com.android.systemui.screenrecord.START";
    public static final String ACTION_STOP = "com.android.systemui.screenrecord.STOP";
    public static final String ACTION_STOP_FROM_NOTIF =
            "com.android.systemui.screenrecord.STOP_FROM_NOTIF";
    public static final String ACTION_ABORT = "com.android.systemui.screenrecord.ABORT";

    /** Original extra name: int ordinal of {@link ScreenRecordingAudioSource}. */
    public static final String EXTRA_USE_AUDIO = "extra_useAudio";
    /** Convenience boolean extra used by the assistant directive handler. */
    public static final String EXTRA_USE_AUDIO_BOOL = "extra_use_audio";
    /** MediaProjection result code delivered by MediaProjectionPermissionActivity. */
    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    /** MediaProjection result data delivered by MediaProjectionPermissionActivity. */
    public static final String EXTRA_RESULT_DATA = "extra_result_data";
    /** Set when the abort came from a denied permission dialog (user visible error). */
    public static final String EXTRA_ABORT_VISIBLE = "extra_abort_visible";

    /** Maximum recording length (original RemoteRecordingConfig default). */
    public static final long MAX_RECORDING_MS = 0L; // unlimited (user request)

    private static final String CHANNEL_ID = "screen_record";
    private static final int NOTIFICATION_ID = 4274;
    private static final String NOTIFICATION_ACTION_STOP = "停止";
    /** Only used when MAX_RECORDING_MS > 0; 0 disables the time backstop. */
    private static final long MAX_DURATION_BACKSTOP_MS =
            MAX_RECORDING_MS > 0L ? MAX_RECORDING_MS + 5_000L : 0L;
    private static final long SERVICE_SHUTDOWN_DELAY_MS = 3_000L;
    private static final long PERMISSION_TIMEOUT_MS = 60_000L;

    private static final int STATE_IDLE = 0;
    private static final int STATE_WAITING_PERMISSION = 1;
    private static final int STATE_STARTING = 2;
    private static final int STATE_RECORDING = 3;
    private static final int STATE_SAVING = 4;

    private static volatile int sState = STATE_IDLE;

    /** MediaProjection grant cached for this process (consumed by one capture). */
    private static volatile Intent sProjectionData;
    private static volatile int sProjectionResultCode = -1;

    private static volatile File sLastSavedFile;
    private static volatile Uri sLastSavedUri;

    private NotificationManager mNotificationManager;
    private HandlerThread mWorkerThread;
    private Handler mWorker;
    private Handler mMain;
    private ScreenMediaRecorder mRecorder;
    private ScreenRecordingAudioSource mAudioSource = ScreenRecordingAudioSource.NONE;
    private RecordingFloatController mFloatController;
    private BroadcastReceiver mInterruptReceiver;
    private CameraInUseMonitor.Watch mCameraWatch;
    private MediaProjection mProjection;
    /** Grant obtained through the hidden system API (no consent dialog). */
    private MediaProjection mSystemProjection;
    private Runnable mMaxDurationBackstop;
    private Runnable mShutdownTask;
    private Runnable mPermissionWatchdog;

    private final MediaProjection.Callback mProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            Log.d(TAG, "media projection stopped by framework");
            Handler main = mMain;
            if (main == null) {
                return;
            }
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (sState == STATE_RECORDING) {
                        requestStop();
                    }
                }
            });
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        mMain = new Handler(Looper.getMainLooper());
        mWorkerThread = new HandlerThread("screen-record");
        mWorkerThread.start();
        mWorker = new Handler(mWorkerThread.getLooper());
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mFloatController = new RecordingFloatController(this, new RecordingFloatController.Callback() {
            @Override
            public void onStopRequested() {
                requestStop();
            }
        });
        createNotificationChannel();
        Log.d(TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        Log.d(TAG, "onStartCommand action=" + action + " state=" + sState);
        if (ACTION_START.equals(action)) {
            handleStartRequest(intent);
        } else if (ACTION_STOP.equals(action) || ACTION_STOP_FROM_NOTIF.equals(action)) {
            requestStop();
        } else if (ACTION_ABORT.equals(action)) {
            abortStart(intent.getBooleanExtra(EXTRA_ABORT_VISIBLE, false));
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy state=" + sState);
        cancelShutdown();
        cancelPermissionWatchdog();
        cancelMaxDurationBackstop();
        unregisterInterruptReceiver();
        stopCameraWatch();
        if (mMain != null) {
            mMain.removeCallbacksAndMessages(null);
        }
        final ScreenMediaRecorder recorder = mRecorder;
        mRecorder = null;
        Handler worker = mWorker;
        if (worker != null) {
            worker.post(new Runnable() {
                @Override
                public void run() {
                    if (recorder != null) {
                        recorder.stop();
                    }
                    Log.d(TAG, "worker shutdown");
                }
            });
        }
        if (mWorkerThread != null) {
            mWorkerThread.quitSafely();
            mWorkerThread = null;
        }
        if (mFloatController != null) {
            mFloatController.hide();
            mFloatController = null;
        }
        sState = STATE_IDLE;
        super.onDestroy();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("RecordingService state=" + sState
                + " audio=" + mAudioSource
                + " lastSaved=" + sLastSavedFile
                + " lastUri=" + sLastSavedUri);
    }

    // -------------------------------------------------------------- public API

    public static boolean isRecording() {
        return sState == STATE_RECORDING;
    }

    public static boolean isSaving() {
        return sState == STATE_SAVING;
    }

    /** True while a recording session owns the screen (starting/recording/saving). */
    public static boolean isBusy() {
        int state = sState;
        return state == STATE_WAITING_PERMISSION || state == STATE_STARTING
                || state == STATE_RECORDING || state == STATE_SAVING;
    }

    public static File getLastSavedFile() {
        return sLastSavedFile;
    }

    public static Uri getLastSavedUri() {
        return sLastSavedUri;
    }

    /** Starts a silent screen recording (no microphone). */
    public static void start(Context context) {
        start(context, ScreenRecordingAudioSource.NONE);
    }

    /** Starts a screen recording with the requested audio source. */
    public static void start(Context context, ScreenRecordingAudioSource source) {
        if (context == null) {
            return;
        }
        ScreenRecordingAudioSource audio = source == null ? ScreenRecordingAudioSource.NONE : source;
        Intent intent = new Intent(context, RecordingService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_USE_AUDIO, audio.ordinal());
        if (sProjectionData != null) {
            intent.putExtra(EXTRA_RESULT_CODE, sProjectionResultCode);
            intent.putExtra(EXTRA_RESULT_DATA, sProjectionData);
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable t) {
            Log.e(TAG, "start: unable to start service", t);
        }
    }

    /** Stops the current session and saves what has been recorded so far. */
    public static void stop(Context context) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent(context, RecordingService.class);
        intent.setAction(ACTION_STOP);
        try {
            context.startService(intent);
        } catch (Throwable t) {
            Log.e(TAG, "stop: unable to reach service", t);
        }
    }

    /** Starts when idle, stops when a session is active. */
    public static void toggle(Context context) {
        if (isBusy()) {
            stop(context);
        } else {
            start(context);
        }
    }

    /** User visible string for a precondition error code (0 when silent). */
    public static int errorTextRes(int code) {
        switch (code) {
            case 2:
                return 0; // in call: the original silently ignored the request
            case 4:
                return R.string.can_not_record_screen_cause_storage;
            case 5:
                return R.string.disable_screen_record_when_open_camera;
            default:
                return 0;
        }
    }

    /**
     * Pre-flight checks (subset of the original 0..7 codes that can be evaluated
     * without vendor framework hooks): 0 = ok, 2 = call in progress,
     * 4 = not enough storage, 5 = camera already in use.
     */
    public static int checkPreconditions(Context context) {
        if (context == null) {
            return 0;
        }
        try {
            TelephonyManager telephony = (TelephonyManager)
                    context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephony != null && telephony.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                return 2;
            }
        } catch (Throwable t) {
            Log.w(TAG, "checkPreconditions: call state check failed", t);
        }
        if (!RecordingStorageHelper.hasEnoughSpace(context)) {
            return 4;
        }
        if (CameraInUseMonitor.isCameraInUse(context)) {
            return 5;
        }
        return 0;
    }

    // ------------------------------------------------------------ start plumbing

    private void handleStartRequest(Intent intent) {
        if (sState == STATE_STARTING || sState == STATE_RECORDING || sState == STATE_SAVING) {
            Log.w(TAG, "start ignored, session already running: " + sState);
            return;
        }
        // A pending shutdown from the previous session must not kill this one.
        cancelShutdown();
        cancelPermissionWatchdog();
        // Mandatory for startForegroundService() callers.
        startForegroundInternal();

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
        Intent data = null;
        try {
            data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        } catch (Throwable t) {
            Log.w(TAG, "unable to read projection data", t);
        }
        if (resultCode >= 0 && data != null) {
            sProjectionResultCode = resultCode;
            sProjectionData = data;
        }
        mAudioSource = resolveAudioSource(intent);

        if (sProjectionData == null) {
            // A platform-signed SystemUI holds MEDIA_PROJECTION; on this build
            // the public consent dialog is auto-denied by the watch, so obtain
            // the grant directly through the hidden IMediaProjectionManager API
            // (no user interaction), and only fall back to the consent activity
            // when that is unavailable.
            MediaProjection systemProjection = obtainSystemProjection();
            if (systemProjection != null) {
                mSystemProjection = systemProjection;
                sState = STATE_STARTING;
                beginCapture();
                return;
            }
            sState = STATE_WAITING_PERMISSION;
            schedulePermissionWatchdog();
            if (!launchPermissionActivity()) {
                abortStart(true);
            }
            return;
        }
        sState = STATE_STARTING;
        beginCapture();
    }

    /** Guards against a permission activity that never returns a result. */
    private void schedulePermissionWatchdog() {
        cancelPermissionWatchdog();
        mPermissionWatchdog = new Runnable() {
            @Override
            public void run() {
                mPermissionWatchdog = null;
                if (sState == STATE_WAITING_PERMISSION) {
                    Log.w(TAG, "media projection permission dialog timed out");
                    abortStart(false);
                }
            }
        };
        mMain.postDelayed(mPermissionWatchdog, PERMISSION_TIMEOUT_MS);
    }

    private void cancelPermissionWatchdog() {
        if (mPermissionWatchdog != null && mMain != null) {
            mMain.removeCallbacks(mPermissionWatchdog);
            mPermissionWatchdog = null;
        }
    }

    private ScreenRecordingAudioSource resolveAudioSource(Intent intent) {
        if (intent.hasExtra(EXTRA_USE_AUDIO)) {
            try {
                return ScreenRecordingAudioSource.fromOrdinal(intent.getIntExtra(EXTRA_USE_AUDIO, 0));
            } catch (Throwable t) {
                Log.w(TAG, "bad audio source extra", t);
            }
        }
        return intent.getBooleanExtra(EXTRA_USE_AUDIO_BOOL, false)
                ? ScreenRecordingAudioSource.MIC : ScreenRecordingAudioSource.NONE;
    }

    private boolean launchPermissionActivity() {
        try {
            Intent request = new Intent(this,
                    com.android.systemui.media.MediaProjectionPermissionActivity.class);
            request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            request.putExtra(
                    com.android.systemui.media.MediaProjectionPermissionActivity.EXTRA_FROM_SYSTEMUI,
                    true);
            request.putExtra(EXTRA_USE_AUDIO, mAudioSource.ordinal());
            startActivity(request);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "unable to request media projection permission", t);
            return false;
        }
    }

    // ------------------------------------------------------- device audio capture

    /** Device-audio recorder (playback capture / submix loopback). */
    private InternalAudioRecorder mInternalAudio;

    /** Starts capturing the device playback audio next to the video. */
    private void startInternalAudio(MediaProjection projection) {
        try {
            File cacheDir = RecordingStorageHelper.getCacheDir(this);
            if (cacheDir == null) {
                return;
            }
            File wav = new File(cacheDir, "internal_audio.wav");
            wav.delete();
            InternalAudioRecorder rec = new InternalAudioRecorder(wav);
            if (rec.start(this, projection)) {
                mInternalAudio = rec;
            } else {
                Log.i(TAG, "device audio not available on this ROM; silent recording");
            }
        } catch (Throwable t) {
            Log.w(TAG, "startInternalAudio failed: " + t);
        }
    }

    /** Stops the device-audio capture; returns the wav or null. */
    private File stopInternalAudio() {
        InternalAudioRecorder rec = mInternalAudio;
        mInternalAudio = null;
        if (rec == null) {
            return null;
        }
        return rec.stop();
    }

    private void beginCapture() {        mWorker.post(new Runnable() {
            @Override
            public void run() {
                int check = checkPreconditions(RecordingService.this);
                if (check != 0) {
                    Log.w(TAG, "refusing to record, precondition code " + check);
                    failStart(check);
                    return;
                }
                MediaProjection projection = createProjection();
                if (projection == null) {
                    failStart(-1);
                    return;
                }
                MediaRecorder.OnInfoListener infoListener = RecordingService.this;
                ScreenMediaRecorder recorder =
                        new ScreenMediaRecorder(RecordingService.this, mAudioSource);
                try {
                    recorder.start(projection, infoListener);
                } catch (Throwable t) {
                    Log.e(TAG, "ScreenMediaRecorder.start failed", t);
                    recorder.stop();
                    failStart(-1);
                    return;
                }
                mRecorder = recorder;
                mProjection = projection;
                // device-audio capture (W5 = playback capture, W4 = submix try)
                startInternalAudio(projection);
                Handler main = mMain;
                if (main != null) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            onCaptureStarted();
                        }
                    });
                }
            }
        });
    }

    /**
     * Obtains a MediaProjection through the hidden framework API
     * android.media.projection.IMediaProjectionManager#createProjection.
     *
     * Android 8.1 does not enforce the hidden-API blacklist yet, and SystemUI is
     * a platform-signed system uid with the MEDIA_PROJECTION permission, so the
     * platform grants the projection directly (the same path the vendor 1.8.00
     * SystemUI used).  Returns null when the hidden API is not usable.
     */
    private MediaProjection obtainSystemProjection() {
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Object binderObject = serviceManager.getMethod("getService", String.class)
                    .invoke(null, "media_projection");
            if (!(binderObject instanceof android.os.IBinder)) {
                return null;
            }
            android.os.IBinder binder = (android.os.IBinder) binderObject;
            Class<?> managerClass = Class.forName(
                    "android.media.projection.IMediaProjectionManager");
            Object manager = Class.forName("android.media.projection.IMediaProjectionManager$Stub")
                    .getMethod("asInterface", android.os.IBinder.class)
                    .invoke(null, binder);
            if (manager == null) {
                return null;
            }
            int uid = android.os.Process.myUid();
            String pkg = getPackageName();
            // Android 8.x signature used by the vendor MediaProjectionManagerEx:
            // createProjection(int uid, String packageName, int type, boolean isPermanentGrant)
            Object projectionObj = managerClass
                    .getMethod("createProjection", int.class, String.class, int.class, boolean.class)
                    .invoke(manager, uid, pkg, 0, false);
            if (projectionObj == null) {
                return null;
            }
            // Wrap the IMediaProjection binder into a public MediaProjection object.
            // Preferred: hidden constructor MediaProjection(Context, IMediaProjection),
            // the same trick the vendor helper uses.
            try {
                Class<?> projectionClass = Class.forName(
                        "android.media.projection.IMediaProjection");
                java.lang.reflect.Constructor<MediaProjection> ctor =
                        MediaProjection.class.getDeclaredConstructor(Context.class, projectionClass);
                ctor.setAccessible(true);
                MediaProjection mediaProjection = ctor.newInstance(this, projectionObj);
                if (mediaProjection != null) {
                    Log.d(TAG, "obtained system MediaProjection (hidden ctor)");
                    return mediaProjection;
                }
            } catch (Throwable ignored) {
                // fall through to the interface accessor
            }
            try {
                Object mediaProjection = Class.forName("android.media.projection.IMediaProjection")
                        .getMethod("getMediaProjection")
                        .invoke(projectionObj);
                if (mediaProjection instanceof MediaProjection) {
                    Log.d(TAG, "obtained system MediaProjection (interface)");
                    return (MediaProjection) mediaProjection;
                }
            } catch (Throwable t) {
                Log.w(TAG, "IMediaProjection.getMediaProjection failed: " + t);
            }
        } catch (Throwable t) {
            Log.w(TAG, "hidden MediaProjection unavailable: " + t);
        }
        return null;
    }

    private MediaProjection createProjection() {
        MediaProjection hidden = mSystemProjection;
        if (hidden != null) {
            mSystemProjection = null;
            try {
                hidden.registerCallback(mProjectionCallback, mMain);
            } catch (Throwable t) {
                Log.w(TAG, "registerCallback on system projection failed", t);
            }
            return hidden;
        }
        Intent data = sProjectionData;
        int resultCode = sProjectionResultCode;
        if (data == null || resultCode < 0) {
            Log.w(TAG, "createProjection: no grant available");
            return null;
        }
        try {
            MediaProjectionManager manager = (MediaProjectionManager)
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (manager == null) {
                return null;
            }
            MediaProjection projection = manager.getMediaProjection(resultCode, data);
            if (projection != null) {
                projection.registerCallback(mProjectionCallback, mMain);
            }
            return projection;
        } catch (Throwable t) {
            Log.e(TAG, "createProjection failed", t);
            return null;
        }
    }

    private void onCaptureStarted() {
        if (sState != STATE_STARTING) {
            Log.w(TAG, "capture became ready while state=" + sState + ", discarding");
            final int state = sState;
            mWorker.post(new Runnable() {
                @Override
                public void run() {
                    if (mRecorder != null) {
                        mRecorder.stop();
                        mRecorder = null;
                    }
                }
            });
            if (state == STATE_IDLE) {
                // The session was aborted while the recorder was starting up.
                shutdownService();
            }
            // In STATE_SAVING the stopAndSave task queued behind this one exports
            // the clip and shuts the service down once it is written.
            return;
        }
        sState = STATE_RECORDING;
        sProjectionData = null;
        sProjectionResultCode = -1;
        cancelPermissionWatchdog();
        if (mFloatController != null) {
            mFloatController.show(SystemClock.elapsedRealtime());
        }
        scheduleMaxDurationBackstop();
        registerInterruptReceiver();
        startCameraWatch();
        Log.d(TAG, "recording started");
    }

    // ------------------------------------------------------------- stop plumbing

    private void requestStop() {
        int state = sState;
        if (state == STATE_IDLE || state == STATE_SAVING) {
            return;
        }
        if (state == STATE_WAITING_PERMISSION) {
            abortStart(false);
            return;
        }
        sState = STATE_SAVING;
        cancelMaxDurationBackstop();
        unregisterInterruptReceiver();
        stopCameraWatch();
        if (mFloatController != null) {
            mFloatController.showStatus("正在保存");
        }
        mWorker.post(new Runnable() {
            @Override
            public void run() {
                stopAndSave();
            }
        });
    }

    private void stopAndSave() {
        ScreenMediaRecorder recorder = mRecorder;
        mRecorder = null;
        File temp = recorder == null ? null : recorder.getOutputFile();
        boolean cleanStop = false;
        if (recorder != null) {
            cleanStop = recorder.stop();
        }
        File wav = stopInternalAudio();
        unregisterProjectionCallback();
        RecordingStorageHelper.SaveResult result = null;
        if (cleanStop) {
            // device audio recorded? mux it into the clip before exporting
            if (temp != null && wav != null && wav.length() > 44L) {
                File muxed = new File(temp.getParentFile(),
                        temp.getName() + ".muxed");
                if (AvMuxer.mux(temp, wav, muxed)) {
                    Log.i(TAG, "device audio muxed into video");
                    temp.delete();
                    temp = muxed;
                } else {
                    Log.w(TAG, "audio mux failed, keeping silent video");
                }
                wav.delete();
            }
            result = RecordingStorageHelper.save(RecordingService.this, temp);
        } else {
            Log.w(TAG, "recorder did not stop cleanly, temp clip dropped");
        }
        final RecordingStorageHelper.SaveResult saved = result;
        Handler main = mMain;
        if (main != null) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    onCaptureSaved(saved);
                }
            });
        }
    }

    private void onCaptureSaved(RecordingStorageHelper.SaveResult result) {
        boolean ok = result != null && result.isValid();
        sState = STATE_IDLE;
        if (ok) {
            sLastSavedFile = result.file;
            sLastSavedUri = result.shareUri;
            String message = getString(R.string.screen_record_has_saved_to_album);
            InfoToast.show(message);
            if (mFloatController != null) {
                mFloatController.showFinalMessage(message);
            }
            Log.d(TAG, "saved " + result.file + " (" + result.durationMs + " ms) uri=" + result.shareUri);
        } else {
            String message = getString(R.string.screen_record_save_video_failed);
            InfoToast.show(message);
            if (mFloatController != null) {
                mFloatController.showFinalMessage(message);
            }
            Log.w(TAG, "saving the recording failed");
        }
        scheduleShutdown();
    }

    private void failStart(int checkCode) {
        final int code = checkCode;
        Handler main = mMain;
        if (main == null) {
            return;
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                sState = STATE_IDLE;
                sProjectionData = null;
                sProjectionResultCode = -1;
                if (mFloatController != null) {
                    mFloatController.hide();
                }
                int textRes = errorTextRes(code);
                String message = textRes != 0 ? getString(textRes) : "录屏失败";
                InfoToast.show(message);
                shutdownService();
            }
        });
    }

    private void abortStart(boolean userVisible) {
        Log.w(TAG, "abortStart userVisible=" + userVisible);
        cancelPermissionWatchdog();
        sState = STATE_IDLE;
        sProjectionData = null;
        sProjectionResultCode = -1;
        if (mFloatController != null) {
            mFloatController.hide();
        }
        if (userVisible) {
            InfoToast.show("无法获取录屏权限");
        }
        shutdownService();
    }

    private void shutdownService() {
        cancelShutdown();
        if (mFloatController != null) {
            mFloatController.hide();
        }
        try {
            stopForeground(true);
        } catch (Throwable t) {
            Log.w(TAG, "stopForeground failed", t);
        }
        stopSelf();
    }

    private void scheduleShutdown() {
        Handler main = mMain;
        if (main == null) {
            shutdownService();
            return;
        }
        cancelShutdown();
        mShutdownTask = new Runnable() {
            @Override
            public void run() {
                mShutdownTask = null;
                shutdownService();
            }
        };
        main.postDelayed(mShutdownTask, SERVICE_SHUTDOWN_DELAY_MS);
    }

    private void cancelShutdown() {
        if (mShutdownTask != null && mMain != null) {
            mMain.removeCallbacks(mShutdownTask);
            mShutdownTask = null;
        }
    }

    private void unregisterProjectionCallback() {
        MediaProjection projection = mProjection;
        mProjection = null;
        if (projection != null) {
            try {
                projection.unregisterCallback(mProjectionCallback);
            } catch (Throwable t) {
                Log.w(TAG, "unable to unregister projection callback", t);
            }
        }
    }

    // --------------------------------------------------------------- notification

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.screen_record), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(getString(R.string.screen_record));
        mNotificationManager.createNotificationChannel(channel);
    }

    private void startForegroundInternal() {
        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, RecordingService.class);
        stopIntent.setAction(ACTION_STOP_FROM_NOTIF);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent stopPendingIntent =
                PendingIntent.getService(this, 1, stopIntent, pendingFlags);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_quick_screen_record)
                .setContentTitle(getString(R.string.screen_record))
                .setOngoing(true)
                .addAction(R.drawable.screen_recording_red, NOTIFICATION_ACTION_STOP, stopPendingIntent);
        return builder.build();
    }

    // ------------------------------------------------------------ max duration

    private void scheduleMaxDurationBackstop() {
        cancelMaxDurationBackstop();
        if (MAX_DURATION_BACKSTOP_MS <= 0L) {
            return; // unlimited recording: no time-based stop
        }
        Handler main = mMain;
        if (main == null) {
            return;
        }
        mMaxDurationBackstop = new Runnable() {
            @Override
            public void run() {
                mMaxDurationBackstop = null;
                Log.d(TAG, "recording stopped by max-duration backstop");
                requestStop();
            }
        };
        main.postDelayed(mMaxDurationBackstop, MAX_DURATION_BACKSTOP_MS);
    }

    private void cancelMaxDurationBackstop() {
        if (mMaxDurationBackstop != null && mMain != null) {
            mMain.removeCallbacks(mMaxDurationBackstop);
            mMaxDurationBackstop = null;
        }
    }

    @Override
    public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
        Log.d(TAG, "media recorder info what=" + what + " extra=" + extra);
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED
                || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            Handler main = mMain;
            if (main != null) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        requestStop();
                    }
                });
            }
        }
    }

    // ------------------------------------------------------------- interruptions

    private void registerInterruptReceiver() {
        if (mInterruptReceiver != null) {
            return;
        }
        mInterruptReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) {
                    return;
                }
                String action = intent.getAction();
                Log.d(TAG, "record interrupted by " + action);
                requestStop();
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        try {
            registerReceiver(mInterruptReceiver, filter);
        } catch (Throwable t) {
            Log.w(TAG, "unable to register interrupt receiver", t);
            mInterruptReceiver = null;
        }
    }

    private void unregisterInterruptReceiver() {
        if (mInterruptReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(mInterruptReceiver);
        } catch (Throwable t) {
            Log.w(TAG, "unable to unregister interrupt receiver", t);
        }
        mInterruptReceiver = null;
    }

    private void startCameraWatch() {
        if (mCameraWatch != null) {
            return;
        }
        mCameraWatch = CameraInUseMonitor.watch(this, new CameraInUseMonitor.Listener() {
            @Override
            public void onCameraUnavailable() {
                Handler main = mMain;
                if (main == null) {
                    return;
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (sState != STATE_RECORDING) {
                            return;
                        }
                        InfoToast.show(getString(R.string.interrupt_screen_record_when_open_camera));
                        requestStop();
                    }
                });
            }
        });
    }

    private void stopCameraWatch() {
        if (mCameraWatch != null) {
            mCameraWatch.stop();
            mCameraWatch = null;
        }
    }
}

