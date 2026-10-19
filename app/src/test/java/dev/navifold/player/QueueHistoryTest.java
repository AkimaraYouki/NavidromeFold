package dev.navifold.player;
import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class QueueHistoryTest {
 @Test public void playedHistoryCanBeReusedButUpcomingCannot(){List<String> q=Arrays.asList("old","now","next");assertEquals(new HashSet<>(Arrays.asList("now","next")),QueueHistory.upcoming(q,1));assertEquals(Collections.singleton("old"),QueueHistory.recent(q,1));}
 @Test public void duplicateIdAlreadyUpcomingStaysBlocked(){assertTrue(QueueHistory.upcoming(Arrays.asList("a","b","a"),1).contains("a"));}
 @Test public void beforePlaybackAllEntriesStayBlocked(){assertEquals(2,QueueHistory.upcoming(Arrays.asList("a","b"),-1).size());assertTrue(QueueHistory.recent(Arrays.asList("a","b"),-1).isEmpty());}
 @Test public void recentWindowIsBounded(){List<String> ids=new ArrayList<>();for(int i=0;i<50;i++)ids.add("s"+i);assertEquals(20,QueueHistory.recent(ids,40).size());assertFalse(QueueHistory.recent(ids,40).contains("s19"));}
}
