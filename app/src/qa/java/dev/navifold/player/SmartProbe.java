package dev.navifold.player;

import android.content.*;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.session.*;
import org.json.*;
import java.util.*;

public final class SmartProbe extends BroadcastReceiver {
 public void onReceive(Context receiverContext,Intent intent){Context c=receiverContext.getApplicationContext();PendingResult result=goAsync();Handler main=new Handler(Looper.getMainLooper());
 new Thread(()->{try {
  String action=intent.getStringExtra("action");if(action==null)action="snapshot";String command=action;final List<MediaItem> seedItems=new ArrayList<>();
  if(command.equals("notesCache")){
   String scope=SmartSession.namespace("http://10.0.2.2:18765","demo");JSONObject album=new JSONObject().put("album","Album 000").put("artist","Artist 00").put("year",2000).put("genre","Jazz").put("recordLabels",new JSONArray().put("Fixture Records"));List<JSONObject> songs=new ArrayList<>();for(int n=1;n<=3;n++)songs.add(new JSONObject().put("title","Track "+n+" - Album 000"));String fingerprint=AlbumNotes.fingerprint(album,songs);SmartLibrary db=SmartLibrary.get(c);db.saveAlbumNote(scope,"a000",fingerprint,"테스트 캐시 · 2000년, 3곡의 Jazz 앨범.");JSONObject proof=new JSONObject().put("cached",db.albumNote(scope,"a000",fingerprint)).put("otherAccountEmpty",db.albumNote("another-account","a000",fingerprint).isEmpty()).put("changedMetadataEmpty",db.albumNote(scope,"a000","changed").isEmpty()).put("databaseVersion",db.getReadableDatabase().getVersion());try(java.io.FileOutputStream out=c.openFileOutput("notes-proof.json",0)){out.write(proof.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}result.setResultData(proof.toString());result.finish();return;
  }
  if(command.equals("setup")){
   Api api=new Api("http://10.0.2.2:18765","demo","demo");SmartSession.connect(api);SmartLibrary db=SmartLibrary.get(c);db.clear(SmartSession.scope);db.index(api,SmartSession.scope,()->true);
   for(JSONObject song:Library.array(api.call("search3","query","","songCount","3","artistCount","0","albumCount","0").optJSONObject("searchResult3"),"song"))seedItems.add(PlaybackItems.make(api,song,true,192,false));
   SmartPlayback.settings(c).edit().putBoolean("learn",true).putBoolean("autoplay",true).putBoolean("skip",true).putBoolean("fallback",true).putBoolean("external",false).apply();SmartSession.changed();
  }
  com.google.common.util.concurrent.ListenableFuture<MediaController> f=new MediaController.Builder(c,new SessionToken(c,new ComponentName(c,PlaybackService.class))).buildAsync();
  f.addListener(()->{try{MediaController p=f.get();String scope=SmartSession.scope;
   if(command.equals("setup")){p.setRepeatMode(Player.REPEAT_MODE_OFF);p.setShuffleModeEnabled(false);p.setMediaItems(seedItems);p.prepare();p.play();}
   if(command.equals("next"))p.seekToNextMediaItem();
   if(command.equals("pause"))p.pause();
   if(command.equals("play"))p.play();
   if(command.equals("disable")){SmartPlayback.settings(c).edit().putBoolean("autoplay",false).apply();SmartSession.changed();}
   if(command.equals("repeat"))p.setRepeatMode(Player.REPEAT_MODE_ONE);
   if(command.equals("end"))p.seekTo(p.getDuration()-200);
   if(command.equals("negativeSuggested")){int target=-1;for(int i=p.getCurrentMediaItemIndex()+1;i<p.getMediaItemCount();i++){MediaItem item=p.getMediaItemAt(i);if(item.mediaMetadata.extras!=null&&item.mediaMetadata.extras.getBoolean(SmartPlayback.SUGGESTED,false)){SmartLibrary.get(c).vote(scope,SmartPlayback.metadata(item),-1);if(target<0)target=i;if(i>=target+2)break;}}if(target>=0)p.seekTo(target,0);}
   if(command.equals("manualNegative")){MediaItem item=p.getCurrentMediaItem();SmartLibrary.get(c).vote(scope,SmartPlayback.metadata(item),-1);}
   main.postDelayed(()->{try{JSONObject proof=new JSONObject();proof.put("queueSize",p.getMediaItemCount()).put("index",p.getCurrentMediaItemIndex()).put("position",p.getCurrentPosition()).put("playing",p.isPlaying()).put("state",p.getPlaybackState()).put("status",SmartPlayback.settings(c).getString("status",""));Set<String> ids=new HashSet<>();int duplicate=0,suggested=0;for(int i=0;i<p.getMediaItemCount();i++){MediaItem item=p.getMediaItemAt(i);if(!ids.add(item.mediaId))duplicate++;if(item.mediaMetadata.extras!=null&&item.mediaMetadata.extras.getBoolean(SmartPlayback.SUGGESTED,false))suggested++;}proof.put("duplicates",duplicate).put("suggested",suggested);SmartLibrary db=SmartLibrary.get(c);proof.put("summary",db.summary(scope));for(String kind:new String[]{"complete","manual-skip","auto-skip","error"})try(android.database.Cursor cursor=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM events WHERE scope=? AND kind=?",new String[]{scope,kind})){if(cursor.moveToFirst())proof.put(kind,cursor.getLong(0));}proof.put("labelSearch",db.search(scope,"Fixture Records").size()).put("genreSearch",db.search(scope,"Jazz").size()).put("otherAccount",db.summary("another-account"));
   try(java.io.FileOutputStream out=c.openFileOutput("smart-proof.json",0)){out.write(proof.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}result.setResultData(proof.toString());}catch(Exception e){result.setResultData("snapshot failed");}finally{p.release();result.finish();}},2000);
  }catch(Exception e){result.setResultData("command failed: "+e.getClass().getSimpleName());result.finish();}},main::post);
 }catch(Exception e){result.setResultData("setup failed: "+e.getClass().getSimpleName());result.finish();}},"smart-probe").start();
 }
}
