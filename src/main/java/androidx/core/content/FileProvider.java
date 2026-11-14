package androidx.core.content;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Framework-only re-implementation of {@code androidx.core.content.FileProvider}
 * (the class name and behavior are kept because the vendor manifest declares
 * exactly this provider).
 *
 * <p>Manifest contract (vendor manifest, unchanged):</p>
 * <pre>
 * &lt;provider android:name="androidx.core.content.FileProvider"
 *           android:authorities="com.android.systemui.fileprovider"
 *           android:exported="false"
 *           android:grantUriPermissions="true"&gt;
 *     &lt;meta-data android:name="android.support.FILE_PROVIDER_PATHS"
 *                android:resource="@xml/fileprovider"/&gt;
 * &lt;/provider&gt;
 * </pre>
 *
 * <p>{@code res/xml/fileprovider.xml} declares exactly one root:
 * {@code <cache-path name="leak" path="leak/"/>}, i.e. shared files live under
 * {@code /data/user/&lt;user&gt;/com.android.systemui/cache/leak/} and the URIs look
 * like {@code content://com.android.systemui.fileprovider/leak/<file>}. The
 * parser below supports the full set of androidx root tags
 * ({@code root-path}, {@code files-path}, {@code cache-path},
 * {@code external-path}, {@code external-files-path},
 * {@code external-cache-path}) so a future path declaration keeps working; if
 * the metadata/resource is missing it falls back to the declared
 * {@code cache/leak} root.</p>
 *
 * <p>Security: {@link #getUriForFile} only accepts files inside a configured
 * root (canonical-path comparison), and every inbound URI is canonicalized and
 * re-checked so {@code ../} traversal cannot escape the root.</p>
 */
public class FileProvider extends ContentProvider {

    private static final String TAG = "SysUiFileProvider";

    private static final String META_DATA_FILE_PROVIDER_PATHS =
            "android.support.FILE_PROVIDER_PATHS";
    private static final String FALLBACK_XML_NAME = "fileprovider";
    private static final String TAG_ROOT_PATH = "root-path";
    private static final String TAG_FILES_PATH = "files-path";
    private static final String TAG_CACHE_PATH = "cache-path";
    private static final String TAG_EXTERNAL = "external-path";
    private static final String TAG_EXTERNAL_FILES = "external-files-path";
    private static final String TAG_EXTERNAL_CACHE = "external-cache-path";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_PATH = "path";
    private static final String[] COLUMNS = {"_display_name", "_size"};
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    private static final String FALLBACK_ROOT_NAME = "leak";
    private static final String FALLBACK_ROOT_PATH = "leak/";

    private static final Map<String, PathStrategy> sStrategies = new HashMap<>();

    private PathStrategy mStrategy;
    private String mAuthority;

    // ------------------------------------------------------------ public API

    /** Builds a {@code content://} URI for {@code file}; throws when outside all roots. */
    public static Uri getUriForFile(Context context, String authority, File file) {
        if (context == null || authority == null || file == null) {
            throw new NullPointerException("context, authority and file must not be null");
        }
        return getPathStrategy(context, authority).getUriForFile(file);
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        // The vendor manifest declares exported=false + grantUriPermissions=true.
        // Do not throw here (a hard failure in attachInfo kills the whole process);
        // log loudly instead so a packaging regression is visible.
        if (info != null) {
            if (info.exported) {
                Log.w(TAG, "provider is exported; it should be exported=false");
            }
            if (!info.grantUriPermissions) {
                Log.w(TAG, "provider does not grant uri permissions");
            }
            mAuthority = info.authority;
        }
        if (mAuthority == null) {
            mAuthority = context.getPackageName() + ".fileprovider";
        }
        try {
            mStrategy = getPathStrategy(context, mAuthority);
        } catch (Throwable t) {
            Log.e(TAG, "failed to parse path strategy, using cache/leak fallback", t);
            mStrategy = fallbackStrategy(context, mAuthority);
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    // ------------------------------------------------------ path strategies

    private static PathStrategy getPathStrategy(Context context, String authority) {
        synchronized (sStrategies) {
            PathStrategy strategy = sStrategies.get(authority);
            if (strategy == null) {
                strategy = parsePathStrategy(context, authority);
                sStrategies.put(authority, strategy);
            }
            return strategy;
        }
    }

    private PathStrategy getStrategy() {
        if (mStrategy != null) {
            return mStrategy;
        }
        Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("FileProvider is not attached");
        }
        String authority = mAuthority != null
                ? mAuthority
                : context.getPackageName() + ".fileprovider";
        mStrategy = getPathStrategy(context, authority);
        return mStrategy;
    }

    private static PathStrategy parsePathStrategy(Context context, String authority) {
        SimplePathStrategy strategy = new SimplePathStrategy(authority);
        int resourceId = resolvePathsResourceId(context, authority);
        if (resourceId == 0) {
            Log.w(TAG, "no " + META_DATA_FILE_PROVIDER_PATHS
                    + " meta-data; using cache/leak fallback");
            return fallbackStrategy(context, authority);
        }

        try {
            XmlResourceParser parser = context.getResources().getXml(resourceId);
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG) {
                    continue;
                }
                String tag = parser.getName();
                if (tag == null || "paths".equals(tag)) {
                    continue;
                }
                String name = parser.getAttributeValue(null, ATTR_NAME);
                String path = parser.getAttributeValue(null, ATTR_PATH);
                if (TextUtils.isEmpty(name)) {
                    // Canonical androidx behavior; also keeps malformed xml safe.
                    Log.w(TAG, "ignoring <" + tag + "> without a name attribute");
                    continue;
                }
                File target;
                if (TAG_ROOT_PATH.equals(tag)) {
                    target = new File("/");
                } else if (TAG_FILES_PATH.equals(tag)) {
                    target = context.getFilesDir();
                } else if (TAG_CACHE_PATH.equals(tag)) {
                    target = context.getCacheDir();
                } else if (TAG_EXTERNAL.equals(tag)) {
                    target = Environment.getExternalStorageDirectory();
                } else if (TAG_EXTERNAL_FILES.equals(tag)) {
                    target = ContextCompatShim.getExternalFilesDir(context, null);
                } else if (TAG_EXTERNAL_CACHE.equals(tag)) {
                    target = ContextCompatShim.getExternalCacheDir(context);
                } else {
                    Log.w(TAG, "ignoring unsupported path tag <" + tag + ">");
                    continue;
                }
                if (target == null) {
                    Log.w(TAG, "skipping <" + tag + ">: root directory unavailable");
                    continue;
                }
                if (!TextUtils.isEmpty(path)) {
                    target = new File(target, path);
                }
                strategy.addRoot(name, target);
            }
        } catch (XmlPullParserException | IOException | RuntimeException e) {
            throw new IllegalArgumentException(
                    "Failed to parse " + META_DATA_FILE_PROVIDER_PATHS + " meta-data", e);
        }

        if (strategy.isEmpty()) {
            Log.w(TAG, "no usable root in " + META_DATA_FILE_PROVIDER_PATHS
                    + "; using cache/leak fallback");
            return fallbackStrategy(context, authority);
        }
        return strategy;
    }

    /**
     * Locates the paths XML: first through the provider's
     * {@code android.support.FILE_PROVIDER_PATHS} meta-data (canonical
     * androidx behavior), then by resource name as a safety net.
     */
    private static int resolvePathsResourceId(Context context, String authority) {
        try {
            ProviderInfo info = context.getPackageManager()
                    .resolveContentProvider(authority, PackageManager.GET_META_DATA);
            if (info != null && info.metaData != null) {
                int resourceId = info.metaData.getInt(META_DATA_FILE_PROVIDER_PATHS, 0);
                if (resourceId != 0) {
                    return resourceId;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "resolveContentProvider(" + authority + ") failed", t);
        }
        return context.getResources().getIdentifier(FALLBACK_XML_NAME, "xml",
                context.getPackageName());
    }

    private static PathStrategy fallbackStrategy(Context context, String authority) {
        SimplePathStrategy strategy = new SimplePathStrategy(authority);
        File cacheRoot = new File(context.getCacheDir(), FALLBACK_ROOT_PATH);
        strategy.addRoot(FALLBACK_ROOT_NAME, cacheRoot);
        return strategy;
    }

    // ------------------------------------------------- content-provider API

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        File file = getStrategy().getFileForUri(uri);
        String[] columns = projection != null ? projection : COLUMNS;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        String name = file.getName();
        long size = file.length();
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            String column = columns[i];
            if ("_display_name".equals(column)) {
                row[i] = name;
            } else if ("_size".equals(column)) {
                row[i] = size;
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        File file = getStrategy().getFileForUri(uri);
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String extension = name.substring(dot + 1);
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mimeType != null) {
                return mimeType;
            }
        }
        return DEFAULT_MIME_TYPE;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("No external inserts");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        File file = getStrategy().getFileForUri(uri);
        return file.delete() ? 1 : 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("No external updates");
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = getStrategy().getFileForUri(uri);
        // parseMode validates the mode string exactly like androidx modeToMode().
        int fileMode = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(file, fileMode);
    }

    /** Small indirection so this file only relies on public Context APIs. */
    private static final class ContextCompatShim {
        private ContextCompatShim() {
        }

        static File getExternalFilesDir(Context context, String type) {
            try {
                return context.getExternalFilesDir(type);
            } catch (Throwable t) {
                return null;
            }
        }

        static File getExternalCacheDir(Context context) {
            try {
                return context.getExternalCacheDir();
            } catch (Throwable t) {
                return null;
            }
        }
    }

    // --------------------------------------------------------- path strategy

    private interface PathStrategy {
        Uri getUriForFile(File file);

        File getFileForUri(Uri uri);
    }

    private static final class SimplePathStrategy implements PathStrategy {

        private final String mAuthority;
        private final HashMap<String, File> mRoots = new HashMap<>();

        SimplePathStrategy(String authority) {
            mAuthority = authority;
        }

        void addRoot(String name, File root) {
            if (name == null || root == null) {
                return;
            }
            if (mRoots.containsKey(name)) {
                throw new IllegalArgumentException("Root " + name + " already defined");
            }
            mRoots.put(name, root);
        }

        boolean isEmpty() {
            return mRoots.isEmpty();
        }

        @Override
        public Uri getUriForFile(File file) {
            String path;
            try {
                path = file.getCanonicalPath();
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to resolve canonical path for " + file,
                        e);
            }
            Map.Entry<String, File> bestMatch = null;
            String bestRootPath = null;
            for (Map.Entry<String, File> entry : mRoots.entrySet()) {
                String rootPath;
                try {
                    rootPath = entry.getValue().getCanonicalPath();
                } catch (IOException e) {
                    continue;
                }
                if (path.startsWith(rootPath)
                        && (bestRootPath == null || rootPath.length() > bestRootPath.length())) {
                    bestMatch = entry;
                    bestRootPath = rootPath;
                }
            }
            if (bestMatch == null || bestRootPath == null) {
                throw new IllegalArgumentException(
                        "Failed to find configured root that contains " + path);
            }
            String pathAfterRoot = path.substring(bestRootPath.length());
            if (pathAfterRoot.startsWith("/")) {
                pathAfterRoot = pathAfterRoot.substring(1);
            }
            return new Uri.Builder()
                    .scheme("content")
                    .authority(mAuthority)
                    .encodedPath(Uri.encode(bestMatch.getKey()) + '/'
                            + Uri.encode(pathAfterRoot, "/"))
                    .build();
        }

        @Override
        public File getFileForUri(Uri uri) {
            List<String> segments = uri.getPathSegments();
            if (segments == null || segments.isEmpty()) {
                throw new IllegalArgumentException("No path segments in uri: " + uri);
            }
            String name = segments.get(0);
            File root = mRoots.get(name);
            if (root == null) {
                throw new IllegalArgumentException("Unable to find configured root for " + uri);
            }
            StringBuilder pathBuilder = new StringBuilder();
            for (int i = 1; i < segments.size(); i++) {
                if (pathBuilder.length() > 0) {
                    pathBuilder.append('/');
                }
                pathBuilder.append(segments.get(i));
            }
            File file = new File(root, pathBuilder.toString());
            try {
                String rootPath = root.getCanonicalPath();
                file = file.getCanonicalFile();
                if (!file.getPath().startsWith(rootPath)) {
                    throw new SecurityException(
                            "Resolved path jumped beyond configured root: " + uri);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to resolve canonical path for " + file,
                        e);
            }
            return file;
        }
    }
}
