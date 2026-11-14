package com.android.systemui.qs;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal .lrc reader: parses [mm:ss.xx] lines and finds same-name files. */
public final class LrcUtil {

    public static final class Line {
        public final long timeMs;
        public final String text;

        Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    private static final String[] DIRS = {"/sdcard/Music", "/sdcard/Download",
            "/sdcard/NETEASE", "/sdcard/netease/cloudmusic/Music", "/sdcard/qqmusic/song",
            "/sdcard/lrc", "/sdcard/Lyrics", "/sdcard"};
    private static final int MAX_FILES = 4000;

    private LrcUtil() {
    }

    /** Parses LRC text into sorted (time, text) lines. */
    public static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<Line>();
        if (lrc == null || lrc.isEmpty()) {
            return out;
        }
        for (String raw : lrc.split("\\r?\\n")) {
            Matcher m = TIME.matcher(raw);
            while (m.find()) {
                try {
                    long min = Long.parseLong(m.group(1));
                    long sec = Long.parseLong(m.group(2));
                    long frac = 0;
                    if (m.group(3) != null) {
                        String f = m.group(3);
                        frac = Long.parseLong(f);
                        if (f.length() == 2) {
                            frac *= 10;
                        } else if (f.length() == 1) {
                            frac *= 100;
                        }
                    }
                    long time = (min * 60 + sec) * 1000 + frac;
                    String text = raw.substring(Math.min(m.end(), raw.length())).trim();
                    if (!text.isEmpty()) {
                        out.add(new Line(time, text));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Collections.sort(out, new Comparator<Line>() {
            @Override
            public int compare(Line a, Line b) {
                return Long.compare(a.timeMs, b.timeMs);
            }
        });
        return out;
    }

    /** Current line for the given playback position (or ""). */
    public static String currentLine(List<Line> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        String current = "";
        for (Line line : lines) {
            if (line.timeMs <= positionMs) {
                current = line.text;
            } else {
                break;
            }
        }
        return current;
    }

    /** Looks for a .lrc matching any candidate ("title" or "artist - title"). */
    public static List<Line> findForTitle(String... titles) {
        List<String> cands = new ArrayList<String>();
        for (String t : titles) {
            if (t == null) {
                continue;
            }
            String s = sanitize(t);
            if (!s.isEmpty() && !cands.contains(s)) {
                cands.add(s);
            }
        }
        if (cands.isEmpty()) {
            return new ArrayList<Line>();
        }
        for (String dir : DIRS) {
            File root = new File(dir);
            if (!root.isDirectory()) {
                continue;
            }
            File hit = search(root, cands, 0);
            if (hit != null) {
                try {
                    return parse(readText(hit));
                } catch (Throwable ignored) {
                }
            }
        }
        return new ArrayList<Line>();
    }

    private static String sanitize(String t) {
        return t.replaceAll("[\\\\/:*?\"<>|]", "").trim().toLowerCase(Locale.US);
    }

    /** Breadth-first: any .lrc in this folder wins, then recurse (depth<=3). */
    private static File search(File dir, List<String> cands, int depth) {
        if (depth > 3) {
            return null;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        int seen = 0;
        for (File f : files) {
            if (seen++ > MAX_FILES) {
                return null;
            }
            if (f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".lrc")) {
                String name = sanitize(f.getName());
                for (String c : cands) {
                    if (name.contains(c)) {
                        return f;
                    }
                }
            }
        }
        for (File f : files) {
            if (f.isDirectory() && !f.getName().equals("Android")) {
                File r = search(f, cands, depth + 1);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /** UTF-8 with GBK fallback (most CN .lrc files are GBK). */
    private static String readText(File file) throws Exception {
        java.io.FileInputStream fis = new java.io.FileInputStream(file);
        try {
            int cap = (int) Math.min(file.length(), 512 * 1024L);
            byte[] buf = new byte[cap];
            int read = 0;
            while (read < cap) {
                int n = fis.read(buf, read, cap - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            String utf8 = new String(buf, 0, read, "UTF-8");
            if (utf8.indexOf('\uFFFD') < 0) {
                return utf8;
            }
            return new String(buf, 0, read, "GBK");
        } finally {
            fis.close();
        }
    }
}
