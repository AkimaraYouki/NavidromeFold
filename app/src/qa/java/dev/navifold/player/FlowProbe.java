package dev.navifold.player;
import android.content.*;import android.os.*;import androidx.media3.common.*;import androidx.media3.session.*;import org.json.*;import java.util.*;
/** Synthetic QA catalog only, excluded from production. */
public final class FlowProbe extends BroadcastReceiver {
 public void onReceive(Context receiver,Intent intent){Context c=receiver.getApplicationContext();PendingResult result=goAsync();Handler main=new Handler(Looper.getMainLooper());String action=intent.getStringExtra("action");new Thread(()->{try{
 Api api=new Api("http://127.0.0.1:18765","demo","demo");
 if("ui".equals(action)){ServerCredentials.save(c,api);AppSettings.preferences(c).edit().putString("startTab","앨범").putBoolean("notificationAsked",true).putBoolean("darkTheme",true).apply();result.setResultData("Fixture credentials prepared");result.finish();return;}
 SmartSession.connect(api);SmartLibrary db=SmartLibrary.get(c);List<JSONObject> songs=Library.array(api.call("search3","query","","songCount","90","albumCount","0","artistCount","0").optJSONObject("searchResult3"),"song");
 if("natural".equals(action)){db.clear(SmartSession.scope);for(int n=0;n<songs.size();n++){JSONObject song=songs.get(n);song.put("genre",n%3==0?"Pop":n%3==1?"Synth-pop":"J-pop");song.remove("genres");db.put(SmartSession.scope,song,System.currentTimeMillis());}}
 JSONObject seed=songs.get("switch".equals(action)?2:0);seed.put("genre","switch".equals(action)?"J-pop":"scarce".equals(action)||"diverse".equals(action)?"Classical":"Pop");
 SmartPlayback.settings(c).edit().putInt("flowMode","scarce".equals(action)?0:"diverse".equals(action)?2:1).putBoolean("autoplay",true).putBoolean("learn",false).putBoolean("skip",false).putBoolean("fallback",true).putBoolean("external",false).apply();SmartSession.changed();
 MediaItem start=PlaybackItems.make(api,seed,true,192,false);
 com.google.common.util.concurrent.ListenableFuture<MediaController> f=new MediaController.Builder(c,new SessionToken(c,new ComponentName(c,PlaybackService.class))).buildAsync();f.addListener(()->{try{MediaController p=f.get();p.setRepeatMode(Player.REPEAT_MODE_OFF);p.setShuffleModeEnabled(false);if("drift".equals(action)){if(p.getMediaItemCount()>1){p.seekTo(1,0);if(p.getMediaItemCount()>4)p.removeMediaItems(4,p.getMediaItemCount());p.play();}}else{p.setMediaItem(start);p.prepare();p.play();}
 SmartPlayback.settings(c).edit().putBoolean("autoplay",true).apply();SmartSession.changed();
 main.postDelayed(()->{try{JSONArray queue=new JSONArray();for(int n=0;n<p.getMediaItemCount();n++){MediaItem item=p.getMediaItemAt(n);JSONObject row=SmartPlayback.metadata(item);row.put("suggested",item.mediaMetadata.extras.getBoolean(SmartPlayback.SUGGESTED,false));row.put("anchor",new JSONObject(item.mediaMetadata.extras.getString(SmartPlayback.FLOW_ANCHOR,"{}")).optString("id"));queue.put(row);}JSONObject proof=new JSONObject().put("action",action).put("queue",queue).put("status",SmartPlayback.settings(c).getString("status",""));try(java.io.FileOutputStream out=c.openFileOutput("flow-proof.json",0)){out.write(proof.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}p.pause();result.setResultData(proof.toString());}catch(Exception e){result.setResultData("failed "+e.getClass().getSimpleName());}finally{p.release();result.finish();}},4500);
 }catch(Exception e){result.setResultData("failed "+e.getClass().getSimpleName());result.finish();}},main::post);
 }catch(Exception e){result.setResultData("failed "+e.getClass().getSimpleName());result.finish();}},"flow-probe").start();}
}
