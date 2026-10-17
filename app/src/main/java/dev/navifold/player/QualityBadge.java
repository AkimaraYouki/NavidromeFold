package dev.navifold.player;
/** Full detail stays in the player and accessibility description. */
final class QualityBadge {
 static String compact(String label){if(label==null)return "";String[] parts=label.split(" · ");return parts.length>2?parts[0]+" · "+parts[1]:label;}
}
