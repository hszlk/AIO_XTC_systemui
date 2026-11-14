package com.android.systemui.vassistant;

import android.content.Context;
import android.util.Log;

import com.android.systemui.screenrecord.RecordingService;
import com.xtc.assistantapi.BaseDeviceModule;
import com.xtc.assistantapi.message.MessageCode;

/**
 * Assistant handler of the {@code VideoRecord} directive.
 *
 * Original APK logic: {@code qs.tiles.g} (ScreenRecordTile) implemented
 * {@code SwitchControlHelper.a}: {@code isOpen != (countdown || recording ||
 * exporting)} toggled the recording controller, then replied {@code 000001};
 * exceptions replied {@code 000003}. There is no {@code query} support for this
 * directive (same as the original).
 */
public final class VideoRecordSwitchHandler implements SwitchControlHelper.SwitchHandler {

    private static final String TAG = "VideoRecordDirective";

    private final Context mContext;

    public VideoRecordSwitchHandler(Context context) {
        mContext = context;
    }

    @Override
    public boolean isEnabled() {
        // No cloud / vendor feature switch: screen recording is always available.
        return true;
    }

    @Override
    public void handle(SwitchPayload payload, BaseDeviceModule module) {
        try {
            boolean wantOpen = payload != null && payload.isOpen();
            boolean current = RecordingService.isBusy();
            Log.d(TAG, "set screen record=" + wantOpen + " current=" + current);
            if (wantOpen != current) {
                if (wantOpen) {
                    RecordingService.start(mContext);
                } else {
                    RecordingService.stop(mContext);
                }
            }
            module.sendSuccess();
        } catch (Throwable t) {
            Log.e(TAG, "video record directive failed", t);
            module.sendError(MessageCode.CODE_ERROR);
        }
    }
}
