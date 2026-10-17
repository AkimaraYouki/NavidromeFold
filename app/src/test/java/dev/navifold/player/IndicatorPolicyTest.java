package dev.navifold.player;
import org.junit.Test;
import static org.junit.Assert.*;
public class IndicatorPolicyTest {
 @Test public void foldedPortraitIsEligibleWithoutAnySensorReading(){assertTrue(IndicatorPolicy.eligible(390,0));}
 @Test public void unfoldedDisplayIsHiddenEvenInNarrowWindow(){assertFalse(IndicatorPolicy.eligible(700,0));assertFalse(IndicatorPolicy.eligible(600,0));}
 @Test public void landscapeDisplayIsHidden(){assertFalse(IndicatorPolicy.eligible(390,1));assertFalse(IndicatorPolicy.eligible(390,3));}
 @Test public void reversePortraitDisplayIsHidden(){assertFalse(IndicatorPolicy.eligible(390,2));}
 @Test public void compactDisplayThreshold(){assertTrue(IndicatorPolicy.eligible(599,0));assertFalse(IndicatorPolicy.eligible(601,0));}
 @Test public void islandSeparatesCodecAndRateAroundCamera(){assertArrayEquals(new String[]{"FLAC","96.0 kHz"},IndicatorPolicy.parts("FLAC · 96.0 kHz · 1200 kbps"));assertArrayEquals(new String[]{"음질 확인 중","—"},IndicatorPolicy.parts("음질 확인 중"));}
}
