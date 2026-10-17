package dev.navifold.player;
import org.json.JSONObject;
import java.text.Collator;
import java.text.Normalizer;
import java.util.*;
final class MediaSort {
 static final String[] VALUES={"newest","alphabeticalByName","alphabeticalByArtist","byYear","byYearAscending","track","frequent"};
 static final String[] LABELS={"최근 추가 · Recently added","제목 · 가나다 / ABC","아티스트 · 가나다 / ABC","발매일 · 최신순","발매일 · 오래된순","디스크 / 트랙 번호","많이 들은 순"};
 static String value(JSONObject j,String... keys){for(String k:keys)if(j.has(k)&&!j.isNull(k)){String s=j.optString(k,"").trim();if(!s.isEmpty())return s;}return "";}
 static String name(String s,boolean ignore){s=Normalizer.normalize(s,Normalizer.Form.NFKC).trim();return ignore?s.replaceFirst("(?i)^(the|an|a)\\s+",""):s;}
 static int group(String s){if(s.isEmpty())return 5;int c=s.codePointAt(0);if(Character.isDigit(c))return 0;if(c>=0xac00&&c<=0xd7af||c>=0x1100&&c<=0x11ff||c>=0x3130&&c<=0x318f)return 1;if(c>='A'&&c<='Z'||c>='a'&&c<='z')return 2;return Character.isLetter(c)?3:4;}
 static Comparator<JSONObject> comparator(String mode,boolean ignore){Collator collator=Collator.getInstance(Locale.KOREAN);collator.setStrength(Collator.PRIMARY);Comparator<String> names=(a,b)->{a=name(a,ignore);b=name(b,ignore);int d=Integer.compare(group(a),group(b));return d!=0?d:collator.compare(a,b);};Comparator<JSONObject> title=(a,b)->names.compare(value(a,"title","name"),value(b,"title","name"));Comparator<JSONObject> c;
 switch(mode){
 case "alphabeticalByArtist":c=(a,b)->names.compare(value(a,"albumArtist","artist"),value(b,"albumArtist","artist"));break;
 case "album":c=(a,b)->names.compare(value(a,"album"),value(b,"album"));break;
 case "newest":c=(a,b)->compareMissing(date(a,true),date(b,true),true);break;
 case "recent":c=(a,b)->compareMissing(value(a,"played"),value(b,"played"),true);break;
 case "byYear":case "byYearAscending":c=(a,b)->compareMissing(date(a,false),date(b,false),mode.equals("byYear"));break;
 case "frequent":c=(a,b)->Long.compare(b.optLong("playCount",b.optLong("play_count",0)),a.optLong("playCount",a.optLong("play_count",0)));break;
 case "track":c=Comparator.comparingInt((JSONObject a)->number(a,"discNumber","disk_number",1)).thenComparingInt(a->number(a,"track","track_number",Integer.MAX_VALUE));break;
 default:c=title;
 }return c.thenComparing(title).thenComparing(a->a.optString("id"));}
 static int number(JSONObject j,String a,String b,int fallback){int n=j.optInt(a,j.optInt(b,fallback));return n>0?n:fallback;}
 static String date(JSONObject j,boolean added){if(added)return value(j,"created","created_at","added_at");String date=value(j,"releaseDate","release_date");JSONObject d=j.optJSONObject("releaseDate");if(d!=null)date="";else d=j.optJSONObject("originalReleaseDate");if(date.isEmpty()&&d!=null&&d.optInt("year")>0)date=String.format(Locale.ROOT,"%04d-%02d-%02d",d.optInt("year"),Math.max(1,d.optInt("month")),Math.max(1,d.optInt("day")));if(date.isEmpty()&&j.optInt("year")>0)date=String.format(Locale.ROOT,"%04d-01-01",j.optInt("year"));return date;}
 static int compareMissing(String a,String b,boolean descending){if(a.isEmpty()||b.isEmpty())return a.isEmpty()?(b.isEmpty()?0:1):-1;return descending?b.compareTo(a):a.compareTo(b);}
}
