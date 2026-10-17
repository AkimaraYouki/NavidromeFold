package dev.navifold.player;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Describes measured candidate features, never invents an LLM explanation. */
final class RecommendationReason {
    static final String EXTRA = "navifold.smart.reason";
    static String explain(JSONObject seed, JSONObject song) {
        List<String> facts = new ArrayList<>();
        if (same(seed.optString("artist"), song.optString("artist")) ||
                same(seed.optString("albumArtist"), song.optString("albumArtist"))) facts.add("같은 아티스트");
        if (same(seed.optString("genre"), song.optString("genre"))) facts.add("같은 장르");
        if (same(SmartLibrary.label(seed), SmartLibrary.label(song))) facts.add("같은 레이블");
        int a = seed.optInt("year"), b = song.optInt("year");
        if (a > 0 && b > 0 && Math.abs(a - b) <= 5) facts.add("가까운 발매 시기");
        double x = seed.optDouble("bpm"), y = song.optDouble("bpm");
        if (x > 0 && y > 0 && Math.abs(x - y) <= 10) facts.add("가까운 BPM 태그");
        return facts.isEmpty() ? "선택한 흐름 조건을 통과한 후보" : String.join(" · ", facts.subList(0, Math.min(3, facts.size())));
    }
    private static boolean same(String a, String b) { return !a.trim().isEmpty() && a.trim().equalsIgnoreCase(b.trim()); }
}
