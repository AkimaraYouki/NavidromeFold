package dev.navifold.player;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class QueueEditsTest {
 @Test public void removesDuplicateOccurrencesInReverseOrder(){assertEquals(Arrays.asList(3,1,0),QueueEdits.removalIndices(Arrays.asList("a","a","b","c"),new HashSet<>(Arrays.asList("a","c"))));}
 @Test public void preservesUnselectedItems(){assertEquals(Collections.emptyList(),QueueEdits.removalIndices(Arrays.asList("a","b"),Collections.singleton("x")));}
}
