package dev.navifold.player;

/** Geometry-independent rules shared by camera ring and external island. */
final class IndicatorPolicy {
 static boolean eligible(float displayShortDp,int rotation){
  return displayShortDp<600 && rotation==0;
 }
 static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
 static String[] parts(String label){
  String[] fields=label.split(" · ");
  return fields.length>=2?new String[]{fields[0],fields[1]}:new String[]{label,"—"};
 }
}
