package dev.navifold.player;
import org.junit.Test;
import static org.junit.Assert.*;
public class SmartPolicyTest {
 @Test public void threeEarlySkipsRequired(){assertFalse(SmartPolicy.strongNegative(2,0,0));assertTrue(SmartPolicy.strongNegative(3,0,0));}
 @Test public void completionsPreventInferredDislike(){assertFalse(SmartPolicy.strongNegative(4,1,0));}
 @Test public void explicitVotesOverrideInference(){assertFalse(SmartPolicy.strongNegative(9,0,1));assertTrue(SmartPolicy.strongNegative(0,5,-1));}
 @Test public void automaticSkipNeverBecomesManualFeedback(){assertEquals("auto-skip",SmartPolicy.outcome(10000,120000,true,true,false));}
 @Test public void errorNeverBecomesManualFeedback(){assertEquals("error",SmartPolicy.outcome(10000,120000,true,false,true));}
 @Test public void shortManualSkipIsNegative(){assertEquals("manual-skip",SmartPolicy.outcome(10000,120000,true,false,false));}
 @Test public void briefTapAndBackwardSkipAreNeutral(){assertEquals("neutral",SmartPolicy.outcome(1500,120000,true,false,false));assertEquals("neutral",SmartPolicy.outcome(10000,120000,false,false,false));}
 @Test public void seeksDoNotSimulateCompletion(){assertEquals("neutral",SmartPolicy.outcome(2000,120000,false,false,false));assertEquals("complete",SmartPolicy.outcome(85000,120000,false,false,false));}
 @Test public void protectsManualQueueAndLastSong(){assertFalse(SmartPolicy.canSkip(true,false,true,true,0));assertFalse(SmartPolicy.canSkip(true,true,true,false,0));}
 @Test public void atMostTwoConsecutiveSkips(){assertTrue(SmartPolicy.canSkip(true,true,true,true,1));assertFalse(SmartPolicy.canSkip(true,true,true,true,2));}
 @Test public void repeatShufflePauseDisableRefill(){assertFalse(SmartPolicy.canRefill(true,true,1,false,0));assertFalse(SmartPolicy.canRefill(true,true,0,true,0));assertFalse(SmartPolicy.canRefill(true,false,0,false,0));assertFalse(SmartPolicy.canRefill(false,true,0,false,0));}
 @Test public void onlyLowQueueRefills(){assertTrue(SmartPolicy.canRefill(true,true,0,false,3));assertFalse(SmartPolicy.canRefill(true,true,0,false,4));}
}
