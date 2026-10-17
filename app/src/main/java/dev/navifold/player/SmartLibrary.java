package dev.navifold.player;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** On-device catalog and account-scoped listening evidence; no audio or API URLs. */
final class SmartLibrary extends SQLiteOpenHelper {
    private static SmartLibrary instance;
    static synchronized SmartLibrary get(Context c) {
        if (instance == null) instance = new SmartLibrary(c.getApplicationContext());
        return instance;
    }
    private SmartLibrary(Context c) { super(c, "smart-library.db", null, 2); setWriteAheadLoggingEnabled(true); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tracks(scope TEXT,id TEXT,json TEXT,stamp INTEGER,PRIMARY KEY(scope,id))");
        db.execSQL("CREATE TABLE taste(scope TEXT,id TEXT,completes INTEGER DEFAULT 0,skips INTEGER DEFAULT 0,vote INTEGER DEFAULT 0,audible INTEGER DEFAULT 0,last INTEGER DEFAULT 0,PRIMARY KEY(scope,id))");
        db.execSQL("CREATE TABLE events(n INTEGER PRIMARY KEY AUTOINCREMENT,scope TEXT,id TEXT,kind TEXT,audible INTEGER,at INTEGER)");
        db.execSQL("CREATE INDEX events_scope ON events(scope,n)");
        db.execSQL("CREATE TABLE enrichment(scope TEXT,album TEXT,json TEXT,checked INTEGER,PRIMARY KEY(scope,album))");
        createNotes(db);
    }
    private static void createNotes(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS album_notes(scope TEXT,id TEXT,fingerprint TEXT,text TEXT,updated INTEGER,PRIMARY KEY(scope,id))");}
    public void onUpgrade(SQLiteDatabase db,int from,int to) {if(from<2)createNotes(db);}
    synchronized String albumNote(String scope,String id,String fingerprint){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT text,updated FROM album_notes WHERE scope=? AND id=? AND fingerprint=?",new String[]{scope,id,fingerprint})){return c.moveToFirst()&&System.currentTimeMillis()-c.getLong(1)<30L*86400000?c.getString(0):"";}
    }
    synchronized void saveAlbumNote(String scope,String id,String fingerprint,String text){ContentValues v=new ContentValues();v.put("scope",scope);v.put("id",id);v.put("fingerprint",fingerprint);v.put("text",text);v.put("updated",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("album_notes",null,v,SQLiteDatabase.CONFLICT_REPLACE);}

    synchronized void put(String scope, JSONObject song, long stamp) {
        String id = song.optString("id"); if (scope.isEmpty() || id.isEmpty()) return;
        JSONObject safe = SongMetadata.safe(song);
        try (Cursor c = getReadableDatabase().rawQuery("SELECT json FROM tracks WHERE scope=? AND id=?",new String[]{scope,id})) {
            if(c.moveToFirst()) try { safe = SongMetadata.merge(new JSONObject(c.getString(0)), safe); } catch(JSONException ignored) { }
        }
        ContentValues v = new ContentValues();v.put("scope",scope);v.put("id",id);v.put("json",safe.toString());v.put("stamp",stamp);
        getWritableDatabase().insertWithOnConflict("tracks",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    void index(Api api,String scope,BooleanSupplier valid) throws Exception {
        long stamp=System.currentTimeMillis(); Set<String> ids=new HashSet<>(); Map<String,JSONObject> albums=new HashMap<>(); int offset=0;
        while(valid.getAsBoolean()) {
            List<JSONObject> page=Library.array(api.call("search3","query","","songCount","500","songOffset",String.valueOf(offset),"artistCount","0","albumCount","0").optJSONObject("searchResult3"),"song");
            if(page.isEmpty()) break;
            int added=0;
            for(JSONObject song:page) { if(!valid.getAsBoolean()) return; if(ids.add(song.optString("id"))) { String albumId=song.optString("albumId");
                    if(!albumId.isEmpty()&&!albums.containsKey(albumId)&&(label(song).isEmpty()||SongMetadata.names(song,"genres","genre").isEmpty())) {
                        JSONObject tags=new JSONObject();
                        try {JSONObject album=api.call("getAlbum","id",albumId).optJSONObject("album");if(album!=null)for(String key:new String[]{"recordLabels","label","genre","genres","musicBrainzId"})if(album.has(key))tags.put(key.equals("musicBrainzId")?"musicBrainzReleaseId":key,album.get(key));}catch(Exception ignored){ }
                        albums.put(albumId,tags);
                    }
                    JSONObject tags=albums.get(albumId);if(tags!=null){JSONObject enriched=SongMetadata.merge(tags,song);song=enriched;}
                    put(scope,song,stamp); added++; } }
            if(added==0) throw new java.io.IOException("서버가 같은 곡 목록을 반복했습니다");
            offset+=page.size();
        }
        if(valid.getAsBoolean()) getWritableDatabase().delete("tracks","scope=? AND stamp<?",new String[]{scope,String.valueOf(stamp)});
    }
    synchronized void event(String scope,JSONObject song,String kind,long audible) {
        if(scope.isEmpty()||song.optString("id").isEmpty()) return;
        put(scope,song,System.currentTimeMillis());String id=song.optString("id");SQLiteDatabase db=getWritableDatabase();
        ContentValues base=new ContentValues();base.put("scope",scope);base.put("id",id);db.insertWithOnConflict("taste",null,base,SQLiteDatabase.CONFLICT_IGNORE);
        db.execSQL("UPDATE taste SET completes=completes+?,skips=skips+?,audible=audible+?,last=? WHERE scope=? AND id=?",new Object[]{kind.equals("complete")?1:0,kind.equals("manual-skip")?1:0,audible,System.currentTimeMillis(),scope,id});
        ContentValues v=new ContentValues();v.put("scope",scope);v.put("id",id);v.put("kind",kind);v.put("audible",audible);v.put("at",System.currentTimeMillis());db.insert("events",null,v);
        db.execSQL("DELETE FROM events WHERE scope=? AND n NOT IN (SELECT n FROM events WHERE scope=? ORDER BY n DESC LIMIT 1000)",new Object[]{scope,scope});
    }
    synchronized void vote(String scope,JSONObject song,int vote) {
        event(scope,song,"vote",0);
        getWritableDatabase().execSQL("UPDATE taste SET vote=? WHERE scope=? AND id=?",new Object[]{vote,scope,song.optString("id")});
    }
    synchronized boolean negative(String scope,String id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT skips,completes,vote FROM taste WHERE scope=? AND id=?",new String[]{scope,id})) {
            return c.moveToFirst() && SmartPolicy.strongNegative(c.getInt(0),c.getInt(1),c.getInt(2));
        }
    }
    static String label(JSONObject song) {return SongMetadata.names(song,"recordLabels","label");}
    static final class Features {
        final String artist,albumArtist,album,genre,label;final int year;final double bpm;
        Features(JSONObject song){artist=song.optString("artist");albumArtist=song.optString("albumArtist");album=song.optString("album");genre=song.optString("genre");label=label(song);year=song.optInt("year");bpm=song.optDouble("bpm");}
    }
    private static double match(String a,String b,double weight){return !a.isEmpty()&&a.equalsIgnoreCase(b)?weight:0;}
    static double affinity(JSONObject a,JSONObject b){return affinity(new Features(a),new Features(b));}
    static double affinity(Features a,Features b){double score=match(a.artist,b.artist,2)+match(a.albumArtist,b.albumArtist,2)+match(a.album,b.album,1)+match(a.genre,b.genre,3)+match(a.label,b.label,1.5);if(a.year>0&&b.year>0)score+=Math.max(0,1-Math.abs(a.year-b.year)/20.0);if(a.bpm>0&&b.bpm>0)score+=Math.max(0,2-Math.abs(a.bpm-b.bpm)/15.0);return score;}
    static final class Ranked {final JSONObject song;final double score;Ranked(JSONObject song,double score){this.song=song;this.score=score;}}
    synchronized List<JSONObject> candidates(String scope,JSONObject seed,Set<String> excluded,int limit) throws Exception {return candidates(scope,seed,excluded,limit,new JSONObject(),2);}
    synchronized List<JSONObject> candidates(String scope,JSONObject seed,Set<String> excluded,int limit,JSONObject anchor,int mode) throws Exception {
        List<JSONObject> preferred=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT t.json FROM tracks t JOIN taste f ON t.scope=f.scope AND t.id=f.id WHERE t.scope=? AND (f.vote>0 OR f.completes>f.skips) ORDER BY f.last DESC LIMIT 12",new String[]{scope})) {while(c.moveToNext())preferred.add(new JSONObject(c.getString(0)));}
        Features seedFeatures=new Features(seed);List<Features> preferredFeatures=new ArrayList<>();for(JSONObject p:preferred)preferredFeatures.add(new Features(p));FlowPolicy.Matcher matcher=new FlowPolicy.Matcher(anchor,mode);long rankedAt=System.currentTimeMillis();
        Random random=new Random();PriorityQueue<Ranked> best=new PriorityQueue<>(Comparator.comparingDouble(x->x.score));
        try(Cursor c=getReadableDatabase().rawQuery("SELECT t.id,t.json,COALESCE(f.completes,0),COALESCE(f.skips,0),COALESCE(f.vote,0),COALESCE(f.last,0) FROM tracks t LEFT JOIN taste f ON t.scope=f.scope AND t.id=f.id WHERE t.scope=?",new String[]{scope})) {
            while(c.moveToNext()) {
                if(excluded.contains(c.getString(0))||SmartPolicy.strongNegative(c.getInt(3),c.getInt(2),c.getInt(4))) continue;
                JSONObject song=new JSONObject(c.getString(1));if(!matcher.allows(song))continue;Features features=new Features(song);double taste=0;for(Features p:preferredFeatures)taste+=affinity(features,p)/Math.max(1,preferredFeatures.size());
                double score=affinity(seedFeatures,features)+taste+Math.min(3,c.getInt(2)*0.3)-Math.min(4,c.getInt(3))+c.getInt(4)*3+random.nextDouble()*3;
                if(rankedAt-c.getLong(5)<3600000)score-=5;
                best.add(new Ranked(song,score));if(best.size()>limit*5)best.poll();
            }
        }
        List<Ranked> ordered=new ArrayList<>(best);ordered.sort((a,b)->Double.compare(b.score,a.score));List<JSONObject> out=new ArrayList<>();Map<String,Integer> artists=new HashMap<>();Set<String> selected=new HashSet<>();
        for(Ranked x:ordered){String artist=Library.artist(x.song);if(artists.getOrDefault(artist,0)>=4)continue;out.add(x.song);selected.add(x.song.optString("id"));artists.put(artist,artists.getOrDefault(artist,0)+1);if(out.size()==limit)break;}
        // A small/single-artist library remains usable without inventing songs.
        if(out.size()<Math.min(10,limit))for(Ranked x:ordered)if(selected.add(x.song.optString("id"))){out.add(x.song);if(out.size()==Math.min(10,limit))break;}
        return out;
    }
    synchronized JSONObject song(String scope,String id) throws Exception {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT json FROM tracks WHERE scope=? AND id=?",new String[]{scope,id})){return c.moveToFirst()?new JSONObject(c.getString(0)):new JSONObject();}
    }
    synchronized String context(String scope) throws Exception {
        JSONArray history=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT t.json,e.kind,COALESCE(f.vote,0) FROM events e JOIN tracks t ON e.scope=t.scope AND e.id=t.id LEFT JOIN taste f ON f.scope=e.scope AND f.id=e.id WHERE e.scope=? AND e.kind IN ('complete','manual-skip','vote') ORDER BY e.n DESC LIMIT 8",new String[]{scope})) {
            while(c.moveToNext()) {JSONObject song=new JSONObject(c.getString(0));history.put(new JSONArray().put(RecommendationBatch.shortText(song.optString("title"))).put(RecommendationBatch.shortText(song.optString("artist"))).put(c.getString(1).equals("vote")?(c.getInt(2)>0?"like":c.getInt(2)<0?"dislike":"neutral"):c.getString(1)));}
        }
        return "Recent listening [title,artist,event]: "+history;
    }
    synchronized JSONObject enrichment(String scope,String album) throws Exception {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT json,checked FROM enrichment WHERE scope=? AND album=?",new String[]{scope,album})) {if(c.moveToFirst()&&System.currentTimeMillis()-c.getLong(1)<30L*86400000)return new JSONObject(c.getString(0));}return null;
    }
    synchronized void cacheEnrichment(String scope,String album,JSONObject data) {
        ContentValues v=new ContentValues();v.put("scope",scope);v.put("album",album);v.put("json",data.toString());v.put("checked",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("enrichment",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    synchronized List<JSONObject> search(String scope,String query) throws Exception {
        List<JSONObject> result=new ArrayList<>();String term=query.toLowerCase(Locale.ROOT);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT json FROM tracks WHERE scope=?",new String[]{scope})) {
            while(c.moveToNext()) {JSONObject song=new JSONObject(c.getString(0));String text=song.optString("title")+" "+SongMetadata.names(song,"artists","artist")+" "+song.optString("album")+" "+SongMetadata.names(song,"genres","genre")+" "+label(song);if(text.toLowerCase(Locale.ROOT).contains(term))result.add(song);}
        }
        return result;
    }
    synchronized String summary(String scope) {
        long tracks=0,events=0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tracks WHERE scope=?",new String[]{scope})){if(c.moveToFirst())tracks=c.getLong(0);}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM events WHERE scope=?",new String[]{scope})){if(c.moveToFirst())events=c.getLong(0);}
        return tracks+" TRACKS · "+events+" EVENTS";
    }
    synchronized void clear(String scope) { for(String table:new String[]{"tracks","taste","events","enrichment","album_notes"})getWritableDatabase().delete(table,"scope=?",new String[]{scope}); }
    synchronized Set<String> recent(String scope) {
        Set<String> out=new HashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM events WHERE scope=? ORDER BY n DESC LIMIT 20",new String[]{scope})){while(c.moveToNext())out.add(c.getString(0));}return out;
    }
}
