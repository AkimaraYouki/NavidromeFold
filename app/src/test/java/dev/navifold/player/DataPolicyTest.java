package dev.navifold.player;
import org.junit.Test;import static org.junit.Assert.*;
public class DataPolicyTest {
 @Test public void countOnlyPositiveCounterDeltas(){assertEquals(25,DataPolicy.delta(100,125));assertEquals(0,DataPolicy.delta(100,20));assertEquals(0,DataPolicy.delta(-1,200));assertEquals(0,DataPolicy.delta(200,-1));}
 @Test public void automaticEconomyRequiresExplicitOptInAndMeteredNetwork(){assertTrue(DataPolicy.economy(true,true));assertFalse(DataPolicy.economy(false,true));assertFalse(DataPolicy.economy(true,false));}
 @Test public void warnAtThresholdAndAllowDisabledLimit(){assertFalse(DataPolicy.threshold(100000000L-1,100));assertTrue(DataPolicy.threshold(100000000L,100));assertFalse(DataPolicy.threshold(Long.MAX_VALUE,0));}
}
