package dev.navifold.player;
import java.util.*;
/** Already played history is a soft exclusion; current and upcoming tracks remain a hard boundary. */
final class QueueHistory {
 static Set<String> upcoming(List<String> ids,int current){Set<String> out=new HashSet<>();for(int i=Math.max(0,current);i<ids.size();i++)out.add(ids.get(i));return out;}
 static Set<String> recent(List<String> ids,int current){Set<String> out=new HashSet<>();for(int i=Math.max(0,current-20);i<Math.min(ids.size(),current);i++)out.add(ids.get(i));return out;}
}
