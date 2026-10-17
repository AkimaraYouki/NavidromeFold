package dev.navifold.player;
import org.junit.Test;import static org.junit.Assert.*;
public class QualityBadgeTest {
 @Test public void omitBitrateOnlyFromCompactBadge(){assertEquals("FLAC · 192.0 kHz",QualityBadge.compact("FLAC · 192.0 kHz · 9216 kbps"));assertEquals("MP3 · 48.0 kHz",QualityBadge.compact("MP3 · 48.0 kHz · 320 kbps"));}
 @Test public void statusLabelsRemainIntact(){assertEquals("재생 오류",QualityBadge.compact("재생 오류"));assertEquals("",QualityBadge.compact(null));}
}
