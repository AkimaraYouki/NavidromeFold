package dev.navifold.player;

import org.json.*;
import java.util.*;

/** A bounded retrieval plan. Scores describe tags, never measured acoustic properties. */
final class MoodIntent {
    final String mood,language;
    final List<String> genres,terms;
    final int minBpm,maxBpm;
    MoodIntent(String mood,String language,List<String> genres,List<String> terms,int min,int max){this.mood=Arrays.asList("uplifting","calm","energetic","melancholic","any").contains(mood)?mood:"any";this.language=language;this.genres=genres;this.terms=terms;minBpm=Math.max(0,Math.min(300,min));maxBpm=max<=0?0:Math.max(minBpm,Math.min(300,max));}
    static MoodIntent parse(String response)throws Exception{
        JSONObject j=new JSONObject(response.trim());return new MoodIntent(j.optString("mood","any"),shortValue(j.optString("language")),strings(j.optJSONArray("genres"),6),strings(j.optJSONArray("terms"),8),j.optInt("minBpm"),j.optInt("maxBpm"));
    }
    static String shortValue(String s){return s.trim().substring(0,Math.min(80,s.trim().length()));}
    private static List<String> strings(JSONArray a,int limit){List<String> out=new ArrayList<>();if(a!=null)for(int i=0;i<Math.min(limit,a.length());i++){Object v=a.opt(i);if(v instanceof String&&!((String)v).trim().isEmpty())out.add(shortValue((String)v));}return out;}
    String json()throws Exception{return new JSONObject().put("mood",mood).put("language",language).put("genres",new JSONArray(genres)).put("terms",new JSONArray(terms)).put("minBpm",minBpm).put("maxBpm",maxBpm).toString();}
    static MoodIntent local(String query){
        String q=query.toLowerCase(Locale.ROOT);int calm=last(q,"차분","공부","집중","잔잔","study","calm","focus","수면","sleep"),up=last(q,"신나","활기","기분전환","운동","upbeat","energetic","workout");
        String mood=calm>=0&&calm>up?"calm":up>=0?"uplifting":last(q,"슬픈 노래","우울한 노래","sad songs","melancholic")>=0?"melancholic":"any";
        List<String> terms=new ArrayList<>();if(mood.equals("any"))for(String term:q.split("[\\s,，.!?]+"))if(term.length()>1&&!Arrays.asList("노래","틀어줘","추천","음악","싶어","please","music","songs","play").contains(term)&&terms.size()<8)terms.add(shortValue(term));
        return new MoodIntent(mood,"",Collections.emptyList(),terms,0,0);
    }
    private static int last(String text,String... words){int at=-1;for(String w:words)at=Math.max(at,text.lastIndexOf(w));return at;}
    double score(JSONObject song){
        if(!language.isEmpty()){
            JSONObject tagged=new JSONObject();try{tagged.put("language",language);}catch(Exception ignored){}
            Set<String> expected=FlowPolicy.languages(tagged),actual=FlowPolicy.languages(song);if(!actual.isEmpty()&&!FlowPolicy.overlaps(expected,actual))return -1;
        }
        String g=FlowPolicy.norm(SongMetadata.names(song,"genres","genre")),text=(song.optString("title")+" "+song.optString("artist")+" "+song.optString("album")+" "+g).toLowerCase(Locale.ROOT);
        double score=0;for(String genre:genres)if(!FlowPolicy.norm(genre).isEmpty()&&g.contains(FlowPolicy.norm(genre)))score+=12;
        for(String term:terms)if(text.contains(term.toLowerCase(Locale.ROOT)))score+=8;
        String[] preferred=mood.equals("calm")?new String[]{"ambient","classical","piano","lofi","acoustic","jazz","chill"}:mood.equals("uplifting")||mood.equals("energetic")?new String[]{"dance","pop","funk","disco","electronic","house","edm"}:mood.equals("melancholic")?new String[]{"ballad","blues","sad"}:new String[0];
        for(String genre:preferred)if(g.contains(genre)){score+=5;break;}
        double bpm=song.optDouble("bpm",0);if(bpm>0){if(minBpm>0||maxBpm>0){if(bpm>=minBpm&&(maxBpm==0||bpm<=maxBpm))score+=4;}else if(mood.equals("calm")&&bpm<=95||!mood.equals("calm")&&!mood.equals("any")&&!mood.equals("melancholic")&&bpm>=105)score+=2;}
        if(song.optInt("aiConfidence")>=60){JSONArray moods=song.optJSONArray("aiMoods");if(moods!=null)for(int i=0;i<moods.length();i++){String m=moods.optString(i);boolean calm=m.equals("calm")||m.equals("focus")||m.equals("relaxing"),up=m.equals("uplifting")||m.equals("energetic");if(m.equals(mood)||mood.equals("calm")&&calm||(mood.equals("uplifting")||mood.equals("energetic"))&&up){score+=10;break;}}}
        return score;
    }
}
