package com.android.systemui.qs;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;

/**
 * Bridges the playing media session to the pull-down's now-playing card.
 *
 * v76 judging logic - ONE decision point ({@link #applyCard()}): the card is
 * visible ONLY while a live session reports real metadata and a resumable
 * state. Every input (session poll, media notification, session callbacks)
 * funnels into it, so the card can never resurrect itself from a stale cache:
 *
 *   no session / getActiveSessions denied -> hide
 *   dead session (state==null && metadata==null) x2 -> hide
 *   state NONE / ERROR / STOPPED          -> hide (nothing is playing)
 *   metadata null while bound             -> hide (never reuse last title)
 *
 * PAUSED stays visible (track still loaded, play button can resume it).
 */
public class NowPlayingController implements MusicCardView.Callback {

    private static final String TAG = "NowPlaying";
    private static final String EXTRA_MEDIA_SESSION = "android.mediaSession";
    private static final long POLL_INTERVAL_MS = 5000L;

    // ---- vendor "custom music notification" protocol (from the original APK's
    // MusicNotificationConstants). Players such as com.tme.karaokewatch publish
    // NO MediaSession; they only post a notification carrying these extras, so
    // the session path alone can never see them.
    private static final String EXTRA_SONG_NAME = "SONG_NAME";
    private static final String EXTRA_SINGER_NAME = "SINGER_NAME";
    private static final String EXTRA_PLAY_STATE = "PLAY_STATE";
    private static final String EXTRA_PREV_INTENT = "PREVIOUS_INTENT";
    private static final String EXTRA_NEXT_INTENT = "NEXT_INTENT";
    private static final String EXTRA_PLAY_PAUSE_INTENT = "PLAY_PAUSE_INTENT";
    /** PLAY_STATE value that means "playing" (32 = paused). */
    private static final int PLAY_STATE_PLAYING = 16;

    private final Context mContext;
    private final MusicCardView mCard;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private MediaSessionManager mSessionManager;
    private ComponentName mListenerComponent;
    private MediaSessionManager.OnActiveSessionsChangedListener mSessionsListener;
    private MediaController mController;
    private MediaController.Callback mCallback;
    private String mNotificationKey;
    private boolean mPolling;
    private boolean mSessionAccessDenied;
    private int mDeniedPolls;
    private String mLastTitle = "";
    private String mLastArtist = "";
    private Bitmap mLastArt;
    private java.util.List<LrcUtil.Line> mLyrics = java.util.Collections.emptyList();
    private boolean mLyricLineTaskRunning;
    private int mDeadSessionMisses;
    private int mArtSeq;
    private String mCurrentArtUri;
    private String mFailedArtUri;
    private long mNextArtTry;
    /** One-entry cover cache: the poll re-reports art==null every 5s, which
     * used to wipe the already-decoded art back to the grey placeholder. */
    private String mCachedArtUri;
    private Bitmap mCachedArtBmp;
    /** True while the card is driven by the vendor notification protocol. */
    private boolean mNotifMode;
    private boolean mNotifPlaying;
    private android.app.PendingIntent mNotifPrev;
    private android.app.PendingIntent mNotifNext;
    private android.app.PendingIntent mNotifPlayPause;
    /** Package currently shown by the audio-only fallback (no session/no notif). */
    private String mAudioOnlyPkg;

    public NowPlayingController(Context context, MusicCardView card) {
        mContext = context.getApplicationContext();
        mCard = card;
        mCard.setCallback(this);
        // default: no music -> no card (the card used to stay VISIBLE forever)
        mCard.setMedia("", "", null, false);
    }

    public void attach() {
        NowPlayingBridge.setListener(new NowPlayingBridge.Listener() {
            @Override
            public void onMediaNotification(Object notification, String key) {
                if (notification instanceof android.app.Notification) {
                    handleNotification((android.app.Notification) notification, key);
                }
            }

            @Override
            public void onMediaNotificationRemoved(String key) {
                if (key != null && key.equals(mNotificationKey)) {
                    if (mNotifMode) {
                        releaseNotificationMode();
                    } else {
                        releaseController();
                    }
                }
            }
        });
        startPolling();
    }

    public void detach() {
        pause();
        stopPolling();
        NowPlayingBridge.setListener(null);
        releaseController();
    }

    /** Panel opened: start event-driven discovery (zero cost while closed). */
    public void resume() {
        startPolling();
    }

    /** Panel closed: stop everything - the card is not visible anyway. */
    public void pause() {
        stopPolling();
    }

    /** Re-applies the last media notification (panel re-created etc.). */
    public void replayLast() {
        NowPlayingBridge.replay();
    }

    // ------------------------------------------------------------- polling

    private void startPolling() {
        if (mPolling) {
            return;
        }
        mPolling = true;
        try {
            if (mSessionManager == null) {
                mSessionManager = (MediaSessionManager) mContext
                        .getSystemService(Context.MEDIA_SESSION_SERVICE);
                mListenerComponent = new ComponentName(mContext,
                        com.xtc.statusbar.XtcNotificationListenerService.class);
            }
            if (mSessionsListener == null) {
                mSessionsListener = new MediaSessionManager.OnActiveSessionsChangedListener() {
                    @Override
                    public void onActiveSessionsChanged(
                            java.util.List<MediaController> controllers) {
                        // the player set changed - re-grab immediately
                        pollActiveSession();
                    }
                };
                mSessionManager.addOnActiveSessionsChangedListener(mSessionsListener,
                        mListenerComponent, mHandler);
            }
        } catch (Throwable t) {
            Log.w(TAG, "register sessions listener failed: " + t);
        }
        // one immediate grab, then a slow fallback tick (events drive the rest)
        mHandler.post(mPollTask);
        if (!mLyricLineTaskRunning) {
            mLyricLineTaskRunning = true;
            mHandler.post(mLyricLineTask);
        }
    }

    private void stopPolling() {
        mPolling = false;
        mHandler.removeCallbacks(mPollTask);
        if (mSessionManager != null && mSessionsListener != null) {
            try {
                mSessionManager.removeOnActiveSessionsChangedListener(mSessionsListener);
            } catch (Throwable ignored) {
            }
            mSessionsListener = null;
        }
    }

    private final Runnable mPollTask = new Runnable() {
        @Override
        public void run() {
            if (!mPolling) {
                return;
            }
            pollActiveSession();
            mHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private final Runnable mLyricLineTask = new Runnable() {
        @Override
        public void run() {
            // desktop-lyrics mode: keep following the song even with the
            // panel closed, as long as the caption is on screen
            if (!LyricsFloat.isShowing()) {
                mLyricLineTaskRunning = false;
                return;
            }
            updateLyricLine();
            mHandler.postDelayed(this, 500L);
        }
    };

    /** Grabs the session of whatever is playing (global, any player). */
    private void pollActiveSession() {
        if (mController != null) {
            // watchdog: a dead session reports neither state nor metadata
            PlaybackState st = mController.getPlaybackState();
            MediaMetadata md = mController.getMetadata();
            if (st == null && md == null) {
                mDeadSessionMisses++;
                if (mDeadSessionMisses >= 2) {
                    Log.i(TAG, "watchdog: session dead, hide card");
                    releaseController();
                }
            } else {
                mDeadSessionMisses = 0;
            }
        }
        try {
            if (mSessionManager == null) {
                mSessionManager = (MediaSessionManager) mContext
                        .getSystemService(Context.MEDIA_SESSION_SERVICE);
                mListenerComponent = new ComponentName(mContext,
                        com.xtc.statusbar.XtcNotificationListenerService.class);
            }
            java.util.List<MediaController> controllers =
                    mSessionManager.getActiveSessions(mListenerComponent);
            MediaController best = null;
            for (MediaController controller : controllers) {
                PlaybackState state = controller.getPlaybackState();
                if (state == null) {
                    continue;
                }
                int s = state.getState();
                // stopped/error/none sessions are NOT music - never show them
                if (s == PlaybackState.STATE_NONE
                        || s == PlaybackState.STATE_ERROR
                        || s == PlaybackState.STATE_STOPPED) {
                    continue;
                }
                if (s == PlaybackState.STATE_PLAYING) {
                    best = controller;
                    break;
                }
                if (best == null) {
                    best = controller; // paused but resumable
                }
            }
            if (best != null) {
                if (mController == null
                        || !best.getSessionToken().equals(mController.getSessionToken())) {
                    bindController(best);
                } else {
                    applyCard();
                }
            } else if (mController != null) {
                releaseController();
            } else if (!mNotifMode) {
                // last resort: no MediaSession and no notification access on this
                // ROM (it clears enabled_notification_listeners at boot), but the
                // audio stack still knows which app is playing -> show the app.
                if (!applyAudioOnlyFallback()) {
                    mAudioOnlyPkg = null;
                    mCard.setMedia("", "", null, false);
                }
            }
        } catch (SecurityException denied) {
            // notification-listener access not granted; nothing reachable to
            // show, so the card must hide instead of keeping a placeholder
            mSessionAccessDenied = true;
            if (mDeniedPolls == 0) {
                Log.w(TAG, "getActiveSessions denied, card hidden until access granted");
            }
            mDeniedPolls++;
            if (mController == null) {
                mCard.setMedia("", "", null, false);
            }
        } catch (Throwable t) {
            Log.w(TAG, "poll sessions failed: " + t);
        }
    }

    // -------------------------------------------------------- notification path

    @SuppressWarnings("deprecation")
    private void handleNotification(android.app.Notification notification, String key) {
        if (notification == null || notification.extras == null) {
            return;
        }
        Parcelable token = notification.extras.getParcelable(EXTRA_MEDIA_SESSION);
        if (token instanceof MediaSession.Token) {
            mNotifMode = false;
            mNotificationKey = key;
            MediaController controller = new MediaController(mContext, (MediaSession.Token) token);
            if (mController != null && token.equals(mController.getSessionToken())) {
                applyCard();
                return;
            }
            bindController(controller);
            return;
        }
        // No session token: fall back to the vendor notification protocol so
        // players that only post a notification (karaoke watch, etc.) still show.
        if (notification.extras.containsKey(EXTRA_SONG_NAME)) {
            bindNotification(notification, key);
        }
    }

    /**
     * Card driven purely by a music notification: title/artist/cover/state come
     * from the extras, the transport buttons fire the notification's
     * PendingIntents (there is no MediaController to talk to).
     */
    private void bindNotification(android.app.Notification notification, String key) {
        mNotificationKey = key;
        mController = null;
        mCallback = null;
        mNotifMode = true;
        android.os.Bundle ex = notification.extras;
        String title = ex.getString(EXTRA_SONG_NAME, "");
        String artist = ex.getString(EXTRA_SINGER_NAME, "");
        mNotifPlaying = ex.getInt(EXTRA_PLAY_STATE, 0) == PLAY_STATE_PLAYING;
        mNotifPrev = ex.getParcelable(EXTRA_PREV_INTENT);
        mNotifNext = ex.getParcelable(EXTRA_NEXT_INTENT);
        mNotifPlayPause = ex.getParcelable(EXTRA_PLAY_PAUSE_INTENT);

        Bitmap art = null;
        try {
            Parcelable iconObj = ex.getParcelable("android.largeIcon");
            if (iconObj instanceof android.graphics.drawable.Icon) {
                // Icon.getBitmap() is @hide on the public SDK: render the
                // drawable into a bitmap instead
                android.graphics.drawable.Drawable d =
                        ((android.graphics.drawable.Icon) iconObj).loadDrawable(mContext);
                if (d != null) {
                    int w = Math.max(1, d.getIntrinsicWidth());
                    int h = Math.max(1, d.getIntrinsicHeight());
                    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas c = new android.graphics.Canvas(bmp);
                    d.setBounds(0, 0, w, h);
                    d.draw(c);
                    art = bmp;
                }
            }
        } catch (Throwable ignored) {
        }
        mLastTitle = title == null ? "" : title;
        mLastArtist = artist == null ? "" : artist;
        mLastArt = art;
        mCard.setMedia(mLastTitle, mLastArtist, art, mNotifPlaying);
        Log.i(TAG, "notification-mode music: " + mLastTitle + " / " + mLastArtist
                + " playing=" + mNotifPlaying);
    }

    /**
     * Audio-only fallback: an app that plays music without publishing a
     * MediaSession (and whose notification we may not read on this ROM) is still
     * visible through the audio stack. Show its icon + label so the card is not
     * blank; no transport controls are offered (there is nothing to control).
     *
     * @return true when a playing app was found and the card was updated
     */
    private boolean applyAudioOnlyFallback() {
        try {
            android.media.AudioManager am = (android.media.AudioManager)
                    mContext.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return false;
            }
            java.util.List<android.media.AudioPlaybackConfiguration> configs =
                    am.getActivePlaybackConfigurations();
            String pkg = null;
            // isActive()/getClientUid() are @hide on the public SDK: reflect them
            java.lang.reflect.Method mIsActive = null;
            java.lang.reflect.Method mGetUid = null;
            try {
                mIsActive = android.media.AudioPlaybackConfiguration.class
                        .getMethod("isActive");
                mGetUid = android.media.AudioPlaybackConfiguration.class
                        .getMethod("getClientUid");
            } catch (Throwable ignored) {
            }
            if (mIsActive == null || mGetUid == null) {
                return false;
            }
            for (android.media.AudioPlaybackConfiguration cfg : configs) {
                if (cfg == null) {
                    continue;
                }
                boolean active = false;
                try {
                    active = (Boolean) mIsActive.invoke(cfg);
                } catch (Throwable ignored) {
                }
                if (!active) {
                    continue;
                }
                android.media.AudioAttributes attrs = cfg.getAudioAttributes();
                if (attrs == null
                        || attrs.getUsage() != android.media.AudioAttributes.USAGE_MEDIA) {
                    continue;
                }
                int uid = -1;
                try {
                    uid = (Integer) mGetUid.invoke(cfg);
                } catch (Throwable ignored) {
                }
                if (uid < 0) {
                    continue;
                }
                String[] pkgs = mContext.getPackageManager().getPackagesForUid(uid);
                if (pkgs != null && pkgs.length > 0) {
                    pkg = pkgs[0];
                    break;
                }
            }
            if (pkg == null) {
                return false;
            }
            if (pkg.equals(mAudioOnlyPkg)) {
                return true; // already showing the same app
            }
            String label = pkg;
            Bitmap art = null;
            try {
                android.content.pm.ApplicationInfo ai = mContext.getPackageManager()
                        .getApplicationInfo(pkg, 0);
                CharSequence cs = mContext.getPackageManager().getApplicationLabel(ai);
                if (cs != null && cs.length() > 0) {
                    label = cs.toString();
                }
                android.graphics.drawable.Drawable icon =
                        mContext.getPackageManager().getApplicationIcon(ai);
                if (icon != null) {
                    int w = Math.max(1, icon.getIntrinsicWidth());
                    int h = Math.max(1, icon.getIntrinsicHeight());
                    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas c = new android.graphics.Canvas(bmp);
                    icon.setBounds(0, 0, w, h);
                    icon.draw(c);
                    art = bmp;
                }
            } catch (Throwable ignored) {
            }
            mAudioOnlyPkg = pkg;
            mLastTitle = label;
            mLastArtist = "正在播放";
            mLastArt = art;
            mCard.setMedia(label, "正在播放", art, true);
            Log.i(TAG, "audio-only fallback: " + pkg + " (" + label + ")");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "audio fallback failed: " + t);
            return false;
        }
    }

    /** Drops the notification-driven card. */
    private void releaseNotificationMode() {
        mNotifMode = false;
        mNotifPlaying = false;
        mNotifPrev = null;
        mNotifNext = null;
        mNotifPlayPause = null;
        mNotificationKey = null;
        mLastTitle = "";
        mLastArtist = "";
        mLastArt = null;
        mCard.setMedia("", "", null, false);
    }

    // -------------------------------------------------------------- binding

    private void bindController(MediaController controller) {
        releaseController();
        mController = controller;
        mCallback = new MediaController.Callback() {
            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                applyCard();
            }

            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                applyCard();
            }

            @Override
            public void onSessionDestroyed() {
                releaseController();
            }
        };
        try {
            mController.registerCallback(mCallback);
        } catch (Throwable t) {
            Log.w(TAG, "registerCallback failed: " + t);
        }
        Log.i(TAG, "bound to session: " + controller.getPackageName());
        applyCard();
    }

    private void refresh() {
        applyCard();
    }

    // --------------------------------------------- THE single judging point

    /**
     * Decides what the card shows right now, from live state only. Called by
     * every path; never trusts mLast* leftovers, so a hidden card cannot be
     * brought back by a stale title.
     */
    private void applyCard() {
        MediaController c = mController;
        if (c == null) {
            // notification-driven card owns its own state; a session poll must
            // not wipe it
            if (mNotifMode) {
                return;
            }
            mCard.setMedia("", "", null, false);
            return;
        }
        PlaybackState st = c.getPlaybackState();
        MediaMetadata md = c.getMetadata();
        int s = st == null ? PlaybackState.STATE_NONE : st.getState();
        if (st == null && md == null) {
            // dead session - drop the binding (watchdog handles stragglers)
            releaseController();
            return;
        }
        if (md == null) {
            // session alive but nothing published yet: hide, keep bound
            mCard.setMedia("", "", null, false);
            return;
        }
        if (s == PlaybackState.STATE_NONE
                || s == PlaybackState.STATE_ERROR
                || s == PlaybackState.STATE_STOPPED) {
            Log.i(TAG, "no active music (state=" + s + "), hide card");
            releaseController();
            return;
        }
        String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null || artist.isEmpty()) {
            artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }
        Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) {
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        String artUri = null;
        if (art == null) {
            artUri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
            // reuse the decoded cover so the grey placeholder never resurfaces
            if (artUri != null && artUri.equals(mCachedArtUri) && mCachedArtBmp != null) {
                art = mCachedArtBmp;
                artUri = null;
            }
        }
        mLastTitle = title == null ? "" : title;
        mLastArtist = artist == null ? "" : artist;
        mLastArt = art;
        mCard.setMedia(title, artist, art, s == PlaybackState.STATE_PLAYING);
        rebuildLyrics(md, mLastTitle, mLastArtist);
        if (LyricsFloat.isShowing()) {
            updateLyricLine();
        }
        // many players (NetEase etc.) only publish a cover URI, not a bitmap
        if (art == null && artUri != null && !artUri.isEmpty()) {
            loadArtAsync(artUri);
        }
    }

    /** Decodes the cover URI off the UI thread, then refreshes the card. */
    private void loadArtAsync(final String uri) {
        if (uri.equals(mCurrentArtUri)) {
            return;
        }
        // failed fetches retry at most every 30s (offline covers etc.)
        if (uri.equals(mFailedArtUri) && android.os.SystemClock.elapsedRealtime() < mNextArtTry) {
            return;
        }
        mCurrentArtUri = uri;
        final int seq = ++mArtSeq;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                try {
                    if (uri.startsWith("http://") || uri.startsWith("https://")) {
                        bmp = fetchHttpArt(uri);
                    } else {
                        android.content.res.AssetFileDescriptor afd =
                                mContext.getContentResolver().openAssetFileDescriptor(
                                        android.net.Uri.parse(uri), "r");
                        if (afd != null) {
                            bmp = decodeFd(afd.getFileDescriptor());
                            try {
                                afd.close();
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "cover load failed: " + t);
                }
                if (seq != mArtSeq) {
                    return;
                }
                if (bmp == null) {
                    mFailedArtUri = uri;
                    mNextArtTry = android.os.SystemClock.elapsedRealtime() + 30000L;
                    return;
                }
                final Bitmap artFinal = bmp;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (seq != mArtSeq) {
                            return;
                        }
                        mCachedArtUri = uri;
                        mCachedArtBmp = artFinal;
                        mLastArt = artFinal;
                        mCard.setMedia(mLastTitle, mLastArtist, artFinal, isPlaying());
                    }
                });
            }
        }, "nowplaying-cover").start();
    }

    /** Downsampling decode of an already-open file descriptor. */
    private Bitmap decodeFd(java.io.FileDescriptor fd) {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFileDescriptor(fd, null, o);
        o.inJustDecodeBounds = false;
        o.inSampleSize = 1;
        while ((o.outWidth * o.outHeight) >> (2 * o.inSampleSize) > 512 * 512) {
            o.inSampleSize *= 2;
        }
        return android.graphics.BitmapFactory.decodeFileDescriptor(fd, null, o);
    }

    /** Players publish http(s) cover links (NetEase etc.); fetch with limits. */
    private Bitmap fetchHttpArt(String uri) throws Exception {
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(uri).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(6000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0 && buf.size() < 8 * 1024 * 1024) {
                buf.write(chunk, 0, n);
            }
            in.close();
            byte[] data = buf.toByteArray();
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length, o);
            o.inJustDecodeBounds = false;
            o.inSampleSize = 1;
            while ((o.outWidth * o.outHeight) >> (2 * o.inSampleSize) > 512 * 512) {
                o.inSampleSize *= 2;
            }
            return android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Lyric source: in-metadata lyrics first, then a same-name .lrc file. */
    private void rebuildLyrics(MediaMetadata metadata, String title, String artist) {
        java.util.List<LrcUtil.Line> lines = java.util.Collections.emptyList();
        try {
            if (metadata != null) {
                String embedded = metadata.getString("android.metadata.lyrics");
                if (embedded != null && !embedded.isEmpty()
                        && embedded.contains("[")) {
                    lines = LrcUtil.parse(embedded);
                }
            }
            if (lines.isEmpty()) {
                String artistTitle = title;
                if (artist != null && !artist.isEmpty() && title != null
                        && !title.isEmpty()) {
                    artistTitle = artist + " - " + title;
                }
                lines = LrcUtil.findForTitle(title, artistTitle);
            }
        } catch (Throwable t) {
            Log.w(TAG, "lyrics rebuild failed: " + t);
        }
        mLyrics = lines;
        Log.i(TAG, "lyrics lines=" + lines.size() + " for " + title);
    }

    /** Pushes the current lyric line while the floating caption is visible. */
    private void updateLyricLine() {
        if (!LyricsFloat.isShowing()) {
            return;
        }
        long position = 0L;
        if (mController != null) {
            PlaybackState state = mController.getPlaybackState();
            if (state != null) {
                position = state.getPosition();
            }
        }
        String line = LrcUtil.currentLine(mLyrics, position);
        if (line.isEmpty()) {
            line = mLastTitle + (mLastArtist.isEmpty() ? "" : " - " + mLastArtist);
        }
        LyricsFloat.updateText(line);
    }

    private boolean isPlaying() {
        if (mNotifMode) {
            return mNotifPlaying;
        }
        if (mController == null) {
            return false;
        }
        PlaybackState state = mController.getPlaybackState();
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }

    /** Fires one of the notification's transport PendingIntents. */
    private void sendNotifIntent(android.app.PendingIntent pi) {
        if (pi == null) {
            return;
        }
        try {
            pi.send();
        } catch (Throwable t) {
            Log.w(TAG, "notif transport failed: " + t);
        }
    }

    private void releaseController() {
        if (mController != null) {
            try {
                if (mCallback != null) {
                    mController.unregisterCallback(mCallback);
                }
            } catch (Throwable ignored) {
            }
            mController = null;
            mCallback = null;
        }
        // stale cache must never resurrect the card
        mLastTitle = "";
        mLastArtist = "";
        mLastArt = null;
        mLyrics = java.util.Collections.emptyList();
        mCachedArtUri = null;
        mCachedArtBmp = null;
        mCurrentArtUri = null;
        mFailedArtUri = null;
        mCard.setMedia("", "", null, false);
    }

    // ------------------------------------------------------- card callbacks

    @Override
    public void onPrevious() {
        if (mNotifMode) {
            sendNotifIntent(mNotifPrev);
            return;
        }
        if (mController != null) {
            try {
                mController.getTransportControls().skipToPrevious();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onPlayPause() {
        if (mNotifMode) {
            sendNotifIntent(mNotifPlayPause);
            return;
        }
        if (mController != null) {
            try {
                if (isPlaying()) {
                    mController.getTransportControls().pause();
                } else {
                    mController.getTransportControls().play();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onNext() {
        if (mNotifMode) {
            sendNotifIntent(mNotifNext);
            return;
        }
        if (mController != null) {
            try {
                mController.getTransportControls().skipToNext();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onLyrics() {
        LyricsFloat.toggle(mContext);
        if (LyricsFloat.isShowing()) {
            if (!mLyricLineTaskRunning) {
                mLyricLineTaskRunning = true;
                mHandler.post(mLyricLineTask);
            }
            updateLyricLine();
        }
    }
}
