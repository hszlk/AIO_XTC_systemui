package com.xtc.assistantapi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.xtc.assistantapi.message.Directive;
import com.xtc.assistantapi.message.DirectiveResult;
import com.xtc.assistantapi.message.MessageCode;
import com.xtc.assistantapi.message.PayloadConfig;

import java.util.HashMap;

/**
 * Directive dispatcher: {@code raw json -> Directive -> device module on the main
 * thread}.
 *
 * Original APK logic: {@code com.xtc.assistantapi.a} (DeviceModuleManager) -
 * singleton, Gson-based json -> Directive conversion, modules keyed by the
 * namespace suffix from "device_interface", dispatch posted to the main looper,
 * reply sender selection by namespace prefix. Reimplemented with the public
 * {@code org.json} API (no Gson) and the same 200/400/404/500 return codes
 * ({@link DirectiveCode}).
 */
public final class DeviceModuleManager {

    private static final String TAG = "DeviceModuleManager";
    private static final String NAMESPACE_MARKER = "device_interface";

    private static final DeviceModuleManager INSTANCE = new DeviceModuleManager();

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final HashMap<String, BaseDeviceModule> mModules = new HashMap<>();
    private volatile IMessageSender mSender = DefaultMessageSender.getInstance();

    private DeviceModuleManager() {
    }

    public static DeviceModuleManager getInstance() {
        return INSTANCE;
    }

    /** Registers (or replaces) a module and its payload types. */
    public void register(BaseDeviceModule module) {
        if (module == null) {
            return;
        }
        String key = normalizeNamespace(module.getNamespace());
        synchronized (mModules) {
            mModules.put(key, module);
        }
        module.registerPayloadTypes(PayloadConfig.getInstance());
        Log.d(TAG, "register module " + key);
    }

    public void unregister(BaseDeviceModule module) {
        if (module == null) {
            return;
        }
        synchronized (mModules) {
            String key = normalizeNamespace(module.getNamespace());
            if (mModules.get(key) == module) {
                mModules.remove(key);
            }
        }
    }

    /** Registers a custom reply sender (defaults to {@link DefaultMessageSender}). */
    public void setMessageSender(IMessageSender sender) {
        mSender = sender == null ? DefaultMessageSender.getInstance() : sender;
    }

    public IMessageSender getMessageSender() {
        return mSender;
    }

    /** Parses and dispatches a raw directive json; returns a {@link DirectiveCode}. */
    public int dispatchJson(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            Log.w(TAG, "dispatchJson: empty directive");
            return DirectiveCode.BAD_REQUEST;
        }
        Directive directive;
        try {
            directive = Directive.parse(rawJson);
        } catch (Throwable t) {
            Log.e(TAG, "dispatchJson: malformed directive", t);
            return DirectiveCode.BAD_REQUEST;
        }
        if (directive == null || directive.getHeader() == null) {
            return DirectiveCode.BAD_REQUEST;
        }
        return dispatch(directive);
    }

    /** Dispatches an already parsed directive; returns a {@link DirectiveCode}. */
    public int dispatch(Directive directive) {
        if (directive == null || directive.getHeader() == null) {
            return DirectiveCode.BAD_REQUEST;
        }
        final BaseDeviceModule module;
        synchronized (mModules) {
            String key = normalizeNamespace(directive.getHeader().getNamespace());
            module = mModules.get(key);
        }
        if (module == null) {
            Log.w(TAG, "dispatch: no module for namespace "
                    + directive.getHeader().getNamespace());
            return DirectiveCode.NOT_FOUND;
        }
        try {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    module.setDirectiveId(directive.getHeader().getDirectiveId());
                    try {
                        module.handleDirective(directive);
                    } catch (Throwable t) {
                        Log.e(TAG, "handleDirective failed: " + directive.getName(), t);
                        module.sendResult(new DirectiveResult(
                                MessageCode.CODE_ERROR, String.valueOf(t.getMessage())));
                    }
                }
            });
            return DirectiveCode.OK;
        } catch (Throwable t) {
            Log.e(TAG, "dispatch failed", t);
            return DirectiveCode.INTERNAL_ERROR;
        }
    }

    /** Module key: the namespace from "device_interface" onwards (original rule). */
    public static String normalizeNamespace(String namespace) {
        if (namespace == null) {
            return "";
        }
        int index = namespace.indexOf(NAMESPACE_MARKER);
        return index < 0 ? namespace : namespace.substring(index);
    }
}
