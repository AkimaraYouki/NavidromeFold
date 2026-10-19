package dev.navifold.player;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Only the login activity's resumed state permits the post-browser exchange. */
final class LoginForegroundGate {
    private boolean resumed,closed;
    synchronized void resumed(boolean value){resumed=value;notifyAll();}
    synchronized void close(){closed=true;notifyAll();}
    synchronized void await(long timeoutMillis)throws IOException,InterruptedException{
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while(!closed&&!resumed){
            long remaining=deadline-System.nanoTime();
            if(remaining<=0)throw new IOException("앱으로 돌아오지 않아 로그인 대기가 끝났습니다. 다시 로그인해 주세요.");
            TimeUnit.NANOSECONDS.timedWait(this,remaining);
        }
        if(closed)throw new IOException("로그인 화면이 닫혔습니다. 앱에서 다시 로그인해 주세요.");
    }
}
