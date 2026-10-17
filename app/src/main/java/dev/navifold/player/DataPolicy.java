package dev.navifold.player;
final class DataPolicy {
 static long delta(long before,long now){return before<0||now<0||now<before?0:now-before;}
 static boolean economy(boolean enabled,boolean metered){return enabled&&metered;}
 static boolean threshold(long bytes,int megabytes){return megabytes>0&&bytes>=megabytes*1000000L;}
}
