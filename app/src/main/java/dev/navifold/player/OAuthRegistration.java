package dev.navifold.player;

import java.io.IOException;
import org.json.JSONObject;

/** An issued registration can outlive a failed exchange; it is not a verified account. */
final class OAuthRegistration {
    static final String DYNAMIC="dynamic_agent_client";
    static String client(JSONObject saved){
        String active=saved.optString("client_id","");
        if(!active.isBlank()&&!DYNAMIC.equals(active))return active;
        String pending=saved.optString("pending_client_id","");
        return pending.isBlank()||DYNAMIC.equals(pending)?DYNAMIC:pending;
    }
    static String issued(String requested,String returned)throws IOException{
        if(DYNAMIC.equals(requested)){
            if(returned==null||returned.isBlank()||DYNAMIC.equals(returned))throw new IOException("앱 등록이 완료되지 않았습니다. 브라우저에서 승인을 마쳐 주세요.");
            return returned;
        }
        if(returned!=null&&!requested.equals(returned))throw new IOException("로그인 앱 등록 정보가 일치하지 않습니다.");
        return requested;
    }
    static JSONObject pending(JSONObject saved,String issued)throws Exception{
        JSONObject next=new JSONObject(saved.toString());
        if(DYNAMIC.equals(client(saved)))next.put("pending_client_id",issued);
        return next;
    }
    static boolean hintsAllowed(JSONObject saved,String client){return client.equals(saved.optString("client_id"))&&saved.has("sub");}
}
