package dev.navifold.player;

/** Static browser handoff only; never renders callback parameters or credentials. */
final class LoginCallbackPage {
    static String html(String packageName){
        if(!packageName.matches("[a-zA-Z0-9_.]+"))throw new IllegalArgumentException("Invalid package");
        String open="intent://login-return#Intent;scheme="+packageName+";package="+packageName+";end";
        return "<!doctype html><html lang='ko'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            +"<title>NaviFold 로그인</title><style>body{color:#eee;background:#111;font:18px system-ui;padding:40px 24px;line-height:1.6}a{display:inline-block;color:#111;background:#eee;border-radius:18px;padding:14px 24px;text-decoration:none}</style>"
            +"<h2>브라우저 승인 확인</h2><p>NaviFold로 돌아가면 로그인을 마무리합니다.</p><a href='"+open+"'>NaviFold로 돌아가기</a><p>버튼이 열리지 않으면 최근 앱에서 NaviFold를 선택해 주세요.</p></html>";
    }
}
