package dev.navifold.player;
import androidx.media3.common.*;
import org.json.*;
final class PlaybackInfo {
 static long duration(MediaMetadata metadata){if(metadata.durationMs!=null&&metadata.durationMs>0)return metadata.durationMs;try{return new JSONObject(metadata.extras==null?"{}":metadata.extras.getString(SongMetadata.EXTRA,"{}")).optLong("duration")*1000L;}catch(JSONException ignored){return 0;}}
 static long duration(Player player){long time=player.getDuration();return time>0?time:duration(player.getMediaMetadata());}
}
