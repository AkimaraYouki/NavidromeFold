package dev.navifold.player;
import android.app.*;
import android.content.*;
import android.os.Build;
final class PlaybackNotifications {
 static final String CHANNEL="navifold_playback_channel";
 static void createChannel(Context c){NotificationChannel channel=new NotificationChannel(CHANNEL,"음악 재생",NotificationManager.IMPORTANCE_LOW);channel.setDescription("재생 중인 음악과 재생 컨트롤");channel.setShowBadge(false);channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);c.getSystemService(NotificationManager.class).createNotificationChannel(channel);}
 static boolean enabled(Context c){NotificationManager n=c.getSystemService(NotificationManager.class);NotificationChannel ch=n.getNotificationChannel(CHANNEL);return n.areNotificationsEnabled()&&(ch==null||ch.getImportance()!=NotificationManager.IMPORTANCE_NONE);}
 static void request(Activity a,boolean explicit){
  createChannel(a);
  if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
   boolean asked=a.getPreferences(0).getBoolean("notificationAsked",false);
   if(!asked||(explicit&&a.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS))){a.getPreferences(0).edit().putBoolean("notificationAsked",true).apply();a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},31);return;}
  }
  if(explicit)a.startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,a.getPackageName()));
 }
}
