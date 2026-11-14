package com.android.systemui.media;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

/**
 * Screen-capture consent activity (the component the framework's
 * MediaProjectionManager.createScreenCaptureIntent() resolves to inside the
 * SystemUI package).
 *
 * Vendor 1.8.00 behaviour (com.android.systemui.media.MediaProjectionPermissionActivity):
 * it does NOT show a dialog - it immediately grants the projection through the
 * hidden IMediaProjectionManager API and returns the grant to the caller:
 *
 * <pre>
 *   setResult(RESULT_OK,
 *       MediaProjectionManagerEx.getInstance(this)
 *           .getMediaProjectionIntent(uid, callingPackage));
 *   finish();
 * </pre>
 *
 * This rebuild does the same: the grant intent carries the IMediaProjection
 * binder under "android.media.projection.extra.EXTRA_MEDIA_PROJECTION", which
 * is exactly what MediaProjectionManager.getMediaProjection() consumes.
 * (No reporting, no extra signature checks beyond the platform permission.)
 */
public class MediaProjectionPermissionActivity extends Activity {

    private static final String TAG = "MediaProjectionPermission";

    /** Extra used by the in-package RecordingService request path. */
    public static final String EXTRA_FROM_SYSTEMUI = "extra_from_systemui";
    /** Compatibility alias kept from earlier reconstruct stages. */
    public static final String EXTRA_REQUEST_INTERNAL = "extra_request_internal";
    /** Reuse this (already registered) transparent activity as the image picker. */
    public static final String EXTRA_PICK_PANEL_BG = "pick_panel_bg";
    private static final int REQ_PICK_BG = 1001;

    private static final String EXTRA_MEDIA_PROJECTION =
            "android.media.projection.extra.EXTRA_MEDIA_PROJECTION";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_PICK_PANEL_BG, false)) {
            pickPanelBackground();
            return;
        }
        int resultCode = RESULT_CANCELED;
        Intent result = null;
        try {
            String callingPackage = getCallingPackage();
            int uid;
            String pkg;
            if (callingPackage != null) {
                uid = getPackageManager().getApplicationInfo(callingPackage, 0).uid;
                pkg = callingPackage;
            } else {
                uid = Process.myUid();
                pkg = getPackageName();
            }
            result = createGrantIntent(uid, pkg);
            if (result != null) {
                resultCode = RESULT_OK;
            }
        } catch (Throwable t) {
            Log.w(TAG, "unable to grant screen capture: " + t);
        }
        setResult(resultCode, result);
        finish();
    }

    /** Uses the system picker when available, otherwise a built-in image list. */
    private void pickPanelBackground() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (getPackageManager().queryIntentActivities(intent, 0).size() > 0) {
                startActivityForResult(Intent.createChooser(intent, "选择背景图片"), REQ_PICK_BG);
                return;
            }
        } catch (Throwable t) {
            Log.w(TAG, "system picker unavailable: " + t);
        }
        showBuiltInPicker();
    }

    /** This ROM has no DocumentsUI, so list the pictures on the device ourselves. */
    private void showBuiltInPicker() {
        final java.util.List<java.io.File> images = collectImages();
        if (images.isEmpty()) {
            Log.w(TAG, "no pictures found on the device");
            android.widget.Toast.makeText(this, "没有找到图片", android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        android.widget.ListView list = new android.widget.ListView(this);
        list.setBackgroundColor(0xFF111111);
        android.widget.ArrayAdapter<java.io.File> adapter =
                new android.widget.ArrayAdapter<java.io.File>(this,
                        android.R.layout.simple_list_item_1, images) {
                    @Override
                    public android.view.View getView(int position, android.view.View convertView,
                            android.view.ViewGroup parent) {
                        android.view.View view = super.getView(position, convertView, parent);
                        android.widget.TextView text = (android.widget.TextView) view
                                .findViewById(android.R.id.text1);
                        if (text != null) {
                            java.io.File file = images.get(position);
                            text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
                            text.setTextColor(0xFFFFFFFF);
                            text.setText(file.getName() + "   "
                                    + (file.length() / 1024) + " KB");
                        }
                        return view;
                    }
                };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent,
                    android.view.View view, int position, long id) {
                applyBackgroundFromFile(images.get(position));
            }
        });
        setContentView(list);
    }

    private java.util.List<java.io.File> collectImages() {
        java.util.List<java.io.File> result = new java.util.ArrayList<java.io.File>();
        String[] dirs = {"/sdcard/DCIM", "/sdcard/Pictures", "/sdcard/Download",
                "/sdcard/Screenshots", "/sdcard/Photo", "/sdcard/image", "/sdcard"};
        for (String dir : dirs) {
            java.io.File folder = new java.io.File(dir);
            java.io.File[] files = folder.listFiles();
            if (files == null) {
                continue;
            }
            for (java.io.File file : files) {
                if (!file.isFile() || result.contains(file)) {
                    continue;
                }
                String name = file.getName().toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".jpeg")
                        || name.endsWith(".png") || name.endsWith(".webp")
                        || name.endsWith(".bmp")) {
                    result.add(file);
                    if (result.size() >= 120) {
                        break;
                    }
                }
            }
            if (result.size() >= 120) {
                break;
            }
        }
        java.util.Collections.sort(result, new java.util.Comparator<java.io.File>() {
            @Override
            public int compare(java.io.File a, java.io.File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return result;
    }

    private void applyBackgroundFromFile(java.io.File file) {
        try {
            android.graphics.BitmapFactory.Options bounds =
                    new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth / sample > 1080 || bounds.outHeight / sample > 1920) {
                sample *= 2;
            }
            android.graphics.BitmapFactory.Options opts =
                    new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = sample;
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory
                    .decodeFile(file.getAbsolutePath(), opts);
            if (bitmap == null) {
                finish();
                return;
            }
            java.io.FileOutputStream fos = new java.io.FileOutputStream(
                    new java.io.File(getFilesDir(), "panel_bg.png"));
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            com.android.systemui.statusbar.phone.XTCPanelView panel =
                    com.android.systemui.statusbar.phone.XTCPanelView.getActive();
            if (panel != null) {
                panel.setCustomBackground(bitmap);
            }
            Log.i(TAG, "custom background applied: " + file.getName());
        } catch (Throwable t) {
            Log.w(TAG, "apply background failed: " + t);
        }
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_BG) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            finish();
            return;
        }
        try {
            android.net.Uri uri = data.getData();
            java.io.InputStream is = getContentResolver().openInputStream(uri);
            android.graphics.BitmapFactory.Options bounds =
                    new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeStream(is, null, bounds);
            if (is != null) {
                is.close();
            }
            int sample = 1;
            while (bounds.outWidth / sample > 1080 || bounds.outHeight / sample > 1920) {
                sample *= 2;
            }
            is = getContentResolver().openInputStream(uri);
            android.graphics.BitmapFactory.Options opts =
                    new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = sample;
            android.graphics.Bitmap bitmap =
                    android.graphics.BitmapFactory.decodeStream(is, null, opts);
            if (is != null) {
                is.close();
            }
            if (bitmap != null) {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(
                        new java.io.File(getFilesDir(), "panel_bg.png"));
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
                com.android.systemui.statusbar.phone.XTCPanelView panel =
                        com.android.systemui.statusbar.phone.XTCPanelView.getActive();
                if (panel != null) {
                    panel.setCustomBackground(bitmap);
                    panel.expandActive();
                }
                Log.i(TAG, "custom background saved: "
                        + bitmap.getWidth() + "x" + bitmap.getHeight());
            }
        } catch (Throwable t) {
            Log.w(TAG, "picking background failed: " + t);
        }
        finish();
    }

    /**
     * Creates the projection grant through the hidden IMediaProjectionManager:
     * {@code createProjection(int uid, String packageName, int type, boolean isPermanentGrant)}
     * - the Android 8.x signature the vendor helper uses.
     */
    private Intent createGrantIntent(int uid, String packageName) {
        try {
            Object binder = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class)
                    .invoke(null, "media_projection");
            if (!(binder instanceof IBinder)) {
                return null;
            }
            Class<?> managerClass = Class.forName(
                    "android.media.projection.IMediaProjectionManager");
            Object manager = Class.forName("android.media.projection.IMediaProjectionManager$Stub")
                    .getMethod("asInterface", IBinder.class)
                    .invoke(null, binder);
            if (manager == null) {
                return null;
            }
            Object projection = managerClass
                    .getMethod("createProjection", int.class, String.class, int.class, boolean.class)
                    .invoke(manager, uid, packageName, 0, false);
            if (projection == null) {
                return null;
            }
            IBinder projectionBinder = (IBinder) projection.getClass()
                    .getMethod("asBinder")
                    .invoke(projection);
            Intent intent = new Intent();
            Bundle bundle = new Bundle();
            bundle.putBinder(EXTRA_MEDIA_PROJECTION, projectionBinder);
            intent.putExtras(bundle);
            return intent;
        } catch (Throwable t) {
            Log.w(TAG, "hidden createProjection failed: " + t);
            return null;
        }
    }
}
