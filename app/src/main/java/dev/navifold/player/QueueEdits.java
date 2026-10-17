package dev.navifold.player;

import androidx.media3.common.*;
import java.util.*;

/** Queue editing never interrupts the current item or starts an empty queue playing. */
final class QueueEdits {
    static void insert(Player player,List<MediaItem> songs,boolean next) {
        if(songs.isEmpty())return;
        if(player.getMediaItemCount()==0){player.pause();player.setMediaItems(songs);player.prepare();}
        else player.addMediaItems(next?Math.max(0,player.getCurrentMediaItemIndex()+1):player.getMediaItemCount(),songs);
    }
    static List<Integer> removalIndices(List<String> queue,Set<String> selected) {
        List<Integer> indices=new ArrayList<>();for(int i=queue.size()-1;i>=0;i--)if(selected.contains(queue.get(i)))indices.add(i);return indices;
    }
    static void remove(Player player,Set<String> selected) {
        List<String> queue=new ArrayList<>();for(int i=0;i<player.getMediaItemCount();i++)queue.add(player.getMediaItemAt(i).mediaId);
        for(int i:removalIndices(queue,selected))player.removeMediaItem(i);
    }
}
