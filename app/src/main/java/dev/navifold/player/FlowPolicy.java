package dev.navifold.player;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.*;
/** Deterministic guard shared by local and remote recommendations. No artist-country guessing. */
final class FlowPolicy {
 static final String[] LABELS={"엄격하게","자연스럽게","다양하게"};
 private static final java.util.regex.Pattern SEPARATORS=java.util.regex.Pattern.compile("[\\s_-]+");
 private static final java.util.regex.Pattern LIST=java.util.regex.Pattern.compile("[,;/|]");
 static String norm(String s){return SEPARATORS.matcher(Normalizer.normalize(s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim()).replaceAll("");}
 static Set<String> genres(JSONObject s){Set<String> out=new HashSet<>();for(String part:LIST.split(SongMetadata.names(s,"genres","genre")+","+s.optString("genre"))){String g=norm(part);if(g.equals("japanesepop"))g="jpop";if(g.equals("koreanpop"))g="kpop";if(g.equals("japaneserock"))g="jrock";if(!g.isEmpty())out.add(g);}return out;}
 static Set<String> scenes(JSONObject s){return scenes(s,genres(s));}
 private static Set<String> scenes(JSONObject s,Set<String> genre){Set<String> out=new HashSet<>();for(String g:genre){if(g.startsWith("jpop")||g.startsWith("jrock")||g.equals("japanesepop")||g.equals("japaneserock"))out.add("jp");if(g.startsWith("kpop")||g.startsWith("krock")||g.equals("koreanpop")||g.equals("koreanrock"))out.add("kr");if(g.equals("cpop")||g.equals("mandopop")||g.equals("cantopop"))out.add("cn");}String explicit=norm(s.optString("musicScene"));if(!explicit.isEmpty())out.add(explicit);return out;}
 static Set<String> languages(JSONObject s){Set<String> out=new HashSet<>();for(String part:LIST.split(SongMetadata.names(s,"languages","language"))){String x=norm(part);switch(x){case "en":case "english":x="eng";break;case "ja":case "japanese":x="jpn";break;case "ko":case "korean":x="kor";break;case "zh":case "chi":case "chinese":x="zho";break;}if(!x.isEmpty()&&!x.equals("und")&&!x.equals("unknown"))out.add(x);}return out;}
 static String family(String g){if(g.equals("pop")||g.endsWith("pop"))return "pop";if(g.equals("rock")||g.endsWith("rock"))return "rock";if(g.equals("hiphop")||g.equals("rap"))return "hiphop";if(g.equals("r&b")||g.equals("rnb")||g.equals("rhythmandblues"))return "rnb";if(g.equals("electronic")||g.equals("electronica")||g.equals("edm")||g.equals("house")||g.equals("techno"))return "electronic";return g;}
 static boolean overlaps(Set<String>a,Set<String>b){return !Collections.disjoint(a,b);}
 static boolean sameArtist(JSONObject a,JSONObject b){String artist=norm(a.optString("albumArtist",a.optString("artist")));return !artist.isEmpty()&&artist.equals(norm(b.optString("albumArtist",b.optString("artist"))));}
 static final class Profile {
  final Set<String> genres,scenes,languages,families;final String artist;
  Profile(JSONObject song){genres=genres(song);scenes=scenes(song,genres);languages=languages(song);families=new HashSet<>();for(String g:genres)families.add(family(g));artist=norm(song.optString("albumArtist",song.optString("artist")));}
 }
 static final class Matcher {
  final Profile anchor;final int mode;
  Matcher(JSONObject song,int mode){this.mode=mode;anchor=mode==2?null:new Profile(song);}
  boolean allows(JSONObject song){return mode==2||allows(new Profile(song));}
  private boolean allows(Profile candidate){
   if(!anchor.languages.isEmpty()&&!candidate.languages.isEmpty()&&!overlaps(anchor.languages,candidate.languages))return false;
   if(!anchor.scenes.equals(candidate.scenes)&&(!anchor.scenes.isEmpty()||!candidate.scenes.isEmpty()))return false;
   if(anchor.genres.isEmpty())return mode!=0&&!anchor.artist.isEmpty()&&anchor.artist.equals(candidate.artist);
   if(candidate.genres.isEmpty())return false;
   if(overlaps(anchor.genres,candidate.genres))return true;
   return mode!=0&&overlaps(anchor.families,candidate.families);
  }
 }
 static boolean allows(JSONObject anchor,JSONObject candidate,int mode){return new Matcher(anchor,mode).allows(candidate);}
 static boolean shouldAnchor(JSONObject anchor,JSONObject song,boolean suggested,boolean manualSeek){
  if(song.optString("id").isEmpty())return false;if(suggested)return false;if(manualSeek)return true;
  return anchor.optString("id").isEmpty()||!song.optString("albumId").equals(anchor.optString("albumId"))||song.optString("albumId").isEmpty()&&!song.optString("id").equals(anchor.optString("id"));
 }
 static List<JSONObject> filter(List<JSONObject> songs,JSONObject anchor,int mode){List<JSONObject> out=new ArrayList<>();Matcher matcher=new Matcher(anchor,mode);for(JSONObject song:songs)if(matcher.allows(song))out.add(song);return out;}
 static JSONObject anchorTags(JSONObject stored,JSONObject original){JSONObject source=SongMetadata.safe(original);for(String key:new String[]{"genre","language","musicScene"})if(source.optString(key).trim().isEmpty())source.remove(key);for(String key:new String[]{"genres","languages"})if(source.optJSONArray(key)!=null&&source.optJSONArray(key).length()==0)source.remove(key);return SongMetadata.merge(stored,source);}
 static String constraint(JSONObject anchor,int mode){return "Maintain the USER-SELECTED anchor, not a drifting chain of suggested tracks. Mode: "+LABELS[Math.max(0,Math.min(2,mode))]+". Anchor genre="+genres(anchor)+", explicit scene="+scenes(anchor)+", tagged language="+languages(anchor)+". Unknown language is unknown; never infer language or nationality from artist names. Do not broaden constraints when candidates are scarce.";}
}
