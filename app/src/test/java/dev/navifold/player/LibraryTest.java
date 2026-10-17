package dev.navifold.player;
import org.junit.Test;
import org.json.JSONObject;
import java.util.*;
import static org.junit.Assert.*;

public class LibraryTest {
    JSONObject song(String id,String path,int disc,int track) throws Exception {
        return new JSONObject().put("id",id).put("path",path).put("title",id).put("discNumber",disc).put("track",track);
    }
    @Test public void indexesPhysicalFoldersAndKeepsMusicRootsSeparate() throws Exception {
        Library.Index index=new Library.Index();
        index.add("1","Music",song("same","Jazz/Album/01.flac",1,1));
        index.add("2","Archive",song("same","Jazz/Album/01.flac",1,1));
        assertEquals(2,index.root.count);assertEquals(2,index.root.children.size());
        assertEquals(1,index.nodes.get("1/Jazz/Album").songs.size());
        assertEquals("모든 음악 폴더 / Archive / Jazz / Album",index.nodes.get("2/Jazz/Album").breadcrumb());
    }
    @Test public void missingPathsAreReportedAndUnsafePathsDoNotCreateFolders() throws Exception {
        Library.Index index=new Library.Index();
        index.add("1","Music",song("missing","",1,1));index.add("1","Music",song("unsafe","../escape/file.mp3",1,1));
        assertEquals(2,index.missingPaths);assertEquals(0,index.root.count);assertFalse(index.nodes.containsKey("1/.."));
    }
    @Test public void duplicatePagesCannotDuplicateSongs() throws Exception {
        Library.Index index=new Library.Index();JSONObject song=song("a","Artist/Album/01.mp3",1,1);
        assertTrue(index.add("1","Music",song));assertFalse(index.add("1","Music",song));assertEquals(1,index.root.count);
    }
    @Test public void recursiveFolderQueuePreservesDiscAndTrackOrder() throws Exception {
        Library.Index index=new Library.Index();
        index.add("1","Music",song("disc2","Artist/Album/b.flac",2,1));
        index.add("1","Music",song("track2","Artist/Album/c.flac",1,2));
        index.add("1","Music",song("track1","Artist/Album/a.flac",1,1));
        List<JSONObject> songs=index.nodes.get("1/Artist").allSongs();
        assertEquals(Arrays.asList("track1","track2","disc2"),Arrays.asList(songs.get(0).getString("id"),songs.get(1).getString("id"),songs.get(2).getString("id")));
    }
    @Test public void missingTrackNumbersSortAfterNumberedTracks() throws Exception {
        List<JSONObject> songs=new ArrayList<>(Arrays.asList(song("missing","a.flac",0,0),song("numbered","b.flac",1,3)));
        songs.sort(Library.TRACK_ORDER);assertEquals("numbered",songs.get(0).getString("id"));
    }
    @Test public void sameAlbumTitleNeverMergesDifferentServerAlbumIds() throws Exception {
        JSONObject a=song("a","a.flac",1,1).put("albumId","edition1").put("album","Same");
        JSONObject b=song("b","b.flac",1,1).put("albumId","edition2").put("album","Same");
        JSONObject c=song("c","c.flac",1,2).put("albumId","edition1").put("album","Same");
        assertEquals(2,Library.albumsIn(Arrays.asList(a,b,c)).size());
    }
}
