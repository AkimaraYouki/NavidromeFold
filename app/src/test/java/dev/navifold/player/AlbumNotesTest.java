package dev.navifold.player;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
public class AlbumNotesTest {
 @Test public void promptContainsOnlyLimitedMusicMetadata() throws Exception {
  JSONObject song=new JSONObject().put("id","private-id").put("path","private-path").put("url","https://private-server").put("title","Track");
  JSONObject album=new JSONObject().put("album","Album").put("artist","Artist").put("genre","Jazz").put("label","Label");
  String prompt=AlbumNotes.prompt(album,Collections.singletonList(song));assertFalse(prompt.contains("private"));assertTrue(prompt.contains("Jazz"));assertTrue(prompt.contains("Label"));
 }
 @Test public void cacheChangesWhenFactsChange() throws Exception {JSONObject album=new JSONObject().put("album","A");List<JSONObject> songs=Collections.singletonList(new JSONObject().put("title","Track"));String first=AlbumNotes.fingerprint(album,songs);album.put("year",2026);assertNotEquals(first,AlbumNotes.fingerprint(album,songs));}
 @Test public void limitsTitleCountAndSummarySize() throws Exception {List<JSONObject> songs=new ArrayList<>();for(int i=0;i<100;i++)songs.add(new JSONObject().put("title","Track "+i));JSONObject prompt=new JSONObject(AlbumNotes.prompt(new JSONObject(),songs));assertEquals(30,prompt.getJSONArray("trackTitles").length());assertEquals(100,prompt.getInt("trackCount"));assertEquals(240,AlbumNotes.clean(String.join("",Collections.nCopies(300,"a"))).length());assertEquals("one two",AlbumNotes.clean("one\n two"));}
}
