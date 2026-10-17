package dev.navifold.player;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class ServerAddress {
    static String normalize(String address) {
        String value = address == null ? "" : address.trim();
        if (value.contains(",") || value.contains("，"))
            throw new IllegalArgumentException("IP 주소는 쉼표(,) 대신 점(.)으로 구분하세요. 예: http://100.101.102.103:4553");
        if (value.isEmpty()) throw new IllegalArgumentException("서버 주소를 입력하세요. 예: http://100.101.102.103:4553");
        if (!value.contains("://")) value = "http://" + value;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("https".equals(scheme) || "http".equals(scheme)) || uri.getHost() == null
                || uri.getRawQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null
                || uri.getPort() > 65535 || uri.getPort() == 0)
                throw new IllegalArgumentException("서버 기본 주소를 확인하세요. 예: http://100.101.102.103:4553");
            return scheme + value.substring(value.indexOf(':')).replaceAll("/+$", "");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("서버 주소 형식이 올바르지 않습니다. 예: http://100.101.102.103:4553");
        }
    }
}
