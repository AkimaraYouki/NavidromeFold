package dev.navifold.player;
import java.util.*;
import org.json.*;
/** Short local aliases avoid sending server IDs, URLs or credentials. */
final class RecommendationBatch {
    static String shortText(String text){return text.length()>80?text.substring(0,80):text;}
    static String prompt(List<JSONObject> candidates,String seed) throws Exception {JSONArray rows=new JSONArray();for(int i=0;i<candidates.size();i++){JSONObject s=candidates.get(i);rows.put(new JSONArray().put(i).put(shortText(s.optString("title"))).put(shortText(s.optString("artist"))).put(shortText(s.optString("album"))).put(shortText(s.optString("genre"))).put(s.optInt("year")));}return "Current track: "+shortText(seed)+"\nCandidate columns: id,title,artist,album,genre,year\n"+rows;}
    static String flowPrompt(List<JSONObject> candidates,JSONObject seed,String history) throws Exception {
        JSONArray rows=new JSONArray();
        for(int i=0;i<candidates.size();i++){JSONObject s=candidates.get(i);rows.put(new JSONArray().put(i).put(shortText(s.optString("title"))).put(shortText(s.optString("artist"))).put(shortText(s.optString("album"))).put(shortText(SongMetadata.names(s,"genres","genre"))).put(shortText(SmartLibrary.label(s))).put(s.optInt("year")).put(s.optDouble("bpm",0)).put(shortText(SongMetadata.names(s,"languages","language"))).put(shortText(s.optString("musicScene"))));}
        return "Queue tail [title,artist,album,genre,label,year,bpm]: "+new JSONArray().put(shortText(seed.optString("title"))).put(shortText(seed.optString("artist"))).put(shortText(seed.optString("album"))).put(shortText(SongMetadata.names(seed,"genres","genre"))).put(shortText(SmartLibrary.label(seed))).put(seed.optInt("year")).put(seed.optDouble("bpm",0))+"\n"+history+"\nCandidates [id,title,artist,album,genre,label,year,bpm,language,musicScene]: "+rows;
    }
    static List<JSONObject> local(List<JSONObject> candidates,int limit){
        List<JSONObject> result=new ArrayList<>();Map<String,Integer> artists=new HashMap<>();
        for(JSONObject song:candidates){String artist=song.optString("artist");if(!artist.isEmpty()&&artists.getOrDefault(artist,0)>=2)continue;result.add(song);artists.put(artist,artists.getOrDefault(artist,0)+1);if(result.size()>=limit)break;}
        return result;
    }
    static List<JSONObject> select(String response,List<JSONObject> candidates) throws Exception {JSONArray ids=new JSONArray(response.trim());if(ids.length()==0||ids.length()>10)throw new IllegalArgumentException("추천 개수 오류");Set<Integer> seen=new HashSet<>();List<JSONObject> result=new ArrayList<>();for(int i=0;i<ids.length();i++){Object v=ids.get(i);if(!(v instanceof Number)||((Number)v).doubleValue()!=((Number)v).intValue())throw new IllegalArgumentException("추천 ID 오류");int id=((Number)v).intValue();if(id<0||id>=candidates.size()||!seen.add(id))throw new IllegalArgumentException("추천 ID 오류");result.add(candidates.get(id));}return result;}
}
