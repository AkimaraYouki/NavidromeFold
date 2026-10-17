package dev.navifold.player;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SongMetadataTest {
    @Test public void onlyMusicFieldsReachThePlayer() throws Exception {
        JSONObject source=new JSONObject().put("title","Track").put("year",2024).put("password","secret").put("url","https://example/stream?t=token").put("token","private");
        JSONObject result=SongMetadata.safe(source);assertEquals("Track",result.getString("title"));assertFalse(result.has("password"));assertFalse(result.has("url"));assertFalse(result.has("token"));
    }
    @Test public void missingYearIsNotInvented() throws Exception {
        assertEquals("정보 없음",SongMetadata.year(new JSONObject()));assertEquals("정보 없음",SongMetadata.year(new JSONObject().put("year",0)));
        assertEquals("2025",SongMetadata.year(new JSONObject().put("releaseDate",new JSONObject().put("year",2025))));
    }
    @Test public void detailedReplyEnrichesSnapshotWithoutLosingExistingMetadata() throws Exception {
        JSONObject snapshot=new JSONObject().put("title","Track").put("album","Album");
        JSONObject enriched=SongMetadata.merge(snapshot,new JSONObject().put("year",2023).put("samplingRate",96000).put("bitRate",1024).put("suffix","flac"));
        Map<String,String> rows=SongMetadata.rows(enriched);assertEquals("Album",rows.get("앨범"));assertEquals("2023",rows.get("발매연도"));assertEquals("96.0 kHz",rows.get("샘플링 레이트"));assertEquals("FLAC",rows.get("원본 형식"));
    }
    @Test public void multipleArtistAndGenreTagsAreReadable() throws Exception {
        JSONObject j=new JSONObject().put("artists",new JSONArray().put(new JSONObject().put("name","Artist A")).put(new JSONObject().put("name","Artist B"))).put("genres",new JSONArray().put(new JSONObject().put("name","Jazz")));
        Map<String,String> rows=SongMetadata.rows(SongMetadata.safe(j));assertEquals("Artist A, Artist B",rows.get("아티스트"));assertEquals("Jazz",rows.get("장르"));
    }
}
