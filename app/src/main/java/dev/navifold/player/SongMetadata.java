package dev.navifold.player;

import org.json.*;
import java.util.*;

/** Only music metadata crosses the player boundary; never copy API URLs or credentials. */
final class SongMetadata {
    static final String EXTRA="navifold.song.metadata";
    private static final String[] KEYS={"aiMoods","aiEnergy","aiInstrumental","aiConfidence","language","languages","musicScene","id","title","artist","albumArtist","album","year","genre","genres","artists","albumArtists","discNumber","track","duration","suffix","contentType","bitRate","samplingRate","channelCount","bitDepth","size","path","bpm","comment","releaseDate","originalReleaseDate","coverArt","albumId","recordLabels","label","musicBrainzId","musicBrainzReleaseId","starred","metadataSource","metadataReleaseId"};
    static JSONObject safe(JSONObject source){
        JSONObject out=new JSONObject();
        if(source==null)return out;
        for(String key:KEYS){Object v=source.opt(key);if(v==null||v==JSONObject.NULL)continue;
            if(v instanceof String && ((String)v).length()>2000)v=((String)v).substring(0,2000);
            // Structured objects are restricted to known date/genre/artist fields.
            if(v instanceof JSONObject){JSONObject date=new JSONObject();for(String part:new String[]{"year","month","day"})try{if(((JSONObject)v).optInt(part)>0)date.put(part,((JSONObject)v).optInt(part));}catch(JSONException ignored){}v=date;}
            if(v instanceof JSONArray){JSONArray arr=new JSONArray();for(int i=0;i<Math.min(32,((JSONArray)v).length());i++){Object item=((JSONArray)v).opt(i);if(item instanceof JSONObject)item=((JSONObject)item).optString("name","");if(item instanceof String)arr.put(((String)item).substring(0,Math.min(200,((String)item).length())));}v=arr;}
            try{out.put(key,v);}catch(JSONException ignored){}
        }return out;
    }
    static JSONObject merge(JSONObject older,JSONObject newer){JSONObject out=safe(older),fresh=safe(newer);Iterator<String> it=fresh.keys();while(it.hasNext()){String key=it.next();try{out.put(key,fresh.get(key));}catch(JSONException ignored){}}return out;}
    static JSONObject complete(JSONObject older,JSONObject newer){
        JSONObject fresh=safe(newer);Iterator<String> keys=fresh.keys();List<String> empty=new ArrayList<>();
        while(keys.hasNext()){String key=keys.next();Object v=fresh.opt(key);if(v instanceof String&&((String)v).trim().isEmpty()||v instanceof JSONArray&&((JSONArray)v).length()==0||v instanceof JSONObject&&((JSONObject)v).length()==0||v instanceof Number&&((Number)v).doubleValue()<=0)empty.add(key);}
        for(String key:empty)fresh.remove(key);return merge(older,fresh);
    }
    static String value(JSONObject j,String key){Object v=j.opt(key);if(v==null||v==JSONObject.NULL)return "";return String.valueOf(v).trim();}
    static String names(JSONObject j,String array,String fallback){JSONArray a=j.optJSONArray(array);List<String> names=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){Object v=a.opt(i);String name=v instanceof JSONObject?((JSONObject)v).optString("name",""):String.valueOf(v);if(!name.isEmpty())names.add(name);}return names.isEmpty()?value(j,fallback):String.join(", ",names);}
    static String year(JSONObject j){if(j.optInt("year")>0)return String.valueOf(j.optInt("year"));JSONObject date=j.optJSONObject("releaseDate");return date!=null&&date.optInt("year")>0?String.valueOf(date.optInt("year")):"정보 없음";}
    static String duration(long seconds){return String.format(Locale.ROOT,"%d:%02d",Math.max(0,seconds)/60,Math.max(0,seconds)%60);}
    static LinkedHashMap<String,String> rows(JSONObject j){
        LinkedHashMap<String,String> r=new LinkedHashMap<>();
        r.put("곡 제목",value(j,"title"));r.put("아티스트",names(j,"artists","artist"));r.put("앨범",value(j,"album"));r.put("앨범 아티스트",names(j,"albumArtists","albumArtist"));r.put("발매연도",year(j));
        JSONObject date=j.optJSONObject("originalReleaseDate");if(date!=null&&date.optInt("year")>0)r.put("최초 발매연도",String.valueOf(date.optInt("year")));
        r.put("장르",names(j,"genres","genre"));r.put("레이블",names(j,"recordLabels","label"));if(!value(j,"metadataSource").isEmpty())r.put("보완 정보 출처",value(j,"metadataSource"));r.put("디스크 · 트랙",(j.optInt("discNumber")>0?j.optInt("discNumber"):"—")+" · "+(j.optInt("track")>0?j.optInt("track"):"—"));
        r.put("재생 시간",j.optLong("duration")>0?duration(j.optLong("duration")):"");
        r.put("원본 형식",value(j,"suffix").toUpperCase(Locale.ROOT));if(j.optInt("bitRate")>0)r.put("원본 비트레이트",j.optInt("bitRate")+" kbps");
        if(j.optInt("samplingRate")>0)r.put("샘플링 레이트",String.format(Locale.ROOT,"%.1f kHz",j.optInt("samplingRate")/1000.0));
        if(j.optInt("bitDepth")>0)r.put("비트 깊이",j.optInt("bitDepth")+" bit");if(j.optInt("channelCount")>0)r.put("채널",j.optInt("channelCount")+" ch");
        if(j.optLong("size")>0)r.put("파일 크기",String.format(Locale.ROOT,"%.1f MiB",j.optLong("size")/(1024.0*1024)));
        if(j.optDouble("bpm")>0)r.put("템포",value(j,"bpm")+" BPM");r.put("파일 경로",value(j,"path"));if(!value(j,"comment").isEmpty())r.put("메모",value(j,"comment"));
        r.replaceAll((key,v)->v==null||v.isEmpty()?"정보 없음":v);return r;
    }
}
