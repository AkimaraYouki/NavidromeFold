package dev.navifold.player;

import java.io.IOException;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LoginForegroundGateTest {
    @Test public void backgroundCallbackWaitsUntilAppReturns()throws Exception{
        LoginForegroundGate gate=new LoginForegroundGate();
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try{
            Future<Boolean> exchange=worker.submit(()->{gate.await(5000);return true;});
            try{exchange.get(50,TimeUnit.MILLISECONDS);fail("Exchange ran while browser was foreground");}catch(TimeoutException expected){}
            gate.resumed(true);assertTrue(exchange.get(1,TimeUnit.SECONDS));
        }finally{worker.shutdownNow();}
    }
    @Test public void alreadyResumedCanExchangeImmediately()throws Exception{LoginForegroundGate gate=new LoginForegroundGate();gate.resumed(true);gate.await(0);}
    @Test(expected=IOException.class) public void leavingAppClosesGateAgain()throws Exception{LoginForegroundGate gate=new LoginForegroundGate();gate.resumed(true);gate.resumed(false);gate.await(0);}
    @Test public void activityDestructionReleasesWaiter()throws Exception{
        LoginForegroundGate gate=new LoginForegroundGate();ExecutorService worker=Executors.newSingleThreadExecutor();
        try{Future<Boolean> result=worker.submit(()->{try{gate.await(5000);return false;}catch(IOException expected){return true;}});gate.close();assertTrue(result.get(1,TimeUnit.SECONDS));}finally{worker.shutdownNow();}
    }
    @Test(expected=IOException.class) public void closedGateCannotBeReopened()throws Exception{LoginForegroundGate gate=new LoginForegroundGate();gate.close();gate.resumed(true);gate.await(1);}
    @Test(expected=IOException.class) public void noReturnExpiresWithoutExchange()throws Exception{new LoginForegroundGate().await(1);}
    @Test public void pageContainsOnlySafeReturnLink(){
        String html=LoginCallbackPage.html("dev.navifold.player.next");
        assertTrue(html.contains("scheme=dev.navifold.player.next;package=dev.navifold.player.next;end"));
        assertTrue(html.contains("NaviFold로 돌아가면"));assertFalse(html.contains("code="));assertFalse(html.contains("state="));
    }
    @Test(expected=IllegalArgumentException.class) public void pageRejectsLinkInjection(){LoginCallbackPage.html("unsafe';end");}
}
