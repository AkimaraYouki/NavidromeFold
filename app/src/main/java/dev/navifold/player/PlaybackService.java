package dev.navifold.player;

import android.app.PendingIntent;
import android.content.Intent;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.*;
import android.os.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/** Playback belongs to the service, so navigation and folding never replace the player. */
public final class PlaybackService extends MediaSessionService {
    private SmartPlayback smart;
    private QueuePersistence queueState;
    private MediaSession session;private ExoPlayer player;private QualityOverlay overlay;private TrackPopup popup;private PendingIntent launch;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){if(player==null)return;NetworkUsage.get(PlaybackService.this).sample(false);queueState.tick();smart.tick();updateQuality();overlay.update(player.getPlayWhenReady()&&player.getMediaItemCount()>0);main.postDelayed(this,750);}};
    @androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
    void updateQuality(){MediaItem item=player.getCurrentMediaItem();if(item==null){AudioQuality.trackId="";return;}Format format=player.getAudioFormat();AudioQuality.trackId=item.mediaId;AudioQuality.current=QualityInfo.classify(format==null?null:format.sampleMimeType,format==null?0:format.sampleRate,format==null?0:format.bitrate,player.getPlayerError()!=null,false);}

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    @Override public void onCreate() {
        super.onCreate();
        PlaybackNotifications.createChannel(this);NetworkUsage.get(this);
        androidx.media3.session.DefaultMediaNotificationProvider notifications = new androidx.media3.session.DefaultMediaNotificationProvider.Builder(this).setChannelId("navifold_playback_channel").setChannelName(R.string.notification_channel_playback).build();
        notifications.setSmallIcon(R.drawable.ic_stat_music);
        setMediaNotificationProvider(notifications);
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(new MusicSourceFactory(this)).build();
        overlay=new QualityOverlay(this);popup=new TrackPopup(this);smart=new SmartPlayback(this,player);queueState=new QueuePersistence(this,player);
        player.addListener(new Player.Listener(){@Override public void onMediaItemTransition(MediaItem item,int reason){smart.transition(item,reason);if(item!=null&&launch!=null)popup.show(item.mediaMetadata,launch);updateQuality();}@Override public void onPlayerError(PlaybackException error){smart.error();}@Override public void onTimelineChanged(Timeline timeline,int reason){smart.timeline(reason);}@Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){smart.discontinuity(oldPosition,newPosition,reason);}@Override public void onEvents(Player p,Player.Events events){smart.events();queueState.changed();updateQuality();}});
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        launch = PendingIntent.getActivity(this, 0,
            new Intent(this, LockPlayerActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaSession.Builder(this, player).setSessionActivity(launch).build();main.post(tick);
    }
    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controller) { return session; }
    @Override public void onTaskRemoved(Intent intent) {
        if (session == null || !session.getPlayer().getPlayWhenReady()) stopSelf();
    }
    @Override public void onDestroy() {
        NetworkUsage.get(this).sample(true);main.removeCallbacksAndMessages(null);if(queueState!=null)queueState.close();if(smart!=null)smart.close();if(overlay!=null)overlay.close();if(popup!=null)popup.close();AudioQuality.trackId="";
        if (session != null) { session.getPlayer().release(); session.release(); session = null; }
        super.onDestroy();
    }
}
