package dev.navifold.player;

/** Pure decisions shared by the recommender, service and regression tests. */
final class SmartPolicy {
    static boolean strongNegative(int skips, int completes, int vote) {
        return vote < 0 || (vote == 0 && skips >= 3 && completes == 0);
    }
    static String outcome(long audibleMs, long durationMs, boolean forwardSkip, boolean automatic, boolean error) {
        if (error) return "error";
        if (automatic) return "auto-skip";
        if (durationMs > 0 && audibleMs >= Math.max(3000, Math.min(durationMs * 7 / 10, 180000))) return "complete";
        if (forwardSkip && audibleMs >= 3000 && audibleMs < Math.min(30000, durationMs > 0 ? durationMs * 3 / 10 : 30000)) return "manual-skip";
        return "neutral";
    }
    static boolean canSkip(boolean enabled, boolean suggested, boolean negative, boolean hasNext, int consecutive) {
        return enabled && suggested && negative && hasNext && consecutive < 2;
    }
    static boolean canRefill(boolean enabled, boolean playing, int repeat, boolean shuffle, int remaining) {
        return enabled && playing && repeat == 0 && !shuffle && remaining <= 3;
    }
}
