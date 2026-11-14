package com.xtc.assistantapi.message;

/**
 * Result codes of the assistant protocol (verbatim values of the original
 * {@code MessageCode} interface; they are an external contract with the
 * assistant app, do not rename the string values).
 */
public interface MessageCode {
    String CODE_APP_NOT_EXIT = "000005";
    String CODE_CANCEL = "000010";
    String CODE_CLASS_DISABLE = "000007";
    String CODE_COMPLETE = "000001";
    String CODE_ERROR = "000003";
    String CODE_FUNC_DISABLE = "000006";
    String CODE_FUNC_UNSUPPORTED = "000008";
    String CODE_NEED_CONFIRM = "000009";
    String CODE_NEXT = "000002";
    String CODE_SELECT = "000011";
    String NEED_ADDITIONAL_SLOTS = "000004";
}
