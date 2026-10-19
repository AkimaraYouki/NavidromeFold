package dev.navifold.player;

import android.content.*;
import org.json.*;
import java.util.*;

/** Fixture-only verification; never compiled into ordinary debug or release APKs. */
public final class DiscoveryProbe extends BroadcastReceiver {
    private static void check(boolean ok,String text)throws Exception{if(!ok)throw new Exception(text);}
    private static JSONObject song(String id,String genre)throws Exception{return new JSONObject().put("id",id).put("title","Fixture "+id).put("artist",id).put("genre",genre);}
    public void onReceive(Context receiver,Intent intent){PendingResult pending=goAsync();Context c=receiver.getApplicationContext();new Thread(()->{
        JSONObject proof=new JSONObject();
        try{
            Api api=new Api("http://127.0.0.1:18765","demo","demo");SmartSession.connect(api);String scope=SmartSession.scope;SmartLibrary db=SmartLibrary.get(c);db.clear(scope);
            SmartPlayback.settings(c).edit().putBoolean("external",false).putBoolean("fallback",true).apply();
            c.getSharedPreferences("catalog-sync",0).edit().clear().commit();CatalogSync.ensure(c,api,scope,()->true,text->{});check(db.count(scope)==705,"pagination");proof.put("indexedTracks",db.count(scope));
            JSONObject tail=song("anchor","Jazz");AutoplayCandidates.Pool full=AutoplayCandidates.load(c,api,scope,tail,tail,1,Collections.singleton("anchor"),()->true,text->{});boolean deep=false;for(JSONObject s:full.songs)if(s.optString("id").compareTo("s100-0")>=0)deep=true;check(deep,"beyond first 100");proof.put("beyondFirst100",true);
            db.clear(scope);JSONObject pop=song("anchor","Pop");db.put(scope,pop,1);db.put(scope,song("unknown",""),1);db.put(scope,song("jpop","J-pop"),1);db.put(scope,song("wrong-language","").put("language","ja"),1);
            JSONObject en=SongMetadata.complete(pop,new JSONObject().put("language","en"));AutoplayCandidates.Pool sparse=AutoplayCandidates.load(c,api,scope,en,en,1,Collections.singleton("anchor"),()->true,text->{});check(sparse.missing&&sparse.songs.size()==1&&sparse.songs.get(0).optString("id").equals("unknown"),"sparse safety");proof.put("missingTagsRecovered",true).put("regionalAndLanguageConflictsBlocked",true);
            db.clear(scope);db.put(scope,pop,1);JSONObject recent=song("recent","Pop"),disliked=song("disliked","Pop");db.event(scope,recent,"complete",80000);db.vote(scope,disliked,-1);
            AutoplayCandidates.Pool small=AutoplayCandidates.load(c,api,scope,pop,pop,1,Collections.singleton("anchor"),()->true,text->{});check(small.recentRelaxed&&small.songs.size()==1&&small.songs.get(0).optString("id").equals("recent"),"small library");proof.put("recentRelaxedWithoutDislikes",true);
            db.clear(scope);db.put(scope,pop,1);db.put(scope,song("blank",""),1);boolean strict=false;try{AutoplayCandidates.load(c,api,scope,pop,pop,0,Collections.singleton("anchor"),()->true,text->{});}catch(AutoplayCandidates.Empty e){strict=e.getMessage().contains("엄격");}check(strict,"strict preserved");proof.put("strictModePreserved",true);
            db.put(scope,pop,1);db.put(scope,song("anchor",""),2);check(db.song(scope,"anchor").optString("genre").equals("Pop"),"blank overwrite");proof.put("cachedTagsPreserved",true);
            db.clear(scope);db.put(scope,song("library-private-1","").put("title","Untagged One").put("artist","Test Artist One"),1);db.put(scope,song("library-private-2","").put("title","Untagged Two").put("artist","Test Artist Two"),1);
            final boolean[] privateSafe={false};MoodRecommender.Model model=new MoodRecommender.Model(){public boolean ready(){return true;}public String interpret(String query){return "{\"mood\":\"calm\",\"terms\":[],\"genres\":[]}";}public String select(String prompt){privateSafe[0]=!prompt.contains("library-private")&&!prompt.contains("127.0.0.1")&&!prompt.contains("password");return "[1,0]";}};
            MoodRecommender.Result remote=MoodRecommender.find(c,api,scope,"공부하고 싶어 차분한 노래 틀어줘",model,true,()->true,text->{});check(!remote.local&&remote.songs.size()==2&&privateSafe[0],"model boundary");proof.put("testModelHandlesTaglessCandidates",true).put("noServerIdentifiersInPrompt",true);
            db.clear(scope);db.put(scope,song("calm","Ambient").put("bpm",75),1);db.put(scope,song("dance","Dance").put("bpm",130),1);
            MoodRecommender.Model absent=new MoodRecommender.Model(){public boolean ready(){return false;}public String interpret(String q){return "";}public String select(String p){return "";}};
            MoodRecommender.Result study=MoodRecommender.find(c,api,scope,"공부하고싶어 차분한 노래",absent,true,()->true,text->{}),happy=MoodRecommender.find(c,api,scope,"기분이우울해 신나는 노래 틀어줘",absent,true,()->true,text->{});
            check(study.local&&study.songs.get(0).optString("id").equals("calm")&&happy.local&&happy.songs.get(0).optString("id").equals("dance"),"local targets");proof.put("studyAndUpliftingTargets",true);
            MoodRecommender.Model invalid=new MoodRecommender.Model(){public boolean ready(){return true;}public String interpret(String q){return "{\"mood\":\"calm\"}";}public String select(String p){return "[9000]";}};
            check(MoodRecommender.find(c,api,scope,"차분한 노래",invalid,true,()->true,text->{}).local,"bad model fallback");proof.put("inventedModelIdFallsBack",true);
            boolean cancelled=false;try{MoodRecommender.find(c,api,scope,"차분한 노래",model,true,()->false,text->{});}catch(InterruptedException e){cancelled=true;}check(cancelled,"cancel guard");proof.put("cancelledQueryCannotPlay",true);
            db.clear(scope);List<JSONObject> tracks=new ArrayList<>();for(int n=0;n<3;n++){JSONObject t=song("profile-"+n,"").put("albumId",n<2?"album-a":"album-b");db.put(scope,t,1);tracks.add(t);}
            List<JSONObject> firstAlbum=db.pendingProfiles(scope,60,1);check(firstAlbum.size()==2,"album batching");JSONArray answer=new JSONArray();for(int n=0;n<firstAlbum.size();n++)answer.put(new JSONObject().put("id",n).put("moods",new JSONArray().put("calm").put("focus")).put("energy",25).put("instrumental","unknown").put("confidence",80));db.saveProfiles(scope,firstAlbum,MusicProfile.parse(answer.toString(),firstAlbum.size()));
            check(db.pendingProfiles(scope,60,3).size()==1,"cached tracks repeated");check(db.moodCandidates(scope,MoodIntent.local("공부 차분한 음악"),40,true).size()==2,"cached mood retrieval");check(!db.albumMood(scope,tracks).isEmpty(),"album summary");
            JSONObject changed=SongMetadata.safe(firstAlbum.get(0));changed.put("title","Changed title");db.put(scope,changed,2);check(db.pendingProfiles(scope,60,3).size()==2,"fingerprint invalidation");proof.put("albumBatchedProfileCache",true).put("taglessTracksMatchCachedMood",true).put("changedMetadataInvalidatesProfile",true).put("databaseVersion",db.getReadableDatabase().getVersion());
            // Prepare real synthetic media metadata for UI checks without any account tokens.
            db.clear(scope);c.getSharedPreferences("catalog-sync",0).edit().clear().commit();CatalogSync.ensure(c,api,scope,()->true,text->{});ServerCredentials.save(c,api);AppSettings.preferences(c).edit().putString("startTab","앨범").putBoolean("notificationAsked",true).apply();
            proof.put("passed",true);
        }catch(Exception e){try{proof.put("passed",false).put("failure",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}catch(Exception ignored){}}
        try(java.io.FileOutputStream out=c.openFileOutput("discovery-proof.json",0)){out.write(proof.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){}
        pending.setResultData(proof.toString());pending.finish();
    },"discovery-probe").start();}
}
