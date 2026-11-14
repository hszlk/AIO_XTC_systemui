package com.xtc.assistantapi.message;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Directive header: {@code {namespace, name, directiveId}}.
 *
 * Reimplementation of the original {@code com.xtc.assistantapi.message.Header}
 * (which was parsed by Gson). Parsed by hand from {@link JSONObject}; the
 * original contract is preserved: a null namespace is a hard error
 * ({@link IllegalArgumentException}).
 */
public class Header {

    private String namespace;
    private String name;
    private String directiveId;

    public Header() {
    }

    public Header(String namespace, String name) {
        setNamespace(namespace);
        setName(name);
    }

    public Header(String namespace, String name, String directiveId) {
        this.namespace = namespace;
        this.name = name;
        this.directiveId = directiveId;
    }

    /** Parses a header object; throws when the required namespace is missing. */
    public static Header parse(JSONObject json) throws JSONException {
        if (json == null) {
            throw new JSONException("header is null");
        }
        String namespace = json.optString("namespace", null);
        if (namespace == null || namespace.isEmpty()) {
            throw new JSONException("header namespace must not be null");
        }
        Header header = new Header();
        header.namespace = namespace;
        header.name = json.optString("name", null);
        header.directiveId = json.optString("directiveId", null);
        return header;
    }

    public final String getNamespace() {
        return namespace;
    }

    public final void setNamespace(String namespace) {
        if (namespace == null) {
            throw new IllegalArgumentException("Header namespace must not be null");
        }
        this.namespace = namespace;
    }

    public final String getName() {
        return name;
    }

    public final void setName(String name) {
        this.name = name;
    }

    public String getDirectiveId() {
        return directiveId;
    }

    public void setDirectiveId(String directiveId) {
        this.directiveId = directiveId;
    }

    @Override
    public String toString() {
        return "Header{namespace='" + namespace + "', name='" + name
                + "', directiveId='" + directiveId + "'}";
    }
}
