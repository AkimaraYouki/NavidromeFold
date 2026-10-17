package dev.navifold.player;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaMetadata;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
final class TrackPopup {
 final Context context;final ExecutorService io=Executors.newSingleThreadExecutor();final Handler main=new Handler(Looper.getMainLooper());int generation;
 TrackPopup(Context c){context=c;}
 void show(MediaMetadata metadata,PendingIntent open){
  int request=++generation;AppSettings settings=new AppSettings(AppSettings.preferences(context));if(!settings.lockPopup||!context.getSystemService(KeyguardManager.class).isKeyguardLocked())return;
  if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
  NotificationManager manager=context.getSystemService(NotificationManager.class);NotificationChannel channel=new NotificationChannel("navifold_track_popup","잠금화면 곡 변경",NotificationManager.IMPORTANCE_HIGH);channel.setSound(null,null);channel.enableVibration(false);channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);manager.createNotificationChannel(channel);
  NotificationCompat.Builder builder=new NotificationCompat.Builder(context,channel.getId()).setSmallIcon(R.drawable.ic_stat_music).setContentTitle(metadata.title).setContentText(metadata.artist).setSubText(metadata.albumTitle).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setCategory(NotificationCompat.CATEGORY_TRANSPORT).setContentIntent(open).setAutoCancel(true).setTimeoutAfter(20000).setPriority(NotificationCompat.PRIORITY_HIGH);
  manager.notify(903,builder.build());
  if(metadata.artworkUri!=null)io.execute(()->{try{HttpURLConnection connection=(HttpURLConnection)new URL(metadata.artworkUri.toString()).openConnection();connection.setConnectTimeout(6000);connection.setReadTimeout(6000);connection.setInstanceFollowRedirects(false);Bitmap image=null;try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>3*1024*1024)break;out.write(buffer,0,n);}byte[] bytes=out.toByteArray();BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>720)options.inSampleSize*=2;image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);}finally{connection.disconnect();}Bitmap art=image;if(art!=null)main.post(()->{if(request==generation&&context.getSystemService(KeyguardManager.class).isKeyguardLocked())manager.notify(903,builder.setOnlyAlertOnce(true).setLargeIcon(art).build());});}catch(Exception ignored){}});
 }
 void close(){generation++;io.shutdownNow();main.removeCallbacksAndMessages(null);context.getSystemService(NotificationManager.class).cancel(903);}
}
