package dev.navifold.player;

import org.json.*;
import java.net.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Optional read-only MusicBrainz enrichment. Never guesses labels on a loose match. */
final class MusicMetadata {
    private static long nextRequest;
    private static synchronized JSONObject get(String path) throws Exception {
        long wait=nextRequest-android.os.SystemClock.elapsedRealtime();if(wait>0)Thread.sleep(wait);
        nextRequest=android.os.SystemClock.elapsedRealtime()+1100;
        HttpURLConnection c=ChatGptPlan.connection("https://musicbrainz.org/ws/2/"+path,"GET",null);
        c.setRequestProperty("User-Agent","NaviFold/0.7.7 (local open-source Navidrome player)");c.setConnectTimeout(3000);c.setReadTimeout(4000);
        try{if(c.getResponseCode()!=200)throw new java.io.IOException("MusicBrainz metadata unavailable");return new JSONObject(ChatGptPlan.read(c.getInputStream(),2*1024*1024));}finally{c.disconnect();}
    }
    private static String normalized(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
    private static String quote(String value){return "\""+value.replace("\\"," ").replace("\""," ")+"\"";}
    static List<JSONObject> enrich(SmartLibrary db,String scope,List<JSONObject> candidates,BooleanSupplier valid) throws Exception {return enrich(db,scope,candidates,valid,true);}
    static List<JSONObject> enrich(SmartLibrary db,String scope,List<JSONObject> candidates,BooleanSupplier valid,boolean persistTracks) throws Exception {
        List<JSONObject> out=new ArrayList<>();int fetched=0;long deadline=android.os.SystemClock.elapsedRealtime()+6000;
        for(JSONObject song:candidates){
            if(!valid.getAsBoolean())throw new InterruptedException();
            String artist=song.optString("albumArtist",song.optString("artist")),album=song.optString("album");String cacheKey=artist+"\n"+album;
            JSONObject data=db.enrichment(scope,cacheKey);
            if(data==null&&fetched<3&&android.os.SystemClock.elapsedRealtime()<deadline&&!artist.isEmpty()&&!album.isEmpty()) {
                fetched++;
                try {data=lookup(artist,album);db.cacheEnrichment(scope,cacheKey,data);}catch(Exception e){data=new JSONObject();if(e instanceof InterruptedException)throw e;}
            }
            JSONObject merged=data==null?song:SongMetadata.complete(data,song);if(persistTracks)db.put(scope,merged,System.currentTimeMillis());out.add(merged);
        }
        return out;
    }
    private static JSONObject lookup(String artist,String album) throws Exception {
        String query="release:"+quote(album)+" AND artist:"+quote(artist);
        JSONArray releases=get("release/?fmt=json&limit=5&query="+URLEncoder.encode(query,"UTF-8")).optJSONArray("releases");
        if(releases==null)return new JSONObject();
        for(int i=0;i<releases.length();i++) {
            JSONObject release=releases.getJSONObject(i);if(release.optInt("score")<95||!normalized(album).equals(normalized(release.optString("title"))))continue;
            JSONArray credits=release.optJSONArray("artist-credit");boolean match=false;
            if(credits!=null)for(int j=0;j<credits.length();j++){JSONObject credit=credits.optJSONObject(j);if(credit!=null){JSONObject a=credit.optJSONObject("artist");match|=normalized(artist).equals(normalized(credit.optString("name")))||a!=null&&normalized(artist).equals(normalized(a.optString("name")));}}
            if(!match)continue;
            JSONObject full=get("release/"+release.getString("id")+"?fmt=json&inc=labels%2Bgenres");
            JSONObject result=new JSONObject();JSONArray labels=new JSONArray(),genres=new JSONArray();JSONArray entries=full.optJSONArray("label-info");
            if(entries!=null)for(int j=0;j<entries.length();j++){JSONObject entry=entries.optJSONObject(j);JSONObject label=entry==null?null:entry.optJSONObject("label");if(label!=null&&!label.optString("name").isEmpty())labels.put(label.optString("name"));}
            entries=full.optJSONArray("genres");if(entries!=null)for(int j=0;j<Math.min(5,entries.length());j++){JSONObject genre=entries.optJSONObject(j);if(genre!=null)genres.put(genre.optString("name"));}
            if(labels.length()>0)result.put("recordLabels",labels);if(genres.length()>0)result.put("genres",genres);
            result.put("metadataSource","MusicBrainz · album/artist match").put("metadataReleaseId",release.getString("id"));return result;
        }
        return new JSONObject();
    }
}
