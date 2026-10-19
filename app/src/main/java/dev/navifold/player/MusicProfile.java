package dev.navifold.player;

import org.json.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Inferred descriptors live separately from source tags. No audio-analysis claim. */
final class MusicProfile {
    static final String[] KEYS={"aiMoods","aiEnergy","aiInstrumental","aiConfidence"};
    static final Set<String> MOODS=new HashSet<>(Arrays.asList("calm","uplifting","energetic","melancholic","focus","relaxing","romantic","dark","neutral"));
    static String fingerprint(JSONObject s){
        JSONArray parts=new JSONArray();for(String k:new String[]{"id","title","artist","albumArtist","album","year","genre","bpm","language","duration"})parts.put(s.optString(k));parts.put(SongMetadata.names(s,"genres","genre"));
        try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(parts.toString().getBytes(StandardCharsets.UTF_8));char[] out=new char[hash.length*2];String hex="0123456789abcdef";for(int i=0;i<hash.length;i++){out[i*2]=hex.charAt((hash[i]&255)>>>4);out[i*2+1]=hex.charAt(hash[i]&15);}return new String(out);}catch(Exception e){throw new IllegalStateException(e);}
    }
    static boolean fresh(JSONObject song,String fingerprint,String json,long updated){
        if(fingerprint==null||json==null||updated<=0||!fingerprint(song).equals(fingerprint))return false;try{JSONObject p=new JSONObject(json);long ttl=p.optInt("confidence")>=60?90L*86400000:2L*86400000;return p.optInt("version")==1&&System.currentTimeMillis()-updated<ttl;}catch(Exception e){return false;}
    }
    static JSONObject attach(JSONObject song,String fingerprint,String json,long updated){
        JSONObject out=SongMetadata.safe(song);for(String k:KEYS)out.remove(k);if(!fresh(out,fingerprint,json,updated))return out;
        try{JSONObject p=new JSONObject(json);out.put("aiMoods",p.getJSONArray("moods")).put("aiEnergy",p.optInt("energy",-1)).put("aiInstrumental",p.optString("instrumental","unknown")).put("aiConfidence",p.optInt("confidence"));}catch(Exception ignored){}return out;
    }
    static List<JSONObject> parse(String response,int count)throws Exception{
        JSONArray rows=new JSONArray(response.trim());if(rows.length()!=count)throw new IllegalArgumentException("분석 결과 곡 수 불일치");List<JSONObject> out=new ArrayList<>(Collections.nCopies(count,null));
        for(int i=0;i<rows.length();i++){JSONObject p=rows.getJSONObject(i);Object id=p.opt("id");if(!(id instanceof Number)||((Number)id).doubleValue()!=((Number)id).intValue())throw new IllegalArgumentException("분석 ID 오류");int n=((Number)id).intValue();if(n<0||n>=count||out.get(n)!=null)throw new IllegalArgumentException("분석 ID 오류");
            int confidence=p.getInt("confidence"),energy=p.getInt("energy");if(confidence<0||confidence>100||energy<-1||energy>100)throw new IllegalArgumentException("분석 범위 오류");String instrumental=p.getString("instrumental");if(!Arrays.asList("yes","no","unknown").contains(instrumental))throw new IllegalArgumentException("보컬 정보 오류");
            JSONArray moods=p.getJSONArray("moods"),safe=new JSONArray();if(moods.length()>5)throw new IllegalArgumentException("분위기 개수 오류");Set<String> seen=new HashSet<>();for(int j=0;j<moods.length();j++){String m=moods.getString(j);if(!MOODS.contains(m))throw new IllegalArgumentException("분위기 값 오류");if(seen.add(m))safe.put(m);}
            out.set(n,new JSONObject().put("version",1).put("moods",safe).put("energy",energy).put("instrumental",instrumental).put("confidence",confidence));
        }return out;
    }
    static String prompt(List<JSONObject> tracks)throws Exception{
        JSONArray albums=new JSONArray(),rows=new JSONArray();Map<String,Integer> indices=new LinkedHashMap<>();List<JSONObject> sources=new ArrayList<>();
        for(int i=0;i<tracks.size();i++){
            JSONObject s=tracks.get(i);String key=albumKey(s);Integer album=indices.get(key);
            if(album==null){album=indices.size();indices.put(key,album);sources.add(s);albums.put(new JSONArray().put(album).put(RecommendationBatch.shortText(s.optString("album"))).put(RecommendationBatch.shortText(Library.artist(s))).put(s.optInt("year")).put(RecommendationBatch.shortText(SongMetadata.names(s,"genres","genre"))));}
            JSONObject base=sources.get(album);String artist=Library.artist(s),genre=SongMetadata.names(s,"genres","genre");
            rows.put(new JSONArray().put(i).put(album).put(RecommendationBatch.shortText(s.optString("title"))).put(artist.equals(Library.artist(base))?"":RecommendationBatch.shortText(artist)).put(genre.equals(SongMetadata.names(base,"genres","genre"))?"":RecommendationBatch.shortText(genre)).put(s.optDouble("bpm",0)));
        }
        return "Albums [albumAlias,title,artist,year,genre]: "+albums+"\nTracks [id,albumAlias,title,artistOverride,genreOverride,taggedBpm]: "+rows+"\nEmpty overrides inherit the album fields. IDs are local aliases only.";
    }
    static String albumKey(JSONObject s){return s.optString("albumId").isEmpty()?s.optString("albumArtist",s.optString("artist"))+"\n"+s.optString("album"):s.optString("albumId");}
    static String label(String mood){switch(mood){case "calm":return "차분함";case "uplifting":return "기분 전환";case "energetic":return "활기";case "melancholic":return "서정적";case "focus":return "집중";case "relaxing":return "휴식";case "romantic":return "로맨틱";case "dark":return "어두움";default:return "중립";}}
}
