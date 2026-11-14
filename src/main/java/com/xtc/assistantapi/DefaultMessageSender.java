package com.xtc.assistantapi;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.xtc.assistantapi.message.DirectiveResult;

/**
 * Default reply sender.
 *
 * Original APK logic: {@code assistantapi.b.i} (XTCMessageSender) called the
 * local content provider of the installed assistant app
 * ({@code content://com.xtc.vassistant.provider}, method
 * {@code callVAssistantResponse}) and {@code assistantapi.b.a}
 * (DefaultMessageSender) which only logged. The rebuild keeps both behaviours and
 * adds a package-local broadcast so any in-process consumer (e.g. a rebuilt QS
 * tile) can observe a reply without a binder:
 *
 *  1. {@code Log.d} the result json (diagnostics),
 *  2. {@code sendBroadcast} with {@link #ACTION_RESULT} inside our own package,
 *  3. best-effort {@code ContentResolver.call} to the assistant provider when it
 *     is installed - pure local IPC, no network, no cloud.
 */
public final class DefaultMessageSender implements IMessageSender {

    private static final String TAG = "AssistantMessageSender";

    /** Local, package-scoped broadcast carrying the reply json. */
    public static final String ACTION_RESULT = "com.xtc.assistantapi.directive.result";
    public static final String EXTRA_RESULT_JSON = "assistantapi-directive-result";

    private static final DefaultMessageSender INSTANCE = new DefaultMessageSender();

    private DefaultMessageSender() {
    }

    public static DefaultMessageSender getInstance() {
        return INSTANCE;
    }

    @Override
    public void send(Context context, DirectiveResult result) {
        if (result == null) {
            return;
        }
        String json = result.toJson();
        Log.d(TAG, "send: " + json);
        if (context == null) {
            return;
        }
        try {
            Intent broadcast = new Intent(ACTION_RESULT);
            broadcast.setPackage(context.getPackageName());
            broadcast.putExtra(EXTRA_RESULT_JSON, json);
            context.sendBroadcast(broadcast);
        } catch (Throwable t) {
            Log.w(TAG, "unable to broadcast directive result", t);
        }
        try {
            Bundle extras = new Bundle();
            context.getContentResolver().call(
                    Uri.parse(IntentAction.PROVIDER_VASSISTANT),
                    IntentAction.METHOD_CALL_RESPONSE,
                    json, extras);
        } catch (Throwable t) {
            // The assistant app is not installed / does not expose the provider.
            Log.d(TAG, "assistant provider not available: " + t);
        }
    }
}
