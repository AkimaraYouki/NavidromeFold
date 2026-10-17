package dev.navifold.player;

import android.net.Uri;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class Api {
    final String base, user, password;
    Api(String address, String user, String password) throws Exception {
        this.base = ServerAddress.normalize(address);
        this.user = user; this.password = password;
    }
    String url(String method, String... args) throws Exception {
        String salt = UUID.randomUUID().toString().replace("-", "");
        byte[] digest = MessageDigest.getInstance("MD5").digest((password + salt).getBytes(StandardCharsets.UTF_8));
        StringBuilder token = new StringBuilder();
        for (byte b : digest) token.append(String.format(Locale.ROOT, "%02x", b & 255));
        Uri.Builder b = Uri.parse(base + "/rest/" + method + ".view").buildUpon()
            .appendQueryParameter("u", user).appendQueryParameter("t", token.toString())
            .appendQueryParameter("s", salt).appendQueryParameter("v", "1.16.1")
            .appendQueryParameter("c", "NaviFold").appendQueryParameter("f", "json");
        for (int i=0; i<args.length; i+=2) b.appendQueryParameter(args[i], args[i+1]);
        return b.build().toString();
    }
    JSONObject call(String method, String... args) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url(method, args)).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(false);
        try {
            int status = c.getResponseCode();
            if (status != 200) throw new Exception("서버 응답 HTTP " + status + " · 주소와 /rest/ 접근 설정을 확인하세요.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n=in.read(buf))!=-1) {
                    if(out.size()+n>8*1024*1024) throw new Exception("응답 크기가 너무 큽니다.");
                    out.write(buf,0,n);
                }
            }
            JSONObject response;
            try { response = new JSONObject(out.toString("UTF-8")).getJSONObject("subsonic-response"); }
            catch (JSONException e) { throw new Exception("Subsonic 응답이 아닙니다. 서버 기본 주소와 프록시 설정을 확인하세요."); }
            if (!"ok".equals(response.optString("status"))) {
                JSONObject error = response.optJSONObject("error");
                throw new Exception("API 오류 " + (error == null ? "" : error.optInt("code")) + " · 로그인 정보 또는 권한을 확인하세요.");
            }
            return response;
        } finally { c.disconnect(); }
    }
}
