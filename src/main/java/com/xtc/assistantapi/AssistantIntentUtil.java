package com.xtc.assistantapi;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.xtc.assistantapi.message.DirectiveResult;
import com.xtc.assistantapi.message.MessageCode;

import java.util.List;

/**
 * Entry helper for directive intents.
 *
 * Original APK logic: {@code com.xtc.assistantapi.d.a} (AssistantIntentUtil) read
 * the {@code assistantapi-directive} string extra of a
 * {@code com.xtc.assistantapi.directive} intent, registered every module of the
 * hosting service and dispatched the json.
 *
 * The service is a startService target (not a binder target): the reply is sent
 * through the configured {@link IMessageSender}. Dispatch failures are reported
 * back with the protocol error codes so the assistant side always gets an answer
 * instead of silence.
 */
public final class AssistantIntentUtil {

    private static final String TAG = "AssistantIntentUtil";

    private AssistantIntentUtil() {
    }

    /**
     * Handles a directive intent.
     *
     * @return a {@link DirectiveCode}; {@link DirectiveCode#BAD_REQUEST} is also
     *         used for "not a directive intent" (nothing is dispatched then).
     */
    public static int dispatch(Context context, Intent intent,
            List<? extends BaseDeviceModule> modules) {
        if (intent == null) {
            return DirectiveCode.BAD_REQUEST;
        }
        String action = intent.getAction();
        if (!IntentAction.ACTION_DIRECTIVE.equals(action)) {
            Log.d(TAG, "ignoring non-directive action " + action);
            return DirectiveCode.BAD_REQUEST;
        }
        String rawJson = intent.getStringExtra(IntentAction.EXTRA_DIRECTIVE);
        DeviceModuleManager manager = DeviceModuleManager.getInstance();
        if (modules != null) {
            for (BaseDeviceModule module : modules) {
                manager.register(module);
            }
        }
        int code = manager.dispatchJson(rawJson);
        Log.d(TAG, "dispatch code=" + code + " directive=" + rawJson);
        if (code != DirectiveCode.OK) {
            String errorCode = code == DirectiveCode.NOT_FOUND
                    ? MessageCode.CODE_FUNC_UNSUPPORTED : MessageCode.CODE_ERROR;
            DirectiveResult result = new DirectiveResult(errorCode,
                    "directive dispatch failed: " + code);
            try {
                manager.getMessageSender().send(context, result);
            } catch (Throwable t) {
                Log.e(TAG, "unable to report dispatch failure", t);
            }
        }
        return code;
    }
}
