package dev.navifold.player;
import org.junit.Test;
import static org.junit.Assert.*;
public class RemovalObservationTest {
 @Test public void excludedCurrentTrackIsIgnoredOnce() {SmartSession.ignoreRemoval("account","song");assertTrue(SmartSession.consumeIgnoredRemoval("account","song"));assertFalse(SmartSession.consumeIgnoredRemoval("account","song"));}
 @Test public void exclusionDoesNotCrossAccountsOrTracks() {SmartSession.ignoreRemoval("a","song");assertFalse(SmartSession.consumeIgnoredRemoval("b","song"));assertFalse(SmartSession.consumeIgnoredRemoval("a","song"));SmartSession.ignoreRemoval("a","song");assertFalse(SmartSession.consumeIgnoredRemoval("a","another"));}
}
