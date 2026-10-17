package dev.navifold.player;

import android.content.Context;
import androidx.media3.common.*;
import androidx.media3.exoplayer.source.*;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;

/** Chunked CBR MP3 has no HTTP length. Keep the server's song duration in the real timeline. */
@androidx.media3.common.util.UnstableApi
final class MusicSourceFactory implements MediaSource.Factory {
 final DefaultMediaSourceFactory original,transcoded;
 MusicSourceFactory(Context c){original=new DefaultMediaSourceFactory(c);transcoded=new DefaultMediaSourceFactory(c,new DefaultExtractorsFactory().setConstantBitrateSeekingAlwaysEnabled(true));}
 @Override public MediaSource.Factory setDrmSessionManagerProvider(DrmSessionManagerProvider provider){original.setDrmSessionManagerProvider(provider);transcoded.setDrmSessionManagerProvider(provider);return this;}
 @Override public MediaSource.Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy policy){original.setLoadErrorHandlingPolicy(policy);transcoded.setLoadErrorHandlingPolicy(policy);return this;}
 @Override public int[] getSupportedTypes(){return original.getSupportedTypes();}
 @Override public MediaSource createMediaSource(MediaItem item){boolean converted=item.mediaMetadata.extras!=null&&item.mediaMetadata.extras.getBoolean("transcoded",false);MediaSource source=(converted?transcoded:original).createMediaSource(item);long duration=PlaybackInfo.duration(item.mediaMetadata);return duration>0?new DurationSource(source,duration*1000):source;}
 static final class DurationSource extends WrappingMediaSource {
  final long knownUs;
  DurationSource(MediaSource child,long us){super(child);knownUs=us;}
  @Override protected void onChildSourceInfoRefreshed(Timeline timeline){refreshSourceInfo(new ForwardingTimeline(timeline){
   @Override public Timeline.Window getWindow(int index,Timeline.Window window,long projection){super.getWindow(index,window,projection);if(window.durationUs==C.TIME_UNSET){window.durationUs=knownUs;window.isDynamic=false;window.liveConfiguration=null;}return window;}
   @Override public Timeline.Period getPeriod(int index,Timeline.Period period,boolean setIds){super.getPeriod(index,period,setIds);if(period.durationUs==C.TIME_UNSET)period.durationUs=knownUs;return period;}
  });}
 }
}
