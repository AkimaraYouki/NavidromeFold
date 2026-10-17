package dev.navifold.player;
import org.junit.Test;
import static org.junit.Assert.*;
public class QualityInfoTest {
 @Test public void pcmCdIsBlue(){assertEquals(QualityInfo.BLUE,QualityInfo.classify("audio/raw",48000,0,false,false).color);}
 @Test public void losslessHighRateIsYellow(){assertEquals(QualityInfo.YELLOW,QualityInfo.classify("audio/flac",192000,4608000,false,false).color);}
 @Test public void lossyDoesNotBecomeHighResolution(){assertEquals(QualityInfo.BLUE,QualityInfo.classify("audio/mpeg",96000,320000,false,false).color);}
 @Test public void unknownIsNotLabelledCd(){assertEquals(QualityInfo.UNKNOWN,QualityInfo.classify(null,0,0,false,false).color);}
 @Test public void errorOverridesFormat(){assertEquals(QualityInfo.RED,QualityInfo.classify("audio/flac",192000,0,true,false).color);}
 @Test public void onlyVerifiedDecoderGetsMqa(){assertNotEquals(QualityInfo.PURPLE,QualityInfo.classify("audio/flac",96000,0,false,false).color);assertEquals(QualityInfo.PURPLE,QualityInfo.classify("audio/flac",96000,0,false,true).color);}
 @Test public void dsdNeedsDecodedMime(){assertEquals(QualityInfo.WHITE,QualityInfo.classify("audio/dsd",2822400,0,false,false).color);assertEquals(QualityInfo.BLUE,QualityInfo.classify("audio/raw",48000,0,false,false).color);}
}
