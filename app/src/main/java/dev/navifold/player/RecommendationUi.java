package dev.navifold.player;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit, per-track feedback works independently of passive listening-history learning. */
final class RecommendationUi {
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    static void show(Activity owner, Player player, int index) {
        if (index < 0 || index >= player.getMediaItemCount()) return;
        MediaItem item = player.getMediaItemAt(index);
        String scope = SmartSession.scope;
        long revision = SmartSession.revision;
        boolean suggested = item.mediaMetadata.extras != null && item.mediaMetadata.extras.getBoolean(SmartPlayback.SUGGESTED, false);
        String reason = suggested ? item.mediaMetadata.extras.getString(RecommendationReason.EXTRA, "선택한 흐름 조건을 통과한 후보") : "직접 추가한 곡";
        String title = String.valueOf(item.mediaMetadata.title == null ? "TRACK" : item.mediaMetadata.title);
        String[] actions = {"더 듣기", "덜 듣기", "선호 초기화", "이번 큐에서 제외", "최근 추천 기록"};
        builder(owner).setTitle(title + "\n" + (suggested ? "후보 선정 기준 · " : "") + reason)
            .setItems(actions, (dialog, which) -> {
                if (!scope.equals(SmartSession.scope) || revision != SmartSession.revision) return;
                if (which == 4) { history(owner); return; }
                if (which == 3) {
                    // Keep the exact occurrence, including duplicate IDs, and never train on this action.
                    if (index < player.getMediaItemCount() && player.getMediaItemAt(index).equals(item)) {
                        if (index == player.getCurrentMediaItemIndex()) SmartSession.ignoreRemoval(scope, item.mediaId);
                        player.removeMediaItem(index);
                    }
                    return;
                }
                if (scope.isEmpty()) return;
                int vote = which == 0 ? 1 : which == 1 ? -1 : 0;
                SmartLibrary library = SmartLibrary.get(owner);
                worker.execute(() -> {
                    try {
                        if (!scope.equals(SmartSession.scope) || revision != SmartSession.revision) return;
                        library.vote(scope, SmartPlayback.metadata(item), vote);
                        main.post(() -> {
                            if (owner.isFinishing() || owner.isDestroyed() || !scope.equals(SmartSession.scope) || revision != SmartSession.revision) return;
                            SmartSession.changed(); // Discard recommendations that started before this feedback.
                            SmartStatus.record(owner, vote > 0 ? "더 듣기 · 곡 선호 저장" : vote < 0 ? "덜 듣기 · 곡 선호 저장" : "곡 선호 초기화");
                            Toast.makeText(owner, "다음 추천부터 반영됩니다", Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        main.post(() -> { if (!owner.isFinishing() && !owner.isDestroyed()) Toast.makeText(owner, "선호 저장 실패", Toast.LENGTH_SHORT).show(); });
                    }
                });
            }).setNegativeButton("닫기", null).show();
    }
    static void current(Activity owner, Player player) {
        if (player.getCurrentMediaItem() == null) history(owner);
        else show(owner, player, player.getCurrentMediaItemIndex());
    }
    private static androidx.appcompat.app.AlertDialog.Builder builder(Activity owner) {
        if (owner instanceof MainActivity) return ((MainActivity) owner).dialog();
        AppSettings settings = AppSettings.cached(owner);
        boolean dark = settings.systemTheme ? (owner.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES : settings.dark;
        return new MaterialAlertDialogBuilder(new android.view.ContextThemeWrapper(owner, dark ? R.style.NaviDialogDark : R.style.NaviDialogLight));
    }
    static void history(Activity owner) {
        builder(owner).setTitle("AI · RECENT").setMessage(SmartStatus.history(owner)).setPositiveButton("닫기", null).show();
    }
}
