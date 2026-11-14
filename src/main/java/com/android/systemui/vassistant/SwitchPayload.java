package com.android.systemui.vassistant;

import com.xtc.assistantapi.message.Payload;
import com.xtc.assistantapi.message.PayloadConfig;

import org.json.JSONObject;

/**
 * Payload of the switch-like directives ({@code FlashLight}, {@code VideoRecord}).
 *
 * (Original: {@code com.android.systemui.vassistant.SwitchPayload}, a Parcelable
 * Gson payload with {@code isOpen} + {@code target}.) {@code target = "query"}
 * is the query semantic: report the current state instead of changing it.
 */
public class SwitchPayload extends Payload {

    public static final String TARGET_QUERY = "query";

    /** Registers this payload for a directive name. */
    public static final PayloadConfig.Factory FACTORY = new PayloadConfig.Factory() {
        @Override
        public Payload create(JSONObject json) {
            return new SwitchPayload(json);
        }
    };

    private boolean isOpen;
    private String target;

    public SwitchPayload(boolean isOpen, String target) {
        super();
        this.isOpen = isOpen;
        this.target = target;
    }

    public SwitchPayload(JSONObject json) {
        super(json);
        if (json != null) {
            this.isOpen = json.optBoolean("isOpen", false);
            this.target = json.optString("target", null);
        }
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        isOpen = open;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public boolean isQuery() {
        return TARGET_QUERY.equals(target);
    }

    @Override
    public String toString() {
        return "SwitchPayload{isOpen=" + isOpen + ", target='" + target + "'}";
    }
}
