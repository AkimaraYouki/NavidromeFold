package dev.navifold.player;
import androidx.media3.common.*;
final class AudioQuality {
 static volatile String trackId="";static volatile QualityInfo current=new QualityInfo(QualityInfo.UNKNOWN,"음질 확인 중");
 static QualityInfo get(Player p){MediaItem item=p.getCurrentMediaItem();if(item!=null&&item.mediaId.equals(trackId))return current;return new QualityInfo(QualityInfo.UNKNOWN,"음질 확인 중");}
}
