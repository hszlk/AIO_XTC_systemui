package com.xtc.assistantapi.message;

import org.json.JSONObject;

/**
 * Base payload: an untyped JSON payload.
 *
 * Reimplementation of the original {@code com.xtc.assistantapi.message.Payload}
 * (Parcelable + Gson). The rebuild keeps it a plain POJO that wraps the raw
 * {@link JSONObject}; typed payloads (e.g. the assistant's
 * {@code SwitchPayload}) extend it and are created by a
 * {@link PayloadConfig.Factory} registered per directive name.
 *
 * An unregistered payload type degrades to this class instead of failing, which
 * is the original degradation rule (no NullPointerException on unknown payloads).
 */
public class Payload {

    private final JSONObject mJson;

    public Payload() {
        this(null);
    }

    public Payload(JSONObject json) {
        mJson = json;
    }

    /** Raw payload json, may be null when the directive carried no payload. */
    public JSONObject getJson() {
        return mJson;
    }

    public boolean hasJson() {
        return mJson != null;
    }

    public String optString(String key, String fallback) {
        return mJson == null ? fallback : mJson.optString(key, fallback);
    }

    public boolean optBoolean(String key, boolean fallback) {
        return mJson != null && mJson.optBoolean(key, fallback);
    }

    public int optInt(String key, int fallback) {
        return mJson == null ? fallback : mJson.optInt(key, fallback);
    }

    @Override
    public String toString() {
        return "Payload{" + (mJson == null ? "" : mJson.toString()) + '}';
    }
}
