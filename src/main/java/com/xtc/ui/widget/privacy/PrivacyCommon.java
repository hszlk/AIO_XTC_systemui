package com.xtc.ui.widget.privacy;

/**
 * Intent-extra / component-version constants of the vendor privacy module
 * (decompiled {@code com.xtc.ui.widget.privacy.PrivacyCommon}). Kept verbatim so
 * existing callers keep compiling and the wire names stay stable.
 */
public interface PrivacyCommon {

    /** Privacy component (dialog/page) version. */
    @interface PrivacyComponentVersion {
        int VERSION_DEFAULT = 1;
        int VERSION_V2 = 2;
    }

    /** Intent extras read/written by the privacy pages. */
    interface PrivacyExtras {
        /** Caption shown on the entry link, e.g. {@code 用户服务协议}. */
        String EXTRA_CUSTOM_STRING = "custom_string";
        /** Package the privacy/agreement text belongs to. */
        String EXTRA_PACKAGE_NAME = "package_name";
        /** Source URL (NOT loaded in this rebuild; kept for intent compatibility). */
        String EXTRA_URL = "url";
        /** One of {@link UrlType}. */
        String EXTRA_URL_TYPE = "url_type";
        /** One of {@link PrivacyComponentVersion}. */
        String EXTRA_VERSION = "version";
    }

    /** Which kind of document a URL refers to. */
    @interface UrlType {
        int OTHER_TYPE = 0;
        int PRIVACY_TYPE = 1;
        int AGREEMENT_TYPE = 2;
    }
}
