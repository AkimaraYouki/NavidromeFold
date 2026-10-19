package dev.navifold.player;

import android.content.*;
import android.os.*;
import androidx.media3.common.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Owned by the playback service, so screen changes and background playback keep working. */
final class SmartPlayback implements AutoCloseable {
    static final String FLOW_ANCHOR="navifold.smart.anchor";
    private JSONObject anchor=new JSONObject();
    static final String SUGGESTED="navifold.smart.suggested";
    private final Context context;
    private final Player player;
    private final SmartLibrary library;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService inference=Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs;
    private volatile boolean closed;
    private boolean busy,ownTimeline,automaticTransition,activeError;
    private long epoch,lastTick,heard,connectionRevision=-1,nextAttempt;
    private int consecutive,failures;
    private String activeScope="";
    private JSONObject active=new JSONObject();
    private int pendingReason=-1;
    private boolean pendingForward;
    private MediaItem pendingItem;
    private MediaItem observed;

    SmartPlayback(Context c,Player p){context=c.getApplicationContext();player=p;library=SmartLibrary.get(c);prefs=c.getSharedPreferences("smart-playback",0);prefs.edit().putBoolean("refillBusy",false).apply();}
    static SharedPreferences settings(Context c){return c.getSharedPreferences("smart-playback",0);}
    void status(String value){SmartStatus.record(context,value);}
    private void setBusy(boolean value){busy=value;prefs.edit().putBoolean("refillBusy",value).apply();}
    void error(){activeError=true;}
    void timeline(int reason){if(!ownTimeline&&reason==Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)epoch++;}
    void discontinuity(Player.PositionInfo before,Player.PositionInfo after,int reason){if(reason==Player.DISCONTINUITY_REASON_SEEK&&before.mediaItemIndex!=after.mediaItemIndex)pendingForward=after.mediaItemIndex>before.mediaItemIndex;}
    void transition(MediaItem item,int reason){pendingItem=item;pendingReason=reason;}
    void events(){
        if(pendingReason<0)return;
        accountChanged();accrue();
        int reason=pendingReason;MediaItem item=pendingItem;pendingReason=-1;
        boolean auto=automaticTransition;automaticTransition=false;
        if(!SmartSession.consumeIgnoredRemoval(activeScope,active.optString("id")))finish(SmartPolicy.outcome(heard,active.optLong("duration")*1000,reason==Player.MEDIA_ITEM_TRANSITION_REASON_SEEK&&pendingForward,auto,activeError));
        activeError=false;
        pendingForward=false;heard=0;lastTick=SystemClock.elapsedRealtime();observed=item;active=metadata(item);activeScope=SmartSession.scope;
        if(!auto)consecutive=0;
        updateAnchor(item,!auto&&(reason==Player.MEDIA_ITEM_TRANSITION_REASON_SEEK||reason==Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED));
        if(item!=null&&!activeScope.isEmpty()&&prefs.getBoolean("learn",false)){
            JSONObject song=active;String scope=activeScope;long revision=SmartSession.revision;worker.execute(()->{if(revision==SmartSession.revision&&prefs.getBoolean("learn",false))library.put(scope,song,System.currentTimeMillis());});
        }
    }
    private void accountChanged(){
        if(connectionRevision==SmartSession.revision)return;
        connectionRevision=SmartSession.revision;epoch++;nextAttempt=0;
        // A new account/configuration must not inherit an unfinished observation.
        boolean sameAccount=activeScope.equals(SmartSession.scope);if(!sameAccount)anchor=new JSONObject();activeScope=SmartSession.scope;active=sameAccount?metadata(player.getCurrentMediaItem()):new JSONObject();heard=0;lastTick=SystemClock.elapsedRealtime();observed=player.getCurrentMediaItem();if(anchor.optString("id").isEmpty())restoreAnchor();
    }
    private static boolean suggested(MediaItem item){return item!=null&&item.mediaMetadata.extras!=null&&item.mediaMetadata.extras.getBoolean(SUGGESTED,false);}
    private void updateAnchor(MediaItem item,boolean manualSeek){JSONObject song=metadata(item);if(FlowPolicy.shouldAnchor(anchor,song,suggested(item),manualSeek)){anchor=SongMetadata.safe(song);epoch++;nextAttempt=0;}}
    private void restoreAnchor(){MediaItem current=player.getCurrentMediaItem();if(current==null)return;
        if(suggested(current)&&current.mediaMetadata.extras!=null){try{anchor=SongMetadata.safe(new JSONObject(current.mediaMetadata.extras.getString(FLOW_ANCHOR,"{}")));}catch(Exception ignored){}}
        if(!anchor.optString("id").isEmpty())return;
        for(int i=Math.min(player.getCurrentMediaItemIndex(),player.getMediaItemCount()-1);i>=0;i--){MediaItem item=player.getMediaItemAt(i);if(!suggested(item)){anchor=SongMetadata.safe(metadata(item));return;}}
        anchor=SongMetadata.safe(metadata(current));
    }
    private void accrue(){long now=SystemClock.elapsedRealtime();if(lastTick!=0&&player.isPlaying())heard+=Math.max(0,Math.min(1500,now-lastTick));lastTick=now;}
    private void finish(String kind){
        if(!prefs.getBoolean("learn",false)||activeScope.isEmpty()||active.optString("id").isEmpty())return;
        JSONObject song=active;String scope=activeScope;long audible=heard;
        long revision=SmartSession.revision;worker.execute(()->{if(revision==SmartSession.revision&&prefs.getBoolean("learn",false))library.event(scope,song,kind,audible);});
    }
    static JSONObject metadata(MediaItem item){
        if(item==null)return new JSONObject();
        try{JSONObject song=item.mediaMetadata.extras==null?new JSONObject():new JSONObject(item.mediaMetadata.extras.getString(SongMetadata.EXTRA,"{}"));song.put("id",item.mediaId);return song;}catch(Exception e){return new JSONObject();}
    }
    void tick(){
        if(closed)return;accountChanged();accrue();
        MediaItem item=player.getCurrentMediaItem();
        if(item==null){if(player.getMediaItemCount()==0)SmartSettings.setAutoplay(context,player,false);return;}if(SmartSession.api==null)return;
        if(prefs.getBoolean("skip",false)&&player.isPlaying()&&observed==item&&item.mediaMetadata.extras!=null&&item.mediaMetadata.extras.getBoolean(SUGGESTED,false)){
            String scope=SmartSession.scope,id=item.mediaId;long expected=epoch;MediaItem expectedItem=item;
            // SQLite lookup runs outside the audio/UI thread.
            if(!skipCheck){skipCheck=true;worker.execute(()->{boolean negative=library.negative(scope,id);main.post(()->{skipCheck=false;if(closed||epoch!=expected||player.getCurrentMediaItem()!=expectedItem)return;
                if(SmartPolicy.canSkip(prefs.getBoolean("skip",false),true,negative,player.hasNextMediaItem(),consecutive)){
                    automaticTransition=true;consecutive++;player.seekToNextMediaItem();status("취향에 따라 추천 곡 넘김");
                }
            });});}
        }
        int remaining=player.getMediaItemCount()-player.getCurrentMediaItemIndex()-1;
        if(SmartPolicy.canRefill(prefs.getBoolean("autoplay",false),player.getPlayWhenReady()&&player.getPlayerError()==null,player.getRepeatMode(),player.getShuffleModeEnabled(),remaining)&&SystemClock.elapsedRealtime()>=nextAttempt&&!busy)refill();
    }
    private boolean skipCheck;
    private void refill(){
        setBusy(true);nextAttempt=SystemClock.elapsedRealtime()+5000;
        Api api=SmartSession.api;String scope=SmartSession.scope;long revision=SmartSession.revision,expected=epoch;
        if(anchor.optString("id").isEmpty())restoreAnchor();
        JSONObject anchorSnapshot=SongMetadata.safe(anchor);int flowMode=Math.max(0,Math.min(2,prefs.getInt("flowMode",1)));
        JSONObject seed=metadata(player.getMediaItemAt(player.getMediaItemCount()-1)); // Continue from the tail, never jump across a manually built queue.
        List<String> queueIds=new ArrayList<>();for(int i=0;i<player.getMediaItemCount();i++)queueIds.add(player.getMediaItemAt(i).mediaId);
        Set<String> excluded=QueueHistory.upcoming(queueIds,player.getCurrentMediaItemIndex()),recentQueue=QueueHistory.recent(queueIds,player.getCurrentMediaItemIndex());
        String model=new ChatGptPlan(context).selection();
        AppSettings playback=new AppSettings(AppSettings.preferences(context));
        status("다음 곡 추천 중");
        inference.execute(()->{
            try {
                if(closed||revision!=SmartSession.revision)throw new InterruptedException();
                AutoplayCandidates.Pool pool=AutoplayCandidates.load(context,api,scope,seed,anchorSnapshot,flowMode,excluded,recentQueue,()->!closed&&revision==SmartSession.revision,text->main.post(()->{if(!closed&&revision==SmartSession.revision&&epoch==expected)status(text);}));
                JSONObject flowSeed=pool.seed,anchorTags=pool.anchor;
                List<JSONObject> candidates=pool.songs;
                List<JSONObject> selected;boolean fallback=false,remoteAttempted=false;
                try {
                    ChatGptPlan plan=new ChatGptPlan(context);
                    if(!plan.ready())throw new java.io.IOException("ChatGPT 연결·모델 선택 필요");
                    if(!reserveRequest())throw new java.io.IOException("시간당 요청 한도 · 로컬 추천 사용");
                    remoteAttempted=true;
                    selected=RecommendationBatch.select(plan.recommend(RecommendationBatch.flowPrompt(candidates,flowSeed,library.context(scope))+"\n"+FlowPolicy.constraint(anchorTags,flowMode)+(pool.missing?" Missing-genre candidates were explicitly admitted in natural mode; keep known language/scene conflicts excluded, do not invent missing tags.":"")),candidates);
                prefs.edit().putInt("remoteFailures",0).putLong("remoteRetryAt",0).apply();
                } catch(Exception e) {
                    if(remoteAttempted){int failures=Math.min(5,prefs.getInt("remoteFailures",0)+1);prefs.edit().putInt("remoteFailures",failures).putLong("remoteRetryAt",System.currentTimeMillis()+Math.min(1800000,120000L*(1L<<failures))).apply();}
                    if(!prefs.getBoolean("fallback",true))throw e;
                    fallback=true;selected=RecommendationBatch.local(candidates,10);
                }
                List<MediaItem> items=new ArrayList<>();
                for(JSONObject song:selected)if(pool.allows(song,flowMode)&&!library.negative(scope,song.optString("id"))){MediaItem item=PlaybackItems.make(api,song,playback.original,playback.bitrate,true);Bundle extras=new Bundle(item.mediaMetadata.extras);extras.putString(FLOW_ANCHOR,anchorTags.toString());extras.putString(RecommendationReason.EXTRA,RecommendationReason.explain(flowSeed,song));items.add(item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build());}
                boolean local=fallback;String confidence=pool.missing?" · 태그 일부 없음":"";String recentNote=pool.recentRelaxed?" · 최근 곡 재허용":"";
                main.post(()->{
                    setBusy(false);
                    if(closed||revision!=SmartSession.revision||epoch!=expected||!prefs.getBoolean("autoplay",false)||!model.equals(new ChatGptPlan(context).selection())||player.getRepeatMode()!=Player.REPEAT_MODE_OFF||player.getShuffleModeEnabled()||!player.getPlayWhenReady())return;
                    Set<String> existing=new HashSet<>();for(int i=Math.max(0,player.getCurrentMediaItemIndex());i<player.getMediaItemCount();i++)existing.add(player.getMediaItemAt(i).mediaId);
                    List<MediaItem> unique=new ArrayList<>();for(MediaItem song:items)if(existing.add(song.mediaId))unique.add(song);
                    if(unique.isEmpty()){nextAttempt=SystemClock.elapsedRealtime()+30000;status("큐 밖에 추가할 새 곡이 없습니다");return;}
                    ownTimeline=true;try{player.addMediaItems(unique);}finally{ownTimeline=false;}
                    if(player.getPlaybackState()==Player.STATE_ENDED){player.seekToNextMediaItem();player.prepare();player.play();}
                    failures=0;
                    nextAttempt=SystemClock.elapsedRealtime()+5000;
                    status((local?"LOCAL":"ChatGPT")+" · "+unique.size()+"곡 추가"+confidence+recentNote);
                });
            } catch(Exception e){main.post(()->{setBusy(false);if(!closed&&revision==SmartSession.revision&&epoch==expected){
                if(e instanceof AutoplayCandidates.Empty){nextAttempt=SystemClock.elapsedRealtime()+30000;status(e.getMessage());}
                else if(!(e instanceof InterruptedException)){failures=Math.min(5,failures+1);nextAttempt=SystemClock.elapsedRealtime()+Math.min(300000,15000L*(1L<<failures));status("추천 연결 대기 · 서버 또는 ChatGPT 연결·모델·한도 확인");}
            }});}

        });
    }
    private boolean reserveRequest(){
        long now=System.currentTimeMillis();
        if(now-prefs.getLong("lastRequestAt",0)<120000||now<prefs.getLong("remoteRetryAt",0))return false;
        long hour=now/3600000;
        if(prefs.getLong("requestHour",-1)!=hour)prefs.edit().putLong("requestHour",hour).putInt("requestCount",0).apply();
        int count=prefs.getInt("requestCount",0);if(count>=6)return false;
        prefs.edit().putInt("requestCount",count+1).putLong("lastRequestAt",now).apply();return true;
    }
    public void close(){setBusy(false);closed=true;epoch++;main.removeCallbacksAndMessages(null);worker.shutdownNow();inference.shutdownNow();}
}
