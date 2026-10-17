package dev.navifold.player;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Restores once after authenticated connection; never resumes audio automatically. */
final class QueuePersistence implements AutoCloseable {
    private static final Object storageLock = new Object();
    private static long storageGeneration;
    private final Context context;
    private final Player player;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private String scope = "";
    private long mutation, savedAt, restoreTicket;
    private boolean closed, dirty, restoring, applying;
    QueuePersistence(Context context, Player player) { this.context = context.getApplicationContext(); this.player = player; }
    private static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("queue-state", 0); }
    static void clear(Context c) {
        synchronized (storageLock) { storageGeneration++; prefs(c).edit().clear().commit(); }
    }
    void changed() { if (!applying) { mutation++; dirty = true; } }
    void tick() {
        if (closed) return;
        String connected = SmartSession.scope;
        if (!scope.equals(connected)) {
            String previous = scope; scope = connected; mutation++; dirty = false; restoring = false; restoreTicket++;
            if (!previous.isEmpty() && player.getMediaItemCount() > 0 && !queueMatchesScope()) { player.stop(); player.clearMediaItems(); }
            dirty = !scope.isEmpty() && player.getMediaItemCount() > 0;
            if (!scope.isEmpty() && SmartSession.api != null && player.getMediaItemCount() == 0) scheduleRestore();
        }
        if (!scope.isEmpty() && !restoring && (dirty || player.isPlaying() && SystemClock.elapsedRealtime() - savedAt >= 10000)) save();
    }
    private void scheduleRestore() {
        // Let events from clearing the previous account settle before taking the restore revision.
        final String expectedScope = scope;
        final long ticket = ++restoreTicket;
        restoring = true;
        main.post(() -> {
            if (closed || ticket != restoreTicket) return;
            restoring = false;
            if (expectedScope.equals(SmartSession.scope) && player.getMediaItemCount() == 0) restore();
        });
    }
    private void restore() {
        final String expectedScope = scope;
        final long ticket = ++restoreTicket;
        final Api api = SmartSession.api;
        final long revision = SmartSession.revision, expectedMutation = mutation;
        final long generation; synchronized (storageLock) { generation = storageGeneration; }
        restoring = true;
        AppSettings settings = new AppSettings(AppSettings.preferences(context));
        worker.execute(() -> {
            QueueSnapshot loaded = null;
            List<MediaItem> items = new ArrayList<>();
            try {
                String json = prefs(context).getString("state_" + expectedScope, "");
                if (!json.isEmpty()) {
                    loaded = QueueSnapshot.decode(json);
                    for (QueueSnapshot.Entry entry : loaded.entries) {
                        MediaItem item = PlaybackItems.make(api, entry.song, settings.original, settings.bitrate, entry.suggested);
                        Bundle extras = new Bundle(item.mediaMetadata.extras);
                        extras.putString(RecommendationReason.EXTRA, entry.reason);
                        extras.putString(SmartPlayback.FLOW_ANCHOR, entry.anchor.toString());
                        items.add(item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build());
                    }
                }
            } catch (Exception ignored) { items.clear(); loaded = null; }
            QueueSnapshot snapshot = loaded;
            main.post(() -> {
                if (closed || ticket != restoreTicket) return;
                restoring = false;
                synchronized (storageLock) { if (generation != storageGeneration) return; }
                if (!expectedScope.equals(SmartSession.scope) || !expectedScope.equals(scope) || revision != SmartSession.revision || mutation != expectedMutation || player.getMediaItemCount() != 0) return;
                if (snapshot == null || items.isEmpty()) return;
                applying = true;
                try {
                    player.setPlayWhenReady(false);
                    player.setMediaItems(items, snapshot.index, snapshot.position);
                    player.setRepeatMode(snapshot.repeat);
                    player.setShuffleModeEnabled(snapshot.shuffle);
                    player.prepare();
                    SmartStatus.record(context, "큐 복원 · 일시정지");
                } finally { applying = false; dirty = true; savedAt = SystemClock.elapsedRealtime(); }
            });
        });
    }
    private boolean queueMatchesScope() {
        for (int i = 0; i < player.getMediaItemCount(); i++) {
            Bundle extras = player.getMediaItemAt(i).mediaMetadata.extras;
            if (extras == null || !scope.equals(extras.getString(PlaybackItems.SCOPE, ""))) return false;
        }
        return true;
    }
    private void save() {
        if (scope.isEmpty() || !scope.equals(SmartSession.scope) || SmartSession.api == null || !queueMatchesScope()) return;
        final String expectedScope = scope;
        final long generation; synchronized (storageLock) { generation = storageGeneration; }
        List<QueueSnapshot.Entry> entries = new ArrayList<>();
        for (int i = 0; i < player.getMediaItemCount(); i++) {
            MediaItem item = player.getMediaItemAt(i); Bundle extras = item.mediaMetadata.extras;
            JSONObject anchor = new JSONObject();
            try { if (extras != null) anchor = new JSONObject(extras.getString(SmartPlayback.FLOW_ANCHOR, "{}")); } catch (Exception ignored) { }
            entries.add(new QueueSnapshot.Entry(SmartPlayback.metadata(item), extras != null && extras.getBoolean(SmartPlayback.SUGGESTED), extras == null ? "" : extras.getString(RecommendationReason.EXTRA, ""), anchor));
        }
        QueueSnapshot snapshot = new QueueSnapshot(entries, player.getCurrentMediaItemIndex(), player.getCurrentPosition(), player.getRepeatMode(), player.getShuffleModeEnabled());
        dirty = false; savedAt = SystemClock.elapsedRealtime();
        worker.execute(() -> {
            try {
                String encoded = snapshot.encode();
                synchronized (storageLock) {
                    if (generation != storageGeneration || !expectedScope.equals(SmartSession.scope)) return;
                    prefs(context).edit().putString("state_" + expectedScope, encoded).commit();
                }
            } catch (Exception ignored) {
                // Never resurrect an older queue when the newest snapshot cannot be encoded.
                synchronized (storageLock) {
                    if (generation == storageGeneration && expectedScope.equals(SmartSession.scope)) prefs(context).edit().remove("state_" + expectedScope).commit();
                }
            }
        });
    }
    public void close() { if (closed) return; save(); closed = true; main.removeCallbacksAndMessages(null); worker.shutdown(); }
}
