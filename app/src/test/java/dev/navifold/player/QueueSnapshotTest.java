package dev.navifold.player;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class QueueSnapshotTest {
    private QueueSnapshot.Entry track(String id) throws Exception {
        return new QueueSnapshot.Entry(new JSONObject().put("id",id).put("title","Track").put("duration",120),false,"",null);
    }
    @Test public void preservesDuplicateOccurrencesAndCurrentPosition() throws Exception {
        QueueSnapshot a = new QueueSnapshot(Arrays.asList(track("a"),track("b"),track("a")),2,42000,2,true);
        QueueSnapshot b = QueueSnapshot.decode(a.encode());
        assertEquals(3,b.entries.size());assertEquals("a",b.entries.get(2).song.getString("id"));
        assertEquals(2,b.index);assertEquals(42000,b.position);assertEquals(2,b.repeat);assertTrue(b.shuffle);
    }
    @Test public void neverPersistsAuthUrlsOrFilePaths() throws Exception {
        JSONObject song = new JSONObject().put("id","a").put("title","safe").put("uri","https://secret").put("password","secret").put("path","private/path").put("comment","private comment");
        QueueSnapshot snapshot = new QueueSnapshot(Collections.singletonList(new QueueSnapshot.Entry(song,true,"same genre",song)),0,0,0,false);
        String text=snapshot.encode();assertFalse(text.contains("secret"));assertFalse(text.contains("private"));
        QueueSnapshot restored=QueueSnapshot.decode(text);assertTrue(restored.entries.get(0).suggested);assertEquals("same genre",restored.entries.get(0).reason);assertEquals("a",restored.entries.get(0).anchor.getString("id"));
    }
    @Test public void clampsInvalidPositionIndexAndRepeat() throws Exception {
        QueueSnapshot a=new QueueSnapshot(Collections.singletonList(track("a")),50,999999,7,false);
        assertEquals(0,a.index);assertEquals(119999,a.position);assertEquals(2,a.repeat);
        assertEquals(0,new QueueSnapshot(Collections.singletonList(track("a")),-5,-200,-4,false).position);
    }
    @Test public void emptyQueueRoundTrips() throws Exception {
        QueueSnapshot b=QueueSnapshot.decode(new QueueSnapshot(Collections.emptyList(),-1,0,0,false).encode());assertTrue(b.entries.isEmpty());assertEquals(0,b.index);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsOversizedQueueInsteadOfTruncating() throws Exception { new QueueSnapshot(Collections.nCopies(QueueSnapshot.LIMIT+1,track("a")),0,0,0,false).encode(); }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnknownSchema() throws Exception {QueueSnapshot.decode("{\"version\":2,\"tracks\":[]}");}
    @Test(expected=IllegalArgumentException.class) public void rejectsMalformedTrackWithoutShiftingIndex() throws Exception {QueueSnapshot.decode("{\"version\":1,\"tracks\":[{\"song\":{\"id\":\"ok\"}},{\"song\":{}}],\"index\":1}");}
}
