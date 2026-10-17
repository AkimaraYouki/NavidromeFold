package dev.navifold.player;
import java.util.Locale;
/** Stream quality, never an assertion about the physical DAC or an MQA decoder. */
final class QualityInfo {
 static final int BLUE=0xff55b9ff,YELLOW=0xffffd05a,PURPLE=0xffc689ff,WHITE=0xffffefe0,RED=0xffff6b76,UNKNOWN=0xff9c9ca7;
 final int color;final String label;QualityInfo(int color,String label){this.color=color;this.label=label;}
 static QualityInfo classify(String mime,int rate,int bitRate,boolean error,boolean verifiedMqa){
  if(error)return new QualityInfo(RED,"재생 오류");
  String m=mime==null?"":mime.toLowerCase(Locale.ROOT);
  if(verifiedMqa)return new QualityInfo(PURPLE,"MQA 디코딩");
  if(m.equals("audio/dsd")||m.equals("audio/x-dsd"))return new QualityInfo(WHITE,"DSD 스트림");
  if(m.isEmpty()||rate<=0)return new QualityInfo(UNKNOWN,"음질 확인 중");
  boolean lossy=m.contains("mpeg")||m.contains("aac")||m.contains("mp4a")||m.contains("opus")||m.contains("vorbis");
  String codec=m.contains("mpeg")?"MP3":m.contains("flac")?"FLAC":m.contains("mp4a")||m.contains("aac")?"AAC":m.contains("opus")?"OPUS":m.equals("audio/raw")?"PCM":"AUDIO";
  String hz=String.format(Locale.ROOT,"%.1f kHz",rate/1000.0);return new QualityInfo(!lossy&&rate>=88200?YELLOW:BLUE,codec+" · "+hz+(bitRate>0?" · "+bitRate/1000+" kbps":""));
 }
}
