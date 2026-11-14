package com.xtc.utils.text;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rich-text mini-language used by XTC system texts (the vendor
 * {@code com.xtc.utils.system.w.StringResourceUtils} +
 * {@code com.xtc.statusbar.AntiAddictionExitLayout.a(String)} pair).
 *
 * <h3>Syntax (two independent passes, in this order)</h3>
 * <ol>
 *   <li><b>Brand placeholder</b> {@code ${XTC}} is replaced by the region brand:
 *       {@code 小天才} for CN/HK/TW locales, {@code imoo} everywhere else.
 *       It is a plain string substitution and (important) it consumes the
 *       {@code $} that sits immediately before <code>{XTC}</code>, so the common
 *       vendor spelling <code>$${XTC}APP-...</code> becomes
 *       <code>$小天才APP-...</code> and the leading {@code $} then acts as the
 *       highlight delimiter.</li>
 *   <li><b>Highlight delimiters</b>: a pair of {@code $} characters delimits a
 *       run that is painted with a {@link ForegroundColorSpan} of pure white
 *       ({@link #HIGHLIGHT_COLOR}, i.e. {@code -1}) spanning flag
 *       {@code 18} (same literal the vendor used) and the two {@code $} are
 *       removed from the output.</li>
 * </ol>
 *
 * <h3>Vendor bug compatibility</h3>
 * <p>Some vendor strings (notably the English
 * {@code app_durtaion_tip_content}) contain an <b>odd</b> number of {@code $}.
 * The vendor renderer paired markers left to right, dropped the paired ones and
 * left the trailing unpaired {@code $} visible as a literal character, e.g.
 * {@code "... under More - App Disable$ to use ..."}. This implementation does
 * exactly that: markers are paired as (0,1), (2,3), ...; an unpaired trailing
 * marker is emitted verbatim and no exception is thrown. Text between an
 * unpaired opening marker and the end of the string is not highlighted (there is
 * no effect on the output string).</p>
 *
 * <p>No {@code $} escaping exists in the vendor format - every {@code $} is a
 * delimiter. Callers rewriting copy must therefore keep the {@code $} count
 * even and never leave a lone {@code $} next to {@code ${XTC}}.</p>
 */
public final class RichText {

    /** Highlight color used by the vendor renderer: opaque white ({@code -1}). */
    public static final int HIGHLIGHT_COLOR = 0xFFFFFFFF;

    /** Span flag literal used by the vendor renderer ({@code AntiAddictionExitLayout}). */
    public static final int SPAN_FLAG = 18;

    /** Base body color used by the vendor rich-text page ({@code tv_content}). */
    public static final int BASE_COLOR = 0xB3FFFFFF;

    public static final String BRAND_CN = "小天才";
    public static final String BRAND_OVERSEAS = "imoo";
    public static final String DELIMITER = "$";

    private static final Pattern BRAND_PATTERN = Pattern.compile("\\$\\{XTC\\}");

    private RichText() {
    }

    // ---------------------------------------------------------------- brand

    /**
     * Brand word for the current configuration: {@code 小天才} for
     * CN/HK/TW locales, {@code imoo} otherwise (vendor
     * {@code w.a(String)} behavior).
     */
    public static String brandName(Context context) {
        String country = "";
        if (context != null) {
            try {
                Resources resources = context.getResources();
                Configuration configuration = resources.getConfiguration();
                Locale locale = null;
                if (configuration != null) {
                    if (configuration.getLocales() != null
                            && configuration.getLocales().size() > 0) {
                        locale = configuration.getLocales().get(0);
                    }
                }
                if (locale != null && locale.getCountry() != null) {
                    country = locale.getCountry();
                }
            } catch (Throwable ignored) {
                // Fall through to the overseas brand: never crash on formatting.
            }
        }
        if ("CN".equalsIgnoreCase(country)
                || "HK".equalsIgnoreCase(country)
                || "TW".equalsIgnoreCase(country)) {
            return BRAND_CN;
        }
        return BRAND_OVERSEAS;
    }

    /** Replaces every {@code ${XTC}} with the region brand. */
    public static String replaceBrand(Context context, String text) {
        if (text == null) {
            return null;
        }
        String brand = brandName(context);
        return BRAND_PATTERN.matcher(text).replaceAll(Matcher.quoteReplacement(brand));
    }

    // ------------------------------------------------------------ formatting

    /**
     * Formats a raw vendor string: brand substitution first, then the
     * {@code $...$} highlight parsing. Never throws; null in -&gt; null out.
     */
    public static CharSequence format(Context context, String rawText) {
        if (rawText == null) {
            return null;
        }
        return highlight(replaceBrand(context, rawText));
    }

    /** Convenience overload for a string resource. */
    public static CharSequence format(Context context, int stringResId) {
        if (context == null) {
            return "";
        }
        String raw;
        try {
            raw = context.getString(stringResId);
        } catch (Resources.NotFoundException e) {
            return "";
        }
        return format(context, raw);
    }

    /** Convenience overload for already-built text (e.g. from a {@link CharSequence}). */
    public static CharSequence format(Context context, CharSequence rawText) {
        return rawText == null ? null : format(context, rawText.toString());
    }

    // ------------------------------------------------------------- highlight

    /**
     * Parses the {@code $...$} highlight syntax on an already brand-replaced
     * string. Implemented as a single left-to-right pass over the markers as
     * ordinal pairs, which reproduces the vendor output exactly (including the
     * odd-count case, where the unpaired marker is kept literally).
     */
    public static SpannableStringBuilder highlight(String text) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (text == null || text.isEmpty()) {
            return builder;
        }

        // Pass 1: locate markers and decide which ones are paired.
        int markerCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '$') {
                markerCount++;
            }
        }
        boolean[] paired = new boolean[markerCount];
        for (int i = 0; i + 1 < markerCount; i += 2) {
            paired[i] = true;
            paired[i + 1] = true;
        }

        // Pass 2: emit text; paired markers become spans, unpaired stay literal.
        int markerOrdinal = -1;
        int highlightStart = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '$') {
                builder.append(c);
                continue;
            }
            markerOrdinal++;
            if (!paired[markerOrdinal]) {
                // Vendor bug-compat: leave the odd marker visible.
                builder.append(c);
                continue;
            }
            boolean opening = (markerOrdinal % 2) == 0;
            if (opening) {
                highlightStart = builder.length();
            } else {
                int start = highlightStart;
                int end = builder.length();
                if (start >= 0 && end > start) {
                    builder.setSpan(new ForegroundColorSpan(HIGHLIGHT_COLOR),
                            start, end, SPAN_FLAG);
                }
                highlightStart = -1;
            }
        }
        return builder;
    }

    /**
     * Renders the given text as a {@link Spanned} without brand substitution
     * (useful for copy that is not shown in a system context, tests).
     */
    public static CharSequence highlightOnly(String text) {
        return highlight(text);
    }
}
