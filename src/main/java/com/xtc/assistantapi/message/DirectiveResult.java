package com.xtc.assistantapi.message;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

/**
 * Directive reply model.
 *
 * Reimplementation of the original {@code com.xtc.assistantapi.message.DirectiveResult}
 * (the fields used by the system UI control protocol are kept; the vendor card
 * data types are reduced to the plain string fields so the class needs no Gson).
 * The reply is serialized with {@link #toJson()} and handed to an
 * {@code IMessageSender}.
 */
public class DirectiveResult {

    private String directiveId;
    private String errorCode;
    private String dec;
    private String packageName;
    private String cardType;
    private String showType;
    private String rawMessage;
    private List<String> needOtherSlots;
    private int objectCount;
    private HashMap<String, String> params;

    public DirectiveResult() {
    }

    public DirectiveResult(String errorCode, String dec) {
        this.errorCode = errorCode;
        this.dec = dec;
    }

    public DirectiveResult(String errorCode, String dec, String packageName) {
        this.errorCode = errorCode;
        this.dec = dec;
        this.packageName = packageName;
    }

    public String getDirectiveId() {
        return directiveId;
    }

    public void setDirectiveId(String directiveId) {
        this.directiveId = directiveId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getDec() {
        return dec;
    }

    public void setDec(String dec) {
        this.dec = dec;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getShowType() {
        return showType;
    }

    public void setShowType(String showType) {
        this.showType = showType;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public void setRawMessage(String rawMessage) {
        this.rawMessage = rawMessage;
    }

    public List<String> getNeedOtherSlots() {
        return needOtherSlots;
    }

    public void setNeedOtherSlots(List<String> needOtherSlots) {
        this.needOtherSlots = needOtherSlots;
    }

    public int getObjectCount() {
        return objectCount;
    }

    public void setObjectCount(int objectCount) {
        this.objectCount = objectCount;
    }

    public HashMap<String, String> getParams() {
        return params;
    }

    public void setParams(HashMap<String, String> params) {
        this.params = params;
    }

    /** Serializes the reply the same way the original Gson reply did. */
    public String toJson() {
        JSONObject json = new JSONObject();
        try {
            if (directiveId != null) {
                json.put("directiveId", directiveId);
            }
            if (errorCode != null) {
                json.put("errorCode", errorCode);
            }
            if (dec != null) {
                json.put("dec", dec);
            }
            if (packageName != null) {
                json.put("packageName", packageName);
            }
            if (cardType != null) {
                json.put("cardType", cardType);
            }
            if (showType != null) {
                json.put("showType", showType);
            }
            if (rawMessage != null) {
                json.put("rawMessage", rawMessage);
            }
            if (needOtherSlots != null) {
                json.put("needOtherSlots", needOtherSlots.toString());
            }
            if (objectCount != 0) {
                json.put("objectCount", objectCount);
            }
            if (params != null && !params.isEmpty()) {
                JSONObject paramsJson = new JSONObject();
                for (String key : params.keySet()) {
                    paramsJson.put(key, params.get(key));
                }
                json.put("params", paramsJson);
            }
        } catch (Throwable ignored) {
            // JSONObject.put only throws for non-finite doubles; ignore.
        }
        return json.toString();
    }

    @Override
    public String toString() {
        return "DirectiveResult{directiveId='" + directiveId + "', errorCode='" + errorCode
                + "', dec='" + dec + "', params=" + params + '}';
    }
}
