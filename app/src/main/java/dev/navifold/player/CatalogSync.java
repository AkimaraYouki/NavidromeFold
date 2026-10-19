package dev.navifold.player;

import android.content.Context;
import org.json.JSONObject;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Metadata-only pagination, shared by text search and cold-start autoplay. Never downloads audio. */
final class CatalogSync {
    static synchronized void ensure(Context context,Api api,String scope,BooleanSupplier valid,Consumer<String> progress)throws Exception {
        SmartLibrary db=SmartLibrary.get(context);
        android.content.SharedPreferences p=context.getSharedPreferences("catalog-sync",0);
        if(db.count(scope)>0&&System.currentTimeMillis()-p.getLong(scope,0)<6*3600000L)return;
        Set<String> seen=new HashSet<>();int offset=0;
        while(true){
            if(!valid.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedException();
            List<JSONObject> page=Library.array(api.call("search3","query","","songCount","500","songOffset",String.valueOf(offset),"albumCount","0","artistCount","0").optJSONObject("searchResult3"),"song");
            if(page.isEmpty())break;
            int added=0;for(JSONObject song:page){if(!valid.getAsBoolean())throw new InterruptedException();String id=song.optString("id");if(!id.isEmpty()&&seen.add(id)){db.put(scope,song,System.currentTimeMillis());added++;}}
            if(added==0)throw new java.io.IOException("서버가 곡 목록 페이지를 반복합니다 · Navidrome 연결 확인");
            offset+=page.size();progress.accept("라이브러리 확인 · "+seen.size()+"곡");
        }
        if(valid.getAsBoolean())p.edit().putLong(scope,System.currentTimeMillis()).apply();
    }
    static JSONObject completeTrack(Api api,SmartLibrary db,String scope,JSONObject song,BooleanSupplier valid)throws Exception {
        JSONObject out=SongMetadata.complete(db.song(scope,song.optString("id")),song);
        if(!FlowPolicy.genres(out).isEmpty())return out;
        try{JSONObject detail=api.quick("getSong","id",out.optString("id")).optJSONObject("song");out=SongMetadata.complete(out,detail);}catch(Exception e){if(e instanceof InterruptedException)throw e;}
        if(!valid.getAsBoolean())throw new InterruptedException();
        if(FlowPolicy.genres(out).isEmpty()&&!out.optString("albumId").isEmpty()){
            try{JSONObject album=api.quick("getAlbum","id",out.optString("albumId")).optJSONObject("album");JSONObject tags=new JSONObject();if(album!=null)for(String key:new String[]{"genre","genres","recordLabels","label","albumArtist","language","languages"})if(album.has(key))tags.put(key,album.get(key));out=SongMetadata.complete(tags,out);}catch(Exception e){if(e instanceof InterruptedException)throw e;}
        }
        db.put(scope,out,System.currentTimeMillis());return out;
    }
}
