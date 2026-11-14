package com.xtc.assistantapi.message;

import org.json.JSONObject;

import java.util.HashMap;

/**
 * Payload type registry: {@code namespace + name -> Factory}.
 *
 * Reimplementation of the original {@code PayloadConfig} (which mapped
 * namespace+name to a Gson payload class). Each device module registers the
 * payload types it understands; an unknown directive keeps the generic
 * {@link Payload} base class.
 */
public final class PayloadConfig {

    /** Creates a typed payload from the raw payload json. */
    public interface Factory {
        Payload create(JSONObject json);
    }

    private static final PayloadConfig INSTANCE = new PayloadConfig();

    private final HashMap<String, Factory> mFactories = new HashMap<>();

    private PayloadConfig() {
    }

    public static PayloadConfig getInstance() {
        return INSTANCE;
    }

    public synchronized void register(String namespace, String name, Factory factory) {
        if (namespace == null || name == null || factory == null) {
            return;
        }
        mFactories.put(key(namespace, name), factory);
    }

    public synchronized Factory find(String namespace, String name) {
        if (namespace == null || name == null) {
            return null;
        }
        return mFactories.get(key(namespace, name));
    }

    public synchronized void clear() {
        mFactories.clear();
    }

    private static String key(String namespace, String name) {
        return namespace + '/' + name;
    }
}
