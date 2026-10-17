package dev.navifold.player;

import android.content.*;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.session.*;
import org.json.*;
import java.util.*;

/** Synthetic fixture only; excluded from ordinary APKs. */
public final class NextProbe extends BroadcastReceiver {
    public void onReceive(Context receiver, Intent intent) {
        Context c=receiver.getApplicationContext();PendingResult result=goAsync();Handler main=new Handler(Looper.getMainLooper());
        com.google.common.util.concurrent.ListenableFuture<MediaController> future=new MediaController.Builder(c,new SessionToken(c,new ComponentName(c,PlaybackService.class))).buildAsync();
        future.addListener(()->{
            try {
                MediaController p=future.get();String action=intent.getStringExtra("action");
                if("seed".equals(action)) {
                    Api api=new Api("http://10.0.2.2:18765","demo","demo");ServerCredentials.save(c,api);SmartSession.connect(api);
                    SmartPlayback.settings(c).edit().putBoolean("autoplay",false).putBoolean("external",false).apply();
                    List<MediaItem> items=new ArrayList<>();
                    for(int n=0;n<3;n++) {
                        JSONObject song=new JSONObject().put("id",n==1?"s001-0":"s000-0").put("title",n==1?"Next recommendation":"Manual selection").put("artist","Fixture Artist").put("album",n==1?"Album 001":"Album 000").put("albumId",n==1?"a001":"a000").put("coverArt",n==1?"c001":"c000").put("genre","Pop").put("duration",120).put("year",2024);
                        MediaItem item=PlaybackItems.make(api,song,true,192,n==1);Bundle extras=new Bundle(item.mediaMetadata.extras);extras.putString(RecommendationReason.EXTRA,"같은 장르 · 가까운 발매 시기");extras.putString(SmartPlayback.FLOW_ANCHOR,song.toString());items.add(item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build());
                    }
                    p.pause();p.setMediaItems(items,1,34567);p.prepare();
                }
                if("learnOn".equals(action))SmartPlayback.settings(c).edit().putBoolean("learn",true).apply();
                if("learnOff".equals(action))SmartPlayback.settings(c).edit().putBoolean("learn",false).apply();
                if("clear".equals(action))p.clearMediaItems();
                if("logout".equals(action)){ServerCredentials.clear(c);SmartSession.connect(null);p.stop();p.clearMediaItems();}
                if("switchAccount".equals(action)){SmartSession.connect(new Api("http://10.0.2.2:18765","other-demo","demo"));}
                main.postDelayed(()->{
                    try {
                        JSONObject proof=new JSONObject().put("count",p.getMediaItemCount()).put("index",p.getCurrentMediaItemIndex()).put("position",p.getCurrentPosition()).put("playing",p.isPlaying()).put("playWhenReady",p.getPlayWhenReady());JSONArray ids=new JSONArray();
                        for(int n=0;n<p.getMediaItemCount();n++)ids.put(p.getMediaItemAt(n).mediaId);proof.put("ids",ids);
                        MediaItem item=p.getCurrentMediaItem();proof.put("reason",item==null||item.mediaMetadata.extras==null?"":item.mediaMetadata.extras.getString(RecommendationReason.EXTRA,""));
                        String saved=c.getSharedPreferences("queue-state",0).getString("state_"+SmartSession.scope,"");proof.put("saved",!saved.isEmpty()).put("noSavedCredentials",!saved.contains("password")&&!saved.contains("http:")&&!saved.contains("t="));
                        if(!saved.isEmpty()){QueueSnapshot s=QueueSnapshot.decode(saved);proof.put("savedCount",s.entries.size()).put("savedIndex",s.index).put("savedPosition",s.position);}
                        try(android.database.Cursor cursor=SmartLibrary.get(c).getReadableDatabase().rawQuery("SELECT vote FROM taste WHERE scope=? AND id=?",new String[]{SmartSession.scope,"s001-0"})){proof.put("vote",cursor.moveToFirst()?cursor.getInt(0):0);}
                        try(android.database.Cursor events=SmartLibrary.get(c).getReadableDatabase().rawQuery("SELECT COUNT(*) FROM events WHERE scope=?",new String[]{SmartSession.scope})){proof.put("events",events.moveToFirst()?events.getInt(0):0);}
                        proof.put("negative",SmartLibrary.get(c).negative(SmartSession.scope,"s001-0"));
                        try(java.io.FileOutputStream out=c.openFileOutput("next-proof.json",0)){out.write(proof.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                        result.setResultData(proof.toString());
                    }catch(Exception e){result.setResultData("failed "+e.getClass().getSimpleName());}finally{p.release();result.finish();}
                },1500);
            }catch(Exception e){result.setResultData("failed "+e.getClass().getSimpleName());result.finish();}
        },main::post);
    }
}
