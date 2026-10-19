package dev.navifold.player;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import java.net.HttpURLConnection;
import java.net.URL;

/** Public GETs on the app's default network. No tokens, model calls or network overrides. */
final class OpenAiNetworkCheck {
    static String run(Context context){
        ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());
        String network=caps==null?"오프라인":caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)?"VPN":caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"모바일":"네트워크";
        StringBuilder report=new StringBuilder("현재 연결 · ").append(network).append('\n');
        probe(report,"auth.openai.com",ChatGptPlan.AUTH+"/.well-known/openid-configuration");
        probe(report,"api.openai.com",ChatGptPlan.RESOURCE+"/models");
        return report.append("\nHTTP 응답이 있으면 DNS·보안 연결을 거쳐 서버에 도달한 상태입니다. 401·403은 로그인·접근 제한 응답이며, 계정 권한은 별도 확인이 필요합니다.\n\n연결 검사에는 로그인 정보나 음악 데이터를 보내지 않습니다.").toString();
    }
    private static void probe(StringBuilder report,String host,String url){
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)new URL(url).openConnection();
            connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
            report.append(host).append(" · HTTP ").append(connection.getResponseCode()).append('\n');
        }catch(Exception error){
            report.append(host).append(" · ").append(NetworkFailure.message(error,"연결 검사 실패")).append('\n');
        }finally{if(connection!=null)connection.disconnect();}
    }
}
