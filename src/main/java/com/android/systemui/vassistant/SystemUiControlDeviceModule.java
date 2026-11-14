package com.android.systemui.vassistant;

import android.content.Context;
import android.util.Log;

import com.xtc.assistantapi.BaseDeviceModule;
import com.xtc.assistantapi.exception.HandleDirectiveException;
import com.xtc.assistantapi.message.Directive;
import com.xtc.assistantapi.message.MessageCode;
import com.xtc.assistantapi.message.Payload;
import com.xtc.assistantapi.message.PayloadConfig;

/**
 * Device module of the system UI control namespace.
 *
 * Original APK logic: {@code com.android.systemui.vassistant.c}
 * (SystemUiControlDeviceModule) - resolves the directive name in
 * {@code SwitchControlHelper}, answers 000008 when no handler exists, 000006
 * when the feature switch is off, otherwise lets the handler execute and reply.
 * The payload types ({@code SwitchPayload}) are registered for both directive
 * names.
 */
public class SystemUiControlDeviceModule extends BaseDeviceModule {

    private static final String TAG = "SystemUiControlModule";

    public SystemUiControlDeviceModule(Context context) {
        super(context, ApiConstants.NAMESPACE);
    }

    @Override
    public void registerPayloadTypes(PayloadConfig config) {
        config.register(ApiConstants.NAMESPACE, ApiConstants.NAME_FLASHLIGHT, SwitchPayload.FACTORY);
        config.register(ApiConstants.NAMESPACE, ApiConstants.NAME_VIDEO_RECORD, SwitchPayload.FACTORY);
    }

    @Override
    public void handleDirective(Directive directive) throws HandleDirectiveException {
        if (directive == null || directive.getHeader() == null) {
            throw new HandleDirectiveException(
                    HandleDirectiveException.ExceptionType.UNEXPECTED_INFORMATION_RECEIVED,
                    "directive/header is null");
        }
        String name = directive.getName();
        Log.d(TAG, "handleDirective name=" + name + " payload=" + directive.getPayload());

        SwitchControlHelper.SwitchHandler handler = SwitchControlHelper.getInstance().find(name);
        if (handler == null) {
            Log.w(TAG, "no handler registered for " + name);
            sendError(MessageCode.CODE_FUNC_UNSUPPORTED);
            return;
        }
        if (!handler.isEnabled()) {
            Log.i(TAG, "function disabled: " + name);
            sendError(MessageCode.CODE_FUNC_DISABLE);
            return;
        }
        Payload payload = directive.getPayload();
        if (!(payload instanceof SwitchPayload)) {
            Log.e(TAG, "unexpected payload type for " + name);
            sendError(MessageCode.CODE_ERROR);
            return;
        }
        handler.handle((SwitchPayload) payload, this);
    }

    @Override
    public void release() {
        // no resources held
    }
}
