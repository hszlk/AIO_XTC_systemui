package com.xtc.assistantapi;

/**
 * Intent contract constants of the assistant API.
 *
 * Values copied from the original {@code com.xtc.assistantapi.c.h} (IntentAction)
 * - they are an external contract with the assistant app, do not change them.
 */
public final class IntentAction {

    /** Action of the directive intent the assistant sends to a device module. */
    public static final String ACTION_DIRECTIVE = "com.xtc.assistantapi.directive";
    /** Action of the assistant deeplink intent. */
    public static final String ACTION_DEEPLINK = "com.xtc.assistantapi.deeplink";

    /** String extra carrying the directive json. */
    public static final String EXTRA_DIRECTIVE = "assistantapi-directive";
    /** String extra carrying the target activity of a deeplink. */
    public static final String EXTRA_TARGET_ACTIVITY = "assistantapi-targetActivity";

    /** Local provider of the installed assistant app (reply channel). */
    public static final String PROVIDER_VASSISTANT = "content://com.xtc.vassistant.provider";
    public static final String METHOD_CALL_RESPONSE = "callVAssistantResponse";

    private IntentAction() {
    }
}
