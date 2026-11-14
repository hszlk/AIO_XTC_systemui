package com.xtc.assistantapi;

import android.content.Context;
import android.util.Log;

import com.xtc.assistantapi.exception.HandleDirectiveException;
import com.xtc.assistantapi.message.Directive;
import com.xtc.assistantapi.message.DirectiveResult;
import com.xtc.assistantapi.message.MessageCode;
import com.xtc.assistantapi.message.PayloadConfig;

/**
 * Base class of an assistant device module.
 *
 * Original APK logic: {@code com.xtc.assistantapi.a.a} (BaseDeviceModule) - a
 * namespace + directive id holder with reply helpers that route through
 * {@code DeviceModuleManager.getMessageSender()}. The rebuild keeps the same
 * contract and adds the module context (module implementations need it to talk
 * to the system UI services).
 */
public abstract class BaseDeviceModule {

    private static final String TAG = "BaseDeviceModule";

    private final Context mContext;
    private String mNamespace;
    private String mDirectiveId;

    public BaseDeviceModule(Context context, String namespace) {
        mContext = context == null ? null : context.getApplicationContext();
        mNamespace = namespace;
    }

    /** Handles one directive; runs on the main thread. */
    public abstract void handleDirective(Directive directive) throws HandleDirectiveException;

    /** Releases module resources when the hosting service dies. */
    public abstract void release();

    /** Registers the payload types this module understands (optional). */
    public void registerPayloadTypes(PayloadConfig config) {
        // default: no typed payloads
    }

    public String getNamespace() {
        return mNamespace;
    }

    public void setNamespace(String namespace) {
        mNamespace = namespace;
    }

    public String getDirectiveId() {
        return mDirectiveId;
    }

    public void setDirectiveId(String directiveId) {
        mDirectiveId = directiveId;
    }

    public Context getContext() {
        return mContext;
    }

    /** Sends a reply through the configured sender, filling in the directive id. */
    public void sendResult(DirectiveResult result) {
        if (result == null) {
            return;
        }
        result.setDirectiveId(mDirectiveId);
        Log.d(TAG, "sendResult " + result);
        try {
            DeviceModuleManager.getInstance().getMessageSender().send(mContext, result);
        } catch (Throwable t) {
            Log.e(TAG, "unable to send directive result", t);
        }
    }

    public void sendSuccess() {
        sendResult(new DirectiveResult(MessageCode.CODE_COMPLETE, ""));
    }

    public void sendError(String errorCode) {
        sendResult(new DirectiveResult(errorCode, ""));
    }

    public void sendError(String errorCode, String dec) {
        sendResult(new DirectiveResult(errorCode, dec));
    }
}
