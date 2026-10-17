package dev.navifold.player;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Versioned queue metadata only. Stream/artwork URLs and credentials are rebuilt after login. */
final class QueueSnapshot {
    static final int LIMIT = 5000;
    static final class Entry {
        final JSONObject song, anchor;
        final boolean suggested;
        final String reason;
        Entry(JSONObject song, boolean suggested, String reason, JSONObject anchor) {
            this.song = SongMetadata.safe(song);
            this.song.remove("path"); this.song.remove("comment");
            this.suggested = suggested; this.reason = reason == null ? "" : reason;
            this.anchor = SongMetadata.safe(anchor);
            this.anchor.remove("path"); this.anchor.remove("comment");
        }
    }
    final List<Entry> entries;
    final int index, repeat;
    final long position;
    final boolean shuffle;
    QueueSnapshot(List<Entry> entries, int index, long position, int repeat, boolean shuffle) {
        this.entries = entries;
        this.index = entries.isEmpty() ? 0 : Math.max(0, Math.min(entries.size() - 1, index));
        long duration = entries.isEmpty() ? 0 : entries.get(this.index).song.optLong("duration") * 1000L;
        this.position = Math.max(0, duration > 0 ? Math.min(position, duration - 1) : position);
        this.repeat = Math.max(0, Math.min(2, repeat)); this.shuffle = shuffle;
    }
    String encode() throws Exception {
        if (entries.size() > LIMIT) throw new IllegalArgumentException("Queue too large");
        JSONArray tracks = new JSONArray();
        for (Entry e : entries) {
            if (e.song.optString("id").isEmpty()) throw new IllegalArgumentException("Missing ID");
            tracks.put(new JSONObject().put("song", e.song).put("suggested", e.suggested).put("reason", e.reason).put("anchor", e.anchor));
        }
        return new JSONObject().put("version", 1).put("tracks", tracks).put("index", index).put("position", position).put("repeat", repeat).put("shuffle", shuffle).toString();
    }
    static QueueSnapshot decode(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (root.optInt("version") != 1) throw new IllegalArgumentException("Unknown version");
        JSONArray tracks = root.getJSONArray("tracks");
        if (tracks.length() > LIMIT) throw new IllegalArgumentException("Queue too large");
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < tracks.length(); i++) {
            JSONObject row = tracks.getJSONObject(i), song = row.getJSONObject("song");
            if (song.optString("id").isEmpty()) throw new IllegalArgumentException("Missing ID");
            entries.add(new Entry(song, row.optBoolean("suggested"), row.optString("reason"), row.optJSONObject("anchor")));
        }
        return new QueueSnapshot(entries, root.optInt("index"), root.optLong("position"), root.optInt("repeat"), root.optBoolean("shuffle"));
    }
}
