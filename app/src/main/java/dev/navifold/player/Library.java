package dev.navifold.player;

import org.json.*;
import java.util.*;

/** Server metadata only. Never turn filesystem paths into stream URLs. */
final class Library {
    static String title(JSONObject j) { return j.optString("name", j.optString("title", "제목 없음")); }
    static String artist(JSONObject j) { return j.optString("albumArtist", j.optString("artist", "알 수 없는 아티스트")); }
    static List<JSONObject> array(JSONObject obj, String key) {
        List<JSONObject> result = new ArrayList<>();
        JSONArray a = obj == null ? null : obj.optJSONArray(key);
        if (a != null) for (int i=0; i<a.length(); i++) { JSONObject j=a.optJSONObject(i); if(j!=null)result.add(j); }
        return result;
    }
    static final Comparator<JSONObject> TRACK_ORDER = Comparator
        .comparingInt((JSONObject j)->Math.max(1,j.optInt("discNumber", 1)))
        .thenComparingInt(j->j.optInt("track",0)>0?j.optInt("track"):Integer.MAX_VALUE)
        .thenComparing(j->j.optString("title", ""), String.CASE_INSENSITIVE_ORDER)
        .thenComparing(j->j.optString("id", ""));
    static final class Node {
        final String name, key;
        final Node parent;
        final SortedMap<String, Node> children = new TreeMap<>(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        final List<JSONObject> songs = new ArrayList<>();
        int count;
        Node(String name, String key, Node parent) {this.name=name;this.key=key;this.parent=parent;}
        List<JSONObject> allSongs() {
            List<JSONObject> result=new ArrayList<>(); collect(this,result); return result;
        }
        private static void collect(Node node,List<JSONObject> result) {
            List<JSONObject> direct=new ArrayList<>(node.songs);direct.sort(TRACK_ORDER);result.addAll(direct);
            for(Node child:node.children.values())collect(child,result);
        }
        String breadcrumb() {return parent==null?name:parent.breadcrumb()+" / "+name;}
    }
    static final class Index {
        final Node root=new Node("모든 음악 폴더", "", null);
        final Map<String,Node> nodes=new HashMap<>();
        final Set<String> seen=new HashSet<>();
        int missingPaths;
        Index(){nodes.put("",root);}
        boolean add(String rootId,String rootName,JSONObject song) {
            String id=song.optString("id");
            if(id.isEmpty()||!seen.add(rootId+"\n"+id))return false;
            String path=song.optString("path", "").replace('\\','/');
            if(path.isEmpty()||path.endsWith("/")){missingPaths++;return true;}
            List<String> parts=new ArrayList<>();
            for(String p:path.split("/")) {
                if(p.equals("..")){missingPaths++;return true;}
                if(!p.isEmpty()&&!p.equals("."))parts.add(p);
            }
            if(parts.isEmpty()){missingPaths++;return true;}
            Node node=root.children.get(rootId);
            if(node==null){node=new Node(rootName,rootId,root);root.children.put(rootId,node);nodes.put(rootId,node);}
            for(int i=0;i<parts.size()-1;i++) {
                String name=parts.get(i); Node next=node.children.get(name);
                if(next==null){next=new Node(name,node.key+"/"+name,node);node.children.put(name,next);nodes.put(next.key,next);}node=next;
            }
            node.songs.add(song);
            for(Node n=node;n!=null;n=n.parent)n.count++;
            return true;
        }
    }
    static List<JSONObject> albumsIn(List<JSONObject> songs) {
        Map<String,JSONObject> map=new LinkedHashMap<>();
        for(JSONObject s:songs) {
            String id=s.optString("albumId");if(id.isEmpty())continue;
            if(!map.containsKey(id))try {
                JSONObject j=new JSONObject();j.put("id",id).put("name",s.optString("album","알 수 없는 앨범"))
                    .put("artist",artist(s)).put("year",s.optInt("year")).put("coverArt",s.optString("coverArt"));map.put(id,j);
            }catch(JSONException ignored){}
        }
        List<JSONObject> out=new ArrayList<>(map.values());
        out.sort(Comparator.comparing(Library::artist,String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(j->j.optInt("year")).thenComparing(Library::title,String.CASE_INSENSITIVE_ORDER));return out;
    }
}
