package dev.navifold.player;
import android.app.*;
import android.os.*;
import android.view.*;
import android.content.*;
import android.widget.*;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import com.google.common.util.concurrent.ListenableFuture;

/** User-opened music card on top of keyguard; never unlocks or wakes the device itself. */
public final class LockPlayerActivity extends Activity {
 ListenableFuture<MediaController> future;MediaController controller;NowPlayingSheet sheet;final Covers covers=new Covers();
 @Override public void onCreate(Bundle b){super.onCreate(b);if(Build.VERSION.SDK_INT>=27)setShowWhenLocked(true);else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);getWindow().setStatusBarColor(0xff111217);getWindow().setNavigationBarColor(0xff111217);LinearLayout root=new LinearLayout(this);root.setBackgroundColor(0xff111217);setContentView(root);future=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();future.addListener(()->{try{controller=future.get();if(isFinishing())return;if(controller.getCurrentMediaItem()==null){finish();return;}showPlayer();}catch(Exception e){finish();}},this::runOnUiThread);}
 void showPlayer(){if(sheet!=null){sheet.setOnDismissListener(null);sheet.dismiss();}AppSettings s=new AppSettings(AppSettings.preferences(this));s.dark=true;s.systemTheme=false;sheet=new NowPlayingSheet(this,controller,()->null,s,covers,0xfff5f5f8,0xffa8aab7,0xff111217,0xff242630,0xff8da9ff);sheet.setOnDismissListener(d->finish());sheet.show();}
 @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);if(controller!=null)showPlayer();}
 @Override protected void onStart(){super.onStart();AppVisibility.activities++;}
 @Override protected void onStop(){AppVisibility.activities=Math.max(0,AppVisibility.activities-1);super.onStop();}
 @Override protected void onDestroy(){if(sheet!=null){sheet.setOnDismissListener(null);sheet.dismiss();}if(future!=null)MediaController.releaseFuture(future);covers.close();super.onDestroy();}
}
