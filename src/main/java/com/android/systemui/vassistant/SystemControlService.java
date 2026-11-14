package com.android.systemui.vassistant;

import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.xtc.assistantapi.BaseDeviceModule;
import com.xtc.assistantapi.DeviceModuleManager;
import com.xtc.assistantapi.launch.AssistantForegroundService;

import java.util.Collections;
import java.util.List;

/**
 * Voice-assistant system control service.
 *
 * Manifest contract (unchanged, it is what the assistant app looks for):
 * {@code exported=true}, intent filter
 * {@code com.xtc.assistantapi.directive}, meta-data
 * {@code assistantapi-directive=[FlashLight,VideoRecord]}.
 *
 * Original APK logic: {@code com.android.systemui.vassistant.SystemControlService}
 * extends {@code AssistantForegroundService}, {@code onBind} returns null (this is
 * a startService target, not a binder service), hosts one
 * {@code SystemUiControlDeviceModule} and dispatches the incoming directive json.
 * The rebuilt handlers for {@code FlashLight} / {@code VideoRecord} are registered
 * here because the vendor QS tiles that used to register themselves are not part
 * of this rebuild.
 */
public class SystemControlService extends AssistantForegroundService {

    private static final String TAG = "SystemControlService";

    private SystemUiControlDeviceModule mDeviceModule;

    @Override
    public IBinder onBind(Intent intent) {
        // Must stay null: the assistant side uses startService + intent extras.
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerHandlers();
        Log.d(TAG, "assistant system control service created");
    }

    @Override
    public List<? extends BaseDeviceModule> getDeviceModules() {
        if (mDeviceModule == null) {
            mDeviceModule = new SystemUiControlDeviceModule(getApplicationContext());
        }
        return Collections.singletonList(mDeviceModule);
    }

    private void registerHandlers() {
        SwitchControlHelper helper = SwitchControlHelper.getInstance();
        helper.register(ApiConstants.NAME_FLASHLIGHT, new FlashLightSwitchHandler(this));
        helper.register(ApiConstants.NAME_VIDEO_RECORD, new VideoRecordSwitchHandler(this));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SwitchControlHelper helper = SwitchControlHelper.getInstance();
        helper.unregister(ApiConstants.NAME_FLASHLIGHT);
        helper.unregister(ApiConstants.NAME_VIDEO_RECORD);
        SystemUiControlDeviceModule module = mDeviceModule;
        mDeviceModule = null;
        if (module != null) {
            DeviceModuleManager.getInstance().unregister(module);
            module.release();
        }
        Log.d(TAG, "assistant system control service destroyed");
    }
}
