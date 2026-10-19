package dev.navifold.player;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.net.*;
import javax.net.ssl.SSLHandshakeException;

public class NetworkFailureTest {
    @Test public void dnsExplainsRecoveryWithoutLeakingRawMessage(){
        String result=NetworkFailure.message(new UnknownHostException("private-host-secret"),"fallback");
        assertTrue(result.contains("DNS"));assertTrue(result.contains("연결 검사"));
        assertFalse(result.contains("private-host-secret"));
    }
    @Test public void wrappedDnsIsRecognized(){assertTrue(NetworkFailure.message(new IOException("wrapper",new UnknownHostException()),"fallback").contains("DNS"));}
    @Test public void timeoutIsNotAnAccountError(){assertTrue(NetworkFailure.message(new SocketTimeoutException(),"fallback").contains("시간 초과"));}
    @Test public void tlsDoesNotSuggestInsecureBypass(){assertTrue(NetworkFailure.message(new SSLHandshakeException("private"),"fallback").contains("보안 연결 실패"));}
    @Test public void connectionFailureHasSeparateMessage(){assertTrue(NetworkFailure.message(new ConnectException(),"fallback").contains("서버에 연결하지 못했습니다"));}
    @Test public void actionableApplicationErrorIsPreserved(){assertEquals("ChatGPT HTTP 429",NetworkFailure.message(new IOException("ChatGPT HTTP 429"),"fallback"));}
    @Test public void unexpectedOrEmptyErrorsUseFallback(){assertEquals("fallback",NetworkFailure.message(new RuntimeException("private"),"fallback"));assertEquals("fallback",NetworkFailure.message(new IOException(),"fallback"));}
    @Test(timeout=1000) public void cyclicCausesTerminate(){IOException a=new IOException("a"),b=new IOException("b");a.initCause(b);b.initCause(a);assertEquals("a",NetworkFailure.message(a,"fallback"));}
}
