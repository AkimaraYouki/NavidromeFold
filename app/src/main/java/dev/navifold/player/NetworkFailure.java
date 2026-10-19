package dev.navifold.player;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/** Classify by exception type, never by server text or account credentials. */
final class NetworkFailure {
    static String message(Throwable error,String fallback){
        // Bound traversal even for a malformed cyclic cause chain.
        Throwable cause=error;
        for(int depth=0;depth<16&&cause!=null;depth++,cause=cause.getCause()){
            if(cause instanceof UnknownHostException)return "DNS 오류 · 서버 주소를 찾지 못했습니다.\n\nWi-Fi·모바일 연결과 Tailscale/VPN의 DNS 상태를 확인한 뒤 다시 시도하세요. ChatGPT 설정의 ‘연결 검사’에서 확인할 수 있습니다.";
            if(cause instanceof SSLException)return "보안 연결 실패 · 기기 날짜·시간과 VPN·보안 필터를 확인하세요.";
            if(cause instanceof SocketTimeoutException)return "연결 시간 초과 · 네트워크 상태를 확인한 뒤 다시 시도하세요.";
            if(cause instanceof ConnectException)return "서버에 연결하지 못했습니다 · Wi-Fi·모바일 연결과 VPN 상태를 확인하세요.";
            if(cause.getCause()==cause)break;
        }
        return error instanceof IOException&&error.getMessage()!=null&&!error.getMessage().isBlank()?error.getMessage():fallback;
    }
}
