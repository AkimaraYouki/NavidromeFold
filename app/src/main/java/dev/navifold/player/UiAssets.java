package dev.navifold.player;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
final class UiAssets {
 private static Typeface regular,semibold;
 static Typeface font(Context c){if(regular==null)regular=c.getResources().getFont(R.font.pretendard);return regular;}
 static Typeface semibold(Context c){if(semibold==null)semibold=android.os.Build.VERSION.SDK_INT>=28?Typeface.create(font(c),600,false):Typeface.create(font(c),Typeface.BOLD);return semibold;}
 static int icon(String symbol){switch(symbol){case "▱":case "▣":return R.drawable.ic_folder;case "▤":return R.drawable.ic_album;case "♡":case "♥":return R.drawable.ic_favorite;case "⚙":return R.drawable.ic_settings;case "⌕":return R.drawable.ic_search;case "‹":return R.drawable.ic_back;case "▶":return R.drawable.ic_play;default:return 0;}}
 static CharSequence label(Context c,String text){return text.replaceAll("^[▱▤▣♡♥⚙⌕‹▶]\\s*","");}
 static Drawable drawable(Context c,int resource,int color,int dp){Drawable d=androidx.appcompat.content.res.AppCompatResources.getDrawable(c,resource).mutate();d.setTint(color);int px=Math.round(dp*c.getResources().getDisplayMetrics().density);d.setBounds(0,0,px,px);return d;}
}
