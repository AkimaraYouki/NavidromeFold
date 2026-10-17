package dev.navifold.player;
import android.os.Bundle;
import android.net.Uri;
import androidx.media3.common.*;
import org.json.JSONObject;
final class PlaybackItems {
    static final String SCOPE = "navifold.server.scope";
    static MediaItem make(Api api,JSONObject song,boolean raw,int bitrate,boolean suggested) throws Exception {
        raw=NetworkUsage.original(raw);bitrate=NetworkUsage.bitrate(bitrate);
        String uri=raw?api.url("stream","id",song.optString("id"),"format","raw"):api.url("stream","id",song.optString("id"),"format","mp3","maxBitRate",String.valueOf(bitrate));
        Bundle extras=new Bundle();extras.putString(SCOPE,SmartSession.namespace(api.base,api.user));extras.putBoolean("transcoded",!raw);extras.putBoolean(SmartPlayback.SUGGESTED,suggested);extras.putString("coverArt",song.optString("coverArt"));extras.putString(SongMetadata.EXTRA,SongMetadata.safe(song).toString());
        MediaMetadata metadata=new MediaMetadata.Builder().setExtras(extras).setTitle(song.optString("title")).setArtist(Library.artist(song)).setAlbumTitle(song.optString("album")).setDurationMs(song.optLong("duration")>0?song.optLong("duration")*1000L:null).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setArtworkUri(song.optString("coverArt").isEmpty()?null:Uri.parse(api.url("getCoverArt","id",song.optString("coverArt"),"size","512"))).build();
        MediaItem.Builder item=new MediaItem.Builder().setMediaId(song.optString("id")).setUri(uri).setMediaMetadata(metadata);if(!raw)item.setMimeType(MimeTypes.AUDIO_MPEG);return item.build();
    }
}
