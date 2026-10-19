package dev.navifold.player;

import android.content.Context;
import org.json.JSONObject;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class AutoplayCandidates {
    static final class Empty extends java.io.IOException {Empty(String message){super(message);}}
    static final class Pool {
        final List<JSONObject> songs;final JSONObject anchor,seed;final boolean missing,recentRelaxed;
        Pool(List<JSONObject> songs,JSONObject anchor,JSONObject seed,boolean missing,boolean recent){this.songs=songs;this.anchor=anchor;this.seed=seed;this.missing=missing;this.recentRelaxed=recent;}
        boolean allows(JSONObject song,int mode){return new FlowPolicy.Matcher(anchor,mode,missing).allows(song);}
    }
    static Pool load(Context c,Api api,String scope,JSONObject seed,JSONObject anchor,int mode,Set<String> queue,BooleanSupplier valid,Consumer<String> progress)throws Exception {
        return load(c,api,scope,seed,anchor,mode,queue,Collections.emptySet(),valid,progress);
    }
    static Pool load(Context c,Api api,String scope,JSONObject seed,JSONObject anchor,int mode,Set<String> queue,Set<String> recentQueue,BooleanSupplier valid,Consumer<String> progress)throws Exception {
        SmartLibrary db=SmartLibrary.get(c);
        seed=SongMetadata.complete(db.song(scope,seed.optString("id")),seed);
        anchor=CatalogSync.completeTrack(api,db,scope,anchor,valid);
        boolean external=SmartPlayback.settings(c).getBoolean("external",true);
        Set<String> excluded=new HashSet<>(queue);excluded.addAll(db.recent(scope));excluded.addAll(recentQueue);
        List<JSONObject> songs=db.candidates(scope,seed,excluded,40,anchor,mode);
        if(songs.isEmpty()){
            try{CatalogSync.ensure(c,api,scope,valid,progress);}catch(InterruptedException e){throw e;}catch(Exception e){
                progress.accept("서버 갱신 지연 · 저장된 후보 확인 중");
                // Some older Subsonic servers cannot enumerate an empty search. Keep a bounded fallback.
                if(db.count(scope)==0){for(JSONObject s:Library.array(api.call("getRandomSongs","size","500").optJSONObject("randomSongs"),"song"))db.put(scope,s,System.currentTimeMillis());}
            }
            songs=db.candidates(scope,seed,excluded,40,anchor,mode);
        }
        // Complete missing server tags BEFORE excluding unknown candidates. External lookup is optional.
        if(songs.isEmpty()){
            progress.accept("빠진 곡 정보 보완 중");
            if(external)anchor=MusicMetadata.enrich(db,scope,Collections.singletonList(anchor),valid).get(0);
            List<JSONObject> unknown=db.candidates(scope,seed,excluded,40,anchor,2);
            int fetched=0;long deadline=android.os.SystemClock.elapsedRealtime()+6000;Set<String> albums=new HashSet<>();
            for(JSONObject s:unknown){if(!valid.getAsBoolean())throw new InterruptedException();if(FlowPolicy.genres(s).isEmpty()&&fetched<6&&android.os.SystemClock.elapsedRealtime()<deadline&&albums.add(s.optString("albumId",s.optString("id")))){CatalogSync.completeTrack(api,db,scope,s,valid);fetched++;}}
            if(external)MusicMetadata.enrich(db,scope,unknown,valid);
            songs=db.candidates(scope,seed,excluded,40,anchor,mode);
        }
        boolean missing=false,recent=false;
        if(songs.isEmpty()&&mode==1){songs=db.candidates(scope,seed,excluded,40,anchor,mode,true);missing=!songs.isEmpty();}
        if(songs.isEmpty()){
            songs=db.candidates(scope,seed,queue,40,anchor,mode);recent=!songs.isEmpty();
            if(songs.isEmpty()&&mode==1){songs=db.candidates(scope,seed,queue,40,anchor,mode,true);missing=!songs.isEmpty();recent=missing;}
        }
        if(songs.isEmpty()){
            if(db.count(scope)==0)throw new Empty("서버에서 추천할 곡을 찾지 못했습니다 · 라이브러리 접근 권한 확인");
            if(db.candidates(scope,seed,queue,1,anchor,2).isEmpty())throw new Empty("큐 밖에 추가할 곡이 없습니다 · 기존 큐 또는 비선호 표시 확인");
            throw new Empty(mode==0?"엄격 모드에 맞는 곡이 없습니다 · 자연스럽게 모드에서 다시 추천 가능":"현재 장르·음악권·언어 조건에 맞는 곡이 없습니다");
        }
        return new Pool(songs,anchor,seed,missing,recent);
    }
}
