package com.xtc.assistantapi.message;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Assistant directive: {@code {header, payload}}.
 *
 * Reimplementation of the original {@code com.xtc.assistantapi.message.Directive}
 * (Parcelable + Gson custom deserializer). The rebuild parses with the public
 * {@code org.json} API and keeps the original semantics:
 *  - header is mandatory and must contain a namespace;
 *  - the payload is mapped through {@link PayloadConfig} when a factory was
 *    registered for that namespace/name, otherwise the generic
 *    {@link Payload} is used;
 *  - the raw json and raw payload strings are kept for logging/replies.
 */
public class Directive {

    private final Header header;
    private final Payload payload;
    private final String rawMessage;
    private final String rawPayload;

    public Directive(Header header, Payload payload) {
        this(header, payload, null, null);
    }

    public Directive(Header header, Payload payload, String rawMessage, String rawPayload) {
        this.header = header;
        this.payload = payload;
        this.rawMessage = rawMessage;
        this.rawPayload = rawPayload;
    }

    /**
     * Parses a directive json string. Returns null for an empty message and
     * throws {@link JSONException} for a malformed one.
     */
    public static Directive parse(String rawJson) throws JSONException {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return null;
        }
        JSONObject root = new JSONObject(rawJson);
        JSONObject headerJson = root.optJSONObject("header");
        Header header = Header.parse(headerJson);
        JSONObject payloadJson = root.optJSONObject("payload");
        Payload payload = createPayload(header, payloadJson);
        return new Directive(header, payload, rawJson,
                payloadJson == null ? null : payloadJson.toString());
    }

    private static Payload createPayload(Header header, JSONObject payloadJson) {
        PayloadConfig.Factory factory =
                PayloadConfig.getInstance().find(header.getNamespace(), header.getName());
        if (factory != null && payloadJson != null) {
            try {
                return factory.create(payloadJson);
            } catch (Throwable ignored) {
                // Degrade to the untyped payload instead of dropping the directive.
            }
        }
        return new Payload(payloadJson);
    }

    public Header getHeader() {
        return header;
    }

    public Payload getPayload() {
        return payload;
    }

    public String getName() {
        return header == null ? "" : header.getName();
    }

    public String getNamespace() {
        return header == null ? null : header.getNamespace();
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    @Override
    public String toString() {
        return "Directive{header=" + header + ", payload=" + payload + '}';
    }
}
