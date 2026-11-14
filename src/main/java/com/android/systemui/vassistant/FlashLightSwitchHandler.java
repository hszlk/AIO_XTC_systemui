package com.android.systemui.vassistant;

import android.content.Context;
import android.util.Log;

import com.android.systemui.flashlight.FlashLightActivity;
import com.android.systemui.flashlight.FlashLightSOSDialog;
import com.xtc.assistantapi.BaseDeviceModule;
import com.xtc.assistantapi.message.DirectiveResult;
import com.xtc.assistantapi.message.MessageCode;

import java.util.HashMap;

/**
 * Assistant handler of the {@code FlashLight} directive.
 *
 * Original APK logic: {@code qs.tiles.d} (FlashlightTile) implemented
 * {@code SwitchControlHelper.a}: on {@code target == "query"} it replied
 * {@code 000001} with params {@code {"flashlightEnabled": "true|false"}}; on a
 * state change it toggled the flashlight controller; exceptions replied
 * {@code 000003}. The tile itself is not part of this rebuild, so the logic lives
 * in this handler and drives the screen-flashlight activities directly.
 */
public final class FlashLightSwitchHandler implements SwitchControlHelper.SwitchHandler {

    private static final String TAG = "FlashLightDirective";

    private final Context mContext;

    public FlashLightSwitchHandler(Context context) {
        mContext = context;
    }

    @Override
    public boolean isEnabled() {
        // No cloud / vendor feature switch: the flashlight is always available.
        return true;
    }

    @Override
    public void handle(SwitchPayload payload, BaseDeviceModule module) {
        try {
            boolean current = FlashLightActivity.isOn() || FlashLightSOSDialog.isSosOn();
            if (payload != null && payload.isQuery()) {
                HashMap<String, String> params = new HashMap<>();
                params.put(ApiConstants.PARAM_FLASHLIGHT_ENABLED, String.valueOf(current));
                DirectiveResult result = new DirectiveResult();
                result.setErrorCode(MessageCode.CODE_COMPLETE);
                result.setParams(params);
                module.sendResult(result);
                return;
            }
            boolean wantOpen = payload != null && payload.isOpen();
            Log.d(TAG, "set flashlight=" + wantOpen + " current=" + current);
            if (wantOpen != current) {
                if (wantOpen) {
                    FlashLightActivity.requestOn(mContext);
                } else {
                    if (FlashLightSOSDialog.isShowing()) {
                        FlashLightSOSDialog.requestClose();
                    }
                    FlashLightActivity.requestOff();
                }
            }
            module.sendSuccess();
        } catch (Throwable t) {
            Log.e(TAG, "flashlight directive failed", t);
            module.sendError(MessageCode.CODE_ERROR);
        }
    }
}
